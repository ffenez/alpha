package app.alpha.ui.components

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
import app.alpha.ui.logic.ThumbnailPoint
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.TrackRampColors

/**
 * Форма маршрута размером с ноготь — по ней узнают свою прогулку в списке.
 *
 * Окрашена той же шкалой, что и след на карте: одна картинка отвечает сразу
 * на два вопроса — «какой это из маршрутов» и «где по дороге было выше». Своей
 * шкалы у миниатюры нет и быть не может: цвет, означающий здесь одно, а на
 * карте другое, — это два разных языка под одними красками.
 *
 * Без шкалы (для места ещё нет обычного фона) миниатюра рисуется одним цветом
 * данных: контур без основания сравнения — это форма, и только форма.
 */
@Composable
fun RouteThumbnail(
    shape: List<ThumbnailPoint>,
    scale: TrackMap.RampScale?,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
) {
    val colors = LocalAppColors.current
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
            .background(colors.chartField),
    ) {
        if (shape.isEmpty()) return@Canvas
        val inset = this.size.minDimension * 0.12f
        val span = this.size.minDimension - inset * 2
        fun offset(point: ThumbnailPoint) =
            Offset(inset + point.x * span, inset + point.y * span)

        fun colorOf(point: ThumbnailPoint): androidx.compose.ui.graphics.Color {
            val value = point.value ?: return colors.muted
            val ramp = scale ?: return colors.dataText
            return TrackRampColors.getOrElse(TrackMap.bucket(value, ramp)) { colors.dataText }
        }

        val stroke = 2.2.dp.toPx()
        if (shape.size == 1) {
            drawCircle(color = colorOf(shape.first()), radius = stroke, center = offset(shape.first()))
            return@Canvas
        }
        for (index in 1 until shape.size) {
            drawLine(
                color = colorOf(shape[index]),
                start = offset(shape[index - 1]),
                end = offset(shape[index]),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
