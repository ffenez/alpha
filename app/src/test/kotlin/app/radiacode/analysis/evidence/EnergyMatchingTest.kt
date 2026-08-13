package app.radiacode.analysis.evidence

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnergyMatchingTest {

    private val cs137 = lineOf("Cs-137", 661.7)
    private val bi609 = lineOf("Bi-214", 609.3)

    @Test
    fun `calibration sigma is an engineering parameter with a floor`() {
        // 1 % от энергии, но не меньше 0,5 кэВ — у 59,5 кэВ процент дал бы
        // калибровку точнее ширины канала.
        assertEquals(6.617, EnergyMatching.calibrationSigmaKeV(661.7), 1e-9)
        assertEquals(0.595, EnergyMatching.calibrationSigmaKeV(59.5), 1e-9)
        assertEquals(0.5, EnergyMatching.calibrationSigmaKeV(10.0), 1e-9)
    }

    @Test
    fun `unknown reference uncertainty is not treated as zero silently`() {
        val match = EnergyMatching.match(peakAt(661.0), cs137)
        assertNotNull(match)
        // Неопределённость таблицы неизвестна — это записано в результате,
        // а не растворено в числе.
        assertFalse(match.referenceUncertaintyKnown)
    }

    @Test
    fun `z is the distance in units of the combined uncertainty`() {
        val peak = peakAt(611.2, netArea = 10_000.0)
        val z = EnergyMatching.z(peak, bi609)
        val sigma = EnergyMatching.combinedSigmaKeV(peak, bi609)
        assertTrue(abs(z - (611.2 - 609.3) / sigma) < 1e-9)
        // При сильном пике знаменатель почти целиком калибровочный.
        assertTrue(abs(sigma - EnergyMatching.calibrationSigmaKeV(609.3)) < 0.05, "σ = $sigma")
        assertTrue(abs(z) < 1.0, "1,9 кэВ на 609 — уверенное совпадение, z = $z")
    }

    @Test
    fun `weak statistics widen the window, strong statistics narrow it`() {
        val weak = peakAt(625.0, netArea = 40.0)
        val strong = peakAt(625.0, netArea = 100_000.0)
        val zWeak = abs(EnergyMatching.z(weak, bi609))
        val zStrong = abs(EnergyMatching.z(strong, bi609))
        assertTrue(zWeak < zStrong, "z слабого $zWeak должен быть мягче сильного $zStrong")
    }

    @Test
    fun `a peak far from the line does not match`() {
        // 583,2 кэВ Tl-208 отстоит от 609,3 на 26 кэВ — это больше 3σ.
        assertNull(EnergyMatching.match(peakAt(609.3, netArea = 10_000.0), lineOf("Tl-208", 583.2)))
    }

    @Test
    fun `sign of delta is preserved for calibration diagnostics`() {
        val above = EnergyMatching.match(peakAt(1470.0), lineOf("K-40", 1460.8))
        assertNotNull(above)
        assertTrue(above.deltaKeV > 0.0)
    }
}
