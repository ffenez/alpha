package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlightDetectTest {

    private fun point(second: Long, altitude: Double?, dose: Float? = null) = FlightDetect.Point(
        timestampMillis = second * 1000L,
        altitudeMeters = altitude,
        doseMicroSvH = dose,
    )

    // --- sustain detection ---

    @Test
    fun `two sustained minutes above 3000 m is a flight`() {
        val points = (0L..130L).map { point(it, 10_500.0) }
        assertTrue(FlightDetect.sustainedFlight(points))
    }

    @Test
    fun `a short altitude spike is not a flight`() {
        val points = (0L..300L).map {
            point(it, if (it in 100L..140L) 10_500.0 else 200.0)
        }
        assertFalse(FlightDetect.sustainedFlight(points))
    }

    @Test
    fun `ground and missing altitude never count`() {
        assertFalse(FlightDetect.sustainedFlight((0L..600L).map { point(it, 250.0) }))
        assertFalse(FlightDetect.sustainedFlight((0L..600L).map { point(it, null) }))
        assertFalse(FlightDetect.sustainedFlight(emptyList()))
    }

    @Test
    fun `a long fix gap breaks the run`() {
        // 90 s high, a 5-minute GPS hole, 90 s high: neither run sustains.
        val points = (0L..90L).map { point(it, 11_000.0) } +
            (390L..480L).map { point(it, 11_000.0) }
        assertFalse(FlightDetect.sustainedFlight(points))
        // Same total but continuous: flight.
        assertTrue(FlightDetect.sustainedFlight((0L..180L).map { point(it, 11_000.0) }))
    }

    @Test
    fun `mountains below the threshold are not flights`() {
        val points = (0L..600L).map { point(it, 2_800.0) }
        assertFalse(FlightDetect.sustainedFlight(points))
    }

    // --- xN summary ---

    @Test
    fun `factor is the ratio of flight to ground dose medians`() {
        val points =
            (0L..99L).map { point(it, 300.0, dose = 0.10f) } + // ground before takeoff
                (100L..199L).map { point(it, 10_000.0, dose = 2.0f) } // cruise
        val summary = FlightDetect.summary(points)
        assertEquals(2.0f, summary.flightMedianMicroSvH!!, 1e-4f)
        assertEquals(0.10f, summary.groundMedianMicroSvH!!, 1e-4f)
        assertEquals(20f, summary.factor!!, 1e-3f)
    }

    @Test
    fun `factor is honest about missing sides`() {
        // Only cruise points: no ground median, no factor.
        val cruiseOnly = FlightDetect.summary((0L..50L).map { point(it, 10_000.0, 1.8f) })
        assertEquals(1.8f, cruiseOnly.flightMedianMicroSvH!!, 1e-4f)
        assertNull(cruiseOnly.groundMedianMicroSvH)
        assertNull(cruiseOnly.factor)
        // Points without dose or altitude are excluded entirely.
        val empty = FlightDetect.summary(
            listOf(point(0, null, 1f), point(1, 10_000.0, null)),
        )
        assertNull(empty.flightMedianMicroSvH)
        assertNull(empty.factor)
    }

    // --- altitude chart columns ---

    @Test
    fun `altitude columns average per bucket and leave gaps null`() {
        val points = listOf(
            point(0, 1000.0),
            point(1, 2000.0), // bucket 0: mean 1500
            point(20, 9000.0), // bucket 2
        )
        val columns = FlightDetect.altitudeColumns(
            points = points,
            alignedFromMillis = 0L,
            bucketMillis = 10_000L,
            columnCount = 4,
        )
        assertEquals(1500f, columns[0]!!, 1e-3f)
        assertNull(columns[1])
        assertEquals(9000f, columns[2]!!, 1e-3f)
        assertNull(columns[3])
    }
}
