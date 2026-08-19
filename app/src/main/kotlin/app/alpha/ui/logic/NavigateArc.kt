package app.alpha.ui.logic

import app.alpha.ui.text.uiDecimal
import java.util.Locale
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

    /**
     * Compose sweep angles: 0° is 3 o'clock, positive clockwise.
     *
     * **Инженерный параметр**: 220° от 160° — почти полный круг с разрывом
     * снизу, то есть циферблат прибора. Прежние 60° укладывали всю лестницу
     * отношений в узкий сектор: разница между ×1 и ×4 занимала полтора
     * сантиметра, стрелка почти не отклонялась, и картинка не работала как
     * прибор, ради которого она нарисована.
     */
    const val START_DEGREES = 160f
    const val SWEEP_DEGREES = 220f

    /**
     * Half-spans the frame may take, in factors of the reference.
     *
     * Начинается с ×4, а не с ×2: базовый кадр прибора — ×0,25…×4 (макет
     * `docs/design/main-and-search.html`), и деления на нём 0,25× / 0,5× / 1× /
     * 2× / 4×. На этом кадре положение считается ровно так, как в макете:
     * `position = (log₂ ratio + 2) / 4`. Более широкие ступени остаются, потому
     * что стрелка, упёртая в конец шкалы, врёт о величине превышения.
     */
    val LADDER = listOf(4.0, 8.0, 16.0, 32.0)

    /** Headroom before the frame has to grow. */
    const val HEADROOM = 1.15

    /** How long the data must stay inside a smaller frame before it shrinks. */
    const val SHRINK_HOLD_MILLIS = 6_000L

    /** Position of a ratio on the arc, 0 (left end) … 1 (right end). */
    fun position(ratio: Double, factor: Double): Float =
        position(ratio, ArcScale.around(factor))

    /** The same position as a Compose sweep angle in degrees. */
    fun angleDegrees(ratio: Double, factor: Double): Float =
        START_DEGREES + SWEEP_DEGREES * position(ratio, factor)

    /** Положение на ПРОИЗВОЛЬНОЙ шкале прибора, 0…1. */
    fun position(ratio: Double, scale: ArcScale): Float {
        if (!ratio.isFinite() || ratio <= 0.0) return scale.position(1.0)
        return scale.position(ratio)
    }

    /** Угол на произвольной шкале — то же положение в градусах Compose. */
    fun angleDegrees(ratio: Double, scale: ArcScale): Float =
        START_DEGREES + SWEEP_DEGREES * position(ratio, scale)

    /** True when the value is off the scale and the needle sits on the end. */
    fun offScale(ratio: Double, factor: Double): Boolean {
        if (!ratio.isFinite() || ratio <= 0.0 || factor <= 1.0) return false
        return ratio > factor || ratio < 1.0 / factor
    }

    /** То же для произвольной шкалы: значение упёрлось в конец. */
    fun offScale(ratio: Double, scale: ArcScale): Boolean {
        if (!ratio.isFinite() || ratio <= 0.0) return false
        return ratio > scale.hi || ratio < scale.lo
    }

    /**
     * Tick ratios: the reference itself and every doubling out to the ends.
     *
     * Powers of two rather than «nice» decimal steps because the scale is
     * logarithmic and the point of it is that equal factors are equal
     * distances — the ticks have to be a geometric series or they would lie
     * about the spacing.
     */
    fun ticks(factor: Double): List<Double> = ticks(ArcScale.around(factor))

    /** Засечки произвольной шкалы: удвоения от нижнего конца до верхнего. */
    fun ticks(scale: ArcScale): List<Double> {
        val low = (ln(scale.lo) / ln(2.0)).roundToInt()
        val high = (ln(scale.hi) / ln(2.0)).roundToInt()
        if (high <= low) return listOf(1.0)
        val out = ArrayList<Double>(high - low + 1)
        for (i in low..high) out += 2.0.pow(i)
        return out
    }

    /**
     * Подпись конца шкалы: «4» для целого множителя, «0,25» для обратного.
     *
     * Живёт рядом с самой шкалой, а не в экране: концы подписывают ОБА места,
     * где эта шкала рисуется — Наведение и Проверка, — и подпись обязана быть
     * там одна и та же.
     */
    fun factorLabel(value: Double): String =
        if (value >= 1.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.2f", value).uiDecimal().trimEnd('0').trimEnd(',')
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

/**
 * Шкала прибора: концы в ОТНОШЕНИЯХ и логарифмическое положение между ними.
 *
 * У прибора два применения, и они различаются только знаменателем ×1 и
 * концами (макет `docs/design/one-instrument.html`):
 *
 *  - [PLACE] — отношение к медиане фона МЕСТА, ×0,5…×8: вниз от обычного
 *    уходить особо некуда, а вверх нужен запас на настоящий рост;
 *  - [MARK] — отношение к точке отсчёта, ×0,25…×4: симметрично, потому что
 *    прибор одинаково часто и приближают к источнику, и уводят от него.
 *
 * Ось логарифмическая в обоих случаях: равные множители обязаны быть равными
 * расстояниями, иначе шкала врёт про интервалы.
 */
data class ArcScale(val lo: Double, val hi: Double) {

    /** Доля шкалы, 0…1; вне концов — прижато к ближнему. */
    fun position(ratio: Double): Float {
        if (!ratio.isFinite() || ratio <= 0.0 || hi <= lo) return 0f
        val span = ln(hi / lo)
        return (ln(ratio / lo) / span).coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        /** Шкала места: ×0,5…×8 вокруг медианы этого места. */
        val PLACE = ArcScale(0.5, 8.0)

        /** Базовая шкала точки отсчёта: ×0,25…×4. */
        val MARK = ArcScale(0.25, 4.0)

        /** Симметричная шкала ×1/factor…×factor — кадр «Наведения». */
        fun around(factor: Double): ArcScale =
            if (factor <= 1.0) ArcScale(0.5, 2.0) else ArcScale(1.0 / factor, factor)
    }
}
