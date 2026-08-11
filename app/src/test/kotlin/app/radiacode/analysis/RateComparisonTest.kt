package app.radiacode.analysis

import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scientific core of Поиск (search redesign §3).
 *
 * The point of most of these tests is not that a number comes out — it is that
 * the number stays right when the two windows have **different exposures**,
 * which is precisely where the naive formula the spec forbids falls apart.
 */
class RateComparisonTest {

    /** A steady window: [seconds] readings of exactly [rate] s⁻¹, 1 Hz. */
    private fun steady(rate: Double, seconds: Int, startMillis: Long = 0L): CountWindow {
        val times = LongArray(seconds) { startMillis + it * 1_000L }
        val rates = DoubleArray(seconds) { rate }
        return CountWindow.reconstruct(times, rates)
    }

    /** Readings drawn from a deterministic pseudo-Poisson-ish series. */
    private fun scattered(mean: Double, spread: Double, seconds: Int): CountWindow {
        val times = LongArray(seconds) { it * 1_000L }
        // Deterministic zig-zag around the mean: the exact shape does not
        // matter, only that the variance is a known multiple of the mean.
        val rates = DoubleArray(seconds) { i ->
            (mean + spread * if (i % 2 == 0) 1.0 else -1.0).coerceAtLeast(0.0)
        }
        return CountWindow.reconstruct(times, rates)
    }

    // ---------------------------------------------------------------- counts

    @Test
    fun `counts are reconstructed as the sum of rate times interval`() {
        val window = steady(rate = 20.0, seconds = 45)
        assertEquals(900.0, window.counts, 1e-9)
        assertEquals(45.0, window.seconds, 1e-9)
        assertEquals(45, window.samples)
        assertEquals(20.0, window.ratePerSecond, 1e-9)
    }

    @Test
    fun `a BLE hole shortens the exposure instead of stretching the last rate`() {
        // 10 readings, then a 20 s hole, then 5 more.
        val times = LongArray(15) { if (it < 10) it * 1_000L else 30_000L + (it - 10) * 1_000L }
        val rates = DoubleArray(15) { 20.0 }
        val window = CountWindow.reconstruct(times, rates)

        // Exposure is 15 measured seconds, not the 35 s of wall clock.
        assertEquals(15.0, window.seconds, 1e-9)
        assertEquals(300.0, window.counts, 1e-9)
        assertTrue(window.gapSeconds > 18.0, "the hole must be reported: ${window.gapSeconds}")
    }

    @Test
    fun `poisson sigma of the rate is sqrt N over t`() {
        val window = steady(rate = 20.0, seconds = 45)
        assertEquals(sqrt(900.0) / 45.0, window.poissonSigma, 1e-9)
    }

    // ------------------------------------------------------- the primary test

    @Test
    fun `identical rates on different exposures are not called a difference`() {
        val current = steady(rate = 25.0, seconds = 5)
        val background = steady(rate = 25.0, seconds = 45)
        val result = RateComparison.compare(current, background)

        assertEquals(RateTest.CONDITIONAL_BINOMIAL, result.test)
        assertTrue(result.pValue > 0.5, "p = ${result.pValue}")
        assertTrue(result.ratioLow < 1.0 && result.ratioHigh > 1.0)
        assertTrue(!result.excessConfirmedByInterval)
    }

    @Test
    fun `a large excess over a long background is detected`() {
        val current = steady(rate = 90.0, seconds = 10)
        val background = steady(rate = 25.0, seconds = 45)
        val result = RateComparison.compare(current, background)

        assertTrue(result.pValue < 1e-6, "p = ${result.pValue}")
        assertTrue(result.excessConfirmedByInterval)
        assertEquals(3.6, result.ratio, 1e-9)
        assertTrue(result.ratioLow < 3.6 && result.ratioHigh > 3.6)
    }

    @Test
    fun `the exact interval brackets the true ratio and tightens with exposure`() {
        val background = steady(rate = 25.0, seconds = 45)
        val short = RateComparison.compare(steady(50.0, 3), background)
        val long = RateComparison.compare(steady(50.0, 30), background)

        assertTrue(short.ratioLow < 2.0 && short.ratioHigh > 2.0)
        assertTrue(long.ratioLow < 2.0 && long.ratioHigh > 2.0)
        val shortWidth = short.ratioHigh - short.ratioLow
        val longWidth = long.ratioHigh - long.ratioLow
        assertTrue(longWidth < shortWidth, "longer exposure must narrow: $longWidth vs $shortWidth")
    }

