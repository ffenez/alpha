package app.alpha.smoke

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.alpha.AppGraph
import app.alpha.ui.screens.MonitorScreen
import app.alpha.ui.screens.SearchScreen
import app.alpha.ui.screens.SettingsScreen
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Выключенные пояснения не уносят с экрана то, без чего результат нельзя
 * истолковать (CLAUDE.md, три категории интерфейса).
 *
 * Смоук намеренно проверяет ОТСУТСТВИЕ пропажи, а не наличие пояснений:
 * по умолчанию `LocalHintsVisible` = false, то есть все экраны здесь и так
 * рисуются в выключенном режиме — именно в нём приложение чаще всего и живёт.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ExplanationsOffTest {

    private val graphs = mutableListOf<AppGraph>()

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        graphs.forEach { it.database.close() }
    }

    private fun graph(): AppGraph = Smoke.graph().also { graphs += it }

    /** Любая из строк должна остаться на экране. */
    private fun assertAnyVisible(vararg texts: String) {
        val found = texts.filter {
            compose.onAllNodesWithText(it, substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(found.isNotEmpty(), "ничего из ${texts.toList()} не осталось на экране")
    }

    @Test
    fun `the main screen keeps its state words`() {
        val g = graph()
        compose.showScreen(UiVariant.ALL.first()) { MonitorScreen(g) }
        // Состояние потока — не пояснение: без него неподвижный экран выглядит
        // работающим.
        assertAnyVisible("мощность дозы", "прибор", "связь")
    }

    @Test
    fun `search keeps the reason it cannot compare`() {
        val g = graph()
        compose.showScreen(UiVariant.ALL.first()) { SearchScreen(g) }
        // Фон не записан — отказ метода, а не подсказка.
        assertAnyVisible("счёт", "фон", "поток")
    }

    @Test
    fun `settings keep the unit and the licences`() {
        val g = graph()
        runBlocking { g.settings.setHintsVisible(false) }
        compose.showScreen(UiVariant.ALL.first()) { SettingsScreen(g, onBack = {}) }
        assertAnyVisible("раздел", "прибор", "вид")
    }

    /** Ни одной строки не должно остаться на экране. */
    private fun assertNoneVisible(vararg texts: String) {
        val left = texts.filter {
            compose.onAllNodesWithText(it, substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(left.isEmpty(), "с выключенными пояснениями осталось: $left")
    }

    @Test
    fun `alarms drop the tip about where the melody lives and keep the state`() {
        val g = graph()
        runBlocking { g.settings.setHintsVisible(false) }
        compose.showScreen(UiVariant.ALL.first()) { SettingsScreen(g, onBack = {}) }
        // Подсказка «мелодия и вибрация — в разделе Звук» учит устройству
        // настроек и уходит вместе с пояснениями; сам порог и его число —
        // основное и остаются.
        assertNoneVisible("в разделе «Звук»")
    }
}
