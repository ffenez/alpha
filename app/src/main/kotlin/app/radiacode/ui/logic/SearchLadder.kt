package app.radiacode.ui.logic

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.analysis.RateComparisonResult
import kotlin.math.sqrt

/**
 * How far the current window has moved away from the recorded background —
 * as a **ladder**, not as a threshold (search redesign §11).
 *
 * The order is fixed and every step up costs both magnitude and time:
 * [UNKNOWN] → [BACKGROUND] → [POSSIBLE_CHANGE] → [CONFIRMED_EXCESS].
 * [CONFIRMED_DEFICIT] is the same rung on the other side.
 */
enum class SearchLevel {
    /** No background, no data, or a stale stream — nothing can be said. */
    UNKNOWN,

    /** The window is statistically indistinguishable from the background. */
    BACKGROUND,

    /** A difference is showing, but it has not held long enough to be called. */
    POSSIBLE_CHANGE,

    /** A difference above the background that held for the confirmation time. */
    CONFIRMED_EXCESS,

    /** The same, below the background (walking away, shielding, geometry). */
    CONFIRMED_DEFICIT,
}

/**
 * Which way the count rate is moving right now — the hot-and-cold half of
 * localisation. Deliberately separate from [SearchLevel]: a rate can be rising
 * while still inside the background, and it can be confirmed high and falling.
 */
enum class SearchDirection {
    /** Not enough of a window, or the slope is inside the noise. */
    UNKNOWN,
    STEADY,
    RISING,
    FALLING,
}

/**
 * A short excursion that appeared and vanished before the confirmation time.
 *
 * It is kept as a **marker**, never announced as a find (redesign §11, §12):
 * one bright second is a fluctuation until proven otherwise, and the honest
 * thing to do with it is to remember where it was so the user can sweep again.
 */
data class SpikeMarker(
    val fromMillis: Long,
    val toMillis: Long,
    /** Highest ratio to background seen inside the excursion. */
    val peakRatio: Double,
)

/** Everything one step of the ladder produced. */
data class SearchLadderState(
    val level: SearchLevel = SearchLevel.UNKNOWN,
    val direction: SearchDirection = SearchDirection.UNKNOWN,
    /** Start of the current uninterrupted run of «windows differ», or null. */
    val differentSinceMillis: Long? = null,
    /** Start of the current uninterrupted run of «windows agree», or null. */
    val sameSinceMillis: Long? = null,
    /** When the level first became one of the confirmed rungs, or null. */
    val confirmedSinceMillis: Long? = null,
    /** Peak ratio of the run in progress; 0 when no run is in progress. */
    val runPeakRatio: Double = 0.0,
    /** Newest last, capped at [SearchLadder.MAX_SPIKES]. */
    val spikes: List<SpikeMarker> = emptyList(),
) {
    val confirmed: Boolean
        get() = level == SearchLevel.CONFIRMED_EXCESS || level == SearchLevel.CONFIRMED_DEFICIT

    /** Milliseconds the confirmed state has held, or null when not confirmed. */
    fun confirmedForMillis(nowMillis: Long): Long? =
        confirmedSinceMillis?.let { nowMillis - it }
}

/** One evaluation of the ladder. */
data class LadderInput(
    val nowMillis: Long,
    /** Result of [app.radiacode.analysis.RateComparison.compare]; null = none. */
    val comparison: RateComparisonResult?,
    /** The 1 Hz stream is delivering. A stale stream freezes nothing — it
     *  drops the verdict to [SearchLevel.UNKNOWN], because the newest window
     *  is no longer about now. */
    val streamFresh: Boolean,
    val direction: SearchDirection = SearchDirection.UNKNOWN,
)

