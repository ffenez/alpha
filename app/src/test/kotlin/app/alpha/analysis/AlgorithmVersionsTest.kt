package app.alpha.analysis

import app.alpha.baseline.BaselineAdmission
import app.alpha.baseline.BaselineConfig
import app.alpha.analysis.quantiles.KllSketch
import app.alpha.context.NetworkIdentity
import app.alpha.ui.logic.DoseHistograms
import app.alpha.ui.logic.TrendFit
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
        // v2: значимость считается по σ нетто-площади (было net/√(B·width)),
        // и добавлена проверка ширины структуры — числа изменились осознанно.
        assertEquals(2, AlgorithmVersions.PEAK_DETECTION)
        assertEquals(1, AlgorithmVersions.ISOTOPE_MATCH)
        // v1 движка доказательств: экран Спектра перешёл на него, ISOTOPE_MATCH
        // не бампается — математика матчера не менялась, он только снят с экрана.
        assertEquals(1, AlgorithmVersions.PEAK_EVIDENCE)
        assertEquals(1, AlgorithmVersions.SPECTRUM_COMPARE)
        assertEquals(1, AlgorithmVersions.SPECTRUM_MERGE)
        assertEquals(1, AlgorithmVersions.RADON_TREND)
        assertEquals(1, AlgorithmVersions.ENERGY_WINDOWS)
        assertEquals(1, AlgorithmVersions.DOSE_PROJECTION)
        assertEquals(1, AlgorithmVersions.AB_ANALYSIS)
        assertEquals(1, AlgorithmVersions.QUANTILE_SKETCH)
        assertEquals(2, AlgorithmVersions.TREND_FIT)
        assertEquals(2, AlgorithmVersions.DOSE_HISTOGRAM)
        assertEquals(1, AlgorithmVersions.DESCRIPTIVE_DEVIATION)
        assertEquals(1, AlgorithmVersions.ANOMALY_TEST_CANDIDATE)
        assertEquals(1, AlgorithmVersions.RATE_COMPARISON)
        assertEquals(1, AlgorithmVersions.SEARCH_LADDER)
        assertEquals(1, AlgorithmVersions.SHAPE_CHANGE)
        assertEquals(1, AlgorithmVersions.HARDNESS)
        assertEquals(1, AlgorithmVersions.FINGERPRINT)
        assertEquals(1, AlgorithmVersions.BACKGROUND_CALIBRATION)
    }

    @Test
    fun `algorithms that carry their own constant stay in sync`() {
        assertEquals(AlgorithmVersions.BASELINE, BaselineConfig.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.BASELINE_ADMISSION, BaselineAdmission.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.NETWORK_IDENTITY, NetworkIdentity.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.QUANTILE_SKETCH, KllSketch.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.ENERGY_WINDOWS, EnergyWindows.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.DOSE_PROJECTION, DoseProjection.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.AB_ANALYSIS, AbAnalysis.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.TREND_FIT, TrendFit.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.DOSE_HISTOGRAM, DoseHistograms.ALGORITHM_VERSION)
        assertEquals(
            AlgorithmVersions.DESCRIPTIVE_DEVIATION,
            DescriptiveDeviation.ALGORITHM_VERSION,
        )
        assertEquals(AlgorithmVersions.RATE_COMPARISON, RateComparison.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.SHAPE_CHANGE, ShapeChange.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.HARDNESS, Hardness.ALGORITHM_VERSION)
        assertEquals(AlgorithmVersions.FINGERPRINT, Fingerprint.ALGORITHM_VERSION)
        assertEquals(
            AlgorithmVersions.BACKGROUND_CALIBRATION,
            app.alpha.analysis.evidence.BackgroundCalibration.ALGORITHM_VERSION,
        )
        assertEquals(
            AlgorithmVersions.PEAK_EVIDENCE,
            app.alpha.analysis.evidence.EvidenceEngine.ALGORITHM_VERSION,
        )
        assertEquals(
            AlgorithmVersions.SEARCH_LADDER,
            app.alpha.ui.logic.SearchLadder.ALGORITHM_VERSION,
        )
        @OptIn(ExperimentalRadiationStatistics::class)
        assertEquals(
            AlgorithmVersions.ANOMALY_TEST_CANDIDATE,
            AnomalyStatistics.ALGORITHM_VERSION,
        )
    }

    @Test
    fun `every version is reachable by its storage key`() {
        val expected = setOf(
            "baseline",
            "baseline_admission",
            "network_identity",
            "peak_detection",
            "isotope_match",
            "peak_evidence",
            "spectrum_compare",
            "spectrum_merge",
            "radon_trend",
            "energy_windows",
            "dose_projection",
            "ab_analysis",
            "quantile_sketch",
            "trend_fit",
            "dose_histogram",
            "descriptive_deviation",
            "anomaly_test_candidate",
            "rate_comparison",
            "search_ladder",
            "shape_change",
            "hardness",
            "fingerprint",
            "background_calibration",
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
