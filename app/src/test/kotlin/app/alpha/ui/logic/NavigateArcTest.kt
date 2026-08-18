package app.alpha.ui.logic

import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Дуга «Наведения» — шкала прибора, и вся её математика обязана быть чистой:
 * если положение стрелки решает рисование, проверить его нечем.
 */
class NavigateArcTest {

    @Test
    fun `the reference sits in the middle and the ends are the ends`() {
        assertEquals(0.5f, NavigateArc.position(1.0, 4.0))
        assertEquals(0f, NavigateArc.position(0.25, 4.0))
        assertEquals(1f, NavigateArc.position(4.0, 4.0))
        assertEquals(
            NavigateArc.START_DEGREES + NavigateArc.SWEEP_DEGREES / 2f,
            NavigateArc.angleDegrees(1.0, 4.0),
        )
    }

    /** Равные множители — равные расстояния: иначе шкала врёт про интервалы. */
    @Test
    fun `equal factors are equal distances`() {
        val a = NavigateArc.position(1.0, 16.0)
        val b = NavigateArc.position(2.0, 16.0)
        val c = NavigateArc.position(4.0, 16.0)
        assertTrue(abs((b - a) - (c - b)) < 1e-5f)
    }

    @Test
    fun `values beyond the frame stay on the scale and are named as such`() {
        assertEquals(1f, NavigateArc.position(40.0, 4.0))
        assertEquals(0f, NavigateArc.position(0.01, 4.0))
        assertTrue(NavigateArc.offScale(40.0, 4.0))
        assertTrue(NavigateArc.offScale(0.01, 4.0))
        assertTrue(!NavigateArc.offScale(2.0, 4.0))
    }

    @Test
    fun `ticks are a geometric series inside the frame`() {
        assertEquals(listOf(0.25, 0.5, 1.0, 2.0, 4.0), NavigateArc.ticks(4.0))
        assertTrue(NavigateArc.ticks(16.0).all { it in 1.0 / 16..16.0 })
    }

    /**
     * Базовый кадр прибора и формула макета — один и тот же mapping.
     *
     * `docs/design/main-and-search.html` считает положение как
     * `(log₂ ratio + 2) / 4` на шкале ×0,25…×4. Пока базовая ступень лестницы
     * равна ×4, эта формула и [NavigateArc.position] обязаны давать одно
     * число, иначе прибор в приложении и прибор в эталоне — разные приборы.
     */
    @Test
    fun `the base frame is the mockup scale, tick for tick`() {
        assertEquals(4.0, NavigateArc.LADDER.first(), 1e-9)
        val factor = NavigateArc.LADDER.first()
        for (ratio in listOf(0.25, 0.35, 0.5, 1.0, 1.7, 2.0, 4.0)) {
            val expected = ((ln(ratio) / ln(2.0) + 2.0) / 4.0).toFloat()
            assertEquals(expected, NavigateArc.position(ratio, factor), 1e-5f)
        }
        assertEquals(
            listOf(0.25, 0.5, 1.0, 2.0, 4.0),
            NavigateArc.ticks(factor),
        )
    }

    /** Границы шкалы: за кадром стрелка стоит на конце, а не уходит с дуги. */
    @Test
    fun `the ends clamp and the labels stay readable`() {
        val factor = NavigateArc.LADDER.first()
        assertEquals(0f, NavigateArc.position(0.0001, factor))
        assertEquals(1f, NavigateArc.position(10_000.0, factor))
        // Нечисло и ноль не двигают стрелку с центра: это отсутствие
        // показания, а не показание «в самом низу шкалы».
        assertEquals(0.5f, NavigateArc.position(Double.NaN, factor))
        assertEquals(0.5f, NavigateArc.position(0.0, factor))
        assertEquals("0,25", NavigateArc.factorLabel(0.25))
        assertEquals("4", NavigateArc.factorLabel(4.0))
    }
}
