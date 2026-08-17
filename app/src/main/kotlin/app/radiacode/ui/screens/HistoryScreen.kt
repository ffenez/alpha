package app.radiacode.ui.screens

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
import app.radiacode.ui.theme.Motion
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
import app.radiacode.AppGraph
import app.radiacode.analysis.DoseProjection
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.SpectrumMerge
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.SessionSummary
import app.radiacode.service.SessionGate
import app.radiacode.ui.logic.SessionGroups
import app.radiacode.ui.logic.SessionGroup
import app.radiacode.data.toSpectrum
import app.radiacode.protocol.Spectrum
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.ProfileEntity
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.export.N42
import app.radiacode.data.export.ProcessingMetadata
import app.radiacode.data.export.RcXml
import app.radiacode.data.export.SpectrumExport
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.DisclosureArrow
import app.radiacode.ui.components.NavArrow
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import app.radiacode.ui.components.AppMenu
import app.radiacode.ui.components.ConfirmDialog
import app.radiacode.ui.components.EntityMenuButton
import app.radiacode.ui.components.EntityMenuItem
import app.radiacode.ui.components.RenameDialog
import app.radiacode.ui.components.AppMenuItem
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.CheckMark
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.RadioMark
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DailyDose
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.DeletionPlan
import app.radiacode.ui.logic.HistoryDeletion
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.HistorySelection
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.AppLanguage
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SessionRadonCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.RouteThumbnail
import app.radiacode.ui.logic.HistoryFilter
import app.radiacode.ui.logic.RouteFormat
import app.radiacode.ui.logic.RouteShape
import app.radiacode.ui.logic.RouteSummary
import app.radiacode.ui.logic.TrackMap
import app.radiacode.ui.text.MapCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import app.radiacode.baseline.BaselineState
import app.radiacode.data.export.GeoJson
import app.radiacode.data.export.ReportFactories
import app.radiacode.data.export.SeriesExport
import app.radiacode.data.export.html.ComparisonReportHtml
import app.radiacode.data.export.html.ReportEvent
import app.radiacode.data.export.html.RoutePrivacy
import app.radiacode.data.export.html.RouteReportHtml
import app.radiacode.data.export.html.RouteTrim
import app.radiacode.data.export.html.SessionReportHtml
import app.radiacode.data.export.html.SpectrumReportHtml
import app.radiacode.data.export.SpectrumReportFactory
import app.radiacode.ui.text.ExportCatalogue
import app.radiacode.ui.text.ExportStrings
import app.radiacode.ui.components.StatusRow
import app.radiacode.ui.logic.DoseTint
import app.radiacode.ui.logic.MapColorScale
import app.radiacode.ui.logic.ThumbnailPoint
import app.radiacode.analysis.FoodScreening
import app.radiacode.data.db.ExperimentEntity
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.text.FoodCatalogue
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
     * Сколько записей журнала УЖЕ прочитано — не строк на экране.
     *
     * Разница не косметическая: подряд идущие записи одного места показываются
     * одной строкой, и двадцать шесть записей превращаются в семь строк.
     * Пока «показать ещё» сравнивала общее число с числом СТРОК, условие
     * оставалось верным всегда: кнопка не исчезала, а следующая страница
     * склеивалась в те же строки — «нажимаю, а данные те же».
     */
    val loadedSessions: Int,
)

