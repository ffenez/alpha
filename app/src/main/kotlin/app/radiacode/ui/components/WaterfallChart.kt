package app.radiacode.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.radiacode.analysis.Spectrogram
import app.radiacode.analysis.SpectrogramColumn
import app.radiacode.ui.theme.DoseRampColors
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Спектрограмма-водопад («Научный терминал»): X = time, Y = energy on the
 * geometric 20–3000 keV scale (low energies at the bottom), cell brightness =
 * per-column log-normalized intensity ([Spectrogram.intensity]). Under the
 * waterfall a thin синхронная dose-rate strip shares the exact same time
 * axis; a tap sets the time cursor across both plots.
 *
 * Color: the map's validated amber ramp. On the dark theme its order is
 * reversed so *luminance always rises with intensity* on the dark surface
 * (perceptual ordering first, ramp identity second); zero intensity is the
 * card surface itself. Axes are always labeled (mono 10sp), the legend and
 * normalization note live next to the chart — never color alone.
 */
@Immutable
data class WaterfallSpec(
    /**
     * Колонки сетки времени, старые → новые; `null` = в этой ячейке измерений
     * не было. Пропуск обязан быть виден: пустая колонка и колонка с нулевым
     * счётом — разные факты.
     */
    val columns: List<SpectrogramColumn?>,
    /**
     * Верх общей цветовой шкалы, имп/с на полосу. Ноль или режим [shapeMode]
     * переключают яркость на нормировку внутри колонки.
     */
    val scaleTop: Float = 0f,
    /** Режим «форма»: нормировка внутри колонки вместо общей шкалы. */
    val shapeMode: Boolean = false,
    /**
     * Группы энергетических полос отображения: полосы объединяются, пока в них
     * не наберётся статистика. Пусто = каждая полоса сама по себе.
     */
    val bandGroups: List<IntRange> = emptyList(),
    /** Cursor column index; null = no cursor. */
    val selectedIndex: Int? = null,
    /** Fraction of plot width → time label. */
    val timeLabels: List<Pair<Float, String>> = emptyList(),
    /** Dose rate per column for the synced strip, µSv/h; null = unknown. */
    val stripValues: List<Float?> = emptyList(),
)

private const val STRIP_GAP_DP = 4
private const val STRIP_HEIGHT_DP = 30

