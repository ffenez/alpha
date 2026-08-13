package app.radiacode.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Два ползунка обязаны двигать РАЗНОЕ: «шрифт» — только текст, «элементы» —
 * только размеры в dp. Если развязка сломается, оба будут менять текст, и
 * пользователь получит два ползунка с одинаковым действием.
 */
class UiScaleTest {

    private val systemDensity = 2.75f
    private val systemFontScale = 1f

    @Test
    fun `element scale moves dp and leaves text where it was`() {
        val elementPercent = 130
        val density = UiScale.density(systemDensity, elementPercent)
        assertTrue(density > systemDensity, "элементы не выросли")

        // Размер текста в пикселях = sp × density × fontScale. Проверяем именно
        // произведение: оно и есть то, что видит глаз.
        val fontScale = UiScale.fontScale(systemFontScale, UiScale.DEFAULT_PERCENT, elementPercent)
        val textPixels = density * fontScale
        assertEquals(systemDensity * systemFontScale, textPixels, 1e-4f)
    }

    @Test
    fun `font scale moves text and leaves dp where it was`() {
        val fontPercent = 130
        val density = UiScale.density(systemDensity, UiScale.DEFAULT_PERCENT)
        assertEquals(systemDensity, density, 1e-4f)

        val fontScale = UiScale.fontScale(systemFontScale, fontPercent, UiScale.DEFAULT_PERCENT)
        assertEquals(systemFontScale * 1.3f, fontScale, 1e-4f)
    }

    @Test
    fun `the system font scale is multiplied, never replaced`() {
        // Человек уже увеличил шрифт во всей системе — он не должен получить
        // его обратно уменьшенным из-за нашего значения по умолчанию.
        val big = 1.45f
        val fontScale = UiScale.fontScale(big, UiScale.DEFAULT_PERCENT, UiScale.DEFAULT_PERCENT)
        assertEquals(big, fontScale, 1e-4f)
        assertTrue(UiScale.fontScale(big, UiScale.FONT_MIN_PERCENT, 100) < big)
        assertTrue(UiScale.fontScale(big, UiScale.FONT_MAX_PERCENT, 100) > big)
    }

    @Test
    fun `a stored value outside the range cannot break the screen`() {
        assertEquals(UiScale.FONT_MAX_PERCENT, UiScale.clampFont(400))
        assertEquals(UiScale.FONT_MIN_PERCENT, UiScale.clampFont(0))
        assertEquals(UiScale.ELEMENT_MAX_PERCENT, UiScale.clampElement(999))
        assertEquals(UiScale.ELEMENT_MIN_PERCENT, UiScale.clampElement(-10))
        // И плотность после зажима остаётся положительной при любом входе.
        assertTrue(UiScale.density(systemDensity, -10) > 0f)
    }

    @Test
    fun `the slider snaps by rounding, not truncation`() {
        // Тик приходит с погрешностью float: 110 приезжает как 109,9999, и
        // усечение схлопывало бы соседние ступени в одну.
        assertEquals(110, UiScale.snap(109.9999f))
        assertEquals(110, UiScale.snap(112.4f))
        assertEquals(115, UiScale.snap(112.5f))
        assertEquals(100, UiScale.snap(100f))
    }

    @Test
    fun `both ranges include the neutral value`() {
        assertTrue(UiScale.DEFAULT_PERCENT in UiScale.FONT_MIN_PERCENT..UiScale.FONT_MAX_PERCENT)
        assertTrue(
            UiScale.DEFAULT_PERCENT in UiScale.ELEMENT_MIN_PERCENT..UiScale.ELEMENT_MAX_PERCENT,
        )
        // Границы кратны шагу — иначе крайнее положение ползунка недостижимо.
        for (bound in listOf(
            UiScale.FONT_MIN_PERCENT, UiScale.FONT_MAX_PERCENT,
            UiScale.ELEMENT_MIN_PERCENT, UiScale.ELEMENT_MAX_PERCENT,
        )) {
            assertEquals(0, bound % UiScale.STEP_PERCENT, "граница $bound не кратна шагу")
        }
    }
}