/**
 * История (SPEC «History»): dense measurement-session rows newest-first with
 * their summaries, interleaved with deviation events and their «обычно здесь
 * X» context. Windowed pages keep months of data smooth; a session opens its
 * detail.
 *
 * Накопленной дозы здесь больше нет: её спрашивают с Главной и по конкретному
 * поводу, а верх Истории она занимала всегда — теперь у неё свой экран
 * ([DoseScreen]), вход на него с плитки «Набралось сегодня».
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
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    val scope = rememberCoroutineScope()
    // Что показывает журнал. Сессия, маршрут и снимок спектра — три разные
    // вещи, и искать одну среди трёх перемешанных списков тяжело; фильтр не
    // прячет данные, а отвечает на вопрос «мне сейчас нужно вот это».
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

    // Маршруты: список строк журнала со своими числами. Перечитывается вместе
    // с остальным журналом — запись маршрута идёт как раз тогда, когда человек
    // сюда заглядывает.
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
    // Измерения продуктов: сама запись — опыт, а итог считает репозиторий,
    // тот же, что показывает экран измерения. Второго расчёта нет.
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
    // Удаление откладывается: строка исчезает сразу, а из базы уходит через
    // несколько секунд — за это время «Отменить» возвращает её целиком.
    var deletingRoutes by remember { mutableStateOf(setOf<Long>()) }
    var confirmingDelete by remember { mutableStateOf<List<RouteSummary>?>(null) }
    // Шкала миниатюр — та же, что у следа на карте, иначе цвет означал бы в
    // списке одно, а внутри маршрута другое.
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

    // Маршрут уезжает в любом из четырёх форматов, и любой из них несёт
    // координаты — поэтому после формата задаётся вопрос о них.
    var routeFormat by remember { mutableStateOf<ExportFile?>(null) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    var exportingSelection by remember { mutableStateOf(false) }
    var exportingSession by remember { mutableStateOf<Long?>(null) }
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

    // Профиль записи правится и из списка: раньше для этого нужно было войти
    // в сессию и найти чип рядом с заголовком.
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

    // Формат → координаты → системный диалог: три вопроса подряд, но каждый
    // задаётся один раз и только про то, что человек уже начал делать.
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
    // одном списке, и «убрать лишнее» это одна задача, а не две.
    var selection by remember { mutableStateOf(HistorySelection()) }
    // «Выбрать всё» обязано знать, что такое «всё»: id снимков живут в
    // карточке спектров, поэтому список поднят сюда и передаётся вниз.
    val savedSpectra by graph.measurementRepository.savedSpectra(SPECTRA_LIMIT)
        .collectAsState(initial = emptyList())
    var confirming by remember { mutableStateOf<DeletionPlan?>(null) }
    // Что вышло из объединения снимков — рядом со списком, где их и выбирали.
    var mergeNote by remember { mutableStateOf<String?>(null) }

    confirming?.let { plan ->
        DeleteConfirmDialog(
            plan = plan,
            onConfirm = {
                scope.launch {
                    graph.sessionRepository.delete(selection.sessions, selection.spectra)
                    selection = HistorySelection()
                    confirming = null
                    reload += 1
                }
            },
            onDismiss = { confirming = null },
        )
    }

    // Правка профиля сессии переехала в саму сессию (spec §20): чип «профиль…»
    // повторялся в КАЖДОЙ строке журнала и обрезался многоточием, хотя нужен
    // он редко и относится к одной конкретной записи.
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
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        // Название экрана в шапке не повторяется: оно и так подписано во
        // вкладке снизу.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (selection.active) {
                Chip(
                    text = strings.cancel,
                    color = colors.ink2,
                    onClick = { selection = selection.cancel() },
                )
            } else {
                // The counter is the way in: tapping «12 сессий» is asking to
                // do something with them.
                model?.takeIf {
                    // «26 сессий» на вкладке «Маршруты» — счёт не того, что
                    // на экране.
                    filter == HistoryFilter.ALL || filter == HistoryFilter.SESSIONS
                }?.let {
                    // Счётчик — число, а не кнопка: в режим выбора везде в
                    // журнале входят одинаково, долгим нажатием на запись.
                    Chip(text = strings.sessionsCount(it.totalSessions), color = colors.ink2)
                }
            }
        }
        if (selection.active) {
            // Идущая сессия не удаляется, поэтому и в «всё» не входит:
            // «выбрано 13» при двенадцати удаляемых было бы неправдой.
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

        Segmented(
            options = listOf(h.filterAll, h.filterSessions, h.filterRoutes, h.filterSpectra),
            selectedIndex = HistoryFilter.entries.indexOf(filter),
            onSelect = { filter = HistoryFilter.entries[it] },
            modifier = Modifier.fillMaxWidth(),
        )

        val m = model
        if (m == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = strings.readingJournal, style = type.bodySmall, color = colors.muted)
            }
        } else {
            if (filter == HistoryFilter.ALL || filter == HistoryFilter.FOOD) {
                FoodList(
                    measurements = foodExperiments,
                    results = foodResults,
                    onOpen = { openFood = it },
                )
            }

            if (filter == HistoryFilter.ALL || filter == HistoryFilter.ROUTES) {
                val visibleRoutes = routes.filter { it.id !in deletingRoutes }
                RoutesList(
                    routes = visibleRoutes,
                    unit = unit,
                    graph = graph,
                    scale = routeScale,
                    picked = pickedRoutes,
                    onOpen = { openRoute = it },
                    onPick = { id ->
                        pickedRoutes = if (id in pickedRoutes) {
                            pickedRoutes - id
                        } else {
                            pickedRoutes + id
                        }
                    },
                    onRename = { renaming = it },
                    onExport = { route -> exportingRoute = route },
                    onDelete = { confirmingDelete = listOf(it) },
                    onCompare = { ids -> comparing = ids },
                )
                // Выбор и отмена удаления — строки действий внизу списка, а не
                // кнопки в каждой карточке.
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
                            onClick = {
                                confirmingDelete = routes.filter { it.id in pickedRoutes }
                            },
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
            }

            if (filter == HistoryFilter.ALL || filter == HistoryFilter.SPECTRA) SavedSpectraCard(
                graph = graph,
                spectra = savedSpectra,
                onContinue = onContinueSpectrum,
                onOpen = onOpenSpectrum,
                selectionActive = selection.active,
                selected = selection.spectra,
                onToggle = { id ->
                    selection = selection.activate().toggleSpectrum(id)
                },
                mergeNote = mergeNote,
            )

            val showSessions = filter == HistoryFilter.ALL || filter == HistoryFilter.SESSIONS
            if (!showSessions) {
                Unit
            } else if (m.items.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        Text(
                            text = strings.noSessionsYet,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        Hint(
                            text = strings.sessionExplained,
                            style = type.bodySmall,
                            color = colors.muted,
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        m.items.forEachIndexed { index, item ->
                            if (index > 0) AppDivider()
                            when (item) {
                                is HistoryItem.Session -> SessionRow(
                                    group = item.group,
                                    unit = unit,
                                    selectionActive = selection.active,
                                    // Склейка выбирается целиком: строка на
                                    // экране одна, и «выбрано наполовину» о
                                    // ней сказать нечего.
                                    selected = item.group.ids.all { it in selection.sessions },
                                    onClick = {
                                        if (selection.active) {
                                            // A session still being written to
                                            // cannot be deleted: the data is
                                            // arriving as we speak.
                                            if (!item.group.running) {
                                                selection = item.group.ids.fold(selection) {
                                                    acc, id ->
                                                    acc.toggleSession(id)
                                                }
                                            }
                                        } else {
                                            onOpenSession(item.group.ids.last())
                                        }
                                    },
                                    onLongClick = {
                                        if (!item.group.running) {
                                            selection = item.group.ids.fold(selection.activate()) {
                                                acc, id ->
                                                acc.toggleSession(id)
                                            }
                                        }
                                    },
                                    // «⋮» у сессии — те же действия, что и на
                                    // её экране: один набор на список и на
                                    // запись.
                                    menu = if (selection.active) {
                                        emptyList()
                                    } else {
                                        EntityMenus.session(
                                            strings = strings,
                                            export = e,
                                            onExport = {
                                                exportingSession = item.group.ids.last()
                                            },
                                            onProfile = {
                                                profileForSession = item.group.ids.last()
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    confirming = graph.sessionRepository
                                                        .deletionPlan(
                                                            sessionIds = item.group.ids.toSet(),
                                                            spectrumIds = emptySet(),
                                                        )
                                                }
                                            },
                                        )
                                    },
                                )
                                is HistoryItem.Deviation -> DeviationRow(item.event, unit)
                            }
                        }
                    }
                }
            }

            if (showSessions && m.loadedSessions < m.totalSessions) {
                // Компактный чип вместо кнопки во всю ширину: догрузка — не
                // главное действие экрана, а продолжение списка.
                Chip(
                    text = strings.showMore,
                    color = colors.ink2,
                    onClick = { pages += 1 },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            // Действия выбранного — одной строкой внизу, как у маршрутов:
            // сравнить и объединить появляются, когда выбрано столько, сколько
            // им нужно, и не занимают места, пока выбирать нечего.
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
                        // Выгрузка выбранного: либо один общий отчёт, либо по
                        // файлу на запись в выбранную папку. Больше форматов
                        // здесь нет намеренно — таблица и данные снимаются с
                        // самой записи, где видно, что именно уезжает.
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
                                        confirming = graph.sessionRepository.deletionPlan(
                                            sessionIds = selection.sessions,
                                            spectrumIds = selection.spectra,
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
 * Измерения продуктов в журнале.
 *
 * Строка отвечает на то, ради чего измерение и делалось: что за продукт, когда,
 * сколько копили и что вышло. Числа фона и образца — вторичны и стоят тусклой
 * строкой: они нужны, чтобы вывод можно было проверить, а не чтобы читать их
 * первыми.
 */
