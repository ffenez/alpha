package app.alpha.ui.text

import app.alpha.data.DoseUnitSetting
import app.alpha.ui.logic.DoseFormat
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UiNumberTest {

    @AfterTest
    fun tearDown() = UiNumber.reset()

    @Test
    fun `английский интерфейс печатает точку, русский — запятую`() {
        UiNumber.apply(AppLanguage.EN)
        assertEquals("0.17", "0.17".uiDecimal())

        UiNumber.apply(AppLanguage.RU)
        assertEquals("0,17", "0.17".uiDecimal())
    }

    @Test
    fun `разделитель доходит до чисел на экране, а не только до строки`() {
        // Ради этого всё и делалось: мощность дозы на английском экране
        // печаталась как «0,17» — запятая читается там перечислением.
        UiNumber.apply(AppLanguage.EN)
        assertEquals("0.17", DoseFormat.rate(0.17f, DoseUnitSetting.MICRO_SIEVERT))

        UiNumber.apply(AppLanguage.RU)
        assertEquals("0,17", DoseFormat.rate(0.17f, DoseUnitSetting.MICRO_SIEVERT))
    }

    @Test
    fun `по умолчанию разделитель русский`() {
        UiNumber.reset()
        assertEquals("1,5", "1.5".uiDecimal())
    }

    @Test
    fun `английская подпись плитки помещается в одну строку`() {
        // Плитка Главной держит подпись в ОДНУ строку и обрезает лишнее:
        // «place background» превращалось на экране в «PLACE BACKGRO…».
        // Русская «фон места» — 9 знаков, английская должна быть не длиннее.
        assertEquals(
            true,
            EnStrings.tilePlaceBackground.length <= RuStrings.tilePlaceBackground.length + 1,
            "подпись «${EnStrings.tilePlaceBackground}» не влезет в плитку",
        )
    }
}
