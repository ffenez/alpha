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
 * a 2.6 kHz sine with a fast exponential decay — a dry, short «tick».
 * Returned as 16-bit PCM samples for the given sample rate.
 */
object ClickWaveform {

    const val FREQUENCY_HZ = 2600f
    const val DURATION_SECONDS = 0.004f
    const val DECAY_SECONDS = 0.0012f
    const val AMPLITUDE = 0.55f

    fun pcm16(sampleRate: Int): ShortArray {
        val n = (sampleRate * DURATION_SECONDS).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val t = i / sampleRate.toFloat()
            val v = sin(2.0 * Math.PI * FREQUENCY_HZ * t) * exp(-t / DECAY_SECONDS) * AMPLITUDE
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
