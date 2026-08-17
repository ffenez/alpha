package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A/B statistics (spec §9): net counts with time scaling, the IAEA σ_net, the
 * two Poisson-appropriate statistics with their documented switch, and the
 * closed verdict vocabulary of spec §8.
 */
class AbAnalysisTest {

    private fun counting(counts: Double, seconds: Double) = AbAnalysis.Counting(counts, seconds)

    // --- net counts and σ_net (IAEA [R4]) ---

    @Test
    fun `net subtracts the background scaled by the time ratio`() {
        // Gross 1000 counts in 100 s; background 600 counts in 300 s.
        // r = 100/300 → net = 1000 − 600/3 = 800.
        val net = assertNotNull(AbAnalysis.net(counting(1000.0, 100.0), counting(600.0, 300.0)))
        assertEquals(1.0 / 3.0, net.ratio, 1e-12)
        assertEquals(800.0, net.net, 1e-9)
        // σ_net = √(G + B·r²) = √(1000 + 600/9)
        assertEquals(sqrt(1000.0 + 600.0 / 9.0), net.sigma, 1e-9)
    }

    @Test
    fun `equal live times reduce sigma to the square root of the sum`() {
        val net = assertNotNull(AbAnalysis.net(counting(400.0, 60.0), counting(300.0, 60.0)))
        assertEquals(1.0, net.ratio, 1e-12)
        assertEquals(100.0, net.net, 1e-9)
        assertEquals(sqrt(700.0), net.sigma, 1e-9)
    }

    @Test
    fun `a longer background measurement lowers the uncertainty of the net`() {
        val short = assertNotNull(AbAnalysis.net(counting(1000.0, 100.0), counting(200.0, 100.0)))
        val long = assertNotNull(AbAnalysis.net(counting(1000.0, 100.0), counting(2000.0, 1000.0)))
        // Same background rate (2 cps), same net (800), but ten times the
        // background exposure: σ must be smaller.
        assertEquals(800.0, short.net, 1e-9)
        assertEquals(800.0, long.net, 1e-9)
        assertTrue(long.sigma < short.sigma)
    }

    @Test
    fun `net is refused without a live time`() {
        assertNull(AbAnalysis.net(counting(10.0, 0.0), counting(10.0, 10.0)))
        assertNull(AbAnalysis.net(counting(10.0, 10.0), counting(10.0, 0.0)))
    }

    // --- statistics and the switch ---

    @Test
    fun `chi square z is the net over its own sigma and matches the rate form`() {
        val comparison = assertNotNull(
            AbAnalysis.compareCounts("t", counting(1000.0, 100.0), counting(600.0, 300.0)),
        )
        assertEquals(AbAnalysis.Method.CHI_SQUARE, comparison.method)
        assertEquals(comparison.net / comparison.netSigma, comparison.zChiSquare, 1e-9)
        // Rate difference and its σ carry the same significance.
        assertEquals(
            comparison.rateDiff / comparison.rateDiffSigma,
            comparison.zChiSquare,
            1e-9,
        )
        assertEquals(10.0 - 2.0, comparison.rateDiff, 1e-9)
    }

    @Test
    fun `identical measurements are consistent with z near zero`() {
        val comparison = assertNotNull(
            AbAnalysis.compareCounts("t", counting(500.0, 60.0), counting(500.0, 60.0)),
        )
        assertEquals(0.0, comparison.z, 1e-12)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
        assertEquals(0.0, comparison.zLikelihoodRatio, 1e-12)
    }

    @Test
    fun `the method switch follows the documented count threshold`() {
        val low = AbAnalysis.NORMAL_APPROX_MIN_COUNTS - 1
        val ok = AbAnalysis.NORMAL_APPROX_MIN_COUNTS
        assertEquals(AbAnalysis.Method.CHI_SQUARE, AbAnalysis.methodFor(ok, ok))
        assertEquals(AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO, AbAnalysis.methodFor(low, ok))
        assertEquals(AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO, AbAnalysis.methodFor(ok, low))
        assertEquals(AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO, AbAnalysis.methodFor(0.0, 0.0))
    }

    @Test
    fun `low counts are judged by the likelihood ratio, high counts by chi square`() {
        val low = assertNotNull(
            AbAnalysis.compareCounts("low", counting(9.0, 60.0), counting(2.0, 60.0)),
        )
        assertEquals(AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO, low.method)
        assertEquals(low.zLikelihoodRatio, low.z, 1e-12)

        val high = assertNotNull(
            AbAnalysis.compareCounts("high", counting(900.0, 60.0), counting(200.0, 60.0)),
        )
        assertEquals(AbAnalysis.Method.CHI_SQUARE, high.method)
        assertEquals(high.zChiSquare, high.z, 1e-12)
    }

