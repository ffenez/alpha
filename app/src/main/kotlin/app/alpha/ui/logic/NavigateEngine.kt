package app.alpha.ui.logic

import app.alpha.analysis.CountWindow
import app.alpha.analysis.RateComparison
import app.alpha.analysis.RateComparisonResult
import kotlin.math.roundToInt

/** Everything «Наведение» knows at one instant, as one immutable value. */
data class NavigateState(
    /** Raw readings, oldest first — the material both windows are built from. */
    val points: List<SearchPoint> = emptyList(),
    /**
     * The short-window rate at each reading, oldest first — the drawn line.
     *
     * Appended **only** on a reading, never on a tick: the picture must be a
     * function of what the instrument delivered, not of how often the screen
     * redrew (the same release criterion the verify mode is held to).
     */
    val trace: List<SearchPoint> = emptyList(),
    val fast: CountWindow? = null,
    val local: CountWindow? = null,
    val trend: NavigateTrend = NavigateTrend.COLLECTING,
    /** The exact test behind [trend]: fast window against the one before it. */
    val trendComparison: RateComparisonResult? = null,
    /** When the current [trend] was entered (instrument clock). */
    val trendSinceMillis: Long? = null,
    /** A different trend waiting out its dwell time, and since when. */
    val pendingTrend: NavigateTrend? = null,
    val pendingSinceMillis: Long? = null,
    val reference: NavigateReference? = null,
    /** Fast window against the frozen reference; null when it may not be run. */
    val referenceComparison: RateComparisonResult? = null,
    val peak: NavigatePeak? = null,
    val scale: NavigateScaleState? = null,
) {
    val latest: SearchPoint? get() = points.lastOrNull()

    /** R_fast / R_reference; null without both. */
    val referenceRatio: Double?
        get() {
            val reference = reference?.ratePerSecond ?: return null
            val current = fast?.ratePerSecond ?: return null
            if (reference <= 0.0 || !current.isFinite()) return null
            return current / reference
        }
}

/**
 * The «Наведение» loop: three rate estimates, one exact test, four states.
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** Readings are kept on a [KEEP_MILLIS] tape. Two counting
 *    windows are cut from it — a **short** one ending now and a **local** one
 *    ending where the short one begins — with lengths chosen by
 *    [NavigateWindows] from a target relative counting error. Counts are
 *    reconstructed by instrument time (N ≈ Σ rᵢ·Δtᵢ, a hole shortens the
 *    exposure), exactly as in [SearchEngine]. The direction is the verdict of
 *    [RateComparison.compare] on those two windows — the conditional binomial
 *    test of Przyborowski–Wilenski with Clopper–Pearson bounds. No new
 *    statistic is invented here and the forbidden naive z never appears.
 * 2. **Assumptions.** The two windows are **disjoint** by construction, which
 *    is what makes them independent enough for the conditional test; nesting
 *    the short window inside the local one would have compared a window with
 *    itself. Each window is assumed stationary within itself — while walking
 *    that is false, and it is the reason a single window is a hint about where
 *    to move, never a verdict: the verdict lives in «Проверка».
 * 3. **Units.** Rates s⁻¹, times milliseconds, exposures seconds, ratios
 *    dimensionless.
 * 4. **Reference.** [RateComparison]; the hysteresis idea is the same
 *    «magnitude AND duration» rule as [SearchLadder], with much shorter times
 *    because this mode is answering a faster question.
 * 5. **Validation data.** `NavigateEngineTest`: a stationary stream never
 *    leaves «без явного изменения», a step reaches «растёт» and holds it
 *    through single quiet windows, a dropped stream falls back to «набираю
 *    статистику», and the same readings fed at different tick rates produce
 *    the same windows and the same states. The one thing an extra tick can do
 *    is let a dwell expire up to one tick earlier: it changes *when* a
 *    transition is shown, never *which* state the readings lead to, because
 *    the windows are anchored on the readings and not on the clock.
 * 6. **Limitations.** [ALPHA], [MIN_COUNTS], [STALE_MILLIS], [ENTER_MILLIS] and
 *    [RELEASE_MILLIS] are **engineering parameters** chosen for a hand-held
 *    sweep. What makes this mode fast is the short windows and the short
 *    dwell, not a lowered statistical bar — α is the same as the verify
 *    mode's. Nothing here is a statement about dose, a nuclide or safety, and
 *    a single pair of windows is a hint about where to move, never a verdict
 *    about the place.
 * 7. **Tests.** `app/src/test/.../ui/logic/NavigateEngineTest.kt`.
 * 8. **Algorithm version.** [RateComparison.ALGORITHM_VERSION] — the windowing
 *    and the state machine add no mathematics of their own.
 * 9. **User-facing meaning.** «Счёт в последние секунды выше, чем в секунды
 *    перед ними» — where to move the instrument next, and nothing more.
 */
