package app.alpha.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.ui.logic.ArcScale
import app.alpha.ui.logic.NavigateArc
import app.alpha.ui.logic.NavigateTrend
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppTypography
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything the dial draws. All positions come from [NavigateArc]; this
 * component contains no mathematics of its own, so what the needle says and
 * what the tests check are the same statement.
 *
 * Интервал и удержанный максимум на самой дуге НЕ рисуются (эталон
 * `docs/design/main-and-search.html`): интервал стоит числами в подписи под
 * главным числом и в «Почему?», максимум — строкой под лентой. Вторая дуга и
 * второй маркер читались как ещё одна шкала.
 */
@Immutable
data class NavigateGaugeSpec(
    /** Current rate as a factor of the знаменателя шкалы; null = нечего показывать. */
    val ratio: Double?,
    /**
     * Шкала прибора: её концы и есть режим. ×1 — либо «как обычно здесь»
     * ([ArcScale.PLACE]), либо «как в точке отсчёта» ([ArcScale.MARK]).
     */
    val scale: ArcScale,
    val trend: NavigateTrend,
    /** «1×» — a mark without a name is a scratch on the glass. */
    val referenceLabel: String,
    /**
     * Обычный разброс места (P10–P90) в тех же отношениях — сектор дуги.
     *
     * Отвечает на вопрос «бывает ли здесь так»: без него отклонение от медианы
     * читается как событие даже там, где место само по себе разбросано. Null —
     * разброс неизвестен либо шкала не про место.
     */
    val bandLow: Double? = null,
    val bandHigh: Double? = null,
    /**
     * Пороги тревоги на той же шкале и подпись первого из них.
     *
     * Уровней два, и на шкале их тоже два: настроив второй, человек ищет его
     * глазами, а одна риска отвечала бы «сколько осталось» только про первый.
     *
     * Подписи у рисок нет: собственный уровень — величина из настроек, и её
     * имя на рабочем экране называло настройку, а не то, что видно. Риска
     * говорит «вот он относительно обычного», а чем она задана — в справке по
     * нажатию на числа.
     */
    val threshold: Double? = null,
    val threshold2: Double? = null,
)

/**
 * The dial of «Наведение»: a 220° arc with a needle at the current value.
 *
 * It is an **instrument dial, not a radar sweep**. Nothing rotates on its own,
 * because a rotating beam would promise a direction in space that a dosimeter
 * does not measure. The scale is logarithmic in the ratio to the точка
 * отсчёта, so equal factors are equal distances — the same mapping the search
 * tone uses, which is what lets the eye and the ear say the same thing.
 *
 * ## Стрелка доезжает — намеренно
 *
 * У физического стрелочного прибора стрелка имеет инерцию и демпфер: она
 * ПОДХОДИТ к значению, а не телепортируется. Здесь то же самое —
 * [NEEDLE_SETTLE_MILLIS] с крутым выходом и мягким подходом. Измеренное число
 * при этом меняется шагом: цифры — измерение, угол стрелки — его подача, и
 * промежуточных значений стрелка не называет, потому что чисел на ней нет.
 * При выключенной системной анимации стрелка переставляется мгновенно.
 *
 * There are no coloured zones: that would be a danger scale, and this is a
 * count rate. Цвет несёт ЗАЛИВКА от ×1 до показания — направление изменения;
 * сама стрелка чернильная, чтобы читаться на любой заливке.
 */
