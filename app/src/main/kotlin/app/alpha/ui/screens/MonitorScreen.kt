package app.alpha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.Context
import app.alpha.device.BluetoothState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.lerp
import app.alpha.ui.components.PlaceScaleBar
import app.alpha.ui.components.BreathingAura
import app.alpha.ui.components.rememberFrameMillis
import app.alpha.ui.logic.DoseTint
import app.alpha.ui.logic.LiveEdge
import app.alpha.ui.logic.MonitorLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.alpha.data.export.CrashLog
import app.alpha.AppGraph
import app.alpha.analysis.Hardness
import app.alpha.baseline.Admission
import app.alpha.baseline.BaselineExclusion
import app.alpha.baseline.AlarmSensitivity
import app.alpha.baseline.Baseline
import app.alpha.baseline.BaselineState
import app.alpha.baseline.alarmThresholds
import app.alpha.context.MeasurementContext
import app.alpha.data.DoseUnitSetting
import app.alpha.data.ExclusionSummary
import app.alpha.data.MonitorBlocks
import app.alpha.data.db.ProfileEntity
import app.alpha.device.ConnectionState
import app.alpha.device.DoseUnits
import app.alpha.service.BatteryOptimization
import app.alpha.ui.components.StatusRow
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppIcons
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.ProfilePickerDialog
import app.alpha.ui.components.MetricTile
import app.alpha.ui.components.MetricTileBox
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.components.StatusDot
import app.alpha.ui.components.WhySheet
import app.alpha.ui.logic.ChartMapping
import app.alpha.ui.logic.ChartDetailMode
import app.alpha.ui.logic.ChartMetric
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.Freshness
import app.alpha.ui.logic.StreamState
import app.alpha.ui.logic.streamAgeLine
import app.alpha.ui.logic.streamStatusLine
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.MonitorStatus
import app.alpha.ui.logic.BaselineSnapshot
import app.alpha.ui.logic.ProfileShift
import app.alpha.ui.logic.ProfileTree
import app.alpha.ui.logic.TrendAvailability
import app.alpha.ui.logic.TrendFit
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.logic.WhyInput
import app.alpha.ui.logic.DoseAlarm
import app.alpha.ui.logic.DoseAlarmLevel
import app.alpha.ui.logic.statusDetail
import app.alpha.ui.logic.statusHeadline
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.ChartAxisCatalogue
import app.alpha.ui.text.ChartTextCatalogue
import app.alpha.ui.text.ChartAxisRu
import app.alpha.ui.text.ChartAxisStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.MonitorCatalogue
import app.alpha.ui.text.MonitorRu
import app.alpha.ui.text.MonitorStrings
import app.alpha.ui.theme.Motion
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.alpha.ui.components.DoseChart
import app.alpha.ui.logic.ChartMetrics
import app.alpha.ui.logic.coverageWording
import app.alpha.ui.logic.ChartSnapshot
import app.alpha.ui.logic.ChartWindow
import app.alpha.ui.logic.ChartTrace
import app.alpha.ui.chart.ChartDataSource
import app.alpha.ui.chart.ReadPadding
import app.alpha.ui.chart.ChartGesture
import app.alpha.ui.chart.ChartGestureInput
import app.alpha.ui.chart.Viewport
import app.alpha.ui.chart.ViewportBounds
import app.alpha.ui.chart.Viewports
import app.alpha.ui.logic.ChartWindows
import app.alpha.ui.logic.LoadedChart
import app.alpha.ui.logic.TrendPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Период перечитывания графиков Главной. Прибор пишет раз в секунду, колонка
 * карточки покрывает минуты.
 */
/**
 * Сколько держится след на шкале места, мс.
 *
 * **Инженерный параметр**: минута — столько человек помнит, что было «только
 * что», и на секундной записи это шестьдесят точек, то есть след успевает
 * стать полосой, а не остаться точкой.
 */
private const val TRAIL_MILLIS = 60_000L

private const val CHART_REFRESH_MILLIS = 15_000L

/**
 * Пауза перед пересборкой кадра под новое окно. **Инженерный параметр**: те же
 * 120 мс, что на полноэкранном графике.
 */
private const val CHART_SETTLE_MILLIS = 120L

/**
 * Доля окна, за которую картинка обязана обновиться хотя бы раз.
 * **Инженерный параметр**: 1/200 окна ≈ ширина одной колонки.
 */
private const val LIVE_TICK_WINDOW_FRACTION = 200L

/**
 * Окно тренда на Главной — час, независимо от окна карточки графика.
 *
 * Величина подписана «Тренд/ч» и обязана иметь собственное окно: при расчёте
 * по окну карточки правило доступности (размах ≥10 мин) не выполнялось на
 * коротких ступенях. Подпись окна — `MonitorStrings.trendWindowHour`.
 */
private const val TREND_WINDOW_MILLIS = 3_600_000L


/**
 * Монитор (Главная): the 2-3 second answer — current dose rate with its
 * uncertainty, count rate, hour trend, dose today, whether the level differs
 * from the usual level of this place. Baseline state and the live deviation
 * picture come from the measurement service (single source); this screen
 * only renders [MonitorStatus].
 */
