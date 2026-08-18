package app.alpha.ui.screens

import android.content.res.Configuration
import app.alpha.ui.logic.ChartInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.layout.onSizeChanged
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
import app.alpha.ui.components.AppCloseButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.AppMenu
import app.alpha.ui.components.AppMenuDivider
import app.alpha.ui.components.AppMenuHeader
import app.alpha.ui.components.AppMenuItem
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartSheet
import app.alpha.ui.components.Chip
import app.alpha.ui.components.DistributionStrip
import app.alpha.ui.components.DoseChart
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.chart.ChartDataSource
import app.alpha.ui.chart.ChartContext
import app.alpha.ui.chart.ChartGesture
import app.alpha.ui.chart.ChartGestureInput
import app.alpha.ui.chart.GestureAxis
import app.alpha.ui.chart.ValueWindow
import app.alpha.ui.chart.ChartYAxis
import app.alpha.ui.chart.Viewport
import app.alpha.ui.chart.ViewportBounds
import app.alpha.ui.chart.Viewports
import app.alpha.ui.logic.ChartBucket
import app.alpha.ui.logic.LocalBackground
import app.alpha.ui.logic.ChartWindow
import app.alpha.ui.logic.DoseScale
import app.alpha.ui.components.rememberFrameMillis
import app.alpha.ui.logic.ChartWindows
import app.alpha.ui.logic.LiveEdge
import app.alpha.analysis.Hardness
import app.alpha.ui.logic.ChartMetric
import app.alpha.ui.logic.ChartDetailMode
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

/** Ширина шага ленты периодов — чип плюс интервал; для авто-прокрутки. */
/** Доезд до «сейчас»: достаточно, чтобы проследить глазом, и не тормозит. */
/** Пауза, после которой ось пересчитывается: движение улеглось. */
private const val SCALE_SETTLE_MILLIS = 120L

private const val EDGE_ANIMATION_MILLIS = 220L
private const val EDGE_ANIMATION_STEPS = 11

/** Дальше этого числа окон возврат не едет, а переносится. */
private const val FAR_JUMP_SPANS = 6

/**
 * Ступеней в строке поповера выбора окна.
 * **Инженерный параметр**: три — при пятнадцати ступенях это пять строк,
 * которые целиком помещаются над панелью и не требуют прокрутки.
 */
private const val PICKER_COLUMNS = 3

/**
 * Во сколько раз растягивается ось за один проход пальца по шкале сверху вниз.
 * **Инженерный параметр**: полный проход по высоте поля меняет размах примерно
 * вдвое — движение видно сразу, но кадр не улетает от одного касания.
 */
private const val VALUE_SCALE_SENSITIVITY = 1f

internal val CURSOR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Правый край исторического диапазона — только время: день назван слева. */
private val RANGE_TIME = DateTimeFormatter.ofPattern("HH:mm")

/** Default period on open — long enough to show a shape, short enough to load fast. */

/**
 * Полноэкранный график мощности дозы (тап по карточке Монитора).
 *
 * **Раскладка.** Шапка (закрыть · заголовок · живое значение с погрешностью ·
 * чип свежести) → график на всю оставшуюся высоту → полоса распределения
 * значений окна → статистика окна (P10 · медиана · P90 · n · окно, спец §13) →
 * раскрываемая расширенная статистика → ряд управления → строка анатомии
 * графика. В ландшафте график занимает весь экран, статистика сжимается в
 * моно-строку шапки.
 *
 * **Производительность.** Один запрос в БД на смену окна с запасом по
 * четверти окна с каждой стороны ([ChartWindows.loadRange]); pan/pinch
 * перепроецируют неизменяемый снимок; повторное чтение — через
 * [RELOAD_DEBOUNCE_MILLIS] после жеста. Живое значение — отдельный composable
 * со своим тикером, поэтому поток 1 Гц не перерисовывает график.
 *
 * **Достоверность (SPEC §2, спец графика §6/§7).** Линия — медиана колонки
 * (Q50), заливки — квантильные конверты Q25–Q75 и Q10–Q90, то есть
 * наблюдаемый разброс измерений, не погрешность и не доверительный интервал.
 * Мин/макс колонки не заливаются полосой (экстремум растёт с числом
 * отсчётов): значимые экстремумы помечаются маркерами. Серая полоса —
 * исторический P10–P90 профиля, статистика места, а не норматив. Эпизоды
 * берут время из журнала событий, длительность считается по колонкам и
 * названа относительно своего порога.
 *
 * **Исторический режим** ([range] ≠ null): тот же экран с другим КРАЕМ
 * ВРЕМЕНИ — окно открывается по диапазону сессии, жесты упираются в её конец,
 * чип возврата называется «⌖ сессия», живого значения в шапке нет.
 */
