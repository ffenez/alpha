package app.radiacode.ui.logic

import app.radiacode.data.DoseUnitSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnitsTest {

    @Test
    fun `micro sievert keeps two decimals`() {
        assertEquals("0,12", DoseFormat.rate(0.1234f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("0,12 мкЗв/ч", DoseFormat.rateWithUnit(0.12f, DoseUnitSetting.MICRO_SIEVERT))
    }

    @Test
    fun `micro roentgen converts display-only at 100x`() {
        assertEquals("12,0", DoseFormat.rate(0.12f, DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("9,5", DoseFormat.rate(0.095f, DoseUnitSetting.MICRO_ROENTGEN))
        // Large values drop the fraction.
        assertEquals("150", DoseFormat.rate(1.5f, DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("12,0 мкР/ч", DoseFormat.rateWithUnit(0.12f, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `raw value is untouched - conversion only at display`() {
        assertEquals(0.12f, DoseFormat.rateValue(0.12f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals(12f, DoseFormat.rateValue(0.12f, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `accumulated dose follows the same unit`() {
        assertEquals("1,84 мкЗв", DoseFormat.doseWithUnit(1.84, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("184 мкР", DoseFormat.doseWithUnit(1.84, DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("46,0 мкР", DoseFormat.doseWithUnit(0.46, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `baseline range in both units`() {
        assertEquals("0,09–0,14", DoseFormat.range(0.09f, 0.14f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("9,0–14,0", DoseFormat.range(0.09f, 0.14f, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `unit labels`() {
        assertEquals("мкЗв/ч", DoseFormat.rateUnitLabel(DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("мкР/ч", DoseFormat.rateUnitLabel(DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("мкЗв", DoseFormat.doseUnitLabel(DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("мкР", DoseFormat.doseUnitLabel(DoseUnitSetting.MICRO_ROENTGEN))
    }
    @Test
    fun `projected dose is coarse, grouped and never falsely precise`() {
        // A year at 0.15 µSv/h ≈ 1315 µSv — rounded to tens and grouped.
        assertEquals(
            "1 310 мкЗв",
            DoseFormat.doseCoarseWithUnit(1314.9, DoseUnitSetting.MICRO_SIEVERT),
        )
        assertEquals("96,4 мкЗв", DoseFormat.doseCoarseWithUnit(96.4, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("124 мкЗв", DoseFormat.doseCoarseWithUnit(123.7, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("12,3 мкЗв", DoseFormat.doseCoarseWithUnit(12.34, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("0,42 мкЗв", DoseFormat.doseCoarseWithUnit(0.42, DoseUnitSetting.MICRO_SIEVERT))
        // µR values are 100× larger and stay grouped too.
        assertEquals(
            "131 490 мкР",
            DoseFormat.doseCoarseWithUnit(1314.9, DoseUnitSetting.MICRO_ROENTGEN),
        )
    }

    @Test
    fun `the projection basis prints enough digits to reproduce the projection`() {
        // Ровно тот случай из поля: средняя 0,1553 печаталась как «0,16», и
        // 0,16 × 8766 давало 1 400 вместо показанных 1 360.
        val mean = 0.15525
        val text = DoseFormat.rateBasisWithUnit(mean, DoseUnitSetting.MICRO_SIEVERT)
        assertEquals("0,155 мкЗв/ч", text)
        val shown = text.substringBefore(' ').replace(',', '.').toDouble()
        val projected = shown * 8766.0
        assertTrue(kotlin.math.abs(projected - mean * 8766.0) < 10.0, "$projected")
    }
}
