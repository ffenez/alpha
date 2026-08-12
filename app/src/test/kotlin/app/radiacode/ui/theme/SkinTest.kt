package app.radiacode.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Скин обязан менять ВИД и ничего кроме: показания, формулировки и правила
 * честности одинаковы во всех вариантах оформления.
 */
class SkinTest {

    @Test
    fun `an unknown stored skin falls back to the original design language`() {
        assertEquals(AppSkin.TERMINAL, AppSkin.of(null))
        assertEquals(AppSkin.TERMINAL, AppSkin.of("mystery"))
        assertEquals(AppSkin.EIGHT_BIT, AppSkin.of("8bit"))
    }

    @Test
    fun `every skin fills every colour role`() {
        // Пропущенная роль означала бы невидимый текст или невидимую границу
        // на одном из оформлений.
        for (colors in listOf(EightBitDarkColors, EightBitLightColors, DarkColors, LightColors)) {
            for (role in listOf(
                colors.bg, colors.surface, colors.surface2, colors.line,
                colors.ink, colors.ink2, colors.muted,
                colors.ok, colors.warn, colors.crit,
                colors.data, colors.dataText, colors.onData,
                colors.chartField, colors.chartGrid,
            )) {
                assertTrue(role.alpha > 0f, "прозрачная роль в палитре")
            }
        }
    }

    @Test
    fun `text stays readable against its own ground in every skin`() {
        // Порог 4.5:1 — требование к основному тексту; проверяются обе
        // плоскости, на которых он лежит.
        for (colors in listOf(EightBitDarkColors, EightBitLightColors, DarkColors, LightColors)) {
            assertTrue(contrast(colors.ink, colors.bg) >= 4.5, "ink на bg: ${colors.isDark}")
            assertTrue(contrast(colors.ink, colors.surface) >= 4.5, "ink на surface")
            // Вторичный текст — не ниже 3:1, он крупнее и короче.
            assertTrue(contrast(colors.ink2, colors.surface) >= 3.0, "ink2 на surface")
        }
    }

    @Test
    fun `the semantics of colour do not change with the skin`() {
        // Янтарь «выше обычного» и красный «тревога» обязаны остаться
        // различимыми между собой в любом оформлении: иначе скин менял бы не
        // вид, а смысл.
        // Различимость ЦВЕТА, а не яркости: янтарь и красный могут совпасть
        // по светлоте и всё равно читаться по-разному.
        for (colors in listOf(EightBitDarkColors, EightBitLightColors)) {
            assertTrue(hueDistance(colors.warn, colors.crit) > 0.15f, "warn и crit слились")
            assertTrue(hueDistance(colors.ok, colors.crit) > 0.2f, "ok и crit слились")
            assertTrue(hueDistance(colors.ok, colors.warn) > 0.15f, "ok и warn слились")
        }
    }

    @Test
    fun `the eight-bit skin has square corners and a thicker border`() {
        assertEquals(0, EightBitMetrics.radiusCard.value.toInt())
        assertEquals(0, EightBitMetrics.radiusChip.value.toInt())
        assertTrue(EightBitMetrics.border > TerminalMetrics.border)
        // А научный терминал не изменился ни на пиксель.
        assertEquals(Dimens.radiusCard, TerminalMetrics.radiusCard)
        assertEquals(Dimens.border, TerminalMetrics.border)
    }

    @Test
    fun `numbers keep tabular figures and gain a slashed zero`() {
        // Ноль и O на приборном экране путаются; перечёркнутый ноль включён
        // везде, где стоят числа, в обоих оформлениях.
        val terminal = AppTypography()
        val eightBit = eightBitTypography()
        for (style in listOf(terminal.valueHero, terminal.value, terminal.axis)) {
            assertTrue(style.fontFeatureSettings?.contains("tnum") == true)
            assertTrue(style.fontFeatureSettings?.contains("zero") == true)
        }
        assertTrue(eightBit.valueHero.fontFeatureSettings?.contains("zero") == true)
        // И главное число больше не сжато отрицательным трекингом.
        assertTrue(terminal.valueHero.letterSpacing.value >= 0f)
    }

    /** Евклидово расстояние в RGB — грубая, но достаточная мера различимости. */
    private fun hueDistance(
        a: androidx.compose.ui.graphics.Color,
        b: androidx.compose.ui.graphics.Color,
    ): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    /** WCAG relative-luminance contrast ratio. */
    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: androidx.compose.ui.graphics.Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
