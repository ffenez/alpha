package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Меню экспорта называет РЕЗУЛЬТАТ, а формат оставляет пояснению.
 *
 * Пункт «HTML» отвечает на вопрос, которого человек не задавал: ему нужен
 * отчёт, таблица или данные. При этом формат не прячется — иначе тот, кому
 * нужен именно GeoJSON, не найдёт его вовсе. Проверяется обе половины правила
 * сразу: имя без формата, пояснение — с ним.
 */
class ExportStringsTest {

    private val formats = listOf("HTML", "CSV", "JSON", "GPX", "GeoJSON", "TXT")

    @Test
    fun `название пункта не является форматом`() {
        for (catalogue in listOf(ExportRu, ExportEn)) {
            val titles = listOf(
                catalogue.report,
                catalogue.table,
                catalogue.data,
                catalogue.mapData,
                catalogue.track,
                catalogue.text,
            )
            for (title in titles) {
                for (format in formats) {
                    assertTrue(
                        !title.contains(format, ignoreCase = true),
                        "формат «$format» в названии пункта: $title",
                    )
                }
            }
        }
    }

    @Test
    fun `формат назван в пояснении`() {
        val hints = listOf(
            ExportRu.reportHint to "HTML",
            ExportRu.tableHint to "CSV",
            ExportRu.dataHint to "JSON",
            ExportRu.mapDataHint to "GeoJSON",
            ExportRu.trackHint to "GPX",
            ExportEn.reportHint to "HTML",
            ExportEn.mapDataHint to "GeoJSON",
        )
        for ((hint, format) in hints) {
            assertTrue(hint.contains(format), "в пояснении нет формата «$format»: $hint")
        }
    }

    @Test
    fun `вопрос о координатах объясняет, зачем он задан`() {
        for (catalogue in listOf(ExportRu, ExportEn)) {
            // «Координаты в файле» без причины выглядит придиркой; причина —
            // дом в начале и конце маршрута — названа прямо.
            assertTrue(catalogue.coordinatesNote.length > 40, catalogue.coordinatesNote)
        }
    }

    @Test
    fun `отказ говорит, что делать дальше`() {
        assertTrue(ExportRu.failed.contains("папку"), ExportRu.failed)
        assertTrue(ExportEn.failed.contains("folder"), ExportEn.failed)
    }

    @Test
    fun `на экране нет слов реализации`() {
        val jargon = listOf("uri", "saf", "mime", "документ-провайдер", "content://", "stream")
        for (text in ExportRu.allTexts() + ExportEn.allTexts()) {
            for (word in jargon) {
                assertTrue(!text.lowercase().contains(word), "«$word» в тексте: $text")
            }
        }
    }
}
