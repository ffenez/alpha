package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Шкала места отвечает на вопрос «много ли это здесь», и её ось —
 * логарифмическая: равные множители обязаны стоять на равных расстояниях,
 * иначе картинка врёт о том, во сколько раз.
 */
class PlaceScaleTest {

    @Test
    fun `the median sits at a quarter of the scale`() {
        // ×0,5 … ×8 это четыре октавы, и ×1 — первая из них.
        assertEquals(0.25f, PlaceScale.position(1.0), 1e-4f)
        assertEquals(0f, PlaceScale.position(0.5), 1e-4f)
        assertEquals(1f, PlaceScale.position(8.0), 1e-4f)
    }

    @Test
    fun `equal factors are equal distances`() {
        val first = PlaceScale.position(2.0) - PlaceScale.position(1.0)
        val second = PlaceScale.position(4.0) - PlaceScale.position(2.0)
        val third = PlaceScale.position(8.0) - PlaceScale.position(4.0)
        assertEquals(first, second, 1e-4f)
        assertEquals(second, third, 1e-4f)
    }

    @Test
    fun `a value beyond the ends is pinned, and says so`() {
        assertEquals(1f, PlaceScale.position(64.0), 1e-4f)
        assertEquals(0f, PlaceScale.position(0.05), 1e-4f)
        assertTrue(PlaceScale.offScale(value = 2f, medianMicroSvH = 0.1f))
        assertTrue(!PlaceScale.offScale(value = 0.2f, medianMicroSvH = 0.1f))
    }

    @Test
    fun `without a median there is nothing to compare with`() {
        assertNull(PlaceScale.positionOf(value = 0.14f, medianMicroSvH = null))
        assertNull(PlaceScale.positionOf(value = 0.14f, medianMicroSvH = 0f))
        assertNull(PlaceScale.positionOf(value = null, medianMicroSvH = 0.12f))
        assertTrue(!PlaceScale.offScale(value = 0.14f, medianMicroSvH = null))
    }

    @Test
    fun `the ticks are the doublings of the scale`() {
        assertEquals(listOf(0.5, 1.0, 2.0, 4.0, 8.0), PlaceScale.ticks())
        // Обратное преобразование сходится с прямым.
        for (ratio in PlaceScale.ticks()) {
            assertEquals(ratio, PlaceScale.ratioAt(PlaceScale.position(ratio)), 1e-6)
        }
    }

    @Test
    fun `a reading right at the median lands on its mark`() {
        assertEquals(
            PlaceScale.position(1.0),
            PlaceScale.positionOf(value = 0.12f, medianMicroSvH = 0.12f)!!,
            1e-4f,
        )
    }
}
