package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoseChartModelTest {

    private fun aggregate(
        start: Long,
        value: Float,
        count: Int = 1,
        min: Float = value,
        max: Float = value,
    ) = DoseAggregate(
        startMillis = start,
        minMicroSvH = min,
        maxMicroSvH = max,
        sumMicroSvH = value.toDouble() * count,
        sumSqMicroSvH = value.toDouble() * value * count,
        sampleCount = count,
    )

    // --- folding sub-buckets into columns ---

    @Test
    fun `column keeps true extremes and pooled mean over its sub-buckets`() {
        val parts = listOf(
            aggregate(0, 0.10f, count = 10, min = 0.05f, max = 0.20f),
            aggregate(1_000, 0.20f, count = 10, min = 0.18f, max = 0.40f),
        )
        val columns = DoseChartModel.fold(parts, 0L, 10_000L, 1)
        assertEquals(1, columns.size)
        val c = columns.first()
        assertEquals(0.05f, c.min)
        assertEquals(0.40f, c.max)
        assertEquals(20, c.sampleCount)
        // Pooled mean of ten 0.10 and ten 0.20 samples.
        assertTrue(abs(c.mean - 0.15f) < 1e-5f)
        // Population sigma of that pooled set is exactly 0.05.
        assertTrue(abs(c.sigma - 0.05f) < 1e-5f)
    }

    @Test
    fun `column median is the weighted median of its sub-buckets`() {
        // One long quiet sub-bucket must outweigh a short spike.
        val parts = listOf(
            aggregate(0, 0.10f, count = 100),
            aggregate(1_000, 5.00f, count = 1),
        )
        val c = DoseChartModel.fold(parts, 0L, 10_000L, 1).single()
        assertEquals(0.10f, c.median)
        assertEquals(5.00f, c.max)
    }

    @Test
    fun `empty columns are absent, not interpolated`() {
        val parts = listOf(aggregate(0, 0.1f), aggregate(20_000, 0.3f))
        val columns = DoseChartModel.fold(parts, 0L, 10_000L, 3)
        assertEquals(2, columns.size)
        assertEquals(0L, columns[0].startMillis)
        assertEquals(20_000L, columns[1].startMillis)
    }

    @Test
    fun `sub-buckets outside the frame are dropped`() {
        val parts = listOf(aggregate(-5_000, 9f), aggregate(50_000, 9f), aggregate(0, 0.1f))
        val columns = DoseChartModel.fold(parts, 0L, 10_000L, 2)
        assertEquals(1, columns.size)
        assertEquals(0.1f, columns.single().median)
    }

    // --- window statistics ---

    @Test
    fun `window stats give exact sigma and nearest-rank percentiles`() {
        val parts = (1..10).map { aggregate(it * 1_000L, it.toFloat()) }
        val stats = DoseChartModel.windowStats(parts, 0L, 20_000L)
        assertNotNull(stats)
        assertEquals(1f, stats.min)
        assertEquals(10f, stats.max)
        assertEquals(1f, stats.p10)
        assertEquals(5f, stats.median)
        assertEquals(9f, stats.p90)
        assertEquals(10, stats.sampleCount)
        // Population sigma of 1..10 = sqrt(8.25).
        assertTrue(abs(stats.sigma - 2.8722813f) < 1e-4f)
    }

    @Test
    fun `window stats weight percentiles by measured time`() {
        // 1000 s at 0.1 and 1 s at 9.0: the median must stay at the level the
        // instrument actually spent its time at.
        val parts = listOf(
            aggregate(0, 0.1f, count = 1_000),
            aggregate(1_000, 9.0f, count = 1),
        )
        val stats = DoseChartModel.windowStats(parts, 0L, 10_000L)!!
        assertEquals(0.1f, stats.median)
        assertEquals(0.1f, stats.p90)
        assertEquals(9.0f, stats.max)
        assertEquals(1_001, stats.sampleCount)
    }

    @Test
    fun `window stats of an empty range are null, not zeros`() {
        assertNull(DoseChartModel.windowStats(emptyList(), 0L, 1_000L))
        assertNull(
            DoseChartModel.windowStats(listOf(aggregate(0, 1f)), 50_000L, 60_000L),
        )
    }

    @Test
    fun `weighted percentile matches the baseline engine definition`() {
        val values = floatArrayOf(3f, 1f, 2f)
        val weights = intArrayOf(1, 1, 1)
        assertEquals(1f, DoseChartModel.weightedPercentile(values, weights, 0.0))
        assertEquals(2f, DoseChartModel.weightedPercentile(values, weights, 0.5))
        assertEquals(3f, DoseChartModel.weightedPercentile(values, weights, 1.0))
    }

    // --- raw dots threshold ---

    @Test
    fun `raw dots appear only when a column holds a handful of samples`() {
        assertTrue(DoseChartModel.rawDotsVisible(1_000L))
        assertTrue(DoseChartModel.rawDotsVisible(DoseChartModel.RAW_DOTS_MAX_BUCKET_MILLIS))
        assertTrue(!DoseChartModel.rawDotsVisible(60_000L))
    }

    // --- bounded cost ---

    /**
     * The performance contract of the screen: whatever the range, the frame is
     * built from a fixed row budget and renders a fixed number of columns.
     */
    @Test
    fun `a seven-day window renders no more columns than a fifteen-minute one`() {
        val day = 24L * 3_600_000L
        val short = snapshotOf(spanMillis = 15L * 60_000L)
        val week = snapshotOf(spanMillis = 7 * day)
        val month = snapshotOf(spanMillis = 30 * day)

        for (s in listOf(short, week, month)) {
            assertTrue(
                s.buckets.size <= DoseChartModel.MAX_BUCKETS + 2,
                "columns ${s.buckets.size} exceed the cap",
            )
            assertTrue(
                s.aggregates.size <=
                    (DoseChartModel.MAX_BUCKETS + 2) * DoseChartModel.SUB_BUCKETS_PER_BUCKET,
                "rows ${s.aggregates.size} exceed the query budget",
            )
        }
        // A 30-day range costs the same as a week: the bucket width scales,
        // the geometry does not.
        assertEquals(week.buckets.size, month.buckets.size)
    }

    @Test
    fun `seven days of synthetic data still yield usable statistics`() {
        val snapshot = snapshotOf(spanMillis = 7L * 24 * 3_600_000L)
        val stats = DoseChartModel.windowStats(
            snapshot.aggregates,
            snapshot.fromMillis,
            snapshot.toMillis,
        )!!
        assertTrue(stats.sampleCount > 0)
        assertTrue(stats.p10 <= stats.median && stats.median <= stats.p90)
        assertTrue(stats.min <= stats.p10 && stats.p90 <= stats.max)
    }

    /** A full synthetic range aggregated exactly as the loader would. */
    private fun snapshotOf(spanMillis: Long): DoseSnapshot {
        val bucketMillis = DoseChartModel.bucketMillis(spanMillis)
        val subMillis = DoseChartModel.subBucketMillis(bucketMillis)
        val from = 0L
        val to = spanMillis
        val rows = ArrayList<DoseAggregate>()
        var t = from
        var i = 0
        while (t < to) {
            val level = 0.10f + 0.02f * kotlin.math.sin(i / 24.0).toFloat()
            val samples = (subMillis / 1_000L).toInt().coerceAtLeast(1)
            rows += aggregate(t, level, count = samples, min = level * 0.8f, max = level * 1.4f)
            t += subMillis
            i++
        }
        return DoseChartModel.snapshot(
            aggregates = rows,
            eventTimesMillis = emptyList(),
            alignedFromMillis = from,
            toMillis = to,
            bucketMillis = bucketMillis,
        )
    }

    // --- deviation episodes ---

    @Test
    fun `an episode grows from the journal event across the columns above the level`() {
        val columns = (0 until 10).map { i ->
            val v = if (i in 3..6) 0.9f else 0.1f
            ChartBucket(
                startMillis = i * 1_000L,
                endMillis = (i + 1) * 1_000L,
                min = v,
                max = v,
                median = v,
                mean = v,
                sigma = 0f,
                sampleCount = 1,
            )
        }
        val episodes = DoseEpisodes.around(columns, listOf(4_500L), thresholdMicroSvH = 0.3f)
        assertEquals(1, episodes.size)
        assertEquals(3_000L, episodes.single().fromMillis)
        assertEquals(7_000L, episodes.single().toMillis)
        assertEquals(0.9f, episodes.single().peak)
    }

    @Test
    fun `two events inside one run produce one band`() {
        val columns = (0 until 6).map { i ->
            val v = if (i in 1..4) 0.9f else 0.1f
            ChartBucket(i * 1_000L, (i + 1) * 1_000L, v, v, v, v, 0f, 1)
        }
        val episodes = DoseEpisodes.around(columns, listOf(1_500L, 3_500L), 0.3f)
        assertEquals(1, episodes.size)
    }

    @Test
    fun `an event with no column above the level still gets a one-column band`() {
        val columns = (0 until 4).map { i ->
            ChartBucket(i * 1_000L, (i + 1) * 1_000L, 0.1f, 0.1f, 0.1f, 0.1f, 0f, 1)
        }
        val episodes = DoseEpisodes.around(columns, listOf(2_500L), 5f)
        assertEquals(1, episodes.size)
        assertEquals(2_000L, episodes.single().fromMillis)
        assertEquals(3_000L, episodes.single().toMillis)
    }

    @Test
    fun `events outside the columns are ignored`() {
        val columns = listOf(ChartBucket(0, 1_000, 0.1f, 0.1f, 0.1f, 0.1f, 0f, 1))
        assertTrue(DoseEpisodes.around(columns, listOf(99_000L), 0.3f).isEmpty())
    }
}
