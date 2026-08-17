package app.alpha.data.export.html

import java.util.Locale

/**
 * Число в отчёте: точность задаёт вызывающий, разделитель — ЯЗЫК отчёта.
 *
 * Отчёт уходит с телефона и читается кем угодно, поэтому русская запятая в
 * английской странице — не мелочь оформления: «0,145» в английском тексте
 * читается как перечисление. Форматирование идёт через `Locale.US`, чтобы
 * системная локаль телефона не подменяла разделитель молча, а запятая
 * ставится явно и только там, где её ждут.
 */
internal object ReportNumber {

    fun decimal(value: Double, decimals: Int, comma: Boolean): String {
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return if (comma) text.replace('.', ',') else text
    }
}
