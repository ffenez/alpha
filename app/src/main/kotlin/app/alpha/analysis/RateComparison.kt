package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * One measurement window reduced to what a counting statistic actually needs:
 * a number of registered events and the exposure time they were registered in.
 *
 * ## The counts are a reconstruction, not a register read
 *
 * The RC-110 reports a **rate** (`countRate`, s⁻¹) once per second; the raw
 * per-second register is not part of the BLE record we decode. So the counts of
 * a window are rebuilt as
 *
 * ```text
 * N ≈ Σ rᵢ · Δtᵢ
 * ```
 *
 * over the received readings, with Δtᵢ the integration interval each reading
 * stands for. This is an honest reconstruction and it is **not** the same thing
 * as reading a scaler: if the device pre-filters, averages or clamps its rate,
 * N inherits that processing. That is exactly why [fanoFactor] exists — it is
 * the only handle we have on whether the reported stream still behaves like a
 * counting process at all.
 *
 * [seconds] is the **measured** exposure — the sum of the Δtᵢ actually
 * received — never the wall-clock span. Time lost to a BLE gap lands in
 * [gapSeconds] instead, so a hole shortens the exposure rather than silently
 * inflating it (spec §12: no interpolation across gaps).
 */
data class CountWindow(
    /** Reconstructed events in the window, N ≈ Σ rᵢ·Δtᵢ. */
    val counts: Double,
    /** Measured exposure, s — the sum of the received integration intervals. */
    val seconds: Double,
    /** Number of readings that entered the sums. */
    val samples: Int,
    /** Σ rᵢ of the per-reading rates, s⁻¹ (empirical variance). */
    val sumRate: Double = 0.0,
    /** Σ rᵢ² of the per-reading rates (empirical variance). */
    val sumRateSquares: Double = 0.0,
    /** Wall-clock time inside the window that carried no reading, s. */
    val gapSeconds: Double = 0.0,
) {

    /** R = N / t, s⁻¹. Zero exposure has no rate; callers must check [usable]. */
    val ratePerSecond: Double get() = if (seconds > 0.0) counts / seconds else 0.0

    /** A window with exposure and at least one reading can enter a comparison. */
    val usable: Boolean get() = seconds > 0.0 && samples > 0 && counts >= 0.0

    /** σ_R ≈ √N / t — the Poisson 1σ of the rate (spec §5, graph spec §11.2). */
    val poissonSigma: Double get() = if (seconds > 0.0) sqrt(counts) / seconds else 0.0

    /** Mean integration interval of the readings, s. */
    val meanDeltaSeconds: Double get() = if (samples > 0) seconds / samples else 0.0

    /** Mean of the per-reading rates, s⁻¹. */
    val meanRate: Double get() = if (samples > 0) sumRate / samples else 0.0

    /** Unbiased sample variance of the per-reading **rates**; null below 2. */
    val rateVariance: Double?
        get() {
            if (samples < 2) return null
            val mean = meanRate
            val v = (sumRateSquares - samples * mean * mean) / (samples - 1)
            return if (v.isFinite() && v >= 0.0) v else null
        }

    /**
     * Standard error of the mean rate from the **observed scatter** of the
     * readings, s⁻¹: s/√n. Null when there are too few readings.
     *
     * Caveat that travels with it: consecutive readings of a filtered stream
     * are correlated, so s/√n is itself optimistic — it assumes independence.
     * [RateComparison] therefore never lets an empirical σ come out *narrower*
     * than the Poisson one.
     */
    val empiricalSigma: Double?
        get() = rateVariance?.let { sqrt(it / samples) }

    /**
     * Fano factor F = Var(N)/E[N] of the window, computed from the reported
     * rates: with Nᵢ = rᵢ·Δt this is F = Δt·Var(r)/r̄.
     *
     * F ≈ 1 is what a Poisson counting process gives. F ≫ 1 means extra
     * variance (a moving instrument, a non-stationary field, pile-up effects);
     * F ≪ 1 means the reported stream is smoother than counting statistics
     * allows, i.e. the device is pre-filtering — and then Poisson σ **overstates
     * the precision of the average**, because the readings are no longer
     * independent samples.
     *
     * Null when the window is too short to estimate a variance at all.
     */
    val fanoFactor: Double?
        get() {
            if (samples < RateComparison.MIN_SAMPLES_FOR_FANO) return null
            val mean = meanRate
            if (mean <= 0.0) return null
            val variance = rateVariance ?: return null
            val f = variance * meanDeltaSeconds / mean
            return if (f.isFinite()) f else null
        }

    companion object {

        /**
         * A reading stands for its own integration interval. The device makes
         * one record per second, so a Δt outside this range is not a longer
         * measurement — it is a hole in delivery.
         */
        const val MIN_DELTA_SECONDS = 0.4
        const val MAX_DELTA_SECONDS = 2.5
        const val NOMINAL_DELTA_SECONDS = 1.0

        /**
         * Rebuilds a window from timestamped rate readings, newest last.
         *
         * Δtᵢ is the distance to the previous reading when that distance looks
         * like one device record ([MIN_DELTA_SECONDS]…[MAX_DELTA_SECONDS]);
         * otherwise the reading contributes its nominal one second and the rest
         * of the wall-clock distance is counted as a **gap**. The rate before a
         * hole is never stretched across it.
         */
        fun reconstruct(timesMillis: LongArray, rates: DoubleArray): CountWindow {
            require(timesMillis.size == rates.size) { "times and rates differ in length" }
            var window = EMPTY
            var previous: Long? = null
            for (i in rates.indices) {
                val rate = rates[i]
                if (!rate.isFinite() || rate < 0.0) continue
                window = window.plusReading(previous, timesMillis[i], rate)
                previous = timesMillis[i]
            }
            return window
        }

        /** A window with nothing in it — the identity of [plusReading]. */
        val EMPTY = CountWindow(counts = 0.0, seconds = 0.0, samples = 0)

        /**
         * Exposure and lost time one reading contributes, given the instant of
         * the previous one (null = the first reading of the window).
         *
         * Extracted so that a window accumulated **live**, reading by reading
         * (the Поиск background measurement), and a window rebuilt from a list
         * cannot drift apart: both go through this rule.
         */
        fun contribution(previousMillis: Long?, timeMillis: Long): Contribution {
            if (previousMillis == null) return Contribution(NOMINAL_DELTA_SECONDS, 0.0)
            val raw = (timeMillis - previousMillis) / 1000.0
            if (raw in MIN_DELTA_SECONDS..MAX_DELTA_SECONDS) return Contribution(raw, 0.0)
            val gap = if (raw > MAX_DELTA_SECONDS) raw - NOMINAL_DELTA_SECONDS else 0.0
            return Contribution(NOMINAL_DELTA_SECONDS, gap)
        }
    }

    /** What one reading adds: its own exposure and the hole in front of it. */
    data class Contribution(val seconds: Double, val gapSeconds: Double)

    /**
     * This window plus one more reading of [rate] s⁻¹ taken at [timeMillis],
     * where [previousMillis] is the instant of the reading before it.
     *
     * Non-finite and negative rates are ignored, exactly as in [reconstruct].
     */
    fun plusReading(previousMillis: Long?, timeMillis: Long, rate: Double): CountWindow {
        if (!rate.isFinite() || rate < 0.0) return this
        val (delta, gap) = contribution(previousMillis, timeMillis)
        return copy(
            counts = counts + rate * delta,
            seconds = seconds + delta,
            samples = samples + 1,
            sumRate = sumRate + rate,
            sumRateSquares = sumRateSquares + rate * rate,
            gapSeconds = gapSeconds + gap,
        )
    }
}

