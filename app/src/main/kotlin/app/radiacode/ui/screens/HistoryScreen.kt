package app.radiacode.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.SessionSummary
import app.radiacode.data.db.EventEntity
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DailyDose
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

private const val PAGE_SIZE = 20
private const val REFRESH_MILLIS = 30_000L
private const val DOSE_DAYS = 30

/** One chronological row of История. */
private sealed interface HistoryItem {
    val timestamp: Long

    data class Session(val summary: SessionSummary) : HistoryItem {
        override val timestamp: Long get() = summary.startedAt
    }

    data class Deviation(val event: EventEntity) : HistoryItem {
        override val timestamp: Long get() = event.timestamp
    }
}

@Immutable
private data class HistoryModel(
    val doseTodayMicroSv: Double,
    val dose7dMicroSv: Double,
    val dose30dMicroSv: Double,
    /** µSv per local day, oldest first, [DOSE_DAYS] entries. */
    val dailyDoseMicroSv: List<Float>,
    val fromMillis: Long,
    val toMillis: Long,
    val items: List<HistoryItem>,
    val totalSessions: Long,
)

/**
 * История (SPEC «History»): accumulated dose with the 30-day bar mini-chart,
 * then dense measurement-session rows newest-first with full summaries,
 * interleaved with deviation events and their «обычно здесь X» context.
 * Windowed pages keep months of data smooth; a session opens its detail.
 */
