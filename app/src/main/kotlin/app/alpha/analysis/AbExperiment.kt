package app.alpha.analysis

import app.alpha.ui.text.ExperimentRu
import app.alpha.ui.text.ExperimentStrings

/**
 * Assembly layer over [AbAnalysis]: turns two (or more) recorded runs into the
 * bundle the A/B screen shows and the report exports — dose rate, total CPS,
 * energy windows and the full spectrum, each with the statistic that produced
 * its verdict (spec §9).
 *
 * Kept free of Room and Compose types so both the screen and the exporter use
 * exactly the same numbers, and so the whole thing is JVM-testable.
 */
object AbExperiment {

    /** One recorded run as the analysis sees it. */
    data class RunData(
        val id: Long,
        val label: String,
        val startedAt: Long,
        val endedAt: Long?,
        /** Live time of the run, seconds (wall bracket of the recording). */
        val durationSeconds: Long,
        /** Spectrum accumulated during the run; null when none was captured. */
        val counts: List<Int>? = null,
        val calibration: EnergyCalibration? = null,
        val doseStats: AbAnalysis.DoseStats? = null,
        val distanceCm: Float? = null,
        val shieldingNote: String? = null,
    ) {
        val totalCounts: Long get() = counts?.sumOf { it.toLong() } ?: 0L
        val hasSpectrum: Boolean get() = counts != null && durationSeconds > 0
    }

    /** Everything the screen and the report say about one A-vs-B pair. */
    data class Comparison(
        val a: RunData,
        val b: RunData,
        /** Total counts of the two spectra as a counting comparison. */
        val totalCounts: AbAnalysis.Comparison?,
        val windows: List<AbAnalysis.Comparison>,
        val spectrum: AbAnalysis.SpectrumComparison?,
        /** Advisory: means of correlated 1 Hz dose-rate readings. */
        val doseRate: AbAnalysis.MeanComparison?,
        /** Max |E_a(ch) − E_b(ch)| between the two calibrations, keV. */
        val calibrationDeltaKeV: Float?,
        val warnings: List<String>,
        /**
         * The strongest verdict among the *counting* comparisons (total counts,
         * windows, full spectrum). The dose-rate row is deliberately excluded:
         * its uncertainty is a lower bound (see [AbAnalysis.compareDoseRates]),
         * so letting it drive the headline would overstate the evidence.
         */
        val verdict: AbAnalysis.Verdict,
    )

    fun compare(
        a: RunData,
        b: RunData,
        windowSpecs: List<EnergyWindowSpec> = EnergyWindows.DEFAULTS,
        s: ExperimentStrings = ExperimentRu,
    ): Comparison {
        val warnings = mutableListOf<String>()
        val doseRate = AbAnalysis.compareDoseRates(a.doseStats, b.doseStats)
        if (doseRate == null) {
            warnings += s.warnDoseMissing
        }

        val aCounts = a.counts
        val bCounts = b.counts
        if (aCounts == null || bCounts == null || !a.hasSpectrum || !b.hasSpectrum) {
            warnings += s.warnSpectrumMissing
            return Comparison(
                a = a,
                b = b,
                totalCounts = null,
                windows = emptyList(),
                spectrum = null,
                doseRate = doseRate,
                calibrationDeltaKeV = null,
                warnings = warnings,
                verdict = AbAnalysis.Verdict.CONSISTENT,
            )
        }

        val total = AbAnalysis.compareCounts(
            label = s.totalCountLabel,
            a = AbAnalysis.Counting(a.totalCounts.toDouble(), a.durationSeconds.toDouble()),
            b = AbAnalysis.Counting(b.totalCounts.toDouble(), b.durationSeconds.toDouble()),
        )

        val aCalibration = a.calibration
        val bCalibration = b.calibration
        var delta: Float? = null
        var windows: List<AbAnalysis.Comparison> = emptyList()
        var spectrum: AbAnalysis.SpectrumComparison? = null
        if (aCalibration != null && bCalibration != null && aCounts.size == bCounts.size) {
            delta = SpectrumCompare.calibrationDeltaKeV(aCalibration, bCalibration, aCounts.size)
            if (delta > SpectrumCompare.CALIBRATION_TOLERANCE_KEV) {
                // Rebinning would make fractional, non-Poisson counts; refusing
                // is the honest answer (same rule as SpectrumMerge).
                warnings += s.warnCalibrationApart("%.1f".format(delta))
            } else {
                windows = AbAnalysis.compareWindows(
                    aCounts = aCounts,
                    aSeconds = a.durationSeconds,
                    aCalibration = aCalibration,
                    bCounts = bCounts,
                    bSeconds = b.durationSeconds,
                    bCalibration = bCalibration,
                    specs = windowSpecs,
                )
                spectrum = AbAnalysis.compareSpectra(
                    aCounts = aCounts,
                    aSeconds = a.durationSeconds,
                    bCounts = bCounts,
                    bSeconds = b.durationSeconds,
                )
            }
        } else if (aCounts.size != bCounts.size) {
            warnings += s.warnChannelCount
        }

        val countingVerdicts = buildList {
            total?.let { add(it.verdict) }
            windows.forEach { add(it.verdict) }
            spectrum?.let { add(it.verdict) }
        }
        return Comparison(
            a = a,
            b = b,
            totalCounts = total,
            windows = windows,
            spectrum = spectrum,
            doseRate = doseRate,
            calibrationDeltaKeV = delta,
            warnings = warnings,
            verdict = strongest(countingVerdicts),
        )
    }

