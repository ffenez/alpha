package app.radiacode.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography

/** Small framed label: place tag, mode marker. 2dp frame, no fill, radius 0. */
@Composable
fun PixelTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPixelColors.current.textSecondary,
) {
    Text(
        text = text,
        style = LocalPixelTypography.current.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .border(2.dp, color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
