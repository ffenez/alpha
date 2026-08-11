package app.radiacode.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.analysis.quantiles.QuantileComparison
import app.radiacode.analysis.quantiles.QuantileDiagnostics
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.PreAggregateRepository
import app.radiacode.data.db.MinuteRollup
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.DistributionStrip
import app.radiacode.ui.components.DoseChart
import app.radiacode.ui.components.DoseChartSpec
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.ChartBucket
import app.radiacode.ui.logic.ChartInteraction
import app.radiacode.ui.logic.ChartInteractions
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.ui.logic.CursorReadout
import app.radiacode.ui.logic.DoseAggregate
import app.radiacode.ui.logic.DoseChartModel
import app.radiacode.ui.logic.DoseEpisodes
import app.radiacode.ui.logic.DoseExtremes
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.DoseHourSlice
import app.radiacode.ui.logic.DoseReference
import app.radiacode.ui.logic.DoseWindowRollup
import app.radiacode.ui.logic.DoseHistogram
import app.radiacode.ui.logic.DoseHistograms
import app.radiacode.ui.logic.DoseScales
import app.radiacode.ui.logic.DoseSnapshot
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.QuantileMetadata
import app.radiacode.ui.logic.QuantileMethod
import app.radiacode.ui.logic.QuantilePaths
import app.radiacode.ui.logic.RatioDenominator
import app.radiacode.ui.logic.TimeAxis
import app.radiacode.ui.logic.referenceWording
import app.radiacode.ui.logic.referenceWordingShort
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.WindowStats
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestures move an already-loaded snapshot; the database is asked again only
 * after the window has been still for this long. `collectLatest` cancels the
 * pending read on every new window, so a pinch that changes the window sixty
 * times a second still produces exactly one query.
 */
private const val RELOAD_DEBOUNCE_MILLIS = 250L

private val CURSOR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Default period on open — long enough to show a daily shape, short enough to load fast. */
private const val DEFAULT_PERIOD_INDEX = 2 // 6ч

/**
 * Полноэкранный график мощности дозы (тап по карточке Монитора).
 *
 * **Раскладка.** Компактная шапка (закрыть · заголовок · живое значение с
 * погрешностью · чип свежести/паузы) → график на всю оставшуюся высоту,
 * от края до края по горизонтали → полоса распределения значений окна →
 * компактная статистика окна (P10 · медиана · P90 · n · окно, спец §13) →
 * раскрываемая «расширенная статистика» (мин/Q25/Q75/макс, MAD/SD/IQR с
 * единицами) → ряд управления (периоды, лин/лог, «⌖ сейчас») → одна
 * приглушённая строка анатомии графика. В ландшафте график занимает весь
 * экран, статистика сжимается в одну моно-строку шапки, управление плавает
 * чипами над правым нижним углом.
 *
 * **Производительность.** Один запрос в БД на смену окна, с запасом по
 * четверти окна с каждой стороны ([ChartWindows.loadRange]); pan/pinch только
 * перепроецируют неизменяемый снимок; повторное чтение — через
 * [RELOAD_DEBOUNCE_MILLIS] после жеста. Живое значение — отдельный composable
 * со своим тикером, поэтому 1 Гц поток не перерисовывает график. Слои графика
 * разделены и кэшируются, см. [DoseChart].
 *
 * **Достоверность (SPEC §2, спец графика §6/§7).** Линия — медиана корзины
 * (Q50), заливки — квантильные конверты Q25–Q75 и Q10–Q90: это НАБЛЮДАЕМЫЙ
 * РАЗБРОС измерений, не погрешность и не доверительный интервал. Мин/макс
 * корзины НЕ заливаются полосой (экстремум растёт с числом отсчётов) —
 * значимые экстремумы помечаются отдельными маркерами и раскрываются по
 * тапу. Серая полоса — исторический P10–P90 профиля, статистика места, а не
 * норматив. Эпизоды берут время из журнала событий, длительность считается
 * по корзинам и всегда названа относительно своего порога. Строка под
 * управлением говорит это словами.
 */
