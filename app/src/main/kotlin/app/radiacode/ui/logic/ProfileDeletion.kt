package app.radiacode.ui.logic

import app.radiacode.data.db.ProfileEntity
import app.radiacode.ui.text.MonitorRu
import app.radiacode.ui.text.MonitorStrings

/**
 * Why a profile may not be deleted. The reason is part of the contract: a
 * disabled button without a named cause is exactly the bug this replaced —
 * the user could not tell «forbidden» from «broken».
 */
enum class ProfileDeletionBlock {
    /** The id does not exist any more (a stale row on screen). */
    UNKNOWN,

    /**
     * Deleting it would leave no profile to record into. Measurements always
     * need a context to be compared against, and the app would silently
     * recreate the presets on the next service start.
     */
    LAST_LIVE_PROFILE,

    /**
     * The profile has nested profiles («Дом» with «Спальня»).
     *
     * Blocking is deliberate, and the alternative — silently re-parenting the
     * children to roots — was rejected: a child owns its own baseline
     * statistics *as a room of that place*, and «Спальня» standing alone is a
     * different context from «Дом / Спальня». Promoting it behind the user's
     * back would keep the numbers while quietly changing what they describe.
     * So the user is told to deal with the children first, explicitly.
     */
    HAS_CHILDREN,

    /**
     * Профиль занимает РОЛЬ, в которую автоматика складывает решения контекста
     * («В пути», «Без места»).
     *
     * Запрет обязателен именно потому, что приложение восстанавливает такой
     * профиль при следующем запуске службы: разрешить удаление значило бы
     * молча отменять действие человека. Переименовать и заархивировать его
     * по-прежнему можно — роль от имени не зависит.
     */
    REQUIRED_ROLE,
}

/** Verdict of the pure profile-deletion guard. */
sealed interface ProfileDeletion {

    /** Deletion may proceed; measurements detach, they are never deleted. */
    data class Allowed(val profile: ProfileEntity) : ProfileDeletion

    data class Blocked(
        val reason: ProfileDeletionBlock,
        /** Names of the nested profiles for [ProfileDeletionBlock.HAS_CHILDREN]. */
        val children: List<String> = emptyList(),
    ) : ProfileDeletion

    companion object {

        /**
         * Rules of «Настройки → Профили → Удалить профиль», pure so they are
         * decided once and tested on the JVM instead of being re-derived by
         * the screen.
         *
         * Archived profiles may always be deleted (they are not recorded into),
         * as long as they have no children.
         */
        fun evaluate(profiles: List<ProfileEntity>, profileId: Long): ProfileDeletion {
            val target = profiles.firstOrNull { it.id == profileId }
                ?: return Blocked(ProfileDeletionBlock.UNKNOWN)

            val children = profiles.filter { it.parentId == profileId }
            if (children.isNotEmpty()) {
                return Blocked(
                    reason = ProfileDeletionBlock.HAS_CHILDREN,
                    children = children.map { it.name },
                )
            }

            if (target.role == ProfileEntity.ROLE_TRANSIT ||
                target.role == ProfileEntity.ROLE_NO_PLACE
            ) {
                return Blocked(ProfileDeletionBlock.REQUIRED_ROLE)
            }

            val liveLeft = profiles.count { !it.archived && it.id != profileId }
            if (!target.archived && liveLeft == 0) {
                return Blocked(ProfileDeletionBlock.LAST_LIVE_PROFILE)
            }
            return Allowed(target)
        }

        /** One honest line naming the actual obstacle. */
        fun blockedWording(
            blocked: Blocked,
            s: MonitorStrings = MonitorRu,
        ): String = when (blocked.reason) {
            ProfileDeletionBlock.UNKNOWN -> s.deleteBlockedUnknown
            ProfileDeletionBlock.LAST_LIVE_PROFILE -> s.deleteBlockedLastLive
            ProfileDeletionBlock.HAS_CHILDREN ->
                s.deleteBlockedHasChildren(blocked.children.joinToString(", "))
            ProfileDeletionBlock.REQUIRED_ROLE -> s.deleteBlockedRequiredRole
        }

        /** Confirmation text: says out loud that measurements survive. */
        fun confirmWording(profileName: String, s: MonitorStrings = MonitorRu): String =
            s.deleteConfirm(profileName)
    }
}
