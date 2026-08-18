package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Дыхание индикатора — третий канал того же показания, что звук и вибрация,
 * поэтому его границы совпадают с границами тона: в зоне фона тон молчит, и
 * дыхание там спокойное; выше период сокращается по той же логарифмической
 * шкале и насыщается там же.
 */
class SearchPulseTest {

    @Test
    fun `the background breathes calmly`() {
        assertEquals(SearchPulse.CALM_PERIOD_MILLIS, SearchPulse.periodMillis(null))
        assertEquals(SearchPulse.CALM_PERIOD_MILLIS, SearchPulse.periodMillis(1.0))
        assertEquals(SearchPulse.CALM_PERIOD_MILLIS, SearchPulse.periodMillis(SearchTone.MIN_RATIO))
        assertEquals(SearchPulse.CALM_PERIOD_MILLIS, SearchPulse.periodMillis(Double.NaN))
    }

    @Test
    fun `the pulse saturates where the tone does`() {
        assertEquals(SearchPulse.FAST_PERIOD_MILLIS, SearchPulse.periodMillis(SearchTone.MAX_RATIO))
        assertEquals(SearchPulse.FAST_PERIOD_MILLIS, SearchPulse.periodMillis(64.0))
        assertTrue(SearchTone.frequencyHz(SearchTone.MAX_RATIO) == SearchTone.MAX_HZ)
    }

    @Test
    fun `equal factors shorten the period equally`() {
        // Шкала логарифмическая: одинаковое удвоение — одинаковый шаг ритма.
        val a = SearchPulse.periodMillis(1.15 * 2)
        val b = SearchPulse.periodMillis(1.15 * 4)
        val c = SearchPulse.periodMillis(1.15 * 8).coerceAtLeast(SearchPulse.FAST_PERIOD_MILLIS)
        assertTrue(a > b && b >= c, "$a $b $c")
        val firstStep = a.toDouble() / b
        val secondStep = b.toDouble() / c
        assertTrue(kotlin.math.abs(firstStep - secondStep) < 0.35, "$firstStep vs $secondStep")
    }

    @Test
    fun `the period never leaves its bounds`() {
        for (ratio in listOf(0.1, 0.9, 1.2, 2.0, 4.0, 8.0, 100.0)) {
            val period = SearchPulse.periodMillis(ratio)
            assertTrue(period in SearchPulse.FAST_PERIOD_MILLIS..SearchPulse.CALM_PERIOD_MILLIS)
        }
    }
}
