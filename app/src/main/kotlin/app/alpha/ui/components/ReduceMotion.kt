package app.alpha.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import app.alpha.ui.theme.Motion

/**
 * Разрешено ли декоративное движение — по системной настройке устройства.
 *
 * «Отключение анимации» в спец-возможностях и режим экономии ставят
 * `ANIMATOR_DURATION_SCALE` в ноль, и это ответ не про скорость, а про
 * согласие человека на движение вообще. Дыхание подсветки — декорация со
 * смыслом «поток жив», и когда движение выключено, смысл обязан остаться
 * (свечение видно), а движение — исчезнуть.
 *
 * Читается один раз на композицию: настройка меняется в системных настройках,
 * то есть с уходом приложения на задний план, и опрашивать её каждый кадр
 * незачем. В превью движение считается разрешённым.
 */
@Composable
fun rememberMotionAllowed(): Boolean {
    if (LocalInspectionMode.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val scale = runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        Motion.animationsEnabled(scale)
    }
}
