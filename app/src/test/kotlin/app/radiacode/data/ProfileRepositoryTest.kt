package app.radiacode.data

import app.radiacode.data.db.BaselineEpochEntity
import app.radiacode.data.db.ProfileDao
import app.radiacode.data.db.ProfileEntity
import app.radiacode.data.db.ProfileFingerprintEntity
import app.radiacode.data.db.ProfileMaintenanceDao
import app.radiacode.data.db.ProfileNetworkEntity
import app.radiacode.ui.logic.ProfileDeletion
import app.radiacode.ui.logic.ProfileDeletionBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Profile deletion: the guard rules and — the part that matters for the data —
 * that measurements are never deleted, only detached.
 */
class ProfileRepositoryTest {

    private class FakeProfileDao(initial: List<ProfileEntity>) : ProfileDao {

        // Резервная копия читает профили целиком; подделке отвечать нечем.
        override suspend fun allNetworks(): List<ProfileNetworkEntity> = emptyList()

        override suspend fun allEpochs(): List<BaselineEpochEntity> = emptyList()

        override suspend fun allFingerprints(): List<ProfileFingerprintEntity> = emptyList()

        override suspend fun clearProfiles() = Unit

        override suspend fun clearEpochs() = Unit

        override suspend fun clearFingerprints() = Unit

        val profiles = initial.toMutableList()
        val networks = mutableListOf<ProfileNetworkEntity>()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

        override suspend fun insert(profile: ProfileEntity): Long {
            val id = nextId++
            profiles += profile.copy(id = id)
            return id
        }

        override suspend fun update(profile: ProfileEntity) {
            val index = profiles.indexOfFirst { it.id == profile.id }
            if (index >= 0) profiles[index] = profile
        }

        override suspend fun rename(profileId: Long, name: String) {
            update(profiles.first { it.id == profileId }.copy(name = name))
        }

        override suspend fun setArchivedWithChildren(profileId: Long, archived: Boolean) {
            profiles.replaceAll {
                if (it.id == profileId || it.parentId == profileId) {
                    it.copy(archived = archived)
                } else {
                    it
                }
            }
        }

        override suspend fun delete(profileId: Long) {
            profiles.removeAll { it.id == profileId }
        }

        override suspend fun detachChildren(profileId: Long) {
            profiles.replaceAll { if (it.parentId == profileId) it.copy(parentId = null) else it }
        }

        override fun observeAll(): Flow<List<ProfileEntity>> = flowOf(profiles.toList())

        override suspend fun all(): List<ProfileEntity> = profiles.toList()

        override suspend fun byId(profileId: Long): ProfileEntity? =
            profiles.firstOrNull { it.id == profileId }

        override suspend fun byRole(role: String): ProfileEntity? =
            profiles.firstOrNull { it.role == role }

        override suspend fun count(): Long = profiles.size.toLong()

        val epochs = mutableListOf<BaselineEpochEntity>()

        override suspend fun setBaselineEpoch(profileId: Long, epochMillis: Long) {
            val index = profiles.indexOfFirst { it.id == profileId }
            if (index >= 0) {
                profiles[index] = profiles[index].copy(
                    baselineEpochMillis = epochMillis,
                    shiftDeclinedAtMillis = null,
                )
            }
        }

        override suspend fun setShiftDeclined(profileId: Long, atMillis: Long) {
            val index = profiles.indexOfFirst { it.id == profileId }
            if (index >= 0) profiles[index] = profiles[index].copy(shiftDeclinedAtMillis = atMillis)
        }

        override suspend fun insertEpoch(epoch: BaselineEpochEntity): Long {
            epochs += epoch.copy(id = epochs.size + 1L)
            return epochs.size.toLong()
        }

