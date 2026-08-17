package app.alpha.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Журнал падений существует ради одного вопроса: «что именно упало». Значит
 * запись обязана нести стек целиком, а обрезка старых записей — не резать
 * последнюю пополам.
 */
class CrashLogTest {

    private fun error(message: String): Throwable = try {
        throw IllegalStateException(message)
    } catch (e: IllegalStateException) {
        e
    }

    @Test
    fun `an entry carries the time, the thread and the stack`() {
        val entry = CrashLog.entry(
            atMillis = 1_700_000_000_000L,
            stamp = "13.08.2026 11:40:36",
            threadName = "main",
            error = error("импорт упал"),
        )
        assertTrue(entry.contains("13.08.2026 11:40:36"), entry)
        assertTrue(entry.contains("поток: main"), entry)
        assertTrue(entry.contains("IllegalStateException"), entry)
        assertTrue(entry.contains("импорт упал"), entry)
        // Стек, а не одна строка: без кадров запись бесполезна.
        assertTrue(entry.lines().count { it.trimStart().startsWith("at ") } >= 1, entry)
    }

    @Test
    fun `the log keeps the last entries whole`() {
        val entries = (1..30).map {
            CrashLog.entry(it.toLong(), "стамп $it", "main", error("падение $it"))
        }
        val trimmed = CrashLog.trimToLast(entries.joinToString(""), CrashLog.MAX_ENTRIES)
        assertEquals(CrashLog.MAX_ENTRIES, CrashLog.count(trimmed))
        // Последнее падение — то, ради которого архив и прислали.
        assertTrue(trimmed.contains("падение 30"), "последняя запись потерялась")
        assertTrue(!trimmed.contains("падение 10"), "старые записи не вытеснились")
        // Обрезка идёт по границам записей: у каждой на месте и время, и стек.
        assertEquals(CrashLog.MAX_ENTRIES, trimmed.split("поток: main").size - 1)
    }

    @Test
    fun `an empty log says so instead of going missing`() {
        // Отсутствие файла в архиве читалось бы как «забыли положить»;
        // «падений не записано» — это ответ.
        assertEquals("падений не записано\n", CrashLog.bundleText(""))
        assertEquals("падений не записано\n", CrashLog.bundleText("   \n"))
        assertEquals(0, CrashLog.count(""))
    }

    @Test
    fun `the log carries no measurements`() {
        // Правило записано тестом, чтобы не потеряться при правках: в журнал
        // попадает только то, что нужно для разбора падения.
        val entry = CrashLog.entry(1L, "стамп", "main", error("сбой"))
        for (forbidden in listOf("мкЗв", "имп/с", "широта", "долгота", "counts=")) {
            assertTrue(!entry.contains(forbidden), "«$forbidden» в журнале падений")
        }
    }
}
