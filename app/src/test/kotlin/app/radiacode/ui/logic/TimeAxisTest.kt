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
}
