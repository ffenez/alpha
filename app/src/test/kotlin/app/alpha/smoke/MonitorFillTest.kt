package app.alpha.smoke

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import app.alpha.AppGraph
import app.alpha.ui.screens.MonitorScreen
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Главная занимает высоту экрана.
 *
 * Страница строилась только сверху вниз, и на пустой базе — когда график ещё
 * ничего не рисует — под карточкой оставалась пустая полоса в треть экрана.
 * Свободная высота отдана главной карточке ([app.alpha.ui.logic.MonitorLayout]),
 * поэтому её содержимое стоит по центру свободного места, а не под шапкой.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MonitorFillTest {

    private var graph: AppGraph? = null

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        graph?.database?.close()
    }

    @Test
    fun `the hero card takes the free height of the screen`() {
        val g = Smoke.graph().also { graph = it }
        compose.showScreen(UiVariant.ALL.first()) { MonitorScreen(g) }

        val screenHeight = compose.onRoot().fetchSemanticsNode().size.height
        // Первый узел с этой подписью — заголовок главного числа; второй,
        // ниже по экрану, принадлежит карточке графика.
        val heroLabelTop = compose
            .onAllNodesWithText("МОЩНОСТЬ ДОЗЫ")[0]
            .fetchSemanticsNode().boundsInRoot.top

        // Под шапкой заголовок стоял бы в первой десятой экрана.
        assertTrue(
            heroLabelTop > screenHeight * 0.2f,
            "заголовок главного числа на $heroLabelTop из $screenHeight",
        )
    }
}
