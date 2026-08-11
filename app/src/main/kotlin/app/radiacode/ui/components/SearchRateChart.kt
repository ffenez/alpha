package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.radiacode.ui.logic.RateChartModel
import app.radiacode.ui.logic.SearchPoint
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.chartField
import app.radiacode.ui.theme.LocalAppTypography
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The count-rate time series of Поиск (search redesign §2).
 *
 * It replaces the decorative bars this screen used to draw. The bars had no
 * quantitative Y scale, so a growing signal looked the same as a steady one and
 * most of the frame was empty — precisely the failure the redesign names. Here
 * the line is the rate itself against a labelled axis, the recorded background
 * is a named dashed line, its expected fluctuation is a band, and a **confirmed**
 * excursion is marked as a named amber stretch — ordinary statistical scatter is
 * never repainted as a finding.
 *
 * Data holes stay holes: a gap longer than [RateChartModel.GAP_MILLIS] breaks
 * the line instead of drawing a straight segment across a lost connection.
 */
@Immutable
data class SearchChartSpec(
    /** Readings, oldest first; [SearchPoint.confirmed] marks the excursion. */
    val points: List<SearchPoint>,
    /** Right edge of the frame — «сейчас». */
    val nowMillis: Long,
    /** Width of the frame in time. */
    val spanMillis: Long,
    /** Top of the Y axis, s⁻¹ (see `RateAutoScale`: sticky on purpose). */
    val yTop: Float,
    /** Expected fluctuation of a single reading around the background. */
    val band: ClosedFloatingPointRange<Float>? = null,
    /** The recorded background itself. */
    val baseline: Float? = null,
    /** «фон 25,5» — the line is never drawn without saying what it is. */
    val baselineLabel: String? = null,
    val xStartLabel: String = "−60 с",
    val xEndLabel: String = "сейчас",
    /** Named marker over the confirmed stretch, e.g. «устойчиво ×1,8». */
    val excursionLabel: String? = null,
)

@Composable
fun SearchRateChart(
    spec: SearchChartSpec,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxWidth().height(height).chartField()) {
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height.toFloat()
        val padT = 6.dp.toPx()
        val padB = labelHeight + 4.dp.toPx()
        val plotH = size.height - padT - padB
        if (plotH <= 0f || spec.yTop <= 0f) return@Canvas

        fun y(value: Float): Float =
            padT + (1f - (value / spec.yTop).coerceIn(0f, 1f)) * plotH

        fun x(timeMillis: Long): Float {
            val age = (spec.nowMillis - timeMillis).toFloat() / spec.spanMillis
            return size.width * (1f - age.coerceIn(0f, 1f))
        }

        // --- Y axis: «nice» steps, labels inside the frame (design language).
        val step = niceStep(spec.yTop / 3f)
        var value = step
        while (value < spec.yTop) {
            val yy = y(value)
            drawLine(
                color = colors.chartGrid,
                start = Offset(0f, yy),
                end = Offset(size.width, yy),
                strokeWidth = 1f,
            )
            val label = textMeasurer.measure(formatTick(value), axisStyle)
            drawText(
                textLayoutResult = label,
                color = colors.muted,
                topLeft = Offset(2.dp.toPx(), yy - label.size.height - 1.dp.toPx()),
            )
            value += step
        }

        // --- Background band and the reference line itself.
        spec.band?.let { band ->
            val top = y(band.endInclusive)
            drawRect(
                color = colors.ink2.copy(alpha = 0.16f),
                topLeft = Offset(0f, top),
                size = Size(size.width, (y(band.start) - top).coerceAtLeast(1f)),
            )
        }
        spec.baseline?.let { baseline ->
            val yy = y(baseline)
            drawLine(
                color = colors.ink2.copy(alpha = 0.6f),
                start = Offset(0f, yy),
                end = Offset(size.width, yy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            spec.baselineLabel?.let { text ->
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

        // --- The series, broken at every hole in the stream.
        val stroke = Stroke(width = 1.6.dp.toPx())
        var path = Path()
        var started = false
        var previous: SearchPoint? = null
        fun flush() {
            if (started) drawPath(path, color = colors.data, style = stroke)
            path = Path()
            started = false
        }
        for (point in spec.points) {
            val previousPoint = previous
            if (previousPoint != null &&
                RateChartModel.isGap(previousPoint.timeMillis, point.timeMillis)
            ) {
                flush()
            }
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

        // --- Confirmed excursions: named, over the same line, never a repaint
        // of ordinary scatter.
        val spans = RateChartModel.confirmedSpans(spec.points)
        val excursionStroke = Stroke(width = 2.4.dp.toPx())
        for (span in spans) {
            val excursion = Path()
            var open = false
            var last: SearchPoint? = null
            for (index in span) {
                val point = spec.points[index]
                val prior = last
                if (prior != null && RateChartModel.isGap(prior.timeMillis, point.timeMillis)) {
                    if (open) drawPath(excursion, color = colors.warn, style = excursionStroke)
                    excursion.reset()
                    open = false
                }
                val px = x(point.timeMillis)
                val py = y(point.cps)
                if (!open) {
                    excursion.moveTo(px, py)
                    open = true
                } else {
                    excursion.lineTo(px, py)
                }
                last = point
            }
            if (open) drawPath(excursion, color = colors.warn, style = excursionStroke)
        }
        spec.excursionLabel?.let { text ->
            val span = spans.lastOrNull() ?: return@let
            val measured = textMeasurer.measure(text, axisStyle)
            val startX = x(spec.points[span.first].timeMillis)
            drawText(
                textLayoutResult = measured,
                color = colors.warn,
                topLeft = Offset(
                    startX.coerceAtMost(size.width - measured.size.width),
                    padT,
                ),
            )
        }

        // --- Time edges.
        val start = textMeasurer.measure(spec.xStartLabel, axisStyle)
        drawText(
            textLayoutResult = start,
            color = colors.muted,
            topLeft = Offset(0f, size.height - labelHeight),
        )
        val end = textMeasurer.measure(spec.xEndLabel, axisStyle)
        drawText(
            textLayoutResult = end,
            color = colors.muted,
            topLeft = Offset(size.width - end.size.width, size.height - labelHeight),
        )
    }
}

/** «Nice» axis step on the 1/2/5·10ᵏ ladder (design language: labelled axes). */
private fun niceStep(raw: Float): Float {
    if (!raw.isFinite() || raw <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(raw.toDouble())))
    val normalized = raw / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return (nice * magnitude).toFloat()
}

private fun formatTick(value: Float): String = when {
    value >= 10f -> String.format(Locale.US, "%.0f", value)
    else -> String.format(Locale.US, "%.1f", value).replace('.', ',')
}
