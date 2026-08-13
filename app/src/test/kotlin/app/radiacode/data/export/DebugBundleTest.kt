package app.radiacode.data.export

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Архив существует ради одного: чтобы разбор случая не зависел от того,
 * сколько файлов человек не забыл приложить.
 */
class DebugBundleTest {

    private val entries = listOf(
        DebugBundle.Entry("problem.txt", "подключается, но спектр пустой"),
        DebugBundle.Entry("report.txt", "## Прибор\nмодель: RadiaCode-103G\n"),
        DebugBundle.Entry("spectrum.xml", "<ResultDataFile/>"),
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return out
    }

    @Test
    fun `every part survives the archive intact`() {
        val files = unzip(DebugBundle.zip(entries))
        assertEquals(entries.size, files.size)
        for (entry in entries) {
            assertEquals(entry.content, files[entry.name], entry.name)
        }
        // Спектр остаётся XML-файлом, а не строкой внутри текста: иначе его
        // нельзя ни открыть другой программой, ни сравнить с эталоном.
        assertTrue(files.getValue("spectrum.xml").startsWith("<"))
    }

    @Test
    fun `the privacy note names what the archive does and does not contain`() {
        // README из архива убран, но оговорка живёт на экране создания архива
        // и обязана называть обе стороны: спектры внутри ЕСТЬ, координат НЕТ.
        assertTrue(DebugBundle.PRIVACY_NOTE.contains("спектры"), DebugBundle.PRIVACY_NOTE)
        assertTrue(DebugBundle.PRIVACY_NOTE.contains("НЕТ координат"), DebugBundle.PRIVACY_NOTE)
    }

    @Test
    fun `the file name carries the moment it was taken`() {
        assertEquals("radiacode-debug-T0.zip", DebugBundle.fileName(0L) { "T0" })
    }

    @Test
    fun `an archive without optional parts is still valid`() {
        // Прибор не подключён: спектра нет, отчёт есть — архив обязан собраться.
        val minimal = listOf(DebugBundle.Entry("report.txt", "## Прибор\nподключение: нет связи\n"))
        val files = unzip(DebugBundle.zip(minimal))
        assertEquals(setOf("report.txt"), files.keys)
    }
}
