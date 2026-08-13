package app.radiacode.smoke

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.radiacode.AppGraph
import app.radiacode.ui.screens.SettingsScreen
import app.radiacode.ui.text.CalibrationCatalogue
import app.radiacode.ui.text.stringsFor
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Настройки: корень и КАЖДАЯ категория открываются и не падают. Категории
 * открываются кликом по строке — тем же путём, что у человека, поэтому смоук
 * проходит через настоящую прокручиваемую колонку Настроек. Вход в
 * калибровку — регрессия полевого краша вложенного verticalScroll: экран
 * диагностики живёт ВНУТРИ прокрутки Настроек и обязан рендериться там.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SettingsSmokeTest(variantId: String) {

    private val variant = UiVariant.of(variantId)
    private val strings = stringsFor(variant.language)
    private val graphs = mutableListOf<AppGraph>()

    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = UiVariant.parameters()
    }

    @After
    fun closeDatabases() {
        graphs.forEach { runCatching { it.database.close() } }
    }

    /** Открывает Настройки на засеянной базе и кликает категорию по названию. */
    private fun openCategory(title: String?) {
        val graph = Smoke.graph().also { graphs += it }
        Smoke.seed(graph)
        compose.showScreen(variant) { SettingsScreen(graph, onBack = {}) }
        if (title != null) {
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.settle()
        }
    }

    @Test fun settings_root() = openCategory(null)

    @Test fun category_alarms() = openCategory(strings.settingsAlarms)

    @Test fun category_profiles() = openCategory(strings.settingsProfiles)

    @Test fun category_notifications() = openCategory(strings.settingsNotifications)

    @Test fun category_view() = openCategory(strings.settingsView)

    @Test fun category_device() = openCategory(strings.settingsDevice)

    @Test fun category_about() = openCategory(strings.settingsAbout)

    /**
     * Регрессия полевого краша: «Настройки → Прибор → Калибровка по природному
     * фону». Экран калибровки рендерится ВНУТРИ прокручиваемой колонки
     * Настроек; собственный verticalScroll в нём получал бесконечную высоту, и
     * Compose ронял процесс («Vertically scrollable component was measured
     * with an infinity maximum height constraints»).
     */
    @Test
    fun calibration_opens_inside_settings_scroll() {
        openCategory(strings.settingsDevice)
        val c = CalibrationCatalogue.of(variant.language)
        compose.onNodeWithText(c.entryTitle).performScrollTo().performClick()
        compose.settle()
        compose.onNodeWithText(c.screenTitle).assertExists()
    }
}
