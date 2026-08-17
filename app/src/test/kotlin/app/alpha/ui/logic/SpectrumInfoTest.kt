package app.alpha.ui.logic

import app.alpha.ui.text.SpectrumEn
import app.alpha.ui.text.SpectrumRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Справка отвечает по вопросам и по глубине: сначала ЧТО это значит, потом
 * ПОЧЕМУ так решено, и только потом КАК посчитано. Порядок — обещание
 * пользователю, поэтому он проверяется, а не поддерживается вниманием.
 */
class SpectrumInfoTest {

    private fun sections(
        calibrationLine: String? = "калибровка: E = 6,9 + 2,34·ch · 1024 канала",
        edgeLine: String? = "у верхней границы шкалы: 8 421 имп.",
    ) = SpectrumInfo.sections(
        s = SpectrumRu,
        calibrationLine = calibrationLine,
        edgeLine = edgeLine,
    )

    @Test
    fun `levels never go backwards`() {
        val levels = sections().map { it.level }
        assertEquals(levels.sortedBy { it.ordinal }, levels)
        assertEquals(SpectrumInfoLevel.WHAT, levels.first())
        assertEquals(SpectrumInfoLevel.HOW, levels.last())
    }

    @Test
    fun `the first answer is what the chart shows`() {
        val first = sections().first()
        assertEquals(SpectrumRu.infoWhatTitle, first.title)
        // Обе оси названы, и выступ назван пиком — этого достаточно, чтобы
        // прочитать картинку, не открывая ничего дальше.
        assertTrue(first.lines.any { it.contains("Горизонтальная ось") })
        assertTrue(first.lines.any { it.contains("Вертикальная ось") })
        assertTrue(first.lines.any { it.contains("пиками") })
    }

    @Test
    fun `the caution about a candidate lives at the first level`() {
        val candidate = sections().first { it.title == SpectrumRu.infoCandidateTitle }
        assertEquals(SpectrumInfoLevel.WHAT, candidate.level)
        // Оговорка — отдельная строка, а не хвост описания.
        assertEquals(2, candidate.lines.size)
        assertTrue(candidate.lines.last().contains("ещё не означает"))
    }

    @Test
    fun `the definition of significance is a third-level answer`() {
        val significance = sections().first { it.title == SpectrumRu.infoSignificanceTitle }
        assertEquals(SpectrumInfoLevel.HOW, significance.level)
        assertTrue(significance.lines.single().contains("нетто-площадь"))
        // На первых двух уровнях этой формулировки нет вовсе.
        val shallow = sections().filter { it.level != SpectrumInfoLevel.HOW }
        assertTrue(shallow.flatMap { it.lines }.none { it.contains("нетто-площадь") })
    }

    @Test
    fun `diagnostics are technical data, not part of the explanation`() {
        val technical = sections().first { it.title == SpectrumRu.infoTechnicalTitle }
        assertEquals(SpectrumInfoLevel.HOW, technical.level)
        assertTrue(technical.lines.any { it.startsWith("калибровка") })
        assertTrue(technical.lines.any { it.contains("у верхней границы шкалы") })
        // Формула калибровки не встречается больше нигде.
        val others = sections().filter { it.title != SpectrumRu.infoTechnicalTitle }
        assertTrue(others.flatMap { it.lines }.none { it.contains("E = ") })
    }

    @Test
    fun `nothing technical is invented when there is nothing to show`() {
        val bare = SpectrumInfo.sections(s = SpectrumRu, calibrationLine = null, edgeLine = null)
        assertTrue(bare.none { it.title == SpectrumRu.infoTechnicalTitle })
    }

    @Test
    fun `the cursor is explained only where it exists`() {
        val tab = SpectrumInfo.sections(s = SpectrumRu, fullscreenEntry = true)
        assertTrue(tab.first().lines.contains(SpectrumRu.infoFullscreen))
        assertTrue(!tab.first().lines.contains(SpectrumRu.infoCursor))
        val full = SpectrumInfo.sections(s = SpectrumRu, cursor = true)
        assertTrue(full.first().lines.contains(SpectrumRu.infoCursor))
    }

    @Test
    fun `both languages answer the same questions`() {
        val ru = SpectrumInfo.sections(s = SpectrumRu)
        val en = SpectrumInfo.sections(s = SpectrumEn)
        assertEquals(ru.map { it.level }, en.map { it.level })
        assertEquals(ru.map { it.lines.size }, en.map { it.lines.size })
    }
}
