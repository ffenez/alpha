package app.radiacode.ui.logic

import java.util.Arrays
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One SQL-aggregated slice of raw 1 Hz samples («под-корзина»), already in
 * µSv/h. The database groups by `timestamp / subBucketMillis`; everything the
 * chart shows is folded from these — the screen never reads individual rows,
 * which is what keeps a 30-day window bounded (see [DoseChartModel]).
 *
 * [sumMicroSvH] and [sumSqMicroSvH] carry Σx and Σx² of the **raw** samples,
 * so the pooled mean and σ of any group of sub-buckets are exact, not an
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
}

/**
 * One drawn column of the chart. The three layers the chart paints come from
 * here and each is a different statement about the data:
 *  - [median] — the **line**: robust level of the column (weighted median of
 *    its sub-bucket means; when a sub-bucket is one second long this is the
 *    median of the raw samples themselves);
 *  - [min]/[max] — the **light envelope**: the true extremes measured inside
 *    the column, taken from SQL MIN/MAX. Nothing is hidden by smoothing;
 *  - [mean] ± [sigma] — the **dense band**: pooled mean and population σ of
 *    the raw samples of the column, exact from Σx/Σx².
 *
 * All three are CALCULATED values over MEASURED samples (SPEC §2); the chart
 * legend says so.
 */
data class ChartBucket(
    val startMillis: Long,
    val endMillis: Long,
    val min: Float,
    val max: Float,
    val median: Float,
    val mean: Float,
    val sigma: Float,
    /** Raw 1 Hz samples inside the column — the honest n. */
    val sampleCount: Int,
) {
    val midMillis: Long get() = (startMillis + endMillis) / 2
}

/** Summary of the visible window (the two statgrid rows). */
data class WindowStats(
    val min: Float,
    val p10: Float,
    val median: Float,
    val p90: Float,
    val max: Float,
    /** Population σ of the raw samples, exact from Σx/Σx². */
    val sigma: Float,
    /** Raw 1 Hz samples inside the window. */
    val sampleCount: Int,
    val spanMillis: Long,
)

