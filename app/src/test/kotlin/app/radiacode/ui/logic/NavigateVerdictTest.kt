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
 * Что «Наведение» имеет право написать.
 *
 * Разделение, которое проверяется здесь: **на рабочем экране — во сколько раз
 * и в какую сторону, в разборе — насколько этому можно верить**. Крупное число
 * это ОТНОШЕНИЕ к точке отсчёта, и оно показывается даже до того, как тест
 * разрешил различие (строка над ним в этот момент честно говорит, что разница
 * не подтверждена). Процент, наоборот, печатается только после разрешения — и
 * только в разборе.
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

    private fun state(currentRate: Double = 30.0, referenceRate: Double? = 25.0) = NavigateState(
        fast = window(currentRate, 3),
        local = local,
        trendComparison = comparison(currentRate),
        reference = referenceRate?.let {
            NavigateReference(window = window(it, 12), atMillis = 0L)
        },
    )

    /**
     * Крупное число — отношение, и оно не ждёт разрешения различия: человек с
     * прибором в руке смотрит на него, чтобы понять, куда шагнуть, а надёжность
     * этого шага сообщает строка над числом.
     */
    @Test
    fun `the big number is the ratio to the reference point`() {
        val headline = NavigateVerdict.ratioHeadline(state())
        assertTrue(headline.endsWith("×"), headline)
        assertTrue(headline.startsWith("1,2"), headline)
        // Сравнивать не с чем — и числа нет; прочерк здесь ответ, а не пропуск.
        assertEquals("—", NavigateVerdict.ratioHeadline(state(referenceRate = null)))
        assertEquals("—", NavigateVerdict.ratioHeadline(NavigateState()))
    }

    /** Процент — только после разрешённого различия и только в разборе. */
    @Test
    fun `the percentage appears only once the test resolved a difference`() {
        assertNull(NavigateVerdict.percentLabel(ReferenceDelta.NoReference))
        assertNull(NavigateVerdict.percentLabel(ReferenceDelta.Collecting))
        assertNull(NavigateVerdict.percentLabel(ReferenceDelta.Unresolved(0.92, 1.31)))
        assertEquals(
            "+31 %",
            NavigateVerdict.percentLabel(ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55)),
        )
        assertEquals(
            "−18 %",
            NavigateVerdict.percentLabel(ReferenceDelta.Resolved(-18, 0.82, 0.70, 0.94)),
        )
    }

    /** Отношение без знаменателя — отношение к чему-то вообще. */
    @Test
    fun `the caption under the number names the reference by value`() {
        val caption = NavigateVerdict.deltaCaption(state(), ReferenceDelta.Unresolved(0.9, 1.3))
        assertTrue(caption.contains("точк"), caption)
        assertTrue(caption.contains("25"), caption)
        // Направление и подпись под числом не повторяют друг друга.
        for (delta in listOf(
            ReferenceDelta.Collecting,
            ReferenceDelta.Unresolved(0.92, 1.31),
            ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55),
        )) {
            assertTrue(
                NavigateVerdict.referenceDirection(delta) !=
                    NavigateVerdict.deltaCaption(state(), delta),
                "$delta",
            )
        }
        assertTrue(
            NavigateVerdict.referenceDirection(ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55))
                .contains("Выше точки отсчёта"),
        )
        assertTrue(
            NavigateVerdict.referenceDirection(ReferenceDelta.Resolved(-18, 0.82, 0.7, 0.94))
                .contains("Ниже точки отсчёта"),
        )
    }

    /**
     * Пока разница не подтверждена, рабочий экран говорит об этом ФРАЗОЙ.
     * Числа интервала уехали в разбор: «интервал 0,74–1,33 включает 1» было
     * главным сообщением экрана, по которому ходят с прибором.
     */
    @Test
    fun `the unresolved state is a sentence on screen and numbers in the report`() {
        val note = assertNotNull(NavigateVerdict.unresolvedNote(ReferenceDelta.Unresolved(0.9, 1.3)))
        assertTrue(!note.contains("0,9") && !note.contains("1,3"), note)
        assertTrue(!note.contains("интервал"), note)
        assertNull(NavigateVerdict.unresolvedNote(ReferenceDelta.Resolved(31, 1.31, 1.12, 1.55)))
        assertNull(NavigateVerdict.unresolvedNote(ReferenceDelta.Collecting))

        val lines = NavigateVerdict.whyLines(
            state = state().copy(referenceComparison = comparison(30.0)),
            delta = ReferenceDelta.Unresolved(0.92, 1.31),
            cps = 30.0f,
        )
        val labels = lines.map { it.label }
        assertTrue(labels.contains(SearchRu.navWhyRatio), "$labels")
        assertTrue(labels.contains(SearchRu.navWhyInterval), "$labels")
        assertTrue(labels.contains(SearchRu.navWhyCriterion), "$labels")
        // Именно тут объяснено, почему числа мало: 1× внутри интервала.
        val interval = lines.first { it.label == SearchRu.navWhyInterval }
        assertTrue(assertNotNull(interval.note).contains("1×"), "${interval.note}")
        // Процента в неразрешённом состоянии нет и в разборе.
        assertTrue(!labels.contains(SearchRu.navWhyDifference), "$labels")
    }

    /**
     * «Недавний уровень» и «точка отсчёта» — РАЗНЫЕ величины, и разбор обязан
     * это сказать: одна считается сама, другую поставил человек.
     */
    @Test
    fun `the report tells the automatic level from the one the operator set`() {
        val lines = NavigateVerdict.whyLines(state(), ReferenceDelta.Collecting, cps = 30.0f)
        val recent = lines.first { it.label == SearchRu.navWhyRecent }
        val note = assertNotNull(recent.note)
        assertTrue(note.contains("сам"), note)
        assertTrue(note.contains("кнопк"), note)
        assertTrue(lines.any { it.label == SearchRu.navWhyReference })
    }

    /** Отношение и его знаменатель живут в ОДНОЙ строке — иначе это ×чего-то. */
    @Test
    fun `the ratio of the main card carries its denominator`() {
        val ratio = assertNotNull(NavigateVerdict.localRatio(comparison(25.0)))
        assertTrue(ratio.endsWith("недавнему уровню"), ratio)
        // Слово «фон» на этом экране занято обычным фоном профиля.
        assertTrue(!ratio.contains("фон"), ratio)
        assertTrue(!SearchEn.navRatioToLocal("1,00").contains("background"))
        // Состояние и величина — одна строка, а не две.
        val line = NavigateVerdict.trendLine(state())
        assertTrue(line.contains("·"), line)
        assertTrue(line.contains("недавнему уровню"), line)
    }

    @Test
    fun `without a test there is no ratio to show`() {
        assertNull(NavigateVerdict.localRatio(null))
        assertNull(NavigateVerdict.localInterval(null))
        assertEquals(SearchRu.navTrendCollecting, NavigateVerdict.trendLine(NavigateState()))
    }

    /**
     * Максимум держится с начала прогона, а лента показывает двадцать секунд —
     * поэтому он назван максимумом СЕССИИ. «максимум · 68 с назад» под окном в
     * 20 с читалось как противоречие, хотя противоречия не было.
     */
    @Test
    fun `the peak caption says it belongs to the session and carries its age`() {
        val bare = NavigateState()
        assertNull(NavigateVerdict.peakLine(bare, nowMillis = 10_000L))
        val held = bare.copy(peak = NavigatePeak(ratePerSecond = 47.6, atMillis = 2_000L))
        val line = assertNotNull(NavigateVerdict.peakLine(held, nowMillis = 20_000L))
        assertTrue(line.contains("47,6"), line)
        assertTrue(line.contains("18"), line)
        assertTrue(line.lowercase().contains("сесси"), line)
    }

    @Test
    fun `the reference control shows a value and a moment, or nothing`() {
        assertNull(NavigateVerdict.referenceLine(null, "11:44"))
        val reference = NavigateReference(window = window(26.0, 4), atMillis = 0L)
        assertNull(NavigateVerdict.referenceLine(reference, null))
        val line = assertNotNull(NavigateVerdict.referenceLine(reference, "11:44"))
        assertTrue(line.contains("11:44"), line)
        assertTrue(line.contains("Точка отсчёта"), line)
        // Единица рядом с числом не повторяется: она названа карточкой выше.
        assertTrue(!line.contains("с⁻¹"), line)
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
                texts += NavigateVerdict.ratioHeadline(state(), catalogue)
                texts += NavigateVerdict.deltaCaption(state(), delta, catalogue)
                texts += NavigateVerdict.referenceDirection(delta, catalogue)
                texts += NavigateVerdict.unresolvedNote(delta, catalogue).orEmpty()
                texts += NavigateVerdict.percentLabel(delta).orEmpty()
                texts += NavigateVerdict.whyLines(state(), delta, 30.0f, catalogue)
                    .flatMap { listOf(it.label, it.value, it.note.orEmpty()) }
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
            Regex("""\bбезопасн\w*\b"""),
            Regex("""\bопасн\w*\b"""),
            Regex("""\bнорма\b"""),
        )
        for (text in texts) {
            for (word in forbidden) {
                assertTrue(!word.containsMatchIn(text.lowercase()), "«$word»: $text")
            }
        }
    }
}
