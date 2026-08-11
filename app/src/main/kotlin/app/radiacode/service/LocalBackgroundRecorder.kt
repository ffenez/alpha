package app.radiacode.service

import app.radiacode.data.db.SampleEntity
import app.radiacode.ui.logic.BackgroundEvent
import app.radiacode.ui.logic.BackgroundRef
import app.radiacode.ui.logic.LocalBackground
import app.radiacode.ui.logic.LocalBackgroundMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owner of the Поиск local-background measurement (45 s of averaging).
 *
 * It lives in the app graph, not in the composition: the measurement used to
 * be `remember`ed by the Поиск screen, so switching tabs or letting the
 * display sleep silently destroyed it. Here it keeps averaging off the same
 * sample stream the service writes, and the screen is a pure observer of
 * [state] — coming back to Поиск shows live progress, and a run that finished
 * while the screen was away is simply there.
 *
 * Cancelling stays explicit ([cancel]); nothing else stops a run except an
 * honest abort (stream gap, service restart), which never stores a reference.
 */
class LocalBackgroundRecorder(
    private val scope: CoroutineScope,
    private val samples: Flow<SampleEntity?>,
    private val serviceRunning: StateFlow<Boolean>,
    /** Persists the finished reference (Настройки DataStore). */
    private val storeReference: suspend (Float) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<LocalBackground>(LocalBackground.Idle)
    val state: StateFlow<LocalBackground> = _state.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    fun start(targetSamples: Int = BackgroundRef.DEFAULT_TARGET_SAMPLES) {
        job?.cancel()
        job = null
        apply(BackgroundEvent.Start(targetSamples, clock()))
        job = scope.launch {
            // A restart of the measurement service means an unknown hole in
            // the averaging window — abort rather than average across it.
            val serviceAtStart = serviceRunning.value
            launch {
                serviceRunning.collect { running ->
                    if (running != serviceAtStart) apply(BackgroundEvent.ServiceRestarted)
                }
            }
            launch {
                while (true) {
                    delay(TICK_MILLIS)
                    apply(BackgroundEvent.Tick(clock()))
                }
            }
            // The stream is a «latest row» flow: the same row re-emits on any
            // write, so records are counted by their device timestamp.
            var lastTimestamp: Long? = null
            samples.collect { sample ->
                if (sample == null || sample.timestamp == lastTimestamp) return@collect
                lastTimestamp = sample.timestamp
                apply(BackgroundEvent.Sample(sample.countRate, clock()))
            }
        }
    }

    fun cancel() = apply(BackgroundEvent.Cancel)

    /** The user has seen the result/reason; clear it. */
    fun dismiss() = apply(BackgroundEvent.Dismiss)

    private fun apply(event: BackgroundEvent) {
        val next = synchronized(lock) {
            val next = LocalBackgroundMachine.reduce(_state.value, event)
            if (next == _state.value) return
            _state.value = next
            next
        }
        // Store before stopping the collectors: the write must not be killed
        // by the cancellation it triggers.
        if (next is LocalBackground.Done) scope.launch { storeReference(next.cps) }
        if (next !is LocalBackground.Running) {
            job?.cancel()
            job = null
        }
    }

    private companion object {
        /** Watchdog resolution; the gap it guards is 10 s. */
        const val TICK_MILLIS = 1_000L
    }
}
