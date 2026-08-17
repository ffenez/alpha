package app.alpha.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Assembly of a full A/B comparison from two recorded runs (spec §9, §16). */
class AbExperimentTest {

    private val calibration = EnergyCalibration(0f, 1f, 0f)

    private fun run(
        label: String,
        counts: List<Int>?,
        seconds: Long,
        dose: List<Double> = emptyList(),
        distanceCm: Float? = null,
    ) = AbExperiment.RunData(
        id = label.hashCode().toLong(),
        label = label,
        startedAt = 1_000_000L,
        endedAt = 1_000_000L + seconds * 1000L,
        durationSeconds = seconds,
        counts = counts,
        calibration = counts?.let { calibration },
        doseStats = AbAnalysis.doseStats(dose),
        distanceCm = distanceCm,
    )

    @Test
    fun `two identical runs are consistent everywhere`() {
        val counts = List(2000) { 20 }
        val a = run("A", counts, 300, List(300) { 0.12 })
        val b = run("B", counts, 300, List(300) { 0.12 })
        val comparison = AbExperiment.compare(a, b)

        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, assertNotNull(comparison.totalCounts).verdict)
        assertEquals(3, comparison.windows.size)
        comparison.windows.forEach { assertEquals(AbAnalysis.Verdict.CONSISTENT, it.verdict) }
        assertEquals(AbAnalysis.Verdict.CONSISTENT, assertNotNull(comparison.spectrum).verdict)
        assertTrue(comparison.warnings.isEmpty())
    }

    @Test
    fun `an object above background is strong evidence of change`() {
        val background = List(2000) { 5 }
        val withObject = background.mapIndexed { index, value ->
            if (index in 600..680) value + 200 else value
        }
        val a = run("A", withObject, 300, List(300) { 0.30 })
        val b = run("B", background, 300, List(300) { 0.12 })
        val comparison = AbExperiment.compare(a, b)

        assertEquals(AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE, comparison.verdict)
        // The excess sits in the 300–700 keV window, not in the others.
        val windows = comparison.windows.associateBy { it.label }
        assertEquals(
            AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE,
            assertNotNull(windows["300–700 кэВ"]).verdict,
        )
        assertEquals(AbAnalysis.Verdict.CONSISTENT, assertNotNull(windows["100–300 кэВ"]).verdict)
    }

    @Test
    fun `different live times are handled by the time scaling`() {
        // Same scene, B measured three times longer.
        val a = run("A", List(2000) { 10 }, 100)
        val b = run("B", List(2000) { 30 }, 300)
        val comparison = AbExperiment.compare(a, b)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
        val total = assertNotNull(comparison.totalCounts)
        assertEquals(200.0, total.rateA, 1e-9)
        assertEquals(200.0, total.rateB, 1e-9)
        assertEquals(0.0, total.net, 1e-6)
    }

    @Test
    fun `a missing spectrum degrades to the dose comparison with a warning`() {
        val a = run("A", null, 300, List(300) { 0.20 })
        val b = run("B", null, 300, List(300) { 0.12 })
        val comparison = AbExperiment.compare(a, b)
        assertNull(comparison.totalCounts)
        assertNull(comparison.spectrum)
        assertTrue(comparison.windows.isEmpty())
        assertNotNull(comparison.doseRate)
        assertTrue(comparison.warnings.any { it.contains("спектр") })
    }

    @Test
    fun `diverging calibrations refuse per-channel comparison instead of rebinning`() {
        val counts = List(2000) { 20 }
        val a = run("A", counts, 300)
        val b = run("B", counts, 300).copy(calibration = EnergyCalibration(50f, 1f, 0f))
        val comparison = AbExperiment.compare(a, b)
        assertNotNull(comparison.totalCounts, "the total count comparison still works")
        assertTrue(comparison.windows.isEmpty())
        assertNull(comparison.spectrum)
        assertTrue(comparison.warnings.any { it.contains("калибровки") })
    }

    @Test
    fun `the headline verdict ignores the advisory dose row`() {
        // Counting evidence says consistent; the (correlated) dose readings
        // would say «changed» — the headline must not inherit that.
        val counts = List(2000) { 20 }
        val a = run("A", counts, 300, List(300) { 0.1200 })
        val b = run("B", counts, 300, List(300) { 0.1201 })
        val comparison = AbExperiment.compare(a, b)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, comparison.verdict)
    }

    @Test
    fun `strongest verdict wins over the counting comparisons`() {
        assertEquals(
            AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE,
            AbExperiment.strongest(
                listOf(
                    AbAnalysis.Verdict.CONSISTENT,
                    AbAnalysis.Verdict.CHANGED,
                    AbAnalysis.Verdict.STRONG_EVIDENCE_OF_CHANGE,
                ),
            ),
        )
        assertEquals(
            AbAnalysis.Verdict.CHANGED,
            AbExperiment.strongest(
                listOf(AbAnalysis.Verdict.CONSISTENT, AbAnalysis.Verdict.CHANGED),
            ),
        )
        assertEquals(AbAnalysis.Verdict.CONSISTENT, AbExperiment.strongest(emptyList()))
    }

    // --- distance series ---

    @Test
    fun `distance series is ordered and carries the idealised 1 over r squared`() {
        // A perfect point source: 400 cps at 10 cm, 100 at 20, 44.4 at 30.
        val runs = listOf(
            run("C", List(100) { 300 }, 100, distanceCm = 30f),
            run("A", List(100) { 400 }, 100, distanceCm = 10f),
            run("B", List(100) { 100 }, 100, distanceCm = 20f),
        )
        val series = AbExperiment.distanceSeries(runs)
        assertEquals(listOf(10f, 20f, 30f), series.map { it.distanceCm })
        assertNull(series.first().inverseSquareCps, "the first point is the reference")
        val reference = series.first().netRateCps
        assertEquals(reference / 4.0, assertNotNull(series[1].inverseSquareCps), 1e-6)
        assertEquals(reference / 9.0, assertNotNull(series[2].inverseSquareCps), 1e-6)
    }

    @Test
    fun `distance series subtracts the background run when it exists`() {
        val background = run("Фон", List(100) { 10 }, 100)
        val runs = listOf(run("A", List(100) { 110 }, 100, distanceCm = 10f))
        val series = AbExperiment.distanceSeries(runs, background)
        // gross 11000 counts / 100 s = 110 cps; background 1000/100 = 10 cps.
        assertEquals(100.0, series.first().netRateCps, 1e-6)
        assertTrue(series.first().sigmaCps > 0.0)
    }

    @Test
    fun `runs without a distance are not part of a distance series`() {
        val series = AbExperiment.distanceSeries(listOf(run("A", List(10) { 1 }, 100)))
        assertTrue(series.isEmpty())
    }
}
