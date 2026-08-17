package app.alpha.ui.theme

/**
 * Пользовательский масштаб интерфейса: отдельно текст, отдельно элементы.
 *
 * ## Почему два множителя
 *
 * Размер текста в пикселях = sp × density × fontScale, размер элемента =
 * dp × density. Один `density` двигает и то, и другое, поэтому элементы едут
 * через `density`, а `fontScale` делится на тот же множитель, чтобы текст не
 * поехал дважды. Прикладывается один раз на всё приложение через
 * `LocalDensity`.
 *
 * ## Системный масштаб шрифта домножается, а не заменяется
 *
 * Процент приложения — множитель к системному значению.
 */
object UiScale {

    /** Значение по умолчанию, %; 100 = как система. */
    const val DEFAULT_PERCENT = 100

    /** Шаг ползунка, %. */
    const val STEP_PERCENT = 5

    /**
     * Границы масштаба текста, %.
     *
     * Верхняя граница выше, чем у элементов: увеличенное число остаётся
     * читаемым, а увеличенная кнопка быстро выталкивает соседей за экран.
     */
    const val FONT_MIN_PERCENT = 80
    const val FONT_MAX_PERCENT = 140

    /** Границы масштаба элементов, %. */
    const val ELEMENT_MIN_PERCENT = 85
    const val ELEMENT_MAX_PERCENT = 130

    /** Хранимое значение может прийти любым: настройка на диске — не гарантия. */
    fun clampFont(percent: Int): Int = percent.coerceIn(FONT_MIN_PERCENT, FONT_MAX_PERCENT)

    fun clampElement(percent: Int): Int =
        percent.coerceIn(ELEMENT_MIN_PERCENT, ELEMENT_MAX_PERCENT)

    /** Ползунок отдаёт непрерывное значение — приводим к шагу округлением, не усечением. */
    fun snap(percent: Float): Int =
        (Math.round(percent / STEP_PERCENT.toFloat()) * STEP_PERCENT)

    /** Плотность экрана с учётом масштаба ЭЛЕМЕНТОВ. */
    fun density(systemDensity: Float, elementPercent: Int): Float =
        systemDensity * clampElement(elementPercent) / 100f

    /**
     * Множитель шрифта: системный × наш, делённый на масштаб элементов.
     *
     * Деление и есть развязка двух ползунков: без него «элементы» тянули бы
     * текст за собой, и два ползунка меняли бы одно и то же.
     */
    fun fontScale(systemFontScale: Float, fontPercent: Int, elementPercent: Int): Float =
        systemFontScale * clampFont(fontPercent) / clampElement(elementPercent).toFloat()
}
