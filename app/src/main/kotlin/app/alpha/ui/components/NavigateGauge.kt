package app.alpha.ui.components

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything the 60° dial draws. All positions come from [NavigateArc]; this
 * component contains no mathematics of its own, so what the needle says and
 * what the tests check are the same statement.
 */
@Immutable
data class NavigateGaugeSpec(
    /** Current rate as a factor of the точка отсчёта; null = nothing to point at. */
    val ratio: Double?,
    /** Held maximum as the same factor; null = none. */
    val peakRatio: Double?,
    /**
     * Ends of the confidence interval of [ratio], on the same scale.
     *
     * This is what the dial was missing: a needle alone says «×1,18» as if it
     * were exact, and at these exposures it is not. The band is the interval
     * the exact test actually produced, so a wide one and a tight one cannot
     * look the same. Null while no test has run — an absent band never means a
     * certain value, which is why the caption names the state in words too.
     */
    val intervalLow: Double? = null,
    val intervalHigh: Double? = null,
    /** Half-span of the scale: the ends are ×factor and ×1/factor. */
    val factor: Double,
    val trend: NavigateTrend,
    /** «1×» — a mark without a name is a scratch on the glass. */
    val referenceLabel: String,
    /** End labels, e.g. «0,25×» and «4×». */
    val lowLabel: String,
    val highLabel: String,
    /**
     * Смысловая строка под числами: «слабее — отсчёт — сильнее».
     *
     * Числа говорят, во сколько раз, и молчат о том, что это значит для
     * человека, который сейчас переставляет прибор. Слово под числом и есть
     * ответ, и оно же делает середину шкалы главной отметкой: ×1 — не «одна из
     * засечек», а то место, откуда пошли. Null — строки нет.
     */
    val lowCaption: String? = null,
    val referenceCaption: String? = null,
    val highCaption: String? = null,
)

/**
 * The dial of «Наведение»: a 60° arc with a needle at the current value.
 *
 * It is an **instrument scale, not a radar sweep**. Nothing rotates on its own,
 * because a rotating beam would promise a direction in space that a dosimeter
 * does not measure. The scale is logarithmic in the ratio to the точка отсчёта,
 * so equal factors are equal distances — the same mapping the search tone uses,
 * which is what lets the eye and the ear say the same thing.
 *
 * The needle **never animates between measurements**: interface state may move
 * smoothly, measured values may not, and the intermediate frames of a sliding
 * needle would show ratios the instrument never measured. The frame — the ends
 * of the scale — is the part allowed to change, and it moves in discrete steps
 * with hysteresis.
 *
 * There are no coloured zones: that would be a danger scale, and this is a
 * count rate. The only accented thing is the direction of change, and «падение»
 * is neutral rather than a fourth shade — the 8-bit palette has no fourth
 * shade, and a falling count is not a state that needs its own colour.
 *
 * Stroke widths come from [LocalAppMetrics], so 8-bit gets a blockier dial from
 * the same code, and every end is butt-cut so the arc reads as one solid piece.
 */
