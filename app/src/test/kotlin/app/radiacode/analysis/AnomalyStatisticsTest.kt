package app.radiacode.analysis

import app.radiacode.analysis.validation.SyntheticSeries
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reference values here are computed by hand in the comments — the point of a
 * test on a statistic is that it can be checked without running the code.
 */
@OptIn(ExperimentalRadiationStatistics::class)
class AnomalyStatisticsTest {

    private val interleavedX = doubleArrayOf(1.0, 3.0, 5.0, 7.0)
    private val interleavedY = doubleArrayOf(2.0, 4.0, 6.0, 8.0)

    @Test
    fun `Mann-Whitney U counts the pairs it says it counts`() {
        // x > y pairs: 3>2; 5>2,5>4; 7>2,7>4,7>6 = 6.
        assertEquals(6.0, AnomalyStatistics.mannWhitneyU(interleavedX, interleavedY), 1e-12)
        // Fully separated: every x below every y.
        assertEquals(
            0.0,
            AnomalyStatistics.mannWhitneyU(
                doubleArrayOf(1.0, 2.0, 3.0, 4.0),
                doubleArrayOf(5.0, 6.0, 7.0, 8.0),
            ),
            1e-12,
        )
        // ... and the mirror image is m·n.
        assertEquals(
            16.0,
            AnomalyStatistics.mannWhitneyU(
                doubleArrayOf(5.0, 6.0, 7.0, 8.0),
                doubleArrayOf(1.0, 2.0, 3.0, 4.0),
            ),
            1e-12,
        )
    }

    @Test
    fun `ties count as half a pair`() {
        // x = [1,2,2], y = [2,3]: pooled ranks 1, 3, 3, 3, 5;
        // rank sum of x = 1+3+3 = 7; U = 7 − 3·4/2 = 1.
        val u = AnomalyStatistics.mannWhitneyU(
            doubleArrayOf(1.0, 2.0, 2.0),
            doubleArrayOf(2.0, 3.0),
        )
        assertEquals(1.0, u, 1e-12)
        // Σ(t³−t) over the single tie group of three 2's = 27 − 3 = 24.
        assertEquals(24.0, AnomalyStatistics.tieTerm(doubleArrayOf(1.0, 2.0, 2.0), doubleArrayOf(2.0, 3.0)), 1e-12)
    }

    @Test
    fun `the naive normal approximation matches the textbook value`() {
        // p̂ = 6/16 = 0.375; Var = (N+1)/(12·m·n) = 9/192 = 0.046875;
        // z = (0.375 − 0.5)/0.21651 = −0.57735; p = 2(1 − Φ(0.57735)) = 0.5637.
        val test = AnomalyStatistics.mannWhitney(interleavedX, interleavedY)
        assertEquals(0.375, test.effect, 1e-12)
        assertEquals(-0.57735, test.z, 1e-4)
        assertEquals(0.5637, test.pValue, 1e-3)
        assertEquals(4.0, test.nEffCurrent, 1e-12)
    }

    @Test
    fun `Kolmogorov-Smirnov is the sup distance of the two step functions`() {
        assertEquals(
            1.0,
            AnomalyStatistics.ksStatistic(
                doubleArrayOf(1.0, 2.0, 3.0, 4.0),
                doubleArrayOf(5.0, 6.0, 7.0, 8.0),
            ),
            1e-12,
        )
        assertEquals(0.25, AnomalyStatistics.ksStatistic(interleavedX, interleavedY), 1e-12)
    }

    @Test
    fun `the Kolmogorov distribution matches its published values`() {
        // Q(1) = 2(e^-2 − e^-8 + e^-18 − …) = 0.26999967.
        assertEquals(0.26999967, AnomalyStatistics.kolmogorovQ(1.0), 1e-7)
        assertEquals(1.0, AnomalyStatistics.kolmogorovQ(0.0), 1e-12)
        assertTrue(AnomalyStatistics.kolmogorovQ(3.0) < 1e-7)
    }

    @Test
    fun `the normal CDF is accurate enough for a p-value`() {
        assertEquals(0.5, AnomalyStatistics.normalCdf(0.0), 1e-9)
        assertEquals(0.841344746, AnomalyStatistics.normalCdf(1.0), 1e-6)
        assertEquals(0.977249868, AnomalyStatistics.normalCdf(2.0), 1e-6)
        assertEquals(0.0455, AnomalyStatistics.twoSidedNormalP(2.0), 1e-4)
    }

    // ------------------------------------------------------- autocorrelation

