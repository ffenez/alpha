package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.PixelDimens

/**
 * The 8-bit panel: radius 0, 3dp frame, 5dp hard chunk shadow offset
 * right-down with no blur. Content sits on [PixelColors.surface].
 *
 * The shadow is drawn outside the layout bounds, so parents should leave
 * [PixelDimens.shadowOffset] of room (screen padding on the pixel grid
 * already does).
 */
@Composable
fun PixelBox(
    modifier: Modifier = Modifier,
    contentPadding: Dp = PixelDimens.space4,
    background: Color = LocalPixelColors.current.surface,
    frame: Color = LocalPixelColors.current.frame,
    content: @Composable BoxScope.() -> Unit,
) {
    val shadow = if (LocalPixelColors.current.isDark) Color.Black.copy(alpha = 0.55f)
    else LocalPixelColors.current.frame.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .drawBehind {
                val off = PixelDimens.shadowOffset.toPx()
                drawRect(
                    color = shadow,
                    topLeft = Offset(off, off),
                    size = Size(size.width, size.height),
                )
            }
            .background(background)
            .border(PixelDimens.frame, frame)
            .padding(contentPadding),
        content = content,
    )
}

/** Divider on the pixel grid: a 2dp hard line in frame color. */
@Composable
fun PixelDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(LocalPixelColors.current.frame),
    )
}
