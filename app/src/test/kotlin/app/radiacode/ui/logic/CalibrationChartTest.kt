package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Картинка калибровки не имеет права получить нечисло.
 *
 * Полевой урок: экран падал при открытии, а все прогоны были зелёными —
 * Compose-канва в модульных тестах не выполняется, поэтому `NaN` в координате
 * доезжает до отрисовки только на телефоне. Геометрия вынесена в чистые
 * функции именно ради этих проверок.
 */
class CalibrationChartTest {

    @Test
    fun `a fit that returned nothing leaves the chart unbuilt instead of drawing NaN`() {
        assertNull(
            CalibrationChart.axisTop(
                fittedAtTop = Double.NaN,
                approximationAtTop = Double.NaN,
                measuredWidths = emptyList(),
            ),
        )
        // Бесконечность — тот же случай: рисовать нечего.
        assertNull(
            CalibrationChart.axisTop(
                fittedAtTop = Double.POSITIVE_INFINITY,
                approximationAtTop = Double.NaN,
                measuredWidths = listOf(Double.NaN),
            ),
        )
    }

    @Test
    fun `a single usable value is enough to build the axis`() {
        // Подгонка не удалась, но приближение и измерения есть — картинка
        // остаётся полезной.
        val top = CalibrationChart.axisTop(
            fittedAtTop = Double.NaN,
            approximationAtTop = 110.0,
            measuredWidths = listOf(Double.NaN, 83.0),
        )
        assertTrue(top != null && top > 110.0, "верх шкалы не оставил запаса: $top")
    }

    @Test
    fun `a fraction is finite and inside the field for any input`() {
        assertNull(CalibrationChart.fraction(Double.NaN, 3000.0))
        assertNull(CalibrationChart.fraction(100.0, 0.0))
        assertNull(CalibrationChart.fraction(100.0, Double.NaN))
        // За пределами поля значение зажимается, а не рисуется по чужой геометрии.
        assertEquals(1f, CalibrationChart.fraction(9000.0, 3000.0))
        assertEquals(0f, CalibrationChart.fraction(-50.0, 3000.0))
        assertEquals(0.5f, CalibrationChart.fraction(1500.0, 3000.0))
    }

    @Test
    fun `a curve skips points the model cannot compute`() {
        val curve = CalibrationChart.curveFractions(3000.0, 120.0, steps = 10) { energy ->
            if (energy < 1000.0) Double.NaN else 90.0
        }
        assertTrue(curve.isNotEmpty(), "кривая пропала целиком")
        assertTrue(curve.all { it.first.isFinite() && it.second.isFinite() })
        // Точки ниже 1000 кэВ не нарисованы — модель там ничего не даёт.
        assertTrue(curve.none { it.first < 1000f / 3000f })
    }

    @Test
    fun `without a measured range nothing is shaded`() {
        // Закрасить всё поле «экстраполяцией» значило бы утверждать то, чего
        // никто не считал.
        assertNull(CalibrationChart.extrapolationBands(Double.NaN, Double.NaN, 3000.0))
        assertNull(CalibrationChart.extrapolationBands(2000.0, 1000.0, 3000.0))
        val bands = CalibrationChart.extrapolationBands(1100.0, 2615.0, 3000.0)
        assertTrue(bands != null)
        assertTrue(bands.first > 0f && bands.second > 0f, "$bands")
        assertTrue(bands.first + bands.second < 1f, "затенено всё поле: $bands")
    }
}
