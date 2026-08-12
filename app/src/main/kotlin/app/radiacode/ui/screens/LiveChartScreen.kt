package app.radiacode.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import app.radiacode.ui.logic.ChartInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import app.radiacode.AppGraph
import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.analysis.quantiles.QuantileComparison
import app.radiacode.analysis.quantiles.QuantileDiagnostics
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.PreAggregateRepository
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.DistributionStrip
import app.radiacode.ui.components.DoseChart
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.ChartBucket
import app.radiacode.ui.logic.ChartInteraction
import app.radiacode.ui.logic.ChartInteractions
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.analysis.Hardness
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.logic.ChartMetrics
import app.radiacode.ui.logic.CursorReadout
import app.radiacode.ui.logic.coverageWording
import app.radiacode.ui.logic.ChartSeriesModel
import app.radiacode.ui.logic.DoseExtremes
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.DoseReference
import app.radiacode.ui.logic.ChartSnapshot
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.freshnessChipLabel
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.QuantileMetadata
import app.radiacode.ui.logic.QuantileMethod
import app.radiacode.ui.logic.RatioDenominator
import app.radiacode.ui.logic.referenceWording
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.WindowStats
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.Motion
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

/** Ширина шага ленты периодов — чип плюс интервал; для авто-прокрутки. */
private const val CHIP_STEP_DP = 52

