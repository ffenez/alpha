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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.data.DoseUnitSetting
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DailyDose
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «Сколько набралось» — отдельный экран вместо блока в Истории.
 *
 * ## Почему отдельный
 *
 * Накопленная доза занимала верх Истории, куда приходят за последними
 * записями, и мешала им — а спрашивают о ней редко и по конкретному поводу
 * («сколько за сегодня?»). Поэтому вход у неё там, где этот вопрос и
 * возникает: на плитке Главной с числом за сегодня.
 *
 * ## Что здесь есть и чего нет
 *
 * Есть числа за периоды, у которых ЕСТЬ история, столбики по дням и время,
 * за которое всё это измерено. Нет годовой оценки: «мЗв/год» рядом с
 * накопленным читается как измеренная доза человека, чем она не является.
 */
@Composable
fun DoseScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    BackHandler { onBack() }

    var model by remember { mutableStateOf<DoseModel?>(null) }
    LaunchedEffect(Unit) { model = withContext(Dispatchers.IO) { loadDose(graph) } }

    var periodIndex by rememberSaveable { mutableIntStateOf(1) }
    val periodDays = DOSE_PERIODS[periodIndex]

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
            Chip(text = strings.accumulatedDose, color = colors.ink)
        }

        val m = model
        if (m == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = strings.readingJournal, style = type.bodySmall, color = colors.muted)
            }
            return@Column
        }

        val days = m.dailyDose.takeLast(periodDays)
        val measuredSeconds = days.sumOf { it.measuredSeconds }
        // Период показывается, только если измерения ДО него были: иначе он
        // повторяет число более короткого, и три одинаковых значения выглядят
        // поломкой, хотя всё верно.
        val depthDays = remember(m.dailyDose) { DailyDose.measuredDepthDays(m.dailyDose) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                StatGrid(
                    cells = buildList {
                        add(
                            StatCell(
                                DoseFormat.dose(m.todayMicroSv, unit),
                                strings.todayWithUnit(DoseFormat.doseUnitLabel(unit, s = strings)),
                            ),
                        )
                        if (depthDays >= 1) {
                            add(StatCell(DoseFormat.dose(m.week, unit), strings.days7))
                        }
                        if (depthDays >= 7) {
                            add(StatCell(DoseFormat.dose(m.month, unit), strings.days30))
                        }
                    },
                )
                Text(
                    text = h.measuredFor(
                        HistoryFormat.duration(m.dailyDose.sumOf { it.measuredSeconds }, h),
                    ),
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Segmented(
                    options = listOf(strings.days7, strings.days30, h.days90),
                    selectedIndex = periodIndex,
                    onSelect = { periodIndex = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                val dailyMax = days.maxOfOrNull { it.microSv } ?: 0f
                if (dailyMax > 0f) {
                    BarChart(
                        spec = BarChartSpec(
                            // День без измерений — ПУСТОЕ место, а не нулевая
                            // доза: прибор в этот день не работал, и рисовать
                            // за него ноль значит утверждать, что дозы не было.
                            values = days.map { it.microSv.takeIf { v -> v > 0f } },
                            // Полый столбик = день измерен не полностью.
                            partial = days.map { !it.full },
                            yMax = dailyMax * 1.15f,
                            emphasizeLast = true,
                            xStartLabel = HistoryFormat.day(
                                m.toMillis - periodDays.toLong() * 86_400_000L,
                                s = h,
                            ),
                            xEndLabel = HistoryFormat.day(m.toMillis, s = h),
                        ),
                        height = 96.dp,
                    )
                } else {
                    Text(
                        text = strings.noData,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                }
                Hint(text = h.recordedOfPeriod(HistoryFormat.duration(measuredSeconds, h)))
            }
        }
    }
}

/** Что показывает экран; считается один раз при открытии. */
private data class DoseModel(
    val todayMicroSv: Double,
    val week: Double,
    val month: Double,
    /** Сутки, старые первыми. */
    val dailyDose: List<DailyDose.Day>,
    val toMillis: Long,
)

/** Периоды графика, дни. */
private val DOSE_PERIODS = listOf(7, 30, 90)

/** Глубина суточной истории: самый длинный период графика. */
private const val DOSE_DAYS = 90

private suspend fun loadDose(graph: AppGraph): DoseModel {
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val from = now - DOSE_DAYS.toLong() * 24 * 3_600_000L
    // Часовые корзины: точное интегрирование AVG×COUNT при любой ширине.
    val hourly = graph.measurementRepository.downsampledSamples(
        from = from,
        to = now,
        bucketMillis = 3_600_000L,
    )
    // «Сегодня» начинается в местную полночь, которую часовая корзина
    // пересекает, — минутные корзины держат его точным.
    val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val todayBuckets = graph.measurementRepository.downsampledSamples(
        from = startOfDay,
        to = now,
        bucketMillis = 60_000L,
    )
    return DoseModel(
        todayMicroSv = ChartMapping.integrateDoseMicroSv(todayBuckets),
        week = ChartMapping.integrateDoseMicroSv(
            hourly.filter { it.bucketStart >= now - 7L * 24 * 3_600_000L },
        ),
        month = ChartMapping.integrateDoseMicroSv(
            hourly.filter { it.bucketStart >= now - 30L * 24 * 3_600_000L },
        ),
        dailyDose = DailyDose.perDay(hourly, now, zone, DOSE_DAYS),
        toMillis = now,
    )
}
