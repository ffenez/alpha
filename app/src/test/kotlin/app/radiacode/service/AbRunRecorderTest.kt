package app.radiacode.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Прогон принадлежит графу, а не экрану: арифметика обратного отсчёта не должна
 * зависеть от того, открыт ли экран, — поэтому она отделена и проверяется без
 * него.
 */
class AbRunTest {

    private val run = AbRun(
        experimentId = 1,
        runId = 7,
        label = "A",
        startedAtMillis = 1_000_000L,
        plannedSeconds = 300,
    )

    @Test
    fun `elapsed time counts from the start and never goes negative`() {
        assertEquals(0L, run.elapsedSeconds(run.startedAtMillis))
        assertEquals(42L, run.elapsedSeconds(run.startedAtMillis + 42_000L))
        // Часы прибора могут шагнуть назад — отрицательного прошедшего времени
        // не бывает.
        assertEquals(0L, run.elapsedSeconds(run.startedAtMillis - 5_000L))
    }

    @Test
    fun `a fixed run counts down to zero and stops there`() {
        assertEquals(300L, run.remainingSeconds(run.startedAtMillis))
        assertEquals(60L, run.remainingSeconds(run.startedAtMillis + 240_000L))
        assertEquals(0L, run.remainingSeconds(run.startedAtMillis + 300_000L))
        assertEquals(0L, run.remainingSeconds(run.startedAtMillis + 900_000L))
    }

    @Test
    fun `a run without a planned length has nothing to count down`() {
        val open = run.copy(plannedSeconds = 0)
        assertNull(open.remainingSeconds(open.startedAtMillis + 10_000L))
        assertTrue(open.elapsedSeconds(open.startedAtMillis + 10_000L) == 10L)
    }
}
