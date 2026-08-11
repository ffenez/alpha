package app.radiacode.ui.logic

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.data.JsonMap

/**
 * How the quantiles of a chart column (or of the window statistics) were
 * obtained. Every number the chart shows carries this with it, because
 * «медиана» computed three different ways is three different claims (CHART
 * SPEC §32, ADR 004).
 */
enum class QuantileMethod(
    /** Whether the result is an exact order statistic of the raw samples. */
    val exact: Boolean,
    /** Short name for Research details and export metadata. */
    val storageKey: String,
) {
    /** Order statistics of the raw 1 Hz samples themselves (§29). */
    EXACT_RAW(true, "exact_raw"),

    /** Merged hourly KLL sketches — approximate with a documented bound (§30). */
    KLL_SKETCH(false, "kll_sketch"),

    /**
     * Order statistics of sub-bucket means: the stopgap of the P0 chart, kept
     * **only** as a fallback for ranges whose pre-aggregation is not built yet
     * (fresh install mid-backfill). It has no proven error bound — averaging
     * shrinks the spread, so Q10 is biased up and Q90 down — and is therefore
     * named separately everywhere it can appear.
     */
    SUB_BUCKET_MEANS(false, "sub_bucket_means"),
}

/**
 * The two query paths of ADR 004 and where the line between them is.
 *
 * **Short window → exact.** Up to [EXACT_MAX_SPAN_MILLIS] the raw samples are
 * simply read (SQL groups them by second, which at 1 Hz is one row per
 * sample), so every column carries the true order statistics of the
 * measurements — the reference the approximate path is validated against
 * (§29, §37G). The budget is bounded by construction: 6 h × 1 Hz = 21 600
 * rows.
 *
 * **Long window → merged sketches.** Above that, columns are built from the
 * hourly KLL sketches. A column then **must** be a whole number of hours, and
 * that is the one hard constraint of this design: a column may only be given
 * the distribution of hours it fully contains, otherwise a 7-minute column
 * would be showing the quantiles of the hour around it — a lie of exactly the
 * kind §28 forbids. So the column width is snapped up to whole hours, and a
 * 24-hour window shows 24 columns instead of 200. That is a deliberate trade:
 * §3's «100–250 корзин» is a UX guideline, §28 is a prohibition, and the
 * prohibition wins.
 *
 * At 30 days the same rule gives 4-hour columns (180 of them) and reads 720
 * sketch rows instead of 2 592 000 raw ones — the performance target of §34.
 */
object QuantilePaths {

    /** Raw samples per second the device produces (DATA_BUF cadence). */
    const val SAMPLES_PER_SECOND = 1

    /**
     * Longest window served by the exact path. Chosen in ADR 004: 6 h of 1 Hz
     * data is 21 600 rows, which SQLite reduces over the timestamp index in
     * well under a debounced reload, and 6 h is the last period the chart
     * still offers raw dots for.
     */
    const val EXACT_MAX_SPAN_MILLIS = 6L * 3_600_000L

    /** Period of one stored sketch — the granularity of the long path. */
    const val SKETCH_PERIOD_MILLIS = 3_600_000L

    /** Which path a span uses, before it is known whether sketches exist. */
    fun methodFor(spanMillis: Long): QuantileMethod =
        if (spanMillis <= EXACT_MAX_SPAN_MILLIS) {
            QuantileMethod.EXACT_RAW
        } else {
            QuantileMethod.KLL_SKETCH
        }

    /**
     * Column width for a span on the given path. The exact path keeps the
     * geometry of the P0 chart ([DoseChartModel.MAX_BUCKETS] columns); the
     * sketch path snaps **up** to whole hours so a column is always the union
     * of complete stored sketches, and never wider than needed to stay under
     * the column cap.
     */
    fun bucketMillis(
        spanMillis: Long,
        method: QuantileMethod,
        bucketCount: Int = DoseChartModel.MAX_BUCKETS,
    ): Long {
        val plain = DoseChartModel.bucketMillis(spanMillis, bucketCount)
        if (method == QuantileMethod.EXACT_RAW) return plain
        val hours = (plain + SKETCH_PERIOD_MILLIS - 1) / SKETCH_PERIOD_MILLIS
        return hours.coerceAtLeast(1L) * SKETCH_PERIOD_MILLIS
    }

    /** Sub-bucket width asked of SQL on the exact path: one raw sample. */
    fun exactSubBucketMillis(): Long = 1_000L

    /** Upper bound on raw rows the exact path can read for a span. */
    fun exactRowBudget(spanMillis: Long): Int =
        ((spanMillis / 1000L) * SAMPLES_PER_SECOND + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Upper bound on sketch rows the long path reads for a span. */
    fun sketchRowBudget(spanMillis: Long): Int =
        (spanMillis / SKETCH_PERIOD_MILLIS + 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/**
 * The reproducibility stamp of a quantile result (CHART SPEC §32, spec §22):
 * method, algorithm version and the configured accuracy parameter. Written in
 * the same flat-JSON shape as `spectra.analysisMeta`, so exports and Research
 * details speak one language.
 */
object QuantileMetadata {

    /** «KLL k=128 · v1» — the short form shown next to an approximate number. */
    fun label(method: QuantileMethod, k: Int = KllSketch.DEFAULT_K): String = when (method) {
        QuantileMethod.EXACT_RAW -> "точные порядковые статистики сырых отсчётов"
        QuantileMethod.KLL_SKETCH ->
            "KLL-скетч, k=$k, v${AlgorithmVersions.QUANTILE_SKETCH}, ошибка ранга ≈ " +
                errorPercentLabel(k)
        QuantileMethod.SUB_BUCKET_MEANS ->
            "оценка по средним под-корзин — без доказанной границы ошибки"
    }

    /** «≈ 1,8 %» — the nominal rank error of the sketch for an accuracy k. */
    fun errorPercentLabel(k: Int): String {
        val percent = RANK_ERROR_CONSTANT / k * 100.0
        return String.format(java.util.Locale.ROOT, "%.1f", percent).replace('.', ',') + " %"
    }

    /** Machine-readable stamp for export metadata and stored diagnostics. */
    fun stamp(method: QuantileMethod, k: Int = KllSketch.DEFAULT_K): String = JsonMap.of(
        "method" to method.storageKey,
        "algorithms" to "quantile_sketch",
        "algorithmVersion" to AlgorithmVersions.QUANTILE_SKETCH,
        "accuracy_k" to if (method == QuantileMethod.KLL_SKETCH) k else null,
        "exact" to method.exact.toString(),
    )

    /**
     * Rank-error constant of the KLL structure: ε ≈ c/k. The value is the one
     * documented by the reference implementation of the same structure (Apache
     * DataSketches, 99 % confidence); the error actually measured on synthetic
     * data is pinned in `KllSketchTest` and recorded in ADR 004.
     */
    const val RANK_ERROR_CONSTANT = 2.296
}
