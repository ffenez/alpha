package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Dose projection D ≈ Ḋ·t (spec §6), including its dimensional checks. */
class DoseProjectionTest {

    @Test
    fun `dimension check µSv per h times h gives µSv`() {
        // 1 µSv/h over 1 h is 1 µSv; over a year it is exactly the year in hours.
        assertEquals(1.0, DoseProjection.project(1.0, 1.0), 1e-12)
        assertEquals(
            DoseProjection.HOURS_PER_YEAR,
            DoseProjection.project(1.0, DoseProjection.HOURS_PER_YEAR),
            1e-9,
        )
        // 0.1 µSv/h × 24 h = 2.4 µSv — a day's worth at a typical background.
        assertEquals(2.4, DoseProjection.project(0.1, DoseProjection.HOURS_PER_DAY), 1e-12)
    }

    @Test
    fun `year is the Julian year in hours`() {
        assertEquals(365.25 * 24.0, DoseProjection.HOURS_PER_YEAR, 1e-9)
        assertEquals(8766.0, DoseProjection.HOURS_PER_YEAR, 1e-9)
    }

    @Test
    fun `mean rate is the integral over measured time, not wall time`() {
        // 12 µSv accumulated over 24 h of *measurement* → 0.5 µSv/h.
        val mean = assertNotNull(DoseProjection.meanRateMicroSvPerHour(12.0, 24 * 3600L))
        assertEquals(0.5, mean, 1e-12)
        // The same dose measured over only 12 h means twice the rate.
        val half = assertNotNull(DoseProjection.meanRateMicroSvPerHour(12.0, 12 * 3600L))
        assertEquals(1.0, half, 1e-12)
    }

    @Test
    fun `projection is linear in the rate`() {
        val single = DoseProjection.project(0.12, DoseProjection.HOURS_PER_YEAR)
        val double = DoseProjection.project(0.24, DoseProjection.HOURS_PER_YEAR)
        assertEquals(2.0 * single, double, 1e-9)
    }

    @Test
    fun `full projection from an integrated window`() {
        // 0.15 µSv/h steady for 48 h of measurement.
        val measuredSeconds = 48 * 3600L
        val dose = 0.15 * 48
        val projection = assertNotNull(DoseProjection.fromIntegral(dose, measuredSeconds))
        assertEquals(0.15, projection.meanRateMicroSvPerHour, 1e-12)
        assertEquals(measuredSeconds, projection.measuredSeconds)
        assertEquals(DoseProjection.HOURS_PER_YEAR, projection.horizonHours, 1e-9)
        assertEquals(0.15 * DoseProjection.HOURS_PER_YEAR, projection.doseMicroSv, 1e-9)
        // ≈ 1315 µSv — the number the History card shows for a typical background.
        assertEquals(1314.9, projection.doseMicroSv, 0.1)
    }

    @Test
    fun `too little measurement gives no projection`() {
        assertNull(
            DoseProjection.fromIntegral(0.05, DoseProjection.MIN_MEASURED_SECONDS - 1),
            "an hour of data cannot honestly produce a year",
        )
        assertNotNull(DoseProjection.fromIntegral(0.05, DoseProjection.MIN_MEASURED_SECONDS))
    }

    @Test
    fun `degenerate inputs never produce a number`() {
        assertNull(DoseProjection.meanRateMicroSvPerHour(1.0, 0L))
        assertNull(DoseProjection.meanRateMicroSvPerHour(-1.0, 3600L))
        assertNull(DoseProjection.meanRateMicroSvPerHour(Double.NaN, 3600L))
        assertEquals(0.0, DoseProjection.project(Double.NaN, 10.0))
        assertEquals(0.0, DoseProjection.project(1.0, 0.0))
        assertEquals(0.0, DoseProjection.project(-1.0, 10.0))
    }

    @Test
    fun `algorithm version is pinned`() {
        assertEquals(AlgorithmVersions.DOSE_PROJECTION, DoseProjection.ALGORITHM_VERSION)
    }
}