@Composable
fun NavigateGauge(
    spec: NavigateGaugeSpec,
    modifier: Modifier = Modifier,
    height: Dp = 128.dp,
) {
    val colors = LocalAppColors.current
    val metrics = LocalAppMetrics.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    val needleColor = when (spec.trend) {
        NavigateTrend.RISING -> colors.warn
        NavigateTrend.FALLING -> colors.ink2
        NavigateTrend.NO_CHANGE -> colors.ink
        NavigateTrend.COLLECTING -> colors.muted
    }

    Canvas(modifier = modifier.fillMaxWidth().height(height).chartField()) {
        val border = metrics.border.toPx()
        val padding = 10.dp.toPx()
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height.toFloat()
        val captions = spec.lowCaption != null ||
            spec.referenceCaption != null ||
            spec.highCaption != null
        val labelRows = if (captions) 2f else 1f
        // Подписи делений стоят СНАРУЖИ дуги: внутри их пересекала бы стрелка,
        // а стрелка обязана идти от оси до самого края шкалы. Кольцо под них
        // резервируется до расчёта радиуса, иначе крайние подписи вылезали бы
        // за края поля.
        val tickLabelRing = labelHeight + 5.dp.toPx()
        // Циферблат в 220° занимает 2R по ширине и R·(1 + sin 20°) по высоте:
        // вершина дуги над центром, оба конца — ниже него.
        val vertical = size.height - labelRows * labelHeight - 3f * padding - tickLabelRing
        val radius = minOf(
            (size.width - 2f * padding) / 2f - tickLabelRing,
            vertical / DIAL_HEIGHT_FACTOR,
        )
        if (radius <= 0f) return@Canvas
        val centerX = size.width / 2f
        val centerY = padding + tickLabelRing + radius

        fun point(angleDegrees: Float, atRadius: Float): Offset {
            val radians = angleDegrees * PI.toFloat() / 180f
            return Offset(centerX + atRadius * cos(radians), centerY + atRadius * sin(radians))
        }

        fun radial(angleDegrees: Float, from: Float, to: Float, color: Color, width: Float) {
            drawLine(
                color = color,
                start = point(angleDegrees, from),
                end = point(angleDegrees, to),
                strokeWidth = width,
                cap = StrokeCap.Butt,
            )
        }

        // Интервал — участок ТОЙ ЖЕ дуги, подсвеченный под ней: отдельная
        // полоса рядом читалась как вторая шкала, хотя говорит о той же
        // величине. Уверенность показана шириной участка, а не цветом.
        val arcWidth = border * 2.5f
        val bandLow = spec.intervalLow
        val bandHigh = spec.intervalHigh
        if (bandLow != null && bandHigh != null && bandLow > 0.0 && bandHigh >= bandLow) {
            val from = NavigateArc.angleDegrees(bandLow, spec.factor)
            val to = NavigateArc.angleDegrees(bandHigh, spec.factor)
            drawArc(
                color = colors.ink2.copy(alpha = 0.22f),
                startAngle = from,
                sweepAngle = (to - from).coerceAtLeast(MIN_BAND_DEGREES),
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = arcWidth * 2.4f, cap = StrokeCap.Butt),
            )
        }

        drawArc(
            color = colors.chartGrid,
            startAngle = NavigateArc.START_DEGREES,
            sweepAngle = NavigateArc.SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = arcWidth, cap = StrokeCap.Butt),
        )

        // Сектор от ×1 до стрелки: заполненная часть шкалы говорит «на столько
        // отсюда ушло», и её длина читается раньше, чем положение стрелки.
        spec.ratio?.let { ratio ->
            val from = NavigateArc.angleDegrees(1.0, spec.factor)
            val to = NavigateArc.angleDegrees(ratio, spec.factor)
            drawArc(
                color = needleColor.copy(alpha = 0.7f),
                startAngle = minOf(from, to),
                sweepAngle = kotlin.math.abs(to - from),
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = arcWidth, cap = StrokeCap.Butt),
            )
        }

        // Засечки — удвоения кадра, и каждая названа множителем: шкала без
        // чисел не шкала, а дуга. Все подписи лежат на ОДНОМ радиусе снаружи
        // дуги, поэтому ни одна не встречается со стрелкой. На широких кадрах
        // подписаны только концы и ×1 — иначе подписи наезжают друг на друга.
        val tick = 9.dp.toPx()
        val labelRadius = radius + tickLabelRing / 2f
        val ticks = NavigateArc.ticks(spec.factor)
        val steps = (ticks.size - 1) / 2
        val labelStep = if (steps <= 3) 1 else steps
        ticks.forEachIndexed { index, value ->
            val reference = value == 1.0
            val angle = NavigateArc.angleDegrees(value, spec.factor)
            radial(
                angleDegrees = angle,
                from = radius - if (reference) tick * 1.5f else tick,
                to = radius + arcWidth / 2f,
                color = if (reference) colors.ink2 else colors.chartGrid,
                width = if (reference) border * 2f else border,
            )
            val power = index - steps
            if (power % labelStep != 0) return@forEachIndexed
            val text = if (reference) {
                spec.referenceLabel
            } else {
                "${NavigateArc.factorLabel(value)}×"
            }
            val measured = textMeasurer.measure(text, axisStyle)
            val at = point(angle, labelRadius)
            drawText(
                textLayoutResult = measured,
                color = if (reference) colors.ink2 else colors.muted,
                topLeft = Offset(
                    (at.x - measured.size.width / 2f)
                        .coerceIn(0f, size.width - measured.size.width),
                    at.y - measured.size.height / 2f,
                ),
            )
        }

        // Held maximum: a mark on the same scale, never a second needle.
        spec.peakRatio?.let { peak ->
            val angle = NavigateArc.angleDegrees(peak, spec.factor)
            val apex = point(angle, radius - tick * 1.2f)
            val left = point(angle - 2.4f, radius - tick * 2.4f)
            val right = point(angle + 2.4f, radius - tick * 2.4f)
            drawPath(
                path = Path().apply {
                    moveTo(apex.x, apex.y)
                    lineTo(left.x, left.y)
                    lineTo(right.x, right.y)
                    close()
                },
                color = colors.ink2,
            )
        }

        // Стрелка идёт ОТ ОСИ, как у стрелочного прибора, и всегда одной
        // длины: короткий штрих у края читался как ещё одна засечка, а
        // «подрастающая» стрелка означала бы величину дважды. При ×1 она
        // стоит строго вверх и всё равно видна — её ни с чем не спутать,
        // потому что засечка ×1 короче и другого цвета.
        spec.ratio?.let { ratio ->
            val angle = NavigateArc.angleDegrees(ratio, spec.factor)
            val tipRadius = radius - tick * 1.7f
            radial(
                angleDegrees = angle,
                from = 0f,
                to = tipRadius,
                color = needleColor,
                width = 2.5.dp.toPx(),
            )
            // Наконечник: у стрелки есть остриё, иначе это просто отрезок.
            val head = 7.dp.toPx()
            val apex = point(angle, tipRadius + head * 0.9f)
            val left = point(angle - 3.4f, tipRadius - head * 0.2f)
            val right = point(angle + 3.4f, tipRadius - head * 0.2f)
            drawPath(
                path = Path().apply {
                    moveTo(apex.x, apex.y)
                    lineTo(left.x, left.y)
                    lineTo(right.x, right.y)
                    close()
                },
                color = needleColor,
            )
        }
        // Ось прибора нарисована всегда, со стрелкой и без неё: геометрия
        // состояний обязана совпадать, иначе экран прыгает при появлении
        // точки отсчёта.
        drawCircle(
            color = if (spec.ratio == null) colors.muted else needleColor,
            radius = 4.dp.toPx(),
            center = Offset(centerX, centerY),
        )

        // Bottom rows: both ends of the scale, the name of its centre, and —
        // under the numbers — what each end means for the person walking.
        val captionBaseline = size.height - labelHeight - padding / 2f
        val baseline = captionBaseline - (labelRows - 1f) * labelHeight

        fun row(low: String?, middle: String?, high: String?, y: Float, color: Color) {
            low?.let {
                drawText(textMeasurer.measure(it, axisStyle), color = color, topLeft = Offset(padding, y))
            }
            high?.let {
                val measured = textMeasurer.measure(it, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = color,
                    topLeft = Offset(size.width - measured.size.width - padding, y),
                )
            }
            middle?.let {
                val measured = textMeasurer.measure(it, axisStyle)
                drawText(
                    textLayoutResult = measured,
                    color = if (color == colors.muted) colors.ink2 else color,
                    topLeft = Offset(centerX - measured.size.width / 2f, y),
                )
            }
        }

        // Множители подписаны у самих засечек, поэтому внизу остаётся только
        // смысл концов и знаменатель шкалы — то, что числами не сказать.
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

/** 1 + sin 20°: высота циферблата в 220° по отношению к его радиусу. */
private const val DIAL_HEIGHT_FACTOR = 1.342f

/** A very tight interval still has to be visible as a band, not vanish. */
private const val MIN_BAND_DEGREES = 1.2f
