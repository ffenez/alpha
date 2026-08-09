package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyWindow
import kotlin.test.Test
import kotlin.test.assertEquals

class SpectrumFormatTest {

    @Test
    fun `accumulation clock formats minutes and hours`() {
        assertEquals("00:05", SpectrumFormat.accumulationClock(5))
        assertEquals("04:32", SpectrumFormat.accumulationClock(272))
        assertEquals("1:07:09", SpectrumFormat.accumulationClock(4029))
        assertEquals("00:00", SpectrumFormat.accumulationClock(-3))
    }

    @Test
    fun `window label rounds to whole keV`() {
        assertEquals("0–3072 кэВ", SpectrumFormat.windowLabel(EnergyWindow(0.4f, 3071.7f)))
    }
}
