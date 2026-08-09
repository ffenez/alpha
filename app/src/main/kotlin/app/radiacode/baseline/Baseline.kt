package app.radiacode.baseline

/**
 * Input to the baseline computation: one time-bucketed aggregate of raw 1 Hz
 * samples recorded while a place was active (typically a 1-minute bucket from
 * Room). Values arrive already converted to display-domain units (µSv/h, CPS);
 * raw stored values are never modified (CLAUDE.md invariant).
 */
data class BaselineBucket(
    val avgDoseRateMicroSvH: Float,
    val avgCps: Float,
    /** Measured seconds inside the bucket (= sample count at 1 Hz). */
    val sampleCount: Int,
)

/** Statistical per-place baseline: robust typical bands (see [BaselineComputer]). */
data class Baseline(
    /** Typical dose-rate band, µSv/h: [doseLow] = P10, [doseHigh] = P90. */
    val doseLowMicroSvH: Float,
    val doseMedianMicroSvH: Float,
    val doseHighMicroSvH: Float,
    /** Typical count-rate band, CPS. */
    val cpsLow: Float,
    val cpsMedian: Float,
    val cpsHigh: Float,
    /** Measurement time the baseline is built from, seconds. */
    val accumulatedSeconds: Long,
)

/** Baseline lifecycle for one place. */
sealed interface BaselineState {
    /** Not enough measurement time yet («изучаю обычный фон — N ч из M»). */
    data class Learning(val accumulatedSeconds: Long, val requiredSeconds: Long) : BaselineState

    data class Active(val baseline: Baseline) : BaselineState
}

data class BaselineConfig(
    /** Minimum accumulated measurement time before the baseline activates. */
    val requiredSeconds: Long = 3L * 3600L,
    /** Lower edge of the typical band. */
    val lowPercentile: Double = 0.10,
    /** Upper edge of the typical band. */
    val highPercentile: Double = 0.90,
    /**
     * Buckets with dose rate above `spikeCutoffFactor × weighted median` are
     * treated as deviation periods and excluded (both dose and CPS), so a
     * spike can never become part of "usual".
     */
    val spikeCutoffFactor: Float = 3f,
) {
    companion object {
        val DEFAULT = BaselineConfig()

        /** Sliding window the caller should query buckets over, days. */
        const val WINDOW_DAYS = 14
    }
}

/**
 * Per-place statistical baseline (docs/adr/002-baseline-method.md).
 *
 * Method — weighted robust percentile band over minute buckets:
 *  1. compute the sample-count-weighted median dose rate over all buckets;
 *  2. drop buckets whose dose rate exceeds `spikeCutoffFactor × median`
 *     (deviation periods — a spike never enters the statistics at all);
 *  3. if the retained measurement time is below [BaselineConfig.requiredSeconds],
 *     stay in [BaselineState.Learning];
 *  4. otherwise the typical band is the weighted P10–P90 (with P50 as the
 *     center) of dose rate and CPS over the retained buckets.
 *
 * Spike resistance is double: the cutoff removes gross excursions entirely,
 * and percentiles are order statistics — a residual excursion shifts P90 only
 * after it occupies more than 10 % of the accumulated time (≥ ~18 min against
 * the 3 h minimum), which is a dwell requirement, not an average drift.
 */
object BaselineComputer {

    fun compute(
        buckets: List<BaselineBucket>,
        config: BaselineConfig = BaselineConfig.DEFAULT,
    ): BaselineState {
        val measured = buckets.filter { it.sampleCount > 0 }
        if (measured.isEmpty()) return BaselineState.Learning(0, config.requiredSeconds)

        val medianDose = weightedPercentile(
            values = measured.map { it.avgDoseRateMicroSvH },
            weights = measured.map { it.sampleCount },
            q = 0.5,
        )
        val retained = if (medianDose > 0f) {
            measured.filter { it.avgDoseRateMicroSvH <= config.spikeCutoffFactor * medianDose }
        } else {
            measured
        }

        val accumulated = retained.sumOf { it.sampleCount.toLong() }
        if (accumulated < config.requiredSeconds) {
            return BaselineState.Learning(accumulated, config.requiredSeconds)
        }

        val weights = retained.map { it.sampleCount }
        val doses = retained.map { it.avgDoseRateMicroSvH }
        val cps = retained.map { it.avgCps }
        return BaselineState.Active(
            Baseline(
                doseLowMicroSvH = weightedPercentile(doses, weights, config.lowPercentile),
                doseMedianMicroSvH = weightedPercentile(doses, weights, 0.5),
                doseHighMicroSvH = weightedPercentile(doses, weights, config.highPercentile),
                cpsLow = weightedPercentile(cps, weights, config.lowPercentile),
                cpsMedian = weightedPercentile(cps, weights, 0.5),
                cpsHigh = weightedPercentile(cps, weights, config.highPercentile),
                accumulatedSeconds = accumulated,
            ),
        )
    }

    /**
     * Weighted nearest-rank percentile: sort by value, walk the cumulative
     * weight, return the first value whose cumulative weight reaches
     * `q × total`. With equal weights this degenerates to the classic
     * nearest-rank percentile; no interpolation (honest order statistic).
     */
    fun weightedPercentile(values: List<Float>, weights: List<Int>, q: Double): Float {
        require(values.size == weights.size) { "values and weights must align" }
        require(q in 0.0..1.0) { "q must be within 0..1" }
        val sorted = values.zip(weights).sortedBy { it.first }
        val total = sorted.sumOf { it.second.toLong() }
        if (total <= 0L) return 0f
        val target = q * total
        var cumulative = 0L
        for ((value, weight) in sorted) {
            cumulative += weight
            if (cumulative >= target) return value
        }
        return sorted.last().first
    }
}
