package app.alpha.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.alpha.AppGraph
import app.alpha.device.ConnectionState
import app.alpha.data.DoseUnitSetting
import app.alpha.ui.components.BarChart
import app.alpha.ui.components.BarChartSpec
import app.alpha.ui.components.Card
import app.alpha.ui.components.EntityHeader
import app.alpha.ui.components.Hint
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.logic.ChartMapping
import app.alpha.ui.logic.DailyDose
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.DosePeriod
import app.alpha.ui.logic.DosePeriods
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.uiDecimal
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «Сколько набралось» за выбранный период.
 *
 * ## Один период на весь экран
 *
 * Переключатель 7 / 30 / 90 суток меняет ВСЁ: главное число, покрытие,
 * подписи дат, столбики и статистику под ними. Прежде верхняя карточка жила
 * своей жизнью — складывала скользящее окно «последние 7×24 ч», пока график
 * рисовал календарные сутки, — и экран распадался на две несогласованные
 * половины ([DosePeriod]).
 *
 * ## Что здесь есть и чего нет
 *
 * Есть накопленное по измерениям, время, за которое это накоплено, и его доля
 * от периода.
 *
 * Годовая оценка есть, но ОТДЕЛЬНОЙ карточкой и названа оценкой: год никто не
 * мерил, это средние полные сутки, растянутые на 365 дней. Стоять рядом с
 * измеренным числом она не имеет права — «мЗв/год» в одной строке с
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
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        // Шапка — общий компонент записи: стрелка и заголовок вместе, как на
        // всех остальных экранах.
        EntityHeader(title = strings.accumulatedDose, onBack = onBack)

        val m = model
        if (m == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = strings.readingJournal, style = type.bodySmall, color = colors.muted)
            }
            return@Column
        }

        // Ограничение, меняющее прочтение числа, стоит рядом с числом, а не
        // в справке: доза относится к одному прибору.
        if (m.otherDevices) {
            Hint(text = strings.doseOneDevice)
        }

        val periodDays = DosePeriods.LENGTHS[periodIndex]
        val period = remember(m.dailyDose, periodDays, m.todayElapsedSeconds) {
            DosePeriods.of(m.dailyDose, periodDays, m.todayElapsedSeconds)
        }

        // Итог: одно число, его период и — критической строкой — за сколько
        // измеренного времени оно набралось. Без неё «9,69 мкЗв за 30 дней»
        // читается как доза за месяц, а это доза за 50 часов внутри месяца.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = DoseFormat.doseWithUnit(period.microSv, unit, strings),
                    style = type.valueLarge,
                    color = colors.ink,
                )
                Text(
                    text = h.forDays(periodDays),
                    style = type.footnote,
                    color = colors.ink2,
                )
                Text(
                    text = h.measuredWithCoverage(
                        duration = HistoryFormat.duration(period.measuredSeconds, h),
                        percent = percent(period.coverage),
                    ),
                    style = type.footnote,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Dimens.space1),
                )
                // Пока история короче периода, все три периода дают одно
                // число — и это верно: складывать больше нечего. Сказать это
                // обязано САМО ЧИСЛО, иначе три одинаковых значения читаются
                // как поломка.
                if (period.shorterThanPeriod) {
                    Text(
                        text = h.measuredOnlyDays(period.measuredDays, periodDays),
                        style = type.footnote,
                        color = colors.warn,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Segmented(
            options = listOf(strings.days7, strings.days30, h.days90),
            selectedIndex = periodIndex,
            onSelect = {
                periodIndex = it
                // Выбранный день принадлежит прежнему набору столбцов, и
                // после смены периода указывал бы на другую дату.
                selectedDay = null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                val dailyMax = period.maxDayMicroSv ?: 0f
                if (dailyMax > 0f) {
                    BarChart(
                        spec = BarChartSpec(
                            // День без измерений — ПУСТОЕ место, а не нулевая
                            // доза: прибор в этот день не работал, и рисовать
                            // за него ноль значит утверждать, что дозы не было.
                            values = period.daily.map { it.microSv.takeIf { v -> v > 0f } },
                            // Полый столбик = день измерен не полностью.
                            partial = period.daily.map { !it.full },
                            yMax = dailyMax * 1.15f,
                            emphasizeLast = true,
                            xStartLabel = HistoryFormat.day(
                                m.dayStartMillis(m.dailyDose.size - period.daily.size),
                                s = h,
                            ),
                            xEndLabel = HistoryFormat.day(m.toMillis, s = h),
                            selectedIndex = selectedDay,
                        ),
                        height = 120.dp,
                        onSelect = { index ->
                            selectedDay = if (selectedDay == index) null else index
                        },
                    )
                    // Разбор одного дня — по нажатию и на месте вопроса.
                    // Постоянные подписи над всеми столбцами превращают
                    // картинку в таблицу.
                    val chosen = selectedDay?.let { period.daily.getOrNull(it) }
                    if (chosen == null) {
                        Hint(text = h.tapDayHint, style = type.footnote, color = colors.muted)
                    } else {
                        val date = HistoryFormat.day(
                            m.dayStartMillis(
                                m.dailyDose.size - period.daily.size + selectedDay!!,
                            ),
                            s = h,
                        )
                        Text(
                            text = if (chosen.measuredSeconds <= 0L) {
                                "$date · ${h.dayWithoutData}"
                            } else {
                                h.dayDose(
                                    date = date,
                                    dose = DoseFormat.doseWithUnit(
                                        chosen.microSv.toDouble(),
                                        unit,
                                        strings,
                                    ),
                                    duration = HistoryFormat.duration(chosen.measuredSeconds, h),
                                )
                            },
                            style = type.footnoteMono,
                            color = colors.ink,
                        )
                    }
                } else {
                    Text(text = strings.noData, style = type.bodySmall, color = colors.muted)
                }
            }
        }

        // Оценка стоит ОТДЕЛЬНО от измеренного и подписана оценкой: год
        // никто не мерил, это средние полные сутки, растянутые на 365 дней.
        period.projectedYearMicroSv?.let { year ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = h.projectedYear(
                            DoseFormat.doseWithUnit(year.toDouble(), unit, strings),
                        ),
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    Hint(
                        text = h.projectedYearNote,
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }

        // Статистика — только та, у которой есть знаменатель: среднее берётся
        // по полным суткам, иначе день, записанный двадцать минут, занижал бы
        // его тем сильнее, чем чаще выключали прибор.
        val average = period.averageFullDayMicroSv
        val peak = period.maxDayMicroSv
        if (average != null || peak != null) {
            StatGrid(
                cells = buildList {
                    average?.let {
                        add(StatCell(DoseFormat.dose(it.toDouble(), unit), h.averageFullDay))
                    }
                    peak?.let {
                        add(StatCell(DoseFormat.dose(it.toDouble(), unit), h.maxDay))
                    }
                },
            )
        }
    }
}

/** Доля в процентах с одним знаком: покрытие бывает и долями процента. */
private fun percent(fraction: Float): String =
    String.format(Locale.US, "%.1f", fraction * 100).uiDecimal()

/** Что показывает экран; считается один раз при открытии. */
private data class DoseModel(
    /** Сутки, старые первыми. */
    val dailyDose: List<DailyDose.Day>,
    /**
     * За те же сутки писал и другой прибор. Доза считается по одному:
     * приборы пишут независимо, и сложение посчитало бы одни часы дважды.
     */
    val otherDevices: Boolean = false,
    val toMillis: Long,
    /** Сколько секунд сегодняшних суток прошло — знаменатель покрытия. */
    val todayElapsedSeconds: Long,
    private val firstDayStartMillis: Long,
) {
    /** Начало суток с этим номером в [dailyDose], мс эпохи. */
    fun dayStartMillis(index: Int): Long =
        firstDayStartMillis + index.coerceAtLeast(0).toLong() * 86_400_000L
}

/** Глубина суточной истории: самый длинный период графика. */
private val DOSE_DAYS = DosePeriods.LENGTHS.max()

private suspend fun loadDose(graph: AppGraph): DoseModel {
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val from = now - DOSE_DAYS.toLong() * 24 * 3_600_000L
    // Часовые корзины: точное интегрирование AVG×COUNT при любой ширине.
    // Корзина относится к тем суткам, в которых НАЧАЛАСЬ; в поясах со
    // сдвигом в полчаса это переносит через полночь не больше часа записи.
    // Доза — по ОДНОМУ прибору: приборы пишут автономно, и после слива
    // памяти второго те же часы покрыты дважды.
    val serial = (graph.serviceStatus.connection.value as? ConnectionState.Connected)
        ?.info?.serialNumber
    val hourly = graph.measurementRepository.downsampledSamplesOf(
        from = from,
        to = now,
        bucketMillis = 3_600_000L,
        deviceSerial = serial,
    )
    val others = serial != null &&
        graph.measurementRepository.hasOtherDeviceSamples(from, now, serial)
    val today = LocalDate.now(zone)
    val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val firstDay = today.minusDays((DOSE_DAYS - 1).toLong())
    return DoseModel(
        dailyDose = DailyDose.perDay(hourly, now, zone, DOSE_DAYS),
        otherDevices = others,
        toMillis = now,
        todayElapsedSeconds = ((now - startOfDay) / 1000L).coerceAtLeast(0L),
        firstDayStartMillis = firstDay.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
