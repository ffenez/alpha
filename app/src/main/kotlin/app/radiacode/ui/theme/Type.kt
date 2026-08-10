// FontVariation (variable-font axes) is still experimental in Compose text.
@file:OptIn(ExperimentalTextApi::class)

package app.radiacode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.radiacode.R

/**
 * «Научный терминал» typography (design-language.md): IBM Plex Sans for UI
 * text, IBM Plex Mono for every number and data string. All numeric roles
 * carry tabular figures so values update without jitter. Both families ship
 * with native cyrillic.
 */
val PlexSans = FontFamily(
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, weight = FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, weight = FontWeight.SemiBold),
)

private const val TABULAR_FIGURES = "tnum"

@Immutable
data class AppTypography(
    /** The single main reading of a screen (44sp mono 600, tabular). */
    val valueHero: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Secondary large readings (24sp mono 600, tabular). */
    val valueLarge: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Inline data values: kv rows, statgrid cells (13sp mono 600, tabular). */
    val value: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Dense data strings: tables, session summaries (11.5sp mono 400, tabular). */
    val valueSmall: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Mono footnotes and units (10.5sp mono 400, tabular). */
    val footnote: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Chart axis labels, chips (10sp mono 600, tabular). */
    val axis: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    /** Screen/section titles (16sp sans 600). */
    val title: TextStyle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    /** UI labels: buttons, place name, session titles (12.5sp sans 600). */
    val label: TextStyle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
    ),
    /** Small caps-style captions above values (10.5sp sans 600, spaced). */
    val labelSmall: TextStyle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.07.em,
    ),
    /** Statgrid keys and table headers (9.5sp sans 500, spaced). */
    val overline: TextStyle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 9.5.sp,
        letterSpacing = 0.04.em,
    ),
    /** Body text: explanations, settings, long strings. */
    val body: TextStyle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** Small body text. */
    val bodySmall: TextStyle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.5.sp,
    ),
)

val LocalAppTypography = staticCompositionLocalOf { AppTypography() }
