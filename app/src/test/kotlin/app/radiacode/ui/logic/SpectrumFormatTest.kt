package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.EnergyWindowSpec
import app.radiacode.analysis.EnergyWindows
import app.radiacode.analysis.HintConfidence
import app.radiacode.analysis.IsotopeHint
import app.radiacode.analysis.Peak
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

    @Test
    fun `peak table cells - energy net snr`() {
        assertEquals("661,9", SpectrumFormat.energyCell(661.94f))
        assertEquals("1 240", SpectrumFormat.netCell(1240.3f))
        assertEquals("890", SpectrumFormat.netCell(890.0f))
        assertEquals("8,2σ", SpectrumFormat.significanceCell(8.24f))
    }

    @Test
    fun `candidate cell - natural is calm, the rest carries confidence`() {
        assertEquals("Bi-214 · природный", SpectrumFormat.candidateCell(hint("Bi-214", natural = true)))
        assertEquals(
            "Cs-137 · средняя ур.",
            SpectrumFormat.candidateCell(
                hint("Cs-137", natural = false, confidence = HintConfidence.MEDIUM),
            ),
        )
        assertEquals(
            "I-131 · низкая ур.",
            SpectrumFormat.candidateCell(
                hint("I-131", natural = false, confidence = HintConfidence.LOW),
            ),
        )
    }

    @Test
    fun `accumulation chip groups the pulse count`() {
        assertEquals(
            "Δt 12:34 · 184 302 имп",
            SpectrumFormat.accumulationChip(754, 184_302),
        )
    }

    @Test
    fun `calibration line - comma decimals and superscript scientific a2`() {
        assertEquals(
            "калибровка: E = −5,6 + 2,41·ch + 4,1·10⁻⁴·ch² · 1024 канала",
            SpectrumFormat.calibrationLine(-5.6f, 2.41f, 4.1e-4f, 1024),
        )
        assertEquals(
            "калибровка: E = 0,0 + 3,00·ch − 1,0·10⁻³·ch² · 256 каналов",
            SpectrumFormat.calibrationLine(0f, 3f, -1e-3f, 256),
        )
    }

    @Test
    fun `range cells carry the rate with its sigma, the share and the covered span`() {
        // 1000 импульсов за 200 с: R = 5 имп/с, σ_R = √1000/200 ≈ 0,158.
        val counts = List(1000) { if (it in 100..299) 5 else 0 }
        val window = EnergyWindows.window(
            counts,
            200,
            EnergyCalibration(0f, 1f, 0f),
            EnergyWindowSpec(100f, 300f),
        )
        assertEquals("100–300", SpectrumFormat.rangeLabel(window.spec))
        assertEquals("5,00 ± 0,158", SpectrumFormat.rangeRate(window))
        assertEquals("100 %", SpectrumFormat.rangeShare(window))
        assertEquals("99,5–299,5", SpectrumFormat.rangeCovered(window))

        val ratio = EnergyWindows.spectralIndex(window, window.copy(counts = 500L))
        assertEquals("2,00", SpectrumFormat.ratioShort(ratio!!))
        assertEquals("2,00 ± 0,110", SpectrumFormat.ratioValue(ratio))
    }

    private fun hint(
        isotope: String,
        natural: Boolean,
        confidence: HintConfidence = HintConfidence.LOW,
    ) = IsotopeHint(
        isotope = isotope,
        chain = null,
        natural = natural,
        peak = Peak(channel = 100, energyKeV = 661.9f, netCounts = 890f, significance = 5.1f),
        lineEnergyKeV = 661.7f,
        confidence = confidence,
        alternatives = emptyList(),
    )
}
