package app.radiacode.ui.logic

import app.radiacode.ui.text.ChartAxisRu
import app.radiacode.ui.text.ChartAxisStrings

import app.radiacode.analysis.quantiles.KllSketch
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One SQL-aggregated slice of raw 1 Hz samples («под-корзина»), already in
 * µSv/h. The database groups by `timestamp / subBucketMillis`; everything the
 * chart shows is folded from these — the screen never reads individual rows,
 * which is what keeps a 30-day window bounded (see [ChartSeriesModel]).
 *
 * [sumMicroSvH] and [sumSqMicroSvH] carry Σx and Σx² of the **raw** samples,
 * so the pooled mean and SD of any group of sub-buckets are exact, not an
 * average of averages.
 */
data class ValueAggregate(
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
     * exact quantiles of the raw samples — see [QuantileMethod].
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
 * [method] tells which path of ADR 004 produced the quantiles: exact order
 * statistics of the raw samples, merged hourly sketches, or the coarse
 * sub-bucket estimate. See [QuantileMethod].
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
    /** Which path of ADR 004 produced [q10]…[q90] — see [QuantileMethod]. */
    val method: QuantileMethod = QuantileMethod.EXACT_RAW,
) {
    /** True when the quantiles are order statistics of the raw samples. */
    val quantilesExact: Boolean get() = method.exact

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
    /** Which path produced the percentiles and the MAD (ADR 004). */
    val method: QuantileMethod = QuantileMethod.EXACT_RAW,
) {
    val quantilesExact: Boolean get() = method.exact

    val iqr: Float get() = q75 - q25

    /**
     * Сколько времени окна реально покрыто измерениями, секунды. Прибор пишет
     * раз в секунду, поэтому число отсчётов — это и есть секунды покрытия.
     */
    val coveredSeconds: Long get() = sampleCount.toLong()

    /** Доля окна, покрытая измерениями, 0…1. */
    val coverage: Float
        get() = if (spanMillis <= 0L) 0f else (coveredSeconds * 1000f / spanMillis).coerceIn(0f, 1f)
}

/**
 * Честная строка о неполном окне (ТЗ полноэкранного графика §2): выбрано «6ч»,
 * а накоплено 47 минут — и это сказано, а не спрятано пустым полем. Null,
 * когда окно покрыто целиком: писать «данных: 6 ч из 6 ч» незачем.
 */
fun coverageWording(
    stats: WindowStats?,
    spanMillis: Long,
    s: ChartAxisStrings = ChartAxisRu,
): String? {
    if (stats == null) return null
    if (stats.coverage >= COVERAGE_FULL) return null
    return s.coverage(
        covered = durationWording(stats.coveredSeconds),
        window = durationWording(spanMillis / 1000),
    )
}

/**
 * Выше этой доли окно считается покрытым. **Инженерный параметр**: пропуски
 * BLE в пару процентов — не «неполное окно», а обычная жизнь потока.
 */
const val COVERAGE_FULL = 0.95f

/**
 * One stored hour of the long path (ADR 004): the exact scalars of the hour
 * plus its mergeable quantile sketch, already converted to µSv/h.
 *
 * [minAtMillis]/[maxAtMillis] are **instants**, not intervals — this is what
 * fixes the P0 limitation and keeps a five-second transient tappable at 30
 * days (CHART SPEC §21).
 */
data class HourSlice(
    val startMillis: Long,
    val sampleCount: Int,
    val min: Float,
    val max: Float,
    val minAtMillis: Long,
    val maxAtMillis: Long,
    val sketch: KllSketch,
)

/**
 * Exact moments of a window read from the minute scalars (ADR 004): n, Σx,
 * Σx² and the extremes, all in µSv/h. Costs no row transfer — SQLite folds
 * `minute_stats` into a single row.
 */
data class WindowRollup(
    val sampleCount: Int,
    val sumMicroSvH: Double,
    val sumSqMicroSvH: Double,
    val min: Float,
    val max: Float,
    val admittedCount: Int = sampleCount,
)

/**
 * Immutable result of one database read: the loaded range with everything the
 * chart can need inside it. Gestures re-project this value; they never trigger
 * another read (see the loader in `LiveChartScreen`).
 */
