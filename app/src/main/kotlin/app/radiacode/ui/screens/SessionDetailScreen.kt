package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.TrendChart
import app.radiacode.ui.components.TrendChartSpec
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.TimeAxis
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

private const val CHART_COLUMNS = 48

@Immutable
private data class SessionDetail(
    val summary: SessionSummary,
    val columns: List<Float?>,
    val stats: ChartMapping.Stats?,
    val fromMillis: Long,
    val toMillis: Long,
    val events: List<EventEntity>,
)

/**
 * Session detail: the full-period dose-rate chart (bucketed to 48 columns
 * whatever the duration) plus the complete summary and the session's
 * deviation events.
 */
@Composable
fun SessionDetailScreen(graph: AppGraph, sessionId: Long, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    var detail by remember { mutableStateOf<SessionDetail?>(null) }
    var missing by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId) {
        val loaded = loadDetail(graph, sessionId)
        detail = loaded
        missing = loaded == null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = "← Назад", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "Сессия", color = colors.ink)
        }

        val d = detail
        when {
            missing -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "сессия не найдена", style = type.bodySmall, color = colors.muted)
            }
            d == null -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "читаю сессию…", style = type.bodySmall, color = colors.muted)
            }
            else -> {
                SummaryCard(d.summary, unit)
                ChartCard(d, unit)
                if (d.events.isNotEmpty()) EventsCard(d.events, unit)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: SessionSummary, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val durationSeconds = ((summary.endedAt ?: now) - summary.startedAt) / 1000L
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.placeName ?: "Без места",
                    style = type.title,
                    color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                if (summary.endedAt == null) Chip(text = "идёт", color = colors.ok)
            }
            Text(
                text = HistoryFormat.dayTime(summary.startedAt, now) +
                    " · ${HistoryFormat.duration(durationSeconds)}",
                style = type.footnote,
                color = colors.ink2,
            )
            AppDivider()

            val stats = summary.stats
            if (stats.sampleCount == 0 || stats.avgDoseRate == null) {
                Text(
                    text = "измерений в этой сессии не записано",
                    style = type.body,
                    color = colors.muted,
                )
            } else {
                DetailRow("измерений", HistoryFormat.count(stats.sampleCount))
                DetailRow(
                    "мощность дозы",
                    "ср ${DoseFormat.rate(stats.avgDoseRate, unit)} · " +
                        "мин ${DoseFormat.rate(stats.minDoseRate ?: 0f, unit)} · " +
                        "макс ${DoseFormat.rate(stats.maxDoseRate ?: 0f, unit)} " +
                        DoseFormat.rateUnitLabel(unit),
                )
                DetailRow(
                    "скорость счёта",
                    "ср ${(stats.avgCountRate ?: 0f).toInt()} · " +
                        "макс ${(stats.maxCountRate ?: 0f).toInt()} с⁻¹",
                )
                DetailRow(
                    "доза за сессию · расчёт",
                    DoseFormat.doseWithUnit(summary.doseMicroSv, unit),
                )
            }

            if (summary.hasSpectrum || summary.hasTrack) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    if (summary.hasSpectrum) Chip(text = "спектр")
                    if (summary.hasTrack) Chip(text = "трек")
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
private fun ChartCard(detail: SessionDetail, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = "Мощность дозы · вся сессия".uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            val stats = detail.stats
            if (stats == null) {
                Text(
                    text = "данных для графика нет",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                val yMax = ChartMapping.yMax(stats.max, null)
                TrendChart(
                    spec = TrendChartSpec(
                        columns = detail.columns,
                        yMax = yMax,
                        yTicks = ChartMapping.yTicks(yMax).map { it to DoseFormat.rate(it, unit) },
                        xLabels = TimeAxis.labels(detail.fromMillis, detail.toMillis),
                    ),
                )
                StatGrid(
                    cells = listOf(
                        StatCell(DoseFormat.rate(stats.min, unit), "мин"),
                        StatCell(DoseFormat.rate(stats.median, unit), "медиана"),
                        StatCell(DoseFormat.rate(stats.max, unit), "макс"),
                        StatCell(DoseFormat.rate(stats.sigma, unit), "σ"),
                        StatCell(HistoryFormat.count(detail.summary.stats.sampleCount), "n"),
                    ),
                )
            }
        }
    }
}

@Composable
private fun EventsCard(events: List<EventEntity>, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = "События сессии".uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            events.forEach { event ->
                val dose = event.doseRate?.let {
                    DoseFormat.rateWithUnit(DoseUnits.rawToMicroSievertPerHour(it), unit)
                }
                Text(
                    text = listOfNotNull(
                        HistoryFormat.dayTime(event.timestamp, now),
                        if (event.source == EventEntity.SOURCE_DEVIATION) {
                            "отклонение"
                        } else {
                            "точка превышения"
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
    return SessionDetail(
        summary = summary,
        columns = columns,
        stats = ChartMapping.stats(columns),
        fromMillis = alignedFrom,
        toMillis = to,
        events = events.sortedBy { it.timestamp },
    )
}
