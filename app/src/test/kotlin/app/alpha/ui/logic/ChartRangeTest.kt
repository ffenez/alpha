package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Исторический график отличается от живого ровно ОДНИМ: краем времени.
 * Правила этого края проверяются здесь, а не глазами на экране.
 */
class ChartRangeTest {

    private val hour = 3_600_000L
    private val start = 1_700_000_000_000L

    @Test
    fun `window of a session is the session itself`() {
        val range = ChartRange(start, start + 2 * hour)
        val window = ChartRanges.initialWindow(range, ChartWindows.MAX_SPAN_MILLIS)
        assertEquals(range.fromMillis, window.fromMillis)
        assertEquals(range.toMillis, window.toMillis)
    }

    @Test
    fun `a metric without long windows shows the last piece it can draw honestly`() {
        val range = ChartRange(start, start + 30 * hour)
        val limit = ChartMetrics.maxSpanMillis(ChartMetric.COUNT_RATE)
        val window = ChartRanges.initialWindow(range, limit)
        assertEquals(limit, window.spanMillis)
        // Правый край остаётся концом сессии: окно урезано, а не сдвинуто.
        assertEquals(range.toMillis, window.toMillis)
    }

    @Test
    fun `a session shorter than the minimum window sits in the middle of it`() {
        val range = ChartRange(start, start + 20_000L)
        val window = ChartRanges.initialWindow(range, ChartWindows.MAX_SPAN_MILLIS)
        assertEquals(ChartWindows.MIN_SPAN_MILLIS, window.spanMillis)
        val padBefore = range.fromMillis - window.fromMillis
        val padAfter = window.toMillis - range.toMillis
        assertTrue(kotlin.math.abs(padBefore - padAfter) <= 1L, "$padBefore vs $padAfter")
    }

    @Test
    fun `the live edge belongs to the live chart only`() {
        val now = start + 100 * hour
        assertTrue(ChartRanges.followsLiveEdge(null))
        assertEquals(now, ChartRanges.edgeMillis(null, now))

        val range = ChartRange(start, start + 2 * hour)
        assertFalse(ChartRanges.followsLiveEdge(range))
        // Жест не имеет права уехать за конец сессии: за ним чужое время.
        assertEquals(range.toMillis, ChartRanges.edgeMillis(range, now))
    }

    @Test
    fun `the session chip is lit only while the window stands on the range`() {
        val range = ChartRange(start, start + 2 * hour)
        val max = ChartWindows.MAX_SPAN_MILLIS
        val full = ChartRanges.initialWindow(range, max)
        assertTrue(ChartRanges.atFullRange(full, range, max))
        // Сдвинули окно — состояние выключено, и чип обязан погаснуть.
        val panned = ChartWindows.pan(full, -0.5f, range.toMillis)
        assertFalse(ChartRanges.atFullRange(panned, range, max))
        // Микроскопическое расхождение после щипка кадра не меняет.
        val jitter = ChartWindow(full.fromMillis + 1_000L, full.toMillis + 1_000L)
        assertTrue(ChartRanges.atFullRange(jitter, range, max))
    }

    @Test
    fun `a running session ends at now, and time never runs backwards`() {
        val now = start + 3 * hour
        assertEquals(now, ChartRanges.of(start, endedAtMillis = null, nowMillis = now).toMillis)
        assertEquals(
            start + hour,
            ChartRanges.of(start, endedAtMillis = start + hour, nowMillis = now).toMillis,
        )
        // Часы телефона могли уехать назад — размах от этого не станет
        // отрицательным, иначе окно вывернулось бы наизнанку.
        val backwards = ChartRanges.of(start, endedAtMillis = null, nowMillis = start - hour)
        assertEquals(0L, backwards.spanMillis)
    }
}
