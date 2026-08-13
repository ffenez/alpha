package app.radiacode.data.export

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Журнал падений — на устройстве, в файле, без сети.
 *
 * ## Зачем
 *
 * Полевые случаи разбираются по отладочному архиву, который человек присылает
 * сам. До сих пор в архиве было СОСТОЯНИЕ приложения, но не было главного:
 * что именно упало. Вопрос «приложение вылетает при импорте» без стека
 * отвечается гаданием, а гадать в этом проекте нельзя.
 *
 * ## Что можно и чего нельзя
 *
 * Пишется только то, что нужно для разбора: время, поток, класс, сообщение и
 * стек. Ни координат, ни измерений, ни содержимого спектров — их в стеке и не
 * бывает, но правило записано здесь, чтобы оно не потерялось при правках.
 *
 * Никакой отправки: файл лежит в каталоге приложения, попадает в архив только
 * по команде человека и стирается его кнопкой. Это ровно тот же принцип, что
 * у самого отчёта.
 *
 * ## Почему хвост, а не всё подряд
 *
 * Хранятся последние [MAX_ENTRIES] записей: цикл падений при старте иначе
 * съел бы место, а разбирают всегда последние. Обрезка идёт по границам
 * записей, а не по байтам — половина стека хуже, чем её отсутствие.
 */
object CrashLog {

    const val FILE_NAME = "crashes.txt"

    /** Сколько последних падений хранится. Инженерный параметр. */
    const val MAX_ENTRIES = 20

    private const val SEPARATOR = "----8<---- "

    /** Одна запись журнала в том виде, в каком она уезжает в архив. */
    fun entry(
        atMillis: Long,
        stamp: String,
        threadName: String,
        error: Throwable,
    ): String {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString().trim()
        return buildString {
            append(SEPARATOR).append(atMillis).append('\n')
            append("время: ").append(stamp).append('\n')
            append("поток: ").append(threadName).append('\n')
            append(stack).append('\n')
        }
    }

    /** Дописать запись, оставив в файле последние [MAX_ENTRIES] штук. */
    fun append(file: File, entry: String) {
        val existing = if (file.exists()) runCatching { file.readText() }.getOrDefault("") else ""
        val trimmed = trimToLast(existing + entry, MAX_ENTRIES)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(trimmed)
        }
    }

    /** Последние [limit] записей; пустая строка, если журнал пуст. */
    fun trimToLast(text: String, limit: Int): String {
        if (text.isBlank()) return ""
        val parts = text.split(SEPARATOR).filter { it.isNotBlank() }
        val kept = if (parts.size <= limit) parts else parts.takeLast(limit)
        return kept.joinToString(separator = "") { SEPARATOR + it }
    }

    /** Сколько падений в журнале — числом на экране отладки. */
    fun count(text: String): Int =
        text.split(SEPARATOR).count { it.isNotBlank() }

    /** Что кладётся в архив: журнал или честная строка о его отсутствии. */
    fun bundleText(text: String): String =
        if (text.isBlank()) "падений не записано\n" else text
}