    @Test
    fun `a deficit is reported as a deficit, not as an excess`() {
        val result = RateComparison.compare(steady(8.0, 20), steady(25.0, 45))
        assertTrue(result.deficitConfirmedByInterval)
        assertTrue(!result.excessConfirmedByInterval)
        assertTrue(result.differencePerSecond < 0.0)
    }

    @Test
    fun `the background exposure matters - the same current window, two backgrounds`() {
        val current = steady(rate = 34.0, seconds = 10)
        val shortBackground = RateComparison.compare(current, steady(25.0, 5))
        val longBackground = RateComparison.compare(current, steady(25.0, 300))

        // Same current window and the same point estimate of the ratio…
        assertEquals(shortBackground.ratio, longBackground.ratio, 1e-9)
        // …but a background measured for 5 s cannot support the same claim as
        // one measured for 300 s. A formula that ignores the baseline exposure
        // could not tell these two situations apart at all.
        assertTrue(longBackground.pValue < shortBackground.pValue)
        assertTrue(
            (longBackground.ratioHigh - longBackground.ratioLow) <
                (shortBackground.ratioHigh - shortBackground.ratioLow),
        )
    }

    @Test
    fun `tiny counts are handled exactly instead of by a normal approximation`() {
        // 2 s at 1.5 s⁻¹ against 4 s at 1.0 s⁻¹: 3 counts vs 4.
        val result = RateComparison.compare(steady(1.5, 2), steady(1.0, 4))
        assertEquals(RateTest.CONDITIONAL_BINOMIAL, result.test)
        assertTrue(result.pValue > 0.2, "small counts prove nothing: p = ${result.pValue}")
        // Below the normal-approximation floor the z is simply not reported.
        assertNull(result.zEquivalent)
    }

    @Test
    fun `a z is reported only when it really is a z`() {
        val big = RateComparison.compare(steady(60.0, 20), steady(25.0, 45))
        val small = RateComparison.compare(steady(0.5, 4), steady(0.4, 6))

        val z = assertNotNull(big.zEquivalent)
        assertEquals(
            (60.0 - 25.0) / sqrt(1200.0 / 400.0 + 1125.0 / 2025.0),
            z,
            1e-6,
        )
        assertNull(small.zEquivalent)
    }

    @Test
    fun `empty windows produce no verdict at all`() {
        val nothing = RateComparison.compare(CountWindow(0.0, 0.0, 0), steady(25.0, 45))
        assertEquals(RateTest.NONE, nothing.test)
        assertEquals(1.0, nothing.pValue)
    }

    // --------------------------------------------------- the forbidden naive

    /**
     * The formula the search redesign §3 forbids. It exists **only here**, as
     * the thing the tests compare against — see
     * `no production path uses the naive significance formula`.
     */
    private fun naiveZ(currentCps: Double, backgroundCps: Double): Double =
        (currentCps - backgroundCps) / sqrt(currentCps)

    @Test
    fun `the naive formula and the correct one diverge on a short current window`() {
        // 3 s of current window against a 45 s background: 105 vs 1125 counts.
        val current = steady(rate = 35.0, seconds = 3)
        val background = steady(rate = 25.0, seconds = 45)
        val result = RateComparison.compare(current, background)

        val naive = naiveZ(35.0, 25.0)
        val correct = assertNotNull(result.zEquivalent)

        // The naive form divides by √(rate) — the σ of a *one-second* count,
        // whatever the window really was. Here it reports 1.69, i.e. «ordinary
        // fluctuation, nothing to see», while the same 105 counts against the
        // recorded 1125 are resolved by the exact conditional test at p < 1 %.
        // On a search screen that error has a direction: the naive statistic
        // walks the user past the source.
        assertTrue(abs(naive - 1.690) < 0.01, "naive = $naive")
        assertTrue(correct > naive * 1.5, "correct = $correct vs naive = $naive")
        assertTrue(result.pValue < 0.01, "p = ${result.pValue}")
        assertTrue(result.excessConfirmedByInterval)
    }

