package app.radiacode.data.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Всё для разбора одного случая — одним файлом.
 *
 * Просить у человека «отчёт, а ещё экспорт спектра, а ещё скажите, что именно
 * не работает» — три шанса потерять половину. Архив собирается одной кнопкой и
 * содержит ровно то, что нужно для разбора: состояние приложения, спектры в
 * СВОЁМ формате (RC-XML остаётся валидным файлом, который открывается другими
 * программами) и описание проблемы словами человека.
 *
 * ZIP, а не один большой текст: спектр, вклеенный в текстовый отчёт,
 * перестаёт быть спектром — его нельзя ни открыть, ни сравнить.
 *
 * Чего в архиве НЕТ: координат, треков и рядов измерений во времени. Спектры
 * есть — без них диагностика прибора невозможна, — и файл говорит об этом
 * прямо, чтобы человек знал, что отправляет.
 */
object DebugBundle {

    const val PRIVACY_NOTE =
        "В архиве: состояние приложения, параметры прибора и накопленные спектры " +
            "(они нужны, чтобы разобрать работу прибора). В нём НЕТ координат, " +
            "треков и рядов измерений во времени. Архив создаётся по вашей команде " +
            "и никуда не отправляется."

    /** Один файл архива. */
    data class Entry(val name: String, val content: String)

    fun fileName(nowMillis: Long, stamp: (Long) -> String): String =
        "radiacode-debug-${stamp(nowMillis)}.zip"

    /**
     * Опись содержимого — первым файлом архива, чтобы тот, кто его откроет,
     * сразу видел, что внутри и чего внутри нет.
     */
    fun manifest(entries: List<Entry>, appVersion: String, stamp: String): String = buildString {
        appendLine("# Архив отладки RadiaCode")
        appendLine("версия приложения: $appVersion")
        appendLine("создан: $stamp")
        appendLine()
        appendLine(PRIVACY_NOTE)
        appendLine()
        appendLine("## Содержимое")
        for (entry in entries) appendLine("${entry.name} — ${describe(entry.name)}")
    }

    private fun describe(name: String): String = when {
        name == "report.txt" -> "состояние приложения и прибора в момент создания"
        name == "problem.txt" -> "описание проблемы словами пользователя"
        name == "spectrum.xml" -> "текущий накопленный спектр, формат RC-XML"
        name == "background.xml" -> "записанный фоновый спектр, формат RC-XML"
        name.endsWith(".xml") -> "спектр, формат RC-XML"
        else -> "текстовый файл"
    }

    /** Собирает архив в память: файлы отладки маленькие, поток здесь не нужен. */
    fun zip(entries: List<Entry>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (entry in entries) {
                zip.putNextEntry(ZipEntry(entry.name))
                zip.write(entry.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
