package app.radiacode.ui.chart

import app.radiacode.ui.logic.ChartWindows
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Жест двигает УЖЕ ПОСЧИТАННУЮ картинку.
 *
 * Проверяется то, чего не увидеть глазом на приборе: после сдвига пальцем
 * преобразование обязано ставить на экран ровно то время, в которое человек
 * уехал. Ошибка здесь — не «криво нарисовано», а «курсор называет не тот
 * момент».
 */
class ChartGestureTest {

    private val now = 1_700_000_000_000L
    private val bounds = ViewportBounds(edgeMillis = now)
    private val widthPx = 1080f

    /**
     * Пиксель, на котором стоит ПРАВЫЙ КРАЙ ДАННЫХ: у живого края кадр всегда
     * оставляет немного воздуха, чтобы свежая точка не читалась как обрыв.
     */
    private val dataEdgePx =
        widthPx / (1f + ChartWindows.RIGHT_PADDING_FRACTION.toFloat())

    private fun gesture(spanMillis: Long = 10 * 60_000L) =
        ChartGesture.of(Viewports.atEdge(spanMillis, bounds), bounds)

    /** Время, которое после преобразования оказалось в точке [xPx] экрана. */
    private fun timeAt(g: ChartGesture, xPx: Float): Long {
        val rendered = g.rendered
        return g.transform(widthPx).timeAt(
            x = xPx,
            fromMillis = rendered.fromMillis,
            toMillis = rendered.toMillis,
            widthPx = widthPx,
        )
    }

    @Test
    fun `без жеста видно ровно посчитанное окно`() {
        val g = gesture()
        assertFalse(g.moved)
        assertEquals(g.frame.startMillis, timeAt(g, 0f), absoluteTolerance())
        assertEquals(g.frame.endMillis, timeAt(g, dataEdgePx), absoluteTolerance())
    }

    @Test
    fun `после сдвига на экране то время, в которое уехали`() {
        val g = gesture().pan(-0.25f, bounds)
        assertTrue(g.moved)
        assertEquals(g.visible.startMillis, timeAt(g, 0f), absoluteTolerance())
        assertEquals(g.visible.endMillis, timeAt(g, dataEdgePx), absoluteTolerance())
    }

    @Test
    fun `после щипка на экране то окно, которое получилось`() {
        val g = gesture().zoom(factor = 2f, focusFraction = 0.25f, bounds = bounds)
        assertEquals(g.visible.startMillis, timeAt(g, 0f), absoluteTolerance())
        assertEquals(g.visible.endMillis, timeAt(g, dataEdgePx), absoluteTolerance())
        // Приблизили — картинка растянута.
        assertTrue(g.transform(widthPx).scaleX > 1f)
    }

    @Test
    fun `запас геометрии покрывает уверенный рывок`() {
        val g = gesture().pan(-0.4f, bounds)
        assertTrue(g.covered(), "сдвиг в четыре десятых окна обязан лежать в запасе")
    }

    @Test
    fun `уехали дальше запаса — рисовать нечем`() {
        val g = gesture().pan(-1.5f, bounds)
        assertFalse(g.covered())
    }

    @Test
    fun `фиксация делает видимое окно посчитанным`() {
        val moved = gesture().pan(-0.3f, bounds)
        val committed = moved.commit(bounds)
        assertFalse(committed.moved)
        assertEquals(moved.visible, committed.frame)
        // Преобразование и в покое не единичное: кадр строится ШИРЕ видимого
        // окна (запас под жест), и его надо ужать до ширины поля.
        assertEquals(committed.visible.startMillis, timeAt(committed, 0f), absoluteTolerance())
        assertEquals(
            committed.visible.endMillis,
            timeAt(committed, dataEdgePx),
            absoluteTolerance(),
        )
    }

    @Test
    fun `такт слежения двигает видимое окно, а кадр оставляет`() {
        val g = gesture()
        val later = ViewportBounds(edgeMillis = now + 5_000L)
        val next = g.followTick(later)
        assertEquals(now + 5_000L, next.visible.endMillis)
        // Кадр не пересобирается: раз в секунду складывать колонки, границы
        // оси, эпизоды и статистику — это и есть «мини-графики тормозят».
        assertEquals(g.frame, next.frame)
        assertEquals(g.rendered, next.rendered)
    }

    @Test
    fun `когда запас кончился, кадр догоняет живой край`() {
        val g = gesture(spanMillis = 5 * 60_000L)
        // Запас — половина окна; через столько же живой край уходит за него.
        val far = ViewportBounds(edgeMillis = now + 4 * 60_000L)
        val next = g.followTick(far)
        assertTrue(next.covered(), "уехали за нарисованное — кадр обязан догнать")
        assertEquals(next.visible, next.frame)
    }

    @Test
    fun `уведённое окно живым краем не двигается`() {
        val g = gesture().pan(-0.5f, bounds)
        val next = g.followTick(ViewportBounds(edgeMillis = now + 30_000L))
        assertEquals(g.visible, next.visible)
    }

    @Test
    fun `границы жеста те же, что у окна`() {
        // Уехать правее «сейчас» нельзя ни жестом, ни броском.
        val g = gesture().pan(5f, bounds)
        assertEquals(now, g.visible.endMillis)
        assertTrue(g.visible.followLiveEdge)
    }

    @Test
    fun `нулевая ширина поля не ломает преобразование`() {
        val g = gesture().pan(-0.3f, bounds)
        assertEquals(ChartTransform.IDENTITY, g.transform(0f))
    }

    private fun absoluteTolerance(): Long = 50L

    private fun assertEquals(expected: Long, actual: Long, tolerance: Long) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "ожидалось $expected, получено $actual (допуск $tolerance мс)",
        )
    }
}
