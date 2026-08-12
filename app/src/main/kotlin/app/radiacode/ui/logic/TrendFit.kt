package app.radiacode.ui.logic

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.data.DoseUnitSetting
import java.util.Locale

/**
 * One present (non-gap) bin of the visible window: when it was measured and
 * its robust central value (the bin **median**, the same line the chart draws).
 * Gaps are simply absent — a missing bin is never interpolated into a point
 * (graph spec §25).
 */
data class TrendPoint(
    val timeMillis: Long,
    val valueMicroSvH: Float,
)

/**
 * Тренд посчитан — или не посчитан, и тогда с числами, объясняющими почему.
 */
sealed interface TrendAvailability {
    data class Ready(val result: TrendResult) : TrendAvailability

    /** Присутствующих интервалов меньше [TrendFit.MIN_PRESENT_BINS]. */
    data class TooFewBins(val present: Int) : TrendAvailability

    /** Интервалы есть, но они покрывают меньше [TrendFit.MIN_SPAN_MILLIS]. */
    data class TooShort(val spanMillis: Long) : TrendAvailability
}

/** Which estimator produced a slope. */
enum class TrendMethod {
    /** Median of all pairwise slopes (default, graph spec §23). */
    THEIL_SEN,

    /** Ordinary least squares — kept for research comparison only. */
    OLS,
}

/**
 * Result of a trend fit. Everything needed to explain the number in «Почему?»
 * travels with it: nothing here is a claim about a cause, only a description
 * of how the measured bins are ordered in time.
 */
data class TrendResult(
    /** β̂ in µSv/h per hour. */
    val slopeMicroSvHPerHour: Float,
    val method: TrendMethod,
    /** Present (non-gap) bins found in the window. */
    val presentBins: Int,
    /** Bins actually entering the pair set (= [presentBins] unless subsampled). */
    val pointsUsed: Int,
    /** First-to-last time distance of the present bins, ms. */
    val spanMillis: Long,
    /** Pairs i<j evaluated. */
    val pairCount: Long,
    /** True when the bins were deterministically thinned before pairing. */
    val subsampled: Boolean,
)

/**
 * Trend of the visible window («Тренд/ч» on the hero card), graph spec §23.
 *
 * ## Scientific release gate (spec §24 / graph spec §41)
 *
 * 1. **Formula.** Theil–Sen slope over the present bin medians:
 *    β̂ = median over all pairs i<j of (xⱼ − xᵢ)/(tⱼ − tᵢ), with tᵢ taken in
 *    hours, so β̂ comes out directly in µSv/h per hour. OLS
 *    (β̂ = Σ(t−t̄)(x−x̄)/Σ(t−t̄)²) stays available as [researchOlsSlope] for
 *    comparison, never as the displayed default.
 * 2. **Assumptions.** (a) the bins are the measured ones — gaps are dropped,
 *    not interpolated, and the real timestamps enter the denominator, so an
 *    unevenly sampled window is handled honestly; (b) a *monotone linear*
 *    description is what is being asked for — Theil–Sen estimates the slope of
 *    a straight line, it does not test whether a line describes the data;
 *    (c) no distributional assumption is made (that is the point of using a
 *    median of pairwise slopes rather than least squares).
 * 3. **Units.** [TrendResult.slopeMicroSvHPerHour] is µSv/h **per hour**
 *    (µSv·h⁻²). The label converts to the display unit only at the last step.
 * 4. **Reference.** Theil, H. (1950), *A rank-invariant method of linear and
 *    polynomial regression analysis*, Proc. K. Ned. Akad. Wet. 53; Sen, P. K.
 *    (1968), *Estimates of the regression coefficient based on Kendall's tau*,
 *    JASA 63(324), 1379–1389. Graph spec §23 selects it over OLS.
 * 5. **Validation data.** Deterministic synthetic series in `TrendFitTest`
 *    (exact known slopes, injected single spike, gaps, subsampling). No RC-110
 *    recording is needed for the estimator itself — it is arithmetic on the
 *    bins; what a *real* recording would validate is whether the availability
 *    thresholds below feel right in the field, which is a product question.
 * 6. **Limitations.** The 1 Hz stream and therefore the bin medians are
 *    strongly autocorrelated, so the slope carries **no** significance and no
 *    confidence interval here: a Theil–Sen confidence interval via Kendall's τ
 *    assumes independent observations and would be far too narrow on this data
 *    (see `analysis/AnomalyStatistics.kt` for the N_eff machinery this would
 *    need). The estimator is also blind to non-monotone shapes: a window that
 *    rose and fell back can report ≈0. And a trend is *descriptive* — it never
 *    states a cause, and «растёт» is not «опасно».
 * 7. **Tests.** `app/src/test/.../ui/logic/TrendFitTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.TREND_FIT].
 * 9. **User-facing meaning.** «За последний час измеренная медиана менялась в
 *    среднем на X мкЗв/ч в час» — a description of the bins on screen, not a
 *    forecast and not a statement that something is happening. When the window
 *    does not carry enough bins or enough time, the UI says [UNAVAILABLE]
 *    instead of showing a number.
 *
 * Pure JVM; no Android dependencies.
 */
