package app.radiacode.ui.logic

import app.radiacode.analysis.AlgorithmVersions
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Which rule produced the bin width — the histogram says how it was built. */
enum class BinRule {
    /** h = 2·IQR·n^(−1/3), then clamped and snapped (graph spec §14). */
    FREEDMAN_DIACONIS,

    /** IQR = 0: FD gives h = 0, so the bin count falls back to ⌈√n⌉. */
    SQRT_IQR_ZERO,

    /** Every value identical: one bin, no shape to show. */
    DEGENERATE,
}

/**
 * Distribution of the visible window: how much measured time was spent at
 * each dose-rate level. This is the answer the time series alone cannot give
 * — «был ли выброс редким гостем или это новый уровень».
 *
 * Counts are **raw 1 Hz samples** per bin, i.e. measured seconds at the
 * device's nominal cadence, not columns — a bin's height is honest measurement
 * time. Values are the sub-bucket means of the window; at short windows a
 * sub-bucket is one second, so the bins then hold the raw samples themselves.
 * [DoseHistograms.COUNT_AXIS_LABEL] is the exact wording of what is counted
 * (graph spec §14, §39).
 */
data class DoseHistogram(
    /** Left edge of bin 0, µSv/h. */
    val lowEdge: Float,
    val binWidth: Float,
    /** Measured samples per bin. */
    val counts: IntArray,
    /** Bins overlapping the baseline P10–P90 band, or null when no baseline. */
    val baselineBins: IntRange?,
    /** First bin containing values at or above the alarm level, or null. */
    val firstAlarmBin: Int?,
    /**
     * Independent observed values behind the shape — the number of sub-buckets,
     * i.e. the honest n of the order statistics the bin width was derived from
     * (the same convention the baseline engine uses for its bucket count).
     */
    val observations: Int = 0,
    /** How [binWidth] was chosen. */
    val rule: BinRule = BinRule.FREEDMAN_DIACONIS,
) {
    val binCount: Int get() = counts.size
    val maxCount: Int get() = counts.maxOrNull() ?: 0
    val totalCount: Int get() = counts.sum()

    fun binLow(index: Int): Float = lowEdge + index * binWidth
    fun binHigh(index: Int): Float = lowEdge + (index + 1) * binWidth

    /** Value → bin, clamped into range. */
    fun binOf(value: Float): Int =
        (floor(((value - lowEdge) / binWidth).toDouble()).toInt()).coerceIn(0, counts.size - 1)

    // IntArray needs manual equality for a data class used as Compose state.
    override fun equals(other: Any?): Boolean =
        other is DoseHistogram &&
            other.lowEdge == lowEdge &&
            other.binWidth == binWidth &&
            other.counts.contentEquals(counts) &&
            other.baselineBins == baselineBins &&
            other.firstAlarmBin == firstAlarmBin &&
            other.observations == observations &&
            other.rule == rule

    override fun hashCode(): Int {
        var h = lowEdge.hashCode()
        h = 31 * h + binWidth.hashCode()
        h = 31 * h + counts.contentHashCode()
        h = 31 * h + (baselineBins?.hashCode() ?: 0)
        h = 31 * h + (firstAlarmBin ?: -1)
        h = 31 * h + observations
        h = 31 * h + rule.ordinal
        return h
    }
}

/**
 * What the distribution strip should show. A histogram that cannot be built
 * honestly is a *state with a reason*, never a finely binned picture of four
 * measurements (graph spec §14: fallbacks are mandatory).
 */
sealed interface DistributionState {
    /** No measurements inside the window at all. */
    data object NoData : DistributionState

    /**
     * Measurements exist but too few independent values to shape a
     * distribution — the UI says [DoseHistograms.INSUFFICIENT_TEXT] and shows
     * nothing else.
     */
    data class Insufficient(val observations: Int, val required: Int) : DistributionState

