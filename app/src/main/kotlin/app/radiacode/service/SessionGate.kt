package app.radiacode.service

/**
 * Pure decision logic for measurement-session boundaries (SPEC «History»:
 * sessions are continuous measurement periods).
 *
 * A session opens on device connect and closes on disconnect/stop. Brief BLE
 * hiccups (Reconnecting shorter than [graceMillis]) do not split a session —
 * the measurement period is still continuous in intent; a reconnect after a
 * longer outage closes the stale session at the last real sample and opens a
 * new one.
 */
class SessionGate(private val graceMillis: Long = DEFAULT_GRACE_MILLIS) {

    sealed interface Action {
        data object None : Action

        /** Open a new session now. */
        data object Open : Action

        /** Close the stale session at [closeAt], then open a new one. */
        data class Reopen(val closeAt: Long) : Action

        /** Close the current session at [closeAt]. */
        data class Close(val closeAt: Long) : Action
    }

    private var open = false
    private var lostAtMillis: Long? = null

    /** [lastSampleAt] = newest recorded sample, the honest close timestamp. */
    fun onConnected(nowMillis: Long, lastSampleAt: Long?): Action {
        val lostAt = lostAtMillis
        lostAtMillis = null
        return when {
            !open -> {
                open = true
                Action.Open
            }
            lostAt != null && nowMillis - lostAt > graceMillis ->
                Action.Reopen(closeAt = lastSampleAt ?: lostAt)
            else -> Action.None
        }
    }

    /** Link lost, reconnect attempts running. */
    fun onLinkLost(nowMillis: Long): Action {
        if (open && lostAtMillis == null) lostAtMillis = nowMillis
        return Action.None
    }

    /** Deliberate stop or terminal disconnect. */
    fun onDisconnected(nowMillis: Long, lastSampleAt: Long?): Action {
        lostAtMillis = null
        if (!open) return Action.None
        open = false
        return Action.Close(closeAt = lastSampleAt ?: nowMillis)
    }

    companion object {
        const val DEFAULT_GRACE_MILLIS = 5L * 60_000L
    }
}
