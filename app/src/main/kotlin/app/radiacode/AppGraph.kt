package app.radiacode

import app.radiacode.data.export.CrashLog
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import android.content.Context
import app.radiacode.data.AppSettings
import app.radiacode.data.BaselineRepository
import app.radiacode.data.ExperimentRepository
import app.radiacode.data.FingerprintRepository
import app.radiacode.data.MeasurementRepository
import app.radiacode.data.PreAggregateRepository
import app.radiacode.data.ProfileRepository
import app.radiacode.data.SessionRepository
import app.radiacode.data.SpectrogramRepository
import app.radiacode.data.TrackRepository
import app.radiacode.analysis.evidence.AcceptedResolution
import app.radiacode.analysis.evidence.ResolutionSource
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
import app.radiacode.service.SpotMeasureRecorder
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
import kotlinx.coroutines.launch

/**
 * Manual dependency graph (no DI framework, ADR 001). One instance per process,
 * shared by the service and (later) UI.
 */
class AppGraph private constructor(
    context: Context,
    databaseOverride: AppDatabase? = null,
    settingsOverride: AppSettings? = null,
    withCrashHandler: Boolean = true,
) {

    /**
     * Файл журнала падений в каталоге приложения (см. [CrashLog]).
     *
     * Обработчик ставится в init графа, а не в `Application`: своего
     * `Application` у приложения нет, а граф создаётся первым обращением из
     * активности и из службы — то есть до того, как что-либо успевает упасть
     * по нашей вине.
     */
    val crashLogFile: File = File(context.filesDir, CrashLog.FILE_NAME)

    init {
        // Тестовый граф обработчик не ставит: Robolectric-прогон не должен
        // перехватывать падения чужих тестов того же процесса.
        if (withCrashHandler) installCrashHandler()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // Свой обработчик НЕ подменяет системный: падение обязано остаться
        // падением (иначе процесс останется в неопределённом состоянии), мы
        // лишь записываем его для разбора и передаём дальше.
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                CrashLog.append(
                    crashLogFile,
                    CrashLog.entry(
                        atMillis = System.currentTimeMillis(),
                        stamp = CRASH_STAMP.format(java.time.Instant.now().atZone(ZoneId.systemDefault())),
                        threadName = thread.name,
                        error = error,
                    ),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    val database: AppDatabase by lazy { databaseOverride ?: AppDatabase.build(context) }

    val settings: AppSettings by lazy { settingsOverride ?: AppSettings(context) }

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

    /** Постоянная история спектрограммы (ADR 007): чтение окна и прореживание. */
    val spectrogramRepository: SpectrogramRepository by lazy {
        SpectrogramRepository(database.spectrogramDao())
    }

    /**
     * Спектрограмма: кольцо для живого просмотра (~2 ч) плюс запись в базу.
     * Наполняется опросом службы; после перезапуска процесса поднимает окно из
     * базы, поэтому картинка не начинается с пустоты.
     */
    val spectrogramStore: SpectrogramStore by lazy { SpectrogramStore(spectrogramRepository) }

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
     * Поиск → Наведение → «Замерить здесь». App-scoped by the same rule as
     * [localBackground]: leaving the screen must not tear a running count.
     */
    val spotMeasure: SpotMeasureRecorder by lazy {
        SpotMeasureRecorder(
            scope = appScope,
            samples = measurementRepository.latestSample(),
            serviceRunning = serviceStatus.serviceRunning,
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

    /**
     * Единственный писатель [ResolutionSource]: принятая измеренная модель
     * разрешения из настроек и серийник подключённого прибора. Держатель
     * читают поиск пиков и допуски совпадения, и оба должны видеть одно и то
     * же — поэтому подписка ровно одна и живёт в графе.
     */
    private fun watchResolutionModel() {
        appScope.launch {
            settings.measuredResolutionRaw.collect {
                ResolutionSource.install(AcceptedResolution.decode(it))
            }
        }
        appScope.launch {
            serviceStatus.connection.collect { state ->
                ResolutionSource.onDevice(
                    (state as? ConnectionState.Connected)?.info?.serialNumber,
                )
            }
        }
    }

    init {
        watchResolutionModel()
    }

    companion object {
        private val CRASH_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }

        /**
         * Тестовая фабрика (Robolectric-смоук): изолированный граф на
         * in-memory БД и отдельном DataStore. НЕ трогает синглтон [get] —
         * каждый тест получает свой граф и свою базу — и не ставит
         * обработчик падений процесса. Продуктовый код через неё не ходит.
         */
        internal fun createForTest(
            context: Context,
            database: AppDatabase,
            settings: AppSettings,
        ): AppGraph = AppGraph(
            context = context.applicationContext,
            databaseOverride = database,
            settingsOverride = settings,
            withCrashHandler = false,
        )
    }
}