@Composable
fun LiveChartScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val deviation by graph.serviceStatus.deviation.collectAsState()
    val thresholds by graph.settings.alarmThresholds
        .collectAsState(initial = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f))
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val baseline = (baselineState as? BaselineState.Active)?.baseline

    var periodIndex by rememberSaveable { mutableIntStateOf(DEFAULT_PERIOD_INDEX) }
    var logScale by rememberSaveable { mutableStateOf(false) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var cursorActive by rememberSaveable { mutableStateOf(false) }
    var statsExpanded by rememberSaveable { mutableStateOf(false) }
    // Crosshair position lives in its own State: the draw layer and the
    // readout card read it, so dragging never recomposes the screen.
    val cursorFraction = remember { mutableStateOf<Float?>(null) }

    var window by remember {
        mutableStateOf(
            ChartWindows.latest(
                ChartWindows.PERIODS[DEFAULT_PERIOD_INDEX].second,
                System.currentTimeMillis(),
            ),
        )
    }
    var snapshot by remember { mutableStateOf<DoseSnapshot?>(null) }

    // Live-follow: advance the right edge at the cadence at which a new column
    // can actually appear (1 s on short windows, at most 15 s on long ones) —
    // never faster than the display could show a difference.
    LaunchedEffect(follow, periodIndex) {
        while (follow) {
            delay(
                ChartWindows.refreshMillis(
                    DoseChartModel.bucketMillis(window.spanMillis),
                ),
            )
            window = ChartWindows.follow(window, System.currentTimeMillis())
        }
    }

    LaunchedEffect(graph) {
        snapshotFlow { window }.collectLatest { w ->
            delay(RELOAD_DEBOUNCE_MILLIS)
            snapshot = withContext(Dispatchers.IO) { loadSnapshot(graph, w) }
        }
    }

    // Keyed on the alert *flag*, not on the deviation snapshot: the engine
    // republishes that object every second and rebuilding the frame at 1 Hz
    // for an unchanged picture is exactly the waste this screen must avoid.
    val endpointAlert = follow && deviation.alertSince != null
    val frame = remember(snapshot, window, unit, logScale, thresholds, baseline, endpointAlert) {
        snapshot?.let {
            buildFrame(
                snapshot = it,
                window = window,
                unit = unit,
                logScale = logScale,
                thresholds = thresholds,
                baseline = baseline,
                endpointAlert = endpointAlert,
            )
        }
    }

    fun selectPeriod(index: Int) {
        periodIndex = index
        window = ChartWindows.latest(
            ChartWindows.PERIODS[index].second,
            System.currentTimeMillis(),
        )
        val next = ChartInteractions.periodChanged()
        follow = next.follow
        cursorActive = false
        cursorFraction.value = null
    }

    fun jumpToNow() {
        window = ChartWindows.follow(window, System.currentTimeMillis())
        val next = ChartInteractions.jumpToNow()
        follow = next.follow
        cursorActive = false
        cursorFraction.value = null
    }

    val onTransform: (Float, Float, Float) -> Unit = { pan, zoom, focus ->
        val now = System.currentTimeMillis()
        var w = window
        if (zoom != 1f) w = ChartWindows.zoom(w, zoom, focus, now)
        // Dragging right pulls earlier data into view.
        if (pan != 0f) w = ChartWindows.pan(w, -pan, now)
        window = w
        val atEdge = ChartWindows.isAtLiveEdge(
            w,
            now,
            DoseChartModel.bucketMillis(w.spanMillis),
        )
        val next = ChartInteractions.afterTransform(
            ChartInteraction(follow, cursorFraction.value),
            atEdge,
        )
        follow = next.follow
        if (cursorActive) {
            cursorActive = false
            cursorFraction.value = null
        }
    }

    val chart: @Composable (Modifier) -> Unit = { chartModifier ->
        Box(chartModifier) {
            val f = frame
            // The chart is drawn even for an empty window: axes and gestures
            // stay alive, so panning into a gap is never a dead end.
            if (f != null) {
                DoseChart(
                    spec = f.spec,
                    cursorFraction = cursorFraction,
                    modifier = Modifier.fillMaxSize(),
                    cursorActive = cursorActive,
                    onCursorFraction = { fraction ->
                        cursorActive = true
                        follow = false
                        cursorFraction.value = fraction
                    },
                    onCursorDismiss = {
                        val atEdge = ChartWindows.isAtLiveEdge(
                            window,
                            System.currentTimeMillis(),
                            DoseChartModel.bucketMillis(window.spanMillis),
                        )
                        cursorActive = false
                        cursorFraction.value = null
                        follow = ChartInteractions.dismissCursor(
                            ChartInteraction(follow, null),
                            atEdge,
                        ).follow
                    },
                    onTransform = onTransform,
                )
                CursorCard(
                    cursorFraction = cursorFraction,
                    buckets = f.spec.buckets,
                    window = window,
                    unit = unit,
                    baseline = baseline,
                    alarmLevel = thresholds.l1MicroSvH,
                )
            }
            if (f == null || f.spec.buckets.isEmpty()) {
                Text(
                    text = if (snapshot == null) "читаем журнал…" else "в этом окне нет измерений",
                    style = type.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    if (landscape) {
        Box(Modifier.fillMaxSize().background(colors.bg).systemBarsPadding()) {
            chart(Modifier.fillMaxSize())
            LandscapeTopBar(
                graph = graph,
                unit = unit,
                periodLabel = ChartWindows.PERIODS[periodIndex].first,
                stats = frame?.stats,
                paused = cursorActive,
                onBack = onBack,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimens.space2),
            ) {
                ControlChips(
                    periodIndex = periodIndex,
                    logScale = logScale,
                    follow = follow,
                    onSelectPeriod = ::selectPeriod,
                    onToggleScale = { logScale = !logScale },
                    onJumpToNow = ::jumpToNow,
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(colors.bg).systemBarsPadding()) {
        PortraitTopBar(
            graph = graph,
            unit = unit,
            periodLabel = ChartWindows.PERIODS[periodIndex].first,
            paused = cursorActive,
            onBack = onBack,
        )
        chart(Modifier.weight(1f).fillMaxWidth())
        AppDivider()
        val histogram = frame?.histogram
        if (frame != null && histogram != null) {
            DistributionStrip(
                histogram = histogram,
                labels = frame.histogramLabels,
            )
        } else {
            Spacer(Modifier.fillMaxWidth().height(Dimens.space1))
        }
        val stats = frame?.stats
        // CHART SPEC §13: the compact default is quantiles, n and the window;
        // MIN/Q25/Q75/MAX/MAD/SD live one tap deeper so the main view is not
        // a wall of numbers and SD never appears without its definition.
        StatGrid(
            cells = listOf(
                StatCell(stats?.let { DoseFormat.rate(it.p10, unit) } ?: "—", "P10"),
                StatCell(stats?.let { DoseFormat.rate(it.median, unit) } ?: "—", "медиана"),
                StatCell(stats?.let { DoseFormat.rate(it.p90, unit) } ?: "—", "P90"),
                StatCell(stats?.let { HistoryFormat.count(it.sampleCount) } ?: "—", "n"),
                StatCell(HistoryFormat.duration(window.spanMillis / 1000), "окно"),
            ),
        )
        ExpandedStats(stats = stats, unit = unit, expanded = statsExpanded) {
            statsExpanded = !statsExpanded
        }
        if (statsExpanded) {
            QuantileDiagnosticPanel(graph = graph, snapshot = snapshot, unit = unit)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.space2, vertical = Dimens.space2),
        ) {
            ControlChips(
                periodIndex = periodIndex,
                logScale = logScale,
                follow = follow,
                onSelectPeriod = ::selectPeriod,
                onToggleScale = { logScale = !logScale },
                onJumpToNow = ::jumpToNow,
            )
        }
        Text(
            text = truthLine(
                logScale = logScale,
                logDropped = frame?.logDropped ?: 0,
                hasBaseline = baseline != null,
                method = frame?.stats?.method ?: QuantileMethod.EXACT_RAW,
            ),
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.padding(
                start = Dimens.space3,
                end = Dimens.space3,
                bottom = Dimens.space2,
            ),
        )
    }
}

// --- top bars -------------------------------------------------------------

/**
 * Live reading with its own 1 Hz ticker. Isolating it here is a performance
 * decision: a new sample recomposes these two texts and the freshness chip,
 * never the chart.
 */
@Composable
private fun liveReading(
    graph: AppGraph,
    unit: DoseUnitSetting,
    compact: Boolean = false,
): Freshness {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val freshness = Freshness.of(sample?.timestamp, nowMillis)
    if (!compact) {
        val dose = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = dose?.let { DoseFormat.rate(it, unit) } ?: "—",
                style = type.valueLarge,
                color = if (dose == null || freshness !is Freshness.Fresh) colors.muted
                else colors.ink,
            )
            Text(
                text = listOfNotNull(
                    Uncertainty.errPercentLabel(sample?.doseRateErr),
                    DoseFormat.rateUnitLabel(unit),
                ).joinToString(" "),
                style = type.footnote,
                color = colors.ink2,
                modifier = Modifier.padding(start = 5.dp, bottom = 2.dp),
            )
        }
    }
    return freshness
}

@Composable
private fun PortraitTopBar(
    graph: AppGraph,
    unit: DoseUnitSetting,
    periodLabel: String,
    paused: Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimens.space3,
                    end = Dimens.space3,
                    top = Dimens.space2,
                    bottom = Dimens.space2,
                ),
        ) {
            Chip(text = "✕", color = colors.ink2, onClick = onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Мощность дозы · $periodLabel".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                    maxLines = 1,
                )
                StatusChipSlot(graph, unit, paused)
            }
        }
        AppDivider()
    }
}