    data class Ready(val histogram: DoseHistogram) : DistributionState
}

/**
 * Binning of the visible window (graph spec §14).
 *
 * ## Scientific release gate (spec §24 / graph spec §41)
 *
 * 1. **Formula.** Freedman–Diaconis bin width h = 2·IQR(x)·n^(−1/3) with
 *    IQR = Q75 − Q25 of the binned values; candidate bin count = ⌈range/h⌉,
 *    clamped to [[MIN_BINS], [MAX_BINS]], then the width is snapped to the
 *    readable 1/2/5·10^k family and the left edge is put on that grid.
 * 2. **Assumptions.** (a) FD is a *heuristic*, not a law — it uses the IQR and
 *    is therefore reasonably outlier-tolerant, and it does **not** require
 *    normality (graph spec §14 says so explicitly); (b) n is the number of
 *    independent observed values (sub-buckets), not the number of raw samples:
 *    at long windows one sub-bucket is a mean of many correlated seconds and
 *    counting them as independent would pretend to a resolution the data does
 *    not have; (c) the clamp and the snapping are **product UI heuristics**,
 *    chosen for readability on a phone, and are documented as such — no
 *    scientific claim rests on them.
 * 3. **Units.** x axis µSv/h; y axis counts of raw 1 Hz samples, i.e. measured
 *    seconds at the device's nominal cadence ([COUNT_AXIS_LABEL]).
 * 4. **Reference.** Freedman, D. & Diaconis, P. (1981), *On the histogram as a
 *    density estimator: L₂ theory*, Z. Wahrscheinlichkeitstheorie verw. Gebiete
 *    57, 453–476. Graph spec §14 for the clamp/fallback requirement.
 * 5. **Validation data.** Deterministic synthetic windows in
 *    `DoseHistogramTest` (known IQR, IQR = 0, degenerate, small N, clamping).
 *    No RC-110 recording is required — the rule is arithmetic on the window.
 * 6. **Limitations.** A histogram of a *time-weighted* mixture is not a
 *    probability density of the physical process: the app never normalises it
 *    to one, and bimodality is not interpreted as a cause (graph spec §15).
 *    Sub-bucket means at long windows smooth the tails, so an extreme second
 *    inside a 20-minute sub-bucket will not appear as its own bin — the
 *    chart's min/max envelope and episode markers, not this strip, are what
 *    keep transients discoverable (graph spec §21).
 * 7. **Tests.** `app/src/test/.../ui/logic/DoseHistogramTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.DOSE_HISTOGRAM].
 * 9. **User-facing meaning.** «Сколько измеренного времени окно провело на
 *    каждом уровне мощности дозы». Below [MIN_OBSERVATIONS] independent values
 *    the honest answer is [INSUFFICIENT_TEXT] — not a prettier histogram.
 *
 * Pure JVM; no Android dependencies.
 */
object DoseHistograms {

    const val ALGORITHM_VERSION = AlgorithmVersions.DOSE_HISTOGRAM

    /**
     * Readable-range clamp. **Product UI heuristic, not a scientific
     * constant** (graph spec §14): fewer than 8 bars stops being a shape and
     * more than 40 is thinner than the strip's pixels on a phone.
     */
    const val MIN_BINS = 8

    /** @see MIN_BINS */
    const val MAX_BINS = 40

    /**
     * Fewer independent values than this and no histogram is drawn at all.
     *
     * **Engineering parameter.** Rationale: FD rests on the IQR, and a
     * quartile of a dozen points is dominated by which point happened to land
     * where; 20 keeps each quartile backed by at least five values. Showing a
     * 40-bin picture of six measurements would invent structure.
     */
    const val MIN_OBSERVATIONS = 20

    /** Exact wording of the y axis (graph spec §39: say what is counted). */
    const val COUNT_AXIS_LABEL = "секунд измерений (1 Гц)"

    const val INSUFFICIENT_TEXT = "недостаточно данных для распределения"

