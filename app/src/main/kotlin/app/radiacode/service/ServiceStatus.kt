package app.radiacode.service

import app.radiacode.device.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bridge between [MeasurementService] and the UI: the service is
 * unbound (start-only), so live connection state is shared through the app
 * graph instead of a binder. The service writes, screens read.
 */
class ServiceStatus {

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    internal fun onServiceStarted() {
        _serviceRunning.value = true
    }

    internal fun onServiceStopped() {
        _serviceRunning.value = false
        _connection.value = ConnectionState.Disconnected
    }

    internal fun onConnectionState(state: ConnectionState) {
        _connection.value = state
    }
}
