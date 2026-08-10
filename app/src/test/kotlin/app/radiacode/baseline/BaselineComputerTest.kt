package app.radiacode.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BaselineComputerTest {

    private fun bucket(dose: Float, cps: Float = dose * 200f, seconds: Int = 60) =
        BaselineBucket(avgDoseRateMicroSvH = dose, avgCps = cps, sampleCount = seconds)

    // --- activation threshold ---

    @Test
    fun `empty input stays learning at zero`() {
        val state = BaselineComputer.compute(emptyList())
        assertEquals(BaselineState.Learning(0, 3L * 3600L), state)
    }

    @Test
    fun `below required time stays learning with accumulated seconds`() {
        // 2 h of minute buckets < 3 h requirement.
        val buckets = List(120) { bucket(0.10f) }
        val state = BaselineComputer.compute(buckets)
        assertEquals(BaselineState.Learning(7200, 10800), state)
    }

    @Test
    fun `activates exactly at required time`() {
        val buckets = List(180) { bucket(0.10f) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Active>(state)
        assertEquals(10800, state.baseline.accumulatedSeconds)
    }

    @Test
    fun `excluded spike time does not count towards activation`() {
        // 3 h total, but 30 min of it is a strong spike -> retained 2.5 h < 3 h.
        val buckets = List(150) { bucket(0.10f) } + List(30) { bucket(1.5f) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Learning>(state)
        assertEquals(9000, state.accumulatedSeconds)
    }

    // --- percentile correctness on synthetic data ---

    @Test
    fun `percentiles on uniform synthetic data`() {
        // Values 0.01..1.00 with equal weights: nearest-rank P10=0.10, P50=0.50, P90=0.90.
        val values = (1..100).map { it / 100f }
        val weights = List(100) { 1 }
        assertEquals(0.10f, BaselineComputer.weightedPercentile(values, weights, 0.10))
        assertEquals(0.50f, BaselineComputer.weightedPercentile(values, weights, 0.50))
        assertEquals(0.90f, BaselineComputer.weightedPercentile(values, weights, 0.90))
    }

    @Test
    fun `weighted percentile respects weights`() {
        // 0.1 carries 9x the time of 1.0 -> median and P90 stay at 0.1.
        val values = listOf(0.1f, 1.0f)
        val weights = listOf(90, 10)
        assertEquals(0.1f, BaselineComputer.weightedPercentile(values, weights, 0.5))
        assertEquals(0.1f, BaselineComputer.weightedPercentile(values, weights, 0.90))
        assertEquals(1.0f, BaselineComputer.weightedPercentile(values, weights, 0.95))
    }

    @Test
    fun `unordered input is sorted before ranking`() {
        val values = listOf(0.5f, 0.1f, 0.9f, 0.3f, 0.7f)
        val weights = List(5) { 1 }
        assertEquals(0.5f, BaselineComputer.weightedPercentile(values, weights, 0.5))
    }

    @Test
    fun `band is computed for dose and cps together`() {
        val buckets =
            (1..200).map { bucket(dose = 0.05f + (it % 100) * 0.001f, cps = 20f + (it % 100) * 0.1f) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Active>(state)
        val b = state.baseline
        assertTrue(b.doseLowMicroSvH < b.doseMedianMicroSvH)
        assertTrue(b.doseMedianMicroSvH < b.doseHighMicroSvH)
        assertTrue(b.cpsLow < b.cpsMedian)
        assertTrue(b.cpsMedian < b.cpsHigh)
    }

    // --- spike resistance ---

    @Test
    fun `short strong spike does not move the band`() {
        // 6 h of calm 0.10 plus a 10-minute 2.0 µSv/h spike.
        val calm = List(360) { bucket(0.10f, cps = 20f) }
        val spike = List(10) { bucket(2.0f, cps = 400f) }
        val quiet = BaselineComputer.compute(calm)
        val spiked = BaselineComputer.compute(calm + spike)
        assertIs<BaselineState.Active>(quiet)
        assertIs<BaselineState.Active>(spiked)
        assertEquals(quiet.baseline.doseHighMicroSvH, spiked.baseline.doseHighMicroSvH)
        assertEquals(quiet.baseline.cpsHigh, spiked.baseline.cpsHigh)
    }

    @Test
    fun `moderate excursion below cutoff needs dwell to reach P90`() {
        // 0.2 is below the 3x-median cutoff; 5 % of time at 0.2 must not shift P90.
        val calm = List(380) { bucket(0.10f) }
        val brief = List(20) { bucket(0.20f) }
        val state = BaselineComputer.compute(calm + brief)
        assertIs<BaselineState.Active>(state)
        assertEquals(0.10f, state.baseline.doseHighMicroSvH)
    }

    @Test
    fun `sustained new level eventually becomes the baseline`() {
        // Same 0.2 level, but for 30 % of the time: P90 moves — a real change
        // of environment is learned, only transient spikes are rejected.
        val calm = List(280) { bucket(0.10f) }
        val sustained = List(120) { bucket(0.20f) }
        val state = BaselineComputer.compute(calm + sustained)
        assertIs<BaselineState.Active>(state)
        assertEquals(0.20f, state.baseline.doseHighMicroSvH)
    }

    // --- extended statistics (spec §4.1) ---

    @Test
    fun `quartiles and MAD on uniform synthetic data`() {
        // Дозы 0.01…1.00, равные веса: nearest-rank P25=0.25, P75=0.75,
        // медиана 0.50, |xᵢ − 0.5| равномерно 0…0.5 → MAD = 0.25.
        val buckets = (1..100).map { bucket(it / 100f, seconds = 180) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Active>(state)
        val b = state.baseline
        assertEquals(0.25f, b.doseP25MicroSvH, 1e-6f)
        assertEquals(0.50f, b.doseMedianMicroSvH, 1e-6f)
        assertEquals(0.75f, b.doseP75MicroSvH, 1e-6f)
        assertEquals(0.25f, b.doseMadMicroSvH, 1e-6f)
        assertTrue(b.doseP25MicroSvH > b.doseLowMicroSvH)
        assertTrue(b.doseP75MicroSvH < b.doseHighMicroSvH)
    }

    @Test
    fun `MAD is robust where sigma is not`() {
        // 190 buckets at 0.10 plus 10 at 0.25 (below the 3x cutoff, so they
        // stay in): the median deviation is untouched, an RMS spread is not.
        val buckets = List(190) { bucket(0.10f) } + List(10) { bucket(0.25f) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Active>(state)
        assertEquals(0f, state.baseline.doseMadMicroSvH, 1e-6f)
    }

    @Test
    fun `MAD formula matches median of absolute deviations`() {
        val values = listOf(1f, 2f, 4f, 8f, 16f)
        val weights = List(5) { 1 }
        val median = BaselineComputer.weightedPercentile(values, weights, 0.5)
        assertEquals(4f, median)
        // |x − 4| = 3, 2, 0, 4, 12 → sorted 0, 2, 3, 4, 12 → median 3.
        assertEquals(3f, BaselineComputer.weightedMad(values, weights, median))
    }

    @Test
    fun `duration and counts describe what the statistics were built from`() {
        val buckets = List(200) { bucket(0.10f, seconds = 60) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Active>(state)
        val b = state.baseline
        assertEquals(200 * 60L, b.accumulatedSeconds)
        assertEquals(200 * 60L, b.sampleCount, "1 Hz: one sample per second")
        assertEquals(200, b.bucketCount, "n of the order statistics = retained buckets")
    }

    @Test
    fun `excluded spike buckets are not counted in n`() {
        val buckets = List(200) { bucket(0.10f) } + List(20) { bucket(5f) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Active>(state)
        assertEquals(200, state.baseline.bucketCount)
        assertEquals(200 * 60L, state.baseline.accumulatedSeconds)
    }

    @Test
    fun `zero-length buckets are ignored`() {
        val buckets = List(200) { bucket(0.10f) } + List(50) { bucket(9f, seconds = 0) }
        val state = BaselineComputer.compute(buckets)
        assertIs<BaselineState.Active>(state)
        assertEquals(0.10f, state.baseline.doseHighMicroSvH)
    }
}
