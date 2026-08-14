package app.radiacode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.radiacode.data.export.CrashLog
import app.radiacode.AppGraph
import app.radiacode.analysis.Hardness
import app.radiacode.baseline.Admission
import app.radiacode.baseline.BaselineExclusion
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.context.MeasurementContext
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.ExclusionSummary
import app.radiacode.data.MonitorBlocks
import app.radiacode.data.db.ProfileEntity
import app.radiacode.device.ConnectionState
import app.radiacode.device.DoseUnits
import app.radiacode.service.BatteryOptimization
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppIcons
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.ProfilePickerDialog
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.StatusDot
import app.radiacode.ui.components.WhySheet
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.StreamState
import app.radiacode.ui.logic.streamAgeLine
import app.radiacode.ui.logic.streamStatusLine
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.BaselineSnapshot
import app.radiacode.ui.logic.ProfileShift
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.TrendAvailability
import app.radiacode.ui.logic.TrendFit
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.WhyInput
import app.radiacode.ui.logic.learningWording
import app.radiacode.ui.logic.statusHeadline
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.ChartAxisCatalogue
import app.radiacode.ui.text.ChartTextCatalogue
import app.radiacode.ui.text.ChartAxisRu
import app.radiacode.ui.text.ChartAxisStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.MonitorCatalogue
import app.radiacode.ui.text.MonitorRu
import app.radiacode.ui.text.MonitorStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.radiacode.ui.components.DoseChart
import app.radiacode.ui.logic.ChartMetrics
import app.radiacode.ui.logic.coverageWording
import app.radiacode.ui.logic.ChartSnapshot
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartTrace
import app.radiacode.ui.logic.ChartViewport
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.ui.logic.TrendPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Как часто перечитываются графики Главной. Прибор пишет раз в секунду, но
 * колонка карточки покрывает минуты — чаще обновлять нечего, а каждый лишний
 * проход это запрос в базу.
 */
private const val CHART_REFRESH_MILLIS = 15_000L

/**
 * Доля окна, за которую картинка обязана обновиться хотя бы раз.
 * **Инженерный параметр**: 1/200 окна ≈ ширина одной колонки — обновление
 * поспевает за движением правого края, не перечитывая базу впустую.
 */
private const val LIVE_TICK_WINDOW_FRACTION = 200L

/**
 * Окно тренда на Главной — ЧАС, независимо от того, какое окно выбрано у
 * карточки графика.
 *
 * Тренд считался по окну карточки, а оно с некоторых пор общее с
 * полноэкранным графиком и запоминается: стоило выбрать там «5м», и правило
 * доступности (размах ≥10 мин) переставало выполняться НАВСЕГДА — плитка
 * показывала вечный прочерк «нужно 10 мин · есть 6 мин», хотя измерений
 * накопились часы. Величина, подписанная «Тренд/ч», обязана иметь собственное
 * названное окно, а не зависеть от того, что человек рассматривает рядом.
 */
private const val TREND_WINDOW_MILLIS = 3_600_000L

// Как это окно называется в подписи под значением — MonitorStrings.trendWindowHour.

/**
 * Ширина колонки, которую даст чтение этого окна.
 *
 * Нужна ровно для одного: понять, годится ли уже прочитанный снимок. Диапазон
 * может покрывать окно, но если ступень сменилась, колонки станут другой
 * ширины — и переиспользовать снимок значит нарисовать не тот масштаб.
 */
private fun expectedBucket(window: ChartWindow, metric: ChartMetric): Long =
    app.radiacode.ui.logic.ChartSeriesModel.bucketMillis(
        ChartWindows.loadRange(window, window.toMillis).spanMillis,
    )

/**
 * Загруженный кадр одной величины: окно и снимок ровно те же, что у
 * полноэкранного графика, поэтому тап по карточке увеличивает картинку, а не
 * заменяет её другой.
 */
