// FontVariation (variable-font axes) is still experimental in Compose text.
@file:OptIn(ExperimentalTextApi::class)

package app.alpha.ui.theme

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
import app.alpha.R

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

/**
 * Цифры одинаковой ширины (`tnum`) — и **перечёркнутый ноль** (`zero`).
 *
 * Ноль и буква O в моноширинном шрифте на приборном экране различаются плохо,
 * особенно боком и на солнце: «0,10» и «O,1O» на секунду читаются одинаково.
 * IBM Plex Mono несёт перечёркнутый ноль стилевым набором, и для показаний
 * прибора он включён везде, где стоят числа.
 */
private const val TABULAR_FIGURES = "tnum, zero"

@Immutable
data class AppTypography(
    /** The single main reading of a screen (44sp mono 600, tabular). */
    val valueHero: TextStyle = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        // Отрицательный трекинг сжимал главное число: цифры слипались как раз
        // там, где их читают быстрее всего. Нулевой — цифры стоят свободно.
        letterSpacing = 0.sp,
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
    /**
     * Приглушённая подпись — ЧЕЛОВЕЧЕСКИЙ ТЕКСТ (11sp sans 400).
     *
     * Была моноширинной, и ею набирались объяснения, оговорки и целые абзацы:
     * моноширинный шрифт хорош для чисел, единиц и меток времени, но длинную
     * фразу им читать заметно тяжелее, и экран начинает выглядеть терминалом,
     * а не прибором. Числа внутри такой фразы остаются сансом — они часть
     * предложения; отдельные ЧИСЛОВЫЕ подписи набираются [footnoteMono].
     */
    val footnote: TextStyle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.5.sp,
    ),
    /** Числовая подпись: значения в строках карточек, единицы (10.5sp mono). */
    val footnoteMono: TextStyle = TextStyle(
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

/**
 * Типографика 8-bit: моноширинный шрифт везде, включая прозу, и разрядка —
 * так текст выглядит набранным на консольном экране.
 *
 * Заголовки и подписи становятся моно НАМЕРЕННО, вопреки правилу «сане —
 * текст, моно — данные»: правило служит читаемости научного терминала, а
 * здесь выбран другой вид, и человек выбрал его сам.
 */
fun eightBitTypography(): AppTypography {
    val base = AppTypography()
    fun TextStyle.pixelated(tracking: Float = 0.04f): TextStyle = copy(
        fontFamily = PlexMono,
        letterSpacing = tracking.em,
    )
    return base.copy(
        valueHero = base.valueHero.pixelated(0.02f),
        valueLarge = base.valueLarge.pixelated(0.02f),
        value = base.value.pixelated(0.02f),
        valueSmall = base.valueSmall.pixelated(),
        footnote = base.footnote.pixelated(),
        footnoteMono = base.footnoteMono.pixelated(),
        axis = base.axis.pixelated(0.06f),
        title = base.title.pixelated(0.06f),
        label = base.label.pixelated(0.06f),
        labelSmall = base.labelSmall.pixelated(0.1f),
        overline = base.overline.pixelated(0.1f),
        body = base.body.pixelated(),
        bodySmall = base.bodySmall.pixelated(),
    )
}

val LocalAppTypography = staticCompositionLocalOf { AppTypography() }
