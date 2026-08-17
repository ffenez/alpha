package app.radiacode.ui.logic

import java.time.Instant
import java.time.ZoneId

/** День ленты: начало суток и записи этого дня, новые первыми. */
data class FeedDay<T>(val startOfDayMillis: Long, val entries: List<T>)

/**
 * Лента журнала: одна хронология на все виды записей.
 *
 * ## Почему лента, а не разделы
 *
 * Сессии, маршруты, снимки спектра и исследования жили в журнале отдельными
 * блоками: маршруты — крупными карточками, спектры — одной общей карточкой со
 * списком внутри, сессии — строками. Вкладка «Все» получалась набором разных
 * экранов, сложенных друг под друга, и найти «то, что я делал позавчера»
 * приходилось в трёх местах сразу. Здесь всё, что произошло, стоит в одном
 * порядке — по времени, — а различается содержанием строки, а не устройством
 * списка.
 *
 * ## Группировка по календарным суткам
 *
 * Границей служит местная полночь, а не «24 часа назад»: человек помнит свои
 * записи днями, а не скользящим окном. Дата уходит в заголовок группы и
 * перестаёт повторяться в каждой строке.
 */
object HistoryFeed {

    /**
     * Разложить записи по дням, новые первыми внутри дня и между днями.
     *
     * Сортировка здесь, а не на стороне вызывающего: смешивать четыре
     * источника и держать порядок — как раз то, ради чего лента и заведена.
     */
    fun <T> group(
        items: List<T>,
        timestamp: (T) -> Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<FeedDay<T>> {
        if (items.isEmpty()) return emptyList()
        return items
            .sortedByDescending(timestamp)
            .groupBy { startOfDay(timestamp(it), zone) }
            .map { (start, entries) -> FeedDay(start, entries) }
            .sortedByDescending { it.startOfDayMillis }
    }

    /** Местная полночь того дня, которому принадлежит момент. */
    fun startOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
}
