package app.radiacode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens of the «Научный терминал» design language
 * (docs/design/design-language.md). Dark is the primary theme; the light
 * palette mirrors it. Semantic rules:
 *  - normal readings are neutral ink — the app never celebrates a value;
 *  - [warn] (amber) means «выше обычного» and appears next to words, plus as
 *    the highlighted-candidate mark on the spectrum chart;
 *  - [crit] (red) is reserved for the confirmed persistent alarm and the
 *    named alarm line on charts;
 *  - [data] is chart/data teal — data, not status; [dataText] is its
 *    text-contrast counterpart (nav active state, links to data).
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    /** Window ground behind everything. */
    val bg: Color,
    /** Card background. */
    val surface: Color,
    /** Secondary surface: segmented-control track, inputs, plain buttons. */
    val surface2: Color,
    /** 1dp hairline borders and dividers. */
    val line: Color,
    /** Main text. */
    val ink: Color,
    /** Secondary text. */
    val ink2: Color,
    /** Muted text: hints, disabled, axis labels, footnotes. */
    val muted: Color,
    /** Normal / connected / ok status. */
    val ok: Color,
    /** «Выше обычного» — amber, next to words. */
    val warn: Color,
    /** Confirmed alarm; alarm line on charts. */
    val crit: Color,
    /** Data teal: chart series, primary button fill. */
    val data: Color,
    /** Data teal with text contrast: active nav item, emphasized data text. */
    val dataText: Color,
    /** Text on a [data]-filled surface (primary button label). */
    val onData: Color,
)

val DarkColors = AppColors(
    isDark = true,
    bg = Color(0xFF0F1216),
    surface = Color(0xFF151A20),
    surface2 = Color(0xFF1B222A),
    line = Color(0xFF232B34),
    ink = Color(0xFFE7EAEE),
    ink2 = Color(0xFF97A1AC),
    muted = Color(0xFF5F6873),
    ok = Color(0xFF55C08B),
    warn = Color(0xFFE8A33D),
    crit = Color(0xFFE86A5E),
    data = Color(0xFF22A0B6),
    dataText = Color(0xFF4FC3D8),
    onData = Color(0xFF06222A),
)

val LightColors = AppColors(
    isDark = false,
    bg = Color(0xFFF4F6F8),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECF0F3),
    line = Color(0xFFE1E6EB),
    ink = Color(0xFF171C22),
    ink2 = Color(0xFF5A6470),
    muted = Color(0xFF8B95A0),
    ok = Color(0xFF1E7A50),
    warn = Color(0xFFA56410),
    crit = Color(0xFFBC3E33),
    data = Color(0xFF177E92),
    dataText = Color(0xFF116273),
    onData = Color(0xFFFFFFFF),
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }
