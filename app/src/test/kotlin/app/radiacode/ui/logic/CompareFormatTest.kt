package app.radiacode.ui.logic

import app.radiacode.analysis.SpectrumCompare
import kotlin.test.Test
import kotlin.test.assertEquals

class CompareFormatTest {

    @Test
    fun `verdicts stay cautious and human`() {
        assertEquals(
            "в пределах шума",
            CompareFormat.verdictLabel(SpectrumCompare.Verdict.NOISE),
        )
        assertEquals(
            "возможное превышение",
            CompareFormat.verdictLabel(SpectrumCompare.Verdict.POSSIBLE_EXCESS),
        )
        assertEquals(
            "значимое превышение",
            CompareFormat.verdictLabel(SpectrumCompare.Verdict.EXCESS),
        )
        assertEquals(
            "возможное снижение",
            CompareFormat.verdictLabel(SpectrumCompare.Verdict.POSSIBLE_DEFICIT),
        )
        assertEquals(
            "значимое снижение",
            CompareFormat.verdictLabel(SpectrumCompare.Verdict.DEFICIT),
        )
    }

    @Test
    fun `region label rounds to whole keV`() {
        assertEquals("300–700", CompareFormat.regionLabel(300f, 700f))
        assertEquals("0–100", CompareFormat.regionLabel(0f, 100f))
    }

    @Test
    fun `cps precision follows magnitude with a typographic sign`() {
        assertEquals("0", CompareFormat.cps(0f))
        assertEquals("+123", CompareFormat.cps(123.4f))
        assertEquals("+9,2", CompareFormat.cps(9.21f))
        assertEquals("−0,42", CompareFormat.cps(-0.421f))
        assertEquals("+0,004", CompareFormat.cps(0.0042f))
    }

    @Test
    fun `z label carries sigma`() {
        assertEquals("+5,3σ", CompareFormat.zLabel(5.31f))
        assertEquals("−2,0σ", CompareFormat.zLabel(-2.04f))
    }

    @Test
    fun `axis labels are unsigned`() {
        assertEquals("0,42", CompareFormat.axisCps(-0.42f))
        assertEquals("9,2", CompareFormat.axisCps(9.2f))
    }
}
