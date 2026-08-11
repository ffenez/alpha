package app.radiacode.ui.logic

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * One drawn reading of the Поиск time series.
 *
 * [confirmed] is the verdict of [SearchLadder] at the moment the reading was
 * taken, carried alongside the value so the chart can mark the excursion
 * without re-deciding anything: the picture and the sentence above it are then
 * the same statement by construction.
 */
data class SearchPoint(
    val timeMillis: Long,
    val cps: Float,
    val confirmed: Boolean = false,
)

/**
 * Y-axis state of the search chart. It is *state*, not a pure function of the
 * data, because the whole point is that it changes rarely — see [RateAutoScale].
 */
data class RateScaleState(
    val top: Float,
    /** When the data first fell into the shrink zone; null = not pending. */
    val shrinkPendingSinceMillis: Long? = null,
)

/**
 * Y autoscale for the search chart, with hysteresis.
 *
 * **Why this is not the usual «fit the data» rule.** While the user walks
 * towards a source the count rate grows; an axis that follows it continuously
 * keeps the line at the same height on screen and flattens the very growth the
 * user is looking for. A search chart that rescales smoothly is a chart that
 * hides the signal.
 *
 * So the scale is deliberately sticky:
 *
 *  - it **grows immediately** when the data would leave the frame — a clipped
 *    line is a lie, and the growth step is a visible discrete jump on the
 *    1/2/5·10ᵏ ladder rather than a slow drift, so the user can see that the
 *    frame changed;
 *  - between [SHRINK_FRACTION]·top and top it does **nothing at all** — that
 *    band is the documented dead zone;
 *  - it shrinks only after the data has stayed in the bottom part of the frame
 *    for [SHRINK_HOLD_MILLIS] continuously;
 *  - it **never rescales while an excursion is confirmed**, in either
 *    direction: the moment the answer matters is the moment the frame must
 *    stop moving.
 *
 * All four constants are **engineering parameters** — they are about reading a
 * chart while walking, not about the physics.
 */
object RateAutoScale {

    /** Headroom above the highest thing that must fit. */
    const val HEADROOM = 1.20f

    /** Below this fraction of the current top the scale becomes shrinkable. */
    const val SHRINK_FRACTION = 0.45f

    /** How long the data must stay in the shrink zone before the axis moves. */
    const val SHRINK_HOLD_MILLIS = 8_000L

    /** Never scale below this, so a near-zero background is not magnified. */
    const val MIN_TOP = 5f

    /** Smallest 1/2/5·10ᵏ value at or above [value]. */
    fun niceTop(value: Float): Float {
        if (!value.isFinite() || value <= 0f) return MIN_TOP
        val magnitude = 10.0.pow(floor(log10(value.toDouble())))
        val normalized = value / magnitude
        val nice = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 5.0 -> 5.0
            else -> 10.0
        }
        return (nice * magnitude).toFloat()
    }

    /**
     * Next axis state.
     *
     * [required] is the highest value that must stay visible — the data maximum
     * of the window together with the top of the fluctuation band, so the
     * background and its band never leave the frame (redesign §2).
     */
    fun next(
        state: RateScaleState?,
        nowMillis: Long,
        required: Float,
        excursionConfirmed: Boolean,
    ): RateScaleState {
        val wanted = niceTop((required * HEADROOM).coerceAtLeast(MIN_TOP))
        if (state == null) return RateScaleState(top = wanted)

        if (required > state.top) {
            // Clipping is never acceptable, not even mid-excursion.
            return RateScaleState(top = maxOf(wanted, niceTop(required)))
        }
        if (excursionConfirmed) return state.copy(shrinkPendingSinceMillis = null)
        if (required >= state.top * SHRINK_FRACTION) {
            return state.copy(shrinkPendingSinceMillis = null)
        }
        val pendingSince = state.shrinkPendingSinceMillis ?: nowMillis
        if (nowMillis - pendingSince < SHRINK_HOLD_MILLIS) {
            return state.copy(shrinkPendingSinceMillis = pendingSince)
        }
        return RateScaleState(top = wanted)
    }
}

/** Geometry helpers of the search chart; pure, so the drawing has no logic. */
object RateChartModel {

    /**
     * A hole this long breaks the line. One device record is ~1 s, so two
     * missed records in a row is already a gap the user must see (§12: BLE
     * dropouts are never interpolated across).
     */
    const val GAP_MILLIS = 2_500L

    /** True when nothing may be drawn between these two readings. */
    fun isGap(previousMillis: Long, nextMillis: Long): Boolean =
        nextMillis - previousMillis > GAP_MILLIS

    /**
     * Maximal runs of confirmed readings, as index ranges into [points].
     * A run is what the chart marks; ordinary statistical noise is left alone
     * (redesign §2: never repaint fluctuation as danger).
     */
    fun confirmedSpans(points: List<SearchPoint>): List<IntRange> {
        val spans = ArrayList<IntRange>()
        var start = -1
        for (i in points.indices) {
            if (points[i].confirmed) {
                if (start < 0) start = i
            } else if (start >= 0) {
                spans += start..(i - 1)
                start = -1
            }
        }
        if (start >= 0) spans += start..(points.size - 1)
        return spans
    }

    /** The highest value that must stay inside the frame. */
    fun requiredTop(points: List<SearchPoint>, bandTop: Float?): Float {
        val dataMax = points.maxOfOrNull { it.cps } ?: 0f
        return maxOf(dataMax, bandTop ?: 0f)
    }
}