/**
 * The state ladder of Поиск: turns a stream of two-sample comparisons into the
 * one sentence the screen is allowed to say (search redesign §11).
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** A pure state machine over the exact test of
 *    [app.radiacode.analysis.RateComparison]. A window *differs* when the
 *    two-sided conditional-binomial p is below [ALPHA] **and** the whole
 *    confidence interval for the rate ratio sits on one side of 1. A run of
 *    differing windows becomes [SearchLevel.CONFIRMED_EXCESS] only after it has
 *    held for [CONFIRM_MILLIS]; a confirmed state is released only after
 *    [RELEASE_MILLIS] of agreement. A run that ends before the confirmation
 *    time becomes a [SpikeMarker].
 * 2. **Assumptions.** The comparisons arrive in time order and roughly once a
 *    second; the machine never looks at future data and holds no percentage
 *    threshold of its own — every magnitude decision comes from the statistics
 *    module (redesign §11: «порог перехода определяется статистической
 *    моделью, а не произвольным процентом»).
 * 3. **Units.** Times are milliseconds, [ALPHA] is a probability, the ratio is
 *    dimensionless.
 * 4. **Reference.** The two-sided test is Przyborowski–Wilenski (see
 *    [app.radiacode.analysis.RateComparison]); the confirmation-time idea is
 *    the same «magnitude AND duration» rule the profile baseline already uses
 *    ([app.radiacode.baseline.PersistenceTracker], ADR 002).
 * 5. **Validation data.** Deterministic sequences in `SearchLadderTest`: a
 *    single spike never confirms, a sustained excess confirms exactly at the
 *    confirmation time, a dropout does not un-confirm instantly, a stale
 *    stream drops to [SearchLevel.UNKNOWN]. Field validation on an RC-110 is
 *    steps 3–6 of the protocol in `docs/analysis/search-statistics.md`.
 * 6. **Limitations.** [ALPHA], [CONFIRM_MILLIS] and [RELEASE_MILLIS] are
 *    **engineering parameters**, chosen for a hand-held sweep, not derived from
 *    the physics. The machine describes the count rate and nothing else: a
 *    confirmed excess is not a source, not a nuclide and not a dose.
 * 7. **Tests.** `app/src/test/.../ui/logic/SearchLadderTest.kt`.
 * 8. **Algorithm version.** [AlgorithmVersions.SEARCH_LADDER].
 * 9. **User-facing meaning.** «Устойчивое превышение фонового счёта» = the
 *    count rate has been statistically above the recorded background for at
 *    least [CONFIRM_MILLIS] — a statement about counting, made against a named
 *    reference.
 */
object SearchLadder {

    const val ALGORITHM_VERSION = AlgorithmVersions.SEARCH_LADDER

    /**
     * Significance a single window must reach before it counts as differing.
     *
     * **Engineering parameter.** The screen re-tests about once a second, so an
     * α of 0.01 alone would raise ~36 flags an hour on a perfectly stationary
     * background. That is not fixed by shrinking α (which would blind the mode
     * to real, brief excursions) but by [CONFIRM_MILLIS]: a false flag has to
     * repeat for seconds in a row, and the probability of that under H₀ falls
     * off as roughly αⁿ. α is therefore kept where a single window is already
     * unlikely, and duration does the rest.
     */
    const val ALPHA = 0.01

    /**
     * How long a difference must hold before the screen calls it «устойчивое».
     *
     * **Engineering parameter.** Four seconds is the shortest interval that
     * both (a) contains at least three independent one-second windows, so a
     * chance run is already improbable, and (b) is short enough that a person
     * sweeping a surface at hand speed does not walk past the spot before the
     * screen agrees with them.
     */
    const val CONFIRM_MILLIS = 4_000L

    /**
     * How long agreement must hold before a confirmed state is released.
     *
     * **Engineering parameter**, asymmetric on purpose: dropping the verdict on
     * the first quiet second would make the screen flicker exactly where it
     * matters most, at the edge of a source's field.
     */
    const val RELEASE_MILLIS = 3_000L

    /** Kept markers; the oldest fall off. Enough for one sweep of a room. */
    const val MAX_SPIKES = 12

    /**
     * A run shorter than [CONFIRM_MILLIS] is remembered as a marker only if it
     * actually stood out; otherwise every borderline second would leave a pin.
     * **Engineering parameter.**
     */
    const val SPIKE_MIN_RATIO = 1.3

