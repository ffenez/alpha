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
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.device.ConnectionState
import app.radiacode.device.DoseUnits
import app.radiacode.device.RadiaCodeDevice
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.protocol.RealTimeData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    /** Condition 1 of the admission pipeline: profile-level learning switch. */
    @Volatile
    private var profileLearningEnabled: Boolean = true

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
            graph.profileRepository.activeProfile().collect { profile ->
                val changed = profile?.id != activeProfileId
                activeProfileId = profile?.id
                profileLearningEnabled = profile?.baselineLearning ?: false
                if (changed) {
                    rebuildTrackers()
                    refreshBaseline()
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
            graph.fastPollHub.watchers.collect { watchers ->
                graph.serviceStatus.onExperiment(
                    ServiceStatus.SOURCE_SEARCH,
                    if (watchers > 0) "Поиск" else null,
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
        val text = buildString {
            append("Сейчас ")
            append(DoseFormat.rateWithUnit(doseMicroSvH, doseUnit))
            if (typicalHighMicroSvH != null && typicalHighMicroSvH > 0f) {
                append(" · обычно здесь до ")
                append(DoseFormat.rateWithUnit(typicalHighMicroSvH, doseUnit))
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
            .setContentTitle("Уровень радиации изменился")
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
            SessionGate.Action.Open -> scope.launch {
                sessionId = graph.sessionRepository.open(activeProfileId)
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
                graph.measurementRepository.record(records, activeProfileId) { sample ->
                    admissionOf(sample, now).storageKey
                }
            }
        }
        deviceJobs += scope.launch {
            newDevice.realTimeData.collect { sample ->
                lastSample = sample
                onSampleForAlarm(sample)
                onSampleForHotspot(sample)
                updateNotification()
            }
        }
        deviceJobs += scope.launch {
            newDevice.connectionState.collect { state ->
                graph.serviceStatus.onConnectionState(state)
                onConnectionForSession(state)
                updateNotification()
            }
        }
        deviceJobs += scope.launch {
            combine(newDevice.connectionState, graph.spectrumHub.watchers) { conn, watchers ->
                if (conn is ConnectionState.Connected) {
                    conn.info.spectrumFormatVersion to (watchers > 0)
                } else {
                    null
                }
            }.distinctUntilChanged().collectLatest { state ->
                if (state == null) return@collectLatest
                val (formatVersion, watched) = state
                if (formatVersion !in SpectrumHub.SUPPORTED_FORMAT_VERSIONS) {
                    graph.spectrumHub.onUnsupportedFormat(formatVersion)
                    return@collectLatest
                }
                // Spectrum poll interleaves with the 1 Hz DATA_BUF poll on the
                // single-in-flight ProtocolClient. Watched (Спектр screen):
                // 5 s live cadence. Unwatched: a slow poll keeps the radon
                // indicator fed — the 1/min autosave throttle persists every
                // slow poll, so the История-visible data stays hourly-dense.
                while (true) {
                    pollSpectrum(newDevice)
                    delay(
                        if (watched) {
                            SpectrumHub.POLL_INTERVAL_MILLIS
                        } else {
                            BACKGROUND_SPECTRUM_POLL_MILLIS
                        },
                    )
                }
            }
        }
        deviceJobs += scope.launch {
            graph.spectrumHub.commands.collect { command ->
                onSpectrumCommand(newDevice, command)
            }
        }
        deviceJobs += scope.launch {
            // Поиск on screen → shorter DATA_BUF period. Same records, half
            // the pickup delay; see FastPollHub for why that is not «2 Hz
            // measurements».
            graph.fastPollHub.watchers.collect { watchers ->
                newDevice.pollIntervalMillis = FastPollHub.intervalMillis(watchers)
            }
        }
    }

    // --- spectrum acquisition ---

    private suspend fun pollSpectrum(device: RadiaCodeDevice) {
        val spectrum = try {
            device.readSpectrum()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return // link hiccup or timeout: the loop retries on the next tick
        }
        val now = System.currentTimeMillis()
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
            graph.measurementRepository.saveSpectrum(spectrum, accumulated = false)
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
                graph.measurementRepository.saveSpectrum(
                    spectrum,
                    accumulated = false,
                    origin = SpectrumSnapshotEntity.ORIGIN_USER,
                )
                graph.measurementRepository.recordSpectrumSaved(now, spectrum.durationSeconds)
                graph.spectrumHub.onSaved(now)
            }
            SpectrumHub.Command.RECORD_BACKGROUND -> {
                val spectrum = graph.spectrumHub.state.value.spectrum ?: return
                graph.measurementRepository.saveSpectrum(
                    spectrum,
                    accumulated = false,
                    isBackgroundReference = true,
                    origin = SpectrumSnapshotEntity.ORIGIN_USER,
                )
            }
        }
    }

    // --- track recording ---

    private fun startTracking() {
        if (trackSessionId != null) return
        if (!hasLocationPermission()) return

        // Track hotspots share the alarm L1 level (single user-facing threshold).
        val detector = HotspotDetector(thresholds.l1MicroSvH)
        hotspotDetector = detector

        val name = "Track " + LocalDateTime.now().format(TRACK_NAME_FORMAT)
        trackJobs += scope.launch {
            val sessionId = graph.trackRepository.startSession(name)
            trackSessionId = sessionId
            graph.serviceStatus.onTrackRecording(
                ServiceStatus.TrackRecording(sessionId, System.currentTimeMillis()),
            )
            registerLocationUpdates(sessionId)
            startForegroundWithCurrentTypes()
        }
    }

    private fun stopTracking() {
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
        if (sessionId != null) {
            scope.launch { graph.trackRepository.endSession(sessionId) }
            startForegroundWithCurrentTypes()
        }
    }

    private fun registerLocationUpdates(sessionId: Long) {
        if (!hasLocationPermission()) return
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location ->
            lastLocation = location
            val sample = lastSample
            scope.launch {
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
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MILLIS,
                0f,
                listener,
                mainLooper,
            )
        } catch (_: SecurityException) {
            locationListener = null
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

    // --- notification ---

    private fun startForegroundWithCurrentTypes() {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (trackSessionId != null && hasLocationPermission()) {
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
                state.info.serialNumber + if (trackSessionId != null) " · recording track" else ""
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
        const val ACTION_START_TRACK = "app.radiacode.action.START_TRACK"
        const val ACTION_STOP_TRACK = "app.radiacode.action.STOP_TRACK"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        const val CHANNEL_ID = Notifications.MEASUREMENT_CHANNEL_ID
        private const val NOTIFICATION_ID = 1
        private const val ALARM_NOTIFICATION_ID = 2
        private const val LOCATION_INTERVAL_MILLIS = 1_000L

        private val TRACK_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private const val BASELINE_REFRESH_MILLIS = 10L * 60_000L

        /**
         * Spectrum poll cadence with no Спектр watcher: one ~1–3 KB read per
         * 10 min feeds the радон indicator around the clock while connected
         * (~6 autosaved snapshots/hour, a few hundred KB per day).
         */
        private const val BACKGROUND_SPECTRUM_POLL_MILLIS = 10L * 60_000L

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