@Immutable
private data class LoadedChart(
    val window: ChartWindow,
    val snapshot: ChartSnapshot,
    /**
     * Диапазон, который реально прочитан из базы (окно плюс запас).
     *
     * По нему решается, нужен ли запрос вообще: сдвиг ВНУТРИ прочитанного —
     * это перепроецирование неизменного снимка, то есть арифметика по полутора
     * сотням колонок, а не поход в базу. Без этого каждый рывок пальцем стоил
     * запроса, и отзыв на жест упирался в диск.
     */
    val loadedRange: ChartWindow,
    /**
     * Начало истории на момент загрузки — чтобы кадр мог пересчитать окно под
     * текущее «сейчас», не ходя за этим в базу каждую секунду.
     */
    val earliestMillis: Long?,
)

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
) {
    val scope = rememberCoroutineScope()
    // Живое показание берётся ИЗ ПАМЯТИ службы, а не из базы.
    //
    // Полевой дефект, который чинился трижды: «нет новых данных · 29 с» при
    // зелёном кружке связи и графики, оживающие после сворачивания. Корень —
    // не в приборе: свежесть считалась как `сейчас − метка записи`, то есть
    // через ДВА независимых повода ошибиться — базу времени прибора (она
    // измеряется по ходу сеанса и может уехать) и запись в таблицу (строку с
    // занятой меткой уникальный индекс отбрасывает молча). Вопрос «идут ли
    // данные сейчас» — это факт о ПРИХОДЕ, и отвечать на него обязан тот, кто
    // данные принял. Метка прибора осталась там, где она и означает время
    // измерения: на оси графиков.
    val live by graph.serviceStatus.lastSample.collectAsState()
    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val deviation by graph.serviceStatus.deviation.collectAsState()
    val thresholds by graph.settings.alarmThresholds
        .collectAsState(initial = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f))
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val blocks by graph.settings.monitorBlocks.collectAsState(initial = MonitorBlocks())
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val contextState by graph.contextHub.state.collectAsState()
    val admission by graph.serviceStatus.admission.collectAsState()
    val frozen by graph.settings.baselineFrozen.collectAsState(initial = false)
    val whyExpanded by graph.settings.whyCalculationsExpanded.collectAsState(initial = false)

    // 1 s wall-clock ticker drives the staleness indicator and held durations.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    // ОДИН источник свежести на весь экран: главное число, плитки, статус и
    // графики обязаны говорить одно и то же. До этого каждый считал возраст
    // сам, и экран мог одновременно сообщать «поток прерван» вверху и
    // выглядеть живым в карточках.
    val stream = StreamState.of(live?.receivedAtMillis, nowMillis, connection)
    val freshness = Freshness.of(live?.receivedAtMillis, nowMillis)

    // Графики Главной читаются тем же путём, что и полноэкранный (ADR 004):
    // одно окно, один снимок, один кадр. Величины, блоки которых выключены,
    // не читаются вовсе.
    val savedSpans by graph.settings.chartSpans.collectAsState(initial = emptyMap())
    val chartMetrics = remember(blocks.countRateChart, blocks.hardnessChart) {
        buildList {
            add(ChartMetric.DOSE)
            if (blocks.countRateChart) add(ChartMetric.COUNT_RATE)
            if (blocks.hardnessChart) add(ChartMetric.HARDNESS)
        }
    }
    // Вьюпорты живут ЗДЕСЬ, а не внутри карточки: окно, которое человек увёл
    // пальцем в прошлое, должно видеть ЗАГРУЗЧИК. Пока он грузил живое окно,
    // сдвиг уводил картинку в диапазон, который никто не читал, — карточка
    // пустела и говорила «накапливаем измерения» при полной базе.
    var viewports by remember { mutableStateOf<Map<ChartMetric, ChartViewport>>(emptyMap()) }
    // Начало истории меняется раз в жизни базы (первая запись, уборка журнала),
    // а спрашивалось раз в проход — на каждый жест по запросу на величину.
    var earliestMillis by remember { mutableStateOf<Long?>(null) }
    var charts by remember { mutableStateOf<Map<ChartMetric, LoadedChart>>(emptyMap()) }
    var trend by remember { mutableStateOf<TrendAvailability?>(null) }
    var doseTodayMicroSv by remember { mutableStateOf<Double?>(null) }
    // Цикл перечитывания графиков привязан к ЖИЗНЕННОМУ ЦИКЛУ и не может
    // умереть молча.
    //
    // Полевой дефект: при открытом экране графики через некоторое время
    // замирали, хотя измерения продолжали приходить, а сворачивание и возврат
    // немедленно показывали накопившееся. Это подпись под ошибкой ВНУТРИ цикла:
    // одно упавшее чтение убивало корутину `LaunchedEffect` навсегда, и картинка
    // застывала до пересоздания композиции. Теперь сбой чтения не рвёт цикл —
    // он записывается в журнал (тот же, что уезжает в отладочный архив) и
    // следующий проход пробует снова; а возврат на передний план запускает
    // обновление сразу, не дожидаясь очередных 15 секунд.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Обновление ведут САМИ ДАННЫЕ, а не только таймер.
    //
    // Полевой дефект: графики замирали при открытом экране, хотя измерения шли,
    // — то есть цикл по таймеру переставал доходить до чтения. Поэтому у
    // перечитывания появился второй, независимый повод: отметка времени
    // последнего отсчёта, огрублённая до шага обновления. Пока прибор пишет,
    // ключ меняется и эффект запускается заново, даже если предыдущий проход
    // где-то застрял. Если отсчётов нет — обновлять нечего по определению.
    // Шаг обновления следует ОКНУ, а не константе: на пятиминутном окне
    // колонка — секунды, и прежние 15 с читались как «график запаздывает»;
    // на месячном чаще и не нужно — перерисовывались бы те же колонки ценой
    // тяжёлого запроса. Берётся самое короткое окно из включённых величин.
    val liveTickMillis = remember(chartMetrics, savedSpans) {
        // Темп перечитывания следует ШИРИНЕ КОЛОНКИ, а не длине окна: новая
        // колонка появляется раз в её ширину, и читать чаще четверти этого
        // смысла нет. По длине окна получалось абсурдно: полуторачасовое окно
        // давало 15 с — то есть данные обновлялись раз в четверть минуты, и
        // карточка выглядела замершей при живом полноэкранном графике.
        val shortestBucket = chartMetrics.minOf { metric ->
            val window = ChartMetrics.startWindow(metric, savedSpans, System.currentTimeMillis())
            app.radiacode.ui.logic.ChartSeriesModel.bucketMillis(window.spanMillis)
        }
        ChartWindows.refreshMillis(shortestBucket)
    }
    val liveTick = live?.receivedAtMillis?.let { it / liveTickMillis }
    LaunchedEffect(chartMetrics, savedSpans, resumeTick, liveTick, viewports) {
        while (true) {
            val now = System.currentTimeMillis()
            val outcome = runCatching {
                val loadedCharts = withContext(Dispatchers.IO) {
                    // Выбранная ступень — максимум, а не обещание, что данные за
                    // неё есть: окно подтягивается к первому измерению и растёт
                    // вместе с историей. Начало истории спрашивается ОДИН раз:
                    // оно меняется первой записью и уборкой журнала, а не
                    // движением пальца.
                    val earliest = earliestMillis
                        ?: graph.measurementRepository.earliestSampleMillis()
                            ?.also { earliestMillis = it }
                    chartMetrics.associateWith { metric ->
                        val viewport = viewports[metric]
                        val chosen = viewport?.window(now)
                            ?: ChartMetrics.startWindow(metric, savedSpans, now)
                        // Подтяжка к началу истории — только у живого края:
                        // если человек сам увёл окно в прошлое, оно принадлежит
                        // ему, и двигать его границы значит отбирать управление.
                        val window = if (viewport == null || viewport.follow) {
                            ChartWindows.limitedByHistory(chosen, earliest)
                        } else {
                            chosen
                        }
                        // Сдвиг ВНУТРИ уже прочитанного диапазона запроса не
                        // требует: снимок неизменен, меняется только проекция.
                        // Именно этим жест и становится мгновенным — раньше
                        // каждый рывок пальцем упирался в диск.
                        val previous = charts[metric]
                        val reuse = previous
                            ?.takeIf { ChartWindows.covers(it.loadedRange, window) }
                            ?.takeIf { it.snapshot.bucketMillis == expectedBucket(window, metric) }
                        if (reuse != null) {
                            return@associateWith reuse.copy(window = window)
                        }
                        val load = ChartWindows.loadRange(window, now)
                        val snapshot = loadSnapshot(graph, window, metric)
                        // Трасса конвейера: три среза ОДНОГО окна — база,
                        // снимок, кадр. Считается только при РЕАЛЬНОМ чтении:
                        // это диагностика запроса, а не проекции.
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
                trend = loadedTrend
                doseTodayMicroSv = loadedDose
                // Отметка для отладочного отчёта: по ней видно, ЖИВ ли цикл
                // обновления, — без неё замерший график и работающий выглядят
                // на экране одинаково.
                graph.serviceStatus.onChartsRefreshed(System.currentTimeMillis())
            }
            outcome.exceptionOrNull()?.let { error ->
                // Отмена корутины — не сбой: она приходит при уходе с экрана.
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

    // Сравнение с эталоном места — тоже запрос, и тоже только ради «Почему?»
    // и вкладки отпечатка: считается, когда шторка открывается.
    val language = LocalStrings.current.language
    var fingerprint by remember {
        mutableStateOf<app.radiacode.analysis.FingerprintComparison?>(null)
    }
    LaunchedEffect(activeProfile?.id, showWhy, language) {
        val id = activeProfile?.id
        fingerprint = if (id == null || !showWhy) {
            null
        } else {
            app.radiacode.analysis.Fingerprint.compare(
                window = graph.fingerprintRepository.window(id),
                reference = graph.fingerprintRepository.reference(id),
                s = app.radiacode.ui.text.FingerprintCatalogue.of(language),
            )
        }
    }

    // Exclusion breakdown is a query, not a stream: it only feeds «Почему?».
    var exclusions by remember { mutableStateOf<List<ExclusionSummary>>(emptyList()) }
    LaunchedEffect(activeProfile?.id, showWhy) {
        val id = activeProfile?.id
        exclusions = if (showWhy && id != null) graph.baselineRepository.exclusions(id) else emptyList()
    }

    val doseMicroSvH = live?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
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
                text = profileChipText(activeProfile, profiles, contextState, t),
                color = colors.ink,
                onClick = { showProfilePicker = true },
            )
            Spacer(Modifier.weight(1f))
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

        HeroCard(
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
        )

        val baseline = (baselineState as? BaselineState.Active)?.baseline
        val alert = status is MonitorStatus.Alert
        for (metric in chartMetrics) key(metric) {
            val loaded = charts[metric]
            // Окно карточки — то же состояние, что у полноэкранного графика:
            // ступень, правый край и слежение за «сейчас». Пока оно жило в
            // двух местах, возможна была картина «большой живой, мелкий
            // замерший» — данные общие, край двигался у одного.
            val viewport = viewports[metric] ?: ChartViewport.atLiveEdge(
                ChartWindows.nearestPeriodIndex(
                    ChartMetrics.startWindow(metric, savedSpans, nowMillis).spanMillis,
                    ChartMetrics.periodIndices(metric),
                ),
                nowMillis,
            )
            val pinch = remember(metric) { ChartViewport.PinchAccumulator() }
            fun setViewport(next: ChartViewport) {
                viewports = viewports + (metric to next)
            }
            // Правый край кадра идёт за «сейчас» КАЖДУЮ СЕКУНДУ, не дожидаясь
            // следующего чтения базы: снимок неизменен, а окно — арифметика по
            // полутора сотням колонок. Ровно так живёт полноэкранный график, и
            // именно поэтому он выглядел живым, пока карточка казалась
            // замершей: данные у обоих были одни и те же, а край двигался
            // только у него.
            val liveSecond = nowMillis / 1_000L
            val frame = remember(loaded, unit, thresholds, baseline, alert, liveSecond, viewport) {
                loaded?.let {
                    val liveWindow = ChartWindows.limitedByHistory(
                        viewport.window(nowMillis),
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
                        // Карточка Главной ВСЕГДА живая: правый край окна и
                        // есть «сейчас», и ось подписывается от него.
                        nowMillis = nowMillis,
                        axisStrings = ChartAxisCatalogue.of(strings.language),
                        showUnit = false,
                    )
                }
            }
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
                following = viewport.follow,
                onBackToNow = { setViewport(ChartViewport.jumpToNow(viewport, nowMillis)) },
                onOpenFromChart = {
                    if (metric == ChartMetric.DOSE) onOpenChart() else onOpenMetricChart(metric)
                },
                onTransform = { panFraction, zoomFactor, _ ->
                    // Жест меняет ВРЕМЯ, а не картинку: из состояния получается
                    // окно, окно идёт в загрузку и в кадр. Готовое изображение
                    // не растягивается — иначе агрегация перестала бы отвечать
                    // масштабу, а геометрия графика у нас следует времени.
                    // Щипок приходит МНОЖИТЕЛЕМ ЗА КАДР — за событие пальцы
                    // расходятся на проценты, и порог «в полтора раза» не
                    // срабатывал никогда. Кадры копятся в накопителе.
                    setViewport(
                        ChartViewport.step(
                            ChartViewport.pan(viewport, panFraction, nowMillis),
                            pinch.add(zoomFactor),
                            nowMillis,
                        ),
                    )
                },
            )
        }

        BatteryBanner()
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
        // §7: after hours of a held deviation the app may ask whether the place
        // itself changed. It never decides that by itself — a source that stays
        // put would otherwise redefine the room it is in.
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
 * Слово о том, КАК выбран профиль, — только когда оно что-то сообщает.
 *
 * Автоматический выбор — обычное состояние приложения, и подпись «авто» рядом
 * с названием места висела всегда, ничего не добавляя. Значение имеют два
 * других случая: место закреплено рукой (иначе человек забудет, почему оно не
 * меняется) и место не подтверждено (сеть пропала). Их и говорим.
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

@Composable
private fun ConnectionChip(
    connection: ConnectionState,
    serviceRunning: Boolean,
    /**
     * Точка говорит о ДАННЫХ, а не о Bluetooth.
     *
     * Зелёный кружок рядом с надписью «нет новых данных» — прямое
     * противоречие: человек читает зелёный как «всё работает». Связь может
     * стоять, а поток не идти, и в этом случае честный цвет — янтарный.
     */
    stream: StreamState,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val (dot, text: String?) = when {
        // Модель берётся у прибора, а не вписана в код: приложение работает
        // со всей серией, и чужому прибору нельзя приписывать чужое имя.
        // Подключён — достаточно зелёной точки: модель и частота опроса не
        // меняются во время работы, и повторять их на главном экране незачем.
        // Они есть в Настройках → Прибор.
        connection is ConnectionState.Connected ->
            (if (stream.live) colors.ok else colors.warn) to null
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
 * Состояние потока одним чипом.
 *
 * В [StreamState.Live] чипа нет вовсе: молчание и есть сообщение «данные
 * идут». Секунды называются только в короткой запинке; в устойчивом состоянии
 * («связь потеряна») счётчик не растёт бесконечно — возраст уходит во
 * вторичную строку под главным числом.
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
 *
 * Порядок — это ответ на вопросы в том порядке, в каком их задают: «сколько
 * сейчас», «это обычно для этого места», «а что ещё известно», «почему так
 * решено». Раньше карточка делилась пополам: слева крупное число, справа
 * колонка «Счёт / Тренд / Сегодня» мелким шрифтом — и вход в «Почему?»
 * оказывался чипом в хвосте строки статуса, где его было не найти. Теперь
 * вспомогательные величины стоят плитками во всю ширину, а оба входа —
 * «Почему такой вывод?» и «Отпечаток места» — отдельной строкой действий.
 */
@Composable
private fun HeroCard(
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
    /** То же состояние потока, что у чипа в шапке: экран говорит одним голосом. */
    stream: StreamState = StreamState.Live,
    blocks: MonitorBlocks = MonitorBlocks(),
    admission: Admission = Admission.Admitted,
    frozen: Boolean = false,
    onWhy: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MonitorCatalogue.of(strings.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            // 1. Величина, ради которой открывают приложение. По центру: это
            // единственный элемент экрана, который читают издалека и мельком —
            // ему нужна ось симметрии, а не левый край текста.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Метки «изм. · расч. · стат.» с Главной убраны (§21): они
                // висели у каждого значения постоянно, поэтому переставали
                // читаться — а именно чтение и было их единственной задачей.
                // Источник каждого числа назван словами в «Почему такой вывод».
                Text(
                    text = strings.doseRate.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Text(
                    text = doseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                    style = type.valueHero,
                    color = if (doseMicroSvH == null || stale) colors.muted else colors.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // Возраст последнего измерения — ВТОРИЧНАЯ строка и только в
                // устойчивом состоянии: в короткой запинке секунды уже названы
                // чипом, а в живом потоке говорить не о чем.
                streamAgeLine(stream, strings)?.let { age ->
                    Text(
                        text = age,
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                // Под числом не осталось ничего: ни единицы, ни погрешности
                // прибора. Обе висели здесь постоянно, а прочитываются один
                // раз — и обе живут в «Почему такой вывод», где строка дозы
                // называет и величину, и чья это ±.
                // Чья это ± — объясняет «Почему такой вывод» (строка дозы), а
                // не постоянная подпись под главным числом: на Главной она
                // висела всегда, а прочитывается один раз. Правило прежнее
                // (одна составляющая не выдаётся за полную неопределённость),
                // изменилось только место объяснения.
            }

            // 2. Состояние фона: во всю ширину, без соседей по строке.
            // Red is reserved for the confirmed alarm; amber for «выше
            // обычного»; normal states never shout (design rule).
            val statusColor = when {
                stale || status == MonitorStatus.Unknown -> colors.muted
                status is MonitorStatus.Alert -> colors.crit
                status is MonitorStatus.AboveUsual -> colors.warn
                status is MonitorStatus.Fixed && status.above -> colors.warn
                else -> colors.ok
            }
            // Сам вывод — и есть кнопка «почему»: вопрос задают, глядя именно
            // на эту строку, и отдельная кнопка рядом с ней была лишним шагом
            // между вопросом и ответом.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
                    .clickable(onClick = onWhy)
                    .padding(vertical = Dimens.space1),
                verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Без свежих данных вывод не имеет права читаться как
                // текущий: он остаётся на экране (скрывать его — значит
                // заставить человека гадать), но подписан тем, к чему
                // относится. Одна строка на любой статус — прошедшее время у
                // каждой формулировки пришлось бы писать отдельно.
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
                    StatusDot(statusColor)
                    Text(
                        text = statusHeadline(status, strings),
                        style = type.label,
                        color = statusColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Что вывод опирается на статистику места, а не на одно
                    // показание, сказано словами в «Почему такой вывод» —
                    // метка «стат.» рядом со строкой этого не объясняла.
                }
                // Эталон вывода (P10–P90 и объём истории) переехал в «Почему
                // такой вывод»: там он стоит на первом уровне вместе со шкалой
                // P10 · медиана · P90 и строкой «использовано N ч». Требование
                // «вывод должен быть проверяем» осталось — изменилось место:
                // на Главной эта строка висела всегда, а читается один раз.
                // `statusDetail` жив и используется шторкой и отчётом.
                // «Изучаю обычный фон — 0 ч из 3» и «этот профиль не собирает
                // обычный фон» стояли рядом и противоречили друг другу: у
                // профиля с выключенным обучением прогресс не может идти. Об
                // объёме говорит только тот, кто его набирает.
                val learningOff = admission is Admission.Excluded &&
                    admission.reason == BaselineExclusion.LEARNING_OFF
                (baselineState as? BaselineState.Learning)
                    ?.takeIf { !learningOff }
                    ?.let { learning ->
                        Text(
                            text = learningWording(learning),
                            style = type.footnote,
                            color = colors.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                // Пополняется ли статистика прямо сейчас — вопрос, который
                // человек задаёт, глядя на объём истории. Молчание означало
                // «да», и это было незаметно; теперь ответ есть в обе стороны.
                Text(
                    text = admissionNote(admission, frozen, t) ?: t.usualBackgroundUpdating,
                    style = type.footnote,
                    color = if (
                        (admission is Admission.Excluded && !admissionIsDeliberate(admission)) ||
                        frozen
                    ) {
                        colors.warn
                    } else {
                        colors.muted
                    },
                    textAlign = TextAlign.Center,
                )
                // Подписи «почему такой вывод ›» нет: нажимается сама строка
                // вывода, а приглашение к нажатию занимало место под каждым
                // состоянием и повторяло то, что уже сообщает цвет ссылки.
            }

            // 3. Плитки: то, что дополняет главное число, а не спорит с ним.
            val tiles = buildList {
                add(
                    HeroTile(
                        label = t.countTile,
                        value = cps?.let { Uncertainty.cpsPlain(it) } ?: "—",
                    ),
                )
                if (blocks.trend) {
                    val slope = (trend as? TrendAvailability.Ready)?.result?.slopeMicroSvHPerHour
                    add(
                        HeroTile(
                            label = strings.trendPerHour,
                            value = slope?.let { TrendFit.label(it, unit) } ?: "—",
                            valueColor = trendWarnColor(slope, status),
                            // Прочерк без причины неотличим от поломки: плитка
                            // говорит, чего именно не хватает — или за какое
                            // окно посчитан показанный наклон.
                            // Окно тренда («за 1 ч») с плитки убрано: оно не
                            // меняется и названо в «Почему такой вывод».
                            // Причина ПРОЧЕРКА остаётся: он без объяснения
                            // неотличим от поломки.
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
                        HeroTile(
                            // Единица — в подписи, как у счёта: в значении она
                            // повторялась у каждого числа, а меняется вместе с
                            // настройкой один раз на всё приложение.
                            label = strings.doseToday + ", " +
                                DoseFormat.doseUnitLabel(unit, s = strings),
                            value = doseTodayMicroSv?.let { DoseFormat.dose(it, unit) } ?: "—",
                        ),
                    )
                }
            }
            if (tiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    for (tile in tiles) {
                        HeroTileBox(tile, Modifier.weight(1f))
                    }
                }
            }

        }
    }
}

// Что показано, когда статистика места пополняется как обычно, —
// MonitorStrings.usualBackgroundUpdating.

/** Одна плитка под главным числом. */
private data class HeroTile(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
    /** Одна тихая строка под значением: за какое окно оно или чего не хватает. */
    val note: String? = null,
)

@Composable
private fun HeroTileBox(tile: HeroTile, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
            .background(colors.surface2)
            .padding(horizontal = Dimens.space2, vertical = Dimens.space2),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tile.label.uppercase(),
            style = type.overline,
            color = colors.muted,
            maxLines = 1,
        )
        Text(
            text = tile.value,
            style = type.value,
            color = tile.valueColor ?: colors.ink,
            maxLines = 1,
        )
        tile.note?.let {
            Text(text = it, style = type.footnote, color = colors.muted, maxLines = 1)
        }
    }
}

/**
 * One line under the status when the baseline is NOT learning right now.
 * Silence means «учится» — saying that on every screen would be noise, but
 * hiding the opposite would make the statistics quietly unexplainable.
 */
private fun admissionNote(
    admission: Admission,
    frozen: Boolean,
    s: MonitorStrings = MonitorRu,
): String? = when {
    // Профиль, который фон не собирает по устройству, — это его СВОЙСТВО, а не
    // приостановка: «не пополняется» подразумевало бы, что обычно пополняется.
    admission is Admission.Excluded &&
        admission.reason == BaselineExclusion.LEARNING_OFF -> s.usualBackgroundNotCollected
    // §12: причина («карантин после отклонения», «непригодно по статистике»)
    // на Главной читалась как основной показатель прибора. Первый уровень
    // называет ОДНО состояние; какие именно измерения исключены и почему —
    // в «Почему такой вывод», куда ведёт нажатие на эту же строку вывода.
    admission is Admission.Excluded -> s.usualBackgroundNotUpdating
    frozen -> s.usualBackgroundFrozen
    else -> null
}

/** Сбой это или заданное состояние: янтарь только там, где что-то пошло не так. */
private fun admissionIsDeliberate(admission: Admission): Boolean =
    admission is Admission.Excluded && admission.reason == BaselineExclusion.LEARNING_OFF

@Composable
private fun trendWarnColor(trend: Float?, status: MonitorStatus): Color? {
    if (trend == null || trend <= TrendFit.FLAT_EPSILON_MICRO_SV) return null
    return when (status) {
        is MonitorStatus.AboveUsual, is MonitorStatus.Alert -> LocalAppColors.current.warn
        else -> null
    }
}

/**
 * Карточка величины на Главной — миниатюра полноэкранного графика.
 *
 * Это буквально тот же кадр: те же корзины, медиана и квантильные конверты,
 * тот же фон с пропусками и границей истории, то же окно и те же правила
 * честности. Раньше здесь жил свой усреднённый ряд по своим корзинам, и тап
 * по карточке ПОДМЕНЯЛ картинку другой — человеку приходилось заново искать
 * на большом графике то, что он увидел на маленьком. Теперь совпадает и
 * управление: щипок меняет ступень окна, перетаскивание уводит в прошлое,
 * двойное нажатие возвращает к «сейчас». Различие осталось одно — размер поля
 * и то, что одиночное нажатие открывает график во весь экран.
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
    onTransform: ((panFraction: Float, zoomFactor: Float, focusFraction: Float) -> Unit)? = null,
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
                // В шапке остаётся ровно то, что называет картинку, и знак
                // раскрытия. Окно («5м») читается по подписям оси времени,
                // единица — по значениям и по углу полноэкранного графика; обе
                // подписи висели здесь постоянно и спорили с самим названием.
                // Название стало крупнее: оно опознаёт график, и мельче
                // соседних подписей быть не должно.
                Text(
                    text = ChartMetrics.title(metric, strings).uppercase(),
                    style = type.label,
                    color = colors.ink,
                )
                // Единицы в шапке нет: величина названа заголовком, а числа
                // на оси значений сами показывают порядок. Подпись «мкЗв/ч»
                // рядом с «МОЩНОСТЬ ДОЗЫ» была шумом — как и «(мкрем/ч)/(имп/с)»
                // рядом с «ЖЁСТКОСТЬ», где она к тому же длиннее заголовка.
                Spacer(Modifier.weight(1f))
                // «Сейчас» появляется, только когда график ушёл от живого края:
                // пока он следит, кнопка возврата — это кнопка «ничего не
                // делать», и место она занимала бы постоянно.
                if (!following) {
                    Chip(text = t.backToNow, color = colors.dataText, onClick = onBackToNow)
                    Spacer(Modifier.width(Dimens.space1))
                }
                // Tap affordance: the card opens the fullscreen live chart.
                Text(text = "⤢", style = type.label, color = colors.ink2)
            }

            if (frame == null || frame.spec.buckets.isEmpty()) {
                // «Накапливаем измерения» — про НАЧАЛО записи, и говорить это
                // человеку, который сам увёл окно в прошлое, неправда: там
                // измерений не было, а не «ещё не набралось». Ответ зависит от
                // того, кто выбрал окно.
                Text(
                    text = if (following) t.collectingMeasurements else emptyWindowText,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                DoseChart(
                    spec = frame.spec,
                    cursorFraction = cursor,
                    // Щипок меняет ступень, перетаскивание уводит в прошлое,
                    // двойное нажатие возвращает к «сейчас» — те же жесты, что
                    // на полноэкранном, и то же состояние окна за ними.
                    interactive = true,
                    onTransform = onTransform,
                    onResetScale = onBackToNow,
                    // Тап по самому графику открывает его во весь экран: пока
                    // поле принимало жесты, нажатие на него не доходило до
                    // карточки, и открыть график можно было только мимо него.
                    onTap = onOpenFromChart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (metric == ChartMetric.DOSE) 168.dp else 132.dp),
                )
                val stats = frame.stats
                // Ни одной пояснительной строки под миниатюрой: покрытие окна и
                // метод квантилей — подробности РАСЧЁТА, их место на
                // полноэкранном графике, где эти числа изучают. Карточка
                // Главной существует ради взгляда мельком.
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
                                "SD, ${ChartMetrics.unitLabel(metric, unit)}",
                            ),
                            StatCell(HistoryFormat.count(stats.sampleCount), "n"),
                        ),
                    )
                }
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
    val t = MonitorCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
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
