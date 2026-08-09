package app.radiacode.ui.logic

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Search-mode local background reference (SPEC: "Set local background" —
 * average CPS over 30–60 s, then compare against it). Immutable steps so the
 * state drops straight into Compose; JVM-tested.
 */
sealed interface BackgroundRef {

    /** No reference set. */
    data object None : BackgroundRef

    /** Averaging in progress; one [onSample] call per 1 Hz CPS sample. */
    data class Measuring(
        val sumCps: Double,
        val sampleCount: Int,
        val targetSamples: Int,
    ) : BackgroundRef {
        val progress: Float get() = sampleCount.toFloat() / targetSamples

        fun onSample(cps: Float): BackgroundRef {
            val sum = sumCps + cps
            val count = sampleCount + 1
            return if (count >= targetSamples) {
                Ready(cps = (sum / count).toFloat())
            } else {
                Measuring(sumCps = sum, sampleCount = count, targetSamples = targetSamples)
            }
        }
    }

    /** Reference established. */
    data class Ready(val cps: Float) : BackgroundRef

    companion object {
        /** 45 s at 1 Hz: inside the 30–60 s window the SPEC allows. */
        const val DEFAULT_TARGET_SAMPLES = 45

        fun startMeasuring(targetSamples: Int = DEFAULT_TARGET_SAMPLES): Measuring =
            Measuring(sumCps = 0.0, sampleCount = 0, targetSamples = targetSamples)
    }
}

/** Whole-percent delta vs background; null when there is no reference. */
fun deltaPercent(cps: Float, backgroundCps: Float?): Int? {
    if (backgroundCps == null || backgroundCps <= 0f) return null
    return (((cps - backgroundCps) / backgroundCps) * 100f).roundToInt()
}

/**
 * LED meter drive: full scale = [FULL_SCALE_FACTOR]× background, so the meter
 * sits low on background and saturates near a strong source. Without a
 * reference the meter stays dark — it has nothing honest to show.
 */
fun ledLevel(cps: Float, backgroundCps: Float?): Float {
    if (backgroundCps == null || backgroundCps <= 0f) return 0f
    return (cps / (backgroundCps * FULL_SCALE_FACTOR)).coerceIn(0f, 1f)
}

/**
 * Expected Poisson fluctuation band around the background at 1 s counting:
 * bg ± 2·sqrt(bg) (~95%). Rendered as the dithered band on the search chart —
 * a statistical statement, not an opinion.
 */
fun backgroundBand(backgroundCps: Float): ClosedFloatingPointRange<Float> {
    val sigma = sqrt(backgroundCps.toDouble()).toFloat()
    return (backgroundCps - 2f * sigma).coerceAtLeast(0f)..(backgroundCps + 2f * sigma)
}

private const val FULL_SCALE_FACTOR = 5f