    const val NO_DATA_TEXT = "нет измерений в окне"

    /**
     * Distribution of the sub-buckets whose start falls inside
     * [fromMillis]..[toMillis], or the honest reason there is none.
     */
    fun distribution(
        aggregates: List<ValueAggregate>,
        fromMillis: Long,
        toMillis: Long,
        baseline: ClosedFloatingPointRange<Float>? = null,
        alarmLevel: Float? = null,
    ): DistributionState {
        var observations = 0
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (a in aggregates) {
            if (!inWindow(a, fromMillis, toMillis)) continue
            val v = a.meanMicroSvH
            if (!v.isFinite()) continue
            if (v < min) min = v
            if (v > max) max = v
            observations++
        }
        if (observations == 0) return DistributionState.NoData
        if (observations < MIN_OBSERVATIONS) {
            return DistributionState.Insufficient(observations, MIN_OBSERVATIONS)
        }

        val values = FloatArray(observations)
        val weights = IntArray(observations)
        var i = 0
        for (a in aggregates) {
            if (!inWindow(a, fromMillis, toMillis)) continue
            val v = a.meanMicroSvH
            if (!v.isFinite()) continue
            values[i] = v
            weights[i] = a.sampleCount
            i++
        }

        val range = (max - min).toDouble()
        val degenerate = range <= 0.0
        val iqr = if (degenerate) 0.0 else iqr(values, weights)
        val rule = when {
            degenerate -> BinRule.DEGENERATE
            iqr <= 0.0 -> BinRule.SQRT_IQR_ZERO
            else -> BinRule.FREEDMAN_DIACONIS
        }

        val width: Float
        val lowEdge: Float
        val binCount: Int
        if (degenerate) {
            // Every value identical: one readable bin around it. A ten-bin
            // picture of one level would be a drawing, not a distribution.
            width = degenerateWidth(max)
            lowEdge = (floor((min / width).toDouble()) * width).toFloat()
            binCount = 1
        } else {
            val candidate = when (rule) {
                // FD collapses (h = 0) when the middle half of the data is one
                // value — a very peaked window with a few outliers. The
                // square-root rule depends on n only, so it always answers.
                BinRule.SQRT_IQR_ZERO -> ceil(sqrt(observations.toDouble())).toInt()
                else -> ceil(range / (2.0 * iqr * cbrt(1.0 / observations))).toInt()
            }.coerceIn(MIN_BINS, MAX_BINS)
            var w = niceStepAtMost(range / candidate)
            var edge = floor(min / w) * w
            var count = floor((max - edge) / w).toInt() + 1
            // Snapping down can add bins; the cap is hard, so widen by whole
            // nice steps until the count fits.
            while (count > MAX_BINS) {
                w = nextNiceStep(w)
                edge = floor(min / w) * w
                count = floor((max - edge) / w).toInt() + 1
            }
            width = w.toFloat()
            lowEdge = edge.toFloat()
            binCount = count.coerceAtLeast(1)
        }

        val counts = IntArray(binCount)
        for (a in aggregates) {
            if (!inWindow(a, fromMillis, toMillis)) continue
            val v = a.meanMicroSvH
            if (!v.isFinite()) continue
            val index = floor(((v - lowEdge) / width).toDouble()).toInt().coerceIn(0, binCount - 1)
            counts[index] += a.sampleCount
        }

        val baselineBins = baseline?.let {
            val lo = ((it.start - lowEdge) / width).toInt().coerceIn(0, binCount - 1)
            val hi = ((it.endInclusive - lowEdge) / width).toInt().coerceIn(0, binCount - 1)
            if (it.endInclusive < lowEdge || it.start > lowEdge + binCount * width) null
            else lo..hi
        }
        // A bin is «hot» when it can contain values at or above the alarm
        // level, i.e. its upper edge exceeds it — never colour a bin crit
        // that only holds values below the threshold.
        val firstAlarmBin = alarmLevel
            ?.takeIf { it > 0f }
            ?.let { level -> (0 until binCount).firstOrNull { lowEdge + (it + 1) * width > level } }

        return DistributionState.Ready(
            DoseHistogram(
                lowEdge = lowEdge,
                binWidth = width,
                counts = counts,
                baselineBins = baselineBins,
                firstAlarmBin = firstAlarmBin,
                observations = observations,
                rule = rule,
            ),
        )
    }