@Composable
fun NavigateGauge(
    spec: NavigateGaugeSpec,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    val colors = LocalAppColors.current
    val metrics = LocalAppMetrics.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
    val motionAllowed = rememberMotionAllowed()

    // Цвет заливки — направление изменения; «падение» остаётся спокойным.
    val fillColor = when (spec.trend) {
        NavigateTrend.RISING -> colors.warn
        else -> colors.data
    }

    // Демпфер стрелки: цель — ДОЛЯ шкалы, не угол, поэтому смена кадра
    // (factor) не крутит стрелку через полкруга. Первая стрелка выходит из ×1
    // — из места, откуда пошли.
    val targetPosition = spec.ratio?.let { NavigateArc.position(it, spec.scale) }
    val animatedPosition by animateFloatAsState(
        targetValue = targetPosition ?: NavigateArc.position(1.0, spec.scale),
        animationSpec = if (motionAllowed) {
            tween(NEEDLE_SETTLE_MILLIS, easing = NEEDLE_EASING)
        } else {
            snap()
        },
        label = "needle",
    )

    // Без подложки: прибор — это шкала на самой карточке, а не картинка в
    // рамке. Поле [chartField] принадлежит графикам, у которых есть площадь
    // данных; у циферблата её нет, и квадрат вокруг круга читался как рамка,
    // которую забыли убрать.
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val border = metrics.border.toPx()
        val padding = 10.dp.toPx()
        // Циферблат в 220° занимает 2R по ширине и R·(1 + sin 20°) по высоте:
        // вершина дуги над центром, оба конца — ниже него.
        val vertical = size.height - 2f * padding
        val radius = minOf((size.width - 2f * padding) / 2f, vertical / DIAL_HEIGHT_FACTOR)
        if (radius <= 0f) return@Canvas
        val centerX = size.width / 2f
        val centerY = padding + radius

        fun point(angleDegrees: Float, atRadius: Float): Offset {
            val radians = angleDegrees * PI.toFloat() / 180f
            return Offset(centerX + atRadius * cos(radians), centerY + atRadius * sin(radians))
        }

        fun arc(color: Color, fromDegrees: Float, sweep: Float, width: Float) {
            drawArc(
                color = color,
                startAngle = fromDegrees,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = width, cap = StrokeCap.Butt),
            )
        }

        // Дорожка и заливка — ОДНОЙ толщины: это одна шкала, у которой
        // закрашена пройденная часть, а не две разные дуги.
        val arcWidth = 6.dp.toPx()
        arc(colors.chartGrid, NavigateArc.START_DEGREES, NavigateArc.SWEEP_DEGREES, arcWidth)

        // Обычный разброс места — широкий тусклый сектор ТОЙ ЖЕ дуги: он
        // отвечает «бывает ли здесь так», и потому лежит под показанием, а не
        // рядом с ним.
        val bandLow = spec.bandLow
        val bandHigh = spec.bandHigh
        if (bandLow != null && bandHigh != null && bandHigh > bandLow) {
            val from = NavigateArc.angleDegrees(bandLow, spec.scale)
            val to = NavigateArc.angleDegrees(bandHigh, spec.scale)
            arc(
                color = colors.ink2.copy(alpha = 0.26f),
                fromDegrees = from,
                sweep = (to - from).coerceAtLeast(MIN_BAND_DEGREES),
                width = arcWidth * 2.4f,
            )
        }

        // Заливка от ×1 до стрелки: «на столько отсюда ушло». Она едет вместе
        // со стрелкой — это одно движение, показанное дважды.
        if (targetPosition != null) {
            val fromDegrees = NavigateArc.angleDegrees(1.0, spec.scale)
            val toDegrees = NavigateArc.START_DEGREES +
                NavigateArc.SWEEP_DEGREES * animatedPosition
            arc(
                color = fillColor,
                fromDegrees = minOf(fromDegrees, toDegrees),
                sweep = abs(toDegrees - fromDegrees),
                width = arcWidth,
            )
        }

        // Засечки и их множители — ВНУТРИ дуги, на одном радиусе (эталон):
        // шкала без чисел не шкала, а дуга. На широких кадрах подписаны только
        // концы и ×1 — иначе подписи наезжают друг на друга.
        val tickOuter = radius - arcWidth / 2f - 2.dp.toPx()
        val tickInner = tickOuter - 9.dp.toPx()
        val labelRadius = tickInner - 11.dp.toPx()
        val ticks = NavigateArc.ticks(spec.scale)
        val steps = (ticks.size - 1) / 2
        val labelStep = if (steps <= 3) 1 else steps
        ticks.forEachIndexed { index, value ->
            val reference = value == 1.0
            val angle = NavigateArc.angleDegrees(value, spec.scale)
            drawLine(
                color = if (reference) colors.ink2 else colors.muted,
                start = point(angle, tickOuter),
                end = point(angle, tickInner),
                strokeWidth = if (reference) border * 2f else border,
                cap = StrokeCap.Butt,
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

        // Порог — своя риска ПОВЕРХ шкалы, с подписью снаружи: он не деление
        // и не показание, а граница, о которой договорились. За концом шкалы
        // не рисуется вовсе — прижатая к краю риска врала бы о расстоянии.
        for ((threshold, color) in listOf(
            spec.threshold to colors.warn,
            spec.threshold2 to colors.crit,
        )) {
            if (threshold == null || NavigateArc.offScale(threshold, spec.scale)) continue
            val at = NavigateArc.angleDegrees(threshold, spec.scale)
            drawLine(
                color = color,
                start = point(at, radius + arcWidth / 2f + 2.dp.toPx()),
                end = point(at, tickInner),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Butt,
            )
        }

        // Стрелка — чернильная линия от оси, без наконечника (эталон): на
        // цветной заливке её читают по контрасту, а не по форме. Длина
        // постоянная, поэтому при ×1 она стоит строго вверх и отчётливо видна
        // рядом с короткой засечкой ×1.
        if (targetPosition != null) {
            val angle = NavigateArc.START_DEGREES +
                NavigateArc.SWEEP_DEGREES * animatedPosition
            drawLine(
                color = colors.ink,
                start = Offset(centerX, centerY),
                end = point(angle, tickInner - 2.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // Ось нарисована в обоих состояниях: геометрия прибора не меняется от
        // того, есть ли у него показание.
        drawCircle(
            color = if (targetPosition == null) colors.muted else colors.ink,
            radius = 4.dp.toPx(),
            center = Offset(centerX, centerY),
        )

    }
}

/** Самый узкий сектор разброса, который ещё виден как сектор. */
private const val MIN_BAND_DEGREES = 2f

/** 1 + sin 20°: высота циферблата в 220° по отношению к его радиусу. */
private const val DIAL_HEIGHT_FACTOR = 1.342f

/**
 * Время подхода стрелки к новому значению, мс.
 *
 * **Инженерный параметр**: 700 мс — меньше секундного темпа прибора, поэтому
 * стрелка успевает встать до следующего отсчёта; быстрее движение читается
 * как скачок, а не как ход стрелки.
 */
private const val NEEDLE_SETTLE_MILLIS = 700

/** Крутой выход, мягкий подход — баллистика демпфированной стрелки. */
private val NEEDLE_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)
