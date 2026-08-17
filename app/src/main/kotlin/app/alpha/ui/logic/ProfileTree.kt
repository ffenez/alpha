package app.alpha.ui.logic

import app.alpha.data.db.ProfileEntity

/** One root profile with its («Дом / Спальня») children. */
data class ProfileNode(val profile: ProfileEntity, val children: List<ProfileEntity>)

/** A creation preset offered by Настройки → Профили (spec §3.1). */
data class ProfilePreset(
    val name: String,
    val icon: String,
    val role: String = ProfileEntity.ROLE_USER,
    /**
     * `В пути` and `Без места` describe situations, not rooms: learning a
     * «typical background of being in transit» would mix a train, a street and
     * a car into one meaningless band, so those two start with learning off.
     */
    val baselineLearning: Boolean = true,
)

/**
 * Profile tree rules (spec §3.1) — pure, so the nesting and archival
 * invariants are tested on the JVM instead of being re-checked by hand in the
 * settings screen.
 *
 * Nesting is exactly one level deep: a child may not become a parent and a
 * profile that already has children may not become a child. Cycles are
 * therefore structurally impossible, which is the point — a deeper tree buys
 * nothing for «Дом / Спальня» and makes both the picker and the baseline
 * bookkeeping ambiguous (whose statistics does a middle node own?).
 */
object ProfileTree {

    /** Presets of spec §3.1, offered on an empty list and behind «+». */
    val PRESETS: List<ProfilePreset> = listOf(
        ProfilePreset("Дом", "⌂"),
        ProfilePreset("Офис", "▣"),
        ProfilePreset("Дача", "⌾"),
        ProfilePreset("Родители", "◈"),
        ProfilePreset("В пути", "→", ProfileEntity.ROLE_TRANSIT, baselineLearning = false),
        ProfilePreset("Без места", "○", ProfileEntity.ROLE_NO_PLACE, baselineLearning = false),
    )

    /** Roots (oldest first) each followed by their children (oldest first). */
    fun tree(profiles: List<ProfileEntity>): List<ProfileNode> {
        val byId = profiles.associateBy { it.id }
        // A parent that vanished (deleted profile) promotes its children to roots.
        val roots = profiles.filter { it.parentId == null || byId[it.parentId] == null }
        return roots.sortedBy { it.createdAt }.map { root ->
            ProfileNode(
                profile = root,
                children = profiles.filter { it.parentId == root.id }.sortedBy { it.createdAt },
            )
        }
    }

    /** Flat picker order, archived profiles left out. */
    fun visible(profiles: List<ProfileEntity>): List<ProfileEntity> =
        tree(profiles.filter { !it.archived }).flatMap { listOf(it.profile) + it.children }

    /** «Дом / Спальня» for a child, plain name for a root. */
    fun displayName(profile: ProfileEntity, profiles: List<ProfileEntity>): String {
        val parent = profile.parentId?.let { id -> profiles.firstOrNull { it.id == id } }
        return if (parent == null) profile.name else "${parent.name} / ${profile.name}"
    }

    /** Profiles that may become the parent of [childId] (see class KDoc). */
    fun parentCandidates(profiles: List<ProfileEntity>, childId: Long): List<ProfileEntity> =
        profiles.filter { canSetParent(profiles, childId, it.id) }.sortedBy { it.createdAt }

    fun canSetParent(profiles: List<ProfileEntity>, childId: Long, parentId: Long?): Boolean {
        if (parentId == null) return true // detaching to a root is always allowed
        if (parentId == childId) return false
        val parent = profiles.firstOrNull { it.id == parentId } ?: return false
        if (parent.parentId != null) return false // depth limit: parent must be a root
        if (parent.archived) return false
        return profiles.none { it.parentId == childId } // a parent cannot become a child
    }

    /** At least one non-archived profile must remain to record into. */
    fun canArchive(profiles: List<ProfileEntity>, profileId: Long): Boolean {
        val target = profiles.firstOrNull { it.id == profileId } ?: return false
        if (target.archived) return false
        val archivedWithChildren = buildSet {
            add(profileId)
            profiles.filter { it.parentId == profileId }.forEach { add(it.id) }
        }
        return profiles.any { !it.archived && it.id !in archivedWithChildren }
    }

    /**
     * The profile measurements are stored into: the one the context machine
     * resolved, else the last explicit choice, else the first non-archived
     * profile. Never returns an archived profile when an active one exists.
     */
    fun resolveActive(
        profiles: List<ProfileEntity>,
        contextProfileId: Long?,
        storedProfileId: Long?,
        /**
         * Принял ли контекст решение о месте. `true` означает «решение есть»,
         * даже если оно = «места нет».
         */
        contextDecided: Boolean = false,
    ): ProfileEntity? {
        val active = profiles.filter { !it.archived }
        profiles.firstOrNull { it.id == contextProfileId && !it.archived }?.let { return it }
        // Полевая ошибка: человек ушёл из дома, Wi-Fi пропал, контекст честно
        // перешёл в «В пути» — но профиля этой роли не оказалось, и запасной
        // вариант возвращал ПОСЛЕДНИЙ профиль. Приложение уверенно писало
        // «Дом» посреди улицы и кормило его статистику чужими измерениями.
        // Решение «места нет» — это ОТВЕТ, и подменять его прежним местом
        // нельзя: лучше писать вообще без профиля.
        if (contextDecided) return null
        return active.firstOrNull { it.id == storedProfileId }
            ?: visible(active).firstOrNull()
    }

    /** Bindings for the context machine: only auto-activating live profiles. */
    fun autoBindings(
        profiles: List<ProfileEntity>,
        networks: List<Pair<String, Long>>,
    ): Map<String, Long> {
        val eligible = profiles.filter { it.autoActivate && !it.archived }.map { it.id }.toSet()
        return networks.filter { it.second in eligible }.toMap()
    }
}
