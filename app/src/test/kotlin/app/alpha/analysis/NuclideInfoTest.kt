package app.alpha.analysis

import app.alpha.analysis.evidence.DataSource
import app.alpha.ui.logic.NuclideCard
import app.alpha.ui.text.NuclideRu
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The reference table is bundled data, so nothing checks it at runtime —
 * these tests are the check: coverage of everything the matcher can emit, and
 * agreement with the line library the matcher actually uses.
 */
class NuclideInfoTest {

    private val libraryIsotopes = GammaLineLibrary.LINES.map { it.isotope }.distinct()

    @Test
    fun `every candidate the matcher can emit has a reference card`() {
        val missing = libraryIsotopes.filter { NuclideInfoLibrary.of(it) == null }
        assertTrue(missing.isEmpty(), "нет справки о: $missing")
    }

    @Test
    fun `the reference table carries no nuclide the matcher can never name`() {
        val extra = NuclideInfoLibrary.ALL.map { it.symbol } - libraryIsotopes.toSet()
        assertTrue(extra.isEmpty(), "справка без линий в библиотеке: $extra")
    }

    @Test
    fun `every library line appears in its nuclide's card with the same energy`() {
        GammaLineLibrary.LINES.forEach { line ->
            val nuclide = assertNotNull(NuclideInfoLibrary.of(line.isotope))
            val match = nuclide.lines.firstOrNull { abs(it.energyKeV - line.energyKeV) <= 0.1f }
            assertNotNull(match, "${line.isotope} ${line.energyKeV} кэВ отсутствует в справке")
        }
    }

    @Test
    fun `origin and chain agree with the line library`() {
        GammaLineLibrary.LINES.forEach { line ->
            val nuclide = assertNotNull(NuclideInfoLibrary.of(line.isotope))
            val natural = nuclide.origin == NuclideOrigin.NATURAL
            assertEquals(line.natural, natural, "${line.isotope}: природность разошлась")
            assertEquals(line.chain, nuclide.chain, "${line.isotope}: ряд разошёлся")
        }
    }

    @Test
    fun `every card is complete and its lines are physically sane`() {
        NuclideInfoLibrary.ALL.forEach { nuclide ->
            assertTrue(nuclide.name.isNotBlank(), nuclide.symbol)
            assertTrue(nuclide.halfLife.isNotBlank(), nuclide.symbol)
            assertTrue(nuclide.decay.isNotBlank(), nuclide.symbol)
            assertTrue(nuclide.everyday.isNotBlank(), nuclide.symbol)
            assertTrue(nuclide.confirmation.isNotBlank(), nuclide.symbol)
            assertTrue(nuclide.lines.isNotEmpty(), nuclide.symbol)
            nuclide.lines.forEach { line ->
                assertTrue(line.energyKeV > 0f, "${nuclide.symbol}: энергия ≤ 0")
                assertTrue(
                    line.intensityPercent > 0f && line.intensityPercent <= 100f,
                    "${nuclide.symbol}: выход ${line.intensityPercent} % вне 0..100",
                )
            }
            assertEquals(
                nuclide.lines.sortedByDescending { it.intensityPercent },
                nuclide.lines,
                "${nuclide.symbol}: линии не по убыванию выхода",
            )
        }
    }

    @Test
    fun `a chain daughter always names its parent, a standalone nuclide never does`() {
        NuclideInfoLibrary.ALL.forEach { nuclide ->
            if (nuclide.origin == NuclideOrigin.ARTIFICIAL) {
                assertEquals(null, nuclide.chain, nuclide.symbol)
            }
        }
        assertEquals("Ra-226", assertNotNull(NuclideInfoLibrary.of("Bi-214")).chain)
        assertEquals("Th-232", assertNotNull(NuclideInfoLibrary.of("Tl-208")).chain)
        assertEquals(null, assertNotNull(NuclideInfoLibrary.of("K-40")).chain)
    }

    @Test
    fun `the card never claims a detection and never gives safety advice`() {
        // Spec §12 vocabulary and §23 «никакой аномалии как опасности».
        val forbidden = listOf(
            "обнаружен", "выявлен", "найден нуклид", "опасн", "вред",
            "мЗв", "предельно допуст", "норм", "безопасн", "врач",
        )
        val texts = NuclideInfoLibrary.ALL.flatMap {
            listOf(it.decay, it.everyday, it.confirmation, it.halfLife)
        }

        texts.forEach { text ->
            forbidden.forEach { word ->
                assertTrue(
                    !text.lowercase().contains(word),
                    "запрещённая формулировка «$word» в: $text",
                )
            }
        }

    }

    @Test
    fun `the standing texts of the card carry the ceiling, not a finding`() {
        // Потолок формулировок держит статусный блок: «возможное совпадение»
        // и ни одного слова о находке.
        assertEquals("ВОЗМОЖНОЕ СОВПАДЕНИЕ", NuclideRu.statusPossibleMatch)
        assertTrue(!NuclideRu.statusPossibleMatch.lowercase().contains("обнаруж"))
    }

    @Test
    fun `the card states what one spectrum on this device cannot do`() {
        assertTrue(NuclideRu.limits.contains("не идентифицирует нуклид"), NuclideRu.limits)
        assertTrue(NuclideRu.limits.contains("калибровк"), NuclideRu.limits)
    }

    @Test
    fun `the numbers are attributed to their evaluated source`() {
        // Источник хранится в самих линиях, а не в одной подписи внизу карточки.
        assertTrue(NuclideInfoLibrary.ALL.all { nuclide -> nuclide.lines.all { it.source == DataSource.ENSDF } })
        assertTrue(NuclideRu.sourceEnsdf.contains("IAEA"), NuclideRu.sourceEnsdf)
        assertTrue(NuclideRu.sourceEnsdf.contains("NuDat"), NuclideRu.sourceEnsdf)
    }

    @Test
    fun `formatting keeps the scientific-terminal decimal comma`() {
        assertEquals("609,3 кэВ · 45,5 % на распад", NuclideCard.lineText(609.3f, 45.5f))
        assertEquals("Cs-137 · цезий-137", NuclideCard.title(NuclideInfoLibrary.of("Cs-137")!!))
        assertEquals("природный · ряд Th-232", NuclideCard.originLine(NuclideInfoLibrary.of("Tl-208")!!))
        assertEquals("искусственный", NuclideCard.originLine(NuclideInfoLibrary.of("Co-60")!!))
    }
}
