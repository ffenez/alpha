package app.radiacode.ui.logic

import app.radiacode.data.DoseUnitSetting
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitsTest {

    @Test
    fun `micro sievert keeps two decimals`() {
        assertEquals("0.12", DoseFormat.rate(0.1234f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("0.12 мкЗв/ч", DoseFormat.rateWithUnit(0.12f, DoseUnitSetting.MICRO_SIEVERT))
    }

    @Test
    fun `micro roentgen converts display-only at 100x`() {
        assertEquals("12.0", DoseFormat.rate(0.12f, DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("9.5", DoseFormat.rate(0.095f, DoseUnitSetting.MICRO_ROENTGEN))
        // Large values drop the fraction.
        assertEquals("150", DoseFormat.rate(1.5f, DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("12.0 мкР/ч", DoseFormat.rateWithUnit(0.12f, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `raw value is untouched - conversion only at display`() {
        assertEquals(0.12f, DoseFormat.rateValue(0.12f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals(12f, DoseFormat.rateValue(0.12f, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `accumulated dose follows the same unit`() {
        assertEquals("1.84 мкЗв", DoseFormat.doseWithUnit(1.84, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("184 мкР", DoseFormat.doseWithUnit(1.84, DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("46.0 мкР", DoseFormat.doseWithUnit(0.46, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `baseline range in both units`() {
        assertEquals("0.09–0.14", DoseFormat.range(0.09f, 0.14f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("9.0–14.0", DoseFormat.range(0.09f, 0.14f, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `unit labels`() {
        assertEquals("мкЗв/ч", DoseFormat.rateUnitLabel(DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("мкР/ч", DoseFormat.rateUnitLabel(DoseUnitSetting.MICRO_ROENTGEN))
        assertEquals("мкЗв", DoseFormat.doseUnitLabel(DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("мкР", DoseFormat.doseUnitLabel(DoseUnitSetting.MICRO_ROENTGEN))
    }
}
