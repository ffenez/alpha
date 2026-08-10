package app.radiacode.ui.logic

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One SQL-aggregated slice of raw 1 Hz samples («под-корзина»), already in
 * µSv/h. The database groups by `timestamp / subBucketMillis`; everything the
 * chart shows is folded from these — the screen never reads individual rows,
 * which is what keeps a 30-day window bounded (see [DoseChartModel]).
 *
 * [sumMicroSvH] and [sumSqMicroSvH] carry Σx and Σx² of the **raw** samples,
 * so the pooled mean and SD of any group of sub-buckets are exact, not an
 * average of averages.
 */
data class DoseAggregate(
    val startMillis: Long,
    val minMicroSvH: Float,
    val maxMicroSvH: Float,
    val sumMicroSvH: Double,
    val sumSqMicroSvH: Double,
    val sampleCount: Int,
) {
    /** Mean dose rate of the sub-bucket, µSv/h. */
    val meanMicroSvH: Float
        get() = if (sampleCount > 0) (sumMicroSvH / sampleCount).toFloat() else 0f

    /**
     * True when the sub-bucket carries a single raw value: one sample, or
     * several samples that were all equal (`MIN == MAX`). Then its mean **is**
     * that raw value, and quantiles built from (value, weight) pairs are the
     * exact quantiles of the raw samples — see [DoseChartModel.QuantileSource].
     */
    val singleValued: Boolean
        get() = sampleCount <= 1 || minMicroSvH == maxMicroSvH
}

/**
 * One drawn column of the chart. Every number here has one exact meaning and
 * the chart draws each of them differently (CHART SPEC §4, §6, §7):
 *  - [median] (= Q50) — the **line**: robust level of the column;
 *  - [q25]–[q75] — the **inner envelope**: the observed robust spread of the
 *    measurements inside the column;
 *  - [q10]–[q90] — the **outer envelope**, same nature, wider;
 *  - [min]/[max] with [minAtMillis]/[maxAtMillis] — the **extrema**: kept as
 *    numbers and as discrete markers (see `DoseExtremes`), never painted as a
 *    continuous filled band, because an extremum grows with N and a min–max
 *    fill would read as a confidence interval (§7).
 *
 * The quantile envelopes are **observed spread**, not measurement uncertainty
 * and not a confidence interval (§6). All of it is CALCULATED over MEASURED
 * samples (SPEC §2); the chart legend says so.
 *
 * [quantilesExact] tells which of the two paths of ADR 004 produced the
 * quantiles: exact order statistics of the raw samples, or the approximation
 * over sub-bucket means. See [DoseChartModel.QuantileSource].
 */
data class ChartBucket(
    val startMillis: Long,
    val endMillis: Long,
    val min: Float,
    val max: Float,
    val median: Float,
    val q10: Float = median,
    val q25: Float = median,
    val q75: Float = median,
    val q90: Float = median,
    /** Raw 1 Hz samples inside the column — the honest n. */
    val sampleCount: Int = 1,
    /** Start of the sub-bucket that held [min]. */
    val minAtMillis: Long = startMillis,
    /** Start of the sub-bucket that held [max]. */
    val maxAtMillis: Long = startMillis,
    /**
     * Width of the sub-bucket the extrema timestamps point into — the honest
     * resolution of «когда». 1 s (one raw sample per sub-bucket) means the
     * timestamps are exact to the second; wider means the extremum happened
     * *somewhere inside* that interval and the UI must say so.
     */
    val extremeWindowMillis: Long = 1_000L,
    val quantilesExact: Boolean = true,
) {
    val midMillis: Long get() = (startMillis + endMillis) / 2

    /** Q75 − Q25 — the robust spread the extremum rule leans on. */
    val iqr: Float get() = q75 - q25
}

/** Summary of the visible window (the statgrid, CHART SPEC §13). */
data class WindowStats(
    val min: Float,
    val p10: Float,
    val q25: Float,
    val median: Float,
    val q75: Float,
    val p90: Float,
    val max: Float,
    /** Median absolute deviation, µSv/h — robust spread, no 1.4826 factor. */
    val mad: Float,
    /** Population SD of the raw samples, exact from Σx/Σx², µSv/h. */
    val sd: Float,
    /** Raw 1 Hz samples inside the window. */
    val sampleCount: Int,
    val spanMillis: Long,
    /** Which path produced the percentiles (ADR 004). */
    val quantilesExact: Boolean = true,
) {
    val iqr: Float get() = q75 - q25
}

/**
 * Immutable result of one database read: the loaded range with everything the
 * chart can need inside it. Gestures re-project this value; they never trigger
 * another read (see the loader in `LiveChartScreen`).
 */
