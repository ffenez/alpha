package app.alpha.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.alpha.AppGraph
import app.alpha.data.DoseUnitSetting
import app.alpha.data.SessionSummary
import app.alpha.data.db.EventEntity
import app.alpha.device.DoseUnits
import app.alpha.ui.components.ExplainInfoButton
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.data.export.ReportFactories
import app.alpha.data.export.SeriesExport
import app.alpha.data.export.html.ReportEvent
import app.alpha.data.export.html.SessionReportHtml
import app.alpha.ui.components.Card
import app.alpha.ui.components.EntityHeader
import app.alpha.ui.components.ChartNotesDialog
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.components.TrendChart
import app.alpha.ui.components.TrendChartSpec
import app.alpha.ui.logic.ChartMapping
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.EnvironmentSeries
import app.alpha.ui.logic.FlightDetect
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.TimeAxis
import androidx.compose.runtime.saveable.rememberSaveable
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.text.ChartTextCatalogue
import app.alpha.ui.text.ExportCatalogue
import app.alpha.ui.text.SearchCatalogue
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SessionRadonCatalogue
import app.alpha.ui.text.SessionRadonStrings
import app.alpha.ui.text.uiDecimal
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.flow.first

private const val CHART_COLUMNS = 48

@Immutable
private data class SessionDetail(
    val summary: SessionSummary,
    val columns: List<Float?>,
    val stats: ChartMapping.Stats?,
    /** Ряды условий на той же сетке; пусто — датчиков нет или нет данных. */
    val conditions: List<EnvironmentSeries.Series> = emptyList(),
    val fromMillis: Long,
    val toMillis: Long,
    val events: List<EventEntity>,
    /** Flight sessions: altitude per dose-chart column (same time grid). */
    val altitudeColumns: List<Float?>? = null,
    val flight: FlightDetect.Summary? = null,
)

/**
 * Session detail: the full-period dose-rate chart (bucketed to 48 columns
 * whatever the duration) plus the complete summary and the session's
 * deviation events.
 */