/** Value row plus the status chip, both driven by the same 1 Hz ticker. */
@Composable
private fun StatusChipSlot(graph: AppGraph, unit: DoseUnitSetting, paused: Boolean) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        val freshness = liveReading(graph, unit)
        Spacer(Modifier.weight(1f))
        FreshnessOrPause(freshness, paused)
    }
}

@Composable
private fun FreshnessOrPause(freshness: Freshness, paused: Boolean) {
    val colors = LocalAppColors.current
    when {
        paused -> Chip(text = "пауза", color = colors.warn, selected = true)
        freshness is Freshness.Fresh -> Chip(text = "${freshness.ageSeconds} с", dot = colors.ok)
        freshness is Freshness.Stale ->
            Chip(text = "прервано ${freshness.ageSeconds} с", color = colors.warn)
        else -> Chip(text = "нет данных", color = colors.muted)
    }
}

@Composable
private fun BoxScope.LandscapeTopBar(
    graph: AppGraph,
    unit: DoseUnitSetting,
    periodLabel: String,
    stats: WindowStats?,
    paused: Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(horizontal = Dimens.space2, vertical = Dimens.space1),
    ) {
        Chip(text = "✕", color = colors.ink2, onClick = onBack)
        Text(
            text = "Мощность дозы · $periodLabel".uppercase(),
            style = type.labelSmall,
            color = colors.ink2,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        stats?.let {
            Text(
                text = landscapeStatsLine(it, unit),
                style = type.footnote,
                color = colors.ink2,
                maxLines = 1,
            )
        }
        val freshness = liveReading(graph, unit, compact = true)
        FreshnessOrPause(freshness, paused)
    }
}

