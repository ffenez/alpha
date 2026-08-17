package app.alpha.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * История — единственное место приложения, где данные действительно исчезают,
 * и единственное, где число печатается с числительным. Проверяется и то, и
 * другое: отказ от обещаний безопасности и грамматика счёта в каждом языке.
 */
class HistoryStringsTest {

    private val catalogues = HistoryCatalogue.all

    @Test
    fun `nothing in either language promises safety`() {
        val forbidden = listOf(
            Regex("""\bбезопасн\w*\b"""),
            Regex("""\bопасн\w*\b"""),
            Regex("""\bдопустим\w*\b"""),
            Regex("""\bнорма\b"""),
            Regex("""\bnormal\b(?! distribution)"""),
            Regex("""\bsafe\b"""),
            Regex("""\bdangerous\b"""),
            Regex("""\bharmless\b"""),
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                for (word in forbidden) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» in: $text")
                }
            }
        }
    }

    @Test
    fun `deletion is called deletion in both languages`() {
        // «Очистить», «убрать», «clean up» смягчают то, что данные исчезают
        // навсегда. Кнопка называется действием, и действие это удаление.
        val softening = Regex("""очист|убрать|clean|clear|remove|tidy""")
        val deletionTexts = catalogues.flatMap {
            listOf(
                it.delete, it.deleteCount(3), it.deleteSelectedTitle, it.deleteSessionsTitle(3),
                it.deleteSpectraTitle(2), it.samplesGone("41 203"), it.cannotUndo,
                it.markWhatToDelete,
            )
        }
        for (text in deletionTexts) {
            assertTrue(!softening.containsMatchIn(text.lowercase()), "смягчение: $text")
        }
        assertTrue(HistoryEn.cannotUndo.contains("cannot be undone"), HistoryEn.cannotUndo)
        assertTrue(HistoryRu.cannotUndo.contains("нельзя"), HistoryRu.cannotUndo)
    }

    @Test
    fun `each language counts by its own rules`() {
        // Русская тройка и английская пара — разные грамматики; функция
        // числительного принадлежит языку, а не общему коду.
        assertEquals("1 сессия", HistoryRu.sessions(1))
        assertEquals("3 сессии", HistoryRu.sessions(3))
        assertEquals("11 сессий", HistoryRu.sessions(11))
        assertEquals("21 сессия", HistoryRu.sessions(21))
        assertEquals("1 session", HistoryEn.sessions(1))
        assertEquals("3 sessions", HistoryEn.sessions(3))
        assertEquals("1 spectrum", HistoryEn.spectra(1))
        assertEquals("2 spectra", HistoryEn.spectra(2))
    }

    @Test
    fun `the projection states its condition and refuses to be an annual dose`() {
        // Спец §6: формулировка обязана назвать условие и обязана отказаться
        // называть результат годовой эффективной дозой человека.
        assertTrue(HistoryEn.doseProjection("1.4 mSv").startsWith("if the average measured"))
        assertTrue(HistoryEn.doseProjectionCaveat.contains("not a person's annual effective dose"))
        assertTrue(HistoryEn.doseProjectionCaveat.contains("radon"))
        assertTrue(HistoryRu.doseProjectionCaveat.contains("не годовая эффективная доза"))
    }

    /**
     * Короткая оговорка первого уровня (ТЗ §13) — она заменяет перечень, но не
     * отказ: и по-русски, и по-английски она обязана сказать, что это НЕ
     * годовая эффективная доза человека.
     */
    @Test
    fun `the short caveat refuses the annual-dose claim in both languages`() {
        assertTrue(HistoryRu.doseProjectionCaveatShort.contains("не годовая эффективная доза"))
        assertTrue(
            HistoryEn.doseProjectionCaveatShort.contains("not a person's annual effective dose"),
        )
        for (catalogue in catalogues) {
            assertTrue(
                catalogue.doseProjectionCaveatShort.length <
                    catalogue.doseProjectionCaveat.length,
            )
        }
    }

    /**
     * Свёрнутая доза — три числа и одна строка, а не абзац.
     *
     * Легенда полого столбца ушла вместе со справкой карточки: блок сведён к
     * тому, ради чего его открывают. Проверяется, что оставшиеся строки
     * действительно короткие — иначе «ёмко» кончится следующим абзацем.
     */
    @Test
    fun `the collapsed dose lines stay short`() {
        for (catalogue in catalogues) {
            assertTrue(catalogue.measuredFor("15 ч 33 мин").length <= 32)
            assertTrue(catalogue.recordedOfPeriod("15 ч 33 мин").length <= 48)
            assertTrue(catalogue.doseGlance("2,36", "2,36", "2,36").length <= 48)
            assertTrue(catalogue.infoTitle.isNotBlank())
        }
    }

    @Test
    fun `both languages have twelve months and the same number of texts`() {
        for (catalogue in catalogues) assertEquals(12, catalogue.months.size)
        assertEquals(HistoryRu.allTexts().size, HistoryEn.allTexts().size)
        assertEquals(HistoryRu, HistoryCatalogue.of(AppLanguage.RU))
        assertEquals(HistoryEn, HistoryCatalogue.of(AppLanguage.EN))
    }
}
