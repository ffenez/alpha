package app.radiacode.device

import app.radiacode.protocol.DataBufRecord
import app.radiacode.protocol.Event
import app.radiacode.protocol.RareData
import app.radiacode.protocol.RealTimeData
import app.radiacode.protocol.Spectrum
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Internal marker: the BLE link dropped while a session was active. */
private class LinkLostException : IOException("BLE link lost")

/**
 * High-level RadiaCode device: owns the connect/init/poll/reconnect loop for
 * one device address and exposes decoded data as flows.
 *
 * Lifecycle: [start] launches the loop into the given scope (the foreground
 * service's scope); [stop] cancels it and returns to [ConnectionState.Disconnected].
 * Reconnects use [BackoffPolicy] (2 s -> 60 s exponential), reset after every
 * successful init.
 */
class RadiaCodeDevice(
    val address: String,
    private val linkFactory: DeviceLinkFactory,
    private val clock: () -> Long = System::currentTimeMillis,
    private val backoff: BackoffPolicy = BackoffPolicy(),
    private val timeouts: Timeouts = Timeouts(),
    private val pollIntervalMillis: Long = 1_000,
) {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _realTimeData = MutableSharedFlow<RealTimeData>(replay = 1, extraBufferCapacity = 64)
    /** ~1 Hz measurement stream while connected; replays the latest value. */
    val realTimeData: SharedFlow<RealTimeData> = _realTimeData.asSharedFlow()

    private val _rareData = MutableSharedFlow<RareData>(replay = 1, extraBufferCapacity = 16)
    /** Battery / temperature / accumulated dose, emitted every few minutes. */
    val rareData: SharedFlow<RareData> = _rareData.asSharedFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    /** Device-side events (alarms, power, resets). */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _records = MutableSharedFlow<List<DataBufRecord>>(extraBufferCapacity = 64)
    /** Every decoded DATA_BUF batch, for persistence. */
    val records: SharedFlow<List<DataBufRecord>> = _records.asSharedFlow()

    /** Cumulative DATA_BUF sequence gaps this device object observed (diagnostics). */
    @Volatile
    var seqGapTotal: Int = 0
        private set

    @Volatile
    private var connection: DeviceConnection? = null
    private var job: Job? = null
    private var sessionEstablished = false

    val deviceInfo: DeviceInfo?
        get() = connection?.info

    /** Idempotent; the loop runs until [stop] or scope cancellation. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        connection = null
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun readSpectrum(): Spectrum = requireConnection().readSpectrum()

    suspend fun readAccumSpectrum(): Spectrum = requireConnection().readAccumSpectrum()

    suspend fun resetSpectrum() = requireConnection().resetSpectrum()

    suspend fun resetDose() = requireConnection().resetDose()

    private fun requireConnection(): DeviceConnection =
        connection ?: throw DeviceNotConnectedException()

    private suspend fun runLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            attempt += 1
            _connectionState.value = ConnectionState.Connecting(attempt)
            var link: DeviceLink? = null
            try {
                link = linkFactory.open(address)
                runSession(link)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Connection attempt or live session failed; fall through to backoff.
            } finally {
                connection = null
                link?.let { runCatching { it.close() } }
            }
            if (sessionEstablished) {
                // The failed streak ended with a live session; restart the attempt count.
                attempt = 0
                sessionEstablished = false
            }
            if (!currentCoroutineContext().isActive) break
            val delayMillis = backoff.nextDelayMillis()
            _connectionState.value = ConnectionState.Reconnecting(attempt, delayMillis)
            delay(delayMillis)
        }
    }

    /** Runs init + poll on an open link; returns only by throwing (link loss/protocol error). */
    private suspend fun runSession(link: DeviceLink): Unit = coroutineScope {
        val client = ProtocolClient(link, timeouts)
        launch { link.notifications.collect { client.onNotification(it) } }
        launch { link.awaitDisconnect(); throw LinkLostException() }

        val conn = DeviceConnection.establish(client, address, clock)
        connection = conn
        _connectionState.value = ConnectionState.Connected(conn.info)
        backoff.reset()
        sessionEstablished = true

        while (true) {
            val startedAt = clock()
            val result = conn.readDataBuf()
            if (result.seqGaps > 0) seqGapTotal += result.seqGaps
            dispatch(result.records)
            val elapsed = clock() - startedAt
            delay((pollIntervalMillis - elapsed).coerceAtLeast(0))
        }
    }

    private suspend fun dispatch(records: List<DataBufRecord>) {
        if (records.isEmpty()) return
        _records.emit(records)
        for (record in records) {
            when (record) {
                is RealTimeData -> _realTimeData.emit(record)
                is RareData -> _rareData.emit(record)
                is Event -> _events.emit(record)
                else -> Unit
            }
        }
    }
}