private fun landscapeStatsLine(stats: WindowStats, unit: DoseUnitSetting): String = listOf(
    "P10 ${DoseFormat.rate(stats.p10, unit)}",
    "медиана ${DoseFormat.rate(stats.median, unit)}",
    "P90 ${DoseFormat.rate(stats.p90, unit)}",
    "MAD ${DoseFormat.rate(stats.mad, unit)}",
    "SD ${DoseFormat.rate(stats.sd, unit)} ${DoseFormat.rateUnitLabel(unit)}",
    "n ${HistoryFormat.count(stats.sampleCount)}",
).joinToString(" · ")

// --- controls -------------------------------------------------------------

@Composable
private fun RowScope.ControlChips(
    periodIndex: Int,
    logScale: Boolean,
    follow: Boolean,
    onSelectPeriod: (Int) -> Unit,
    onToggleScale: () -> Unit,
    onJumpToNow: () -> Unit,
) {
    val colors = LocalAppColors.current
    for (index in ChartWindows.periodChipRange(periodIndex)) {
        val selected = index == periodIndex
        Chip(
            text = ChartWindows.PERIODS[index].first,
            color = if (selected) colors.ink else colors.ink2,
            selected = selected,
            onClick = { onSelectPeriod(index) },
        )
    }
    Spacer(Modifier.weight(1f))
    Chip(
        text = if (logScale) "лог" else "лин",
        color = if (logScale) colors.dataText else colors.ink2,
        selected = logScale,
        onClick = onToggleScale,
    )
    Chip(
        text = "⌖ сейчас",
        color = if (follow) colors.ink2 else colors.dataText,
        selected = !follow,
        onClick = onJumpToNow,
    )
}

/**
 * «Расширенная статистика» (CHART SPEC §12, §13): MIN/Q25/Q75/MAX/MAD/SD, each
 * named in full and with its unit — a bare «σ» is forbidden, and SD/MAD belong
 * here rather than in the compact view.
 */
@Composable
private fun ExpandedStats(
    stats: WindowStats?,
    unit: DoseUnitSetting,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val unitLabel = DoseFormat.rateUnitLabel(unit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Dimens.space3, vertical = Dimens.space1),
    ) {
        Text(
            text = "расширенная статистика",
            style = type.footnote,
            color = colors.ink2,
        )
        Spacer(Modifier.weight(1f))
        Text(text = if (expanded) "▴" else "▾", style = type.footnote, color = colors.ink2)
    }
    if (!expanded) return
    StatGrid(
        cells = listOf(
            StatCell(stats?.let { DoseFormat.rate(it.min, unit) } ?: "—", "мин"),
            StatCell(stats?.let { DoseFormat.rate(it.q25, unit) } ?: "—", "Q25"),
            StatCell(stats?.let { DoseFormat.rate(it.q75, unit) } ?: "—", "Q75"),
            StatCell(stats?.let { DoseFormat.rate(it.max, unit) } ?: "—", "макс"),
        ),
    )
    StatGrid(
        cells = listOf(
            StatCell(stats?.let { DoseFormat.rate(it.mad, unit) } ?: "—", "MAD, $unitLabel"),
            StatCell(stats?.let { DoseFormat.rate(it.sd, unit) } ?: "—", "SD, $unitLabel"),
            StatCell(stats?.let { DoseFormat.rate(it.iqr, unit) } ?: "—", "IQR, $unitLabel"),
        ),
    )
    Text(
        text = "SD — наблюдаемый разброс значений · MAD = median(|xᵢ − медиана|), " +
            "робастный разброс · IQR = Q75 − Q25",
        style = type.footnote,
        color = colors.muted,
        modifier = Modifier.padding(
            start = Dimens.space3,
            end = Dimens.space3,
            top = Dimens.space1,
        ),
    )
}