private val CURSOR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Default period on open — long enough to show a shape, short enough to load fast. */

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
fun LiveChartScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    metric: ChartMetric = ChartMetric.DOSE,
) {
    val colors = LocalAppColors.current
    val settingsScope = rememberCoroutineScope()
    val type = LocalAppTypography.current
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val deviation by graph.serviceStatus.deviation.collectAsState()
    val thresholds by graph.settings.alarmThresholds
        .collectAsState(initial = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f))
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val baseline = (baselineState as? BaselineState.Active)?.baseline

    // Пока график открыт, экран не гаснет: на него смотрят, а не листают его,
    // и системный таймаут гасил дисплей ровно посреди наблюдения. Флаг живёт
    // на View этого экрана — он снимается, как только экран закрыт, и никогда
    // не действует на приложение целиком.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val periodIndices = remember(metric) { ChartMetrics.periodIndices(metric) }
    var logScale by rememberSaveable { mutableStateOf(false) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var cursorActive by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    // Crosshair position lives in its own State: the draw layer and the
    // readout card read it, so dragging never recomposes the screen.
    val cursorFraction = remember { mutableStateOf<Float?>(null) }

    val savedSpans by graph.settings.chartSpans.collectAsState(initial = emptyMap())
    var window by remember(metric) {
        mutableStateOf(ChartMetrics.startWindow(metric, emptyMap(), System.currentTimeMillis()))
    }
    // Экран открывается там, где его закрыли: окно — это ЧТО человек смотрит,
    // и переспрашивать об этом каждый раз незачем. Восстанавливается один раз,
    // после того как настройки прочитаны.
    var spanRestored by remember(metric) { mutableStateOf(false) }
    LaunchedEffect(metric, savedSpans) {
        if (spanRestored) return@LaunchedEffect
        if (metric.id !in savedSpans) return@LaunchedEffect
        spanRestored = true
        window = ChartMetrics.startWindow(metric, savedSpans, System.currentTimeMillis())
    }
    // Лестница следует за окном, а не наоборот: щипок меняет окно плавно, и
    // подсвеченный чип обязан говорить правду о том, что на экране.
    val periodIndex = ChartWindows.nearestPeriodIndex(window.spanMillis, periodIndices)
    val periodExact = ChartWindows.matchesPeriod(window.spanMillis, periodIndex)
    var snapshot by remember { mutableStateOf<ChartSnapshot?>(null) }

    // Live-follow: advance the right edge at the cadence at which a new column
    // can actually appear (1 s on short windows, at most 15 s on long ones) —
    // never faster than the display could show a difference.
    LaunchedEffect(follow, periodIndex) {
        while (follow) {
            delay(
                ChartWindows.refreshMillis(
                    ChartSeriesModel.bucketMillis(window.spanMillis),
                ),
            )
            window = ChartWindows.follow(window, System.currentTimeMillis())
        }
    }

    LaunchedEffect(graph, metric) {
        snapshotFlow { window }.collectLatest { w ->
            delay(RELOAD_DEBOUNCE_MILLIS)
            snapshot = withContext(Dispatchers.IO) { loadSnapshot(graph, w, metric) }
        }
    }

    // Keyed on the alert *flag*, not on the deviation snapshot: the engine
    // republishes that object every second and rebuilding the frame at 1 Hz
    // for an unchanged picture is exactly the waste this screen must avoid.
    val endpointAlert = follow && deviation.alertSince != null
    val frame = remember(
        snapshot, window, unit, logScale, thresholds, baseline, endpointAlert, metric,
    ) {
        snapshot?.let {
            buildFrame(
                snapshot = it,
                window = window,
                unit = unit,
                logScale = logScale,
                thresholds = thresholds,
                baseline = if (ChartMetrics.showsProfileBand(metric)) baseline else null,
                endpointAlert = endpointAlert,
                metric = metric,
            )
        }
    }

    fun selectPeriod(index: Int) {
        val span = ChartWindows.PERIODS[index].second
        window = ChartWindows.latest(span, System.currentTimeMillis())
        spanRestored = true
        settingsScope.launch { graph.settings.setChartSpan(metric.id, span) }
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
        // Щипок не должен выводить окно за пределы того, что величина умеет
        // показать честно: у счёта и жёсткости нет предагрегации длинных окон.
        val limit = ChartMetrics.maxSpanMillis(metric)
        if (w.spanMillis > limit) w = ChartWindows.latest(limit, minOf(w.toMillis, now))
        window = w
        val atEdge = ChartWindows.isAtLiveEdge(
            w,
            now,
            ChartSeriesModel.bucketMillis(w.spanMillis),
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
                    onResetScale = {
                        // §10: «оптимальный масштаб» — это выбранное окно у
                        // живого края; двойной тап отменяет зум и панораму,
                        // а не придумывает свой масштаб.
                        selectPeriod(periodIndex)
                    },
                    onCursorDismiss = {
                        val atEdge = ChartWindows.isAtLiveEdge(
                            window,
                            System.currentTimeMillis(),
                            ChartSeriesModel.bucketMillis(window.spanMillis),
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
                metricTitle = metric.title,
                stats = frame?.stats,
                paused = cursorActive,
                follow = follow,
                onBack = onBack,
                onInfo = { infoOpen = true },
                onJumpToNow = ::jumpToNow,
                onOpenDetails = { detailsOpen = true },
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
                    onSelectPeriod = ::selectPeriod,
                    onToggleScale = { logScale = !logScale },
                    availablePeriods = periodIndices,
                    periodExact = periodExact,
                    currentSpanLabel = HistoryFormat.duration(window.spanMillis / 1000),
                )
            }
            ChartDetailsSheet(
                open = detailsOpen,
                graph = graph,
                snapshot = snapshot,
                frame = frame,
                unit = unit,
                metric = metric,
                spanMillis = window.spanMillis,
                onClose = { detailsOpen = false },
            )
            ChartInfoSheet(
                open = infoOpen,
                metric = metric,
                frame = frame,
                baseline = baseline,
                logScale = logScale,
                onClose = { infoOpen = false },
            )
        }
        return
    }

    Box(Modifier.fillMaxSize().background(colors.bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
        PortraitTopBar(
            graph = graph,
            unit = unit,
            periodLabel = ChartWindows.PERIODS[periodIndex].first,
            metricTitle = metric.title,
            paused = cursorActive,
            follow = follow,
            onBack = onBack,
            onInfo = { infoOpen = true },
            onJumpToNow = ::jumpToNow,
            metric = metric,
        )
        chart(Modifier.weight(1f).fillMaxWidth())
        AppDivider()
        // Под графиком — ровно две строки: числа окна и управление. Всё
        // остальное (распределение, расширенная статистика, покрытие окна,
        // метод квантилей) открывается панелью поверх и не отнимает высоту у
        // самих данных: полноэкранный режим — это режим ПРОСМОТРА ГРАФИКА, а
        // не страница статистики с графиком наверху.
        WindowStatsLine(
            stats = frame?.stats,
            unit = unit,
            metric = metric,
            spanMillis = window.spanMillis,
            onOpenDetails = { detailsOpen = true },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.space2, vertical = Dimens.space1),
        ) {
            ControlChips(
                periodIndex = periodIndex,
                logScale = logScale,
                onSelectPeriod = ::selectPeriod,
                onToggleScale = { logScale = !logScale },
                availablePeriods = periodIndices,
                periodExact = periodExact,
                currentSpanLabel = HistoryFormat.duration(window.spanMillis / 1000),
            )
        }
        }
        ChartDetailsSheet(
            open = detailsOpen,
            graph = graph,
            snapshot = snapshot,
            frame = frame,
            unit = unit,
            metric = metric,
            spanMillis = window.spanMillis,
            onClose = { detailsOpen = false },
        )
        ChartInfoSheet(
            open = infoOpen,
            metric = metric,
            frame = frame,
            baseline = baseline,
            logScale = logScale,
            onClose = { infoOpen = false },
        )
    }
}

