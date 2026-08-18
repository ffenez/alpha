package app.alpha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.alpha.ui.logic.PlaceScale
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppTypography

/**
 * Шкала места под главным числом: где стоит текущее показание относительно
 * обычного фона ЭТОГО места.
 *
 * Что нарисовано и почему именно это:
 *
 *  - серая полоса — обычный разброс места (P10–P90 профиля): она отвечает на
 *    вопрос «бывает ли здесь так»;
 *  - янтарная риска — порог тревоги, и она стоит на той же оси, поэтому видно
 *    не только «выше обычного», но и «сколько ещё до порога»;
 *  - маркер — текущее значение. Он переставляется ШАГОМ: положение маркера —
 *    измеренное число, а измеренное в этом приложении не доезжает;
 *  - след — где значение побывало за последнюю минуту; это история, и она
 *    двигается плавно.
 *
 * Медиана места стоит на ×1 — на четверти шкалы, а не в середине: вправо
 * оставлено место для настоящего роста ([PlaceScale]).
 */
@Composable
fun PlaceScaleBar(
    /** Текущее значение в единицах хранения (мкЗв/ч). */
    value: Float?,
    /** Медиана профиля места; null — сравнивать не с чем, шкала не рисуется. */
    medianMicroSvH: Float?,
    /** Обычный разброс места, P10–P90. */
    lowMicroSvH: Float?,
    highMicroSvH: Float?,
    /** Порог тревоги на той же оси; null — порога нет. */
    thresholdMicroSvH: Float?,
    /** Куда значение ходило за последнюю минуту. */
    trailLowMicroSvH: Float? = null,
    trailHighMicroSvH: Float? = null,
    /** Цвет маркера — тот же, что у числа. */
    tint: Color = LocalAppColors.current.ok,
    modifier: Modifier = Modifier,
) {
    val median = medianMicroSvH ?: return
    if (median <= 0f) return
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val metrics = LocalAppMetrics.current
    val measurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val border = metrics.border.toPx()
            val axisY = size.height * 0.62f
            fun x(fraction: Float) = fraction * size.width

            drawLine(
                color = colors.line,
                start = Offset(0f, axisY),
                end = Offset(size.width, axisY),
                strokeWidth = border * 2f,
                cap = StrokeCap.Butt,
            )
            for (ratio in PlaceScale.ticks()) {
                val reference = ratio == 1.0
                val at = x(PlaceScale.position(ratio))
                drawLine(
                    color = if (reference) colors.ink2 else colors.line,
                    start = Offset(at, axisY - if (reference) 7.dp.toPx() else 4.dp.toPx()),
                    end = Offset(at, axisY + if (reference) 7.dp.toPx() else 4.dp.toPx()),
                    strokeWidth = if (reference) border * 2f else border,
                )
            }
            val low = PlaceScale.positionOf(lowMicroSvH, median)
            val high = PlaceScale.positionOf(highMicroSvH, median)
            if (low != null && high != null && high > low) {
                drawLine(
                    color = colors.ink2.copy(alpha = 0.22f),
                    start = Offset(x(low), axisY),
                    end = Offset(x(high), axisY),
                    strokeWidth = 12.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
            val trailLow = PlaceScale.positionOf(trailLowMicroSvH, median)
            val trailHigh = PlaceScale.positionOf(trailHighMicroSvH, median)
            if (trailLow != null && trailHigh != null) {
                drawLine(
                    color = tint.copy(alpha = 0.3f),
                    start = Offset(x(trailLow), axisY),
                    end = Offset(x(maxOf(trailHigh, trailLow + 0.004f)), axisY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            PlaceScale.positionOf(thresholdMicroSvH, median)?.let { at ->
                drawLine(
                    color = colors.warn,
                    start = Offset(x(at), axisY - 9.dp.toPx()),
                    end = Offset(x(at), axisY + 9.dp.toPx()),
                    strokeWidth = border * 2f,
                )
            }
            PlaceScale.positionOf(value, median)?.let { at ->
                val markerX = x(at)
                drawLine(
                    color = tint,
                    start = Offset(markerX, axisY - 10.dp.toPx()),
                    end = Offset(markerX, axisY + 10.dp.toPx()),
                    strokeWidth = border * 3f,
                )
                drawCircle(color = tint, radius = 3.5.dp.toPx(), center = Offset(markerX, axisY - 12.dp.toPx()))
            }
            // Подписи концов и середины: без них шкала — просто полоска.
            val labels = listOf(
                0f to "×0,5",
                PlaceScale.position(1.0) to "×1",
                PlaceScale.position(4.0) to "×4",
                1f to "×8",
            )
            for ((fraction, text) in labels) {
                val measured = measurer.measure(text, type.axis)
                val left = (x(fraction) - measured.size.width / 2f)
                    .coerceIn(0f, size.width - measured.size.width)
                drawText(
                    textLayoutResult = measured,
                    color = colors.muted,
                    topLeft = Offset(left, size.height - measured.size.height),
                )
            }
        }
    }
}
