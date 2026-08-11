package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BackgroundReferenceTest {

    private fun start(target: Int, now: Long = 0L): LocalBackground =
        LocalBackgroundMachine.reduce(
            LocalBackground.Idle,
            BackgroundEvent.Start(target, now),
        )

    private fun feed(state: LocalBackground, cps: Float, now: Long): LocalBackground =
        LocalBackgroundMachine.reduce(state, BackgroundEvent.Sample(cps, now))

    @Test
    fun `default averaging window is within the 30-60 s the spec allows`() {
        assertTrue(BackgroundRef.DEFAULT_TARGET_SAMPLES in 30..60)
    }

    @Test
    fun `averaging completes at the target with the mean cps`() {
        var state = start(target = 4)
        listOf(20f, 22f, 18f, 24f).forEachIndexed { index, cps ->
            state = feed(state, cps, now = (index + 1) * 1_000L)
        }
        val done = assertIs<LocalBackground.Done>(state)
        assertTrue(abs(done.cps - 21f) < 1e-5f)
        assertEquals(4, done.samples)
        assertEquals(4_000L, done.atMillis)
    }

    @Test
    fun `progress grows monotonically and stays below one until done`() {
        var state = start(target = 10)
        var last = -1f
        repeat(9) { index ->
            state = feed(state, 20f, now = (index + 1) * 1_000L)
            val running = assertIs<LocalBackground.Running>(state)
            assertTrue(running.progress > last)
            assertTrue(running.progress < 1f)
            last = running.progress
        }
    }

    @Test
    fun `a gap in the stream aborts instead of averaging across the hole`() {
        var state = start(target = 45, now = 0L)
        state = feed(state, 20f, now = 1_000L)
        state = LocalBackgroundMachine.reduce(state, BackgroundEvent.Tick(nowMillis = 5_000L))
        assertIs<LocalBackground.Running>(state)

        state = LocalBackgroundMachine.reduce(state, BackgroundEvent.Tick(nowMillis = 12_000L))
        val aborted = assertIs<LocalBackground.Aborted>(state)
        assertEquals(BackgroundAbort.STREAM_LOST, aborted.reason)
        assertEquals(1, aborted.collected)
        assertEquals(45, aborted.target)
    }

    @Test
    fun `a service restart mid-measurement aborts honestly, never completes`() {
        var state = start(target = 45)
        repeat(44) { index -> state = feed(state, 20f, now = (index + 1) * 1_000L) }
        state = LocalBackgroundMachine.reduce(state, BackgroundEvent.ServiceRestarted)

        val aborted = assertIs<LocalBackground.Aborted>(state)
        assertEquals(BackgroundAbort.SERVICE_RESTARTED, aborted.reason)
        assertEquals(44, aborted.collected)
    }

    @Test
    fun `samples after a terminal state are ignored`() {
        val done = LocalBackground.Done(cps = 21f, samples = 45, atMillis = 1_000L)
        assertSame(done, feed(done, 99f, now = 2_000L))

        val aborted = LocalBackground.Aborted(BackgroundAbort.STREAM_LOST, 3, 45)
        assertSame(aborted, feed(aborted, 99f, now = 2_000L))
        assertSame(aborted, LocalBackgroundMachine.reduce(aborted, BackgroundEvent.Tick(99_000L)))
    }

    @Test
    fun `cancel is explicit and needs no explanation afterwards`() {
        var state = start(target = 45)
        state = feed(state, 20f, now = 1_000L)
        state = LocalBackgroundMachine.reduce(state, BackgroundEvent.Cancel)
        assertEquals(LocalBackground.Idle, state)
    }

    @Test
    fun `cancel does not wipe a finished result`() {
        val done = LocalBackground.Done(cps = 21f, samples = 45, atMillis = 1_000L)
        assertSame(done, LocalBackgroundMachine.reduce(done, BackgroundEvent.Cancel))
    }

    @Test
    fun `dismiss clears a result but never a running measurement`() {
        val done = LocalBackground.Done(cps = 21f, samples = 45, atMillis = 1_000L)
        assertEquals(LocalBackground.Idle, LocalBackgroundMachine.reduce(done, BackgroundEvent.Dismiss))

        val running = assertIs<LocalBackground.Running>(start(target = 45))
        assertSame(running, LocalBackgroundMachine.reduce(running, BackgroundEvent.Dismiss))
    }

    @Test
    fun `starting again restarts the averaging from zero`() {
        var state = start(target = 45)
        state = feed(state, 20f, now = 1_000L)
        state = LocalBackgroundMachine.reduce(state, BackgroundEvent.Start(45, nowMillis = 9_000L))
        val running = assertIs<LocalBackground.Running>(state)
        assertEquals(0, running.collected)
        assertEquals(0.0, running.sumCps)
    }

    @Test
    fun `abort wording names the cause and how far it got`() {
        val gap = LocalBackgroundMachine.abortWording(
            LocalBackground.Aborted(BackgroundAbort.STREAM_LOST, collected = 12, target = 45),
        )
        assertTrue(gap.contains("поток данных пропал"), gap)
        assertTrue(gap.contains("12") && gap.contains("45"), gap)

        val restart = LocalBackgroundMachine.abortWording(
            LocalBackground.Aborted(BackgroundAbort.SERVICE_RESTARTED, collected = 12, target = 45),
        )
        assertTrue(restart.contains("перезапустилось"), restart)
    }

    @Test
    fun `delta percent against background`() {
        assertEquals(81, deltaPercent(cps = 38f, backgroundCps = 21f))
        assertEquals(0, deltaPercent(cps = 21f, backgroundCps = 21f))
        assertEquals(-50, deltaPercent(cps = 10.5f, backgroundCps = 21f))
        assertNull(deltaPercent(cps = 38f, backgroundCps = null))
        assertNull(deltaPercent(cps = 38f, backgroundCps = 0f))
    }

    @Test
    fun `led level scales from background to five times background`() {
        assertEquals(0.2f, ledLevel(cps = 21f, backgroundCps = 21f))
        assertEquals(1f, ledLevel(cps = 105f, backgroundCps = 21f))
        assertEquals(1f, ledLevel(cps = 500f, backgroundCps = 21f))
        assertEquals(0f, ledLevel(cps = 30f, backgroundCps = null))
    }

    @Test
    fun `poisson band is bg plus-minus two sigma clamped at zero`() {
        val band = backgroundBand(25f)
        assertEquals(15f, band.start)
        assertEquals(35f, band.endInclusive)
        assertEquals(0f, backgroundBand(1f).start)
    }
}