data class ChartSnapshot(
    val fromMillis: Long,
    val toMillis: Long,
    val bucketMillis: Long,
    val subBucketMillis: Long,
    /** Drawn columns; empty columns are absent (gaps stay gaps). */
    val buckets: List<ChartBucket>,
    /** Sub-bucket aggregates, kept for the window statistics and histogram. */
    val aggregates: List<ValueAggregate>,
    /** Timestamps of journal events (deviations, hotspots) inside the range. */
    val eventTimesMillis: List<Long>,
    /** Which path of ADR 004 produced the quantiles of this snapshot. */
    val method: QuantileMethod = QuantileMethod.EXACT_RAW,
    /** Long path: all hourly sketches of the range merged into one. */
    val windowSketch: KllSketch? = null,
    /** Long path: exact window moments from the minute scalars. */
    val rollup: WindowRollup? = null,
    /**
     * Long path: statistics of the visible window, computed once per read.
     * The short path recomputes them per frame from [aggregates] instead —
     * merging sketches is too expensive to do on a gesture.
     */
    val windowStats: WindowStats? = null,
    /**
     * Exact time range the [windowSketch] was built from — whole stored hours,
     * so the diagnostic can read *the same* raw samples back and compare like
     * with like (CHART SPEC §37G).
     */
    val windowSketchRange: LongRange? = null,
) {
    val spanMillis: Long get() = toMillis - fromMillis
}

/**
 * Folding of stored aggregates into the immutable chart snapshot.
 *
 * **Two paths, one truth (CHART SPEC §6, §28–§32, §34; ADR 004).** Which one
 * a window uses is decided by [QuantilePaths] and travels with every number as
 * [QuantileMethod]:
 *
 *  - **Exact** ([QuantileMethod.EXACT_RAW]) — short windows. SQL is asked for
 *    1-second sub-buckets, which at 1 Hz is one row per raw sample, so the
 *    (value, weight) pairs *are* the measurements and the column quantiles are
 *    their true order statistics (§29). This is the reference the approximate
 *    path is validated against (§37G).
 *  - **Sketch** ([QuantileMethod.KLL_SKETCH]) — long windows, [foldSketches].
 *    A column is a whole number of stored hours; the hours' KLL sketches are
 *    **merged** and the merged structure is queried once (§30). No hour's
 *    quantile is ever computed, so this is not the forbidden
 *    «quantiles of quantiles» of §28. Error is bounded and documented; n,
 *    min, max and the extremum instants stay exact.
 *  - **Fallback** ([QuantileMethod.SUB_BUCKET_MEANS]) — a long window whose
 *    hours are not pre-aggregated yet (fresh install, backfill running).
 *    Quantiles of sub-bucket *means* have no proven error bound (averaging
 *    shrinks the spread: Q10 biased up, Q90 down), so this state is named
 *    separately in the UI and disappears as the backfill catches up.
 *
 * **Why it cannot get slow.** The exact path reads at most one row per second
 * of a ≤ 6 h window; the sketch path reads one row per hour (720 for 30 days).
 * The column count never exceeds [MAX_BUCKETS] + 2 on either path, and
 * gestures never re-enter this code — they only re-project the snapshot.
 */
object ChartSeriesModel {

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

    /**
     * Columns needed to cover a span, hard-capped so the frame stays bounded.
     *
     * Потолок задаётся вызывающим: чтение снимка держит прежнюю геометрию P0
     * ([MAX_BUCKETS] колонок независимо от экрана), а кадр отрисовки просит
     * столько колонок, сколько видно пикселей
     * (`ChartDownsampler.MAX_COLUMNS`).
     */
    fun bucketCount(
        spanMillis: Long,
        bucketMillis: Long,
        maxColumns: Int = MAX_BUCKETS,
    ): Int {
        if (bucketMillis <= 0L) return 0
        return ((spanMillis / bucketMillis) + 1).toInt().coerceIn(1, maxColumns + 2)
    }

