package app.radiacode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens of the "8-bit hybrid" design language
 * (docs/design/design-language.md). Two fixed palettes: CRT (dark, primary)
 * and DMG (light). Semantic rules:
 *  - [accent] glow is reserved for the main reading and its status only;
 *  - [aboveUsual] (amber) appears ONLY next to words, never inside charts;
 *  - charts use exactly two mark colors: [chartData] and [chartAlarm].
 */
@Immutable
data class PixelColors(
    val isDark: Boolean,
    /** Window ground behind everything. */
    val ground: Color,
    /** Panel background (PixelBox). */
    val surface: Color,
    /** Secondary panel background (nested/pressed). */
    val surface2: Color,
    /** 3dp frame color. */
    val frame: Color,
    /** Main text. */
    val text: Color,
    /** Phosphor accent: main number + status glow only. */
    val accent: Color,
    /** Secondary text. */
    val textSecondary: Color,
    /** Muted text: hints, disabled, axis labels. */
    val textMuted: Color,
    /** "Above usual" wording, text-only. */
    val aboveUsual: Color,
    /** Chart mark: data. */
    val chartData: Color,
    /** Chart mark: alarm/threshold. */
    val chartAlarm: Color,
)

val CrtColors = PixelColors(
    isDark = true,
    ground = Color(0xFF0B1406),
    surface = Color(0xFF12200C),
    surface2 = Color(0xFF1A2C10),
    frame = Color(0xFF2C4A18),
    text = Color(0xFFC9EE9A),
    accent = Color(0xFF9BE838),
    textSecondary = Color(0xFF7FA45C),
    textMuted = Color(0xFF557A38),
    aboveUsual = Color(0xFFE8B93D),
    chartData = Color(0xFF55A81E),
    chartAlarm = Color(0xFFE05570),
)

val DmgColors = PixelColors(
    isDark = false,
    ground = Color(0xFFC4CFA1),
    surface = Color(0xFFCDD8AC),
    surface2 = Color(0xFFC4CFA1),
    frame = Color(0xFF9AAA76),
    text = Color(0xFF1E3009),
    accent = Color(0xFF16260A),
    textSecondary = Color(0xFF44622A),
    textMuted = Color(0xFF44622A),
    aboveUsual = Color(0xFF8A6206),
    chartData = Color(0xFF3F7A14),
    chartAlarm = Color(0xFFB3263C),
)

val LocalPixelColors = staticCompositionLocalOf { CrtColors }
