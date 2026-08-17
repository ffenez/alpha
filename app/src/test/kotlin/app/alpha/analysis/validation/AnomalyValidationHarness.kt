package app.alpha.analysis.validation

import app.alpha.analysis.AnomalyStatistics
import app.alpha.analysis.AutocorrelationMethod
import app.alpha.analysis.ExperimentalRadiationStatistics
import kotlin.math.min

/**
 * Validation harness for the candidate current-vs-baseline test (graph spec
 * §36.5–36.7).
 *
 * It answers exactly two questions and refuses to answer any others:
 *
 * 1. **False-positive rate under continuous scanning.** A stationary recording
 *    is scanned the way the app would scan it — a sliding current window
 *    compared with a baseline window that precedes it, re-tested every
 *    [ScanConfig.strideSeconds]. Every rejection of H₀ is a false alarm by
 *    construction, because nothing changed. The output is both the per-test
 *    rate and the rate the user would actually feel: **alarms per day**.
 * 2. **Detection power.** The same scan on a recording with a known injected
 *    step, counting the trials where the test fires inside the response
 *    horizon and how long it took.
 *
 * The harness is the only place allowed to consult [AnomalyStatistics]; the
 * app does not.
 */
@OptIn(ExperimentalRadiationStatistics::class)
object AnomalyValidationHarness {

    /**
     * @param currentSeconds current window, samples at 1 Hz
     * @param baselineSeconds baseline window immediately preceding the current one
     * @param strideSeconds how often the comparison is repeated
     * @param alpha per-test significance the scan would use
     * @param correctForAutocorrelation false = the naive N (what the numbers
     *        look like when the serial correlation is ignored)
     */
    data class ScanConfig(
        val currentSeconds: Int = 300,
        val baselineSeconds: Int = 3_600,
        val strideSeconds: Int = 120,
        val alpha: Double = 0.001,
        val correctForAutocorrelation: Boolean = true,
        val method: AutocorrelationMethod = AutocorrelationMethod.INTEGRATED,
    )

    data class ScanReport(
        val tests: Int,
        val alarms: Int,
        val meanTau: Double,
        val medianP: Double,
    ) {
        val rate: Double get() = if (tests == 0) 0.0 else alarms.toDouble() / tests
    }

    /** [ScanReport] plus what the rate means for a day of standing still. */
    data class FalsePositiveReport(
        val scan: ScanReport,
        val strideSeconds: Int,
    ) {
        val alarmsPerDay: Double get() = scan.rate * (86_400.0 / strideSeconds)
    }

    data class PowerReport(
        val trials: Int,
        val detected: Int,
        /** Median seconds from the step to the first alarm, over detecting trials. */
        val medianDelaySeconds: Double?,
    ) {
        val power: Double get() = if (trials == 0) 0.0 else detected.toDouble() / trials
    }

    /**
     * False-positive rate of continuous scanning over a stationary [series]
     * (1 Hz samples). Windows that would run past the end are not tested.
     */
    fun falsePositiveRate(series: DoubleArray, config: ScanConfig = ScanConfig()): FalsePositiveReport =
        FalsePositiveReport(scan(series, config, fromIndex = 0), config.strideSeconds)

    /**
     * Detection power over independent [trials] of a series with a step of
     * [stepFactor] injected at [stepAtSeconds]. A trial counts as detected
     * when a scan whose current window lies entirely after the step alarms
     * within [horizonSeconds] of it.
     */
    fun detectionPower(
        trials: Int,
        seed: Long,
        stepAtSeconds: Int,
        stepFactor: Double,
        totalSeconds: Int,
        horizonSeconds: Int = 900,
        config: ScanConfig = ScanConfig(),
    ): PowerReport {
        val delays = ArrayList<Double>(trials)
        var detected = 0
        for (trial in 0 until trials) {
            val series = SyntheticSeries.withStep(
                seconds = totalSeconds,
                seed = seed + trial,
                stepAt = stepAtSeconds,
                stepFactor = stepFactor,
            )
            val delay = firstAlarmAfterStep(series, config, stepAtSeconds, horizonSeconds)
            if (delay != null) {
                detected++
                delays += delay.toDouble()
            }
        }
        delays.sort()
        val median = when {
            delays.isEmpty() -> null
            delays.size % 2 == 1 -> delays[delays.size / 2]
            else -> (delays[delays.size / 2 - 1] + delays[delays.size / 2]) / 2.0
        }
        return PowerReport(trials, detected, median)
    }

    /**
     * Scans [series] and returns how often H₀ was rejected. The baseline
     * window is the [ScanConfig.baselineSeconds] immediately before the
     * current window — the app's «профиль» in miniature.
     */
    fun scan(series: DoubleArray, config: ScanConfig, fromIndex: Int): ScanReport {
        var tests = 0
        var alarms = 0
        var tauSum = 0.0
        val pValues = ArrayList<Double>()
        var end = maxOf(fromIndex, config.baselineSeconds + config.currentSeconds)
        while (end <= series.size) {
            val result = testAt(series, config, end)
            if (result != null) {
                tests++
                tauSum += result.tau
                pValues += result.p
                if (result.p < config.alpha) alarms++
            }
            end += config.strideSeconds
        }
        pValues.sort()
        return ScanReport(
            tests = tests,
            alarms = alarms,
            meanTau = if (tests == 0) 0.0 else tauSum / tests,
            medianP = if (pValues.isEmpty()) 1.0 else pValues[pValues.size / 2],
        )
    }

    private data class Point(val p: Double, val tau: Double)

    /** One comparison whose current window ends at [end] (exclusive). */
    private fun testAt(series: DoubleArray, config: ScanConfig, end: Int): Point? {
        val currentFrom = end - config.currentSeconds
        val baselineFrom = currentFrom - config.baselineSeconds
        if (baselineFrom < 0) return null
        val current = series.copyOfRange(currentFrom, end)
        val baseline = series.copyOfRange(baselineFrom, currentFrom)
        return if (config.correctForAutocorrelation) {
            val evidence = AnomalyStatistics.compare(current, baseline, config.method) ?: return null
            Point(evidence.mannWhitney.pValue, evidence.currentAutocorrelation.time)
        } else {
            // The same statistic with naive N: this is the number the test
            // would report if the 1 Hz stream were independent, which it is not.
            Point(AnomalyStatistics.mannWhitney(current, baseline).pValue, 1.0)
        }
    }

    private fun firstAlarmAfterStep(
        series: DoubleArray,
        config: ScanConfig,
        stepAtSeconds: Int,
        horizonSeconds: Int,
    ): Int? {
        // The current window must lie entirely after the step, and the whole
        // comparison must fit inside the horizon.
        var end = stepAtSeconds + config.currentSeconds
        val last = min(series.size, stepAtSeconds + horizonSeconds)
        while (end <= last) {
            val result = testAt(series, config, end)
            if (result != null && result.p < config.alpha) return end - stepAtSeconds
            end += config.strideSeconds
        }
        return null
    }
}
