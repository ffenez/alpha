package app.alpha.ui.screens

import android.content.res.Configuration
import app.alpha.ui.logic.ChartInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.alpha.AppGraph
import app.alpha.analysis.quantiles.KllSketch
import app.alpha.analysis.quantiles.QuantileComparison
import app.alpha.analysis.quantiles.QuantileDiagnostics
import app.alpha.baseline.AlarmSensitivity
import app.alpha.baseline.Baseline
import app.alpha.baseline.BaselineState
import app.alpha.baseline.alarmThresholds
import app.alpha.data.DoseUnitSetting
import app.alpha.data.PreAggregateRepository
import app.alpha.device.DoseUnits
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartSheet
import app.alpha.ui.components.Chip
import app.alpha.ui.components.DistributionStrip
import app.alpha.ui.components.DoseChart
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.logic.ChartBucket
import app.alpha.ui.logic.ChartWindow
import app.alpha.ui.logic.ChartWindows
import app.alpha.analysis.Hardness
import app.alpha.ui.logic.ChartMetric
import app.alpha.ui.logic.ChartMetrics
import app.alpha.ui.logic.ChartRange
import app.alpha.ui.logic.ChartRanges
import app.alpha.ui.logic.CursorReadout
import app.alpha.ui.logic.coverageWording
import app.alpha.ui.logic.ChartSeriesModel
import app.alpha.ui.logic.DoseExtremes
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.DoseHistograms
import app.alpha.ui.logic.DoseReference
import app.alpha.ui.logic.ChartSnapshot
import app.alpha.ui.logic.Freshness
import app.alpha.ui.logic.freshnessChipLabel
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.QuantileMetadata
import app.alpha.ui.logic.QuantileMethod
import app.alpha.ui.logic.RatioDenominator
import app.alpha.ui.logic.markerWording
import app.alpha.ui.logic.referenceWording
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.logic.WindowStats
import app.alpha.ui.text.ChartAxisCatalogue
import app.alpha.ui.text.ChartTextCatalogue
import app.alpha.ui.text.ChartTextStrings
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.Strings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Числа полноэкранного графика: расширенная статистика окна, диагностика
 * метода квантилей и карточка курсора.
 *
 * Карточка курсора называет интервал, медиану, конверты, экстремумы со
 * временем и честное n.
 */

/**
 * «Расширенная статистика» (CHART SPEC §12, §13): MIN/Q25/Q75/MAX/MAD/SD, each
 * named in full and with its unit — a bare «σ» is forbidden, and SD/MAD belong
 * here rather than in the compact view.
 */
@Composable
internal fun ExpandedStats(
    stats: WindowStats?,
    unit: DoseUnitSetting,
    metric: ChartMetric = ChartMetric.DOSE,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = ChartTextCatalogue.of(strings.language)
    val axis = ChartAxisCatalogue.of(strings.language)
    StatGrid(
        cells = listOf(
            StatCell(stats?.let { DoseFormat.rate(it.min, unit) } ?: "—", t.min),
            StatCell(stats?.let { DoseFormat.rate(it.q25, unit) } ?: "—", "P25"),
            StatCell(stats?.let { DoseFormat.rate(it.q75, unit) } ?: "—", "P75"),
            StatCell(stats?.let { DoseFormat.rate(it.max, unit) } ?: "—", t.max),
        ),
    )
    StatGrid(
        cells = listOf(
            StatCell(stats?.let { DoseFormat.rate(it.mad, unit) } ?: "—", "MAD"),
            StatCell(stats?.let { DoseFormat.rate(it.sd, unit) } ?: "—", "SD"),
            StatCell(stats?.let { DoseFormat.rate(it.iqr, unit) } ?: "—", "IQR"),
        ),
    )
    Hint(
        text = t.spreadDefinitions,
        modifier = Modifier.padding(
            start = Dimens.space3,
            end = Dimens.space3,
            top = Dimens.space1,
        ),
    )
}