data class DoseSnapshot(
    val fromMillis: Long,
    val toMillis: Long,
    val bucketMillis: Long,
    val subBucketMillis: Long,
    /** Drawn columns; empty columns are absent (gaps stay gaps). */
    val buckets: List<ChartBucket>,
    /** Sub-bucket aggregates, kept for the window statistics and histogram. */
    val aggregates: List<DoseAggregate>,
    /** Timestamps of journal events (deviations, hotspots) inside the range. */
    val eventTimesMillis: List<Long>,
) {
    val spanMillis: Long get() = toMillis - fromMillis
}

/**
 * Folding of SQL aggregates into the immutable chart snapshot.
 *
 * **Quantiles and their honest error status (CHART SPEC §6, §28–§32, ADR
 * 004).** Every drawn column carries Q10/Q25/Q50/Q75/Q90. They are computed
 * from the (value, weight) pairs of its sub-buckets — the sub-bucket mean
 * weighted by how many raw samples it covers — with the same nearest-rank
 * definition the baseline engine uses. That gives two regimes:
 *
 *  - **Exact** ([QuantileSource.EXACT_RAW]): when every sub-bucket of the
 *    column holds a single raw value (one sample, or several equal ones), the
 *    pairs *are* the raw samples with their multiplicities, so the result is
 *    the exact order statistic of the raw data (§29). This is the normal case
 *    on short windows, where SQL is asked for 1-second sub-buckets.
 *  - **Approximate** ([QuantileSource.SUB_BUCKET_MEANS]): on long windows a
 *    sub-bucket averages many seconds, so the quantiles are those of the
 *    *sub-bucket means*, not of the raw samples. Averaging shrinks the spread,
 *    therefore Q10 is biased **up** and Q90 **down**; the median is the least
 *    affected. This is an approximation with **no proven error bound** — it is
 *    not the mergeable sketch §30 asks for, and it is deliberately marked as
 *    approximate in the UI ([WindowStats.quantilesExact], the truth line of
 *    the chart) until the P1 KLL hierarchy of ADR 004 lands. It is *not* the
 *    forbidden «quantiles of quantiles» of §28: no per-sub-bucket quantile is
 *    ever computed, let alone re-quantiled.
 *
 * **Why it cannot get slow.** The number of drawn columns is fixed at
 * [MAX_BUCKETS] whatever the range, and the database is asked for at most
 * [MAX_BUCKETS] × [SUB_BUCKETS_PER_BUCKET] rows, so a 30-day window renders
 * the same geometry as a 15-minute one: the SQL `GROUP BY timestamp / bucket`
 * does the reduction inside SQLite over the timestamp index, and folding here
 * is a single O(rows) pass with no sorting per column beyond its own
 * sub-buckets. Gestures never re-enter this code — they only re-project the
 * snapshot.
 */
object DoseChartModel {

    /** Which path produced a set of quantiles (ADR 004, §29/§32). */
    enum class QuantileSource {
        /** Order statistics of the raw samples themselves. */
        EXACT_RAW,

        /** Order statistics of sub-bucket means — approximate, spread shrunk. */
        SUB_BUCKET_MEANS,
    }

    /** Columns drawn, independent of the range (≈2 px per column on a phone). */
    const val MAX_BUCKETS = 200

    /**
     * Sub-buckets per drawn column — the resolution the quantiles and the
     * extremum timestamps are built from. A median needs few points; Q10/Q90
     * of a column need enough of them that the tails are not defined by two
     * values, hence 30 rather than the 12 the median-only chart used. The
     * query budget stays fixed at [MAX_BUCKETS] × this.
     */
    const val SUB_BUCKETS_PER_BUCKET = 30

    /**
     * Below this column width a column holds ~5 samples or fewer, so the
     * individual measurements are worth drawing as dots — above it the dots
     * would be denser than pixels and would lie about resolution.
     */
    const val RAW_DOTS_MAX_BUCKET_MILLIS = 5_000L

    /** Quantile probabilities carried by every column, ascending. */
    private val QUANTILES = doubleArrayOf(0.10, 0.25, 0.50, 0.75, 0.90)

    /** Column width for a span, ≥1 s (the raw sample period). */
    fun bucketMillis(spanMillis: Long, bucketCount: Int = MAX_BUCKETS): Long =
        (spanMillis / bucketCount.coerceAtLeast(1)).coerceAtLeast(1_000L)

    /** Aggregation width asked of SQL, ≥1 s. */
    fun subBucketMillis(bucketMillis: Long): Long =
        (bucketMillis / SUB_BUCKETS_PER_BUCKET).coerceAtLeast(1_000L)

