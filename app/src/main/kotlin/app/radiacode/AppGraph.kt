package app.radiacode

import android.content.Context
import app.radiacode.data.AppSettings
import app.radiacode.data.BaselineRepository
import app.radiacode.data.ExperimentRepository
import app.radiacode.data.FingerprintRepository
import app.radiacode.data.MeasurementRepository
import app.radiacode.data.PreAggregateRepository
import app.radiacode.data.ProfileRepository
import app.radiacode.data.SessionRepository
import app.radiacode.data.TrackRepository
import app.radiacode.context.ContextController
import app.radiacode.context.ContextHub
import app.radiacode.context.WifiNetworkSource
import app.radiacode.data.db.AppDatabase
import app.radiacode.data.preagg.PreAggregator
import app.radiacode.device.ConnectionState
import app.radiacode.device.DeviceLinkFactory
import app.radiacode.device.KableLinkFactory
import app.radiacode.device.RadiaCodeScanner
import app.radiacode.service.AbRunRecorder
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.service.FastPollHub
import app.radiacode.service.LocalBackgroundRecorder
import app.radiacode.service.ServiceStatus
import app.radiacode.service.SpectrogramStore
import app.radiacode.service.DeviceControlHub
import app.radiacode.service.SpectrumHub
import app.radiacode.ui.logic.BackgroundContext
import app.radiacode.ui.logic.BackgroundRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manual dependency graph (no DI framework, ADR 001). One instance per process,
 * shared by the service and (later) UI.
 */
class AppGraph private constructor(context: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val settings: AppSettings by lazy { AppSettings(context) }

    val measurementRepository: MeasurementRepository by lazy {
        MeasurementRepository(
            sampleDao = database.sampleDao(),
            rareDataDao = database.rareDataDao(),
            eventDao = database.eventDao(),
            spectrumDao = database.spectrumDao(),
        )
    }

    val trackRepository: TrackRepository by lazy { TrackRepository(database.trackDao()) }

    /** Read side of the minute/hour pre-aggregation (ADR 004). */
    val preAggregateRepository: PreAggregateRepository by lazy {
        PreAggregateRepository(database.preAggregateDao())
    }

    /**
     * The single writer of the pre-aggregation: closes minutes and hours while
     * measuring and backfills existing history once. Started from the
     * measurement service, so there is never a second writer.
     */
    val preAggregator: PreAggregator by lazy { PreAggregator(database.preAggregateDao()) }

    /** A/B research experiments (spec §9, §16). */
    val experimentRepository: ExperimentRepository by lazy {
        ExperimentRepository(
            experimentDao = database.experimentDao(),
            sampleDao = database.sampleDao(),
            spectrumDao = database.spectrumDao(),
        )
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(
            profileDao = database.profileDao(),
            maintenanceDao = database.profileMaintenanceDao(),
            settings = settings,
            contextProfileId = contextHub.activeProfileId,
        )
    }

    /** Live measurement context (Wi-Fi auto profile, spec §3.4). */
    val contextHub: ContextHub = ContextHub()

    val contextController: ContextController by lazy {
        ContextController(
            wifi = WifiNetworkSource(context.applicationContext),
            profileDao = database.profileDao(),
            settings = settings,
            hub = contextHub,
        )
    }

    val baselineRepository: BaselineRepository by lazy {
        BaselineRepository(database.sampleDao(), database.profileDao())
    }

    /** Эталон места и сравнение с ним (ADR 005). */
    val fingerprintRepository: FingerprintRepository by lazy {
        FingerprintRepository(
            profileDao = database.profileDao(),
            sampleDao = database.sampleDao(),
            spectrumDao = database.spectrumDao(),
        )
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            sessionDao = database.sessionDao(),
            sampleDao = database.sampleDao(),
            profileDao = database.profileDao(),
            spectrumDao = database.spectrumDao(),
            trackDao = database.trackDao(),
            eventDao = database.eventDao(),
            preAggregateDao = database.preAggregateDao(),
        )
    }

    val linkFactory: DeviceLinkFactory by lazy { KableLinkFactory() }

    val scanner: RadiaCodeScanner by lazy { RadiaCodeScanner() }

    /** Live service/connection state for the UI (service is unbound). */
    val serviceStatus: ServiceStatus = ServiceStatus()

    /** Spectrum acquisition bridge: UI attaches, service polls and executes commands. */
    val spectrumHub: SpectrumHub = SpectrumHub()

    /** Звук и вибрация САМОГО прибора — они работают и без телефона. */
    val deviceControlHub: DeviceControlHub = DeviceControlHub()

    /** Поиск asks for a shorter DATA_BUF poll period while it is on screen. */
    val fastPollHub: FastPollHub = FastPollHub()

    /** In-memory спектрограмма ring (~2 ч), fed by the service's spectrum poll. */
    val spectrogramStore: SpectrogramStore = SpectrogramStore()

    /**
     * Process-lifetime scope for work that must outlive any screen. Never
     * cancelled: the graph is a singleton owned by the application object.
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Поиск → «Записать локальный фон». App-scoped so navigating away or the
     * display sleeping cannot destroy 45 s of averaging.
     */
    val localBackground: LocalBackgroundRecorder by lazy {
        LocalBackgroundRecorder(
            scope = appScope,
            samples = measurementRepository.latestSample(),
            serviceRunning = serviceStatus.serviceRunning,
            storeReference = { settings.setSearchBackgroundRaw(it.encode()) },
            contextProvider = {
                val profileId = contextHub.activeProfileId.value
                BackgroundContext(
                    profileId = profileId,
                    profileName = profileId?.let { profileRepository.byId(it)?.name },
                    deviceSerial = (serviceStatus.connection.value as? ConnectionState.Connected)
                        ?.info?.serialNumber,
                )
            },
        )
    }

    /**
     * Идущий прогон A/B-эксперимента: живёт в графе, а не в экране, поэтому
     * переход на другую вкладку и сворачивание приложения его не убивают.
     */
    val abRun: AbRunRecorder by lazy {
        AbRunRecorder(
            scope = appScope,
            experiments = experimentRepository,
            spectrumHub = spectrumHub,
            status = serviceStatus,
            captureSpectrum = { runId, nowMillis ->
                experimentRepository.captureIntervalSpectrum(
                    runId = runId,
                    liveSpectrum = spectrumHub.state.value.spectrum,
                    nowMillis = nowMillis,
                    saveSpectrum = { spectrum, label, meta ->
                        measurementRepository.saveSpectrum(
                            spectrum = spectrum,
                            accumulated = false,
                            origin = SpectrumSnapshotEntity.ORIGIN_DERIVED,
                            label = label,
                            analysisMeta = meta,
                        ).id
                    },
                )
            },
        )
    }

    /**
     * The Поиск background reference, decoded once for whoever reads it. The
     * screen never sees the storage format, and a blob written by an older or
     * broken version simply decodes to «no reference» instead of crashing it.
     */
    val searchBackground: Flow<BackgroundRecord?> by lazy {
        settings.searchBackgroundRaw.map { BackgroundRecord.decode(it) }
    }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }
    }
}