    @Test
    fun `both statistics agree closely when counts are large`() {
        val comparison = assertNotNull(
            AbAnalysis.compareCounts("t", counting(10_000.0, 100.0), counting(9_800.0, 100.0)),
        )
        // Asymptotic equivalence: within a few percent at these counts.
        val relative = abs(comparison.zLikelihoodRatio - comparison.zChiSquare) /
            abs(comparison.zChiSquare)
        assertTrue(relative < 0.05, "LR ${comparison.zLikelihoodRatio} vs χ² ${comparison.zChiSquare}")
    }

    @Test
    fun `deviance is zero for equal rates and positive otherwise`() {
        // Equal rates: 100 counts in 100 s vs 200 counts in 200 s.
        assertEquals(0.0, AbAnalysis.poissonDeviance(100.0, 100.0, 200.0, 200.0), 1e-9)
        assertTrue(AbAnalysis.poissonDeviance(150.0, 100.0, 200.0, 200.0) > 0.0)
        // No counts at all: nothing to test.
        assertEquals(0.0, AbAnalysis.poissonDeviance(0.0, 100.0, 0.0, 100.0))
        // A zero count on one side is legal (0·ln0 ≡ 0).
        assertTrue(AbAnalysis.poissonDeviance(10.0, 60.0, 0.0, 60.0) > 0.0)
    }

    @Test
    fun `the sign of the likelihood-ratio z follows the direction of the change`() {
        val more = assertNotNull(
            AbAnalysis.compareCounts("t", counting(20.0, 60.0), counting(5.0, 60.0)),
        )
        val less = assertNotNull(
            AbAnalysis.compareCounts("t", counting(5.0, 60.0), counting(20.0, 60.0)),
        )
        assertTrue(more.z > 0.0)
        assertTrue(less.z < 0.0)
        assertEquals(more.z, -less.z, 1e-9)
    }

    // --- verdicts (spec §8) ---

    @Test
    fun `verdict thresholds are 2 and 4 sigma in both directions`() {
        assertEquals(AbAnalysis.Verdict.CONSISTENT, AbAnalysis.verdictFor(0.0))
        assertEquals(AbAnalysis.Verdict.CONSISTENT, AbAnalysis.verdictFor(1.99))
        assertEquals(AbAnalysis.Verdict.CHANGED, AbAnalysis.verdictFor(2.0))
        assertEquals(AbAnalysis.Verdict.CHANGED, AbAnalysis.verdictFor(-3.5))
        assertEquals(AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE, AbAnalysis.verdictFor(4.0))
        assertEquals(AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE, AbAnalysis.verdictFor(-12.0))
        assertEquals(2.0, AbAnalysis.Z_CHANGED)
        assertEquals(4.0, AbAnalysis.Z_STRONG)
    }

    @Test
    fun `the vocabulary has exactly three verdicts`() {
        assertEquals(3, AbAnalysis.Verdict.entries.size)
    }

    // --- energy windows ---

