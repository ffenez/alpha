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
 * Vibration policy for Search: one short pulse each time the count rate
 * climbs to a new whole-σ step above the local background, starting at +2σ
 * (below that is ordinary Poisson fluctuation, ~95% band). σ = √background
 * (1 s counting). Falling back re-arms a step only after dropping a full σ
 * below it, so boundary noise cannot buzz. Honest and quiet: a stationary
 * meter — even over a source — does not vibrate; only getting *closer*
 * (rising σ level) does.
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
