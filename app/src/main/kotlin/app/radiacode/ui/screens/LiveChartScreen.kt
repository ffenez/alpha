package app.radiacode.ui.screens

import android.content.res.Configuration
import app.radiacode.ui.logic.ChartInfo
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.onSizeChanged
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
import app.radiacode.ui.components.ChartSheet
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.DistributionStrip
import app.radiacode.ui.components.DoseChart
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.chart.ChartDataSource
import app.radiacode.ui.chart.Viewport
import app.radiacode.ui.chart.ViewportBounds
import app.radiacode.ui.chart.Viewports
import app.radiacode.ui.logic.ChartBucket
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.DoseScale
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.analysis.Hardness
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.logic.ChartDetailMode
import app.radiacode.ui.logic.ChartMetrics
import app.radiacode.ui.logic.ChartRange
import app.radiacode.ui.logic.ChartRanges
import app.radiacode.ui.logic.CursorReadout
import app.radiacode.ui.logic.coverageWording
import app.radiacode.ui.logic.ChartSeriesModel
import app.radiacode.ui.logic.DoseExtremes
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.DoseHistograms
import app.radiacode.ui.logic.DoseReference
import app.radiacode.ui.logic.ChartSnapshot
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.freshnessChipLabel
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.QuantileMetadata
import app.radiacode.ui.logic.QuantileMethod
import app.radiacode.ui.logic.RatioDenominator
import app.radiacode.ui.logic.markerWording
import app.radiacode.ui.logic.referenceWording
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.WindowStats
import app.radiacode.ui.text.ChartAxisCatalogue
import app.radiacode.ui.text.ChartTextCatalogue
import app.radiacode.ui.text.ChartTextStrings
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.Strings
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

/** Ширина шага ленты периодов — чип плюс интервал; для авто-прокрутки. */
/** Доезд до «сейчас»: достаточно, чтобы проследить глазом, и не тормозит. */
/** Пауза, после которой ось пересчитывается: движение улеглось. */
private const val SCALE_SETTLE_MILLIS = 120L

private const val EDGE_ANIMATION_MILLIS = 220L
private const val EDGE_ANIMATION_STEPS = 11

/** Дальше этого числа окон возврат не едет, а переносится. */
private const val FAR_JUMP_SPANS = 6

private const val CHIP_STEP_DP = 52

internal val CURSOR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Правый край исторического диапазона — только время: день назван слева. */
private val RANGE_TIME = DateTimeFormatter.ofPattern("HH:mm")

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
 *
 * **Исторический режим** ([range] ≠ null, вход — деталка сессии). Экран тот
 * же, меняется только КРАЙ ВРЕМЕНИ: окно открывается по диапазону сессии,
 * жесты упираются в её конец, а не в «сейчас», чип «⌖ сейчас» становится
 * «⌖ сессия» и возвращает к полному диапазону, живого значения в шапке нет —
 * на его месте сам диапазон и его длительность. Отдельного экрана для истории
 * не существует намеренно: две реализации одного графика неизбежно разошлись
 * бы математикой, и человек искал бы в истории то, что видел на живом.
 */
