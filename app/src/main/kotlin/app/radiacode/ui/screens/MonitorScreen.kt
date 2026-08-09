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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.radiacode.AppGraph
import app.radiacode.device.ConnectionState
import app.radiacode.device.DoseUnits
import app.radiacode.service.BatteryOptimization
import app.radiacode.ui.components.PixelBox
import app.radiacode.ui.components.PixelButton
import app.radiacode.ui.components.PixelChart
import app.radiacode.ui.components.PixelChartSpec
import app.radiacode.ui.components.PixelTag
import app.radiacode.ui.components.StatusLine
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.formatMicroSv
import app.radiacode.ui.logic.freshnessLabel
import app.radiacode.ui.logic.statusWording
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

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
 * differs from usual, dose today, last hour trend. Status is currently a
 * fixed-threshold comparison; the baseline engine (roadmap #3) plugs into
 * [MonitorStatus] without touching this screen.
 */
@Composable
fun MonitorScreen(graph: AppGraph) {
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val thresholds by graph.settings.alarmThresholds.collectAsState(
        initial = app.radiacode.baseline.alarmThresholds(
            app.radiacode.baseline.AlarmSensitivity.NORMAL,
            0f,
            0f,
        ),
    )
    val threshold = thresholds.l1MicroSvH

    // 1 s wall-clock ticker drives only the staleness indicator.
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

    val doseMicroSvH = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
    val status = MonitorStatus.of(doseMicroSvH, threshold)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelDimens.space4),
        verticalArrangement = Arrangement.spacedBy(PixelDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Place tag is static until the per-place baseline engine exists.
            PixelTag(text = "ДОМ")
            Spacer(Modifier.weight(1f))
            FreshnessIndicator(freshness)
        }

        MainReading(
            doseMicroSvH = doseMicroSvH,
            cps = sample?.countRate,
            status = status,
            thresholdMicroSvH = threshold,
            stale = freshness !is Freshness.Fresh,
            doseTodayMicroSv = hourChart?.doseTodayMicroSv,
        )

        HourChartPanel(
            chart = hourChart,
            thresholdMicroSvH = threshold,
        )

        BatteryBanner()

        ConnectionFooter(connection = connection, serviceRunning = serviceRunning)
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
    cps: Float?,
    status: MonitorStatus,
    thresholdMicroSvH: Float,
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
                doseMicroSvH == null -> colors.textMuted
                stale -> colors.textMuted
                else -> colors.accent
            }
            Text(
                text = doseMicroSvH?.let { formatMicroSv(it) } ?: "—.—",
                style = glow?.let { type.valueHuge.copy(shadow = it) } ?: type.valueHuge,
                color = valueColor,
                textAlign = TextAlign.Center,
            )
            Text(text = "мкЗв/ч", style = type.label, color = colors.textSecondary)

            val statusColor = when {
                stale || status == MonitorStatus.UNKNOWN -> colors.textMuted
                status == MonitorStatus.ABOVE_THRESHOLD -> colors.aboveUsual
                else -> colors.accent
            }
            Text(
                text = statusWording(status, thresholdMicroSvH),
                style = glow?.let { type.heading.copy(shadow = it) } ?: type.heading,
                color = statusColor,
                textAlign = TextAlign.Center,
            )

            CpsPill(cps = cps)

            if (doseTodayMicroSv != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
                ) {
                    Text(
                        text = "доза сегодня: ${formatMicroSv(doseTodayMicroSv.toFloat())} мкЗв",
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
private fun CpsPill(cps: Float?) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    var showHint by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = cps?.let { "${it.toInt()} CPS" } ?: "— CPS",
            style = type.value,
            color = colors.textSecondary,
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
private fun HourChartPanel(chart: HourChart?, thresholdMicroSvH: Float) {
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
                val yMax = ChartMapping.yMax(stats.max, thresholdMicroSvH)
                PixelChart(
                    spec = PixelChartSpec(
                        columns = chart.columns,
                        yMax = yMax,
                        alarmLevel = thresholdMicroSvH,
                        // TODO(baseline engine): usual-range band per place.
                        band = null,
                        columnWidthPx = 2,
                        gapPx = 1,
                    ),
                    yMaxLabel = "${formatMicroSv(yMax)} мкЗв/ч",
                    xStartLabel = "-60 мин",
                    xEndLabel = "сейчас",
                )
                val s = stats
                Text(
                    text = "мин ${formatMicroSv(s.min)} · ср ${formatMicroSv(s.avg)} · " +
                        "макс ${formatMicroSv(s.max)} · σ ${formatMicroSv(s.sigma)}",
                    style = type.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = "пунктир — порог ${formatMicroSv(thresholdMicroSvH)} мкЗв/ч. " +
                        "Привычный диапазон появится, когда накопится история " +
                        "наблюдений.",
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