/** Which uncertainty model produced the numbers of a comparison. */
enum class UncertaintyModel(val label: String) {
    /** σ_R = √N/t — counting statistics, the model of spec §5. */
    POISSON("пуассоновская статистика счёта"),

    /**
     * σ_R from the observed scatter of the readings, because the Fano factor
     * says the stream does not behave like a plain counting process.
     */
    EMPIRICAL_VARIANCE("эмпирическая дисперсия показаний"),
}

/** What the Fano factor says about the stream. */
enum class Dispersion(val label: String) {
    /** Not enough readings to say anything. */
    UNKNOWN("дисперсия не оценивалась"),

    /** F inside the accepted band — consistent with counting statistics. */
    POISSON_LIKE("совместимо со счётной статистикой"),

    /** F above the band: extra variance beyond counting statistics. */
    OVERDISPERSED("разброс шире счётной статистики"),

    /** F below the band: the stream is smoother than counting allows. */
    UNDERDISPERSED("разброс уже счётной статистики (сглаживание прибора)"),
}

/** Which test produced [RateComparisonResult.pValue]. */
enum class RateTest(val label: String) {
    /** Przyborowski–Wilenski conditional binomial — exact, no approximation. */
    CONDITIONAL_BINOMIAL("условный биномиальный тест (Przyborowski–Wilenski)"),