    fun strongest(verdicts: List<AbAnalysis.Verdict>): AbAnalysis.Verdict = when {
        verdicts.any { it == AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE } ->
            AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE
        verdicts.any { it == AbAnalysis.Verdict.CHANGED } -> AbAnalysis.Verdict.CHANGED
        else -> AbAnalysis.Verdict.CONSISTENT
    }

    // --- distance scenario (spec §16) ---

    /**
     * One point of a distance series: the net count rate above the background
     * run, and — *for comparison only* — what an idealised point source would
     * give at this distance if it gave [reference] at its distance.
     */
    data class DistancePoint(
        val run: RunData,
        val distanceCm: Float,
        val netRateCps: Double,
        val sigmaCps: Double,
        /** 1/r² prediction from the nearest measured point; null for that point. */
        val inverseSquareCps: Double?,
    )

    /**
     * Distance series ordered by distance. [background] (if given) is
     * subtracted from every run with time scaling — the background is exactly
     * what makes a real series flatten out at large distances, which is why
     * the 1/r² overlay must never be presented as a validation.
     */
    fun distanceSeries(runs: List<RunData>, background: RunData? = null): List<DistancePoint> {
        val measured = runs
            .filter { it.distanceCm != null && it.distanceCm > 0f && it.durationSeconds > 0 }
            .sortedBy { it.distanceCm }
        if (measured.isEmpty()) return emptyList()

        val netRates = measured.map { run ->
            val gross = AbAnalysis.Counting(run.totalCounts.toDouble(), run.durationSeconds.toDouble())
            if (background != null && background.durationSeconds > 0) {
                AbAnalysis.netRate(
                    gross,
                    AbAnalysis.Counting(
                        background.totalCounts.toDouble(),
                        background.durationSeconds.toDouble(),
                    ),
                ) ?: (gross.rateCps to 0.0)
            } else {
                gross.rateCps to (
                    if (run.durationSeconds > 0) {
                        Math.sqrt(run.totalCounts.toDouble()) / run.durationSeconds
                    } else {
                        0.0
                    }
                    )
            }
        }
        val referenceDistance = measured.first().distanceCm!!.toDouble()
        val referenceRate = netRates.first().first
        return measured.mapIndexed { index, run ->
            val distance = run.distanceCm!!.toDouble()
            DistancePoint(
                run = run,
                distanceCm = run.distanceCm,
                netRateCps = netRates[index].first,
                sigmaCps = netRates[index].second,
                inverseSquareCps = if (index == 0) {
                    null
                } else {
                    AbAnalysis.inverseSquarePrediction(referenceRate, referenceDistance, distance)
                },
            )
        }
    }
}