object TrendFit {

    const val ALGORITHM_VERSION = AlgorithmVersions.TREND_FIT

    /** Below this µSv/h-per-hour magnitude the trend reads as flat («→»). */
    const val FLAT_EPSILON_MICRO_SV = 0.0005f

    /**
     * Minimum present (non-gap) bins before a trend is shown.
     *
     * **Engineering parameter, not a scientific constant.** Rationale: with 12
     * bins the median is taken over 66 pairwise slopes, so a single deviant
     * bin participates in 11 of them (≈17 %) and cannot move the median; below
     * ~8 bins one bad bin starts to carry the answer. Chosen at the round
     * number above that, not derived from any statistic of the data.
     */
    const val MIN_PRESENT_BINS = 12

    /**
     * Minimum first-to-last distance of the present bins before a trend is
     * shown, ms.
     *
     * **Engineering parameter, not a scientific constant.** Rationale: the
     * number is extrapolated to one hour, so a window of length T multiplies
     * whatever noise it contains by 3600 s/T. At 10 minutes that factor is 6,
     * which is already generous; below it the displayed number would be mostly
     * an amplified fluctuation.
     */
    const val MIN_SPAN_MILLIS = 10L * 60_000L

    /**
     * All pairs are evaluated up to this many bins (O(n²) ≈ 31 000 pairs at the
     * cap — microseconds, and the chart never holds more than
     * [ChartSeriesModel.MAX_BUCKETS] = 200 columns anyway). Above it the bins are
     * thinned **deterministically** (fixed stride, first and last kept) so the
     * same window always gives the same slope: no randomness, nothing to seed,
     * nothing that could differ between a test and a phone.
     */
    const val MAX_PAIRED_POINTS = 250

    /** What the UI shows instead of a number when the window is too thin. */
    const val UNAVAILABLE = "тренд недоступен"

    /**
     * Theil–Sen slope over [points] (present bins only), µSv/h per hour, or
     * null when the availability rule ([MIN_PRESENT_BINS], [MIN_SPAN_MILLIS])
     * is not met.
     */
    fun fit(
        points: List<TrendPoint>,
        method: TrendMethod = TrendMethod.THEIL_SEN,
    ): TrendResult? = (availability(points, method) as? TrendAvailability.Ready)?.result

    /**
     * Тренд — или ПРИЧИНА, по которой его нет.
     *
     * Голый прочерк на месте числа не отличим от поломки: человек видит «—» и
     * не знает, копится ли ещё окно, прервана ли связь или что-то сломалось.
     * Правило доступности здесь одно и то же, но оно возвращает числа, из
     * которых можно составить честную подпись.
     */
    fun availability(
        points: List<TrendPoint>,
        method: TrendMethod = TrendMethod.THEIL_SEN,
    ): TrendAvailability {
        val usable = points
            .filter { it.valueMicroSvH.isFinite() }
            .sortedBy { it.timeMillis }
        if (usable.size < MIN_PRESENT_BINS) return TrendAvailability.TooFewBins(usable.size)
        val span = usable.last().timeMillis - usable.first().timeMillis
        if (span < MIN_SPAN_MILLIS) return TrendAvailability.TooShort(span)

        val paired = subsample(usable)
        val slope = when (method) {
            TrendMethod.THEIL_SEN -> theilSen(paired)
            TrendMethod.OLS -> ols(paired)
        } ?: return TrendAvailability.TooFewBins(usable.size)
        val n = paired.size.toLong()
        return TrendAvailability.Ready(
            TrendResult(
                slopeMicroSvHPerHour = slope,
                method = method,
                presentBins = usable.size,
                pointsUsed = paired.size,
                spanMillis = span,
                pairCount = n * (n - 1) / 2,
                subsampled = paired.size != usable.size,
            ),
        )
    }

