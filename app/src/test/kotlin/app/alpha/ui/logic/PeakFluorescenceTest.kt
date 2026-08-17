package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.Peak
import app.alpha.ui.text.SpectrumEn
import app.alpha.ui.text.SpectrumRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Область 70–115 кэВ: характеристический рентген K-серии.
 *
 * До этого пик 84,5 кэВ получал прочерк без единого слова — ни линии в
 * библиотеке, ни механизма, ни места, где об этом прочитать. Между тем в
 * гамма-спектре этот бугор — обычное дело: Kβ свинца 84,9 кэВ и Kβ висмута
 * 87,3 кэВ.
 */
class PeakFluorescenceTest {

    private val calibration = EnergyCalibration(0f, 3f, 0f)
    private val flat: List<Int> = List(1024) { 100 }

    private fun peak(energyKeV: Float) = Peak(
        channel = (energyKeV / 3f).toInt(),
        energyKeV = energyKeV,
        netCounts = 3_000f,
        significance = 10f,
    )

    private fun matchAt(energyKeV: Float): PeakMatch =
        PeakEvidenceBridge.analyse(listOf(peak(energyKeV)), flat, calibration, 0.08f)
            .rows.single().match

    @Test
    fun `84,5 keV is named as the K series of lead and bismuth`() {
        val match = matchAt(84.5f)
        assertTrue(match is PeakMatch.Artifact, "$match")
        assertEquals(ArtifactKind.XRAY, match.kind)
        // Ближайшая линия идёт первой: Kβ1 свинца 84,94 кэВ.
        assertEquals("Pb", match.xrayLines.first().first)
        val note = SpectrumFormat.matchNotes(match).single()
        assertTrue(note.contains("Pb 84,9") && note.contains("Bi 87,3"), note)
        // Числа форматируются одинаково на всех языках ([SpectrumFormat]).
        assertTrue(SpectrumFormat.matchNotes(match, SpectrumEn).single().contains("Pb 84,9"))
    }

    @Test
    fun `the note names at most three elements`() {
        // Допуск на 93 кэВ накрывает линии тория, урана и висмута сразу;
        // перечислять все попавшие линии серии бессмысленно — прибор их не
        // разделяет.
        val match = matchAt(93f)
        assertTrue(match is PeakMatch.Artifact, "$match")
        assertEquals(listOf("Th", "U", "Bi"), match.xrayLines.map { it.first })
    }

    @Test
    fun `a library line closer than the X-ray line keeps the peak`() {
        // 81,0 кэВ Ba-133 против Kα1 висмута 77,1 кэВ: обе энергии табличные,
        // и спор решается расстоянием до центроиды, а не порядком стадий.
        val match = matchAt(81f)
        assertTrue(match is PeakMatch.Candidate, "$match")
        assertEquals("Ba-133", match.nuclide)
    }

    @Test
    fun `the K series of uranium does not reach the 122 keV lines`() {
        // При допуске в половину FWHM Kβ2 урана 114,55 кэВ дотягивалась до
        // 122 кэВ и забирала пик у кобальта-57 и европия.
        val match = matchAt(122f)
        assertTrue(match !is PeakMatch.Artifact, "$match")
    }

    @Test
    fun `a dash says what it is a dash about`() {
        val match = matchAt(2000f)
        assertEquals(PeakMatch.None, match)
        assertEquals(listOf(SpectrumRu.noExplanationNote), SpectrumFormat.matchNotes(match))
        assertEquals(listOf(SpectrumEn.noExplanationNote), SpectrumFormat.matchNotes(match, SpectrumEn))
    }
}