    /** Same test on counts deflated by the dispersion factor (quasi-binomial). */
    QUASI_BINOMIAL("условный биномиальный тест с поправкой на сверхдисперсию"),

    /** The windows carry nothing to compare. */
    NONE("сравнение невозможно"),
}

/** Everything one comparison of two rate estimates produced. */
data class RateComparisonResult(
    val current: CountWindow,
    val background: CountWindow,
    /** R = (N_c/t_c)/(N_b/t_b); 0 when the background rate is zero. */
    val ratio: Double,
    /** Lower end of the [confidenceLevel] interval for R. */
    val ratioLow: Double,
    /** Upper end; [Double.POSITIVE_INFINITY] when the background window is empty. */
    val ratioHigh: Double,
    val confidenceLevel: Double,
    /** Two-sided p of the conditional test; 1.0 when nothing can be said. */
    val pValue: Double,
    val test: RateTest,
    val model: UncertaintyModel,
    /** φ used to deflate the counts; 1.0 = no correction. */
    val dispersionFactor: Double,
    val fanoFactor: Double?,
    val dispersion: Dispersion,
    /** R_c − R_b, s⁻¹. */
    val differencePerSecond: Double,
    /** 1σ of that difference under the model named by [model], s⁻¹. */
    val differenceSigma: Double,
    /**
     * Signed z of the difference — present **only** when the normal
     * approximation genuinely holds for both windows (see
     * [RateComparison.MIN_COUNTS_FOR_NORMAL]). Never a renamed anomaly score
     * (spec §11).
     */
    val zEquivalent: Double?,
) {
    /** The whole interval for R sits above 1 — an excess, not a fluctuation. */
    val excessConfirmedByInterval: Boolean get() = ratioLow > 1.0

    /** The whole interval sits below 1. */
    val deficitConfirmedByInterval: Boolean get() = ratioHigh < 1.0
}

