package app.radiacode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * App theme: follows the system dark/light setting (CRT dark is primary,
 * DMG light). A settings override can later feed [dark] explicitly.
 *
 * Material3 is used only as plumbing (default content colors for [Text]);
 * all real styling comes from [LocalPixelColors]/[LocalPixelTypography].
 */
@Composable
fun PixelTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) CrtColors else DmgColors
    val materialScheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.ground,
            surface = colors.surface,
            onPrimary = colors.ground,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.ground,
            surface = colors.surface,
            onPrimary = colors.ground,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    }
    CompositionLocalProvider(
        LocalPixelColors provides colors,
        LocalPixelTypography provides PixelTypography(),
    ) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
