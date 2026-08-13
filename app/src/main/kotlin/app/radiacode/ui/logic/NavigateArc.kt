package app.radiacode.ui.logic

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Frame of the 20-second trace under the arc.
 *
 * Deliberately **not** zero-based: over twenty seconds an ordinary background
 * varies by a few per cent, and an axis that starts at zero would draw that as
 * a flat line — the one thing this picture exists to show. The bounds follow
 * the data with a margin instead, and never collapse below [MIN_SPAN_FRACTION]
 * of the level, so a perfectly steady stretch does not get magnified into
 * dramatic noise.
 */
object NavigateTraceScale {

    /** Margin above and below the data, as a fraction of its span. */
    const val MARGIN = 0.18f

    /** Smallest frame height, as a fraction of the level being drawn. */
    const val MIN_SPAN_FRACTION = 0.12f

    /** Bottom…top of the value axis for [values] and an optional level line. */
    fun of(values: List<Float>, level: Float?): ClosedFloatingPointRange<Float> {
        val all = values.filter { it.isFinite() } + listOfNotNull(level?.takeIf { it.isFinite() })
        if (all.isEmpty()) return 0f..1f
        val low = all.min()
        val high = all.max()
        val centre = (low + high) / 2f
        val minSpan = (centre * MIN_SPAN_FRACTION).coerceAtLeast(0.5f)
        val span = (high - low).coerceAtLeast(minSpan)
        val margin = span * MARGIN
        return (low - margin).coerceAtLeast(0f)..(high + margin)
    }
}

/** Frame of the arc: half-span of the logarithmic scale, plus its hysteresis. */
data class NavigateScaleState(
    /** Ends of the scale are ×[factor] and ×1/[factor] around the reference. */
    val factor: Double,
    /** When the data first fell inside a smaller frame; null = not pending. */
    val shrinkPendingSinceMillis: Long? = null,
)

/**
 * The 60° arc of «Наведение»: geometry only, so the drawing decides nothing.
 *
 * ## Why an arc and not a sweeping radar beam
 *
 * A rotating beam would promise a **direction in space**, and a dosimeter does
 * not measure one — it measures how many events arrived, not where they came
 * from. So this is an instrument dial: a needle stands at a position on a named
 * scale, and nothing on it moves by itself.
 *
 * ## Why logarithmic in the ratio
 *
 * The scale is logarithmic **in the ratio to the точка отсчёта**, with the same
 * mapping the search tone already uses ([SearchTone]: equal factors of R are
 * equal musical intervals). Eye and ear then say the same thing: a doubling is
 * the same distance on the dial as it is an octave in the audio.
 *
 * There are no green/amber/red zones. Those would make it a danger scale, and
 * the count rate is not a dose; the only accented thing on the arc is the
 * direction of change.
 *
 * All constants are **engineering parameters** — this is a presentation of a
 * measured ratio, not a measurement of its own.
 */
object NavigateArc {

    /** Compose sweep angles: 0° is 3 o'clock, positive clockwise. */
    const val START_DEGREES = 240f
    const val SWEEP_DEGREES = 60f

    /** Half-spans the frame may take, in factors of the reference. */
    val LADDER = listOf(2.0, 4.0, 8.0, 16.0, 32.0)

    /** Headroom before the frame has to grow. */
    const val HEADROOM = 1.15

    /** How long the data must stay inside a smaller frame before it shrinks. */
    const val SHRINK_HOLD_MILLIS = 6_000L

    /** Position of a ratio on the arc, 0 (left end) … 1 (right end). */
    fun position(ratio: Double, factor: Double): Float {
        if (!ratio.isFinite() || ratio <= 0.0 || factor <= 1.0) return 0.5f
        val half = ln(factor)
        return (0.5 + ln(ratio) / (2.0 * half)).coerceIn(0.0, 1.0).toFloat()
    }

    /** The same position as a Compose sweep angle in degrees. */
    fun angleDegrees(ratio: Double, factor: Double): Float =
        START_DEGREES + SWEEP_DEGREES * position(ratio, factor)

    /** True when the value is off the scale and the needle sits on the end. */
    fun offScale(ratio: Double, factor: Double): Boolean {
        if (!ratio.isFinite() || ratio <= 0.0 || factor <= 1.0) return false
        return ratio > factor || ratio < 1.0 / factor
    }

    /**
     * Tick ratios: the reference itself and every doubling out to the ends.
     *
     * Powers of two rather than «nice» decimal steps because the scale is
     * logarithmic and the point of it is that equal factors are equal
     * distances — the ticks have to be a geometric series or they would lie
     * about the spacing.
     */
    fun ticks(factor: Double): List<Double> {
        if (factor <= 1.0) return listOf(1.0)
        val steps = (ln(factor) / ln(2.0)).roundToInt().coerceAtLeast(1)
        val out = ArrayList<Double>(2 * steps + 1)
        for (i in -steps..steps) out += 2.0.pow(i)
        return out
    }

    /** Smallest frame on [LADDER] that still holds every one of [ratios]. */
    fun requiredFactor(ratios: List<Double>): Double {
        var needed = 1.0
        for (ratio in ratios) {
            if (!ratio.isFinite() || ratio <= 0.0) continue
            val away = if (ratio >= 1.0) ratio else 1.0 / ratio
            if (away > needed) needed = away
        }
        val wanted = needed * HEADROOM
        return LADDER.firstOrNull { it >= wanted } ?: LADDER.last()
    }

    /**
     * Next frame, with the same hysteresis rule the search chart uses: grow
     * immediately (a needle pinned at the end is a lie), shrink only after the
     * data has stayed inside the smaller frame for [SHRINK_HOLD_MILLIS].
     *
     * The **frame** may move smoothly; the needle never does — an animated
     * needle would draw ratios between two measurements that the instrument
     * never measured.
     */
    fun next(
        state: NavigateScaleState?,
        nowMillis: Long,
        requiredFactor: Double,
    ): NavigateScaleState {
        if (state == null) return NavigateScaleState(factor = requiredFactor)
        if (requiredFactor > state.factor) return NavigateScaleState(factor = requiredFactor)
        if (abs(requiredFactor - state.factor) < 1e-9) {
            return state.copy(shrinkPendingSinceMillis = null)
        }
        val pendingSince = state.shrinkPendingSinceMillis ?: nowMillis
        if (nowMillis - pendingSince < SHRINK_HOLD_MILLIS) {
            return state.copy(shrinkPendingSinceMillis = pendingSince)
        }
        return NavigateScaleState(factor = requiredFactor)
    }
}
