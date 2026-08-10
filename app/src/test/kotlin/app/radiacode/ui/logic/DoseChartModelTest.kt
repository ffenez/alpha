package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** One second of raw data = one sub-bucket holding exactly one sample. */
    private fun raw(second: Long, value: Float) = aggregate(second * 1_000L, value)

    // --- folding sub-buckets into columns ---

    @Test
    fun `column keeps true extremes and the time they happened`() {
        val parts = listOf(
            aggregate(0, 0.10f, count = 10, min = 0.05f, max = 0.20f),
            aggregate(1_000, 0.20f, count = 10, min = 0.18f, max = 0.40f),
        )
        val columns = DoseChartModel.fold(parts, 0L, 10_000L, 1, subBucketMillis = 1_000L)
        assertEquals(1, columns.size)
        val c = columns.first()
        assertEquals(0.05f, c.min)
        assertEquals(0.40f, c.max)
        assertEquals(0L, c.minAtMillis)
        assertEquals(1_000L, c.maxAtMillis)
        assertEquals(20, c.sampleCount)
        // Sub-buckets that hold several *different* values cannot give exact
        // quantiles of the raw samples — the column says so.
        assertFalse(c.quantilesExact)
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
        // Every sub-bucket carries a single value → exact order statistics.
        assertTrue(c.quantilesExact)
    }

    @Test
    fun `column carries all five quantiles, ordered`() {
        val parts = (1..10).map { raw(it.toLong(), it.toFloat()) }
        val c = DoseChartModel.fold(parts, 0L, 20_000L, 1).single()
        assertEquals(1f, c.q10)
        assertEquals(3f, c.q25)
        assertEquals(5f, c.median)
        assertEquals(8f, c.q75)
        assertEquals(9f, c.q90)
        assertTrue(c.q10 <= c.q25 && c.q25 <= c.median && c.median <= c.q75 && c.q75 <= c.q90)
        assertEquals(5f, c.iqr)
    }

    @Test
    fun `a single-sample column collapses every quantile onto the value`() {
        val c = DoseChartModel.fold(listOf(raw(0, 0.42f)), 0L, 10_000L, 1).single()
        assertEquals(0.42f, c.q10)
        assertEquals(0.42f, c.q25)
        assertEquals(0.42f, c.median)
        assertEquals(0.42f, c.q75)
        assertEquals(0.42f, c.q90)
        assertEquals(0.42f, c.min)
        assertEquals(0.42f, c.max)
        assertEquals(0f, c.iqr)
        assertTrue(c.quantilesExact)
    }

    @Test
    fun `duplicate timestamps inside one second are honest about exactness`() {
        // Two readings inside the same second: SQL folds them into one
        // sub-bucket whose mean is neither of them.
        val duplicate = DoseAggregate(
            startMillis = 0L,
            minMicroSvH = 0.10f,
            maxMicroSvH = 0.30f,
            sumMicroSvH = 0.40,
            sumSqMicroSvH = 0.10,
            sampleCount = 2,
        )
        val c = DoseChartModel.fold(listOf(duplicate, raw(1, 0.5f)), 0L, 10_000L, 1).single()
        assertFalse(c.quantilesExact)
        assertEquals(0.10f, c.min)
        assertEquals(0.5f, c.max)
        assertEquals(3, c.sampleCount)
    }

    @Test
    fun `out-of-order sub-buckets fold into the same column as sorted ones`() {
        val ordered = (1..5).map { raw(it.toLong(), it.toFloat()) }
        val shuffled = listOf(raw(3, 3f), raw(1, 1f), raw(5, 5f), raw(2, 2f), raw(4, 4f))
        val a = DoseChartModel.fold(ordered, 0L, 10_000L, 1).single()
        val b = DoseChartModel.fold(shuffled, 0L, 10_000L, 1).single()
        assertEquals(a.median, b.median)
        assertEquals(a.q10, b.q10)
        assertEquals(a.q90, b.q90)
        assertEquals(a.min, b.min)
        assertEquals(a.max, b.max)
    }

    @Test
    fun `extreme numeric values survive folding without overflow`() {
        val parts = listOf(raw(0, 1e-6f), raw(1, 1e6f), raw(2, 0f))
        val c = DoseChartModel.fold(parts, 0L, 10_000L, 1).single()
        assertEquals(0f, c.min)
        assertEquals(1e6f, c.max)
        assertEquals(1e-6f, c.median)
        assertTrue(c.q90.isFinite())
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

    @Test
    fun `the extremum timestamp carries the resolution it really has`() {
        val parts = listOf(aggregate(0, 1f, count = 60, min = 1f, max = 3f))
        val coarse = DoseChartModel.fold(parts, 0L, 600_000L, 1, subBucketMillis = 60_000L).single()
        assertEquals(60_000L, coarse.extremeWindowMillis)
        val fine = DoseChartModel.fold(listOf(raw(0, 1f)), 0L, 10_000L, 1).single()
        assertEquals(1_000L, fine.extremeWindowMillis)
    }

    // --- window statistics ---

    @Test
    fun `window stats give exact SD and nearest-rank percentiles`() {
        val parts = (1..10).map { aggregate(it * 1_000L, it.toFloat()) }
        val stats = DoseChartModel.windowStats(parts, 0L, 20_000L)
        assertNotNull(stats)
        assertEquals(1f, stats.min)
        assertEquals(10f, stats.max)
        assertEquals(1f, stats.p10)
        assertEquals(3f, stats.q25)
        assertEquals(5f, stats.median)
        assertEquals(8f, stats.q75)
        assertEquals(9f, stats.p90)
        assertEquals(10, stats.sampleCount)
        assertTrue(stats.quantilesExact)
        // Population SD of 1..10 = sqrt(8.25).
        assertTrue(abs(stats.sd - 2.8722813f) < 1e-4f)
        // MAD = median(|xᵢ − 5|); deviations sorted 0,1,1,2,2,3,3,4,4,5 →
        // nearest-rank median 2 (no 1.4826 factor: normality is not assumed).
        assertEquals(2f, stats.mad)
        assertEquals(5f, stats.iqr)
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
        assertEquals(0f, stats.mad)
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

    @Test
    fun `several percentiles in one pass equal the single-percentile answers`() {
        val values = FloatArray(37) { (it * 7 % 37).toFloat() }
        val weights = IntArray(37) { 1 + it % 3 }
        val qs = doubleArrayOf(0.10, 0.25, 0.50, 0.75, 0.90)
        val many = DoseChartModel.weightedPercentiles(values, weights, qs)
        qs.forEachIndexed { i, q ->
            assertEquals(DoseChartModel.weightedPercentile(values, weights, q), many[i])
        }
    }

    @Test
    fun `MAD is the weighted median absolute deviation, no normality factor`() {
        val values = floatArrayOf(1f, 2f, 3f, 4f, 100f)
        val weights = intArrayOf(1, 1, 1, 1, 1)
        val median = DoseChartModel.weightedPercentile(values, weights, 0.5)
        assertEquals(3f, median)
        // |1-3|,|2-3|,|3-3|,|4-3|,|100-3| = 2,1,0,1,97 → median 1.
        assertEquals(1f, DoseChartModel.weightedMad(values, weights, median))
    }

    // --- exact vs approximate quantiles (ADR 004, spec §29/§34/§37G) ---

    /**
     * The honest error status of the approximation used until the P1 sketch
     * lands: quantiles over sub-bucket means keep the median close but shrink
     * the tails inwards. The test states that direction explicitly so a future
     * change of method cannot quietly claim exactness.
     */
    @Test
    fun `sub-bucket quantiles bracket the raw ones and shrink the spread`() {
        val random = java.util.Random(20260810)
        val seconds = 3_600
        val values = FloatArray(seconds) {
            (0.12 + 0.02 * random.nextGaussian()).toFloat().coerceAtLeast(0.001f)
        }
        val exactRows = List(seconds) { raw(it.toLong(), values[it]) }
        val minuteRows = (0 until seconds / 60).map { minute ->
            val slice = values.copyOfRange(minute * 60, minute * 60 + 60)
            DoseAggregate(
                startMillis = minute * 60_000L,
                minMicroSvH = slice.min(),
                maxMicroSvH = slice.max(),
                sumMicroSvH = slice.sumOf { it.toDouble() },
                sumSqMicroSvH = slice.sumOf { it.toDouble() * it },
                sampleCount = slice.size,
            )
        }
        val exact = DoseChartModel.windowStats(exactRows, 0L, 3_600_000L)!!
        val approx = DoseChartModel.windowStats(minuteRows, 0L, 3_600_000L)!!

        assertTrue(exact.quantilesExact)
        assertFalse(approx.quantilesExact, "minute means are not raw samples")
        // The extremes and n stay exact — they come from SQL MIN/MAX/COUNT.
        assertEquals(exact.min, approx.min)
        assertEquals(exact.max, approx.max)
        assertEquals(exact.sampleCount, approx.sampleCount)
        // The median survives averaging almost intact…
        assertTrue(
            abs(approx.median - exact.median) < 0.1f * exact.median,
            "median ${approx.median} vs ${exact.median}",
        )
        // …while the envelope shrinks inwards, which is exactly why the UI
        // must mark it approximate rather than claim raw quantiles.
        assertTrue(approx.p10 > exact.p10, "${approx.p10} vs ${exact.p10}")
        assertTrue(approx.p90 < exact.p90, "${approx.p90} vs ${exact.p90}")
    }

    // --- raw dots threshold ---

    @Test
    fun `raw dots appear only when a column holds a handful of samples`() {
        assertTrue(DoseChartModel.rawDotsVisible(1_000L))
        assertTrue(DoseChartModel.rawDotsVisible(DoseChartModel.RAW_DOTS_MAX_BUCKET_MILLIS))
        assertTrue(!DoseChartModel.rawDotsVisible(60_000L))
    }

    @Test
    fun `short windows are served by the exact path, long ones are not`() {
        // A 1-hour window aggregates into 1-second sub-buckets: exact.
        assertEquals(
            1_000L,
            DoseChartModel.subBucketMillis(DoseChartModel.bucketMillis(3_600_000L)),
        )
        // A 24-hour window cannot: its sub-buckets average many seconds.
        assertTrue(
            DoseChartModel.subBucketMillis(DoseChartModel.bucketMillis(24 * 3_600_000L)) > 1_000L,
        )
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
        assertTrue(stats.q25 <= stats.median && stats.median <= stats.q75)
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
}
