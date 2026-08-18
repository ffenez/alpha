package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Подробный ряд не выдумывает движение между колонками.
 *
 * Дефект, ради которого это существует: на шестичасовом окне колонка приходила
 * из хранения шириной в час, а ломаная шла «минимум прошлой колонки → максимум
 * следующей». Получался час равномерного роста и вертикальное падение —
 * семь треугольников с размахом 0,06…0,17 при часовых медианах 0,115…0,125.
 */
class ChartDetailShapeTest {

    // Три широкие колонки: медианы ровные, размах большой.
    private val x = floatArrayOf(10f, 160f, 310f)
    private val medianY = floatArrayOf(100f, 98f, 101f)
    private val minY = floatArrayOf(140f, 138f, 142f)   // ось вниз: минимум величины — больший y
    private val maxY = floatArrayOf(40f, 42f, 39f)
    private val plottable = booleanArrayOf(true, true, true)
    private val starts = booleanArrayOf(true, false, false)

    @Test
    fun `columns are connected by the median, never by the extremes`() {
        val lines = ChartDetailShape.medianPolylines(x, medianY, plottable, starts)
        assertEquals(1, lines.size)
        assertEquals(listOf(10f to 100f, 160f to 98f, 310f to 101f), lines.single())
        // Ни одна точка ломаной не является крайним значением колонки:
        // именно их соединение и рисовало ложный час роста.
        for ((px, py) in lines.single()) {
            val column = x.indexOfFirst { it == px }
            assertTrue(py != minY[column] && py != maxY[column], "точка ломаной = крайнее значение")
        }
    }

    @Test
    fun `the spread of a column stays inside that column`() {
        val strokes = ChartDetailShape.rangeStrokes(x, minY, maxY, plottable)
        assertEquals(3, strokes.size)
        strokes.forEachIndexed { index, stroke ->
            assertEquals(x[index], stroke.x)
            assertEquals(maxY[index], stroke.topY)
            assertEquals(minY[index], stroke.bottomY)
        }
    }

    @Test
    fun `a gap breaks the line instead of crossing it`() {
        val lines = ChartDetailShape.medianPolylines(
            x, medianY, plottable, booleanArrayOf(true, true, false),
        )
        assertEquals(2, lines.size)
        assertEquals(listOf(10f to 100f), lines[0])
        assertEquals(listOf(160f to 98f, 310f to 101f), lines[1])
    }

    @Test
    fun `a column without spread carries no stroke`() {
        val flatMin = floatArrayOf(100f, 138f, 142f)
        val flatMax = floatArrayOf(100f, 42f, 39f)
        val strokes = ChartDetailShape.rangeStrokes(x, flatMin, flatMax, plottable)
        assertEquals(2, strokes.size)
        assertTrue(strokes.none { it.x == 10f })
    }

    @Test
    fun `unplottable columns are left out of both shapes`() {
        val mask = booleanArrayOf(true, false, true)
        assertEquals(2, ChartDetailShape.medianPolylines(x, medianY, mask, starts).size)
        assertEquals(2, ChartDetailShape.rangeStrokes(x, minY, maxY, mask).size)
    }

    @Test
    fun `a column wider than one measurement leaves the detailed view`() {
        // Точный путь, колонка равна секундному агрегату: сами измерения.
        assertTrue(ChartDetailShape.detailedFits(1_000L, 1_000L, quantilesExact = true))
        // Шестичасовое окно на поле 1080 px: колонка 40 с — сорок измерений,
        // её min–max это порядковые статистики группы, а не скачок в данных.
        assertTrue(!ChartDetailShape.detailedFits(40_000L, 1_000L, quantilesExact = true))
        // Почасовые скетчи: агрегат сам уже статистика при любой колонке.
        assertTrue(!ChartDetailShape.detailedFits(3_600_000L, 3_600_000L, quantilesExact = false))
    }

    @Test
    fun `an unknown geometry does not switch the view`() {
        // Ширина колонки ещё не посчитана — вид не меняется на полпути.
        assertTrue(ChartDetailShape.detailedFits(0L, 1_000L, quantilesExact = true))
        assertTrue(ChartDetailShape.detailedFits(1_000L, 0L, quantilesExact = true))
    }
}