    /**
     * Histogram or null.
     *
     * Kept so the current chart screen compiles unchanged; it cannot express
     * the difference between «нет измерений» and «недостаточно данных», which
     * graph spec §14 requires the UI to say out loud.
     */
    @Deprecated(
        "Use distribution(): null hides the reason there is no histogram. " +
            "The P0/P1 chart integration switches the call site in LiveChartScreen.",
        ReplaceWith("distribution(aggregates, fromMillis, toMillis, baseline, alarmLevel)"),
    )
    fun build(
        aggregates: List<ValueAggregate>,
        fromMillis: Long,
        toMillis: Long,
        baseline: ClosedFloatingPointRange<Float>? = null,
        alarmLevel: Float? = null,
    ): DoseHistogram? =
        (distribution(aggregates, fromMillis, toMillis, baseline, alarmLevel)
            as? DistributionState.Ready)?.histogram

    /** Evenly spaced bin-edge values for the strip's axis labels. */
    fun labelValues(histogram: DoseHistogram, count: Int = 4): List<Pair<Float, Float>> {
        if (count <= 0 || histogram.binCount == 0) return emptyList()
        return (0 until count).map { i ->
            val fraction = (i + 0.5f) / count
            fraction to (histogram.lowEdge + fraction * histogram.binCount * histogram.binWidth)
        }
    }

    private fun inWindow(a: ValueAggregate, fromMillis: Long, toMillis: Long): Boolean =
        a.sampleCount > 0 && a.startMillis >= fromMillis && a.startMillis <= toMillis

    /** IQR = Q75 − Q25, weighted by measured samples (nearest-rank, no interpolation). */
    private fun iqr(values: FloatArray, weights: IntArray): Double {
        val order = values.indices.sortedBy { values[it] }
        var total = 0L
        for (w in weights) total += w
        if (total <= 0L) return 0.0
        fun quantile(q: Double): Double {
            val target = q * total
            var cumulative = 0L
            for (index in order) {
                cumulative += weights[index]
                if (cumulative >= target) return values[index].toDouble()
            }
            return values[order.last()].toDouble()
        }
        return (quantile(0.75) - quantile(0.25)).coerceAtLeast(0.0)
    }

    /**
     * One-bin width for an all-equal window: a tenth of the level, snapped to
     * the readable family, with a floor so a zero reading still gets a bin.
     */
    private fun degenerateWidth(value: Float): Float {
        val raw = abs(value) * 0.1
        return if (raw <= 1e-4) 0.01f else niceStepAtMost(raw).toFloat()
    }

    /** Largest 1/2/5·10^k step not exceeding [raw]. */
    private fun niceStepAtMost(raw: Double): Double {
        if (!raw.isFinite() || raw <= 0.0) return 0.01
        val mag = Math.pow(10.0, floor(Math.log10(raw)))
        val norm = raw / mag
        val nice = when {
            norm >= 5.0 -> 5.0
            norm >= 2.0 -> 2.0
            else -> 1.0
        }
        return nice * mag
    }

    /** Next step up in the 1/2/5·10^k family. */
    private fun nextNiceStep(step: Double): Double {
        val mag = Math.pow(10.0, floor(Math.log10(step)))
        val norm = step / mag
        return when {
            norm < 1.5 -> 2.0 * mag
            norm < 3.5 -> 5.0 * mag
            else -> 10.0 * mag
        }
    }
}
