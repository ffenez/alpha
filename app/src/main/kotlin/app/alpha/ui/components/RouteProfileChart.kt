package app.alpha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.ui.logic.MapTrackPoint
import app.alpha.ui.logic.RouteProfile
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.logic.TrackMetric
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.chartField

/**
 * Профиль маршрута во времени с общим с картой курсором.
 *
 * Ведёшь пальцем по графику — кольцо едет по следу на карте; трогаешь след —
 * курсор встаёт на графике. Это одна и та же прогулка, показанная двумя
 * способами, и разъехаться они не имеют права.
 *
 * Выбранная точка называет себя ВНУТРИ поля: значение — плашкой у правого
 * края на своей высоте, время — под курсором на оси. Отдельная карточка под
 * графиком отнимала треть экрана и разрывала связь между курсором и точкой:
 * глаз читал число далеко от того места, к которому оно относится.
 *
 * Пропуски координат не заштриховываются линией: там, где следу нельзя было
 * рисовать прямую, графику нельзя тем более — он выглядел бы непрерывным
 * измерением, которого не было.
 */
@Composable
fun RouteProfileChart(
    points: List<MapTrackPoint>,
    metric: TrackMetric,
    cursorIndex: Int?,
    onCursor: (Int?) -> Unit,
    /** Значение точки словами — с единицей: «0,10 мкЗв/ч». */
    valueLabel: (MapTrackPoint) -> String?,
    /** Время точки: «14:12». */
    timeLabel: (MapTrackPoint) -> String,
    /** Вторая строка курсора: скорость счёта и точность фикса. */
    detailLabel: (MapTrackPoint) -> String?,
    modifier: Modifier = Modifier,
    height: Dp = 108.dp,
    /** Нажатие без курсора — открыть график во весь экран. */
    onOpen: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()
    if (points.isEmpty()) return
    val times = points.map { it.timestamp }
    val values = points.map { TrackMap.metricValue(it, metric) }
    val bounds = RouteProfile.bounds(values.filterNotNull()) ?: return
    val breaks = TrackMap.lineBreaks(points)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .chartField()
            .pointerInput(points, metric, onOpen) {
                detectTapGestures(
                    onTap = { offset ->
                        val open = onOpen
                        // Первое касание ставит курсор, второе — открывает
                        // график целиком: у одного жеста одно значение за раз,
                        // и «открыть» не отбирает у графика его собственное.
                        if (cursorIndex == null || open == null) {
                            onCursor(RouteProfile.indexAt(times, offset.x / size.width.toFloat()))
                        } else {
                            open()
                        }
                    },
                )
            }
            .pointerInput(points, metric) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onCursor(RouteProfile.indexAt(times, change.position.x / size.width.toFloat()))
                }
            },
    ) {
        val padY = 10.dp.toPx()
        val plotHeight = size.height - padY * 2
        if (plotHeight <= 0f) return@Canvas
        val span = (bounds.endInclusive - bounds.start).coerceAtLeast(1e-6f)
        val from = times.first()
        val to = times.last()

        fun x(index: Int) = RouteProfile.fractionOf(times[index], from, to) * size.width
        fun y(value: Float) =
            padY + (1f - ((value - bounds.start) / span).coerceIn(0f, 1f)) * plotHeight

        val stroke = 1.6.dp.toPx()
        for (index in 1 until points.size) {
            val previous = values[index - 1] ?: continue
            val current = values[index] ?: continue
            if (breaks.getOrElse(index) { true }) continue
            drawLine(
                color = colors.dataText,
                start = Offset(x(index - 1), y(previous)),
                end = Offset(x(index), y(current)),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        val index = cursorIndex?.takeIf { it in points.indices } ?: return@Canvas
        val point = points[index]
        val cursorX = x(index)
        drawLine(
            color = colors.chartGrid,
            start = Offset(cursorX, 0f),
            end = Offset(cursorX, size.height),
            strokeWidth = stroke,
        )
        values[index]?.let { value ->
            drawCircle(colors.data, 3.5.dp.toPx(), Offset(cursorX, y(value)))
            // Значение — у правого края на СВОЕЙ высоте: так число стоит там,
            // где его читает глаз, ведя взгляд от точки вправо.
            valueLabel(point)?.let { label ->
                val layout = textMeasurer.measure(label, axisStyle)
                drawLabel(
                    layout = layout,
                    x = size.width - layout.size.width - 6.dp.toPx(),
                    y = (y(value) - layout.size.height / 2f)
                        .coerceIn(0f, size.height - layout.size.height),
                    background = colors.surface,
                    color = colors.ink,
                    padding = 3.dp.toPx(),
                )
            }
        }
        // Время — под курсором на оси.
        val timeLayout = textMeasurer.measure(timeLabel(point), axisStyle)
        drawLabel(
            layout = timeLayout,
            x = (cursorX - timeLayout.size.width / 2f)
                .coerceIn(0f, size.width - timeLayout.size.width),
            y = size.height - timeLayout.size.height - 2.dp.toPx(),
            background = colors.surface,
            color = colors.ink2,
            padding = 3.dp.toPx(),
        )
        // Остальное о точке — тонкой строкой у верхней кромки поля.
        detailLabel(point)?.let { detail ->
            val layout = textMeasurer.measure(detail, axisStyle)
            drawText(
                textLayoutResult = layout,
                color = colors.muted,
                topLeft = Offset(4.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}

/** Подпись на подложке: над линией графика голый текст читается через раз. */
private fun DrawScope.drawLabel(
    layout: TextLayoutResult,
    x: Float,
    y: Float,
    background: androidx.compose.ui.graphics.Color,
    color: androidx.compose.ui.graphics.Color,
    padding: Float,
) {
    drawRoundRect(
        color = background,
        topLeft = Offset(x - padding, y - padding / 2f),
        size = Size(
            layout.size.width + padding * 2,
            layout.size.height + padding,
        ),
        cornerRadius = CornerRadius(padding),
    )
    drawText(textLayoutResult = layout, color = color, topLeft = Offset(x, y))
}
