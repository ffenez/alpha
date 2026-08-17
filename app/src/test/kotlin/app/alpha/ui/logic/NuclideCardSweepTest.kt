package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.GammaLineLibrary
import app.alpha.analysis.NuclideInfoLibrary
import app.alpha.analysis.Peak
import app.alpha.analysis.evidence.EvidenceClass
import app.alpha.ui.text.NuclideEn
import app.alpha.ui.text.NuclideRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Один прогон по ВСЕЙ библиотеке нуклидов: каждый нуклид, каждая его линия.
 *
 * Проверка одного случая («у K-40 одна линия») ничего не говорит об остальных
 * восьми: у Co-60 линий две, у Tl-208 три, у Pb-212 вторая линия слишком
 * слаба, чтобы о ней судить, — и объяснение «почему одной линии мало» у каждого
 * своё. Здесь перебираются все сочетания и проверяется, что карточка
 * объясняет РОВНО ту причину, которая имеет место, и ни в одном случае не
 * говорит «этого недостаточно» без причины.
 */
class NuclideCardSweepTest {

    private val calibration = EnergyCalibration(0f, 3f, 0f)
    private val flat: List<Int> = List(1024) { 100 }

    private fun peak(energyKeV: Float) = Peak(
        channel = (energyKeV / 3f).toInt(),
        energyKeV = energyKeV,
        netCounts = 3_000f,
        significance = 10f,
    )

    private fun cardFor(symbol: String, matched: Float) = NuclideCard.build(
        nuclide = NuclideInfoLibrary.of(symbol)!!,
        check = PeakEvidenceBridge
            .analyse(listOf(peak(matched)), flat, calibration, 0.08f)
            .checks[symbol],
    )

    @Test
    fun `две библиотеки линий описывают одни и те же нуклиды`() {
        // Карточка считает линии по результату движка, а рисует список из
        // своей библиотеки: разойдись они — «одна линия» появилось бы у
        // нуклида, у которого их три.
        val engine = GammaLineLibrary.LINES.groupBy { it.isotope }
        for (nuclide in NuclideInfoLibrary.all()) {
            val engineLines = engine[nuclide.symbol] ?: emptyList()
            assertEquals(
                nuclide.lines.map { line -> line.energyKeV }.sorted(),
                engineLines.map { line -> line.energyKeV }.sorted(),
                nuclide.symbol,
            )
        }
    }

    @Test
    fun `однолинейный нуклид объясняет, что второй линии у него нет`() {
        val single = NuclideInfoLibrary.all().filter { it.lines.size == 1 }
        // Нуклиды, у которых прибор видит ровно одну линию. Появится новый —
        // тест заставит проверить и его формулировку.
        assertEquals(
            listOf(
                "K-40", "Cs-137", "Am-241", "Tc-99m", "Na-22", "Bi-212", "Ra-226",
            ),
            single.map { it.symbol },
        )
        for (nuclide in single) {
            val card = cardFor(nuclide.symbol, nuclide.lines.first().energyKeV)
            // Исключения по физике, а не по коду: единственная линия нуклида
            // лежит под чужой и этим прибором не отделяется. Ra-226 186,2 кэВ
            // против U-235 185,7 кэВ; Tc-99m 140,5 кэВ против Co-57 136,5 и
            // 122,1 кэВ — при FWHM около 25 кэВ на этой энергии всё это один
            // бугор.
            if (nuclide.symbol in listOf("Ra-226", "Tc-99m")) {
                assertEquals(NuclideCardStatus.AMBIGUOUS, card.status.status, nuclide.symbol)
                continue
            }
            assertEquals(NuclideCardStatus.POSSIBLE_MATCH, card.status.status, nuclide.symbol)
            assertEquals(
                NuclideRu.matchedOfChecked(1, 1) + " " + NuclideRu.singleLineNuclide(
                    nuclide.symbol,
                ),
                card.status.detail,
                nuclide.symbol,
            )
        }
    }

    @Test
    fun `у многолинейного нуклида «одной линии мало» не объясняется единственной линией`() {
        for (nuclide in NuclideInfoLibrary.all().filter { it.lines.size > 1 }) {
            for (line in nuclide.lines) {
                val card = cardFor(nuclide.symbol, line.energyKeV)
                assertTrue(
                    !card.status.detail.contains(NuclideRu.singleLineNuclide(nuclide.symbol)),
                    "${nuclide.symbol} @ ${line.energyKeV}: ${card.status.detail}",
                )
            }
        }
    }

    @Test
    fun `каждое слабое совпадение называет свою причину`() {
        // «Этого недостаточно для подтверждения» без причины — тупик: человек
        // не может понять, чего именно не хватило. Слабый вывод обязан
        // назвать, чем именно проверка ограничена.
        for (nuclide in NuclideInfoLibrary.all()) {
            for (line in nuclide.lines) {
                val check = PeakEvidenceBridge
                    .analyse(listOf(peak(line.energyKeV)), flat, calibration, 0.08f)
                    .checks[nuclide.symbol] ?: continue
                if (check.classification != EvidenceClass.WEAK) continue
                val detail = NuclideCard.build(NuclideInfoLibrary.of(nuclide.symbol)!!, check)
                    .status.detail
                val explained = detail.contains(NuclideRu.singleLineNuclide(nuclide.symbol)) ||
                    detail.contains(NuclideRu.onlyOneLineCheckable(nuclide.symbol))
                assertTrue(explained, "${nuclide.symbol} @ ${line.energyKeV}: $detail")
            }
        }
    }

    @Test
    fun `ни один вывод по всей библиотеке не произносит находку`() {
        val forbidden = listOf("обнаруж", "выявлен", "detected", "identified")
        for (nuclide in NuclideInfoLibrary.all()) {
            for (line in nuclide.lines) {
                val ru = cardFor(nuclide.symbol, line.energyKeV).status
                val en = NuclideCard.build(
                    nuclide = NuclideInfoLibrary.of(nuclide.symbol, NuclideEn)!!,
                    check = PeakEvidenceBridge
                        .analyse(listOf(peak(line.energyKeV)), flat, calibration, 0.08f)
                        .checks[nuclide.symbol],
                    s = NuclideEn,
                ).status
                for (word in forbidden) {
                    assertTrue(
                        !ru.detail.lowercase().contains(word) &&
                            !en.detail.lowercase().contains(word),
                        "«$word»: ${nuclide.symbol} @ ${line.energyKeV}",
                    )
                }
            }
        }
    }
}
