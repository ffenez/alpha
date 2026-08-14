package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.radiacode.ui.logic.RouteShape
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppMetrics

/**
 * Форма маршрута размером в ноготь: по ней узнают свою прогулку в списке, не
 * открывая её.
 *
 * Одним цветом, а не шкалой: на такой площади семь ступеней превратились бы в
 * шум, а цвет, который ничего не различает, читался бы как утверждение. Что
 * где было — видно на карте маршрута, а здесь только «какой это из них».
 */
@Composable
fun RouteThumbnail(
    /** Уже нормализованные точки (см. [RouteShape.normalize]). */
    shape: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = LocalAppColors.current
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
            .background(colors.chartField),
    ) {
        if (shape.isEmpty()) return@Canvas
        val inset = this.size.minDimension * 0.14f
        val span = this.size.minDimension - inset * 2
        fun offset(point: Pair<Float, Float>) =
            Offset(inset + point.first * span, inset + point.second * span)

        val stroke = 1.6.dp.toPx()
        if (shape.size == 1) {
            drawCircle(color = colors.dataText, radius = stroke, center = offset(shape.first()))
            return@Canvas
        }
        for (index in 1 until shape.size) {
            drawLine(
                color = colors.dataText,
                start = offset(shape[index - 1]),
                end = offset(shape[index]),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
