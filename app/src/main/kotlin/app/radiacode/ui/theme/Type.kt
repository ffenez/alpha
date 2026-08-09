// FontVariation (variable-font axes) is still experimental in Compose text.
@file:OptIn(ExperimentalTextApi::class)

package app.radiacode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.radiacode.R

/**
 * Hybrid typography (design-language.md): Pixelify Sans for data screens,
 * headings and numbers; system Roboto for long/body text. Tabular figures
 * everywhere a value updates in place, so digits do not jitter.
 */
val PixelFontFamily = FontFamily(
    Font(
        R.font.pixelify_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.pixelify_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private const val TABULAR_FIGURES = "tnum"

@Immutable
data class PixelTypography(
    /** The single main reading of a screen (64sp/700, tabular). */
    val valueHuge: TextStyle = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Secondary readings (24sp/700, tabular). */
    val valueLarge: TextStyle = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Inline numbers in rows and pills (16sp, tabular). */
    val value: TextStyle = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Screen and panel titles. */
    val heading: TextStyle = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    /** Pixel-styled UI text: buttons, tags, nav, statuses. */
    val label: TextStyle = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    /** Small pixel text: axis labels, footnotes on data screens (tabular). */
    val labelSmall: TextStyle = TextStyle(
        fontFamily = PixelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Body text: explanations, settings, long strings — system Roboto. */
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    /** Small body text — system Roboto. */
    val bodySmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

val LocalPixelTypography = staticCompositionLocalOf { PixelTypography() }
