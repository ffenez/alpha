package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Арифметика перетаскивания строки. Ошибка здесь — «строка встала не туда»:
 * на глаз это видно только повторив жест, числом — сразу.
 */
class DragReorderTest {

    private val rowHeight = 48f

    @Test
    fun `порядок меняется, когда строка перекрыла соседнюю наполовину`() {
        assertEquals(0, DragReorder.steps(offsetPx = 20f, rowHeightPx = rowHeight))
        assertEquals(1, DragReorder.steps(offsetPx = 25f, rowHeightPx = rowHeight))
        assertEquals(-1, DragReorder.steps(offsetPx = -25f, rowHeightPx = rowHeight))
        assertEquals(2, DragReorder.steps(offsetPx = 100f, rowHeightPx = rowHeight))
    }

    @Test
    fun `дрожание руки порядок не меняет`() {
        assertEquals(0, DragReorder.steps(offsetPx = 3f, rowHeightPx = rowHeight))
        assertEquals(0, DragReorder.steps(offsetPx = -3f, rowHeightPx = rowHeight))
    }

    @Test
    fun `неизмеренная высота строки не ломает жест`() {
        assertEquals(0, DragReorder.steps(offsetPx = 100f, rowHeightPx = 0f))
        assertEquals(0, DragReorder.steps(offsetPx = Float.NaN, rowHeightPx = rowHeight))
    }

    @Test
    fun `за края списка строка не уезжает`() {
        assertEquals(0, DragReorder.target(from = 0, steps = -5, count = 4))
        assertEquals(3, DragReorder.target(from = 3, steps = 5, count = 4))
        assertEquals(2, DragReorder.target(from = 1, steps = 1, count = 4))
    }

    @Test
    fun `перестановка не трогает исходный список`() {
        val items = listOf("а", "б", "в", "г")
        assertEquals(listOf("б", "в", "а", "г"), DragReorder.move(items, from = 0, to = 2))
        assertEquals(listOf("а", "б", "в", "г"), items)
    }

    @Test
    fun `перестановка на место не меняет ничего`() {
        val items = listOf("а", "б", "в")
        assertEquals(items, DragReorder.move(items, from = 1, to = 1))
        assertEquals(items, DragReorder.move(items, from = 5, to = 0))
    }
}
