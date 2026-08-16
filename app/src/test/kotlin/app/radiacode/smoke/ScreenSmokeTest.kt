package app.radiacode.smoke

import androidx.compose.ui.test.junit4.createComposeRule
import app.radiacode.AppGraph
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.chart.ChartContext
import app.radiacode.ui.logic.ChartRange
import app.radiacode.ui.logic.SearchMode
import app.radiacode.ui.logic.SpectrumViewOptions
import app.radiacode.ui.screens.AbExperimentScreen
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
import app.radiacode.ui.screens.SpectrogramScreen
import app.radiacode.ui.screens.SpectrumCompareScreen
import app.radiacode.ui.screens.SpectrumScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Смоук: каждый экран приложения открывается и не падает — в пустой базе, в
 * засеянной базе и всегда в состоянии «прибор не подключён» (свежий
 * ServiceStatus). Класс дефектов, ради которого он существует, невидим чистым
 * JVM-тестам: поведение Android-разборщика XML, measure/layout вложенных
 * прокруток, NaN, доезжающий до канвы.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenSmokeTest(variantId: String) {

    private val variant = UiVariant.of(variantId)

    private val graphs = mutableListOf<AppGraph>()

    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = UiVariant.parameters()
    }

    private fun emptyGraph(): AppGraph = Smoke.graph().also { graphs += it }

    private fun seededGraph(): Pair<AppGraph, Smoke.Seeded> {
        val graph = emptyGraph()
        return graph to Smoke.seed(graph)
    }

    @After
    fun closeDatabases() {
        graphs.forEach { runCatching { it.database.close() } }
    }

    // --- Монитор ---

    @Test
    fun monitor_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { MonitorScreen(graph) }
    }

    @Test
    fun monitor_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { MonitorScreen(graph) }
    }

    // --- Поиск: оба режима ---

    @Test
    fun search_verify_empty() {
        val graph = emptyGraph()
        runBlocking { graph.settings.setSearchMode(SearchMode.VERIFY.id) }
        compose.showScreen(variant) { SearchScreen(graph) }
    }

    @Test
    fun search_verify_seeded() {
        val (graph, _) = seededGraph()
        runBlocking { graph.settings.setSearchMode(SearchMode.VERIFY.id) }
        compose.showScreen(variant) { SearchScreen(graph) }
    }

    @Test
    fun search_navigate_seeded() {
        val (graph, _) = seededGraph()
        runBlocking { graph.settings.setSearchMode(SearchMode.NAVIGATE.id) }
        compose.showScreen(variant) { SearchScreen(graph) }
    }

    // --- Спектр: живой, снимок, полный экран ---

    @Test
    fun spectrum_live_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { SpectrumScreen(graph) }
    }

    @Test
    fun spectrum_live_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { SpectrumScreen(graph) }
    }

    @Test
    fun spectrum_snapshot_seeded() {
        val (graph, seeded) = seededGraph()
        compose.showScreen(variant) {
            SpectrumScreen(graph, snapshotId = seeded.spectrumId, onBack = {})
        }
    }

    @Test
    fun spectrum_fullscreen_seeded() {
        val (graph, seeded) = seededGraph()
        compose.showScreen(variant) {
            SpectrumScreen(
                graph,
                snapshotId = seeded.spectrumId,
                fullscreenOptions = SpectrumViewOptions(),
                onCloseFullscreen = {},
            )
        }
    }

    // --- Спектрограмма ---

    @Test
    fun spectrogram_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { SpectrogramScreen(graph, onBack = {}) }
    }

    @Test
    fun spectrogram_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { SpectrogramScreen(graph, onBack = {}) }
    }

    // --- История и деталка сессии ---

    @Test
    fun history_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { HistoryScreen(graph, onOpenSession = {}) }
    }

    @Test
    fun history_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { HistoryScreen(graph, onOpenSession = {}) }
    }

    @Test
    fun session_detail_seeded() {
        val (graph, seeded) = seededGraph()
        compose.showScreen(variant) {
            SessionDetailScreen(graph, sessionId = seeded.sessionId, onBack = {})
        }
    }

    // --- Полноэкранный график: три величины, живой и диапазон ---

    @Test
    fun live_chart_dose_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { LiveChartScreen(graph, onBack = {}) }
    }

    @Test
    fun live_chart_count_rate_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) {
            LiveChartScreen(graph, onBack = {}, metric = ChartMetric.COUNT_RATE)
        }
    }

    @Test
    fun live_chart_hardness_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) {
            LiveChartScreen(graph, onBack = {}, metric = ChartMetric.HARDNESS)
        }
    }

    @Test
    fun live_chart_session_range_seeded() {
        val (graph, seeded) = seededGraph()
        val now = System.currentTimeMillis()
        compose.showScreen(variant) {
            LiveChartScreen(
                graph,
                onBack = {},
                context = ChartContext.Session(ChartRange(now - 150_000L, now)),
            )
        }
    }

    // --- A/B, радон, компаратор, отпечаток, онбординг ---

    @Test
    fun ab_experiment_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { AbExperimentScreen(graph, onBack = {}) }
    }

    @Test
    fun ab_experiment_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { AbExperimentScreen(graph, onBack = {}) }
    }

    @Test
    fun radon_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { RadonScreen(graph, onBack = {}) }
    }

    @Test
    fun radon_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { RadonScreen(graph, onBack = {}) }
    }

    @Test
    fun spectrum_compare_seeded() {
        val (graph, seeded) = seededGraph()
        compose.showScreen(variant) {
            SpectrumCompareScreen(graph, seeded.spectrumId, seeded.secondSpectrumId, onBack = {})
        }
    }

    @Test
    fun spectrum_compare_missing_ids() {
        val graph = emptyGraph()
        compose.showScreen(variant) { SpectrumCompareScreen(graph, 404L, 405L, onBack = {}) }
    }

    @Test
    fun fingerprint_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { FingerprintScreen(graph, onBack = {}) }
    }

    @Test
    fun fingerprint_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { FingerprintScreen(graph, onBack = {}) }
    }

    @Test
    fun onboarding_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { OnboardingScreen(graph) }
    }

    // --- Карта ---
    //
    // osmdroid рисуется через AndroidView поверх обычной канвы, собственной
    // GL-поверхности у него нет (ADR 003), поэтому под Robolectric карта
    // пробуется честно. Если MapView не заведётся в песочнице — исключить
    // именно эти два теста с причиной, остальной смоук от карты не зависит.

    @Test
    fun map_empty() {
        val graph = emptyGraph()
        compose.showScreen(variant) { MapScreen(graph) }
    }

    @Test
    fun map_seeded() {
        val (graph, _) = seededGraph()
        compose.showScreen(variant) { MapScreen(graph) }
    }

    @Test
    fun session_track_map_seeded() {
        val (graph, seeded) = seededGraph()
        compose.showScreen(variant) {
            SessionTrackMapScreen(graph, sessionId = seeded.sessionId, onBack = {})
        }
    }
}