@Composable
fun LiveChartScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    metric: ChartMetric = ChartMetric.DOSE,
    /**
     * Откуда открыт график: живой край, сессия, маршрут или Поиск. От контекста
     * зависят край времени, подпись чипа возврата и то, с чем сравнивает
     * курсор (ТЗ §22); жесты, конверты, маркеры и статистика общие.
     */
    context: ChartContext = ChartContext.Live,
) {
    val range = context.range
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

    // Пока график открыт, экран не гаснет. Флаг живёт на View этого экрана и
    // снимается вместе с ним.
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
    // Состояние графика — само ОКНО, а не ступень лестницы (Charts V2 §3), и
    // окна ДВА: то, для которого посчитан кадр, и то, в которое уехал жест
    // ([ChartGesture]). Пока они различаются, экран двигает готовую картинку.
    var gesture by remember(metric, range) {
        val startBounds = ViewportBounds(
            edgeMillis = ChartRanges.edgeMillis(range, System.currentTimeMillis()),
            earliestMillis = range?.fromMillis,
            maxSpanMillis = maxSpan,
        )
        // Живой график и карточка Главной делят одно окно
        // (`ChartCache.gestures`), поэтому тап увеличивает ту же картинку
        // (Charts V2 §20).
        val shared = graph.chartCache.gestures[metric]?.takeIf { range == null }
        mutableStateOf(
            shared ?: ChartGesture.of(
                if (range != null) {
                    val initial = ChartRanges.initialWindow(range, maxSpan)
                    Viewport(initial.fromMillis, initial.toMillis, followLiveEdge = false)
                } else {
                    val initial =
                        ChartMetrics.startWindow(metric, emptyMap(), System.currentTimeMillis())
                    Viewport(initial.fromMillis, initial.toMillis, followLiveEdge = true)
                },
                startBounds,
            ),
        )
    }
    // Выбранное здесь окно возвращается на карточку: это одна картинка в двух
    // размерах.
    LaunchedEffect(gesture, historical) {
        if (!historical) {
            graph.chartCache.gestures = graph.chartCache.gestures + (metric to gesture)
        }
    }
    val window = gesture.visible.window()
    val follow = gesture.visible.followLiveEdge

    /** Границы, в которых окну разрешено двигаться, на текущий момент. */
    fun bounds(nowMillis: Long = System.currentTimeMillis()) = ViewportBounds(
        edgeMillis = ChartRanges.edgeMillis(range, nowMillis),
        // У исторического графика левый предел — начало сессии.
        earliestMillis = range?.fromMillis ?: earliestMillis,
        maxSpanMillis = maxSpan,
    )

    /** Историческому окну живой край не принадлежит: слежению там неоткуда взяться. */
    fun setViewport(next: Viewport) {
        gesture = gesture.withViewport(
            if (historical) next.copy(followLiveEdge = false) else next,
            bounds(),
        )
    }

    // Экран открывается там, где его закрыли; окно восстанавливается один раз,
    // после чтения настроек.
    var spanRestored by remember(metric) { mutableStateOf(false) }
    LaunchedEffect(metric, savedSpans, historical) {
        val earliest = withContext(Dispatchers.IO) {
            graph.measurementRepository.earliestSampleMillis()
        }
        earliestMillis = earliest
        // Окно исторического графика задано диапазоном сессии.
        if (historical) return@LaunchedEffect
        if (spanRestored) return@LaunchedEffect
        spanRestored = true
        // Окно уже пришло с карточки — запомненная ступень не применяется.
        if (graph.chartCache.gestures[metric] != null) return@LaunchedEffect
        val now = System.currentTimeMillis()
        // Ступень — максимум: окно ОТКРЫТИЯ подтягивается к первому
        // измерению, дальше окном распоряжаются жесты.
        val start = ChartWindows.limitedByHistory(
            ChartMetrics.startWindow(metric, savedSpans, now),
            earliest,
        )
        setViewport(Viewport(start.fromMillis, start.toMillis, followLiveEdge = true))
    }
    // Лестница следует за окном: щипок меняет окно плавно, подсвеченный чип
    // называет то, что на экране.
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
            gesture = gesture.followTick(bounds())
        }
    }

    LaunchedEffect(graph, metric) {
        snapshotFlow { gesture.visible.window() }.collectLatest { w ->
            delay(ChartDataSource.RELOAD_DEBOUNCE_MILLIS)
            snapshot = withContext(Dispatchers.IO) { loadSnapshot(graph, w, metric) }
        }
    }

    // Кадр пересобирается, когда движение улеглось: под пальцем меняется
    // только видимое окно, ось значений принадлежит посчитанному кадру
    // (V2 §7, §13).
    var lastGestureAt by remember { mutableLongStateOf(0L) }
    // Когда кадр пересобирали в последний раз — чтобы во время жеста это
    // случалось по времени, а не по числу событий указателя.
    var lastCommitAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastGestureAt) {
        if (lastGestureAt == 0L) return@LaunchedEffect
        delay(SCALE_SETTLE_MILLIS)
        if (gesture.moved) gesture = gesture.commit(bounds())
    }

    // Keyed on the alert *flag*, not on the deviation snapshot: the engine
    // republishes that object every second and rebuilding the frame at 1 Hz
    // for an unchanged picture is exactly the waste this screen must avoid.
    val endpointAlert = follow && deviation.alertSince != null
    val axisStrings = ChartAxisCatalogue.of(LocalStrings.current.language)
    // Ширина поля задаёт число колонок ([ChartDownsampler]).
    var plotWidthPx by remember { mutableStateOf(0f) }
    // Ключи кадра — посчитанное окно, а не видимое: движение пальца кадр не
    // пересобирает.
    val frame = remember(
        snapshot, gesture.frame, gesture.rendered, unit, logScale, thresholds, baseline,
        endpointAlert, metric, follow, detail, showEvents,
        axisStrings, plotWidthPx,
    ) {
        snapshot?.let {
            buildFrame(
                snapshot = it,
                window = gesture.frame.window(),
                unit = unit,
                logScale = logScale,
                thresholds = thresholds,
                baseline = if (ChartMetrics.showsProfileBand(metric)) baseline else null,
                endpointAlert = endpointAlert,
                metric = metric,
                // Ось от «сейчас» — только пока график держится живого края.
                nowMillis = gesture.frame.endMillis.takeIf { range == null && follow },
                axisStrings = axisStrings,
                detail = detail,
                showEvents = showEvents,
                plotWidthPx = plotWidthPx,
                renderWindow = gesture.rendered,
            )
        }
    }

    /**
     * «Вся история»: окно от первого измерения до края времени. Не ступень
     * лестницы — длина зависит от объёма записи. Длиннее предела величины окно
     * не станет (`ChartMetrics.maxSpanMillis`).
     */
    fun selectAllHistory() {
        val b = bounds()
        val earliest = b.earliestMillis ?: return
        val span = (b.edgeMillis - earliest).coerceAtLeast(Viewports.MIN_SPAN_MILLIS)
        setViewport(Viewports.withSpan(gesture.visible, span, b))
        cursorActive = false
        cursorFraction.value = null
    }

    fun selectPeriod(index: Int) {
        val span = ChartWindows.PERIODS[index].second
        setViewport(Viewports.withSpan(gesture.visible, span, bounds()).copy(values = null))
        // Запомненное окно принадлежит живому экрану и карточке Главной.
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
     * Возврат к краю времени доездом (~220 мс), а не подменой окна: едет
     * ВРЕМЯ окна — длительность сохраняется, меняется правый край. Сами данные
     * не анимируются (V2 §8).
     */
    fun jumpToEdge() {
        cursorActive = false
        cursorFraction.value = null
        val target = if (range != null) {
            val full = ChartRanges.initialWindow(range, maxSpan)
            Viewport(full.fromMillis, full.toMillis, followLiveEdge = false)
        } else {
            Viewports.jumpToEdge(gesture.visible, bounds())
        }
        val from = gesture.visible
        val distance = kotlin.math.abs(target.endMillis - from.endMillis)
        // Далёкий возврат переносится, а не едет: доезд имеет смысл, пока
        // глаз успевает проследить движение.
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
                // Едет и длина окна: «⌖ сессия» возвращает к полному
                // диапазону.
                val span = from.spanMillis +
                    ((target.spanMillis - from.spanMillis) * eased).toLong()
                setViewport(Viewport(to - span, to, followLiveEdge = false))
                delay(EDGE_ANIMATION_MILLIS / steps)
            }
            setViewport(target)
        }
    }

    val minAxisSpan = ChartMetrics.minAxisSpan(metric)

    /** Кадр оси, с которого начинается ручной режим: то, что видно сейчас. */
    fun currentValues(): ValueWindow? = gesture.visible.values
        ?: frame?.spec?.scale?.let { ChartYAxis.windowOf(it) }

    val onTransform: (ChartGestureInput) -> Unit = { input ->
        lastGestureAt = System.currentTimeMillis()
        val b = bounds()
        var g = gesture
        when (input.axis) {
            GestureAxis.VALUE -> {
                // Палец ведёт ось: тянут вниз — в кадр приходит то, что было
                // выше. Автоподбор при этом выключается, и это видно чипом.
                currentValues()?.let { current ->
                    g = g.copy(
                        visible = g.visible.copy(
                            values = ChartYAxis.pan(current, input.panYFraction, minAxisSpan),
                        ),
                    )
                }
            }
            GestureAxis.VALUE_SCALE -> {
                // Жест по шкале справа сжимает и растягивает ось значений.
                currentValues()?.let { current ->
                    g = g.copy(
                        visible = g.visible.copy(
                            values = ChartYAxis.zoom(
                                window = current,
                                factor = 1f - input.panYFraction * VALUE_SCALE_SENSITIVITY,
                                minSpan = minAxisSpan,
                            ),
                        ),
                    )
                }
            }
            else -> {
                // Зум НЕПРЕРЫВНЫЙ (Charts V2 §5.3): множитель кадра идёт прямо
                // в окно, а точка под пальцами остаётся на месте.
                if (input.zoom != 1f) {
                    g = g.zoom(input.zoom, input.focusXFraction, b)
                }
                // Тянут вправо — в кадр приходит более раннее время.
                if (input.panXFraction != 0f) g = g.pan(-input.panXFraction, b)
                if (historical) g = g.copy(visible = g.visible.copy(followLiveEdge = false))
            }
        }
        // Уехали дальше нарисованного — кадр пересобирается, но не чаще
        // нескольких раз в секунду: при отдалении видимое окно покидает
        // нарисованное почти сразу.
        val at = System.currentTimeMillis()
        gesture = if (g.shouldCommit(at, lastCommitAt)) {
            lastCommitAt = at
            g.commit(b)
        } else {
            g
        }
        if (cursorActive) {
            cursorActive = false
            cursorFraction.value = null
        }
    }

    // Ось значений переезжает в новый диапазон коротким переходом; сами
    // измерения при этом не двигаются (V2 §7, §8).
    var shownScale by remember(metric, logScale) { mutableStateOf<DoseScale?>(null) }
    val targetScale = frame?.spec?.scale
    LaunchedEffect(targetScale) {
        val target = targetScale ?: return@LaunchedEffect
        val from = shownScale
        if (from == null || !ChartYAxis.animates(from, target)) {
            shownScale = target
            return@LaunchedEffect
        }
        for (step in 1..ChartYAxis.TRANSITION_STEPS) {
            val fraction = step.toFloat() / ChartYAxis.TRANSITION_STEPS
            val eased = 1f - (1f - fraction) * (1f - fraction)
            shownScale = ChartYAxis.interpolate(from, target, eased)
            delay(ChartYAxis.TRANSITION_MILLIS / ChartYAxis.TRANSITION_STEPS)
        }
        shownScale = target
    }

    // Фон Поиска — только когда график открыт из Поиска и показывает ту же
    // величину, в которой фон записан.
    val searchBackground by graph.localBackground.state.collectAsState()
    val searchBackgroundCps = (searchBackground as? LocalBackground.Done)
        ?.cps
        ?.takeIf { context == ChartContext.Search && metric == ChartMetric.COUNT_RATE }

    val chart: @Composable (Modifier) -> Unit = { chartModifier ->
        Box(chartModifier.onSizeChanged { plotWidthPx = it.width.toFloat() }) {
            val f = frame
            // The chart is drawn even for an empty window: axes and gestures
            // stay alive, so panning into a gap is never a dead end.
            if (f != null) {
                // Кадр посчитан шире видимого окна; на экран раскладывается
                // видимое.
                // Тот же живой край, что на карточках: между секундными
                // тиками едет окно, а не данные ([LiveEdge]).
                val padded = ChartWindows.withRightPadding(window)
                // Момент последнего тика — правый край видимого окна: пока
                // экран следит за «сейчас», это и есть «сейчас» на тике.
                val tickMillis = window.toMillis
                val smoothEdge = follow && LiveEdge.smooth(padded.spanMillis, plotWidthPx)
                val frameMillis by rememberFrameMillis(smoothEdge, tickMillis)
                val view = LiveEdge.shifted(padded, tickMillis, frameMillis)
                DoseChart(
                    spec = f.spec.copy(
                        viewFromMillis = view.fromMillis,
                        viewToMillis = view.toMillis,
                        scale = shownScale ?: f.spec.scale,
                    ),
                    cursorFraction = cursorFraction,
                    modifier = Modifier.fillMaxSize(),
                    cursorActive = cursorActive,
                    onCursorFraction = { fraction ->
                        cursorActive = true
                        // Видимый курсор останавливает слежение за живым краем.
                        gesture = gesture.holdForCursor()
                        cursorFraction.value = fraction
                    },
                    onResetScale = {
                        // Двойное нажатие возвращает автоматическую ось и
                        // оставляет окно времени как есть (ТЗ §5.7); полный
                        // сброс — пунктом в «⋯».
                        if (gesture.visible.values != null) {
                            gesture = gesture.copy(
                                frame = gesture.frame.copy(values = null),
                                visible = gesture.visible.copy(values = null),
                            )
                        } else {
                            selectPeriod(periodIndex)
                        }
                    },
                    onCursorDismiss = {
                        cursorActive = false
                        cursorFraction.value = null
                        // Слежение возобновляется, только если окно стоит у
                        // живого края.
                        setViewport(Viewports.clamp(gesture.visible, bounds()))
                    },
                    onTransform = onTransform,
                )
                CursorCard(
                    cursorFraction = cursorFraction,
                    buckets = f.spec.buckets,
                    // Курсор читает время по тому же окну, что разложено на
                    // ширину поля, включая воздух у живого края.
                    window = view,
                    unit = unit,
                    baseline = baseline,
                    alarmLevel = thresholds.l1MicroSvH,
                    eventTimesMillis = snapshot?.eventTimesMillis.orEmpty(),
                    searchBackgroundCps = searchBackgroundCps,
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
                context = context,
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
                    onSelectAllHistory = ::selectAllHistory,
                    onResetScale = { selectPeriod(periodIndex) },
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
                unit = unit,
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
            context = context,
            atRange = range != null && ChartRanges.atFullRange(window, range, maxSpan),
        )
        chart(Modifier.weight(1f).fillMaxWidth())
        AppDivider()
        // Под графиком две строки: числа окна и управление. Распределение,
        // расширенная статистика, покрытие окна и метод квантилей открываются
        // панелью поверх и высоту у данных не отнимают.
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
                onSelectAllHistory = ::selectAllHistory,
                onResetScale = { selectPeriod(periodIndex) },
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
            unit = unit,
            historical = historical,
            onClose = { infoOpen = false },
        )
    }
}