@Composable
fun WaterfallChart(
    spec: WaterfallSpec,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    onTapColumn: ((Int) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Ramp lookup: 256 ARGB steps. Dark theme reverses the map ramp so higher
    // intensity is always brighter than the ground it sits on.
    val rampLut = remember(colors.isDark) {
        val stops = if (colors.isDark) DoseRampColors.reversed() else DoseRampColors
        IntArray(256) { i ->
            val t = i / 255f
            val scaled = t * (stops.size - 1)
            val idx = scaled.toInt().coerceAtMost(stops.size - 2)
            lerp(stops[idx], stops[idx + 1], scaled - idx).toArgb()
        }
    }
    val surfaceArgb = colors.surface.toArgb()

    // The waterfall as a bands×columns bitmap, scaled up with nearest-neighbor
    // sampling at draw time — thousands of cells without per-frame rects.
    val gapArgb = colors.chartBeyondData.toArgb()
    val bitmap: ImageBitmap? = remember(
        spec.columns,
        spec.scaleTop,
        spec.shapeMode,
        spec.bandGroups,
        rampLut,
        surfaceArgb,
    ) {
        if (spec.columns.isEmpty()) return@remember null
        val w = spec.columns.size
        val h = Spectrogram.BAND_COUNT
        val pixels = IntArray(w * h)
        val groups = spec.bandGroups.ifEmpty { (0 until h).map { it..it } }
        for (x in 0 until w) {
            val column = spec.columns[x]
            if (column == null) {
                // Пропуск потока: своя плоскость, а не «нулевая интенсивность».
                for (band in 0 until h) pixels[(h - 1 - band) * w + x] = gapArgb
                continue
            }
            // Значение считается по ГРУППЕ полос (адаптивная энергетическая
            // нарезка), и все строки группы красятся одинаково: это честная
            // запись «на таком энергетическом разрешении столько-то».
            var columnMax = 0f
            for (group in groups) {
                val v = column.groupCounts(group)
                if (v > columnMax) columnMax = v
            }
            for (group in groups) {
                val t = if (spec.shapeMode || spec.scaleTop <= 0f) {
                    Spectrogram.shapeIntensity(column.groupCounts(group), columnMax)
                } else {
                    Spectrogram.intensity(column.groupRate(group), spec.scaleTop)
                }
                val argb = if (t <= 0f) {
                    surfaceArgb
                } else {
                    rampLut[(t * 255f).toInt().coerceIn(0, 255)]
                }
                for (band in group) {
                    if (band !in 0 until h) continue
                    // Row 0 of the bitmap is the top = highest energy band.
                    pixels[(h - 1 - band) * w + x] = argb
                }
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    // Left pad fits the widest energy label; shared by drawing and tapping.
    val energyTicks = remember {
        Spectrogram.ENERGY_TICKS_KEV.mapNotNull { keV ->
            Spectrogram.fractionOfEnergy(keV)?.let { it to "${keV.toInt()}" }
        }
    }
    val padLpx = remember(axisStyle, textMeasurer, density) {
        energyTicks.maxOf { textMeasurer.measure(it.second, axisStyle).size.width } +
            with(density) { 5.dp.toPx() }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (onTapColumn == null || spec.columns.isEmpty()) {
                    Modifier
                } else {
                    Modifier.pointerInput(spec.columns.size, padLpx) {
                        detectTapGestures { offset ->
                            val plotW = (size.width - padLpx - with(density) { 4.dp.toPx() })
                                .coerceAtLeast(1f)
                            val fraction = ((offset.x - padLpx) / plotW).coerceIn(0f, 1f)
                            val index = (fraction * spec.columns.size).toInt()
                                .coerceIn(0, spec.columns.size - 1)
                            onTapColumn(index)
                        }
                    }
                },
            ),
    ) {
        val labelHeight = textMeasurer.measure("00:00", axisStyle).size.height
        val padL = padLpx
        val padR = 4.dp.toPx()
        val padT = 2.dp.toPx()
        val padB = labelHeight + 3.dp.toPx()
        val stripH = STRIP_HEIGHT_DP.dp.toPx()
        val stripGap = STRIP_GAP_DP.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB - stripH - stripGap
        if (plotW <= 0 || plotH <= 0) return@Canvas
        val plotBottom = padT + plotH
        val stripTop = plotBottom + stripGap
        val stripBottom = stripTop + stripH

        // 1. Waterfall bitmap, nearest-neighbor so cells stay crisp.
        if (bitmap != null) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(padL.toInt(), padT.toInt()),
                dstSize = IntSize(plotW.toInt(), plotH.toInt()),
                filterQuality = FilterQuality.None,
            )
        }

        // 2. Energy gridlines + labels (fraction 0 = 20 keV at the bottom).
        val grid = colors.ink2.copy(alpha = 0.18f)
        for ((fraction, label) in energyTicks) {
            val yy = plotBottom - fraction * plotH
            drawLine(grid, Offset(padL, yy), Offset(size.width - padR, yy), 1f)
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(
                    padL - 5.dp.toPx() - measured.size.width,
                    (yy - measured.size.height / 2f).coerceIn(0f, plotBottom),
                ),
            )
        }

        // 3. Synced dose strip: same time axis, filled teal line; gaps stay gaps.
        val strip = spec.stripValues
        if (strip.isNotEmpty()) {
            val stripMax = strip.filterNotNull().maxOrNull()?.coerceAtLeast(1e-6f)
            if (stripMax != null) {
                drawLine(
                    color = colors.line,
                    start = Offset(padL, stripBottom),
                    end = Offset(size.width - padR, stripBottom),
                    strokeWidth = 1f,
                )
                val n = strip.size
                fun x(i: Int): Float = padL + (i + 0.5f) * plotW / n
                fun y(v: Float): Float =
                    stripBottom - (v / stripMax).coerceIn(0f, 1f) * (stripH - 2.dp.toPx())
                val path = Path()
                var penDown = false
                strip.forEachIndexed { i, v ->
                    if (v == null) {
                        penDown = false
                    } else {
                        if (penDown) path.lineTo(x(i), y(v)) else path.moveTo(x(i), y(v))
                        penDown = true
                    }
                }
                drawPath(path, colors.data, style = Stroke(width = 1.6.dp.toPx()))
            }
        }

        // 4. Time labels along the shared axis.
        for ((fraction, label) in spec.timeLabels) {
            val measured = textMeasurer.measure(label, axisStyle)
            val xx = (padL + fraction * plotW - measured.size.width / 2f)
                .coerceIn(0f, size.width - measured.size.width)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(xx, size.height - labelHeight),
            )
        }

        // 5. Time cursor across waterfall and strip.
        val selected = spec.selectedIndex
        if (selected != null && spec.columns.isNotEmpty() && selected in spec.columns.indices) {
            val xx = padL + (selected + 0.5f) * plotW / spec.columns.size
            drawLine(
                color = colors.ink,
                start = Offset(xx, padT),
                end = Offset(xx, stripBottom),
                strokeWidth = 1.dp.toPx(),
            )
        }
        // Hairline frame around the waterfall plot.
        drawRect(
            color = colors.line,
            topLeft = Offset(padL, padT),
            size = Size(plotW, plotH),
            style = Stroke(width = 1f),
        )
    }
}

/** Legend swatch row data: 4 ramp steps from «фон» to «макс», theme-ordered. */
@Composable
fun waterfallLegendColors(): List<Color> {
    val dark = LocalAppColors.current.isDark
    return if (dark) DoseRampColors.reversed() else DoseRampColors
}
