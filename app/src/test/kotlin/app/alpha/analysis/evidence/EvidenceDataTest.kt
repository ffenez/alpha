package app.alpha.analysis.evidence

import app.alpha.analysis.Peak
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvidenceDataTest {

    @Test
    fun `library lines carry provenance and no invented uncertainties`() {
        assertTrue(EvidenceLineLibrary.LINES.isNotEmpty())
        for (line in EvidenceLineLibrary.LINES) {
            assertEquals(DataSource.ENSDF, line.source, "источник линии ${line.energyKeV}")
            // Числа без источника не появляются: ENSDF-выборка не дала
            // неопределённостей, поэтому они ОТСУТСТВУЮТ, а не равны нулю.
            assertNull(line.energyUncertaintyKeV)
            assertNull(line.intensityUncertaintyPercent)
        }
    }

    @Test
    fun `strongest line of a nuclide is the brightest one`() {
        assertEquals(2614.5, EvidenceLineLibrary.strongestLineOf("Tl-208")?.energyKeV)
        assertEquals(609.3, EvidenceLineLibrary.strongestLineOf("Bi-214")?.energyKeV)
    }

    @Test
    fun `centroid uncertainty follows FWHM over 2_355 sqrt N`() {
        val sigma = ObservedPeak.centroidUncertaintyKeV(fwhmKeV = 47.1, netArea = 10_000.0)
        // 47,1 / (2,3548 · 100) = 0,2 кэВ
        assertTrue(abs(sigma - 0.2) < 0.005, "σ центроида = $sigma")
    }

    @Test
    fun `centroid uncertainty shrinks as sqrt of statistics`() {
        val few = ObservedPeak.centroidUncertaintyKeV(50.0, 100.0)
        val many = ObservedPeak.centroidUncertaintyKeV(50.0, 10_000.0)
        assertTrue(abs(few / many - 10.0) < 1e-9, "вчетверо больше статистики — вдвое точнее")
    }

    @Test
    fun `net area uncertainty is recovered exactly from significance`() {
        val detected = Peak(channel = 200, energyKeV = 661.7f, netCounts = 900f, significance = 30f)
        val observed = ObservedPeak.from(detected, TEST_RESOLUTION)
        // σ = нетто / значимость — то самое число, что посчитал детектор по IAEA.
        assertTrue(abs(observed.netAreaUncertainty - 30.0) < 1e-4)
        assertEquals(TEST_RESOLUTION.fwhmKeV(661.7), observed.fwhmKeV, 1e-6)
    }
}
