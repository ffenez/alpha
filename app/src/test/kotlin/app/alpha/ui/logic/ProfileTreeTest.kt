package app.alpha.ui.logic

import app.alpha.data.db.ProfileEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTreeTest {

    private fun profile(
        id: Long,
        name: String,
        parentId: Long? = null,
        archived: Boolean = false,
        autoActivate: Boolean = true,
        createdAt: Long = id * 100,
    ) = ProfileEntity(
        id = id,
        name = name,
        parentId = parentId,
        archived = archived,
        autoActivate = autoActivate,
        createdAt = createdAt,
    )

    private val home = profile(1, "Дом")
    private val bedroom = profile(2, "Спальня", parentId = 1)
    private val kitchen = profile(3, "Кухня", parentId = 1)
    private val office = profile(4, "Офис")
    private val all = listOf(home, bedroom, kitchen, office)

    @Test
    fun `tree groups children under their root in creation order`() {
        val nodes = ProfileTree.tree(all)
        assertEquals(listOf("Дом", "Офис"), nodes.map { it.profile.name })
        assertEquals(listOf("Спальня", "Кухня"), nodes.first().children.map { it.name })
    }

    @Test
    fun `flat order interleaves children right after their parent`() {
        assertEquals(
            listOf("Дом", "Спальня", "Кухня", "Офис"),
            ProfileTree.visible(all).map { it.name },
        )
    }

    @Test
    fun `archived profiles leave the picker`() {
        val archived = all.map { if (it.id == 4L) it.copy(archived = true) else it }
        assertEquals(
            listOf("Дом", "Спальня", "Кухня"),
            ProfileTree.visible(archived).map { it.name },
        )
    }

    @Test
    fun `orphaned children are promoted to roots instead of vanishing`() {
        val orphan = listOf(bedroom, office)
        assertEquals(listOf("Спальня", "Офис"), ProfileTree.visible(orphan).map { it.name })
    }

    @Test
    fun `display name spells out the nesting`() {
        assertEquals("Дом / Спальня", ProfileTree.displayName(bedroom, all))
        assertEquals("Дом", ProfileTree.displayName(home, all))
    }

    @Test
    fun `nesting is one level deep and never cyclic`() {
        assertTrue(ProfileTree.canSetParent(all, office.id, home.id))
        assertFalse(ProfileTree.canSetParent(all, office.id, bedroom.id), "parent must be a root")
        assertFalse(ProfileTree.canSetParent(all, home.id, office.id), "a parent cannot be nested")
        assertFalse(ProfileTree.canSetParent(all, home.id, home.id), "no self-parenting")
        assertFalse(ProfileTree.canSetParent(all, office.id, 99L), "unknown parent")
        assertTrue(ProfileTree.canSetParent(all, bedroom.id, null), "detaching is always allowed")
    }

    @Test
    fun `an archived profile cannot adopt children`() {
        val archivedHome = all.map { if (it.id == 1L) it.copy(archived = true) else it }
        assertFalse(ProfileTree.canSetParent(archivedHome, office.id, home.id))
    }

    @Test
    fun `parent candidates exclude everything the rules forbid`() {
        assertEquals(listOf("Дом"), ProfileTree.parentCandidates(all, office.id).map { it.name })
        assertEquals(emptyList(), ProfileTree.parentCandidates(all, home.id).map { it.name })
    }

    @Test
    fun `archiving keeps at least one live profile to record into`() {
        assertTrue(ProfileTree.canArchive(all, office.id))
        // Archiving «Дом» takes its children with it, and only «Офис» remains.
        assertTrue(ProfileTree.canArchive(all, home.id))

        val onlyHome = listOf(home, bedroom)
        assertFalse(
            ProfileTree.canArchive(onlyHome, home.id),
            "archiving a parent archives its children — nothing would be left",
        )
        assertTrue(ProfileTree.canArchive(onlyHome, bedroom.id))
        assertFalse(ProfileTree.canArchive(all, 99L), "unknown profile")
        assertFalse(
            ProfileTree.canArchive(listOf(home.copy(archived = true), office), home.id),
            "already archived",
        )
    }

    @Test
    fun `active profile prefers the context answer over the stored one`() {
        assertEquals(office, ProfileTree.resolveActive(all, contextProfileId = 4, storedProfileId = 1))
        assertEquals(home, ProfileTree.resolveActive(all, contextProfileId = null, storedProfileId = 1))
    }

    @Test
    fun `active profile never resolves to an archived or deleted one`() {
        val archivedOffice = all.map { if (it.id == 4L) it.copy(archived = true) else it }
        assertEquals(
            home,
            ProfileTree.resolveActive(archivedOffice, contextProfileId = 4, storedProfileId = 4),
        )
        assertEquals(home, ProfileTree.resolveActive(all, contextProfileId = 77, storedProfileId = 77))
        assertNull(ProfileTree.resolveActive(emptyList(), 1, 1))
    }

    @Test
    fun `auto bindings drop profiles that opted out of automation`() {
        val profiles = listOf(home.copy(autoActivate = false), office)
        val bindings = ProfileTree.autoBindings(
            profiles,
            listOf("hash-home" to home.id, "hash-office" to office.id),
        )
        assertEquals(mapOf("hash-office" to office.id), bindings)
    }

    @Test
    fun `auto bindings drop archived profiles`() {
        val profiles = listOf(home.copy(archived = true))
        assertEquals(
            emptyMap(),
            ProfileTree.autoBindings(profiles, listOf("hash-home" to home.id)),
        )
    }

    @Test
    fun `presets cover the set the spec asks for`() {
        assertEquals(
            listOf("Дом", "Офис", "Дача", "Родители", "В пути", "Без места"),
            ProfileTree.PRESETS.map { it.name },
        )
        // «В пути» / «Без места» are situations, not places: learning a
        // «typical background of being somewhere» would be meaningless.
        assertEquals(
            listOf("В пути", "Без места"),
            ProfileTree.PRESETS.filter { !it.baselineLearning }.map { it.name },
        )
        assertEquals(
            listOf(ProfileEntity.ROLE_TRANSIT, ProfileEntity.ROLE_NO_PLACE),
            ProfileTree.PRESETS.filter { it.role != ProfileEntity.ROLE_USER }.map { it.role },
        )
    }

    @Test
    fun `leaving home does not silently keep recording into home`() {
        // Полевой случай: Wi-Fi пропал, контекст решил «В пути», но профиля
        // этой роли нет. Приложение показывало «Дом» посреди улицы и кормило
        // его статистику чужими измерениями.
        val home = profile(id = 1, name = "Дом")
        val profiles = listOf(home)
        val resolved = ProfileTree.resolveActive(
            profiles = profiles,
            contextProfileId = null,
            storedProfileId = home.id,
            contextDecided = true,
        )
        assertNull(resolved, "решение «места нет» подменено прежним местом")

        // А пока контекст решения не принял (ручной выбор, старт службы),
        // прежний профиль — правильный ответ.
        assertEquals(
            home.id,
            ProfileTree.resolveActive(profiles, null, home.id, contextDecided = false)?.id,
        )
    }

    @Test
    fun `a decided context still uses the profile of its role when it exists`() {
        val home = profile(id = 1, name = "Дом")
        val transit = profile(id = 2, name = "В пути")
        val resolved = ProfileTree.resolveActive(
            profiles = listOf(home, transit),
            contextProfileId = transit.id,
            storedProfileId = home.id,
            contextDecided = true,
        )
        assertEquals(transit.id, resolved?.id)
    }
}
