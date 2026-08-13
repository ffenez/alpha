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
        .flatMap { listOf(it.title, it.version) + it.lines }

    @Test
    fun `the screen shows exactly the promised number of updates`() {
        assertEquals(ReleaseNotes.SHOWN, ReleaseNotes.shown.size)
        assertTrue(ReleaseNotes.shown.all { it.lines.isNotEmpty() })
    }

    @Test
    fun `versions are numbers, unique and ordered newest first`() {
        val version = Regex("""^\d+\.\d+\.\d+$""")
        val versions = ReleaseNotes.all.map { it.version }
        for (v in versions) assertTrue(version.matches(v), v)
        assertEquals(versions.distinct(), versions, "повторяющиеся номера версий")
        val ordered = versions.sortedByDescending { v ->
            v.split('.').map { it.toInt().toString().padStart(4, '0') }.joinToString(".")
        }
        assertEquals(ordered, versions, "список должен идти от новой версии к старой")
    }

    @Test
    fun `the newest note describes the build that is installed`() {
        // Два источника одного факта обязаны совпадать: иначе список
        // обновлений описывает не ту версию, что стоит на телефоне.
        assertEquals(app.radiacode.BuildConfig.VERSION_NAME, ReleaseNotes.current)
    }

    @Test
    fun `version numbers do not depend on the language`() {
        // Номер версии — факт сборки: он обязан быть одним и тем же на любом
        // языке, иначе «та ли у меня версия» перестало бы иметь ответ.
        val ru = ReleaseNotes.notes(app.radiacode.ui.text.ReleaseRu).map { it.version }
        val en = ReleaseNotes.notes(app.radiacode.ui.text.ReleaseEn).map { it.version }
        assertEquals(ru, en)
        assertEquals(ru.first(), ReleaseNotes.current)
        assertEquals(ReleaseNotes.SHOWN, ReleaseNotes.shownIn(app.radiacode.ui.text.ReleaseEn).size)
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