    /** Короткая подпись «почему тренда нет»; null, когда он есть. */
    fun unavailableNote(availability: TrendAvailability): String? = when (availability) {
        is TrendAvailability.Ready -> null
        is TrendAvailability.TooFewBins ->
            "нужно $MIN_PRESENT_BINS интервалов · есть ${availability.present}"
        is TrendAvailability.TooShort ->
            "нужно ${HistoryFormat.duration(MIN_SPAN_MILLIS / 1000)} измерений · есть " +
                HistoryFormat.duration(availability.spanMillis / 1000)
    }

    /**
     * Same fit for a column array whose bins are evenly spaced by
     * [bucketMillis] (the Monitor hour chart): nulls are gaps, the time of a
     * bin is its centre.
     */
    fun fit(
        columns: List<Float?>,
        bucketMillis: Long,
        method: TrendMethod = TrendMethod.THEIL_SEN,
    ): TrendResult? {
        if (bucketMillis <= 0L) return null
        return fit(toPoints(columns, bucketMillis), method)
    }

    /**
     * **Theil–Sen** slope of the present columns in µSv/h per hour, or null
     * when the window is too thin (see [MIN_PRESENT_BINS], [MIN_SPAN_MILLIS]).
     * This is the default trend everywhere in the UI (graph spec §23).
     */
    fun slopePerHour(columns: List<Float?>, bucketMillis: Long): Float? =
        fit(columns, bucketMillis)?.slopeMicroSvHPerHour

    /** [slopePerHour] on explicit timestamps. */
    fun slopePerHour(points: List<TrendPoint>): Float? =
        fit(points)?.slopeMicroSvHPerHour

    /**
     * OLS slope — **research comparison only** (graph spec §23: «OLS можно
     * оставить как Research comparison, но не как основной тренд»). Useful in
     * one place: showing how far a single spike drags least squares while the
     * displayed Theil–Sen line stays put.
     */
    fun researchOlsSlope(points: List<TrendPoint>): TrendResult? =
        fit(points, TrendMethod.OLS)

    /** [researchOlsSlope] for evenly spaced columns. */
    fun researchOlsSlopePerHour(columns: List<Float?>, bucketMillis: Long): Float? =
        fit(columns, bucketMillis, TrendMethod.OLS)?.slopeMicroSvHPerHour

    /**
     * The bare Theil–Sen estimator on raw timestamped values, in **value units
     * per second** — no availability rule, no dose units, no [TrendResult].
     *
     * It exists because Поиск needs the same robust slope over a ~10 s window
     * of count rate to answer «теплее или холоднее», where [fit]'s thresholds
     * (12 bins, 10 minutes) are deliberately wrong: those guard a number that
     * is *extrapolated to an hour*, which the search direction never is. The
     * availability rule for the short window lives at that call site
     * ([SearchDirectionFit]) so the two uses cannot silently borrow each
     * other's constants.
     *
     * Returns null when fewer than two distinct instants are present.
     */
    fun theilSenPerSecond(timesMillis: LongArray, values: FloatArray): Double? {
        require(timesMillis.size == values.size) { "times and values differ in length" }
        val n = values.size
        if (n < 2) return null
        val slopes = ArrayList<Double>(n * (n - 1) / 2)
        for (i in 0 until n - 1) {
            if (!values[i].isFinite()) continue
            for (j in i + 1 until n) {
                if (!values[j].isFinite()) continue
                val dt = (timesMillis[j] - timesMillis[i]) / 1000.0
                if (dt <= 0.0) continue
                slopes += (values[j] - values[i]) / dt
            }
        }
        if (slopes.isEmpty()) return null
        slopes.sort()
        val mid = slopes.size / 2
        return if (slopes.size % 2 == 1) slopes[mid] else (slopes[mid - 1] + slopes[mid]) / 2.0
    }

