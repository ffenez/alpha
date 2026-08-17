package app.alpha.analysis.evidence

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvidenceEngineTest {

    private val options = EvidenceOptions(continuum = flatContinuum(1.0))

    private fun analyse(peaks: List<ObservedPeak>, opts: EvidenceOptions = options) =
        EvidenceEngine.analysePeaks(peaks, opts)

    private fun candidate(result: SpectrumEvidence, nuclide: String) =
        assertNotNull(result.candidates.firstOrNull { it.nuclide == nuclide }, "нет кандидата $nuclide")

    @Test
    fun `three matched lines with no missing ones support the candidate`() {
        val result = analyse(
            listOf(
                peakAt(609.3, netArea = 1000.0),
                peakAt(1120.3, netArea = 330.0),
                peakAt(1764.5, netArea = 340.0),
            ),
        )
        val bi = candidate(result, "Bi-214")
        assertEquals(3, bi.matchedLines)
        assertEquals(3, bi.expectedObservableLines)
        assertTrue(bi.missingExpectedLines.isEmpty())
        assertTrue(bi.contradictions.isEmpty())
        assertEquals(EvidenceClass.SUPPORTED, bi.classification)
        assertEquals("Ra-226", bi.chain)
    }

    @Test
    fun `a strong line without its brighter companion contradicts the candidate`() {
        // Виден только 1764,5 кэВ. Линия 609,3 втрое ярче и лежит ниже по
        // энергии, поэтому её отсутствие — довод ПРОТИВ, а не «нет данных».
        val result = analyse(listOf(peakAt(1764.5, netArea = 1000.0)))
        val bi = candidate(result, "Bi-214")
        assertEquals(1, bi.matchedLines)
        assertTrue(bi.missingExpectedLines.any { it.energyKeV == 609.3 })
        assertEquals(EvidenceClass.CONTRADICTED, bi.classification)
        assertTrue(
            bi.contradictions.all { it.kind == ContradictionKind.MISSING_EXPECTED_OBSERVABLE_LINE },
        )
    }

    @Test
    fun `without a continuum a missing line proves nothing`() {
        val result = analyse(
            listOf(peakAt(1764.5, netArea = 1000.0)),
            EvidenceOptions(continuum = null),
        )
        val bi = candidate(result, "Bi-214")
        assertTrue(bi.missingExpectedLines.isEmpty(), "нечем судить о видимости")
        assertTrue(bi.contradictions.isEmpty())
        assertEquals(EvidenceClass.WEAK, bi.classification)
    }

    @Test
    fun `unresolvable alternatives are grouped instead of picking a winner`() {
        val result = analyse(
            listOf(
                peakAt(242.0, netArea = 200.0),
                peakAt(295.2, netArea = 500.0),
                peakAt(351.9, netArea = 900.0),
            ),
        )
        val pb214 = candidate(result, "Pb-214")
        assertEquals(EvidenceClass.AMBIGUOUS, pb214.classification)
        val group = pb214.resolutionAmbiguities.first { it.lines.any { l -> l.energyKeV == 351.9 } }
        assertTrue("I-131" in group.nuclides, "альтернативы 351,9: ${group.nuclides}")
        // Прибор не различает область 238–242 кэВ, поэтому Pb-212 остаётся
        // равноправным кандидатом, а не отбрасывается.
        assertEquals(EvidenceClass.AMBIGUOUS, candidate(result, "Pb-212").classification)
    }

    @Test
    fun `a lone unique line is weak, never supported`() {
        val result = analyse(listOf(peakAt(1460.8, netArea = 800.0)))
        val k40 = candidate(result, "K-40")
        assertEquals(1, k40.matchedLines)
        assertTrue(k40.resolutionAmbiguities.isEmpty())
        assertEquals(EvidenceClass.WEAK, k40.classification)
    }

    @Test
    fun `511 keV is explained as annihilation while the nuclide stays weak`() {
        val result = analyse(listOf(peakAt(511.0, netArea = 700.0)))
        assertTrue(result.artifactExplanations.any { it is PeakExplanation.Annihilation })
        val tl = candidate(result, "Tl-208")
        assertEquals(EvidenceClass.WEAK, tl.classification)
        val line = tl.lines.first { it.line.energyKeV == 510.8 }
        assertTrue(line.explainedByArtifact, "пик объясним и без нуклида")
    }

    @Test
    fun `intensity ratios are shown but not evaluated without an efficiency model`() {
        val result = analyse(
            listOf(
                peakAt(609.3, netArea = 1000.0),
                peakAt(1120.3, netArea = 330.0),
                peakAt(1764.5, netArea = 340.0),
            ),
        )
        val consistency = candidate(result, "Bi-214").intensityConsistency
        assertTrue(consistency is IntensityConsistency.NotEvaluated)
        assertEquals(NotEvaluatedReason.NO_EFFICIENCY_MODEL, consistency.reason)
        // Сырое отношение — измерение, и оно доступно всегда.
        val ratio = consistency.ratios.first { it.numerator.line.energyKeV == 1764.5 }
        assertTrue(abs(ratio.observed - 0.34) < 1e-9)
        assertTrue(abs(ratio.expectedByYield - 15.3 / 45.5) < 1e-9)
        assertTrue(ratio.sigma > 0.0)
    }

    @Test
    fun `an unexplained peak stays unexplained`() {
        val result = analyse(listOf(peakAt(661.7, netArea = 900.0), peakAt(900.0, netArea = 400.0)))
        assertEquals(listOf(900.0), result.unexplainedPeaks.map { it.centroidKeV })
    }

    @Test
    fun `calibration diagnostics reports a systematic shift without correcting anything`() {
        val shifted = analyse(listOf(peakAt(1470.8, netArea = 1000.0), peakAt(2624.5, netArea = 1000.0)))
        val diagnostic = shifted.calibration
        assertEquals(CalibrationVerdict.POSSIBLE_SYSTEMATIC_SHIFT, diagnostic.verdict)
        val shift = assertNotNull(diagnostic.shiftKeV)
        assertTrue(abs(shift - 10.0) < 0.5, "сдвиг $shift")
        // Энергии пиков остались нетронутыми — никакой тихой коррекции.
        assertEquals(1470.8, shifted.peaks.first().centroidKeV)

        val aligned = analyse(listOf(peakAt(1460.8, netArea = 1000.0), peakAt(2614.5, netArea = 1000.0)))
        assertEquals(CalibrationVerdict.CONSISTENT, aligned.calibration.verdict)
    }
}
