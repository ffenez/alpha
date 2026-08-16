package app.radiacode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.radiacode.AppGraph
import app.radiacode.service.MeasurementService
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import app.radiacode.ui.theme.Motion
import app.radiacode.ui.components.ProvideSwipeBusy
import app.radiacode.ui.components.TabPager
import app.radiacode.ui.components.AppTab
import app.radiacode.ui.components.NavBar
import app.radiacode.ui.logic.NavConfig
import app.radiacode.ui.screens.DoseScreen
import app.radiacode.ui.screens.AbExperimentScreen
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.logic.ChartRange
import app.radiacode.ui.screens.FingerprintScreen
import app.radiacode.ui.screens.FoodScreen
import app.radiacode.ui.screens.HistoryScreen
import app.radiacode.ui.logic.SpectrumViewOptions
import app.radiacode.ui.screens.LiveChartScreen
import app.radiacode.ui.screens.MapScreen
import app.radiacode.ui.screens.MonitorScreen
import app.radiacode.ui.screens.OnboardingScreen
import app.radiacode.ui.screens.NuclideTrendScreen
import app.radiacode.ui.screens.RadonScreen
import app.radiacode.ui.screens.SearchScreen
import app.radiacode.ui.screens.SessionDetailScreen
import app.radiacode.ui.screens.SessionTrackMapScreen
import app.radiacode.ui.screens.SettingsScreen
import app.radiacode.ui.screens.SpectrogramScreen
import app.radiacode.ui.screens.SpectrumScreen
import app.radiacode.ui.theme.LocalAppColors

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
    var fullSpectrumSmoothing by rememberSaveable { mutableStateOf(false) }
    var fullSpectrumFromKeV by rememberSaveable { mutableStateOf(0f) }
    var fullSpectrumToKeV by rememberSaveable { mutableStateOf(0f) }
    // Отметка линии — часть того же вида: её поставили, чтобы рассмотреть место
    // на шкале, и полный экран открывают ровно за этим.
    var fullSpectrumHighlightKeV by rememberSaveable { mutableStateOf(0f) }
    val openFullSpectrum: (SpectrumViewOptions) -> Unit = { options ->
        fullSpectrumMinusBackground = options.minusBackground
        fullSpectrumSmoothing = options.smoothing
        fullSpectrumFromKeV = options.startKeV
        fullSpectrumToKeV = options.endKeV
        fullSpectrumHighlightKeV = options.highlightKeV
        fullSpectrum = true
    }
    var showSpectrogram by rememberSaveable { mutableStateOf(false) }
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

    BackHandler(
        enabled = showSettings || showLiveChart || fullSpectrum || showSpectrogram || showRadon ||
            showLineTrend || showDose ||
            showExperiments || showFood || showFingerprint || sessionDetailId != null ||
            trackMapSessionId != null || spectrumSnapshotId != null,
    ) {
        when {
            showSettings -> showSettings = false
            fullSpectrum -> fullSpectrum = false
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
                smoothing = fullSpectrumSmoothing,
                startKeV = fullSpectrumFromKeV,
                endKeV = fullSpectrumToKeV,
                highlightKeV = fullSpectrumHighlightKeV,
            ),
            onCloseFullscreen = { fullSpectrum = false },
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
            range = if (from != null && to != null) ChartRange(from, to) else null,
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
                )
                key.fingerprint -> FingerprintScreen(
                    graph = graph,
                    onBack = { showFingerprint = false },
                )
                key.spectrogram -> SpectrogramScreen(graph, onBack = { showSpectrogram = false })
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
                AppTab.HOME -> MonitorScreen(
                    graph = graph,
                    onOpenSettings = { showSettings = true },
                    onOpenChart = {
                        chartMetricId = ChartMetric.DOSE.id
                        // С Главной график живой: диапазон снимается.
                        chartRangeFrom = null
                        chartRangeTo = null
                        showLiveChart = true
                    },
                    onOpenMetricChart = { metric ->
                        chartMetricId = metric.id
                        chartRangeFrom = null
                        chartRangeTo = null
                        showLiveChart = true
                    },
                    onOpenDose = { showDose = true },
                )
                AppTab.SEARCH -> SearchScreen(
                    graph = graph,
                    // §13 of the search redesign: a confirmed excursion
                    // whose *spectral shape* also changed may invite the
                    // user to the spectrum. Nothing is carried across —
                    // the spectrum tab shows its own live accumulation.
                    onOpenSpectrum = { tab = AppTab.SPECTRUM },
                    // «Отпечаток места» спрашивает то же, что и Поиск, но
                    // про место целиком, а не про сейчас — поэтому вход
                    // живёт здесь, а не на Главной.
                    onOpenFingerprint = { showFingerprint = true },
                )
                AppTab.SPECTRUM -> SpectrumScreen(
                    graph = graph,
                    onOpenSpectrogram = { showSpectrogram = true },
                    onOpenRadon = { showRadon = true },
                    onOpenLineTrend = { showLineTrend = true },
                    onOpenExperiments = { showExperiments = true },
                    onOpenFood = { showFood = true },
                    continueSnapshotId = continueSpectrumId,
                    onStopContinuation = { continueSpectrumId = null },
                    onOpenFullscreen = openFullSpectrum,
                )
                AppTab.MAP -> MapScreen(graph)
                AppTab.HISTORY -> HistoryScreen(
                    graph = graph,
                    onOpenSession = { sessionDetailId = it },
                    onOpenChart = { from, to ->
                        chartMetricId = ChartMetric.DOSE.id
                        chartRangeFrom = from
                        chartRangeTo = to
                        showLiveChart = true
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
                showSettings = false
                showLiveChart = false
                showFood = false
                showSpectrogram = false
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