object NavigateEngine {

    /** Tape length: the longest local window plus the longest short one. */
    const val KEEP_MILLIS = 40_000L

    /** How much of the recent past the mini-chart draws. */
    const val TRACE_MILLIS = 20_000L

    /** Backstop against an instrument clock that steps backwards (cdump #63). */
    private const val MAX_POINTS = 200

    /**
     * Significance a single pair of windows must reach to move the arrow.
     *
     * **Engineering parameter**, and deliberately the *same* as
     * [SearchLadder.ALPHA]: what makes this mode fast is the windows and the
     * short dwell, not a lowered bar for calling a difference. A looser α was
     * tried on paper and rejected — it does not buy sensitivity (a 20 % rise
     * is unresolved at any α with these exposures) and it does buy chatter:
     * at two evaluations a second, α = 0.05 blinks «растёт» on a stationary
     * background every few dozen seconds, which is a moving arrow that means
     * nothing.
     */
    const val ALPHA = 0.01

    /**
     * Below this many reconstructed events in either window the state is
     * «набираю статистику». **Engineering parameter**: at 20 events the
     * relative counting error is already 22 %, and below it the test would be
     * comparing two numbers that are mostly noise.
     */
    const val MIN_COUNTS = 20.0

    /** A new direction must survive this long before it is shown. */
    const val ENTER_MILLIS = 1_000L

    /** …and a shown direction survives this long after it stops resolving. */
    const val RELEASE_MILLIS = 2_500L

    /**
     * A stream silent this long stops being about «now», and the arrow drops
     * to «набираю статистику». **Engineering parameter**: three missed records
     * at 1 Hz — long enough to ride out ordinary BLE jitter, short enough that
     * a frozen arrow is never mistaken for a measurement.
     */
    const val STALE_MILLIS = 3_000L

    /** Appends a reading taken at [timeMillis] (instrument clock). */
    fun onReading(
        state: NavigateState,
        timeMillis: Long,
        cps: Float,
        nowMillis: Long = timeMillis,
    ): NavigateState {
        val points = (state.points + SearchPoint(timeMillis, cps))
            .filter { timeMillis - it.timeMillis <= KEEP_MILLIS }
            .takeLast(MAX_POINTS)
        return evaluate(state, points, nowMillis, traceAt = timeMillis)
    }

    /**
     * Re-evaluates without a new reading — the path that lets a lost stream
     * become visible instead of freezing the last arrow.
     */
    fun onTick(state: NavigateState, nowMillis: Long): NavigateState =
        evaluate(state, state.points, nowMillis, traceAt = null)

    /**
     * «Запомнить здесь»: freezes the last local window as the точка отсчёта.
     *
     * It is a temporary reference of this sweep — the profile and its ordinary
     * background are not touched, and nothing is written anywhere.
     */
    fun mark(
        state: NavigateState,
        nowMillis: Long,
        magneticUt: Float? = null,
    ): NavigateState {
        val latest = state.latest ?: return state
        val seconds = NavigateWindows.localSeconds(latest.cps.toDouble())
        // Anchored at the newest reading, like every other window here: the
        // reference has to be an interval the instrument actually delivered.
        val anchor = latest.timeMillis
        val window = window(state.points, anchor - (seconds * 1000).toLong(), anchor)
            ?: return state
        return evaluate(
            state.copy(
                reference = NavigateReference(
                    window = window,
                    atMillis = anchor,
                    magneticUt = magneticUt,
                ),
            ),
            state.points,
            nowMillis,
            traceAt = null,
        )
    }

    /**
     * Снять точку отсчёта.
     *
     * То, что человек начал, он должен уметь и прекратить. Пока отсчёт стоял,
     * дуга и отклик считали ОТ НЕГО, и единственным способом вернуться к
     * обычному наведению был уход с экрана — то есть отмена делалась чем
     * угодно, только не кнопкой.
     */
    fun clearMark(state: NavigateState): NavigateState =
        state.copy(reference = null, referenceComparison = null)