    /** Present columns → points at bin centres. */
    fun toPoints(columns: List<Float?>, bucketMillis: Long): List<TrendPoint> =
        columns.mapIndexedNotNull { index, value ->
            value?.let { TrendPoint(index * bucketMillis + bucketMillis / 2, it) }
        }

    /**
     * «+0,004 ↗» / «−0,012 ↘» / «0,000 →» in the display unit. The arrow is
     * sign with a flatness epsilon so noise does not oscillate the glyph.
     */
    fun label(slopeMicroSvHPerHour: Float, unit: DoseUnitSetting): String {
        val display = DoseFormat.rateValue(slopeMicroSvHPerHour, unit)
        val text = when (unit) {
            DoseUnitSetting.MICRO_SIEVERT -> String.format(Locale.US, "%+.3f", display)
            DoseUnitSetting.MICRO_ROENTGEN -> String.format(Locale.US, "%+.1f", display)
        }.replace('.', ',').replace("-", "−")
        val arrow = when {
            slopeMicroSvHPerHour > FLAT_EPSILON_MICRO_SV -> "↗"
            slopeMicroSvHPerHour < -FLAT_EPSILON_MICRO_SV -> "↘"
            else -> "→"
        }
        return "$text $arrow"
    }

    /**
     * Deterministic thinning above [MAX_PAIRED_POINTS]: keep every k-th bin
     * with k = ⌈n / MAX_PAIRED_POINTS⌉ and always keep the last one, so the
     * time span — the denominator that matters most — is preserved exactly.
     */
    private fun subsample(points: List<TrendPoint>): List<TrendPoint> {
        if (points.size <= MAX_PAIRED_POINTS) return points
        val stride = (points.size + MAX_PAIRED_POINTS - 1) / MAX_PAIRED_POINTS
        val out = ArrayList<TrendPoint>(points.size / stride + 2)
        var i = 0
        while (i < points.size) {
            out += points[i]
            i += stride
        }
        if (out.last() !== points.last()) out += points.last()
        return out
    }

    /**
     * Median of the n(n−1)/2 pairwise slopes. Pairs sharing a timestamp are
     * skipped (an infinite slope is not a measurement). For an even number of
     * slopes the two central values are averaged — the classic definition of
     * the sample median; with n ≥ [MIN_PRESENT_BINS] the choice cannot change
     * the displayed value beyond its last digit.
     */
    private fun theilSen(points: List<TrendPoint>): Float? {
        val n = points.size
        val slopes = DoubleArray(n * (n - 1) / 2)
        var count = 0
        for (i in 0 until n - 1) {
            val ti = points[i].timeMillis
            val xi = points[i].valueMicroSvH.toDouble()
            for (j in i + 1 until n) {
                val dt = points[j].timeMillis - ti
                if (dt == 0L) continue
                slopes[count++] = (points[j].valueMicroSvH - xi) / (dt / 3_600_000.0)
            }
        }
        if (count == 0) return null
        val used = if (count == slopes.size) slopes else slopes.copyOf(count)
        java.util.Arrays.sort(used)
        val mid = used.size / 2
        val median = if (used.size % 2 == 1) used[mid] else (used[mid - 1] + used[mid]) / 2.0
        return median.toFloat()
    }

    /** Least squares on the same points, hours on the x axis. */
    private fun ols(points: List<TrendPoint>): Float? {
        val t0 = points.first().timeMillis
        val n = points.size.toDouble()
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0
        for (p in points) {
            val x = (p.timeMillis - t0) / 3_600_000.0
            val y = p.valueMicroSvH.toDouble()
            sumX += x
            sumY += y
            sumXY += x * y
            sumXX += x * x
        }
        val denominator = n * sumXX - sumX * sumX
        if (denominator == 0.0) return null
        return ((n * sumXY - sumX * sumY) / denominator).toFloat()
    }
}
