package app.alpha.context

/**
 * Active measurement context (spec §3.4). Pure data — the machine that
 * produces it is [ContextMachine].
 */
sealed interface MeasurementContext {

    /** A known Wi-Fi confidently maps to a profile. */
    data class AutoKnown(val profileId: Long) : MeasurementContext

    /**
     * The known network just went away (or an unknown one appeared) and the
     * grace period has not expired yet. The previous profile stays active for
     * recording, but its baseline is frozen — this is exactly the window where
     * «I left home» and «the router rebooted» look identical.
     */
    data class AutoUncertain(
        val previousProfileId: Long,
        val sinceMillis: Long,
        val pending: Pending,
    ) : MeasurementContext

    /** No known network after the grace period — «В пути». */
    data object AutoTransit : MeasurementContext

    /** The context cannot be determined — «Без места». */
    data object NoContext : MeasurementContext

    /** The user picked a profile explicitly; Wi-Fi no longer overrides it. */
    data class Manual(val profileId: Long) : MeasurementContext

    /** What [AutoUncertain] resolves to once the grace period expires. */
    enum class Pending { TRANSIT, NO_CONTEXT }

    /**
     * Condition 2 of the baseline admission pipeline (spec §4.2). Uncertain
     * is the only unreliable state: `В пути` and `Без места` are ordinary
     * profiles with their own honest statistics, and a manual choice is the
     * most reliable signal there is.
     */
    val isReliable: Boolean
        get() = this !is AutoUncertain

    /** True while the user's explicit choice is in force (spec §3.2). */
    val isManual: Boolean
        get() = this is Manual
}

/** Input events of [ContextMachine]. */
sealed interface ContextEvent {

    /**
     * The observed Wi-Fi identity changed. [hash] is
     * [NetworkIdentity.of] of the current network, or null when there is no
     * Wi-Fi link at all.
     */
    data class Network(val hash: String?, val atMillis: Long) : ContextEvent

    /** Wall-clock tick; the only thing that can end a grace period. */
    data class Tick(val atMillis: Long) : ContextEvent

    /** «Место измерения» picker: an explicit choice. */
    data class SelectManually(val profileId: Long) : ContextEvent

    /** «Вернуться к авто»: re-resolve immediately from the current network. */
    data class ReturnToAuto(val hash: String?, val atMillis: Long) : ContextEvent
}

/**
 * Grace period before an automatically detected profile is given up.
 *
 * Default 3 minutes: long enough to survive a router reboot, a lift ride or a
 * few minutes in the corridor without throwing away the home baseline, short
 * enough that a real departure is recognised before a walk produces a
 * meaningful amount of «home» statistics. The value is user-configurable
 * (Настройки → Профили) because flats, offices and country houses differ.
 */
data class ContextConfig(val graceMillis: Long = DEFAULT_GRACE_MILLIS) {
    companion object {
        const val DEFAULT_GRACE_MILLIS = 3L * 60_000L
        val ALLOWED_GRACE_MILLIS = listOf(60_000L, 2 * 60_000L, 3 * 60_000L, 5 * 60_000L)
    }
}

/**
 * Wi-Fi driven context state machine (spec §3.2, §3.4).
 *
 * **Why Wi-Fi and not GPS.** A phone knows it is at home the moment it
 * associates with the home network; keeping a GPS fix alive for the same
 * answer costs battery and location privacy for nothing (spec §3.3, §23). The
 * network is identified by [NetworkIdentity] — a local hash of the gateway
 * address — so no location permission is involved either.
 *
 * **Rules.**
 *  - A known network wins immediately: a confident signal never waits.
 *  - Losing the known network starts the grace period; during it the previous
 *    profile keeps recording but its baseline is frozen
 *    ([MeasurementContext.isReliable] = false).
 *  - After the grace period: no link at all → `AUTO_TRANSIT` («В пути»);
 *    an unknown network → `NO_CONTEXT` («Без места») — being on someone
 *    else's Wi-Fi says nothing about where the dosimeter is.
 *  - A manual choice sticks through every network event until the user asks
 *    to go back to automatic (spec §3.2). `Вернуться к авто` resolves at once,
 *    without a grace period — the user is telling us the situation is stable.
 *
 * The machine is pure: the same (state, event, bindings) always gives the same
 * result, which is what makes the transition table testable on the JVM.
 */
object ContextMachine {

    /**
     * @param bindings network hash → profile id, already filtered to profiles
     *        that are not archived and have `autoActivate` on.
     */
    fun reduce(
        state: MeasurementContext,
        event: ContextEvent,
        bindings: Map<String, Long>,
        config: ContextConfig = ContextConfig(),
    ): MeasurementContext = when (event) {
        is ContextEvent.SelectManually -> MeasurementContext.Manual(event.profileId)

        is ContextEvent.ReturnToAuto -> resolveNow(event.hash, bindings)

        is ContextEvent.Network -> {
            if (state.isManual) state else onNetwork(state, event, bindings)
        }

        is ContextEvent.Tick -> {
            if (state is MeasurementContext.AutoUncertain &&
                event.atMillis - state.sinceMillis >= config.graceMillis
            ) {
                when (state.pending) {
                    MeasurementContext.Pending.TRANSIT -> MeasurementContext.AutoTransit
                    MeasurementContext.Pending.NO_CONTEXT -> MeasurementContext.NoContext
                }
            } else {
                state
            }
        }
    }

    private fun onNetwork(
        state: MeasurementContext,
        event: ContextEvent.Network,
        bindings: Map<String, Long>,
    ): MeasurementContext {
        val known = event.hash?.let { bindings[it] }
        if (known != null) return MeasurementContext.AutoKnown(known)

        val pending = if (event.hash == null) {
            MeasurementContext.Pending.TRANSIT
        } else {
            MeasurementContext.Pending.NO_CONTEXT
        }
        return when (state) {
            is MeasurementContext.AutoKnown -> MeasurementContext.AutoUncertain(
                previousProfileId = state.profileId,
                sinceMillis = event.atMillis,
                pending = pending,
            )
            // The grace period is measured from losing the known network, so a
            // later change between «no link» and «foreign link» updates where
            // it will land but never restarts the countdown.
            is MeasurementContext.AutoUncertain -> state.copy(pending = pending)
            else -> when (pending) {
                MeasurementContext.Pending.TRANSIT -> MeasurementContext.AutoTransit
                MeasurementContext.Pending.NO_CONTEXT -> MeasurementContext.NoContext
            }
        }
    }

    private fun resolveNow(hash: String?, bindings: Map<String, Long>): MeasurementContext {
        val known = hash?.let { bindings[it] }
        return when {
            known != null -> MeasurementContext.AutoKnown(known)
            hash == null -> MeasurementContext.AutoTransit
            else -> MeasurementContext.NoContext
        }
    }

    /**
     * Profile that should be recording in this state. `AUTO_TRANSIT` and
     * `NO_CONTEXT` fall back to the special profiles
     * ([app.alpha.data.db.ProfileEntity.ROLE_TRANSIT] /
     * `ROLE_NO_PLACE`); a null there simply means the user deleted them, and
     * measurements are then stored without a profile.
     */
    fun activeProfileId(
        state: MeasurementContext,
        transitProfileId: Long?,
        noPlaceProfileId: Long?,
    ): Long? = when (state) {
        is MeasurementContext.AutoKnown -> state.profileId
        is MeasurementContext.Manual -> state.profileId
        is MeasurementContext.AutoUncertain -> state.previousProfileId
        MeasurementContext.AutoTransit -> transitProfileId
        MeasurementContext.NoContext -> noPlaceProfileId
    }
}
