package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Trend chart engine («Научный терминал», design-language.md): raw samples
 * stay visible as dots (alpha 0.55), a smoothed line carries the shape, the
 * baseline band is a translucent fill, axes are always labeled (y values
 * left, time below, mono 10sp), the alarm line is dashed and named
 * («L1 0,30»), and the freshest point is a ringed endpoint dot.
 *
 * Smoothing is display-only; missing columns stay gaps — nothing is
 * interpolated across them.
 */
@Immutable
data class TrendChartSpec(
    /** Raw values per column slot, null = no data in that slot. */
    val columns: List<Float?>,
    /** Top of the y scale, same unit as [columns]. */
    val yMax: Float,
    /** Dashed named alarm line, same unit; omitted when out of frame. */
    val alarmLevel: Float? = null,
    /** Name drawn on the alarm line, e.g. «L1 0,30». */
    val alarmLabel: String? = null,
    /** «Usual range» band, same unit. */
    val band: ClosedFloatingPointRange<Float>? = null,
    /** Value → label gridlines on the y axis. */
    val yTicks: List<Pair<Float, String>> = emptyList(),
    /** Fraction (0..1) → label ticks on the time axis. */
    val xLabels: List<Pair<Float, String>> = emptyList(),
    /** Paint the endpoint dot in the alarm color (confirmed alert only). */
    val endpointAlert: Boolean = false,
)

private const val SMOOTH_RADIUS = 4

@Composable
fun TrendChart(
    spec: TrendChartSpec,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val axisColor = colors.muted
        val labelHeight = textMeasurer.measure("00:00", axisStyle).size.height

        val padL = (spec.yTicks.maxOfOrNull {
            textMeasurer.measure(it.second, axisStyle).size.width
        } ?: 0) + 5.dp.toPx()
        val padR = 5.dp.toPx()
        val padT = 6.dp.toPx()
        val padB = labelHeight + 3.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        if (plotW <= 0 || plotH <= 0 || spec.yMax <= 0f) return@Canvas

        val n = spec.columns.size
        fun x(index: Int): Float =
            padL + if (n <= 1) 0f else index * plotW / (n - 1)
        fun y(value: Float): Float =
            padT + (1f - (value / spec.yMax).coerceIn(0f, 1f)) * plotH

        // 1. Usual-range band.
        spec.band?.let { band ->
            val top = y(band.endInclusive)
            val bottom = y(band.start)
            drawRect(
                color = colors.ink2.copy(alpha = 0.14f),
                topLeft = Offset(padL, top),
                size = Size(plotW, bottom - top),
            )
        }

        // 2. Gridlines + y labels.
        for ((value, label) in spec.yTicks) {
            val yy = y(value)
            drawLine(
                color = colors.ink2.copy(alpha = 0.14f),
                start = Offset(padL, yy),
                end = Offset(size.width - padR, yy),
                strokeWidth = 1f,
            )
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = axisColor,
                topLeft = Offset(
                    padL - 5.dp.toPx() - measured.size.width,
                    yy - measured.size.height / 2f,
                ),
            )
        }

        // 3. Time labels.
        for ((fraction, label) in spec.xLabels) {
            val measured = textMeasurer.measure(label, axisStyle)
            val xx = (padL + fraction * plotW - measured.size.width / 2f)
                .coerceIn(0f, size.width - measured.size.width)
            drawText(
                textLayoutResult = measured,
                color = axisColor,
                topLeft = Offset(xx, size.height - labelHeight),
            )
        }

        // 4. Named alarm line.
        val alarm = spec.alarmLevel
        if (alarm != null && alarm <= spec.yMax) {
            val yy = y(alarm)
            drawLine(
                color = colors.crit.copy(alpha = 0.65f),
                start = Offset(padL, yy),
                end = Offset(size.width - padR, yy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                ),
            )
            spec.alarmLabel?.let { label ->
                val measured = textMeasurer.measure(label, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = colors.crit,
                    topLeft = Offset(
                        padL + 4.dp.toPx(),
                        (yy - 3.dp.toPx() - measured.size.height).coerceAtLeast(0f),
                    ),
                )
            }
        }

        // 5. Raw dots.
        val dotRadius = 1.5.dp.toPx()
        spec.columns.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            drawCircle(
                color = colors.muted.copy(alpha = 0.55f),
                radius = dotRadius,
                center = Offset(x(index), y(value)),
            )
        }

        // 6. Smoothed line, broken at gaps.
        val smoothed = smoothColumns(spec.columns)
        drawSmoothedPath(smoothed, colors.data, ::x, ::y)

        // 7. Endpoint dot with a surface ring.
        val lastIndex = smoothed.indexOfLast { it != null }
        if (lastIndex >= 0) {
            val center = Offset(x(lastIndex), y(smoothed[lastIndex]!!))
            drawCircle(
                color = if (spec.endpointAlert) colors.crit else colors.data,
                radius = 4.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = colors.surface,
                radius = 4.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawSmoothedPath(
    smoothed: List<Float?>,
    color: Color,
    x: (Int) -> Float,
    y: (Float) -> Float,
) {
    val path = Path()
    var penDown = false
    smoothed.forEachIndexed { index, value ->
        if (value == null) {
            penDown = false
        } else {
            if (penDown) path.lineTo(x(index), y(value)) else path.moveTo(x(index), y(value))
            penDown = true
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        ),
    )
}

/** Centered moving average over present neighbours; gaps stay gaps. */
private fun smoothColumns(columns: List<Float?>, radius: Int = SMOOTH_RADIUS): List<Float?> =
    columns.mapIndexed { index, value ->
        if (value == null) return@mapIndexed null
        var sum = 0f
        var count = 0
        for (j in (index - radius)..(index + radius)) {
            val v = columns.getOrNull(j) ?: continue
            sum += v
            count++
        }
        sum / count
    }
