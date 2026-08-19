package app.alpha.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.alpha.AppGraph
import app.alpha.service.MeasurementService
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import app.alpha.ui.theme.Motion
import app.alpha.ui.components.ProvideSwipeBusy
import app.alpha.ui.components.TabPager
import app.alpha.ui.components.AppTab
import app.alpha.ui.components.NavBar
import app.alpha.ui.logic.NavConfig
import app.alpha.ui.screens.DoseScreen
import app.alpha.ui.screens.AbExperimentScreen
import app.alpha.ui.logic.ChartMetric
import app.alpha.ui.chart.ChartContexts
import app.alpha.ui.screens.FingerprintScreen
import app.alpha.ui.screens.FoodScreen
import app.alpha.ui.screens.HistoryScreen
import app.alpha.ui.logic.SpectrumViewOptions
import app.alpha.ui.screens.LiveChartScreen
import app.alpha.ui.screens.MapFocus
import app.alpha.ui.screens.MapScreen
import app.alpha.ui.screens.InstrumentScreen
import app.alpha.ui.screens.OnboardingScreen
import app.alpha.ui.screens.NuclideTrendScreen
import app.alpha.ui.screens.RadonScreen
import app.alpha.ui.screens.SessionDetailScreen
import app.alpha.ui.screens.SessionTrackMapScreen
import app.alpha.ui.screens.SettingsScreen
import app.alpha.ui.screens.SpectrogramScreen
import app.alpha.ui.screens.SpectrogramViewOptions
import app.alpha.ui.screens.SpectrumScreen
import app.alpha.ui.theme.LocalAppColors

/** Remembered-device lookup: distinguishes "loading" from "no device yet". */
private sealed interface RememberedDevice {
    data object Loading : RememberedDevice
    data class Loaded(val address: String?) : RememberedDevice
}

/**
 * Single-activity root. A remembered device routes straight to the tabbed
 * app (and resumes the measurement service); otherwise onboarding runs and
 * the first successful connect flips this switch automatically, because the
 * service persists the address into settings.
 */
/** Что именно показано сейчас — ключ перехода между экранами. */
private data class ScreenKey(
    val spectrumSnapshotId: Long?,
    val settings: Boolean,
    val fingerprint: Boolean,
    val spectrogram: Boolean,
    val radon: Boolean,
    val lineTrend: Boolean,
    val experiments: Boolean,
    val food: Boolean,
    val trackMapId: Long?,
    val detailId: Long?,
)

