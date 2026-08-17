package app.alpha.data

import app.alpha.data.db.BaselineEpochEntity
import app.alpha.data.db.DownsampledSample
import app.alpha.data.db.ExclusionCount
import app.alpha.data.db.ProfileDao
import app.alpha.data.db.ProfileEntity
import app.alpha.data.db.ProfileFingerprintEntity
import app.alpha.data.db.ProfileNetworkEntity
import app.alpha.data.db.SampleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The baseline epoch (why-spec §7): what the statistics are allowed to read
 * may move, what the app recorded never does.
 */
class BaselineRepositoryTest {

    private class RecordingSampleDao : SampleDao by FakeSampleDao() {
        var lastFrom: Long? = null

        override suspend fun downsampledRangeForProfile(
            profileId: Long,
            from: Long,
            to: Long,
            bucketMillis: Long,
        ): List<DownsampledSample> {
            lastFrom = from
            return emptyList()
        }

        override suspend fun exclusionCountsForProfile(
            profileId: Long,
            from: Long,
            to: Long,
        ): List<ExclusionCount> {
            lastFrom = from
            return emptyList()
        }
    }

    private class FakeProfileDao(profile: ProfileEntity) : ProfileDao {

        // Резервная копия читает профили целиком; подделке отвечать нечем.
        override suspend fun allNetworks(): List<ProfileNetworkEntity> = emptyList()

        override suspend fun allEpochs(): List<BaselineEpochEntity> = emptyList()

        override suspend fun allFingerprints(): List<ProfileFingerprintEntity> = emptyList()

        override suspend fun clearProfiles() = Unit

        override suspend fun clearEpochs() = Unit

        override suspend fun clearFingerprints() = Unit

        var profile: ProfileEntity = profile
        val epochs = mutableListOf<BaselineEpochEntity>()

        override suspend fun insert(profile: ProfileEntity): Long = profile.id
        override suspend fun update(profile: ProfileEntity) { this.profile = profile }
        override suspend fun rename(profileId: Long, name: String) = Unit
        override suspend fun setArchivedWithChildren(profileId: Long, archived: Boolean) = Unit
        override suspend fun delete(profileId: Long) = Unit
        override suspend fun detachChildren(profileId: Long) = Unit
        override suspend fun setBaselineEpoch(profileId: Long, epochMillis: Long) {
            profile = profile.copy(
                baselineEpochMillis = epochMillis,
                shiftDeclinedAtMillis = null,
            )
        }
        override suspend fun setShiftDeclined(profileId: Long, atMillis: Long) {
            profile = profile.copy(shiftDeclinedAtMillis = atMillis)
        }
        override suspend fun insertEpoch(epoch: BaselineEpochEntity): Long {
            epochs += epoch
            return epochs.size.toLong()
        }
        override suspend fun epochs(profileId: Long): List<BaselineEpochEntity> = epochs
        val fingerprints = mutableListOf<ProfileFingerprintEntity>()
        override suspend fun insertFingerprint(fingerprint: ProfileFingerprintEntity): Long {
            fingerprints += fingerprint.copy(id = fingerprints.size + 1L)
            return fingerprints.size.toLong()
        }
        override suspend fun newestFingerprint(profileId: Long): ProfileFingerprintEntity? =
            fingerprints.filter { it.profileId == profileId }.maxByOrNull { it.createdAt }
        override fun observeNewestFingerprint(profileId: Long): Flow<ProfileFingerprintEntity?> =
            flowOf(fingerprints.filter { it.profileId == profileId }.maxByOrNull { it.createdAt })
        override fun observeAll(): Flow<List<ProfileEntity>> = flowOf(listOf(profile))
        override suspend fun all(): List<ProfileEntity> = listOf(profile)
        override suspend fun byId(profileId: Long): ProfileEntity? =
            profile.takeIf { it.id == profileId }
        override fun observeNetworks(): Flow<List<ProfileNetworkEntity>> = flowOf(emptyList())
        override suspend fun byRole(role: String): ProfileEntity? =
            profile.takeIf { it.role == role }
        override suspend fun count(): Long = 1
        override suspend fun insertNetwork(network: ProfileNetworkEntity): Long = 0
        override suspend fun deleteNetwork(id: Long) = Unit
        override suspend fun deleteNetworksOf(profileId: Long) = Unit
        override suspend fun networkByHash(hash: String): ProfileNetworkEntity? = null
    }

