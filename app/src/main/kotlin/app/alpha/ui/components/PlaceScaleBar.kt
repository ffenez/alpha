package app.alpha.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 *  - риски порогов — оба заданных уровня на той же оси, поэтому видно не
 *    только «выше обычного», но и «сколько ещё до каждого из них»;
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
    /**
     * Пороги тревоги на той же оси; null — порога нет.
     *
     * Уровней два, и на шкале их тоже два: настроив второй, человек ищет его
     * глазами, а одна риска отвечала бы на вопрос «сколько осталось» только
     * про первый.
     */
    thresholdMicroSvH: Float?,
    threshold2MicroSvH: Float? = null,
    /** Куда значение ходило за последнюю минуту. */
    trailLowMicroSvH: Float? = null,
    trailHighMicroSvH: Float? = null,
    /**
     * Подпись риски порога («порог»); null — риска без подписи. Стоит на месте
     * подписи ×4 (эталон): две подписи в одной точке слились бы.
     */
    thresholdLabel: String? = null,
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
    val motionAllowed = rememberMotionAllowed()

    // След — история, и она ЕДЕТ плавно (эталон: 600 мс), в отличие от
    // маркера, который переставляется шагом: маркер — измеренное значение.
    val trailSpec: AnimationSpec<Float> = if (motionAllowed) {
        tween(TRAIL_SETTLE_MILLIS)
    } else {
        snap()
    }
    val trailLowTarget = PlaceScale.positionOf(trailLowMicroSvH, median)
    val trailHighTarget = PlaceScale.positionOf(trailHighMicroSvH, median)
    val trailLowAnimated by animateFloatAsState(
        targetValue = trailLowTarget ?: 0f,
        animationSpec = trailSpec,
        label = "trailLow",
    )
    val trailHighAnimated by animateFloatAsState(
        targetValue = trailHighTarget ?: 0f,
        animationSpec = trailSpec,
        label = "trailHigh",
    )

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
            // Засечки лестницы одинаковые: ×1 отдельной палкой не выделяется —
            // медиана места уже названа подписью, а вертикаль на ней спорила с
            // риской порога и маркером за внимание.
            for (ratio in PlaceScale.ticks()) {
                val at = x(PlaceScale.position(ratio))
                drawLine(
                    color = colors.line,
                    start = Offset(at, axisY - 4.dp.toPx()),
                    end = Offset(at, axisY + 4.dp.toPx()),
                    strokeWidth = border,
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
            if (trailLowTarget != null && trailHighTarget != null) {
                val trailLow = trailLowAnimated
                val trailHigh = trailHighAnimated
                drawLine(
                    color = tint.copy(alpha = 0.3f),
                    start = Offset(x(trailLow), axisY),
                    end = Offset(x(maxOf(trailHigh, trailLow + 0.004f)), axisY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Оба порога: первый янтарный, второй — цветом тревоги. Порог за
            // правым концом шкалы не рисуется вовсе, иначе риска прилипла бы к
            // краю и врала бы о расстоянии до него.
            for ((threshold, color) in listOf(
                thresholdMicroSvH to colors.warn,
                threshold2MicroSvH to colors.crit,
            )) {
                if (PlaceScale.offScale(threshold, median)) continue
                PlaceScale.positionOf(threshold, median)?.let { at ->
                    drawLine(
                        color = color,
                        start = Offset(x(at), axisY - 9.dp.toPx()),
                        end = Offset(x(at), axisY + 9.dp.toPx()),
                        strokeWidth = border * 2f,
                    )
                }
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
            // Порог подписан СЛОВОМ на своей риске (эталон: ×0,5 · ×1 · порог
            // · ×8); ×4 в этом случае уступает место — две подписи рядом
            // слились бы.
            val thresholdAt = PlaceScale.positionOf(thresholdMicroSvH, median)
                ?.takeIf { !PlaceScale.offScale(thresholdMicroSvH, median) }
            val labels = buildList {
                add(0f to "×0,5")
                add(PlaceScale.position(1.0) to "×1")
                if (thresholdAt != null && thresholdLabel != null) {
                    add(thresholdAt to thresholdLabel)
                } else {
                    add(PlaceScale.position(4.0) to "×4")
                }
                add(1f to "×8")
            }
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

/**
 * Время подхода следа к новому краю, мс.
 *
 * **Инженерный параметр**: 600 мс — след успевает доехать до следующего
 * секундного отсчёта и читается как движение, а не как скачок.
 */
private const val TRAIL_SETTLE_MILLIS = 600
