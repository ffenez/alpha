package app.radiacode.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Две стрелки приложения — и их ровно две.
 *
 * Одинаковое действие обязано выглядеть одинаково везде, иначе значок
 * перестаёт быть значком и превращается в украшение, которое приходится
 * читать заново на каждом экране. Раньше раскрытие на месте рисовалось то
 * «▾», то «⌄», а переход — то «›» с одним размером, то с другим.
 *
 *  - [DisclosureArrow] — блок раскрывается ЗДЕСЬ ЖЕ, никуда не уходя.
 *  - [NavArrow] — откроется другой экран.
 *
 * Символы, а не иконки: скин «8-bit» рисует их своим шрифтом вместе со всем
 * остальным текстом, и отдельного набора картинок под каждый скин не нужно.
 */
@Composable
fun DisclosureArrow(expanded: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (expanded) "▴" else "▾",
        style = LocalAppTypography.current.label,
        color = LocalAppColors.current.ink2,
        modifier = modifier,
    )
}

@Composable
fun NavArrow(modifier: Modifier = Modifier) {
    Text(
        text = "›",
        style = LocalAppTypography.current.value,
        color = LocalAppColors.current.ink2,
        modifier = modifier,
    )
}
