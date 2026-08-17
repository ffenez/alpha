package app.radiacode.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.radiacode.AppGraph
import app.radiacode.MainActivity
import app.radiacode.R
import app.radiacode.baseline.ABOVE_USUAL_MIN_DWELL_SECONDS
import app.radiacode.baseline.Admission
import app.radiacode.baseline.AdmissionInput
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.BaselineAdmission
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.QuarantineWindow
import app.radiacode.baseline.DeviationSnapshot
import app.radiacode.baseline.PersistenceTracker
import app.radiacode.baseline.aboveUsualMagnitude
import app.radiacode.baseline.alarmThresholds
import app.radiacode.baseline.deviationMagnitude
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.RawRetention
import app.radiacode.data.SpectrumPollPolicy
import app.radiacode.ui.text.AppLanguage
import app.radiacode.ui.text.NotificationCatalogue
import app.radiacode.ui.text.NotificationEn
import app.radiacode.ui.text.NotificationRu
import app.radiacode.ui.text.NotificationStrings
import app.radiacode.ui.text.stringsFor
import app.radiacode.analysis.SpectrumEpoch
import app.radiacode.protocol.Spectrum
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.device.ConnectionState
import app.radiacode.device.DoseUnits
import app.radiacode.device.RadiaCodeDevice
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.protocol.RealTimeData
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that owns the BLE connection and persists everything the
 * device reports. The system notification is the only UI; screens come later.
 *
 * Modes:
 *  - measuring (always while running): 1 Hz samples, rare data and device
 *    events go to Room;
 *  - track recording (opt-in): GPS points (LocationManager, no GMS) joined
 *    with the latest dose rate, plus automatic hotspot events above the
 *    configured dose-rate threshold.
 *
 * The caller (UI) is responsible for having BLUETOOTH_CONNECT — and, for
 * tracking, ACCESS_FINE_LOCATION — granted before starting.
 */
class MeasurementService : Service() {

    private lateinit var scope: CoroutineScope
    private lateinit var graph: AppGraph

    private var device: RadiaCodeDevice? = null
    private val deviceJobs = mutableListOf<Job>()

    private var trackSessionId: Long? = null

    /**
     * Идёт ли запись маршрута ПРЯМО СЕЙЧАС. Отдельно от `trackSessionId`,
     * потому что между нажатием и первой координатой строки в журнале ещё
     * нет, а запись уже идёт — и второй старт в этот момент обязан быть
     * отброшен.
     */
    private var tracking = false
    private var trackStartedAt = 0L
    private val trackJobs = mutableListOf<Job>()
    private var locationListener: LocationListener? = null
    private var hotspotDetector: HotspotDetector? = null

    @Volatile
    private var lastLocation: Location? = null

    @Volatile
    private var lastSample: RealTimeData? = null

    /** Spectrum auto-persist throttle (1/min while the Спектр tab is watched). */
    @Volatile
    private var lastSpectrumAutosaveAt: Long = 0L

    // --- baseline / sessions / alarm engine state ---

    @Volatile
    private var activeProfileId: Long? = null

    /**
     * Имя профиля НА МОМЕНТ СЪЁМКИ: снимок подписывается тем именем, под
     * которым место звалось тогда. Позже человек может переименовать место, и
     * старый снимок не должен молча сменить контекст.
     */
    private var activeProfileName: String? = null

    /** Condition 1 of the admission pipeline: profile-level learning switch. */
    @Volatile
    private var profileLearningEnabled: Boolean = true

    /**
     * Baseline epoch of the active profile. Watched, not just read: when the
     * user starts a new period («Уровень изменился надолго»), the statistics
     * must be rebuilt at once — waiting for the 10-minute refresh would leave
     * the screen showing the band the user has just retired.
     */
    private var activeBaselineEpoch: Long? = null

    /** Condition 2: the Wi-Fi context machine's confidence. */
    @Volatile
    private var contextReliable: Boolean = true

    /** Condition 4: Поиск / experiment is on screen. */
    @Volatile
    private var experimentActive: Boolean = false

    /** Condition 7: manual freeze from Настройки. */
    @Volatile
    private var baselineFrozen: Boolean = false

    /** Condition 5: quarantine after a detected deviation episode. */
    private val quarantine = QuarantineWindow()

    @Volatile
    private var baselineState: BaselineState? = null