    /** Forgets the held maximum; the next window starts a new one. */
    fun resetPeak(state: NavigateState): NavigateState = state.copy(peak = null)

    private fun evaluate(
        state: NavigateState,
        points: List<SearchPoint>,
        nowMillis: Long,
        traceAt: Long?,
    ): NavigateState {
        // Windows are anchored at the **newest reading**, not at the wall
        // clock: at a high count rate the short window is one second long and
        // records arrive once a second, so a window ending «now» would empty
        // and refill between readings and the arrow would blink at the tick
        // rate. A stream that has actually stopped is caught by [STALE_MILLIS]
        // instead, which is a statement about the stream rather than an
        // artefact of when the screen redrew.
        val newest = points.lastOrNull()
        val stale = newest == null || nowMillis - newest.timeMillis > STALE_MILLIS
        val anchor = newest?.timeMillis ?: nowMillis
        val rate = newest?.cps?.toDouble() ?: 0.0
        val fastMillis = (NavigateWindows.fastSeconds(rate) * 1000).toLong()
        val localMillis = (NavigateWindows.localSeconds(rate) * 1000).toLong()
        val fastFrom = anchor - fastMillis
        val fast = if (stale) null else window(points, fastFrom, anchor)
        val local = if (stale) null else window(points, fastFrom - localMillis, fastFrom)

        val trendComparison = comparison(fast, local)
        val raw = trendOf(trendComparison)
        val settled = settle(state, raw, nowMillis)

        val peak = peak(state.peak, fast, anchor)
        val referenceComparison = referenceComparison(state.reference, fast, points, fastFrom)
        val scale = NavigateArc.next(
            state = state.scale,
            nowMillis = nowMillis,
            requiredFactor = NavigateArc.requiredFactor(
                listOfNotNull(
                    ratio(fast?.ratePerSecond, state.reference),
                    ratio(peak?.ratePerSecond, state.reference),
                ),
            ),
        )
        val trace = traceAt?.let { at ->
            val rateNow = fast?.ratePerSecond?.toFloat() ?: return@let state.trace
            (state.trace + SearchPoint(at, rateNow))
                .filter { at - it.timeMillis <= TRACE_MILLIS }
                .takeLast(MAX_POINTS)
        } ?: state.trace.filter { nowMillis - it.timeMillis <= TRACE_MILLIS }

        return state.copy(
            points = points,
            trace = trace,
            fast = fast,
            local = local,
            trend = settled.trend,
            trendComparison = trendComparison,
            trendSinceMillis = settled.since,
            pendingTrend = settled.pending,
            pendingSinceMillis = settled.pendingSince,
            referenceComparison = referenceComparison,
            peak = peak,
            scale = scale,
        )
    }

    /** Readings inside (from, to] as a counting window; null when unusable. */
    fun window(points: List<SearchPoint>, fromMillis: Long, toMillis: Long): CountWindow? {
        val slice = points.filter { it.timeMillis > fromMillis && it.timeMillis <= toMillis }
        if (slice.isEmpty()) return null
        val times = LongArray(slice.size) { slice[it].timeMillis }
        val rates = DoubleArray(slice.size) { slice[it].cps.toDouble() }
        return CountWindow.reconstruct(times, rates).takeIf { it.usable }
    }

    private fun comparison(fast: CountWindow?, local: CountWindow?): RateComparisonResult? {
        if (fast == null || local == null) return null
        if (fast.counts < MIN_COUNTS || local.counts < MIN_COUNTS) return null
        // **Neither window is stationary here, and that is the point.** The
        // dispersion check exists to catch a pre-filtering instrument, and it
        // needs a window measured while standing still — the recorded
        // background of «Проверка». On a window collected while sweeping, the
        // Fano factor measures the sweep: at the moment the probe passes a
        // spot the local window mixes two levels, its variance explodes, and
        // deflating the counts by that φ would erase exactly the change this
        // mode exists to find. Passing the short window keeps the estimate
        // honest instead: it never reaches
        // [RateComparison.MIN_SAMPLES_FOR_FANO] readings, so the dispersion is
        // reported as unknown and no correction is applied at all.
        return RateComparison.compare(current = fast, background = local, stationaryWindow = fast)
    }

