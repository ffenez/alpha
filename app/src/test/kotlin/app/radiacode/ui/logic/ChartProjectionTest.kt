package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChartProjectionTest {

    private fun bucket(
        start: Long,
        median: Float,
        min: Float = median,
        max: Float = median,
        sigma: Float = 0f,
    ) = ChartBucket(
        startMillis = start,
        endMillis = start + 1_000,
        min = min,
        max = max,
        median = median,
        mean = median,
        sigma = sigma,
        sampleCount = 1,
    )

    private val scale = LinearDoseScale(1f)

    @Test
    fun `columns are placed by wall-clock time across the plot width`() {
        // Midpoints at 500, 5500, 10500 inside a 0..11000 window.
        val buckets = listOf(bucket(0, 0.5f), bucket(5_000, 0.5f), bucket(10_000, 0.5f))
        val p = ChartProjection.project(buckets, 0L, 11_000L, scale, 0f, 110f, 0f, 100f)
        assertEquals(3, p.count)
        assertEquals(5f, p.x[0], 1e-3f)
        assertEquals(55f, p.x[1], 1e-3f)
        assertEquals(105f, p.x[2], 1e-3f)
    }

    @Test
    fun `value zero sits on the bottom row and the top of the scale on the top row`() {
        val buckets = listOf(bucket(0, 0f), bucket(1_000, 1f))
        val p = ChartProjection.project(buckets, 0L, 2_000L, scale, 0f, 100f, 10f, 100f)
        assertEquals(110f, p.medianY[0], 1e-3f) // top + height
        assertEquals(10f, p.medianY[1], 1e-3f) // top
    }

    @Test
    fun `the envelope and the sigma band come out of the same column`() {
        val buckets = listOf(bucket(0, 0.5f, min = 0.2f, max = 0.8f, sigma = 0.1f))
        val p = ChartProjection.project(buckets, 0L, 1_000L, scale, 0f, 100f, 0f, 100f)
        // Screen y grows downwards: max is above min, +sigma above -sigma.
        assertTrue(p.maxY[0] < p.minY[0])
        assertTrue(p.sigmaHiY[0] < p.sigmaLoY[0])
        // The sigma band is inside the envelope.
        assertTrue(p.sigmaHiY[0] > p.maxY[0])
        assertTrue(p.sigmaLoY[0] < p.minY[0])
    }

    @Test
    fun `columns outside the window are not projected`() {
        val buckets = listOf(bucket(0, 0.5f), bucket(50_000, 0.5f), bucket(90_000, 0.5f))
        val p = ChartProjection.project(buckets, 40_000L, 60_000L, scale, 0f, 100f, 0f, 100f)
        assertEquals(1, p.count)
        assertEquals(1, p.source[0])
    }

    @Test
    fun `a log scale marks unplottable columns instead of pinning them to zero`() {
        val buckets = listOf(bucket(0, 0f), bucket(1_000, 0.1f))
        val p = ChartProjection.project(
            buckets,
            0L,
            2_000L,
            LogDoseScale(0.01f, 10f),
            0f,
            100f,
            0f,
            100f,
        )
        assertFalse(p.plottable[0])
        assertTrue(p.plottable[1])
    }

    @Test
    fun `an empty frame projects to nothing rather than to a degenerate line`() {
        assertEquals(0, ChartProjection.project(emptyList(), 0L, 1_000L, scale, 0f, 10f, 0f, 10f).count)
        assertEquals(
            0,
            ChartProjection.project(listOf(bucket(0, 1f)), 0L, 0L, scale, 0f, 10f, 0f, 10f).count,
        )
    }

    @Test
    fun `the nearest column lookup drives the crosshair`() {
        val buckets = listOf(bucket(0, 0.5f), bucket(5_000, 0.5f), bucket(10_000, 0.5f))
        val p = ChartProjection.project(buckets, 0L, 11_000L, scale, 0f, 110f, 0f, 100f)
        assertEquals(0, p.nearestIndex(0f))
        assertEquals(1, p.nearestIndex(50f))
        assertEquals(2, p.nearestIndex(110f))
        assertEquals(null, ChartPixels.EMPTY.nearestIndex(10f))
    }
}
