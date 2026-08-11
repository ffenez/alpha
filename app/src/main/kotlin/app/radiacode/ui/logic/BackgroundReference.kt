package app.radiacode.ui.logic

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Search-mode local background reference (SPEC: "Set local background" —
 * average CPS over 30–60 s, then compare against it).
 *
 * The state machine is pure and lives outside the composition on purpose:
 * the measurement used to be `remember`ed inside the Поиск screen, so leaving
 * the tab or letting the display sleep threw away 45 s of averaging. The owner
 * is now app-scoped ([app.radiacode.service.LocalBackgroundRecorder]) and this
 * file only decides what each event means.
 */
sealed interface LocalBackground {

    /** Nothing running; the stored reference (if any) lives in settings. */
    data object Idle : LocalBackground

    /** Averaging in progress; one [BackgroundEvent.Sample] per 1 Hz reading. */
    data class Running(
        val sumCps: Double,
        val collected: Int,
        val target: Int,
        val startedAtMillis: Long,
        /** Wall clock of the last accepted sample — the stream-gap watchdog. */
        val lastSampleAtMillis: Long,
    ) : LocalBackground {
        val progress: Float get() = collected.toFloat() / target
    }

    /** Averaging finished; [cps] is the reference the screen compares against. */
    data class Done(
        val cps: Float,
        val samples: Int,
        val atMillis: Long,
    ) : LocalBackground

    /**
     * Ended without a reference. Never silently: an average over an interval
     * with a hole in it is a wrong number, and a wrong background is worse
     * than no background — everything on Поиск is relative to it.
     */
    data class Aborted(
        val reason: BackgroundAbort,
        val collected: Int,
        val target: Int,
    ) : LocalBackground
}

/** Why a local-background measurement ended without a reference. */
enum class BackgroundAbort {
    /** The 1 Hz stream stopped for longer than the screen calls «свежий». */
    STREAM_LOST,

    /**
     * The measurement service stopped or restarted mid-run. The averaging
     * window then has an unknown hole in it, so the partial sum is discarded
     * instead of being passed off as a 45-second average.
     */
    SERVICE_RESTARTED,
}

sealed interface BackgroundEvent {
    data class Start(val target: Int, val nowMillis: Long) : BackgroundEvent
    data class Sample(val cps: Float, val nowMillis: Long) : BackgroundEvent

    /** Watchdog tick; the only time-driven transition. */
    data class Tick(val nowMillis: Long) : BackgroundEvent

    /** Explicit user cancel — needs no explanation afterwards. */
    data object Cancel : BackgroundEvent

    data object ServiceRestarted : BackgroundEvent

    /** The user has seen the result; go back to a clean slate. */
    data object Dismiss : BackgroundEvent
}

object LocalBackgroundMachine {

    /**
     * A gap this long means the stream is no longer live — the same 10 s the
     * rest of the app uses to call a reading stale ([Freshness]). Anything
     * shorter would abort on ordinary BLE jitter.
     */
    const val STREAM_GAP_MILLIS = 10_000L

    fun reduce(state: LocalBackground, event: BackgroundEvent): LocalBackground = when (event) {
        is BackgroundEvent.Start -> LocalBackground.Running(
            sumCps = 0.0,
            collected = 0,
            target = event.target,
            startedAtMillis = event.nowMillis,
            lastSampleAtMillis = event.nowMillis,
        )

        is BackgroundEvent.Sample -> when (state) {
            is LocalBackground.Running -> {
                val sum = state.sumCps + event.cps
                val collected = state.collected + 1
                if (collected >= state.target) {
                    LocalBackground.Done(
                        cps = (sum / collected).toFloat(),
                        samples = collected,
                        atMillis = event.nowMillis,
                    )
                } else {
                    state.copy(
                        sumCps = sum,
                        collected = collected,
                        lastSampleAtMillis = event.nowMillis,
                    )
                }
            }
            else -> state
        }

        is BackgroundEvent.Tick -> when {
            state !is LocalBackground.Running -> state
            event.nowMillis - state.lastSampleAtMillis > STREAM_GAP_MILLIS ->
                LocalBackground.Aborted(
                    reason = BackgroundAbort.STREAM_LOST,
                    collected = state.collected,
                    target = state.target,
                )
            else -> state
        }

        BackgroundEvent.Cancel ->
            if (state is LocalBackground.Running) LocalBackground.Idle else state

        BackgroundEvent.ServiceRestarted -> when (state) {
            is LocalBackground.Running -> LocalBackground.Aborted(
                reason = BackgroundAbort.SERVICE_RESTARTED,
                collected = state.collected,
                target = state.target,
            )
            else -> state
        }

        BackgroundEvent.Dismiss ->
            if (state is LocalBackground.Running) state else LocalBackground.Idle
    }

    /** One honest line naming what actually happened. */
    fun abortWording(aborted: LocalBackground.Aborted): String = when (aborted.reason) {
        BackgroundAbort.STREAM_LOST ->
            "замер фона прерван: поток данных пропал на ${aborted.collected} " +
                "из ${aborted.target} с — среднее по неполному интервалу не сохранено"
        BackgroundAbort.SERVICE_RESTARTED ->
            "замер фона прерван: измерение перезапустилось на ${aborted.collected} " +
                "из ${aborted.target} с — в интервале дыра, среднее не сохранено"
    }
}

/** Constants of the reference measurement. */
object BackgroundRef {
    /** 45 s at 1 Hz: inside the 30–60 s window the SPEC allows. */
    const val DEFAULT_TARGET_SAMPLES = 45
}

/** Whole-percent delta vs background; null when there is no reference. */
fun deltaPercent(cps: Float, backgroundCps: Float?): Int? {
    if (backgroundCps == null || backgroundCps <= 0f) return null
    return (((cps - backgroundCps) / backgroundCps) * 100f).roundToInt()
}

/**
 * LED meter drive: full scale = [FULL_SCALE_FACTOR]× background, so the meter
 * sits low on background and saturates near a strong source. Without a
 * reference the meter stays dark — it has nothing honest to show.
 */
fun ledLevel(cps: Float, backgroundCps: Float?): Float {
    if (backgroundCps == null || backgroundCps <= 0f) return 0f
    return (cps / (backgroundCps * FULL_SCALE_FACTOR)).coerceIn(0f, 1f)
}

/**
 * Expected Poisson fluctuation band around the background at 1 s counting:
 * bg ± 2·sqrt(bg) (~95%). Rendered as the dithered band on the search chart —
 * a statistical statement, not an opinion.
 */
fun backgroundBand(backgroundCps: Float): ClosedFloatingPointRange<Float> {
    val sigma = sqrt(backgroundCps.toDouble()).toFloat()
    return (backgroundCps - 2f * sigma).coerceAtLeast(0f)..(backgroundCps + 2f * sigma)
}

private const val FULL_SCALE_FACTOR = 5f