/**
 * Two-sample comparison of a **current** counting window against a **recorded
 * background** window with a different exposure time (Поиск, redesign §3).
 *
 * ## Scientific release gate (spec §24, graph spec §41)
 *
 * 1. **Formula.** Two independent Poisson counts N_c ~ Poisson(λ_c·t_c) and
 *    N_b ~ Poisson(λ_b·t_b). Conditioning on the total n = N_c + N_b removes the
 *    nuisance parameter: under H₀ (λ_c = λ_b)
 *
 *    ```text
 *    N_c | n  ~  Binomial(n, p₀),   p₀ = t_c / (t_c + t_b)
 *    ```
 *
 *    (Przyborowski & Wilenski 1940). The two-sided p is twice the smaller tail
 *    of that binomial, capped at 1. The estimand shown to the user is the
 *    **rate ratio** R = (N_c/t_c)/(N_b/t_b); since p = λ_c t_c/(λ_c t_c + λ_b t_b)
 *    is a strictly increasing function of R,
 *
 *    ```text
 *    R = p/(1−p) · t_b/t_c
 *    ```
 *
 *    maps a Clopper–Pearson interval for p directly onto an interval for R —
 *    an exact interval, not a delta-method approximation.
 *    The difference R_c − R_b is reported too, with
 *    σ_diff = √(N_c/t_c² + N_b/t_b²), for the expert block only.
 *
 * 2. **Assumptions.** (a) The two windows are independent — the background is a
 *    *recorded* measurement and is never re-derived from the current sweep
 *    (redesign §6, §12). (b) Each window is stationary within itself; walking
 *    with the instrument violates this for the current window and is the reason
 *    a single window is never a verdict on its own — the state ladder adds a
 *    minimum confirmation time. (c) The counting process is Poisson; that is
 *    checked, not assumed, by [CountWindow.fanoFactor]. (d) Dead time and
 *    pile-up are ignored, which spec §5 permits only at low and moderate rates
 *    — at high rates the reconstruction and the Poisson σ both degrade, and no
 *    correction is applied because the RC-110 dead time is not documented.
 *
 * 3. **Units.** Counts are dimensionless, exposures are seconds, rates and σ
 *    are s⁻¹, the ratio and the Fano factor are dimensionless. σ_diff has the
 *    same unit as the rates by construction (√(N/t²) = √N/t).
 *
 * 4. **Reference.** Przyborowski, J. & Wilenski, H. (1940), *Homogeneity of
 *    results in testing samples from Poisson series*, Biometrika 31, 313–323
 *    (the conditional binomial test); Clopper, C. & Pearson, E. (1934),
 *    Biometrika 26, 404–413 (the exact interval that is inverted here);
 *    NIST/SEMATECH e-Handbook §6.3 on Poisson counting and its limits;
 *    Fano, U. (1947), Phys. Rev. 72, 26 (the dispersion factor).
 *
 * 5. **Validation data.** Deterministic synthetic windows in
 *    `RateComparisonTest`: equal rates give a large p, a known excess a small
 *    one, the exact interval brackets the true ratio, and the forbidden naive
 *    statistic is shown to diverge from the correct one. **Not yet measured on
 *    an RC-110** — the Fano factor of the real stream is the one number here
 *    that cannot be derived and must come off the instrument
 *    (`docs/analysis/search-statistics.md`, field protocol step 11).
 *
 * 6. **Limitations.** The p-value is a statement about *this* pair of windows.
 *    Search re-evaluates it about once a second, so an uncorrected α would
 *    produce roughly α·3600 flags an hour on a stationary background; the
 *    minimum confirmation time of the state ladder — not the α — is what makes
 *    the displayed verdict trustworthy. The test says «the rates differ», never
 *    «a source is here» and never anything about dose (redesign §12).
 *    Underdispersion is *reported*, not corrected: the correct correction needs
 *    the autocorrelation time of the RC-110 stream, which is unmeasured.
 *
 * 7. **Tests.** `app/src/test/.../analysis/RateComparisonTest.kt`.
 *
 * 8. **Algorithm version.** [AlgorithmVersions.RATE_COMPARISON].
 *
 * 9. **User-facing meaning.** «Скорость счёта в текущем окне выше записанного
 *    фона в R раз, и это отличие не объясняется статистикой счёта» — nothing
 *    more. It is not a dose, not a nuclide and not a safety statement.
 *
 * ## What this module exists to prevent
 *
 * The naive significance
 *
 * ```text
 * z = (current − baseline) / √current        ← FORBIDDEN (redesign §3)
 * ```
 *
 * ignores the exposure of both windows, the uncertainty of the baseline and the
 * fact that a rate is not a count. On a 5-second current window against a
 * 45-second background it is off by a large factor in both directions depending
 * on which side is short — `RateComparisonTest` pins that divergence so the
 * formula cannot creep back in.
 *
 * Pure JVM; no Android dependencies.
 */
object RateComparison {

    const val ALGORITHM_VERSION = AlgorithmVersions.RATE_COMPARISON

    /** Interval reported for the ratio. Two-sided, exact. */
    const val CONFIDENCE_LEVEL = 0.95

