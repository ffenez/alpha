package app.radiacode.smoke

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.theme.AppSkin
import app.radiacode.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Полевой дефект: при увеличенном системном шрифте «Сохранить в историю» на
 * кнопке превращалось в «Сохранить в».
 *
 * Обрезка по краю — худший вид потери текста: она не выглядит как обрезка. У
 * человека на экране остаётся синтаксически законченное, но ДРУГОЕ действие,
 * и понять, что часть подписи не поместилась, неоткуда. Поэтому подписи
 * управляющих элементов растут во вторую строку, а не режутся.
 *
 * Тест ставит настоящие подписи приложения в узкую строку при масштабе шрифта
 * 1,5 и спрашивает у самой разметки текста, не переполнена ли она
 * ([TextLayoutResult.hasVisualOverflow]) — то есть проверяет ФАКТ отрисовки, а
 * не намерение автора.
 *
 * `@GraphicsMode(NATIVE)` здесь обязателен: в режиме по умолчанию Robolectric
 * подменяет шрифт заглушкой шириной в один пиксель на символ, и любая проверка
 * ширины текста в нём проходит или падает случайно.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeFontLabelsTest {

    @get:Rule
    val compose = createComposeRule()

    /** Тот самый масштаб, при котором дефект и был замечен. */
    private val fontScale = 1.5f

    /**
     * Сколько символов подписи ДЕЙСТВИТЕЛЬНО отрисовано.
     *
     * Спрашивается именно это, а не `hasVisualOverflow`: у выключенного по
     * центру текста тот флаг взводится и когда всё поместилось (разметка
     * занимает всю ширину, а сама строка — нет). Число видимых символов
     * отвечает ровно на вопрос дефекта: пропали ли слова.
     */
    private fun renderedLength(text: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        val action = requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action) {
            "у текста «$text» нет разметки"
        }
        action.invoke(results)
        val layout = results.first()
        return layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AppTheme(dark = true, skin = AppSkin.TERMINAL) { content() }
            }
        }
        compose.settle(passes = 2)
    }

    @Test
    fun `a long button label wraps instead of losing its last words`() {
        val label = "Сохранить в историю"
        show {
            Row {
                AppButton(text = label, onClick = {}, modifier = Modifier.width(150.dp))
            }
        }

        assertEquals(label.length, renderedLength(label), "подпись кнопки обрезана: «$label»")
    }

    @Test
    fun `the mode switch keeps both mode names`() {
        show {
            Segmented(
                options = listOf("Наведение", "Проверка"),
                selectedIndex = 0,
                onSelect = {},
                // Ширина реального экрана: переключатель режимов всегда во всю
                // строку, и мерить его в вымышленно узкой колонке значило бы
                // проверять не тот случай.
                modifier = Modifier.fillMaxWidth(),
            )
        }

        assertEquals(9, renderedLength("Наведение"), "подпись вкладки обрезана")
        assertEquals(8, renderedLength("Проверка"), "подпись вкладки обрезана")
    }

    @Test
    fun `a chip keeps its whole label`() {
        val label = "Запомнить текущий уровень"
        show { Row { Chip(text = label, modifier = Modifier.width(180.dp)) } }

        assertEquals(label.length, renderedLength(label), "подпись чипа обрезана: «$label»")
    }
}