        override suspend fun epochs(profileId: Long): List<BaselineEpochEntity> =
            epochs.filter { it.profileId == profileId }.sortedByDescending { it.endedAtMillis }
        val fingerprints = mutableListOf<ProfileFingerprintEntity>()
        override suspend fun insertFingerprint(fingerprint: ProfileFingerprintEntity): Long {
            fingerprints += fingerprint.copy(id = fingerprints.size + 1L)
            return fingerprints.size.toLong()
        }
        override suspend fun newestFingerprint(profileId: Long): ProfileFingerprintEntity? =
            fingerprints.filter { it.profileId == profileId }.maxByOrNull { it.createdAt }
        override fun observeNewestFingerprint(profileId: Long): Flow<ProfileFingerprintEntity?> =
            flowOf(fingerprints.filter { it.profileId == profileId }.maxByOrNull { it.createdAt })

        override suspend fun insertNetwork(network: ProfileNetworkEntity): Long {
            networks.removeAll { it.networkHash == network.networkHash }
            networks += network
            return network.id
        }

        override suspend fun deleteNetwork(id: Long) {
            networks.removeAll { it.id == id }
        }

        override suspend fun deleteNetworksOf(profileId: Long) {
            networks.removeAll { it.profileId == profileId }
        }

        override fun observeNetworks(): Flow<List<ProfileNetworkEntity>> =
            flowOf(networks.toList())

        override suspend fun networkByHash(hash: String): ProfileNetworkEntity? =
            networks.firstOrNull { it.networkHash == hash }
    }

    /** Records detaches instead of performing them; nothing is ever deleted. */
    private class FakeMaintenanceDao : ProfileMaintenanceDao {
        val detachedSamples = mutableListOf<Long>()
        val detachedSessions = mutableListOf<Long>()
        val detachedExperiments = mutableListOf<Long>()

        override suspend fun detachSamples(profileId: Long) {
            detachedSamples += profileId
        }

        override suspend fun detachSessions(profileId: Long) {
            detachedSessions += profileId
        }

        override suspend fun detachExperiments(profileId: Long) {
            detachedExperiments += profileId
        }

        override suspend fun sampleCount(profileId: Long): Int = 0

        override suspend fun sessionCount(profileId: Long): Int = 0
    }

    private class FakePin(pinned: Long?) : ActiveProfilePin {
        private val pin = MutableStateFlow(pinned)
        var manual: Boolean = pinned != null

        override val activeProfileId: Flow<Long?> = pin

        override suspend fun setActiveProfileId(profileId: Long?) {
            pin.value = profileId
        }

        override suspend fun setContextManual(manual: Boolean) {
            this.manual = manual
        }

        val pinned: Long? get() = pin.value
    }

    private fun profile(
        id: Long,
        name: String,
        parentId: Long? = null,
        archived: Boolean = false,
    ) = ProfileEntity(
        id = id,
        name = name,
        parentId = parentId,
        archived = archived,
        createdAt = id * 100,
    )

    private fun repository(
        dao: FakeProfileDao,
        maintenance: FakeMaintenanceDao = FakeMaintenanceDao(),
        pin: FakePin = FakePin(null),
    ) = ProfileRepository(
        profileDao = dao,
        maintenanceDao = maintenance,
        settings = pin,
        contextProfileId = flowOf(null),
        clock = { 1_000L },
    )

    @Test
    fun `deleting a profile detaches its measurements and never deletes them`() = runTest {
        val dao = FakeProfileDao(listOf(profile(1, "Дом"), profile(2, "Тест")))
        dao.insertNetwork(
            ProfileNetworkEntity(id = 7, profileId = 2, networkHash = "abc", createdAt = 0),
        )
        val maintenance = FakeMaintenanceDao()
        val repo = repository(dao, maintenance)

        val verdict = repo.delete(2)

        assertIs<ProfileDeletion.Allowed>(verdict)
        assertEquals(listOf("Дом"), dao.profiles.map { it.name })
        assertEquals(listOf(2L), maintenance.detachedSamples)
        assertEquals(listOf(2L), maintenance.detachedSessions)
        assertEquals(listOf(2L), maintenance.detachedExperiments)
        assertTrue(dao.networks.isEmpty())
    }

    @Test
    fun `deleting the active profile falls back to the automatic context`() = runTest {
        val dao = FakeProfileDao(listOf(profile(1, "Дом"), profile(2, "Тест")))
        val pin = FakePin(pinned = 2)
        val repo = repository(dao, pin = pin)

        repo.delete(2)

        assertNull(pin.pinned)
        assertEquals(false, pin.manual)
    }

