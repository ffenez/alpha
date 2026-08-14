package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.radiacode.ui.logic.MapTrackPoint
import app.radiacode.ui.logic.RouteProfile
import app.radiacode.ui.logic.TrackMap
import app.radiacode.ui.logic.TrackMetric
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.chartField

/**
 * Профиль маршрута во времени с общим с картой курсором.
 *
 * Ведёшь пальцем по графику — кольцо едет по следу на карте; трогаешь след —
 * курсор встаёт на графике. Это одна и та же прогулка, показанная двумя
 * способами, и разъехаться они не имеют права.
 *
 * Пропуски координат не заштриховываются линией: там, где следу нельзя было
 * рисовать прямую, графику нельзя рисовать её тем более — он выглядел бы
 * непрерывным измерением, которого не было.
 */
@Composable
fun RouteProfileChart(
    points: List<MapTrackPoint>,
    metric: TrackMetric,
    cursorIndex: Int?,
    onCursor: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
) {
    val colors = LocalAppColors.current
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
            .pointerInput(points, metric) {
                detectTapGestures { offset ->
                    onCursor(RouteProfile.indexAt(times, offset.x / size.width.toFloat()))
                }
            }
            .pointerInput(points, metric) {
                detectDragGestures(
                    onDragEnd = { },
                    onDragCancel = { },
                ) { change, _ ->
                    change.consume()
                    onCursor(RouteProfile.indexAt(times, change.position.x / size.width.toFloat()))
                }
            },
    ) {
        val padY = 6.dp.toPx()
        val plotHeight = size.height - padY * 2
        if (plotHeight <= 0f) return@Canvas
        val span = (bounds.endInclusive - bounds.start).coerceAtLeast(1e-6f)
        val from = times.first()
        val to = times.last()

        fun x(index: Int) = RouteProfile.fractionOf(times[index], from, to) * size.width
        fun y(value: Float) = padY + (1f - ((value - bounds.start) / span).coerceIn(0f, 1f)) * plotHeight

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
            )
        }

        cursorIndex?.takeIf { it in points.indices }?.let { index ->
            val cursorX = x(index)
            drawLine(
                color = colors.chartGrid,
                start = Offset(cursorX, 0f),
                end = Offset(cursorX, size.height),
                strokeWidth = stroke,
            )
            values[index]?.let { value ->
                drawCircle(
                    color = colors.data,
                    radius = 3.dp.toPx(),
                    center = Offset(cursorX, y(value)),
                )
            }
        }
    }
}
