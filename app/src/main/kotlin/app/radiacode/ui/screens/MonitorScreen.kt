package app.radiacode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.device.ConnectionState
import app.radiacode.device.DoseUnits
import app.radiacode.service.BatteryOptimization
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppIcons
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.PlacePickerDialog
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.StatusDot
import app.radiacode.ui.components.TrendChart
import app.radiacode.ui.components.TrendChartSpec
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.TimeAxis
import app.radiacode.ui.logic.TrendFit
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.learningWording
import app.radiacode.ui.logic.statusDetail
import app.radiacode.ui.logic.statusHeadline
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHART_COLUMNS = 48
private const val CHART_WINDOW_MILLIS = 60L * 60_000L
private const val CHART_BUCKET_MILLIS = CHART_WINDOW_MILLIS / CHART_COLUMNS
private const val CHART_REFRESH_MILLIS = 15_000L

/** Hour-chart snapshot loaded off the 1 Hz path; values in µSv/h. */
@Immutable
private data class HourChart(
    val columns: List<Float?>,
    val stats: ChartMapping.Stats?,
    /** Raw 1 Hz samples inside the window (the honest n of the statgrid). */
    val sampleCount: Int,
    val fromMillis: Long,
    val toMillis: Long,
    val doseTodayMicroSv: Double,
)

/**
 * Монитор (Главная): the 2-3 second answer — current dose rate with its
 * uncertainty, count rate, hour trend, dose today, whether the level differs
 * from the usual level of this place. Baseline state and the live deviation
 * picture come from the measurement service (single source); this screen
 * only renders [MonitorStatus].
 */
@Composable
fun MonitorScreen(graph: AppGraph, onOpenSettings: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val deviation by graph.serviceStatus.deviation.collectAsState()
    val thresholds by graph.settings.alarmThresholds
        .collectAsState(initial = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f))
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val places by graph.placeRepository.places().collectAsState(initial = emptyList())
    val activePlace by graph.placeRepository.activePlace().collectAsState(initial = null)

    // 1 s wall-clock ticker drives the staleness indicator and held durations.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val freshness = Freshness.of(sample?.timestamp, nowMillis)

    var hourChart by remember { mutableStateOf<HourChart?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            hourChart = loadHourChart(graph)
            delay(CHART_REFRESH_MILLIS)
        }
    }

    var showPlacePicker by remember { mutableStateOf(false) }

    val doseMicroSvH = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
    val status = MonitorStatus.of(
        doseRateMicroSvH = doseMicroSvH,
        baselineState = baselineState,
        deviation = deviation,
        thresholds = thresholds,
        nowMillis = nowMillis,
    )

    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Chip(
                text = "${activePlace?.name ?: "Место?"} ▾",
                color = colors.ink,
                onClick = { showPlacePicker = true },
            )
            Spacer(Modifier.weight(1f))
            ConnectionChip(connection, serviceRunning)
            FreshnessChip(freshness)
            Icon(
                imageVector = AppIcons.Gear,
                contentDescription = "Настройки",
                tint = colors.ink2,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenSettings,
                    ),
            )
        }

        HeroCard(
            doseMicroSvH = doseMicroSvH,
            errPercent = sample?.doseRateErr,
            cps = sample?.countRate,
            trendMicroSvHPerHour = hourChart?.let {
                TrendFit.slopePerHour(it.columns, CHART_BUCKET_MILLIS)
            },
            doseTodayMicroSv = hourChart?.doseTodayMicroSv,
            status = status,
            baselineState = baselineState,
            unit = unit,
            stale = freshness !is Freshness.Fresh,
        )

        HourChartCard(
            chart = hourChart,
            baseline = (baselineState as? BaselineState.Active)?.baseline,
            thresholds = thresholds,
            unit = unit,
            alert = status is MonitorStatus.Alert,
        )

        Text(
            text = "CPS — счёт событий детектора, не мера опасности",
            style = LocalAppTypography.current.footnote,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = Dimens.space1),
        )

        BatteryBanner()
    }

    if (showPlacePicker) {
        PlacePickerDialog(
            places = places,
            activePlaceId = activePlace?.id,
            onSelect = { id -> scope.launch { graph.placeRepository.setActive(id) } },
            onCreate = { name ->
                scope.launch {
                    val id = graph.placeRepository.add(name)
                    graph.placeRepository.setActive(id)
                }
            },
            onDismiss = { showPlacePicker = false },
        )
    }
}

