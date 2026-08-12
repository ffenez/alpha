package app.radiacode.service

import app.radiacode.baseline.Admission
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

    /**
     * Live baseline-admission verdict for the current sample (spec §4.2).
     * The Монитор shows it as a single honest line and «Почему?» expands it.
     */
    private val _admission = MutableStateFlow<Admission>(Admission.Admitted)
    val admission: StateFlow<Admission> = _admission.asStateFlow()

    /**
     * Name of the running experiment («Поиск», «A/B: прогон A») or null.
     * Condition 4 of the admission pipeline: an experiment must never teach
     * the baseline (spec §4.2, §18).
     *
     * Several sources can declare an experiment at once (Поиск on screen while
     * an A/B run records), so declarations are keyed by source and the flag
     * clears only when the last one is withdrawn — one source stopping must not
     * silently re-enable baseline learning under another.
     */
    private val _experiment = MutableStateFlow<String?>(null)
    val experiment: StateFlow<String?> = _experiment.asStateFlow()

    private val experimentSources = LinkedHashMap<String, String>()

    /** Active track recording; null = not recording. */
    data class TrackRecording(val sessionId: Long, val startedAt: Long)

    private val _trackRecording = MutableStateFlow<TrackRecording?>(null)
    val trackRecording: StateFlow<TrackRecording?> = _trackRecording.asStateFlow()

    internal fun onServiceStarted() {
        _serviceRunning.value = true
    }

    internal fun onServiceStopped() {
        _serviceRunning.value = false
        _connection.value = ConnectionState.Disconnected
        _deviation.value = DeviationSnapshot()
        _trackRecording.value = null
    }

    internal fun onTrackRecording(recording: TrackRecording?) {
        _trackRecording.value = recording
    }

    /**
     * Причина последнего неудавшегося подключения — только для отладочного
     * отчёта. На экране её нет: человеку нужен статус, а не имя исключения.
     */
    @Volatile
    var lastConnectionFailure: String? = null
        internal set

    /**
     * Здоровье потока для отладочного отчёта: пропуски seq в DATA_BUF и число
     * переподключений. Оба числа объясняют «показания идут рывками» на ЛЮБОМ
     * приборе, включая свой, — на экране им места нет, а в отчёте они первое,
     * что нужно посмотреть.
     */
    @Volatile
    var seqGapTotal: Int = 0
        internal set

    @Volatile
    var reconnectCount: Int = 0
        internal set

    internal fun onConnectionState(state: ConnectionState) {
        _connection.value = state
    }

    internal fun onBaseline(state: BaselineState?) {
        _baseline.value = state
    }

    internal fun onDeviation(snapshot: DeviationSnapshot) {
        _deviation.value = snapshot
    }

    internal fun onAdmission(admission: Admission) {
        _admission.value = admission
    }

    /**
     * Declares (or withdraws, with a null [name]) a running experiment for one
     * [source]. Called by the service for Поиск ([SOURCE_SEARCH]) and by the
     * A/B screen for a recording run ([SOURCE_AB]).
     */
    fun onExperiment(source: String, name: String?) {
        synchronized(experimentSources) {
            if (name == null) experimentSources.remove(source) else experimentSources[source] = name
            _experiment.value = experimentSources.values.firstOrNull()
        }
    }

    companion object {
        /** Поиск screen (FastPollHub watcher count). */
        const val SOURCE_SEARCH = "search"

        /** A/B experiment run in progress. */
        const val SOURCE_AB = "ab_experiment"
    }
}