    /**
     * Below this many readings a variance estimate is noise, so no Fano factor
     * is reported and the model stays Poisson. **Engineering parameter**: the
     * relative error of a variance estimate is ≈ √(2/(n−1)), so at n = 10 it is
     * already ±47 % — enough to tell F ≈ 1 from F ≈ 3, not enough for anything
     * finer, which is all this decision needs.
     */
    const val MIN_SAMPLES_FOR_FANO = 10

    /**
     * Fano band treated as «consistent with counting statistics».
     *
     * **Engineering parameters, not physics.** They are set wide on purpose:
     * with 45 readings the sampling scatter of F itself is ≈ ±21 % (1σ), so a
     * narrower band would flip the model back and forth on a perfectly Poisson
     * stream. The real values for the RC-110 must be measured (field protocol
     * step 11) and these constants revisited with the measurement in hand.
     */
    const val FANO_LOW = 0.5
    const val FANO_HIGH = 2.0

    /**
     * A normal approximation for a Poisson count is respectable from a few tens
     * of events; 25 is the same threshold [AbAnalysis] uses to switch away from
     * the likelihood-ratio form, kept identical so two parts of the app do not
     * disagree about when «z» is a word we are allowed to use.
     */
    const val MIN_COUNTS_FOR_NORMAL = 25.0

    /** Nothing to compare: both windows empty, or a zero exposure. */
    private val NOTHING = RateComparisonResult(
        current = CountWindow(0.0, 0.0, 0),
        background = CountWindow(0.0, 0.0, 0),
        ratio = 0.0,
        ratioLow = 0.0,
        ratioHigh = Double.POSITIVE_INFINITY,
        confidenceLevel = CONFIDENCE_LEVEL,
        pValue = 1.0,
        test = RateTest.NONE,
        model = UncertaintyModel.POISSON,
        dispersionFactor = 1.0,
        fanoFactor = null,
        dispersion = Dispersion.UNKNOWN,
        differencePerSecond = 0.0,
        differenceSigma = 0.0,
        zEquivalent = null,
    )

    /**
     * Compares [current] against [background].
     *
     * [stationaryWindow] is the window whose dispersion is meaningful — by
     * construction the background (the user was asked to stand still for it,
     * redesign §5). Passing the current window instead would measure the walk,
     * not the instrument. Defaults to [background].
     */
    fun compare(
        current: CountWindow,
        background: CountWindow,
        stationaryWindow: CountWindow = background,
        confidenceLevel: Double = CONFIDENCE_LEVEL,
    ): RateComparisonResult {
        if (!current.usable || !background.usable) return NOTHING

        val fano = stationaryWindow.fanoFactor
        val dispersion = when {
            fano == null -> Dispersion.UNKNOWN
            fano < FANO_LOW -> Dispersion.UNDERDISPERSED
            fano > FANO_HIGH -> Dispersion.OVERDISPERSED
            else -> Dispersion.POISSON_LIKE
        }
        // Overdispersion is corrected for (quasi-binomial: the same test on
        // counts deflated by φ, which is the standard quasi-likelihood device).
        // Underdispersion is NOT corrected the other way: a stream smoother
        // than counting statistics carries less independent information, not
        // more, so deflating in that direction would manufacture confidence.
        val phi = if (dispersion == Dispersion.OVERDISPERSED && fano != null) fano else 1.0
        val model = if (dispersion == Dispersion.POISSON_LIKE || dispersion == Dispersion.UNKNOWN) {
            UncertaintyModel.POISSON
        } else {
            UncertaintyModel.EMPIRICAL_VARIANCE
        }

        val tc = current.seconds
        val tb = background.seconds
        val p0 = tc / (tc + tb)
        val k = current.counts / phi
        val n = (current.counts + background.counts) / phi

        val pValue = conditionalBinomialP(k, n, p0)
        val alpha = 1.0 - confidenceLevel
        val pLow = clopperPearsonLower(k, n, alpha / 2.0)
        val pHigh = clopperPearsonUpper(k, n, alpha / 2.0)
        val scale = tb / tc

        val backgroundRate = background.ratePerSecond
        val ratio = if (backgroundRate > 0.0) current.ratePerSecond / backgroundRate else Double.POSITIVE_INFINITY
        val ratioLow = oddsToRatio(pLow, scale)
        val ratioHigh = oddsToRatio(pHigh, scale)

        val difference = current.ratePerSecond - backgroundRate
        val poissonSigma = sqrt(
            current.counts / (tc * tc) + background.counts / (tb * tb),
        )
        val empiricalSigma = empiricalDifferenceSigma(current, background)
        // The empirical σ never narrows the answer: s/√n assumes independent
        // readings, which is exactly what an underdispersed stream is not.
        val sigma = when {
            model == UncertaintyModel.POISSON || empiricalSigma == null -> poissonSigma
            else -> maxOf(poissonSigma, empiricalSigma)
        }

        val z = if (
            current.counts >= MIN_COUNTS_FOR_NORMAL &&
            background.counts >= MIN_COUNTS_FOR_NORMAL &&
            sigma > 0.0
        ) {
            difference / sigma
        } else {
            null
        }

        return RateComparisonResult(
            current = current,
            background = background,
            ratio = ratio,
            ratioLow = ratioLow,
            ratioHigh = ratioHigh,
            confidenceLevel = confidenceLevel,
            pValue = pValue,
            test = if (phi > 1.0) RateTest.QUASI_BINOMIAL else RateTest.CONDITIONAL_BINOMIAL,
            model = model,
            dispersionFactor = phi,
            fanoFactor = fano,
            dispersion = dispersion,
            differencePerSecond = difference,
            differenceSigma = sigma,
            zEquivalent = z,
        )
    }