@Composable
fun AppRoot(graph: AppGraph) {
    val colors = LocalAppColors.current
    var remembered by remember { mutableStateOf<RememberedDevice>(RememberedDevice.Loading) }
    LaunchedEffect(graph) {
        graph.settings.lastDeviceAddress.collect { remembered = RememberedDevice.Loaded(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        when (val state = remembered) {
            RememberedDevice.Loading -> Unit // bg-colored frame, resolves in ms
            is RememberedDevice.Loaded ->
                if (state.address == null) {
                    OnboardingScreen(graph)
                } else {
                    MainScaffold(graph)
                }
        }
    }
}

@Composable
private fun MainScaffold(graph: AppGraph) {
    // Замок горизонтального жеста общий на всё приложение: карту открывают и
    // из Истории, а пейджер живёт здесь.
    ProvideSwipeBusy { MainScaffoldContent(graph) }
}

@Composable
private fun MainScaffoldContent(graph: AppGraph) {
    val context = LocalContext.current
    // Keep the measurement service alive (no-op if it already runs).
    LaunchedEffect(Unit) {
        ContextCompat.startForegroundService(context, MeasurementService.resumeIntent(context))
    }

    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    // Customized bottom nav (Настройки → Интерфейс); hiding the current tab
    // falls back to Главная.
    val navRaw by graph.settings.navTabsRaw.collectAsState(initial = null)
    val navTabs = remember(navRaw) { NavConfig.tabsForBar(NavConfig.parse(navRaw)) }
    LaunchedEffect(navTabs) {
        if (tab !in navTabs) tab = AppTab.HOME
    }
    // Overlays above the tab content; Settings opens separately (SPEC, gear
    // on Монитор), a session detail comes from История and can open its
    // track map on top. Back and tab switches dismiss them.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showFingerprint by rememberSaveable { mutableStateOf(false) }
    var showFood by rememberSaveable { mutableStateOf(false) }
    // Какую величину показывает полноэкранный график: доза с карточки, счёт
    // или жёсткость — экран и жесты одни и те же.
    var chartMetricId by rememberSaveable { mutableStateOf(ChartMetric.DOSE.id) }
    var showLiveChart by rememberSaveable { mutableStateOf(false) }
    // Полноэкранный график, открытый для ДИАПАЗОНА сессии: границы живут
    // здесь, потому что экран графика один и тот же, а край времени у него
    // может быть либо «сейчас», либо конец этой сессии.
    // Откуда открыт полноэкранный график: от этого зависят край времени,
    // подпись чипа возврата и то, с чем сравнивает курсор (`ChartContext`).
    var chartContextId by rememberSaveable { mutableStateOf(ChartContexts.LIVE) }
    var chartRangeFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var chartRangeTo by rememberSaveable { mutableStateOf<Long?>(null) }
    // Снимок спектра из Истории, открытый на просмотр.
    var spectrumSnapshotId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Полноэкранный спектр (тап по самому графику). Состояние живёт здесь по
    // той же причине, что и у полноэкранного графика: поле обязано рисоваться
    // ПОВЕРХ таб-бара, а не полосой над ним. Вместе с флагом уезжает и вид, в
    // котором по графику тапнули, — иначе полный экран подменил бы картинку.
    var fullSpectrum by rememberSaveable { mutableStateOf(false) }
    var fullSpectrumMinusBackground by rememberSaveable { mutableStateOf(false) }
    var fullSpectrumOverlayBackground by rememberSaveable { mutableStateOf(false) }
    var fullSpectrumSmoothing by rememberSaveable { mutableStateOf(false) }
    var fullSpectrumFromKeV by rememberSaveable { mutableStateOf(0f) }
    var fullSpectrumToKeV by rememberSaveable { mutableStateOf(0f) }
    // Отметка линии — часть того же вида: её поставили, чтобы рассмотреть место
    // на шкале, и полный экран открывают ровно за этим.
    var fullSpectrumHighlightKeV by rememberSaveable { mutableStateOf(0f) }
    val openFullSpectrum: (SpectrumViewOptions) -> Unit = { options ->
        fullSpectrumMinusBackground = options.minusBackground
        fullSpectrumOverlayBackground = options.overlayBackground
        fullSpectrumSmoothing = options.smoothing
        fullSpectrumFromKeV = options.startKeV
        fullSpectrumToKeV = options.endKeV
        fullSpectrumHighlightKeV = options.highlightKeV
        fullSpectrum = true
    }
    // Место превышения, о котором спросили из Истории: карта открывается на нём.
    var mapFocusLat by rememberSaveable { mutableStateOf(0.0) }
    var mapFocusLon by rememberSaveable { mutableStateOf(0.0) }
    val mapFocus = if (mapFocusLat != 0.0 || mapFocusLon != 0.0) {
        MapFocus(mapFocusLat, mapFocusLon)
    } else {
        null
    }

    var showSpectrogram by rememberSaveable { mutableStateOf(false) }
    // Полный экран спектрограммы — тот же приём, что у спектра: поле владеет
    // дисплеем, поэтому режим живёт здесь, выше таб-бара. Вид (окно и режим)
    // хранится рядом: человек тапнул по тому, что видел, и увидеть обязан то
    // же самое, только крупнее.
    var spectrogramFull by rememberSaveable { mutableStateOf(false) }
    var spectrogramWindow by rememberSaveable { mutableStateOf(0L) }
    var spectrogramShape by rememberSaveable { mutableStateOf(false) }
    val spectrogramOptions = SpectrogramViewOptions(
        windowMillis = spectrogramWindow,
        shapeMode = spectrogramShape,
    )
    val onSpectrogramOptions: (SpectrogramViewOptions) -> Unit = { next ->
        spectrogramWindow = next.windowMillis
        spectrogramShape = next.shapeMode
    }
    var showExperiments by rememberSaveable { mutableStateOf(false) }
    var showRadon by rememberSaveable { mutableStateOf(false) }
    var showLineTrend by rememberSaveable { mutableStateOf(false) }
    // «Сколько набралось» — свой экран, а не блок в Истории: спрашивают о нём
    // редко и с Главной, где и стоит число за сегодня.
    var showDose by rememberSaveable { mutableStateOf(false) }
    var sessionDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var trackMapSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    // «Продолжить накопление»: snapshot id the Спектр tab merges with the live
    // stream; survives tab switches until the user turns it off.
    var continueSpectrumId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Отклик поиска живёт на всех экранах: прибор ведут на слух и на ощупь, и
    // уход с одного экрана на другой не имеет права его обрывать. Глохнет он
    // вместе с интерфейсом — щёлкать в кармане при заблокированном экране
    // никто не просил.
    val feedback = graph.feedbackHub
    val feedbackOwner = LocalLifecycleOwner.current
    DisposableEffect(feedbackOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> feedback.start()
                Lifecycle.Event.ON_STOP -> feedback.stop()
                else -> Unit
            }
        }
        feedbackOwner.lifecycle.addObserver(observer)
        onDispose {
            feedbackOwner.lifecycle.removeObserver(observer)
            feedback.stop()
        }
    }

    BackHandler(
        enabled = showSettings || showLiveChart || fullSpectrum || showSpectrogram || showRadon ||
            showLineTrend || showDose ||
            showExperiments || showFood || showFingerprint || sessionDetailId != null ||
            trackMapSessionId != null || spectrumSnapshotId != null,
    ) {
        when {
            showSettings -> showSettings = false
            fullSpectrum -> fullSpectrum = false
            spectrogramFull -> spectrogramFull = false
            showFood -> showFood = false
            showFingerprint -> showFingerprint = false
            showDose -> showDose = false
            showLiveChart -> showLiveChart = false
            spectrumSnapshotId != null -> spectrumSnapshotId = null
            showSpectrogram -> showSpectrogram = false
            showRadon -> showRadon = false
            showLineTrend -> showLineTrend = false
            showExperiments -> showExperiments = false
            trackMapSessionId != null -> trackMapSessionId = null
            else -> sessionDetailId = null
        }
    }

    // The fullscreen chart is the one screen that owns the whole display: it
    // renders above the tab bar (and handles its own system-bar insets), so
    // the plot really does fill the screen instead of a strip in the middle.
    // Полноэкранный спектр — тот же приём, что у полноэкранного графика:
    // экран владеет дисплеем целиком. Данные берёт ТОТ ЖЕ `SpectrumScreen`
    // (снимок из Истории или живое накопление), поэтому источник спектра
    // выбирается одним правилом на оба режима.
    if (fullSpectrum) {
        SpectrumScreen(
            graph = graph,
            snapshotId = spectrumSnapshotId,
            continueSnapshotId = continueSpectrumId,
            fullscreenOptions = SpectrumViewOptions(
                minusBackground = fullSpectrumMinusBackground,
                overlayBackground = fullSpectrumOverlayBackground,
                smoothing = fullSpectrumSmoothing,
                startKeV = fullSpectrumFromKeV,
                endKeV = fullSpectrumToKeV,
                highlightKeV = fullSpectrumHighlightKeV,
            ),
            onCloseFullscreen = { fullSpectrum = false },
        )
        return
    }

    // Спектрограмма во весь экран: как и полноэкранный спектр, она рисуется
    // поверх таб-бара — иначе «во весь экран» означало бы полосу посередине.
    if (showSpectrogram && spectrogramFull) {
        SpectrogramScreen(
            graph = graph,
            onBack = { spectrogramFull = false },
            options = spectrogramOptions,
            onOptionsChange = onSpectrogramOptions,
            fullscreen = true,
        )
        return
    }

    if (showDose) {
        DoseScreen(graph = graph, onBack = { showDose = false })
        return
    }

    if (showLiveChart) {
        val from = chartRangeFrom
        val to = chartRangeTo
        LiveChartScreen(
            graph = graph,
            onBack = { showLiveChart = false },
            metric = ChartMetric.of(chartMetricId),
            // Диапазон есть — график стоит на прошлом; нет — едет за живым
            // краем. Экран один, различается только край.
            context = ChartContexts.of(chartContextId, from, to),
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            val detailId = sessionDetailId
            val trackMapId = trackMapSessionId
            // Смена экрана — единственное место, где движение заметно само по
            // себе: без него переход читается как подмена картинки. Данные
            // внутри не анимируются никогда (см. ui/theme/Motion.kt).
            AnimatedContent(
                targetState = ScreenKey(
                    spectrumSnapshotId = spectrumSnapshotId,
                    settings = showSettings,
                    fingerprint = showFingerprint,
                    spectrogram = showSpectrogram,
                    radon = showRadon,
                    lineTrend = showLineTrend,
                    experiments = showExperiments,
                    food = showFood,
                    trackMapId = trackMapId,
                    detailId = detailId,
                ),
                // Fade through: уходящее гаснет быстро и первым, приходящее
                // проявляется и подрастает с 96 % — переход читается как смена
                // содержания, а не как сдвиг холста.
                transitionSpec = {
                    (
                        fadeIn(Motion.screen()) +
                            scaleIn(Motion.screen(), initialScale = 0.96f)
                        ) togetherWith fadeOut(tween(Motion.SCREEN_EXIT_MILLIS))
                },
                label = "screen",
            ) { key ->
            // Экран выбирается по КЛЮЧУ перехода, а не по внешнему состоянию:
            // иначе уходящий кадр перерисовался бы уже новым экраном.
            when {
                key.settings -> SettingsScreen(graph, onBack = { showSettings = false })
                // Снимок спектра открывается ТЕМ ЖЕ экраном Спектра — поверх
                // вкладок, чтобы «назад» возвращало в Историю, а не на вкладку.
                key.spectrumSnapshotId != null -> SpectrumScreen(
                    graph = graph,
                    snapshotId = key.spectrumSnapshotId,
                    onBack = { spectrumSnapshotId = null },
                    onOpenFullscreen = openFullSpectrum,
                    // «Продолжить накопление» из «⋮» снимка ведёт туда же, куда
                    // вело из Истории: на вкладку Спектра, поверх этого снимка.
                    onContinueSnapshot = { id ->
                        spectrumSnapshotId = null
                        continueSpectrumId = id
                        tab = AppTab.SPECTRUM
                    },
                )
                key.fingerprint -> FingerprintScreen(
                    graph = graph,
                    onBack = { showFingerprint = false },
                )
                key.spectrogram -> SpectrogramScreen(
                    graph = graph,
                    onBack = { showSpectrogram = false },
                    options = spectrogramOptions,
                    onOptionsChange = onSpectrogramOptions,
                    onOpenFullscreen = { spectrogramFull = true },
                )
                key.radon -> RadonScreen(graph, onBack = { showRadon = false })
                key.lineTrend -> NuclideTrendScreen(graph, onBack = { showLineTrend = false })
                key.food -> FoodScreen(graph, onBack = { showFood = false })
                key.experiments -> AbExperimentScreen(
                    graph = graph,
                    onBack = { showExperiments = false },
                )
                key.trackMapId != null -> SessionTrackMapScreen(
                    graph = graph,
                    sessionId = key.trackMapId,
                    onBack = { trackMapSessionId = null },
                    onOpenChart = { from, to ->
                        chartMetricId = ChartMetric.DOSE.id
                        // Открыто со следа на карте: это маршрут, и чип
                        // возврата обязан называть его маршрутом.
                        chartContextId = ChartContexts.ROUTE
                        chartRangeFrom = from
                        chartRangeTo = to
                        showLiveChart = true
                    },
                )
                key.detailId != null -> SessionDetailScreen(
                    graph = graph,
                    sessionId = key.detailId,
                    onBack = { sessionDetailId = null },
                    onOpenTrack = { trackMapSessionId = key.detailId },
                    onOpenChart = { from, to ->
                        chartMetricId = ChartMetric.DOSE.id
                        chartContextId = ChartContexts.SESSION
                        chartRangeFrom = from
                        chartRangeTo = to
                        showLiveChart = true
                    },
                )
                else -> TabPager(
                    tabs = navTabs,
                    selected = tab,
                    onSelected = { tab = it },
                    // Карту двигают пальцем: свайп вкладок отбирал у неё каждое
                    // движение вбок.
                    swipeDisabledOn = setOf(AppTab.MAP),
                ) { pageTab ->
                    when (pageTab) {
                AppTab.HOME -> InstrumentScreen(
                    graph = graph,
                    onOpenSettings = { showSettings = true },
                    onOpenChart = {
                        chartMetricId = ChartMetric.DOSE.id
                        // Из наблюдения график живой: диапазон снимается.
                        chartContextId = ChartContexts.LIVE
                        chartRangeFrom = null
                        chartRangeTo = null
                        showLiveChart = true
                    },
                    onOpenMetricChart = { metric ->
                        chartMetricId = metric.id
                        chartContextId = ChartContexts.LIVE
                        chartRangeFrom = null
                        chartRangeTo = null
                        showLiveChart = true
                    },
                    onOpenDose = { showDose = true },
                    // §13 of the search redesign: a confirmed excursion whose
                    // *spectral shape* also changed may invite the user to the
                    // spectrum. Nothing is carried across — the spectrum tab
                    // shows its own live accumulation.
                    onOpenSpectrum = { tab = AppTab.SPECTRUM },
                    // «Отпечаток места» спрашивает то же, что и Проверка, но
                    // про место целиком, а не про сейчас.
                    onOpenFingerprint = { showFingerprint = true },
                    // Лента поиска и полноэкранный график — одна величина в
                    // двух размерах: тап открывает счёт во весь экран.
                    onOpenSearchChart = {
                        chartMetricId = ChartMetric.COUNT_RATE.id
                        // Открыто из поиска: курсор там сравнивает с
                        // записанным фоном поиска.
                        chartContextId = ChartContexts.SEARCH
                        chartRangeFrom = null
                        chartRangeTo = null
                        showLiveChart = true
                    },
                )
                AppTab.SPECTRUM -> SpectrumScreen(
                    graph = graph,
                    onOpenImported = { spectrumSnapshotId = it },
                    onOpenSpectrogram = { showSpectrogram = true },
                    onOpenRadon = { showRadon = true },
                    onOpenLineTrend = { showLineTrend = true },
                    onOpenExperiments = { showExperiments = true },
                    onOpenFood = { showFood = true },
                    continueSnapshotId = continueSpectrumId,
                    onStopContinuation = { continueSpectrumId = null },
                    onOpenFullscreen = openFullSpectrum,
                )
                AppTab.MAP -> MapScreen(graph, focus = mapFocus)
                AppTab.HISTORY -> HistoryScreen(
                    graph = graph,
                    onOpenSession = { sessionDetailId = it },
                    onOpenChart = { from, to ->
                        chartMetricId = ChartMetric.DOSE.id
                        chartContextId = ChartContexts.SESSION
                        chartRangeFrom = from
                        chartRangeTo = to
                        showLiveChart = true
                    },
                    onOpenPlace = { latitude, longitude ->
                        mapFocusLat = latitude
                        mapFocusLon = longitude
                        tab = AppTab.MAP
                    },
                    onOpenSpectrum = { spectrumSnapshotId = it },
                    onContinueSpectrum = {
                        continueSpectrumId = it
                        tab = AppTab.SPECTRUM
                    },
                )
                    }
                }
            }
            }
        }
        NavBar(
            tabs = navTabs,
            selected = tab,
            onSelect = {
                if (it != AppTab.MAP) {
                    mapFocusLat = 0.0
                    mapFocusLon = 0.0
                }
                showSettings = false
                showLiveChart = false
                showFood = false
                showSpectrogram = false
                spectrogramFull = false
                showRadon = false
                showLineTrend = false
                showExperiments = false
                sessionDetailId = null
                trackMapSessionId = null
                spectrumSnapshotId = null
                tab = it
            },
        )
    }
}
