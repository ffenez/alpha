package app.radiacode.analysis

import app.radiacode.baseline.BaselineAdmission
import app.radiacode.baseline.BaselineConfig
import app.radiacode.context.NetworkIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Algorithm versions (spec §22, §24 point 8).
 *
 * These assertions are **pins, not logic**: they fail whenever a version
 * number changes, which is exactly the point — a maths change must arrive
 * together with a conscious version bump and a review of what old stored
 * results now mean. Update the expected numbers in the same commit that
 * changes the algorithm, never separately.
 */
class AlgorithmVersionsTest {

    @Test
    fun `versions are pinned`() {
        assertEquals(2, AlgorithmVersions.BASELINE)
        assertEquals(1, AlgorithmVersions.BASELINE_ADMISSION)
        assertEquals(1, AlgorithmVersions.NETWORK_IDENTITY)
        assertEquals(1, AlgorithmVersions.PEAK_DETECTION)
        assertEquals(1, AlgorithmVersions.ISOTOPE_MATCH)
        assertEquals(1, AlgorithmVersions.SPECTRUM_COMPARE)
        assertEquals(1, AlgorithmVersions.SPECTRUM_MERGE)
        assertEquals(1, AlgorithmVersions.RADON_TREND)
        assertEquals(1, AlgorithmVersions.ENERGY_WINDOWS)
        assertEquals(1, AlgorithmVersions.DOSE_PROJECTION)
        assertEquals(1, AlgorithmVersions.AB_ANALYSIS)
    }

    @Test
    fun `algorithms that carry their own constant stay in sync`() {
        assertEquals(AlgorithmVersions.BASELINE, BaselineConfig.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.BASELINE_ADMISSION, BaselineAdmission.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.NETWORK_IDENTITY, NetworkIdentity.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.ENERGY_WINDOWS, EnergyWindows.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.DOSE_PROJECTION, DoseProjection.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.AB_ANALYSIS, AbAnalysis.ALGORITHM_VERSION)
    }

    @Test
    fun `every version is reachable by its storage key`() {
        val expected = setOf(
            "baseline",
            "baseline_admission",
            "network_identity",
            "peak_detection",
            "isotope_match",
            "spectrum_compare",
            "spectrum_merge",
            "radon_trend",
            "energy_windows",
            "dose_projection",
            "ab_analysis",
        )
        assertEquals(expected, AlgorithmVersions.all.keys)
        assertTrue(AlgorithmVersions.all.values.all { it >= 1 })
    }

    @Test
    fun `stamp names the algorithms and their versions`() {
        assertEquals(
            "ab_analysis v1 · energy_windows v1",
            AlgorithmVersions.stamp("ab_analysis", "energy_windows"),
        )
        assertEquals("", AlgorithmVersions.stamp("no_such_algorithm"))
    }
}
