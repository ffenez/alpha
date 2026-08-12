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
import app.radiacode.ui.components.AppTab
import app.radiacode.ui.components.NavBar
import app.radiacode.ui.logic.NavConfig
import app.radiacode.ui.screens.AbExperimentScreen
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.screens.FingerprintScreen
import app.radiacode.ui.screens.HistoryScreen
import app.radiacode.ui.screens.LiveChartScreen
import app.radiacode.ui.screens.MapScreen
import app.radiacode.ui.screens.MonitorScreen
import app.radiacode.ui.screens.OnboardingScreen
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
    val settings: Boolean,
    val fingerprint: Boolean,
    val spectrogram: Boolean,
    val radon: Boolean,
    val experiments: Boolean,
    val trackMapId: Long?,
    val detailId: Long?,
    val tab: AppTab,
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
    // Какую величину показывает полноэкранный график: доза с карточки, счёт
    // или жёсткость — экран и жесты одни и те же.
    var chartMetricId by rememberSaveable { mutableStateOf(ChartMetric.DOSE.id) }
    var showLiveChart by rememberSaveable { mutableStateOf(false) }
    var showSpectrogram by rememberSaveable { mutableStateOf(false) }
    var showExperiments by rememberSaveable { mutableStateOf(false) }
    var showRadon by rememberSaveable { mutableStateOf(false) }
    var sessionDetailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var trackMapSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    // «Продолжить накопление»: snapshot id the Спектр tab merges with the live
    // stream; survives tab switches until the user turns it off.
    var continueSpectrumId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(
        enabled = showSettings || showLiveChart || showSpectrogram || showRadon ||
            showExperiments || showFingerprint || sessionDetailId != null ||
            trackMapSessionId != null,
    ) {
        when {
            showSettings -> showSettings = false
            showFingerprint -> showFingerprint = false
            showLiveChart -> showLiveChart = false
            showSpectrogram -> showSpectrogram = false
            showRadon -> showRadon = false
            showExperiments -> showExperiments = false
            trackMapSessionId != null -> trackMapSessionId = null
            else -> sessionDetailId = null
        }
    }

    // The fullscreen chart is the one screen that owns the whole display: it
    // renders above the tab bar (and handles its own system-bar insets), so
    // the plot really does fill the screen instead of a strip in the middle.
    if (showLiveChart) {
        LiveChartScreen(
            graph = graph,
            onBack = { showLiveChart = false },
            metric = ChartMetric.of(chartMetricId),
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
                    settings = showSettings,
                    fingerprint = showFingerprint,
                    spectrogram = showSpectrogram,
                    radon = showRadon,
                    experiments = showExperiments,
                    trackMapId = trackMapId,
                    detailId = detailId,
                    tab = tab,
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
                key.fingerprint -> FingerprintScreen(
                    graph = graph,
                    onBack = { showFingerprint = false },
                )
                key.spectrogram -> SpectrogramScreen(graph, onBack = { showSpectrogram = false })
                key.radon -> RadonScreen(graph, onBack = { showRadon = false })
                key.experiments -> AbExperimentScreen(
                    graph = graph,
                    onBack = { showExperiments = false },
                )
                key.trackMapId != null -> SessionTrackMapScreen(
                    graph = graph,
                    sessionId = key.trackMapId,
                    onBack = { trackMapSessionId = null },
                )
                key.detailId != null -> SessionDetailScreen(
                    graph = graph,
                    sessionId = key.detailId,
                    onBack = { sessionDetailId = null },
                    onOpenTrack = { trackMapSessionId = key.detailId },
                )
                else -> when (key.tab) {
                    AppTab.HOME -> MonitorScreen(
                        graph = graph,
                        onOpenSettings = { showSettings = true },
                        onOpenChart = {
                            chartMetricId = ChartMetric.DOSE.id
                            showLiveChart = true
                        },
                        onOpenMetricChart = { metric ->
                            chartMetricId = metric.id
                            showLiveChart = true
                        },
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
                        onOpenExperiments = { showExperiments = true },
                        continueSnapshotId = continueSpectrumId,
                        onStopContinuation = { continueSpectrumId = null },
                    )
                    AppTab.MAP -> MapScreen(graph)
                    AppTab.HISTORY -> HistoryScreen(
                        graph = graph,
                        onOpenSession = { sessionDetailId = it },
                        onContinueSpectrum = {
                            continueSpectrumId = it
                            tab = AppTab.SPECTRUM
                        },
                    )
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
                showSpectrogram = false
                showRadon = false
                showExperiments = false
                sessionDetailId = null
                trackMapSessionId = null
                tab = it
            },
        )
    }
}
