package app.alpha.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.ui.logic.NavigateArc
import app.alpha.ui.logic.NavigateTrend
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppTypography

/**
 * Прямая шкала прибора — то же утверждение, что и циферблат
 * ([NavigateGauge]), другим рисунком.
 *
 * Обе картинки строятся из одного [NavigateGaugeSpec] и одной математики
 * ([app.alpha.ui.logic.ArcScale]), поэтому выбор вида не может изменить
 * показание: меняется форма, а не то, что сказано. Прямая шкала занимает
 * строку вместо круга — на неё переходят, когда экран нужен под график и
 * ленту, а не под прибор.
 *
 * Всё остальное — как у циферблата: сектор обычного разброса места, риска
 * порога, засечки-удвоения с подписями, маркер, который ЕДЕТ (это подача
 * измерения, а не само измерение), и строка состояния под шкалой.
 */
@Composable
fun InstrumentBar(
    spec: NavigateGaugeSpec,
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
) {
    val colors = LocalAppColors.current
    val metrics = LocalAppMetrics.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
    val motionAllowed = rememberMotionAllowed()

    val fillColor = when (spec.trend) {
        NavigateTrend.RISING -> colors.warn
        else -> colors.data
    }

    val targetPosition = spec.ratio?.let { NavigateArc.position(it, spec.scale) }
    val animatedPosition by animateFloatAsState(
        targetValue = targetPosition ?: NavigateArc.position(1.0, spec.scale),
        animationSpec = if (motionAllowed) {
            tween(SETTLE_MILLIS, easing = SETTLE_EASING)
        } else {
            snap()
        },
        label = "barMarker",
    )

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val border = metrics.border.toPx()
        val padding = 10.dp.toPx()
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height.toFloat()
        val axisY = padding + labelHeight + 12.dp.toPx()
        val left = padding
        val right = size.width - padding
        val width = right - left
        if (width <= 0f) return@Canvas
        fun x(fraction: Float) = left + fraction * width

        val trackWidth = 6.dp.toPx()
        drawLine(
            color = colors.chartGrid,
            start = Offset(left, axisY),
            end = Offset(right, axisY),
            strokeWidth = trackWidth,
            cap = StrokeCap.Butt,
        )

        // Обычный разброс места — та же ось, шире и тусклее.
        val bandLow = spec.bandLow
        val bandHigh = spec.bandHigh
        if (bandLow != null && bandHigh != null && bandHigh > bandLow) {
            drawLine(
                color = colors.ink2.copy(alpha = 0.26f),
                start = Offset(x(NavigateArc.position(bandLow, spec.scale)), axisY),
                end = Offset(x(NavigateArc.position(bandHigh, spec.scale)), axisY),
                strokeWidth = trackWidth * 2.4f,
                cap = StrokeCap.Butt,
            )
        }

        // Заливка от ×1 до показания — пройденная часть шкалы.
        if (targetPosition != null) {
            val from = NavigateArc.position(1.0, spec.scale)
            drawLine(
                color = fillColor,
                start = Offset(x(minOf(from, animatedPosition)), axisY),
                end = Offset(x(maxOf(from, animatedPosition)), axisY),
                strokeWidth = trackWidth,
                cap = StrokeCap.Butt,
            )
        }

        // Засечки-удвоения с подписями над осью.
        val ticks = NavigateArc.ticks(spec.scale)
        val steps = ticks.size - 1
        val labelStep = if (steps <= 4) 1 else steps / 2
        ticks.forEachIndexed { index, value ->
            val reference = value == 1.0
            val at = x(NavigateArc.position(value, spec.scale))
            drawLine(
                color = if (reference) colors.ink2 else colors.muted,
                start = Offset(at, axisY - trackWidth / 2f - 4.dp.toPx()),
                end = Offset(at, axisY - trackWidth / 2f),
                strokeWidth = if (reference) border * 2f else border,
            )
            if (index % labelStep != 0 && !reference) return@forEachIndexed
            val text = if (reference) {
                spec.referenceLabel
            } else {
                "${NavigateArc.factorLabel(value)}×"
            }
            val measured = textMeasurer.measure(text, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = if (reference) colors.ink2 else colors.muted,
                topLeft = Offset(
                    (at - measured.size.width / 2f)
                        .coerceIn(0f, size.width - measured.size.width),
                    axisY - trackWidth / 2f - 6.dp.toPx() - measured.size.height,
                ),
            )
        }

        // Порог — своя риска с подписью под осью; за концом шкалы не рисуется.
        for ((threshold, color) in listOf(
            spec.threshold to colors.warn,
            spec.threshold2 to colors.crit,
        )) {
            if (threshold == null || NavigateArc.offScale(threshold, spec.scale)) continue
            val at = x(NavigateArc.position(threshold, spec.scale))
            drawLine(
                color = color,
                start = Offset(at, axisY - trackWidth),
                end = Offset(at, axisY + trackWidth),
                strokeWidth = 2.dp.toPx(),
            )
            if (threshold != spec.threshold) continue
            spec.thresholdLabel?.let { text ->
                val measured = textMeasurer.measure(text, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = color,
                    topLeft = Offset(
                        (at - measured.size.width / 2f)
                            .coerceIn(0f, size.width - measured.size.width),
                        axisY + trackWidth + 3.dp.toPx(),
                    ),
                )
            }
        }

        // Маркер показания — чернильная риска поверх заливки, как стрелка у
        // циферблата: её читают по контрасту, а не по цвету.
        if (targetPosition != null) {
            val at = x(animatedPosition)
            drawLine(
                color = colors.ink,
                start = Offset(at, axisY - trackWidth * 1.6f),
                end = Offset(at, axisY + trackWidth * 1.6f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

    }
}

/** Время подхода маркера — то же, что у стрелки: это один прибор. */
private const val SETTLE_MILLIS = 700

private val SETTLE_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)
