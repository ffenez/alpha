package app.radiacode.ui.chart

import app.radiacode.ui.logic.LinearDoseScale
import app.radiacode.ui.logic.LogDoseScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Переход оси значений: анимируется ПРЕДСТАВЛЕНИЕ, не данные.
 *
 * Промежуточный масштаб — это другая шкала для тех же измерений; ни одного
 * нового значения по дороге появиться не может, и проверяется здесь именно то,
 * что переход остаётся внутри исходного и целевого диапазонов.
 */
class ChartYAxisTest {

    @Test
    fun `середина перехода лежит между кадрами`() {
        val from = LinearDoseScale(maxValue = 0.30f, minValue = 0.10f)
        val to = LinearDoseScale(maxValue = 0.50f, minValue = 0.20f)
        val mid = ChartYAxis.interpolate(from, to, 0.5f)
        assertEquals(0.40f, mid.maxValue, 0.001f)
        assertEquals(0.15f, mid.minValue, 0.001f)
    }

    @Test
    fun `края перехода — сами кадры`() {
        val from = LinearDoseScale(maxValue = 0.30f, minValue = 0.10f)
        val to = LinearDoseScale(maxValue = 0.50f, minValue = 0.20f)
        assertEquals(from, ChartYAxis.interpolate(from, to, 0f))
        assertEquals(to, ChartYAxis.interpolate(from, to, 1f))
    }

    @Test
    fun `логарифмическая ось едет в логарифмах`() {
        val from = LogDoseScale(minValue = 0.1f, maxValue = 1f)
        val to = LogDoseScale(minValue = 0.1f, maxValue = 100f)
        val mid = ChartYAxis.interpolate(from, to, 0.5f)
        // Середина между 1 и 100 на декадной шкале — 10, а не 50.
        assertEquals(10f, mid.maxValue, 0.01f)
    }

    @Test
    fun `смена вида шкалы не интерполируется`() {
        val linear = LinearDoseScale(maxValue = 1f, minValue = 0f)
        val log = LogDoseScale(minValue = 0.1f, maxValue = 10f)
        assertFalse(ChartYAxis.animates(linear, log))
        assertEquals(log, ChartYAxis.interpolate(linear, log, 0.5f))
    }

    @Test
    fun `настоящий скачок ставится сразу`() {
        val background = LinearDoseScale(maxValue = 0.20f, minValue = 0.10f)
        val spike = LinearDoseScale(maxValue = 3.20f, minValue = 0.10f)
        assertFalse(ChartYAxis.animates(background, spike))
    }

    @Test
    fun `подстройка кадра переходит плавно`() {
        val from = LinearDoseScale(maxValue = 0.20f, minValue = 0.10f)
        val to = LinearDoseScale(maxValue = 0.26f, minValue = 0.12f)
        assertTrue(ChartYAxis.animates(from, to))
    }

    @Test
    fun `совпавший кадр не анимируется`() {
        val scale = LinearDoseScale(maxValue = 0.20f, minValue = 0.10f)
        assertFalse(ChartYAxis.animates(scale, scale))
    }

    @Test
    fun `переход не выходит за пределы кадров`() {
        val from = LinearDoseScale(maxValue = 0.30f, minValue = 0.10f)
        val to = LinearDoseScale(maxValue = 0.50f, minValue = 0.20f)
        for (step in 0..10) {
            val mid = ChartYAxis.interpolate(from, to, step / 10f)
            assertTrue(mid.maxValue in from.maxValue..to.maxValue)
            assertTrue(mid.minValue in from.minValue..to.minValue)
        }
    }
}