@Composable
fun MonitorScreen(
    graph: AppGraph,
    onOpenMetricChart: (ChartMetric) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenChart: () -> Unit = {},
    /** Плитка накопленного открывает свой экран. */
    onOpenDose: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Живое показание берётся из памяти службы, а не из базы: «идут ли данные
    // сейчас» — факт о ПРИХОДЕ пакета. Метка времени прибора остаётся осью
    // графиков; она измеряется по ходу сеанса и для свежести не годится.
    val live by graph.serviceStatus.lastSample.collectAsState()
    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val deviation by graph.serviceStatus.deviation.collectAsState()
    val thresholds by graph.settings.alarmThresholds
        .collectAsState(initial = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f))
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val doseTint by graph.settings.doseTint.collectAsState(initial = true)
    val doseTintFactor by graph.settings.doseTintFactor
        .collectAsState(initial = DoseTint.DEFAULT_FACTOR)
    val connectedAt by graph.serviceStatus.connectedAtMillis.collectAsState()
    val chartDetailId by graph.settings.chartDetailModeId
        .collectAsState(initial = ChartDetailMode.DEFAULT.id)
    val chartDetail = remember(chartDetailId) { ChartDetailMode.of(chartDetailId) }
    val blocks by graph.settings.monitorBlocks.collectAsState(initial = MonitorBlocks())
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val contextState by graph.contextHub.state.collectAsState()
    val admission by graph.serviceStatus.admission.collectAsState()
    val frozen by graph.settings.baselineFrozen.collectAsState(initial = false)
    val whyExpanded by graph.settings.whyCalculationsExpanded.collectAsState(initial = false)

    // Секундный тик стенных часов ведёт индикатор свежести и длительности.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    // Один источник свежести на весь экран: число, плитки, статус и графики
    // говорят об одном возрасте данных.
    val stream = StreamState.of(live?.receivedAtMillis, nowMillis, connection)
    val freshness = Freshness.of(live?.receivedAtMillis, nowMillis)

    // Графики Главной читаются тем же путём, что полноэкранный (ADR 004):
    // одно окно, один снимок, один кадр. Выключенные величины не читаются.
    val savedSpans by graph.settings.chartSpans.collectAsState(initial = emptyMap())
    val chartMetrics = remember(blocks.countRateChart, blocks.hardnessChart) {
        buildList {
            add(ChartMetric.DOSE)
            if (blocks.countRateChart) add(ChartMetric.COUNT_RATE)
            if (blocks.hardnessChart) add(ChartMetric.HARDNESS)
        }
    }
    // Вьюпорты живут здесь, а не внутри карточки: сдвинутое окно должно быть
    // видно загрузчику. Кэш хранит результат последнего чтения, поэтому
    // возврат на вкладку показывает картинку сразу.
    val cache = graph.chartCache
    var gestures by remember { mutableStateOf(cache.gestures) }
    // Ширина поля карточки в пикселях задаёт число колонок кадра
    // ([ChartDownsampler]); меряется карточкой, кадр собирается здесь.
    var plotWidths by remember { mutableStateOf<Map<ChartMetric, Float>>(emptyMap()) }
    // Начало истории меняется первой записью и уборкой журнала, поэтому
    // спрашивается один раз, а не на каждый жест.
    var earliestMillis by remember { mutableStateOf(cache.earliestMillis) }
    var charts by remember { mutableStateOf(cache.charts) }
    var trend by remember { mutableStateOf<TrendAvailability?>(null) }
    var doseTodayMicroSv by remember { mutableStateOf<Double?>(null) }
    // Цикл перечитывания привязан к жизненному циклу. Сбой чтения не рвёт
    // цикл: ошибка уходит в журнал, следующий проход пробует снова; возврат на
    // передний план запускает обновление, не дожидаясь очередных 15 с.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Второй, независимый от таймера повод перечитать: отметка времени
    // последнего отсчёта, огрублённая до шага обновления. Шаг следует ОКНУ:
    // на пятиминутном окне колонка — секунды, на месячном частое обновление
    // рисует те же колонки ценой тяжёлого запроса. Берётся самое короткое
    // окно из включённых величин.
    val liveTickMillis = remember(chartMetrics, savedSpans) {
        // Темп перечитывания следует ШИРИНЕ КОЛОНКИ: новая колонка появляется
        // раз в её ширину, читать чаще четверти этого нечего.
        val shortestBucket = chartMetrics.minOf { metric ->
            val window = ChartMetrics.startWindow(metric, savedSpans, System.currentTimeMillis())
            app.alpha.ui.logic.ChartSeriesModel.bucketMillis(window.spanMillis)
        }
        ChartWindows.refreshMillis(shortestBucket)
    }
    val liveTick = live?.receivedAtMillis?.let { it / liveTickMillis }
    // Ключ чтения — посчитанные окна, а не состояние жестов: видимое окно
    // едет за живым краем каждую секунду. Окно берётся внутри цикла.
    val readKey = gestures.mapValues { (_, gesture) -> gesture.frame.window() }
    LaunchedEffect(chartMetrics, savedSpans, resumeTick, liveTick, readKey) {
        while (true) {
            val now = System.currentTimeMillis()
            val outcome = runCatching {
                val loadedCharts = withContext(Dispatchers.IO) {
                    // Выбранная ступень — максимум: окно подтягивается к
                    // первому измерению и растёт вместе с историей.
                    val earliest = earliestMillis
                        ?: graph.measurementRepository.earliestSampleMillis()
                            ?.also { earliestMillis = it }
                    chartMetrics.associateWith { metric ->
                        val viewport = gestures[metric]?.visible
                        val chosen = viewport
                            ?.let { Viewports.followTick(it, ViewportBounds(edgeMillis = now)) }
                            ?.window()
                            ?: ChartMetrics.startWindow(metric, savedSpans, now)
                        // Подтяжка к началу истории — только у живого края:
                        // окно, уведённое в прошлое, не двигается.
                        val window = if (viewport == null || viewport.followLiveEdge) {
                            ChartWindows.limitedByHistory(chosen, earliest)
                        } else {
                            chosen
                        }
                        // Сдвиг внутри прочитанного диапазона запроса не
                        // требует: снимок неизменен, меняется проекция.
                        val previous = charts[metric]
                        val reuse = previous?.takeIf {
                            ChartDataSource.reusable(
                                loadedRange = it.loadedRange,
                                loadedBucketMillis = it.snapshot.bucketMillis,
                                window = window,
                                edgeMillis = now,
                                padding = ReadPadding.Compact,
                            )
                        }
                        if (reuse != null) {
                            return@associateWith reuse.copy(window = window)
                        }
                        val load = ChartDataSource.readRange(window, now, ReadPadding.Compact)
                        val snapshot = loadSnapshot(graph, window, metric, ReadPadding.Compact)
                        // Трасса конвейера: база, снимок, кадр одного окна.
                        // Считается только при реальном чтении.
                        val census = graph.measurementRepository.rangeCensus(
                            window.fromMillis,
                            window.toMillis,
                        )
                        val visible = snapshot.buckets.filter {
                            it.endMillis > window.fromMillis && it.startMillis < window.toMillis
                        }
                        graph.chartTrace.add(
                            ChartTrace.Pass(
                                atMillis = System.currentTimeMillis(),
                                metric = metric.id,
                                nowMillis = now,
                                windowStart = window.fromMillis,
                                windowEnd = window.toMillis,
                                roomCount = census.count,
                                roomMin = census.minTimestamp,
                                roomMax = census.maxTimestamp,
                                snapshotBuckets = snapshot.buckets.size,
                                snapshotMin = snapshot.buckets.firstOrNull()?.startMillis,
                                snapshotMax = snapshot.buckets.lastOrNull()?.endMillis,
                                frameBuckets = visible.size,
                                frameMin = visible.firstOrNull()?.startMillis,
                                frameMax = visible.lastOrNull()?.endMillis,
                            ),
                        )
                        LoadedChart(window, snapshot, load, earliest)
                    }
                }
                val loadedTrend = withContext(Dispatchers.IO) {
                    val hour = ChartWindows.latest(TREND_WINDOW_MILLIS, now)
                    TrendFit.availability(
                        loadSnapshot(graph, hour, ChartMetric.DOSE).buckets
                            .filter { it.midMillis >= hour.fromMillis }
                            .map { TrendPoint(it.midMillis, it.median) },
                    )
                }
                val loadedDose = withContext(Dispatchers.IO) { loadDoseToday(graph) }
                Triple(loadedCharts, loadedTrend, loadedDose)
            }
            outcome.getOrNull()?.let { (loadedCharts, loadedTrend, loadedDose) ->
                charts = loadedCharts
                cache.charts = loadedCharts
                cache.earliestMillis = earliestMillis
                trend = loadedTrend
                doseTodayMicroSv = loadedDose
                // Отметка для отладочного отчёта: по ней видно, жив ли цикл
                // обновления.
                graph.serviceStatus.onChartsRefreshed(System.currentTimeMillis())
            }
            outcome.exceptionOrNull()?.let { error ->
                // Отмена корутины приходит при уходе с экрана и сбоем не является.
                if (error is kotlinx.coroutines.CancellationException) throw error
                CrashLog.append(
                    graph.crashLogFile,
                    CrashLog.entry(
                        atMillis = System.currentTimeMillis(),
                        stamp = "обновление графиков Главной",
                        threadName = Thread.currentThread().name,
                        error = error,
                    ),
                )
            }
            delay(CHART_REFRESH_MILLIS)
        }
    }

    var showProfilePicker by remember { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf(false) }

    // Сравнение с эталоном места — запрос ради «Почему?» и вкладки отпечатка:
    // считается при открытии шторки.
    val language = LocalStrings.current.language
    var fingerprint by remember {
        mutableStateOf<app.alpha.analysis.FingerprintComparison?>(null)
    }
    LaunchedEffect(activeProfile?.id, showWhy, language) {
        val id = activeProfile?.id
        fingerprint = if (id == null || !showWhy) {
            null
        } else {
            app.alpha.analysis.Fingerprint.compare(
                window = graph.fingerprintRepository.window(id),
                reference = graph.fingerprintRepository.reference(id),
                s = app.alpha.ui.text.FingerprintCatalogue.of(language),
            )
        }
    }

    // Разбор исключений — запрос, а не поток: нужен только «Почему?».
    var exclusions by remember { mutableStateOf<List<ExclusionSummary>>(emptyList()) }
    LaunchedEffect(activeProfile?.id, showWhy) {
        val id = activeProfile?.id
        exclusions = if (showWhy && id != null) graph.baselineRepository.exclusions(id) else emptyList()
    }

    val doseMicroSvH = live?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
    // След последней минуты на шкале места: где значение уже побывало. Это
    // ИСТОРИЯ, а не измерение, поэтому она и может двигаться плавно; сами
    // измерения по-прежнему переставляются шагом.
    var trailPoints by remember { mutableStateOf(emptyList<Pair<Long, Float>>()) }
    LaunchedEffect(live?.receivedAtMillis) {
        val value = doseMicroSvH ?: return@LaunchedEffect
        val at = live?.receivedAtMillis ?: return@LaunchedEffect
        trailPoints = (trailPoints + (at to value)).filter { at - it.first <= TRAIL_MILLIS }
    }
    val trail = trailPoints.takeIf { it.size > 1 }?.let { points ->
        points.minOf { it.second } to points.maxOf { it.second }
    }
    val status = MonitorStatus.of(
        doseRateMicroSvH = doseMicroSvH,
        baselineState = baselineState,
        deviation = deviation,
        thresholds = thresholds,
        nowMillis = nowMillis,
    )

    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = MonitorCatalogue.of(strings.language)
    // Свободная высота экрана достаётся главной карточке. Без графиков она
    // иначе висела бы полосой под числом: телефоны выше, чем содержимое
    // Главной, и пустота внизу читается как «что-то не загрузилось».
    // Измеряются СОСЕДИ карточки — их высота от неё не зависит, поэтому
    // измерение не зацикливается.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf(0.dp) }
        var belowHeight by remember { mutableStateOf(0.dp) }
        val heroContentMin = MonitorLayout.heroContentMin(
            viewport = viewportHeight,
            header = headerHeight,
            below = belowHeight,
            gap = Dimens.space3,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.space3),
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            Column(
                modifier = Modifier.onSizeChanged {
                    headerHeight = with(density) { it.height.toDp() }
                },
                verticalArrangement = Arrangement.spacedBy(Dimens.space3),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Chip(
                        text = profileChipText(activeProfile, profiles, contextState, t),
                        color = colors.ink,
                        onClick = { showProfilePicker = true },
                    )
                    Spacer(Modifier.weight(1f))
                    ConnectedFlash(connectedAt)
                    ConnectionChip(connection, serviceRunning, stream)
                    StreamChip(stream)
                    Icon(
                        imageVector = AppIcons.Lambda,
                        contentDescription = strings.settings,
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

                // Выключенный Bluetooth стоит выше всего: пока он выключен, прибор
                // не подключится, и все числа ниже относятся к прошлому.
                BluetoothBanner()
            }

            HeroCard(
                minContentHeight = heroContentMin,
                doseMicroSvH = doseMicroSvH,
                errPercent = live?.doseRateErr,
                cps = live?.countRate,
                trend = trend,
                trendWindowLabel = t.trendWindowHour,
                doseTodayMicroSv = doseTodayMicroSv,
                status = status,
                baselineState = baselineState,
                unit = unit,
                stale = !stream.live,
                stream = stream,
                blocks = blocks,
                admission = admission,
                frozen = frozen,
                onWhy = { showWhy = true },
                onOpenDose = onOpenDose,
                tintEnabled = doseTint,
                tintFactor = doseTintFactor,
                thresholdMicroSvH = thresholds.l1MicroSvH,
                trail = trail,
            )

            Column(
                modifier = Modifier.onSizeChanged {
                    belowHeight = with(density) { it.height.toDp() }
                },
                verticalArrangement = Arrangement.spacedBy(Dimens.space3),
            ) {
                val baseline = (baselineState as? BaselineState.Active)?.baseline
                val alert = status is MonitorStatus.Alert
                for (metric in chartMetrics) key(metric) {
                    val loaded = charts[metric]
                    // Окно карточки — то же состояние, что у полноэкранного графика:
                    // ступень, правый край и слежение за «сейчас».
                    val bounds = ViewportBounds(
                        edgeMillis = nowMillis,
                        earliestMillis = earliestMillis,
                        maxSpanMillis = ChartMetrics.maxSpanMillis(metric),
                    )
                    val gesture = gestures[metric] ?: ChartGesture.of(
                        Viewports.atEdge(
                            ChartMetrics.startWindow(metric, savedSpans, nowMillis).spanMillis,
                            bounds,
                        ),
                        bounds,
                    )
                    val viewport = gesture.visible
                    fun setGesture(next: ChartGesture) {
                        gestures = gestures + (metric to next)
                        cache.gestures = gestures
                    }
                    // Живой край двигает видимое окно; кадр остаётся, пока хватает
                    // запаса геометрии.
                    LaunchedEffect(nowMillis / 1_000L, metric) {
                        val current = gestures[metric] ?: return@LaunchedEffect
                        val next = current.followTick(bounds)
                        if (next != current) setGesture(next)
                    }
                    // Кадр пересобирается, когда движение улеглось: под пальцем
                    // двигается уже нарисованная картинка.
                    var lastGestureAt by remember(metric) { mutableLongStateOf(0L) }
                    LaunchedEffect(lastGestureAt) {
                        if (lastGestureAt == 0L) return@LaunchedEffect
                        delay(CHART_SETTLE_MILLIS)
                        val current = gestures[metric] ?: return@LaunchedEffect
                        if (current.moved) setGesture(current.commit(bounds))
                    }
                    // Правый край кадра идёт за «сейчас» каждую секунду без чтения
                    // базы: снимок неизменен, окно — арифметика по колонкам. Ширина
                    // карточки решает, сколько колонок имеет смысл (Charts V2 §20).
                    val plotWidthPx = plotWidths[metric] ?: 0f
                    // Ключи кадра — снимок и посчитанное окно, а не секунда часов:
                    // иначе кадр пересобирается ежесекундно на каждую карточку.
                    val frame = remember(
                        loaded?.snapshot, loaded?.earliestMillis, unit, thresholds, baseline, alert,
                        gesture.frame, gesture.rendered, gesture.visible.values, chartDetail,
                        plotWidthPx, blocks.stats,
                    ) {
                        loaded?.let {
                            val liveWindow = ChartWindows.limitedByHistory(
                                gesture.frame.window(),
                                it.earliestMillis,
                            )
                            buildFrame(
                                snapshot = it.snapshot,
                                window = liveWindow,
                                unit = unit,
                                logScale = false,
                                thresholds = thresholds,
                                baseline = baseline,
                                endpointAlert = alert && metric == ChartMetric.DOSE,
                                metric = metric,
                                xLabelCount = 3,
                                // Карточка Главной всегда живая: правый край окна и
                                // есть «сейчас».
                                nowMillis = nowMillis,
                                axisStrings = ChartAxisCatalogue.of(strings.language),
                                showUnit = false,
                                detail = chartDetail,
                                // Далёкий порог на карточке не рисуется.
                                showDistantAlarm = false,
                                plotWidthPx = plotWidthPx,
                                renderWindow = gesture.rendered,
                                // Ни распределения, ни статистики окна: их нет на
                                // экране карточки.
                                withHistogram = false,
                                withStats = blocks.stats,
                                values = gesture.visible.values,
                            )
                        }
                    }
                    // Живой край едет покадрово, а не рывком раз в секунду:
                    // между тиками сдвигается только ОКНО просмотра, кадр и
                    // значения не пересчитываются ([LiveEdge]). Покадровая
                    // перерисовка включается лишь там, где движение видно.
                    val liveWindow = ChartWindows.withRightPadding(viewport.window())
                    // Момент последнего тика — правый край САМОГО окна, а не
                    // часы экрана: они обновляются со своей частотой, и от
                    // разницы частот картинка дёргалась влево-вправо.
                    val tickMillis = viewport.window().toMillis
                    val smoothEdge = viewport.followLiveEdge &&
                        stream.live &&
                        LiveEdge.smooth(liveWindow.spanMillis, plotWidthPx)
                    val frameMillis by rememberFrameMillis(smoothEdge, tickMillis)
                    MetricChartCard(
                        metric = metric,
                        frame = frame,
                        spanMillis = loaded?.window?.spanMillis,
                        hasBaselineBand = baseline != null,
                        unit = unit,
                        showStats = blocks.stats,
                        onOpen = {
                            if (metric == ChartMetric.DOSE) onOpenChart() else onOpenMetricChart(metric)
                        },
                        following = viewport.followLiveEdge,
                        onBackToNow = {
                            setGesture(gesture.withViewport(Viewports.jumpToEdge(viewport, bounds), bounds))
                        },
                        viewWindow = LiveEdge.shifted(liveWindow, tickMillis, frameMillis),
                        onOpenFromChart = {
                            if (metric == ChartMetric.DOSE) onOpenChart() else onOpenMetricChart(metric)
                        },
                        onPlotWidth = { width ->
                            if (plotWidths[metric] != width) {
                                plotWidths = plotWidths + (metric to width)
                            }
                        },
                        onTransform = { input ->
                            lastGestureAt = System.currentTimeMillis()
                            // Жест меняет ВРЕМЯ, а не картинку: из состояния получается
                            // окно, окно идёт в загрузку и в кадр. Готовое изображение
                            // не растягивается — агрегация обязана отвечать масштабу.
                            // Щипок непрерывный, вокруг точки под пальцами.
                            var next = gesture
                            if (input.zoom != 1f) {
                                next = next.zoom(input.zoom, input.focusXFraction, bounds)
                            }
                            if (input.panXFraction != 0f) {
                                next = next.pan(-input.panXFraction, bounds)
                            }
                            // Вышли за нарисованное — паузы не ждём.
                            setGesture(if (next.covered()) next else next.commit(bounds))
                        },
                    )
                }

                BatteryBanner()
            }
        }
    }

    if (showProfilePicker) {
        ProfilePickerDialog(
            profiles = profiles,
            activeProfileId = activeProfile?.id,
            manual = contextState.isManual,
            contextWording = contextWording(contextState, t),
            onSelect = { id -> scope.launch { graph.profileRepository.selectManually(id) } },
            onReturnToAuto = { scope.launch { graph.profileRepository.returnToAuto() } },
            onCreate = { name ->
                scope.launch {
                    val id = graph.profileRepository.add(name)
                    graph.profileRepository.selectManually(id)
                }
            },
            onDismiss = { showProfilePicker = false },
        )
    }

    if (showWhy) {
        // §7: удержанное часами отклонение может означать, что изменилось
        // само место. Приложение не решает этого само.
        val profile = activeProfile
        val shiftOffered = profile != null && ProfileShift.shouldOffer(
            status = status,
            declinedAtMillis = profile.shiftDeclinedAtMillis,
            nowMillis = System.currentTimeMillis(),
        )
        WhySheet(
            expanded = whyExpanded,
            onExpandedChange = { scope.launch { graph.settings.setWhyCalculationsExpanded(it) } },
            offerProfileShift = shiftOffered,
            onUpdateProfile = {
                val profileId = profile?.id
                val current = (baselineState as? BaselineState.Active)?.baseline
                if (profileId != null && current != null) {
                    scope.launch {
                        graph.baselineRepository.startNewPeriod(
                            profileId = profileId,
                            stats = BaselineSnapshot.encode(current),
                        )
                    }
                }
                showWhy = false
            },
            onKeepProfile = {
                profile?.id?.let { id ->
                    scope.launch { graph.baselineRepository.declineShift(id) }
                }
                showWhy = false
            },
            input = WhyInput(
                status = status,
                baselineState = baselineState,
                doseRateMicroSvH = doseMicroSvH,
                cps = live?.countRate,
                freshness = freshness,
                thresholds = thresholds,
                admission = admission,
                exclusions = exclusions,
                unit = unit,
                profileName = activeProfile?.let { ProfileTree.displayName(it, profiles) },
                contextWording = contextWording(contextState, t),
                fingerprint = fingerprint,
            ),
            onDismiss = { showWhy = false },
        )
    }
}

