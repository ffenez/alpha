package app.alpha.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings

/**
 * Оформление приложения — вариант дизайн-языка, а не «тема».
 *
 * Светлая/тёмная отвечают на вопрос «сколько вокруг света»; скин отвечает на
 * другой — «как это выглядит». Поэтому они независимы: 8-bit существует и в
 * светлом, и в тёмном варианте, а научный терминал остаётся тем, чем был.
 *
 * ## Почему это безопасно
 *
 * Скин НЕ трогает ни один экран: он меняет только значения токенов, которые
 * экраны и так читают через `LocalAppColors`, `LocalAppTypography` и
 * `LocalAppMetrics`. Ни одна формулировка, ни одно правило честности, ни один
 * расчёт от скина не зависят — меняются цвет, шрифт и радиус, и только они.
 */
enum class AppSkin(val id: String) {
    /** «Научный терминал» — исходный дизайн-язык (docs/design/design-language.md). */
    TERMINAL("terminal"),

    /**
     * «8-bit» — дозиметр с ретро-консоли: ограниченная насыщенная палитра,
     * прямые углы, толстая рамка, моноширинный шрифт с разрядкой.
     *
     * Пиксельный шрифт не бандлится: лишний файл в APK ради одного скина не
     * окупается, а IBM Plex Mono с разрядкой даёт нужное ощущение экрана
     * консоли, не притворяясь растровым шрифтом.
     */
    EIGHT_BIT("8bit"),
    ;

    /** Название оформления — из каталога: имя enum уезжает в настройки на диск. */
    fun title(s: Strings = RuStrings): String = when (this) {
        TERMINAL -> s.skinTerminal
        EIGHT_BIT -> s.skinEightBit
    }

    companion object {
        fun of(id: String?): AppSkin = entries.firstOrNull { it.id == id } ?: TERMINAL
    }
}

/**
 * Размеры, зависящие от скина: радиусы и толщина рамки.
 *
 * Вынесены из [Dimens] в свой CompositionLocal именно потому, что от скина
 * зависят только они — сетка отступов и размер цели нажатия остаются общими,
 * иначе «8-bit» превратился бы в другое приложение, а не в другой вид.
 */
@Immutable
data class AppMetrics(
    val radiusCard: Dp,
    val radiusButton: Dp,
    val radiusChip: Dp,
    val radiusSegment: Dp,
    val border: Dp,
)

val TerminalMetrics = AppMetrics(
    radiusCard = Dimens.radiusCard,
    radiusButton = Dimens.radiusButton,
    radiusChip = Dimens.radiusChip,
    radiusSegment = Dimens.radiusSegment,
    border = Dimens.border,
)

/** Прямые углы и рамка в два пикселя — так рисовал бы интерфейс тайловый движок. */
val EightBitMetrics = AppMetrics(
    radiusCard = 0.dp,
    radiusButton = 0.dp,
    radiusChip = 0.dp,
    radiusSegment = 0.dp,
    border = 2.dp,
)

val LocalAppMetrics = staticCompositionLocalOf { TerminalMetrics }

/**
 * Палитра 8-bit, тёмный вариант: почти чёрный фон и люминофорная зелень.
 *
 * Семантика цветов та же, что в научном терминале, и это принципиально:
 * янтарь по-прежнему значит «выше обычного», красный — подтверждённую
 * тревогу. Скин меняет оттенки, а не смысл — иначе он менял бы показания.
 */
val EightBitDarkColors = AppColors(
    isDark = true,
    bg = Color(0xFF0B0F0B),
    surface = Color(0xFF121A12),
    surface2 = Color(0xFF1A241A),
    line = Color(0xFF2E4630),
    ink = Color(0xFFCFF5CF),
    ink2 = Color(0xFF7FBF7F),
    muted = Color(0xFF4E7A50),
    ok = Color(0xFF5BE85B),
    warn = Color(0xFFF2C14E),
    crit = Color(0xFFFF5A5A),
    data = Color(0xFF3AD6C8),
    dataText = Color(0xFF6BF0E2),
    onData = Color(0xFF06231F),
    chartField = Color(0xFF080C08),
    chartGrid = Color(0xFF25391F),
    chartZebra = Color(0x0AFFFFFF),
    chartBeyondData = Color(0x1F000000),
)

/** Светлый 8-bit: бумажно-жёлтый корпус карманной консоли. */
val EightBitLightColors = AppColors(
    isDark = false,
    bg = Color(0xFFE8E6C8),
    surface = Color(0xFFF4F2D8),
    surface2 = Color(0xFFDAD8B4),
    line = Color(0xFF8C8A6E),
    ink = Color(0xFF1B2416),
    ink2 = Color(0xFF4A5540),
    muted = Color(0xFF7A8470),
    ok = Color(0xFF2C7A2C),
    warn = Color(0xFF9A6A00),
    crit = Color(0xFFB03A2E),
    data = Color(0xFF116D68),
    dataText = Color(0xFF0C4F4B),
    onData = Color(0xFFF4F2D8),
    chartField = Color(0xFFDCDAB8),
    chartGrid = Color(0xFFA8A688),
    chartZebra = Color(0x08000000),
    chartBeyondData = Color(0x1A000000),
)
