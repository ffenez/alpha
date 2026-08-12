package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.Motion

/**
 * Thin segmented intensity bar. Lights `level * segments` segments in data
 * teal; segments above [alarmFraction] light in the alarm color. Level
 * changes snap per segment — measurements are not tweened.
 */
@Composable
fun LedMeter(
    level: Float,
    modifier: Modifier = Modifier,
    segments: Int = 24,
    alarmFraction: Float = 0.75f,
) {
    // Индикатор — состояние интерфейса, а не измерение: его заполнение может
    // ехать плавно. Само число (CPS) при этом не анимируется нигде.
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = Motion.normal(),
        label = "ledLevel",
    )
    val colors = LocalAppColors.current
    val lit = (animated * segments).toInt()
    Canvas(modifier = modifier.fillMaxWidth().height(6.dp)) {
        val gap = 3.dp.toPx()
        val radius = CornerRadius(2.dp.toPx())
        val segWidth = (size.width - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            val x = i * (segWidth + gap)
            val isLit = i < lit
            val isAlarmZone = i >= (alarmFraction * segments).toInt()
            val color = when {
                isLit && isAlarmZone -> colors.crit
                isLit -> colors.data
                else -> colors.surface2
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(segWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}
