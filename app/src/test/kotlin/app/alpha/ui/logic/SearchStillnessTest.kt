package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Предложение «Проверить здесь» появляется, когда счёт держится ровно —
 * и не появляется само собой, пока человек идёт.
 */
class SearchStillnessTest {

    private val start = 1_000_000L

    private fun steadyFor(millis: Long): Pair<SearchStillness.State, Long> {
        val state = SearchStillness.step(
            SearchStillness.State(),
            SearchDirection.STEADY,
            start,
        )
        return state to start + millis
    }

    @Test
    fun `a steady count offers verification only after the dwell`() {
        val (state, tooEarly) = steadyFor(SearchStillness.DWELL_MILLIS - 1)
        assertTrue(!SearchStillness.offering(state, tooEarly))

        val (held, late) = steadyFor(SearchStillness.DWELL_MILLIS)
        assertTrue(SearchStillness.offering(held, late))
    }

    @Test
    fun `walking again resets the dwell`() {
        val (state, _) = steadyFor(SearchStillness.DWELL_MILLIS)
        val moved = SearchStillness.step(state, SearchDirection.RISING, start + 1_000)
        assertEquals(null, moved.steadySinceMillis)
        assertTrue(!SearchStillness.offering(moved, start + 60_000))
    }

    /** Нет данных — не повод ни начинать отсчёт, ни сбрасывать его. */
    @Test
    fun `an unknown direction changes nothing`() {
        val (state, _) = steadyFor(0)
        val unknown = SearchStillness.step(state, SearchDirection.UNKNOWN, start + 500)
        assertEquals(state.steadySinceMillis, unknown.steadySinceMillis)
    }

    @Test
    fun `a refused offer is not shown again until the person moves`() {
        val (state, late) = steadyFor(SearchStillness.DWELL_MILLIS)
        val dismissed = SearchStillness.dismiss(state)
        assertTrue(!SearchStillness.offering(dismissed, late))

        // Пошёл дальше и снова остановился — это уже другой повод.
        val moved = SearchStillness.step(dismissed, SearchDirection.RISING, late)
        val stoppedAgain = SearchStillness.step(moved, SearchDirection.STEADY, late + 1_000)
        assertTrue(
            SearchStillness.offering(stoppedAgain, late + 1_000 + SearchStillness.DWELL_MILLIS),
        )
    }
}
