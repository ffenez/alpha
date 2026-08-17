package app.radiacode.ui.screens

import app.radiacode.ui.text.AppLanguage
import app.radiacode.ui.text.ExportCatalogue
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.stringsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Действия над записями называются одинаково и стоят в одном порядке.
 *
 * Это и есть суть единого контракта журнала: сессия, маршрут, спектр и опыт —
 * разные по содержанию, но одинаковые по обращению. Пока наборы собирались в
 * каждом экране отдельно, «Экспорт» звался то экспортом, то форматом файла, а
 * удаление стояло где придётся.
 */
class EntityMenusTest {

    private fun menus(language: AppLanguage): List<List<String>> {
        val strings = stringsFor(language)
        val export = ExportCatalogue.of(language)
        val history = HistoryCatalogue.of(language)
        return listOf(
            EntityMenus.spectrum(
                strings = strings,
                export = export,
                history = history,
                canCompare = true,
                onExport = {},
                onCompare = {},
                onContinue = {},
                onRename = {},
                onDelete = {},
            ),
            EntityMenus.session(
                strings = strings,
                export = export,
                onExport = {},
                onProfile = {},
                onDelete = {},
            ),
            EntityMenus.route(
                strings = strings,
                export = export,
                history = history,
                canCompare = true,
                onExport = {},
                onCompare = {},
                onRename = {},
                onDelete = {},
            ),
            EntityMenus.experiment(
                strings = strings,
                export = export,
                onExport = {},
                onDelete = {},
            ),
        ).map { menu -> menu.map { it.title } }
    }

    @Test
    fun `экспорт называется одинаково у всех записей`() {
        for (language in AppLanguage.entries) {
            val export = ExportCatalogue.of(language).export
            for (titles in menus(language)) {
                assertEquals(export, titles.first(), "экспорт не первый или назван иначе: $titles")
            }
        }
    }

    @Test
    fun `удаление всегда последнее`() {
        for (language in AppLanguage.entries) {
            val delete = stringsFor(language).delete
            for (titles in menus(language)) {
                // Разрушающее действие не должно стоять там, куда палец
                // приходит по привычке.
                assertEquals(delete, titles.last(), "удаление не последнее: $titles")
            }
        }
    }

    @Test
    fun `недоступное действие гаснет, а не исчезает`() {
        val strings = stringsFor(AppLanguage.RU)
        val export = ExportCatalogue.of(AppLanguage.RU)
        val history = HistoryCatalogue.of(AppLanguage.RU)
        val alone = EntityMenus.spectrum(
            strings = strings,
            export = export,
            history = history,
            canCompare = false,
            onExport = {},
            onCompare = {},
            onContinue = {},
            onRename = {},
            onDelete = {},
        )
        val paired = EntityMenus.spectrum(
            strings = strings,
            export = export,
            history = history,
            canCompare = true,
            onExport = {},
            onCompare = {},
            onContinue = {},
            onRename = {},
            onDelete = {},
        )
        // Пропавший пункт заставляет искать, куда он делся; погасший объясняет
        // состояние записи — сравнивать не с чем, пока снимок один.
        assertEquals(paired.map { it.title }, alone.map { it.title })
        assertTrue(paired.all { it.enabled })
        assertEquals(1, alone.count { !it.enabled })
    }

    @Test
    fun `названия действий не повторяются внутри меню`() {
        for (language in AppLanguage.entries) {
            for (titles in menus(language)) {
                assertEquals(titles.size, titles.toSet().size, "повтор в меню: $titles")
            }
        }
    }
}
