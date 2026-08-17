package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How the autocorrelation time was estimated. */
@ExperimentalRadiationStatistics
enum class AutocorrelationMethod {
    /** τ = (1+ρ₁)/(1−ρ₁): the AR(1) closed form, needs only the lag-1 value. */
    AR1_LAG1,

    /** τ = 1 + 2·Σρ_k over the initial positive sequence (Geyer's truncation). */
    INTEGRATED,
}

/** Serial-correlation description of one series. */
@ExperimentalRadiationStatistics
data class Autocorrelation(
    /** ρ₁ ∈ [−1, 1]. */
    val lag1: Double,
    /** Integrated autocorrelation time τ ≥ 1, in samples. */
    val time: Double,
    val method: AutocorrelationMethod,
    /** Samples the estimate was made from. */
    val samples: Int,
    /** Lags summed by [AutocorrelationMethod.INTEGRATED] (0 for the AR(1) form). */
    val lagsUsed: Int,
) {
    /** Independent-equivalent sample size of a series of [n] samples. */
    fun effectiveSize(n: Int): Double = n / max(1.0, time)
}

/** One candidate two-sample statistic with its effective-size correction. */
@ExperimentalRadiationStatistics
data class CandidateTest(
    val name: String,
    /** The raw statistic (U for Mann–Whitney, D for Kolmogorov–Smirnov). */
    val statistic: Double,
    /**
     * Standardised value **after** the N_eff correction. Not to be shown, not
     * to be called σ: it is a test statistic of a candidate test.
     */
    val z: Double,
    /** Two-sided asymptotic p-value under the null, with N_eff sizes. */
    val pValue: Double,
    /** Effective sizes actually used. */
    val nEffCurrent: Double,
    val nEffBaseline: Double,
    /**
     * Common-language effect size P(x_current > x_baseline) + ½P(equal) for
     * Mann–Whitney; the sup-distance for KS. Descriptive, unaffected by N_eff.
     */
    val effect: Double,
)

/** Both candidate statistics plus the autocorrelation they were corrected with. */
@ExperimentalRadiationStatistics
data class AnomalyEvidence(
    val mannWhitney: CandidateTest,
    val kolmogorovSmirnov: CandidateTest,
    val currentAutocorrelation: Autocorrelation,
    val baselineAutocorrelation: Autocorrelation,
)

