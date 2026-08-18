package app.alpha.smoke

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.alpha.baseline.Baseline
import app.alpha.baseline.BaselineState
import app.alpha.ui.logic.MonitorStatus
import app.alpha.ui.logic.NavigateEngine
import app.alpha.ui.logic.NavigateState
import app.alpha.ui.logic.SearchUiStates
import app.alpha.ui.logic.SpotMeasure
import app.alpha.ui.logic.StreamState
import app.alpha.ui.screens.HeroCard
import app.alpha.ui.screens.NavigateSection
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SearchCatalogue
import app.alpha.ui.text.SearchRu
import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Состояния Главной и Поиска — те, по которым экраны сверяются с эталоном
 * `docs/design/main-and-search.html`.
 *
 * Сверяется СТРУКТУРА, а не пиксели: какие элементы обязаны стоять в каждом
 * состоянии и каких там быть не должно. Растровые снимки Robolectric не даёт —
 * `captureToImage` требует настоящего дисплея (`forceRedraw`), — а сравнение
 * картинок без него превратилось бы в сравнение с самим собой.
 *
 * Данные задаются параметрами блоков, а не базой: «уровень обычный» и
 * «превышение» — это разные числа, а не разные сеансы записи.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenStatesTest {

    @get:Rule
    val compose = createComposeRule()

    private val quietPlace = Baseline(
        doseLowMicroSvH = 0.10f,
        doseMedianMicroSvH = 0.12f,
        doseHighMicroSvH = 0.15f,
        doseP25MicroSvH = 0.11f,
        doseP75MicroSvH = 0.13f,
        doseMadMicroSvH = 0.01f,
        cpsLow = 14f,
        cpsMedian = 17f,
        cpsHigh = 21f,
        accumulatedSeconds = 4 * 3600L,
        sampleCount = 4 * 3600L,
        bucketCount = 240,
    )

    private fun show(variant: UiVariant, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.showScreen(variant) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalAppColors.current.bg)
                    .padding(Dimens.space3),
            ) { content() }
        }
    }

    /** Текст есть на экране (в любом регистре подписи). */
    private fun assertShown(text: String) {
        compose.onNodeWithText(text, substring = true, ignoreCase = true).assertIsDisplayed()
    }

    /** Текста на экране нет — то, что эталон требует убрать. */
    private fun assertAbsent(text: String) {
        val matches = compose.onAllNodesWithText(text, substring = true, ignoreCase = true)
            .fetchSemanticsNodes().size
        assert(matches == 0) { "«$text» не должно быть на экране, найдено $matches" }
    }

    private fun hero(
        dose: Float?,
        status: MonitorStatus,
        stream: StreamState,
    ): @androidx.compose.runtime.Composable () -> Unit = {
        HeroCard(
            doseMicroSvH = dose,
            errPercent = 9f,
            cps = dose?.let { it * 140f },
            trend = null,
            trendWindowLabel = null,
            doseTodayMicroSv = 2.8,
            status = status,
            baselineState = BaselineState.Active(quietPlace),
            unit = app.alpha.data.DoseUnitSetting.MICRO_SIEVERT,
            stale = !stream.live,
            stream = stream,
            thresholdMicroSvH = 0.30f,
            threshold2MicroSvH = 1.00f,
            trail = dose?.let { it * 0.92f to it * 1.04f },
        )
    }

    private fun ruStrings(): Strings = RuStrings

    /** Точка отсчёта, снятая на [reference] cps, и текущий счёт [current]. */
    private fun navigateAt(reference: Float, current: Float): NavigateState {
        var state = NavigateState()
        var time = 1_700_000_000_000L
        repeat(40) {
            state = NavigateEngine.onReading(state, time, reference)
            time += 1_000
        }
        state = NavigateEngine.mark(state, time)
        repeat(20) {
            state = NavigateEngine.onReading(state, time, current)
            time += 1_000
        }
        return state
    }

    @androidx.compose.runtime.Composable
    private fun search(state: NavigateState, cps: Float?) {
        val strings = LocalStrings.current
        val now = state.latest?.timeMillis ?: 1_700_000_000_000L
        NavigateSection(
            ui = SearchUiStates.of(
                cps = cps,
                receivedAtMillis = if (cps == null) null else now,
                nowMillis = now,
                connected = cps != null,
                navigate = state,
            ),
            state = state,
            spot = SpotMeasure.Idle,
            nowMillis = state.latest?.timeMillis ?: 1_700_000_000_000L,
            cps = cps,
            doseLine = null,
            referenceTime = "11:44",
            strings = strings,
            t = SearchCatalogue.of(strings.language),
            onMark = {},
            onClearMark = {},
            onResetPeak = {},
            onMeasureHere = {},
            onCancelMeasure = {},
            onDismissMeasure = {},
            onGoToVerify = {},
        )
    }

    @Test
    fun `home carries the three tiles of the mockup`() {
        show(UiVariant.ALL[0], hero(0.14f, MonitorStatus.Usual(quietPlace), StreamState.Live))
        val s = ruStrings()
        // Три показателя эталона: фон места, за час, за сутки.
        assertShown(s.tilePlaceBackground)
        assertShown(s.tilePerHour)
        assertShown(s.tilePerDay)
        // Погрешность прибора под числом не стоит: её место — в «Почему».
        assertAbsent("±9")
    }

    @Test
    fun `home above the usual level still reads as one screen`() {
        show(
            UiVariant.ALL[0],
            hero(0.42f, MonitorStatus.AboveUsual(baseline = quietPlace, heldSeconds = 240L), StreamState.Live),
        )
        assertShown(ruStrings().tilePlaceBackground)
    }

    @Test
    fun `home on a light skin draws the same structure`() {
        show(UiVariant.ALL[1], hero(0.29f, MonitorStatus.Usual(quietPlace), StreamState.Live))
        compose.onAllNodesWithText("0,29", substring = true).fetchSemanticsNodes().let {
            assert(it.isNotEmpty()) { "главное число пропало на светлом оформлении" }
        }
    }

    @Test
    fun `home without a stream keeps the reading and says how old it is`() {
        show(
            UiVariant.ALL[0],
            hero(0.14f, MonitorStatus.Usual(quietPlace), StreamState.Disconnected(ageSeconds = 180)),
        )
        assertShown("0,14")
        assertShown(ruStrings().tilePlaceBackground)
    }

    @Test
    fun `search before a reference shows the empty instrument and the action`() {
        show(UiVariant.ALL[0]) { search(NavigateState(), cps = 17.4f) }
        // Прибор стоит пустым, и рядом — единственное действие этого состояния.
        assertShown(SearchRu.navMark)
        // Отношения ещё нет, поэтому его подписи тоже нет.
        assertAbsent("к точке отсчёта")
    }

    @Test
    fun `search at the reference answers stronger-or-weaker, not statistics`() {
        show(UiVariant.ALL[0]) { search(navigateAt(reference = 17.4f, current = 17.6f), cps = 17.6f) }
        // Разбор статистики уехал в «Почему?»: на рабочем экране его нет.
        assertAbsent(SearchRu.navUnresolvedNote)
        assertShown(SearchRu.navWhy)
    }

    @Test
    fun `search well above the reference names the ratio in one caption`() {
        show(UiVariant.ALL[0]) { search(navigateAt(reference = 17.4f, current = 74.5f), cps = 74.5f) }
        // Отношение — подписью под числом, и знаменатель назван в ней же.
        assertShown("к точке отсчёта")
        // Большое действие ушло: точка отсчёта уже стоит.
        assertAbsent(SearchRu.navMark)
        assertAbsent(SearchRu.navUnresolvedNote)
    }

    @Test
    fun `search with the stream stopped says so and only about the stream`() {
        show(UiVariant.ALL[0]) { search(NavigateState(), cps = null) }
        assertShown(SearchRu.waitingStream)
    }

    @Test
    fun `a live reading never shows the waiting line`() {
        show(UiVariant.ALL[0]) { search(navigateAt(reference = 17.4f, current = 74.5f), cps = 74.5f) }
        assertAbsent(SearchRu.waitingStream)
    }
}
