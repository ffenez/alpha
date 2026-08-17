package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Раздел резервных копий говорит про ДАННЫЕ ЧЕЛОВЕКА, а не про устройство
 * программы.
 *
 * Внутри копии есть манифест, построчный JSON и контрольные суммы — на экране
 * этих слов быть не должно ни одного: человек переносит свою историю, а не
 * выгружает таблицы. Отдельно проверяется, что отказ называет причину: «копия
 * повреждена» без указания части оставляет без следующего шага.
 */
class BackupStringsTest {

    private val jargon = listOf(
        "дамп", "dump", "база данных", "таблиц", "room", "ndjson", "json",
        "манифест", "сериализ", "чексум", "schema", "sql",
    )

    private val jargonEn = listOf(
        "dump", "database", "table", "room", "ndjson", "json",
        "manifest", "serial", "schema", "sql",
    )

    @Test
    fun `никакого инженерного жаргона на экране`() {
        for (word in jargon) {
            for (text in BackupRu.allTexts()) {
                assertTrue(!text.lowercase().contains(word), "«$word» в тексте: $text")
            }
        }
        for (word in jargonEn) {
            for (text in BackupEn.allTexts()) {
                assertTrue(!text.lowercase().contains(word), "«$word» in text: $text")
            }
        }
    }

    @Test
    fun `каждая причина отказа названа словами`() {
        for (catalogue in listOf(BackupRu, BackupEn)) {
            val problems = listOf(
                app.alpha.data.export.backup.BackupProblem.NotABackup,
                app.alpha.data.export.backup.BackupProblem.TooNew(2, 1),
                app.alpha.data.export.backup.BackupProblem.Missing("measurements"),
                app.alpha.data.export.backup.BackupProblem.Corrupted("measurements"),
                app.alpha.data.export.backup.BackupProblem.Unreadable("нет доступа"),
            )
            for (problem in problems) {
                val text = catalogue.problem(problem)
                assertTrue(text.length > 10, "слишком коротко: $text")
                assertTrue(text.trim().endsWith(".") || text.contains(":"), text)
            }
        }
    }

    @Test
    fun `оба языка описывают одно и то же`() {
        assertEquals(BackupRu.allTexts().size, BackupEn.allTexts().size)
    }

    @Test
    fun `замена честно предупреждает, а объединение нет`() {
        // Объединение ничего не удаляет — пугать нечем; замена удаляет, и об
        // этом сказано до нажатия, а не после.
        assertTrue(BackupRu.replaceNote.contains("заменены"))
        assertTrue(BackupRu.mergeNote.contains("не удаляя"))
    }
}