/**
 * Числа окна одной строкой: квантили, честное n и длительность окна. Нажатие
 * открывает панель с расширенной статистикой и распределением.
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
        // Живое значение — та же величина, что на графике.
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
            // Погрешность и единица живут в шапке и в разборе: величина
            // названа заголовком, порядок виден по оси значений.
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
    /** Откуда открыт график: у прошлого нет живого значения. */
    context: ChartContext = ChartContext.Live,
    atRange: Boolean = false,
) {
    val range = context.range
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column {
        // Одна строка вместо блока: шапка забирает у графика столько, сколько
        // нужно, чтобы назвать величину, её текущее значение и выход.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.space2, vertical = Dimens.space1),
        ) {
            AppCloseButton(onClose = onBack)
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
                // У прошлого нет «сейчас»: на месте живого значения стоит
                // отрезок времени.
                RangeLabel(range, compact = true)
                if (paused) FreshnessOrPause(Freshness.NoData, paused = true)
            }
            EdgeChip(context = context, follow = follow, atRange = atRange, onClick = onJumpToEdge)
            Chip(text = "i", color = colors.ink2, onClick = onInfo)
        }
        AppDivider()
    }
}

/**
 * «⌖ сейчас» — состояние: подсвечен, пока график стоит у живого края и едет
 * за ним. Отодвинули окно — подсветка гаснет, нажатие возвращает к «сейчас».
 */
