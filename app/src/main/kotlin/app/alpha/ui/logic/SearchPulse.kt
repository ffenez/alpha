package app.alpha.ui.logic

import kotlin.math.ln
import kotlin.math.pow

/**
 * Дыхание индикатора «Наведения»: период пульса как отношение к точке отсчёта.
 *
 * Это третий канал того же показания — рядом со звуком и вибрацией. Отсюда и
 * границы: ниже [SearchTone.MIN_RATIO] тон молчит, потому что там фон, и
 * дыхание в этой зоне остаётся спокойным; выше — период сокращается по той же
 * логарифмической шкале, что и высота тона, и насыщается на
 * [SearchTone.MAX_RATIO]. Глаз и ухо обязаны говорить одно и то же.
 *
 * Само измерение при этом не анимируется: пульсирует подсветка индикатора, а
 * маркер значения переставляется шагом.
 */
object SearchPulse {

    /** Спокойное дыхание: прибор жив, счёт у точки отсчёта, мс. */
    const val CALM_PERIOD_MILLIS = 2_600

    /**
     * Самый частый пульс, мс.
     *
     * **Инженерный параметр**: 450 мс — примерно два удара в секунду. Быстрее
     * подсветка перестаёт читаться как ритм и превращается в мигание, которое
     * на периферии зрения неотличимо от тревоги.
     */
    const val FAST_PERIOD_MILLIS = 450

    /** Период дыхания для отношения; null или фон — спокойный период. */
    fun periodMillis(ratio: Double?): Int {
        if (ratio == null || !ratio.isFinite() || ratio <= SearchTone.MIN_RATIO) {
            return CALM_PERIOD_MILLIS
        }
        val clamped = ratio.coerceAtMost(SearchTone.MAX_RATIO)
        val position = ln(clamped / SearchTone.MIN_RATIO) /
            ln(SearchTone.MAX_RATIO / SearchTone.MIN_RATIO)
        val factor = FAST_PERIOD_MILLIS.toDouble() / CALM_PERIOD_MILLIS
        return (CALM_PERIOD_MILLIS * factor.pow(position)).toInt()
            .coerceIn(FAST_PERIOD_MILLIS, CALM_PERIOD_MILLIS)
    }
}