/**
 * The one muted line that describes the anatomy of the chart exactly (CHART
 * SPEC §6, §7, §8, §41): what is a level, what is observed spread, what is a
 * historical statistic of the place, what is an event marker — and, when the
 * window is long, that the quantiles are an approximation.
 */
private fun truthLine(
    logScale: Boolean,
    logDropped: Int,
    hasBaseline: Boolean,
    method: QuantileMethod,
): String {
    val parts = mutableListOf(
        "линия — медиана корзины (Q50)",
        "Q25–Q75 и Q10–Q90 — наблюдаемый разброс измерений, не погрешность",
    )
    parts += if (hasBaseline) {
        "серая полоса — исторический P10–P90 профиля, это статистика места, а не норматив"
    } else {
        "исторический диапазон профиля ещё не собран"
    }
    parts += "▲ — экстремум корзины выше порога L1 (залит) или выше P90 профиля (контур)"
    parts += "полосы эпизодов — журнал событий, длительность расчётная"
    parts += when (method) {
        QuantileMethod.EXACT_RAW -> "квантили — точные по сырым отсчётам"
        QuantileMethod.KLL_SKETCH ->
            "квантили — приближение по почасовым KLL-скетчам (ошибка ранга ≈ " +
                QuantileMetadata.errorPercentLabel(KllSketch.DEFAULT_K) + ")"
        QuantileMethod.SUB_BUCKET_MEANS ->
            "квантили — грубая оценка по под-корзинам: предагрегация ещё строится"
    }
    if (logScale && logDropped > 0) {
        parts += "лог-шкала: корзин с нулём не показано — $logDropped"
    }
    return parts.joinToString(" · ")
}

/**
 * Исследовательская диагностика квантилей (CHART SPEC §32, §34, §37G; ADR
 * 004). Живёт под расширенной статистикой, потому что это ровно то место, где
 * пользователь уже спрашивает «а как именно посчитано».
 *
 * Показывает: каким путём получены квантили текущего окна, версию и параметр
 * точности скетча, ход построения предагрегации — и, по явному запросу,
 * считает то же окно ВТОРЫМ путём: читает все сырые отсчёты часов, из которых
 * собран скетч, берёт точные порядковые статистики и сравнивает. Ошибка
 * измеряется по РАНГУ (где приближённое значение реально стоит в
 * распределении), потому что разница в значении сама по себе нечитаема: на
 * плоском участке 1 % ранга невидим, на крутом хвосте — заметен.
 *
 * Точный путь читает окно целиком, поэтому он никогда не запускается сам.
 */