@Composable
private fun FoodList(
    measurements: List<ExperimentEntity>,
    results: Map<Long, FoodScreening.Result?>,
    onOpen: (Long) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val f = FoodCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()

    if (measurements.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = h.noFoodYet, style = type.bodySmall, color = colors.ink2)
                Hint(text = h.foodExplained, style = type.bodySmall, color = colors.muted)
            }
        }
        return
    }

    for (measurement in measurements) {
        val result = results[measurement.id]
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onOpen(measurement.id) },
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = measurement.note.ifBlank { f.title },
                    style = type.label,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        HistoryFormat.dayTime(measurement.createdAt, now, s = h),
                        measurement.geometry.ifBlank { null },
                    ).joinToString(" · "),
                    style = type.footnote,
                    color = colors.ink2,
                )
                Text(
                    text = when (result?.verdict) {
                        FoodScreening.Verdict.NO_DIFFERENCE -> f.verdictNoDifference
                        FoodScreening.Verdict.EXCESS_WITHOUT_LINE -> f.verdictExcess
                        FoodScreening.Verdict.SPECTRAL_FEATURE -> f.verdictLine
                        else -> f.verdictNotEnough
                    },
                    style = type.footnote,
                    color = if (result?.verdict == FoodScreening.Verdict.NO_DIFFERENCE) {
                        colors.ink
                    } else {
                        colors.warn
                    },
                )
                result?.comparison?.let { comparison ->
                    Text(
                        text = f.stepBackground + " " +
                            Uncertainty.num1(comparison.rateB.toFloat()) + " · " +
                            f.stepSample + " " +
                            Uncertainty.num1(comparison.rateA.toFloat()),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

/**
 * Маршруты журнала.
 *
 * Список без заголовка раздела и без внешней карточки: вкладка уже названа
 * фильтром, и второй раз повторять слово «Маршруты» незачем. Дата тоже ушла из
 * строк в заголовки дней — в списке за месяц она стояла бы у каждой записи,
 * ничего не различая.
 *
 * Различают запись три вещи: её форма (миниатюра, окрашенная той же шкалой,
 * что след на карте), время начала и то, чем прогулка была — путь и
 * длительность. Всё остальное живёт внутри маршрута.
 */
@Composable
private fun RoutesList(
    routes: List<RouteSummary>,
    unit: DoseUnitSetting,
    graph: AppGraph,
    scale: TrackMap.RampScale?,
    picked: Set<Long>,
    onOpen: (Long) -> Unit,
    onPick: (Long) -> Unit,
    onRename: (RouteSummary) -> Unit,
    onExport: (RouteSummary) -> Unit,
    onDelete: (RouteSummary) -> Unit,
    onCompare: (List<Long>) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()

    if (routes.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = h.noRoutesYet, style = type.bodySmall, color = colors.ink2)
                Hint(text = h.routesExplained, style = type.bodySmall, color = colors.muted)
            }
        }
        return
    }

    var lastHeader: String? = null
    for (route in routes) {
        val header = HistoryFormat.dayHeader(route.startedAt, now, s = h)
        if (header != lastHeader) {
            lastHeader = header
            Text(
                text = header,
                style = type.labelSmall,
                color = colors.ink2,
                modifier = Modifier.padding(top = Dimens.space1),
            )
        }
        RouteCard(
            route = route,
            unit = unit,
            graph = graph,
            scale = scale,
            picked = route.id in picked,
            selecting = picked.isNotEmpty(),
            onOpen = { onOpen(route.id) },
            onPick = { onPick(route.id) },
            onRename = { onRename(route) },
            onExport = { onExport(route) },
            onDelete = { onDelete(route) },
            onCompare = { onCompare(picked.toList()) },
        )
    }
}

