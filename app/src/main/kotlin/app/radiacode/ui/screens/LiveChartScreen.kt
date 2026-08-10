package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.radiacode.AppGraph
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.TrendChart
import app.radiacode.ui.components.TrendChartSpec
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.TimeAxis
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Fullscreen chart resolution; higher than the Монитор card (48). */
private const val COLUMNS = 120

/** Gesture streams update the window per frame; loads coalesce behind this. */
private const val LOAD_DEBOUNCE_MILLIS = 60L

/** Loaded snapshot of the visible window; values in µSv/h. */
@Immutable
private data class WindowChart(
    val window: ChartWindow,
    val columns: List<Float?>,
    val stats: ChartMapping.Stats?,
    /** Honest n: raw 1 Hz samples inside the visible window. */
    val sampleCount: Int,
)

/**
 * Полноэкранный живой график мощности дозы: открывается тапом по карточке
 * графика на Мониторе. Live-режим дописывает данные с частотой потока и
 * держит правый край на «сейчас»; жест (pan/pinch по оси времени) отпускает
 * слежение, чип «⌖ сейчас» возвращает его. Полоса обычного диапазона, линия
 * L1, сырые точки и сглаженная линия — как на Мониторе; statgrid внизу
 * пересчитывается по видимому окну. Свободно вращается: график заполняет
 * доступное место в обеих ориентациях.
 */
@Composable
fun LiveChartScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val deviation by graph.serviceStatus.deviation.collectAsState()
    val thresholds by graph.settings.alarmThresholds
        .collectAsState(initial = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f))
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    var periodIndex by rememberSaveable { mutableIntStateOf(1) } // 1ч
    var follow by rememberSaveable { mutableStateOf(true) }
    var window by remember {
        mutableStateOf(
            ChartWindows.latest(ChartWindows.PERIODS[1].second, System.currentTimeMillis()),
        )
    }
    var chart by remember { mutableStateOf<WindowChart?>(null) }

    // Live-follow ticker: advance the right edge to now at the refresh cadence.
    LaunchedEffect(follow, periodIndex) {
        while (follow) {
            window = ChartWindows.follow(window, System.currentTimeMillis())
            delay(ChartWindows.refreshMillis(ChartWindows.bucketMillis(window.spanMillis, COLUMNS)))
        }
    }

    // Loader: every window change reloads; collectLatest cancels stale loads,
    // the small debounce coalesces per-frame gesture updates.
    LaunchedEffect(graph) {
        snapshotFlow { window }.collectLatest { w ->
            delay(LOAD_DEBOUNCE_MILLIS)
            chart = loadWindowChart(graph, w)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            AppButton(text = "← Назад", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "Мощность дозы", color = colors.ink)
        }

        Segmented(
            options = ChartWindows.PERIODS.map { it.first },
            selectedIndex = periodIndex,
            onSelect = { index ->
                periodIndex = index
                window = ChartWindows.latest(
                    ChartWindows.PERIODS[index].second,
                    System.currentTimeMillis(),
                )
                follow = true
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = Dimens.space2,
        ) {
            val loaded = chart
            if (loaded == null || loaded.stats == null) {
                Text(
                    text = if (loaded == null) "читаем журнал…" else "в этом окне нет измерений",
                    style = type.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (loaded != null && loaded.stats != null) {
                val baseline = (baselineState as? BaselineState.Active)?.baseline
                val alarmLevel = thresholds.l1MicroSvH
                val yMax = ChartMapping.yMax(
                    maxOf(loaded.stats.max, baseline?.doseHighMicroSvH ?: 0f),
                    alarmLevel,
                )
                TrendChart(
                    spec = TrendChartSpec(
                        columns = loaded.columns,
                        yMax = yMax,
                        alarmLevel = alarmLevel,
                        alarmLabel = "L1 ${DoseFormat.rate(alarmLevel, unit)}",
                        band = baseline?.let { it.doseLowMicroSvH..it.doseHighMicroSvH },
                        yTicks = ChartMapping.yTicks(yMax)
                            .map { it to DoseFormat.rate(it, unit) },
                        xLabels = TimeAxis.labels(
                            loaded.window.fromMillis,
                            loaded.window.toMillis,
                            count = 5,
                        ),
                        endpointAlert = follow && deviation.alertSince != null,
                    ),
                    height = null,
                    onTransform = { panFraction, zoomFactor, focusFraction ->
                        val now = System.currentTimeMillis()
                        var w = window
                        if (zoomFactor != 1f) {
                            w = ChartWindows.zoom(w, zoomFactor, focusFraction, now)
                        }
                        if (panFraction != 0f) {
                            // Dragging right pulls earlier data into view.
                            w = ChartWindows.pan(w, -panFraction, now)
                        }
                        window = w
                        // Back at the right edge = live again; otherwise the
                        // «⌖ сейчас» chip re-enables following.
                        follow = ChartWindows.isAtLiveEdge(
                            w,
                            now,
                            ChartWindows.bucketMillis(w.spanMillis, COLUMNS),
                        )
                    },
                )
            }

            if (follow) {
                Chip(
                    text = "живой · сейчас",
                    dot = colors.ok,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.space1),
                )
            } else {
                Chip(
                    text = "⌖ сейчас",
                    color = colors.dataText,
                    onClick = {
                        window = ChartWindows.follow(window, System.currentTimeMillis())
                        follow = true
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.space1),
                )
            }
        }

        // Statgrid of the visible window (recomputed with every window change).
        val stats = chart?.stats
        StatGrid(
            cells = listOf(
                StatCell(stats?.let { DoseFormat.rate(it.min, unit) } ?: "—", "мин"),
                StatCell(stats?.let { DoseFormat.rate(it.median, unit) } ?: "—", "медиана"),
                StatCell(stats?.let { DoseFormat.rate(it.max, unit) } ?: "—", "макс"),
                StatCell(stats?.let { DoseFormat.rate(it.sigma, unit) } ?: "—", "σ"),
                StatCell(chart?.let { HistoryFormat.count(it.sampleCount) } ?: "—", "n"),
            ),
        )
        Text(
            text = "${DoseFormat.rateUnitLabel(unit)} · окно " +
                HistoryFormat.duration(window.spanMillis / 1000),
            style = type.footnote,
            color = colors.muted,
        )
    }
}

private suspend fun loadWindowChart(graph: AppGraph, window: ChartWindow): WindowChart {
    val bucketMillis = ChartWindows.bucketMillis(window.spanMillis, COLUMNS)
    val alignedFrom = ChartMapping.alignedFrom(window.toMillis, window.spanMillis, bucketMillis)
    val buckets = graph.measurementRepository.downsampledSamples(
        from = alignedFrom,
        to = window.toMillis,
        bucketMillis = bucketMillis,
    )
    val columns = ChartMapping.toColumns(
        buckets = buckets,
        alignedFromMillis = alignedFrom,
        bucketMillis = bucketMillis,
        columnCount = COLUMNS,
    ) { DoseUnits.rawToMicroSievertPerHour(it.avgDoseRate) }
    return WindowChart(
        window = window,
        columns = columns,
        stats = ChartMapping.stats(columns),
        sampleCount = buckets.sumOf { it.sampleCount },
    )
}
