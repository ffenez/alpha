package app.radiacode.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HotspotDetectorTest {

    @Test
    fun `fires once on upward crossing`() {
        val detector = HotspotDetector(thresholdMicroSvH = 0.30f)
        assertFalse(detector.onSample(0.10f))
        assertTrue(detector.onSample(0.35f))
        // Still above: no repeated events.
        assertFalse(detector.onSample(0.50f))
        assertFalse(detector.onSample(0.31f))
    }

    @Test
    fun `rearms only after dropping below the hysteresis band`() {
        val detector = HotspotDetector(thresholdMicroSvH = 0.30f, rearmFraction = 0.8f)
        assertTrue(detector.onSample(0.40f))
        // 0.25 is below threshold but inside the band (>= 0.24): stays disarmed.
        assertFalse(detector.onSample(0.25f))
        assertFalse(detector.onSample(0.35f))
        // Below 0.24 rearms; the next crossing fires again.
        assertFalse(detector.onSample(0.20f))
        assertTrue(detector.onSample(0.31f))
    }

    @Test
    fun `exact threshold value fires`() {
        val detector = HotspotDetector(thresholdMicroSvH = 0.30f)
        assertTrue(detector.onSample(0.30f))
    }

    @Test
    fun `threshold can be reconfigured live`() {
        val detector = HotspotDetector(thresholdMicroSvH = 0.30f)
        assertFalse(detector.onSample(0.20f))
        detector.thresholdMicroSvH = 0.15f
        assertTrue(detector.onSample(0.20f))
    }

    @Test
    fun `rejects invalid rearm fraction`() {
        assertFailsWith<IllegalArgumentException> { HotspotDetector(0.3f, rearmFraction = 1.5f) }
    }

    @Test
    fun `sequence around threshold produces one event per excursion`() {
        val detector = HotspotDetector(thresholdMicroSvH = 0.30f)
        val samples = listOf(0.1f, 0.2f, 0.35f, 0.4f, 0.28f, 0.33f, 0.1f, 0.45f, 0.5f)
        val events = samples.count { detector.onSample(it) }
        assertEquals(2, events)
    }
}
