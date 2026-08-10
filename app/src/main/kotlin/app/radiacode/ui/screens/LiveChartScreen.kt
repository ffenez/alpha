package app.radiacode.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
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
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.DoseHistogram
import app.radiacode.ui.logic.DoseHistograms
import app.radiacode.ui.logic.DoseScales
import app.radiacode.ui.logic.DoseSnapshot
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.TimeAxis
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
 * от края до края по горизонтали → полоса распределения значений окна → две
 * строки статистики (мин/P10/медиана/P90 · макс/σ/n/окно) → ряд управления
 * (периоды, лин/лог, «⌖ сейчас») → одна приглушённая строка о том, что здесь
 * измерено, а что рассчитано. В ландшафте график занимает весь экран,
 * статистика сжимается в одну моно-строку шапки, управление плавает чипами
 * над правым нижним углом.
 *
 * **Производительность.** Один запрос в БД на смену окна, с запасом по
 * четверти окна с каждой стороны ([ChartWindows.loadRange]); pan/pinch только
 * перепроецируют неизменяемый снимок; повторное чтение — через
 * [RELOAD_DEBOUNCE_MILLIS] после жеста. Живое значение — отдельный composable
 * со своим тикером, поэтому 1 Гц поток не перерисовывает график. Слои графика
 * разделены и кэшируются, см. [DoseChart].
 *
 * **Достоверность (SPEC §2).** Линия — медиана корзины, заливки — конверт
 * мин–макс и ±σ: это РАСЧЁТ по измеренным корзинам, а не само измерение.
 * Полоса привычного — статистика профиля места. Эпизоды берут время из
 * журнала событий, длительность считается по корзинам. Строка под управлением
 * говорит это словами.
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
            if (f == null || f.spec.buckets.isEmpty()) {
                Text(
                    text = if (snapshot == null) "читаем журнал…" else "в этом окне нет измерений",
                    style = type.bodySmall,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
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
                    baselineHigh = baseline?.doseHighMicroSvH,
                    alarmLevel = thresholds.l1MicroSvH,
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
        StatGrid(
            cells = listOf(
                StatCell(stats?.let { DoseFormat.rate(it.min, unit) } ?: "—", "мин"),
                StatCell(stats?.let { DoseFormat.rate(it.p10, unit) } ?: "—", "P10"),
                StatCell(stats?.let { DoseFormat.rate(it.median, unit) } ?: "—", "медиана"),
                StatCell(stats?.let { DoseFormat.rate(it.p90, unit) } ?: "—", "P90"),
            ),
        )
        StatGrid(
            cells = listOf(
                StatCell(stats?.let { DoseFormat.rate(it.max, unit) } ?: "—", "макс"),
                StatCell(stats?.let { DoseFormat.rate(it.sigma, unit) } ?: "—", "σ"),
                StatCell(stats?.let { HistoryFormat.count(it.sampleCount) } ?: "—", "n"),
                StatCell(HistoryFormat.duration(window.spanMillis / 1000), "окно"),
            ),
        )
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
            text = truthLine(logScale, frame?.logDropped ?: 0, baseline != null),
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
    "мин ${DoseFormat.rate(stats.min, unit)}",
    "P10 ${DoseFormat.rate(stats.p10, unit)}",
    "медиана ${DoseFormat.rate(stats.median, unit)}",
    "P90 ${DoseFormat.rate(stats.p90, unit)}",
    "макс ${DoseFormat.rate(stats.max, unit)}",
    "σ ${DoseFormat.rate(stats.sigma, unit)}",
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

private fun truthLine(logScale: Boolean, logDropped: Int, hasBaseline: Boolean): String {
    val parts = mutableListOf(
        "медиана, ±σ и конверт — расчёт по корзинам измерений",
    )
    parts += if (hasBaseline) {
        "полоса привычного — статистика профиля места"
    } else {
        "привычный фон места ещё не собран"
    }
    parts += "эпизоды — журнал событий, длительность расчётная"
    if (logScale && logDropped > 0) {
        parts += "лог-шкала: корзин с нулём не показано — $logDropped"
    }
    return parts.joinToString(" · ")
}

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
    baselineHigh: Float?,
    alarmLevel: Float?,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val fraction = cursorFraction.value ?: return
    val time = ChartWindows.timeAt(window, fraction)
    val bucket = CursorReadout.nearestBucket(buckets, time) ?: return
    val above = alarmLevel != null && bucket.median >= alarmLevel
    Card(
        modifier = Modifier
            .align(if (fraction < 0.5f) Alignment.TopEnd else Alignment.TopStart)
            .padding(Dimens.space2),
        contentPadding = Dimens.space2,
    ) {
        Column {
            Text(
                text = Instant.ofEpochMilli(bucket.midMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(CURSOR_TIME),
                style = type.footnote,
                color = colors.ink2,
            )
            Text(
                text = "${DoseFormat.rate(bucket.median, unit)} ±" +
                    DoseFormat.rate(bucket.sigma, unit),
                style = type.value,
                color = if (above) colors.crit else colors.ink,
            )
            Text(
                text = "n ${HistoryFormat.count(bucket.sampleCount)}" +
                    (baselineHigh?.let { " · P90 фона ${DoseFormat.rate(it, unit)}" } ?: ""),
                style = type.footnote,
                color = colors.muted,
            )
            CursorReadout.ratioToUsual(bucket.median, baselineHigh)?.let { ratio ->
                Text(
                    text = CursorReadout.ratioLabel(ratio),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
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
    val scale = DoseScales.of(
        logarithmic = logScale,
        dataMin = snapshot.buckets.minOfOrNull { it.min },
        dataMax = snapshot.buckets.maxOfOrNull { it.max },
        alarmLevel = alarm,
        baselineHigh = baseline?.doseHighMicroSvH,
    )
    val episodes = DoseEpisodes.around(
        buckets = visible,
        eventTimesMillis = snapshot.eventTimesMillis.filter {
            it >= window.fromMillis && it <= window.toMillis
        },
        thresholdMicroSvH = alarm ?: Float.MAX_VALUE,
    )
    val histogram = DoseHistograms.build(
        aggregates = snapshot.aggregates,
        fromMillis = window.fromMillis,
        toMillis = window.toMillis,
        baseline = band,
        alarmLevel = alarm,
    )
    val rawDots = if (DoseChartModel.rawDotsVisible(snapshot.bucketMillis)) {
        snapshot.aggregates
    } else {
        emptyList()
    }
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
            episodeLabels = episodes.map { HistoryFormat.duration(it.durationMillis / 1000) },
            yLabels = scale.ticks().map { it to DoseFormat.rate(it, unit) },
            xLabels = TimeAxis.autoLabels(window.fromMillis, window.toMillis, count = 4),
            unitLabel = DoseFormat.rateUnitLabel(unit),
            rawSamples = rawDots,
            endpointAlert = endpointAlert,
        ),
        stats = DoseChartModel.windowStats(
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
 * The single database read of a window change. Runs on the IO dispatcher and
 * asks SQLite for at most [DoseChartModel.MAX_BUCKETS] ×
 * [DoseChartModel.SUB_BUCKETS_PER_BUCKET] aggregate rows — a fixed budget
 * whatever the range.
 */
private suspend fun loadSnapshot(graph: AppGraph, window: ChartWindow): DoseSnapshot {
    val now = System.currentTimeMillis()
    val load = ChartWindows.loadRange(window, now)
    val bucketMillis = DoseChartModel.bucketMillis(load.spanMillis)
    val subMillis = DoseChartModel.subBucketMillis(bucketMillis)
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
    )
}