/**
 * Общая оболочка панелей поверх графика: затемнение, заголовок, крестик,
 * «назад» и потолок высоты.
 *
 * Панель не двигает график и не может съесть весь экран: под ней всегда видна
 * часть картинки, к которой она относится. Касание мимо панели закрывает её —
 * тем же движением, каким человек возвращается к графику.
 */
@Composable
private fun BoxScope.ChartSheet(
    open: Boolean,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * SHEET_MAX_FRACTION).dp
    BackHandler(enabled = open) { onClose() }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(Motion.fast()),
        exit = fadeOut(Motion.fast()),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(Motion.normal()) + expandVertically(Motion.springy()),
        exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.springy()),
        modifier = Modifier.align(Alignment.BottomStart),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .background(colors.surface)
                // Панель лежит ПОВЕРХ графика, поэтому забирает касания себе:
                // иначе прокрутка её содержимого двигала бы окно под ней.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(vertical = Dimens.space2)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space3),
            ) {
                Text(text = title.uppercase(), style = type.labelSmall, color = colors.ink2)
                Spacer(Modifier.weight(1f))
                Chip(text = "✕", color = colors.ink2, onClick = onClose)
            }
            content()
        }
    }
}

/** Панель никогда не занимает больше этой доли экрана — график остаётся виден. */
private const val SHEET_MAX_FRACTION = 0.72f

/**
 * Числа окна одной строкой: квантили, честное n и длительность окна.
 *
 * Раньше здесь стояла сетка из пяти ячеек, под ней строка-переключатель
 * «расширенная статистика», под ней ещё одна про распределение — три полосы
 * интерфейса, которые вместе съедали у графика заметную часть высоты ради
 * чисел, на которые смотрят вторыми. Теперь это одна строка, и она же —
 * дверь: нажатие открывает панель со всем остальным.
 */
@Composable
private fun WindowStatsLine(
    stats: WindowStats?,
    unit: DoseUnitSetting,
    metric: ChartMetric,
    spanMillis: Long,
    onOpenDetails: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenDetails,
            )
            .padding(horizontal = Dimens.space3, vertical = 9.dp),
    ) {
        val value = { v: Float -> ChartMetrics.format(metric, v, unit) }
        Text(
            text = if (stats == null) {
                "окно ${HistoryFormat.duration(spanMillis / 1000)}"
            } else {
                "P10 ${value(stats.p10)} · медиана ${value(stats.median)} · " +
                    "P90 ${value(stats.p90)} · n ${HistoryFormat.count(stats.sampleCount)} · " +
                    HistoryFormat.duration(spanMillis / 1000)
            },
            style = type.axis,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = "подробнее ▴", style = type.footnote, color = colors.muted)
    }
}

/**
 * Панель «подробнее»: распределение, расширенная статистика, покрытие окна и
 * метод квантилей — всё, что нужно по требованию и ничего, что нужно всегда.
 *
 * Панель лежит ПОВЕРХ графика и не двигает его: открыли, посмотрели, закрыли —
 * картинка под ней осталась на месте.
 */
@Composable
private fun BoxScope.ChartDetailsSheet(
    open: Boolean,
    graph: AppGraph,
    snapshot: ChartSnapshot?,
    frame: ChartFrame?,
    unit: DoseUnitSetting,
    metric: ChartMetric,
    spanMillis: Long,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    ChartSheet(
        open = open,
        title = "Окно ${HistoryFormat.duration(spanMillis / 1000)}",
        onClose = onClose,
    ) {
        // §2 ТЗ: неполное окно называется словами, а не остаётся пустым полем.
        coverageWording(frame?.stats, spanMillis)?.let { coverage ->
            Text(
                text = coverage,
                style = type.footnote,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = Dimens.space3),
            )
        }
        val histogram = frame?.histogram
        if (histogram != null) {
            Text(
                text = "Распределение",
                style = type.label,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = Dimens.space3),
            )
            DistributionStrip(histogram = histogram, labels = frame.histogramLabels)
        }
        Text(
            text = "Статистика окна",
            style = type.label,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = Dimens.space3),
        )
        ExpandedStats(stats = frame?.stats, unit = unit, metric = metric)
        QuantileDiagnosticPanel(graph = graph, snapshot = snapshot, unit = unit)
    }
}