/** «⌂ Дом · авто ▾» — profile plus how it was chosen (spec §17 layout). */
private fun profileChipText(
    active: ProfileEntity?,
    profiles: List<ProfileEntity>,
    context: MeasurementContext,
    s: MonitorStrings = MonitorRu,
): String {
    val name = active?.let { ProfileTree.displayName(it, profiles) } ?: s.profileUnknown
    val icon = active?.icon.orEmpty()
    val prefix = if (icon.isBlank()) "" else "$icon "
    val mode = contextModeWord(context, s)
    return if (mode == null) "$prefix$name ▾" else "$prefix$name · $mode ▾"
}

/**
 * Слово о том, как выбран профиль. Автоматический выбор — обычное состояние и
 * не подписывается; сообщаются два случая: место закреплено рукой и место не
 * подтверждено (сеть пропала).
 */
private fun contextModeWord(
    context: MeasurementContext,
    s: MonitorStrings = MonitorRu,
): String? = when (context) {
    is MeasurementContext.Manual -> s.modeManual
    is MeasurementContext.AutoUncertain -> s.modeUnconfirmed
    else -> null
}

/** One honest phrase about how the current profile was chosen (spec §3.4). */
fun contextWording(
    context: MeasurementContext,
    s: MonitorStrings = MonitorRu,
): String = when (context) {
    is MeasurementContext.AutoKnown -> s.contextAutoKnown
    is MeasurementContext.AutoUncertain -> s.contextAutoUncertain
    MeasurementContext.AutoTransit -> s.contextTransit
    MeasurementContext.NoContext -> s.contextNoContext
    is MeasurementContext.Manual -> s.contextManual
}

