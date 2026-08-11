package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
import app.radiacode.analysis.RateComparison
import app.radiacode.analysis.RateComparisonResult

/**
 * Everything Поиск knows at one instant, as one immutable value.
 *
 * The screen owns none of this logic: it appends readings and draws the result,
 * so the whole chain «reading → decision window → exact test → state ladder →
 * picture» is a pure function that JVM tests can walk second by second. That is
 * also what makes the release-gate requirement of the redesign (§14, «изменение
 * частоты перерисовки UI не должно менять статистический вывод») checkable at
 * all: the verdict depends on the readings and their instants, never on when
 * Compose happened to recompose.
 */
data class SearchState(
    /** The drawn tape, oldest first, at most [SearchEngine.TAPE_MILLIS] long. */
    val points: List<SearchPoint> = emptyList(),
    /** The decision window against the background; null = nothing to compare. */
    val comparison: RateComparisonResult? = null,
    val ladder: SearchLadderState = SearchLadderState(),
    val direction: SearchDirection = SearchDirection.UNKNOWN,
    /** Sticky Y axis of the chart; null until the first reading. */
    val scale: RateScaleState? = null,
) {
    val level: SearchLevel get() = ladder.level
    val latest: SearchPoint? get() = points.lastOrNull()
}

/**
 * The Поиск decision loop (search redesign §2, §3, §8, §11).
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** Every reading is appended to a 60 s tape. The **decision
 *    window** is the readings of the last [DECISION_WINDOW_MILLIS], rebuilt into
 *    a [CountWindow] (counts = Σ rᵢ·Δtᵢ, exposure = Σ Δtᵢ) and compared with the
 *    recorded background by [RateComparison.compare]; the verdict is the state
 *    ladder of [SearchLadder] over that sequence of comparisons. Direction comes
 *    from [SearchDirectionFit] over a longer 10 s window. Nothing here smooths,
 *    interpolates or re-derives the background.
 * 2. **Assumptions.** Readings arrive in time order, roughly once a second,
 *    stamped with the instrument's own instant. Successive decision windows
 *    **overlap**, so the tests are correlated — the confirmation time of the
 *    ladder is therefore a dwell requirement, not an independence multiplier
 *    (the honest wording of that is in `docs/analysis/search-statistics.md`).
 * 3. **Units.** Rates s⁻¹, times milliseconds, exposures seconds.
 * 4. **Reference.** [RateComparison] (Przyborowski–Wilenski + Clopper–Pearson),
 *    [SearchLadder] (magnitude AND duration, as in ADR 002).
 * 5. **Validation data.** `SearchEngineTest`: a stationary stream never
 *    confirms, a step confirms within a bounded time, a dropped stream drops the
 *    verdict, and feeding the same readings at different tick rates yields the
 *    same verdict (redesign §14). Field validation is steps 1–10 of the protocol
 *    in `docs/analysis/search-statistics.md`.
 * 6. **Limitations.** [DECISION_WINDOW_MILLIS] is an **engineering parameter**:
 *    shorter windows react faster and detect less, longer ones the reverse. It
 *    is not derived from the physics and is not user-tunable in the normal mode
 *    on purpose (§9). While the instrument is being carried, the current window
 *    is not stationary — one window is never a verdict on its own.
 * 7. **Tests.** `app/src/test/.../ui/logic/SearchEngineTest.kt`.
 * 8. **Algorithm version.** [SearchLadder.ALGORITHM_VERSION] together with
 *    [RateComparison.ALGORITHM_VERSION] — this file adds no mathematics of its
 *    own beyond windowing.
 * 9. **User-facing meaning.** Everything the screen says about «фон» is about
 *    **the last few seconds of counting compared with a recorded reference** —
 *    not about dose, not about a nuclide, not about safety.
 */
object SearchEngine {

    /** How much of the recent past the chart keeps and draws. */
    const val TAPE_MILLIS = 60_000L

    /**
     * Length of the window the statistical decision is made on.
     *
     * **Engineering parameter.** Three seconds is the shortest window that still
     * carries enough counts for the exact test to see an ordinary hand-held
     * find: at a 25 s⁻¹ background it holds ~75 counts, so a 60 % excess already
     * lands beyond p = 0.01, while a one-second window would need the rate to
     * roughly double before it could say anything at all. Longer would dilute
     * the moment the probe passes over a spot.
     */
    const val DECISION_WINDOW_MILLIS = 3_000L