    @Test
    fun `window comparison uses the per-window counts of both runs`() {
        val calibration = EnergyCalibration(0f, 1f, 0f)
        val a = List(2000) { if (it in 100..299) 10 else 1 }
        val b = List(2000) { 1 }
        val comparisons = AbAnalysis.compareWindows(
            aCounts = a,
            aSeconds = 100,
            aCalibration = calibration,
            bCounts = b,
            bSeconds = 100,
            bCalibration = calibration,
        )
        assertEquals(3, comparisons.size)
        val low = comparisons.first()
        assertEquals(2000.0, low.a.counts, 1e-9)
        assertEquals(200.0, low.b.counts, 1e-9)
        assertEquals(AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE, low.verdict)
        // The untouched windows stay consistent.
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparisons[1].verdict)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparisons[2].verdict)
    }

    // --- full spectrum ---

    @Test
    fun `identical spectra are consistent`() {
        val counts = List(256) { 20 }
        val comparison = assertNotNull(AbAnalysis.compareSpectra(counts, 100, counts, 100))
        assertEquals(256, comparison.channelsUsed)
        assertEquals(0.0, comparison.deviance, 1e-9)
        assertEquals(0.0, comparison.chiSquare, 1e-9)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
    }

    @Test
    fun `a strong extra line makes the spectrum comparison significant`() {
        val background = List(256) { 20 }
        val withPeak = background.mapIndexed { index, value ->
            if (index in 120..126) value + 400 else value
        }
        val comparison = assertNotNull(AbAnalysis.compareSpectra(withPeak, 100, background, 100))
        assertEquals(AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE, comparison.verdict)
        assertTrue(comparison.z > AbAnalysis.Z_STRONG)
    }

    @Test
    fun `spectrum comparison normalizes by live time`() {
        // Same rates, different exposure: B measured twice as long with twice
        // the counts must stay consistent.
        val a = List(256) { 30 }
        val b = List(256) { 60 }
        val comparison = assertNotNull(AbAnalysis.compareSpectra(a, 100, b, 200))
        assertEquals(0.0, comparison.deviance, 1e-6)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
    }

    @Test
    fun `spectrum comparison picks the low-count statistic for sparse channels`() {
        val a = List(256) { 3 }
        val b = List(256) { 2 }
        val comparison = assertNotNull(AbAnalysis.compareSpectra(a, 100, b, 100))
        assertEquals(AbAnalysis.Method.POISSON_LIKELIHOOD_RATIO, comparison.method)

        val dense = List(256) { 300 }
        val dense2 = List(256) { 300 }
        val denseComparison = assertNotNull(AbAnalysis.compareSpectra(dense, 100, dense2, 100))
        assertEquals(AbAnalysis.Method.CHI_SQUARE, denseComparison.method)
    }

    @Test
    fun `spectrum comparison refuses mismatched grids and missing exposure`() {
        assertNull(AbAnalysis.compareSpectra(List(256) { 1 }, 100, List(128) { 1 }, 100))
        assertNull(AbAnalysis.compareSpectra(List(256) { 1 }, 0, List(256) { 1 }, 100))
        assertNull(AbAnalysis.compareSpectra(emptyList(), 100, emptyList(), 100))
        assertNull(AbAnalysis.compareSpectra(List(256) { 0 }, 100, List(256) { 0 }, 100))
    }

    @Test
    fun `the spectrum test is one-sided - a perfect match is not evidence`() {
        val counts = List(256) { 20 }
        val comparison = assertNotNull(AbAnalysis.compareSpectra(counts, 100, counts, 100))
        assertEquals(0.0, comparison.z, "χ² below ν is agreement, not change")
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
    }

    @Test
    fun `wilson-hilferty maps chi square to a sensible significance`() {
        // χ² = ν is the expectation of the null → z ≈ 0.
        assertTrue(abs(AbAnalysis.wilsonHilferty(100.0, 100)) < 0.2)
        // A χ² far above ν is a large significance.
        assertTrue(AbAnalysis.wilsonHilferty(400.0, 100) > 4.0)
        // Degenerate input never blows up.
        assertEquals(0.0, AbAnalysis.wilsonHilferty(10.0, 0))
    }

    // --- dose rate (Gaussian on means) ---

    @Test
    fun `dose statistics describe the readings`() {
        val stats = assertNotNull(AbAnalysis.doseStats(listOf(0.10, 0.12, 0.14, 0.12)))
        assertEquals(4, stats.sampleCount)
        assertEquals(0.12, stats.meanMicroSvH, 1e-12)
        assertEquals(0.10, stats.minMicroSvH, 1e-12)
        assertEquals(0.14, stats.maxMicroSvH, 1e-12)
        assertEquals(sqrt(0.0002), stats.sdMicroSvH, 1e-9)
        assertEquals(stats.sdMicroSvH / 2.0, stats.standardErrorMicroSvH, 1e-12)
        assertNull(AbAnalysis.doseStats(emptyList()))
    }

    @Test
    fun `dose rate comparison uses the standard errors of the means`() {
        val a = assertNotNull(AbAnalysis.doseStats(List(100) { 0.20 } + List(100) { 0.22 }))
        val b = assertNotNull(AbAnalysis.doseStats(List(100) { 0.10 } + List(100) { 0.12 }))
        val comparison = assertNotNull(AbAnalysis.compareDoseRates(a, b))
        assertEquals(0.10, comparison.diffMicroSvH, 1e-9)
        assertTrue(comparison.z > AbAnalysis.Z_STRONG)
        assertEquals(AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE, comparison.verdict)
        assertNull(AbAnalysis.compareDoseRates(a, null))
    }

    @Test
    fun `a single reading gives no spread and therefore no significance`() {
        val a = assertNotNull(AbAnalysis.doseStats(listOf(0.2)))
        val b = assertNotNull(AbAnalysis.doseStats(listOf(0.1)))
        val comparison = assertNotNull(AbAnalysis.compareDoseRates(a, b))
        assertEquals(0.0, comparison.z)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
    }

    // --- distance scenario ---

    @Test
    fun `inverse square prediction falls with the square of the distance`() {
        assertEquals(25.0, assertNotNull(AbAnalysis.inverseSquarePrediction(100.0, 10.0, 20.0)), 1e-9)
        assertEquals(400.0, assertNotNull(AbAnalysis.inverseSquarePrediction(100.0, 10.0, 5.0)), 1e-9)
        assertNull(AbAnalysis.inverseSquarePrediction(100.0, 0.0, 10.0))
        assertNull(AbAnalysis.inverseSquarePrediction(100.0, 10.0, 0.0))
    }

    @Test
    fun `net rate subtracts the background and propagates sigma into cps`() {
        val (rate, sigma) = assertNotNull(
            AbAnalysis.netRate(counting(1000.0, 100.0), counting(600.0, 300.0)),
        )
        assertEquals(8.0, rate, 1e-9)
        assertEquals(sqrt(1000.0 + 600.0 / 9.0) / 100.0, sigma, 1e-9)
    }

    @Test
    fun `algorithm version is pinned`() {
        assertEquals(AlgorithmVersions.AB_ANALYSIS, AbAnalysis.ALGORITHM_VERSION)
    }
}
