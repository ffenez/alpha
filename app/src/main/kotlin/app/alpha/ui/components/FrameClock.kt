package app.alpha.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis

/**
 * Часы кадра: время, обновляемое на каждом кадре, пока [enabled].
 *
 * Нужны там, где между секундными измерениями двигается ОКНО, а не данные —
 * живой край графика ([app.alpha.ui.logic.LiveEdge]). Выключенные часы стоят
 * на [idleMillis] и композицию не будят: покадровая перерисовка включается
 * только там, где движение видно.
 */
@Composable
fun rememberFrameMillis(enabled: Boolean, idleMillis: Long): State<Long> {
    val frame = remember { mutableLongStateOf(idleMillis) }
    if (!enabled) SideEffect { frame.longValue = idleMillis }
    LaunchedEffect(enabled) {
        while (enabled) {
            withFrameMillis { frame.longValue = System.currentTimeMillis() }
        }
    }
    return frame
}
