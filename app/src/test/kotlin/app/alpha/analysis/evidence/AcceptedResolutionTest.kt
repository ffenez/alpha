package app.alpha.analysis.evidence

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Хранение принятой модели и правило «только на своём приборе».
 */
class AcceptedResolutionTest {

    private val record = AcceptedResolution(
        a = 412.5,
        b = 2.47,
        c = 0.0,
        deviceSerial = "RC-110-000115",
        acceptedAtMillis = 1_760_000_000_000L,
        points = 4,
        lowestKeV = 1120.3,
        highestKeV = 2614.5,
        algorithmVersion = 1,
    )

    @AfterTest
    fun reset() {
        ResolutionSource.install(null)
        ResolutionSource.onDevice(null)
    }

    @Test
    fun `the record survives a round trip`() {
        val decoded = AcceptedResolution.decode(record.encode())
        assertEquals(record, decoded)
    }

    @Test
    fun `a broken string decodes to no model instead of crashing`() {
        assertNull(AcceptedResolution.decode(null))
        assertNull(AcceptedResolution.decode(""))
        assertNull(AcceptedResolution.decode("a=nonsense;b=1;c=0"))
        // Отрицательный шум невозможен физически — такую запись читать нельзя.
        assertNull(AcceptedResolution.decode("a=-5;b=1;c=0"))
    }

    @Test
    fun `the measured model acts only on the instrument it was measured on`() {
        ResolutionSource.install(record)
        ResolutionSource.onDevice(null)
        assertNotNull(ResolutionSource.active, "без подключённого прибора модель действует")

        ResolutionSource.onDevice("RC-110-000115")
        assertNotNull(ResolutionSource.active)

        ResolutionSource.onDevice("RC-103G-000042")
        assertNull(ResolutionSource.active, "на чужом приборе чужая ширина не применяется")
        assertNotNull(ResolutionSource.stored, "но запись никуда не девается")
    }

    @Test
    fun `peak search follows the accepted model and returns to the approximation`() {
        val approximate = app.alpha.analysis.PeakDetection.expectedFwhmKeV(1460.8f)
        ResolutionSource.install(record)
        val measured = app.alpha.analysis.PeakDetection.expectedFwhmKeV(1460.8f)
        assertTrue(
            kotlin.math.abs(measured - approximate) > 1f,
            "измеренная ширина обязана отличаться: $measured против $approximate",
        )
        ResolutionSource.install(null)
        assertEquals(approximate, app.alpha.analysis.PeakDetection.expectedFwhmKeV(1460.8f))
    }

    @Test
    fun `the radon ROI keeps the approximation so its series stays comparable`() {
        val before = app.alpha.analysis.PeakDetection.fwhmKeV(609.3f)
        ResolutionSource.install(record)
        assertEquals(before, app.alpha.analysis.PeakDetection.fwhmKeV(609.3f))
    }
}
