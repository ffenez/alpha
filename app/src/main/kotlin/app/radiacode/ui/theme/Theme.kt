package app.radiacode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * App theme: follows the system dark/light setting (dark is primary).
 * A settings override can later feed [dark] explicitly.
 *
 * Material3 is used only as plumbing (default content colors for [Text],
 * ripples); all real styling comes from [LocalAppColors]/[LocalAppTypography].
 */
@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    /** Вариант дизайн-языка; меняет только токены, не поведение. */
    skin: AppSkin = AppSkin.TERMINAL,
    content: @Composable () -> Unit,
) {
    val colors = when {
        skin == AppSkin.EIGHT_BIT && dark -> EightBitDarkColors
        skin == AppSkin.EIGHT_BIT -> EightBitLightColors
        dark -> DarkColors
        else -> LightColors
    }
    val materialScheme = if (dark) {
        darkColorScheme(
            primary = colors.data,
            background = colors.bg,
            surface = colors.surface,
            onPrimary = colors.onData,
            onBackground = colors.ink,
            onSurface = colors.ink,
            outline = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.data,
            background = colors.bg,
            surface = colors.surface,
            onPrimary = colors.onData,
            onBackground = colors.ink,
            onSurface = colors.ink,
            outline = colors.line,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides when (skin) {
            AppSkin.TERMINAL -> AppTypography()
            AppSkin.EIGHT_BIT -> eightBitTypography()
        },
        LocalAppMetrics provides when (skin) {
            AppSkin.TERMINAL -> TerminalMetrics
            AppSkin.EIGHT_BIT -> EightBitMetrics
        },
    ) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
