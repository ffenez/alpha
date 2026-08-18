package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Живой край едет между секундными тиками.
 *
 * Проверяется ровно то, что делает движение честным: сдвигается ОКНО, размер
 * окна не меняется, назад время не идёт, и дальше секунды окно не тянется —
 * иначе картинка показывала бы время, которого прибор не измерял.
 */
class LiveEdgeTest {

    private val window = ChartWindow(fromMillis = 1_000_000L, toMillis = 1_300_000L) // 5 мин

    @Test
    fun `the window slides without changing its span`() {
        val shifted = LiveEdge.shifted(window, tickMillis = 1_300_000L, frameMillis = 1_300_400L)
        assertEquals(window.spanMillis, shifted.spanMillis)
        assertEquals(1_300_400L, shifted.toMillis)
        assertEquals(1_000_400L, shifted.fromMillis)
    }

    @Test
    fun `time never runs backwards`() {
        val shifted = LiveEdge.shifted(window, tickMillis = 1_300_000L, frameMillis = 1_299_000L)
        assertEquals(window, shifted)
    }

    @Test
    fun `a missing tick does not stretch the window past a second`() {
        // Тик не пришёл пять секунд: окно уезжает ровно на секунду и ждёт.
        val shifted = LiveEdge.shifted(window, tickMillis = 1_300_000L, frameMillis = 1_305_000L)
        assertEquals(1_301_000L, shifted.toMillis)
    }

    @Test
    fun `motion is animated only where it can be seen`() {
        val plotWidthPx = 1_000f
        // Пять минут на 1000 px — около 3,3 px/с: видно.
        assertTrue(LiveEdge.smooth(spanMillis = 5 * 60_000L, plotWidthPx = plotWidthPx))
        // Шесть часов — 0,046 px/с: покадровая перерисовка тратила бы батарею
        // на движение, которого нет.
        assertFalse(LiveEdge.smooth(spanMillis = 6 * 3_600_000L, plotWidthPx = plotWidthPx))
        assertFalse(LiveEdge.smooth(spanMillis = 0L, plotWidthPx = plotWidthPx))
        assertFalse(LiveEdge.smooth(spanMillis = 60_000L, plotWidthPx = 0f))
    }

    @Test
    fun `the threshold is the pixel rate itself`() {
        assertEquals(0.5, LiveEdge.pixelsPerSecond(spanMillis = 2_000_000L, plotWidthPx = 1_000f), 1e-9)
        assertTrue(LiveEdge.smooth(spanMillis = 2_000_000L, plotWidthPx = 1_000f))
        assertFalse(LiveEdge.smooth(spanMillis = 2_100_000L, plotWidthPx = 1_000f))
    }
}
