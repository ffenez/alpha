package app.alpha.ui.logic

import app.alpha.ui.text.SearchRu
import app.alpha.ui.text.SearchStrings
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Search-mode audio/vibration feedback policies (SPEC «Search»: sound and
 * vibration whose frequency rises with CPS). Pure JVM math; the Android side
 * (AudioTrack, Vibrator) lives in `ui/feedback`.
 */
object ClickRate {

    /** Cap so a strong source stays a rattle, not a solid tone. */
    const val MAX_CLICKS_PER_SECOND = 40f

    /** Shortest inter-click gap, the inverse of the cap. */
    const val MIN_INTERVAL_SECONDS = 1f / MAX_CLICKS_PER_SECOND

    /**
     * Longest gap: at very low rates the clamp keeps the mode audibly alive.
     * RadiaCode background is 5–30 CPS, so the clamp never biases real use.
     */
    const val MAX_INTERVAL_SECONDS = 10f

    /**
     * Click rate = CPS one-to-one (a click per registered event, like a
     * classic Geiger counter speaker), clamped to [MAX_CLICKS_PER_SECOND].
     */
    fun clicksPerSecond(cps: Float?): Float =
        (cps ?: 0f).coerceIn(0f, MAX_CLICKS_PER_SECOND)

    /**
     * Next inter-click interval: exponential (Poisson process) via the
     * inverse CDF, because real detector events arrive that way — regular
     * metronome clicking would misrepresent counting statistics. [u] is a
     * uniform random in [0, 1). Memoryless, so the renderer may resample on
     * every rate change without bias.
     */
    fun nextIntervalSeconds(rate: Float, u: Float): Float {
        if (rate <= 0f) return Float.POSITIVE_INFINITY
        val clamped = u.coerceIn(0f, 0.999999f)
        val interval = (-ln(1.0 - clamped) / rate).toFloat()
        return interval.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
    }
}

/**
 * The click renderer's state machine, extracted from the Android audio
 * thread so it can be tested on the JVM: given a rate and a click waveform
 * it fills PCM chunks with silence and «ticks» at exponential intervals.
 *
 * This is the part that used to be untestable — a wrong interval or a stuck
 * state machine here means the Search screen is simply silent, with nothing
 * on screen to say why.
 *
 * [random] returns a uniform [0, 1); injectable so tests are deterministic.
 */
class ClickEngine(
    private val sampleRate: Int,
    private val random: () -> Float,
) {

    private var clickPos = -1
    private var active: ShortArray? = null
    private var framesToNext = 0L
    private var lastRate = -1f

    /**
     * Renders one chunk. [click] is the waveform for clicks *started* in this
     * chunk (the pitch of «тон по энергии»); a click already sounding keeps
     * the waveform it started with.
     *
     * A changed rate resamples the pending interval: exponential intervals
     * are memoryless, so that is statistically exact, not an approximation.
     */
    fun fillChunk(out: ShortArray, rate: Float, click: ShortArray) {
        if (rate != lastRate) {
            lastRate = rate
            framesToNext = intervalFrames(rate)
        }
        for (i in out.indices) {
            if (clickPos < 0 && framesToNext <= 0) {
                if (rate > 0f && click.isNotEmpty()) {
                    clickPos = 0
                    active = click
                    framesToNext = intervalFrames(rate)
                } else {
                    // Silent: re-check on the next chunk instead of spinning.
                    framesToNext = out.size.toLong()
                }
            }
            framesToNext--
            val waveform = active
            if (waveform != null && clickPos >= 0 && clickPos < waveform.size) {
                out[i] = waveform[clickPos]
                clickPos++
                if (clickPos >= waveform.size) clickPos = -1
            } else {
                out[i] = 0
            }
        }
    }

    private fun intervalFrames(rate: Float): Long {
        val seconds = ClickRate.nextIntervalSeconds(rate, random())
        if (seconds == Float.POSITIVE_INFINITY) return Long.MAX_VALUE
        return (seconds * sampleRate).toLong().coerceAtLeast(1L)
    }
}

/**
 * The click waveform, generated programmatically (no bundled audio assets):
 * a short sine with a fast exponential decay — a dry «tick». The default
 * pitch is 2.6 kHz; «тон по энергии» renders the same tick at the band
 * pitch. Returned as 16-bit PCM samples for the given sample rate.
 */
object ClickWaveform {

    const val FREQUENCY_HZ = 2600f
    const val DURATION_SECONDS = 0.004f
    const val DECAY_SECONDS = 0.0012f
    const val AMPLITUDE = 0.55f