@Composable
private fun ConnectionChip(connection: ConnectionState, serviceRunning: Boolean) {
    val colors = LocalAppColors.current
    val (dot, text) = when {
        connection is ConnectionState.Connected -> colors.ok to "RC-110 · 1 Гц"
        connection is ConnectionState.Connecting -> colors.warn to "подключение"
        connection is ConnectionState.Reconnecting -> colors.warn to "переподкл."
        !serviceRunning -> colors.muted to "служба выкл."
        else -> colors.muted to "нет связи"
    }
    Chip(text = text, dot = dot)
}

@Composable
private fun FreshnessChip(freshness: Freshness) {
    val colors = LocalAppColors.current
    when (freshness) {
        Freshness.NoData -> Chip(text = "нет данных", color = colors.muted)
        is Freshness.Fresh -> Chip(text = "${freshness.ageSeconds} с")
        is Freshness.Stale ->
            Chip(text = "прервано ${freshness.ageSeconds} с", color = colors.warn)
    }
}

@Composable
private fun HeroCard(
    doseMicroSvH: Float?,
    errPercent: Float?,
    cps: Float?,
    trendMicroSvHPerHour: Float?,
    doseTodayMicroSv: Double?,
    status: MonitorStatus,
    baselineState: BaselineState?,
    unit: DoseUnitSetting,
    stale: Boolean,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Column(Modifier.weight(1.35f)) {
                    Text(
                        text = "Мощность дозы".uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                    )
                    Text(
                        text = doseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                        style = type.valueHero,
                        color = if (doseMicroSvH == null || stale) colors.muted else colors.ink,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = listOfNotNull(
                            DoseFormat.rateUnitLabel(unit),
                            Uncertainty.errPercentLabel(errPercent),
                        ).joinToString(" · "),
                        style = type.footnote,
                        color = colors.ink2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(Dimens.border)
                            .fillMaxHeight()
                            .background(colors.line),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Dimens.space3),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KvRow("Счёт", cps?.let { Uncertainty.cpsWithSigma(it) } ?: "—")
                        KvRow(
                            label = "Тренд/ч",
                            value = trendMicroSvHPerHour?.let { TrendFit.label(it, unit) } ?: "—",
                            valueColor = trendWarnColor(trendMicroSvHPerHour, status),
                        )
                        KvRow(
                            label = "Сегодня",
                            value = doseTodayMicroSv?.let { DoseFormat.doseWithUnit(it, unit) }
                                ?: "—",
                        )
                    }
                }
            }

            // Red is reserved for the confirmed alarm; amber for «выше
            // обычного»; normal states never shout (design rule).
            val statusColor = when {
                stale || status == MonitorStatus.Unknown -> colors.muted
                status is MonitorStatus.Alert -> colors.crit
                status is MonitorStatus.AboveUsual -> colors.warn
                status is MonitorStatus.Fixed && status.above -> colors.warn
                else -> colors.ok
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StatusDot(statusColor)
                Text(
                    text = statusHeadline(status),
                    style = type.label,
                    color = statusColor,
                )
                statusDetail(status, unit)?.let { detail ->
                    Text(
                        text = detail,
                        style = type.footnote,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            (baselineState as? BaselineState.Learning)?.let { learning ->
                Text(
                    text = learningWording(learning),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun trendWarnColor(trend: Float?, status: MonitorStatus): Color? {
    if (trend == null || trend <= TrendFit.FLAT_EPSILON_MICRO_SV) return null
    return when (status) {
        is MonitorStatus.AboveUsual, is MonitorStatus.Alert -> LocalAppColors.current.warn
        else -> null
    }
}

@Composable
private fun KvRow(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.bodySmall, color = colors.ink2)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = type.value,
            color = valueColor ?: colors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun HourChartCard(
    chart: HourChart?,
    baseline: Baseline?,
    thresholds: AlarmThresholds,
    unit: DoseUnitSetting,
    alert: Boolean,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Мощность дозы · час".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                if (baseline != null) {
                    Text(
                        text = "полоса — обычный диапазон",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }

            val stats = chart?.stats
            if (chart == null || stats == null) {
                Text(
                    text = "накапливаем измерения…",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                val alarmLevel = thresholds.l1MicroSvH
                val yMax = ChartMapping.yMax(
                    maxOf(stats.max, baseline?.doseHighMicroSvH ?: 0f),
                    alarmLevel,
                )
                TrendChart(
                    spec = TrendChartSpec(
                        columns = chart.columns,
                        yMax = yMax,
                        alarmLevel = alarmLevel,
                        alarmLabel = "L1 ${DoseFormat.rate(alarmLevel, unit)}",
                        band = baseline?.let { it.doseLowMicroSvH..it.doseHighMicroSvH },
                        yTicks = ChartMapping.yTicks(yMax).map { it to DoseFormat.rate(it, unit) },
                        xLabels = TimeAxis.labels(chart.fromMillis, chart.toMillis),
                        endpointAlert = alert,
                    ),
                )
                StatGrid(
                    cells = listOf(
                        StatCell(DoseFormat.rate(stats.min, unit), "мин"),
                        StatCell(DoseFormat.rate(stats.median, unit), "медиана"),
                        StatCell(DoseFormat.rate(stats.max, unit), "макс"),
                        StatCell(DoseFormat.rate(stats.sigma, unit), "σ"),
                        StatCell(HistoryFormat.count(chart.sampleCount), "n"),
                    ),
                )
            }
        }
    }
}

@Composable
private fun BatteryBanner() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(BatteryOptimization.isExempt(context)) }
    if (exempt) return
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = "Android может остановить измерение в фоне. Чтобы запись " +
                    "шла непрерывно, исключите приложение из оптимизации батареи.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            AppButton(
                text = "Разрешить работу в фоне",
                onClick = {
                    runCatching {
                        context.startActivity(BatteryOptimization.buildRequestIntent(context))
                    }
                    exempt = BatteryOptimization.isExempt(context)
                },
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private suspend fun loadHourChart(graph: AppGraph): HourChart {
    val now = System.currentTimeMillis()
    val from = ChartMapping.alignedFrom(now, CHART_WINDOW_MILLIS, CHART_BUCKET_MILLIS)
    val buckets = graph.measurementRepository.downsampledSamples(
        from = from,
        to = now,
        bucketMillis = CHART_BUCKET_MILLIS,
    )
    val columns = ChartMapping.toColumns(
        buckets = buckets,
        alignedFromMillis = from,
        bucketMillis = CHART_BUCKET_MILLIS,
        columnCount = CHART_COLUMNS,
    ) { DoseUnits.rawToMicroSievertPerHour(it.avgDoseRate) }

    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()
    val todayBuckets = graph.measurementRepository.downsampledSamples(
        from = startOfDay,
        to = now,
        bucketMillis = 60_000L,
    )

    return HourChart(
        columns = columns,
        stats = ChartMapping.stats(columns),
        sampleCount = buckets.sumOf { it.sampleCount },
        fromMillis = from,
        toMillis = now,
        doseTodayMicroSv = ChartMapping.integrateDoseMicroSv(todayBuckets),
    )
}
