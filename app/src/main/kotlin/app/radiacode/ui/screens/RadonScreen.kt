package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.RadonTrend
import app.radiacode.data.toSpectrum
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")
private const val REFRESH_MILLIS = 2L * 60_000L

@Immutable
private data class RadonModel(
    /** Hour-grid columns for the selected window, oldest first; null = gap. */
    val columns: List<Float?>,
    val fromMillis: Long,
    val toMillis: Long,
    val hours: List<RadonTrend.HourPoint>,
    val current: RadonTrend.HourPoint?,
    val median: Float?,
    val trend: RadonTrend.Trend,
)

/**
 * Радон (вход с экрана Спектр): относительный индикатор радоновых продуктов
 * распада по ROI Bi-214 (609 кэВ) и Pb-214 (352 кэВ) — почасовой график за
 * 24 ч / 7 д, текущий уровень против медианы места и честная формулировка:
 * это НЕ концентрация в Бк/м³. Данные — интервальные разности сохранённых
 * снимков спектра; пока прибор подключён, сервис снимает спектр даже без
 * открытого экрана (медленный фоновый опрос).
 */
@Composable
fun RadonScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    BackHandler { onBack() }

    var windowIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = 24 ч, 1 = 7 д
    var model by remember { mutableStateOf<RadonModel?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(windowIndex) {
        while (true) {
            model = loadRadon(graph, days = if (windowIndex == 0) 1 else 7)
            loaded = true
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
            AppButton(text = "← Назад", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "Радон", color = colors.ink)
        }

        Segmented(
            options = listOf("24 ч", "7 д"),
            selectedIndex = windowIndex,
            onSelect = { windowIndex = it },
        )

        val m = model
        when {
            !loaded -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = "читаю снимки спектра…", style = type.bodySmall, color = colors.muted)
            }
            m == null || m.hours.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(
                        text = "данных пока нет",
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    Text(
                        text = "Индикатор строится по снимкам спектра: пока прибор " +
                            "подключён, они пишутся автоматически (раз в ~10 мин, чаще " +
                            "при открытом Спектре). Первые точки появятся через час-два " +
                            "измерения.",
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
            }
            else -> RadonContent(m)
        }

        Text(
            text = "Относительный индикатор радоновых продуктов распада — net-скорость " +
                "счёта в окнах Bi-214 (609 кэВ) и Pb-214 (352 кэВ). Это не концентрация " +
                "радона в Бк/м³: прибор не откалиброван по объёмной активности.",
            style = type.footnote,
            color = colors.muted,
        )
        Text(
            text = "Проверка: проветрите помещение и наблюдайте спад — продукты распада " +
                "радона вымываются воздухообменом за десятки минут.",
            style = type.footnote,
            color = colors.muted,
        )
    }
}

@Composable
private fun RadonContent(m: RadonModel) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Индикатор по часам".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(text = "имп/с в ROI", style = type.footnote, color = colors.muted)
            }
            val dataMax = m.columns.filterNotNull().maxOrNull()
            if (dataMax == null || dataMax <= 0f) {
                Text(
                    text = "в выбранном окне измерений не было",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                BarChart(
                    spec = BarChartSpec(
                        values = m.columns,
                        yMax = dataMax * 1.25f,
                        refLine = m.median,
                        xStartLabel = edgeLabel(m.fromMillis, m.toMillis),
                        xEndLabel = "сейчас",
                    ),
                    height = 80.dp,
                )
                Text(
                    text = "пунктир — медиана окна · пропуски — часы без измерений",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        m.current?.let { rate(it.rateCps) } ?: "—",
                        "сейчас",
                    ),
                    StatCell(m.median?.let { rate(it) } ?: "—", "медиана"),
                    StatCell(relativeLabel(m.current?.rateCps, m.median), "к медиане"),
                    StatCell("${m.hours.size}", "часов данных"),
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                val (label, color) = when (m.trend) {
                    RadonTrend.Trend.RISING -> "↗ растёт" to colors.warn
                    RadonTrend.Trend.FALLING -> "↘ спадает" to colors.ok
                    // Правило сравнивает проекцию наклона с разбросом: оно
                    // может НЕ найти направления, но не может доказать
                    // постоянство. «Стабильно» утверждало бы второе.
                    RadonTrend.Trend.FLAT -> "— направление не выделено" to colors.ink2
                    RadonTrend.Trend.UNKNOWN -> "тренд: мало данных" to colors.muted
                }
                Chip(text = label, color = color)
                Text(
                    text = "тренд последних 6 часов",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            m.current?.let { current ->
                Text(
                    text = "текущая точка: ${rate(current.rateCps)} ± " +
                        "${rate(current.sigmaCps)} имп/с (1σ) за " +
                        HistoryFormat.duration(current.seconds),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

private fun rate(cps: Float): String =
    String.format(Locale.US, "%.2f", cps).replace('.', ',')

private fun relativeLabel(current: Float?, median: Float?): String {
    if (current == null || median == null || median <= 0f) return "—"
    return "×" + String.format(Locale.US, "%.1f", current / median).replace('.', ',')
}

private fun edgeLabel(fromMillis: Long, toMillis: Long): String =
    if (toMillis - fromMillis <= 25 * RadonTrend.HOUR_MILLIS) {
        Instant.ofEpochMilli(fromMillis).atZone(ZoneId.systemDefault()).format(HH_MM)
    } else {
        HistoryFormat.day(fromMillis)
    }

private suspend fun loadRadon(graph: AppGraph, days: Int): RadonModel {
    val now = System.currentTimeMillis()
    val hourCount = days * 24
    val currentHour = now / RadonTrend.HOUR_MILLIS
    val fromHour = currentHour - hourCount + 1
    val from = fromHour * RadonTrend.HOUR_MILLIS

    // Hourly thinning first — the week of per-minute autosave blobs is never
    // loaded, only one snapshot per hour plus the anchor before the window.
    val metas = graph.measurementRepository
        .deviceSnapshotMeta(from - RadonTrend.HOUR_MILLIS, now)
        .map { RadonTrend.Meta(it.id, it.timestamp, it.durationSeconds) }
    val snapshots = RadonTrend.selectHourlyIds(metas).mapNotNull { id ->
        graph.measurementRepository.spectrumById(id)?.let { entity ->
            val s = entity.toSpectrum()
            RadonTrend.Snapshot(
                timestampMillis = entity.timestamp,
                durationSeconds = s.durationSeconds,
                counts = s.counts,
                calibration = EnergyCalibration(s.a0, s.a1, s.a2),
            )
        }
    }
    val hours = RadonTrend.hourly(RadonTrend.intervals(snapshots))
        .filter { it.hourStartMillis >= from }

    val byHour = hours.associateBy { it.hourStartMillis / RadonTrend.HOUR_MILLIS }
    val columns = List(hourCount) { i ->
        byHour[fromHour + i]?.rateCps?.coerceAtLeast(0f)
    }
    val current = hours.lastOrNull()?.takeIf {
        // «Сейчас» honestly means the last ~2 hours; older data is history.
        now - (it.hourStartMillis + RadonTrend.HOUR_MILLIS) <= 2 * RadonTrend.HOUR_MILLIS
    }
    return RadonModel(
        columns = columns,
        fromMillis = from,
        toMillis = now,
        hours = hours,
        current = current,
        median = RadonTrend.medianRate(hours),
        trend = RadonTrend.trend(hours),
    )
}
