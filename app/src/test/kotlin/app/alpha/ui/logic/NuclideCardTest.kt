package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.GammaLineLibrary
import app.alpha.analysis.NuclideInfoLibrary
import app.alpha.analysis.Peak
import app.alpha.analysis.evidence.DataSource
import app.alpha.analysis.evidence.EvidenceClass
import app.alpha.analysis.evidence.LineObservability
import app.alpha.ui.text.NuclideCatalogue
import app.alpha.ui.text.NuclideEn
import app.alpha.ui.text.NuclideRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Карточка нуклида показывает РЕЗУЛЬТАТ разбора движка, а не своё мнение о
 * нём. Тесты держат ровно это: статус берётся из классификации движка через
 * мост, счёт линий — у него же, измеренная энергия появляется только тогда,
 * когда движок её знает, и ни на одном языке не произносится «обнаружен».
 */
class NuclideCardTest {

    private fun peak(energyKeV: Float, net: Float = 3_000f) = Peak(
        channel = (energyKeV / 3f).toInt(),
        energyKeV = energyKeV,
        netCounts = net,
        significance = 10f,
    )

    /** Линейная шкала 3 кэВ/канал — как у прибора с 1024 каналами. */
    private val calibration = EnergyCalibration(0f, 3f, 0f)

    /** Ровный континуум, чтобы движок мог спросить «а где ожидаемая линия?». */
    private val flatCounts: List<Int> = List(1024) { 100 }

    /** Проверка кандидата — ТЕМ ЖЕ мостом, что наполняет таблицу пиков. */
    private fun check(
        isotope: String,
        peaks: List<Peak>,
        counts: List<Int> = emptyList(),
    ): NuclideCheck? =
        PeakEvidenceBridge.analyse(peaks, counts, calibration, 0.08f).checks[isotope]

    private fun card(
        isotope: String,
        peaks: List<Peak>,
        counts: List<Int> = emptyList(),
    ) = NuclideCard.build(
        nuclide = assertNotNull(NuclideInfoLibrary.of(isotope)),
        check = check(isotope, peaks, counts),
    )

    @Test
    fun `a weak single-line match stays a possible match with the count named`() {
        // Am-241: единственная линия совпала — WEAK, без усиления.
        val model = card("Am-241", listOf(peak(59.5f)))
        assertEquals(NuclideCardStatus.POSSIBLE_MATCH, model.status.status)
        assertEquals(NuclideRu.statusPossibleMatch, model.status.headline)
        // «Совпала 1 из 1 … этого недостаточно» читалось как противоречие:
        // совпало всё, что было. Причина не в счёте, а в устройстве нуклида —
        // второй линии у него нет, и проверить совпадение нечем.
        assertTrue(
            model.status.detail.startsWith("Совпала 1 из 1 проверяемых линий."),
            model.status.detail,
        )
        assertTrue(model.status.detail.contains("одна гамма-линия"), model.status.detail)
        assertTrue(
            !model.status.detail.contains(NuclideRu.notEnoughToConfirm),
            model.status.detail,
        )
    }

    @Test
    fun `an unresolvable line names the group instead of a winner`() {
        // I-131 364,5 кэВ и Pb-214 351,9 кэВ прибор не разделяет: карточка
        // говорит это, а не объявляет победителя.
        val model = card("Pb-214", listOf(peak(351.9f)))
        assertEquals(NuclideCardStatus.AMBIGUOUS, model.status.status)
        assertEquals(NuclideCardTone.UNCERTAIN, model.status.tone)
        assertEquals(NuclideRu.statusAmbiguous, model.status.headline)
        assertTrue(model.status.detail.contains("I-131"), model.status.detail)
        assertTrue(model.status.detail.contains("не разделяет"), model.status.detail)
    }

    @Test
    fun `a missing expected strong line names both lines and refuses to confirm`() {
        // Bi-214: совпала 1120,3 кэВ, а более яркая 609,3 кэВ на ровном
        // континууме обязана была быть видна — и не выделена.
        val model = card("Bi-214", listOf(peak(1120.3f)), flatCounts)
        assertEquals(NuclideCardStatus.NOT_CONFIRMED, model.status.status)
        assertEquals(NuclideCardTone.UNCERTAIN, model.status.tone)
        assertTrue(model.status.detail.contains("1120"), model.status.detail)
        assertTrue(model.status.detail.contains("609"), model.status.detail)
    }

