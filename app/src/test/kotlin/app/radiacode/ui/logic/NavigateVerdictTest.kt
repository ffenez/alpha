package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
import app.radiacode.analysis.RateComparison
import app.radiacode.ui.text.SearchEn
import app.radiacode.ui.text.SearchRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Что «Наведение» имеет право написать после переезда в один модуль.
 *
 * Экран собран так, что число одно, поэтому проверяется главное: процент
 * печатается ТОЛЬКО когда тест разрешил различие, прочерк всегда объяснён,
 * отношение никогда не остаётся без знаменателя, а находки источника нет ни на
 * одном языке.
 */
class NavigateVerdictTest {

    private fun window(rate: Double, seconds: Int): CountWindow {
        val times = LongArray(seconds) { it * 1_000L }
        val rates = DoubleArray(seconds) { i -> rate + if (i % 2 == 0) 1.0 else -1.0 }
        return CountWindow.reconstruct(times, rates)
    }

    private val local = window(25.0, 12)

    private fun comparison(rate: Double, seconds: Int = 3) =
        RateComparison.compare(window(rate, seconds), local)

    @Test
    fun `the big number is a percentage only once the test resolved a difference`() {
        assertEquals("—", NavigateVerdict.deltaHeadline(ReferenceDelta.NoReference))
        assertEquals("—", NavigateVerdict.deltaHeadline(ReferenceDelta.Collecting))
        assertEquals("—", NavigateVerdict.deltaHeadline(ReferenceDelta.Unresolved(0.92, 1.31)))
        assertEquals(
            "+31 %",
            NavigateVerdict.deltaHeadline(ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55)),
        )
        assertEquals(
            "−18 %",
            NavigateVerdict.deltaHeadline(ReferenceDelta.Resolved(-18, 0.82, 0.70, 0.94)),
        )
    }

    /** Прочерк — ответ теста, поэтому он обязан назвать причину числами. */
    @Test
    fun `the dash always names its reason`() {
        val unresolved = NavigateVerdict.deltaCaption(ReferenceDelta.Unresolved(0.92, 1.31))
        assertTrue(unresolved.contains("0,92") && unresolved.contains("1,31"), unresolved)
        assertTrue(unresolved.contains("1"), unresolved)
        assertTrue(
            NavigateVerdict.deltaCaption(ReferenceDelta.NoReference).isNotBlank(),
        )
        assertTrue(
            NavigateVerdict.deltaCaption(ReferenceDelta.Collecting).contains("точк"),
        )
        // Направление и подпись под числом не повторяют друг друга.
        for (delta in listOf(
            ReferenceDelta.NoReference,
            ReferenceDelta.Collecting,
            ReferenceDelta.Unresolved(0.92, 1.31),
            ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55),
        )) {
            assertTrue(
                NavigateVerdict.referenceDirection(delta) != NavigateVerdict.deltaCaption(delta),
                "$delta",
            )
        }
        // Знаменатель модуля — точка отсчёта, а не локальный уровень карточки.
        assertTrue(
            NavigateVerdict.referenceDirection(ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55))
                .contains("выше точки отсчёта"),
        )
        assertTrue(
            NavigateVerdict.referenceDirection(ReferenceDelta.Resolved(-18, 0.82, 0.7, 0.94))
                .contains("ниже точки отсчёта"),
        )
        assertTrue(
            NavigateVerdict.deltaCaption(ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55))
                .contains("×1,31"),
        )
    }

    /** Отношение и его знаменатель живут в ОДНОЙ строке — иначе это ×чего-то. */
    @Test
    fun `the ratio of the main card carries its denominator and its interval`() {
        val ratio = assertNotNull(NavigateVerdict.localRatio(comparison(25.0)))
        assertTrue(ratio.startsWith("×"), ratio)
        assertTrue(ratio.contains("локальному уровню"), ratio)
        val interval = assertNotNull(NavigateVerdict.localInterval(comparison(25.0)))
        assertTrue(interval.contains("95 %"), interval)
        assertTrue(interval.contains("–"), interval)
        // Слово «фон» на этом экране занято обычным фоном профиля.
        assertTrue(!ratio.contains("фон"), ratio)
        assertTrue(!SearchEn.navRatioToLocal("1,00").contains("background"))
    }

    @Test
    fun `without a test there is no ratio to show`() {
        assertNull(NavigateVerdict.localRatio(null))
        assertNull(NavigateVerdict.localInterval(null))
    }

    /** Максимум без возраста ничего не говорит о том, куда идти. */
    @Test
    fun `the peak caption appears only with a peak and carries its age`() {
        val bare = NavigateState()
        assertNull(NavigateVerdict.peakLine(bare, nowMillis = 10_000L))
        val held = bare.copy(peak = NavigatePeak(ratePerSecond = 47.6, atMillis = 2_000L))
        val line = assertNotNull(NavigateVerdict.peakLine(held, nowMillis = 20_000L))
        assertTrue(line.contains("47,6"), line)
        assertTrue(line.contains("18"), line)
    }

    @Test
    fun `the reference control shows a value and a moment, or nothing`() {
        assertNull(NavigateVerdict.referenceLine(null, "11:44"))
        val reference = NavigateReference(window = window(26.0, 4), atMillis = 0L)
        assertNull(NavigateVerdict.referenceLine(reference, null))
        val line = assertNotNull(NavigateVerdict.referenceLine(reference, "11:44"))
        assertTrue(line.contains("11:44"), line)
        assertTrue(line.contains("с⁻¹"), line)
    }

    /**
     * Режим подтверждает изменение скорости счёта. Ни на одном языке он не
     * находит источник — и в английском «detected» тоже нет.
     */
    @Test
    fun `nothing here claims a source was located`() {
        val deltas = listOf(
            ReferenceDelta.NoReference,
            ReferenceDelta.Collecting,
            ReferenceDelta.Unresolved(0.92, 1.31),
            ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55),
        )
        val texts = ArrayList<String>()
        for (catalogue in listOf(SearchRu, SearchEn)) {
            for (delta in deltas) {
                texts += NavigateVerdict.deltaHeadline(delta, catalogue)
                texts += NavigateVerdict.deltaCaption(delta, catalogue)
                texts += NavigateVerdict.referenceDirection(delta, catalogue)
            }
            for (trend in NavigateTrend.entries) {
                texts += NavigateVerdict.trendLabel(trend, catalogue)
            }
            texts += NavigateVerdict.localRatio(comparison(40.0), catalogue).orEmpty()
        }
        val forbidden = listOf(
            Regex("""\bисточник\w*\b"""),
            Regex("""\bобнаруж\w*\b"""),
            Regex("""\bнайден\w*\b"""),
            Regex("""\bsource\b"""),
            Regex("""\bdetect\w*\b"""),
            Regex("""\bfound\b"""),
        )
        for (text in texts) {
            for (word in forbidden) {
                assertTrue(!word.containsMatchIn(text.lowercase()), "«$word»: $text")
            }
        }
    }
}
