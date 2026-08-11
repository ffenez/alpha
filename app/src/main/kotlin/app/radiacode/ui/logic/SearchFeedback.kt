package app.radiacode.ui.logic

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

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
 * Vibration policy for Search: one short pulse each time the count rate
 * climbs to a new whole-σ step above the local background, starting at +2σ
 * (below that is ordinary Poisson fluctuation, ~95% band). σ = √background
 * (1 s counting). Falling back re-arms a step only after dropping a full σ
 * below it, so boundary noise cannot buzz. Honest and quiet: a stationary
 * meter — even over a source — does not vibrate; only getting *closer*
 * (rising σ level) does.
 *
 * Consequence the UI must state out loud: **without a recorded local
 * background there is no σ and no reference, so this never pulses.** That is
 * a deliberate policy, not a failure — but silently never vibrating reads as
 * a broken feature, so the Поиск screen says «фон не записан» ([FeedbackReason]).
 */
class VibrationPolicy {

    private var level = 0

    /** Feed one 1 Hz sample; true = emit one pulse now. */
    fun onSample(cps: Float, backgroundCps: Float?): Boolean {
        if (backgroundCps == null || backgroundCps <= 0f) {
            level = 0
            return false
        }
        val sigma = sqrt(backgroundCps)
        val step = floor((cps - backgroundCps) / sigma).toInt().coerceAtLeast(0)
        return when {
            step >= MIN_STEP && step > level -> {
                level = step
                true
            }
            step < level - 1 -> {
                // 1σ hysteresis on the way down; never pulses.
                level = step.coerceAtLeast(0)
                false
            }
            else -> false
        }
    }

    fun reset() {
        level = 0
    }

    companion object {
        /** First pulsing step: +2σ over background. */
        const val MIN_STEP = 2
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
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val deviceConnected: Boolean,
    /** A sample arrived recently enough to drive clicks. */
    val dataFresh: Boolean,
    val dndBlocked: Boolean,
    /** The audio engine could not be started at all (no track, no channel). */
    val audioUnavailable: Boolean,
    /** Media volume is at zero — the stream the clicks play on. */
    val volumeZero: Boolean,
    /** A local background reference exists; σ-steps are relative to it. */
    val backgroundRecorded: Boolean,
)

/**
 * Silence must be explainable. Instead of a screen that just says nothing,
 * this returns the single most important reason no clicks or pulses are
 * being produced right now — or null when feedback really is running.
 */
object FeedbackReason {

    fun line(state: FeedbackState): String? = when {
        !state.soundEnabled && !state.vibrationEnabled ->
            "звук и вибрация выключены"
        !state.deviceConnected ->
            "прибор не подключён — клики и вибрация появятся после подключения"
        !state.dataFresh ->
            "нет данных с прибора — клики молчат, пока поток не восстановится"
        state.dndBlocked ->
            "режим «не беспокоить» — клики и вибрация молчат, пока он включён"
        state.soundEnabled && state.audioUnavailable ->
            "звук не запустился — система не дала звуковой канал"
        state.soundEnabled && state.volumeZero ->
            "громкость мультимедиа на нуле — прибавьте громкость кнопкой"
        state.vibrationEnabled && !state.backgroundRecorded ->
            "фон не записан — вибрация включится после записи фона"
        !state.soundEnabled ->
            "звук выключен — работает только вибрация"
        else -> null
    }
}
