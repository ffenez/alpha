package app.radiacode.ui.chart

import app.radiacode.ui.logic.DoseScale
import app.radiacode.ui.logic.LinearDoseScale
import app.radiacode.ui.logic.LogDoseScale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Переход оси значений из одного кадра в другой.
 *
 * ## Зачем
 *
 * Ось подгоняется под то, что видно (устойчивые границы Q10/Q90 колонок, см.
 * `DoseScales`), и после жеста новый кадр почти всегда требует другого
 * диапазона. Мгновенная подмена читается как рывок всей картинки: линия
 * остаётся на месте по времени, но прыгает по высоте, и глаз теряет, где она
 * только что была. Короткий переход показывает, ЧТО изменилось — сама ось, а
 * не измерения.
 *
 * Анимируется ПРЕДСТАВЛЕНИЕ, а не данные (ТЗ §8): значения колонок не
 * меняются, меняется только отображение «значение → доля высоты». Промежуточных
 * измерений не появляется.
 *
 * ## Когда перехода нет
 *
 * Если диапазон изменился в разы — в кадр вошёл настоящий всплеск, — плавный
 * переход превратился бы в затяжное «уползание» шкалы. Такой масштаб ставится
 * сразу ([FAST_RESCALE_FACTOR]).
 */
object ChartYAxis {

    /**
     * Длительность перехода оси.
     * **Инженерный параметр**: 160 мс — глаз успевает проследить, а рука не
     * успевает заметить задержку.
     */
    const val TRANSITION_MILLIS = 160L

    /** Кадров перехода: 8 при 160 мс — это примерно каждый второй кадр экрана. */
    const val TRANSITION_STEPS = 8

    /**
     * Во сколько раз должен измениться размах, чтобы переход был не нужен.
     * **Инженерный параметр**: втрое. Меньше — это подстройка кадра, и её
     * стоит проследить глазом; больше — в окно вошло другое явление, и
     * растягивать его появление незачем.
     */
    const val FAST_RESCALE_FACTOR = 3f

    /** Стоит ли переходить плавно или сразу поставить новый масштаб. */
    fun animates(from: DoseScale, to: DoseScale): Boolean {
        if (from.logarithmic != to.logarithmic) return false
        val fromSpan = (from.maxValue - from.minValue).takeIf { it > 0f } ?: return false
        val toSpan = (to.maxValue - to.minValue).takeIf { it > 0f } ?: return false
        val ratio = maxOf(fromSpan / toSpan, toSpan / fromSpan)
        if (ratio > FAST_RESCALE_FACTOR) return false
        // Совпавший кадр анимировать нечего.
        return abs(from.minValue - to.minValue) > 0f || abs(from.maxValue - to.maxValue) > 0f
    }

    /**
     * Промежуточный масштаб: [fraction] = 0 — исходный, 1 — целевой.
     *
     * Логарифмическая ось интерполируется В ЛОГАРИФМАХ: линейная середина
     * между 0,1 и 10 — это 5, а не 1, и переход шёл бы рывком в конце.
     */
    fun interpolate(from: DoseScale, to: DoseScale, fraction: Float): DoseScale {
        val t = fraction.coerceIn(0f, 1f)
        if (t >= 1f || from.logarithmic != to.logarithmic) return to
        if (t <= 0f) return from
        return if (to.logarithmic) {
            LogDoseScale(
                minValue = logMix(from.minValue, to.minValue, t),
                maxValue = logMix(from.maxValue, to.maxValue, t),
            )
        } else {
            LinearDoseScale(
                maxValue = mix(from.maxValue, to.maxValue, t),
                minValue = mix(from.minValue, to.minValue, t),
            )
        }
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun logMix(a: Float, b: Float, t: Float): Float {
        if (a <= 0f || b <= 0f) return mix(a, b, t)
        val value = 10.0.pow(log10(a.toDouble()) + (log10(b.toDouble()) - log10(a.toDouble())) * t)
        return value.toFloat()
    }
}
