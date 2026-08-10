package app.radiacode.context

import app.radiacode.data.AppSettings
import app.radiacode.data.db.ProfileDao
import app.radiacode.data.db.ProfileEntity
import app.radiacode.ui.logic.ProfileTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the pure [ContextMachine] from the live world: the Wi-Fi observer,
 * the profile table with its bindings and the user's manual choice. Owned by
 * `MeasurementService` — automatic context only matters while something is
 * being measured — and published through [ContextHub].
 *
 * GPS is not involved anywhere in this path (spec §3.3): the only location
 * subscription in the app is the explicit map recording in `MeasurementService`.
 */
class ContextController(
    private val wifi: WifiNetworkSource,
    private val profileDao: ProfileDao,
    private val settings: AppSettings,
    private val hub: ContextHub,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()
    private var state: MeasurementContext = MeasurementContext.NoContext
    private var config = ContextConfig()
    private var jobs = mutableListOf<Job>()

    /** Last world snapshot, so the grace tick can resolve without re-querying. */
    private var lastInputs: Inputs? = null

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        wifi.start()
        jobs += scope.launch {
            combine(
                wifi.snapshot,
                profileDao.observeAll(),
                profileDao.observeNetworks(),
                settings.contextManual,
                settings.activeProfileId,
            ) { snapshot, profiles, networks, manual, storedId ->
                Inputs(
                    snapshot = snapshot,
                    profiles = profiles,
                    bindings = ProfileTree.autoBindings(
                        profiles,
                        networks.map { it.networkHash to it.profileId },
                    ),
                    manual = manual,
                    storedProfileId = storedId,
                )
            }.collect { inputs ->
                hub.publishNetwork(inputs.snapshot)
                apply(inputs)
            }
        }
        jobs += scope.launch {
            settings.contextGraceMillis.collect { millis ->
                synchronized(lock) { config = ContextConfig(millis) }
            }
        }
        jobs += scope.launch {
            // The grace period is the only time-driven transition; a 15 s tick
            // resolves a 1–5 min window with more than enough precision.
            while (true) {
                delay(TICK_MILLIS)
                tick()
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        wifi.stop()
    }

    private data class Inputs(
        val snapshot: NetworkSnapshot,
        val profiles: List<ProfileEntity>,
        val bindings: Map<String, Long>,
        val manual: Boolean,
        val storedProfileId: Long?,
    )

    private fun apply(inputs: Inputs) {
        val now = clock()
        synchronized(lock) {
            lastInputs = inputs
            var next = state
            // The manual pin lives in settings so it survives a service
            // restart; the machine is told about it as an ordinary event.
            if (inputs.manual && inputs.storedProfileId != null) {
                if (next != MeasurementContext.Manual(inputs.storedProfileId)) {
                    next = ContextMachine.reduce(
                        next,
                        ContextEvent.SelectManually(inputs.storedProfileId),
                        inputs.bindings,
                        config,
                    )
                }
            } else if (!inputs.manual && next.isManual) {
                next = ContextMachine.reduce(
                    next,
                    ContextEvent.ReturnToAuto(inputs.snapshot.hash, now),
                    inputs.bindings,
                    config,
                )
            }
            next = ContextMachine.reduce(
                next,
                ContextEvent.Network(inputs.snapshot.hash, now),
                inputs.bindings,
                config,
            )
            state = next
            publish(next, inputs.profiles, inputs.storedProfileId)
        }
    }

    private fun tick() {
        val now = clock()
        synchronized(lock) {
            val next = ContextMachine.reduce(state, ContextEvent.Tick(now), emptyMap(), config)
            if (next == state) return
            state = next
            val inputs = lastInputs ?: return
            publish(next, inputs.profiles, inputs.storedProfileId)
        }
    }

    private fun publish(
        state: MeasurementContext,
        profiles: List<ProfileEntity>,
        storedProfileId: Long?,
    ) {
        val resolved = ContextMachine.activeProfileId(
            state = state,
            transitProfileId = profiles.firstOrNull {
                it.role == ProfileEntity.ROLE_TRANSIT && !it.archived
            }?.id,
            noPlaceProfileId = profiles.firstOrNull {
                it.role == ProfileEntity.ROLE_NO_PLACE && !it.archived
            }?.id,
        )
        hub.publish(state, ProfileTree.resolveActive(profiles, resolved, storedProfileId)?.id)
    }

    companion object {
        private const val TICK_MILLIS = 15_000L
    }
}
