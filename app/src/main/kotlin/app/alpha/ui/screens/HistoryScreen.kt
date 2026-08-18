package app.alpha.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.alpha.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.alpha.AppGraph
import app.alpha.analysis.DoseProjection
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.SpectrumMerge
import app.alpha.data.DoseUnitSetting
import app.alpha.data.SessionSummary
import app.alpha.service.SessionGate
import app.alpha.ui.logic.SessionGroups
import app.alpha.ui.logic.SessionGroup
import app.alpha.data.toSpectrum
import app.alpha.protocol.Spectrum
import app.alpha.data.db.EventEntity
import app.alpha.data.db.ProfileEntity
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.data.export.N42
import app.alpha.data.export.ProcessingMetadata
import app.alpha.data.export.RcXml
import app.alpha.data.export.SpectrumExport
import app.alpha.device.DoseUnits
import app.alpha.ui.components.DisclosureArrow
import app.alpha.ui.components.NavArrow
import app.alpha.ui.components.Hint
import app.alpha.ui.components.LocalHintsVisible
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.BarChart
import app.alpha.ui.components.BarChartSpec
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import app.alpha.ui.components.AppMenu
import app.alpha.ui.components.ConfirmDialog
import app.alpha.ui.components.EntityMenuButton
import app.alpha.ui.components.EntityMenuItem
import app.alpha.ui.components.RenameDialog
import app.alpha.ui.components.AppMenuItem
import app.alpha.ui.components.Card
import app.alpha.ui.components.HistoryRow
import app.alpha.ui.components.PREVIEW_SIZE
import app.alpha.ui.components.CheckMark
import app.alpha.ui.components.Chip
import app.alpha.ui.components.RadioMark
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.logic.ChartMapping
import app.alpha.ui.logic.DailyDose
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.Evidence
import app.alpha.ui.logic.DeletionPlan
import app.alpha.ui.logic.HistoryDeletion
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.HistorySelection
import app.alpha.ui.logic.PendingDeletion
import app.alpha.ui.logic.ProfileTree
import app.alpha.ui.logic.SpectrumFormat
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.FoodStrings
import app.alpha.ui.text.Strings
import app.alpha.ui.text.MapStrings
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.AppLanguage
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SessionRadonCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import app.alpha.ui.components.AppTextField
import app.alpha.ui.components.RouteThumbnail
import app.alpha.ui.logic.HistoryFeed
import app.alpha.ui.logic.HistoryFilter
import app.alpha.ui.logic.RouteFormat
import app.alpha.ui.logic.RouteShape
import app.alpha.ui.logic.RouteSummary
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.text.MapCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import app.alpha.baseline.BaselineState
import app.alpha.data.export.GeoJson
import app.alpha.data.export.ReportFactories
import app.alpha.data.export.SeriesExport
import app.alpha.data.export.html.ComparisonReportHtml
import app.alpha.data.export.html.ExperimentReportHtml
import app.alpha.data.export.html.ReportEvent
import app.alpha.data.export.html.RoutePrivacy
import app.alpha.data.export.html.RouteReportHtml
import app.alpha.data.export.html.RouteTrim
import app.alpha.data.export.html.SessionReportHtml
import app.alpha.data.export.html.SpectrumReportHtml
import app.alpha.data.export.SpectrumReportFactory
import app.alpha.ui.text.ExportCatalogue
import app.alpha.ui.text.ExportStrings
import app.alpha.ui.components.StatusRow
import app.alpha.ui.logic.DoseTint
import app.alpha.ui.logic.MapColorScale
import app.alpha.ui.logic.ThumbnailPoint
import app.alpha.analysis.FoodScreening
import app.alpha.data.db.ExperimentEntity
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.text.FoodCatalogue
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/**
 * Сколько времени у человека есть на «Отменить» после удаления.
 * **Инженерный параметр**: пять секунд — успеть заметить строку и передумать,
 * но не держать удалённое в подвешенном состоянии дольше, чем на него смотрят.
 */
private const val UNDO_MILLIS = 5_000L

/** Многоточие меню: редкие действия прячутся сюда, а не стоят в строке. */
private const val MENU_GLYPH = "⋮"

/**
 * С какого пропуска о нём стоит говорить.
 * **Инженерный параметр**: минута. Секунды разницы между длительностью и
 * числом измерений даёт округление на границах записи, и «пропуски 3 с»
 * сообщали бы о ровно работающем приборе.
 */
private const val GAP_VISIBLE_SECONDS = 60L
private const val REFRESH_MILLIS = 30_000L
/**
 * Глубина суточной истории дозы: самый длинный период графика.
 * Час — одна строка предагрегата, поэтому девяносто дней это ~2160 строк:
 * дешевле одного экрана списка.
 */
private const val DOSE_DAYS = 90

/** One chronological row of История. */
private sealed interface HistoryItem {
    val timestamp: Long

    data class Session(val group: SessionGroup) : HistoryItem {
        override val timestamp: Long get() = group.startedAt
    }

    data class Deviation(val event: EventEntity) : HistoryItem {
        override val timestamp: Long get() = event.timestamp
    }
}

@Immutable
private data class HistoryModel(
    val items: List<HistoryItem>,
    val totalSessions: Long,
    /**
     * Сколько записей журнала уже прочитано — не строк на экране: подряд
     * идущие записи одного места показываются одной строкой, и «показать ещё»
     * обязана сравнивать общее число именно с числом ЗАПИСЕЙ.
     */
    val loadedSessions: Int,
)

/**
 * История (SPEC «History»): dense measurement-session rows newest-first with
 * their summaries, interleaved with deviation events and their «обычно здесь
 * X» context. Windowed pages keep months of data smooth; a session opens its
 * detail.
 *
 * Накопленная доза живёт на своём экране ([DoseScreen]), вход с плитки
 * «Набралось сегодня».
 */
