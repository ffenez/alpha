package app.alpha.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bridge for the measurement context, same shape as
 * `ServiceStatus`: [ContextController] writes, screens and repositories read.
 * Keeping it separate from the controller breaks the dependency cycle between
 * «who resolves the profile» and «who lists the profiles».
 */
class ContextHub {

    private val _state = MutableStateFlow<MeasurementContext>(MeasurementContext.NoContext)
    val state: StateFlow<MeasurementContext> = _state.asStateFlow()

    /** Profile the current state resolves to; null = none/unknown yet. */
    private val _activeProfileId = MutableStateFlow<Long?>(null)
    val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

    /** Currently observed Wi-Fi (hash + optional label) for Настройки. */
    private val _network = MutableStateFlow(NetworkSnapshot(null, null))
    val network: StateFlow<NetworkSnapshot> = _network.asStateFlow()

    internal fun publish(state: MeasurementContext, activeProfileId: Long?) {
        _state.value = state
        _activeProfileId.value = activeProfileId
    }

    internal fun publishNetwork(snapshot: NetworkSnapshot) {
        _network.value = snapshot
    }
}
