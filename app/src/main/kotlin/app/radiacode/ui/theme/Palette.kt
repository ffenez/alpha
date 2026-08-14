package app.radiacode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
    /**
     * Плоскость поля графика — утопленная относительно карточки.
     *
     * До неё график рисовался прямо на карточке, и в светлой теме поле
     * получалось листом бумаги: белое на белом, без границы данных. Прибор
     * читается лучше, когда поле — отдельная плоскость: в тёмной теме она
     * темнее карточки, в светлой — на шаг холоднее и темнее, и в обеих ясно,
     * где кончаются данные и начинается интерфейс.
     */
    val chartField: Color,
    /** Линии сетки поля: тише подписей, но различимы на солнце. */
    val chartGrid: Color,
    /** Полоса зебры времени на длинных окнах — опора для глаза, не данные. */
    val chartZebra: Color,
    /** Область, куда история не доходит: не ноль, а отсутствие данных. */
    val chartBeyondData: Color,
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
    // Тёмная тема: поле утоплено ниже карточки — тот же приём, что у
    // приборного экрана в корпусе.
    chartField = Color(0xFF0D1116),
    chartGrid = Color(0xFF2A333D),
    chartZebra = Color(0x07FFFFFF),
    chartBeyondData = Color(0x14000000),
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
    // Светлая тема: белая карточка, поле — на шаг холоднее и темнее, чтобы
    // граница данных была видна без рамки.
    chartField = Color(0xFFEDF1F4),
    chartGrid = Color(0xFFD5DDE4),
    chartZebra = Color(0x06000000),
    chartBeyondData = Color(0x12000000),
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

/**
 * Amber ramp of the spectrogram (design-language.md), light→dark = low→high.
 * One ramp for both themes: the amber steps hold contrast on the dark chart
 * field and on the light one alike, and the scale always pairs them with
 * numbers — density is never color alone.
 */
val DoseRampColors = listOf(
    Color(0xFFE8CB93),
    Color(0xFFC4831E),
    Color(0xFF8F5312),
    Color(0xFF5C300A),
)

/**
 * Шкала следа на карте: зелёный → багровый, семь ступеней.
 *
 * Своя, отдельная от янтарной шкалы спектрограммы: на карте цвет означает
 * УРОВЕНЬ относительно шкалы, названной в легенде, и последовательная шкала
 * читается на растровых тайлах в обеих темах, чего почти одноцветная янтарная
 * не давала — весь маршрут выходил коричневым.
 *
 * Верх — багровый, а НЕ алый: алый в этом приложении принадлежит тревоге
 * (`AppColors.crit`, метки превышений), и если бы им заканчивался обычный
 * маршрут, верх любой прогулки читался бы как авария. Цвет здесь описывает
 * величину и ничего не обещает: что он значит, сказано числами легенды.
 */
val TrackRampColors = listOf(
    Color(0xFF2E7D32),
    Color(0xFF65A844),
    Color(0xFFA6B83F),
    Color(0xFFD6A62E),
    Color(0xFFD87524),
    Color(0xFFB83A2D),
    Color(0xFF6F1635),
)


/**
 * Утопленная плоскость поля графика ([AppColors.chartField]) с тем же радиусом,
 * что у карточек: один модификатор на все графики, чтобы поле нигде не
 * оказалось «почти таким же», как в соседнем компоненте.
 */
fun Modifier.chartField(): Modifier = composed {
    val colors = LocalAppColors.current
    // Радиус берётся из МЕТРИК ОФОРМЛЕНИЯ, а не из [Dimens]: в «8-bit» углы
    // нулевые, и поле графика со скруглением 9 dp было единственным местом,
    // где скин не доезжал до картинки.
    background(colors.chartField, RoundedCornerShape(LocalAppMetrics.current.radiusChip))
}
