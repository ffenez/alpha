package app.alpha.device

import app.alpha.protocol.DataBufRecord
import app.alpha.protocol.Event
import app.alpha.protocol.ProtocolException
import app.alpha.protocol.RareData
import app.alpha.protocol.RealTimeData
import app.alpha.protocol.Spectrum
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
    pollIntervalMillis: Long = 1_000,
) {

    /**
     * DATA_BUF poll period. Adjustable at runtime (the Поиск screen asks for
     * a shorter one through `FastPollHub`) — the loop reads it once per tick,
     * so a change applies from the next tick without touching the session.
     */
    @Volatile
    var pollIntervalMillis: Long = pollIntervalMillis
        set(value) {
            field = value.coerceAtLeast(MIN_POLL_INTERVAL_MILLIS)
        }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Причина последнего неудавшегося подключения, одной строкой.
     *
     * Нужна ровно для одного: разобрать «не подключается» на приборе, которого
     * у нас нет. Уходит в отладочный отчёт; сообщение исключения не содержит
     * ни координат, ни измерений.
     */
    @Volatile
    var lastFailure: String? = null
        private set

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

    /**
     * Осечки чтения DATA_BUF за всё время жизни объекта — счётчик для
     * отладочного отчёта. Пережитая осечка не видна на экране (она и не должна
     * быть видна), но именно по этому числу разбирается «показания идут рывками».
     */
    @Volatile
    var readFailures: Int = 0
        private set

    /** Cumulative DATA_BUF sequence gaps this device object observed (diagnostics). */
    @Volatile
    var seqGapTotal: Int = 0
        private set

    @Volatile
    private var connection: DeviceConnection? = null

    /** Измеренная поправка часов прибора текущей сессии (см. [DeviceConnection]). */
    val clockCorrectionMillis: Long get() = connection?.clockCorrectionMillis ?: 0L
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

    /**
     * Объём ответов со спектром за всё время жизни объекта, байт — счётчик
     * эфира для отладочного отчёта (ADR 007). Переподключение его не сбрасывает.
     */
    @Volatile
    var spectrumPayloadBytes: Long = 0L
        private set

    suspend fun readSpectrum(): Spectrum {
        val connection = requireConnection()
        val before = connection.spectrumPayloadBytes
        val spectrum = connection.readSpectrum()
        spectrumPayloadBytes += connection.spectrumPayloadBytes - before
        return spectrum
    }

    suspend fun readAccumSpectrum(): Spectrum = requireConnection().readAccumSpectrum()

    suspend fun resetSpectrum() = requireConnection().resetSpectrum()

    suspend fun resetDose() = requireConnection().resetDose()

    /** Звук/вибрация САМОГО прибора — они работают и без телефона. */
    suspend fun setDeviceSoundOn(on: Boolean) = requireConnection().setDeviceSoundOn(on)

    suspend fun setDeviceVibroOn(on: Boolean) = requireConnection().setDeviceVibroOn(on)

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
            } catch (e: Exception) {
                // Connection attempt or live session failed; fall through to
                // backoff. Причина запоминается ОДНОЙ строкой: без неё
                // «не подключается» на чужом приборе неразбираемо, а полный
                // стек в лог писать некуда — логов в поле нет.
                lastFailure = "${e::class.simpleName}: ${e.message.orEmpty()}".trim(':', ' ')
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

        // Осечка одного чтения — не потеря связи. Прибор занят своим экраном
        // (человек нажимает на нём кнопки), эфир мешает, ответ опоздал — всё
        // это лечится следующим тиком, а разрыв сессии стоит переподключения
        // с паузой и разрывом в записи. Сессию заканчивает только связь: либо
        // сам линк (awaitDisconnect), либо череда осечек подряд.
        var consecutiveFailures = 0
        while (true) {
            val startedAt = clock()
            try {
                val result = conn.readDataBuf()
                if (result.seqGaps > 0) seqGapTotal += result.seqGaps
                dispatch(result.records)
                consecutiveFailures = 0
            } catch (e: DeviceTimeoutException) {
                consecutiveFailures = noteReadFailure(e, consecutiveFailures)
            } catch (e: ProtocolException) {
                consecutiveFailures = noteReadFailure(e, consecutiveFailures)
            }
            // Strictly sequential: the next read starts only after this one
            // returned, so a faster cadence cannot pile requests up. An empty
            // reply (no new records yet) is normal and costs one round trip.
            val elapsed = clock() - startedAt
            delay((pollIntervalMillis - elapsed).coerceAtLeast(0))
        }
    }

    /**
     * Считает осечку чтения и решает, жива ли ещё сессия.
     * @return число осечек подряд; [MAX_CONSECUTIVE_READ_FAILURES] подряд
     *   означают, что прибор перестал отвечать, и сессию пора пересобрать.
     */
    private fun noteReadFailure(e: Exception, soFar: Int): Int {
        val failures = soFar + 1
        readFailures += 1
        lastFailure = "${e::class.simpleName}: ${e.message.orEmpty()}".trim(':', ' ')
        if (failures >= MAX_CONSECUTIVE_READ_FAILURES) throw e
        return failures
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

    companion object {
        /**
         * Floor for [pollIntervalMillis]. The device produces ~1 record per
         * second; polling faster than this only burns radio time and battery
         * without lowering the pickup delay any further.
         */
        const val MIN_POLL_INTERVAL_MILLIS = 250L

        /**
         * Сколько чтений подряд должны сорваться, чтобы пересобрать сессию.
         * **Инженерный параметр**: пять при опросе 1–4 раза в секунду — это
         * секунды терпения, дольше любой занятости прибора и заметно короче
         * паузы, после которой человек сам поймёт, что связи нет.
         */
        const val MAX_CONSECUTIVE_READ_FAILURES = 5
    }
}