    /**
     * One database read → one immutable frame source. Cost is O(rows) with
     * rows ≤ [MAX_BUCKETS] × [SUB_BUCKETS_PER_BUCKET] whatever the range, and
     * the column count never exceeds [MAX_BUCKETS] + 2 — a 30-day window
     * renders exactly as much geometry as a 15-minute one.
     */
    fun snapshot(
        aggregates: List<ValueAggregate>,
        eventTimesMillis: List<Long>,
        alignedFromMillis: Long,
        toMillis: Long,
        bucketMillis: Long,
        subBucketMillis: Long = subBucketMillis(bucketMillis),
    ): ChartSnapshot {
        val count = bucketCount(toMillis - alignedFromMillis, bucketMillis)
        val buckets = fold(aggregates, alignedFromMillis, bucketMillis, count, subBucketMillis)
        return ChartSnapshot(
            fromMillis = alignedFromMillis,
            toMillis = toMillis,
            bucketMillis = bucketMillis,
            subBucketMillis = subBucketMillis,
            buckets = buckets,
            aggregates = aggregates,
            eventTimesMillis = eventTimesMillis,
            // The columns know which regime they came out of; the snapshot
            // reports the worst of them, because one approximated column makes
            // the picture approximate.
            method = if (buckets.all { it.quantilesExact }) {
                QuantileMethod.EXACT_RAW
            } else {
                QuantileMethod.SUB_BUCKET_MEANS
            },
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
        aggregates: List<ValueAggregate>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        bucketCount: Int,
        subBucketMillis: Long = 1_000L,
    ): List<ChartBucket> {
        if (bucketMillis <= 0L || bucketCount <= 0) return emptyList()
        val slots = arrayOfNulls<MutableList<ValueAggregate>>(bucketCount)
        for (a in aggregates) {
            if (a.sampleCount <= 0) continue
            val index = ((a.startMillis - alignedFromMillis) / bucketMillis).toInt()
            if (index !in 0 until bucketCount) continue
            val list = slots[index] ?: ArrayList<ValueAggregate>(SUB_BUCKETS_PER_BUCKET)
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
        parts: List<ValueAggregate>,
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
            method = if (exact) QuantileMethod.EXACT_RAW else QuantileMethod.SUB_BUCKET_MEANS,
        )
    }

    // --- long path: merged hourly sketches (ADR 004, §30) -------------------

    /** Columns of the sketch path plus the merged sketch of the whole range. */
    data class SketchFold(
        val buckets: List<ChartBucket>,
        val windowSketch: KllSketch?,
    )

    /**
     * One database read of the long path → one immutable frame source. The
     * range costs [HourSlice] rows only: 720 of them for 30 days, against
     * 2 592 000 raw samples (CHART SPEC §34).
     *
     * [bucketMillis] must be a whole number of hours — see [QuantilePaths]: a
     * column may only be given the distribution of the hours it fully
     * contains.
     */
    fun snapshotFromSketches(
        slices: List<HourSlice>,
        eventTimesMillis: List<Long>,
        alignedFromMillis: Long,
        toMillis: Long,
        bucketMillis: Long,
        visibleFromMillis: Long = alignedFromMillis,
        visibleToMillis: Long = toMillis,
        rollup: WindowRollup? = null,
    ): ChartSnapshot {
        val count = bucketCount(toMillis - alignedFromMillis, bucketMillis)
        val fold = foldSketches(slices, alignedFromMillis, bucketMillis, count)
        // Window statistics describe the *visible* window, so only the hours
        // inside it are merged — the loaded range deliberately reaches beyond
        // it and must not inflate n.
        val inWindow = slices.filter {
            it.sampleCount > 0 &&
                it.startMillis >= visibleFromMillis &&
                it.startMillis <= visibleToMillis
        }
        val visible = KllSketch.mergeAll(inWindow.map { it.sketch })
        val sketchRange = if (inWindow.isEmpty()) {
            null
        } else {
            inWindow.first().startMillis..
                (inWindow.last().startMillis + QuantilePaths.SKETCH_PERIOD_MILLIS - 1)
        }
        return ChartSnapshot(
            fromMillis = alignedFromMillis,
            toMillis = toMillis,
            bucketMillis = bucketMillis,
            subBucketMillis = QuantilePaths.SKETCH_PERIOD_MILLIS,
            buckets = fold.buckets,
            aggregates = visible?.let { sketchAggregates(it, visibleFromMillis) }.orEmpty(),
            eventTimesMillis = eventTimesMillis,
            method = QuantileMethod.KLL_SKETCH,
            windowSketch = visible,
            rollup = rollup,
            windowStats = windowStatsFromSketch(
                rollup = rollup,
                sketch = visible,
                fromMillis = visibleFromMillis,
                toMillis = visibleToMillis,
            ),
            windowSketchRange = sketchRange,
        )
    }

    /**
     * Folds stored hours into columns: the sketches of the hours inside a
     * column are **merged** and queried once, which is the mergeable-sketch
     * path §30 requires and the exact opposite of the forbidden
     * «quantiles of quantiles» of §28 — no hour's quantile is ever computed,
     * let alone re-quantiled.
     *
     * Extremes come from the stored scalars, so they stay exact with their
     * true instants ([ChartBucket.extremeWindowMillis] = 1 s).
     */
    fun foldSketches(
        slices: List<HourSlice>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        bucketCount: Int,
    ): SketchFold {
        if (bucketMillis <= 0L || bucketCount <= 0) return SketchFold(emptyList(), null)
        val slots = arrayOfNulls<MutableList<HourSlice>>(bucketCount)
        for (slice in slices) {
            if (slice.sampleCount <= 0) continue
            val index = ((slice.startMillis - alignedFromMillis) / bucketMillis).toInt()
            if (index !in 0 until bucketCount) continue
            val list = slots[index] ?: ArrayList<HourSlice>(4).also { slots[index] = it }
            list += slice
        }
        val out = ArrayList<ChartBucket>(bucketCount)
        var window: KllSketch? = null
        for (index in 0 until bucketCount) {
            val list = slots[index] ?: continue
            val start = alignedFromMillis + index * bucketMillis
            val merged = KllSketch.mergeAll(list.map { it.sketch }) ?: continue
            if (window == null) window = merged.copy() else window.merge(merged)
            var n = 0
            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            var minAt = start
            var maxAt = start
            for (slice in list) {
                n += slice.sampleCount
                if (slice.min < min) {
                    min = slice.min
                    minAt = slice.minAtMillis
                }
                if (slice.max > max) {
                    max = slice.max
                    maxAt = slice.maxAtMillis
                }
            }
            val q = merged.quantiles(QUANTILES)
            out += ChartBucket(
                startMillis = start,
                endMillis = start + bucketMillis,
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
                // Stored extrema are instants, not intervals.
                extremeWindowMillis = 1_000L,
                method = QuantileMethod.KLL_SKETCH,
            )
        }
        return SketchFold(out, window)
    }

    /**
     * Window statistics of the long path: n/Σx/Σx²/min/max are **exact** (they
     * come from the minute scalars), the percentiles and the MAD are the
     * sketch's approximation and are labelled as such
     * ([WindowStats.method]).
     */
    fun windowStatsFromSketch(
        rollup: WindowRollup?,
        sketch: KllSketch?,
        fromMillis: Long,
        toMillis: Long,
    ): WindowStats? {
        if (sketch == null || sketch.isEmpty) return null
        val q = sketch.quantiles(QUANTILES)
        val n = rollup?.sampleCount?.takeIf { it > 0 } ?: sketch.count.toInt()
        // With the minute scalars present, Σx/Σx² are exact over the raw
        // samples; without them (backfill still running) the same moments are
        // estimated from the sketch's weighted items, which is approximate but
        // honest — never a zero pretending to be a spread.
        var sum = rollup?.sumMicroSvH ?: 0.0
        var sumSq = rollup?.sumSqMicroSvH ?: 0.0
        var total = n
        if (rollup == null) {
            val items = sketch.weightedItems()
            var weight = 0
            for (i in items.values.indices) {
                val v = items.values[i].toDouble()
                val w = items.weights[i]
                sum += v * w
                sumSq += v * v * w
                weight += w
            }
            total = weight.coerceAtLeast(1)
        }
        val mean = sum / total
        val variance = (sumSq / total) - mean * mean
        return WindowStats(
            min = rollup?.min ?: sketch.min,
            p10 = q[0],
            q25 = q[1],
            median = q[2],
            q75 = q[3],
            p90 = q[4],
            max = rollup?.max ?: sketch.max,
            mad = sketch.mad(),
            sd = sqrt(max(0.0, variance)).toFloat(),
            sampleCount = n,
            spanMillis = toMillis - fromMillis,
            method = QuantileMethod.KLL_SKETCH,
        )
    }

    /**
     * The sketch's weighted items as pseudo sub-buckets, so the distribution
     * strip can be drawn on the long path too. They are placed at
     * [atMillis] because they no longer belong to any single instant — the
     * histogram only ever asks «how much measured time sat at this level».
     */
    fun sketchAggregates(sketch: KllSketch, atMillis: Long): List<ValueAggregate> {
        val items = sketch.weightedItems()
        val out = ArrayList<ValueAggregate>(items.values.size)
        for (i in items.values.indices) {
            val value = items.values[i]
            val weight = items.weights[i]
            if (weight <= 0) continue
            out += ValueAggregate(
                startMillis = atMillis,
                minMicroSvH = value,
                maxMicroSvH = value,
                sumMicroSvH = value.toDouble() * weight,
                sumSqMicroSvH = value.toDouble() * value * weight,
                sampleCount = weight,
            )
        }
        return out
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
        aggregates: List<ValueAggregate>,
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
            method = if (exact) QuantileMethod.EXACT_RAW else QuantileMethod.SUB_BUCKET_MEANS,
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
