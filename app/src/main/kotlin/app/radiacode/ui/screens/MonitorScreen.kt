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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
import app.radiacode.ui.components.PixelBox
import app.radiacode.ui.components.PixelButton
import app.radiacode.ui.components.PixelChart
import app.radiacode.ui.components.PixelChartSpec
import app.radiacode.ui.components.PixelTag
import app.radiacode.ui.components.PlacePickerDialog
import app.radiacode.ui.components.StatusLine
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.baselineCollectedWording
import app.radiacode.ui.logic.cpsWording
import app.radiacode.ui.logic.freshnessLabel
import app.radiacode.ui.logic.learningWording
import app.radiacode.ui.logic.statusDetail
import app.radiacode.ui.logic.statusHeadline
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens
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
    val doseTodayMicroSv: Double,
)

/**
 * Монитор (Главная): the 2-3 second answer — current dose rate, whether it
 * differs from the usual level of this place, dose today, last hour trend.
 * Baseline state and the live deviation picture come from the measurement
 * service (single source); this screen only renders [MonitorStatus].
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelDimens.space4),
        verticalArrangement = Arrangement.spacedBy(PixelDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelTag(
                text = (activePlace?.name ?: "МЕСТО?").uppercase(),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showPlacePicker = true },
            )
            Spacer(Modifier.weight(1f))
            FreshnessIndicator(freshness)
        }

        MainReading(
            doseMicroSvH = doseMicroSvH,
            cpsLine = cpsWording(sample?.countRate, baselineState),
            status = status,
            baselineState = baselineState,
            unit = unit,
            stale = freshness !is Freshness.Fresh,
            doseTodayMicroSv = hourChart?.doseTodayMicroSv,
        )

        HourChartPanel(
            chart = hourChart,
            baseline = (baselineState as? BaselineState.Active)?.baseline,
            thresholds = thresholds,
            unit = unit,
        )

        BatteryBanner()

        ConnectionFooter(connection = connection, serviceRunning = serviceRunning)
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
private fun FreshnessIndicator(freshness: Freshness) {
    val colors = LocalPixelColors.current
    val (text, color) = when (freshness) {
        Freshness.NoData -> freshnessLabel(freshness) to colors.textSecondary
        is Freshness.Fresh -> freshnessLabel(freshness) to colors.textMuted
        is Freshness.Stale -> "! " + freshnessLabel(freshness) to colors.aboveUsual
    }
    Text(
        text = text,
        style = LocalPixelTypography.current.labelSmall,
        color = color,
    )
}

@Composable
private fun MainReading(
    doseMicroSvH: Float?,
    cpsLine: String,
    status: MonitorStatus,
    baselineState: BaselineState?,
    unit: DoseUnitSetting,
    stale: Boolean,
    doseTodayMicroSv: Double?,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PixelDimens.space2),
        ) {
            // Glow only here: the main number and its status (design rule).
            val glow = if (colors.isDark && !stale) {
                Shadow(color = colors.accent.copy(alpha = 0.55f), blurRadius = 24f)
            } else {
                null
            }
            val valueColor = when {
                doseMicroSvH == null || stale -> colors.textMuted
                else -> colors.accent
            }
            Text(
                text = doseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—.—",
                style = glow?.let { type.valueHuge.copy(shadow = it) } ?: type.valueHuge,
                color = valueColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = DoseFormat.rateUnitLabel(unit),
                style = type.label,
                color = colors.textSecondary,
            )

            // Red is reserved for the confirmed alarm state; amber for «выше
            // обычного»; normal states never shout (design rule).
            val statusColor = when {
                stale || status == MonitorStatus.Unknown -> colors.textMuted
                status is MonitorStatus.Alert -> colors.chartAlarm
                status is MonitorStatus.AboveUsual -> colors.aboveUsual
                status is MonitorStatus.Fixed && status.above -> colors.aboveUsual
                else -> colors.accent
            }
            Text(
                text = statusHeadline(status),
                style = glow?.let { type.heading.copy(shadow = it) } ?: type.heading,
                color = statusColor,
                textAlign = TextAlign.Center,
            )
            statusDetail(status, unit)?.let { detail ->
                Text(
                    text = detail,
                    style = type.labelSmall,
                    color = if (statusColor == colors.accent) colors.textSecondary else statusColor,
                    textAlign = TextAlign.Center,
                )
            }
            // Baseline comparison is a distinct data category (SPEC).
            if (status is MonitorStatus.Usual || status is MonitorStatus.AboveUsual) {
                PixelTag(text = "сравнение с baseline")
            }
            (baselineState as? BaselineState.Learning)?.let { learning ->
                StatusLine(
                    text = learningWording(learning),
                    cursor = true,
                    color = colors.textMuted,
                )
            }

            CpsPill(cpsLine)

            if (doseTodayMicroSv != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
                ) {
                    Text(
                        text = "доза сегодня: ${DoseFormat.doseWithUnit(doseTodayMicroSv, unit)}",
                        style = type.value,
                        color = colors.textSecondary,
                    )
                    PixelTag(text = "расчёт")
                }
            }
        }
    }
}

@Composable
private fun CpsPill(cpsLine: String) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    var showHint by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = cpsLine,
            style = type.value,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showHint = !showHint }
                .padding(PixelDimens.space1),
        )
        if (showHint) {
            Text(
                text = "CPS — число событий, зарегистрированных детектором " +
                    "за секунду. Это скорость счёта, а не мера опасности.",
                style = type.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = PixelDimens.space2),
            )
        }
    }
}

@Composable
private fun HourChartPanel(
    chart: HourChart?,
    baseline: Baseline?,
    thresholds: AlarmThresholds,
    unit: DoseUnitSetting,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text("МОЩНОСТЬ ДОЗЫ · ЧАС", style = type.label, color = colors.text)

            val stats = chart?.stats
            if (chart == null || stats == null) {
                StatusLine(
                    text = "накапливаем измерения",
                    cursor = true,
                    color = colors.textMuted,
                )
            } else {
                val alarmLevel = thresholds.l1MicroSvH
                val yMax = ChartMapping.yMax(
                    maxOf(stats.max, baseline?.doseHighMicroSvH ?: 0f),
                    alarmLevel,
                )
                PixelChart(
                    spec = PixelChartSpec(
                        columns = chart.columns,
                        yMax = yMax,
                        alarmLevel = alarmLevel,
                        band = baseline?.let { it.doseLowMicroSvH..it.doseHighMicroSvH },
                        columnWidthPx = 2,
                        gapPx = 1,
                    ),
                    yMaxLabel = DoseFormat.rateWithUnit(yMax, unit),
                    xStartLabel = "-60 мин",
                    xEndLabel = "сейчас",
                )
                Text(
                    text = "мин ${DoseFormat.rate(stats.min, unit)} · " +
                        "ср ${DoseFormat.rate(stats.avg, unit)} · " +
                        "макс ${DoseFormat.rate(stats.max, unit)} · " +
                        "σ ${DoseFormat.rate(stats.sigma, unit)}",
                    style = type.labelSmall,
                    color = colors.textSecondary,
                )
                val legend = buildString {
                    append("пунктир — тревога ")
                    append(DoseFormat.rateWithUnit(thresholds.l1MicroSvH, unit))
                    if (baseline != null) {
                        append(". Штриховка — обычный диапазон этого места, ")
                        append(baselineCollectedWording(baseline))
                    } else {
                        append(". Привычный диапазон появится, когда накопится ")
                        append("история наблюдений")
                    }
                    append(".")
                }
                Text(
                    text = legend,
                    style = type.bodySmall,
                    color = colors.textMuted,
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
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text(
                text = "Android может остановить измерение в фоне. Чтобы запись " +
                    "шла непрерывно, исключите приложение из оптимизации батареи.",
                style = type.bodySmall,
                color = colors.textSecondary,
            )
            PixelButton(
                text = "РАЗРЕШИТЬ РАБОТУ В ФОНЕ",
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

@Composable
private fun ConnectionFooter(connection: ConnectionState, serviceRunning: Boolean) {
    val colors = LocalPixelColors.current
    when {
        connection is ConnectionState.Connected -> StatusLine(
            text = "подключено · ${connection.info.serialNumber}",
            color = colors.textSecondary,
        )
        connection is ConnectionState.Connecting -> StatusLine(
            text = "подключение",
            cursor = true,
            color = colors.textSecondary,
        )
        connection is ConnectionState.Reconnecting -> StatusLine(
            text = "переподключение",
            cursor = true,
            color = colors.aboveUsual,
        )
        !serviceRunning -> StatusLine(
            text = "служба измерения не запущена",
            color = colors.textMuted,
        )
        else -> StatusLine(text = "нет соединения", color = colors.textMuted)
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
        doseTodayMicroSv = ChartMapping.integrateDoseMicroSv(todayBuckets),
    )
}
