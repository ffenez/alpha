package app.alpha.analysis.validation

import app.alpha.analysis.ExperimentalRadiationStatistics
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the validation harness (graph spec §36.5–36.7) on the bundled
 * deterministic synthetic recordings, and on real RC-110 CSV recordings if the
 * operator has put them in [ValidationCsv.DIRECTORY].
 *
 * What this proves today: the machinery measures what it claims to measure,
 * and the autocorrelation correction is not cosmetic — without it, continuous
 * scanning of a *perfectly stationary* series raises alarms constantly.
 *
 * What it does not prove: that the candidate test is safe on real data. The
 * promotion criteria are in `docs/analysis/trend-and-anomaly.md`.
 */
@OptIn(ExperimentalRadiationStatistics::class)
class AnomalyValidationTest {

    private val config = AnomalyValidationHarness.ScanConfig()

    @Test
    fun `naive N makes a stationary recording look like a permanent anomaly`() {
        val series = SyntheticSeries.stationary(seconds = 12 * 3_600, seed = 20260810L)
        val naive = AnomalyValidationHarness.falsePositiveRate(
            series,
            config.copy(correctForAutocorrelation = false),
        )
        // 1 Hz readings are serially correlated, so the naive sample size
        // overstates the evidence by roughly √τ ≈ 3 in the standardised
        // statistic — and a nominal α = 0.001 becomes a false alarm rate of
        // tens of percent.
        assertTrue(naive.scan.tests > 100, "tests: ${naive.scan.tests}")
        assertTrue(naive.scan.rate > 0.10, "naive FP rate was ${naive.scan.rate}")
        println("naive: ${naive.scan}, alarms/day = ${naive.alarmsPerDay}")
    }

    @Test
    fun `the N_eff correction brings the false-positive rate down by an order of magnitude`() {
        val series = SyntheticSeries.stationary(seconds = 12 * 3_600, seed = 20260810L)
        val corrected = AnomalyValidationHarness.falsePositiveRate(series, config)
        val naive = AnomalyValidationHarness.falsePositiveRate(
            series,
            config.copy(correctForAutocorrelation = false),
        )
        println("corrected: ${corrected.scan}, alarms/day = ${corrected.alarmsPerDay}")
        assertEquals(naive.scan.tests, corrected.scan.tests)
        assertTrue(
            corrected.scan.rate * 5 < naive.scan.rate,
            "corrected ${corrected.scan.rate} vs naive ${naive.scan.rate}",
        )
        // Honest bound, not a promotion criterion: on the synthetic model the
        // corrected scan stays below 5 % of tests. The real target
        // (≤ 1 alarm/day on a real stationary RC-110 recording) needs real data.
        assertTrue(corrected.scan.rate < 0.05, "corrected FP rate was ${corrected.scan.rate}")
        // The estimated autocorrelation time is the one the model was built
        // with: a = 0.2 → τ = (2−a)/a = 9.
        assertTrue(corrected.scan.meanTau in 4.0..16.0, "τ̄ = ${corrected.scan.meanTau}")
    }

    @Test
    fun `a controlled step is detected with high power`() {
        val report = AnomalyValidationHarness.detectionPower(
            trials = 12,
            seed = 555L,
            stepAtSeconds = 4 * 3_600,
            stepFactor = 1.5,
            totalSeconds = 5 * 3_600,
            config = config,
        )
        println("power: $report")
        assertTrue(report.power >= 0.9, "power was ${report.power}")
        assertTrue(report.medianDelaySeconds != null)
    }

    @Test
    fun `a step too small to matter is not claimed`() {
        // +2 % of the level: below what a 5-minute window can resolve against
        // an hour of baseline once the correlation is accounted for.
        val report = AnomalyValidationHarness.detectionPower(
            trials = 12,
            seed = 777L,
            stepAtSeconds = 4 * 3_600,
            stepFactor = 1.02,
            totalSeconds = 5 * 3_600,
            config = config,
        )
        println("small-step power: $report")
        assertTrue(report.power <= 0.5, "power was ${report.power} — the test is over-confident")
    }

    @Test
    fun `real recordings are used when the operator supplies them`() {
        val stationary = File(repoRoot(), "${ValidationCsv.DIRECTORY}/${ValidationCsv.STATIONARY_FILE}")
        val segments = ValidationCsv.read(stationary)
        if (segments.isEmpty()) {
            println(
                "no real RC-110 recording at ${stationary.path} — the candidate test stays " +
                    "experimental; see docs/analysis/trend-and-anomaly.md",
            )
            return
        }
        val longest = segments.maxBy { it.values.size }
        val report = AnomalyValidationHarness.falsePositiveRate(longest.values, config)
        println("real stationary: ${report.scan}, alarms/day = ${report.alarmsPerDay}")
        assertTrue(report.scan.tests > 0, "the recording is shorter than one scan window")
    }

    @Test
    fun `the CSV reader splits on gaps and ignores comments`() {
        val file = File.createTempFile("validation", ".csv").apply { deleteOnExit() }
        file.writeText(
            """
            # RC-110, кухня
            timestamp_ms,dose_rate_usv_h
            1000,0.10
            2000,0.11
            3000,0.12
            60000,0.30
            61000,0.31
            """.trimIndent(),
        )
        val segments = ValidationCsv.read(file)
        assertEquals(2, segments.size)
        assertEquals(3, segments[0].values.size)
        assertEquals(1000L, segments[0].startMillis)
        assertEquals(2, segments[1].values.size)
        assertEquals(60_000L, segments[1].startMillis)
    }

    /** Tests run with the module directory as cwd; the repo root is its parent. */
    private fun repoRoot(): File =
        File(System.getProperty("user.dir") ?: ".").parentFile ?: File(".")
}