/**
 * «Подключено» на пару секунд после установления связи; надпись гаснет сама.
 */
@Composable
private fun ConnectedFlash(connectedAtMillis: Long?) {
    val colors = LocalAppColors.current
    val t = MonitorCatalogue.of(LocalStrings.current.language)
    // Видимость считается от момента подключения, а не от сборки экрана.
    var visible by remember(connectedAtMillis) {
        mutableStateOf(
            connectedAtMillis != null &&
                System.currentTimeMillis() - connectedAtMillis < CONNECTED_FLASH_MILLIS,
        )
    }
    LaunchedEffect(connectedAtMillis) {
        if (!visible) return@LaunchedEffect
        val left = CONNECTED_FLASH_MILLIS -
            (System.currentTimeMillis() - (connectedAtMillis ?: 0L))
        delay(left.coerceAtLeast(0L))
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.normal()),
        exit = fadeOut(Motion.screen()),
    ) {
        Chip(text = t.connectedFlash, color = colors.ok, dot = colors.ok)
    }
}

/** Сколько держится подтверждение связи перед тем, как погаснуть. */
private const val CONNECTED_FLASH_MILLIS = 2_500L

@Composable
private fun ConnectionChip(
    connection: ConnectionState,
    serviceRunning: Boolean,
    /** Точка говорит о ДАННЫХ, а не о Bluetooth: связь может стоять, а поток не идти. */
    stream: StreamState,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    // При живом потоке в шапке нет ничего: молчание и есть сообщение «идёт».
    // Момент подключения показывает отдельная надпись «Подключено».
    val (dot, text: String?) = when {
        connection is ConnectionState.Connected && stream.live -> return
        // Связь стоит, поток встал — янтарная точка.
        connection is ConnectionState.Connected -> colors.warn to null
        connection is ConnectionState.Connecting -> colors.warn to strings.connecting
        connection is ConnectionState.Reconnecting -> colors.warn to strings.reconnecting
        !serviceRunning -> colors.muted to strings.serviceOff
        else -> colors.muted to strings.noLink
    }
    if (text == null) {
        StatusDot(dot)
    } else {
        Chip(text = text, dot = dot)
    }
}

