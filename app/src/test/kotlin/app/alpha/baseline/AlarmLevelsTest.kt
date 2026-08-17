package app.alpha.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmLevelsTest {

    @Test
    fun `normal preset`() {
        val t = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f)
        assertEquals(AlarmThresholds(0.30f, 1.00f, 2.0f, 120), t)
    }

    @Test
    fun `high preset is more sensitive on every axis`() {
        val normal = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f)
        val high = alarmThresholds(AlarmSensitivity.HIGH, 0f, 0f)
        assertTrue(high.l1MicroSvH < normal.l1MicroSvH)
        assertTrue(high.l2MicroSvH < normal.l2MicroSvH)
        assertTrue(high.relativeFactor < normal.relativeFactor)
        assertTrue(high.persistenceSeconds < normal.persistenceSeconds)
    }

    @Test
    fun `custom preset uses user levels`() {
        val t = alarmThresholds(AlarmSensitivity.CUSTOM, 0.5f, 2.0f)
        assertEquals(0.5f, t.l1MicroSvH)
        assertEquals(2.0f, t.l2MicroSvH)
    }

    @Test
    fun `custom preset sanitizes broken values`() {
        val zero = alarmThresholds(AlarmSensitivity.CUSTOM, 0f, 0f)
        assertEquals(0.30f, zero.l1MicroSvH)
        assertTrue(zero.l2MicroSvH >= zero.l1MicroSvH)

        val inverted = alarmThresholds(AlarmSensitivity.CUSTOM, 1.0f, 0.2f)
        assertEquals(1.0f, inverted.l1MicroSvH)
        assertEquals(1.0f, inverted.l2MicroSvH)
    }

    @Test
    fun `storage round-trip is case-insensitive and defaults to normal`() {
        assertEquals(AlarmSensitivity.HIGH, AlarmSensitivity.fromStorage("high"))
        assertEquals(AlarmSensitivity.CUSTOM, AlarmSensitivity.fromStorage("CUSTOM"))
        assertEquals(AlarmSensitivity.NORMAL, AlarmSensitivity.fromStorage(null))
        assertEquals(AlarmSensitivity.NORMAL, AlarmSensitivity.fromStorage("garbage"))
    }

    @Test
    fun `deviation magnitude - absolute threshold`() {
        val t = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f)
        assertTrue(deviationMagnitude(0.30f, baselineHighMicroSvH = null, t))
        assertFalse(deviationMagnitude(0.29f, baselineHighMicroSvH = null, t))
    }

    @Test
    fun `deviation magnitude - relative to baseline`() {
        val t = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f)
        // baseline high 0.12, factor 2.0 -> relative trigger at 0.24 < abs 0.30.
        assertTrue(deviationMagnitude(0.24f, baselineHighMicroSvH = 0.12f, t))
        assertFalse(deviationMagnitude(0.23f, baselineHighMicroSvH = 0.12f, t))
    }

    @Test
    fun `above usual magnitude needs a margin over P90`() {
        val baseline = Baseline(
            doseLowMicroSvH = 0.09f,
            doseMedianMicroSvH = 0.11f,
            doseHighMicroSvH = 0.14f,
            doseP25MicroSvH = 0.10f,
            doseP75MicroSvH = 0.13f,
            doseMadMicroSvH = 0.01f,
            cpsLow = 18f,
            cpsMedian = 22f,
            cpsHigh = 27f,
            accumulatedSeconds = 10800,
            sampleCount = 10800,
            bucketCount = 180,
        )
        assertFalse(aboveUsualMagnitude(0.14f, baseline))
        assertFalse(aboveUsualMagnitude(0.146f, baseline)) // within 5 % margin
        assertTrue(aboveUsualMagnitude(0.16f, baseline))
        assertFalse(aboveUsualMagnitude(0.16f, baseline = null))
    }
}
