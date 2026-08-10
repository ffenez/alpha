package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoseHistogramTest {

    private fun aggregate(start: Long, value: Float, count: Int = 1) = DoseAggregate(
        startMillis = start,
        minMicroSvH = value,
        maxMicroSvH = value,
        sumMicroSvH = value.toDouble() * count,
        sumSqMicroSvH = value.toDouble() * value * count,
        sampleCount = count,
    )

    @Test
    fun `bins count measured samples, not sub-buckets`() {
        val parts = listOf(
            aggregate(0, 0.10f, count = 60),
            aggregate(1_000, 0.10f, count = 60),
            aggregate(2_000, 0.30f, count = 1),
        )
        val h = DoseHistograms.build(parts, 0L, 10_000L)
        assertNotNull(h)
        assertEquals(121, h.totalCount)
        assertEquals(120, h.counts[h.binOf(0.10f)])
        assertEquals(1, h.counts[h.binOf(0.30f)])
    }

    /** True for a value from the 1/2/5·10^k family (float tolerance). */
    private fun isNiceStep(value: Float): Boolean = (-9..3).any { exponent ->
        listOf(1.0, 2.0, 5.0).any { mantissa ->
            abs(value - mantissa * Math.pow(10.0, exponent.toDouble())) < 1e-4 * value + 1e-12
        }
    }

    @Test
    fun `bin edges sit on nice steps`() {
        val parts = (0 until 20).map { aggregate(it * 1_000L, 0.101f + it * 0.011f) }
        val h = DoseHistograms.build(parts, 0L, 100_000L)!!
        assertTrue(isNiceStep(h.binWidth), "unexpected bin width ${h.binWidth}")
        // The left edge lies on the same grid.
        val steps = h.lowEdge / h.binWidth
        assertTrue(abs(steps - Math.round(steps)) < 1e-2f, "left edge off grid: ${h.lowEdge}")
    }

    @Test
    fun `every value lands inside the bins`() {
        val parts = (0 until 50).map { aggregate(it * 1_000L, 0.05f + it * 0.007f) }
        val h = DoseHistograms.build(parts, 0L, 100_000L)!!
        assertEquals(parts.size, h.totalCount)
        assertTrue(h.binCount <= DoseHistograms.MAX_BINS)
    }

    @Test
    fun `a flat window still produces one readable bin`() {
        val parts = (0 until 5).map { aggregate(it * 1_000L, 0.12f, count = 60) }
        val h = DoseHistograms.build(parts, 0L, 10_000L)!!
        assertEquals(300, h.totalCount)
        assertTrue(h.binWidth > 0f)
    }

    @Test
    fun `baseline band marks the bins it covers`() {
        val parts = (0 until 30).map { aggregate(it * 1_000L, 0.05f + it * 0.01f) }
        val h = DoseHistograms.build(parts, 0L, 100_000L, baseline = 0.10f..0.20f)!!
        val range = h.baselineBins!!
        assertTrue(h.binLow(range.first) <= 0.10f && h.binHigh(range.first) > 0.10f)
        assertTrue(h.binLow(range.last) <= 0.20f && h.binHigh(range.last) > 0.20f)
    }

    @Test
    fun `alarm marks the first bin that can hold values at or above L1`() {
        val parts = (0 until 40).map { aggregate(it * 1_000L, 0.05f + it * 0.01f) }
        val h = DoseHistograms.build(parts, 0L, 100_000L, alarmLevel = 0.30f)!!
        val first = h.firstAlarmBin!!
        assertTrue(h.binHigh(first) > 0.30f)
        // The bin before it holds only values below the level.
        if (first > 0) assertTrue(h.binHigh(first - 1) <= 0.30f)
    }

    @Test
    fun `no alarm level means no hot bins`() {
        val parts = listOf(aggregate(0, 0.1f))
        assertNull(DoseHistograms.build(parts, 0L, 10_000L)!!.firstAlarmBin)
        assertNull(DoseHistograms.build(parts, 0L, 10_000L, alarmLevel = 0f)!!.firstAlarmBin)
    }

    @Test
    fun `only the visible window is binned`() {
        val parts = listOf(aggregate(0, 0.1f, count = 5), aggregate(90_000, 9f, count = 5))
        val h = DoseHistograms.build(parts, 0L, 10_000L)!!
        assertEquals(5, h.totalCount)
    }

    @Test
    fun `an empty window has no histogram at all`() {
        assertNull(DoseHistograms.build(emptyList(), 0L, 1_000L))
        assertNull(DoseHistograms.build(listOf(aggregate(0, 1f)), 5_000L, 9_000L))
    }

    @Test
    fun `axis labels span the binned range`() {
        val parts = (0 until 20).map { aggregate(it * 1_000L, 0.05f + it * 0.01f) }
        val h = DoseHistograms.build(parts, 0L, 100_000L)!!
        val labels = DoseHistograms.labelValues(h, count = 4)
        assertEquals(4, labels.size)
        assertTrue(labels.all { it.first in 0f..1f })
        assertTrue(labels.first().second >= h.lowEdge)
        assertTrue(labels.last().second <= h.binHigh(h.binCount - 1))
    }
}
