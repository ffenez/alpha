package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.radiacode.analysis.SpectrumDisplay
import app.radiacode.ui.logic.SpectrumScale
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.chartField
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Spectrum chart («Научный терминал», design-language.md): counts/keV as a
 * line with an area fill, log decades labeled 1/10/10²/10³/10⁴ (linear:
 * labeled quarter lines), keV ticks on the bottom axis, the recorded
 * background as a dimmed overlay line, and labeled tick markers above
 * detected peaks — amber only for the highlighted isotope candidate.
 */
@Immutable
data class SpectrumPeakMark(
    /** Column index of the peak center in [SpectrumChartSpec.columns]. */
    val columnIndex: Int,
    /** Marker label, e.g. «662». */
    val label: String,
    /** Amber highlighted candidate (matches the selected table row). */
    val highlighted: Boolean = false,
)

@Immutable
data class SpectrumChartSpec(
    /** Aggregated counts per column (linear values; the chart maps them). */
    val columns: List<Float>,
    /** Background overlay series in the same columns; null = no overlay. */
    val overlay: List<Float>? = null,
    /** Как высота столбца получается из числа импульсов. */
    val scale: SpectrumScale = SpectrumScale.Log,
    /** Scale top: linear max or a power of ten for log (see [SpectrumDisplay.logTop]). */
    val yTop: Float,
    val peaks: List<SpectrumPeakMark> = emptyList(),
    val energyTicks: List<SpectrumDisplay.EnergyTick> = emptyList(),
)

/** Bottom of the log scale (mirrors the mockup: fractions of a count clamp here). */
private const val LOG_FLOOR = 0.6f

@Composable
fun SpectrumChart(
    spec: SpectrumChartSpec,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    /** Pinch/drag: scale factor (>1 = zoom in), pan and focus as width fractions. */
    onGesture: ((scale: Float, panFraction: Float, focusFraction: Float) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .chartField()
            .then(
                if (onGesture == null) {
                    Modifier
                } else {
                    // Экран прокручивается по вертикали, поэтому горизонтальный
                    // сдвиг обязан забирать событие себе: иначе прокрутка
                    // перехватывает палец и график «не двигается».
                    Modifier
                        .pointerInput(Unit) {
                            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                onGesture(zoom, pan.x / w, (centroid.x / w).coerceIn(0f, 1f))
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                onGesture(1f, dragAmount / w, 0.5f)
                            }
                        }
                },
            ),
    ) {
        if (spec.columns.isEmpty() || spec.yTop <= 0f) return@Canvas

        // Подписи оси задаёт сам масштаб: декады у логарифма, четверти у
        // линейного, неравномерные значения у степенного.
        val yLabels: List<Pair<Float, String>> = spec.scale.ticks(spec.yTop).map { value ->
            value to compactCount(value)
        }

        val labelHeight = textMeasurer.measure("0", axisStyle).size.height
        val padL = yLabels.maxOf { textMeasurer.measure(it.second, axisStyle).size.width } +
            4.dp.toPx()
        val padR = 4.dp.toPx()
        val padT = 18.dp.toPx() // room for peak marker labels
        val padB = labelHeight + 3.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        if (plotW <= 0 || plotH <= 0) return@Canvas
        val bottom = padT + plotH

        fun y(value: Float): Float =
            padT + (1f - spec.scale.fraction(value, spec.yTop)) * plotH

        val n = spec.columns.size
        fun x(index: Int): Float =
            padL + if (n <= 1) 0f else index * plotW / (n - 1)

        val grid = colors.ink2.copy(alpha = 0.14f)

        // 1a. Тонкая сетка внутри декады (только логарифм): без неё
        // положение между 10 и 100 нечитаемо — 30 и 80 выглядят одинаково.
        if (spec.scale is SpectrumScale.Log) {
            val minor = colors.ink2.copy(alpha = 0.06f)
            for (value in SpectrumScale.Log.minorTicks(spec.yTop)) {
                val yy = y(value)
                drawLine(minor, Offset(padL, yy), Offset(size.width - padR, yy), 1f)
            }
        }

        // 1. Horizontal gridlines + y labels.
        for ((value, label) in yLabels) {
            val yy = y(value)
            drawLine(grid, Offset(padL, yy), Offset(size.width - padR, yy), 1f)
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(
                    padL - 4.dp.toPx() - measured.size.width,
                    yy - measured.size.height / 2f,
                ),
            )
        }

        // 2. Energy ticks: vertical gridlines + keV labels below.
        for (tick in spec.energyTicks) {
            val xx = padL + tick.fraction * plotW
            drawLine(grid, Offset(xx, padT), Offset(xx, bottom), 1f)
            val measured = textMeasurer.measure("${tick.keV}", axisStyle)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(
                    (xx - measured.size.width / 2f)
                        .coerceIn(0f, size.width - measured.size.width),
                    size.height - labelHeight,
                ),
            )
        }

        // 3. Background overlay: dimmed muted line.
        spec.overlay?.let { overlay ->
            val path = Path()
            overlay.forEachIndexed { index, value ->
                if (index >= n) return@forEachIndexed
                val point = Offset(x(index), y(value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = colors.muted.copy(alpha = 0.7f),
                style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round),
            )
        }

        // 4. Data line + area fill.
        val line = Path()
        spec.columns.forEachIndexed { index, value ->
            val point = Offset(x(index), y(value))
            if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(n - 1), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(path = area, color = colors.data.copy(alpha = 0.16f))
        drawPath(
            path = line,
            color = colors.data,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 5. Peak markers: tick + label above the local line top; amber only
        // for the highlighted candidate (color + the table carry the meaning).
        for (peak in spec.peaks) {
            if (peak.columnIndex !in spec.columns.indices) continue
            var top = Float.MAX_VALUE
            for (j in (peak.columnIndex - 3)..(peak.columnIndex + 3)) {
                val v = spec.columns.getOrNull(j) ?: continue
                top = minOf(top, y(v))
            }
            if (top == Float.MAX_VALUE) continue
            val xx = x(peak.columnIndex)
            val color = if (peak.highlighted) colors.warn else colors.ink2
            drawLine(
                color = color,
                start = Offset(xx, (top - 3.dp.toPx()).coerceAtLeast(labelHeight + 2.dp.toPx())),
                end = Offset(xx, (top - 9.dp.toPx()).coerceAtLeast(labelHeight + 2.dp.toPx())),
                strokeWidth = 2.dp.toPx(),
            )
            val measured = textMeasurer.measure(peak.label, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = color,
                topLeft = Offset(
                    (xx - measured.size.width / 2f)
                        .coerceIn(0f, size.width - measured.size.width),
                    (top - 11.dp.toPx() - measured.size.height).coerceAtLeast(0f),
                ),
            )
        }
    }
}

/** 1234 → «1,2k», 250 → «250» — compact linear-axis count label. */
private fun compactCount(value: Float): String = when {
    value >= 10_000f -> "${(value / 1000f).toInt()}k"
    value >= 1_000f ->
        String.format(Locale.US, "%.1f", value / 1000f).replace('.', ',') + "k"
    else -> "${value.toInt()}"
}
