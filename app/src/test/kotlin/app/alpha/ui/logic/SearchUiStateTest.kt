package app.alpha.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Состояние «Поиска» — то место, где раньше расходились независимые флаги:
 * живое число шло, а экран писал «ждём данные»; точка отсчёта стояла, а
 * стрелки не было. Каждое утверждение ниже — одно из этих расхождений.
 */
class SearchUiStateTest {

    private val now = 1_700_000_000_000L

    private fun navigate(reference: Float?, current: Float): NavigateState {
        var state = NavigateState()
        var time = now - 60_000L
        repeat(30) {
            state = NavigateEngine.onReading(state, time, reference ?: current)
            time += 1_000
        }
        if (reference != null) state = NavigateEngine.mark(state, time)
        repeat(5) {
            state = NavigateEngine.onReading(state, time, current)
            time += 1_000
        }
        return state
    }

    @Test
    fun `a live reading always ends the waiting state`() {
        val state = SearchUiStates.of(
            cps = 23.7f,
            receivedAtMillis = now - 500,
            nowMillis = now,
            connected = true,
            navigate = NavigateState(),
        )
        assertTrue("живое число, а экран ждёт прибор", state !is SearchUiState.WaitingForLiveData)
        assertEquals(SearchUiState.LiveNoReference(23.7f), state)
    }

    @Test
    fun `without a reference the instrument is empty and the action is offered`() {
        val state = SearchUiStates.of(23.7f, now - 200, now, connected = true, navigate = NavigateState())
        assertFalse("стрелке не от чего считать", state.needleVisible)
        assertTrue("действие обязано быть предложено", state.offersReference)
        assertNull(state.ratioOrNull)
    }

    @Test
    fun `a saved reference shows the needle at once`() {
        val navigate = navigate(reference = 23.6f, current = 23.7f)
        val state = SearchUiStates.of(23.7f, now - 200, now, connected = true, navigate = navigate)
        assertTrue(state is SearchUiState.ReferenceReady)
        assertTrue("стрелка обязана стоять", state.needleVisible)
        assertFalse("большое действие обязано уйти", state.offersReference)
        assertEquals(1.0, state.ratioOrNull!!, 0.05)
    }

    @Test
    fun `too little statistics hides neither the needle nor the ratio`() {
        // Точка отсчёта только что поставлена: сравнения ещё нет.
        var navigate = NavigateState()
        var time = now - 30_000L
        repeat(25) {
            navigate = NavigateEngine.onReading(navigate, time, 23.6f)
            time += 1_000
        }
        navigate = NavigateEngine.mark(navigate, time)
        val state = SearchUiStates.of(23.7f, now - 100, now, connected = true, navigate = navigate)
        assertTrue(state is SearchUiState.ReferenceReady)
        assertTrue(state.needleVisible)
        assertTrue("отношение вычислимо и обязано быть", state.ratioOrNull!! > 0.0)
    }

    @Test
    fun `the ratio is current over reference, not a window that has not arrived`() {
        val navigate = navigate(reference = 20.0f, current = 40.0f)
        val state = SearchUiStates.of(40f, now - 100, now, connected = true, navigate = navigate)
        assertEquals(2.0, state.ratioOrNull!!, 0.15)
    }

    @Test
    fun `a stale reading is waiting, not a reading`() {
        val state = SearchUiStates.of(
            cps = 23.7f,
            receivedAtMillis = now - SearchUiStates.LIVE_TIMEOUT_MILLIS - 1,
            nowMillis = now,
            connected = true,
            navigate = NavigateState(),
        )
        assertEquals(SearchUiState.WaitingForLiveData, state)
        assertFalse(state.live)
    }

    @Test
    fun `no connection and no reading is no device`() {
        assertEquals(
            SearchUiState.NoDevice,
            SearchUiStates.of(null, null, now, connected = false, navigate = NavigateState()),
        )
    }

    @Test
    fun `confidence is about the difference, never about the ratio`() {
        // Свежая точка отсчёта: сравнение ещё не посчитано.
        var navigate = NavigateState()
        var time = now - 30_000L
        repeat(25) {
            navigate = NavigateEngine.onReading(navigate, time, 23.6f)
            time += 1_000
        }
        navigate = NavigateEngine.mark(navigate, time)
        assertEquals(SearchConfidence.INSUFFICIENT, SearchUiStates.confidenceOf(navigate))

        // Устоявшийся тот же уровень: критерий проверил отличие и не нашёл его.
        val steady = navigate(reference = 23.6f, current = 23.6f)
        assertEquals(SearchConfidence.NO_DIFFERENCE, SearchUiStates.confidenceOf(steady))
    }
}
