package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Заголовок компактной плитки — ОДНО слово.
 *
 * Плитка узкая, и заголовок в два-три слова на ней переносится; перенесённый
 * заголовок перестаёт быть заголовком — глаз читает его как две строки текста,
 * а плитка теряет форму. Всё, что в слово не помещается — единица, период,
 * оговорка, — живёт вторичной строкой под значением.
 *
 * Если смысл нельзя выразить одним словом, для него не подходит плитка: тогда
 * это строка или подпись, а не KPI.
 */
class TileLabelsTest {

    /** Подписи, которые действительно стоят заголовками плиток. */
    private fun labels(s: Strings) = listOf(
        s.backgroundTag,
        s.trendPerHour,
        s.dose,
    )

    @Test
    fun `every tile header is a single word`() {
        for (s in listOf(RuStrings, EnStrings)) {
            for (label in labels(s) + listOf(SearchRu.toBackground, SearchEn.toBackground)) {
                assertTrue(label.isNotBlank(), "пустой заголовок")
                assertTrue(
                    label.trim().none { it.isWhitespace() },
                    "заголовок из нескольких слов: «$label»",
                )
                // Ни предлогов, ни единиц: они уводят заголовок в перенос.
                assertTrue(!label.contains(","), "единица в заголовке: «$label»")
                assertTrue(label.length <= MAX_LABEL, "слишком длинный заголовок: «$label»")
            }
        }
    }

    private companion object {
        /**
         * Предел длины заголовка.
         * **Инженерный параметр**: двенадцать знаков — столько помещается в
         * треть ширины экрана мелким шрифтом заголовка без переноса.
         */
        const val MAX_LABEL = 12
    }
}
