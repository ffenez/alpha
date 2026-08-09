package app.radiacode.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.radiacode.analysis.SpectrumDisplay
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelColors

/**
 * Spectrum histogram in the pixel-chart engine style (design-language.md):
 * rasterized into a small offscreen bitmap, upscaled by an integer factor with
 * FilterQuality.None.
 *
 * Layers, bottom to top: dotted gridlines (log decades or linear quarters,
 * plus vertical energy ticks), data columns with a bright head pixel, the
 * dimmed background overlay drawn as 1 px caps (muted — never amber, per the
 * design language chart rule), and alarm-colored caps on the columns of the
 * highlighted peak candidate.
 */
@Immutable
data class SpectrumChartSpec(
    /** Aggregated counts per column (linear values; the spec maps them). */
    val columns: List<Float>,
    /** Background overlay series in the same columns; null = no overlay. */
    val overlay: List<Float>? = null,
    val logScale: Boolean = true,
    /** Scale top: linear max or a power of ten for log (see [SpectrumDisplay.logTop]). */
    val yTop: Float,
    /** Columns of the highlighted isotope-candidate peak (alarm caps). */
    val highlightedColumns: Set<Int> = emptySet(),
    val energyTicks: List<SpectrumDisplay.EnergyTick> = emptyList(),
    val columnWidthPx: Int = 2,
    val gapPx: Int = 1,
    val heightPx: Int = 96,
) {
    val widthPx: Int = (columns.size * (columnWidthPx + gapPx) - gapPx).coerceAtLeast(1)
}

@Composable
fun SpectrumChart(
    spec: SpectrumChartSpec,
    modifier: Modifier = Modifier,
    /** Pinch/drag: scale factor (>1 = zoom in), pan and focus as width fractions. */
    onGesture: ((scale: Float, panFraction: Float, focusFraction: Float) -> Unit)? = null,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val bitmap = remember(spec, colors) { renderSpectrum(spec, colors) }

    Column(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val scale = (constraints.maxWidth / spec.widthPx).coerceAtLeast(1)
            val width = with(density) { (spec.widthPx * scale).toDp() }
            val height = with(density) { (spec.heightPx * scale).toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width, height)
                    .then(
                        if (onGesture == null) {
                            Modifier
                        } else {
                            Modifier.pointerInput(Unit) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    onGesture(zoom, pan.x / w, (centroid.x / w).coerceIn(0f, 1f))
                                }
                            }
                        },
                    ),
            ) {
                Image(
                    painter = BitmapPainter(bitmap, filterQuality = FilterQuality.None),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(width, height),
                )
                if (spec.logScale) {
                    // Decade labels on the log axis: 1 / 10 / 100 / 1k / 10k.
                    val decades = SpectrumDisplay.decadeCount(spec.yTop)
                    for (decade in 0..decades) {
                        val row = SpectrumDisplay.decadeRow(decade, spec.yTop, spec.heightPx)
                        val yDp = height * (row.toFloat() / spec.heightPx)
                        Text(
                            text = SpectrumDisplay.decadeLabel(decade),
                            style = type.labelSmall,
                            color = colors.textMuted,
                            modifier = Modifier.offset(
                                x = 2.dp,
                                y = (yDp - 14.dp).coerceAtLeast(0.dp),
                            ),
                        )
                    }
                }
            }
        }
        // Energy axis labels under their ticks (кэВ; unit named in the panel title).
        if (spec.energyTicks.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val scale = (constraints.maxWidth / spec.widthPx).coerceAtLeast(1)
                val chartWidth = with(density) { (spec.widthPx * scale).toDp() }
                val leftPad = (maxWidth - chartWidth) / 2
                val labelWidth = 48.dp
                Box(Modifier.fillMaxWidth().height(16.dp)) {
                    spec.energyTicks.forEach { tick ->
                        Text(
                            text = "${tick.keV}",
                            style = type.labelSmall,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(labelWidth)
                                .offset(
                                    x = (leftPad + chartWidth * tick.fraction - labelWidth / 2)
                                        .coerceIn(0.dp, this@BoxWithConstraints.maxWidth - labelWidth),
                                ),
                        )
                    }
                }
            }
        }
    }
}

private fun renderSpectrum(spec: SpectrumChartSpec, colors: PixelColors): ImageBitmap {
    val w = spec.widthPx
    val h = spec.heightPx
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    val paint = Paint()

    fun px(x: Int, y: Int, color: Color) {
        if (x < 0 || y < 0 || x >= w || y >= h) return
        paint.color = color
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
    }

    // 1. Horizontal gridlines: log decades, or quarter heights on linear.
    if (spec.logScale) {
        val decades = SpectrumDisplay.decadeCount(spec.yTop)
        for (decade in 0..decades) {
            val y = SpectrumDisplay.decadeRow(decade, spec.yTop, h)
            for (x in 0 until w step 4) px(x, y, colors.frame)
        }
    } else {
        for (quarter in 1..3) {
            val y = h - 1 - (h - 1) * quarter / 4
            for (x in 0 until w step 4) px(x, y, colors.frame)
        }
    }
    // Vertical dotted gridlines at the energy ticks.
    val stride = spec.columnWidthPx + spec.gapPx
    for (tick in spec.energyTicks) {
        val x = (tick.fraction * (w - 1)).toInt()
        for (y in 0 until h step 4) px(x, y, colors.frame)
    }

    // 2. Data columns with a bright head pixel.
    spec.columns.forEachIndexed { index, value ->
        val height = SpectrumDisplay.columnHeightPx(value, spec.yTop, h, spec.logScale)
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
        paint.color = if (colors.isDark) colors.accent else colors.text
        canvas.drawRect(
            x0.toFloat(),
            yTop.toFloat(),
            (x0 + spec.columnWidthPx).toFloat(),
            (yTop + 1).toFloat(),
            paint,
        )
    }

    // 3. Background overlay: dim 1 px caps tracing the reference spectrum.
    spec.overlay?.forEachIndexed { index, value ->
        if (index >= spec.columns.size) return@forEachIndexed
        val height = SpectrumDisplay.columnHeightPx(value, spec.yTop, h, spec.logScale)
        if (height == 0) return@forEachIndexed
        val x0 = index * stride
        val y = h - height
        for (x in x0 until x0 + spec.columnWidthPx) px(x, y, colors.textMuted)
    }

    // 4. Alarm caps on the highlighted candidate peak (color + the text label
    // below the chart carry the meaning together — never color alone).
    for (index in spec.highlightedColumns) {
        if (index !in spec.columns.indices) continue
        val height = SpectrumDisplay.columnHeightPx(spec.columns[index], spec.yTop, h, spec.logScale)
        if (height == 0) continue
        val x0 = index * stride
        val yTop = h - height
        for (x in x0 until x0 + spec.columnWidthPx) {
            px(x, yTop, colors.chartAlarm)
            px(x, yTop - 1, colors.chartAlarm)
        }
    }

    return bitmap
}
