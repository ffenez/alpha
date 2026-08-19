package app.alpha

import app.alpha.data.export.CrashLog
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import android.content.Context
import app.alpha.data.AppSettings
import app.alpha.data.BaselineRepository
import app.alpha.data.ExperimentRepository
import app.alpha.data.FingerprintRepository
import app.alpha.data.MeasurementRepository
import app.alpha.data.PreAggregateRepository
import app.alpha.data.ProfileRepository
import app.alpha.data.SessionRepository
import app.alpha.data.SpectrogramRepository
import app.alpha.data.TrackRepository
import app.alpha.analysis.evidence.AcceptedResolution
import app.alpha.analysis.evidence.ResolutionSource
import app.alpha.context.ContextController
import app.alpha.context.ContextHub
import app.alpha.context.WifiNetworkSource
import app.alpha.data.db.AppDatabase
import app.alpha.data.preagg.PreAggregator
import app.alpha.device.ConnectionState
import app.alpha.device.DeviceLinkFactory
import app.alpha.device.KableLinkFactory
import app.alpha.device.RadiaCodeScanner
import app.alpha.service.AbRunRecorder
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.service.FastPollHub
import app.alpha.service.SearchPresenceHub
import app.alpha.service.StreamTrace
import app.alpha.data.BackupManager
import app.alpha.data.BackupRepository
import app.alpha.ui.logic.ChartCache
import app.alpha.ui.feedback.FeedbackHub
import app.alpha.ui.logic.NavigateSession
import app.alpha.ui.logic.ChartTrace
import app.alpha.service.LocalBackgroundRecorder
import app.alpha.service.SpotMeasureRecorder
import app.alpha.service.ServiceStatus
import app.alpha.service.SpectrogramStore
import app.alpha.service.DeviceControlHub
import app.alpha.service.SpectrumHub
import app.alpha.ui.logic.BackgroundContext
import app.alpha.ui.logic.BackgroundRecord
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

    val trackRepository: TrackRepository by lazy { TrackRepository(database.trackDao(), database.sampleDao()) }

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

    /**
     * Последние построенные ряды карточек Главной.
     *
     * Живёт в графе, а не в экране: переход на другую вкладку выводит Главную
     * из композиции, и всё, что она помнила, умирало вместе с ней.
     */
    val chartCache: ChartCache = ChartCache()

    /** База ⇄ резервная копия: превращение строк таблиц в записи копии. */
    val backupRepository: BackupRepository by lazy {
        BackupRepository(database = database, settings = settings)
    }

    /**
     * Создание и восстановление копий. Живёт в области приложения, а не
     * экрана: копия большой истории идёт минутами, и уход с экрана не повод
     * её обрывать.
     */
    val backupManager: BackupManager by lazy {
        BackupManager(
            contentResolver = context.contentResolver,
            repository = backupRepository,
            appVersion = BuildConfig.VERSION_NAME,
            databaseSchemaVersion = AppDatabase.VERSION,
            scope = appScope,
        )
    }

    /**
     * «Наведение»: точка отсчёта и максимум переживают уход с вкладки. То, что
     * поставил человек, приложение само не отменяет.
     */
    val navigateSession: NavigateSession = NavigateSession()

    /**
     * Отклик поиска — щелчки, тон и вибрация — на любом экране приложения.
     *
     * Живёт в графе, а не в композиции экрана: прибор в руке ведут на слух и
     * на ощупь, и уход на карту или в спектр не должен обрывать этот канал.
     */
    val feedbackHub: FeedbackHub by lazy {
        FeedbackHub(
            context = context,
            settings = settings,
            status = serviceStatus,
            navigateSession = navigateSession,
            scope = appScope,
        )
    }

    /** Трасса конвейера графика: на каком этапе исчезают точки. */
    val chartTrace: ChartTrace = ChartTrace()

    /** Покадровая трасса обмена с прибором — для отладочного отчёта. */
    val streamTrace: StreamTrace = StreamTrace()

    /** Открыт ли Поиск (эксперимент), отдельно от частоты опроса. */
    val searchPresenceHub: SearchPresenceHub = SearchPresenceHub()

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
