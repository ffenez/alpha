package app.alpha.ui.logic

import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Две шкалы одного прибора: место (×0,5…×8) и точка отсчёта (×0,25…×4).
 * Различаются только концами, и обе логарифмические — иначе переключение
 * режима меняло бы не знаменатель, а язык, на котором прибор говорит.
 */
class ArcScaleTest {

    @Test
    fun `the mark scale is the mockup mapping`() {
        // (log₂ ratio + 2) / 4 — формула макета.
        for (ratio in listOf(0.25, 0.5, 1.0, 1.7, 2.0, 4.0)) {
            val expected = ((ln(ratio) / ln(2.0) + 2.0) / 4.0).toFloat()
            assertEquals(expected, ArcScale.MARK.position(ratio), 1e-5f)
        }
        assertEquals(0.5f, ArcScale.MARK.position(1.0), 1e-5f)
    }

    @Test
    fun `the place scale keeps room for real growth`() {
        // ×1 стоит на четверти: вниз от обычного уходить особо некуда, вверх
        // нужен запас — те же пропорции, что у прежней линейной шкалы места.
        assertEquals(0.25f, ArcScale.PLACE.position(1.0), 1e-5f)
        assertEquals(0f, ArcScale.PLACE.position(0.5), 1e-5f)
        assertEquals(1f, ArcScale.PLACE.position(8.0), 1e-5f)
    }

    @Test
    fun `equal factors are equal distances on both scales`() {
        for (scale in listOf(ArcScale.PLACE, ArcScale.MARK)) {
            val a = scale.position(1.0)
            val b = scale.position(2.0)
            val c = scale.position(4.0)
            assertEquals("удвоение должно быть одним шагом", b - a, c - b, 1e-5f)
        }
    }

    @Test
    fun `values beyond the ends stay on the scale and are named as such`() {
        assertEquals(1f, ArcScale.PLACE.position(40.0), 1e-5f)
        assertEquals(0f, ArcScale.MARK.position(0.01), 1e-5f)
        assertTrue(NavigateArc.offScale(40.0, ArcScale.PLACE))
        assertTrue(NavigateArc.offScale(0.01, ArcScale.MARK))
        assertFalse(NavigateArc.offScale(2.0, ArcScale.PLACE))
        // Нечисло не двигает стрелку с ×1: это отсутствие показания.
        assertEquals(
            ArcScale.MARK.position(1.0),
            NavigateArc.position(Double.NaN, ArcScale.MARK),
            1e-5f,
        )
    }

    @Test
    fun `ticks are the doublings between the ends`() {
        assertEquals(listOf(0.5, 1.0, 2.0, 4.0, 8.0), NavigateArc.ticks(ArcScale.PLACE))
        assertEquals(listOf(0.25, 0.5, 1.0, 2.0, 4.0), NavigateArc.ticks(ArcScale.MARK))
    }

    @Test
    fun `the symmetric frame of Наведение is the same object`() {
        assertEquals(ArcScale.MARK, ArcScale.around(4.0))
        for (ratio in listOf(0.3, 1.0, 3.0)) {
            assertEquals(
                NavigateArc.position(ratio, 4.0),
                ArcScale.MARK.position(ratio),
                1e-5f,
            )
        }
    }
}
