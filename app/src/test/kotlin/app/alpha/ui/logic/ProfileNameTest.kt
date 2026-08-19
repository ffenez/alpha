package app.alpha.ui.logic

import app.alpha.data.db.ProfileEntity
import app.alpha.ui.text.EnStrings
import app.alpha.ui.text.RuStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileNameTest {

    private fun profile(name: String, role: String = ProfileEntity.ROLE_USER) =
        ProfileEntity(id = 1, name = name, role = role, createdAt = 0L)

    @Test
    fun `готовое место называется на языке интерфейса`() {
        assertEquals("Home", ProfileName.of(profile("Дом"), EnStrings))
        assertEquals("Дом", ProfileName.of(profile("Home"), RuStrings))
        assertEquals("Cottage", ProfileName.of(profile("Дача"), EnStrings))
    }

    @Test
    fun `служебные роли называются по роли, а не по хранимой строке`() {
        // Эти два места приложение заводит само, и человек их не вводил.
        assertEquals(
            "In transit",
            ProfileName.of(profile("В пути", ProfileEntity.ROLE_TRANSIT), EnStrings),
        )
        assertEquals(
            "No place",
            ProfileName.of(profile("Без места", ProfileEntity.ROLE_NO_PLACE), EnStrings),
        )
    }

    @Test
    fun `имя, введённое человеком, не переводится`() {
        assertEquals("Гараж", ProfileName.of(profile("Гараж"), EnStrings))
        assertEquals("Grandma", ProfileName.of(profile("Grandma"), RuStrings))
    }

    @Test
    fun `путь до вложенного места переводит обе части`() {
        val home = ProfileEntity(id = 1, name = "Дом", createdAt = 0L)
        val child = ProfileEntity(id = 2, name = "Спальня", parentId = 1, createdAt = 1L)
        assertEquals(
            "Home / Спальня",
            ProfileTree.displayName(child, listOf(home, child), EnStrings),
        )
    }
}
