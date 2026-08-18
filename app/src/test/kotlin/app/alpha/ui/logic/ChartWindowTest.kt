package app.alpha.ui.logic

import app.alpha.data.db.DownsampledSample
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
        // Уверенный рывок пальцем — целое окно — обязан уложиться в
        // прочитанное: именно ради этого запас и существует, и именно на нём
        // раньше было видно подгрузку.
        assertTrue(ChartWindows.covers(load, ChartWindows.pan(w, -1f, now)))
        // Два окна — уже за пределами, и там честно происходит чтение.
        assertFalse(ChartWindows.covers(load, ChartWindows.pan(w, -2.1f, now)))
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
    fun `the ladder follows the window, so the highlighted chip never lies`() {
        // Щипок меняет окно плавно; подсвечивается ближайшая ступень.
        for ((index, period) in ChartWindows.PERIODS.withIndex()) {
            assertEquals(index, ChartWindows.nearestPeriodIndex(period.second))
            assertTrue("ступень ${period.first}", ChartWindows.matchesPeriod(period.second, index))
        }
        // Ровно между 1ч и 2ч ближайшей может быть любая из них, но не 6ч…
        val between = ChartWindows.nearestPeriodIndex(90L * 60_000L)
        assertTrue(
            ChartWindows.PERIODS[between].first,
            ChartWindows.PERIODS[between].first in listOf("1ч", "2ч"),
        )
        // …и «выбранной» она не считается: окно не равно ступени.
        assertTrue("окно между ступенями не считается выбранной ступенью",
            !ChartWindows.matchesPeriod(90L * 60_000L, between))
    }

    @Test
    fun `the nearest step is measured by ratio, not by difference`() {
        // 70 минут ближе к часу, чем к двум, хотя по разности почти поровну…
        assertEquals("1ч", ChartWindows.PERIODS[ChartWindows.nearestPeriodIndex(70L * 60_000L)].first)
        // …а полторы минуты ближе к двум минутам, чем к одной.
        assertEquals("2м", ChartWindows.PERIODS[ChartWindows.nearestPeriodIndex(95_000L)].first)
    }

    @Test
    fun `a metric with fewer windows never highlights one it cannot show`() {
        val short = ChartWindows.PERIODS.indices.filter {
            ChartWindows.PERIODS[it].second <= 6L * 3_600_000L
        }
        val index = ChartWindows.nearestPeriodIndex(30L * 24 * 3_600_000L, short)
        assertEquals("6ч", ChartWindows.PERIODS[index].first)
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

    @Test
    fun `the read-ahead never changes the quantile path`() {
        // Метод выбирается по длине ЗАГРУЖАЕМОГО диапазона. Раздутый запас
        // мог бы перебросить окно через границу шести часов, и подпись под
        // графиком заговорила бы о приближении там, где человек ничего не
        // менял.
        for ((_, span) in ChartWindows.PERIODS) {
            val window = ChartWindows.latest(span, now)
            val load = ChartWindows.loadRange(window, now)
            if (span < QuantilePaths.EXACT_MAX_SPAN_MILLIS) {
                assertTrue(
                    "окно $span мс потеряло точный путь из-за запаса",
                    QuantilePaths.methodFor(load.spanMillis) == QuantileMethod.EXACT_RAW,
                )
            }
        }
    }
}

class ExactPathBoundaryTest {

    /**
     * Ступень «6 ч» обязана читаться точным путём.
     *
     * Запас чтения добавлялся без ограничителя ровно на границе, диапазон
     * становился 12 ч, и `methodFor` молча переводил окно на почасовые
     * скетчи: колонка вырастала до часа, и подробный ряд рисовал семь
     * треугольников вместо шести часов измерений.
     */
    @Test
    fun `the six hour step still reads raw samples`() {
        val now = 1_700_000_000_000L
        val exact = QuantilePaths.EXACT_MAX_SPAN_MILLIS
        val window = ChartWindows.latest(exact, now)
        val loaded = ChartWindows.loadRange(window, now)
        assertEquals("запас на границе раздувает чтение", exact, loaded.spanMillis)
        assertEquals(QuantileMethod.EXACT_RAW, QuantilePaths.methodFor(loaded.spanMillis))
    }

    @Test
    fun `a window past the limit keeps its reading padding`() {
        val now = 1_700_000_000_000L
        val span = QuantilePaths.EXACT_MAX_SPAN_MILLIS + 60_000L
        val loaded = ChartWindows.loadRange(ChartWindows.latest(span, now), now)
        assertTrue(loaded.spanMillis > span)
        assertEquals(QuantileMethod.KLL_SKETCH, QuantilePaths.methodFor(loaded.spanMillis))
    }

    @Test
    fun `a short window keeps padding inside the exact path`() {
        val now = 1_700_000_000_000L
        val loaded = ChartWindows.loadRange(ChartWindows.latest(5L * 60_000L, now), now)
        assertTrue(loaded.spanMillis > 5L * 60_000L)
        assertEquals(QuantileMethod.EXACT_RAW, QuantilePaths.methodFor(loaded.spanMillis))
    }
}