    fun pcm16(sampleRate: Int, frequencyHz: Float = FREQUENCY_HZ): ShortArray {
        val n = (sampleRate * DURATION_SECONDS).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val t = i / sampleRate.toFloat()
            val v = sin(2.0 * Math.PI * frequencyHz * t) * exp(-t / DECAY_SECONDS) * AMPLITUDE
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }
}

/**
 * «Тон по энергии» (Поиск): the click pitch follows the mean photon energy
 * of the latest spectrogram interval slice (5 s cadence). Three bands are
 * deliberate — CsI(Tl) resolution and 5 s statistics do not support a finer
 * musical scale, and three pitches are instantly tellable apart by ear:
 *
 *  - LOW  < 300 keV            → 1.8 kHz («мягкий» — scattered/low-energy);
 *  - MID  300–1000 keV         → 2.6 kHz (the classic default tick);
 *  - HIGH > 1000 keV           → 3.4 kHz («звонкий» — hard gammas, K-40 etc).
 *
 * Higher keV → higher pitch. Without fresh spectrum data the mode honestly
 * falls back to the plain default click ([bandForMeanEnergy] = null).
 */
object EnergyTone {

    enum class Band { LOW, MID, HIGH }

    const val LOW_MAX_KEV = 300f
    const val MID_MAX_KEV = 1000f

    /** Spectrum slices older than this no longer steer the pitch (3 polls). */
    const val STALE_MILLIS = 15_000L

    fun bandForMeanEnergy(meanKeV: Float?): Band? = when {
        meanKeV == null || meanKeV <= 0f -> null
        meanKeV < LOW_MAX_KEV -> Band.LOW
        meanKeV <= MID_MAX_KEV -> Band.MID
        else -> Band.HIGH
    }

    fun frequencyHz(band: Band): Float = when (band) {
        Band.LOW -> 1800f
        Band.MID -> ClickWaveform.FREQUENCY_HZ
        Band.HIGH -> 3400f
    }

    fun isFresh(sliceAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - sliceAtMillis <= STALE_MILLIS
}

/**
 * Everything the Поиск screen knows about why feedback might be silent.
 * Booleans only, so the wording is pure and JVM-testable.
 */
data class FeedbackState(
    /** Каналы отклика, включённые человеком; выключены все — тишина. */
    val channels: SearchFeedbackChannels,
    val deviceConnected: Boolean,
    /** A sample arrived recently enough to drive clicks. */
    val dataFresh: Boolean,
    val dndBlocked: Boolean,
    /** The audio engine could not be started at all (no track, no channel). */
    val audioUnavailable: Boolean,
    /** Media volume is at zero — the stream the clicks play on. */
    val volumeZero: Boolean,
    /** A local background reference exists; everything relative needs it. */
    val backgroundRecorded: Boolean,
    /**
     * The tone/vibration is silent because the count rate is inside the
     * background right now. That is the **designed** behaviour, not a fault —
     * but a channel that is simply quiet reads as broken, so it is said out
     * loud (redesign §7).
     */
    val insideBackground: Boolean = false,
) {
    val usesSound: Boolean get() = channels.usesSound

    /** Каналы, которые описывают счёт ОТНОСИТЕЛЬНО того, с чем сравнивают. */
    val usesBackground: Boolean get() = channels.usesReference
}

/**
 * Silence must be explainable. Instead of a screen that just says nothing,
 * this returns the single most important reason no clicks or pulses are
 * being produced right now — or null when feedback really is running.
 */
object FeedbackReason {

    fun line(state: FeedbackState, t: SearchStrings = SearchRu): String? = when {
        state.channels.silent -> t.reasonOff
        !state.deviceConnected -> t.reasonNoDevice
        !state.dataFresh -> t.reasonNoData
        state.dndBlocked -> t.reasonDnd
        state.usesSound && state.audioUnavailable -> t.reasonNoAudio
        state.usesSound && state.volumeZero -> t.reasonVolumeZero
        // Щелчки слышны всегда: они про импульсы, а не про отношение. Поэтому
        // молчание «относительных» каналов объясняется, только когда щелчков
        // нет и слушать больше нечего.
        !state.channels.clicks && state.usesBackground && !state.backgroundRecorded ->
            t.reasonNoBackground(channel(state.channels, t))
        !state.channels.clicks && state.usesBackground && state.insideBackground ->
            t.reasonInsideBackground(channel(state.channels, t))
        else -> null
    }

    /** Как назвать молчащий канал: одним словом, когда он один. */
    private fun channel(channels: SearchFeedbackChannels, t: SearchStrings): String = when {
        channels.tone && !channels.vibro -> t.channelTone
        channels.vibro && !channels.tone -> t.channelVibro
        else -> t.channelFeedback
    }
}