    @Test
    fun `the card prints the engine verdict instead of matching lines again`() {
        // Результат СОБРАН ВРУЧНУЮ и противоречит тому, что посчитал бы сам
        // движок: сильнейшая линия помечена «обязана была быть видна» и не
        // найдена, а классификация при этом WEAK. Карточка обязана напечатать
        // именно это — своего пересчёта у неё нет.
        val info = assertNotNull(NuclideInfoLibrary.of("Bi-214"))
        val strongest = info.lines.maxByOrNull { it.intensityPercent }!!
        val weakest = info.lines.minByOrNull { it.intensityPercent }!!
        val handmade = NuclideCheck(
            nuclide = "Bi-214",
            classification = EvidenceClass.WEAK,
            ambiguousWith = emptyList(),
            lines = listOf(
                CheckedLine(
                    energyKeV = strongest.energyKeV,
                    intensityPercent = strongest.intensityPercent,
                    measuredKeV = null,
                    observability = LineObservability.EXPECTED_OBSERVABLE,
                ),
                CheckedLine(
                    energyKeV = weakest.energyKeV,
                    intensityPercent = weakest.intensityPercent,
                    measuredKeV = weakest.energyKeV - 11f,
                    observability = LineObservability.OBSERVED,
                ),
            ),
            ratios = emptyList(),
        )
        val model = NuclideCard.build(info, handmade)
        // Ни «не подтверждается» (что дал бы собственный пересчёт), ни своей
        // сортировки правды: статус и вердикты — из переданного результата.
        assertEquals(NuclideCardStatus.POSSIBLE_MATCH, model.status.status)
        assertEquals(
            "Совпала 1 из 2 проверяемых линий. Этого недостаточно для подтверждения.",
            model.status.detail,
        )
        val rows = model.lineCheck
        assertEquals(NuclideLineVerdict.NOT_FOUND, rows.first().verdict)
        assertEquals(NuclideLineVerdict.MATCHED, rows.last().verdict)
    }

    @Test
    fun `lines are sorted by yield and carry their energy for the spectrum`() {
        // Bi-214: сильная линия первой — именно её отсутствие что-то значит.
        val model = card("Bi-214", listOf(peak(609.3f)))
        assertEquals(
            listOf(609.3f, 1764.5f, 1120.3f, 1238.1f, 2204.2f, 1377.7f, 934.1f),
            model.lineCheck.map { it.energyKeV },
        )
    }

    @Test
    fun `every line row offers to show itself on the spectrum, in both languages`() {
        // Строка таблицы нажимается, и действие названо словами: визуально она
        // остаётся строкой, а экранный диктор обязан сказать, что произойдёт.
        val model = card("Bi-214", listOf(peak(609.3f)))
        assertEquals(
            listOf("Показать 609,3 кэВ на спектре", "Показать 1764,5 кэВ на спектре"),
            model.lineCheck.take(2).map { it.actionLabel },
        )
        assertTrue(model.lineTapHint.isNotBlank())
        val en = NuclideCard.build(
            nuclide = assertNotNull(NuclideInfoLibrary.of("Bi-214", NuclideEn)),
            check = check("Bi-214", listOf(peak(609.3f))),
            s = NuclideEn,
        )
        assertEquals("Show 609,3 keV on the spectrum", en.lineCheck.first().actionLabel)
        // Подсказка про нажатие — часть карточки, а не подпись, забытая в UI.
        assertTrue(en.lineTapHint != model.lineTapHint)
    }

    @Test
    fun `the measured energy and delta appear only where the matcher knows a peak`() {
        val model = card("Bi-214", listOf(peak(598.3f)))
        val matched = model.lineCheck.first { it.verdict == NuclideLineVerdict.MATCHED }
        assertEquals(598.3f, matched.measuredKeV)
        assertTrue(matched.measuredText!!.contains("−11,0"), matched.measuredText!!)
        // У ненайденных линий измеренной энергии нет — и выдумывать её нечем.
        model.lineCheck.filter { it.verdict != NuclideLineVerdict.MATCHED }.forEach {
            assertNull(it.measuredKeV)
            assertNull(it.measuredText)
        }
    }

