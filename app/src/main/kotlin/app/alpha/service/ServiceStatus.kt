package app.alpha.service

import app.alpha.baseline.Admission
import app.alpha.baseline.BaselineState
import app.alpha.baseline.DeviationSnapshot
import app.alpha.device.ConnectionState
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

    /**
     * Когда графики Главной последний раз ПЕРЕЧИТАЛИСЬ и сколько раз всего.
     *
     * Существует ради разбора полевого случая «графики замерли при открытом
     * экране»: на экране застывшая картинка и работающая выглядят одинаково, а
     * по этим двум числам в отладочном отчёте видно, жив ли цикл обновления
     * вообще. К измерению отношения не имеет и в UI не показывается.
     */
    @Volatile
    var chartsRefreshedAtMillis: Long? = null
        private set

    @Volatile
    var chartsRefreshCount: Int = 0
        private set

    /** Измеренная поправка часов прибора, мс (0 = база не корректировалась). */
    @Volatile
    var deviceClockCorrectionMillis: Long = 0L
        private set

    fun onClockCorrection(millis: Long) {
        deviceClockCorrectionMillis = millis
    }

    fun onChartsRefreshed(atMillis: Long) {
        chartsRefreshedAtMillis = atMillis
        chartsRefreshCount += 1
    }

    /** Baseline of the active place, computed by the service; null = unknown yet. */
    /**
     * Последнее показание, ПРИШЕДШЕЕ с прибора, вместе с моментом прихода.
     *
     * ## Почему это отдельный источник, а не строка из базы
     *
     * Экран спрашивает «идут ли данные ПРЯМО СЕЙЧАС». Это факт о ПРИХОДЕ, а не
     * о том, что прибор думает о времени. Раньше свежесть считалась как
     * `сейчас − метка записи`, а метка стоит на базе времени прибора, которая
     * ИЗМЕРЯЕТСЯ по ходу сеанса и может уехать, и приходила она из базы, где
     * строку мог молча отбросить уникальный индекс. Двух независимых поводов
     * ошибиться хватало, чтобы приложение говорило «нет новых данных · 29 с»,
     * пока записи исправно приходили каждую секунду.
     *
     * Здесь между прибором и экраном нет ни базы, ни часов прибора: показание
     * кладётся в память сразу после разбора ответа. Метка прибора
     * ([deviceTimestampMillis]) остаётся — она отвечает на другой вопрос,
     * «когда измерено», и по ней строятся графики.
     */
    data class LiveSample(
        val deviceTimestampMillis: Long,
        /** Часы телефона в момент разбора ответа — единственный источник свежести. */
        val receivedAtMillis: Long,
        /** Сырые единицы прибора, как пришли. */
        val doseRate: Float,
        val doseRateErr: Float,
        val countRate: Float,
        val countRateErr: Float,
    )

    private val _lastSample = MutableStateFlow<LiveSample?>(null)
    val lastSample: StateFlow<LiveSample?> = _lastSample.asStateFlow()

    internal fun onSample(sample: LiveSample) {
        _lastSample.value = sample
    }

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
    /**
     * Идущая запись маршрута.
     *
     * [sessionId] пуст, пока не пришла ни одна координата: до первой точки
     * записывать нечего, и маршрут не заводится в журнале вовсе. Иначе каждый
     * случайный старт оставлял бы в Истории пустую запись «идёт запись».
     */
    data class TrackRecording(val sessionId: Long?, val startedAt: Long)

    private val _trackRecording = MutableStateFlow<TrackRecording?>(null)
    val trackRecording: StateFlow<TrackRecording?> = _trackRecording.asStateFlow()

    /**
     * Почему в следе ещё нет точек.
     *
     * Полевой отчёт: «Жду первые точки» не пропадало никогда. Ждать можно
     * по-разному — можно ждать спутников, а можно ждать разрешения, которого
     * никто не даст, и от бесконечного ожидания второе неотличимо. Экран
     * обязан назвать причину, а не молчать с многоточием.
     */
    enum class TrackLocation {
        /** Идёт запись, фиксов ещё не было — обычное ожидание спутников. */
        WAITING,

        /** Разрешения на местоположение нет: ждать нечего. */
        NO_PERMISSION,

        /** Ни один источник координат не включён в системе. */
        NO_PROVIDER,

        /** Точки приходят. */
        RECEIVING,
    }

    private val _trackLocation = MutableStateFlow(TrackLocation.WAITING)
    val trackLocation: StateFlow<TrackLocation> = _trackLocation.asStateFlow()

    /**
     * Что произошло с координатами с начала записи — для отчёта.
     *
     * «След не пишется» на чужом устройстве неразбираемо: непонятно, дошла ли
     * подписка до системы, приходят ли фиксы вообще и доезжают ли они до базы.
     * Три числа отвечают на все три вопроса сразу.
     */
    data class TrackDiagnostics(
        /** Провайдеры, на которые удалось подписаться. */
        val providers: List<String> = emptyList(),
        /** Провайдеры, включённые в системе на момент подписки. */
        val enabled: List<String> = emptyList(),
        /** Сколько фиксов пришло от системы. */
        val fixes: Int = 0,
        /** Сколько точек записано в базу (часть фиксов отбрасывается как дубли). */
        val points: Int = 0,
        /** Когда пришёл последний фикс и от кого. */
        val lastFixMillis: Long? = null,
        val lastProvider: String? = null,
        val lastAccuracyMeters: Float? = null,
        /** Точное разрешение (FINE) или только приблизительное (COARSE). */
        val precise: Boolean = false,
    )

    private val _trackDiagnostics = MutableStateFlow(TrackDiagnostics())
    val trackDiagnostics: StateFlow<TrackDiagnostics> = _trackDiagnostics.asStateFlow()

    internal fun onTrackSubscribed(
        providers: List<String>,
        enabled: List<String>,
        precise: Boolean,
    ) {
        _trackDiagnostics.value = TrackDiagnostics(
            providers = providers,
            enabled = enabled,
            precise = precise,
        )
    }

    internal fun onTrackFix(provider: String?, accuracyMeters: Float, atMillis: Long, stored: Boolean) {
        val current = _trackDiagnostics.value
        _trackDiagnostics.value = current.copy(
            fixes = current.fixes + 1,
            points = current.points + if (stored) 1 else 0,
            lastFixMillis = atMillis,
            lastProvider = provider,
            lastAccuracyMeters = accuracyMeters,
        )
    }

    internal fun onServiceStarted() {
        _serviceRunning.value = true
        serviceStartedAtMillis = System.currentTimeMillis()
        spectrumRequests = 0L
        spectrumPayloadBytes = 0L
    }

    internal fun onServiceStopped() {
        _serviceRunning.value = false
        serviceStartedAtMillis = 0L
        _connection.value = ConnectionState.Disconnected
        _deviation.value = DeviationSnapshot()
        _trackRecording.value = null
    }

    internal fun onTrackRecording(recording: TrackRecording?) {
        _trackRecording.value = recording
        if (recording == null) _trackLocation.value = TrackLocation.WAITING
    }

    internal fun onTrackLocation(state: TrackLocation) {
        _trackLocation.value = state
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

    /**
     * Цена частоты опроса спектра, ИЗМЕРЕННАЯ, а не предсказанная (ADR 007):
     * сколько запросов сделано, сколько байт ответов принято и сколько времени
     * работает служба. Из этих трёх чисел получаются «запросов в час» и «байт в
     * час» — факты, на которые можно опереться, прежде чем говорить что-либо
     * про батарею.
     */
    @Volatile
    var spectrumRequests: Long = 0L
        internal set

    @Volatile
    var spectrumPayloadBytes: Long = 0L
        internal set

    /** Когда служба поднялась; 0 — не работает. */
    @Volatile
    var serviceStartedAtMillis: Long = 0L
        internal set

    internal fun onSpectrumRead(payloadBytes: Long) {
        spectrumRequests += 1
        if (payloadBytes > 0L) spectrumPayloadBytes += payloadBytes
    }

    /**
     * Когда связь установилась — момент прибора, а не экрана.
     *
     * Полевой дефект: возврат из Настроек на Главную показывал «Подключено»
     * заново. Подтверждение жило в композиции и появлялось при каждой её
     * сборке, то есть сообщало о том, что человек открыл экран, а не о том,
     * что прибор подключился.
     */
    private val _connectedAtMillis = MutableStateFlow<Long?>(null)
    val connectedAtMillis: StateFlow<Long?> = _connectedAtMillis.asStateFlow()

    internal fun onConnectionState(state: ConnectionState) {
        val was = _connection.value
        _connection.value = state
        // Момент ставится на ПЕРЕХОДЕ в подключённое состояние: повторное
        // сообщение о том же соединении подтверждением не является.
        if (state is ConnectionState.Connected && was !is ConnectionState.Connected) {
            _connectedAtMillis.value = System.currentTimeMillis()
        }
        if (state !is ConnectionState.Connected) _connectedAtMillis.value = null
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
        /** Поиск screen (SearchPresenceHub watcher count). */
        const val SOURCE_SEARCH = "search"

        /** A/B experiment run in progress. */
        const val SOURCE_AB = "ab_experiment"
    }
}
