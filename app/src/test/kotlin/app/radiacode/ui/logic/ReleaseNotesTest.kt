package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Список обновлений виден пользователю, поэтому на него действуют те же
 * правила, что и на любой другой текст приложения: никаких обещаний
 * безопасности и никаких внутренних имён, по которым ничего не понять.
 */
class ReleaseNotesTest {

    private val text: List<String> = ReleaseNotes.shown
        .flatMap { listOf(it.title, it.date) + it.lines } + ReleaseNotes.VERSIONING_NOTE

    @Test
    fun `the screen shows exactly the promised number of updates`() {
        assertEquals(ReleaseNotes.SHOWN, ReleaseNotes.shown.size)
        assertTrue(ReleaseNotes.shown.all { it.lines.isNotEmpty() })
    }

    @Test
    fun `dates are real dates in one format`() {
        val date = Regex("""^\d{2}\.\d{2}\.\d{4}$""")
        for (note in ReleaseNotes.shown) {
            assertTrue(date.matches(note.date), note.date)
        }
    }

    @Test
    fun `nothing here promises safety`() {
        val forbidden = listOf(
            Regex("""\bбезопасн(о|ый|ая|ое)\b"""),
            Regex("""\bопасн(о|ый|ая|ое)\b"""),
            Regex("""\bдопустим\w*\b"""),
            Regex("""\bнормальн\w*\b(?! распределени)"""),
            Regex("""\bнорма\b"""),
        )
        for (line in text) {
            for (word in forbidden) {
                assertTrue(!word.containsMatchIn(line.lowercase()), "«$word» in: $line")
            }
        }
    }

    @Test
    fun `no internal names leak into what the user reads`() {
        // Записи объясняют, что изменилось на экране, а не как это устроено.
        val internal = listOf(
            "baseline", "snapshot", "repository", "dao", "compose", "sql",
            "buildframe", "chartmetric", "datastore",
        )
        for (line in text) {
            for (name in internal) {
                assertTrue(!line.lowercase().contains(name), "«$name» in: $line")
            }
        }
    }
}