/**
 * **Candidate** current-vs-baseline test (graph spec §36). Everything here is
 * marked [ExperimentalRadiationStatistics] and must not reach the user as a
 * claim; the UI is served by [DescriptiveDeviation] until this passes the
 * gate. See `docs/analysis/trend-and-anomaly.md` for the promotion criteria.
 *
 * ## Scientific release gate (spec §24 / graph spec §41) — NOT PASSED
 *
 * 1. **Formula.**
 *    - **H₀**: the current window and the baseline window are samples of the
 *      same dose-rate distribution.
 *    - **Mann–Whitney U** counts pairs: U = #{(i,j) : xᵢ > yⱼ} + ½#{xᵢ = yⱼ},
 *      effect p̂ = U/(m·n). Under H₀ with *independent* samples
 *      Var(p̂) = [(N+1) − Σ(t³−t)/(N(N−1))] / (12·m·n), N = m+n, tᵢ the tie
 *      group sizes. This implementation substitutes the **effective** sizes
 *      m_e = m/τ_current, n_e = n/τ_baseline (and scales the tie term by
 *      N_e/N), then z = (p̂ − ½)/√Var_e(p̂), p = 2(1 − Φ(|z|)).
 *    - **Kolmogorov–Smirnov** D = sup|F_current − F_baseline|, with
 *      λ = D·√(m_e·n_e/(m_e+n_e)) and the asymptotic
 *      Q(λ) = 2·Σ(−1)^{k−1}·e^{−2k²λ²}.
 *    - **τ** — the integrated autocorrelation time in samples, either the
 *      AR(1) closed form (1+ρ₁)/(1−ρ₁) or 1 + 2Σρ_k truncated at the first
 *      non-positive ρ_k (Geyer's initial positive sequence).
 * 2. **Assumptions.** (a) H₀ is about *distributions*, not means — no
 *    normality is assumed anywhere, which is why a rank test was chosen;
 *    (b) the N_eff correction assumes the series is stationary and its
 *    correlation structure is well summarised by one time constant — for 1 Hz
 *    RC-110 readings, where the device itself integrates, this is a working
 *    approximation and **not** a proven model; (c) the two windows are
 *    compared as if their samples were exchangeable under H₀, which ignores
 *    any slow drift (day/night, weather, radon) that is real in a home
 *    baseline; (d) the asymptotic p-values need m_e, n_e of order tens —
 *    below that they are decoration.
 * 3. **Units.** Inputs µSv/h (any consistent unit works — both statistics are
 *    rank/CDF based); τ in samples; z and p dimensionless.
 * 4. **Reference.** Mann, H. B. & Whitney, D. R. (1947), Ann. Math. Statist.
 *    18(1), 50–60. Smirnov, N. (1948), Ann. Math. Statist. 19(2), 279–281.
 *    Geyer, C. J. (1992), *Practical Markov chain Monte Carlo*, Statist. Sci.
 *    7(4), 473–483 (initial positive sequence for τ). Bartlett, M. S. (1946)
 *    for the N/τ effective-sample-size idea.
 * 5. **Validation data.** Today: deterministic synthetic series only
 *    (`AnomalyStatisticsTest`, `AnomalyValidationTest`) — an AR(1)-driven
 *    Poisson-like stationary series and the same series with an injected step.
 *    Required before promotion: real RC-110 recordings, see
 *    `docs/analysis/trend-and-anomaly.md` §«Что нужно от реальных данных».
 * 6. **Limitations.** (a) **Repeated testing**: a chart that re-tests every
 *    second performs ~86 400 tests a day, so even p = 10⁻⁴ yields ~9 false
 *    alarms daily — the scan-level rate, not the per-test α, is what has to be
 *    validated (see the harness); (b) τ estimated on a window that already
 *    contains the change is biased upward, which makes the test *conservative*
 *    there but unreliable as a τ measurement; (c) a rank test is blind to
 *    *how much* the level moved — the effect size, not the p-value, carries
 *    that; (d) sub-bucket means from long windows are not raw samples and have
 *    their own correlation structure.
 * 7. **Tests.** `app/src/test/.../analysis/AnomalyStatisticsTest.kt` (hand
 *    computed U, D, ρ₁, τ) and
 *    `app/src/test/.../analysis/validation/AnomalyValidationTest.kt`
 *    (false-positive rate under continuous scanning, detection power).
 * 8. **Algorithm version.** [AlgorithmVersions.ANOMALY_TEST_CANDIDATE].
 * 9. **User-facing meaning.** *None yet, by design.* Nothing from this object
 *    is rendered. When it is promoted, the wording will still be a statement
 *    about a test («сравнение с профилем: различие подтверждается тестом
 *    X»), never «опасно» and never a bare σ (graph spec §39).
 *
 * Pure JVM; no Android dependencies.
 */
@ExperimentalRadiationStatistics
object AnomalyStatistics {

    const val ALGORITHM_VERSION = AlgorithmVersions.ANOMALY_TEST_CANDIDATE

    /** ρ₁ is clamped below this before the AR(1) closed form, so τ stays finite. */
    const val MAX_LAG1 = 0.995

    /** Hard cap on the lags summed for the integrated τ. */
    const val MAX_LAGS = 500

    /**
     * Both candidate statistics for one comparison, each corrected with the
     * autocorrelation time of its own series.
     */
    fun compare(
        current: DoubleArray,
        baseline: DoubleArray,
        method: AutocorrelationMethod = AutocorrelationMethod.INTEGRATED,
    ): AnomalyEvidence? {
        if (current.size < 2 || baseline.size < 2) return null
        val acCurrent = autocorrelation(current, method) ?: return null
        val acBaseline = autocorrelation(baseline, method) ?: return null
        val mEff = acCurrent.effectiveSize(current.size)
        val nEff = acBaseline.effectiveSize(baseline.size)
        return AnomalyEvidence(
            mannWhitney = mannWhitney(current, baseline, mEff, nEff),
            kolmogorovSmirnov = kolmogorovSmirnov(current, baseline, mEff, nEff),
            currentAutocorrelation = acCurrent,
            baselineAutocorrelation = acBaseline,
        )
    }

    // ---------------------------------------------------------------- ranks

