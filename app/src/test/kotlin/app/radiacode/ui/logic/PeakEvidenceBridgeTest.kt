package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.Peak
import app.radiacode.analysis.evidence.EvidenceClass
import app.radiacode.ui.text.SpectrumEn
import app.radiacode.ui.text.SpectrumRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Мост движка доказательств — правила отображения таблицы пиков.
 *
 * Пинится по одному правилу на класс: противоречие → прочерк с пометкой,
 * неразрешимость → группа без победителя, артефакт → подпись артефакта, слабое
 * одиночное совпадение → «возможное совпадение» без усиления. И нигде — слово
 * «обнаружен».
 */
class PeakEvidenceBridgeTest {

    /** Линейная шкала 3 кэВ/канал. */
    private val calibration = EnergyCalibration(0f, 3f, 0f)

    private val flatCounts: List<Int> = List(1024) { 100 }

    private fun peak(energyKeV: Float, net: Float = 3_000f) = Peak(
        channel = (energyKeV / 3f).toInt(),
        energyKeV = energyKeV,
        netCounts = net,
        significance = 10f,
    )

    private fun analyse(
        peaks: List<Peak>,
        counts: List<Int> = emptyList(),
    ): PeakEvidenceVerdict =
        PeakEvidenceBridge.analyse(peaks, counts, calibration, resolution662 = 0.08f)

    @Test
    fun `a weak lone line is a possible match without amplification`() {
        // Am-241 — искусственный нуклид с единственной различимой линией:
        // ровно случай «совпало, но подтвердить нечем».
        val verdict = analyse(listOf(peak(59.5f)))
        val match = verdict.rows.single().match
        assertTrue(match is PeakMatch.Candidate, "$match")
        assertEquals("Am-241", match.nuclide)
        assertEquals(EvidenceClass.WEAK, match.classification)
        // Ячейка называет счёт линий, а не шкалу уверенности и не находку.
        assertEquals("Am-241 · 1 линия", SpectrumFormat.matchCell(match))
        assertEquals("Am-241 · 1 line", SpectrumFormat.matchCell(match, SpectrumEn))
        // Справка нуклида получает ТОТ ЖЕ вердикт — двоевластия больше нет.
        assertEquals(EvidenceClass.WEAK, verdict.checks.getValue("Am-241").classification)
    }

    @Test
    fun `a lone Cs-137 line falls into the 637 keV group of this library`() {
        // Честное следствие консервативного критерия Рэлея (ADR 006) на
        // текущей библиотеке: 637,0 кэВ (I-131) лежит ближе одной FWHM к
        // 661,7 кэВ, поэтому одиночной линии Cs-137 победитель не назначается
        // — показывается группа, всё так же без слова «обнаружен».
        val verdict = analyse(listOf(peak(661.7f)))
        val match = verdict.rows.single().match
        assertTrue(match is PeakMatch.AmbiguousGroup, "$match")
        assertEquals(listOf("Cs-137", "I-131"), match.nuclides)
        assertTrue(
            SpectrumFormat.matchNotes(match).any { it.contains("не разделяет") },
        )
    }

    @Test
    fun `a contradicted candidate is a dash with the note in the details`() {
        // Bi-214: виден 1120,3 кэВ, а куда более яркий 609,3 кэВ на ровном
        // континууме отсутствует — кандидат противоречит ожидаемым линиям.
        val verdict = analyse(listOf(peak(1120.3f)), flatCounts)
        val match = verdict.rows.single().match
        assertTrue(match is PeakMatch.Contradicted, "$match")
        assertEquals("—", SpectrumFormat.matchCell(match))
        val notes = SpectrumFormat.matchNotes(match)
        assertTrue(
            notes.any { it.contains("противоречит ожидаемым линиям") },
            "$notes",
        )
        assertEquals(
            EvidenceClass.CONTRADICTED,
            verdict.checks.getValue("Bi-214").classification,
        )
    }

