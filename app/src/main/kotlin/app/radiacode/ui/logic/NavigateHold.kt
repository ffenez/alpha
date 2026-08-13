package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
import app.radiacode.analysis.RateComparison
import app.radiacode.analysis.RateComparisonResult
import app.radiacode.ui.text.SearchRu
import app.radiacode.ui.text.SearchStrings

/**
 * «Замерить здесь» — a still, fixed-length count at the spot the sweep found.
 *
 * Navigation trades precision for speed on purpose; once the spot is found the
 * trade is no longer worth making, so this collects one honest window while the
 * instrument stands still and hands its numbers to «Проверка».
 *
 * The machine is pure and lives outside the composition
 * ([app.radiacode.service.SpotMeasureRecorder] owns it, app-scoped): leaving the
 * screen or letting the display sleep must not tear a running measurement, the
 * same rule the local-background run already follows.
 */
sealed interface SpotMeasure {

    data object Idle : SpotMeasure

    data class Running(
        val window: CountWindow,
        /** Exposure the run is collecting, s. */
        val targetSeconds: Int,
        val startedAtMillis: Long,
        /** Wall clock of the last accepted reading — the stream watchdog. */
        val lastSampleAtMillis: Long,
        val lastDeviceTimestamp: Long? = null,
        /** The точка отсчёта at the moment the run started, if there was one. */
        val reference: NavigateReference? = null,
    ) : SpotMeasure {
        val collectedSeconds: Int get() = window.seconds.toInt()
    }

    data class Done(val result: SpotResult) : SpotMeasure

    /**
     * Ended without a result — never silently. An average over an interval
     * with an unknown hole in it is a wrong number, and here the wrong number
     * would be the one the user walks away with.
     */
    data class Aborted(
        val reason: BackgroundAbort,
        val collectedSeconds: Int,
        val targetSeconds: Int,
    ) : SpotMeasure
}

/** What a finished spot measurement produced. */
data class SpotResult(
    val window: CountWindow,
    /** Wall clock of the moment the run finished. */
    val atMillis: Long,
    /** Against the точка отсчёта; null when there was none. */
    val comparison: RateComparisonResult? = null,
) {
    val ratePerSecond: Double get() = window.ratePerSecond
    val sigma: Double get() = window.poissonSigma
}

sealed interface SpotEvent {
    data class Start(
        val targetSeconds: Int,
        val nowMillis: Long,
        val reference: NavigateReference? = null,
    ) : SpotEvent

    data class Sample(
        val cps: Float,
        val nowMillis: Long,
        val deviceTimestampMillis: Long,
    ) : SpotEvent

    data class Tick(val nowMillis: Long) : SpotEvent
    data object Cancel : SpotEvent
    data object ServiceRestarted : SpotEvent
    data object Dismiss : SpotEvent
}

object SpotMeasureMachine {

    /**
     * Length of the still count, s. **Engineering parameter**: ten seconds is
     * long enough that at an ordinary background it carries a few hundred
     * events (≈5 % counting error) and short enough to hold an instrument
     * still by hand.
     */
    const val TARGET_SECONDS = 10

    /** The same 10 s the rest of the app calls a dead stream ([Freshness]). */
    const val STREAM_GAP_MILLIS = LocalBackgroundMachine.STREAM_GAP_MILLIS

    fun reduce(state: SpotMeasure, event: SpotEvent): SpotMeasure = when (event) {
        is SpotEvent.Start -> SpotMeasure.Running(
            window = CountWindow.EMPTY,
            targetSeconds = event.targetSeconds,
            startedAtMillis = event.nowMillis,
            lastSampleAtMillis = event.nowMillis,
            reference = event.reference,
        )

        is SpotEvent.Sample -> when (state) {
            is SpotMeasure.Running -> {
                val window = state.window.plusReading(
                    previousMillis = state.lastDeviceTimestamp,
                    timeMillis = event.deviceTimestampMillis,
                    rate = event.cps.toDouble(),
                )
                if (window.seconds >= state.targetSeconds) {
                    SpotMeasure.Done(
                        SpotResult(
                            window = window,
                            atMillis = event.nowMillis,
                            comparison = state.reference?.window
                                ?.takeIf { it.usable && window.usable }
                                ?.let {
                                    RateComparison.compare(
                                        current = window,
                                        background = it,
                                        // The still window is the stationary
                                        // one here: the reference was taken
                                        // while the instrument was moving.
                                        stationaryWindow = window,
                                    )
                                },
                        ),
                    )
                } else {
                    state.copy(
                        window = window,
                        lastSampleAtMillis = event.nowMillis,
                        lastDeviceTimestamp = event.deviceTimestampMillis,
                    )
                }
            }
            else -> state
        }

        is SpotEvent.Tick -> when {
            state !is SpotMeasure.Running -> state
            event.nowMillis - state.lastSampleAtMillis > STREAM_GAP_MILLIS ->
                SpotMeasure.Aborted(
                    reason = BackgroundAbort.STREAM_LOST,
                    collectedSeconds = state.collectedSeconds,
                    targetSeconds = state.targetSeconds,
                )
            else -> state
        }

        SpotEvent.Cancel -> if (state is SpotMeasure.Running) SpotMeasure.Idle else state

        SpotEvent.ServiceRestarted -> when (state) {
            is SpotMeasure.Running -> SpotMeasure.Aborted(
                reason = BackgroundAbort.SERVICE_RESTARTED,
                collectedSeconds = state.collectedSeconds,
                targetSeconds = state.targetSeconds,
            )
            else -> state
        }

        SpotEvent.Dismiss -> if (state is SpotMeasure.Running) state else SpotMeasure.Idle
    }

    /** One honest line naming what actually happened. */
    fun abortWording(
        aborted: SpotMeasure.Aborted,
        t: SearchStrings = SearchRu,
    ): String = when (aborted.reason) {
        BackgroundAbort.STREAM_LOST ->
            t.navSpotAbortStreamLost(aborted.collectedSeconds, aborted.targetSeconds)
        BackgroundAbort.SERVICE_RESTARTED ->
            t.navSpotAbortServiceRestarted(aborted.collectedSeconds, aborted.targetSeconds)
    }
}
