package app.alpha.service

import app.alpha.data.db.SampleEntity
import app.alpha.ui.logic.NavigateReference
import app.alpha.ui.logic.SpotEvent
import app.alpha.ui.logic.SpotMeasure
import app.alpha.ui.logic.SpotMeasureMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owner of the «Замерить здесь» spot measurement of «Наведение».
 *
 * App-scoped for the same reason the local-background run is
 * ([LocalBackgroundRecorder]): a measurement that a tab switch or a sleeping
 * display can destroy is a measurement the user cannot trust to finish. The
 * screen is a pure observer of [state]; the only thing that stops a run besides
 * finishing it is an explicit cancel or an honest abort.
 */
class SpotMeasureRecorder(
    private val scope: CoroutineScope,
    private val samples: Flow<SampleEntity?>,
    private val serviceRunning: StateFlow<Boolean>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<SpotMeasure>(SpotMeasure.Idle)
    val state: StateFlow<SpotMeasure> = _state.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    fun start(
        reference: NavigateReference?,
        targetSeconds: Int = SpotMeasureMachine.TARGET_SECONDS,
    ) {
        job?.cancel()
        job = null
        job = scope.launch {
            apply(SpotEvent.Start(targetSeconds, clock(), reference))
            val serviceAtStart = serviceRunning.value
            launch {
                serviceRunning.collect { running ->
                    if (running != serviceAtStart) apply(SpotEvent.ServiceRestarted)
                }
            }
            launch {
                while (true) {
                    delay(TICK_MILLIS)
                    apply(SpotEvent.Tick(clock()))
                }
            }
            // «Latest row» flow: the same row re-emits on any write, so records
            // are counted by their device timestamp.
            var lastTimestamp: Long? = null
            samples.collect { sample ->
                if (sample == null || sample.timestamp == lastTimestamp) return@collect
                lastTimestamp = sample.timestamp
                apply(
                    SpotEvent.Sample(
                        cps = sample.countRate,
                        nowMillis = clock(),
                        deviceTimestampMillis = sample.timestamp,
                    ),
                )
            }
        }
    }

    fun cancel() = apply(SpotEvent.Cancel)

    /** The user has seen the result or the reason; clear it. */
    fun dismiss() = apply(SpotEvent.Dismiss)

    private fun apply(event: SpotEvent) {
        val next = synchronized(lock) {
            val next = SpotMeasureMachine.reduce(_state.value, event)
            if (next == _state.value) return
            _state.value = next
            next
        }
        if (next !is SpotMeasure.Running) {
            job?.cancel()
            job = null
        }
    }

    private companion object {
        /** Watchdog resolution; the gap it guards is 10 s. */
        const val TICK_MILLIS = 1_000L
    }
}
