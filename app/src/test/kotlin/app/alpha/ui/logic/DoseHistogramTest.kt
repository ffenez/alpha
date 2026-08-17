package app.alpha.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoseHistogramTest {

    private fun aggregate(start: Long, value: Float, count: Int = 1) = ValueAggregate(
        startMillis = start,
        minMicroSvH = value,
        maxMicroSvH = value,
        sumMicroSvH = value.toDouble() * count,
        sumSqMicroSvH = value.toDouble() * value * count,
        sampleCount = count,
    )

    private fun ready(state: DistributionState): DoseHistogram =
        assertIs<DistributionState.Ready>(state).histogram

    @Test
    fun `bins count measured samples, not sub-buckets`() {
        val parts = (0 until 24).map { aggregate(it * 1_000L, 0.10f + 0.002f * it, count = 60) } +
            aggregate(30_000L, 0.30f, count = 1)
        val h = ready(DoseHistograms.distribution(parts, 0L, 60_000L))
        assertEquals(24 * 60 + 1, h.totalCount)
        assertEquals(1, h.counts[h.binOf(0.30f)])
        assertEquals(25, h.observations)
    }

    /** True for a value from the 1/2/5·10^k family (float tolerance). */
    private fun isNiceStep(value: Float): Boolean = (-9..3).any { exponent ->
        listOf(1.0, 2.0, 5.0).any { mantissa ->
            abs(value - mantissa * Math.pow(10.0, exponent.toDouble())) < 1e-4 * value + 1e-12
        }
    }

    @Test
    fun `bin edges sit on nice steps`() {
        val parts = (0 until 40).map { aggregate(it * 1_000L, 0.101f + it * 0.011f) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 100_000L))
        assertTrue(isNiceStep(h.binWidth), "unexpected bin width ${h.binWidth}")
        // The left edge lies on the same grid.
        val steps = h.lowEdge / h.binWidth
        assertTrue(abs(steps - Math.round(steps)) < 1e-2f, "left edge off grid: ${h.lowEdge}")
    }

    @Test
    fun `Freedman-Diaconis width follows 2 IQR n^(-1_3)`() {
        // 100 values 0.100 .. 0.199 step 0.001, one sample each.
        val parts = (0 until 100).map { aggregate(it * 1_000L, 0.100f + 0.001f * it) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 200_000L))
        assertEquals(BinRule.FREEDMAN_DIACONIS, h.rule)
        assertEquals(100, h.observations)
        // Nearest-rank quartiles: Q25 = 0.124, Q75 = 0.174 -> IQR = 0.050;
        // h = 2·0.05·100^(-1/3) = 0.02154 -> candidate = ceil(0.099/0.02154) = 5,
        // clamped up to MIN_BINS = 8. Snapping the width to the readable family
        // may add a couple of bins, never past the cap.
        assertTrue(h.binCount in DoseHistograms.MIN_BINS..DoseHistograms.MAX_BINS)
        assertTrue(isNiceStep(h.binWidth))
        assertEquals(100, h.totalCount)
    }

    @Test
    fun `a heavy tail is clamped to the readable maximum`() {
        // Tight core plus a far outlier: FD asks for thousands of bins.
        val parts = (0 until 200).map { aggregate(it * 1_000L, 0.100f + 0.00005f * it) } +
            aggregate(900_000L, 5.0f)
        val h = ready(DoseHistograms.distribution(parts, 0L, 1_000_000L))
        assertTrue(h.binCount <= DoseHistograms.MAX_BINS, "bins: ${h.binCount}")
        assertEquals(201, h.totalCount)
        assertTrue(isNiceStep(h.binWidth))
    }

    @Test
    fun `IQR zero falls back to the square-root rule`() {
        // The middle half is a single value, so FD would ask for h = 0.
        val parts = (0 until 40).map { aggregate(it * 1_000L, 0.10f) } +
            (0 until 4).map { aggregate(50_000L + it * 1_000L, 0.50f) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 100_000L))
        assertEquals(BinRule.SQRT_IQR_ZERO, h.rule)
        assertTrue(h.binCount in DoseHistograms.MIN_BINS..DoseHistograms.MAX_BINS)
        assertEquals(44, h.totalCount)
        assertTrue(h.binWidth > 0f)
    }

    @Test
    fun `a degenerate window is one honest bin`() {
        val parts = (0 until 25).map { aggregate(it * 1_000L, 0.12f, count = 60) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 30_000L))
        assertEquals(BinRule.DEGENERATE, h.rule)
        assertEquals(1, h.binCount)
        assertEquals(25 * 60, h.totalCount)
        assertTrue(h.binWidth > 0f)
        assertTrue(h.binLow(0) <= 0.12f && h.binHigh(0) >= 0.12f)
        assertEquals(0, h.binOf(0.12f))
    }

    @Test
    fun `too few observations is an honest state, not a histogram`() {
        val parts = (0 until 10).map { aggregate(it * 1_000L, 0.10f + 0.01f * it) }
        val state = DoseHistograms.distribution(parts, 0L, 20_000L)
        val insufficient = assertIs<DistributionState.Insufficient>(state)
        assertEquals(10, insufficient.observations)
        assertEquals(DoseHistograms.MIN_OBSERVATIONS, insufficient.required)
        assertEquals(
            "недостаточно данных для распределения",
            DoseHistograms.insufficientText(),
        )
    }

    @Test
    fun `every value lands inside the bins`() {
        val parts = (0 until 50).map { aggregate(it * 1_000L, 0.05f + it * 0.007f) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 100_000L))
        assertEquals(parts.size, h.totalCount)
        assertTrue(h.binCount <= DoseHistograms.MAX_BINS)
    }

    @Test
    fun `baseline band marks the bins it covers`() {
        val parts = (0 until 30).map { aggregate(it * 1_000L, 0.05f + it * 0.01f) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 100_000L, baseline = 0.10f..0.20f))
        val range = h.baselineBins!!
        assertTrue(h.binLow(range.first) <= 0.10f && h.binHigh(range.first) > 0.10f)
        assertTrue(h.binLow(range.last) <= 0.20f && h.binHigh(range.last) > 0.20f)
    }

    @Test
    fun `alarm marks the first bin that can hold values at or above L1`() {
        val parts = (0 until 40).map { aggregate(it * 1_000L, 0.05f + it * 0.01f) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 100_000L, alarmLevel = 0.30f))
        val first = h.firstAlarmBin!!
        assertTrue(h.binHigh(first) > 0.30f)
        // The bin before it holds only values below the level.
        if (first > 0) assertTrue(h.binHigh(first - 1) <= 0.30f)
    }

    @Test
    fun `no alarm level means no hot bins`() {
        val parts = (0 until 25).map { aggregate(it * 1_000L, 0.10f + 0.001f * it) }
        assertNull(ready(DoseHistograms.distribution(parts, 0L, 30_000L)).firstAlarmBin)
        assertNull(
            ready(DoseHistograms.distribution(parts, 0L, 30_000L, alarmLevel = 0f)).firstAlarmBin,
        )
    }

    @Test
    fun `only the visible window is binned`() {
        val parts = (0 until 20).map { aggregate(it * 500L, 0.10f + 0.001f * it, count = 5) } +
            aggregate(90_000L, 9f, count = 5)
        val h = ready(DoseHistograms.distribution(parts, 0L, 10_000L))
        assertEquals(100, h.totalCount)
        assertEquals(20, h.observations)
    }

    @Test
    fun `an empty window has no histogram at all`() {
        assertIs<DistributionState.NoData>(DoseHistograms.distribution(emptyList(), 0L, 1_000L))
        assertIs<DistributionState.NoData>(
            DoseHistograms.distribution(listOf(aggregate(0, 1f)), 5_000L, 9_000L),
        )
    }

    @Test
    fun `the legacy build call still answers, with null for every non-ready state`() {
        val enough = (0 until 25).map { aggregate(it * 1_000L, 0.10f + 0.002f * it) }
        @Suppress("DEPRECATION")
        assertNotNull(DoseHistograms.build(enough, 0L, 30_000L))
        @Suppress("DEPRECATION")
        assertNull(DoseHistograms.build(enough.take(5), 0L, 30_000L))
        @Suppress("DEPRECATION")
        assertNull(DoseHistograms.build(emptyList(), 0L, 30_000L))
    }

    @Test
    fun `axis labels span the binned range and say what is counted`() {
        val parts = (0 until 20).map { aggregate(it * 1_000L, 0.05f + it * 0.01f) }
        val h = ready(DoseHistograms.distribution(parts, 0L, 100_000L))
        val labels = DoseHistograms.labelValues(h, count = 4)
        assertEquals(4, labels.size)
        assertTrue(labels.all { it.first in 0f..1f })
        assertTrue(labels.first().second >= h.lowEdge)
        assertTrue(labels.last().second <= h.binHigh(h.binCount - 1))
        // Ось Y считает ПОКАЗАНИЯ прибора, а не секунды экспозиции: при
        // пропусках это разные числа, и подпись обязана называть то, что
        // реально суммируется (`a.sampleCount`). Никакой «частоты» и плотности.
        assertEquals("показаний прибора (≈1 в секунду)", DoseHistograms.countAxisLabel())
    }
}
