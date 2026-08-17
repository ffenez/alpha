package app.alpha.ui.logic

import app.alpha.data.db.ProfileEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileDeletionTest {

    private fun profile(
        id: Long,
        name: String,
        parentId: Long? = null,
        archived: Boolean = false,
        role: String = ProfileEntity.ROLE_USER,
    ) = ProfileEntity(
        id = id,
        name = name,
        parentId = parentId,
        archived = archived,
        role = role,
        createdAt = id * 100,
    )

    private val home = profile(1, "Дом")
    private val bedroom = profile(2, "Спальня", parentId = 1)
    private val office = profile(4, "Офис")

    @Test
    fun `a plain leaf profile can be deleted`() {
        val verdict = ProfileDeletion.evaluate(listOf(home, office), office.id)
        assertEquals(office, assertIs<ProfileDeletion.Allowed>(verdict).profile)
    }

    @Test
    fun `a freshly created profile is deletable — the field bug`() {
        val fresh = profile(9, "Тест")
        val verdict = ProfileDeletion.evaluate(listOf(home, office, fresh), fresh.id)
        assertIs<ProfileDeletion.Allowed>(verdict)
    }

    @Test
    fun `the last live profile is kept — measurements need a context`() {
        val verdict = ProfileDeletion.evaluate(listOf(home), home.id)
        val blocked = assertIs<ProfileDeletion.Blocked>(verdict)
        assertEquals(ProfileDeletionBlock.LAST_LIVE_PROFILE, blocked.reason)
    }

    @Test
    fun `archived profiles do not count as the one left to record into`() {
        val archived = profile(5, "Дача", archived = true)
        val verdict = ProfileDeletion.evaluate(listOf(home, archived), home.id)
        assertEquals(
            ProfileDeletionBlock.LAST_LIVE_PROFILE,
            assertIs<ProfileDeletion.Blocked>(verdict).reason,
        )
    }

    @Test
    fun `an archived profile may go even when it is the only one left besides one live`() {
        val archived = profile(5, "Дача", archived = true)
        val verdict = ProfileDeletion.evaluate(listOf(home, archived), archived.id)
        assertIs<ProfileDeletion.Allowed>(verdict)
    }

    @Test
    fun `a parent is not deleted behind its children's back`() {
        val verdict = ProfileDeletion.evaluate(listOf(home, bedroom, office), home.id)
        val blocked = assertIs<ProfileDeletion.Blocked>(verdict)
        assertEquals(ProfileDeletionBlock.HAS_CHILDREN, blocked.reason)
        assertEquals(listOf("Спальня"), blocked.children)
    }

    @Test
    fun `an archived parent is blocked by its children just the same`() {
        val archivedHome = home.copy(archived = true)
        val archivedBedroom = bedroom.copy(archived = true)
        val verdict = ProfileDeletion.evaluate(
            listOf(archivedHome, archivedBedroom, office),
            archivedHome.id,
        )
        assertEquals(
            ProfileDeletionBlock.HAS_CHILDREN,
            assertIs<ProfileDeletion.Blocked>(verdict).reason,
        )
    }

    @Test
    fun `a child may be deleted while its parent stays`() {
        val verdict = ProfileDeletion.evaluate(listOf(home, bedroom, office), bedroom.id)
        assertIs<ProfileDeletion.Allowed>(verdict)
    }

    @Test
    fun `a vanished id is reported, not silently allowed`() {
        val verdict = ProfileDeletion.evaluate(listOf(home, office), 777L)
        assertEquals(
            ProfileDeletionBlock.UNKNOWN,
            assertIs<ProfileDeletion.Blocked>(verdict).reason,
        )
    }

    @Test
    fun `every block names its actual obstacle`() {
        val children = ProfileDeletion.blockedWording(
            ProfileDeletion.Blocked(ProfileDeletionBlock.HAS_CHILDREN, listOf("Спальня", "Кухня")),
        )
        assertTrue(children.contains("Спальня") && children.contains("Кухня"), children)

        val last = ProfileDeletion.blockedWording(
            ProfileDeletion.Blocked(ProfileDeletionBlock.LAST_LIVE_PROFILE),
        )
        assertTrue(last.contains("последний профиль"), last)
    }

    @Test
    fun `the confirmation promises the measurements survive`() {
        val text = ProfileDeletion.confirmWording("Тест")
        assertTrue(text.contains("«Тест»"), text)
        assertTrue(text.contains("останутся в журнале"), text)
        assertTrue(text.contains("не удаляются"), text)
    }

    @Test
    fun `the roles the automation needs cannot be deleted, and the reason says why`() {
        // Приложение восстанавливает эти профили при запуске службы, поэтому
        // разрешить удаление значило бы молча отменять действие человека.
        val home = profile(id = 1, name = "Дом")
        val transit = profile(id = 2, name = "В пути", role = ProfileEntity.ROLE_TRANSIT)
        val noPlace = profile(id = 3, name = "Без места", role = ProfileEntity.ROLE_NO_PLACE)
        val profiles = listOf(home, transit, noPlace)

        for (id in listOf(transit.id, noPlace.id)) {
            val verdict = ProfileDeletion.evaluate(profiles, id)
            assertTrue(verdict is ProfileDeletion.Blocked, "$verdict")
            assertEquals(
                ProfileDeletionBlock.REQUIRED_ROLE,
                (verdict as ProfileDeletion.Blocked).reason,
            )
            val wording = ProfileDeletion.blockedWording(verdict)
            assertTrue(wording.contains("создало бы его заново"), wording)
            assertTrue(wording.contains("переименовать"), wording)
        }
        // Обычный профиль удаляется как прежде.
        assertTrue(ProfileDeletion.evaluate(profiles, home.id) is ProfileDeletion.Allowed)
    }
}
