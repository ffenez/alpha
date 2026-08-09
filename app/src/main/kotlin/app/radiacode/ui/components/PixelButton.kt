package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens

/**
 * Console-style button: `[ ТЕКСТ ]`. The brackets are part of the label,
 * so the button reads as a command even without a frame. Pressed state
 * inverts to a solid block; no ripple, no rounding.
 *
 * [primary] renders a solid accent block for the single main action of a
 * screen (e.g. connect); at most one per screen.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val colors = LocalPixelColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val background: Color
    val foreground: Color
    when {
        !enabled -> {
            background = Color.Transparent
            foreground = colors.textMuted
        }
        primary || pressed -> {
            background = if (pressed && primary) colors.text else colors.accent
            foreground = colors.ground
        }
        else -> {
            background = Color.Transparent
            foreground = colors.text
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = PixelDimens.touchTarget)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = PixelDimens.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "[ $text ]",
            style = LocalPixelTypography.current.label,
            color = foreground,
            maxLines = 1,
        )
    }
}
