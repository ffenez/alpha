package app.radiacode.ui.logic

import app.radiacode.data.db.DownsampledSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartWindowTest {

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    // --- construction and live-follow ---

    @Test
    fun `latest window ends at now with the requested span`() {
        val w = ChartWindows.latest(hour, now)
        assertEquals(now, w.toMillis)
        assertEquals(hour, w.spanMillis)
    }

    @Test
    fun `latest clamps span into bounds`() {
        assertEquals(
            ChartWindows.MIN_SPAN_MILLIS,
            ChartWindows.latest(1L, now).spanMillis,
        )
        assertEquals(
            ChartWindows.MAX_SPAN_MILLIS,
            ChartWindows.latest(Long.MAX_VALUE / 4, now).spanMillis,
        )
    }

    @Test
    fun `follow keeps the span and pins the right edge to now`() {
        val w = ChartWindow(now - hour - 500_000, now - 500_000)
        val followed = ChartWindows.follow(w, now)
        assertEquals(now, followed.toMillis)
        assertEquals(w.spanMillis, followed.spanMillis)
    }

    // --- pan ---

    @Test
    fun `pan shifts by a fraction of the span`() {
        val w = ChartWindows.latest(hour, now - hour) // ends one hour ago
        val panned = ChartWindows.pan(w, -0.5f, now)
        assertEquals(w.fromMillis - hour / 2, panned.fromMillis)
        assertEquals(w.spanMillis, panned.spanMillis)
    }

    @Test
    fun `pan clamps the right edge at now and preserves the span`() {
        val w = ChartWindows.latest(hour, now - 60_000)
        val panned = ChartWindows.pan(w, 1f, now) // wants to go far into the future
        assertEquals(now, panned.toMillis)
        assertEquals(hour, panned.spanMillis)
    }

    // --- zoom ---

    @Test
    fun `zoom in keeps the focus time fixed`() {
        val w = ChartWindows.latest(hour, now - hour)
        val focusFraction = 0.25f
        val focusTime = ChartWindows.timeAt(w, focusFraction)
        val zoomed = ChartWindows.zoom(w, 2f, focusFraction, now)
        assertEquals(hour / 2, zoomed.spanMillis)
        assertEquals(focusTime, ChartWindows.timeAt(zoomed, focusFraction))
    }

    @Test
    fun `zoom clamps at the minimum span`() {
        val w = ChartWindows.latest(ChartWindows.MIN_SPAN_MILLIS, now)
        val zoomed = ChartWindows.zoom(w, 10f, 0.5f, now)
        assertEquals(ChartWindows.MIN_SPAN_MILLIS, zoomed.spanMillis)
    }

    @Test
    fun `zoom out clamps at the maximum span and at now`() {
        val w = ChartWindows.latest(hour, now)
        val zoomed = ChartWindows.zoom(w, 1e-9f, 1f, now)
        assertEquals(ChartWindows.MAX_SPAN_MILLIS, zoomed.spanMillis)
        assertTrue(zoomed.toMillis <= now)
    }

    @Test
    fun `zoom with nonpositive factor is a no-op`() {
        val w = ChartWindows.latest(hour, now)
        assertEquals(w, ChartWindows.zoom(w, 0f, 0.5f, now))
    }

    // --- window <-> fraction mapping ---

    @Test
    fun `timeAt and fraction round trip`() {
        val w = ChartWindows.latest(hour, now)
        for (f in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val t = ChartWindows.timeAt(w, f)
            assertEquals(f, ChartWindows.fraction(w, t), 1e-4f)
        }
    }

    @Test
    fun `fraction clamps outside the window`() {
        val w = ChartWindows.latest(hour, now)
        assertEquals(0f, ChartWindows.fraction(w, w.fromMillis - 999), 0f)
        assertEquals(1f, ChartWindows.fraction(w, w.toMillis + 999), 0f)
    }

    // --- live edge and cadence ---

    @Test
    fun `live edge detection is one bucket wide`() {
        val bucket = 30_000L
        val atEdge = ChartWindow(now - hour, now - bucket)
        val behind = ChartWindow(now - hour, now - bucket - 1)
        assertTrue(ChartWindows.isAtLiveEdge(atEdge, now, bucket))
        assertFalse(ChartWindows.isAtLiveEdge(behind, now, bucket))
    }

    @Test
    fun `bucket and refresh cadence clamp sensibly`() {
        // 15 min / 120 columns = 7.5 s buckets, refreshed at a quarter bucket.
        val bucket15m = ChartWindows.bucketMillis(15 * 60_000L, 120)
        assertEquals(7_500L, bucket15m)
        assertEquals(1_875L, ChartWindows.refreshMillis(bucket15m))
        // A 1-minute window refreshes at the 1 s floor (1 Hz appends).
        assertEquals(
            1_000L,
            ChartWindows.refreshMillis(ChartWindows.bucketMillis(60_000L, 120)),
        )
        // 7 days / 120 columns = 84 min buckets, refresh capped at 15 s.
        val bucket7d = ChartWindows.bucketMillis(ChartWindows.MAX_SPAN_MILLIS, 120)
        assertEquals(15_000L, ChartWindows.refreshMillis(bucket7d))
        // Degenerate window still yields a positive bucket.
        assertEquals(1_000L, ChartWindows.bucketMillis(0L, 120))
    }

    // --- loaded range with gesture headroom ---

    @Test
    fun `the loaded range pads the window so a small pan needs no query`() {
        val w = ChartWindows.latest(hour, now - hour)
        val load = ChartWindows.loadRange(w, now)
        val pad = (hour * ChartWindows.LOAD_PADDING_FRACTION).toLong()
        assertEquals(w.fromMillis - pad, load.fromMillis)
        assertEquals(w.toMillis + pad, load.toMillis)
        assertTrue(ChartWindows.covers(load, w))
        // A pan of a fifth of the window still sits inside the loaded data.
        assertTrue(ChartWindows.covers(load, ChartWindows.pan(w, -0.2f, now)))
        // A pan of a whole window does not — that is when the reload happens.
        assertFalse(ChartWindows.covers(load, ChartWindows.pan(w, -1f, now)))
    }

    @Test
    fun `the loaded range never asks for the future`() {
        val w = ChartWindows.latest(hour, now)
        val load = ChartWindows.loadRange(w, now)
        assertEquals(now, load.toMillis)
        assertTrue(load.fromMillis < w.fromMillis)
    }

    // --- period chips ---

    @Test
    fun `the chip row scrolls to keep the selection off the edge`() {
        for (selected in ChartWindows.PERIODS.indices) {
            val target = ChartWindows.scrollTargetIndex(selected)
            assertTrue("target $target out of range", target in ChartWindows.PERIODS.indices)
            assertTrue("target $target is past the selection $selected", target <= selected)
        }
        // Первый чип не уезжает за левый край.
        assertEquals(0, ChartWindows.scrollTargetIndex(0))
    }

    @Test
    fun `the ladder steps like a clock face and starts at a minute`() {
        val labels = ChartWindows.PERIODS.map { it.first }
        assertEquals("1м", labels.first())
        assertEquals(ChartWindows.MIN_SPAN_MILLIS, ChartWindows.PERIODS.first().second)
        // Знакомый шаг 1-2-3-5-10-30 внутри минут и часов.
        assertTrue(labels.containsAll(listOf("1м", "2м", "3м", "5м", "10м", "30м")))
        assertTrue(labels.containsAll(listOf("1ч", "2ч", "3ч", "6ч", "12ч")))
        assertTrue(labels.containsAll(listOf("1д", "2д", "7д", "30д")))
        // Окно по умолчанию существует и лежит в лестнице.
        assertTrue(ChartWindows.DEFAULT_PERIOD_INDEX in ChartWindows.PERIODS.indices)
    }

    @Test
    fun `every period is reachable and the longest is a month`() {
        assertEquals("30д", ChartWindows.PERIODS.last().first)
        assertEquals(ChartWindows.MAX_SPAN_MILLIS, ChartWindows.PERIODS.last().second)
        assertTrue(ChartWindows.PERIODS.map { it.second }.zipWithNext().all { (a, b) -> a < b })
    }

    // --- visible-window stats over a shifted window ---

    @Test
    fun `visible window stats follow the panned window`() {
        val columns = 4
        val bucket = ChartWindows.bucketMillis(hour, columns) // 15 min
        val w = ChartWindows.latest(hour, now)
        val alignedFrom = ChartMapping.alignedFrom(w.toMillis, w.spanMillis, bucket)
        // Data exists only in the older half of the window.
        val buckets = listOf(
            DownsampledSample(
                bucketStart = alignedFrom,
                avgDoseRate = 1f,
                maxDoseRate = 1f,
                avgCountRate = 10f,
                sampleCount = 10,
            ),
            DownsampledSample(
                bucketStart = alignedFrom + bucket,
                avgDoseRate = 3f,
                maxDoseRate = 3f,
                avgCountRate = 30f,
                sampleCount = 20,
            ),
        )
        val cols = ChartMapping.toColumns(buckets, alignedFrom, bucket, columns) { it.avgDoseRate }
        val stats = ChartMapping.stats(cols)
        assertNotNull(stats)
        assertEquals(1f, stats!!.min, 0f)
        assertEquals(3f, stats.max, 0f)
        assertEquals(2, stats.count)
        assertEquals(30, buckets.sumOf { it.sampleCount })

        // Pan a full window into the past: the same buckets fall out of frame.
        val past = ChartWindows.pan(w, -1f, now)
        val pastFrom = ChartMapping.alignedFrom(past.toMillis, past.spanMillis, bucket)
        val pastCols = ChartMapping.toColumns(buckets, pastFrom, bucket, columns) { it.avgDoseRate }
        assertTrue(ChartMapping.stats(pastCols) == null || pastFrom == alignedFrom)
    }
}
