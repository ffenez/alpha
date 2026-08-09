package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalPixelColors

/**
 * Segmented LED intensity meter. Lights `level * segments` hard segments;
 * segments above [alarmFraction] light in the alarm mark color. Level changes
 * snap per segment — no tweening (step animation rule).
 */
@Composable
fun LedMeter(
    level: Float,
    modifier: Modifier = Modifier,
    segments: Int = 24,
    alarmFraction: Float = 0.75f,
) {
    val colors = LocalPixelColors.current
    val lit = (level.coerceIn(0f, 1f) * segments).toInt()
    Canvas(modifier = modifier.fillMaxWidth().height(20.dp)) {
        val gap = 3.dp.toPx()
        val segWidth = (size.width - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            val x = i * (segWidth + gap)
            val isLit = i < lit
            val isAlarmZone = i >= (alarmFraction * segments).toInt()
            val color = when {
                isLit && isAlarmZone -> colors.chartAlarm
                isLit -> colors.chartData
                else -> colors.surface2
            }
            drawRect(color = color, topLeft = Offset(x, 0f), size = Size(segWidth, size.height))
        }
    }
}