    @Test
    fun `deleting another profile leaves the pin alone`() = runTest {
        val dao = FakeProfileDao(listOf(profile(1, "Дом"), profile(2, "Тест")))
        val pin = FakePin(pinned = 1)
        val repo = repository(dao, pin = pin)

        repo.delete(2)

        assertEquals(1L, pin.pinned)
        assertTrue(pin.manual)
    }

    @Test
    fun `a blocked deletion touches nothing at all`() = runTest {
        val dao = FakeProfileDao(listOf(profile(1, "Дом"), profile(2, "Спальня", parentId = 1)))
        val maintenance = FakeMaintenanceDao()
        val pin = FakePin(pinned = 1)
        val repo = repository(dao, maintenance, pin)

        val verdict = repo.delete(1)

        assertEquals(
            ProfileDeletionBlock.HAS_CHILDREN,
            assertIs<ProfileDeletion.Blocked>(verdict).reason,
        )
        assertEquals(2, dao.profiles.size)
        assertTrue(maintenance.detachedSamples.isEmpty())
        assertTrue(maintenance.detachedSessions.isEmpty())
        assertEquals(1L, pin.pinned)
    }

    @Test
    fun `the last live profile survives a delete attempt`() = runTest {
        val dao = FakeProfileDao(listOf(profile(1, "Дом")))
        val repo = repository(dao)

        val verdict = repo.delete(1)

        assertEquals(
            ProfileDeletionBlock.LAST_LIVE_PROFILE,
            assertIs<ProfileDeletion.Blocked>(verdict).reason,
        )
        assertEquals(1, dao.profiles.size)
    }

    @Test
    fun `the verdict a screen shows matches what delete would do`() = runTest {
        val dao = FakeProfileDao(listOf(profile(1, "Дом"), profile(2, "Тест")))
        val repo = repository(dao)

        assertIs<ProfileDeletion.Allowed>(repo.deletionVerdict(2))
        assertIs<ProfileDeletion.Blocked>(repo.deletionVerdict(99))
    }

    @Test
    fun `the roles the automation needs are restored when they are gone`() = runTest {
        // Полевой случай: профиля «В пути» нет, человек уходит из дома — и
        // решению контекста некуда лечь.
        val dao = FakeProfileDao(listOf(ProfileEntity(id = 1, name = "Дом", createdAt = 1)))
        val repo = repository(dao)

        repo.ensureDefaultProfiles()

        assertNotNull(dao.byRole(ProfileEntity.ROLE_TRANSIT))
        assertNotNull(dao.byRole(ProfileEntity.ROLE_NO_PLACE))
        // «Дом» не создаётся повторно: таблица была не пуста.
        assertEquals(1, dao.profiles.count { it.name == "Дом" })
        // И обучение фона у ролевых профилей выключено по смыслу: «в пути» —
        // это ситуация, а не комната.
        assertEquals(false, dao.byRole(ProfileEntity.ROLE_TRANSIT)?.baselineLearning)
    }

    @Test
    fun `an archived role profile is revived, not duplicated`() = runTest {
        val dao = FakeProfileDao(
            listOf(
                ProfileEntity(id = 1, name = "Дом", createdAt = 1),
                ProfileEntity(
                    id = 2,
                    name = "В дороге",
                    role = ProfileEntity.ROLE_TRANSIT,
                    archived = true,
                    createdAt = 2,
                ),
            ),
        )
        val repo = repository(dao)

        repo.ensureDefaultProfiles()

        // У него уже есть история и привязки — второй такой же был бы потерей.
        assertEquals(1, dao.profiles.count { it.role == ProfileEntity.ROLE_TRANSIT })
        assertEquals(false, dao.byRole(ProfileEntity.ROLE_TRANSIT)?.archived)
        // Имя не трогаем: роль от имени не зависит.
        assertEquals("В дороге", dao.byRole(ProfileEntity.ROLE_TRANSIT)?.name)
    }
}