/**
 * Исследовательская диагностика квантилей (CHART SPEC §32, §34, §37G;
 * ADR 004). Живёт под расширенной статистикой.
 *
 * Показывает, каким путём получены квантили текущего окна, версию и параметр
 * точности скетча, ход построения предагрегации — и по явному запросу считает
 * то же окно вторым путём: читает сырые отсчёты часов, из которых собран
 * скетч, берёт точные порядковые статистики и сравнивает. Ошибка измеряется
 * по РАНГУ: разница в значении зависит от крутизны распределения.
 *
 * Точный путь читает окно целиком и сам не запускается.
 */
@Composable
internal fun QuantileDiagnosticPanel(
    graph: AppGraph,
    snapshot: ChartSnapshot?,
    unit: DoseUnitSetting,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    val scope = rememberCoroutineScope()
    val backfill by graph.preAggregator.progress.collectAsState()
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    val method = snapshot?.method ?: QuantileMethod.EXACT_RAW
    val sketch = snapshot?.windowSketch
    val range = snapshot?.windowSketchRange

    Column(
        Modifier.fillMaxWidth().padding(
            start = Dimens.space3,
            end = Dimens.space3,
            top = Dimens.space1,
        ),
    ) {
        Text(
            text = t.quantileMethodLine(
                QuantileMetadata.label(method, sketch?.k ?: KllSketch.DEFAULT_K),
            ),
            style = type.footnote,
            color = colors.muted,
        )
        if (backfill.running && backfill.hoursTotal > 0) {
            Text(
                text = t.preAggregationProgress(
                    percent = (backfill.fraction * 100).toInt(),
                    done = backfill.hoursDone,
                    total = backfill.hoursTotal,
                ),
                style = type.footnote,
                color = colors.muted,
            )
        }
        if (sketch != null && range != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Dimens.space1),
            ) {
                Chip(
                    text = if (running) t.computing else t.compareWithRaw,
                    color = colors.ink2,
                    onClick = {
                        if (!running) {
                            running = true
                            report = null
                            scope.launch {
                                val text = withContext(Dispatchers.IO) {
                                    compareQuantilePaths(graph, sketch, range, unit, t)
                                }
                                report = text
                                running = false
                            }
                        }
                    },
                )
            }
        }
        report?.let {
            Text(text = it, style = type.footnote, color = colors.ink2)
        }
    }
}

/**
 * Runs the same window both ways and renders the observed error. Returns the
 * reason instead of numbers when the exact path refuses (too many rows) or
 * when the two sides describe different data.
 */
private suspend fun compareQuantilePaths(
    graph: AppGraph,
    sketch: KllSketch,
    range: LongRange,
    unit: DoseUnitSetting,
    t: ChartTextStrings,
): String {
    val raw = graph.preAggregateRepository.rawDoseValues(range.first, range.last)
        ?: return t.exactPathRefused(PreAggregateRepository.MAX_DIAGNOSTIC_ROWS)
    if (raw.isEmpty()) return t.noRawSamplesInHours
    val factor = DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR
    for (i in raw.indices) raw[i] = raw[i] * factor
    val comparison = QuantileDiagnostics.compare(raw, sketch)
    return diagnosticReport(comparison, unit, t)
}

/** Text of the exact-vs-sketch comparison — plain numbers, no verdicts. */
internal fun diagnosticReport(
    comparison: QuantileComparison,
    unit: DoseUnitSetting,
    t: ChartTextStrings,
): String {
    val names = listOf("P10", "P25", t.median, "P75", "P90")
    val lines = StringBuilder()
    lines.append(t.diagnosticsHeader(HistoryFormat.count(comparison.sampleCount)))
    if (!comparison.countsAgree) {
        lines.append(t.sketchCountMismatch(comparison.sketchCount.toString()))
    }
    lines.append(" · k=${comparison.k}\n")
    for (i in comparison.probabilities.indices) {
        val name = names.getOrElse(i) { "p${comparison.probabilities[i]}" }
        lines.append(name)
        lines.append(' ')
        lines.append(DoseFormat.rate(comparison.exactValues[i], unit))
        lines.append(" → ")
        lines.append(DoseFormat.rate(comparison.approximateValues[i], unit))
        lines.append(t.rankErrorSuffix(percent(comparison.rankErrors[i])))
        lines.append("\n")
    }
    lines.append(t.maxRankError(percent(comparison.maxRankError)))
    return lines.toString()
}