    @Test
    fun `the naive formula returns one number for two incomparable situations`() {
        // Identical rates, four-fold different exposures. The naive statistic
        // cannot see either duration, so it answers the same thing twice; the
        // conditional test knows how much counting stands behind each answer.
        val short = RateComparison.compare(steady(35.0, 3), steady(25.0, 45))
        val long = RateComparison.compare(steady(35.0, 30), steady(25.0, 300))

        val naive = naiveZ(35.0, 25.0)
        assertTrue(abs(naive - 1.690) < 0.01)

        val shortZ = assertNotNull(short.zEquivalent)
        val longZ = assertNotNull(long.zEquivalent)
        assertTrue(longZ > shortZ * 2.5, "short = $shortZ, long = $longZ")
        assertTrue(long.pValue < short.pValue)
        assertTrue(
            (long.ratioHigh - long.ratioLow) < (short.ratioHigh - short.ratioLow),
        )
    }

    @Test
    fun `the naive formula misses an excess the exact test resolves`() {
        // 30 s at 30 s⁻¹ against 45 s at 25 s⁻¹ — a 4σ excess by the honest
        // statistic, and «0,9σ» by the forbidden one.
        val result = RateComparison.compare(steady(30.0, 30), steady(25.0, 45))
        val naive = naiveZ(30.0, 25.0)

        assertTrue(naive < 1.0, "naive = $naive")
        assertTrue(assertNotNull(result.zEquivalent) > 3.5)
        assertTrue(result.pValue < 1e-3, "p = ${result.pValue}")
        assertTrue(result.excessConfirmedByInterval)
    }

