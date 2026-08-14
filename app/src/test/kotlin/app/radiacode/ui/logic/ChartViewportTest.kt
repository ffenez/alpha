package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Окно графика как состояние, а не как поведение двух экранов по отдельности.
 *
 * Карточка Главной и полноэкранный график показывают одни и те же измерения, и
 * пока каждый вёл своё окно сам, возможна была картина «большой живой, мелкий
 * замерший»: данные общие, край двигался у одного.
 */
class ChartViewportTest {

    private val now = 1_700_000_000_000L
    private val fiveMinutes = ChartWindows.PERIODS.indexOfFirst { it.second == 5 * 60_000L }

    @Test
    fun `while following, the right edge is now`() {
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val window = viewport.window(now + 30_000)

        assertEquals(now + 30_000, window.toMillis)
        assertEquals(5 * 60_000L, window.spanMillis)
    }

    @Test
    fun `a pinch moves one step at a time`() {
        // Один плавный щипок не имеет права пролететь всю лестницу: ширина
        // колонки и метод квантилей должны меняться предсказуемо.
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val closer = ChartViewport.zoom(viewport, scale = 4f, nowMillis = now)

        assertEquals(fiveMinutes - 1, closer.stepIndex)
    }

    @Test
    fun `a small pinch changes nothing`() {
        // Дрожание руки не должно переключать ступень.
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        assertEquals(viewport, ChartViewport.zoom(viewport, scale = 1.1f, nowMillis = now))
        assertEquals(viewport, ChartViewport.zoom(viewport, scale = 0.95f, nowMillis = now))
    }

    @Test
    fun `zoom keeps the moment, not the place in the ladder`() {
        // Приближают, чтобы разглядеть ТО ЖЕ время, а не чтобы уехать.
        val panned = ChartViewport.pan(
            ChartViewport.atLiveEdge(fiveMinutes, now),
            fractionOfWindow = 2f,
            nowMillis = now,
        )

        val zoomed = ChartViewport.zoom(panned, scale = 4f, nowMillis = now)

        assertEquals(panned.endMillis, zoomed.endMillis)
        assertFalse(zoomed.follow)
    }

    @Test
    fun `panning into the past switches following off`() {
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val panned = ChartViewport.pan(viewport, fractionOfWindow = 0.5f, nowMillis = now)

        assertFalse(panned.follow, "график вырывался бы из-под пальца")
        assertEquals(now - 150_000L, panned.endMillis)
    }

    @Test
    fun `coming back to the edge switches following on again`() {
        // Отдельного действия для этого не нужно: возврат к «сейчас» и есть
        // просьба следить.
        val panned = ChartViewport.pan(
            ChartViewport.atLiveEdge(fiveMinutes, now),
            fractionOfWindow = 0.5f,
            nowMillis = now,
        )

        val back = ChartViewport.pan(panned, fractionOfWindow = -0.5f, nowMillis = now)

        assertTrue(back.follow)
    }

    @Test
    fun `the window never runs past now`() {
        // Будущее нельзя посмотреть даже жестом.
        val viewport = ChartViewport.atLiveEdge(fiveMinutes, now)

        val forward = ChartViewport.pan(viewport, fractionOfWindow = -5f, nowMillis = now)

        assertEquals(now, forward.endMillis)
        assertTrue(forward.follow)
    }

    @Test
    fun `jumping to now restores following`() {
        val panned = ChartViewport.pan(
            ChartViewport.atLiveEdge(fiveMinutes, now),
            fractionOfWindow = 3f,
            nowMillis = now,
        )

        val live = ChartViewport.jumpToNow(panned, now)

        assertTrue(live.follow)
        assertEquals(now, live.window(now).toMillis)
    }

    @Test
    fun `the ladder cannot be walked off either end`() {
        val shortest = ChartViewport.atLiveEdge(0, now)
        val longest = ChartViewport.atLiveEdge(ChartWindows.PERIODS.lastIndex, now)

        assertEquals(0, ChartViewport.zoom(shortest, scale = 4f, nowMillis = now).stepIndex)
        assertEquals(
            ChartWindows.PERIODS.lastIndex,
            ChartViewport.zoom(longest, scale = 0.1f, nowMillis = now).stepIndex,
        )
    }
}