@Composable
private fun EdgeChip(
    context: ChartContext,
    follow: Boolean,
    atRange: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    // У исторического графика живой край относится к другому времени: чип
    // возвращает к диапазону, из которого график открыт, и называет его.
    val selected = if (context.range == null) follow else atRange
    Chip(
        text = when (context) {
            ChartContext.Live, ChartContext.Search -> t.nowChip
            is ChartContext.Session -> t.sessionChip
            is ChartContext.Route -> t.routeChip
        },
        color = if (selected) colors.dataText else colors.ink2,
        selected = selected,
        onClick = onClick,
    )
}

/**
 * Диапазон исторического графика словами. В портрете печатается начало и
 * длительность (конец из них следует), в ландшафте — весь отрезок.
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
    // Чип показывается только у отставшего потока.
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
    context: ChartContext = ChartContext.Live,
    atRange: Boolean = false,
) {
    val range = context.range
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
        AppCloseButton(onClose = onBack)
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
        EdgeChip(context = context, follow = follow, atRange = atRange, onClick = onJumpToEdge)
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
    /** «Вся история»: окно от первого измерения до края; null — история неизвестна. */
    onSelectAllHistory: (() -> Unit)? = null,
    /**
     * Сбросить масштаб — окно у края и автоматическая ось. Единственный
     * способ вернуть автоподбор после жеста по оси значений.
     */
    onResetScale: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val texts = ChartTextCatalogue.of(LocalStrings.current.language)
    val strings = LocalStrings.current
    // Окно выбирается из сетки в поповере: пятнадцать ступеней лентой поверх
    // графика закрывали данные (V2 §17). Подпись чипа: ровно ступень — её
    // название и подсветка, между ступенями — фактическое окно без подсветки.
    var pickerOpen by remember { mutableStateOf(false) }
    Box {
        Chip(
            text = (if (periodExact) ChartWindows.PERIODS[periodIndex].first else currentSpanLabel) +
                " ▾",
            color = colors.ink,
            selected = periodExact,
            onClick = { pickerOpen = true },
        )
        AppMenu(expanded = pickerOpen, onDismiss = { pickerOpen = false }) {
            AppMenuHeader(texts.windowPicker)
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.space3,
                    vertical = Dimens.space1,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            ) {
                for (row in availablePeriods.chunked(PICKER_COLUMNS)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                        for (index in row) {
                            val exact = index == periodIndex && periodExact
                            Chip(
                                text = ChartWindows.PERIODS[index].first,
                                color = if (index == periodIndex) colors.ink else colors.ink2,
                                selected = exact,
                                onClick = {
                                    onSelectPeriod(index)
                                    pickerOpen = false
                                },
                            )
                        }
                    }
                }
            }
            if (onSelectAllHistory != null) {
                // «Вся история» — не ступень: её длина зависит от объёма
                // записи.
                AppMenuDivider()
                AppMenuItem(
                    text = texts.allHistory,
                    onClick = {
                        onSelectAllHistory()
                        pickerOpen = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Spacer(Modifier.width(Dimens.space1))
    // Вид шкалы называет себя: «лин» — сейчас линейная, «лог» — сейчас
    // логарифмическая; подсветка означает включённое состояние.
    Chip(
        text = if (logScale) texts.logChip else texts.linearChip,
        color = if (logScale) colors.dataText else colors.ink2,
        selected = logScale,
        onClick = onToggleScale,
    )
    Spacer(Modifier.width(Dimens.space1))
    // Видимыми оставлены два управляющих элемента, которыми пользуются по ходу
    // чтения: окно и вид шкалы; остальное — под «⋯» (V2 §16).
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Chip(text = "⋯", color = colors.ink2, onClick = { menuOpen = true })
        // Меню раскрывается влево от «⋯»: чип стоит у правого края панели.
        AppMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            alignment = Alignment.BottomEnd,
        ) {
            AppMenuItem(
                text = texts.smoothChip,
                state = if (detailed) strings.off else strings.on,
                stateOn = !detailed,
                onClick = {
                    menuOpen = false
                    onToggleDetail()
                },
            )
            AppMenuItem(
                text = texts.eventsChip,
                state = if (events) strings.on else strings.off,
                stateOn = events,
                onClick = {
                    menuOpen = false
                    onToggleEvents()
                },
            )
            AppMenuDivider()
            AppMenuItem(
                text = texts.moreDetails,
                onClick = {
                    menuOpen = false
                    onOpenDetails()
                },
            )
            // То же, что двойное нажатие по полю; пункт виден, жест — нет
            // (V2 §5.7).
            AppMenuItem(
                text = texts.resetScale,
                onClick = {
                    menuOpen = false
                    onResetScale()
                },
            )
        }
    }
}
