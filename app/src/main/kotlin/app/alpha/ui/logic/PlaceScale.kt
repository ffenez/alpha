package app.alpha.ui.logic

import kotlin.math.ln
import kotlin.math.pow

/**
 * Шкала места: ось не времени, а ОТНОШЕНИЯ к обычному фону этого места.
 *
 * График отвечает на вопрос «растёт ли», а эта шкала — на другой: «много ли
 * это здесь». Ось логарифмическая, потому что равные множители обязаны быть
 * равными расстояниями: от ×[MIN_RATIO] до ×[MAX_RATIO], медиана профиля —
 * ×1 и стоит на четверти шкалы, а не в её середине, чтобы вправо оставалось
 * место для настоящего роста.
 *
 * Всё здесь — доли ширины поля (0…1); ни одного пикселя и ни одного цвета:
 * рисование живёт в компоненте, а проверяется эта арифметика.
 */
object PlaceScale {

    /** Левый конец шкалы: вдвое ниже обычного. */
    const val MIN_RATIO = 0.5

    /** Правый конец: восьмикратное превышение обычного. */
    const val MAX_RATIO = 8.0

    /** Положение отношения на шкале, 0…1; вне шкалы — прижато к концу. */
    fun position(ratio: Double): Float {
        if (!ratio.isFinite() || ratio <= 0.0) return 0f
        val octaves = ln(MAX_RATIO / MIN_RATIO) / ln(2.0)
        val value = ln(ratio / MIN_RATIO) / ln(2.0) / octaves
        return value.coerceIn(0.0, 1.0).toFloat()
    }

    /** Положение значения относительно медианы места; null — медианы нет. */
    fun positionOf(value: Float?, medianMicroSvH: Float?): Float? {
        if (value == null || medianMicroSvH == null || medianMicroSvH <= 0f) return null
        return position(value.toDouble() / medianMicroSvH)
    }

    /** Засечки шкалы: удвоения от края до края. */
    fun ticks(): List<Double> {
        val out = mutableListOf<Double>()
        var value = MIN_RATIO
        while (value <= MAX_RATIO + 1e-9) {
            out += value
            value *= 2.0
        }
        return out
    }

    /** Правда ли, что значение ушло за правый конец шкалы. */
    fun offScale(value: Float?, medianMicroSvH: Float?): Boolean {
        if (value == null || medianMicroSvH == null || medianMicroSvH <= 0f) return false
        return value / medianMicroSvH > MAX_RATIO
    }

    /**
     * Отношение по положению — обратное преобразование для подписей засечек.
     */
    fun ratioAt(position: Float): Double {
        val octaves = ln(MAX_RATIO / MIN_RATIO) / ln(2.0)
        return MIN_RATIO * 2.0.pow(position.toDouble() * octaves)
    }
}