@Composable
fun SessionDetailScreen(
    graph: AppGraph,
    sessionId: Long,
    onBack: () -> Unit,
    onOpenTrack: () -> Unit = {},
    /**
     * Открыть тот же период полноэкранным графиком (жесты, курсор, конверты,
     * маркеры). Границы отдаются наружу, потому что диапазон — это и есть то,
     * чем отличается исторический график от живого.
     */
    onOpenChart: (fromMillis: Long, toMillis: Long) -> Unit = { _, _ -> },
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SessionRadonCatalogue.of(strings.language)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    val saver = rememberFileSaver { ok -> notice = if (ok) t.exportSaved else t.exportFailed }

    var detail by remember { mutableStateOf<SessionDetail?>(null) }
    var missing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(sessionId, reload) {
        val loaded = loadDetail(graph, sessionId)
        detail = loaded
        missing = loaded == null
    }

    // Спец §20: профиль записи правится задним числом, вместе с её участием в
    // обучении обычного фона.
    var reassigning by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val summaryForProfile = detail?.summary
    if (reassigning && summaryForProfile != null) {
        SessionProfileDialog(
            startedAt = summaryForProfile.startedAt,
            profileId = summaryForProfile.profileId,
            profiles = profiles,
            onPick = { profileId ->
                scope.launch {
                    graph.sessionRepository.reassignProfile(sessionId, profileId)
                    reassigning = false
                    reload += 1
                }
            },
            onDismiss = { reassigning = false },
        )
    }

    val d0 = detail
    if (exporting && d0 != null) {
        val e = ExportCatalogue.of(strings.language)
        fun withSamples(block: (List<app.alpha.data.db.SampleEntity>) -> Unit) {
            scope.launch {
                block(
                    graph.measurementRepository.samplesList(
                        d0.summary.startedAt,
                        d0.summary.endedAt ?: d0.toMillis,
                    ),
                )
            }
        }
        EntityExportSheet(
            title = e.export,
            groups = listOf(
                ExportGroup(
                    title = e.groupReport,
                    options = listOf(
                        ExportOptions.report(e) {
                            exporting = false
                            withSamples { samples ->
                                saver.save(
                                    ExportFile.HTML,
                                    SeriesExport.fileName(d0.summary.startedAt, "html"),
                                    SessionReportHtml.render(
                                        ReportFactories.session(
                                            summary = d0.summary,
                                            samples = samples,
                                            events = d0.events.map { event ->
                                                ReportEvent(
                                                    timeText = HistoryFormat.dayTime(
                                                        event.timestamp,
                                                        System.currentTimeMillis(),
                                                        s = HistoryCatalogue.of(strings.language),
                                                    ),
                                                    text = if (
                                                        event.source == EventEntity.SOURCE_DEVIATION
                                                    ) {
                                                        t.deviationEvent
                                                    } else {
                                                        t.excursionEvent
                                                    },
                                                )
                                            },
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
                            exporting = false
                            withSamples { samples ->
                                saver.save(
                                    ExportFile.JSON,
                                    SeriesExport.fileName(d0.summary.startedAt, "json"),
                                    ReportFactories.sessionJson(d0.summary, samples),
                                )
                            }
                        },
                    ),
                ),
                ExportGroup(
                    title = e.groupTable,
                    options = listOf(
                        ExportOptions.table(e) {
                            exporting = false
                            withSamples { samples ->
                                saver.save(
                                    ExportFile.CSV,
                                    SeriesExport.fileName(d0.summary.startedAt, "csv"),
                                    SeriesExport.csv(samples),
                                )
                            }
                        },
                    ),
                ),
            ),
            onDismiss = { exporting = false },
        )
    }

    if (confirmDelete) {
        SessionDeleteDialog(
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    graph.sessionRepository.delete(setOf(sessionId), emptySet())
                    onBack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        // Шапка записи — та же, что у маршрута, спектра и опыта: имя, время
        // и «⋮» с редкими действиями. Экспорт уехал туда же: он нужен реже,
        // чем прочитать саму сессию.
        detail?.let { d ->
            val h = HistoryCatalogue.of(strings.language)
            val now = System.currentTimeMillis()
            val duration = ((d.summary.endedAt ?: now) - d.summary.startedAt) / 1000L
            EntityHeader(
                title = d.summary.profileName ?: t.sessionTag,
                subtitle = HistoryFormat.dayTime(d.summary.startedAt, now, s = h) +
                    " · " + HistoryFormat.duration(duration, s = h),
                onBack = onBack,
                menu = EntityMenus.session(
                    strings = strings,
                    export = ExportCatalogue.of(strings.language),
                    onExport = { exporting = true },
                    onProfile = { reassigning = true },
                    onDelete = { confirmDelete = true },
                ),
            )
        } ?: EntityHeader(title = t.sessionTag, onBack = onBack)

        val d = detail
        when {
            missing -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = t.sessionNotFound, style = type.bodySmall, color = colors.muted)
            }
            d == null -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = t.readingSession, style = type.bodySmall, color = colors.muted)
            }
            else -> {
                SummaryCard(d.summary, unit, t, onOpenTrack) { reassigning = true }
                ChartCard(
                    detail = d,
                    unit = unit,
                    t = t,
                    onOpenChart = {
                        onOpenChart(d.summary.startedAt, d.summary.endedAt ?: d.toMillis)
                    },
                )
                if (d.altitudeColumns != null) {
                    FlightCard(d, unit, t)
                }
                if (d.conditions.isNotEmpty()) ConditionsCard(d, t)
                if (d.events.isNotEmpty()) EventsCard(d.events, unit, t)
                notice?.let {
                    Text(text = it, style = type.footnote, color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: SessionSummary,
    unit: DoseUnitSetting,
    t: SessionRadonStrings,
    onOpenTrack: () -> Unit,
    onReassign: () -> Unit,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val now = System.currentTimeMillis()
    val durationSeconds = ((summary.endedAt ?: now) - summary.startedAt) / 1000L
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.profileName ?: strings.noProfile,
                    style = type.title,
                    color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                if (summary.endedAt == null) {
                    Chip(text = t.runningNow, color = colors.ok)
                } else {
                    // Правка профиля живёт ЗДЕСЬ, а не в каждой строке журнала:
                    // нужна она редко и относится к одной записи.
                    // «Профиль…» многоточием обещало продолжение мысли, а не
                    // выбор: шеврон говорит, что откроется список.
                    Chip(
                        text = "${strings.profile} ›",
                        color = colors.ink2,
                        onClick = onReassign,
                    )
                }
            }
            Text(
                text = HistoryFormat.dayTime(summary.startedAt, now, s = h) +
                    " · ${HistoryFormat.duration(durationSeconds, s = h)}",
                style = type.footnote,
                color = colors.ink2,
            )
            AppDivider()

            // Сетка вместо строк «мощность дозы: ср 0,15 · мин 0,12 · макс 0,18».
            //
            // Длинная собранная строка ломалась на узком экране в «мкЗв/» и «ч»
            // на разных строках и склеивала подпись со значением. Здесь подпись
            // и число — отдельные элементы одной колонки, и переносить нечего.
            // Минимум, максимум и разброс уехали в «Подробнее»: карточка
            // отвечает на вопрос «сколько тут было», а не описывает выборку.
            val stats = summary.stats
            val avgMicroSvH = stats.avgDoseRateMicroSvH
            if (stats.sampleCount == 0 || avgMicroSvH == null) {
                Text(
                    text = strings.noSamplesInSession,
                    style = type.body,
                    color = colors.muted,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = DoseFormat.dose(summary.doseMicroSv, unit),
                        style = type.valueHero,
                        color = colors.ink,
                    )
                    Text(
                        text = t.sessionDoseLabel,
                        style = type.overline,
                        color = colors.muted,
                    )
                }
                StatGrid(
                    cells = listOf(
                        StatCell(
                            DoseFormat.rate(avgMicroSvH, unit),
                            t.doseRateLabel,
                        ),
                        StatCell(
                            "${(stats.avgCountRate ?: 0f).toInt()}",
                            t.countRateLabel,
                        ),
                        StatCell(HistoryFormat.count(stats.sampleCount), t.samplesLabel),
                    ),
                )
            }

            if (summary.hasSpectrum || summary.hasTrack || summary.hasFlight) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    if (summary.hasSpectrum) Chip(text = strings.spectrum)
                    if (summary.hasTrack) {
                        Chip(text = t.trackOnMap, onClick = onOpenTrack)
                    }
                    if (summary.hasFlight) Chip(text = strings.flight)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = type.bodySmall, color = colors.ink2)
        Spacer(Modifier.weight(1f))
        Text(text = value, style = type.valueSmall, color = colors.ink)
    }
}

@Composable
private fun ChartCard(
    detail: SessionDetail,
    unit: DoseUnitSetting,
    t: SessionRadonStrings,
    onOpenChart: () -> Unit,
) {
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val h = HistoryCatalogue.of(strings.language)
    val labels = ChartTextCatalogue.of(strings.language)
    var more by rememberSaveable { mutableStateOf(false) }
    // Оговорки о статистике не исчезли, а переехали под «i»: линия здесь —
    // СРЕДНЕЕ интервала, а полный экран считает те же измерения медианой с
    // конвертами. Молчать об этом нельзя, но и держать две строки под каждой
    // картинкой незачем — их читают один раз.
    var info by remember { mutableStateOf(false) }
    if (info) {
        ChartNotesDialog(notes = listOf(t.chartLineNote, t.fullChartNote)) { info = false }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.chartTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                // Длительность вторичным текстом, а не подписью «вся сессия»:
                // это и есть весь ответ на вопрос «за какой срок картинка».
                Text(
                    text = HistoryFormat.duration(
                        (detail.toMillis - detail.fromMillis) / 1000L,
                        s = h,
                    ),
                    style = type.footnote,
                    color = colors.muted,
                    modifier = Modifier.padding(end = Dimens.space2),
                )
                if (detail.stats != null) {
                    Chip(text = "↗", color = colors.dataText, onClick = onOpenChart)
                }
                ExplainInfoButton(
                    onClick = { info = true },
                    modifier = Modifier.padding(start = Dimens.space1),
                )
            }
            val stats = detail.stats
            if (stats == null) {
                Text(
                    text = t.noChartData,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                val yMax = ChartMapping.yMax(stats.max, null)
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenChart,
                    ),
                ) {
                    TrendChart(
                        spec = TrendChartSpec(
                            columns = detail.columns,
                            yMax = yMax,
                            yTicks = ChartMapping.yTicks(yMax)
                                .map { it to DoseFormat.rate(it, unit) },
                            xLabels = TimeAxis.labels(detail.fromMillis, detail.toMillis),
                            endpointLabel = detail.columns.lastOrNull { it != null }
                                ?.let { DoseFormat.rate(it, unit) },
                        ),
                    )
                }
                // Между какими значениями держалось и сколько измерений — то,
                // что спрашивают о ряде. Крайние точки и разброс отвечают на
                // другой вопрос и ждут в «Подробнее».
                StatGrid(
                    cells = listOf(
                        StatCell(DoseFormat.rate(stats.p10, unit), "P10"),
                        StatCell(DoseFormat.rate(stats.median, unit), t.statMedian),
                        StatCell(DoseFormat.rate(stats.p90, unit), "P90"),
                        StatCell(HistoryFormat.count(detail.summary.stats.sampleCount), "n"),
                    ),
                )
                Chip(
                    text = if (more) labels.hideDetails else labels.showDetails,
                    color = colors.dataText,
                    selected = more,
                    onClick = { more = !more },
                )
                if (more) {
                    StatGrid(
                        cells = listOf(
                            StatCell(DoseFormat.rate(stats.min, unit), t.statMin),
                            StatCell(DoseFormat.rate(stats.max, unit), t.statMax),
                            StatCell(
                                DoseFormat.rate(stats.sigma, unit),
                                t.sd,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Условия вокруг измерения: давление, магнитное поле, температура телефона.
 *
 * Один график и переключатель ряда, а не три картинки друг под другом: ряды
 * отвечают на разные вопросы, и смотрят их по одному. Ось времени — та же, что
 * у дозы выше, поэтому совпадения читаются глазом без второй оси.
 *
 * Шкала каждого ряда идёт от его собственного минимума: давление живёт в
 * полосе шириной несколько гектопаскалей около тысячи, и шкала от нуля
 * превратила бы его в прямую. Подписи делений при этом настоящие.
 */
@Composable
private fun ConditionsCard(detail: SessionDetail, t: SessionRadonStrings) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val series = detail.conditions
    var selected by rememberSaveable(series.size) { mutableIntStateOf(0) }
    val current = series.getOrNull(selected) ?: series.first()
    var info by remember { mutableStateOf(false) }
    if (info) {
        ChartNotesDialog(
            title = t.conditionsTitle,
            notes = series.map { note(it.kind, t) },
        ) { info = false }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.conditionsTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                ExplainInfoButton(onClick = { info = true })
            }
            if (series.size > 1) {
                Segmented(
                    options = series.map { label(it.kind, t) },
                    selectedIndex = series.indexOf(current),
                    onSelect = { selected = it },
                )
            }
            TrendChart(
                spec = TrendChartSpec(
                    columns = current.plot,
                    yMax = current.span,
                    yTicks = current.ticks().map { (offset, value) ->
                        offset to Uncertainty.num1(value)
                    },
                    xLabels = TimeAxis.labels(detail.fromMillis, detail.toMillis),
                    endpointLabel = Uncertainty.num1(current.last),
                ),
            )
            Text(
                text = t.conditionRange(
                    Uncertainty.num1(current.min),
                    Uncertainty.num1(current.max),
                    unit(current.kind, t),
                ),
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

private fun label(kind: EnvironmentSeries.Kind, t: SessionRadonStrings): String = when (kind) {
    EnvironmentSeries.Kind.PRESSURE -> t.conditionPressure
    EnvironmentSeries.Kind.FIELD -> t.conditionField
    EnvironmentSeries.Kind.DEVICE_TEMPERATURE -> t.conditionDeviceTemp
}

private fun unit(kind: EnvironmentSeries.Kind, t: SessionRadonStrings): String = when (kind) {
    EnvironmentSeries.Kind.PRESSURE -> t.unitHpa
    EnvironmentSeries.Kind.FIELD -> t.unitMicroTesla
    EnvironmentSeries.Kind.DEVICE_TEMPERATURE -> t.unitCelsius
}

private fun note(kind: EnvironmentSeries.Kind, t: SessionRadonStrings): String = when (kind) {
    EnvironmentSeries.Kind.PRESSURE -> t.conditionPressureNote
    EnvironmentSeries.Kind.FIELD -> t.conditionFieldNote
    EnvironmentSeries.Kind.DEVICE_TEMPERATURE -> t.conditionDeviceTempNote
}

/**
 * Полётная сессия: высота на той же временной сетке, что и график дозы выше —
 * два состыкованных графика с общей осью времени, никакой двойной оси. Ниже —
 * честный множитель «на эшелоне фон ×N от наземной медианы этой же записи».
 */
@Composable
private fun FlightCard(detail: SessionDetail, unit: DoseUnitSetting, t: SessionRadonStrings) {
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val columns = detail.altitudeColumns ?: return
    val flight = detail.flight
    var info by remember { mutableStateOf(false) }
    if (info) {
        ChartNotesDialog(notes = listOf(t.altitudeNote, t.cosmicNote)) { info = false }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.altitudeTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                ExplainInfoButton(onClick = { info = true })
            }
            val maxAltitude = columns.filterNotNull().maxOrNull()
            if (maxAltitude == null) {
                Text(
                    text = t.noAltitudePoints,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                val yMax = maxAltitude * 1.15f
                TrendChart(
                    spec = TrendChartSpec(
                        columns = columns,
                        yMax = yMax,
                        yTicks = ChartMapping.yTicks(yMax).map {
                            it to HistoryFormat.count(it.toInt())
                        },
                        xLabels = TimeAxis.labels(detail.fromMillis, detail.toMillis),
                        endpointLabel = columns.lastOrNull { it != null }
                            ?.let { HistoryFormat.count(it.toInt()) },
                    ),
                    height = 80.dp,
                )
            }
            if (flight != null) {
                AppDivider()
                val factor = flight.factor
                when {
                    factor != null -> Text(
                        text = t.flightFactor(
                            factor = String.format(Locale.US, "%.1f", factor)
                                .uiDecimal(),
                            flightMedian =
                                DoseFormat.rate(flight.flightMedianMicroSvH ?: 0f, unit),
                            groundMedian =
                                DoseFormat.rate(flight.groundMedianMicroSvH ?: 0f, unit),
                        ),
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    flight.flightMedianMicroSvH != null -> Text(
                        text = t.noGroundPoints,
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventsCard(
    events: List<EventEntity>,
    unit: DoseUnitSetting,
    t: SessionRadonStrings,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = t.eventsTitle.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            events.forEach { event ->
                val dose = event.doseRate?.let {
                    DoseFormat.rate(DoseUnits.rawToMicroSievertPerHour(it), unit)
                }
                Text(
                    text = listOfNotNull(
                        HistoryFormat.dayTime(event.timestamp, now, s = h),
                        if (event.source == EventEntity.SOURCE_DEVIATION) {
                            t.deviationEvent
                        } else {
                            t.excursionEvent
                        },
                        dose,
                    ).joinToString(" · "),
                    style = type.valueSmall,
                    color = colors.warn,
                )
            }
        }
    }
}

private suspend fun loadDetail(graph: AppGraph, sessionId: Long): SessionDetail? {
    val summary = graph.sessionRepository.summary(sessionId) ?: return null
    val to = summary.endedAt ?: System.currentTimeMillis()
    val durationMillis = (to - summary.startedAt).coerceAtLeast(1L)
    val bucketMillis = (durationMillis / CHART_COLUMNS).coerceAtLeast(1_000L)

    val buckets = graph.sessionRepository.chartBuckets(sessionId, bucketMillis)
    val alignedFrom = (summary.startedAt / bucketMillis) * bucketMillis
    val columns = ChartMapping.toColumns(
        buckets = buckets,
        alignedFromMillis = alignedFrom,
        bucketMillis = bucketMillis,
        columnCount = CHART_COLUMNS,
    ) { DoseUnits.rawToMicroSievertPerHour(it.avgDoseRate) }

    val events = graph.sessionRepository.deviationEvents(from = summary.startedAt, to = to)

    // Условия — те же колонки, что у дозы: сравнивать ряды можно только на
    // одной оси времени.
    val conditions = EnvironmentSeries.of(
        rows = graph.environmentRepository.range(summary.startedAt, to),
        // Температура — с ПРИБОРА: его датчик близок к воздуху, а телефон
        // мерит собственную батарею.
        deviceTemperature = graph.measurementRepository
            .rareData(summary.startedAt, to)
            .map { it.timestamp to it.temperature },
        alignedFromMillis = alignedFrom,
        bucketMillis = bucketMillis,
        columnCount = CHART_COLUMNS,
    )

    // Flight view: exact sustain detection on the session's track points
    // (the list badge uses only an approximate count query).
    var altitudeColumns: List<Float?>? = null
    var flight: FlightDetect.Summary? = null
    if (summary.hasTrack) {
        val flightPoints = graph.trackRepository
            .sessionsOverlapping(summary.startedAt, to)
            .flatMap { track -> graph.trackRepository.points(track.id).first() }
            .asSequence()
            .filter { it.timestamp in summary.startedAt..to }
            .map {
                FlightDetect.Point(
                    timestampMillis = it.timestamp,
                    altitudeMeters = it.altitudeMeters,
                    doseMicroSvH = it.doseRate?.let(DoseUnits::rawToMicroSievertPerHour),
                )
            }
            .sortedBy { it.timestampMillis }
            .toList()
        if (FlightDetect.sustainedFlight(flightPoints)) {
            altitudeColumns = FlightDetect.altitudeColumns(
                points = flightPoints,
                alignedFromMillis = alignedFrom,
                bucketMillis = bucketMillis,
                columnCount = CHART_COLUMNS,
            )
            flight = FlightDetect.summary(flightPoints)
        }
    }

    return SessionDetail(
        summary = summary,
        columns = columns,
        stats = ChartMapping.stats(columns),
        conditions = conditions,
        fromMillis = alignedFrom,
        toMillis = to,
        events = events.sortedBy { it.timestamp },
        altitudeColumns = altitudeColumns,
        flight = flight,
    )
}

/**
 * Подтверждение удаления сессии.
 *
 * Спрашивается один раз и словами о последствии: вместе с сессией уходят её
 * измерения, и вернуть их можно только из резервной копии.
 */
@Composable
private fun SessionDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = h.sessionDeleteTitle, style = type.title, color = colors.ink)
                Text(text = h.sessionDeleteBody, style = type.bodySmall, color = colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(text = strings.delete, onClick = onConfirm)
                    AppButton(text = strings.cancel, onClick = onDismiss)
                }
            }
        }
    }
}
