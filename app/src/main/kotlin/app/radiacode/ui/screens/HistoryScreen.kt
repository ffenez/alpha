package app.radiacode.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableIntStateOf
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
import app.radiacode.ui.components.PixelBox
import app.radiacode.ui.components.PixelButton
import app.radiacode.ui.components.PixelTag
import app.radiacode.ui.components.StatusLine
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

private const val PAGE_SIZE = 20
private const val REFRESH_MILLIS = 30_000L

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
    val items: List<HistoryItem>,
    val totalSessions: Long,
)

/**
 * История (SPEC «History»): accumulated dose, then measurement sessions
 * newest-first with per-session summaries, interleaved with deviation events
 * and their «обычно здесь X» context. Windowed pages keep months of data
 * smooth; a session opens its detail screen.
 */
@Composable
fun HistoryScreen(graph: AppGraph, onOpenSession: (Long) -> Unit) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
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
            .padding(PixelDimens.space4),
        verticalArrangement = Arrangement.spacedBy(PixelDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ИСТОРИЯ", style = type.heading, color = colors.text)
            Spacer(Modifier.weight(1f))
            model?.let { PixelTag(text = "${it.totalSessions} сессий") }
        }

        val m = model
        if (m == null) {
            PixelBox(modifier = Modifier.fillMaxWidth()) {
                StatusLine(text = "читаю журнал", cursor = true, color = colors.textMuted)
            }
        } else {
            AccumulatedDoseCard(m, unit)

            if (m.items.isEmpty()) {
                PixelBox(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                        StatusLine(text = "сессий пока нет", color = colors.textSecondary)
                        Text(
                            text = "Сессия — непрерывный период измерения: она начинается " +
                                "при подключении прибора и закрывается при отключении.",
                            style = type.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            m.items.forEach { item ->
                when (item) {
                    is HistoryItem.Session -> SessionCard(
                        summary = item.summary,
                        unit = unit,
                        onClick = { onOpenSession(item.summary.id) },
                    )
                    is HistoryItem.Deviation -> DeviationRow(item.event, unit)
                }
            }

            if (m.totalSessions > m.items.count { it is HistoryItem.Session }) {
                PixelButton(
                    text = "ПОКАЗАТЬ ЕЩЁ",
                    onClick = { pages += 1 },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun AccumulatedDoseCard(model: HistoryModel, unit: DoseUnitSetting) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("НАКОПЛЕННАЯ ДОЗА", style = type.label, color = colors.text)
                Spacer(Modifier.weight(1f))
                PixelTag(text = "расчёт")
            }
            DoseRow("сегодня", model.doseTodayMicroSv, unit)
            DoseRow("7 дней", model.dose7dMicroSv, unit)
            DoseRow("30 дней", model.dose30dMicroSv, unit)
            Text(
                text = "Сумма мощности дозы по секундам измерения — не путать " +
                    "с текущей мощностью дозы.",
                style = type.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun DoseRow(label: String, doseMicroSv: Double, unit: DoseUnitSetting) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = type.value, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            text = DoseFormat.doseWithUnit(doseMicroSv, unit),
            style = type.value,
            color = colors.text,
        )
    }
}

@Composable
private fun SessionCard(summary: SessionSummary, unit: DoseUnitSetting, onClick: () -> Unit) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val now = System.currentTimeMillis()
    val endedAt = summary.endedAt
    val durationSeconds = ((endedAt ?: now) - summary.startedAt) / 1000L

    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (summary.placeName ?: "сессия").uppercase(),
                    style = type.label,
                    color = colors.text,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = HistoryFormat.dayTime(summary.startedAt, now),
                    style = type.labelSmall,
                    color = colors.textMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
            ) {
                if (endedAt == null) {
                    PixelTag(text = "идёт", color = colors.accent)
                }
                Text(
                    text = HistoryFormat.duration(durationSeconds) +
                        " · ${HistoryFormat.count(summary.stats.sampleCount)} изм.",
                    style = type.labelSmall,
                    color = colors.textSecondary,
                )
            }

            val stats = summary.stats
            if (stats.sampleCount > 0 && stats.avgDoseRate != null) {
                Text(
                    text = "ср ${rate(stats.avgDoseRate, unit)} · " +
                        "мин ${rate(stats.minDoseRate ?: 0f, unit)} · " +
                        "макс ${rate(stats.maxDoseRate ?: 0f, unit)} " +
                        DoseFormat.rateUnitLabel(unit),
                    style = type.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = "CPS ср ${(stats.avgCountRate ?: 0f).toInt()} · " +
                        "макс ${(stats.maxCountRate ?: 0f).toInt()} · " +
                        "доза ${DoseFormat.doseWithUnit(summary.doseMicroSv, unit)}",
                    style = type.labelSmall,
                    color = colors.textSecondary,
                )
            } else {
                Text(
                    text = "измерений в этой сессии не записано",
                    style = type.labelSmall,
                    color = colors.textMuted,
                )
            }

            if (summary.hasSpectrum || summary.hasTrack) {
                Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    if (summary.hasSpectrum) PixelTag(text = "спектр")
                    if (summary.hasTrack) PixelTag(text = "трек")
                }
            }
        }
    }
}

@Composable
private fun DeviationRow(event: EventEntity, unit: DoseUnitSetting) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val now = System.currentTimeMillis()
    val kind = when (event.source) {
        EventEntity.SOURCE_DEVIATION -> "отклонение"
        else -> "точка превышения"
    }
    val dose = event.doseRate?.let {
        rate(DoseUnits.rawToMicroSievertPerHour(it), unit) + " " + DoseFormat.rateUnitLabel(unit)
    }
    // param1 of a deviation stores the baseline typical high, nSv/h.
    val usual = if (event.source == EventEntity.SOURCE_DEVIATION && event.param1 > 0) {
        "обычно здесь ${rate(event.param1 / 1000f, unit)}"
    } else {
        null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
        modifier = Modifier.padding(horizontal = PixelDimens.space2),
    ) {
        Text(text = "!", style = type.label, color = colors.aboveUsual)
        Text(
            text = listOfNotNull(
                HistoryFormat.dayTime(event.timestamp, now),
                kind,
                dose,
                usual,
            ).joinToString(" · "),
            style = type.labelSmall,
            color = colors.aboveUsual,
        )
    }
}

private fun rate(microSvH: Float, unit: DoseUnitSetting): String = DoseFormat.rate(microSvH, unit)

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

    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    return HistoryModel(
        doseTodayMicroSv = doseOver(graph, startOfDay, now),
        dose7dMicroSv = doseOver(graph, now - 7L * 24 * 3600_000, now),
        dose30dMicroSv = doseOver(graph, now - 30L * 24 * 3600_000, now),
        items = items,
        totalSessions = totalSessions,
    )
}

/** AVG×COUNT integration is exact for any bucket width; hour buckets are cheap. */
private suspend fun doseOver(graph: AppGraph, from: Long, to: Long): Double =
    ChartMapping.integrateDoseMicroSv(
        graph.measurementRepository.downsampledSamples(from, to, bucketMillis = 3_600_000L),
    )