    @Test
    fun `no production path uses the naive significance formula`() {
        // A division by the square root of a rate/count-rate quantity is the
        // textual fingerprint of the forbidden statistic. Legitimate σ
        // propagation in this app divides by √(a variance) or by √n, never by
        // √(a rate), so this pattern has no honest use here.
        val forbidden = Regex(
            """/\s*(kotlin\.math\.|Math\.)?sqrt\(\s*[^)]*\b(cps|countRate|current[A-Za-z]*Rate|ratePerSecond)\b""",
            RegexOption.IGNORE_CASE,
        )
        val offenders = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { forbidden.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()
        assertEquals(emptyList(), offenders, "naive (current − background)/√current found")
    }

    // ------------------------------------------------------- Fano dispersion

    @Test
    fun `a poisson-like window reports a Fano factor near one and keeps the Poisson model`() {
        // Var = mean gives F = 1: readings alternate mean ± √mean.
        val window = scattered(mean = 25.0, spread = 5.0, seconds = 45)
        val fano = assertNotNull(window.fanoFactor)
        assertTrue(abs(fano - 1.0) < 0.1, "F = $fano")

        val result = RateComparison.compare(steady(25.0, 10), window)
        assertEquals(Dispersion.POISSON_LIKE, result.dispersion)
        assertEquals(UncertaintyModel.POISSON, result.model)
        assertEquals(1.0, result.dispersionFactor)
        assertEquals(RateTest.CONDITIONAL_BINOMIAL, result.test)
    }

    @Test
    fun `overdispersion widens the interval and names the corrected test`() {
        val calm = scattered(mean = 25.0, spread = 5.0, seconds = 45)
        val wild = scattered(mean = 25.0, spread = 15.0, seconds = 45)
        assertTrue(assertNotNull(wild.fanoFactor) > RateComparison.FANO_HIGH)

        val current = steady(40.0, 10)
        val strict = RateComparison.compare(current, calm)
        val corrected = RateComparison.compare(current, wild)

        assertEquals(Dispersion.OVERDISPERSED, corrected.dispersion)
        assertEquals(UncertaintyModel.EMPIRICAL_VARIANCE, corrected.model)
        assertEquals(RateTest.QUASI_BINOMIAL, corrected.test)
        assertTrue(corrected.dispersionFactor > 1.0)
        assertTrue(
            corrected.pValue > strict.pValue,
            "extra variance must cost confidence: ${corrected.pValue} vs ${strict.pValue}",
        )
        assertTrue(
            (corrected.ratioHigh - corrected.ratioLow) > (strict.ratioHigh - strict.ratioLow),
        )
    }

    @Test
    fun `underdispersion is reported but never narrows the answer`() {
        val smooth = steady(rate = 25.0, seconds = 45) // zero variance: F = 0
        assertEquals(0.0, assertNotNull(smooth.fanoFactor), 1e-12)

        val result = RateComparison.compare(steady(40.0, 10), smooth)
        assertEquals(Dispersion.UNDERDISPERSED, result.dispersion)
        assertEquals(UncertaintyModel.EMPIRICAL_VARIANCE, result.model)
        // φ stays 1: a smoother-than-Poisson stream carries less independent
        // information, not more, so nothing is deflated in that direction.
        assertEquals(1.0, result.dispersionFactor)
        // And the σ of the difference is never allowed below the Poisson one.
        val poisson = sqrt(400.0 / 100.0 + 1125.0 / 2025.0)
        assertTrue(result.differenceSigma >= poisson - 1e-9)
    }

    @Test
    fun `too few readings mean no Fano factor and no claim about dispersion`() {
        val tiny = steady(rate = 25.0, seconds = 4)
        assertNull(tiny.fanoFactor)
        val result = RateComparison.compare(steady(40.0, 10), tiny)
        assertEquals(Dispersion.UNKNOWN, result.dispersion)
        assertEquals(UncertaintyModel.POISSON, result.model)
    }

    @Test
    fun `the dispersion check reads the stationary window, not the walk`() {
        val background = scattered(mean = 25.0, spread = 5.0, seconds = 45)
        val walking = scattered(mean = 60.0, spread = 40.0, seconds = 20)
        val result = RateComparison.compare(walking, background)
        // The current window is wildly overdispersed — that is the sweep, not
        // the instrument, and it must not change the model.
        assertEquals(Dispersion.POISSON_LIKE, result.dispersion)
    }

    // ------------------------------------------------------------- numerics

    @Test
    fun `log gamma matches known factorials`() {
        assertEquals(0.0, RateComparison.lnGamma(1.0), 1e-12)
        assertEquals(0.0, RateComparison.lnGamma(2.0), 1e-12)
        assertEquals(kotlin.math.ln(24.0), RateComparison.lnGamma(5.0), 1e-12)
        assertEquals(0.5 * kotlin.math.ln(Math.PI), RateComparison.lnGamma(0.5), 1e-12)
    }

    @Test
    fun `the regularized incomplete beta matches the binomial it encodes`() {
        // P(X ≤ 2) for X ~ Binomial(5, 0.3), summed term by term.
        val exact = (0..2).sumOf { binomialPmf(it, 5, 0.3) }
        assertEquals(exact, RateComparison.binomialCdf(2.0, 5.0, 0.3), 1e-12)

        // …and the survival is the complementary sum.
        val survival = (3..5).sumOf { binomialPmf(it, 5, 0.3) }
        assertEquals(survival, RateComparison.binomialSurvival(3.0, 5.0, 0.3), 1e-12)
    }

    @Test
    fun `the beta quantile inverts the beta cdf`() {
        for (p in listOf(0.025, 0.5, 0.975)) {
            val x = RateComparison.betaQuantile(p, 4.0, 7.0)
            assertEquals(p, RateComparison.regularizedIncompleteBeta(4.0, 7.0, x), 1e-9)
        }
    }

    @Test
    fun `the conditional p is symmetric in the direction of the difference`() {
        // Equal exposures: an excess of k and a deficit of the mirror count
        // must produce the same two-sided p.
        val high = RateComparison.conditionalBinomialP(k = 70.0, n = 100.0, p0 = 0.5)
        val low = RateComparison.conditionalBinomialP(k = 30.0, n = 100.0, p0 = 0.5)
        assertEquals(high, low, 1e-12)
        assertTrue(high < 0.001, "p = $high")
    }

    private fun binomialPmf(k: Int, n: Int, p: Double): Double {
        var coefficient = 1.0
        for (i in 1..k) coefficient = coefficient * (n - k + i) / i
        return coefficient * Math.pow(p, k.toDouble()) * Math.pow(1.0 - p, (n - k).toDouble())
    }
}
