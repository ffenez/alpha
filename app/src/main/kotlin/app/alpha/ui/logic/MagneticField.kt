package app.alpha.ui.logic

import app.alpha.sensors.EnvironmentWindow
import app.alpha.ui.text.SearchStrings

/**
 * Строка магнитного поля в Поиске.
 *
 * Абсолютные микротеслы сами по себе почти ничего не значат: телефон — это
 * набор магнитов (динамик, вибромотор, магнит чехла), и его собственное поле
 * обычно больше того, что ищут. Полезно ИЗМЕНЕНИЕ: отношение к точке отсчёта,
 * если она снята, и движение числа, пока прибор ведут.
 *
 * Поэтому строка всегда называет знаменатель, а разброс за окно печатается
 * рядом с числом: большой разброс означает, что телефон крутили в руке, и
 * отношение в этот момент верить нельзя.
 */
object MagneticField {

    /**
     * Ниже этого разброса окно считается снятым спокойно. Порог выбран как
     * доля от типичного модуля поля Земли (около 50 мкТл): 1 мкТл — это 2 %,
     * меньше самого слабого отличия, которое имеет смысл обсуждать.
     */
    const val CALM_SD_UT = 1.0f

    /** Отношение к отсчёту; null — отсчёта нет или он нулевой. */
    fun ratio(current: Float?, reference: Float?): Float? {
        if (current == null || reference == null || reference <= 0f) return null
        return current / reference
    }

    /**
     * @return null, когда магнитометра нет или окно ещё не собрано — пустая
     *   строка на экране хуже отсутствующей.
     */
    fun line(
        window: EnvironmentWindow?,
        referenceUt: Float?,
        s: SearchStrings,
    ): String? {
        val value = window?.magneticUt ?: return null
        val spread = window.magneticSd
        val head = s.fieldValue(
            Uncertainty.num1(value),
            spread?.let { Uncertainty.num1(it) },
        )
        val ratio = ratio(value, referenceUt) ?: return head
        return s.fieldRatioToMark(Uncertainty.num2(ratio), head)
    }

    /** Поле «дрожит» — телефон двигали, отношению верить рано. */
    fun restless(window: EnvironmentWindow?): Boolean =
        (window?.magneticSd ?: 0f) > CALM_SD_UT
}
