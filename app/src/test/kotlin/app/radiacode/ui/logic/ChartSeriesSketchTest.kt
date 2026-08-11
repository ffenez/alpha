package app.radiacode.ui.logic

import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.analysis.quantiles.QuantileDiagnostics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

/** Deterministic pseudo-random stream, identical on every machine. */
private class Lcg(private var state: Long = 12345L) {
    fun nextUnit(): Double {
        state = state * 6364136223846793005L + 1442695040888963407L
        return (state ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}

/** One stored hour of 1 Hz samples with a given value generator. */
private fun hourSlice(startMillis: Long, values: FloatArray): HourSlice {
    var min = Float.MAX_VALUE
    var max = -Float.MAX_VALUE
    var minAt = startMillis
    var maxAt = startMillis
    values.forEachIndexed { i, v ->
        if (v < min) {
            min = v
            minAt = startMillis + i * 1000L
        }
        if (v > max) {
            max = v
            maxAt = startMillis + i * 1000L
        }
    }
    return HourSlice(
        startMillis = startMillis,
        sampleCount = values.size,
        min = min,
        max = max,
        minAtMillis = minAt,
        maxAtMillis = maxAt,
        sketch = KllSketch.of(values),
    )
}

private fun background(seed: Long, level: Float = 0.10f, spread: Float = 0.03f): FloatArray {
    val rng = Lcg(seed)
    return FloatArray(3600) { (level + rng.nextUnit() * spread).toFloat() }
}

/**
 * The two query paths of ADR 004 as the chart actually uses them: where the
 * threshold is, what a column may be built from, and that nothing a short
 * transient carries is lost on the way to a 30-day window (CHART SPEC §21,
 * §28–§30, §34).
 */
class QuantilePathTest {

    @Test
    fun `short windows take the exact path, long ones the sketch path`() {
        assertEquals(QuantileMethod.EXACT_RAW, QuantilePaths.methodFor(15 * 60_000L))
        assertEquals(QuantileMethod.EXACT_RAW, QuantilePaths.methodFor(HOUR))
        assertEquals(QuantileMethod.EXACT_RAW, QuantilePaths.methodFor(6 * HOUR))
        assertEquals(QuantileMethod.KLL_SKETCH, QuantilePaths.methodFor(6 * HOUR + 1))
        assertEquals(QuantileMethod.KLL_SKETCH, QuantilePaths.methodFor(DAY))
        assertEquals(QuantileMethod.KLL_SKETCH, QuantilePaths.methodFor(30 * DAY))
    }

    @Test
    fun `a sketch column is always a whole number of stored hours`() {
        for (span in listOf(6 * HOUR + 1, DAY, 2 * DAY, 7 * DAY, 30 * DAY)) {
            val bucket = QuantilePaths.bucketMillis(span, QuantileMethod.KLL_SKETCH)
            assertEquals(0L, bucket % QuantilePaths.SKETCH_PERIOD_MILLIS, "span $span")
            assertTrue(bucket >= QuantilePaths.SKETCH_PERIOD_MILLIS, "span $span")
        }
    }

    @Test
    fun `column counts stay readable on the long path`() {
        assertEquals(HOUR, QuantilePaths.bucketMillis(7 * DAY, QuantileMethod.KLL_SKETCH))
        assertEquals(168, (7 * DAY / HOUR).toInt())
        assertEquals(4 * HOUR, QuantilePaths.bucketMillis(30 * DAY, QuantileMethod.KLL_SKETCH))
        assertEquals(180, (30 * DAY / (4 * HOUR)).toInt())
    }

    @Test
    fun `the exact path keeps the P0 geometry`() {
        val span = 6 * HOUR
        assertEquals(
            ChartSeriesModel.bucketMillis(span),
            QuantilePaths.bucketMillis(span, QuantileMethod.EXACT_RAW),
        )
        assertEquals(1_000L, QuantilePaths.exactSubBucketMillis())
    }

    @Test
    fun `row budgets are the ones the performance target of §34 asks for`() {
        // Exact path: one row per raw sample, bounded by its own threshold.
        assertTrue(QuantilePaths.exactRowBudget(QuantilePaths.EXACT_MAX_SPAN_MILLIS) <= 21_601)
        // Long path: one row per stored hour — 30 days is 722 rows, not 2.6 M.
        assertEquals(722, QuantilePaths.sketchRowBudget(30 * DAY))
        assertTrue(QuantilePaths.sketchRowBudget(30 * DAY) < 1_000)
        // And the long path is never allowed to fall back to raw rows silently.
        assertTrue(QuantilePaths.methodFor(30 * DAY) != QuantileMethod.EXACT_RAW)
    }

    @Test
    fun `metadata names the method, the version and the accuracy parameter`() {
        val stamp = QuantileMetadata.stamp(QuantileMethod.KLL_SKETCH, k = 128)
        assertTrue(stamp.contains("kll_sketch"), stamp)
        assertTrue(stamp.contains("quantile_sketch"), stamp)
        assertTrue(stamp.contains("128"), stamp)
        val exactStamp = QuantileMetadata.stamp(QuantileMethod.EXACT_RAW)
        assertTrue(exactStamp.contains("exact_raw"), exactStamp)
        assertTrue(QuantileMetadata.label(QuantileMethod.KLL_SKETCH, 128).contains("k=128"))
    }
}

class ChartSeriesSketchTest {

    @Test
    fun `columns are folded from merged hourly sketches, not from hourly quantiles`() {
        // Hour A sits low and holds three quarters of the samples; hour B is
        // high. The median of the union is inside A — the average of the two
        // hourly medians (the forbidden §28 shortcut) would be far above it.
        val low = FloatArray(3600) { 0.10f }
        val high = FloatArray(1200) { 1.00f }
        val slices = listOf(
            hourSlice(0, low),
            hourSlice(HOUR, high),
        )
        val fold = ChartSeriesModel.foldSketches(slices, 0, 2 * HOUR, 1)
        val column = fold.buckets.single()
        assertEquals(0.10f, column.median, 1e-4f)
        assertEquals(4800, column.sampleCount)
        assertEquals(QuantileMethod.KLL_SKETCH, column.method)
        assertTrue(!column.quantilesExact)
    }

    @Test
    fun `merged column quantiles track the exact quantiles of the raw data`() {
        val hours = (0 until 4).map { background(seed = 100L + it) }
        val slices = hours.mapIndexed { i, values -> hourSlice(i * HOUR, values) }
        val fold = ChartSeriesModel.foldSketches(slices, 0, 4 * HOUR, 1)
        val column = fold.buckets.single()

        val all = FloatArray(hours.sumOf { it.size })
        var at = 0
        hours.forEach { it.copyInto(all, at).also { _ -> at += it.size } }
        val sketch = assertNotNull(fold.windowSketch)
        val comparison = QuantileDiagnostics.compare(all, sketch)
        assertTrue(comparison.maxRankError <= 0.02, "rank error ${comparison.maxRankError}")
        assertEquals(14_400, column.sampleCount)
    }

    @Test
    fun `a five-second spike stays marked and tappable on a 30-day window`() {
        // 30 days of quiet background, one hour of which holds a 5-second
        // excursion — shorter than a column by three orders of magnitude.
        val spikeHour = 500
        val spikeAt = spikeHour * HOUR + 1234 * 1000L
        val slices = (0 until 720).map { hour ->
            val values = background(seed = hour.toLong())
            if (hour == spikeHour) {
                for (i in 1234 until 1239) values[i] = 4.2f
            }
            hourSlice(hour * HOUR, values)
        }
        val bucketMillis = QuantilePaths.bucketMillis(30 * DAY, QuantileMethod.KLL_SKETCH)
        val snapshot = ChartSeriesModel.snapshotFromSketches(
            slices = slices,
            eventTimesMillis = emptyList(),
            alignedFromMillis = 0,
            toMillis = 30 * DAY,
            bucketMillis = bucketMillis,
        )
        assertEquals(180, snapshot.buckets.size)

        val column = snapshot.buckets.single { spikeAt in it.startMillis until it.endMillis }
        // The extremum survived aggregation exactly, with its true instant.
        assertEquals(4.2f, column.max)
        assertEquals(spikeAt, column.maxAtMillis)
        assertEquals(1_000L, column.extremeWindowMillis, "minute-level instants, not intervals")

        // And it is marked, so the user can find it without knowing where it is.
        val markers = DoseExtremes.markers(
            buckets = snapshot.buckets,
            alarmMicroSvH = 0.30f,
            baselineP90MicroSvH = 0.14f,
        )
        val marker = markers.single { it.atMillis == spikeAt }
        assertEquals(DoseReference.ALARM_L1, marker.reference)
        assertEquals(4.2f, marker.valueMicroSvH)
        assertEquals(1_000L, marker.windowMillis)

        // Tapping the column shows the same exact numbers.
        val tapped = assertNotNull(CursorReadout.nearestBucket(snapshot.buckets, spikeAt))
        assertEquals(column.startMillis, tapped.startMillis)
        assertEquals(4.2f, tapped.max)
    }

    @Test
    fun `a raised level is not called a transient, a spike inside it is`() {
        val quiet = hourSlice(0, background(seed = 7L))
        val raised = hourSlice(HOUR, background(seed = 8L, level = 0.40f))
        val fold = ChartSeriesModel.foldSketches(listOf(quiet, raised), 0, HOUR, 2)
        val markers = DoseExtremes.markers(fold.buckets, alarmMicroSvH = 0.30f, baselineP90MicroSvH = null)
        assertTrue(markers.isEmpty(), "a column that merely sits high is a level, not a spike")
    }

    @Test
    fun `window statistics mix exact moments with approximate percentiles`() {
        val values = background(seed = 42L)
        val slices = listOf(hourSlice(0, values))
        val rollup = WindowRollup(
            sampleCount = values.size,
            sumMicroSvH = values.sumOf { it.toDouble() },
            sumSqMicroSvH = values.sumOf { it.toDouble() * it },
            min = values.min(),
            max = values.max(),
        )
        val snapshot = ChartSeriesModel.snapshotFromSketches(
            slices = slices,
            eventTimesMillis = emptyList(),
            alignedFromMillis = 0,
            toMillis = HOUR,
            bucketMillis = HOUR,
            rollup = rollup,
        )
        val stats = assertNotNull(snapshot.windowStats)
        assertEquals(QuantileMethod.KLL_SKETCH, stats.method)
        assertTrue(!stats.quantilesExact)
        // n, min, max and SD are exact — they never went through the sketch.
        assertEquals(values.size, stats.sampleCount)
        assertEquals(values.min(), stats.min)
        assertEquals(values.max(), stats.max)
        val mean = values.map { it.toDouble() }.average()
        val sd = kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average())
        assertEquals(sd.toFloat(), stats.sd, 1e-4f)
        // The percentiles are the sketch's, inside its error band.
        val exact = KllSketch.exactQuantiles(values, doubleArrayOf(0.5))
        assertTrue(abs(stats.median - exact[0]) < 0.005f, "${stats.median} vs ${exact[0]}")
    }

    @Test
    fun `window statistics describe the visible window, not the loaded range`() {
        val slices = (0 until 8).map { hourSlice(it * HOUR, background(seed = it.toLong())) }
        val snapshot = ChartSeriesModel.snapshotFromSketches(
            slices = slices,
            eventTimesMillis = emptyList(),
            alignedFromMillis = 0,
            toMillis = 8 * HOUR,
            bucketMillis = HOUR,
            visibleFromMillis = 2 * HOUR,
            visibleToMillis = 6 * HOUR,
        )
        val stats = assertNotNull(snapshot.windowStats)
        assertEquals(5 * 3600, stats.sampleCount, "hours 2..6 inclusive")
        assertEquals(2 * HOUR..(7 * HOUR - 1), snapshot.windowSketchRange)
    }

    @Test
    fun `the distribution strip can be built from the sketch items`() {
        val values = background(seed = 3L)
        val snapshot = ChartSeriesModel.snapshotFromSketches(
            slices = listOf(hourSlice(0, values)),
            eventTimesMillis = emptyList(),
            alignedFromMillis = 0,
            toMillis = HOUR,
            bucketMillis = HOUR,
        )
        val histogram = assertNotNull(
            DoseHistograms.build(snapshot.aggregates, 0, HOUR),
        )
        // Measured seconds, not sketch items: the weights are the sample counts.
        assertTrue(histogram.totalCount > 3000, "counts are measured seconds: ${histogram.totalCount}")
        assertTrue(histogram.totalCount <= 3600)
    }

    @Test
    fun `an empty range yields no columns and no statistics`() {
        val snapshot = ChartSeriesModel.snapshotFromSketches(
            slices = emptyList(),
            eventTimesMillis = emptyList(),
            alignedFromMillis = 0,
            toMillis = DAY,
            bucketMillis = HOUR,
        )
        assertTrue(snapshot.buckets.isEmpty())
        assertEquals(null, snapshot.windowStats)
        assertEquals(null, snapshot.windowSketchRange)
    }

    @Test
    fun `gaps stay gaps on the long path`() {
        val slices = listOf(
            hourSlice(0, background(seed = 1L)),
            // hour 1 missing: device was off
            hourSlice(2 * HOUR, background(seed = 2L)),
        )
        val fold = ChartSeriesModel.foldSketches(slices, 0, HOUR, 3)
        assertEquals(listOf(0L, 2 * HOUR), fold.buckets.map { it.startMillis })
    }
}
