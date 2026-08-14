package app.radiacode.ui.logic

import app.radiacode.data.db.DownsampledSample
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartMappingTest {

    private fun bucket(start: Long, avgDoseRate: Float = 0.00001f, count: Int = 60) =
        DownsampledSample(
            bucketStart = start,
            avgDoseRate = avgDoseRate,
            maxDoseRate = avgDoseRate,
            avgCountRate = 10f,
            sampleCount = count,
        )

    @Test
    fun `alignedFrom lands on the bucket grid Room groups by`() {
        // Room buckets by floor(timestamp / bucketMillis) * bucketMillis.
        val from = ChartMapping.alignedFrom(toMillis = 10_500, windowMillis = 4_000, bucketMillis = 1_000)
        assertEquals(6_000, from)
        assertEquals(0L, from % 1_000)
    }

    @Test
    fun `buckets land in their slots and absent slots stay null gaps`() {
        val columns = ChartMapping.toColumns(
            buckets = listOf(bucket(1_000), bucket(3_000)),
            alignedFromMillis = 1_000,
            bucketMillis = 1_000,
            columnCount = 4,
        ) { it.avgDoseRate }
        assertEquals(listOf(0.00001f, null, 0.00001f, null), columns)
    }

    @Test
    fun `buckets outside the window are dropped`() {
        val columns = ChartMapping.toColumns(
            buckets = listOf(bucket(0), bucket(9_000)),
            alignedFromMillis = 1_000,
            bucketMillis = 1_000,
            columnCount = 4,
        ) { it.avgDoseRate }
        assertEquals(listOf(null, null, null, null), columns)
    }

    @Test
    fun `stats cover min avg median max and population sigma`() {
        val stats = ChartMapping.stats(listOf(1f, null, 3f, null, 5f))!!
        assertEquals(1f, stats.min)
        assertEquals(5f, stats.max)
        assertEquals(3f, stats.avg)
        assertEquals(3f, stats.median)
        assertEquals(3, stats.count)
        // Population sigma of {1,3,5} = sqrt(8/3).
        assertTrue(abs(stats.sigma - 1.632993f) < 1e-4f)
    }

    @Test
    fun `median of an even count averages the middle pair`() {
        val stats = ChartMapping.stats(listOf(1f, 2f, 10f, 20f))!!
        assertEquals(6f, stats.median)
    }

    @Test
    fun `y ticks are nice steps below the top`() {
        // yMax 0.42 -> step 0.1 -> 0.1/0.2/0.3/0.4.
        val ticks = ChartMapping.yTicks(0.42f)
        assertEquals(4, ticks.size)
        assertTrue(abs(ticks[0] - 0.1f) < 1e-5f)
        assertTrue(abs(ticks[3] - 0.4f) < 1e-5f)
        // Ticks never reach the top itself.
        assertTrue(ChartMapping.yTicks(50f).all { it < 50f })
        assertTrue(ChartMapping.yTicks(0f).isEmpty())
    }

    @Test
    fun `stats of an empty window are null not zeros`() {
        assertNull(ChartMapping.stats(listOf(null, null)))
    }

    @Test
    fun `dose integration weights each bucket by its measured seconds`() {
        // 0.00003 raw = 0.3 µSv/h; a full hour of samples accumulates 0.3 µSv.
        val fullHour = listOf(bucket(0, avgDoseRate = 0.00003f, count = 3_600))
        assertTrue(abs(ChartMapping.integrateDoseMicroSv(fullHour) - 0.3) < 1e-6)

        // Half the samples missing -> half the dose, not interpolated.
        val halfHour = listOf(bucket(0, avgDoseRate = 0.00003f, count = 1_800))
        assertTrue(abs(ChartMapping.integrateDoseMicroSv(halfHour) - 0.15) < 1e-6)
    }

    @Test
    fun `y scale keeps headroom and includes a reachable alarm line`() {
        // Alarm close to the data: scale stretches so the line stays in frame.
        assertTrue(ChartMapping.yMax(dataMax = 0.2f, alarmLevel = 0.3f) >= 0.3f * 1.15f)
        // Alarm far above the data: columns must not flatten into noise.
        val far = ChartMapping.yMax(dataMax = 0.1f, alarmLevel = 10f)
        assertTrue(far < 1f)
    }

    @Test
    fun `small positive values still draw at least one pixel`() {
        assertEquals(0, ChartMapping.columnHeightPx(0f, 1f, 52))
        assertEquals(1, ChartMapping.columnHeightPx(0.001f, 1f, 52))
        assertEquals(52, ChartMapping.columnHeightPx(1f, 1f, 52))
    }

    @Test
    fun `level rows map top-down and out-of-frame levels are null`() {
        assertEquals(0, ChartMapping.rowForLevel(1f, 1f, 52))
        assertEquals(51, ChartMapping.rowForLevel(0.0001f, 1f, 52))
        assertNull(ChartMapping.rowForLevel(1.5f, 1f, 52))
        assertNull(ChartMapping.rowForLevel(0f, 1f, 52))
    }

    /**
     * P10 и P90 — порядковые статистики, а не интерполяция.
     *
     * Возвращается ИЗМЕРЕННОЕ значение: среднее двух соседних отсчётов —
     * число, которого прибор не показывал, и на экране прибора ему не место.
     */
    @Test
    fun `the band edges are measured values, not interpolations`() {
        val columns = (1..10).map { it.toFloat() as Float? }

        val stats = ChartMapping.stats(columns)!!

        assertTrue(stats.p10 in columns.filterNotNull(), "${stats.p10}")
        assertTrue(stats.p90 in columns.filterNotNull(), "${stats.p90}")
        assertTrue(stats.p10 <= stats.median && stats.median <= stats.p90)
        assertTrue(stats.min <= stats.p10 && stats.p90 <= stats.max)
    }

    @Test
    fun `a single column is its own band`() {
        val stats = ChartMapping.stats(listOf(0.15f))!!

        assertEquals(0.15f, stats.p10)
        assertEquals(0.15f, stats.p90)
        assertEquals(0.15f, stats.median)
    }
}
