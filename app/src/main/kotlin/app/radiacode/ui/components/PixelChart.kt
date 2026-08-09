package app.radiacode.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelColors

/**
 * Pixel chart engine (design-language.md): the plot is rasterized into a
 * small offscreen bitmap (~144x52) and upscaled by an integer factor with
 * FilterQuality.None — chunky pixels, no smoothing. Missing columns stay
 * gaps; nothing is interpolated.
 *
 * Layers, bottom to top: dithered band (checker — dithering instead of
 * translucency), dotted gridlines, data columns with a bright head pixel,
 * dashed alarm row.
 */
@Immutable
data class PixelChartSpec(
    /** Raw values per column slot, null = no data in that slot. */
    val columns: List<Float?>,
    /** Top of the y scale, same unit as [columns]. */
    val yMax: Float,
    /** Dashed alarm/threshold row, same unit; omitted when out of frame. */
    val alarmLevel: Float? = null,
    /** Dithered "usual range" band, same unit. */
    val band: ClosedFloatingPointRange<Float>? = null,
    val columnWidthPx: Int = 2,
    val gapPx: Int = 1,
    val heightPx: Int = 52,
) {
    val widthPx: Int = (columns.size * (columnWidthPx + gapPx) - gapPx).coerceAtLeast(1)
}

@Composable
fun PixelChart(
    spec: PixelChartSpec,
    modifier: Modifier = Modifier,
    xStartLabel: String? = null,
    xEndLabel: String? = null,
    yMaxLabel: String? = null,
) {
    val colors = LocalPixelColors.current
    val bitmap = remember(spec, colors) { renderChart(spec, colors) }

    Column(modifier = modifier) {
        if (yMaxLabel != null) {
            Text(
                text = yMaxLabel,
                style = LocalPixelTypography.current.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.End),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val scale = (constraints.maxWidth / spec.widthPx).coerceAtLeast(1)
            val width = with(density) { (spec.widthPx * scale).toDp() }
            val height = with(density) { (spec.heightPx * scale).toDp() }
            Image(
                painter = BitmapPainter(bitmap, filterQuality = FilterQuality.None),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 2.dp)
                    .size(width, height),
            )
        }
        if (xStartLabel != null || xEndLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = xStartLabel.orEmpty(),
                    style = LocalPixelTypography.current.labelSmall,
                    color = colors.textMuted,
                )
                Text(
                    text = xEndLabel.orEmpty(),
                    style = LocalPixelTypography.current.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

private fun renderChart(spec: PixelChartSpec, colors: PixelColors): ImageBitmap {
    val w = spec.widthPx
    val h = spec.heightPx
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    fun px(x: Int, y: Int, color: Color) {
        paint.color = color
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
    }

    // 1. Dithered band: checker pattern in a muted tone.
    spec.band?.let { band ->
        val top = ChartMapping.rowForLevel(band.endInclusive, spec.yMax, h) ?: 0
        val bottom = ChartMapping.rowForLevel(band.start.coerceAtLeast(0.0001f), spec.yMax, h)
            ?: (h - 1)
        for (y in top..bottom) {
            for (x in 0 until w) {
                if ((x + y) % 2 == 0) px(x, y, colors.textMuted.copy(alpha = 0.5f))
            }
        }
    }

    // 2. Dotted gridlines at quarter heights.
    for (quarter in 1..3) {
        val y = h - 1 - (h - 1) * quarter / 4
        for (x in 0 until w step 4) px(x, y, colors.frame)
    }

    // 3. Data columns, gap-preserving, bright head pixel.
    val stride = spec.columnWidthPx + spec.gapPx
    spec.columns.forEachIndexed { index, value ->
        if (value == null) return@forEachIndexed
        val height = ChartMapping.columnHeightPx(value, spec.yMax, h)
        if (height == 0) return@forEachIndexed
        val x0 = index * stride
        val yTop = h - height
        paint.color = colors.chartData
        canvas.drawRect(
            x0.toFloat(),
            (yTop + 1).coerceAtMost(h).toFloat(),
            (x0 + spec.columnWidthPx).toFloat(),
            h.toFloat(),
            paint,
        )
        // Head pixel row: the freshest, brightest mark of the column tip.
        paint.color = if (colors.isDark) colors.accent else colors.text
        canvas.drawRect(
            x0.toFloat(),
            yTop.toFloat(),
            (x0 + spec.columnWidthPx).toFloat(),
            (yTop + 1).toFloat(),
            paint,
        )
    }

    // 4. Dashed alarm row on top: 3 px on, 3 px off.
    spec.alarmLevel?.let { alarm ->
        val y = ChartMapping.rowForLevel(alarm, spec.yMax, h)
        if (y != null) {
            for (x in 0 until w) {
                if (x % 6 < 3) px(x, y, colors.chartAlarm)
            }
        }
    }

    return bitmap
}
