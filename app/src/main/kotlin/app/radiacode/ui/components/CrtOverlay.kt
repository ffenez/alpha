package app.radiacode.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlin.math.hypot

/**
 * CRT phosphor overlay: scanlines + vignette. Dark (CRT) theme only, static —
 * one cached shader + one gradient, two draw calls over the content, zero
 * recomposition cost. Attach to the root screen container.
 */
fun Modifier.crtOverlay(enabled: Boolean): Modifier {
    if (!enabled) return this
    return drawWithCache {
        // 1x4 px tile: one darkened scanline, three clear lines.
        val tile = ImageBitmap(1, 4)
        val canvas = Canvas(tile)
        val paint = Paint().apply { color = Color.Black.copy(alpha = 0.16f) }
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
        val scanlines = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))

        val vignette = Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.32f),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = hypot(size.width, size.height) / 2f,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = scanlines)
            drawRect(brush = vignette)
        }
    }
}
