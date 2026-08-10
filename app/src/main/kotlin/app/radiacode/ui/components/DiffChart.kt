package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.radiacode.analysis.SpectrumDisplay
import app.radiacode.ui.logic.CompareFormat
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlin.math.abs
import kotlin.math.max

/**
 * Difference chart for the spectrum comparator («Научный терминал»): the
 * A−B count-rate difference per energy column around an explicit zero line,
 * inside its own ±1σ/±2σ Poisson bands (translucent fills, exactly like the
 * baseline band on trend charts). Symmetric linear y scale — a difference
 * has a sign, so a log axis would lie. Axes always labeled: cps on the left,
 * keV below.
 */
@Immutable
data class DiffChartSpec(
    /** Rate difference per column, counts/s (sums over the column's channels). */
    val diff: List<Float>,
    /** 1σ of each column's difference (quadrature over its channels). */
    val sigma: List<Float>,
    val energyTicks: List<SpectrumDisplay.EnergyTick> = emptyList(),
)

@Composable
fun DiffChart(
    spec: DiffChartSpec,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val n = spec.diff.size
        if (n == 0) return@Canvas

        // Symmetric scale covering both the data and its 2σ envelope.
        var top = 0f
        for (i in 0 until n) {
            top = max(top, max(abs(spec.diff[i]), 2f * (spec.sigma.getOrNull(i) ?: 0f)))
        }
        if (top <= 0f) top = 1f
        top *= 1.1f

        val yLabels = listOf(top to CompareFormat.axisCps(top), 0f to "0",
            -top to "−" + CompareFormat.axisCps(top))
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height
        val padL = yLabels.maxOf { textMeasurer.measure(it.second, axisStyle).size.width } +
            4.dp.toPx()
        val padR = 4.dp.toPx()
        val padT = 6.dp.toPx()
        val padB = labelHeight + 3.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        if (plotW <= 0 || plotH <= 0) return@Canvas
        val bottom = padT + plotH

        fun y(value: Float): Float {
            val fraction = ((value / top) + 1f) / 2f
            return padT + (1f - fraction.coerceIn(0f, 1f)) * plotH
        }

        fun x(index: Int): Float = padL + if (n <= 1) 0f else index * plotW / (n - 1)

        val grid = colors.ink2.copy(alpha = 0.14f)

        // 1. Energy ticks: vertical gridlines + keV labels below.
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

        // 2. σ bands around zero: ±2σ under ±1σ, both translucent.
        fun bandPath(scale: Float): Path {
            val path = Path()
            for (i in 0 until n) {
                val value = scale * (spec.sigma.getOrNull(i) ?: 0f)
                val point = Offset(x(i), y(value))
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            for (i in n - 1 downTo 0) {
                val value = -scale * (spec.sigma.getOrNull(i) ?: 0f)
                path.lineTo(x(i), y(value))
            }
            path.close()
            return path
        }
        drawPath(bandPath(2f), color = colors.ink2.copy(alpha = 0.10f))
        drawPath(bandPath(1f), color = colors.ink2.copy(alpha = 0.14f))

        // 3. Horizontal gridlines + y labels (top / zero / bottom).
        for ((value, label) in yLabels) {
            val yy = y(value)
            drawLine(
                color = if (value == 0f) colors.ink2.copy(alpha = 0.5f) else grid,
                start = Offset(padL, yy),
                end = Offset(size.width - padR, yy),
                strokeWidth = 1f,
            )
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(
                    padL - 4.dp.toPx() - measured.size.width,
                    (yy - measured.size.height / 2f)
                        .coerceIn(0f, size.height - measured.size.height),
                ),
            )
        }

        // 4. Difference line.
        val line = Path()
        spec.diff.forEachIndexed { index, value ->
            val point = Offset(x(index), y(value))
            if (index == 0) line.moveTo(point.x, point.y) else line.lineTo(point.x, point.y)
        }
        drawPath(
            path = line,
            color = colors.data,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