/** A stretch of the window that sat above the alarm level. */
data class DoseEpisode(
    val fromMillis: Long,
    val toMillis: Long,
    /** Peak bucket max inside the episode, µSv/h. */
    val peak: Float,
) {
    val durationMillis: Long get() = toMillis - fromMillis
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
 * **Why it cannot get slow.** The number of drawn columns is fixed at
 * [MAX_BUCKETS] whatever the range, and the database is asked for at most
 * [MAX_BUCKETS] × [SUB_BUCKETS_PER_BUCKET] rows, so a 30-day window costs the
 * same as a 15-minute one: the SQL `GROUP BY timestamp / bucket` does the
 * reduction inside SQLite over the timestamp index, and folding here is a
 * single O(rows) pass with no sorting per column beyond its own sub-buckets.
 * Gestures never re-enter this code — they only re-project the snapshot.
 */
object DoseChartModel {

    /** Columns drawn, independent of the range (≈2 px per column on a phone). */
    const val MAX_BUCKETS = 200

    /** Sub-buckets per drawn column: the resolution the median is built from. */
    const val SUB_BUCKETS_PER_BUCKET = 12

    /**
     * Below this column width a column holds ~5 samples or fewer, so the
     * individual measurements are worth drawing as dots — above it the dots
     * would be denser than pixels and would lie about resolution.
     */
    const val RAW_DOTS_MAX_BUCKET_MILLIS = 5_000L

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
        return DoseSnapshot(
            fromMillis = alignedFromMillis,
            toMillis = toMillis,
            bucketMillis = bucketMillis,
            subBucketMillis = subBucketMillis(bucketMillis),
            buckets = fold(aggregates, alignedFromMillis, bucketMillis, count),
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
            out += reduce(list, start, start + bucketMillis)
        }
        return out
    }

    private fun reduce(parts: List<DoseAggregate>, start: Long, end: Long): ChartBucket {
        var n = 0
        var sum = 0.0
        var sumSq = 0.0
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        val values = FloatArray(parts.size)
        val weights = IntArray(parts.size)
        parts.forEachIndexed { i, p ->
            n += p.sampleCount
            sum += p.sumMicroSvH
            sumSq += p.sumSqMicroSvH
            if (p.minMicroSvH < min) min = p.minMicroSvH
            if (p.maxMicroSvH > max) max = p.maxMicroSvH
            values[i] = p.meanMicroSvH
            weights[i] = p.sampleCount
        }
        val mean = if (n > 0) (sum / n).toFloat() else 0f
        val variance = if (n > 0) (sumSq / n) - mean.toDouble() * mean else 0.0
        return ChartBucket(
            startMillis = start,
            endMillis = end,
            min = min,
            max = max,
            median = weightedPercentile(values, weights, 0.5),
            mean = mean,
            sigma = sqrt(max(0.0, variance)).toFloat(),
            sampleCount = n,
        )
    }

    /**
     * Statistics of the visible window. min/max/σ/n are exact over the raw
     * samples (SQL extremes and Σx/Σx²); the percentiles are order statistics
     * of the sub-bucket means weighted by sample count — at short windows a
     * sub-bucket is one second, so they are then percentiles of the raw
     * samples themselves. The UI labels these as calculated (SPEC §2, §4.1:
     * quantiles preferred over mean for a background distribution).
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
        for (a in aggregates) {
            if (a.sampleCount <= 0 || a.startMillis < fromMillis || a.startMillis > toMillis) continue
            kept++
            n += a.sampleCount
            sum += a.sumMicroSvH
            sumSq += a.sumSqMicroSvH
            if (a.minMicroSvH < min) min = a.minMicroSvH
            if (a.maxMicroSvH > max) max = a.maxMicroSvH
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
        val sorted = packSorted(values, weights)
        val mean = (sum / n).toFloat()
        val variance = (sumSq / n) - mean.toDouble() * mean
        return WindowStats(
            min = min,
            p10 = percentileOfSorted(sorted, 0.10),
            median = percentileOfSorted(sorted, 0.50),
            p90 = percentileOfSorted(sorted, 0.90),
            max = max,
            sigma = sqrt(max(0.0, variance)).toFloat(),
            sampleCount = n,
            spanMillis = toMillis - fromMillis,
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
        return percentileOfSorted(packSorted(values, weights), q)
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

    private fun percentileOfSorted(sorted: LongArray, q: Double): Float {
        if (sorted.isEmpty()) return 0f
        var total = 0L
        for (p in sorted) total += (p and 0xFFFF_FFFFL)
        if (total <= 0L) return 0f
        val target = q * total
        var cumulative = 0L
        for (p in sorted) {
            cumulative += (p and 0xFFFF_FFFFL)
            if (cumulative >= target) return java.lang.Float.intBitsToFloat((p ushr 32).toInt())
        }
        return java.lang.Float.intBitsToFloat((sorted.last() ushr 32).toInt())
    }
}

/**
 * Deviation episodes drawn as amber vertical bands.
 *
 * The **anchor is the journal**: every band starts from a recorded event of
 * the `events` table (a confirmed persistent deviation or a track hotspot).
 * Its extent is then CALCULATED by walking the visible columns outward while
 * they stay above the alarm level, because the journal stores one row per
 * episode and not its end. The label therefore states a computed duration
 * over measured columns, and a single event with no column above the level
 * still gets a one-column band rather than disappearing.
 */
object DoseEpisodes {

    fun around(
        buckets: List<ChartBucket>,
        eventTimesMillis: List<Long>,
        thresholdMicroSvH: Float,
    ): List<DoseEpisode> {
        if (buckets.isEmpty() || eventTimesMillis.isEmpty()) return emptyList()
        val above = BooleanArray(buckets.size) { buckets[it].max >= thresholdMicroSvH }
        val result = ArrayList<DoseEpisode>()
        for (time in eventTimesMillis.sorted()) {
            val anchor = indexAt(buckets, time) ?: continue
            if (result.any { time in it.fromMillis..it.toMillis }) continue
            var lo = anchor
            var hi = anchor
            if (above[anchor]) {
                while (lo > 0 && above[lo - 1]) lo--
                while (hi < buckets.size - 1 && above[hi + 1]) hi++
            }
            var peak = 0f
            for (i in lo..hi) peak = max(peak, buckets[i].max)
            result += DoseEpisode(buckets[lo].startMillis, buckets[hi].endMillis, peak)
        }
        return result
    }

    /** Index of the column containing [timeMillis], or null when outside. */
    fun indexAt(buckets: List<ChartBucket>, timeMillis: Long): Int? {
        if (buckets.isEmpty()) return null
        if (timeMillis < buckets.first().startMillis) return null
        if (timeMillis > buckets.last().endMillis) return null
        var lo = 0
        var hi = buckets.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val b = buckets[mid]
            when {
                timeMillis < b.startMillis -> hi = mid - 1
                timeMillis >= b.endMillis -> lo = mid + 1
                else -> return mid
            }
        }
        return null
    }
}
