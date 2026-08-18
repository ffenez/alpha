package app.alpha.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Вид шкалы — вопрос привычки: он не имеет права менять показание, поэтому
 * проверяется ровно одно — что выбор переживает хранение и что неизвестное
 * значение даёт прибор, а не пустоту.
 */
class InstrumentIndicatorTest {

    @Test
    fun `an unknown or missing choice falls back to the dial`() {
        assertEquals(InstrumentIndicator.DIAL, InstrumentIndicator.of(null))
        assertEquals(InstrumentIndicator.DIAL, InstrumentIndicator.of(""))
        assertEquals(InstrumentIndicator.DIAL, InstrumentIndicator.of("needle"))
    }

    @Test
    fun `the id survives a round trip through storage`() {
        for (indicator in InstrumentIndicator.entries) {
            assertEquals(indicator, InstrumentIndicator.of(indicator.id))
        }
    }

    @Test
    fun `both drawings place a ratio identically`() {
        // Обе картинки берут одно и то же положение на шкале: переключение
        // вида не может сдвинуть показание.
        for (scale in listOf(ArcScale.PLACE, ArcScale.MARK)) {
            for (ratio in listOf(0.4, 1.0, 2.0, 6.0)) {
                assertEquals(
                    scale.position(ratio),
                    NavigateArc.position(ratio, scale),
                    1e-6f,
                )
            }
        }
    }
}
