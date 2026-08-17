package app.alpha.ui.logic

import app.alpha.analysis.CountWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «Замерить здесь» — та же честность, что у замера фона: неполный интервал не
 * превращается в результат, а разрыв потока называется словами.
 */
class NavigateHoldTest {

    private val start = 500_000L

    private fun run(
        state: SpotMeasure,
        seconds: Int,
        cps: Float,
        fromMillis: Long,
    ): SpotMeasure {
        var current = state
        var at = fromMillis
        repeat(seconds) {
            current = SpotMeasureMachine.reduce(
                current,
                SpotEvent.Sample(cps = cps, nowMillis = at, deviceTimestampMillis = at),
            )
            at += 1_000L
        }
        return current
    }

    @Test
    fun `a full run finishes with its exposure and its own sigma`() {
        val started = SpotMeasureMachine.reduce(
            SpotMeasure.Idle,
            SpotEvent.Start(SpotMeasureMachine.TARGET_SECONDS, start),
        )
        val done = run(started, 12, 48f, start)
        assertTrue(done is SpotMeasure.Done, "$done")
        assertTrue(done.result.window.seconds >= SpotMeasureMachine.TARGET_SECONDS)
        assertTrue(done.result.ratePerSecond > 40.0)
        assertTrue(done.result.sigma > 0.0)
        assertTrue(done.result.comparison == null)
    }

    /** С точкой отсчёта результат несёт отношение с интервалом, а не процент. */
    @Test
    fun `with a reference the result carries the exact ratio`() {
        val reference = NavigateReference(
            window = CountWindow.reconstruct(
                LongArray(20) { start - 20_000L + it * 1_000L },
                DoubleArray(20) { 25.0 },
            ),
            atMillis = start,
        )
        val started = SpotMeasureMachine.reduce(
            SpotMeasure.Idle,
            SpotEvent.Start(SpotMeasureMachine.TARGET_SECONDS, start, reference),
        )
        val done = run(started, 12, 100f, start) as SpotMeasure.Done
        val comparison = done.result.comparison
        assertTrue(comparison != null)
        assertTrue(comparison.ratio > 3.0)
        assertTrue(comparison.ratioLow > 1.0)
    }

    /**
     * Разрыв потока рвёт замер: среднее по интервалу с дырой — неверное число,
     * и здесь именно с ним человек ушёл бы с места.
     */
    @Test
    fun `a stream gap aborts the run instead of averaging across the hole`() {
        val started = SpotMeasureMachine.reduce(
            SpotMeasure.Idle,
            SpotEvent.Start(SpotMeasureMachine.TARGET_SECONDS, start),
        )
        val partial = run(started, 4, 48f, start)
        assertTrue(partial is SpotMeasure.Running)
        val aborted = SpotMeasureMachine.reduce(
            partial,
            SpotEvent.Tick(start + 4_000L + SpotMeasureMachine.STREAM_GAP_MILLIS + 1),
        )
        assertTrue(aborted is SpotMeasure.Aborted)
        assertEquals(BackgroundAbort.STREAM_LOST, aborted.reason)
        assertTrue(SpotMeasureMachine.abortWording(aborted).contains("прерван"))
    }

    @Test
    fun `a service restart aborts too, and cancelling needs no explanation`() {
        val started = SpotMeasureMachine.reduce(
            SpotMeasure.Idle,
            SpotEvent.Start(SpotMeasureMachine.TARGET_SECONDS, start),
        )
        val partial = run(started, 3, 48f, start)
        val restarted = SpotMeasureMachine.reduce(partial, SpotEvent.ServiceRestarted)
        assertTrue(restarted is SpotMeasure.Aborted)
        assertEquals(BackgroundAbort.SERVICE_RESTARTED, restarted.reason)
        assertEquals(
            SpotMeasure.Idle,
            SpotMeasureMachine.reduce(partial, SpotEvent.Cancel),
        )
    }
}
