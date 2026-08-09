package app.radiacode.service

import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.DeviationSnapshot
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

    /** Baseline of the active place, computed by the service; null = unknown yet. */
    private val _baseline = MutableStateFlow<BaselineState?>(null)
    val baseline: StateFlow<BaselineState?> = _baseline.asStateFlow()

    /** Live deviation picture from the alarm engine (single source of truth). */
    private val _deviation = MutableStateFlow(DeviationSnapshot())
    val deviation: StateFlow<DeviationSnapshot> = _deviation.asStateFlow()

    internal fun onServiceStarted() {
        _serviceRunning.value = true
    }

    internal fun onServiceStopped() {
        _serviceRunning.value = false
        _connection.value = ConnectionState.Disconnected
        _deviation.value = DeviationSnapshot()
    }

    internal fun onConnectionState(state: ConnectionState) {
        _connection.value = state
    }

    internal fun onBaseline(state: BaselineState?) {
        _baseline.value = state
    }

    internal fun onDeviation(snapshot: DeviationSnapshot) {
        _deviation.value = snapshot
    }
}
