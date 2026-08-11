package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Фон, который несёт данные: «прибор молчал» не должно выглядеть как «уровень
 * был низкий», а «сюда история не доходит» — как измеренный ноль.
 */
class ChartBackgroundTest {

    private val minute = 60_000L

    private fun bucket(startMillis: Long, bucketMillis: Long, samples: Int) = ChartBucket(
        startMillis = startMillis,
        endMillis = startMillis + bucketMillis,
        min = 0.1f,
        max = 0.2f,
        median = 0.15f,
        sampleCount = samples,
    )

    @Test
    fun `a covered window has no gaps at all`() {
        val bucketMillis = minute
        val from = 0L
        val to = 10 * minute
        val buckets = (0 until 10).map { bucket(it * bucketMillis, bucketMillis, 60) }
        assertTrue(ChartBackground.gaps(buckets, from, to, bucketMillis).isEmpty())
    }

    @Test
    fun `neighbouring empty buckets merge into one honest hole`() {
        val bucketMillis = minute
        val from = 0L
        val to = 10 * minute
        // Минуты 3, 4, 5 без измерений.
        val buckets = (0 until 10)
            .filter { it !in 3..5 }
            .map { bucket(it * bucketMillis, bucketMillis, 60) }

        val gaps = ChartBackground.gaps(buckets, from, to, bucketMillis, minGapMillis = 1L)
        assertEquals(1, gaps.size, "$gaps")
        assertEquals(3 * minute, gaps.single().fromMillis)
        assertEquals(6 * minute, gaps.single().toMillis)
    }

    @Test
    fun `a bucket that exists but holds nothing is still a hole`() {
        val bucketMillis = minute
        val buckets = listOf(
            bucket(0, bucketMillis, 60),
            bucket(bucketMillis, bucketMillis, 0),
            bucket(2 * bucketMillis, bucketMillis, 60),
        )
        val gaps = ChartBackground.gaps(buckets, 0, 3 * minute, bucketMillis, minGapMillis = 1L)
        assertEquals(1, gaps.size)
        assertEquals(bucketMillis, gaps.single().fromMillis)
    }

    @Test
    fun `a hole shorter than a hundredth of the window is not drawn`() {
        val bucketMillis = 1_000L
        val span = 15 * minute
        val buckets = (0 until 900).filter { it != 100 }.map { bucket(it * 1_000L, 1_000L, 1) }

        // Одна секунда на пятнадцати минутах — жизнь потока, а не пропуск.
        assertTrue(ChartBackground.gaps(buckets, 0, span, bucketMillis).isEmpty())
        // Порог привязан к окну, а не к абсолютному времени.
        assertEquals(9_000L, ChartBackground.minGapFor(span))
        assertEquals(1_000L, ChartBackground.minGapFor(minute))
    }

    @Test
    fun `the area before the first measurement is marked, not left blank`() {
        val from = 0L
        val to = 10 * minute
        val start = assertNotNull(ChartBackground.historyStart(3 * minute, from, to))
        assertEquals(0L, start.fromMillis)
        assertEquals(3 * minute, start.toMillis)

        // История начинается раньше окна — отмечать нечего.
        assertNull(ChartBackground.historyStart(from - minute, from, to))
        // Истории нет вовсе — отмечено всё окно.
        val empty = assertNotNull(ChartBackground.historyStart(null, from, to))
        assertEquals(from, empty.fromMillis)
        assertEquals(to, empty.toMillis)
    }

    @Test
    fun `zebra appears only on long windows and never as a swarm of stripes`() {
        assertTrue(ChartBackground.bands(0, 15 * minute).isEmpty(), "15 минут — не для зебры")
        assertTrue(ChartBackground.bands(0, 3 * 3_600_000L).isEmpty())

        val day = ChartBackground.bands(0, 24 * 3_600_000L)
        assertEquals(24, day.size)
        assertTrue(day.size <= ChartBackground.MAX_BANDS)

        val week = ChartBackground.bands(0, 7 * 24 * 3_600_000L)
        assertEquals(7, week.size, "на неделе полосы — сутки, а не часы")
        assertTrue(week.zipWithNext().all { (a, b) -> a.shaded != b.shaded }, "полосы чередуются")
    }

    @Test
    fun `bands are pinned to the clock, not to the edge of the screen`() {
        // Сдвиг окна на полчаса не должен перекрашивать полосы: они привязаны
        // к стенным часам, иначе зебра дёргалась бы при прокрутке.
        val instant = 5 * 3_600_000L + 30 * minute
        fun shadedAt(from: Long, to: Long) = ChartBackground.bands(from, to)
            .first { instant >= it.fromMillis && instant < it.toMillis }
            .shaded

        assertEquals(
            shadedAt(0, 24 * 3_600_000L),
            shadedAt(30 * minute, 24 * 3_600_000L + 30 * minute),
        )
    }
}