    @Volatile
    private var thresholds: AlarmThresholds = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f)

    /** Display unit for the alarm notification text; raw values stay µSv/h. */
    @Volatile
    private var doseUnit: DoseUnitSetting = DoseUnitSetting.MICRO_SIEVERT

    private val sessionGate = SessionGate()

    @Volatile
    private var sessionId: Long? = null

    /**
     * Язык уведомлений. Сервис читает его сам: `LocalStrings` живёт в
     * композиции, а уведомление пишется без экрана — и человек, выбравший
     * английский, не должен получать русскую тревогу.
     */
    @Volatile
    private var texts: NotificationStrings = NotificationRu

    /** Guarded by [alarmLock]: trackers are recreated on place/threshold change. */
    private val alarmLock = Any()
    private var aboveUsualTracker = PersistenceTracker(persistenceMillis = 0)
    private var alertTracker = PersistenceTracker(persistenceMillis = 0)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.get(this)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        Notifications.ensureChannels(this)
        graph.serviceStatus.onServiceStarted()
        rebuildTrackers()

        scope.launch {
            graph.profileRepository.ensureDefaultProfiles()
            // Sessions a killed process left open end at the last real sample.
            graph.sessionRepository.closeStale()
        }
        // Wi-Fi context: no GPS involved (spec §3.3).
        graph.contextController.start(scope)
        // Versioned pre-aggregation of ADR 004: minute scalars and hourly
        // quantile sketches. Owns its own IO loop and history backfill.
        graph.preAggregator.start(scope)
        scope.launch {
            // Срез сырых измерений — только по явному выбору владельца
            // (умолчание «хранить всё»); правило и границы — в [RawRetention].
            while (true) {
                val days = graph.settings.rawRetentionDays.first()
                RawRetention.cutoffMillis(System.currentTimeMillis(), days)?.let { cutoff ->
                    graph.database.sampleDao().deleteOlderThan(cutoff)
                }
                delay(RETENTION_SWEEP_MILLIS)
            }
        }
        scope.launch {
            // История спектрограммы переживает процесс (ADR 007): окно
            // поднимается из базы ДО первого опроса, поэтому экран открывается
            // на продолжении картинки, а не на пустоте.
            graph.spectrogramStore.restore(System.currentTimeMillis())
            while (true) {
                graph.spectrogramStore.compact(System.currentTimeMillis())
                delay(SPECTROGRAM_COMPACT_INTERVAL_MILLIS)
            }
        }
        scope.launch {
            graph.settings.alarmThresholds.collect { next ->
                val persistenceChanged = next.persistenceSeconds != thresholds.persistenceSeconds
                thresholds = next
                if (persistenceChanged) rebuildTrackers()
                hotspotDetector?.thresholdMicroSvH = next.l1MicroSvH
            }
        }
        scope.launch {
            graph.settings.doseUnit.collect { doseUnit = it }
        }
        scope.launch {
            graph.settings.language.collect { setting ->
                val language = AppLanguage.resolve(
                    setting,
                    resources.configuration.locales[0]?.toLanguageTag().orEmpty(),
                )
                texts = NotificationCatalogue.of(language)
                // Имя канала видно в системных настройках: после смены языка
                // его нужно переписать, иначе там остаётся прежний язык.
                Notifications.ensureChannels(this@MeasurementService, texts)
            }
        }
        scope.launch {
            graph.profileRepository.activeProfile().collect { profile ->
                val placeChanged = profile?.id != activeProfileId
                val changed = placeChanged ||
                    profile?.baselineEpochMillis != activeBaselineEpoch
                val previousSession = sessionId
                activeProfileId = profile?.id
                activeProfileName = profile?.name
                activeBaselineEpoch = profile?.baselineEpochMillis
                profileLearningEnabled = profile?.baselineLearning ?: false
                if (changed) {
                    rebuildTrackers()
                    refreshBaseline()
                }
                // Смена места — НАСТОЯЩАЯ граница записи, в отличие от разрыва
                // связи. Полевой случай: человек ушёл из дома, контекст честно
                // переключился на «В пути», карта писала след — а в журнале
                // этой записи не было вовсе: она осталась внутри записи «Дом»,
                // потому что профиль запоминается один раз, при открытии.
                if (placeChanged && previousSession != null) {
                    graph.sessionRepository.close(
                        sessionId = previousSession,
                        endedAt = lastSample?.timestampMillis ?: System.currentTimeMillis(),
                    )
                    sessionId = graph.sessionRepository.open(profile?.id)
                }
            }
        }
        scope.launch {
            graph.contextHub.state.collect { contextReliable = it.isReliable }
        }
        scope.launch {
            graph.settings.baselineFrozen.collect { baselineFrozen = it }
        }
        scope.launch {
            // Поиск on screen = an experiment (spec §18): its interval must
            // never teach the baseline, and the user must see why.
            graph.searchPresenceHub.watchers.collect { watchers ->
                graph.serviceStatus.onExperiment(
                    ServiceStatus.SOURCE_SEARCH,
                    if (watchers > 0) texts.searchSource else null,
                )
            }
        }
        scope.launch {
            // Single source of truth for admission condition 4: whoever
            // declared the experiment (Поиск, A/B run) flips the same flag.
            graph.serviceStatus.experiment.collect { experimentActive = it != null }
        }
        scope.launch {
            while (true) {
                refreshBaseline()
                delay(BASELINE_REFRESH_MILLIS)
            }
        }
        // Запись маршрута переживает гибель процесса.
        //
        // Сначала возобновляется та, которую человек начал и не останавливал:
        // система вправе убить процесс когда угодно, и «Начать маршрут»
        // означает «пиши, пока не скажу стоп», а не «пиши, пока жив процесс».
        // И только потом закрываются ОСТАЛЬНЫЕ незакрытые записи — те, что
        // остались от прежних запусков.
        scope.launch {
            val resumed = resumeTrackingIfAny()
            graph.trackRepository.recoverUnfinished(exceptId = resumed)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithCurrentTypes()
        when (intent?.action) {
            ACTION_START -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) {
                    scope.launch { graph.settings.setLastDeviceAddress(address) }
                    startMeasuring(address)
                } else {
                    resumeFromSettings()
                }
            }
            ACTION_START_TRACK -> startTracking()
            ACTION_STOP_TRACK -> stopTracking()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            // Restarted by the system after being killed: resume the last device.
            null -> resumeFromSettings()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        graph.serviceStatus.onServiceStopped()
        graph.contextController.stop()
        stopTracking()
        val current = device
        device = null
        if (current != null) {
            // Bounded: stop() only cancels the connection loop and joins it.
            runBlocking { current.stop() }
        }
        val openSession = sessionId
        sessionId = null
        if (openSession != null) {
            val endedAt = lastSample?.timestampMillis ?: System.currentTimeMillis()
            // Bounded single UPDATE; scope is about to be cancelled.
            runBlocking { graph.sessionRepository.close(openSession, endedAt) }
        }
        scope.cancel()
        super.onDestroy()
    }

    // --- baseline & alarm engine ---

    private fun rebuildTrackers() {
        synchronized(alarmLock) {
            aboveUsualTracker = PersistenceTracker(
                persistenceMillis = ABOVE_USUAL_MIN_DWELL_SECONDS * 1000,
            )
            alertTracker = PersistenceTracker(
                persistenceMillis = thresholds.persistenceSeconds * 1000L,
            )
            graph.serviceStatus.onDeviation(DeviationSnapshot())
        }
    }

    private suspend fun refreshBaseline() {
        val profileId = activeProfileId ?: return
        val state = graph.baselineRepository.state(profileId)
        baselineState = state
        graph.serviceStatus.onBaseline(state)
        ensureFingerprint(profileId, state)
    }

    /**
     * Эталон места создаётся САМ, по достижении зрелости профиля (ADR 005):
     * пользователь не обязан ничего нажимать, чтобы функция заработала. Если
     * эталон уже есть, здесь не происходит ничего — заменяет его только явное
     * «Обновить эталон».
     */
    private suspend fun ensureFingerprint(profileId: Long, state: BaselineState) {
        val baseline = (state as? BaselineState.Active)?.baseline ?: return
        if (graph.fingerprintRepository.entity(profileId) != null) return
        if (!graph.fingerprintRepository.maturity(profileId, state).ready) return
        graph.fingerprintRepository.create(profileId, baseline)
    }

    /**
     * Baseline admission verdict for one sample (spec §4.2). Evaluated at
     * write time so the reason reflects the state the measurement was actually
     * taken in; the raw sample is stored either way.
     */
    private fun admissionOf(sample: RealTimeData, nowMillis: Long): Admission =
        BaselineAdmission.evaluate(
            AdmissionInput(
                profileLearningEnabled = profileLearningEnabled,
                contextReliable = contextReliable,
                sampleAgeMillis = (nowMillis - sample.timestampMillis).coerceAtLeast(0L),
                experimentActive = experimentActive,
                quarantineUntilMillis = quarantine.untilMillis,
                nowMillis = nowMillis,
                doseRateMicroSvH = DoseUnits.rawToMicroSievertPerHour(sample.doseRate),
                countRateCps = sample.countRate,
                countRateErrPercent = sample.countRateErr,
                doseRateErrPercent = sample.doseRateErr,
                manuallyFrozen = baselineFrozen,
            ),
        )

    /**
     * 1 Hz alarm engine (ADR 002): deviation = magnitude AND persistence.
     * A confirmed persistent deviation lands in the events journal once per
     * excursion; the live picture goes to [ServiceStatus] for the UI.
     */
    private fun onSampleForAlarm(sample: RealTimeData) {
        val microSvH = DoseUnits.rawToMicroSievertPerHour(sample.doseRate)
        val baseline = (baselineState as? BaselineState.Active)?.baseline
        val now = sample.timestampMillis

        val snapshot: DeviationSnapshot
        val alertFired: Boolean
        synchronized(alarmLock) {
            val above = aboveUsualTracker.onSample(
                nowMillis = now,
                conditionMet = aboveUsualMagnitude(microSvH, baseline),
            )
            val alert = alertTracker.onSample(
                nowMillis = now,
                conditionMet = deviationMagnitude(microSvH, baseline?.doseHighMicroSvH, thresholds),
            )
            alertFired = alert.fired
            snapshot = DeviationSnapshot(
                aboveUsualSince = when (val s = above.state) {
                    is PersistenceTracker.State.Building -> s.sinceMillis
                    is PersistenceTracker.State.Confirmed -> s.sinceMillis
                    PersistenceTracker.State.Idle -> null
                },
                alertSince = (alert.state as? PersistenceTracker.State.Confirmed)?.sinceMillis,
                alarmConditionSince = when (val s = alert.state) {
                    is PersistenceTracker.State.Building -> s.sinceMillis
                    is PersistenceTracker.State.Confirmed -> s.sinceMillis
                    PersistenceTracker.State.Idle -> null
                },
            )
        }
        graph.serviceStatus.onDeviation(snapshot)
        // Quarantine (admission condition 5) runs off the same live picture:
        // the window is measured from the END of the excursion, so the tail of
        // an episode cannot slip back into «usual».
        quarantine.onSample(
            nowMillis = now,
            deviationActive = snapshot.aboveUsualSince != null || snapshot.alertSince != null,
        )
        graph.serviceStatus.onAdmission(admissionOf(sample, System.currentTimeMillis()))
        if (alertFired) {
            // Once per deviation episode: PersistenceTracker fires the rising
            // edge exactly once and re-arms only after the excursion ends.
            postAlarmNotification(
                doseMicroSvH = microSvH,
                typicalHighMicroSvH = baseline?.doseHighMicroSvH,
            )
            scope.launch {
                graph.measurementRepository.recordDeviation(
                    timestamp = now,
                    doseRate = sample.doseRate,
                    baselineHighMicroSvH = baseline?.doseHighMicroSvH,
                )
            }
        }
    }

    /**
     * System alarm for a confirmed deviation (threshold or baseline-relative —
     * both confirm through the same tracker). Dedicated high-importance
     * channel «Тревога»: default system alarm sound + vibration, tunable by
     * the user in the channel's system settings.
     */
    private fun postAlarmNotification(doseMicroSvH: Float, typicalHighMicroSvH: Float?) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val s = texts
        val units = stringsFor(if (s === NotificationEn) AppLanguage.EN else AppLanguage.RU)
        val text = buildString {
            append(s.nowRate(DoseFormat.rateWithUnit(doseMicroSvH, doseUnit, units)))
            if (typicalHighMicroSvH != null && typicalHighMicroSvH > 0f) {
                append(s.usuallyUpTo(DoseFormat.rateWithUnit(typicalHighMicroSvH, doseUnit, units)))
            }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, Notifications.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_measurement)
            .setContentTitle(s.levelChanged)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(ALARM_NOTIFICATION_ID, notification)
    }

    // --- sessions ---

    private fun onConnectionForSession(state: ConnectionState) {
        val now = System.currentTimeMillis()
        val lastSampleAt = lastSample?.timestampMillis
        val action = when (state) {
            is ConnectionState.Connected -> sessionGate.onConnected(now, lastSampleAt)
            is ConnectionState.Reconnecting -> sessionGate.onLinkLost(now)
            ConnectionState.Disconnected -> sessionGate.onDisconnected(now, lastSampleAt)
            is ConnectionState.Connecting -> SessionGate.Action.None
        }
        when (action) {
            SessionGate.Action.None -> Unit
            // Первое подключение после запуска службы ПРОДОЛЖАЕТ последнюю
            // запись, если она о том же месте и только что шла: перезапуск
            // процесса системой — не решение человека закончить измерение.
            SessionGate.Action.Open -> scope.launch {
                sessionId = graph.sessionRepository.resumeOrOpen(
                    profileId = activeProfileId,
                    graceMillis = SessionGate.DEFAULT_GRACE_MILLIS,
                )
            }
            is SessionGate.Action.Reopen -> scope.launch {
                sessionId?.let { graph.sessionRepository.close(it, action.closeAt) }
                sessionId = graph.sessionRepository.open(activeProfileId)
            }
            is SessionGate.Action.Close -> {
                val current = sessionId
                sessionId = null
                if (current != null) {
                    scope.launch { graph.sessionRepository.close(current, action.closeAt) }
                }
            }
        }
    }

    // --- measuring ---

    private fun resumeFromSettings() {
        scope.launch {
            val address = graph.settings.lastDeviceAddress.first()
            if (address != null) startMeasuring(address) else stopSelf()
        }
    }

    private fun startMeasuring(address: String) {
        if (device?.address == address) return
        val previous = device
        device = null
        deviceJobs.forEach { it.cancel() }
        deviceJobs.clear()
        if (previous != null) scope.launch { previous.stop() }

        val newDevice = RadiaCodeDevice(address = address, linkFactory = graph.linkFactory)
        device = newDevice
        newDevice.start(scope)

        deviceJobs += scope.launch {
            newDevice.records.collect { records ->
                val now = System.currentTimeMillis()
                val outcome = graph.measurementRepository.record(records, activeProfileId) { sample ->
                    admissionOf(sample, now).storageKey
                }
                // Покадровая трасса обмена — единственное, что отличает
                // «записи не пришли» от «пришли, но не записались»; на экране
                // обе беды выглядят как «нет новых данных · N с».
                graph.streamTrace.add(
                    StreamTrace.Tick(
                        atMillis = now,
                        records = records.size,
                        newestAgeMillis = records.maxOfOrNull { it.timestampMillis }
                            ?.let { now - it },
                        correctionMillis = newDevice.clockCorrectionMillis,
                        inserted = outcome.inserted,
                        dropped = outcome.dropped,
                    ),
                )
            }
        }
        deviceJobs += scope.launch {
            newDevice.realTimeData.collect { sample ->
                graph.serviceStatus.onClockCorrection(newDevice.clockCorrectionMillis)
                lastSample = sample
                // Живое показание уходит на экран ПРЯМО ОТСЮДА, минуя базу:
                // свежесть — факт о приходе данных, и она не должна зависеть
                // ни от часов прибора, ни от того, легла ли строка в таблицу.
                graph.serviceStatus.onSample(
                    ServiceStatus.LiveSample(
                        deviceTimestampMillis = sample.timestampMillis,
                        receivedAtMillis = System.currentTimeMillis(),
                        doseRate = sample.doseRate,
                        doseRateErr = sample.doseRateErr,
                        countRate = sample.countRate,
                        countRateErr = sample.countRateErr,
                    ),
                )
                onSampleForAlarm(sample)
                onSampleForHotspot(sample)
                updateNotification()
            }
        }
        deviceJobs += scope.launch {
            newDevice.connectionState.collect { state ->
                // Причина отказа едет вместе со статусом: она нужна только
                // отладочному отчёту и только когда подключение не удалось.
                graph.serviceStatus.lastConnectionFailure = newDevice.lastFailure
                graph.serviceStatus.seqGapTotal = newDevice.seqGapTotal
                if (state is ConnectionState.Reconnecting) {
                    graph.serviceStatus.reconnectCount += 1
                }
                graph.serviceStatus.onConnectionState(state)
                if (state !is ConnectionState.Connected) graph.deviceControlHub.onDisconnected()
                onConnectionForSession(state)
                updateNotification()
            }
        }
        deviceJobs += scope.launch {
            combine(
                newDevice.connectionState,
                graph.spectrumHub.watchers,
                graph.settings.spectrumPollPolicy,
            ) { conn, watchers, policy ->
                if (conn is ConnectionState.Connected) {
                    conn.info.spectrumFormatVersion to
                        SpectrumPollPolicy.intervalMillis(policy, watchers)
                } else {
                    null
                }
            }.distinctUntilChanged().collectLatest { state ->
                if (state == null) return@collectLatest
                val (formatVersion, intervalMillis) = state
                if (formatVersion !in SpectrumHub.SUPPORTED_FORMAT_VERSIONS) {
                    graph.spectrumHub.onUnsupportedFormat(formatVersion)
                    return@collectLatest
                }
                // Spectrum poll interleaves with the 1 Hz DATA_BUF poll on the
                // single-in-flight ProtocolClient. Частота — ЯВНАЯ политика
                // (ADR 007): открытый Спектр/Спектрограмма всегда 5 с, иначе
                // выбранная ступень. Каждый опрос это ещё и срез истории
                // спектрограммы, поэтому политика решает не «живость экрана», а
                // временное разрешение записи.
                while (true) {
                    pollSpectrum(newDevice)
                    delay(intervalMillis)
                }
            }
        }
        deviceJobs += scope.launch {
            graph.spectrumHub.commands.collect { command ->
                onSpectrumCommand(newDevice, command)
            }
        }
        deviceJobs += scope.launch {
            graph.deviceControlHub.commands.collect { command ->
                applyDeviceControl(newDevice, command)
            }
        }
        deviceJobs += scope.launch {
            // Просьба человека доносится до прибора при КАЖДОМ подключении.
            // Нажатие в момент переподключения раньше исчезало: слушателя
            // команд в этот момент не было, а буфера воспроизведения у потока
            // нет — и повторить нажатие было нечем, тумблер уже стоял как надо.
            newDevice.connectionState.collect { state ->
                if (state !is ConnectionState.Connected) return@collect
                for (command in graph.deviceControlHub.pending()) {
                    applyDeviceControl(newDevice, command)
                }
            }
        }
        deviceJobs += scope.launch {
            // Приложение на экране → короткий период DATA_BUF. Те же записи,
            // вчетверо меньше задержка подбора; почему это не «4 измерения в
            // секунду» — в KDoc FastPollHub.
            graph.fastPollHub.watchers.collect { watchers ->
                newDevice.pollIntervalMillis = FastPollHub.intervalMillis(watchers)
            }
        }
    }

    // --- spectrum acquisition ---

    /**
     * Пишет настройку в прибор и ЧЕСТНО сообщает исход.
     *
     * Состояние помечается применённым только после подтверждения прибором:
     * иначе тумблер показывал бы желаемое, а не то, что в приборе. Отказ тоже
     * не проглатывается — молчащая кнопка неотличима от сломанной.
     */
    private suspend fun applyDeviceControl(
        device: RadiaCodeDevice,
        command: DeviceControlHub.Command,
    ) {
        val ok = try {
            when (command) {
                is DeviceControlHub.Command.Sound -> device.setDeviceSoundOn(command.on)
                is DeviceControlHub.Command.Vibro -> device.setDeviceVibroOn(command.on)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (ok) graph.deviceControlHub.onApplied(command) else graph.deviceControlHub.onFailed(command)
    }

    /**
     * Провенанс снимка: чей это спектр и из какой эпохи накопления.
     *
     * Считается на каждом опросе — эпоха определяется по самим числам
     * (ADR 008), поэтому знать о ней можно только читая их подряд.
     */
    private var epochMark: SpectrumEpoch.Mark? = null

    private suspend fun updateEpoch(spectrum: Spectrum): SpectrumEpoch.Mark {
        val serial = (device?.connectionState?.value as? ConnectionState.Connected)
            ?.info?.serialNumber
        val previous = epochMark ?: SpectrumEpoch.decode(graph.settings.spectrumEpochMark.first())
        val mark = SpectrumEpoch.mark(
            previous = previous,
            spectrum = spectrum,
            deviceSerial = serial,
            newEpochId = System.currentTimeMillis(),
        )
        epochMark = mark
        // На диск — только когда эпоха сменилась или накопление заметно
        // выросло: запись на каждый опрос стоила бы дороже, чем стоит.
        if (previous == null ||
            previous.epochId != mark.epochId ||
            mark.durationSeconds - previous.durationSeconds >= EPOCH_PERSIST_SECONDS
        ) {
            graph.settings.setSpectrumEpochMark(SpectrumEpoch.encode(mark))
        }
        return mark
    }

    private fun spectrumProvenance(): Triple<String?, String?, Long?> {
        val connected = device?.connectionState?.value as? ConnectionState.Connected
        return Triple(
            connected?.info?.serialNumber,
            connected?.info?.firmware?.toString(),
            epochMark?.epochId,
        )
    }

    private suspend fun pollSpectrum(device: RadiaCodeDevice) {
        val bytesBefore = device.spectrumPayloadBytes
        val spectrum = try {
            device.readSpectrum()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return // link hiccup or timeout: the loop retries on the next tick
        }
        // Цена частоты записывается на каждом опросе — ADR 007 обещает факты, а
        // не оценку по числу опросов.
        graph.serviceStatus.onSpectrumRead(device.spectrumPayloadBytes - bytesBefore)
        val now = System.currentTimeMillis()
        updateEpoch(spectrum)
        graph.spectrumHub.onSpectrum(spectrum, now)
        val sample = lastSample
        graph.spectrogramStore.onSpectrum(
            spectrum = spectrum,
            atMillis = now,
            cps = sample?.countRate,
            doseMicroSvH = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) },
        )
        if (now - lastSpectrumAutosaveAt >= SpectrumHub.AUTOSAVE_INTERVAL_MILLIS) {
            lastSpectrumAutosaveAt = now
            val (serial, firmware, epochId) = spectrumProvenance()
            graph.measurementRepository.saveSpectrum(
                spectrum,
                accumulated = false,
                trigger = SpectrumSnapshotEntity.TRIGGER_PERIODIC,
                deviceSerial = serial,
                firmware = firmware,
                epochId = epochId,
                profileId = activeProfileId,
                profileName = activeProfileName,
            )
        }
    }

    private suspend fun onSpectrumCommand(device: RadiaCodeDevice, command: SpectrumHub.Command) {
        when (command) {
            SpectrumHub.Command.RESET -> {
                try {
                    device.resetSpectrum()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return // device unreachable; the UI still shows the old spectrum
                }
                graph.spectrumHub.onReset()
                graph.spectrogramStore.onReset()
                // The device also reports a SPECTRUM_RESET event via DATA_BUF,
                // which lands in the journal through the regular record path.
                pollSpectrum(device)
            }
            SpectrumHub.Command.SAVE_SNAPSHOT -> {
                val spectrum = graph.spectrumHub.state.value.spectrum ?: return
                val now = System.currentTimeMillis()
                val (serial, firmware, epochId) = spectrumProvenance()
                graph.measurementRepository.saveSpectrum(
                    spectrum,
                    accumulated = false,
                    origin = SpectrumSnapshotEntity.ORIGIN_USER,
                    trigger = SpectrumSnapshotEntity.TRIGGER_MANUAL,
                    deviceSerial = serial,
                    firmware = firmware,
                    epochId = epochId,
                    profileId = activeProfileId,
                    profileName = activeProfileName,
                )
                graph.measurementRepository.recordSpectrumSaved(now, spectrum.durationSeconds)
                graph.spectrumHub.onSaved(now)
            }
            SpectrumHub.Command.RECORD_BACKGROUND -> {
                val spectrum = graph.spectrumHub.state.value.spectrum ?: return
                val (serial, firmware, epochId) = spectrumProvenance()
                graph.measurementRepository.saveSpectrum(
                    spectrum,
                    accumulated = false,
                    isBackgroundReference = true,
                    origin = SpectrumSnapshotEntity.ORIGIN_USER,
                    trigger = SpectrumSnapshotEntity.TRIGGER_BACKGROUND,
                    deviceSerial = serial,
                    firmware = firmware,
                    epochId = epochId,
                    profileId = activeProfileId,
                    profileName = activeProfileName,
                )
                // Подтверждение — ПОСЛЕ записи: нажатие без ответа неотличимо
                // от нажатия, которое ничего не сделало.
                graph.spectrumHub.onBackgroundRecorded(System.currentTimeMillis())
            }
        }
    }

    // --- track recording ---

    /**
     * Запись маршрута.
     *
     * Маршрут НЕ заводится в журнале в момент нажатия: сначала он черновик, а
     * строка появляется с первой принятой координатой. Полевой дефект был
     * ровно об этом — в Истории копились пустые записи «идёт запись, 0 с, 0
     * измерений»: старт приходил дважды (кнопка и служба), запись создавалась
     * до всякой координаты, а `trackSessionId` присваивался внутри корутины,
     * поэтому проверка «уже пишем» пропускала второй старт.
     *
     * Отсюда три правила: флаг ставится СИНХРОННО, строка создаётся по первой
     * точке, а запись без единой точки не остаётся в журнале вовсе.
     */
    private fun startTracking() {
        if (tracking) return
        tracking = true
        // Достаточно ЛЮБОГО разрешения на место. Раньше требовалось только
        // точное, и человек, выбравший в системном диалоге «Приблизительно»,
        // получал молчащую кнопку: запись не начиналась и не объясняла почему.
        // Приблизительный след честнее отсутствующего — его точность видна
        // кружком у каждой точки.
        if (!hasLocationPermission() && !hasCoarseLocationPermission()) return

        // Track hotspots share the alarm L1 level (single user-facing threshold).
        val detector = HotspotDetector(thresholds.l1MicroSvH)
        hotspotDetector = detector

        trackStartedAt = System.currentTimeMillis()
        graph.serviceStatus.onTrackRecording(
            ServiceStatus.TrackRecording(sessionId = null, startedAt = trackStartedAt),
        )
        // Сначала служба объявляется работающей С МЕСТОПОЛОЖЕНИЕМ, и только
        // потом подписывается: с Android 14 система смотрит на тип службы
        // в момент подписки, и подписка «не того» типа не получает ни
        // одного обновления — молча.
        startForegroundWithCurrentTypes()
        registerLocationUpdates()
    }

    /**
     * Строка маршрута в журнале — по первой координате, не раньше.
     *
     * Вызывается из обработчика координат, то есть с главного потока, поэтому
     * `trackSessionId` присваивается здесь же, а не внутри корутины: гонка
     * между двумя первыми фиксами дала бы два маршрута на одну прогулку.
     */
    private suspend fun ensureTrackSession(): Long? {
        if (!tracking) return null
        trackSessionId?.let { return it }
        val id = graph.trackRepository.startSession(name = "")
        // Пока шла вставка, запись могли остановить — тогда пустой маршрут
        // тут же убирается, а не остаётся в журнале.
        if (!tracking) {
            graph.trackRepository.discardIfEmpty(id)
            return null
        }
        trackSessionId = id
        graph.settings.setActiveTrackSessionId(id)
        graph.serviceStatus.onTrackRecording(
            ServiceStatus.TrackRecording(sessionId = id, startedAt = trackStartedAt),
        )
        return id
    }

    /**
     * Продолжить запись, начатую до перезапуска службы.
     *
     * @return id возобновлённой записи; null — возобновлять нечего.
     */
    private suspend fun resumeTrackingIfAny(): Long? {
        if (tracking) return trackSessionId
        val stored = graph.settings.activeTrackSessionId.first() ?: return null
        val session = graph.trackRepository.session(stored)
        // Запись уже закрыта (остановили в прошлом запуске) — возобновлять
        // нечего, и метку надо убрать, чтобы она не воскрешала её потом.
        if (session == null || session.endedAt != null) {
            graph.settings.setActiveTrackSessionId(null)
            return null
        }
        if (!hasLocationPermission() && !hasCoarseLocationPermission()) return null
        tracking = true
        trackSessionId = stored
        trackStartedAt = session.startedAt
        hotspotDetector = HotspotDetector(thresholds.l1MicroSvH)
        graph.serviceStatus.onTrackRecording(
            ServiceStatus.TrackRecording(sessionId = stored, startedAt = session.startedAt),
        )
        startForegroundWithCurrentTypes()
        registerLocationUpdates()
        return stored
    }

    private fun stopTracking() {
        tracking = false
        trackJobs.forEach { it.cancel() }
        trackJobs.clear()
        locationListener?.let {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(it)
        }
        locationListener = null
        hotspotDetector = null
        val sessionId = trackSessionId
        trackSessionId = null
        graph.serviceStatus.onTrackRecording(null)
        scope.launch { graph.settings.setActiveTrackSessionId(null) }
        if (sessionId != null) {
            // Маршрут без единой точки не попадает в журнал: показывать
            // «прогулку», которой не было, хуже, чем не показывать ничего.
            scope.launch { graph.trackRepository.finishSession(sessionId) }
            startForegroundWithCurrentTypes()
        }
    }

    /**
     * Подписка на координаты для записи следа.
     *
     * ## Что было сломано
     *
     * Запрашивался ОДИН источник — GPS. В помещении, в машине с плёнкой на
     * стёклах, на телефоне без свежего альманаха фикса нет минутами, и след
     * не получал ни одной точки: «Жду первые точки» висело вечно. При этом
     * синяя точка на карте появлялась — её подписка (`ui/map/MapLocation`)
     * с самого начала слушала и сетевого провайдера. Теперь оба места
     * спрашивают одно и то же.
     *
     * `PASSIVE_PROVIDER` добавлен третьим: он ничего не включает сам и отдаёт
     * фиксы, которые в этот момент запросил кто-то другой, — на устройстве без
     * сервисов Google это иногда единственный источник в помещении.
     *
     * Причина отсутствия точек уходит в статус: ждать спутников и ждать
     * разрешения, которого нет, — разные вещи, и экран обязан их различать.
     */
    // Разрешение проверяется первой же строкой тела: подписка без него не
    // доходит до `requestLocationUpdates`, а экран получает NO_PERMISSION.
    // Lint не видит проверку через собственные `has*Permission()`, поэтому
    // предупреждение снято здесь, а не обойдено ослаблением проверки.
    @android.annotation.SuppressLint("MissingPermission")
    private fun registerLocationUpdates() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val precise = hasLocationPermission()
        if ((!precise && !hasCoarseLocationPermission()) || locationManager == null) {
            graph.serviceStatus.onTrackLocation(ServiceStatus.TrackLocation.NO_PERMISSION)
            return
        }
        var lastAcceptedAt = 0L
        var lastAccuracy = Float.MAX_VALUE
        val listener = LocationListener { location ->
            // Источников несколько, и они присылают одно и то же место с
            // разной точностью. Точка принимается, если прошла секунда — или
            // если она ТОЧНЕЕ предыдущей: иначе сетевой фикс с точностью в
            // километр вытеснял бы спутниковый.
            val fresh = location.time - lastAcceptedAt >= LOCATION_INTERVAL_MILLIS
            val better = location.accuracy < lastAccuracy
            if (!fresh && !better) {
                // Дубль от второго провайдера тоже считается: без этого
                // «фиксов 0» в отчёте означало бы и «система молчит», и «всё
                // приходит, но отбрасывается».
                graph.serviceStatus.onTrackFix(
                    provider = location.provider,
                    accuracyMeters = location.accuracy,
                    atMillis = System.currentTimeMillis(),
                    stored = false,
                )
                return@LocationListener
            }
            lastAcceptedAt = location.time
            lastAccuracy = location.accuracy
            lastLocation = location
            graph.serviceStatus.onTrackLocation(ServiceStatus.TrackLocation.RECEIVING)
            graph.serviceStatus.onTrackFix(
                provider = location.provider,
                accuracyMeters = location.accuracy,
                atMillis = System.currentTimeMillis(),
                stored = true,
            )
            val sample = lastSample
            trackJobs += scope.launch {
                val sessionId = ensureTrackSession() ?: return@launch
                graph.trackRepository.addPoint(
                    sessionId = sessionId,
                    timestamp = location.time,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    doseRate = sample?.doseRate,
                    countRate = sample?.countRate,
                    // GPS altitude feeds flight detection (полёт badge/chart).
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                )
            }
        }
        locationListener = listener
        val subscribedTo = mutableListOf<String>()
        val enabled = LOCATION_PROVIDERS.filter {
            runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false)
        }
        for (provider in enabled) {
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    LOCATION_INTERVAL_MILLIS,
                    0f,
                    listener,
                    mainLooper,
                )
                subscribedTo += provider
            }
        }
        graph.serviceStatus.onTrackSubscribed(subscribedTo, enabled, precise)
        if (subscribedTo.isEmpty()) {
            locationListener = null
            graph.serviceStatus.onTrackLocation(ServiceStatus.TrackLocation.NO_PROVIDER)
            return
        }
        graph.serviceStatus.onTrackLocation(ServiceStatus.TrackLocation.WAITING)
        // Последний известный фикс — первая точка следа сразу, а не через
        // минуту ожидания спутников. Он несёт СВОЁ время, поэтому старый
        // виден как старый, а не выдаётся за текущий.
        runCatching {
            LOCATION_PROVIDERS
                .mapNotNull { locationManager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { listener.onLocationChanged(it) }
        }
    }

    private fun onSampleForHotspot(sample: RealTimeData) {
        if (trackSessionId == null) return
        val detector = hotspotDetector ?: return
        val microSvH = DoseUnits.rawToMicroSievertPerHour(sample.doseRate)
        if (detector.onSample(microSvH)) {
            val location = lastLocation
            val baseline = (baselineState as? BaselineState.Active)?.baseline
            scope.launch {
                graph.measurementRepository.recordHotspot(
                    timestamp = sample.timestampMillis,
                    doseRate = sample.doseRate,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    baselineHighMicroSvH = baseline?.doseHighMicroSvH,
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** «Приблизительно» в системном диалоге: место известно грубо, но известно. */
    private fun hasCoarseLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    // --- notification ---

    /**
     * Объявление типов службы.
     *
     * Тип `location` включается по ФЛАГУ ЗАПИСИ, а не по наличию строки
     * маршрута в журнале. Это тот самый дефект «маршрут пишется, пока открыт
     * экран, и обрывается, когда свернул»: строка появляется с первой
     * координатой, а служба объявляется работающей ДО подписки — то есть
     * ровно тогда, когда `trackSessionId` ещё пуст. Служба уходила в фон без
     * типа `location`, и система переставала слать ей фиксы; пока приложение
     * было на переднем плане, координаты шли по общему правилу, поэтому на
     * открытом экране всё выглядело исправным.
     */
    private fun startForegroundWithCurrentTypes() {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (tracking && (hasLocationPermission() || hasCoarseLocationPermission())) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types)
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val state = device?.connectionState?.value ?: ConnectionState.Disconnected
        val sample = lastSample

        val title = when {
            state is ConnectionState.Connected && sample != null ->
                String.format(
                    Locale.US,
                    "%.2f µSv/h · %.1f cps",
                    DoseUnits.rawToMicroSievertPerHour(sample.doseRate),
                    sample.countRate,
                )
            state is ConnectionState.Connected -> "Connected"
            else -> "RadiaCode"
        }
        val text = when (state) {
            is ConnectionState.Connected ->
                state.info.serialNumber + if (tracking) " · recording track" else ""
            is ConnectionState.Connecting -> "Connecting…"
            is ConnectionState.Reconnecting -> "Reconnecting…"
            ConnectionState.Disconnected -> "Disconnected"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_measurement)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "app.radiacode.action.START"
        const val ACTION_STOP = "app.radiacode.action.STOP"
        /** Как часто метка эпохи спектра уходит на диск, с накопления. */
        private const val EPOCH_PERSIST_SECONDS = 60L

        const val ACTION_START_TRACK = "app.radiacode.action.START_TRACK"
        const val ACTION_STOP_TRACK = "app.radiacode.action.STOP_TRACK"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        const val CHANNEL_ID = Notifications.MEASUREMENT_CHANNEL_ID
        private const val NOTIFICATION_ID = 1
        private const val ALARM_NOTIFICATION_ID = 2
        private const val LOCATION_INTERVAL_MILLIS = 1_000L

        /**
         * Источники координат следа — все, что есть у устройства без сервисов
         * Google. Пассивный ничего не включает сам: он отдаёт фиксы, которые
         * в этот момент запросил кто-то другой.
         */
        private val LOCATION_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        private const val BASELINE_REFRESH_MILLIS = 10L * 60_000L

        /**
         * Как часто прорежается старая история спектрограммы (ADR 007). Раз в
         * час: работа идёт часовыми кусками и трогает только то, что старше
         * недели, поэтому чаще нечего делать, а реже — история неделями лежала
         * бы в самом дорогом виде.
         */
        private const val SPECTROGRAM_COMPACT_INTERVAL_MILLIS = 3_600_000L

        /** Как часто проверяется срез: раз в 6 ч достаточно для суточных величин. */
        private const val RETENTION_SWEEP_MILLIS = 6L * 3_600_000L

        fun startIntent(context: Context, deviceAddress: String): Intent =
            Intent(context, MeasurementService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)

        /** Resume the remembered device (no-op start when already measuring). */
        fun resumeIntent(context: Context): Intent =
            Intent(context, MeasurementService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, MeasurementService::class.java).setAction(ACTION_STOP)

        fun startTrackIntent(context: Context): Intent =
            Intent(context, MeasurementService::class.java).setAction(ACTION_START_TRACK)

        fun stopTrackIntent(context: Context): Intent =
            Intent(context, MeasurementService::class.java).setAction(ACTION_STOP_TRACK)
    }
}
