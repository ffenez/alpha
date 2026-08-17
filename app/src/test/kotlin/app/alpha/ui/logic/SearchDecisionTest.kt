package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Окно решения следует из того, что видно: явное превышение подтверждается
 * быстро, еле заметное копится дольше, а слишком тонкое честно упирается в
 * предел ожидания вместо обещания «ещё сорок минут».
 */
class SearchDecisionTest {

    private val background = 25.0

    @Test
    fun `a large excess is decided quickly`() {
        val window = SearchDecision.of(background, observedFraction = 0.5, collectedSeconds = 0)
        assertEquals(SearchDecision.MIN_SECONDS, window.targetSeconds)
        assertTrue(!window.atLimit)
    }

    @Test
    fun `a small difference takes longer`() {
        val big = SearchDecision.of(background, 0.5, 0).targetSeconds
        val small = SearchDecision.of(background, 0.08, 0).targetSeconds
        assertTrue(small > big, "$small vs $big")
        assertTrue(small <= SearchDecision.MAX_SECONDS)
    }

    /** Ниже счёт — дольше ждать: та же добавка при 5 с⁻¹ набирается медленнее. */
    @Test
    fun `a quieter instrument needs more time for the same fraction`() {
        val loud = SearchDecision.of(50.0, 0.1, 0).targetSeconds
        val quiet = SearchDecision.of(5.0, 0.1, 0).targetSeconds
        assertTrue(quiet > loud, "$quiet vs $loud")
    }

    /**
     * Отличие тоньше того, что различимо за разумное время, не превращается в
     * бесконечное ожидание: окно упирается в предел и говорит об этом.
     */
    @Test
    fun `an unresolvable difference hits the limit instead of promising forever`() {
        val window = SearchDecision.of(background, observedFraction = 0.001, collectedSeconds = 0)
        assertEquals(SearchDecision.MAX_SECONDS, window.targetSeconds)
        assertTrue(window.atLimit)
    }

    @Test
    fun `nothing seen yet is treated as the finest addition worth chasing`() {
        val blind = SearchDecision.of(background, observedFraction = null, collectedSeconds = 0)
        val finest = SearchDecision.of(background, SearchDecision.MIN_FRACTION, 0)
        assertEquals(finest.targetSeconds, blind.targetSeconds)
    }

    @Test
    fun `progress and remaining follow what is already collected`() {
        val window = SearchDecision.of(background, 0.08, collectedSeconds = 5)
        assertEquals(window.targetSeconds - 5, window.remainingSeconds)
        assertTrue(window.progress > 0f && window.progress < 1f)
        assertTrue(!window.ready)

        val done = SearchDecision.of(background, 0.5, collectedSeconds = 999)
        assertTrue(done.ready)
        assertEquals(0L, done.remainingSeconds)
        assertEquals(1f, done.progress)
    }

    @Test
    fun `a dead stream is not a decision window`() {
        val window = SearchDecision.of(backgroundCps = 0.0, observedFraction = 0.1, collectedSeconds = 0)
        assertEquals(SearchDecision.MAX_SECONDS, window.targetSeconds)
    }
}
