package app.radiacode.data

import app.radiacode.data.db.ProfileDao
import app.radiacode.data.db.ProfileEntity
import app.radiacode.data.db.ProfileMaintenanceDao
import app.radiacode.data.db.ProfileNetworkEntity
import app.radiacode.ui.logic.ProfileDeletion
import app.radiacode.ui.logic.ProfilePreset
import app.radiacode.ui.logic.ProfileTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Measurement profiles (spec §3) and their Wi-Fi bindings. Replaces the v2–v5
 * «places» repository: the entity is the same shape plus nesting, icon,
 * archive and the two automation switches.
 *
 * The active profile is resolved by [ProfileTree.resolveActive] against the
 * context machine's answer — the manual choice persisted in [AppSettings] is
 * only the fallback, so a stale id can never point the recording at a deleted
 * or archived profile.
 */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val maintenanceDao: ProfileMaintenanceDao,
    private val settings: ActiveProfilePin,
    /** Profile the context machine currently resolves to; null = unknown. */
    private val contextProfileId: Flow<Long?>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun profiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    fun networks(): Flow<List<ProfileNetworkEntity>> = profileDao.observeNetworks()

    /** Resolved active profile; null only while no profiles exist at all. */
    fun activeProfile(): Flow<ProfileEntity?> = combine(
        profileDao.observeAll(),
        contextProfileId,
        settings.activeProfileId,
    ) { profiles, fromContext, stored ->
        ProfileTree.resolveActive(profiles, fromContext, stored)
    }

    /** Auto-activation table for the context machine: network hash → profile. */
    fun autoBindings(): Flow<Map<String, Long>> = combine(
        profileDao.observeAll(),
        profileDao.observeNetworks(),
    ) { profiles, networks ->
        ProfileTree.autoBindings(profiles, networks.map { it.networkHash to it.profileId })
    }

    /**
     * First launch: create the two profiles the automation cannot work
     * without («В пути», «Без места») plus «Дом» as the initial context.
     * Idempotent — a user who deleted them is not nagged again, because the
     * whole thing only runs while the table is empty.
     */
    suspend fun ensureDefaultProfiles() {
        if (profileDao.count() > 0L) return
        var homeId: Long? = null
        for (preset in ProfileTree.PRESETS.filter { it.name in FIRST_RUN_PRESETS }) {
            val id = create(preset)
            if (preset.name == DEFAULT_PROFILE_NAME) homeId = id
        }
        homeId?.let { settings.setActiveProfileId(it) }
    }

    suspend fun create(preset: ProfilePreset, parentId: Long? = null): Long = profileDao.insert(
        ProfileEntity(
            name = preset.name,
            icon = preset.icon,
            parentId = parentId,
            role = preset.role,
            baselineLearning = preset.baselineLearning,
            createdAt = clock(),
        ),
    )

    suspend fun add(name: String, icon: String = "", parentId: Long? = null): Long =
        profileDao.insert(
            ProfileEntity(
                name = name.trim(),
                icon = icon,
                parentId = parentId,
                createdAt = clock(),
            ),
        )

    suspend fun rename(profileId: Long, name: String) = profileDao.rename(profileId, name.trim())

    suspend fun byId(profileId: Long): ProfileEntity? = profileDao.byId(profileId)

    suspend fun update(profile: ProfileEntity) = profileDao.update(profile)

    suspend fun setIcon(profileId: Long, icon: String) {
        val profile = profileDao.byId(profileId) ?: return
        profileDao.update(profile.copy(icon = icon))
    }

    suspend fun setParent(profileId: Long, parentId: Long?) {
        val profiles = profileDao.all()
        if (!ProfileTree.canSetParent(profiles, profileId, parentId)) return
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        profileDao.update(profile.copy(parentId = parentId))
    }

    suspend fun setAutoActivate(profileId: Long, enabled: Boolean) {
        val profile = profileDao.byId(profileId) ?: return
        profileDao.update(profile.copy(autoActivate = enabled))
    }

    suspend fun setBaselineLearning(profileId: Long, enabled: Boolean) {
        val profile = profileDao.byId(profileId) ?: return
        profileDao.update(profile.copy(baselineLearning = enabled))
    }

    /** Archiving a parent archives its children — «Дом» without «Спальня» is a lie. */
    suspend fun setArchived(profileId: Long, archived: Boolean) {
        if (archived && !ProfileTree.canArchive(profileDao.all(), profileId)) return
        profileDao.setArchivedWithChildren(profileId, archived)
    }

    /**
     * Deletes a profile, keeping every measurement it ever carried.
     *
     * The guard is pure ([ProfileDeletion.evaluate]) and the caller gets the
     * verdict back, so a refusal can be explained instead of looking broken.
     * On success, samples, sessions and experiments detach (profile column →
     * NULL) — the same semantics place deletion had since v2 — the Wi-Fi
     * bindings go, and a manual pin on the deleted profile is released so the
     * app falls back to the automatic context instead of pinning a dead id.
     */
    suspend fun delete(profileId: Long): ProfileDeletion {
        val verdict = ProfileDeletion.evaluate(profileDao.all(), profileId)
        if (verdict !is ProfileDeletion.Allowed) return verdict

        maintenanceDao.detachSamples(profileId)
        maintenanceDao.detachSessions(profileId)
        maintenanceDao.detachExperiments(profileId)
        profileDao.deleteNetworksOf(profileId)
        profileDao.delete(profileId)

        if (settings.activeProfileId.first() == profileId) {
            settings.setActiveProfileId(null)
            settings.setContextManual(false)
        }
        return verdict
    }

    /** Whether «Удалить профиль» may act, and why not when it may not. */
    suspend fun deletionVerdict(profileId: Long): ProfileDeletion =
        ProfileDeletion.evaluate(profileDao.all(), profileId)

    // --- Wi-Fi bindings ---

    /**
     * Binds the currently observed network to a profile. The hash is a local
     * token, never an SSID (see [app.radiacode.context.NetworkIdentity]);
     * [label] is a display-only name and may be null.
     */
    suspend fun bindNetwork(profileId: Long, hash: String, label: String?) {
        val existing = profileDao.networkByHash(hash)
        profileDao.insertNetwork(
            ProfileNetworkEntity(
                id = existing?.id ?: 0,
                profileId = profileId,
                networkHash = hash,
                label = label ?: existing?.label,
                createdAt = existing?.createdAt ?: clock(),
            ),
        )
    }

    suspend fun unbindNetwork(id: Long) = profileDao.deleteNetwork(id)

    // --- active context ---

    suspend fun selectManually(profileId: Long) {
        settings.setActiveProfileId(profileId)
        settings.setContextManual(true)
    }

    /** «Вернуться к авто» (spec §3.2). */
    suspend fun returnToAuto() = settings.setContextManual(false)

    companion object {
        const val DEFAULT_PROFILE_NAME = "Дом"
        private val FIRST_RUN_PRESETS = setOf(DEFAULT_PROFILE_NAME, "В пути", "Без места")
    }
}
