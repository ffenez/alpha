package app.radiacode.ui.logic

import app.radiacode.analysis.ShapeVerdict
import app.radiacode.analysis.SpectrogramSlice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchSpectrumHintTest {

    private val bands = 96

    private fun slice(atMillis: Long, total: Double, linePeak: Double = 0.0): SpectrogramSlice {
        val raw = DoubleArray(bands) { i -> 1.0 / (1.0 + i * 0.15) }
        val sum = raw.sum()
        val counts = FloatArray(bands) { i -> (raw[i] / sum * total).toFloat() }
        if (linePeak > 0.0) counts[60] += linePeak.toFloat()
        return SpectrogramSlice(
            timestampMillis = atMillis,
            intervalSeconds = 5,
            bandCounts = counts,
            cps = 25f,
            doseMicroSvH = 0.1f,
        )
    }

    /** 24 slices of «before», then slices of the excursion. */
    private fun tape(excursionStart: Long, excursionSlices: Int, linePeak: Double) = buildList {
        for (i in 1..24) add(slice(excursionStart - i * 5_000L, total = 1_000.0))
        for (i in 0 until excursionSlices) {
            add(slice(excursionStart + i * 5_000L, total = 1_000.0, linePeak = linePeak))
        }
    }.sortedBy { it.timestampMillis }

    @Test
    fun `no excursion means no question is asked`() {
        assertNull(
            SearchSpectrumHint.compare(
                tape(100_000L, excursionSlices = 4, linePeak = 0.0),
                excursionStartMillis = null,
                nowMillis = 200_000L,
            ),
        )
        assertNull(SearchSpectrumHint.compare(emptyList(), 100_000L, 200_000L))
    }

    @Test
    fun `an excursion with the same spectral shape produces no invitation`() {
        val start = 100_000L
        val comparison = assertNotNull(
            SearchSpectrumHint.compare(
                tape(start, excursionSlices = 6, linePeak = 0.0),
                excursionStartMillis = start,
                nowMillis = start + 30_000L,
            ),
        )
        assertEquals(ShapeVerdict.CONSISTENT, comparison.verdict, "z = ${comparison.z}")
        assertNull(SearchSpectrumHint.invitation(comparison))
        assertTrue(assertNotNull(SearchSpectrumHint.note(comparison)).contains("не изменилась"))
    }

    @Test
    fun `a new line during the excursion earns the invitation`() {
        val start = 100_000L
        val comparison = assertNotNull(
            SearchSpectrumHint.compare(
                tape(start, excursionSlices = 6, linePeak = 400.0),
                excursionStartMillis = start,
                nowMillis = start + 30_000L,
            ),
        )
        assertEquals(ShapeVerdict.CHANGED, comparison.verdict, "z = ${comparison.z}")
        assertEquals(
            "Изменился не только счёт, но и форма спектра",
            SearchSpectrumHint.invitation(comparison),
        )
        val note = assertNotNull(SearchSpectrumHint.note(comparison))
        assertTrue(note.contains("не решает"), note)
        // The invitation may never turn into an identification (§12, §13).
        for (word in listOf("изотоп", "обнаруж", "источник найден")) {
            assertTrue(!note.lowercase().contains(word), "«$word» in: $note")
        }
    }

    @Test
    fun `a short excursion says the data is thin instead of guessing`() {
        val start = 100_000L
        val comparison = assertNotNull(
            SearchSpectrumHint.compare(
                tape(start, excursionSlices = 1, linePeak = 0.0).map {
                    // One thin slice on the excursion side.
                    if (it.timestampMillis >= start) slice(it.timestampMillis, total = 20.0) else it
                },
                excursionStartMillis = start,
                nowMillis = start + 5_000L,
            ),
        )
        assertEquals(ShapeVerdict.NOT_ENOUGH_DATA, comparison.verdict)
        assertNull(SearchSpectrumHint.invitation(comparison))
        assertTrue(assertNotNull(SearchSpectrumHint.note(comparison)).contains("мало"))
    }

    @Test
    fun `only the two minutes before the excursion are the reference`() {
        val start = 1_000_000L
        val ancient = slice(start - SearchSpectrumHint.REFERENCE_MILLIS - 10_000L, total = 9_999.0)
        val slices = tape(start, excursionSlices = 6, linePeak = 0.0) + ancient

        val comparison = assertNotNull(
            SearchSpectrumHint.compare(slices, start, start + 30_000L),
        )
        // 24 reference slices of 1 000 counts each; the ancient one is outside.
        assertEquals(24_000.0, comparison.referenceCounts, 1.0)
    }
}
