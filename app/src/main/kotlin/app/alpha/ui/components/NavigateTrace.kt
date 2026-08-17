package app.alpha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.ui.logic.NavigateTraceScale
import app.alpha.ui.logic.RateChartModel
import app.alpha.ui.logic.SearchPoint
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.chartField

/** The short-window series of «Наведение» and the local level under it. */
@Immutable
data class NavigateTraceSpec(
    /** Short-window rate at each reading, oldest first. */
    val points: List<SearchPoint>,
    val nowMillis: Long,
    val spanMillis: Long,
    /** The slower estimate, drawn as one calm line; null while unavailable. */
    val localLevel: Float?,
    /** «локальный уровень 24,8» — the line is never drawn without its name. */
    val localLabel: String?,
    val startLabel: String,
    val endLabel: String,
)

/**
 * The last twenty seconds, as a line — the context the arc cannot carry.
 *
 * The drawn value is the **short window**, not the raw per-second reading: the
 * raw stream at an ordinary background scatters by ±20 % from counting
 * statistics alone, and a picture of that is a picture of noise. Holes in the
 * stream break the line rather than being bridged, exactly as on the verify
 * tape — a straight segment across a lost connection is data the instrument
 * never sent.
 *
 * The axis does not start at zero and says so by labelling both ends: over
 * twenty seconds the interesting part is the shape, and a zero-based axis
 * flattens it.
 */
@Composable
fun NavigateTrace(
    spec: NavigateTraceSpec,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
) {
    val colors = LocalAppColors.current
    val metrics = LocalAppMetrics.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxWidth().height(height).chartField()) {
        val border = metrics.border.toPx()
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height.toFloat()
        val padT = 6.dp.toPx()
        val padB = labelHeight + 4.dp.toPx()
        val plotH = size.height - padT - padB
        if (plotH <= 0f) return@Canvas

        val bounds = NavigateTraceScale.of(spec.points.map { it.cps }, spec.localLevel)
        val span = (bounds.endInclusive - bounds.start).coerceAtLeast(1e-3f)

        fun y(value: Float): Float =
            padT + (1f - ((value - bounds.start) / span).coerceIn(0f, 1f)) * plotH

        fun x(timeMillis: Long): Float {
            val age = (spec.nowMillis - timeMillis).toFloat() / spec.spanMillis
            return size.width * (1f - age.coerceIn(0f, 1f))
        }

        // The local level: a calm reference line, dashed so it can never be
        // mistaken for the measured series.
        spec.localLevel?.let { level ->
            val yy = y(level)
            drawLine(
                color = colors.ink2.copy(alpha = 0.6f),
                start = Offset(0f, yy),
                end = Offset(size.width, yy),
                strokeWidth = border,
                cap = StrokeCap.Butt,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            spec.localLabel?.let { text ->
                val measured = textMeasurer.measure(text, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = colors.ink2,
                    topLeft = Offset(
                        size.width - measured.size.width - 2.dp.toPx(),
                        (yy + 2.dp.toPx()).coerceAtMost(padT + plotH - measured.size.height),
                    ),
                )
            }
        }

        // The series, broken at every hole in the stream.
        val stroke = Stroke(width = border * 1.6f, cap = StrokeCap.Butt)
        var path = Path()
        var started = false
        var previous: SearchPoint? = null
        fun flush() {
            if (started) drawPath(path, color = colors.data, style = stroke)
            path = Path()
            started = false
        }
        for (point in spec.points) {
            val prior = previous
            if (prior != null && RateChartModel.isGap(prior.timeMillis, point.timeMillis)) flush()
            val px = x(point.timeMillis)
            val py = y(point.cps)
            if (!started) {
                path.moveTo(px, py)
                started = true
            } else {
                path.lineTo(px, py)
            }
            previous = point
        }
        flush()

        // Both ends of a non-zero axis, and both ends of the time window.
        val top = textMeasurer.measure(tick(bounds.endInclusive), axisStyle)
        drawText(top, color = colors.muted, topLeft = Offset(2.dp.toPx(), padT))
        val bottom = textMeasurer.measure(tick(bounds.start), axisStyle)
        drawText(
            textLayoutResult = bottom,
            color = colors.muted,
            topLeft = Offset(2.dp.toPx(), padT + plotH - bottom.size.height),
        )
        val start = textMeasurer.measure(spec.startLabel, axisStyle)
        drawText(start, color = colors.muted, topLeft = Offset(0f, size.height - labelHeight))
        val end = textMeasurer.measure(spec.endLabel, axisStyle)
        drawText(
            textLayoutResult = end,
            color = colors.muted,
            topLeft = Offset(size.width - end.size.width, size.height - labelHeight),
        )
    }
}

private fun tick(value: Float): String = when {
    value >= 10f -> String.format(java.util.Locale.US, "%.0f", value)
    else -> String.format(java.util.Locale.US, "%.1f", value).replace('.', ',')
}
