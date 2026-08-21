package app.alpha.data.export

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Разбор текстового формата спектра `.spe` (IAEA/ORTEC SPE) — обмена файлами
 * между Maestro, ORTEC-совместимыми МКА и программами анализа.
 *
 * Файл — последовательность секций `$ИМЯ:`, тело секции идёт до следующего
 * заголовка. Читаются:
 *
 * - `$SPEC_ID:` — свободное описание пробы;
 * - `$DATE_MEA:` — момент начала измерения (в файле локальное время без зоны);
 * - `$MEAS_TIM:` — два числа в СЕКУНДАХ: живое время, затем полное;
 * - `$DATA:` — первая строка «первый последний» номера каналов, дальше отсчёты;
 * - `$MCA_CAL:` — число коэффициентов, затем сами коэффициенты (до трёх) и
 *   необязательная единица;
 * - `$ENER_FIT:` — та же калибровка в линейном виде, два коэффициента.
 *
 * Коэффициенты в обеих секциях идут по возрастанию степени:
 * E(кэВ) = a0 + a1·ch + a2·ch². `$MCA_CAL` предпочтительнее `$ENER_FIT`,
 * потому что несёт квадратичный член, который `$ENER_FIT` выразить не может.
 * Калибровки может не быть вовсе — тогда [SpeSpectrum.calibration] равен null
 * и шкала остаётся в каналах; подставлять E = канал разборщик не вправе.
 *
 * Разбор терпим к необязательному (нет описания, нет даты, нет калибровки,
 * лишние секции) и отказывает там, где иначе пришлось бы догадываться: нет
 * каналов, нечитаемый диапазон, отсутствующее или неположительное живое время
 * (на него делится счёт при переводе в имп/с). Неизвестные секции игнорируются.
 *
 * Отсчёты читаются лексемами по всему телу `$DATA:`, а не «строка = канал»:
 * писатели формата ставят один отсчёт на строку, но встречаются и файлы с
 * несколькими числами в строке.
 *
 * Десятичный разделитель — только точка, как требует формат; число с запятой
 * считается нечитаемым и вызывает отказ, а не молчаливую замену.
 */
object SpeFile {

    private val WHITESPACE = Regex("\\s+")

    /**
     * Форматы `$DATE_MEA:`, встречающиеся у писателей формата. Разбор месяца
     * без учёта регистра: в файлах попадается и «Jan», и «JAN».
     */
    private val DATE_FORMATS = listOf(
        DateTimeFormatterBuilder().appendPattern("MM/dd/yyyy HH:mm:ss")
            .toFormatter(Locale.US),
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("dd-MMM-yyyy HH:mm:ss").toFormatter(Locale.US),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    )