    private val now = 10_000_000_000L
    private val profile = ProfileEntity(id = 7, name = "Дом", createdAt = 0)

    @Test
    fun `without an epoch the statistics read the whole sliding window`() = runTest {
        val samples = RecordingSampleDao()
        val profiles = FakeProfileDao(profile)
        val repository = BaselineRepository(samples, profiles) { now }

        repository.state(profileId = 7)
        assertEquals(now - 14L * 24 * 3600_000L, assertNotNull(samples.lastFrom))
    }

    @Test
    fun `an epoch cuts the window without touching a single measurement`() = runTest {
        val samples = RecordingSampleDao()
        val epoch = now - 2L * 24 * 3600_000L
        val profiles = FakeProfileDao(profile.copy(baselineEpochMillis = epoch))
        val repository = BaselineRepository(samples, profiles) { now }

        repository.state(profileId = 7)
        assertEquals(epoch, assertNotNull(samples.lastFrom))
        repository.exclusions(profileId = 7)
        assertEquals(epoch, samples.lastFrom, "the same cut applies to the exclusions")

        // The repository only ever *reads*: the epoch is a query bound.
        assertEquals(0L, samples.count())
    }

    @Test
    fun `an epoch older than the window does not widen it`() = runTest {
        val samples = RecordingSampleDao()
        val profiles = FakeProfileDao(profile.copy(baselineEpochMillis = now - 400L * 24 * 3600_000L))
        val repository = BaselineRepository(samples, profiles) { now }

        repository.state(profileId = 7)
        assertEquals(now - 14L * 24 * 3600_000L, assertNotNull(samples.lastFrom))
    }

    @Test
    fun `starting a new period keeps the old one and clears the decline`() = runTest {
        val profiles = FakeProfileDao(profile.copy(shiftDeclinedAtMillis = now - 1000))
        val repository = BaselineRepository(RecordingSampleDao(), profiles) { now }

        repository.startNewPeriod(profileId = 7, stats = """{"p10":"0.14","p90":"0.17"}""")

        val stored = profiles.epochs.single()
        assertEquals(7L, stored.profileId)
        assertEquals(now, stored.endedAtMillis)
        assertEquals(BaselineEpochEntity.REASON_USER_SHIFT, stored.reason)
        assertTrue(stored.stats.contains("0.14"), stored.stats)
        // The first period starts where the window did — there was no epoch.
        assertEquals(now - 14L * 24 * 3600_000L, stored.startedAtMillis)

        assertEquals(now, profiles.profile.baselineEpochMillis)
        assertNull(profiles.profile.shiftDeclinedAtMillis, "a new period is not a declined offer")
    }

    @Test
    fun `a second period starts where the previous one ended`() = runTest {
        val first = now - 5L * 24 * 3600_000L
        val profiles = FakeProfileDao(profile.copy(baselineEpochMillis = first))
        val repository = BaselineRepository(RecordingSampleDao(), profiles) { now }

        repository.startNewPeriod(profileId = 7, stats = "{}")

        assertEquals(first, profiles.epochs.single().startedAtMillis)
        assertEquals(now, profiles.profile.baselineEpochMillis)
    }

    @Test
    fun `declining is remembered and changes nothing else`() = runTest {
        val profiles = FakeProfileDao(profile)
        val repository = BaselineRepository(RecordingSampleDao(), profiles) { now }

        repository.declineShift(profileId = 7)

        assertEquals(now, profiles.profile.shiftDeclinedAtMillis)
        assertNull(profiles.profile.baselineEpochMillis, "declining never moves the epoch")
        assertTrue(profiles.epochs.isEmpty())
    }
}
