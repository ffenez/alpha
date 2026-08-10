package app.radiacode.ui.logic

import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeAxisTest {

    private val utc = ZoneOffset.UTC

    private fun millis(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 8, 9, hour, minute, 0, 0, utc).toInstant().toEpochMilli()

    @Test
    fun `one-hour window ticks on quarter hours`() {
        val labels = TimeAxis.labels(millis(13, 5), millis(14, 5), utc, count = 4)
        assertEquals(listOf("13:15", "13:30", "13:45", "14:00"), labels.map { it.second })
        // 13:15 is 10 min into the 60 min window.
        assertTrue(abs(labels[0].first - 10f / 60f) < 1e-4f)
    }

    @Test
    fun `aligned window includes the edge tick`() {
        val labels = TimeAxis.labels(millis(13, 0), millis(14, 0), utc, count = 4)
        assertEquals(listOf("13:00", "13:15", "13:30", "13:45", "14:00"), labels.map { it.second })
        assertEquals(0f, labels.first().first)
        assertEquals(1f, labels.last().first)
    }

    @Test
    fun `zone offset shifts the wall-clock grid`() {
        val plus3 = ZoneOffset.ofHours(3)
        val labels = TimeAxis.labels(millis(13, 5), millis(14, 5), plus3, count = 4)
        assertEquals(listOf("16:15", "16:30", "16:45", "17:00"), labels.map { it.second })
    }

    @Test
    fun `degenerate windows are empty`() {
        assertTrue(TimeAxis.labels(1000, 1000, utc).isEmpty())
        assertTrue(TimeAxis.labels(2000, 1000, utc).isEmpty())
    }

    // --- long windows switch to calendar days ---

    @Test
    fun `a week is labelled with dates, not with midnight repeated`() {
        val from = millis(13, 0)
        val labels = TimeAxis.autoLabels(from, from + 7L * 24 * 3_600_000L, utc, count = 4)
        assertTrue(labels.isNotEmpty())
        assertTrue(labels.all { it.second.contains(' ') }, "expected «9 авг» style labels")
        assertTrue(labels.all { it.first in 0f..1f })
        assertTrue(labels.map { it.first }.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `a day still reads as a clock`() {
        val from = millis(13, 0)
        val labels = TimeAxis.autoLabels(from, from + 6L * 3_600_000L, utc, count = 4)
        assertTrue(labels.all { it.second.matches(Regex("\\d\\d:\\d\\d")) })
    }

    @Test
    fun `day ticks land on local midnights`() {
        val from = millis(13, 0)
        val labels = TimeAxis.dayLabels(from, from + 4L * 24 * 3_600_000L, utc, count = 4)
        assertEquals(listOf("10 авг", "11 авг", "12 авг", "13 авг"), labels.map { it.second })
    }

    @Test
    fun `a month uses a coarser day step`() {
        val from = millis(0, 0)
        val labels = TimeAxis.dayLabels(from, from + 30L * 24 * 3_600_000L, utc, count = 4)
        assertTrue(labels.size in 3..6, "expected a handful of ticks, got ${labels.size}")
    }
}