    /**
     * @param zone зона, в которой истолковано локальное время `$DATE_MEA:`
     *   (в файле оно без указания зоны).
     * @throws SpeFileException если файл нельзя прочитать без догадок.
     */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): SpeParseResult {
        val warnings = mutableListOf<String>()
        val sections = sections(text, warnings)
        if (sections.isEmpty()) {
            throw SpeFileException("это не файл спектра SPE: в тексте нет ни одной секции \$ИМЯ:")
        }

        val channels = parseData(sections["DATA"], warnings)
        val times = parseTimes(sections["MEAS_TIM"], warnings)

        val calibration = parseMcaCal(sections["MCA_CAL"], warnings)
            ?: parseEnerFit(sections["ENER_FIT"], warnings)
        if (calibration == null) {
            warnings += "калибровки энергии в файле нет (\$MCA_CAL:/\$ENER_FIT:) — " +
                "шкала осталась в каналах"
        }

        return SpeParseResult(
            data = SpeSpectrum(
                title = sections["SPEC_ID"]?.joinToString(" ")?.trim()?.ifEmpty { null },
                startMillis = parseDate(sections["DATE_MEA"]?.firstOrNull(), zone, warnings),
                liveSeconds = times.first,
                realSeconds = times.second,
                firstChannel = channels.first,
                counts = channels.second,
                calibration = calibration,
            ),
            warnings = warnings,
        )
    }

    /**
     * Тело каждой секции: непустые строки до следующего заголовка. Строки до
     * первого заголовка — преамбула писателя, они отбрасываются.
     */
    private fun sections(text: String, warnings: MutableList<String>): Map<String, List<String>> {
        val sections = LinkedHashMap<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith('$')) {
                val name = line.drop(1).substringBefore(':').trim().uppercase(Locale.ROOT)
                if (name.isEmpty()) continue
                current = if (sections.containsKey(name)) {
                    warnings += "секция \$$name: встречается несколько раз — прочитана первая"
                    // Повторное тело копится в отброшенный список, чтобы не
                    // попасть в предыдущую секцию.
                    mutableListOf()
                } else {
                    mutableListOf<String>().also { sections[name] = it }
                }
                continue
            }
            if (line.isNotEmpty()) current?.add(line)
        }
        return sections
    }

    /** @return номер первого канала и отсчёты по каналам. */
    private fun parseData(
        body: List<String>?,
        warnings: MutableList<String>,
    ): Pair<Int, List<Int>> {
        if (body.isNullOrEmpty()) {
            throw SpeFileException("в файле нет секции \$DATA: со счётом по каналам")
        }
        val header = body.first()
        val range = header.split(WHITESPACE)
        val first = range.getOrNull(0)?.toIntOrNull()
        val last = range.getOrNull(1)?.toIntOrNull()
        if (first == null || last == null) {
            throw SpeFileException(
                "секция \$DATA: начинается не с диапазона каналов («$header»)",
            )
        }
        if (last < first || first < 0) {
            throw SpeFileException("секция \$DATA: задаёт диапазон каналов $first…$last")
        }
        val declared = last - first + 1

        val counts = body.drop(1)
            .flatMap { it.split(WHITESPACE) }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, token ->
                token.toLongOrNull()?.toInt()
                    ?: token.toDoubleOrNull()?.roundToInt()
                    ?: throw SpeFileException(
                        "\$DATA: канал №${first + index} не число («$token»)",
                    )
            }
        if (counts.isEmpty()) throw SpeFileException("в секции \$DATA: нет ни одного отсчёта")
        if (counts.size != declared) {
            warnings += "заявлено $declared каналов ($first…$last), в файле ${counts.size} — " +
                "использованы фактические"
        }
        if (first != 0) {
            warnings += "спектр начинается с канала $first — первые $first каналов неизвестны"
        }
        return first to counts
    }

    /** @return живое время (с) и полное время (с), если оно указано. */
    private fun parseTimes(
        body: List<String>?,
        warnings: MutableList<String>,
    ): Pair<Double, Double?> {
        val line = body?.firstOrNull()
            ?: throw SpeFileException(
                "в файле нет секции \$MEAS_TIM: — живое время измерения неизвестно, " +
                    "счёт нельзя перевести в имп/с",
            )
        val tokens = line.split(WHITESPACE)
        val live = tokens.getOrNull(0)?.toDoubleOrNull()
            ?: throw SpeFileException(
                "секция \$MEAS_TIM: не начинается с живого времени в секундах («$line»)",
            )
        if (live <= 0.0) {
            throw SpeFileException("живое время $live с не положительно — спектр не нормировать")
        }
        val real = tokens.getOrNull(1)?.toDoubleOrNull()
        if (real == null) {
            warnings += "в \$MEAS_TIM: нет полного времени — известно только живое ($live с)"
        } else if (real < live) {
            warnings += "полное время $real с меньше живого $live с — значения взяты как есть"
        }
        return live to real
    }

    private fun parseMcaCal(
        body: List<String>?,
        warnings: MutableList<String>,
    ): SpeCalibration? {
        val tokens = tokens(body) ?: return null
        val declared = tokens.first().toIntOrNull()
            ?: throw SpeFileException(
                "секция \$MCA_CAL: начинается не с числа коэффициентов («${tokens.first()}»)",
            )
        // Хвост вроде «keV» — единица шкалы, а не коэффициент.
        val values = tokens.drop(1).mapNotNull { it.toDoubleOrNull() }
        if (values.isEmpty()) {
            warnings += "в секции \$MCA_CAL: нет коэффициентов — калибровка не прочитана"
            return null
        }
        if (values.size < declared) {
            warnings += "в \$MCA_CAL: заявлено $declared коэффициентов, прочитано " +
                "${values.size} — недостающие приняты нулевыми"
        }
        if (values.size > 3) {
            warnings += "калибровка порядка ${values.size - 1} не поддержана — взяты первые " +
                "три коэффициента"
        }
        return SpeCalibration(values[0], values.getOrElse(1) { 0.0 }, values.getOrElse(2) { 0.0 })
    }

    private fun parseEnerFit(
        body: List<String>?,
        warnings: MutableList<String>,
    ): SpeCalibration? {
        val tokens = tokens(body) ?: return null
        val values = tokens.mapNotNull { it.toDoubleOrNull() }
        if (values.size < 2) {
            warnings += "в секции \$ENER_FIT: меньше двух коэффициентов — калибровка не прочитана"
            return null
        }
        // $ENER_FIT линейная по определению формата: квадратичного члена нет.
        return SpeCalibration(values[0], values[1], 0.0)
    }

    private fun tokens(body: List<String>?): List<String>? = body
        ?.flatMap { it.split(WHITESPACE) }
        ?.filter { it.isNotEmpty() }
        ?.ifEmpty { null }

    private fun parseDate(
        line: String?,
        zone: ZoneId,
        warnings: MutableList<String>,
    ): Long? {
        val raw = line?.trim()?.ifEmpty { null } ?: return null
        for (format in DATE_FORMATS) {
            runCatching {
                return LocalDateTime.parse(raw, format).atZone(zone).toInstant().toEpochMilli()
            }
        }
        warnings += "дата измерения «$raw» не распознана — время не импортировано"
        return null
    }
}

/** Калибровка энергии: E(кэВ) = a0 + a1·ch + a2·ch². */
data class SpeCalibration(val a0: Double, val a1: Double, val a2: Double)

/** Спектр из файла `.spe`. */
data class SpeSpectrum(
    /** `$SPEC_ID:`, null если описания нет. */
    val title: String?,
    /** `$DATE_MEA:` в epoch millis, null если даты нет или она нечитаема. */
    val startMillis: Long?,
    /** Живое время накопления, секунды; всегда больше нуля. */
    val liveSeconds: Double,
    /** Полное время накопления, секунды; null если в файле только живое. */
    val realSeconds: Double?,
    /** Номер канала, которому соответствует `counts[0]` (обычно 0). */
    val firstChannel: Int,
    val counts: List<Int>,
    /** null, если калибровки в файле нет: шкала остаётся в каналах. */
    val calibration: SpeCalibration?,
) {
    val channelCount: Int get() = counts.size
}

data class SpeParseResult(val data: SpeSpectrum, val warnings: List<String>)

/** Файл `.spe`, который нельзя прочитать без догадок. */
class SpeFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
