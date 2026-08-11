package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
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
        /** Counts, exposure and scatter accumulated so far. */
        val window: CountWindow,
        val target: Int,
        val startedAtMillis: Long,
        /** Wall clock of the last accepted sample — the stream-gap watchdog. */
        val lastSampleAtMillis: Long,
        /** Device instant of the last accepted reading, for its exposure. */
        val lastDeviceTimestamp: Long? = null,
        val context: BackgroundContext = BackgroundContext(),
    ) : LocalBackground {
        val collected: Int get() = window.samples
        val progress: Float get() = collected.toFloat() / target
    }

    /** Averaging finished; [record] is the reference the screen compares against. */
    data class Done(val record: BackgroundRecord) : LocalBackground {
        val cps: Float get() = record.cps
        val samples: Int get() = record.window.samples
        val atMillis: Long get() = record.atMillis
    }

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

/**
 * Where and with what the reference is being recorded (redesign §6). Captured
 * at [BackgroundEvent.Start] and carried into the record: deciding it at the
 * *end* of the run would stamp the reference with the profile the user walked
 * into, not the one they measured in.
 */
data class BackgroundContext(
    val profileId: Long? = null,
    val profileName: String? = null,
    val deviceSerial: String? = null,
)

sealed interface BackgroundEvent {
    data class Start(
        val target: Int,
        val nowMillis: Long,
        val context: BackgroundContext = BackgroundContext(),
    ) : BackgroundEvent

    /**
     * One accepted reading. [deviceTimestampMillis] is the instrument's own
     * instant for the record — the exposure of the averaging window is built
     * from those, never from arrival times, which carry the poll jitter.
     */
    data class Sample(
        val cps: Float,
        val nowMillis: Long,
        val deviceTimestampMillis: Long,
    ) : BackgroundEvent

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
            window = CountWindow.EMPTY,
            target = event.target,
            startedAtMillis = event.nowMillis,
            lastSampleAtMillis = event.nowMillis,
            context = event.context,
        )

        is BackgroundEvent.Sample -> when (state) {
            is LocalBackground.Running -> {
                val window = state.window.plusReading(
                    previousMillis = state.lastDeviceTimestamp,
                    timeMillis = event.deviceTimestampMillis,
                    rate = event.cps.toDouble(),
                )
                if (window.samples >= state.target) {
                    LocalBackground.Done(
                        BackgroundRecord(
                            window = window,
                            atMillis = event.nowMillis,
                            targetSamples = state.target,
                            profileId = state.context.profileId,
                            profileName = state.context.profileName,
                            deviceSerial = state.context.deviceSerial,
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
 * Expected fluctuation of a **single reading** around the recorded background,
 * ≈95 %: bg ± 2σ, widened by the uncertainty of the reference itself.
 *
 * A single 1 s reading scatters with variance λ; the estimate of λ from a
 * finite background run carries λ/t_b on top of that, so the honest half-width
 * is 2·√(λ·(1 + 1/t_b)). With a 45 s reference the correction is about one
 * percent — small, and drawn anyway, because a band that pretends the
 * reference is exact is the same mistake as the forbidden naive σ (§3).
 *
 * It is a statistical statement, not a threshold: a point outside it is what
 * counting statistics produces one time in twenty by itself, which is why the
 * verdict is decided by [SearchLadder] over a window and not by this band.
 */
fun backgroundBand(record: BackgroundRecord): ClosedFloatingPointRange<Float> {
    val rate = record.window.ratePerSecond
    if (rate <= 0.0) return 0f..0f
    val variance = rate * (1.0 + 1.0 / record.window.seconds)
    val sigma = sqrt(variance).toFloat()
    val centre = rate.toFloat()
    return (centre - 2f * sigma).coerceAtLeast(0f)..(centre + 2f * sigma)
}

private const val FULL_SCALE_FACTOR = 5f