    /** Columns needed to cover a span, hard-capped so the frame stays bounded. */
    fun bucketCount(spanMillis: Long, bucketMillis: Long): Int {
        if (bucketMillis <= 0L) return 0
        return ((spanMillis / bucketMillis) + 1).toInt().coerceIn(1, MAX_BUCKETS + 2)
    }

    /**
     * One database read → one immutable frame source. Cost is O(rows) with
     * rows ≤ [MAX_BUCKETS] × [SUB_BUCKETS_PER_BUCKET] whatever the range, and
     * the column count never exceeds [MAX_BUCKETS] + 2 — a 30-day window
     * renders exactly as much geometry as a 15-minute one.
     */
    fun snapshot(
        aggregates: List<DoseAggregate>,
        eventTimesMillis: List<Long>,
        alignedFromMillis: Long,
        toMillis: Long,
        bucketMillis: Long,
    ): DoseSnapshot {
        val count = bucketCount(toMillis - alignedFromMillis, bucketMillis)
        val subMillis = subBucketMillis(bucketMillis)
        return DoseSnapshot(
            fromMillis = alignedFromMillis,
            toMillis = toMillis,
            bucketMillis = bucketMillis,
            subBucketMillis = subMillis,
            buckets = fold(aggregates, alignedFromMillis, bucketMillis, count, subMillis),
            aggregates = aggregates,
            eventTimesMillis = eventTimesMillis,
        )
    }

    /** True when columns are short enough that raw dots mean something. */
    fun rawDotsVisible(bucketMillis: Long): Boolean =
        bucketMillis <= RAW_DOTS_MAX_BUCKET_MILLIS

    /**
     * Folds sub-buckets into columns of [bucketMillis] starting at
     * [alignedFromMillis]. Empty columns are dropped, not interpolated — a gap
     * in the data stays a gap on the chart (design-language.md).
     */
    fun fold(
        aggregates: List<DoseAggregate>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        bucketCount: Int,
        subBucketMillis: Long = 1_000L,
    ): List<ChartBucket> {
        if (bucketMillis <= 0L || bucketCount <= 0) return emptyList()
        val slots = arrayOfNulls<MutableList<DoseAggregate>>(bucketCount)
        for (a in aggregates) {
            if (a.sampleCount <= 0) continue
            val index = ((a.startMillis - alignedFromMillis) / bucketMillis).toInt()
            if (index !in 0 until bucketCount) continue
            val list = slots[index] ?: ArrayList<DoseAggregate>(SUB_BUCKETS_PER_BUCKET)
                .also { slots[index] = it }
            list += a
        }
        val out = ArrayList<ChartBucket>(bucketCount)
        for (index in 0 until bucketCount) {
            val list = slots[index] ?: continue
            val start = alignedFromMillis + index * bucketMillis
            out += reduce(list, start, start + bucketMillis, subBucketMillis)
        }
        return out
    }

    private fun reduce(
        parts: List<DoseAggregate>,
        start: Long,
        end: Long,
        subBucketMillis: Long,
    ): ChartBucket {
        var n = 0
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var minAt = start
        var maxAt = start
        var exact = true
        val values = FloatArray(parts.size)
        val weights = IntArray(parts.size)
        parts.forEachIndexed { i, p ->
            n += p.sampleCount
            if (p.minMicroSvH < min) {
                min = p.minMicroSvH
                minAt = p.startMillis
            }
            if (p.maxMicroSvH > max) {
                max = p.maxMicroSvH
                maxAt = p.startMillis
            }
            if (!p.singleValued) exact = false
            values[i] = p.meanMicroSvH
            weights[i] = p.sampleCount
        }
        val q = percentilesOfSorted(packSorted(values, weights), QUANTILES)
        return ChartBucket(
            startMillis = start,
            endMillis = end,
            min = min,
            max = max,
            median = q[2],
            q10 = q[0],
            q25 = q[1],
            q75 = q[3],
            q90 = q[4],
            sampleCount = n,
            minAtMillis = minAt,
            maxAtMillis = maxAt,
            extremeWindowMillis = subBucketMillis,
            quantilesExact = exact,
        )
    }