    /** Readings older than this leave the tape entirely. */
    private const val KEEP_MILLIS = TAPE_MILLIS

    /**
     * Appends a reading taken at [timeMillis] (instrument time base) and
     * re-evaluates everything.
     */
    fun onReading(
        state: SearchState,
        timeMillis: Long,
        cps: Float,
        background: BackgroundRecord?,
        nowMillis: Long = timeMillis,
    ): SearchState {
        val appended = (state.points + SearchPoint(timeMillis, cps))
            .filter { timeMillis - it.timeMillis <= KEEP_MILLIS }
        return evaluate(state, appended, background, nowMillis)
    }

    /**
     * Re-evaluates without a new reading — the path that makes a lost stream
     * visible: the decision window empties out on its own and the ladder falls
     * back to [SearchLevel.UNKNOWN] instead of freezing the last verdict.
     */
    fun onTick(
        state: SearchState,
        background: BackgroundRecord?,
        nowMillis: Long,
    ): SearchState = evaluate(state, state.points, background, nowMillis)

    private fun evaluate(
        state: SearchState,
        points: List<SearchPoint>,
        background: BackgroundRecord?,
        nowMillis: Long,
    ): SearchState {
        val current = decisionWindow(points, nowMillis)
        val comparison = if (current != null && background != null) {
            RateComparison.compare(current = current, background = background.window)
        } else {
            null
        }
        val direction = direction(points, nowMillis)
        val ladder = SearchLadder.step(
            state = state.ladder,
            input = LadderInput(
                nowMillis = nowMillis,
                comparison = comparison,
                streamFresh = current != null,
                direction = direction,
            ),
        )
        val flagged = flag(points, ladder)
        val bandTop = background?.let { backgroundBand(it).endInclusive }
        val scale = RateAutoScale.next(
            state = state.scale,
            nowMillis = nowMillis,
            required = RateChartModel.requiredTop(flagged, bandTop),
            excursionConfirmed = ladder.confirmed,
        )
        return SearchState(
            points = flagged,
            comparison = comparison,
            ladder = ladder,
            direction = direction,
            scale = scale,
        )
    }

    /**
     * The readings of the last [DECISION_WINDOW_MILLIS] as a counting window,
     * or null when the stream has not delivered inside it — an empty window is
     * not a quiet one, and the difference must reach the user as «нет данных».
     */
    fun decisionWindow(points: List<SearchPoint>, nowMillis: Long): CountWindow? {
        val from = nowMillis - DECISION_WINDOW_MILLIS
        val window = points.filter { it.timeMillis > from }
        if (window.isEmpty()) return null
        val times = LongArray(window.size) { window[it].timeMillis }
        val rates = DoubleArray(window.size) { window[it].cps.toDouble() }
        return CountWindow.reconstruct(times, rates).takeIf { it.usable }
    }

    private fun direction(points: List<SearchPoint>, nowMillis: Long): SearchDirection {
        val from = nowMillis - SearchDirectionFit.WINDOW_MILLIS
        val window = points.filter { it.timeMillis > from }
        if (window.isEmpty()) return SearchDirection.UNKNOWN
        val times = LongArray(window.size) { window[it].timeMillis }
        val rates = FloatArray(window.size) { window[it].cps }
        return SearchDirectionFit.of(times, rates)
    }

    /**
     * Marks the readings that belong to a **confirmed** excursion.
     *
     * The whole run is marked, back to the instant the difference started —
     * not only from the moment the ladder agreed. The confirmation time is how
     * long the evidence had to hold, and once it has held, the excursion on the
     * chart is the run that was confirmed; marking only its tail would draw a
     * different event than the one the sentence above the chart describes.
     * Nothing is marked retroactively for a run that never confirmed: those
     * stay ordinary readings and live on as [SpikeMarker]s instead.
     *
     * Marks are **monotone** — once a stretch of the tape has been confirmed it
     * keeps its mark until it scrolls off. Releasing the verdict means «not any
     * more», not «it never happened», and a chart that erased the excursion the
     * moment the user stepped back would make the найденное место unfindable.
     */
    private fun flag(points: List<SearchPoint>, ladder: SearchLadderState): List<SearchPoint> {
        val since = ladder.differentSinceMillis
        if (!ladder.confirmed || since == null) return points
        return points.map {
            if (!it.confirmed && it.timeMillis >= since) it.copy(confirmed = true) else it
        }
    }
}