/**
 * Одна прогулка.
 *
 * Тап открывает маршрут, долгое нажатие включает выбор, `⋮` даёт редкие
 * действия. Стрелки «›» нет: карточка целиком и есть кнопка, а стрелка
 * повторяла бы это ещё раз.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteCard(
    route: RouteSummary,
    unit: DoseUnitSetting,
    graph: AppGraph,
    scale: TrackMap.RampScale?,
    picked: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCompare: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val mapStrings = MapCatalogue.of(strings.language)
    val now = System.currentTimeMillis()
    var menuOpen by remember { mutableStateOf(false) }

    // Форма читается прореженной и один раз на карточку: по ногтю маршрут
    // узнают, а не измеряют.
    var shape by remember(route.id, route.measurementCount) {
        mutableStateOf<List<ThumbnailPoint>>(emptyList())
    }
    LaunchedEffect(route.id, route.measurementCount) {
        shape = withContext(Dispatchers.IO) {
            RouteShape.normalize(graph.trackRepository.routeShape(route.id, route.measurementCount))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onPick() else onOpen() },
                onLongClick = onPick,
            )
            .then(
                if (picked) {
                    Modifier.border(
                        width = LocalAppMetrics.current.border,
                        color = colors.dataText,
                        shape = RoundedCornerShape(LocalAppMetrics.current.radiusCard),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            RouteThumbnail(shape = shape, scale = scale)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = RouteFormat.title(route, now, h),
                        style = type.label,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    // Идущая запись — зелёная точка и одно слово: красить ради
                    // этого название значило бы сказать то же самое дважды.
                    if (route.running) {
                        StatusRow(text = h.routeRecording, color = colors.ok)
                    } else if (route.interrupted) {
                        StatusRow(text = h.routeInterrupted, color = colors.warn)
                    }
                    // Меню строки — в языке терминала: материаловская карточка
                    // с тенью рядом с чипами читалась как чужое приложение.
                    var menuHeight by remember { mutableIntStateOf(0) }
                    val menuGap = with(LocalDensity.current) { Dimens.space1.roundToPx() }
                    Box(modifier = Modifier.onSizeChanged { menuHeight = it.height }) {
                        Chip(text = MENU_GLYPH, color = colors.ink2, onClick = { menuOpen = true })
                        AppMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(0, menuHeight + menuGap),
                        ) {
                            AppMenuItem(
                                text = h.routeRename,
                                onClick = { menuOpen = false; onRename() },
                            )
                            AppMenuItem(
                                text = h.routeCompare,
                                onClick = { menuOpen = false; if (picked) onCompare() else onPick() },
                            )
                            AppMenuItem(
                                text = h.routeExport,
                                onClick = { menuOpen = false; onExport() },
                            )
                            AppMenuItem(
                                text = strings.delete,
                                onClick = { menuOpen = false; onDelete() },
                            )
                        }
                    }
                }
                // Чем была прогулка: путь и время. Число измерений вторично и
                // живёт внутри маршрута — кроме идущей записи, где оно
                // единственный признак, что след действительно пишется.
                Text(
                    text = listOfNotNull(
                        route.distanceMeters?.let { TrackMap.formatDistance(it, mapStrings) },
                        HistoryFormat.duration(route.durationSeconds, s = h),
                    ).joinToString(" · "),
                    style = type.footnote,
                    color = colors.ink2,
                )
                if (route.running) {
                    Text(
                        text = h.routeMeasurements(HistoryFormat.count(route.measurementCount)),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                // Показатели отделены от описания: это уже не про прогулку, а
                // про то, что намерено. Единица названа один раз на величину.
                Text(
                    text = listOfNotNull(
                        route.avgDoseMicroSvH?.let { "${mapStrings.statAvg} ${DoseFormat.rate(it, unit)}" },
                        route.maxDoseMicroSvH?.let {
                            "${mapStrings.statMax} ${DoseFormat.rateWithUnit(it, unit, s = strings)}"
                        },
                        route.doseMicroSv?.let {
                            "${h.statDose} ${DoseFormat.doseWithUnit(it, unit, s = strings)}"
                        },
                    ).joinToString(" · "),
                    style = type.footnote,
                    color = colors.ink,
                )
            }
        }
    }
}

/**
 * Удаление маршрута спрашивают один раз и говорят, что именно исчезнет:
 * точки прогулки уходят и из накопленной карты тоже, а измерения прибора за
 * это время остаются — это разные данные, и путать их нельзя.
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
 * Имя маршрута спрашивается ПОСЛЕ прогулки и не обязательно: пока его нет,
 * список подписывает маршрут датой, и это тоже различает записи.
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    group: SessionGroup,
    unit: DoseUnitSetting,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selectionActive: Boolean = false,
    selected: Boolean = false,
    /** Редкие действия строки; пусто — в режиме выбора их не показывают. */
    menu: List<EntityMenuItem> = emptyList(),
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val endedAt = group.endedAt
    val durationSeconds = ((endedAt ?: now) - group.startedAt) / 1000L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                // Зажать = выбрать: тот же жест, что у маршрутов, — режим
                // выбора включается там, где на него смотрят.
                onLongClick = onLongClick,
            )
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionActive) {
                if (group.running) {
                    // Nothing to tick: a running session is still being written.
                    Spacer(Modifier.size(18.dp))
                } else {
                    CheckMark(selected = selected)
                }
                Spacer(Modifier.size(Dimens.space2))
            }
            Text(
                text = group.profileName ?: strings.noProfile,
                style = type.label,
                color = if (selectionActive && group.running) colors.muted else colors.ink,
            )
            if (group.running) {
                Text(
                    text = if (selectionActive) strings.runningCannotDelete else strings.running,
                    style = type.label,
                    color = if (selectionActive) colors.muted else colors.ok,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (menu.isNotEmpty()) {
                EntityMenuButton(menu = menu, modifier = Modifier.padding(end = Dimens.space1))
            }
            if (!selectionActive) NavArrow()
        }

        // Момент и длительность. У идущей записи сказано «начата»: без этого
        // время читалось как момент окончания того, что ещё идёт.
        Text(
            text = (
                if (group.running) {
                    h.startedAt(HistoryFormat.dayTime(group.startedAt, now, s = h))
                } else {
                    HistoryFormat.dayTime(group.startedAt, now, s = h)
                }
                ) + " · " + HistoryFormat.duration(durationSeconds, h),
            style = type.footnote,
            color = colors.ink2,
        )

        // Две величины: сколько было в среднем и сколько накопилось. Максимум,
        // число измерений и пометки о треке со спектром отвечают на вопросы,
        // которые задают уже ВНУТРИ записи.
        val stats = group.stats
        val avgMicroSvH = stats.avgDoseRateMicroSvH
        if (stats.sampleCount > 0 && avgMicroSvH != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DataItem(strings.avg, DoseFormat.rateWithUnit(avgMicroSvH, unit, s = strings))
                DataItem(
                    strings.dose,
                    DoseFormat.doseWithUnit(group.doseMicroSv, unit, s = strings),
                )
            }
            // Сколько времени прибор действительно писал и сколько его в
            // записи нет. Строка появляется, только когда пропуски есть:
            // «пропуски 0 мин» сообщало бы о том, чего не было.
            // Прибор пишет раз в секунду, поэтому число измерений и есть
            // измеренные секунды.
            val dataSeconds = stats.sampleCount.toLong()
            val gapSeconds = (durationSeconds - dataSeconds).coerceAtLeast(0L)
            Text(
                text = h.dataFor(HistoryFormat.duration(dataSeconds, h)) +
                    if (gapSeconds >= GAP_VISIBLE_SECONDS) {
                        " · " + h.gapsFor(HistoryFormat.duration(gapSeconds, h))
                    } else {
                        ""
                    },
                style = type.footnote,
                color = colors.ink2,
            )
        } else {
            Text(
                text = strings.noSamplesInSession,
                style = type.valueSmall,
                color = colors.muted,
            )
        }

        // Спец §20: журнал обязан говорить, учила ли запись обычный фон, —
        // но это ПОЯСНЕНИЕ, и оно уходит вместе с остальными.
        Hint(
            text = HistoryFormat.admissionLine(group.admission, h),
            color = if (group.admission.included) colors.muted else colors.ink2,
        )
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
private fun DeviationRow(event: EventEntity, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val kind = when (event.source) {
        EventEntity.SOURCE_DEVIATION -> strings.deviation
        else -> strings.excursionPoint
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
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

private const val SPECTRA_LIMIT = 30

/**
 * Сохранённые и импортированные спектры: экспорт в файл, вход в сравнение
 * («Сравнить» → выбрать два снимка → экран сравнения), объединение 2+
 * снимков в один (каналы складываются, Δt суммируется; расходящиеся
 * калибровки честно отклоняются) и «продолжить накопление» на Спектре.
 * Автоснимки раз в минуту сюда не попадают — только явные сохранения,
 * фоны и импорт.
 */
@Composable
private fun SavedSpectraCard(
    graph: AppGraph,
    spectra: List<SpectrumSnapshotEntity>,
    onContinue: (Long) -> Unit,
    /** Открыть снимок полноценным экраном Спектра. */
    onOpen: (Long) -> Unit,
    selectionActive: Boolean = false,
    selected: Set<Long> = emptySet(),
    onToggle: (Long) -> Unit = {},
    /** Результат объединения показывается там же, где список снимков. */
    mergeNote: String? = null,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (spectra.isEmpty()) return

    var notice by remember { mutableStateOf<SpectrumFileNotice?>(null) }
    var exportedNote by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var renaming by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    var deleting by remember { mutableStateOf<SpectrumSnapshotEntity?>(null) }
    val e = ExportCatalogue.of(strings.language)
    val saver = rememberFileSaver { ok ->
        if (ok) {
            exportedNote = e.saved
        } else {
            notice = SpectrumFileNotice(
                title = strings.exportFailedTitle,
                lines = listOf(strings.exportFailedBody),
                isError = true,
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = strings.spectraTitle.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            // Что делать со снимками, спрашивают не заранее: сначала выбирают
            // (долгим нажатием), потом внизу появляются действия — сравнить,
            // объединить, удалить. Прежние режимы «сравнение» и «объединение»
            // были третьим и четвёртым состоянием одного списка, и в каждом
            // строка значила своё.
            Hint(
                text = strings.snapshotOpensActions,
                style = type.footnote,
                color = colors.muted,
                modifier = Modifier.padding(top = 3.dp, bottom = 5.dp),
            )
            spectra.forEachIndexed { index, entity ->
                if (index > 0) AppDivider()
                SavedSpectrumRow(
                    entity = entity,
                    marker = null,
                    check = if (selectionActive) entity.id in selected else null,
                    // Обычное нажатие открывает сам снимок — как у сессии и
                    // маршрута. Промежуточный список действий, который стоял
                    // здесь раньше, смешивал навигацию, экспорт и действия
                    // над записью в одном окне.
                    onClick = {
                        if (selectionActive) onToggle(entity.id) else onOpen(entity.id)
                    },
                    onLongClick = { onToggle(entity.id) },
                    menu = if (selectionActive) {
                        emptyList()
                    } else {
                        EntityMenus.spectrum(
                            strings = strings,
                            export = e,
                            history = h,
                            canCompare = spectra.size >= 2,
                            onExport = { exporting = entity },
                            onCompare = { onToggle(entity.id) },
                            onContinue = { onContinue(entity.id) },
                            onRename = { renaming = entity },
                            onDelete = { deleting = entity },
                        )
                    },
                )
            }
            (exportedNote ?: mergeNote)?.let {
                Text(text = it, style = type.footnote, color = colors.muted)
            }
        }
    }

    exporting?.let { entity ->
        EntityExportSheet(
            title = e.export,
            groups = spectrumExportGroups(
                entity = entity,
                e = e,
                appVersion = appVersionName(context),
                language = strings.language,
                saver = saver,
                onPicked = { exporting = null },
            ),
            onDismiss = { exporting = null },
        )
    }

    renaming?.let { entity ->
        RenameDialog(
            title = h.routeRename,
            initial = entity.label.orEmpty(),
            placeholder = h.routeNameHint,
            onSave = { name ->
                renaming = null
                scope.launch { graph.measurementRepository.renameSpectrum(entity.id, name) }
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { entity ->
        ConfirmDialog(
            title = h.routeDeleteTitle(1),
            body = h.routeDeleteBody,
            confirmText = strings.delete,
            onConfirm = {
                deleting = null
                scope.launch {
                    graph.sessionRepository.delete(emptySet(), setOf(entity.id))
                }
            },
            onDismiss = { deleting = null },
        )
    }

    notice?.let { current ->
        SpectrumFileNoticeDialog(notice = current, onDismiss = { notice = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedSpectrumRow(
    entity: SpectrumSnapshotEntity,
    marker: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    /** Non-null while the list is in selection mode: the tick of this row. */
    check: Boolean? = null,
    /** Редкие действия строки; пусто — в режиме выбора их не показывают. */
    menu: List<EntityMenuItem> = emptyList(),
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (check != null) {
                CheckMark(selected = check, modifier = Modifier.padding(end = Dimens.space2))
            }
            if (marker != null) {
                Text(
                    text = "$marker ▸",
                    style = type.label,
                    color = colors.dataText,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                // Название снимка без даты: она стоит справа в этой же
                // строке, и повторять её в имени незачем.
                text = entity.label ?: strings.spectrum,
                style = type.label,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = HistoryFormat.dayTime(entity.timestamp, now, s = h),
                style = type.footnote,
                color = colors.ink2,
            )
            // Редкие действия — там же, где у маршрута: «⋮» справа в строке.
            if (menu.isNotEmpty()) {
                EntityMenuButton(menu = menu, modifier = Modifier.padding(start = Dimens.space2))
            }
        }
        // Δt — это время НАКОПЛЕНИЯ снимка (`durationSeconds` прибора), а не
        // момент и не интервал между снимками. Поэтому оно и пишется
        // длительностью: «121:10:00» читалось как время суток.
        Text(
            text = listOfNotNull(
                HistoryFormat.duration(entity.durationSeconds, h),
                strings.importedTag.takeIf {
                    entity.origin == SpectrumSnapshotEntity.ORIGIN_IMPORT
                },
                strings.backgroundTag.takeIf { entity.isBackgroundReference },
            ).joinToString(" · "),
            style = type.footnote,
            color = colors.ink2,
        )
    }
}

/** Outcome of the История merge action. */
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
            // Метка ХРАНИТСЯ в базе, поэтому она не зависит от языка
            // интерфейса: иначе снимок, объединённый по-русски, так и остался
            // бы русским после переключения языка.
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

    // Подряд идущие записи одного места показываются одной строкой: рвали их
    // разрывы связи и перезапуски службы, а не решение человека. Журнал в базе
    // при этом не переписывается — склейка живёт только в показе.
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


/**
 * Deleting measurements is the one place in the app where data really goes
 * away, so the dialog reads like an account, not like a warning: what exactly
 * disappears, what stays, and the fact that it cannot be undone.
 */
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
 * Измерения сессии для отчёта.
 *
 * У идущей записи конца нет, поэтому границей служит текущий момент: отчёт о
 * ней описывает то, что записано К ЭТОЙ МИНУТЕ, и подписан этим временем.
 */
private suspend fun samplesOf(graph: AppGraph, summary: SessionSummary) =
    graph.measurementRepository.samplesList(
        summary.startedAt,
        summary.endedAt ?: System.currentTimeMillis(),
    )

/**
 * Пакет отчётов: по файлу на выбранную запись.
 *
 * Имена файлов различаются временем записи, а не порядковым номером: папка с
 * «report-1…report-9» через месяц не говорит ни о чём.
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
 * таблицы.
 *
 * Собрано в одном месте, потому что список одинаков и в Журнале, и на экране
 * спектра: расходиться им незачем, а раньше они расходились — в Журнале не
 * было ни отчёта, ни таблицы.
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
