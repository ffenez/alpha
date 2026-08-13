package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Хранимая история спектрограммы (ADR 007): арифметика объединения и запреты,
 * ради которых она вообще выделена в отдельный вид данных.
 */
class SpectrogramHistoryTest {

    private fun slice(
        startMillis: Long,
        durationMillis: Long,
        counts: Int,
        scheme: String = SpectrogramBinning.CURRENT_SCHEME,
        endMillis: Long = startMillis + durationMillis,
        cps: Float? = null,
    ) = HistorySlice(
        startMillis = startMillis,
        endMillis = endMillis,
        durationMillis = durationMillis,
        schemeId = scheme,
        bandCounts = IntArray(Spectrogram.BAND_COUNT) { if (it == 0) counts else 0 },
        cps = cps,
        doseMicroSvH = null,
    )

    @Test
    fun `merge sums counts and durations and divides only afterwards`() {
        // 100 импульсов за 5 с и 100 за 10 с. Среднее готовых скоростей дало бы
        // (20 + 10)/2 = 15 имп/с — числа, которого прибор не измерял.
        val merged = SpectrogramHistory.merge(
            listOf(
                slice(startMillis = 0L, durationMillis = 5_000L, counts = 100),
                slice(startMillis = 5_000L, durationMillis = 10_000L, counts = 100),
            ),
        )
        assertNotNull(merged)
        assertEquals(200L, merged.totalCounts)
        assertEquals(15_000L, merged.durationMillis)
        assertEquals(0L, merged.startMillis)
        assertEquals(15_000L, merged.endMillis)
        assertEquals(2, merged.sliceCount)
        assertEquals(200f / 15f, merged.ratePerSecond()!!, 1e-4f)
    }

    @Test
    fun `merge never bridges a gap in the recording`() {
        // 18:00–18:02 данные, 18:02–18:07 пропуск, 18:07–18:10 данные.
        val slices = listOf(
            slice(startMillis = 0L, durationMillis = 120_000L, counts = 50),
            slice(startMillis = 420_000L, durationMillis = 180_000L, counts = 50),
        )
        assertEquals(SliceMergeRefusal.GAP, SpectrogramHistory.refusal(slices))
        assertNull(SpectrogramHistory.merge(slices))
        // И прореживание тоже оставляет их двумя срезами, а не одним на 10 мин.
        val compacted = SpectrogramHistory.compact(slices, 600_000L)
        assertEquals(2, compacted.size)
    }

    @Test
    fun `slices of different binning schemes are never added together`() {
        val slices = listOf(
            slice(startMillis = 0L, durationMillis = 5_000L, counts = 10),
            slice(
                startMillis = 5_000L,
                durationMillis = 5_000L,
                counts = 10,
                scheme = "SPECTROGRAM_96_V2",
            ),
        )
        assertEquals(SliceMergeRefusal.SCHEME_MISMATCH, SpectrogramHistory.refusal(slices))
        assertNull(SpectrogramHistory.merge(slices))
        assertEquals(2, SpectrogramHistory.compact(slices, 60_000L).size)
    }

    @Test
    fun `compaction merges inside epoch-aligned buckets and is idempotent`() {
        // Десять получасовых… нет: десять тридцатисекундных срезов подряд.
        val slices = (0 until 10).map {
            slice(startMillis = it * 30_000L, durationMillis = 30_000L, counts = 30)
        }
        val once = SpectrogramHistory.compact(slices, SpectrogramHistory.COMPACTED_SLICE_MILLIS)
        assertEquals(1, once.size)
        assertEquals(300L, once.first().totalCounts)
        assertEquals(300_000L, once.first().durationMillis)
        // Повторный проход по уже прорежённому ничего не меняет: корзины
        // выровнены по эпохе, а не «жадно от первого».
        val twice = SpectrogramHistory.compact(once, SpectrogramHistory.COMPACTED_SLICE_MILLIS)
        assertEquals(1, twice.size)
        assertEquals(300L, twice.first().totalCounts)
        assertEquals(once.first().sliceCount, twice.first().sliceCount)
    }

    @Test
    fun `compaction never merges across a bucket boundary`() {
        val slices = listOf(
            slice(startMillis = 4L * 60_000L, durationMillis = 60_000L, counts = 60),
            slice(startMillis = 5L * 60_000L, durationMillis = 60_000L, counts = 60),
        )
        val compacted = SpectrogramHistory.compact(slices, SpectrogramHistory.COMPACTED_SLICE_MILLIS)
        assertEquals(2, compacted.size, "пятиминутная граница режет корзину")
    }

    @Test
    fun `the 1 Hz readings of a merged slice are weighted by exposure`() {
        val merged = SpectrogramHistory.merge(
            listOf(
                slice(startMillis = 0L, durationMillis = 10_000L, counts = 1, cps = 30f),
                slice(startMillis = 10_000L, durationMillis = 30_000L, counts = 1, cps = 10f),
            ),
        )
        assertNotNull(merged)
        // (30·10 + 10·30) / 40 = 15, а не среднее арифметическое 20.
        assertEquals(15f, merged.cps!!, 1e-4f)
    }

    @Test
    fun `storage estimate follows the recording interval`() {
        val detailed = SpectrogramHistory.megabytesPerDay(5_000L)
        val balanced = SpectrogramHistory.megabytesPerDay(30_000L)
        val frugal = SpectrogramHistory.megabytesPerDay(600_000L)
        assertEquals(7.95f, detailed, 0.05f)
        assertEquals(1.32f, balanced, 0.05f)
        assertEquals(0.066f, frugal, 0.005f)
        assertTrue(detailed > balanced && balanced > frugal)
    }

    @Test
    fun `the band scheme has one set of edges and unknown schemes are refused`() {
        val edges = SpectrogramBinning.edgesKeV(SpectrogramBinning.CURRENT_SCHEME)
        assertNotNull(edges)
        assertEquals(Spectrogram.BAND_COUNT + 1, edges.size)
        assertEquals(Spectrogram.MIN_KEV, edges.first(), 1e-3f)
        assertEquals(Spectrogram.MAX_KEV, edges.last(), 1e-1f)
        // Границы схемы — те же, по которым режет картинка.
        assertEquals(0, Spectrogram.bandOfEnergy(edges[1] - 0.1f))
        assertNull(SpectrogramBinning.bandCount("SPECTROGRAM_128_V1"))
        assertNull(SpectrogramBinning.edgesKeV("SPECTROGRAM_128_V1"))
    }
}
