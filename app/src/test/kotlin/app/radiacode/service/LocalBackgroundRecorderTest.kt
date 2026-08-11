package app.radiacode.service

import app.radiacode.data.db.SampleEntity
import app.radiacode.ui.logic.BackgroundAbort
import app.radiacode.ui.logic.LocalBackground
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The measurement lives outside the composition, so what is tested here is
 * the wiring: real samples advance it, a restart aborts it, and a finished run
 * stores exactly one reference.
 */
class LocalBackgroundRecorderTest {

    private fun sample(timestamp: Long, cps: Float) = SampleEntity(
        id = timestamp,
        timestamp = timestamp,
        doseRate = 0.1f,
        doseRateErr = 8f,
        countRate = cps,
        countRateErr = 5f,
        flags = 0,
        realTimeFlags = 0,
    )

    @Test
    fun `a full run stores the average exactly once`() = runTest {
        val samples = MutableStateFlow<SampleEntity?>(null)
        val running = MutableStateFlow(true)
        val stored = mutableListOf<Float>()
        var now = 0L
        val recorder = LocalBackgroundRecorder(
            scope = backgroundScope,
            samples = samples,
            serviceRunning = running,
            storeReference = { stored += it },
            clock = { now },
        )

        recorder.start(targetSamples = 3)
        runCurrent()
        listOf(20f, 22f, 24f).forEachIndexed { index, cps ->
            now = (index + 1) * 1_000L
            samples.value = sample(now, cps)
            runCurrent()
        }

        val done = assertIs<LocalBackground.Done>(recorder.state.value)
        assertTrue(abs(done.cps - 22f) < 1e-5f)
        assertEquals(listOf(22f), stored)
    }

    @Test
    fun `the same row re-emitted is counted once`() = runTest {
        val samples = MutableStateFlow<SampleEntity?>(null)
        val recorder = LocalBackgroundRecorder(
            scope = backgroundScope,
            samples = samples,
            serviceRunning = MutableStateFlow(true),
            storeReference = {},
            clock = { 1_000L },
        )

        recorder.start(targetSamples = 5)
        runCurrent()
        samples.value = sample(1_000L, 20f)
        runCurrent()
        samples.value = sample(1_000L, 20f).copy(id = 99)
        runCurrent()

        assertEquals(1, assertIs<LocalBackground.Running>(recorder.state.value).collected)
    }

    @Test
    fun `a service restart mid-measurement aborts and stores nothing`() = runTest {
        val samples = MutableStateFlow<SampleEntity?>(null)
        val running = MutableStateFlow(true)
        val stored = mutableListOf<Float>()
        val recorder = LocalBackgroundRecorder(
            scope = backgroundScope,
            samples = samples,
            serviceRunning = running,
            storeReference = { stored += it },
            clock = { 1_000L },
        )

        recorder.start(targetSamples = 3)
        runCurrent()
        samples.value = sample(1_000L, 20f)
        runCurrent()
        running.value = false
        runCurrent()

        val aborted = assertIs<LocalBackground.Aborted>(recorder.state.value)
        assertEquals(BackgroundAbort.SERVICE_RESTARTED, aborted.reason)
        assertTrue(stored.isEmpty())

        // Late samples must not resurrect the run.
        samples.value = sample(2_000L, 20f)
        runCurrent()
        assertIs<LocalBackground.Aborted>(recorder.state.value)
    }

    @Test
    fun `a stalled stream aborts on the watchdog`() = runTest {
        val samples = MutableStateFlow<SampleEntity?>(null)
        var now = 0L
        val recorder = LocalBackgroundRecorder(
            scope = backgroundScope,
            samples = samples,
            serviceRunning = MutableStateFlow(true),
            storeReference = {},
            clock = { now },
        )

        recorder.start(targetSamples = 45)
        runCurrent()
        now = 1_000L
        samples.value = sample(1_000L, 20f)
        runCurrent()

        now = 20_000L
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(
            BackgroundAbort.STREAM_LOST,
            assertIs<LocalBackground.Aborted>(recorder.state.value).reason,
        )
    }

    @Test
    fun `cancel stops the run and leaves no result behind`() = runTest {
        val samples = MutableStateFlow<SampleEntity?>(null)
        val stored = mutableListOf<Float>()
        val recorder = LocalBackgroundRecorder(
            scope = backgroundScope,
            samples = samples,
            serviceRunning = MutableStateFlow(true),
            storeReference = { stored += it },
            clock = { 1_000L },
        )

        recorder.start(targetSamples = 2)
        runCurrent()
        samples.value = sample(1_000L, 20f)
        runCurrent()
        recorder.cancel()
        runCurrent()

        assertEquals(LocalBackground.Idle, recorder.state.value)

        // The collector is gone: a further sample must not finish the old run.
        samples.value = sample(2_000L, 20f)
        runCurrent()
        assertEquals(LocalBackground.Idle, recorder.state.value)
        assertTrue(stored.isEmpty())
    }
}
