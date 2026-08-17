package app.alpha.ui.logic

import kotlin.math.abs
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
}