    /**
     * U = #{(i,j) : xᵢ > yⱼ} + ½#{xᵢ = yⱼ}, computed from the tie-averaged
     * rank sum of [x] in the pooled sample (O((m+n)log(m+n)), no m·n loop).
     */
    fun mannWhitneyU(x: DoubleArray, y: DoubleArray): Double {
        val m = x.size
        val n = y.size
        if (m == 0 || n == 0) return 0.0
        val pooled = DoubleArray(m + n)
        System.arraycopy(x, 0, pooled, 0, m)
        System.arraycopy(y, 0, pooled, m, n)
        val order = pooled.indices.sortedBy { pooled[it] }
        var rankSumX = 0.0
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && pooled[order[j + 1]] == pooled[order[i]]) j++
            // Ranks are 1-based; a tie group shares the average rank.
            val averageRank = (i + j + 2) / 2.0
            for (k in i..j) if (order[k] < m) rankSumX += averageRank
            i = j + 1
        }
        return rankSumX - m * (m + 1.0) / 2.0
    }

    /** Σ(t³ − t) over tie groups of the pooled sample (the ties correction term). */
    fun tieTerm(x: DoubleArray, y: DoubleArray): Double {
        val pooled = DoubleArray(x.size + y.size)
        System.arraycopy(x, 0, pooled, 0, x.size)
        System.arraycopy(y, 0, pooled, x.size, y.size)
        pooled.sort()
        var sum = 0.0
        var i = 0
        while (i < pooled.size) {
            var j = i
            while (j + 1 < pooled.size && pooled[j + 1] == pooled[i]) j++
            val t = (j - i + 1).toDouble()
            sum += t * t * t - t
            i = j + 1
        }
        return sum
    }

    /**
     * Mann–Whitney with explicit effective sizes. Passing
     * `mEff = x.size, nEff = y.size` gives the textbook normal approximation —
     * that is what the unit tests check against hand-computed values.
     */
    fun mannWhitney(
        x: DoubleArray,
        y: DoubleArray,
        mEff: Double = x.size.toDouble(),
        nEff: Double = y.size.toDouble(),
    ): CandidateTest {
        val u = mannWhitneyU(x, y)
        val effect = if (x.isEmpty() || y.isEmpty()) 0.5 else u / (x.size.toDouble() * y.size)
        val n = (x.size + y.size).toDouble()
        val nEffTotal = mEff + nEff
        // Ties are a property of the data; per observation they stay the same,
        // so the correction term is carried over proportionally to N_e/N.
        val ties = if (n > 1.0) tieTerm(x, y) / (n * (n - 1.0)) * (nEffTotal / n) else 0.0
        val variance = ((nEffTotal + 1.0) - ties) / (12.0 * mEff * nEff)
        val z = if (variance > 0.0) (effect - 0.5) / sqrt(variance) else 0.0
        return CandidateTest(
            name = "Mann–Whitney U (N_eff)",
            statistic = u,
            z = z,
            pValue = twoSidedNormalP(z),
            nEffCurrent = mEff,
            nEffBaseline = nEff,
            effect = effect,
        )
    }

    // ------------------------------------------------------------------ KS

    /** D = sup|F_x − F_y|, the two-sample Kolmogorov–Smirnov statistic. */
    fun ksStatistic(x: DoubleArray, y: DoubleArray): Double {
        if (x.isEmpty() || y.isEmpty()) return 0.0
        val a = x.copyOf().also { it.sort() }
        val b = y.copyOf().also { it.sort() }
        var i = 0
        var j = 0
        var d = 0.0
        while (i < a.size && j < b.size) {
            val value = min(a[i], b[j])
            while (i < a.size && a[i] <= value) i++
            while (j < b.size && b[j] <= value) j++
            d = max(d, abs(i.toDouble() / a.size - j.toDouble() / b.size))
        }
        return d
    }

    /** KS with explicit effective sizes; defaults are the raw sizes. */
    fun kolmogorovSmirnov(
        x: DoubleArray,
        y: DoubleArray,
        mEff: Double = x.size.toDouble(),
        nEff: Double = y.size.toDouble(),
    ): CandidateTest {
        val d = ksStatistic(x, y)
        val harmonic = if (mEff > 0 && nEff > 0) mEff * nEff / (mEff + nEff) else 0.0
        val lambda = d * sqrt(harmonic)
        val p = kolmogorovQ(lambda)
        // The KS "z" is reported as the equivalent two-sided normal deviate so
        // the two candidates can be compared on one axis; it is not a σ.
        return CandidateTest(
            name = "Kolmogorov–Smirnov (N_eff)",
            statistic = d,
            z = normalDeviateOf(p),
            pValue = p,
            nEffCurrent = mEff,
            nEffBaseline = nEff,
            effect = d,
        )
    }

    /** Q(λ) = 2·Σ(−1)^{k−1}·e^{−2k²λ²}, clamped to [0,1]. */
    fun kolmogorovQ(lambda: Double): Double {
        if (lambda <= 0.0) return 1.0
        var sum = 0.0
        for (k in 1..100) {
            val term = exp(-2.0 * k * k * lambda * lambda)
            sum += if (k % 2 == 1) term else -term
            if (term < 1e-12) break
        }
        return (2.0 * sum).coerceIn(0.0, 1.0)
    }

    // ------------------------------------------------------- autocorrelation

    /** ρ₁ = Σ(xₜ−x̄)(xₜ₊₁−x̄) / Σ(xₜ−x̄)², or 0 for a constant series. */
    fun lag1(series: DoubleArray): Double = autocovariance(series, 1)

    /**
     * ρ_k for k = 1…, truncated at the first non-positive value (Geyer's
     * initial positive sequence) and capped at [MAX_LAGS] and n/4 — beyond a
     * quarter of the record ρ_k is mostly estimation noise.
     */
    fun integratedAutocorrelationTime(series: DoubleArray): Pair<Double, Int> {
        val maxLag = min(MAX_LAGS, max(1, series.size / 4))
        var sum = 0.0
        var lags = 0
        for (k in 1..maxLag) {
            val rho = autocovariance(series, k)
            if (rho <= 0.0) break
            sum += rho
            lags = k
        }
        return (1.0 + 2.0 * sum) to lags
    }

    /**
     * τ of a series, by the requested method. Null when the series is too
     * short or constant (τ is meaningless without variance).
     */
    fun autocorrelation(
        series: DoubleArray,
        method: AutocorrelationMethod = AutocorrelationMethod.INTEGRATED,
    ): Autocorrelation? {
        if (series.size < 3) return null
        val rho1 = lag1(series)
        return when (method) {
            AutocorrelationMethod.AR1_LAG1 -> {
                val r = rho1.coerceIn(0.0, MAX_LAG1)
                Autocorrelation(
                    lag1 = rho1,
                    time = ((1.0 + r) / (1.0 - r)).coerceAtLeast(1.0),
                    method = method,
                    samples = series.size,
                    lagsUsed = 0,
                )
            }
            AutocorrelationMethod.INTEGRATED -> {
                val (tau, lags) = integratedAutocorrelationTime(series)
                Autocorrelation(
                    lag1 = rho1,
                    time = tau.coerceAtLeast(1.0),
                    method = method,
                    samples = series.size,
                    lagsUsed = lags,
                )
            }
        }
    }

    /** Independent-equivalent size of [n] samples with autocorrelation time [tau]. */
    fun effectiveSize(n: Int, tau: Double): Double = n / max(1.0, tau)

    private fun autocovariance(series: DoubleArray, lag: Int): Double {
        val n = series.size
        if (lag >= n) return 0.0
        var mean = 0.0
        for (v in series) mean += v
        mean /= n
        var c0 = 0.0
        var ck = 0.0
        for (t in 0 until n) {
            val d = series[t] - mean
            c0 += d * d
            if (t + lag < n) ck += d * (series[t + lag] - mean)
        }
        // A series whose spread is below the rounding of its own level is a
        // constant: ρ is then a ratio of rounding errors, and the honest answer
        // is «no correlation structure», not τ = 45.
        val negligible = 1e-18 * n * (mean * mean + Double.MIN_VALUE)
        return if (c0 <= negligible) 0.0 else ck / c0
    }

    // ------------------------------------------------------------- normal

    /** Two-sided p from a standard normal deviate. */
    fun twoSidedNormalP(z: Double): Double = (2.0 * (1.0 - normalCdf(abs(z)))).coerceIn(0.0, 1.0)

    /** Φ(z) via the erf approximation of Abramowitz & Stegun 7.1.26 (|ε| < 1.5e-7). */
    fun normalCdf(z: Double): Double = 0.5 * (1.0 + erf(z / sqrt(2.0)))

    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val a = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * a)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t +
            0.254829592) * t * exp(-a * a)
        return sign * y
    }

    /** Inverse of [twoSidedNormalP]: the |z| whose two-sided p equals [p]. */
    private fun normalDeviateOf(p: Double): Double {
        if (p >= 1.0) return 0.0
        if (p <= 0.0) return Double.MAX_VALUE
        // Bisection on the monotone p(z); 60 halvings resolve z to ~1e-16.
        var lo = 0.0
        var hi = 40.0
        repeat(60) {
            val mid = (lo + hi) / 2.0
            if (twoSidedNormalP(mid) > p) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }
}
