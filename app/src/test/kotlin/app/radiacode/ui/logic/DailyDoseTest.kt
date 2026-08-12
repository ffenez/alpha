package app.radiacode.ui.logic

import app.radiacode.data.db.DownsampledSample
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyDoseTest {

    private val utc = ZoneOffset.UTC

    private fun millis(day: Int, hour: Int): Long =
        ZonedDateTime.of(2026, 8, day, hour, 0, 0, 0, utc).toInstant().toEpochMilli()

    private fun bucket(start: Long, avgDoseRate: Float, count: Int) = DownsampledSample(
        bucketStart = start,
        avgDoseRate = avgDoseRate,
        maxDoseRate = avgDoseRate,
        avgCountRate = 10f,
        sampleCount = count,
    )

    @Test
    fun `buckets integrate into their local day`() {
        // 0.00003 raw = 0.3 µSv/h; one full hour on Aug 8, one on Aug 9.
        val buckets = listOf(
            bucket(millis(8, 10), avgDoseRate = 0.00003f, count = 3600),
            bucket(millis(9, 12), avgDoseRate = 0.00003f, count = 3600),
        )
        val days = DailyDose.perDay(buckets, nowMillis = millis(9, 14), zone = utc, days = 30)
        assertEquals(30, days.size)
        assertTrue(abs(days[29].microSv - 0.3f) < 1e-6f) // today = Aug 9
        assertTrue(abs(days[28].microSv - 0.3f) < 1e-6f) // yesterday
        assertTrue(days.subList(0, 28).all { it.microSv == 0f })
    }

    @Test
    fun `days without measurements stay zero and old buckets drop`() {
        val buckets = listOf(
            bucket(millis(1, 0), avgDoseRate = 0.00003f, count = 3600),
        )
        val days = DailyDose.perDay(buckets, nowMillis = millis(9, 0), zone = utc, days = 5)
        assertEquals(5, days.size)
        assertTrue(days.all { it.microSv == 0f })
    }

    @Test
    fun `partial hours weight by their measured seconds`() {
        val buckets = listOf(
            bucket(millis(9, 8), avgDoseRate = 0.00003f, count = 1800),
        )
        val days = DailyDose.perDay(buckets, nowMillis = millis(9, 14), zone = utc, days = 3)
        assertTrue(abs(days[2].microSv - 0.15f) < 1e-6f)
    }

    @Test
    fun `a day carries how much of it was measured`() {
        // Час записи в сутках: доза настоящая, но день неполный — и столбик
        // такого дня нельзя сравнивать с полным как равный.
        val buckets = listOf(bucket(millis(9, 10), avgDoseRate = 0.00003f, count = 3600))
        val days = DailyDose.perDay(buckets, nowMillis = millis(9, 14), zone = utc, days = 3)
        val today = days[2]
        assertEquals(3600L, today.measuredSeconds)
        assertTrue(abs(today.coverage - 3600f / 86_400f) < 1e-6f)
        assertTrue(!today.full)

        // Полные сутки записи — полный день.
        val fullDay = DailyDose.perDay(
            (0 until 24).map { bucket(millis(9, it), avgDoseRate = 0.00003f, count = 3600) },
            nowMillis = millis(9, 23),
            zone = utc,
            days = 3,
        )[2]
        assertTrue(fullDay.full, "coverage=${fullDay.coverage}")
    }
}