@Composable
fun LiveChartScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    metric: ChartMetric = ChartMetric.DOSE,
    /**
     * Фиксированный диапазон (сессия из Истории) вместо живого края.
     *
     * Экран один и тот же намеренно: два графика с разной математикой уже
     * однажды разошлись между Главной и полным экраном, и человек искал на
     * большом графике то, что видел на маленьком. Здесь меняется только КРАЙ —
     * жесты, конверты, курсор, маркеры и статистика остаются те же.
     */
    range: ChartRange? = null,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val settingsScope = rememberCoroutineScope()
    val type = LocalAppTypography.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    val metricTitle = ChartMetrics.title(metric, LocalStrings.current)
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
    // Исторический режим: край времени — конец сессии, а не «сейчас».
    val historical = !ChartRanges.followsLiveEdge(range)
    val maxSpan = ChartMetrics.maxSpanMillis(metric)
    var logScale by rememberSaveable { mutableStateOf(false) }
    // Метки кратковременных отклонений: выключены, пока их не попросят.
    var showEvents by rememberSaveable { mutableStateOf(false) }
    // Вид живого графика — настройка, а не состояние экрана: карточка Главной
    // и полноэкранный график это два размера одной картинки.
    val detailId by graph.settings.chartDetailModeId
        .collectAsState(initial = ChartDetailMode.DEFAULT.id)
    val detail = remember(detailId) { ChartDetailMode.of(detailId) }
    var cursorActive by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    // Crosshair position lives in its own State: the draw layer and the
    // readout card read it, so dragging never recomposes the screen.
    val cursorFraction = remember { mutableStateOf<Float?>(null) }

    val savedSpans by graph.settings.chartSpans.collectAsState(initial = emptyMap())
    // Начало истории — левый предел жестов: дальше первого измерения ехать
    // некуда. Спрашивается один раз: меняется оно первой записью и уборкой
    // журнала, а не движением пальца.
    var earliestMillis by remember { mutableStateOf<Long?>(null) }
    // Состояние графика — само ОКНО, а не ступень лестницы (Charts V2 §3):
    // после щипка окно может быть каким угодно, и график в нём остаётся.
    var viewport by remember(metric, range) {
        mutableStateOf(
            if (range != null) {
                val initial = ChartRanges.initialWindow(range, maxSpan)
                Viewport(initial.fromMillis, initial.toMillis, followLiveEdge = false)
            } else {
                val initial = ChartMetrics.startWindow(metric, emptyMap(), System.currentTimeMillis())
                Viewport(initial.fromMillis, initial.toMillis, followLiveEdge = true)
            },
        )
    }
    val window = viewport.window()
    val follow = viewport.followLiveEdge

    /** Границы, в которых окну разрешено двигаться, на текущий момент. */
    fun bounds(nowMillis: Long = System.currentTimeMillis()) = ViewportBounds(
        edgeMillis = ChartRanges.edgeMillis(range, nowMillis),
        // У исторического графика левый предел — начало сессии: за ним лежит
        // не «нет данных», а другая запись, к этому экрану не относящаяся.
        earliestMillis = range?.fromMillis ?: earliestMillis,
        maxSpanMillis = maxSpan,
    )

    /** Историческому окну живой край не принадлежит: слежению там неоткуда взяться. */
    fun setViewport(next: Viewport) {
        viewport = if (historical) next.copy(followLiveEdge = false) else next
    }

    // Экран открывается там, где его закрыли: окно — это ЧТО человек смотрит,
    // и переспрашивать об этом каждый раз незачем. Восстанавливается один раз,
    // после того как настройки прочитаны.
    var spanRestored by remember(metric) { mutableStateOf(false) }
    LaunchedEffect(metric, savedSpans, historical) {
        val earliest = withContext(Dispatchers.IO) {
            graph.measurementRepository.earliestSampleMillis()
        }
        earliestMillis = earliest
        // Окно исторического графика задано диапазоном сессии: запомненное
        // окно живого экрана к нему отношения не имеет.
        if (historical) return@LaunchedEffect
        if (spanRestored) return@LaunchedEffect
        spanRestored = true
        val now = System.currentTimeMillis()
        // Ступень — максимум, а не обещание, что данные за неё есть: окно
        // открытия подтягивается к первому измерению. Только ОТКРЫТИЕ: дальше
        // окном распоряжаются жесты, и подтягивать его на каждом щипке значило
        // бы отбирать у человека управление.
        val start = ChartWindows.limitedByHistory(
            ChartMetrics.startWindow(metric, savedSpans, now),
            earliest,
        )
        viewport = Viewport(start.fromMillis, start.toMillis, followLiveEdge = true)
    }
    // Лестница следует за окном, а не наоборот: щипок меняет окно плавно, и
    // подсвеченный чип обязан говорить правду о том, что на экране.
    val periodIndex = ChartWindows.nearestPeriodIndex(window.spanMillis, periodIndices)
    val periodExact = ChartWindows.matchesPeriod(window.spanMillis, periodIndex)
    var snapshot by remember { mutableStateOf<ChartSnapshot?>(null) }

    // Live-follow: advance the right edge at the cadence at which a new column
    // can actually appear (1 s on short windows, at most 15 s on long ones) —
    // never faster than the display could show a difference.
    LaunchedEffect(follow, window.spanMillis, historical) {
        while (follow && !historical) {
            delay(
                ChartWindows.refreshMillis(
                    ChartSeriesModel.bucketMillis(window.spanMillis),
                ),
            )
            viewport = Viewports.followTick(viewport, bounds())
        }
    }

    LaunchedEffect(graph, metric) {
        snapshotFlow { viewport.window() }.collectLatest { w ->
            delay(ChartDataSource.RELOAD_DEBOUNCE_MILLIS)
            snapshot = withContext(Dispatchers.IO) { loadSnapshot(graph, w, metric) }
        }
    }

    // Ось замирает на время жеста и оживает, когда движение улеглось.
    var interacting by remember { mutableStateOf(false) }
    var lastGestureAt by remember { mutableLongStateOf(0L) }
    var heldScale by remember(metric) { mutableStateOf<DoseScale?>(null) }
    LaunchedEffect(lastGestureAt) {
        if (lastGestureAt == 0L) return@LaunchedEffect
        delay(SCALE_SETTLE_MILLIS)
        interacting = false
        heldScale = null
    }

    // Keyed on the alert *flag*, not on the deviation snapshot: the engine
    // republishes that object every second and rebuilding the frame at 1 Hz
    // for an unchanged picture is exactly the waste this screen must avoid.
    val endpointAlert = follow && deviation.alertSince != null
    val axisStrings = ChartAxisCatalogue.of(LocalStrings.current.language)
    // Ширина поля решает, сколько колонок имеет смысл рисовать: больше, чем
    // видно пикселей, — работа впустую на каждом кадре жеста; меньше — грубая
    // картинка там, где данные подробнее ([ChartDownsampler]).
    var plotWidthPx by remember { mutableStateOf(0f) }
    val frame = remember(
        snapshot, window, unit, logScale, thresholds, baseline, endpointAlert, metric, follow,
        detail, showEvents, heldScale,
        axisStrings, plotWidthPx,
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
                // Ось от «сейчас» — только когда график ДЕРЖИТСЯ живого края.
                // Отъехали жестом или смотрим сохранённый диапазон — правый
                // край уже не текущий момент, и подпись «сейчас» соврала бы.
                nowMillis = window.toMillis.takeIf { range == null && follow },
                axisStrings = axisStrings,
                detail = detail,
                showEvents = showEvents,
                scaleOverride = heldScale,
                plotWidthPx = plotWidthPx,
            )
        }
    }

    fun selectPeriod(index: Int) {
        val span = ChartWindows.PERIODS[index].second
        setViewport(Viewports.withSpan(viewport, span, bounds()))
        // Запомненное окно принадлежит ЖИВОМУ экрану (и карточке Главной):
        // выбор ступени при разглядывании прошлой сессии не должен переставлять
        // то, что человек смотрит на Главной.
        if (!historical) {
            spanRestored = true
            settingsScope.launch { graph.settings.setChartSpan(metric.id, span) }
        }
        cursorActive = false
        cursorFraction.value = null
    }

    /** «⌖ сейчас» на живом графике, «⌖ сессия» — на историческом. */
    var edgeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    /**
     * Возврат к «сейчас» — доездом, а не телепортом.
     *
     * Мгновенная подмена окна не читается как перемещение: картинка просто
     * становится другой, и глазу нечем связать то, что было, с тем, что стало.
     * Короткий проезд (около 220 мс) показывает, КУДА уехало окно, и человек
     * не теряет место, откуда он смотрел.
     *
     * Едет ВРЕМЯ окна, а не пиксели: длительность окна сохраняется, меняется
     * только его правый край. Данные при этом не анимируются — двигается
     * представление (V2 §8).
     */
    fun jumpToEdge() {
        cursorActive = false
        cursorFraction.value = null
        val target = if (range != null) {
            val full = ChartRanges.initialWindow(range, maxSpan)
            Viewport(full.fromMillis, full.toMillis, followLiveEdge = false)
        } else {
            Viewports.jumpToEdge(viewport, bounds())
        }
        val from = viewport
        val distance = kotlin.math.abs(target.endMillis - from.endMillis)
        // Далёкий возврат не «летит» через часы истории: доезд имеет смысл,
        // пока глаз успевает проследить, иначе это просто медленный телепорт.
        if (distance > from.spanMillis * FAR_JUMP_SPANS) {
            setViewport(target)
            return
        }
        edgeJob?.cancel()
        edgeJob = settingsScope.launch {
            val steps = EDGE_ANIMATION_STEPS
            for (step in 1..steps) {
                val fraction = step.toFloat() / steps
                val eased = 1f - (1f - fraction) * (1f - fraction)
                val to = from.endMillis + ((target.endMillis - from.endMillis) * eased).toLong()
                // Едет и длина окна: «⌖ сессия» возвращает к полному диапазону,
                // и подменять его в последнем кадре значило бы прыжок в конце
                // плавного движения.
                val span = from.spanMillis +
                    ((target.spanMillis - from.spanMillis) * eased).toLong()
                setViewport(Viewport(to - span, to, followLiveEdge = false))
                delay(EDGE_ANIMATION_MILLIS / steps)
            }
            setViewport(target)
        }
    }

    val onTransform: (Float, Float, Float) -> Unit = { pan, zoom, focus ->
        if (!interacting) heldScale = frame?.spec?.scale
        interacting = true
        lastGestureAt = System.currentTimeMillis()
        val b = bounds()
        var v = viewport
        // Зум НЕПРЕРЫВНЫЙ (Charts V2 §5.3): множитель кадра идёт прямо в окно,
        // а точка под пальцами остаётся на месте. Прежний ступенчатый зум
        // копил множители до полутора раз и переключал ступень целиком — между
        // ступенями показать было нечего, и щипок ощущался как рывок.
        if (zoom != 1f) v = Viewports.zoom(v, zoom, focus, b)
        // Тянут вправо — в кадр приходит более раннее время.
        if (pan != 0f) v = Viewports.pan(v, -pan, b)
        setViewport(v)
        if (cursorActive) {
            cursorActive = false
            cursorFraction.value = null
        }
    }

    val chart: @Composable (Modifier) -> Unit = { chartModifier ->
        Box(chartModifier.onSizeChanged { plotWidthPx = it.width.toFloat() }) {
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
                        // Видимый курсор останавливает слежение: иначе ряд
                        // уезжал бы из-под показания, которое разглядывают.
                        viewport = viewport.copy(followLiveEdge = false)
                        cursorFraction.value = fraction
                    },
                    onResetScale = {
                        // §10: «оптимальный масштаб» — это выбранное окно у
                        // живого края; двойной тап отменяет зум и панораму,
                        // а не придумывает свой масштаб.
                        selectPeriod(periodIndex)
                    },
                    onCursorDismiss = {
                        cursorActive = false
                        cursorFraction.value = null
                        // Слежение возобновляется, только если окно всё ещё
                        // стоит у живого края: уведённый в прошлое график
                        // остаётся там, где его оставили.
                        setViewport(Viewports.clamp(viewport, bounds()))
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
                    eventTimesMillis = snapshot?.eventTimesMillis.orEmpty(),
                )
            }
            if (f == null || f.spec.buckets.isEmpty()) {
                Text(
                    text = if (snapshot == null) t.loadingLog else t.emptyWindow,
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
                metricTitle = metricTitle,
                stats = frame?.stats,
                paused = cursorActive,
                follow = follow,
                onBack = onBack,
                onInfo = { infoOpen = true },
                onJumpToEdge = ::jumpToEdge,
                onOpenDetails = { detailsOpen = true },
                range = range,
                atRange = range != null && ChartRanges.atFullRange(window, range, maxSpan),
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
                    detailed = detail == ChartDetailMode.DETAILED,
                    onToggleDetail = {
                        settingsScope.launch {
                            graph.settings.setChartDetailMode(
                                if (detail == ChartDetailMode.DETAILED) {
                                    ChartDetailMode.SMOOTHED.id
                                } else {
                                    ChartDetailMode.DETAILED.id
                                },
                            )
                        }
                    },
                    events = showEvents,
                    onToggleEvents = { showEvents = !showEvents },
                    onOpenDetails = { detailsOpen = true },
                    availablePeriods = periodIndices,
                    periodExact = periodExact,
                    currentSpanLabel = HistoryFormat.duration(window.spanMillis / 1000, s = h),
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
                historical = historical,
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
            metricTitle = metricTitle,
            paused = cursorActive,
            follow = follow,
            onBack = onBack,
            onInfo = { infoOpen = true },
            onJumpToEdge = ::jumpToEdge,
            metric = metric,
            range = range,
            atRange = range != null && ChartRanges.atFullRange(window, range, maxSpan),
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
                detailed = detail == ChartDetailMode.DETAILED,
                onToggleDetail = {
                    settingsScope.launch {
                        graph.settings.setChartDetailMode(
                            if (detail == ChartDetailMode.DETAILED) {
                                ChartDetailMode.SMOOTHED.id
                            } else {
                                ChartDetailMode.DETAILED.id
                            },
                        )
                    }
                },
                events = showEvents,
                onToggleEvents = { showEvents = !showEvents },
                onOpenDetails = { detailsOpen = true },
                availablePeriods = periodIndices,
                periodExact = periodExact,
                currentSpanLabel = HistoryFormat.duration(window.spanMillis / 1000, s = h),
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
            historical = historical,
            onClose = { infoOpen = false },
        )
    }
}

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
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.space3, vertical = 9.dp),
    ) {
        val value = { v: Float -> ChartMetrics.format(metric, v, unit) }
        Text(
            text = if (stats == null) {
                t.windowLabel(HistoryFormat.duration(spanMillis / 1000, s = h))
            } else {
                t.windowStatsLine(
                    p10 = value(stats.p10),
                    median = value(stats.median),
                    p90 = value(stats.p90),
                    samples = HistoryFormat.count(stats.sampleCount),
                    duration = HistoryFormat.duration(spanMillis / 1000, s = h),
                )
            },
            style = type.axis,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

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
    val strings = LocalStrings.current
    val axis = ChartAxisCatalogue.of(strings.language)
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
            // Рядом с числом не осталось ничего: ни погрешности, ни единицы.
            // Величина названа заголовком шапки, порядок виден по оси значений,
            // а разбор — в «подробнее». Три подписи вокруг одного числа
            // складывались в шум, который перестаёшь читать.
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
    onJumpToEdge: () -> Unit,
    metric: ChartMetric = ChartMetric.DOSE,
    /** Исторический диапазон: живого значения у прошлого нет. */
    range: ChartRange? = null,
    atRange: Boolean = false,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (range == null) {
                val freshness = liveReading(graph, unit, compact = true, metric = metric)
                FreshnessOrPause(freshness, paused)
            } else {
                // У прошлого нет «сейчас»: на месте живого значения стоит то,
                // что это за отрезок времени.
                RangeLabel(range, compact = true)
                if (paused) FreshnessOrPause(Freshness.NoData, paused = true)
            }
            EdgeChip(range = range, follow = follow, atRange = atRange, onClick = onJumpToEdge)
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
private fun EdgeChip(
    range: ChartRange?,
    follow: Boolean,
    atRange: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    // У исторического графика «сейчас» не значит ничего: живой край относится
    // к другому времени. Чип возвращает к диапазону сессии, а подсветка
    // подчиняется тому же правилу — горит названное состояние.
    val selected = if (range == null) follow else atRange
    Chip(
        text = if (range == null) t.nowChip else t.sessionChip,
        color = if (selected) colors.dataText else colors.ink2,
        selected = selected,
        onClick = onClick,
    )
}

/**
 * Диапазон исторического графика словами: когда и сколько это длилось.
 *
 * В портрете шапка — одна строка на весь экран, и полный «14:03–16:18 · 2 ч
 * 15 мин» съедал бы название величины: там печатается начало и длительность,
 * конец из них следует. В ландшафте место есть, и стоит весь отрезок.
 */
@Composable
private fun RangeLabel(range: ChartRange, compact: Boolean = false) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val t = ChartTextCatalogue.of(strings.language)
    val now = System.currentTimeMillis()
    Text(
        text = t.sessionRangeLabel(
            range = HistoryFormat.dayTime(range.fromMillis, now, s = h) + if (compact) {
                ""
            } else {
                "–" + Instant.ofEpochMilli(range.toMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(RANGE_TIME)
            },
            duration = HistoryFormat.duration(range.spanMillis / 1000, s = h),
        ),
        style = type.footnote,
        color = colors.ink2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FreshnessOrPause(freshness: Freshness, paused: Boolean) {
    val colors = LocalAppColors.current
    if (paused) {
        Chip(
            text = ChartTextCatalogue.of(LocalStrings.current.language).pausedChip,
            color = colors.warn,
            selected = true,
        )
        return
    }
    // Идущий поток чипа не заслуживает — заслуживает отставший.
    val label = freshnessChipLabel(freshness, LocalStrings.current) ?: return
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
    onJumpToEdge: () -> Unit,
    onOpenDetails: () -> Unit,
    range: ChartRange? = null,
    atRange: Boolean = false,
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (range != null) RangeLabel(range)
        Spacer(Modifier.weight(1f))
        stats?.let {
            Text(
                text = landscapeStatsLine(
                    it,
                    unit,
                    ChartTextCatalogue.of(LocalStrings.current.language),
                    LocalStrings.current,
                ),
                style = type.footnote,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenDetails,
                ),
            )
        }
        if (range == null) {
            val freshness = liveReading(graph, unit, showValue = false)
            FreshnessOrPause(freshness, paused)
        } else if (paused) {
            FreshnessOrPause(Freshness.NoData, paused = true)
        }
        EdgeChip(range = range, follow = follow, atRange = atRange, onClick = onJumpToEdge)
        Chip(text = "i", color = colors.ink2, onClick = onInfo)
    }
}

private fun landscapeStatsLine(
    stats: WindowStats,
    unit: DoseUnitSetting,
    t: ChartTextStrings,
    strings: Strings,
): String = listOf(
    "P10 ${DoseFormat.rate(stats.p10, unit)}",
    "${t.median} ${DoseFormat.rate(stats.median, unit)}",
    "P90 ${DoseFormat.rate(stats.p90, unit)}",
    "MAD ${DoseFormat.rate(stats.mad, unit)}",
    "SD ${DoseFormat.rate(stats.sd, unit)} ${DoseFormat.rateUnitLabel(unit, s = strings)}",
    "n ${HistoryFormat.count(stats.sampleCount)}",
).joinToString(" · ")

// --- controls -------------------------------------------------------------

@Composable
private fun RowScope.ControlChips(
    periodIndex: Int,
    logScale: Boolean,
    detailed: Boolean,
    events: Boolean,
    onSelectPeriod: (Int) -> Unit,
    onToggleScale: () -> Unit,
    onToggleDetail: () -> Unit,
    onToggleEvents: () -> Unit,
    onOpenDetails: () -> Unit,
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
    val texts = ChartTextCatalogue.of(LocalStrings.current.language)
    val strings = LocalStrings.current
    Chip(
        text = texts.logChip,
        color = if (logScale) colors.dataText else colors.ink2,
        selected = logScale,
        onClick = onToggleScale,
    )
    Spacer(Modifier.width(Dimens.space1))
    // Остальное — под «⋯».
    //
    // Шесть равнозначных чипов стояли в строке постоянно, хотя сглаживание,
    // события и разбор трогают раз в сеанс, а место они занимали всегда — и
    // отнимали его у самого графика. Видимыми остались те два, которыми
    // управляют по ходу чтения: окно и вид шкалы (V2 §16).
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Chip(text = "⋯", color = colors.ink2, onClick = { menuOpen = true })
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        texts.smoothChip + " · " + if (detailed) strings.off else strings.on,
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleDetail()
                },
            )
            DropdownMenuItem(
                text = { Text(texts.eventsChip + " · " + if (events) strings.on else strings.off) },
                onClick = {
                    menuOpen = false
                    onToggleEvents()
                },
            )
            DropdownMenuItem(
                text = { Text(texts.moreDetails) },
                onClick = {
                    menuOpen = false
                    onOpenDetails()
                },
            )
        }
    }
}