    /**
     * Two-sided p of N_c ~ Binomial(n, p₀) at the observed [k], by doubling the
     * smaller tail. The tails are evaluated through the regularized incomplete
     * beta function, so [k] and [n] may be non-integer — that is what makes the
     * quasi-binomial correction possible without resampling anything.
     */
    fun conditionalBinomialP(k: Double, n: Double, p0: Double): Double {
        if (n <= 0.0 || p0 <= 0.0 || p0 >= 1.0) return 1.0
        val lower = binomialCdf(k, n, p0)
        val upper = binomialSurvival(k, n, p0)
        return (2.0 * minOf(lower, upper)).coerceIn(0.0, 1.0)
    }

    /** P(X ≤ k) = I_{1−p}(n−k, k+1), with the degenerate ends handled exactly. */
    fun binomialCdf(k: Double, n: Double, p: Double): Double = when {
        k >= n -> 1.0
        k < 0.0 -> 0.0
        else -> regularizedIncompleteBeta(n - k, k + 1.0, 1.0 - p)
    }

    /** P(X ≥ k) = I_p(k, n−k+1). */
    fun binomialSurvival(k: Double, n: Double, p: Double): Double = when {
        k <= 0.0 -> 1.0
        k > n -> 0.0
        else -> regularizedIncompleteBeta(k, n - k + 1.0, p)
    }

    /** Clopper–Pearson lower bound for p = k/n. */
    fun clopperPearsonLower(k: Double, n: Double, tail: Double): Double =
        if (k <= 0.0) 0.0 else betaQuantile(tail, k, n - k + 1.0)

    /** Clopper–Pearson upper bound for p = k/n. */
    fun clopperPearsonUpper(k: Double, n: Double, tail: Double): Double =
        if (k >= n) 1.0 else betaQuantile(1.0 - tail, k + 1.0, n - k)

    /** p → R = p/(1−p) · t_b/t_c, the monotone map that carries the interval. */
    private fun oddsToRatio(p: Double, scale: Double): Double = when {
        p <= 0.0 -> 0.0
        p >= 1.0 -> Double.POSITIVE_INFINITY
        else -> p / (1.0 - p) * scale
    }

    /** √(s_c²/n_c + s_b²/n_b) — the observed-scatter σ of the difference. */
    private fun empiricalDifferenceSigma(current: CountWindow, background: CountWindow): Double? {
        val a = current.empiricalSigma ?: return null
        val b = background.empiricalSigma ?: return null
        return sqrt(a * a + b * b)
    }

