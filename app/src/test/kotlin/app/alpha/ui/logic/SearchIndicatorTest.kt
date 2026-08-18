package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Вид индикатора «Наведения» — вопрос привычки, а не точности: обе картинки
 * строятся из одного положения на логарифмической шкале, поэтому переключение
 * не имеет права изменить показание.
 */
class SearchIndicatorTest {

    @Test
    fun `an unknown or missing choice falls back to the needle`() {
        assertEquals(SearchIndicator.NEEDLE, SearchIndicator.of(null))
        assertEquals(SearchIndicator.NEEDLE, SearchIndicator.of(""))
        assertEquals(SearchIndicator.NEEDLE, SearchIndicator.of("dial"))
        assertEquals(SearchIndicator.SCALE, SearchIndicator.of("scale"))
        assertEquals(SearchIndicator.NEEDLE, SearchIndicator.of("needle"))
    }

    @Test
    fun `the id survives a round trip through storage`() {
        for (indicator in SearchIndicator.entries) {
            assertEquals(indicator, SearchIndicator.of(indicator.id))
        }
    }

    @Test
    fun `both views place a ratio identically`() {
        // Стрелка берёт угол, шкала — долю ширины; обе из одной функции, и
        // равные множители стоят на равных расстояниях в обеих.
        val factor = 4.0
        for (ratio in listOf(0.25, 0.5, 1.0, 2.0, 4.0)) {
            val position = NavigateArc.position(ratio, factor)
            val angle = NavigateArc.angleDegrees(ratio, factor)
            assertEquals(
                NavigateArc.START_DEGREES + NavigateArc.SWEEP_DEGREES * position,
                angle,
                1e-4f,
            )
        }
        // Удвоение — одинаковый шаг по шкале на любом её участке.
        val step = NavigateArc.position(2.0, factor) - NavigateArc.position(1.0, factor)
        val stepHigher = NavigateArc.position(4.0, factor) - NavigateArc.position(2.0, factor)
        assertTrue(kotlin.math.abs(step - stepHigher) < 1e-4f, "$step vs $stepHigher")
    }
}