@Composable
fun HistoryScreen(graph: AppGraph, onOpenSession: (Long) -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    var pages by remember { mutableIntStateOf(1) }
    var model by remember { mutableStateOf<HistoryModel?>(null) }
    LaunchedEffect(pages) {
        while (true) {
            model = loadHistory(graph, pages * PAGE_SIZE)
            delay(REFRESH_MILLIS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(text = "История", color = colors.ink)
            Spacer(Modifier.weight(1f))
            model?.let { Chip(text = "${it.totalSessions} сессий") }
        }

        val m = model
        if (m == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "читаю журнал…", style = type.bodySmall, color = colors.muted)
            }
        } else {
            AccumulatedDoseCard(m, unit)

            if (m.items.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        Text(
                            text = "сессий пока нет",
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        Text(
                            text = "Сессия — непрерывный период измерения: она начинается " +
                                "при подключении прибора и закрывается при отключении.",
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
                                    summary = item.summary,
                                    unit = unit,
                                    onClick = { onOpenSession(item.summary.id) },
                                )
                                is HistoryItem.Deviation -> DeviationRow(item.event, unit)
                            }
                        }
                    }
                }
            }

            if (m.totalSessions > m.items.count { it is HistoryItem.Session }) {
                AppButton(
                    text = "Показать ещё",
                    onClick = { pages += 1 },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun AccumulatedDoseCard(model: HistoryModel, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Накопленная доза".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(text = "расчёт", style = type.footnote, color = colors.muted)
            }
            val dailyMax = model.dailyDoseMicroSv.maxOrNull() ?: 0f
            if (dailyMax > 0f) {
                BarChart(
                    spec = BarChartSpec(
                        values = model.dailyDoseMicroSv.map { if (it > 0f) it else null },
                        yMax = dailyMax * 1.15f,
                        emphasizeLast = true,
                        xStartLabel = HistoryFormat.day(model.fromMillis),
                        xEndLabel = HistoryFormat.day(model.toMillis),
                    ),
                    height = 55.dp,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        DoseFormat.dose(model.doseTodayMicroSv, unit),
                        "сегодня, ${DoseFormat.doseUnitLabel(unit)}",
                    ),
                    StatCell(DoseFormat.dose(model.dose7dMicroSv, unit), "7 дней"),
                    StatCell(DoseFormat.dose(model.dose30dMicroSv, unit), "30 дней"),
                ),
            )
            Text(
                text = "Сумма мощности дозы по секундам измерения — не путать " +
                    "с текущей мощностью дозы.",
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(summary: SessionSummary, unit: DoseUnitSetting, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val endedAt = summary.endedAt
    val durationSeconds = ((endedAt ?: now) - summary.startedAt) / 1000L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.placeName ?: "Сессия",
                style = type.label,
                color = colors.ink,
            )
            if (endedAt == null) {
                Text(
                    text = "· идёт",
                    style = type.label,
                    color = colors.ok,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = HistoryFormat.dayTime(summary.startedAt, now) +
                    " · " + HistoryFormat.duration(durationSeconds),
                style = type.footnote,
                color = colors.ink2,
            )
        }

        val stats = summary.stats
        if (stats.sampleCount > 0 && stats.avgDoseRate != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space3),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DataItem("ср", DoseFormat.rate(stats.avgDoseRate, unit))
                DataItem("макс", DoseFormat.rate(stats.maxDoseRate ?: 0f, unit))
                DataItem("доза", DoseFormat.doseWithUnit(summary.doseMicroSv, unit))
                DataItem("n", HistoryFormat.count(stats.sampleCount))
                val badges = listOfNotNull(
                    "трек".takeIf { summary.hasTrack },
                    "спектр".takeIf { summary.hasSpectrum },
                )
                if (badges.isNotEmpty()) {
                    Text(
                        text = badges.joinToString(" · "),
                        style = type.valueSmall,
                        color = colors.ink2,
                    )
                }
            }
        } else {
            Text(
                text = "измерений в этой сессии не записано",
                style = type.valueSmall,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun DataItem(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalAppColors.current
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
    val type = LocalAppTypography.current
    val now = System.currentTimeMillis()
    val kind = when (event.source) {
        EventEntity.SOURCE_DEVIATION -> "Отклонение"
        else -> "Точка превышения"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⚠ $kind", style = type.label, color = colors.warn)
            Spacer(Modifier.weight(1f))
            Text(
                text = HistoryFormat.dayTime(event.timestamp, now),
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
                    label = DoseFormat.rateUnitLabel(unit),
                    value = DoseFormat.rate(DoseUnits.rawToMicroSievertPerHour(it), unit),
                    valueColor = colors.warn,
                )
            }
            // param1 of a deviation stores the baseline typical high, nSv/h.
            if (event.source == EventEntity.SOURCE_DEVIATION && event.param1 > 0) {
                DataItem("обычно", DoseFormat.rate(event.param1 / 1000f, unit))
            }
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

    val items = (
        sessions.map { HistoryItem.Session(it) } + events.map { HistoryItem.Deviation(it) }
        ).sortedByDescending { it.timestamp }

    val zone = ZoneId.systemDefault()
    val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val from30d = now - DOSE_DAYS.toLong() * 24 * 3600_000
    // Hour buckets across the 30-day span feed both the totals and the
    // per-day bars (AVG×COUNT integration is exact for any bucket width).
    val buckets30 = graph.measurementRepository.downsampledSamples(
        from = from30d,
        to = now,
        bucketMillis = 3_600_000L,
    )

    // «Сегодня» starts at local midnight, which epoch-hour buckets straddle —
    // minute buckets keep it exact (same as the Монитор figure).
    val todayBuckets = graph.measurementRepository.downsampledSamples(
        from = startOfDay,
        to = now,
        bucketMillis = 60_000L,
    )

    return HistoryModel(
        doseTodayMicroSv = ChartMapping.integrateDoseMicroSv(todayBuckets),
        dose7dMicroSv = ChartMapping.integrateDoseMicroSv(
            buckets30.filter { it.bucketStart >= now - 7L * 24 * 3600_000 },
        ),
        dose30dMicroSv = ChartMapping.integrateDoseMicroSv(buckets30),
        dailyDoseMicroSv = DailyDose.perDay(buckets30, now, zone, DOSE_DAYS),
        fromMillis = from30d,
        toMillis = now,
        items = items,
        totalSessions = totalSessions,
    )
}