    // ---------------------------------------------------------------------
    // Numerics. Self-contained on purpose: the statistics of this screen must
    // be readable and testable without opting into the experimental module.
    // ---------------------------------------------------------------------

    /**
     * Regularized incomplete beta I_x(a, b) via the Lentz continued fraction
     * (Numerical Recipes §6.4 form, re-implemented from the standard
     * recurrence — the recurrence itself is textbook mathematics).
     */
    fun regularizedIncompleteBeta(a: Double, b: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        if (a <= 0.0 || b <= 0.0) return Double.NaN
        val front = exp(
            a * ln(x) + b * ln(1.0 - x) + lnGamma(a + b) - lnGamma(a) - lnGamma(b),
        )
        // The fraction converges fast only on the near side of the mode; the
        // symmetry I_x(a,b) = 1 − I_{1−x}(b,a) supplies the other side. The
        // comparison is non-strict so that x exactly on the split point takes
        // the fraction — with `<` it would bounce into infinite recursion.
        return if (x <= (a + 1.0) / (a + b + 2.0)) {
            front * betaContinuedFraction(a, b, x) / a
        } else {
            1.0 - regularizedIncompleteBeta(b, a, 1.0 - x)
        }
    }

    private fun betaContinuedFraction(a: Double, b: Double, x: Double): Double {
        val tiny = 1e-300
        var c = 1.0
        var d = 1.0 - (a + b) * x / (a + 1.0)
        if (abs(d) < tiny) d = tiny
        d = 1.0 / d
        var result = d
        for (m in 1..300) {
            val m2 = 2 * m
            // even step
            var numerator = m * (b - m) * x / ((a + m2 - 1.0) * (a + m2))
            d = 1.0 + numerator * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + numerator / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            result *= d * c
            // odd step
            numerator = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1.0))
            d = 1.0 + numerator * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + numerator / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            val delta = d * c
            result *= delta
            if (abs(delta - 1.0) < 1e-14) break
        }
        return result
    }

    /**
     * Inverse of [regularizedIncompleteBeta] in x, by bisection.
     *
     * Bisection rather than Newton on purpose: 200 halvings of [0,1] reach the
     * double-precision floor, the beta function is monotone in x so the bracket
     * is always valid, and there is no derivative to get wrong. This runs about
     * twice a second, not in a hot loop.
     */
    fun betaQuantile(probability: Double, a: Double, b: Double): Double {
        if (probability <= 0.0) return 0.0
        if (probability >= 1.0) return 1.0
        if (a <= 0.0 || b <= 0.0) return Double.NaN
        var low = 0.0
        var high = 1.0
        repeat(200) {
            val mid = 0.5 * (low + high)
            if (regularizedIncompleteBeta(a, b, mid) < probability) low = mid else high = mid
            if (high - low < 1e-15) return 0.5 * (low + high)
        }
        return 0.5 * (low + high)
    }

    /** Lanczos approximation, g = 7, n = 9 — accurate to ~1e-15 for x > 0. */
    fun lnGamma(x: Double): Double {
        if (x < 0.5) {
            // Reflection: Γ(x)Γ(1−x) = π/sin(πx).
            return ln(Math.PI / kotlin.math.sin(Math.PI * x)) - lnGamma(1.0 - x)
        }
        val z = x - 1.0
        var series = LANCZOS[0]
        for (i in 1 until LANCZOS.size) series += LANCZOS[i] / (z + i)
        val t = z + LANCZOS.size - 1.5
        return 0.5 * ln(2.0 * Math.PI) + (z + 0.5) * ln(t) - t + ln(series)
    }

    private val LANCZOS = doubleArrayOf(
        0.99999999999980993,
        676.5203681218851,
        -1259.1392167224028,
        771.32342877765313,
        -176.61502916214059,
        12.507343278686905,
        -0.13857109526572012,
        9.9843695780195716e-6,
        1.5056327351493116e-7,
    )
}