internal fun percent(value: Double): String =
    String.format(java.util.Locale.ROOT, "%.2f %%", value * 100).replace('.', ',')

// --- cursor readout -------------------------------------------------------

/**
 * Crosshair readout. Reads the cursor [State] itself, so a drag recomposes
 * this card and nothing else. It sits on the side of the plot the finger is
 * not on, so the value is never hidden by the reading hand.
 */
@Composable
internal fun BoxScope.CursorCard(
    cursorFraction: State<Float?>,
    buckets: List<ChartBucket>,
    window: ChartWindow,
    unit: DoseUnitSetting,
    baseline: Baseline?,
    alarmLevel: Float?,
    /** Моменты кратковременных отклонений — для строки «3 события». */
    eventTimesMillis: List<Long> = emptyList(),
    /**
     * Записанный фон Поиска, имп/с; null — график открыт не из Поиска или фон
     * ещё не замерен.
     *
     * Число приходит из движка Поиска, а не считается здесь: второй фон,
     * посчитанный по-своему, спорил бы с тем, что говорит сам экран Поиска.
     */
    searchBackgroundCps: Float? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    val fraction = cursorFraction.value ?: return
    val time = ChartWindows.timeAt(window, fraction)
    val clock: (Long) -> String = { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CURSOR_TIME)
    }
    val bucket = CursorReadout.nearestBucket(buckets, time)
    if (bucket == null) {
        // Курсор стоит там, где измерений не было: место обязано назвать
        // себя, иначе плоскость «нет данных» неотличима от низкого уровня.
        Card(
            modifier = Modifier
                .align(if (fraction < 0.5f) Alignment.TopEnd else Alignment.TopStart)
                .padding(Dimens.space2),
            contentPadding = Dimens.space2,
        ) {
            Column {
                Text(text = clock(time), style = type.footnote, color = colors.ink2)
                Text(text = t.cursorNoData, style = type.value, color = colors.muted)
                Text(
                    text = t.cursorNoDataDetail,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
        return
    }
    // Курсор сначала отвечает на вопрос «что здесь было»: момент и значение.
    // Вся остальная статистика колонки — по нажатию: вываливать десять строк
    // на каждое долгое касание значит заслонять сам график ради чисел, за
    // которыми приходят изредка (V2 §14).
    var expanded by rememberSaveable { mutableStateOf(false) }
    val above = alarmLevel != null && bucket.median >= alarmLevel
    val extreme = DoseExtremes.classify(bucket, alarmLevel, baseline?.doseHighMicroSvH)
    Card(
        modifier = Modifier
            .align(if (fraction < 0.5f) Alignment.TopEnd else Alignment.TopStart)
            .padding(Dimens.space2)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { expanded = !expanded },
            ),
        contentPadding = Dimens.space2,
    ) {
        // CHART SPEC §16: interval, median, both envelopes, the exact extrema
        // with their times, n — then the profile baseline block.
        Column {
            // Колонка шириной в одно измерение подписывается моментом, а не
            // интервалом: интервал читался бы как усреднение. Усреднённая
            // колонка называет и интервал, и то, что число — медиана (V2 §14).
            val aggregated = bucket.sampleCount > 1
            Text(
                text = if (aggregated) {
                    CursorReadout.binRangeLabel(bucket, clock)
                } else {
                    clock(bucket.midMillis)
                },
                style = type.footnote,
                color = colors.ink2,
            )
            Text(
                text = if (aggregated) {
                    t.median + " " + DoseFormat.rate(bucket.median, unit)
                } else {
                    DoseFormat.rate(bucket.median, unit)
                },
                style = type.value,
                color = if (above) colors.crit else colors.ink,
            )
            if (!expanded) {
                Text(
                    text = t.cursorMoreDetails,
                    style = type.footnote,
                    color = colors.muted,
                )
                return@Column
            }
            CursorRow(t.median, DoseFormat.rate(bucket.median, unit))
            CursorRow(
                "P25–P75",
                DoseFormat.range(bucket.q25, bucket.q75, unit),
            )
            CursorRow(
                "P10–P90",
                DoseFormat.range(bucket.q10, bucket.q90, unit),
            )
            CursorRow(
                t.min,
                DoseFormat.rate(bucket.min, unit) + " " +
                    CursorReadout.extremeTimeLabel(
                        bucket.minAtMillis,
                        bucket.extremeWindowMillis,
                        format = clock,
                    ),
            )
            CursorRow(
                t.max,
                DoseFormat.rate(bucket.max, unit) + " " +
                    CursorReadout.extremeTimeLabel(
                        bucket.maxAtMillis,
                        bucket.extremeWindowMillis,
                        format = clock,
                    ),
            )
            CursorRow(t.samplesLabel, HistoryFormat.count(bucket.sampleCount))
            // Сколько кратковременных отклонений пришлось на эту колонку.
            // Из Поиска график открывают с вопросом «во сколько раз здесь
            // больше, чем там, где мерили фон», поэтому отношение называет
            // знаменатель: «×2,4 к фону поиска 25,5».
            if (searchBackgroundCps != null && searchBackgroundCps > 0f) {
                val ratio = bucket.median / searchBackgroundCps
                Text(
                    text = if (ratio >= 1f) {
                        t.cursorSearchBackground(
                            Uncertainty.num1(ratio),
                            Uncertainty.num1(searchBackgroundCps),
                        )
                    } else {
                        t.cursorSearchBackgroundBelow
                    },
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
            val eventsHere = eventTimesMillis.count {
                it >= bucket.startMillis && it < bucket.endMillis
            }
            if (eventsHere > 0) {
                Text(
                    text = t.cursorEvents(eventsHere),
                    style = type.footnote,
                    color = colors.warn,
                )
                Text(
                    text = t.cursorEventsNote,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            if (extreme != null) {
                // Маркер сообщает ФАКТ СРАВНЕНИЯ и сразу показывает оба числа,
                // на которых он стоит: «выше P90 профиля» без самого P90 —
                // это утверждение, которое нечем проверить.
                val reference = when (extreme) {
                    DoseReference.ALARM_L1 -> alarmLevel
                    DoseReference.BASELINE_P90 -> baseline?.doseHighMicroSvH
                }
                Text(
                    text = "▲ " + markerWording(extreme),
                    style = type.footnote,
                    color = if (extreme == DoseReference.ALARM_L1) colors.crit else colors.warn,
                )
                if (reference != null) {
                    Text(
                        text = if (extreme == DoseReference.ALARM_L1) {
                            t.cursorMaxAgainstAlarm(
                                DoseFormat.rate(bucket.max, unit),
                                DoseFormat.rate(reference, unit),
                            )
                        } else {
                            t.cursorMaxAgainstProfileP90(
                                DoseFormat.rate(bucket.max, unit),
                                DoseFormat.rate(reference, unit),
                            )
                        },
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
            if (!bucket.quantilesExact) {
                Text(
                    text = when (bucket.method) {
                        QuantileMethod.KLL_SKETCH -> t.bucketQuantilesSketch
                        else -> t.bucketQuantilesCoarse
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            if (baseline != null) {
                AppDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    text = t.historicalProfile,
                    style = type.footnote,
                    color = colors.ink2,
                )
                CursorRow(t.median, DoseFormat.rate(baseline.doseMedianMicroSvH, unit))
                CursorRow(
                    "P10–P90",
                    DoseFormat.range(
                        baseline.doseLowMicroSvH,
                        baseline.doseHighMicroSvH,
                        unit,
                    ),
                )
                CursorReadout.ratioTo(bucket.median, baseline.doseHighMicroSvH)?.let { ratio ->
                    Text(
                        text = CursorReadout.ratioLabel(ratio, RatioDenominator.BASELINE_P90),
                        style = type.footnote,
                        color = colors.ink2,
                    )
                    Hint(
                        text = CursorReadout.ratioExplanation(RatioDenominator.BASELINE_P90),
                    )
                }
            }
        }
    }
}

/** One «label   value» line of the cursor card. */
@Composable
internal fun CursorRow(label: String, value: String) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = type.footnote, color = colors.ink2)
        Spacer(Modifier.width(Dimens.space2))
        Spacer(Modifier.weight(1f))
        // Значение — данные: моноширинный, чтобы столбец чисел выравнивался.
        Text(text = value, style = type.footnoteMono, color = colors.ink)
    }
}
