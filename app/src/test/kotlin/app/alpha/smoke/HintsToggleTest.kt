package app.alpha.smoke

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.alpha.ui.components.Hint
import app.alpha.ui.components.LocalHintsVisible
import app.alpha.ui.components.StatusRow
import app.alpha.ui.theme.AppSkin
import app.alpha.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Выключатель пояснений убирает объяснения — и только их.
 *
 * Граница здесь не косметическая: пояснение можно спрятать, потому что без
 * него экран остаётся понятным, а СОСТОЯНИЕ прятать нельзя ни при каких
 * настройках — экран без него выглядит работающим, когда он не работает.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class HintsToggleTest {

    @get:Rule
    val compose = createComposeRule()

    private val hint = "Порог задаётся в настройках"
    private val state = "нет связи — показан последний прочитанный спектр"

    private fun show(hintsVisible: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            AppTheme(dark = true, skin = AppSkin.TERMINAL) {
                CompositionLocalProvider(LocalHintsVisible provides hintsVisible) {
                    Column { content() }
                }
            }
        }
        compose.settle(passes = 2)
    }

    @Test
    fun `with hints on both the explanation and the state are on screen`() {
        show(hintsVisible = true) {
            Hint(text = hint)
            StatusRow(text = state)
        }

        compose.onNodeWithText(hint).assertIsDisplayed()
        compose.onNodeWithText(state).assertIsDisplayed()
    }

    @Test
    fun `with hints off the explanation is gone and the state stays`() {
        show(hintsVisible = false) {
            Hint(text = hint)
            StatusRow(text = state)
        }

        compose.onNodeWithText(hint).assertDoesNotExist()
        compose.onNodeWithText(state).assertIsDisplayed()
    }
}
