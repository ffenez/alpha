package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Поиск по настройкам ищет СЛОВОМ, которым человек называет настройку про
 * себя, а не подписью, которой она подписана.
 */
class SettingsSearchTest {

    private val index = listOf(
        SettingsSearch.Entry(
            categoryId = "sound",
            title = "Сигнал при поиске",
            section = "Отклик",
            keywords = listOf("звук", "сигнал", "клики", "вибро", "тон"),
        ),
        SettingsSearch.Entry(
            categoryId = "profiles",
            title = "Обновлять обычный фон",
            section = "Профили и фон",
            keywords = listOf("фон", "профиль", "место", "обучение"),
        ),
        SettingsSearch.Entry(
            categoryId = "view",
            title = "Язык",
            section = "Интерфейс",
            keywords = listOf("язык", "language", "русский", "english"),
        ),
    )

    @Test
    fun `слово ведёт в раздел, где настройка лежит`() {
        val hits = SettingsSearch.find("звук", index)
        assertEquals(1, hits.size)
        assertEquals("sound", hits.first().categoryId)
        assertEquals("Отклик", hits.first().section)
    }

    @Test
    fun `регистр и ё значения не имеют`() {
        assertEquals(1, SettingsSearch.find("ЗВУК", index).size)
        assertEquals(
            SettingsSearch.normalize("Ёлка"),
            SettingsSearch.normalize("елка"),
        )
    }

    @Test
    fun `совпадение только по началу слова`() {
        // «он» не должно находить «фон»: после второй буквы список обязан
        // сужаться, а не расширяться.
        assertTrue(SettingsSearch.find("он", index).isEmpty())
        assertEquals(1, SettingsSearch.find("фо", index).size)
    }

    @Test
    fun `ищется и по самой подписи`() {
        val hits = SettingsSearch.find("обновлять", index)
        assertEquals("profiles", hits.single().categoryId)
    }

    @Test
    fun `пустой запрос ничего не находит`() {
        assertTrue(SettingsSearch.find("", index).isEmpty())
        assertTrue(SettingsSearch.find("   ", index).isEmpty())
    }

    @Test
    fun `второй язык ищется своими словами`() {
        assertEquals("view", SettingsSearch.find("language", index).single().categoryId)
    }
}
