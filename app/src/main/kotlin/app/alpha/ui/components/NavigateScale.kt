package app.alpha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import app.alpha.ui.theme.chartField

/**
 * Прямая шкала «Наведения» — то же утверждение, что и стрелка
 * ([NavigateGauge]), другим рисунком.
 *
 * Обе картинки строятся из одного [NavigateGaugeSpec] и одной математики
 * ([NavigateArc.position]), поэтому переключение вида не может изменить
 * показание: меняется форма, а не то, что сказано. Прямая шкала занимает
 * строку вместо круга и говорит тем же языком, что шкала места на Главной —
 * ось отношений, ×1 посередине, равные множители на равных расстояниях.
 *
 * Как и у стрелки: измеренное не анимируется — маркер переставляется шагом,
 * плавно двигается только рамка шкалы; цветных зон нет, потому что это счёт, а
 * не приговор; толщины берутся из [LocalAppMetrics], поэтому 8-bit получает ту
 * же шкалу более грубым штрихом.
 */
@Composable
fun NavigateScale(
    spec: NavigateGaugeSpec,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
) {
    val colors = LocalAppColors.current
    val metrics = LocalAppMetrics.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    val markerColor = when (spec.trend) {
        NavigateTrend.RISING -> colors.warn
        NavigateTrend.FALLING -> colors.ink2
        NavigateTrend.NO_CHANGE -> colors.ink
        NavigateTrend.COLLECTING -> colors.muted
    }

    Canvas(modifier = modifier.fillMaxWidth().height(height).chartField()) {
        val border = metrics.border.toPx()
        val padding = 12.dp.toPx()
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height.toFloat()
        val captions = spec.lowCaption != null ||
            spec.referenceCaption != null ||
            spec.highCaption != null
        val labelRows = if (captions) 2f else 1f
        val left = padding
        val right = size.width - padding
        val width = right - left
        if (width <= 0f) return@Canvas
        val axisY = padding + (size.height - labelRows * labelHeight - 3f * padding) / 2f

        fun x(ratio: Double): Float = left + width * NavigateArc.position(ratio, spec.factor)

        // Интервал — ширина полосы, а не отдельный вердикт: узкий и широкий
        // интервалы не имеют права выглядеть одинаково.
        val bandLow = spec.intervalLow
        val bandHigh = spec.intervalHigh
        if (bandLow != null && bandHigh != null && bandLow > 0.0 && bandHigh >= bandLow) {
            val from = x(bandLow)
            val to = x(bandHigh)
            drawLine(
                color = colors.ink2.copy(alpha = 0.28f),
                start = Offset(from, axisY),
                end = Offset(maxOf(to, from + MIN_BAND_PX), axisY),
                strokeWidth = border * 7f,
                cap = StrokeCap.Butt,
            )
        }

        drawLine(
            color = colors.chartGrid,
            start = Offset(left, axisY),
            end = Offset(right, axisY),
            strokeWidth = border * 2f,
            cap = StrokeCap.Butt,
        )

        // Засечки — удвоения; ×1 длиннее и чернильного цвета: это единственная
        // засечка, которая что-то значит сама по себе.
        val tick = 7.dp.toPx()
        for (value in NavigateArc.ticks(spec.factor)) {
            val reference = value == 1.0
            val length = if (reference) tick * 2f else tick
            drawLine(
                color = if (reference) colors.ink2 else colors.chartGrid,
                start = Offset(x(value), axisY - length),
                end = Offset(x(value), axisY + length),
                strokeWidth = if (reference) border * 2f else border,
                cap = StrokeCap.Butt,
            )
        }

        // Удержанный максимум — метка на той же шкале, но не второй маркер.
        spec.peakRatio?.let { peak ->
            val px = x(peak)
            val apexY = axisY - tick * 2f
            drawPath(
                path = Path().apply {
                    moveTo(px, apexY)
                    lineTo(px - tick * 0.7f, apexY - tick * 1.4f)
                    lineTo(px + tick * 0.7f, apexY - tick * 1.4f)
                    close()
                },
                color = colors.ink2,
            )
        }

        // Маркер значения — последним, чтобы его ничто не пересекало.
        spec.ratio?.let { ratio ->
            val px = x(ratio)
            drawLine(
                color = markerColor,
                start = Offset(px, axisY - tick * 2.6f),
                end = Offset(px, axisY + tick * 2.6f),
                strokeWidth = border * 3f,
                cap = StrokeCap.Butt,
            )
            val head = 6.dp.toPx()
            drawPath(
                path = Path().apply {
                    moveTo(px, axisY + tick * 2.6f)
                    lineTo(px - head * 0.7f, axisY + tick * 2.6f + head)
                    lineTo(px + head * 0.7f, axisY + tick * 2.6f + head)
                    close()
                },
                color = markerColor,
            )
        }

        val captionBaseline = size.height - labelHeight - padding / 2f
        val baseline = captionBaseline - (labelRows - 1f) * labelHeight

        fun row(low: String?, middle: String?, high: String?, y: Float, color: Color) {
            low?.let {
                drawText(textMeasurer.measure(it, axisStyle), color = color, topLeft = Offset(left, y))
            }
            high?.let {
                val measured = textMeasurer.measure(it, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = color,
                    topLeft = Offset(right - measured.size.width, y),
                )
            }
            middle?.let {
                val measured = textMeasurer.measure(it, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = if (color == colors.muted) colors.ink2 else color,
                    // Середина шкалы — это ×1, а не середина экрана.
                    topLeft = Offset(x(1.0) - measured.size.width / 2f, y),
                )
            }
        }

        row(spec.lowLabel, spec.referenceLabel, spec.highLabel, baseline, colors.muted)
        if (captions) {
            row(
                spec.lowCaption,
                spec.referenceCaption,
                spec.highCaption,
                captionBaseline,
                colors.muted,
            )
        }
    }
}

/** Очень узкий интервал обязан остаться видимой полосой, а не исчезнуть. */
private const val MIN_BAND_PX = 3f