    fun step(state: SearchLadderState, input: LadderInput): SearchLadderState {
        val comparison = input.comparison
        if (!input.streamFresh || comparison == null) {
            // Nothing to compare: keep the markers, drop the verdict. Freezing
            // the old level would let a lost connection look like a finding.
            return SearchLadderState(
                level = SearchLevel.UNKNOWN,
                direction = SearchDirection.UNKNOWN,
                spikes = state.spikes,
            )
        }

        val excess = comparison.excessConfirmedByInterval
        val deficit = comparison.deficitConfirmedByInterval
        val differs = comparison.pValue < ALPHA && (excess || deficit)
        val now = input.nowMillis

        if (differs) {
            val since = state.differentSinceMillis ?: now
            val peak = maxOf(state.runPeakRatio, comparison.ratio.takeIf { it.isFinite() } ?: 0.0)
            val held = now - since
            val level = when {
                held < CONFIRM_MILLIS -> SearchLevel.POSSIBLE_CHANGE
                excess -> SearchLevel.CONFIRMED_EXCESS
                else -> SearchLevel.CONFIRMED_DEFICIT
            }
            val confirmedSince = when {
                level == SearchLevel.POSSIBLE_CHANGE -> null
                state.confirmedSinceMillis != null -> state.confirmedSinceMillis
                else -> now
            }
            return state.copy(
                level = level,
                direction = input.direction,
                differentSinceMillis = since,
                sameSinceMillis = null,
                confirmedSinceMillis = confirmedSince,
                runPeakRatio = peak,
            )
        }

        // The windows agree now.
        val sameSince = state.sameSinceMillis ?: now
        if (state.confirmed) {
            // Hysteresis: a confirmed state survives short quiet stretches.
            if (now - sameSince < RELEASE_MILLIS) {
                return state.copy(direction = input.direction, sameSinceMillis = sameSince)
            }
            return state.copy(
                level = SearchLevel.BACKGROUND,
                direction = input.direction,
                differentSinceMillis = null,
                sameSinceMillis = sameSince,
                confirmedSinceMillis = null,
                runPeakRatio = 0.0,
            )
        }

        // An unconfirmed run just ended: remember it as a marker, never as a find.
        val startedAt = state.differentSinceMillis
        val spikes = if (startedAt != null && state.runPeakRatio >= SPIKE_MIN_RATIO) {
            (state.spikes + SpikeMarker(startedAt, now, state.runPeakRatio)).takeLast(MAX_SPIKES)
        } else {
            state.spikes
        }
        return state.copy(
            level = SearchLevel.BACKGROUND,
            direction = input.direction,
            differentSinceMillis = null,
            sameSinceMillis = sameSince,
            confirmedSinceMillis = null,
            runPeakRatio = 0.0,
            spikes = spikes,
        )
    }
}

/**
 * «Теплее / холоднее»: a robust short-window slope of the count rate.
 *
 * Localisation is a hot-and-cold game, and while walking this is the single
 * most useful signal on the screen — more useful than the absolute number,
 * because the absolute number depends on distance, geometry and shielding all
 * at once, while its *sign in time* only depends on whether the last step was
 * towards or away.
 *
 * The estimator is [TrendFit.theilSenPerSecond] (median of pairwise slopes), so
 * one outlying second cannot flip the arrow. The availability rule is local:
 * a ~10 s window, at least [MIN_POINTS] readings, and a slope large enough that
 * the modelled change over the window exceeds [SIGMA_MULTIPLE] times the
 * counting σ of the window mean — below that the arrow would be tracking noise.
 *
 * **All three constants are engineering parameters**, chosen for a hand-held
 * sweep; none of them is a statistical test and the arrow carries no p-value.
 */
object SearchDirectionFit {

    /** How far back the direction looks. Shorter than any decision window. */
    const val WINDOW_MILLIS = 10_000L

    /** Below this many readings a median of pairwise slopes is not robust. */
    const val MIN_POINTS = 6

    /** Modelled change over the window, in σ of the window mean, to call it. */
    const val SIGMA_MULTIPLE = 1.5

    /**
     * Direction over the newest [WINDOW_MILLIS] of [timesMillis]/[rates]
     * (ascending in time, rates in s⁻¹).
     */
    fun of(timesMillis: LongArray, rates: FloatArray): SearchDirection {
        require(timesMillis.size == rates.size) { "times and rates differ in length" }
        if (rates.isEmpty()) return SearchDirection.UNKNOWN
        val last = timesMillis.last()
        var from = timesMillis.size
        for (i in timesMillis.indices.reversed()) {
            if (last - timesMillis[i] > WINDOW_MILLIS) break
            from = i
        }
        val count = timesMillis.size - from
        if (count < MIN_POINTS) return SearchDirection.UNKNOWN

        val t = timesMillis.copyOfRange(from, timesMillis.size)
        val r = rates.copyOfRange(from, rates.size)
        val spanSeconds = (t.last() - t.first()) / 1000.0
        if (spanSeconds <= 0.0) return SearchDirection.UNKNOWN

        val slope = TrendFit.theilSenPerSecond(t, r) ?: return SearchDirection.UNKNOWN
        val mean = r.fold(0.0) { acc, v -> acc + v } / r.size
        if (mean <= 0.0) return SearchDirection.UNKNOWN

        // σ of a rate averaged over the window: √(r̄/T) (Poisson, spec §5).
        val sigma = sqrt(mean / spanSeconds)
        val modelledChange = slope * spanSeconds
        return when {
            modelledChange >= SIGMA_MULTIPLE * sigma -> SearchDirection.RISING
            modelledChange <= -SIGMA_MULTIPLE * sigma -> SearchDirection.FALLING
            else -> SearchDirection.STEADY
        }
    }
}
