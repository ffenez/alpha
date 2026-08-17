package app.radiacode.ui.logic

import app.radiacode.ui.text.HistoryRu
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Лента журнала: один порядок на все виды записей и границы по местным суткам.
 */
class HistoryFeedTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `записи разных видов стоят в одном порядке`() {
        // Маршрут, сессия и снимок одного дня перемешаны по времени — так же,
        // как они происходили.
        val items = listOf(
            "маршрут" to at(16, 13),
            "сессия" to at(16, 19),
            "снимок" to at(16, 20),
        )
        val days = HistoryFeed.group(items, timestamp = { it.second }, zone = zone)
        assertEquals(1, days.size)
        assertEquals(listOf("снимок", "сессия", "маршрут"), days.first().entries.map { it.first })
    }

    @Test
    fun `дни идут от новых к старым`() {
        val items = listOf(14, 16, 15).map { day -> day to at(day, 12) }
        val days = HistoryFeed.group(items, timestamp = { it.second }, zone = zone)
        assertEquals(listOf(16, 15, 14), days.map { it.entries.first().first })
    }

    @Test
    fun `граница дня — местная полночь, а не сутки назад`() {
        // 23:50 и 00:10 разделены двадцатью минутами и всё-таки принадлежат
        // разным дням: человек помнит записи днями.
        val items = listOf("вечер" to at(15, 23, 50), "ночь" to at(16, 0, 10))
        val days = HistoryFeed.group(items, timestamp = { it.second }, zone = zone)
        assertEquals(2, days.size)
        assertEquals("ночь", days.first().entries.single().first)
        assertEquals("вечер", days.last().entries.single().first)
    }

    @Test
    fun `заголовки дня читаются словами`() {
        val now = at(17, 10)
        assertEquals(
            HistoryRu.today,
            HistoryFormat.dayHeader(at(17, 9), now, zone, HistoryRu),
        )
        assertEquals(
            HistoryRu.yesterday,
            HistoryFormat.dayHeader(at(16, 9), now, zone, HistoryRu),
        )
        assertEquals(
            "15 августа",
            HistoryFormat.dayHeader(at(15, 9), now, zone, HistoryRu),
        )
    }

    @Test
    fun `пустая лента не даёт пустых дней`() {
        assertEquals(emptyList(), HistoryFeed.group(emptyList<Long>(), timestamp = { it }))
    }
}
