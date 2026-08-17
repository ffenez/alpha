package app.alpha.analysis

import app.alpha.ui.text.SpectrumRu
import app.alpha.ui.text.SpectrumStrings

/**
 * Channel-wise merge of spectrum snapshots (История «объединить», Спектр
 * «продолжить накопление»). Counts add per channel, accumulation times add —
 * the result is exactly what one longer measurement of the same scene would
 * have recorded, *provided the snapshots share the energy grid*: same channel
 * count and calibrations within [SpectrumCompare.CALIBRATION_TOLERANCE_KEV]
 * (≈2 channels, well under the detector FWHM). Beyond the tolerance a merge
 * would smear peaks across bins, so it is refused with an honest reason —
 * resampled fractional counts would no longer be Poisson counts, use the
 * «Скорости счёта» comparison instead.
 *
 * JVM-tested; no Android dependencies.
 */
object SpectrumMerge {

    data class Input(
        val counts: List<Int>,
        val durationSeconds: Long,
        val calibration: EnergyCalibration,
        /** Display name for refusal messages («снимок 2», label…). */
        val name: String,
    )

    sealed interface Outcome {
        data class Ok(
            val counts: List<Int>,
            /** Sum of the inputs' accumulation times, seconds. */
            val durationSeconds: Long,
            /** Grid of the longest input (dominant statistics). */
            val calibration: EnergyCalibration,
        ) : Outcome

        data class Invalid(val reason: String) : Outcome
    }

    fun merge(inputs: List<Input>, s: SpectrumStrings = SpectrumRu): Outcome {
        if (inputs.size < 2) {
            return Outcome.Invalid(s.mergeNeedsTwo)
        }
        val base = inputs.maxBy { it.durationSeconds }
        for (input in inputs) {
            if (input.counts.size != base.counts.size) {
                return Outcome.Invalid(
                    s.mergeChannelMismatch(input.counts.size, base.counts.size),
                )
            }
            val delta = SpectrumCompare.calibrationDeltaKeV(
                input.calibration,
                base.calibration,
                base.counts.size,
            )
            if (delta > SpectrumCompare.CALIBRATION_TOLERANCE_KEV) {
                return Outcome.Invalid(
                    s.mergeCalibrationMismatch(
                        input.name,
                        base.name,
                        "%.1f".format(delta),
                    ),
                )
            }
        }
        val counts = IntArray(base.counts.size)
        var duration = 0L
        for (input in inputs) {
            for (i in counts.indices) counts[i] += input.counts[i]
            duration += input.durationSeconds
        }
        return Outcome.Ok(
            counts = counts.toList(),
            durationSeconds = duration,
            calibration = base.calibration,
        )
    }
}
