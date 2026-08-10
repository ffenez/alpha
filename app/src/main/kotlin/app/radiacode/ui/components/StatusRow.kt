package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens

/**
 * One console status line: `> ТЕКСТ █`. Status is never conveyed by color
 * alone (design-language.md) — the words carry the meaning, color assists.
 */
@Composable
fun StatusLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPixelColors.current.textSecondary,
    cursor: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
    ) {
        Text(
            text = ">",
            style = LocalPixelTypography.current.label,
            color = color,
        )
        Text(
            text = text,
            style = LocalPixelTypography.current.label,
            color = color,
        )
        if (cursor) {
            BlinkingCursor(size = DpSize(8.dp, 14.dp), color = color)
        }
        trailing()
    }
}
