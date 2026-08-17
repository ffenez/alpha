package app.alpha.analysis

import app.alpha.analysis.SpectrumCompare.IntervalOutcome
import app.alpha.analysis.SpectrumCompare.RateOutcome
import app.alpha.analysis.SpectrumCompare.Verdict
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SpectrumCompareTest {

    private val calibration = EnergyCalibration(0f, 3f, 0f)

    private fun input(
        counts: List<Int>,
        durationSeconds: Long,
        timestampMillis: Long,
        cal: EnergyCalibration = calibration,
    ) = SpectrumCompare.Input(counts, durationSeconds, cal, timestampMillis)

    // --- interval extraction ---

    @Test
    fun `interval is later minus earlier with delta live time`() {
        val earlier = input(listOf(10, 20, 30, 0), 600, timestampMillis = 1_000_000L)
        val later = input(listOf(15, 26, 30, 4), 1200, timestampMillis = 1_600_000L)

        val outcome = assertIs<IntervalOutcome.Ok>(SpectrumCompare.extractInterval(later, earlier))

        assertEquals(listOf(5, 6, 0, 4), outcome.counts)
        assertEquals(600L, outcome.durationSeconds)
        assertEquals(emptyList(), outcome.warnings)
    }

    @Test
    fun `pick order does not matter — live time orders the snapshots`() {
        val earlier = input(listOf(1, 2), 100, timestampMillis = 0L)
        val later = input(listOf(3, 5), 200, timestampMillis = 100_000L)

        val ab = assertIs<IntervalOutcome.Ok>(SpectrumCompare.extractInterval(earlier, later))
        val ba = assertIs<IntervalOutcome.Ok>(SpectrumCompare.extractInterval(later, earlier))
        assertEquals(ab, ba)
        assertEquals(listOf(2, 3), ab.counts)
    }

    @Test
    fun `start time is end minus delta in SECONDS, converted to millis once`() {
        // The community Diff-Calc bug: mixing ms and s in StartTime. Δt here
        // is 3600 s, so the wall bracket must be exactly 3 600 000 ms.
        val earlier = input(List(4) { 1 }, 600, timestampMillis = 10_000_000L)
        val later = input(List(4) { 9 }, 4200, timestampMillis = 13_600_000L)

        val outcome = assertIs<IntervalOutcome.Ok>(SpectrumCompare.extractInterval(later, earlier))

        assertEquals(3600L, outcome.durationSeconds)
        assertEquals(13_600_000L, outcome.endMillis)
        assertEquals(13_600_000L - 3_600_000L, outcome.startMillis)
        assertEquals(outcome.durationSeconds * 1000L, outcome.endMillis - outcome.startMillis)
    }

    @Test
    fun `equal live times refuse honestly`() {
        val a = input(listOf(1, 2), 600, 0L)
        val b = input(listOf(3, 4), 600, 1_000L)
        val outcome = assertIs<IntervalOutcome.Invalid>(SpectrumCompare.extractInterval(a, b))
        assertTrue("одинаковое время" in outcome.reason)
    }

    @Test
    fun `different channel counts refuse`() {
        val a = input(listOf(1, 2, 3), 600, 0L)
        val b = input(listOf(1, 2), 300, 0L)
        assertIs<IntervalOutcome.Invalid>(SpectrumCompare.extractInterval(a, b))
    }

    @Test
    fun `calibration mismatch refuses interval extraction`() {
        val a = input(List(1024) { 2 }, 1200, 0L)
        val b = input(
            List(1024) { 1 },
            600,
            0L,
            cal = EnergyCalibration(0f, 3.1f, 0f), // ~100 keV off at ch 1023
        )
        val outcome = assertIs<IntervalOutcome.Invalid>(SpectrumCompare.extractInterval(a, b))
        assertTrue("калибровки" in outcome.reason.lowercase())
    }

    @Test
    fun `later snapshot with smaller channel counts means a reset happened`() {
        val earlier = input(listOf(10, 20, 30), 600, 0L)
        val later = input(listOf(12, 5, 31), 1200, 600_000L)
        val outcome = assertIs<IntervalOutcome.Invalid>(
            SpectrumCompare.extractInterval(later, earlier),
        )
        assertTrue("сбрасывалось" in outcome.reason)
    }

    @Test
    fun `wall clock gap much wider than delta warns about interruption`() {
        // Δt = 600 s of live time, but 2 hours passed on the wall clock.
        val earlier = input(listOf(1, 1), 600, timestampMillis = 0L)
        val later = input(listOf(2, 2), 1200, timestampMillis = 7_200_000L)

        val outcome = assertIs<IntervalOutcome.Ok>(SpectrumCompare.extractInterval(later, earlier))
        assertTrue(outcome.warnings.any { "прерывалось" in it })
    }

    @Test
    fun `save order contradicting accumulation order warns`() {
        val earlier = input(listOf(1, 1), 600, timestampMillis = 9_000_000L)
        val later = input(listOf(2, 2), 1200, timestampMillis = 1_000_000L)

        val outcome = assertIs<IntervalOutcome.Ok>(SpectrumCompare.extractInterval(later, earlier))
        assertTrue(outcome.warnings.any { "порядок" in it })
    }

    // --- rate comparison ---

    @Test
    fun `identical measurements difference is zero with Poisson sigma`() {
        val a = input(List(4) { 100 }, 10, 0L)
        val b = input(List(4) { 100 }, 10, 0L)

        val outcome = assertIs<RateOutcome.Ok>(SpectrumCompare.compareRates(a, b))

        for (i in 0 until 4) {
            assertEquals(0f, outcome.diffCps[i], 1e-6f)
            // σ = √(100/100 + 100/100) = √2 cps
            assertEquals(sqrt(2f), outcome.sigmaCps[i], 1e-5f)
        }
        assertEquals(false, outcome.resampled)
        assertEquals(emptyList(), outcome.warnings)
    }

    @Test
    fun `different live times normalize to cps before differencing`() {
        // A: 100 counts in 10 s → 10 cps; B: 100 counts in 100 s → 1 cps.
        val a = input(listOf(100), 10, 0L)
        val b = input(listOf(100), 100, 0L)

        val outcome = assertIs<RateOutcome.Ok>(SpectrumCompare.compareRates(a, b))

        assertEquals(9f, outcome.diffCps[0], 1e-5f)
        // σ = √(100/10² + 100/100²) = √1.01
        assertEquals(sqrt(1.01f), outcome.sigmaCps[0], 1e-5f)
    }

    @Test
    fun `zero live time refuses rate comparison`() {
        val a = input(listOf(1), 0, 0L)
        val b = input(listOf(1), 10, 0L)
        assertIs<RateOutcome.Invalid>(SpectrumCompare.compareRates(a, b))
    }

    @Test
    fun `calibration within tolerance is not resampled`() {
        val a = input(List(1024) { 10 }, 10, 0L)
        val b = input(
            List(1024) { 10 },
            10,
            0L,
            cal = EnergyCalibration(2f, 3f, 0f), // constant 2 keV shift < 5 keV
        )
        val outcome = assertIs<RateOutcome.Ok>(SpectrumCompare.compareRates(a, b))
        assertEquals(false, outcome.resampled)
    }

    @Test
    fun `calibration beyond tolerance resamples B onto A grid with a warning`() {
        // B counts channel ch at 6 keV/ch; A grid is 3 keV/ch: a spike in B
        // channel 100 (600 keV) must land at A channel 200.
        val bCounts = MutableList(1024) { 0 }
        bCounts[100] = 1000
        val a = input(List(1024) { 0 }, 10, 0L)
        val b = input(bCounts, 10, 0L, cal = EnergyCalibration(0f, 6f, 0f))

        val outcome = assertIs<RateOutcome.Ok>(SpectrumCompare.compareRates(a, b))

        assertTrue(outcome.resampled)
        assertTrue(outcome.warnings.any { "пересчитан" in it })
        // diff = −B rate; the spike spreads over A channels 199..201 (a 6 keV
        // source bin covers two 3 keV target bins), total preserved.
        val total = outcome.diffCps.sum()
        assertEquals(-100f, total, 1e-3f) // 1000 counts / 10 s
        val around = (199..201).sumOf { -outcome.diffCps[it].toDouble() }
        assertEquals(100.0, around, 1e-3)
        assertEquals(0f, outcome.diffCps[100], 1e-6f)
    }

    // --- resampling ---

    @Test
    fun `resample onto the same grid is the identity`() {
        val counts = List(64) { (it * 13) % 7 }
        val result = SpectrumCompare.resample(counts, calibration, calibration, 64)
        for (i in counts.indices) {
            // float half-channel edges leak ~1e-6 of a count to neighbors
            assertEquals(counts[i].toDouble(), result[i], 1e-3)
        }
    }

    @Test
    fun `resample preserves total counts for interior bins`() {
        val counts = MutableList(256) { 0 }
        counts[50] = 500
        counts[51] = 300
        counts[120] = 200
        val from = EnergyCalibration(0f, 3f, 0f)
        val to = EnergyCalibration(-1.7f, 2.9f, 1e-5f)

        val result = SpectrumCompare.resample(counts, from, to, 512)

        assertEquals(1000.0, result.sum(), 1e-3)
    }

    // --- region verdicts ---

    private fun rateOutcome(diff: List<Float>, sigma: List<Float>) = RateOutcome.Ok(
        diffCps = diff,
        sigmaCps = sigma,
        resampled = false,
        bCountsOnGrid = List(diff.size) { 0f },
        calibration = calibration, // 3 keV per channel
        warnings = emptyList(),
    )

    @Test
    fun `strong excess in one band is flagged, quiet bands stay noise`() {
        val n = 1024
        val diff = MutableList(n) { 0f }
        val sigma = MutableList(n) { 0.1f }
        // Region 300–700 keV = channels 100..233 (3 keV/ch). Put a clear
        // excess there: z = Σd/√(Σσ²) = 134·0.5 / (0.1·√134) ≈ 57.9.
        for (ch in 100..233) diff[ch] = 0.5f

        val verdicts = SpectrumCompare.regionVerdicts(rateOutcome(diff, sigma), n)

        val byBand = verdicts.associateBy { it.startKeV }
        assertEquals(Verdict.EXCESS, byBand.getValue(300f).verdict)
        assertEquals(Verdict.NOISE, byBand.getValue(0f).verdict)
        assertEquals(Verdict.NOISE, byBand.getValue(100f).verdict)
        assertEquals(Verdict.NOISE, byBand.getValue(700f).verdict)
        assertEquals(Verdict.NOISE, byBand.getValue(1500f).verdict)
        assertTrue(byBand.getValue(300f).z > 4f)
    }

    @Test
    fun `deficit mirrors excess`() {
        val n = 1024
        val diff = MutableList(n) { 0f }
        val sigma = MutableList(n) { 0.1f }
        for (ch in 100..233) diff[ch] = -0.5f
        val verdicts = SpectrumCompare.regionVerdicts(rateOutcome(diff, sigma), n)
        assertEquals(
            Verdict.DEFICIT,
            verdicts.first { it.startKeV == 300f }.verdict,
        )
    }

    @Test
    fun `verdict ladder is cautious at the boundaries`() {
        assertEquals(Verdict.NOISE, SpectrumCompare.verdictFor(1.99f))
        assertEquals(Verdict.POSSIBLE_EXCESS, SpectrumCompare.verdictFor(2f))
        assertEquals(Verdict.POSSIBLE_EXCESS, SpectrumCompare.verdictFor(3.99f))
        assertEquals(Verdict.EXCESS, SpectrumCompare.verdictFor(4f))
        assertEquals(Verdict.NOISE, SpectrumCompare.verdictFor(-1.99f))
        assertEquals(Verdict.POSSIBLE_DEFICIT, SpectrumCompare.verdictFor(-2f))
        assertEquals(Verdict.DEFICIT, SpectrumCompare.verdictFor(-4f))
    }

    @Test
    fun `region z uses quadrature sigma`() {
        val n = 1024
        val diff = MutableList(n) { 0f }
        val sigma = MutableList(n) { 0f }
        // Two channels in the 0–100 keV band (channels 0..33).
        diff[0] = 3f
        diff[1] = 1f
        sigma[0] = 1f
        sigma[1] = 2f
        val verdict = SpectrumCompare.regionVerdicts(rateOutcome(diff, sigma), n)
            .first { it.startKeV == 0f }
        assertEquals(4f, verdict.diffCps, 1e-6f)
        assertEquals(sqrt(5f), verdict.sigmaCps, 1e-6f)
        assertEquals(4f / sqrt(5f), verdict.z, 1e-5f)
    }

    // --- chart aggregation ---

    @Test
    fun `diff aggregation sums differences and adds sigma in quadrature`() {
        val diff = listOf(1f, 2f, -1f, 4f)
        val sigma = listOf(3f, 4f, 0f, 1f)

        val columns = SpectrumCompare.aggregateDiff(diff, sigma, 0..3, 2)

        assertEquals(listOf(3f, 3f), columns.diff)
        assertEquals(5f, columns.sigma[0], 1e-6f) // √(9+16)
        assertEquals(1f, columns.sigma[1], 1e-6f)
    }

    // --- calibration distance ---

    @Test
    fun `calibration delta is the worst-case energy shift`() {
        val a = EnergyCalibration(0f, 3f, 0f)
        val b = EnergyCalibration(1f, 3f, 0f)
        assertEquals(1f, SpectrumCompare.calibrationDeltaKeV(a, b, 1024), 1e-5f)

        val c = EnergyCalibration(0f, 3.01f, 0f)
        // worst at the last channel: 1023 · 0.01
        assertEquals(10.23f, SpectrumCompare.calibrationDeltaKeV(a, c, 1024), 1e-3f)
    }
}