    private fun trendOf(comparison: RateComparisonResult?): NavigateTrend = when {
        comparison == null -> NavigateTrend.COLLECTING
        comparison.pValue >= ALPHA -> NavigateTrend.NO_CHANGE
        comparison.excessConfirmedByInterval -> NavigateTrend.RISING
        comparison.deficitConfirmedByInterval -> NavigateTrend.FALLING
        else -> NavigateTrend.NO_CHANGE
    }

    private data class Settled(
        val trend: NavigateTrend,
        val since: Long?,
        val pending: NavigateTrend?,
        val pendingSince: Long?,
    )

    /**
     * Hysteresis. «Набираю статистику» is a fact about the data, so it applies
     * at once; every other change has to wait out a dwell, and leaving a
     * resolved direction waits longer than entering one — at the edge of a
     * source's field the arrow would otherwise flicker exactly where it is
     * being read most closely.
     */
    private fun settle(
        state: NavigateState,
        raw: NavigateTrend,
        nowMillis: Long,
    ): Settled {
        if (raw == state.trend) {
            return Settled(state.trend, state.trendSinceMillis ?: nowMillis, null, null)
        }
        if (raw == NavigateTrend.COLLECTING || state.trend == NavigateTrend.COLLECTING) {
            return Settled(raw, nowMillis, null, null)
        }
        val dwell = if (raw == NavigateTrend.NO_CHANGE) RELEASE_MILLIS else ENTER_MILLIS
        val pendingSince = if (state.pendingTrend == raw) {
            state.pendingSinceMillis ?: nowMillis
        } else {
            nowMillis
        }
        if (nowMillis - pendingSince < dwell) {
            return Settled(state.trend, state.trendSinceMillis, raw, pendingSince)
        }
        return Settled(raw, nowMillis, null, null)
    }

    /**
     * Peak hold over the **short-window** rate, not over single readings: one
     * bright second is counting noise, and a maximum that is mostly noise
     * would send the user back to a spot where nothing is.
     */
    private fun peak(
        previous: NavigatePeak?,
        fast: CountWindow?,
        nowMillis: Long,
    ): NavigatePeak? {
        val rate = fast?.ratePerSecond ?: return previous
        if (!rate.isFinite() || fast.counts < MIN_COUNTS) return previous
        if (previous != null && rate <= previous.ratePerSecond) return previous
        return NavigatePeak(ratePerSecond = rate, atMillis = nowMillis)
    }

    /**
     * The reference test may only run once the short window has moved past the
     * interval the reference was measured over. Until then the two windows
     * share readings, and a test on overlapping windows would be comparing a
     * measurement with part of itself.
     */
    private fun referenceComparison(
        reference: NavigateReference?,
        fast: CountWindow?,
        points: List<SearchPoint>,
        fastFromMillis: Long,
    ): RateComparisonResult? {
        if (reference == null || fast == null) return null
        if (fast.counts < MIN_COUNTS || reference.window.counts < MIN_COUNTS) return null
        val earliest = points.firstOrNull { it.timeMillis > fastFromMillis }?.timeMillis
            ?: return null
        if (earliest <= reference.atMillis) return null
        return RateComparison.compare(
            current = fast,
            background = reference.window,
            stationaryWindow = reference.window,
        )
    }

    private fun ratio(rate: Double?, reference: NavigateReference?): Double? {
        val base = reference?.ratePerSecond ?: return null
        if (rate == null || base <= 0.0 || !rate.isFinite()) return null
        return rate / base
    }

    /**
     * What may honestly be said about the current rate relative to the точка
     * отсчёта. A percentage is returned **only** when the exact interval sits
     * entirely on one side of 1 — see [ReferenceDelta].
     */
    fun referenceDelta(state: NavigateState): ReferenceDelta {
        if (state.reference == null) return ReferenceDelta.NoReference
        val comparison = state.referenceComparison ?: return ReferenceDelta.Collecting
        val low = comparison.ratioLow
        val high = comparison.ratioHigh
        val resolved = comparison.excessConfirmedByInterval ||
            comparison.deficitConfirmedByInterval
        if (!resolved) return ReferenceDelta.Unresolved(low, high)
        val base = comparison.background.ratePerSecond
        if (base <= 0.0) return ReferenceDelta.Unresolved(low, high)
        val percent = (((comparison.current.ratePerSecond - base) / base) * 100.0).roundToInt()
        return ReferenceDelta.Resolved(
            percent = percent,
            ratio = comparison.ratio,
            low = low,
            high = high,
        )
    }
}