    /** AR(1) with a known φ, built the same way the device integrates. */
    private fun ar1(n: Int, phi: Double, seed: Long): DoubleArray {
        val rng = SyntheticSeries.Lcg(seed)
        val out = DoubleArray(n)
        var x = 0.0
        for (t in 0 until n) {
            // Box–Muller from the LCG: deterministic normal innovations.
            val u1 = rng.nextDouble()
            val u2 = rng.nextDouble()
            val g = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2)
            x = phi * x + g
            out[t] = x
        }
        return out
    }

    @Test
    fun `lag-1 autocorrelation recovers the AR(1) parameter`() {
        for (phi in listOf(0.0, 0.5, 0.8, 0.9)) {
            val rho = AnomalyStatistics.lag1(ar1(50_000, phi, seed = 42L + (phi * 10).toLong()))
            assertTrue(abs(rho - phi) < 0.02, "φ=$phi gave ρ₁=$rho")
        }
    }

    @Test
    fun `the integrated autocorrelation time matches the AR(1) closed form`() {
        val series = ar1(50_000, phi = 0.8, seed = 7L)
        // τ = (1+φ)/(1−φ) = 9 for φ = 0.8.
        val integrated = AnomalyStatistics.autocorrelation(
            series,
            AutocorrelationMethod.INTEGRATED,
        )!!
        val ar1Form = AnomalyStatistics.autocorrelation(series, AutocorrelationMethod.AR1_LAG1)!!
        assertTrue(abs(integrated.time - 9.0) < 1.5, "τ_int = ${integrated.time}")
        assertTrue(abs(ar1Form.time - 9.0) < 1.5, "τ_AR1 = ${ar1Form.time}")
        assertTrue(integrated.lagsUsed > 5)
        // 50 000 correlated samples are worth ~5 500 independent ones.
        assertEquals(50_000 / integrated.time, integrated.effectiveSize(50_000), 1e-9)
    }

    @Test
    fun `white noise has an autocorrelation time of one`() {
        val white = ar1(20_000, phi = 0.0, seed = 3L)
        val ac = AnomalyStatistics.autocorrelation(white)!!
        assertTrue(ac.time < 1.6, "τ = ${ac.time}")
        assertEquals(20_000.0, AnomalyStatistics.effectiveSize(20_000, ac.time), 20_000 * 0.4)
    }

    @Test
    fun `a constant or too short series has no autocorrelation time`() {
        assertNull(AnomalyStatistics.autocorrelation(doubleArrayOf(1.0, 1.0)))
        val constant = AnomalyStatistics.autocorrelation(DoubleArray(100) { 0.12 })!!
        assertEquals(1.0, constant.time, 1e-12)
    }

    // ------------------------------------------------------ N_eff correction

    @Test
    fun `the N_eff correction shrinks the evidence by about the square root of tau`() {
        val current = ar1(600, phi = 0.8, seed = 11L)
        val baseline = ar1(3_600, phi = 0.8, seed = 12L).map { it + 0.4 }.toDoubleArray()
        val naive = AnomalyStatistics.mannWhitney(current, baseline)
        val evidence = AnomalyStatistics.compare(current, baseline)!!
        val corrected = evidence.mannWhitney
        assertEquals(naive.effect, corrected.effect, 1e-12)
        // Same data, same statistic — only the variance changed.
        assertTrue(abs(corrected.z) < abs(naive.z))
        val ratio = abs(naive.z) / abs(corrected.z)
        assertTrue(ratio > 2.0 && ratio < 4.5, "z shrank by ×$ratio, expected ≈ √9")
        assertEquals(600 / evidence.currentAutocorrelation.time, corrected.nEffCurrent, 1e-9)
    }

    @Test
    fun `compare needs two usable series`() {
        assertNull(AnomalyStatistics.compare(doubleArrayOf(1.0), DoubleArray(100) { it.toDouble() }))
        assertNotNull(
            AnomalyStatistics.compare(
                ar1(300, phi = 0.5, seed = 1L),
                ar1(600, phi = 0.5, seed = 2L),
            ),
        )
    }

    @Test
    fun `an identical stationary window is not evidence of anything`() {
        val series = SyntheticSeries.stationary(seconds = 7_200, seed = 2026L)
        val current = series.copyOfRange(6_000, 6_600)
        val baseline = series.copyOfRange(2_000, 5_600)
        val evidence = AnomalyStatistics.compare(current, baseline)!!
        assertTrue(evidence.mannWhitney.pValue > 0.01, "p = ${evidence.mannWhitney.pValue}")
        assertTrue(evidence.kolmogorovSmirnov.pValue > 0.01)
        // The synthetic device integrator gives ρ₁ ≈ 0.8, τ ≈ 9.
        assertTrue(evidence.baselineAutocorrelation.lag1 > 0.6)
    }
}