/**
 * Справка «как читать график» — по кнопке «i» в шапке.
 *
 * Панель кладётся ПОВЕРХ экрана, а не раздвигает его: график не должен
 * прыгать от того, что человек открыл объяснение и закрыл его. Системная
 * «назад» закрывает справку, а не сам график, — «назад» здесь означает ровно
 * один шаг, как и везде.
 */
@Composable
private fun BoxScope.ChartInfoSheet(
    open: Boolean,
    metric: ChartMetric,
    frame: ChartFrame?,
    baseline: Baseline?,
    logScale: Boolean,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val sections = remember(metric, frame, baseline, logScale) {
        ChartInfo.sections(
            metric = metric,
            hasBaselineBand = baseline != null && ChartMetrics.showsProfileBand(metric),
            hasExtremeMarkers = frame?.spec?.extremeMarkers?.isNotEmpty() == true,
            hasEpisodes = frame?.spec?.episodes?.isNotEmpty() == true,
            method = frame?.stats?.method ?: QuantileMethod.EXACT_RAW,
            logScale = logScale,
            logDropped = frame?.logDropped ?: 0,
        )
    }
    ChartSheet(open = open, title = "Как читать этот график", onClose = onClose) {
        for (section in sections) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = Dimens.space3),
            ) {
                Text(text = section.title, style = type.label, color = colors.ink)
                for (line in section.lines) {
                    Text(text = line, style = type.bodySmall, color = colors.muted)
                }
            }
        }
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
    showValue: Boolean = true,
    compact: Boolean = false,
    metric: ChartMetric = ChartMetric.DOSE,
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
    if (showValue) {
        // Живое значение — та же величина, что на графике: смотреть на дозу
        // над графиком счёта было бы двумя разными числами в одной шапке.
        val value = sample?.let { row ->
            when (metric) {
                ChartMetric.DOSE -> DoseUnits.rawToMicroSievertPerHour(row.doseRate)
                ChartMetric.COUNT_RATE -> row.countRate
                ChartMetric.HARDNESS -> Hardness.of(
                    doseRateMicroSvH = DoseUnits.rawToMicroSievertPerHour(row.doseRate).toDouble(),
                    countRate = row.countRate.toDouble(),
                    seconds = Hardness.MIN_COUNTS / row.countRate.coerceAtLeast(0.01f),
                )?.value?.toFloat()
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value?.let { ChartMetrics.format(metric, it, unit) } ?: "—",
                style = if (compact) type.value else type.valueLarge,
                color = if (value == null || freshness !is Freshness.Fresh) colors.muted
                else colors.ink,
            )
            Text(
                text = listOfNotNull(
                    if (metric == ChartMetric.DOSE) {
                        Uncertainty.errPercentLabel(sample?.doseRateErr)
                    } else {
                        null
                    },
                    ChartMetrics.unitLabel(metric, unit),
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
    metricTitle: String,
    paused: Boolean,
    follow: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onJumpToNow: () -> Unit,
    metric: ChartMetric = ChartMetric.DOSE,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column {
        // Одна строка вместо блока: в полноэкранном режиме высота — это данные,
        // и шапка забирает у графика ровно столько, сколько нужно, чтобы
        // сказать, что показано, чему оно сейчас равно и как отсюда выйти.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.space2, vertical = Dimens.space1),
        ) {
            Chip(text = "✕", color = colors.ink2, onClick = onBack)
            Text(
                text = "$metricTitle · $periodLabel".uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val freshness = liveReading(graph, unit, compact = true, metric = metric)
            FreshnessOrPause(freshness, paused)
            NowChip(follow, onJumpToNow)
            Chip(text = "i", color = colors.ink2, onClick = onInfo)
        }
        AppDivider()
    }
}

/**
 * «⌖ сейчас» — состояние, а не только действие: подсвечен, когда график стоит
 * у живого края и сам едет за ним. Отодвинули окно — подсветка гаснет, и
 * нажатие возвращает к «сейчас». Раньше было наоборот: горело именно тогда,
 * когда график НЕ следил за временем.
 */
@Composable
private fun NowChip(follow: Boolean, onJumpToNow: () -> Unit) {
    val colors = LocalAppColors.current
    Chip(
        text = "⌖ сейчас",
        color = if (follow) colors.dataText else colors.ink2,
        selected = follow,
        onClick = onJumpToNow,
    )
}

@Composable
private fun FreshnessOrPause(freshness: Freshness, paused: Boolean) {
    val colors = LocalAppColors.current
    if (paused) {
        Chip(text = "пауза", color = colors.warn, selected = true)
        return
    }
    // Идущий поток чипа не заслуживает — заслуживает отставший.
    val label = freshnessChipLabel(freshness) ?: return
    when (freshness) {
        is Freshness.Stale, is Freshness.Fresh -> Chip(text = label, color = colors.warn)
        Freshness.NoData -> Chip(text = label, color = colors.muted)
    }
}

@Composable
private fun BoxScope.LandscapeTopBar(
    graph: AppGraph,
    unit: DoseUnitSetting,
    periodLabel: String,
    metricTitle: String,
    stats: WindowStats?,
    paused: Boolean,
    follow: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onJumpToNow: () -> Unit,
    onOpenDetails: () -> Unit,
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
            text = "$metricTitle · $periodLabel".uppercase(),
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
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenDetails,
                ),
            )
        }
        val freshness = liveReading(graph, unit, showValue = false)
        FreshnessOrPause(freshness, paused)
        NowChip(follow, onJumpToNow)
        Chip(text = "i", color = colors.ink2, onClick = onInfo)
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
    onSelectPeriod: (Int) -> Unit,
    onToggleScale: () -> Unit,
    availablePeriods: List<Int> = ChartWindows.PERIODS.indices.toList(),
    periodExact: Boolean = true,
    /** Фактическое окно словами — для свёрнутого чипа между ступенями. */
    currentSpanLabel: String = "",
) {
    val colors = LocalAppColors.current
    // Лестница из пятнадцати ступеней постоянно на экране съедала место и
    // требовала прокрутки ради одного нажатия. Свёрнутая она — один чип с
    // текущим окном; развёрнутая показывает ряд и прячется сразу после
    // выбора. Между ступенями чип называет фактическое окно, а не ближайшую
    // ступень: подсказка обязана говорить правду о том, что на экране.
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(periodIndex, periodExact) {
        if (expanded && periodExact) expanded = false
    }
    if (!expanded) {
        Chip(
            text = (if (periodExact) ChartWindows.PERIODS[periodIndex].first else currentSpanLabel) +
                " ▾",
            color = colors.ink,
            selected = true,
            onClick = { expanded = true },
        )
        Spacer(Modifier.weight(1f))
    } else {
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        LaunchedEffect(periodIndex, availablePeriods.size) {
            val target = ChartWindows.scrollTargetIndex(availablePeriods.indexOf(periodIndex))
            val offsetPx = with(density) { (target * CHIP_STEP_DP).dp.roundToPx() }
            scroll.animateScrollTo(offsetPx)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
        ) {
            for (index in availablePeriods) {
                // Точное совпадение — выбранный чип; между ступенями (после
                // щипка) ближайший просто ярче: «вы примерно здесь», но окно
                // не равно ступени, и притворяться иначе нельзя.
                val exact = index == periodIndex && periodExact
                val nearest = index == periodIndex && !periodExact
                Chip(
                    text = ChartWindows.PERIODS[index].first,
                    color = if (exact || nearest) colors.ink else colors.ink2,
                    selected = exact,
                    onClick = {
                        onSelectPeriod(index)
                        expanded = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.width(Dimens.space1))
    // Правило на всю панель: подсвечен = названное состояние ВКЛЮЧЕНО.
    // Название у чипа постоянное («лог»), иначе подсветка ничего не значила
    // бы — надпись и так меняла бы смысл под ней.
    Chip(
        text = "лог",
        color = if (logScale) colors.dataText else colors.ink2,
        selected = logScale,
        onClick = onToggleScale,
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
    metric: ChartMetric = ChartMetric.DOSE,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val unitLabel = ChartMetrics.unitLabel(metric, unit)
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
    snapshot: ChartSnapshot?,
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
