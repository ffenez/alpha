package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SpectrumMergeTest {

    private val cal = EnergyCalibration(-6f, 2.4f, 0.0004f)

    private fun input(
        counts: List<Int>,
        seconds: Long,
        calibration: EnergyCalibration = cal,
        name: String = "s",
    ) = SpectrumMerge.Input(counts, seconds, calibration, name)

    @Test
    fun `merges channel-wise with summed duration`() {
        val outcome = SpectrumMerge.merge(
            listOf(
                input(listOf(1, 2, 3, 0), 100),
                input(listOf(10, 0, 5, 7), 250),
            ),
        )
        val ok = assertIs<SpectrumMerge.Outcome.Ok>(outcome)
        assertEquals(listOf(11, 2, 8, 7), ok.counts)
        assertEquals(350L, ok.durationSeconds)
        assertEquals(cal, ok.calibration)
    }

    @Test
    fun `merges more than two snapshots`() {
        val outcome = SpectrumMerge.merge(
            listOf(
                input(listOf(1, 1), 10),
                input(listOf(2, 2), 20),
                input(listOf(3, 3), 30),
            ),
        )
        val ok = assertIs<SpectrumMerge.Outcome.Ok>(outcome)
        assertEquals(listOf(6, 6), ok.counts)
        assertEquals(60L, ok.durationSeconds)
    }

    @Test
    fun `takes the calibration of the longest input`() {
        val cal2 = EnergyCalibration(-6f, 2.4005f, 0.0004f) // ~0.5 keV at ch 1023
        val outcome = SpectrumMerge.merge(
            listOf(
                input(List(1024) { 1 }, 100, cal),
                input(List(1024) { 1 }, 900, cal2),
            ),
        )
        val ok = assertIs<SpectrumMerge.Outcome.Ok>(outcome)
        assertEquals(cal2, ok.calibration)
    }

    @Test
    fun `refuses calibration mismatch beyond the shared tolerance`() {
        // ~10 keV shift at high channels: clearly beyond the 5 keV tolerance.
        val shifted = EnergyCalibration(-6f, 2.41f, 0.0004f)
        val outcome = SpectrumMerge.merge(
            listOf(
                input(List(1024) { 1 }, 100, cal, name = "A"),
                input(List(1024) { 1 }, 200, shifted, name = "B"),
            ),
        )
        val invalid = assertIs<SpectrumMerge.Outcome.Invalid>(outcome)
        assertTrue("калибровк" in invalid.reason)
        assertTrue("скорост" in invalid.reason) // honest pointer to the safe tool
    }

    @Test
    fun `small calibration difference within tolerance merges`() {
        // ~1 keV at ch 1023 — inside the 5 keV tolerance.
        val near = EnergyCalibration(-5.5f, 2.4f, 0.0004f)
        val outcome = SpectrumMerge.merge(
            listOf(
                input(List(1024) { 2 }, 100, cal),
                input(List(1024) { 3 }, 200, near),
            ),
        )
        assertIs<SpectrumMerge.Outcome.Ok>(outcome)
    }

    @Test
    fun `refuses different channel grids and single input`() {
        assertIs<SpectrumMerge.Outcome.Invalid>(
            SpectrumMerge.merge(
                listOf(input(listOf(1, 2), 10), input(listOf(1, 2, 3), 10)),
            ),
        )
        assertIs<SpectrumMerge.Outcome.Invalid>(
            SpectrumMerge.merge(listOf(input(listOf(1, 2), 10))),
        )
    }
}
