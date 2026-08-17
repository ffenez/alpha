package app.alpha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.chartField
import app.alpha.ui.theme.LocalAppTypography

/**
 * Bar chart for rate/dose series (Поиск 60 s tape, История 30-day dose).
 * Data bars in data teal; an optional reference band (фон ±2σ) with a dashed
 * center line; bars inside the band render dimmed so statistically
 * significant excess stands out by opacity, not by a new color.
 */
@Immutable
data class BarChartSpec(
    /** One value per slot, null = no data in that slot. */
    val values: List<Float?>,
    val yMax: Float,
    /**
     * Низ шкалы. Ноль по умолчанию — так рисуются доза и счёт, у которых
     * отрицательных значений не бывает.
     *
     * Отрицательные величины (нетто над оценённым континуумом бывает и
     * отрицательным) обязаны опускаться НИЖЕ нулевой линии, а не ложиться на
     * неё: прижатый к нулю столбик выглядит как «ровно ноль», хотя число под
     * картинкой в этот момент говорит «−0,29». Одна величина не имеет права
     * выглядеть на графике иначе, чем в подписи.
     */
    val yMin: Float = 0f,
    /** Reference band (e.g. background ±2σ), same unit. */
    val band: ClosedFloatingPointRange<Float>? = null,
    /** Dashed reference line (e.g. the recorded background), same unit. */
    val refLine: Float? = null,
    /** Bars at or below this render at 50% alpha (band top, usually). */
    val dimAtOrBelow: Float? = null,
    /** История: all bars at 60% alpha except the freshest one. */
    val emphasizeLast: Boolean = false,
    /**
     * Столбцы, посчитанные по НЕПОЛНОМУ интервалу (день измерен частично):
     * рисуются контуром, а не заливкой. Полный и неполный столбик — разные
     * величины, и различать их прозрачностью нельзя: прозрачность уже занята
     * под «внутри полосы фона».
     */
    val partial: List<Boolean> = emptyList(),
    val xStartLabel: String? = null,
    val xEndLabel: String? = null,
)

@Composable
fun BarChart(
    spec: BarChartSpec,
    modifier: Modifier = Modifier,
    height: Dp = 85.dp,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxWidth().height(height).chartField()) {
        val hasLabels = spec.xStartLabel != null || spec.xEndLabel != null
        val labelHeight = if (hasLabels) {
            textMeasurer.measure("0", axisStyle).size.height + 3.dp.toPx()
        } else {
            0f
        }
        val padT = 2.dp.toPx()
        val plotH = size.height - padT - labelHeight
        val span = spec.yMax - spec.yMin
        if (plotH <= 0 || span <= 0f || spec.values.isEmpty()) return@Canvas

        fun y(value: Float): Float =
            padT + (1f - ((value - spec.yMin) / span).coerceIn(0f, 1f)) * plotH
        // Основание столбиков — НОЛЬ, а не низ поля: иначе отрицательная
        // величина была бы нарисована как маленькая положительная.
        val baseline = y(0f.coerceIn(spec.yMin, spec.yMax))

        // Reference band + dashed center line.
        spec.band?.let { band ->
            val top = y(band.endInclusive)
            drawRect(
                color = colors.ink2.copy(alpha = 0.16f),
                topLeft = Offset(0f, top),
                size = Size(size.width, y(band.start) - top),
            )
        }
        spec.refLine?.let { ref ->
            val yy = y(ref)
            drawLine(
                color = colors.ink2.copy(alpha = 0.5f),
                start = Offset(0f, yy),
                end = Offset(size.width, yy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                ),
            )
        }

        // Bars.
        val n = spec.values.size
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1.5.dp.toPx())
        val radius = CornerRadius(2.dp.toPx())
        spec.values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val level = y(value)
            val top = minOf(level, baseline)
            val bottom = maxOf(level, baseline)
            val alpha = when {
                spec.emphasizeLast -> if (index == n - 1) 1f else 0.6f
                spec.dimAtOrBelow != null && value <= spec.dimAtOrBelow -> 0.5f
                else -> 1f
            }
            val partial = spec.partial.getOrElse(index) { false }
            if (partial) {
                // Контур вместо заливки: величина посчитана по неполному
                // интервалу и не сравнима с соседями напрямую.
                drawRoundRect(
                    color = colors.data.copy(alpha = alpha),
                    topLeft = Offset(index * (barWidth + gap), top),
                    size = Size(barWidth, (bottom - top).coerceAtLeast(1f)),
                    cornerRadius = radius,
                    style = Stroke(width = 1.dp.toPx()),
                )
            } else {
                drawRoundRect(
                    color = colors.data.copy(alpha = alpha),
                    topLeft = Offset(index * (barWidth + gap), top),
                    size = Size(barWidth, (bottom - top).coerceAtLeast(1f)),
                    cornerRadius = radius,
                )
            }
        }

        // Edge time labels.
        spec.xStartLabel?.let { label ->
            drawText(
                textLayoutResult = textMeasurer.measure(label, axisStyle),
                color = colors.muted,
                topLeft = Offset(0f, size.height - labelHeight + 3.dp.toPx()),
            )
        }
        spec.xEndLabel?.let { label ->
            val measured = textMeasurer.measure(label, axisStyle)
            drawText(
                textLayoutResult = measured,
                color = colors.muted,
                topLeft = Offset(
                    size.width - measured.size.width,
                    size.height - labelHeight + 3.dp.toPx(),
                ),
            )
        }
    }
}