@Composable
private fun QuantileDiagnosticPanel(
    graph: AppGraph,
    snapshot: DoseSnapshot?,
    unit: DoseUnitSetting,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
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
            text = "метод квантилей: " + QuantileMetadata.label(method, sketch?.k ?: KllSketch.DEFAULT_K),
            style = type.footnote,
            color = colors.muted,
        )
        if (backfill.running && backfill.hoursTotal > 0) {
            Text(
                text = "предагрегация истории: ${(backfill.fraction * 100).toInt()} % " +
                    "(${backfill.hoursDone} из ${backfill.hoursTotal} ч)",
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
                    text = if (running) "считаем…" else "сверить с сырыми",
                    color = colors.ink2,
                    onClick = {
                        if (!running) {
                            running = true
                            report = null
                            scope.launch {
                                val text = withContext(Dispatchers.IO) {
                                    compareQuantilePaths(graph, sketch, range, unit)
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
): String {
    val raw = graph.preAggregateRepository.rawDoseValues(range.first, range.last)
        ?: return "точный путь отказался: в окне больше " +
            "${PreAggregateRepository.MAX_DIAGNOSTIC_ROWS} отсчётов"
    if (raw.isEmpty()) return "в этих часах нет сырых отсчётов"
    val factor = DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR
    for (i in raw.indices) raw[i] = raw[i] * factor
    val comparison = QuantileDiagnostics.compare(raw, sketch)
    return diagnosticReport(comparison, unit)
}

/** Text of the exact-vs-sketch comparison — plain numbers, no verdicts. */
private fun diagnosticReport(comparison: QuantileComparison, unit: DoseUnitSetting): String {
    val names = listOf("P10", "Q25", "медиана", "Q75", "P90")
    val lines = StringBuilder()
    lines.append("точные против скетча · n ")
    lines.append(HistoryFormat.count(comparison.sampleCount))
    if (!comparison.countsAgree) {
        lines.append(" (скетч знает ${comparison.sketchCount} — сравниваются разные данные)")
    }
    lines.append(" · k=${comparison.k}\n")
    for (i in comparison.probabilities.indices) {
        val name = names.getOrElse(i) { "p${comparison.probabilities[i]}" }
        lines.append(name)
        lines.append(' ')
        lines.append(DoseFormat.rate(comparison.exactValues[i], unit))
        lines.append(" → ")
        lines.append(DoseFormat.rate(comparison.approximateValues[i], unit))
        lines.append(" (ранг ")
        lines.append(percent(comparison.rankErrors[i]))
        lines.append(")\n")
    }
    lines.append("максимальная ошибка ранга ")
    lines.append(percent(comparison.maxRankError))
    return lines.toString()
}

private fun percent(value: Double): String =
    String.format(java.util.Locale.ROOT, "%.2f %%", value * 100).replace('.', ',')

// --- cursor readout -------------------------------------------------------

/**
 * Crosshair readout. Reads the cursor [State] itself, so a drag recomposes
 * this card and nothing else. It sits on the side of the plot the finger is
 * not on, so the value is never hidden by the reading hand.
 */
@Composable
private fun BoxScope.CursorCard(
    cursorFraction: State<Float?>,
    buckets: List<ChartBucket>,
    window: ChartWindow,
    unit: DoseUnitSetting,
    baseline: Baseline?,
    alarmLevel: Float?,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val fraction = cursorFraction.value ?: return
    val time = ChartWindows.timeAt(window, fraction)
    val bucket = CursorReadout.nearestBucket(buckets, time) ?: return
    val above = alarmLevel != null && bucket.median >= alarmLevel
    val clock: (Long) -> String = { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CURSOR_TIME)
    }
    val extreme = DoseExtremes.classify(bucket, alarmLevel, baseline?.doseHighMicroSvH)
    Card(
        modifier = Modifier
            .align(if (fraction < 0.5f) Alignment.TopEnd else Alignment.TopStart)
            .padding(Dimens.space2),
        contentPadding = Dimens.space2,
    ) {
        // CHART SPEC §16: interval, median, both envelopes, the exact extrema
        // with their times, n — then the profile baseline block.
        Column {
            Text(
                text = CursorReadout.binRangeLabel(bucket, clock),
                style = type.footnote,
                color = colors.ink2,
            )
            Text(
                text = DoseFormat.rate(bucket.median, unit),
                style = type.value,
                color = if (above) colors.crit else colors.ink,
            )
            CursorRow("медиана", DoseFormat.rate(bucket.median, unit))
            CursorRow(
                "Q25–Q75",
                DoseFormat.range(bucket.q25, bucket.q75, unit),
            )
            CursorRow(
                "Q10–Q90",
                DoseFormat.range(bucket.q10, bucket.q90, unit),
            )
            CursorRow(
                "мин",
                DoseFormat.rate(bucket.min, unit) + " " +
                    CursorReadout.extremeTimeLabel(
                        bucket.minAtMillis,
                        bucket.extremeWindowMillis,
                        clock,
                    ),
            )
            CursorRow(
                "макс",
                DoseFormat.rate(bucket.max, unit) + " " +
                    CursorReadout.extremeTimeLabel(
                        bucket.maxAtMillis,
                        bucket.extremeWindowMillis,
                        clock,
                    ),
            )
            CursorRow("измерений", HistoryFormat.count(bucket.sampleCount))
            if (extreme != null) {
                Text(
                    text = "▲ экстремум ${referenceWording(extreme)}",
                    style = type.footnote,
                    color = if (extreme == DoseReference.ALARM_L1) colors.crit else colors.warn,
                )
            }
            if (!bucket.quantilesExact) {
                Text(
                    text = when (bucket.method) {
                        QuantileMethod.KLL_SKETCH -> "квантили корзины — почасовые скетчи; " +
                            "мин/макс и время — точные"
                        else -> "квантили корзины — грубая оценка, предагрегация ещё строится"
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            if (baseline != null) {
                AppDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    text = "исторический профиль",
                    style = type.footnote,
                    color = colors.ink2,
                )
                CursorRow("медиана", DoseFormat.rate(baseline.doseMedianMicroSvH, unit))
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
                    Text(
                        text = CursorReadout.ratioExplanation(RatioDenominator.BASELINE_P90),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

/** One «label   value» line of the cursor card. */
@Composable
private fun CursorRow(label: String, value: String) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = type.footnote, color = colors.ink2)
        Spacer(Modifier.width(Dimens.space2))
        Spacer(Modifier.weight(1f))
        Text(text = value, style = type.footnote, color = colors.ink)
    }
}

// --- frame assembly -------------------------------------------------------

/** Everything one frame of the screen needs, derived from the snapshot. */
private data class ChartFrame(
    val spec: DoseChartSpec,
    val stats: WindowStats?,
    val histogram: DoseHistogram?,
    val histogramLabels: List<Pair<Float, String>>,
    val logDropped: Int,
)

/**
 * Pure derivation of the visible frame from an immutable snapshot: no I/O, no
 * suspension, O(columns ≤ 200) plus one pass over the sub-buckets. This runs
 * on every gesture frame and must stay that cheap.
 *
 * The scale is fitted to the whole **loaded** range, not to the visible slice,
 * so panning does not make the axis jump under the finger; the debounced
 * reload refits it afterwards.
 */
private fun buildFrame(
    snapshot: DoseSnapshot,
    window: ChartWindow,
    unit: DoseUnitSetting,
    logScale: Boolean,
    thresholds: AlarmThresholds,
    baseline: Baseline?,
    endpointAlert: Boolean,
): ChartFrame {
    val visible = snapshot.buckets.filter {
        it.midMillis >= window.fromMillis && it.midMillis <= window.toMillis
    }
    val alarm = thresholds.l1MicroSvH.takeIf { it > 0f }
    val band = baseline?.let { it.doseLowMicroSvH..it.doseHighMicroSvH }
    // The frame is fitted to what is actually drawn. With raw dots on screen
    // that is the true extremes; without them the envelopes stop at Q10–Q90,
    // and a single off-scale spike is carried by its marker and the cursor
    // card instead of stretching the whole axis (CHART SPEC §7 — an extremum
    // grows with N, so it must not define the frame).
    val dotsVisible = DoseChartModel.rawDotsVisible(snapshot.bucketMillis)
    val scale = DoseScales.of(
        logarithmic = logScale,
        dataMin = snapshot.buckets.minOfOrNull { if (dotsVisible) it.min else it.q10 },
        dataMax = snapshot.buckets.maxOfOrNull { if (dotsVisible) it.max else it.q90 },
        alarmLevel = alarm,
        baselineHigh = baseline?.doseHighMicroSvH,
    )
    val episodes = DoseEpisodes.around(
        buckets = visible,
        eventTimesMillis = snapshot.eventTimesMillis.filter {
            it >= window.fromMillis && it <= window.toMillis
        },
        alarmMicroSvH = alarm,
        baselineP90MicroSvH = baseline?.doseHighMicroSvH,
    )
    val markers = DoseExtremes.markers(
        buckets = visible,
        alarmMicroSvH = alarm,
        baselineP90MicroSvH = baseline?.doseHighMicroSvH,
    )
    val histogram = DoseHistograms.build(
        aggregates = snapshot.aggregates,
        fromMillis = window.fromMillis,
        toMillis = window.toMillis,
        baseline = band,
        alarmLevel = alarm,
    )
    val rawDots = if (dotsVisible) snapshot.aggregates else emptyList()
    return ChartFrame(
        spec = DoseChartSpec(
            buckets = visible,
            fromMillis = window.fromMillis,
            toMillis = window.toMillis,
            scale = scale,
            baselineBand = band,
            baselineMedian = baseline?.doseMedianMicroSvH,
            alarmLevel = alarm,
            alarmLabel = alarm?.let { "L1 ${DoseFormat.rate(it, unit)}" },
            episodes = episodes,
            // §20: a band must name the reference it is above, not only its
            // duration — «выше порога L1» and «выше исторического P90
            // профиля» are different events.
            episodeLabels = episodes.map {
                "${referenceWording(it.reference)} · " +
                    HistoryFormat.duration(it.durationMillis / 1000)
            },
            episodeShortLabels = episodes.map {
                "${referenceWordingShort(it.reference)} · " +
                    HistoryFormat.duration(it.durationMillis / 1000)
            },
            extremeMarkers = markers,
            yLabels = scale.ticks().map { it to DoseFormat.rate(it, unit) },
            xLabels = TimeAxis.autoLabels(window.fromMillis, window.toMillis, count = 4),
            unitLabel = DoseFormat.rateUnitLabel(unit),
            rawSamples = rawDots,
            endpointAlert = endpointAlert,
        ),
        // The long path computes the window statistics once per read (merging
        // sketches is far too expensive for a gesture frame); the exact path
        // recomputes them here from the sub-buckets.
        stats = snapshot.windowStats ?: DoseChartModel.windowStats(
            snapshot.aggregates,
            window.fromMillis,
            window.toMillis,
        ),
        histogram = histogram,
        histogramLabels = histogram
            ?.let { h ->
                DoseHistograms.labelValues(h).map { (fraction, value) ->
                    fraction to DoseFormat.rate(value, unit)
                }
            }
            .orEmpty(),
        logDropped = if (logScale) DoseScales.logDroppedBuckets(visible) else 0,
    )
}

/**
 * The single database read of a window change (ADR 004). Runs on the IO
 * dispatcher and picks one of the two paths by the span:
 *
 *  - **≤ 6 h — exact.** SQL is asked for 1-second buckets, i.e. one row per
 *    raw sample (≤ 21 600), and every column carries the true order
 *    statistics of the measurements (CHART SPEC §29).
 *  - **longer — merged hourly sketches.** One row per stored hour (720 for 30
 *    days) instead of millions of raw ones (§30, §34). Columns are whole
 *    hours, because a column may only be given the distribution of the hours
 *    it fully covers.
 *
 * When the long path finds no pre-aggregation at all (fresh install, backfill
 * still running) it degrades to the coarse sub-bucket estimate and says so
 * everywhere the numbers appear.
 */
private suspend fun loadSnapshot(graph: AppGraph, window: ChartWindow): DoseSnapshot {
    val now = System.currentTimeMillis()
    val load = ChartWindows.loadRange(window, now)
    return when (QuantilePaths.methodFor(load.spanMillis)) {
        QuantileMethod.EXACT_RAW -> loadExact(graph, load, QuantilePaths.exactSubBucketMillis())
        else -> loadSketched(graph, load, window)
    }
}

/** Exact path: raw samples, aggregated by SQL at [subMillis] granularity. */
private suspend fun loadExact(
    graph: AppGraph,
    load: ChartWindow,
    subMillis: Long,
    bucketMillis: Long = DoseChartModel.bucketMillis(load.spanMillis),
): DoseSnapshot {
    val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
    val rows = graph.measurementRepository.doseBuckets(alignedFrom, load.toMillis, subMillis)
    val aggregates = rows.map { row ->
        DoseAggregate(
            startMillis = row.bucketStart,
            minMicroSvH = DoseUnits.rawToMicroSievertPerHour(row.minDoseRate),
            maxMicroSvH = DoseUnits.rawToMicroSievertPerHour(row.maxDoseRate),
            sumMicroSvH = row.sumDoseRate * DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR,
            sumSqMicroSvH = row.sumSqDoseRate *
                DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR.toDouble() *
                DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR,
            sampleCount = row.sampleCount,
        )
    }
    val events = graph.measurementRepository
        .deviationEvents(alignedFrom, load.toMillis)
        .map { it.timestamp }
    return DoseChartModel.snapshot(
        aggregates = aggregates,
        eventTimesMillis = events,
        alignedFromMillis = alignedFrom,
        toMillis = load.toMillis,
        bucketMillis = bucketMillis,
        subBucketMillis = subMillis,
    )
}

/** Long path: merged hourly KLL sketches, with the coarse estimate as fallback. */
private suspend fun loadSketched(
    graph: AppGraph,
    load: ChartWindow,
    window: ChartWindow,
): DoseSnapshot {
    val bucketMillis = QuantilePaths.bucketMillis(load.spanMillis, QuantileMethod.KLL_SKETCH)
    val alignedFrom = ChartMapping.alignedFrom(load.toMillis, load.spanMillis, bucketMillis)
    val hours = graph.preAggregateRepository.hourSketchesWithLiveTail(alignedFrom, load.toMillis)
    val factor = DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR
    val slices = hours.mapNotNull { row ->
        val sketch = KllSketch.fromByteArray(row.sketch) ?: return@mapNotNull null
        DoseHourSlice(
            startMillis = row.hourStart,
            sampleCount = row.count,
            min = DoseUnits.rawToMicroSievertPerHour(row.minDoseRate),
            max = DoseUnits.rawToMicroSievertPerHour(row.maxDoseRate),
            minAtMillis = row.minAtMillis,
            maxAtMillis = row.maxAtMillis,
            sketch = sketch.scaled(factor),
        )
    }
    if (slices.isEmpty()) {
        // Nothing pre-aggregated for this range yet: fall back to the coarse
        // sub-bucket estimate, which the UI names as such (§32).
        return loadExact(graph, load, DoseChartModel.subBucketMillis(
            DoseChartModel.bucketMillis(load.spanMillis),
        ))
    }
    val events = graph.measurementRepository
        .deviationEvents(alignedFrom, load.toMillis)
        .map { it.timestamp }
    val rollup = graph.preAggregateRepository.rollup(window.fromMillis, window.toMillis)
    return DoseChartModel.snapshotFromSketches(
        slices = slices,
        eventTimesMillis = events,
        alignedFromMillis = alignedFrom,
        toMillis = load.toMillis,
        bucketMillis = bucketMillis,
        visibleFromMillis = window.fromMillis,
        visibleToMillis = window.toMillis,
        rollup = rollup.toDoseWindowRollup(factor),
    )
}

/** Minute-scalar rollup → window moments in µSv/h; null when nothing is built. */
private fun MinuteRollup.toDoseWindowRollup(factor: Float): DoseWindowRollup? {
    val n = sampleCount ?: return null
    if (n <= 0) return null
    return DoseWindowRollup(
        sampleCount = n,
        sumMicroSvH = (sumDoseRate ?: 0.0) * factor,
        sumSqMicroSvH = (sumSqDoseRate ?: 0.0) * factor.toDouble() * factor,
        min = DoseUnits.rawToMicroSievertPerHour(minDoseRate ?: 0f),
        max = DoseUnits.rawToMicroSievertPerHour(maxDoseRate ?: 0f),
        admittedCount = admittedCount ?: n,
    )
}