@Composable
fun HistoryScreen(
    graph: AppGraph,
    onOpenSession: (Long) -> Unit,
    onContinueSpectrum: (Long) -> Unit = {},
    /** Снимок спектра открывается тем же экраном Спектра, что и живой. */
    onOpenSpectrum: (Long) -> Unit = {},
    /** Отрезок маршрута — полноэкранным графиком тех же измерений. */
    onOpenChart: ((from: Long, to: Long) -> Unit)? = null,
    /** Место превышения на карте: у события журнала есть координаты. */
    onOpenPlace: ((latitude: Double, longitude: Double) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    val scope = rememberCoroutineScope()
    // Что показывает журнал: сессия, маршрут и снимок спектра — разные вещи,
    // и фильтр отвечает на вопрос «нужно вот это».
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var pages by remember { mutableIntStateOf(1) }
    var model by remember { mutableStateOf<HistoryModel?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(pages, reload) {
        while (true) {
            model = loadHistory(graph, pages * PAGE_SIZE)
            delay(REFRESH_MILLIS)
        }
    }

    // Маршруты перечитываются вместе с остальным журналом.
    var routes by remember { mutableStateOf<List<RouteSummary>>(emptyList()) }
    LaunchedEffect(reload, filter) {
        while (true) {
            routes = withContext(Dispatchers.IO) {
                graph.trackRepository.sessions().first().map {
                    graph.trackRepository.routeSummary(it)
                }
            }
            delay(REFRESH_MILLIS)
        }
    }
    // Итог измерения продукта считает репозиторий — тот же, что показывает
    // экран измерения. Второго расчёта нет.
    val foodExperiments by graph.experimentRepository.foodMeasurements()
        .collectAsState(initial = emptyList())
    var foodResults by remember { mutableStateOf(mapOf<Long, FoodScreening.Result?>()) }
    LaunchedEffect(foodExperiments, reload) {
        foodResults = withContext(Dispatchers.IO) {
            foodExperiments.associate { it.id to graph.experimentRepository.foodResult(it.id) }
        }
    }
    var openFood by remember { mutableStateOf<Long?>(null) }

    openFood?.let { id ->
        FoodScreen(graph = graph, onBack = { openFood = null }, openMeasurementId = id)
        return
    }

    var comparing by remember { mutableStateOf<List<Long>?>(null) }
    var openRoute by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf<RouteSummary?>(null) }
    var pickedRoutes by remember { mutableStateOf(setOf<Long>()) }
    var exportingRoute by remember { mutableStateOf<RouteSummary?>(null) }
    val context = LocalContext.current
    // Удаление откладывается: строка исчезает сразу, из базы уходит через
    // несколько секунд, и «Отменить» возвращает её целиком.
    var deletingRoutes by remember { mutableStateOf(setOf<Long>()) }
    var confirmingDelete by remember { mutableStateOf<List<RouteSummary>?>(null) }
    // Шкала миниатюр — та же, что у следа на карте.
    val routeScaleMode by graph.settings.mapColorScale
        .collectAsState(initial = MapColorScale.ABSOLUTE)
    val routeTintFactor by graph.settings.doseTintFactor
        .collectAsState(initial = DoseTint.DEFAULT_FACTOR)
    val routeBaseline by graph.serviceStatus.baseline.collectAsState()
    val routeScale = remember(routeScaleMode, routeTintFactor, routeBaseline, routes) {
        val band = (routeBaseline as? BaselineState.Active)?.baseline
            ?.let { it.doseLowMicroSvH to it.doseHighMicroSvH }
        TrackMap.scaleFor(
            mode = routeScaleMode,
            usualBand = band,
            factor = routeTintFactor,
            values = routes.mapNotNull { it.avgDoseMicroSvH },
        )
    }

    LaunchedEffect(deletingRoutes) {
        val doomed = deletingRoutes
        if (doomed.isEmpty()) return@LaunchedEffect
        delay(UNDO_MILLIS)
        doomed.forEach { graph.trackRepository.delete(it) }
        deletingRoutes = deletingRoutes - doomed
        reload += 1
    }

    // Маршрут уезжает в одном из четырёх форматов, и любой несёт координаты —
    // поэтому после формата задаётся вопрос о них.
    var routeFormat by remember { mutableStateOf<ExportFile?>(null) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    var exportingSelection by remember { mutableStateOf(false) }
    var exportingSession by remember { mutableStateOf<Long?>(null) }
    var exportingSpectrum by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var renamingSpectrum by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var deletingSpectrum by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var profileForSpectrum by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var exportingStudy by remember { mutableStateOf<ExperimentEntity?>(null) }
    var renamingStudy by remember { mutableStateOf<ExperimentEntity?>(null) }
    var deletingStudy by remember { mutableStateOf<ExperimentEntity?>(null) }
    val f = FoodCatalogue.of(strings.language)
    val mapStrings = MapCatalogue.of(strings.language)
    var profileForSession by remember { mutableStateOf<Long?>(null) }
    val e = ExportCatalogue.of(strings.language)
    val fileSaver = rememberFileSaver { ok -> exportNote = if (ok) e.saved else e.failed }
    val folderSaver = rememberFolderSaver { saved, failed ->
        exportNote = if (failed == 0) e.filesSaved(saved) else e.failed
    }

    fun exportRoute(route: RouteSummary, format: ExportFile, privacy: RoutePrivacy) {
        scope.launch {
            val all = graph.trackRepository.points(route.id).first()
            val kept = if (privacy == RoutePrivacy.FULL) all else RouteTrim.ends(all)
            val title = RouteFormat.title(route, System.currentTimeMillis(), h)
            val name = SeriesExport.fileName(route.startedAt, format.extension)
            when (format) {
                ExportFile.HTML -> fileSaver.save(
                    format,
                    name,
                    RouteReportHtml.render(
                        ReportFactories.route(
                            summary = route,
                            points = all,
                            privacy = privacy,
                            appName = REPORT_APP,
                            appVersion = appVersionName(context) ?: "",
                            language = strings.language,
                        ),
                    ),
                )
                ExportFile.GEOJSON -> fileSaver.save(format, name, GeoJson.route(kept, title))
                ExportFile.GPX -> fileSaver.save(format, name, SeriesExport.gpx(kept, title))
                else -> fileSaver.save(format, name, SeriesExport.trackCsv(kept))
            }
        }
    }

    // Снимок спектра: форматы, имя и удаление — из «⋮» его строки.
    exportingSpectrum?.let { entity ->
        EntityExportSheet(
            title = e.export,
            groups = spectrumExportGroups(
                entity = entity,
                e = e,
                appVersion = appVersionName(context),
                language = strings.language,
                saver = fileSaver,
                onPicked = { exportingSpectrum = null },
            ),
            onDismiss = { exportingSpectrum = null },
        )
    }
    renamingSpectrum?.let { entity ->
        RenameDialog(
            title = h.routeRename,
            initial = entity.label.orEmpty(),
            placeholder = h.routeNameHint,
            onSave = { name ->
                renamingSpectrum = null
                scope.launch {
                    graph.measurementRepository.renameSpectrum(entity.id, name)
                    reload += 1
                }
            },
            onDismiss = { renamingSpectrum = null },
        )
    }
    // Профиль снимка правится задним числом: меняется запись о месте съёмки,
    // отсчёты, время и калибровка остаются прежними.
    profileForSpectrum?.let { entity ->
        val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
        SessionProfileDialog(
            startedAt = entity.timestamp,
            profileId = entity.profileId,
            profiles = profiles,
            onPick = { profileId ->
                profileForSpectrum = null
                scope.launch {
                    graph.measurementRepository.setSpectrumProfile(
                        id = entity.id,
                        profileId = profileId,
                        profileName = profiles.firstOrNull { it.id == profileId }?.name,
                    )
                    reload += 1
                }
            },
            onDismiss = { profileForSpectrum = null },
        )
    }

    deletingSpectrum?.let { entity ->
        ConfirmDialog(
            title = h.routeDeleteTitle(1),
            body = h.routeDeleteBody,
            confirmText = strings.delete,
            onConfirm = {
                deletingSpectrum = null
                scope.launch { graph.sessionRepository.delete(emptySet(), setOf(entity.id)) }
            },
            onDismiss = { deletingSpectrum = null },
        )
    }

    // Исследование продукта: то же обращение, что и с остальными записями.
    exportingStudy?.let { study ->
        EntityExportSheet(
            title = e.export,
            groups = listOf(
                ExportGroup(
                    title = e.groupReport,
                    options = listOf(
                        ExportOptions.report(e) {
                            val target = study
                            exportingStudy = null
                            scope.launch {
                                val runs = graph.experimentRepository.runData(
                                    graph.experimentRepository.runs(target.id),
                                )
                                fileSaver.save(
                                    ExportFile.HTML,
                                    SeriesExport.fileName(target.createdAt, "html"),
                                    ExperimentReportHtml.render(
                                        ReportFactories.experiment(
                                            entity = target,
                                            profileName = null,
                                            runs = runs,
                                            comparison = null,
                                            verdictText = studyStatus(
                                                foodResults[target.id],
                                                f,
                                            ),
                                            appName = REPORT_APP,
                                            appVersion = appVersionName(context) ?: "",
                                            language = strings.language,
                                        ),
                                    ),
                                )
                            }
                        },
                    ),
                ),
                ExportGroup(
                    title = e.groupTable,
                    options = listOf(
                        ExportOptions.table(e) {
                            val target = study
                            exportingStudy = null
                            scope.launch {
                                val runs = graph.experimentRepository.runData(
                                    graph.experimentRepository.runs(target.id),
                                )
                                fileSaver.save(
                                    ExportFile.CSV,
                                    SeriesExport.fileName(target.createdAt, "csv"),
                                    ReportFactories.experimentCsv(runs),
                                )
                            }
                        },
                    ),
                ),
            ),
            onDismiss = { exportingStudy = null },
        )
    }
    renamingStudy?.let { study ->
        RenameDialog(
            title = h.routeRename,
            initial = study.note,
            placeholder = f.sampleName,
            onSave = { name ->
                renamingStudy = null
                scope.launch {
                    graph.experimentRepository.setNote(study.id, name.trim())
                    reload += 1
                }
            },
            onDismiss = { renamingStudy = null },
        )
    }
    deletingStudy?.let { study ->
        ConfirmDialog(
            title = h.studyDeleteTitle(study.note.ifBlank { f.title }),
            body = h.studyDeleteBody,
            confirmText = strings.delete,
            onConfirm = {
                deletingStudy = null
                scope.launch {
                    // Идущий прогон останавливается первым: иначе он допишет
                    // результат в уже удалённое исследование.
                    graph.abRun.stop()
                    if (graph.settings.activeFoodExperimentId.first() == study.id) {
                        graph.settings.setActiveFoodExperimentId(null)
                    }
                    graph.experimentRepository.delete(study.id)
                    reload += 1
                }
            },
            onDismiss = { deletingStudy = null },
        )
    }

    // Сессия из списка уносится теми же тремя форматами, что и с её экрана.
    exportingSession?.let { id ->
        var summary by remember(id) { mutableStateOf<SessionSummary?>(null) }
        LaunchedEffect(id) { summary = graph.sessionRepository.summary(id) }
        summary?.let { found ->
            EntityExportSheet(
                title = e.export,
                groups = listOf(
                    ExportGroup(
                        title = e.groupReport,
                        options = listOf(
                            ExportOptions.report(e) {
                                exportingSession = null
                                scope.launch {
                                    fileSaver.save(
                                        ExportFile.HTML,
                                        SeriesExport.fileName(found.startedAt, "html"),
                                        SessionReportHtml.render(
                                            ReportFactories.session(
                                                summary = found,
                                                samples = samplesOf(graph, found),
                                                events = emptyList(),
                                                appName = REPORT_APP,
                                                appVersion = appVersionName(context) ?: "",
                                                language = strings.language,
                                            ),
                                        ),
                                    )
                                }
                            },
                        ),
                    ),
                    ExportGroup(
                        title = e.groupExchange,
                        options = listOf(
                            ExportOptions.data(e) {
                                exportingSession = null
                                scope.launch {
                                    fileSaver.save(
                                        ExportFile.JSON,
                                        SeriesExport.fileName(found.startedAt, "json"),
                                        ReportFactories.sessionJson(
                                            found,
                                            samplesOf(graph, found),
                                        ),
                                    )
                                }
                            },
                        ),
                    ),
                    ExportGroup(
                        title = e.groupTable,
                        options = listOf(
                            ExportOptions.table(e) {
                                exportingSession = null
                                scope.launch {
                                    fileSaver.save(
                                        ExportFile.CSV,
                                        SeriesExport.fileName(found.startedAt, "csv"),
                                        SeriesExport.csv(samplesOf(graph, found)),
                                    )
                                }
                            },
                        ),
                    ),
                ),
                onDismiss = { exportingSession = null },
            )
        }
    }

    // Профиль записи правится и из списка.
    profileForSession?.let { id ->
        var summary by remember(id) { mutableStateOf<SessionSummary?>(null) }
        val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
        LaunchedEffect(id) { summary = graph.sessionRepository.summary(id) }
        summary?.let { found ->
            SessionProfileDialog(
                startedAt = found.startedAt,
                profileId = found.profileId,
                profiles = profiles,
                onPick = { profileId ->
                    profileForSession = null
                    scope.launch {
                        graph.sessionRepository.reassignProfile(id, profileId)
                        reload += 1
                    }
                },
                onDismiss = { profileForSession = null },
            )
        }
    }

    // Формат → координаты → системный диалог: каждый вопрос задаётся один раз.
    exportingRoute?.let { route ->
        if (routeFormat == null) {
            EntityExportSheet(
                title = e.export,
                groups = listOf(
                    ExportGroup(
                        title = e.groupReport,
                        options = listOf(
                            ExportOptions.report(e) { routeFormat = ExportFile.HTML },
                        ),
                    ),
                    ExportGroup(
                        title = e.groupExchange,
                        options = listOf(
                            ExportOptions.map(e) { routeFormat = ExportFile.GEOJSON },
                            ExportOptions.track(e) { routeFormat = ExportFile.GPX },
                        ),
                    ),
                    ExportGroup(
                        title = e.groupTable,
                        options = listOf(
                            ExportOptions.table(e) { routeFormat = ExportFile.CSV },
                        ),
                    ),
                ),
                onDismiss = { exportingRoute = null },
            )
        } else {
            val format = routeFormat!!
            RoutePrivacyDialog(
                allowNoCoordinates = format == ExportFile.HTML,
                onPick = { privacy ->
                    exportingRoute = null
                    routeFormat = null
                    exportRoute(route, format, privacy)
                },
                onDismiss = { exportingRoute = null; routeFormat = null },
            )
        }
    }

    confirmingDelete?.let { doomed ->
        RouteDeleteDialog(
            routes = doomed,
            onDismiss = { confirmingDelete = null },
            onConfirm = {
                deletingRoutes = deletingRoutes + doomed.map { it.id }
                pickedRoutes = emptySet()
                confirmingDelete = null
            },
        )
    }

    openRoute?.let { routeId ->
        RouteMapScreen(
            graph = graph,
            routeId = routeId,
            onBack = { openRoute = null },
            onOpenChart = onOpenChart,
        )
        return
    }
    comparing?.let { ids ->
        RouteCompareScreen(graph = graph, routeIds = ids, onBack = { comparing = null })
        return
    }
    renaming?.let { route ->
        RouteRenameDialog(
            route = route,
            onDismiss = { renaming = null },
            onSave = { name ->
                scope.launch {
                    graph.trackRepository.rename(route.id, name)
                    renaming = null
                    reload += 1
                }
            },
        )
    }

    // Уборка журнала: один режим выбора на сессии и спектры — они лежат в
    // одном списке.
    var selection by remember { mutableStateOf(HistorySelection()) }
    // «Выбрать всё» обязано знать, что такое «всё»: список id снимков поднят
    // сюда и передаётся вниз.
    val savedSpectra by graph.measurementRepository.savedSpectra(SPECTRA_LIMIT)
        .collectAsState(initial = emptyList())
    var confirming by remember { mutableStateOf<PendingDeletion?>(null) }
    // Что вышло из объединения снимков — рядом со списком, где их и выбирали.
    var mergeNote by remember { mutableStateOf<String?>(null) }

    confirming?.let { pending ->
        DeleteConfirmDialog(
            plan = pending.plan,
            onConfirm = {
                scope.launch {
                    // Удаляется ровно то, что посчитано в плане: набор целей
                    // приехал вместе с ним.
                    graph.sessionRepository.delete(pending.sessions, pending.spectra)
                    if (pending.fromSelection) selection = HistorySelection()
                    confirming = null
                    reload += 1
                }
            },
            onDismiss = { confirming = null },
        )
    }

    // Правка профиля сессии живёт в самой сессии (spec §20).
    // Comparator flow is self-contained in История: picking two snapshots
    // swaps the screen for the comparator; back returns to the list.
    var comparePair by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    comparePair?.let { (firstId, secondId) ->
        SpectrumCompareScreen(
            graph = graph,
            firstId = firstId,
            secondId = secondId,
            onBack = { comparePair = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Сверху половина шага: название экрана подписано во вкладке снизу.
            .padding(start = Dimens.space3, end = Dimens.space3, bottom = Dimens.space3)
            .padding(top = Dimens.space2),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (selection.active) {
                Chip(
                    text = strings.cancel,
                    color = colors.ink2,
                    onClick = { selection = selection.cancel() },
                )
            } else {
                // Счётчик считает то, что на экране, и служит входом в режим
                // выбора: в него везде входят долгим нажатием на запись.
                model?.let { m ->
                    val total = when (filter) {
                        HistoryFilter.ALL -> h.records(
                            m.totalSessions.toInt() + routes.size + savedSpectra.size +
                                foodExperiments.size,
                        )
                        HistoryFilter.SESSIONS -> h.sessions(m.totalSessions.toInt())
                        HistoryFilter.ROUTES -> h.routes(routes.size)
                        HistoryFilter.SPECTRA -> h.spectra(savedSpectra.size)
                        HistoryFilter.FOOD -> h.studies(foodExperiments.size)
                    }
                    Chip(text = total, color = colors.ink2)
                }
            }
        }
        if (selection.active) {
            // Идущая сессия не удаляется и в «всё» не входит.
            val selectableSessions = model?.items.orEmpty()
                .filterIsInstance<HistoryItem.Session>()
                .filter { !it.group.running }
                .flatMap { it.group.ids }
            val selectableSpectra = savedSpectra.map { it.id }
            val allSelected = selection.isAllSelected(selectableSessions, selectableSpectra)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Chip(
                    text = if (allSelected) strings.clearAll else strings.selectAll,
                    color = if (allSelected) colors.dataText else colors.ink2,
                    selected = allSelected,
                    onClick = {
                        selection = selection.toggleAll(selectableSessions, selectableSpectra)
                    },
                )
                Text(
                    text = if (selection.isEmpty) {
                        HistoryDeletion.emptyHint(h)
                    } else {
                        strings.selectedCount(selection.count)
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }

        // Пять вкладок; ряд прокручивается: пять равных долей на узком экране
        // превращают подписи в огрызки.
        Segmented(
            options = listOf(
                h.filterAll,
                h.filterSessions,
                h.filterRoutes,
                h.filterSpectra,
                h.filterFood,
            ),
            selectedIndex = HistoryFilter.entries.indexOf(filter),
            onSelect = { filter = HistoryFilter.entries[it] },
            scrollable = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val m = model
        if (m == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = strings.readingJournal, style = type.bodySmall, color = colors.muted)
            }
        } else {
            val now = System.currentTimeMillis()
            val visibleRoutes = routes.filter { it.id !in deletingRoutes }
            val showSessions = filter == HistoryFilter.ALL || filter == HistoryFilter.SESSIONS
            // Одна лента: сессии, маршруты, снимки и исследования стоят в том
            // порядке, в каком произошли, и различаются содержанием строки.
            val entries = buildList {
                if (showSessions) {
                    m.items.forEach { item ->
                        when (item) {
                            is HistoryItem.Session -> add(FeedEntry.Session(item.group))
                            is HistoryItem.Deviation -> add(FeedEntry.Deviation(item.event))
                        }
                    }
                }
                if (filter == HistoryFilter.ALL || filter == HistoryFilter.ROUTES) {
                    visibleRoutes.forEach { add(FeedEntry.Route(it)) }
                }
                if (filter == HistoryFilter.ALL || filter == HistoryFilter.SPECTRA) {
                    savedSpectra.forEach { add(FeedEntry.Spectrum(it)) }
                }
                if (filter == HistoryFilter.ALL || filter == HistoryFilter.FOOD) {
                    foodExperiments.forEach { add(FeedEntry.Study(it, foodResults[it.id])) }
                }
            }

            if (entries.isEmpty()) {
                EmptyFeedCard(filter)
            } else {
                for (day in HistoryFeed.group(entries, timestamp = { it.timestamp })) {
                    Text(
                        text = HistoryFormat.dayHeader(day.startOfDayMillis, now, s = h),
                        style = type.labelSmall,
                        color = colors.ink2,
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            day.entries.forEachIndexed { index, entry ->
                                if (index > 0) AppDivider()
                                when (entry) {
                                    is FeedEntry.Session -> {
                                        val group = entry.group
                                        val ids = group.ids
                                        HistoryRow(
                                            title = group.profileName ?: strings.noProfile,
                                            status = when {
                                                group.running && selection.active ->
                                                    strings.runningCannotDelete
                                                group.running -> strings.running
                                                else -> null
                                            },
                                            statusColor = if (selection.active) {
                                                colors.muted
                                            } else {
                                                colors.ok
                                            },
                                            subtitle = sessionSubtitle(group, now, h),
                                            detail = sessionDetail(
                                                group = group,
                                                unit = unit,
                                                strings = strings,
                                                h = h,
                                                // Пропуски в записи — данные, а
                                                // не пояснение: запись с
                                                // часовым провалом обязана
                                                // отличаться от сплошной при
                                                // любых настройках.
                                                withGaps = true,
                                            ),
                                            check = if (selection.active && !group.running) {
                                                ids.all { it in selection.sessions }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                if (selection.active) {
                                                    if (!group.running) {
                                                        selection = ids.fold(selection) { acc, id ->
                                                            acc.toggleSession(id)
                                                        }
                                                    }
                                                } else {
                                                    onOpenSession(ids.last())
                                                }
                                            },
                                            onLongClick = {
                                                if (!group.running) {
                                                    selection = ids.fold(selection.activate()) {
                                                        acc, id ->
                                                        acc.toggleSession(id)
                                                    }
                                                }
                                            },
                                            menu = if (selection.active) {
                                                emptyList()
                                            } else {
                                                EntityMenus.session(
                                                    strings = strings,
                                                    export = e,
                                                    canDelete = !group.running,
                                                    onExport = { exportingSession = ids.last() },
                                                    onProfile = { profileForSession = ids.last() },
                                                    onDelete = {
                                                        scope.launch {
                                                            confirming = PendingDeletion(
                                                                plan = graph.sessionRepository
                                                                    .deletionPlan(
                                                                        sessionIds = ids.toSet(),
                                                                        spectrumIds = emptySet(),
                                                                    ),
                                                                sessions = ids.toSet(),
                                                            )
                                                        }
                                                    },
                                                )
                                            },
                                        )
                                    }

                                    is FeedEntry.Route -> {
                                        val route = entry.route
                                        HistoryRow(
                                            title = RouteFormat.title(route, now, h),
                                            status = when {
                                                route.running -> h.routeRecording
                                                route.interrupted -> h.routeInterrupted
                                                else -> null
                                            },
                                            statusColor = if (route.running) {
                                                colors.ok
                                            } else {
                                                colors.warn
                                            },
                                            subtitle = routeSubtitle(route, now, h, mapStrings),
                                            detail = routeDetail(route, unit, strings),
                                            preview = {
                                                RoutePreview(
                                                    graph = graph,
                                                    route = route,
                                                    scale = routeScale,
                                                )
                                            },
                                            check = if (pickedRoutes.isNotEmpty()) {
                                                route.id in pickedRoutes
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                if (pickedRoutes.isNotEmpty()) {
                                                    pickedRoutes = if (route.id in pickedRoutes) {
                                                        pickedRoutes - route.id
                                                    } else {
                                                        pickedRoutes + route.id
                                                    }
                                                } else {
                                                    openRoute = route.id
                                                }
                                            },
                                            onLongClick = { pickedRoutes = pickedRoutes + route.id },
                                            menu = EntityMenus.route(
                                                strings = strings,
                                                export = e,
                                                history = h,
                                                canCompare = routes.size >= 2,
                                                onExport = { exportingRoute = route },
                                                onCompare = {
                                                    pickedRoutes = pickedRoutes + route.id
                                                },
                                                onRename = { renaming = route },
                                                onDelete = { confirmingDelete = listOf(route) },
                                            ),
                                        )
                                    }

                                    is FeedEntry.Spectrum -> {
                                        val entity = entry.entity
                                        HistoryRow(
                                            // Дата стоит в заголовке дня,
                                            // время — во второй строке.
                                            title = entity.label ?: h.spectrumTitle,
                                            subtitle = HistoryFormat.dayTime(
                                                entity.timestamp,
                                                now,
                                                s = h,
                                            ) + " · " + SpectrumFormat.accumulationClock(
                                                entity.durationSeconds,
                                            ),
                                            detail = spectrumProvenance(entity, strings),
                                            check = if (selection.active) {
                                                entity.id in selection.spectra
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                if (selection.active) {
                                                    selection = selection.toggleSpectrum(entity.id)
                                                } else {
                                                    onOpenSpectrum(entity.id)
                                                }
                                            },
                                            onLongClick = {
                                                selection = selection.activate()
                                                    .toggleSpectrum(entity.id)
                                            },
                                            menu = if (selection.active) {
                                                emptyList()
                                            } else {
                                                EntityMenus.spectrum(
                                                    strings = strings,
                                                    export = e,
                                                    history = h,
                                                    canCompare = savedSpectra.size >= 2,
                                                    onExport = { exportingSpectrum = entity },
                                                    onCompare = {
                                                        selection = selection.activate()
                                                            .toggleSpectrum(entity.id)
                                                    },
                                                    onContinue = { onContinueSpectrum(entity.id) },
                                                    onRename = { renamingSpectrum = entity },
                                                    onProfile = { profileForSpectrum = entity },
                                                    onDelete = { deletingSpectrum = entity },
                                                )
                                            },
                                        )
                                    }

                                    is FeedEntry.Study -> {
                                        val study = entry.experiment
                                        HistoryRow(
                                            title = study.note.ifBlank { f.title },
                                            subtitle = studySubtitle(study, now, h),
                                            detail = studyStatus(entry.result, f),
                                            onClick = { openFood = study.id },
                                            menu = EntityMenus.study(
                                                strings = strings,
                                                export = e,
                                                history = h,
                                                onExport = { exportingStudy = study },
                                                onRename = { renamingStudy = study },
                                                onDelete = { deletingStudy = study },
                                            ),
                                        )
                                    }

                                    is FeedEntry.Deviation -> DeviationRow(
                                        event = entry.event,
                                        unit = unit,
                                        // Событие ведёт туда, где его видно:
                                        // с координатами — на карту, без них
                                        // — на график того же времени.
                                        onClick = entry.event.let { event ->
                                            val lat = event.latitude
                                            val lon = event.longitude
                                            when {
                                                lat != null && lon != null &&
                                                    onOpenPlace != null ->
                                                    { { onOpenPlace(lat, lon) } }
                                                onOpenChart != null -> {
                                                    {
                                                        onOpenChart(
                                                            event.timestamp - EVENT_CHART_MARGIN,
                                                            event.timestamp + EVENT_CHART_MARGIN,
                                                        )
                                                    }
                                                }
                                                else -> null
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Выбор и отмена удаления маршрутов — строки действий под лентой.
            if (pickedRoutes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = h.routeCompareCount(pickedRoutes.size),
                        onClick = { comparing = pickedRoutes.toList() },
                        primary = pickedRoutes.size >= 2,
                        enabled = pickedRoutes.size >= 2,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = strings.delete,
                        onClick = { confirmingDelete = routes.filter { it.id in pickedRoutes } },
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = strings.cancel,
                        onClick = { pickedRoutes = emptySet() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (deletingRoutes.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Text(
                        text = h.routesDeleted(deletingRoutes.size),
                        style = type.footnote,
                        color = colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        text = h.routeUndo,
                        color = colors.dataText,
                        onClick = { deletingRoutes = emptySet() },
                    )
                }
            }
            mergeNote?.let {
                Text(text = it, style = type.footnote, color = colors.muted)
            }

            if (showSessions && m.loadedSessions < m.totalSessions) {
                // Компактный чип: догрузка — продолжение списка, а не главное
                // действие экрана.
                Chip(
                    text = strings.showMore,
                    color = colors.ink2,
                    onClick = { pages += 1 },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            // Действия выбранного — одной строкой внизу: «сравнить» и
            // «объединить» появляются, когда выбрано столько, сколько им нужно.
            AnimatedVisibility(
                visible = selection.active,
                enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
                exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        val chosenSpectra = savedSpectra.filter { it.id in selection.spectra }
                        if (chosenSpectra.size >= 2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                                AppButton(
                                    text = strings.compare,
                                    enabled = chosenSpectra.size == 2,
                                    onClick = {
                                        comparePair = chosenSpectra[0].id to chosenSpectra[1].id
                                        selection = selection.cancel()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                AppButton(
                                    text = strings.mergeAction(chosenSpectra.size),
                                    onClick = {
                                        scope.launch {
                                            when (val saved = mergeSnapshots(graph, chosenSpectra)) {
                                                is MergeResult.Saved -> {
                                                    selection = selection.cancel()
                                                    mergeNote = strings.mergedSaved(
                                                        saved.label.orEmpty(),
                                                    )
                                                }
                                                is MergeResult.Refused -> mergeNote =
                                                    strings.mergeImpossible + " — " + saved.reason
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        // Выгрузка выбранного: общий отчёт или по файлу на
                        // запись в выбранную папку. Таблица и данные снимаются
                        // с самой записи.
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                            AppButton(
                                text = e.export,
                                enabled = !selection.isEmpty,
                                onClick = { exportingSelection = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (exportingSelection) {
                            EntityExportSheet(
                                title = e.export,
                                groups = listOf(ExportGroup(e.groupReport, listOf(
                                    ExportOptions.oneReport(e) {
                                        exportingSelection = false
                                        scope.launch {
                                            val records = selection.sessions.mapNotNull { id ->
                                                val summary = graph.sessionRepository.summary(id)
                                                    ?: return@mapNotNull null
                                                summary to samplesOf(graph, summary)
                                            }.sortedBy { it.first.startedAt }
                                            if (records.isEmpty()) {
                                                exportNote = e.nothingSelected
                                            } else {
                                                fileSaver.save(
                                                    ExportFile.HTML,
                                                    SeriesExport.fileName(
                                                        records.first().first.startedAt,
                                                        "html",
                                                    ),
                                                    ComparisonReportHtml.render(
                                                        ReportFactories.comparison(
                                                            records = records,
                                                            appName = REPORT_APP,
                                                            appVersion =
                                                                appVersionName(context) ?: "",
                                                            language = strings.language,
                                                        ),
                                                    ),
                                                )
                                                selection = selection.cancel()
                                            }
                                        }
                                    },
                                    ExportOptions.separateFiles(e) {
                                        exportingSelection = false
                                        val sessions = selection.sessions
                                        val spectra = selection.spectra
                                        val version = appVersionName(context) ?: ""
                                        folderSaver.save {
                                            reportsFor(
                                                graph = graph,
                                                sessionIds = sessions,
                                                spectrumIds = spectra,
                                                appVersion = version,
                                                language = strings.language,
                                            )
                                        }
                                        selection = selection.cancel()
                                    },
                                ))),
                                onDismiss = { exportingSelection = false },
                            )
                        }
                        exportNote?.let {
                            Text(text = it, style = type.footnote, color = colors.muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                            AppButton(
                                text = HistoryDeletion.actionLabel(selection, h),
                                onClick = {
                                    scope.launch {
                                        confirming = PendingDeletion(
                                            plan = graph.sessionRepository.deletionPlan(
                                                sessionIds = selection.sessions,
                                                spectrumIds = selection.spectra,
                                            ),
                                            sessions = selection.sessions,
                                            spectra = selection.spectra,
                                            fromSelection = true,
                                        )
                                    }
                                },
                                primary = !selection.isEmpty,
                                enabled = !selection.isEmpty,
                                modifier = Modifier.weight(1f),
                            )
                            AppButton(
                                text = strings.cancel,
                                onClick = { selection = selection.cancel() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}





/**
 * Измерения продуктов в журнале: что за продукт, когда, сколько копили и что
 * вышло. Числа фона и образца стоят тусклой строкой — по ним проверяют вывод.
 */
@Composable
private fun RouteDeleteDialog(
    routes: List<RouteSummary>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = h.routeDeleteTitle(routes.size),
                    style = type.label,
                    color = colors.ink,
                )
                Text(text = h.routeDeleteBody, style = type.bodySmall, color = colors.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = strings.delete,
                        onClick = onConfirm,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = strings.cancel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Имя маршрута задаётся после прогулки и не обязательно: пока его нет, список
 * подписывает маршрут датой.
 */
@Composable
private fun RouteRenameDialog(
    route: RouteSummary,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    var text by remember(route.id) { mutableStateOf(route.name) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = h.routeRename, style = type.label, color = colors.ink)
                AppTextField(
                    value = text,
                    onValueChange = { text = RouteFormat.cleanName(it) },
                    placeholder = h.routeNameHint,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = strings.saveName,
                        onClick = { onSave(text) },
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = strings.cancel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DataItem(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = type.valueSmall, color = colors.ink2)
        Text(
            text = value,
            style = type.valueSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = valueColor ?: colors.ink,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviationRow(
    event: EventEntity,
    unit: DoseUnitSetting,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val kind = when (event.source) {
        EventEntity.SOURCE_DEVIATION -> strings.deviation
        else -> strings.excursionPoint
    }
    val located = event.latitude != null && event.longitude != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⚠ $kind", style = type.label, color = colors.warn)
            Spacer(Modifier.weight(1f))
            Text(
                text = HistoryFormat.dayTime(event.timestamp, now, s = h),
                style = type.footnote,
                color = colors.ink2,
            )
            if (onClick != null) {
                Spacer(Modifier.width(Dimens.space1))
                // Куда ведёт строка, сказано значком: на карту, если у события
                // есть место, иначе на график того же времени.
                Text(
                    text = if (located) h.openOnMap else h.openOnChart,
                    style = type.footnote,
                    color = colors.dataText,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            event.doseRate?.let {
                DataItem(
                    label = DoseFormat.rateUnitLabel(unit, s = strings),
                    value = DoseFormat.rate(DoseUnits.rawToMicroSievertPerHour(it), unit),
                    valueColor = colors.warn,
                )
            }
            // param1 of a deviation stores the baseline typical high, nSv/h.
            if (event.source == EventEntity.SOURCE_DEVIATION && event.param1 > 0) {
                DataItem(strings.usually, DoseFormat.rate(event.param1 / 1000f, unit))
            }
        }
    }
}

// --- saved spectra: export + comparator entry ---

/**
 * Окно графика вокруг события журнала: по четверти часа с каждой стороны.
 * **Инженерный параметр**: столько нужно, чтобы увидеть подъём и спад вокруг
 * момента, а не одну точку в середине пустого поля.
 */
private const val EVENT_CHART_MARGIN = 15L * 60_000L

/** Сколько снимков спектра держит лента: список — не архив прибора. */
private const val SPECTRA_LIMIT = 30

private sealed interface MergeResult {
    data class Saved(val label: String) : MergeResult
    data class Refused(val reason: String) : MergeResult
}

/**
 * Channel-wise merge of the chosen snapshots ([SpectrumMerge]) saved as a new
 * user snapshot labeled «merge». Refusals (calibration/grid mismatch) come
 * back verbatim — the math layer words them honestly.
 */
private suspend fun mergeSnapshots(
    graph: AppGraph,
    chosen: List<SpectrumSnapshotEntity>,
): MergeResult {
    val inputs = chosen.map { entity ->
        val s = entity.toSpectrum()
        SpectrumMerge.Input(
            counts = s.counts,
            durationSeconds = s.durationSeconds,
            calibration = EnergyCalibration(s.a0, s.a1, s.a2),
            name = SpectrumExport.title(entity),
        )
    }
    return when (val outcome = SpectrumMerge.merge(inputs)) {
        is SpectrumMerge.Outcome.Invalid -> MergeResult.Refused(outcome.reason)
        is SpectrumMerge.Outcome.Ok -> {
            // Метка хранится в базе и не зависит от языка интерфейса.
            val label = "merge · ${chosen.size}"
            graph.measurementRepository.saveSpectrum(
                Spectrum(
                    durationSeconds = outcome.durationSeconds,
                    a0 = outcome.calibration.a0,
                    a1 = outcome.calibration.a1,
                    a2 = outcome.calibration.a2,
                    counts = outcome.counts,
                ),
                accumulated = false,
                origin = SpectrumSnapshotEntity.ORIGIN_USER,
                label = label,
                // Спец §22: сумма — производный результат, метод и версии
                // алгоритмов едут вместе со снимком и в экспорт.
                analysisMeta = ProcessingMetadata.stamp(
                    method = "channel_sum (merge)",
                    algorithms = listOf("spectrum_merge"),
                    extra = mapOf(
                        "sourceIds" to chosen.joinToString(",") { it.id.toString() },
                        "durationSeconds" to outcome.durationSeconds.toString(),
                    ),
                ),
            )
            MergeResult.Saved(label)
        }
    }
}


private suspend fun loadHistory(graph: AppGraph, sessionLimit: Int): HistoryModel {
    val now = System.currentTimeMillis()
    val repo = graph.sessionRepository

    val sessions = repo.page(offset = 0, limit = sessionLimit)
    val totalSessions = repo.count()

    // Deviations across the visible span (down to the oldest loaded session).
    val eventsFrom = sessions.lastOrNull()?.startedAt ?: (now - 24L * 3600_000)
    val events = repo.deviationEvents(from = eventsFrom, to = now)

    // Подряд идущие записи одного места показываются одной строкой: их рвали
    // разрывы связи и перезапуски службы. Журнал в базе не переписывается —
    // склейка живёт в показе.
    val groups = SessionGroups.merge(
        sessions = sessions,
        graceMillis = SessionGate.DEFAULT_GRACE_MILLIS,
        nowMillis = now,
    )
    val items = (
        groups.map { HistoryItem.Session(it) } + events.map { HistoryItem.Deviation(it) }
        ).sortedByDescending { it.timestamp }

    return HistoryModel(
        items = items,
        totalSessions = totalSessions,
        loadedSessions = sessions.size,
    )
}

@Composable
private fun DeleteConfirmDialog(
    plan: DeletionPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = HistoryDeletion.title(plan, h), style = type.title, color = colors.ink)
                Text(
                    text = HistoryDeletion.body(plan, h),
                    style = type.bodySmall,
                    color = colors.ink2,
                )
                Text(
                    text = HistoryDeletion.keepsWording(plan, h),
                    style = type.footnote,
                    color = colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = strings.delete,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = strings.cancel,
                        onClick = onDismiss,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Измерения сессии для отчёта. У идущей записи конца нет, поэтому границей
 * служит текущий момент, и отчёт подписан этим временем.
 */
private suspend fun samplesOf(graph: AppGraph, summary: SessionSummary) =
    graph.measurementRepository.samplesList(
        summary.startedAt,
        summary.endedAt ?: System.currentTimeMillis(),
    )

/**
 * Пакет отчётов: по файлу на выбранную запись. Имена файлов различаются
 * временем записи, а не порядковым номером.
 */
private suspend fun reportsFor(
    graph: AppGraph,
    sessionIds: Set<Long>,
    spectrumIds: Set<Long>,
    appVersion: String,
    language: AppLanguage,
): List<ExportDocument> {
    val out = mutableListOf<ExportDocument>()
    for (id in sessionIds) {
        val summary = graph.sessionRepository.summary(id) ?: continue
        val to = summary.endedAt ?: System.currentTimeMillis()
        val events = graph.sessionRepository.deviationEvents(from = summary.startedAt, to = to)
        val h = HistoryCatalogue.of(language)
        val sessionStrings = SessionRadonCatalogue.of(language)
        out += ExportDocument(
            name = SeriesExport.fileName(summary.startedAt, "html"),
            mime = ExportFile.HTML.mime,
            content = SessionReportHtml.render(
                ReportFactories.session(
                    summary = summary,
                    samples = samplesOf(graph, summary),
                    events = events.map { event ->
                        ReportEvent(
                            timeText = HistoryFormat.dayTime(
                                event.timestamp,
                                System.currentTimeMillis(),
                                s = h,
                            ),
                            text = if (event.source == EventEntity.SOURCE_DEVIATION) {
                                sessionStrings.deviationEvent
                            } else {
                                sessionStrings.excursionEvent
                            },
                        )
                    },
                    appName = REPORT_APP,
                    appVersion = appVersion,
                    language = language,
                ),
            ),
        )
    }
    for (id in spectrumIds) {
        val entity = graph.measurementRepository.spectrumById(id) ?: continue
        out += ExportDocument(
            name = SpectrumExport.fileName(entity.timestamp, "html"),
            mime = ExportFile.HTML.mime,
            content = SpectrumReportHtml.render(
                SpectrumReportFactory.build(
                    entity = entity,
                    appName = REPORT_APP,
                    appVersion = appVersion,
                    language = language,
                ),
            ),
        )
    }
    return out
}

/**
 * Форматы снимка спектра: отчёт для чтения, N42 и XML для программ, CSV для
 * таблицы. Один список на Журнал и на экран спектра.
 */
@Composable
internal fun spectrumExportGroups(
    entity: SpectrumSnapshotEntity,
    e: ExportStrings,
    appVersion: String?,
    language: AppLanguage,
    saver: FileSaver,
    onPicked: () -> Unit,
): List<ExportGroup> = listOf(
    ExportGroup(
        title = e.groupReport,
        options = listOf(
            ExportOptions.report(e) {
                onPicked()
                saver.save(
                    ExportFile.HTML,
                    SpectrumExport.fileName(entity.timestamp, "html"),
                    SpectrumReportHtml.render(
                        SpectrumReportFactory.build(
                            entity = entity,
                            appName = REPORT_APP,
                            appVersion = appVersion ?: "",
                            language = language,
                        ),
                    ),
                )
            },
        ),
    ),
    ExportGroup(
        title = e.groupExchange,
        options = listOf(
            ExportOptions.standard(e) {
                onPicked()
                saver.save(
                    ExportFile.N42,
                    SpectrumExport.fileName(entity.timestamp, "n42"),
                    N42.write(
                        foreground = SpectrumExport.toN42Measurement(
                            entity,
                            N42.CLASS_FOREGROUND,
                        ),
                        softwareVersion = appVersion,
                        remarks = SpectrumExport.metadataLines(entity, appVersion),
                    ),
                )
            },
            ExportOptions.rawXml(e) {
                onPicked()
                saver.save(
                    ExportFile.XML,
                    SpectrumExport.fileName(entity.timestamp, "xml"),
                    RcXml.write(
                        SpectrumExport.toResultData(
                            entity = entity,
                            background = null,
                            serialNumber = null,
                            appVersion = appVersion,
                        ),
                    ),
                )
            },
        ),
    ),
    ExportGroup(
        title = e.groupTable,
        options = listOf(
            ExportOptions.table(e) {
                onPicked()
                saver.save(
                    ExportFile.CSV,
                    SpectrumExport.fileName(entity.timestamp, "csv"),
                    SpectrumReportFactory.toCsv(entity),
                )
            },
        ),
    ),
)


/**
 * Запись ленты журнала. Виды перечислены здесь, а не выводятся из типов
 * данных: лента — решение о показе, сессия попадает в неё строкой, а её
 * события — отдельными пометками времени.
 */
private sealed interface FeedEntry {
    val timestamp: Long

    data class Session(val group: SessionGroup) : FeedEntry {
        override val timestamp: Long get() = group.startedAt
    }

    data class Deviation(val event: EventEntity) : FeedEntry {
        override val timestamp: Long get() = event.timestamp
    }

    data class Route(val route: RouteSummary) : FeedEntry {
        override val timestamp: Long get() = route.startedAt
    }

    data class Spectrum(val entity: SpectrumSnapshotEntity) : FeedEntry {
        override val timestamp: Long get() = entity.timestamp
    }

    data class Study(
        val experiment: ExperimentEntity,
        val result: FoodScreening.Result?,
    ) : FeedEntry {
        override val timestamp: Long get() = experiment.createdAt
    }
}

/** «16 авг 19:37 · 15 ч 49 мин» — когда началось и сколько длилось. */
private fun sessionSubtitle(group: SessionGroup, now: Long, h: HistoryStrings): String {
    val seconds = ((group.endedAt ?: now) - group.startedAt) / 1000L
    val start = if (group.running) {
        h.startedAt(HistoryFormat.dayTime(group.startedAt, now, s = h))
    } else {
        HistoryFormat.dayTime(group.startedAt, now, s = h)
    }
    return "$start · ${HistoryFormat.duration(seconds, h)}"
}

/**
 * Третья строка сессии: средняя мощность и накопленная доза. Пустая запись
 * говорит об этом прямо; разбор пропусков живёт внутри записи.
 */
private fun sessionDetail(
    group: SessionGroup,
    unit: DoseUnitSetting,
    strings: Strings,
    h: HistoryStrings,
    withGaps: Boolean = false,
): String {
    val stats = group.stats
    val avg = stats.avgDoseRateMicroSvH
    if (stats.sampleCount == 0 || avg == null) return h.noMeasurements
    val values = DoseFormat.rate(avg, unit) + " · " +
        DoseFormat.dose(group.doseMicroSv, unit)
    if (!withGaps) return values
    // Прибор пишет раз в секунду, поэтому число измерений и есть измеренные
    // секунды; пропуск — то, чего в записи нет.
    val measuredSeconds = stats.sampleCount.toLong()
    val durationSeconds = ((group.endedAt ?: System.currentTimeMillis()) - group.startedAt) / 1000L
    val gapSeconds = (durationSeconds - measuredSeconds).coerceAtLeast(0L)
    if (gapSeconds < GAP_VISIBLE_SECONDS) return values
    return values + "\n" + h.dataFor(HistoryFormat.duration(measuredSeconds, h)) +
        " · " + h.gapsFor(HistoryFormat.duration(gapSeconds, h))
}

/** «15 авг 13:52 · 2 ч 37 мин · 15 км». */
private fun routeSubtitle(
    route: RouteSummary,
    now: Long,
    h: HistoryStrings,
    map: MapStrings,
): String = listOfNotNull(
    HistoryFormat.dayTime(route.startedAt, now, s = h),
    HistoryFormat.duration(route.durationSeconds, s = h),
    route.distanceMeters?.let { TrackMap.formatDistance(it, map) },
).joinToString(" · ")

/** «ср 0,08 · макс 0,16 мкЗв/ч» — то, ради чего маршрут писали. */
private fun routeDetail(
    route: RouteSummary,
    unit: DoseUnitSetting,
    strings: Strings,
): String? {
    val avg = route.avgDoseMicroSvH ?: return null
    val max = route.maxDoseMicroSvH
    return listOfNotNull(
        "${strings.avg} ${DoseFormat.rate(avg, unit)}",
        max?.let { "${strings.max} ${DoseFormat.rate(it, unit)}" },
    ).joinToString(" · ")
}

/** «16 авг 21:39 · банка 1 л» — когда исследование начато и в чём образец. */
private fun studySubtitle(study: ExperimentEntity, now: Long, h: HistoryStrings): String =
    listOfNotNull(
        HistoryFormat.dayTime(study.createdAt, now, s = h),
        study.geometry.ifBlank { null },
    ).joinToString(" · ")

/** Итог исследования одной строкой — тот же, что на его экране. */
private fun studyStatus(result: FoodScreening.Result?, f: FoodStrings): String =
    when (result?.verdict) {
        FoodScreening.Verdict.NO_DIFFERENCE -> f.verdictNoDifference
        FoodScreening.Verdict.EXCESS_WITHOUT_LINE -> f.verdictExcess
        FoodScreening.Verdict.SPECTRAL_FEATURE -> f.verdictLine
        else -> f.verdictNotEnough
    }

/** Превью маршрута в строке: форма следа читается, но не спорит с именем. */
@Composable
private fun RoutePreview(graph: AppGraph, route: RouteSummary, scale: TrackMap.RampScale?) {
    var shape by remember(route.id, route.measurementCount) {
        mutableStateOf<List<ThumbnailPoint>>(emptyList())
    }
    LaunchedEffect(route.id, route.measurementCount) {
        shape = withContext(Dispatchers.IO) {
            RouteShape.normalize(graph.trackRepository.routeShape(route.id, route.measurementCount))
        }
    }
    RouteThumbnail(shape = shape, scale = scale, size = PREVIEW_SIZE)
}

/** Пустая вкладка объясняет, чем она наполняется, а не молчит. */
@Composable
private fun EmptyFeedCard(filter: HistoryFilter) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val (title, explanation) = when (filter) {
        HistoryFilter.ROUTES -> h.noRoutesYet to h.routesExplained
        HistoryFilter.FOOD -> h.noFoodYet to h.foodExplained
        HistoryFilter.SPECTRA -> strings.noSpectraYet to strings.spectrumExplained
        else -> strings.noSessionsYet to strings.sessionExplained
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(text = title, style = type.bodySmall, color = colors.ink2)
            Hint(text = explanation, style = type.bodySmall, color = colors.muted)
        }
    }
}

/**
 * Третья строка снимка: чей это спектр и что он собой представляет.
 *
 * «фон» без ответа на вопрос «фон чего» ничего не объяснял: у снимка теперь
 * стоит профиль, при котором он снят, а у безымянного — прямое «без профиля».
 * Молчание здесь хуже любой из двух подписей.
 */
private fun spectrumProvenance(
    entity: SpectrumSnapshotEntity,
    strings: Strings,
): String {
    val profile = entity.profileName?.takeIf { it.isNotBlank() } ?: strings.noProfile
    return if (entity.isBackgroundReference) {
        "$profile · ${strings.backgroundSpectrum}"
    } else {
        profile
    }
}
