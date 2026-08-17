package app.alpha.ui.chart

import app.alpha.ui.logic.ChartWindow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Преобразование готового кадра — арифметика, от которой зависит, какое время
 * покажет курсор. Проверяется без экрана: ошибка здесь означает не «криво
 * нарисовано», а «показано не то время».
 */
class ChartTransformTest {

    private val width = 1000f
    private val from = 1_000_000L
    private val to = 1_060_000L // окно в минуту

    @Test
    fun `identity changes nothing`() {
        val t = ChartTransform.IDENTITY
        assertTrue(t.isIdentity)
        assertEquals(300f, t.mapX(300f))
        assertEquals(300f, t.unmapX(300f))
        assertEquals(from + 30_000L, t.timeAt(width / 2, from, to, width))
    }

    @Test
    fun `panning moves the picture with the finger and back`() {
        val t = ChartTransform.IDENTITY.pan(120f)
        assertEquals(420f, t.mapX(300f))
        assertEquals(300f, t.unmapX(420f), 1e-3f)
        // Картинку сдвинули вправо — значит под точкой экрана более раннее время.
        assertTrue(t.timeAt(width / 2, from, to, width) < from + 30_000L)
    }

    /** Точка под пальцами обязана остаться на месте — иначе щипок «уезжает». */
    @Test
    fun `zooming keeps the point under the fingers`() {
        val focus = 800f
        val t = ChartTransform.IDENTITY.zoom(2f, focus)
        val source = ChartTransform.IDENTITY.unmapX(focus)
        assertEquals(focus, t.mapX(source), 1e-2f)
        assertEquals(2f, t.scaleX)
    }

    @Test
    fun `zoom and pan compose without losing the anchor`() {
        val t = ChartTransform.IDENTITY.pan(-50f).zoom(1.5f, 600f).pan(20f)
        // Обратное отображение остаётся обратным при любой цепочке.
        assertEquals(400f, t.unmapX(t.mapX(400f)), 1e-2f)
    }

    @Test
    fun `an absurd zoom factor is ignored rather than breaking the frame`() {
        val t = ChartTransform.IDENTITY.zoom(0f, 500f).zoom(Float.NaN, 500f)
        assertTrue(t.isIdentity)
    }

    /**
     * Видимое окно — то, что придётся пересобрать, когда движение улеглось.
     * Сдвиг вправо показывает более раннее время: окно едет в прошлое.
     */
    @Test
    fun `the visible window follows the transform`() {
        val moved = ChartTransform.IDENTITY.pan(width / 2)
        val window = moved.visibleWindow(from, to, width)
        assertTrue(window.fromMillis < from, "${window.fromMillis}")
        assertEquals(to - from, window.spanMillis)

        val zoomed = ChartTransform.IDENTITY.zoom(2f, width / 2)
        val closer = zoomed.visibleWindow(from, to, width)
        assertTrue(closer.spanMillis < to - from, "${closer.spanMillis}")
    }

    @Test
    fun `a zero-width plot cannot be projected and says so by staying put`() {
        val t = ChartTransform.IDENTITY.pan(100f)
        assertEquals(from, t.timeAt(10f, from, to, widthPx = 0f))
        assertEquals(ChartWindow(from, to), t.visibleWindow(from, to, widthPx = 0f))
    }
}