    /**
     * Statistics of the visible window (CHART SPEC §13). min/max/SD/n are
     * exact over the raw samples (SQL extremes and Σx/Σx²); the percentiles
     * and the MAD follow the two-regime rule documented on this object — exact
     * order statistics of the raw samples on short windows, an approximation
     * over sub-bucket means on long ones, flagged by
     * [WindowStats.quantilesExact]. The UI labels all of it as calculated
     * (SPEC §2).
     */
    fun windowStats(
        aggregates: List<DoseAggregate>,
        fromMillis: Long,
        toMillis: Long,
    ): WindowStats? {
        var n = 0
        var sum = 0.0
        var sumSq = 0.0
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var kept = 0
        var exact = true
        for (a in aggregates) {
            if (a.sampleCount <= 0 || a.startMillis < fromMillis || a.startMillis > toMillis) continue
            kept++
            n += a.sampleCount
            sum += a.sumMicroSvH
            sumSq += a.sumSqMicroSvH
            if (a.minMicroSvH < min) min = a.minMicroSvH
            if (a.maxMicroSvH > max) max = a.maxMicroSvH
            if (!a.singleValued) exact = false
        }
        if (kept == 0 || n == 0) return null
        val values = FloatArray(kept)
        val weights = IntArray(kept)
        var i = 0
        for (a in aggregates) {
            if (a.sampleCount <= 0 || a.startMillis < fromMillis || a.startMillis > toMillis) continue
            values[i] = a.meanMicroSvH
            weights[i] = a.sampleCount
            i++
        }
        val q = percentilesOfSorted(packSorted(values, weights), QUANTILES)
        val mean = (sum / n).toFloat()
        val variance = (sumSq / n) - mean.toDouble() * mean
        return WindowStats(
            min = min,
            p10 = q[0],
            q25 = q[1],
            median = q[2],
            q75 = q[3],
            p90 = q[4],
            max = max,
            mad = weightedMad(values, weights, q[2]),
            sd = sqrt(max(0.0, variance)).toFloat(),
            sampleCount = n,
            spanMillis = toMillis - fromMillis,
            quantilesExact = exact,
        )
    }

    /**
     * Weighted nearest-rank percentile over non-negative values — same
     * definition as the baseline engine (no interpolation, honest order
     * statistic), but on primitive arrays so the gesture path allocates one
     * `LongArray` instead of boxing every sample.
     */
    fun weightedPercentile(values: FloatArray, weights: IntArray, q: Double): Float {
        require(values.size == weights.size) { "values and weights must align" }
        if (values.isEmpty()) return 0f
        return percentilesOfSorted(packSorted(values, weights), doubleArrayOf(q))[0]
    }

    /** Several ascending percentiles in one sort — the chart needs five. */
    fun weightedPercentiles(values: FloatArray, weights: IntArray, qs: DoubleArray): FloatArray {
        require(values.size == weights.size) { "values and weights must align" }
        if (values.isEmpty()) return FloatArray(qs.size)
        return percentilesOfSorted(packSorted(values, weights), qs)
    }

    /**
     * MAD = median(|xᵢ − median|), weighted, **without** the 1.4826 factor:
     * that factor converts MAD into an SD estimate only under normality, which
     * the scientific instruction forbids assuming (same rule as the baseline
     * engine).
     */
    fun weightedMad(values: FloatArray, weights: IntArray, median: Float): Float {
        if (values.isEmpty()) return 0f
        val deviations = FloatArray(values.size) { abs(values[it] - median) }
        return percentilesOfSorted(packSorted(deviations, weights), doubleArrayOf(0.5))[0]
    }

    /**
     * Packs (value, weight) into one sortable `Long` per element: the IEEE-754
     * bits of a non-negative float are monotone as an `Int`, so the primitive
     * dual-pivot sort orders the pairs by value with no comparator and no
     * boxing. Negative dose rates cannot occur (the device reports magnitudes)
     * and are clamped to 0 if they ever do.
     */
    private fun packSorted(values: FloatArray, weights: IntArray): LongArray {
        val packed = LongArray(values.size)
        for (i in values.indices) {
            val bits = java.lang.Float.floatToIntBits(max(0f, values[i]))
            packed[i] = (bits.toLong() shl 32) or (weights[i].toLong() and 0xFFFF_FFFFL)
        }
        Arrays.sort(packed)
        return packed
    }

    /**
     * Nearest-rank percentiles of a packed sorted array in a single pass.
     * [qs] must be ascending; the result is aligned with it.
     */
    private fun percentilesOfSorted(sorted: LongArray, qs: DoubleArray): FloatArray {
        val out = FloatArray(qs.size)
        if (sorted.isEmpty()) return out
        var total = 0L
        for (p in sorted) total += (p and 0xFFFF_FFFFL)
        if (total <= 0L) return out
        var qi = 0
        var cumulative = 0L
        for (p in sorted) {
            cumulative += (p and 0xFFFF_FFFFL)
            val value = java.lang.Float.intBitsToFloat((p ushr 32).toInt())
            while (qi < qs.size && cumulative >= qs[qi] * total) {
                out[qi] = value
                qi++
            }
            if (qi >= qs.size) break
        }
        val last = java.lang.Float.intBitsToFloat((sorted.last() ushr 32).toInt())
        while (qi < qs.size) {
            out[qi] = last
            qi++
        }
        return out
    }
}
