package app.radiacode.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalPixelColors
import kotlinx.coroutines.delay

/**
 * Terminal cursor: a solid block that toggles on/off as a step function
 * (530 ms, no fades — design-language.md: animations are step functions).
 * When system animations are disabled (animator scale 0), stays solid on.
 */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(10.dp, 18.dp),
    color: Color = LocalPixelColors.current.accent,
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
    val visible by if (animationsEnabled) {
        produceState(initialValue = true) {
            while (true) {
                delay(530)
                value = !value
            }
        }
    } else {
        remember { mutableStateOf(true) }
    }
    Box(
        modifier = modifier
            .size(size)
            .background(if (visible) color else Color.Transparent),
    )
}
