package app.radiacode.ui.screens

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import app.radiacode.AppGraph
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.SessionSummary
import app.radiacode.data.db.EventEntity
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.data.export.SeriesExport
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.ChartNotesDialog
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.TrendChart
import app.radiacode.ui.components.TrendChartSpec
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.FlightDetect
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.TimeAxis
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SessionRadonCatalogue
import app.radiacode.ui.text.SessionRadonStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
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
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val content = pendingCsv
        pendingCsv = null
        if (uri != null && content != null) {
            scope.launch {
                notice = if (writeTextToUri(context, uri, content)) t.exportSaved else t.exportFailed
            }
        }
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = "← ${strings.back}", onClick = onBack)
            Spacer(Modifier.weight(1f))
            // Ряд измерений в CSV: до сих пор наружу уезжали только спектры, то
            // есть срез, а не ход измерения. Сохранение — явное действие через
            // системный диалог, как и у спектров.
            detail?.let { d ->
                Chip(
                    text = t.exportCsv,
                    color = colors.dataText,
                    onClick = {
                        scope.launch {
                            pendingCsv = SeriesExport.csv(
                                graph.measurementRepository.samplesList(
                                    d.summary.startedAt,
                                    d.summary.endedAt ?: d.toMillis,
                                ),
                            )
                            csvLauncher.launch(
                                SeriesExport.fileName(d.summary.startedAt, "csv"),
                            )
                        }
                    },
                )
                Spacer(Modifier.width(Dimens.space2))
            }
            Chip(text = t.sessionTag, color = colors.ink)
        }

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
                    Chip(
                        text = strings.profileEllipsis,
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

            val stats = summary.stats
            val avgMicroSvH = stats.avgDoseRateMicroSvH
            if (stats.sampleCount == 0 || avgMicroSvH == null) {
                Text(
                    text = strings.noSamplesInSession,
                    style = type.body,
                    color = colors.muted,
                )
            } else {
                DetailRow(t.samplesLabel, HistoryFormat.count(stats.sampleCount))
                DetailRow(
                    t.doseRateLabel,
                    t.doseRateSummary(
                        avg = DoseFormat.rate(avgMicroSvH, unit),
                        min = DoseFormat.rate(stats.minDoseRateMicroSvH ?: 0f, unit),
                        max = DoseFormat.rate(stats.maxDoseRateMicroSvH ?: 0f, unit),
                        unit = DoseFormat.rateUnitLabel(unit, s = strings),
                    ),
                )
                DetailRow(
                    t.countRateLabel,
                    t.countRateSummary(
                        avg = "${(stats.avgCountRate ?: 0f).toInt()}",
                        max = "${(stats.maxCountRate ?: 0f).toInt()}",
                    ),
                )
                DetailRow(
                    t.sessionDoseLabel,
                    DoseFormat.doseWithUnit(summary.doseMicroSv, unit, s = strings),
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
                // Явное действие рядом с картинкой: тап по самому графику
                // делает то же, но по одной картинке это не видно.
                if (detail.stats != null) {
                    Chip(text = t.openFullChart, color = colors.dataText, onClick = onOpenChart)
                }
                Chip(text = "i", color = colors.ink2, onClick = { info = true })
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
                StatGrid(
                    cells = listOf(
                        StatCell(DoseFormat.rate(stats.min, unit), t.statMin),
                        StatCell(DoseFormat.rate(stats.median, unit), t.statMedian),
                        StatCell(DoseFormat.rate(stats.max, unit), t.statMax),
                        StatCell(
                            DoseFormat.rate(stats.sigma, unit),
                            t.sdWithUnit(DoseFormat.rateUnitLabel(unit, s = strings)),
                        ),
                        StatCell(HistoryFormat.count(detail.summary.stats.sampleCount), "n"),
                    ),
                )
            }
        }
    }
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
                Chip(text = "i", color = colors.ink2, onClick = { info = true })
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
                                .replace('.', ','),
                            flightMedian =
                                DoseFormat.rate(flight.flightMedianMicroSvH ?: 0f, unit),
                            groundMedian =
                                DoseFormat.rate(flight.groundMedianMicroSvH ?: 0f, unit),
                            unit = DoseFormat.rateUnitLabel(unit, s = strings),
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
                    DoseFormat.rateWithUnit(DoseUnits.rawToMicroSievertPerHour(it), unit)
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
        fromMillis = alignedFrom,
        toMillis = to,
        events = events.sortedBy { it.timestamp },
        altitudeColumns = altitudeColumns,
        flight = flight,
    )
}