    @Test
    fun `without a spectrum the card says nothing was checked`() {
        val model = NuclideCard.build(assertNotNull(NuclideInfoLibrary.of("Cs-137")), null)
        assertEquals(NuclideCardStatus.NOT_EVALUATED, model.status.status)
        // «Не оценивалось» ≠ «не найдено»: вердикт у строк отдельный.
        assertTrue(model.lineCheck.all { it.verdict == NuclideLineVerdict.NOT_EVALUATED })
    }

    @Test
    fun `no card text claims a detection in either language`() {
        val detection = Regex("""обнаружен|выявлен|найден нуклид|\bdetected\b|\bidentified\b""")
        // Каждый статус движка проверяется на каждом языке: и WEAK Cs-137, и
        // AMBIGUOUS Pb-214, и CONTRADICTED Bi-214, и карточка без спектра.
        val checks = mapOf(
            "Cs-137" to check("Cs-137", listOf(peak(661.7f))),
            "Pb-214" to check("Pb-214", listOf(peak(351.9f))),
            "Bi-214" to check("Bi-214", listOf(peak(1120.3f)), flatCounts),
        )
        for (catalogue in NuclideCatalogue.all) {
            for (symbol in NuclideInfoLibrary.ALL.map { it.symbol }) {
                val nuclide = assertNotNull(NuclideInfoLibrary.of(symbol, catalogue))
                for (text in NuclideCard.build(nuclide, checks[symbol], catalogue).allTexts()) {
                    assertTrue(!detection.containsMatchIn(text.lowercase()), "находка в: $text")
                }
            }
        }
    }

    @Test
    fun `the caution ceiling and its translation stay in place`() {
        assertEquals("ВОЗМОЖНОЕ СОВПАДЕНИЕ", NuclideRu.statusPossibleMatch)
        assertEquals("POSSIBLE MATCH", NuclideEn.statusPossibleMatch)
        val en = NuclideCard.build(
            nuclide = assertNotNull(NuclideInfoLibrary.of("Am-241", NuclideEn)),
            check = check("Am-241", listOf(peak(59.5f))),
            s = NuclideEn,
        )
        assertEquals("POSSIBLE MATCH", en.status.headline)
        assertTrue(en.status.detail.contains("1 of 1"), en.status.detail)
    }

    /**
     * Числа справочной карточки называют, откуда они, — но ОДНОЙ строкой.
     * Построчный раздел «Источник и неопределённости» убран: он повторял для
     * каждой линии один и тот же источник и один и тот же отказ назвать
     * неопределённость, которой в выборке ENSDF нет.
     */
    @Test
    fun `the card names where the line data came from, in one line`() {
        assertTrue(GammaLineLibrary.LINES.all { it.source == DataSource.ENSDF })

        val model = card("Bi-214", listOf(peak(609.3f)))
        assertTrue(model.provenance.summary.contains("ENSDF"), model.provenance.summary)
        assertTrue(
            model.allTexts().none { it.contains("неопределённост", ignoreCase = true) },
            "неопределённости сняты с карточки целиком",
        )
    }

    @Test
    fun `what would strengthen the hypothesis is computed from the library`() {
        val model = card("Bi-214", listOf(peak(609.3f)))
        val bullets = model.strengthen
        // Названы именно ненайденные линии этого нуклида и сосед по ряду.
        assertTrue(bullets.any { it.contains("1764,5") && it.contains("1120,3") }, "$bullets")
        assertTrue(bullets.any { it.contains("Pb-214") }, "$bullets")
        // Период полураспада сам по себе аргументом не объявляется.
        assertTrue(bullets.none { it.contains("полураспад") }, "$bullets")
    }

    @Test
    fun `the method caveat is stated once, at the bottom`() {
        val model = card("Cs-137", listOf(peak(661.7f)))
        val texts = model.allTexts()
        assertEquals(1, texts.count { it == model.limitation })
        assertTrue(model.limitation.contains("калибровк"), model.limitation)
    }
}
