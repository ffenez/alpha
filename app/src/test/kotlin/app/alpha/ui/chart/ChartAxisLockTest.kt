package app.alpha.ui.chart

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * За какую ось держится палец.
 *
 * Главный риск здесь не в том, что вертикальный жест не сработает, а в
 * обратном: перемещение по времени рука ведёт слегка наискось, и если каждая
 * такая наклонность будет уводить шкалу, автоподбор оси начнёт молча
 * выключаться сам собой.
 */
class ChartAxisLockTest {

    private val width = 1080f
    private val slop = 12f
    private val gutter = 100f

    private fun lock() = ChartAxisLock(slopPx = slop, gutterPx = gutter)

    private fun ChartAxisLock.drag(
        dx: Float,
        dy: Float,
        atX: Float = 300f,
        vertical: Boolean = true,
    ) = update(
        positionXPx = atX,
        widthPx = width,
        panXPx = dx,
        panYPx = dy,
        zoom = 1f,
        vertical = vertical,
    )

    @Test
    fun `дрожание руки не выбирает ось`() {
        assertEquals(GestureAxis.UNDECIDED, lock().drag(dx = 2f, dy = 1f))
    }

    @Test
    fun `перемещение по времени остаётся временем даже наискось`() {
        val axis = lock()
        assertEquals(GestureAxis.UNDECIDED, axis.drag(dx = 8f, dy = 4f))
        assertEquals(GestureAxis.TIME, axis.drag(dx = 8f, dy = 4f))
    }

    @Test
    fun `явно вертикальный жест ведёт ось значений`() {
        val axis = lock()
        assertEquals(GestureAxis.VALUE, axis.drag(dx = 2f, dy = 20f))
    }

    @Test
    fun `выбранная ось не меняется до конца жеста`() {
        val axis = lock()
        assertEquals(GestureAxis.VALUE, axis.drag(dx = 0f, dy = 20f))
        assertEquals(GestureAxis.VALUE, axis.drag(dx = 200f, dy = 0f))
        axis.reset()
        assertEquals(GestureAxis.UNDECIDED, axis.axis)
    }

    @Test
    fun `щипок всегда про время`() {
        val axis = lock()
        assertEquals(
            GestureAxis.TIME,
            axis.update(
                positionXPx = 300f,
                widthPx = width,
                panXPx = 0f,
                panYPx = 30f,
                zoom = 1.05f,
                vertical = true,
            ),
        )
    }

    @Test
    fun `жест на шкале справа масштабирует ось`() {
        val axis = lock()
        assertEquals(GestureAxis.VALUE_SCALE, axis.drag(dx = 0f, dy = 5f, atX = width - 20f))
    }

    @Test
    fun `там, где вертикаль принадлежит экрану, ось не двигается`() {
        // Карточка Главной: вертикаль — это прокрутка страницы.
        val axis = lock()
        assertEquals(GestureAxis.TIME, axis.drag(dx = 0f, dy = 40f, vertical = false))
    }
}
