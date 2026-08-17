package app.alpha.ui.chart

import app.alpha.ui.logic.LinearDoseScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ось значений, которую ведут рукой.
 *
 * Автоподбор намеренно не растягивается до далёкого порога: при фоне 0,15 и
 * пороге 0,30 ось до порога сделала бы сам фон плоской чертой. Значит вопрос
 * «где проходят мои пороги относительно того, что сейчас» должен решаться
 * иначе — и здесь проверяется, что он решается честно: кадр меняется, данные
 * нет.
 */
class ChartValueAxisTest {

    private val minSpan = 0.04f
    private val background = ValueWindow(0.12f, 0.20f)

    @Test
    fun `сдвиг вниз пальцем показывает то, что было выше`() {
        val moved = ChartYAxis.pan(background, fractionOfSpan = 1f, minSpan = minSpan)
        assertEquals(background.span, moved.span, 1e-4f)
        assertTrue(moved.min > background.min, "кадр обязан уехать вверх по значениям")
        assertEquals(background.min + background.span, moved.min, 1e-4f)
    }

    @Test
    fun `ниже нуля кадр не опускается`() {
        val moved = ChartYAxis.pan(background, fractionOfSpan = -10f, minSpan = minSpan)
        assertEquals(0f, moved.min)
        assertTrue(moved.span >= minSpan)
    }

    @Test
    fun `масштаб оси сохраняет точку под пальцем`() {
        val zoomed = ChartYAxis.zoom(
            window = background,
            factor = 2f,
            focusFraction = 0.5f,
            minSpan = minSpan,
        )
        val centre = background.min + background.span / 2f
        assertEquals(centre, zoomed.min + zoomed.span / 2f, 1e-4f)
        assertEquals(background.span / 2f, zoomed.span, 1e-4f)
    }

    @Test
    fun `размах не становится меньше значимого`() {
        val zoomed = ChartYAxis.zoom(background, factor = 1_000f, minSpan = minSpan)
        assertEquals(minSpan, zoomed.span, 1e-4f)
    }

    @Test
    fun `ручной кадр становится шкалой один в один`() {
        val window = ValueWindow(0.05f, 1.20f)
        val scale = ChartYAxis.scaleOf(window, logarithmic = false)
        assertEquals(LinearDoseScale(maxValue = 1.20f, minValue = 0.05f), scale)
        assertEquals(0f, scale.fractionOrNull(0.05f))
        assertEquals(1f, scale.fractionOrNull(1.20f))
    }

    @Test
    fun `логарифмическая ось не опускается до нуля`() {
        val scale = ChartYAxis.scaleOf(ValueWindow(0f, 10f), logarithmic = true)
        assertTrue(scale.minValue > 0f)
        assertTrue(scale.logarithmic)
    }

    @Test
    fun `кадр из текущей шкалы — то, что видно сейчас`() {
        val scale = LinearDoseScale(maxValue = 0.22f, minValue = 0.10f)
        assertEquals(ValueWindow(0.10f, 0.22f), ChartYAxis.windowOf(scale))
    }
}