    @Test
    fun `unresolvable lines are shown as a group, not a winner`() {
        // 351,9 кэВ Pb-214 и 364,5 кэВ I-131 прибор физически не разделяет.
        val verdict = analyse(listOf(peak(351.9f)))
        val match = verdict.rows.single().match
        assertTrue(match is PeakMatch.AmbiguousGroup, "$match")
        assertTrue("Pb-214" in match.nuclides && "I-131" in match.nuclides, "$match")
        val cell = SpectrumFormat.matchCell(match)
        assertTrue("Pb-214" in cell && "I-131" in cell, cell)
        assertTrue(
            SpectrumFormat.matchNotes(match).any { it.contains("не разделяет") },
        )
        assertTrue(
            SpectrumFormat.matchNotes(match, SpectrumEn)
                .any { it.contains("cannot separate") },
        )
    }

    @Test
    fun `the 511 keV peak is labelled an artifact, with compatible lines named`() {
        val verdict = analyse(listOf(peak(511f)))
        val match = verdict.rows.single().match
        assertTrue(match is PeakMatch.Artifact, "$match")
        assertEquals(ArtifactKind.ANNIHILATION, match.kind)
        assertEquals(SpectrumRu.artifactAnnihilation, SpectrumFormat.matchCell(match))
        val notes = SpectrumFormat.matchNotes(match)
        assertTrue(notes.any { it.contains("аннигиляционный пик 511 кэВ") }, "$notes")
        // 510,8 кэВ Tl-208 неотличима от 511: подавлять её — такая же ошибка,
        // как объявлять, поэтому совместимость названа в деталях.
        assertTrue(notes.any { it.contains("Tl-208") }, "$notes")
    }

    @Test
    fun `an escape peak is labelled by its parent, not by a nuclide`() {
        val verdict = analyse(listOf(peak(2614.5f), peak(2103.5f)))
        val escape = verdict.rows.last().match
        assertTrue(escape is PeakMatch.Artifact, "$escape")
        assertEquals(ArtifactKind.SINGLE_ESCAPE, escape.kind)
        assertEquals(SpectrumRu.artifactEscape, SpectrumFormat.matchCell(escape))
        assertTrue(
            SpectrumFormat.matchNotes(escape)
                .any { it.contains("escape-пик от 2614,5 кэВ") },
        )
    }

    @Test
    fun `a cascade sum peak is labelled a sum and the lines stay a candidate`() {
        val verdict = analyse(
            listOf(peak(1173.2f), peak(1332.5f), peak(2505.7f)),
        )
        val sum = verdict.rows.last().match
        assertTrue(sum is PeakMatch.Artifact, "$sum")
        assertEquals(ArtifactKind.SUM, sum.kind)
        assertTrue(
            SpectrumFormat.matchNotes(sum)
                .any { it.contains("сумма 1173,2 + 1332,5 кэВ") && it.contains("Co-60") },
        )
        // Сами линии каскада остаются кандидатом Co-60 с двумя линиями.
        val co60 = verdict.rows.first().match
        assertTrue(co60 is PeakMatch.Candidate, "$co60")
        assertEquals("Co-60", co60.nuclide)
        assertEquals(2, co60.matchedLines)
    }

    @Test
    fun `a peak far from every line and mechanism stays a dash`() {
        val verdict = analyse(listOf(peak(2000f)))
        val match = verdict.rows.single().match
        assertEquals(PeakMatch.None, match)
        assertEquals("—", SpectrumFormat.matchCell(match))
        assertTrue(SpectrumFormat.matchNotes(match).isEmpty())
    }

    @Test
    fun `no cell or note ever claims a detection in either language`() {
        val detection = Regex("""обнаружен|выявлен|\bdetected\b|\bidentified\b""")
        val rows = listOf(
            analyse(listOf(peak(661.7f))),
            analyse(listOf(peak(351.9f))),
            analyse(listOf(peak(1120.3f)), flatCounts),
            analyse(listOf(peak(511f))),
            analyse(listOf(peak(1173.2f), peak(1332.5f), peak(2505.7f))),
        ).flatMap { it.rows }
        for (s in listOf(SpectrumRu, SpectrumEn)) {
            for (row in rows) {
                val texts = listOf(SpectrumFormat.matchCell(row.match, s)) +
                    SpectrumFormat.matchNotes(row.match, s)
                for (text in texts) {
                    assertTrue(
                        !detection.containsMatchIn(text.lowercase()),
                        "находка в: $text",
                    )
                }
            }
        }
    }
}