/**
 * Состояние потока одним чипом. В [StreamState.Live] чипа нет. Секунды
 * называются только в короткой запинке; в устойчивом состоянии возраст уходит
 * во вторичную строку под главным числом.
 */
@Composable
private fun StreamChip(stream: StreamState) {
    val colors = LocalAppColors.current
    val label = streamStatusLine(stream, LocalStrings.current) ?: return
    val color = when (stream) {
        StreamState.Live -> colors.muted
        is StreamState.Stale -> colors.ink2
        StreamState.Reconnecting -> colors.ink2
        is StreamState.Disconnected -> colors.warn
    }
    Chip(text = label, color = color)
}

/**
 * Карточка главного экрана: величина → состояние → плитки → действия.
 * Вспомогательные величины стоят плитками во всю ширину, входы «Почему такой
 * вывод?» и «Отпечаток места» — отдельной строкой действий.
 */
@Composable
private fun HeroCard(
    /**
     * Наименьшая высота СОДЕРЖИМОГО карточки, dp: свободная высота страницы,
     * которую карточка забирает себе. Ноль — карточка по своему содержимому.
     */
    minContentHeight: Dp = 0.dp,
    doseMicroSvH: Float?,
    errPercent: Float?,
    cps: Float?,
    trend: TrendAvailability?,
    trendWindowLabel: String?,
    doseTodayMicroSv: Double?,
    status: MonitorStatus,
    baselineState: BaselineState?,
    unit: DoseUnitSetting,
    stale: Boolean,
    /** То же состояние потока, что у чипа в шапке. */
    stream: StreamState = StreamState.Live,
    blocks: MonitorBlocks = MonitorBlocks(),
    admission: Admission = Admission.Admitted,
    frozen: Boolean = false,
    /** Красить ли главное число по отношению к обычному фону места. */
    tintEnabled: Boolean = true,
    /** Во сколько раз выше обычного цвет насыщается. */
    tintFactor: Float = DoseTint.DEFAULT_FACTOR,
    /** Порог тревоги на шкале места; null — порога нет. */
    thresholdMicroSvH: Float? = null,
    /** Где значение побывало за последнюю минуту: (минимум, максимум). */
    trail: Pair<Float, Float>? = null,
    onWhy: () -> Unit = {},
    /** Плитка накопленного открывает свой экран. */
    onOpenDose: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MonitorCatalogue.of(strings.language)
    // Цвет главного числа — отношение к обычному фону МЕСТА: от обычного до
    // заданного порога. Им же красятся дыхание и маркер шкалы: один смысл —
    // один цвет.
    val tintFraction = if (tintEnabled) {
        DoseTint.fraction(
            doseMicroSvH,
            (baselineState as? BaselineState.Active)?.baseline,
            tintFactor,
        )
    } else {
        null
    }
    val heroTint by animateColorAsState(
        targetValue = when {
            doseMicroSvH == null || stale -> colors.muted
            tintFraction == null -> colors.ink
            tintFraction <= 0f -> colors.ok
            tintFraction < 1f -> lerp(colors.warn, colors.crit, tintFraction)
            else -> colors.crit
        },
        animationSpec = Motion.normal(),
        label = "doseTint",
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.heightIn(min = minContentHeight),
            // Излишек высоты уходит НАД числом и ПОД строку состояния поровну:
            // растянутая карточка держит ту же вёрстку, только по центру.
            verticalArrangement = Arrangement.spacedBy(
                Dimens.space3,
                Alignment.CenterVertically,
            ),
        ) {
            // 1. Главная величина, по центру. За ней дышит свечение, пока
            // идут измерения ([BreathingAura]): движение здесь означает «поток
            // жив», и на замолчавшем приборе оно замирает.
            BreathingAura(live = stream.live, tint = heroTint) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Источник каждого числа назван словами в «Почему такой вывод»
                // (§21), а не метками у значений.
                Text(
                    text = strings.doseRate.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Text(
                    text = doseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                    style = type.valueHero,
                    color = heroTint,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        // Число и есть вход в разбор.
                        .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onWhy,
                        ),
                )
                // Неопределённость показания — КРИТИЧЕСКОЕ: число без неё
                // читается как точное, а на этих выдержках оно не точное.
                // Здесь стоит собственная оценка прибора; чем она не является
                // (полной неопределённостью с калибровкой и систематикой),
                // сказано в «Почему такой вывод» — это уже пояснение.
                Uncertainty.errPercentLabel(errPercent)?.let { error ->
                    Text(
                        text = error,
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                Hint(text = strings.deviceErrorNote, textAlign = TextAlign.Center)
                // Возраст последнего измерения — вторичная строка и только в
                // устойчивом состоянии.
                streamAgeLine(stream, strings)?.let { age ->
                    Text(
                        text = age,
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }

                // Шкала места: «много ли это здесь». График отвечает на другой
                // вопрос — «растёт ли», и одно другого не заменяет.
                val band = (baselineState as? BaselineState.Active)?.baseline
                PlaceScaleBar(
                    value = doseMicroSvH,
                    medianMicroSvH = band?.doseMedianMicroSvH,
                    lowMicroSvH = band?.doseLowMicroSvH,
                    highMicroSvH = band?.doseHighMicroSvH,
                    thresholdMicroSvH = thresholdMicroSvH,
                    trailLowMicroSvH = trail?.first,
                    trailHighMicroSvH = trail?.second,
                    tint = heroTint,
                    modifier = Modifier.padding(top = Dimens.space2),
                )
            }
            }

            // 2. Плитки под числом: величины, дополняющие главное число;
            // вывод словами идёт последним.
            val tiles = buildList<MetricTile> {
                // Фон — то, с чем сравнивается главное число, поэтому первая
                // плитка. Скорость счёта в выводе не участвует и живёт своей
                // карточкой ниже.
                val band = (baselineState as? BaselineState.Active)?.baseline
                add(
                    MetricTile(
                        // Заголовок плитки — одно слово, единица уходит
                        // вторичной строкой.
                        label = strings.backgroundTag,
                        // МЕДИАНА места, а не среднее: всплеск сдвигает
                        // среднее и не сдвигает медиану, и весь движок фона
                        // считает медианой (ADR 002). Пока фон собирается,
                        // плитка говорит об этом словом, а не прочерком.
                        value = band?.let { DoseFormat.rate(it.doseMedianMicroSvH, unit) }
                            ?: if (baselineState is BaselineState.Learning) {
                                t.backgroundCollecting
                            } else {
                                "—"
                            },
                    ),
                )
                if (blocks.trend) {
                    val slope = (trend as? TrendAvailability.Ready)?.result?.slopeMicroSvHPerHour
                    add(
                        MetricTile(
                            label = strings.trendPerHour,
                            value = slope?.let { TrendFit.label(it, unit) } ?: "—",
                            valueColor = trendWarnColor(slope, status),
                            // Плитка называет, чего не хватает для наклона,
                            // или окно, за которое он посчитан. Окно тренда
                            // постоянно и названо в «Почему такой вывод».
                            note = if (trend != null && slope == null) {
                                TrendFit.unavailableShort(trend)
                            } else {
                                null
                            },
                        ),
                    )
                }
                if (blocks.doseToday) {
                    add(
                        MetricTile(
                            // Единица и период — свойства числа и стоят
                            // вторичной строкой; заголовок называет величину.
                            label = strings.dose,
                            value = doseTodayMicroSv?.let { DoseFormat.dose(it, unit) } ?: "—",
                            onClick = onOpenDose,
                        ),
                    )
                }
            }
            if (tiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    for (tile in tiles) {
                        MetricTileBox(tile, Modifier.weight(1f))
                    }
                }
            }

            // 3. Состояние фона во всю ширину. Красный — только подтверждённая
            // тревога, янтарный — «выше обычного».
            val statusColor = when {
                stale || status == MonitorStatus.Unknown -> colors.muted
                status is MonitorStatus.Alert -> colors.crit
                status is MonitorStatus.AboveUsual -> colors.warn
                status is MonitorStatus.Fixed && status.above -> colors.warn
                else -> colors.ok
            }
            // Заголовков на Главной три:
            //
            //  1. «Держится выше порога» — выполнены и величина, и длительность
            //     заданного порога;
            //  2. «Повышенный уровень» — абсолютный уровень выше природного
            //     фона, независимо от места и настроек;
            //  3. «Уходите отсюда» — за час набирается годовая доза.
            //
            // Сравнение с местом живёт в справке по нажатию на числа.
            val alarmLevel = DoseAlarm.of(doseMicroSvH)
            val ownThreshold = status is MonitorStatus.Alert
            val headline = when {
                alarmLevel != DoseAlarmLevel.NONE -> DoseAlarm.headline(alarmLevel, t)
                ownThreshold -> statusHeadline(status, strings)
                else -> null
            } ?: return@Column
            val headlineColor = when (alarmLevel) {
                DoseAlarmLevel.LEAVE -> colors.crit
                DoseAlarmLevel.ELEVATED -> colors.warn
                DoseAlarmLevel.NONE -> statusColor
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
                    .clickable(onClick = onWhy)
                    .padding(vertical = Dimens.space1),
                verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Без свежих данных вывод остаётся на экране, но подписан
                // тем, к чему относится.
                if (!stream.live) {
                    Text(
                        text = t.byLastMeasurement,
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    StatusDot(headlineColor)
                    Text(
                        text = headline,
                        style = type.label,
                        color = headlineColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                // Чем измеряется заголовок: отношение к природному фону со
                // знаменателем или доза за час.
                val note = DoseAlarm.note(alarmLevel, doseMicroSvH, t)
                    ?: statusDetail(status, unit, strings).takeIf { ownThreshold }
                note?.let {
                    Text(
                        text = it,
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                // Ход сбора фона места — состояние фоновой модели, а не
                // текущее измерение: живёт в справке по нажатию на строку.
            }

        }
    }
}

/** Одна плитка под главным числом. */

@Composable
private fun trendWarnColor(trend: Float?, status: MonitorStatus): Color? {
    if (trend == null || trend <= TrendFit.FLAT_EPSILON_MICRO_SV) return null
    return when (status) {
        is MonitorStatus.AboveUsual, is MonitorStatus.Alert -> LocalAppColors.current.warn
        else -> null
    }
}

/**
 * Карточка величины на Главной — миниатюра полноэкранного графика: тот же
 * кадр, те же корзины, квантили и правила отрисовки пропусков, то же окно и то
 * же управление (щипок, перетаскивание, двойное нажатие). Отличия — размер
 * поля и одиночное нажатие, открывающее график во весь экран.
 */
@Composable
private fun MetricChartCard(
    metric: ChartMetric,
    frame: ChartFrame?,
    spanMillis: Long?,
    hasBaselineBand: Boolean,
    unit: DoseUnitSetting,
    showStats: Boolean,
    onOpen: () -> Unit,
    /** Держится ли окно живого края — от этого зависит кнопка возврата. */
    following: Boolean = true,
    onBackToNow: () -> Unit = {},
    /** Одиночное нажатие по самому полю — открыть во весь экран. */
    onOpenFromChart: () -> Unit = {},
    /** Измеренная ширина поля — по ней кадр решает, сколько колонок рисовать. */
    onPlotWidth: (Float) -> Unit = {},
    /**
     * Окно, которое видно СЕЙЧАС, внутри посчитанного кадра.
     *
     * Пока идёт жест, кадр не пересобирается: меняется только это окно, и
     * готовая картинка раскладывается по нему (`ChartGesture`).
     */
    viewWindow: ChartWindow? = null,
    onTransform: ((ChartGestureInput) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MonitorCatalogue.of(strings.language)
    val cursor = remember { mutableStateOf<Float?>(null) }
    val emptyWindowText = ChartTextCatalogue.of(strings.language).emptyWindow
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // В шапке — название картинки, длительность окна и знак
                // раскрытия. Единица читается по значениям оси.
                Text(
                    text = ChartMetrics.title(metric, strings).uppercase(),
                    style = type.label,
                    color = colors.ink,
                )
                Spacer(Modifier.weight(1f))
                // Окно названо числом, пока оно ступень лестницы. После щипка
                // окно произвольное, подписи нет, и его читают по оси времени.
                ChartWindows.spanLabel(spanMillis, ChartAxisCatalogue.of(strings.language))
                    ?.let { span ->
                        Text(text = span, style = type.footnoteMono, color = colors.ink2)
                        Spacer(Modifier.width(Dimens.space1))
                    }
                // «Сейчас» появляется, только когда график ушёл от живого края.
                if (!following) {
                    Chip(text = t.backToNow, color = colors.dataText, onClick = onBackToNow)
                    Spacer(Modifier.width(Dimens.space1))
                }
                Text(text = "⤢", style = type.label, color = colors.ink2)
            }

            if (frame == null || frame.spec.buckets.isEmpty()) {
                // «Накапливаем измерения» — про начало записи; для окна,
                // уведённого в прошлое, ответ другой.
                Text(
                    text = if (following) t.collectingMeasurements else emptyWindowText,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                DoseChart(
                    spec = frame.spec.copy(
                        viewFromMillis = viewWindow?.fromMillis,
                        viewToMillis = viewWindow?.toMillis,
                    ),
                    cursorFraction = cursor,
                    // Те же жесты и то же состояние окна, что на полноэкранном.
                    interactive = true,
                    onTransform = onTransform,
                    // Вертикаль принадлежит прокрутке страницы; ручной кадр оси
                    // карточка показывает, но не меняет.
                    verticalGestures = false,
                    onResetScale = onBackToNow,
                    // Тап по полю открывает график во весь экран.
                    onTap = onOpenFromChart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (metric == ChartMetric.DOSE) 168.dp else 132.dp)
                        .onSizeChanged { onPlotWidth(it.width.toFloat()) },
                )
                val stats = frame.stats
                // Покрытие окна и метод квантилей — подробности расчёта, их
                // место на полноэкранном графике.
                if (showStats && stats != null) {
                    StatGrid(
                        cells = listOf(
                            StatCell(ChartMetrics.format(metric, stats.min, unit), t.statMin),
                            StatCell(
                                ChartMetrics.format(metric, stats.median, unit),
                                t.statMedian,
                            ),
                            StatCell(ChartMetrics.format(metric, stats.max, unit), t.statMax),
                            StatCell(
                                ChartMetrics.format(metric, stats.sd, unit),
                                "SD",
                            ),
                            StatCell(HistoryFormat.count(stats.sampleCount), "n"),
                        ),
                    )
                }
            }

        }
    }
}


/**
 * Bluetooth выключен: состояние стоит над показаниями, рядом кнопка перехода
 * в системные настройки.
 *
 * Приложение не включает Bluetooth само — с Android 13 включение чужим кодом
 * запрещено.
 */
@Composable
private fun BluetoothBanner() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BluetoothState.isEnabled(context)) }
    // Состояние адаптера меняется снаружи приложения, поэтому оно слушается,
    // а не спрашивается один раз.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ignored: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    enabled = BluetoothState.isEnabled(context)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    if (enabled) return
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MonitorCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            StatusRow(text = t.bluetoothOffTitle, color = colors.warn)
            Hint(text = t.bluetoothOffBody, style = type.bodySmall, color = colors.ink2)
            AppButton(
                text = t.bluetoothOffAction,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.align(Alignment.End),
            )
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
    val t = MonitorCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            // Причина, по которой прибор может замолчать, — состояние, а не
            // пояснение: без неё карточка вырождается в одинокую кнопку.
            Text(
                text = t.batteryBannerBody,
                style = type.bodySmall,
                color = colors.ink2,
            )
            AppButton(
                text = t.batteryBannerAction,
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

/**
 * Доза за сегодня — единственное, что осталось отдельным запросом: она
 * считается от начала суток, а не по окну графика, и минутных корзин для неё
 * достаточно.
 */
private suspend fun loadDoseToday(graph: AppGraph): Double {
    val now = System.currentTimeMillis()
    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()
    val buckets = graph.measurementRepository.downsampledSamples(
        from = startOfDay,
        to = now,
        bucketMillis = 60_000L,
    )
    return ChartMapping.integrateDoseMicroSv(buckets)
}
