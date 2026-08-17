package app.alpha.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Показывать ли пояснения — серые строки, которые объясняют, а не сообщают.
 *
 * Их читают один раз, а место они занимают всегда. Человеку, который носит
 * прибор каждый день, они через неделю превращаются в шум, и просьба их убрать
 * — не про минимализм, а про то, что экран перестаёт помещаться.
 */
val LocalHintsVisible = staticCompositionLocalOf { false }

/**
 * Пояснение: строка, без которой экран остаётся понятным.
 *
 * ## Что сюда попадает и что не попадает
 *
 * Сюда — только объяснения: как устроен расчёт, что означает величина, что
 * делать дальше, оговорка о границе метода. Их выключатель и прячет.
 *
 * Сюда НЕ попадают строки СОСТОЯНИЯ («нет связи», «прибор не подключён», «не
 * в сети Wi-Fi») и строки ДАННЫХ (единица, длительность замера). Состояние
 * обязано быть видно всегда — спрятанное, оно превращает работающий экран в
 * молчащий, — а данные это и есть содержимое экрана.
 */
@Composable
fun Hint(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalAppTypography.current.footnote,
    color: Color = LocalAppColors.current.muted,
) {
    if (!LocalHintsVisible.current) return
    Text(text = text, style = style, color = color, modifier = modifier)
}
