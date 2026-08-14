package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import app.radiacode.ui.components.ChartNotesDialog
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SessionRadonCatalogue
import app.radiacode.ui.text.SessionRadonStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")
/**
 * Страховочный пересчёт: основной повод — появление снимка.
 * **Инженерный параметр**: пять минут — заметно реже самой редкой политики
 * опроса спектра (10 мин) и достаточно часто, чтобы незамеченный сигнал не
 * оставил экран застывшим.
 */
private const val FALLBACK_REFRESH_MILLIS = 5L * 60_000L

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
    val strings = LocalStrings.current
    val t = SessionRadonCatalogue.of(strings.language)

    BackHandler { onBack() }

    var windowIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = 24 ч, 1 = 7 д
    var model by remember { mutableStateOf<RadonModel?>(null) }
    var loaded by remember { mutableStateOf(false) }
    // Пересчёт ведут САМИ ДАННЫЕ: ряд считается по приборным снимкам, и повод
    // пересчитать его ровно один — появился новый снимок. Опрос по таймеру
    // остаётся редкой страховкой: если сигнал таблицы почему-то не дойдёт,
    // экран не должен замереть навсегда — этот урок уже оплачен на графиках
    // Главной.
    LaunchedEffect(windowIndex) {
        graph.measurementRepository.deviceSnapshotsChanged().collectLatest {
            model = loadRadon(graph, days = if (windowIndex == 0) 1 else 7)
            loaded = true
        }
    }
    LaunchedEffect(windowIndex) {
        while (true) {
            delay(FALLBACK_REFRESH_MILLIS)
            model = loadRadon(graph, days = if (windowIndex == 0) 1 else 7)
            loaded = true
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
            AppButton(text = "← ${strings.back}", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = t.radonTag, color = colors.ink)
        }

        Segmented(
            options = listOf(t.window24h, t.window7d),
            selectedIndex = windowIndex,
            onSelect = { windowIndex = it },
        )

        val m = model
        when {
            !loaded -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = t.readingSnapshots, style = type.bodySmall, color = colors.muted)
            }
            m == null || m.hours.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(
                        text = t.noRadonDataYet,
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    Text(
                        text = t.radonEmptyExplained,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
            }
            else -> RadonContent(m, t)
        }

        Text(
            text = t.radonCaveat,
            style = type.footnote,
            color = colors.muted,
        )
        Text(
            text = t.ventilationCheck,
            style = type.footnote,
            color = colors.muted,
        )
    }
}

@Composable
private fun RadonContent(m: RadonModel, t: SessionRadonStrings) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    // Что означает пунктир и пропуски — под «i»: строка под картинкой
    // читается один раз, а высоту забирает всегда.
    var info by remember { mutableStateOf(false) }
    if (info) {
        ChartNotesDialog(notes = listOf(t.radonChartNote)) { info = false }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.hourlyTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(text = t.roiRateUnit, style = type.footnote, color = colors.muted)
                Spacer(Modifier.width(Dimens.space1))
                Chip(text = "i", color = colors.ink2, onClick = { info = true })
            }
            val dataMax = m.columns.filterNotNull().maxOrNull()
            if (dataMax == null || dataMax <= 0f) {
                Text(
                    text = t.noMeasurementsInWindow,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                BarChart(
                    spec = BarChartSpec(
                        values = m.columns,
                        yMax = dataMax * 1.25f,
                        refLine = m.median,
                        xStartLabel = edgeLabel(m.fromMillis, m.toMillis, h),
                        xEndLabel = t.now,
                    ),
                    height = 80.dp,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        m.current?.let { rate(it.rateCps) } ?: "—",
                        t.now,
                    ),
                    StatCell(m.median?.let { rate(it) } ?: "—", t.statMedian),
                    StatCell(relativeLabel(m.current?.rateCps, m.median), t.toMedian),
                    StatCell("${m.hours.size}", t.hoursOfData),
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                val (label, color) = when (m.trend) {
                    RadonTrend.Trend.RISING -> t.trendRising to colors.warn
                    RadonTrend.Trend.FALLING -> t.trendFalling to colors.ok
                    // Правило сравнивает проекцию наклона с разбросом: оно
                    // может НЕ найти направления, но не может доказать
                    // постоянство. «Стабильно» утверждало бы второе.
                    RadonTrend.Trend.FLAT -> t.trendFlat to colors.ink2
                    RadonTrend.Trend.UNKNOWN -> t.trendUnknown to colors.muted
                }
                Chip(text = label, color = color)
                Text(
                    text = t.trendWindow,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            m.current?.let { current ->
                Text(
                    text = t.currentPoint(
                        rate = rate(current.rateCps),
                        sigma = rate(current.sigmaCps),
                        duration = HistoryFormat.duration(current.seconds, s = h),
                    ),
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

private fun edgeLabel(
    fromMillis: Long,
    toMillis: Long,
    h: HistoryStrings = HistoryRu,
): String =
    if (toMillis - fromMillis <= 25 * RadonTrend.HOUR_MILLIS) {
        Instant.ofEpochMilli(fromMillis).atZone(ZoneId.systemDefault()).format(HH_MM)
    } else {
        HistoryFormat.day(fromMillis, s = h)
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
