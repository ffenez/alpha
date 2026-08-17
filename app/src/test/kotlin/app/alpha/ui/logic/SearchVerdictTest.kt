package app.alpha.ui.logic

import app.alpha.analysis.CountWindow
import app.alpha.analysis.RateComparison
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wording is part of the science here (redesign §3, §4, §12): the screen may
 * describe counting, and nothing else.
 */
class SearchVerdictTest {

    private fun window(rate: Double, seconds: Int): CountWindow {
        val times = LongArray(seconds) { it * 1_000L }
        val rates = DoubleArray(seconds) { i -> rate + if (i % 2 == 0) 1.0 else -1.0 }
        return CountWindow.reconstruct(times, rates)
    }

    private val background = window(25.0, 45)

    private fun record() = BackgroundRecord(
        window = background,
        atMillis = 0L,
        targetSamples = 45,
        profileId = 1L,
        profileName = "Дом",
        deviceSerial = "RC-110-TEST",
    )

    private fun comparison(rate: Double, seconds: Int = 3) =
        RateComparison.compare(window(rate, seconds), background)

    // Слова, а не подстроки: «нормальное приближение» и «нормальное
    // распределение» — статистические термины, а запрещено утверждение о
    // норме и безопасности (та же поправка, что в WhyReportTest).
    private val forbidden = listOf(
        Regex("""\bбезопасн\w*\b"""),
        Regex("""\bопасн\w*\b"""),
        Regex("""\bдопустим\w*\b"""),
        Regex("""\bнормальн\w*\b(?! (распределени|приближени))"""),
        Regex("""\bнорма\b"""),
    )

    @Test
    fun `no wording on this screen may speak about safety`() {
        val texts = ArrayList<String>()
        for (level in SearchLevel.entries) {
            for (direction in SearchDirection.entries) {
                texts += SearchVerdict.headline(level, direction, hasBackground = true)
                texts += SearchVerdict.headline(level, direction, hasBackground = false)
                SearchVerdict.directionLabel(direction)?.let { texts += it }
            }
            texts += SearchVerdict.explanation(level, comparison(90.0))
            texts += SearchVerdict.explanation(level, null)
        }
        texts += SearchVerdict.whyLines(
            SearchWhyInput(
                cps = 90f,
                background = record(),
                comparison = comparison(90.0),
                heldMillis = 5_000L,
                streamFresh = true,
            ),
        ).flatMap { listOfNotNull(it.label, it.value, it.note) }

        for (text in texts) {
            val lower = text.lowercase()
            for (word in forbidden) {
                assertTrue(!word.containsMatchIn(lower), "«$word» in: $text")
            }
        }
    }

    @Test
    fun `an unconfirmed difference is never called a rise or a fall`() {
        val text = SearchVerdict.headline(
            SearchLevel.POSSIBLE_CHANGE,
            SearchDirection.RISING,
            hasBackground = true,
        ) + " " + SearchVerdict.explanation(SearchLevel.POSSIBLE_CHANGE, comparison(40.0))
        assertTrue(text.contains("недостаточно данных"), text)
        assertTrue(!text.contains("превышение"), text)
    }

    @Test
    fun `no excess found is never stated as equality with the background`() {
        val text = SearchVerdict.headline(
            SearchLevel.BACKGROUND,
            SearchDirection.STEADY,
            hasBackground = true,
        )
        // Непринятие различия не доказывает равенство: экран говорит о том,
        // что проверено, а не о том, что «уровень такой же».
        assertEquals("Превышение над фоном не обнаружено", text)
        assertTrue(!text.contains("На уровне"), text)
    }

    @Test
    fun `a confirmed excess says what it is a statement about`() {
        val text = SearchVerdict.explanation(SearchLevel.CONFIRMED_EXCESS, comparison(90.0))
        assertTrue(text.contains("счёт"), text)
        assertTrue(text.contains("не о дозе"), text)
    }

    @Test
    fun `every ratio names its denominator and carries its interval`() {
        val phrase = assertNotNull(SearchVerdict.ratioPhrase(comparison(90.0)))
        assertTrue(phrase.contains("к записанному фону"), phrase)
        assertTrue(phrase.contains("95 % интервал"), phrase)
    }

    @Test
    fun `no percentage exists without something to divide by`() {
        assertNull(SearchVerdict.deltaPercent(null))
        val same = assertNotNull(SearchVerdict.deltaPercent(comparison(25.0)))
        assertTrue(same in -2..2, "$same")
        val plus = assertNotNull(SearchVerdict.deltaPercent(comparison(50.0)))
        assertTrue(plus in 95..105, "$plus")
    }

    @Test
    fun `the research layer shows both exposures, both counts and the criterion`() {
        val lines = SearchVerdict.whyLines(
            SearchWhyInput(
                cps = 90f,
                background = record(),
                comparison = comparison(90.0),
                heldMillis = 6_000L,
                streamFresh = true,
            ),
        )
        val labels = lines.map { it.label }
        // «окно решения» — термин алгоритма; он назван один раз в справке
        // экрана, а строка отчёта называет то же человеческими словами (§3).
        assertTrue(labels.contains("Время подтверждения"), "$labels")
        assertTrue(labels.contains("Окно фона"), "$labels")
        assertTrue(labels.contains("Критерий"), "$labels")
        assertTrue(labels.contains("Значимость"), "$labels")
        assertTrue(labels.contains("Разброс показаний"), "$labels")
        assertTrue(labels.contains("Поток данных"), "$labels")

        val criterion = lines.single { it.label == "Критерий" }
        assertTrue(criterion.note!!.contains("Przyborowski"), criterion.note!!)

        // Spectral shape is honestly absent, not quietly implied.
        val spectral = lines.single { it.label == "Спектральная форма" }
        assertEquals("не оценивается", spectral.value)
    }

    @Test
    fun `without a comparison the sheet says why, instead of showing empty numbers`() {
        val lines = SearchVerdict.whyLines(
            SearchWhyInput(
                cps = 30f,
                background = null,
                comparison = null,
                heldMillis = null,
                streamFresh = false,
            ),
        )
        val comparisonLine = lines.single { it.label == "Сравнение" }
        assertEquals("не выполнялось", comparisonLine.value)
        assertTrue(comparisonLine.note!!.contains("нет записанного фона"), comparisonLine.note!!)
    }

    @Test
    fun `short excursions are reported as markers, not as finds`() {
        assertNull(SearchVerdict.spikeLine(emptyList()))
        val line = assertNotNull(
            SearchVerdict.spikeLine(
                listOf(
                    SpikeMarker(1_000L, 3_000L, peakRatio = 1.8),
                    SpikeMarker(9_000L, 11_000L, peakRatio = 4.2),
                ),
            ),
        )
        assertTrue(line.contains("2"), line)
        assertTrue(line.contains("4,2"), line)
        assertTrue(!line.lowercase().contains("найден"), line)
    }
}
