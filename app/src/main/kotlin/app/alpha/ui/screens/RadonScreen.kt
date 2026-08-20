package app.alpha.ui.screens

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
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.RadonTrend
import app.alpha.data.toSpectrum
import app.alpha.ui.components.ExplainInfoButton
import app.alpha.ui.components.AppBackButton
import app.alpha.ui.components.Hint
import app.alpha.ui.components.BarChart
import app.alpha.ui.components.BarChartSpec
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartNotesDialog
import app.alpha.ui.components.Chip
import app.alpha.ui.components.ResultCard
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.components.AppButton
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.logic.RadonReport
import app.alpha.ui.text.SessionRadonCatalogue
import app.alpha.ui.text.SessionRadonStrings
import app.alpha.ui.text.uiDecimal
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
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

/**
 * Меньше двух сводок давления — не изменение, а одно значение: сравнивать
 * край окна не с чем.
 */
private const val MIN_PRESSURE_POINTS = 2

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
    /** Давление на краях окна, гПа; null — барометра нет или данных мало. */
    val pressureFrom: Float? = null,
    val pressureTo: Float? = null,
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
            AppBackButton(onBack = onBack)
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
            // Пустая история и посчитанный индикатор идут по ОДНОМУ шаблону:
            // «данных пока мало» — такой же ответ, как «признак не выражен», и
            // отдельной карточкой он говорил бы, что экран сломан.
            else -> RadonContent(m, t)
        }

        Hint(text = t.ventilationCheck)
    }
}

@Composable
private fun RadonContent(m: RadonModel?, t: SessionRadonStrings) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    // Что означает пунктир и пропуски — под «i»: строка под картинкой
    // читается один раз, а высоту забирает всегда.
    var info by remember { mutableStateOf(false) }
    if (info) {
        ChartNotesDialog(
            notes = buildList {
                add(t.radonChartNote)
                // Про давление рассказываем только когда оно на экране.
                if (m?.pressureTo != null) add(t.radonPressureNote)
            },
        ) { info = false }
    }
    val span = m?.hours?.takeIf { it.isNotEmpty() }?.let {
        HistoryFormat.duration(
            (it.last().hourStartMillis + RadonTrend.HOUR_MILLIS - it.first().hourStartMillis)
                / 1000L,
            s = h,
        )
    }
    ResultCard(
        result = RadonReport.build(
            current = m?.current,
            median = m?.median,
            hours = m?.hours?.size ?: 0,
            spanText = span,
            t = t,
        ),
    ) {
        if (m != null && m.hours.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = t.hourlyTitle.uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                    )
                    Spacer(Modifier.weight(1f))
                    ExplainInfoButton(onClick = { info = true })
                }
                // Ноль на своём месте, отрицательные столбики — вниз. Раньше
                // отрицательный час прижимался к нулю и выглядел как «ровно
                // столько же»; теперь видно, что там оценка континуума вышла
                // выше самих окон.
                val values = m.columns.filterNotNull()
                val high = values.maxOrNull() ?: 0f
                val low = values.minOrNull() ?: 0f
                BarChart(
                    spec = BarChartSpec(
                        values = m.columns,
                        yMax = if (high > 0f) high * 1.25f else 0.01f,
                        yMin = if (low < 0f) low * 1.25f else 0f,
                        refLine = m.median,
                        xStartLabel = edgeLabel(m.fromMillis, m.toMillis, h),
                        xEndLabel = t.now,
                    ),
                    height = 80.dp,
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
                    Text(text = t.trendWindow, style = type.footnote, color = colors.muted)
                }
                // Давление — тем же окном, одной строкой и без вывода: связь
                // радона с падением давления известна, но два ряда рядом её не
                // доказывают, и экран этого не утверждает.
                val from = m.pressureFrom
                val to = m.pressureTo
                if (from != null && to != null) {
                    Text(
                        text = t.pressureChange(
                            Uncertainty.signed1(to - from),
                            Uncertainty.num1(from),
                            Uncertainty.num1(to),
                            t.unitHpa,
                        ),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

private fun rate(cps: Float): String =
    String.format(Locale.US, "%.2f", cps).uiDecimal()

private fun relativeLabel(current: Float?, median: Float?): String {
    if (current == null || median == null || median <= 0f) return "—"
    return "×" + String.format(Locale.US, "%.1f", current / median).uiDecimal()
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
    // Давление тем же окном: край окна против края, без сглаживания — здесь
    // важна не форма кривой, а изменение за срок.
    val pressure = graph.environmentRepository.range(from, now)
        .mapNotNull { it.pressureHpa }

    return RadonModel(
        columns = columns,
        fromMillis = from,
        toMillis = now,
        hours = hours,
        current = current,
        median = RadonTrend.medianRate(hours),
        trend = RadonTrend.trend(hours),
        pressureFrom = pressure.firstOrNull().takeIf { pressure.size >= MIN_PRESSURE_POINTS },
        pressureTo = pressure.lastOrNull().takeIf { pressure.size >= MIN_PRESSURE_POINTS },
    )
}
