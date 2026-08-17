package app.alpha.ui.chart

import app.alpha.ui.logic.DoseScale
import app.alpha.ui.logic.DoseScales
import app.alpha.ui.logic.LinearDoseScale
import app.alpha.ui.logic.LogDoseScale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Кадр по значениям, заданный рукой.
 *
 * Автоподбор оси отвечает на вопрос «как выглядят измерения»: он подгоняет
 * кадр под наблюдаемое и намеренно НЕ растягивается до далёкого порога — при
 * фоне 0,15 и пороге 0,30 ось до порога сделала бы сам фон плоской чертой.
 * Но вопрос «где мои пороги относительно того, что сейчас» тоже законный, и
 * ответить на него нечем, пока ось не слушается руки. Отсюда второй режим:
 * границы кадра становятся состоянием, и их двигают пальцем.
 */
data class ValueWindow(val min: Float, val max: Float) {
    val span: Float get() = max - min
}

/**
 * Переход оси значений из одного кадра в другой.
 *
 * Ось подгоняется под видимое (устойчивые границы Q10/Q90 колонок, см.
 * `DoseScales`), и после жеста новый кадр обычно требует другого диапазона.
 * Короткий переход показывает, что изменилась сама ось.
 *
 * Анимируется ПРЕДСТАВЛЕНИЕ, а не данные (ТЗ §8): значения колонок не
 * меняются, меняется отображение «значение → доля высоты». Промежуточных
 * измерений не появляется.
 *
 * Если диапазон изменился больше чем в [FAST_RESCALE_FACTOR] раз, масштаб
 * ставится сразу.
 *
 * Здесь же живёт арифметика [ValueWindow] — оси, которую ведут пальцем.
 * Автоподбор не растягивается до далёкого порога; кадр, включающий пороги,
 * задаётся вручную.
 */
object ChartYAxis {

    /**
     * Во сколько раз кадр может быть шире минимального размаха величины.
     * **Инженерный параметр**: тысяча. Ограничение не про смысл, а про
     * арифметику: при размахе в миллионы раз доля значения в кадре перестаёт
     * различаться числом с плавающей точкой, и линия ложится на дно.
     */
    const val MAX_SPAN_FACTOR = 1_000f

    /** Кадр из текущей шкалы — с него начинается ручной режим. */
    fun windowOf(scale: DoseScale): ValueWindow = ValueWindow(scale.minValue, scale.maxValue)

    /**
     * Сдвиг кадра по значениям: [fractionOfSpan] > 0 — смотрим ВЫШЕ.
     *
     * Палец тянет картинку вниз — в кадр приходит то, что было над ним. Так же
     * ведёт себя карта, и так же — время по горизонтали.
     */
    fun pan(window: ValueWindow, fractionOfSpan: Float, minSpan: Float): ValueWindow {
        val shift = window.span * fractionOfSpan
        return clamp(ValueWindow(window.min + shift, window.max + shift), minSpan)
    }

    /**
     * Масштаб кадра: [factor] > 1 — приблизить (размах меньше).
     *
     * Точка под пальцем ([focusFraction], 0 — низ кадра) остаётся на месте: у
     * оси значений это то же правило, что у времени при щипке.
     */
    fun zoom(
        window: ValueWindow,
        factor: Float,
        focusFraction: Float = 0.5f,
        minSpan: Float,
    ): ValueWindow {
        if (factor <= 0f || !factor.isFinite()) return window
        val focus = focusFraction.coerceIn(0f, 1f)
        val at = window.min + window.span * focus
        val span = window.span / factor
        return clamp(ValueWindow(at - span * focus, at + span * (1f - focus)), minSpan)
    }

    /** Ниже нуля мощность дозы и счёт не бывают; размах не меньше значимого. */
    fun clamp(window: ValueWindow, minSpan: Float): ValueWindow {
        val floor = minSpan.coerceAtLeast(1e-6f)
        var min = window.min
        var max = window.max
        if (max - min < floor) {
            val centre = (max + min) / 2f
            min = centre - floor / 2f
            max = centre + floor / 2f
        }
        val ceiling = floor * MAX_SPAN_FACTOR
        if (max - min > ceiling) max = min + ceiling
        if (min < 0f) {
            max -= min
            min = 0f
        }
        return ValueWindow(min, max)
    }

    /** Шкала по заданному кадру: логарифмическая не опускается ниже своего дна. */
    fun scaleOf(window: ValueWindow, logarithmic: Boolean): DoseScale = if (logarithmic) {
        val min = maxOf(window.min, DoseScales.LOG_FLOOR_MICRO_SV_H)
        LogDoseScale(min, maxOf(window.max, min * 10f))
    } else {
        LinearDoseScale(maxValue = window.max, minValue = window.min)
    }

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
     * **Инженерный параметр**: втрое. Меньше — подстройка кадра, больше — в
     * окно вошло другое явление.
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
