package app.alpha.data.export

import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.roundToInt
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Разбор XML-файла спектра BecqMoni (BecquerelMonitor) — программы, которой
 * пользуются с самодельными и звуковыми МКА, Atom Spectra и RadiaCode.
 *
 * Структура установлена по исходникам самой программы (C#, имена элементов
 * задаёт .NET `XmlSerializer` по именам свойств) и по реальным файлам:
 *
 * - [BecquerelMonitor/EnergySpectrum.cs](https://github.com/Am6er/BecqMoni/blob/master/BecquerelMonitor/EnergySpectrum.cs)
 * - [BecquerelMonitor/PolynomialEnergyCalibration.cs](https://github.com/Am6er/BecqMoni/blob/master/BecquerelMonitor/PolynomialEnergyCalibration.cs)
 * - [BecquerelMonitor/ResultData.cs](https://github.com/Am6er/BecqMoni/blob/master/BecquerelMonitor/ResultData.cs)
 * - образец файла Atom Nano 3: [byBenPuls/spectrumconverter, examples/atom_nano3_test.xml](https://github.com/byBenPuls/spectrumconverter)
 *
 * ```
 * ResultDataFile → FormatVersion (120920)
 *                → ResultDataList → ResultData → SampleInfo (Name/Location/Time/Note)
 *                                              → DeviceConfigReference → Name, Guid
 *                                              → StartTime, EndTime, PresetTime
 *                                              → EnergySpectrum → NumberOfChannels
 *                                                               → ChannelPitch
 *                                                               → EnergyCalibration
 *                                                                 → PolynomialOrder
 *                                                                 → Coefficients → Coefficient…
 *                                                               → ValidPulseCount, TotalPulseCount
 *                                                               → MeasurementTime, LiveTime
 *                                                               → Spectrum → DataPoint…
 *                                              → BackgroundEnergySpectrum (та же схема)
 * ```
 *
 * Единицы и соглашения, подтверждённые кодом BecqMoni:
 *
 * - `MeasurementTime` — ПОЛНОЕ время (real time) в секундах;
 * - `LiveTime` — живое время в секундах, появилось позже и есть не во всех
 *   файлах (в файлах RadiaCode его нет вовсе);
 * - калибровка — многочлен по возрастанию степени,
 *   E(кэВ) = c0 + c1·ch + c2·ch² + …, `PolynomialOrder` от 1 до 4, а
 *   коэффициентов ровно `PolynomialOrder + 1`;
 * - один `<DataPoint>` на канал, целое число отсчётов.
 *
 * [RcXml] читает файлы того же корня: RadiaCode пишет ровно этот формат, а не
 * производный от него. Отдельный разборщик нужен из-за полей, которых в файлах
 * RadiaCode нет: живое время, счёт импульсов, шаг канала и степень многочлена
 * выше второй.
 *
 * Разбор терпим к необязательному (нет пробы, нет фона, нет живого времени,
 * незнакомые элементы) и отказывает там, где иначе пришлось бы догадываться:
 * чужой корень, нет каналов, нет ни одного времени, калибровка степени выше
 * второй с ненулевым старшим коэффициентом — приложение считает E по
 * квадратичной формуле, и молча отброшенный член при ch³ сместил бы энергии.
 */
object BecqMoniXml {

    /** Строковая константа формата; в файлах BecqMoni и RadiaCode она одна. */
    const val FORMAT_VERSION = "120920"

    /** Многочлен выше этой степени приложение вычислить не может. */
    private const val MAX_ORDER = 2

    fun parse(xml: String, zone: ZoneId = ZoneId.systemDefault()): BecqMoniParseResult {
        val warnings = mutableListOf<String>()
        val root = parseDocument(xml).documentElement
            ?: throw BecqMoniXmlException("пустой XML-документ")
        if (root.tagName != "ResultDataFile") {
            throw BecqMoniXmlException(
                "это не файл спектра BecqMoni: корневой элемент <${root.tagName}>",
            )
        }

        val formatVersion = child(root, "FormatVersion")?.let { text(it).trim() }
        if (formatVersion != null && formatVersion != FORMAT_VERSION) {
            warnings += "формат версии $formatVersion — файл прочитан по правилам версии " +
                "$FORMAT_VERSION, проверьте результат"
        }

        val list = child(root, "ResultDataList") ?: root
        val results = children(list, "ResultData")
        if (results.isEmpty()) throw BecqMoniXmlException("в файле нет элемента ResultData")
        if (results.size > 1) {
            warnings += "в файле ${results.size} записей — импортирована первая"
        }
        val result = results.first()

        val sampleInfo = child(result, "SampleInfo")
        val spectrum = parseSpectrum(
            child(result, "EnergySpectrum")
                ?: throw BecqMoniXmlException("в файле нет элемента EnergySpectrum"),
            "спектр",
            warnings,
        )
        val background = child(result, "BackgroundEnergySpectrum")?.let { element ->
            try {
                parseSpectrum(element, "фоновый спектр", warnings)
            } catch (e: BecqMoniXmlException) {
                warnings += "фоновый спектр не прочитан (${e.message}) — импортирован только " +
                    "основной"
                null
            }
        }

        return BecqMoniParseResult(
            data = BecqMoniResult(
                deviceName = child(result, "DeviceConfigReference")?.let { child(it, "Name") }
                    ?.let { text(it).trim().ifEmpty { null } },
                sampleName = sampleInfo?.let { child(it, "Name") }
                    ?.let { text(it).trim().ifEmpty { null } },
                sampleLocation = sampleInfo?.let { child(it, "Location") }
                    ?.let { text(it).trim().ifEmpty { null } },
                sampleNote = sampleInfo?.let { child(it, "Note") }
                    ?.let { text(it).trim().ifEmpty { null } },
                startMillis = parseTime(child(result, "StartTime"), zone, "StartTime", warnings),
                endMillis = parseTime(child(result, "EndTime"), zone, "EndTime", warnings),
                spectrum = spectrum,
                background = background,
            ),
            warnings = warnings,
        )
    }

    private fun parseSpectrum(
        element: Element,
        role: String,
        warnings: MutableList<String>,
    ): BecqMoniSpectrum {
        val counts = (child(element, "Spectrum")?.let { children(it, "DataPoint") }
            ?: throw BecqMoniXmlException("$role: нет элемента Spectrum с каналами"))
            .mapIndexed { index, point ->
                val raw = text(point).trim()
                raw.toLongOrNull()?.toInt()
                    ?: raw.toDoubleOrNull()?.roundToInt()
                    ?: throw BecqMoniXmlException("$role: канал №$index не число («$raw»)")
            }
        if (counts.isEmpty()) throw BecqMoniXmlException("$role: нет каналов (DataPoint)")

        val declared = child(element, "NumberOfChannels")?.let { text(it).trim().toIntOrNull() }
        if (declared != null && declared != counts.size) {
            warnings += "$role: заявлено $declared каналов, в файле ${counts.size} — " +
                "использованы фактические"
        }

        // MeasurementTime — полное время, LiveTime — живое; оба в секундах.
        val realSeconds = seconds(element, "MeasurementTime", role, warnings)
        val liveSeconds = seconds(element, "LiveTime", role, warnings)
        if (realSeconds == null && liveSeconds == null) {
            throw BecqMoniXmlException(
                "$role: нет ни MeasurementTime, ни LiveTime — длительность измерения " +
                    "неизвестна, счёт нельзя перевести в имп/с",
            )
        }
        if (liveSeconds == null) {
            warnings += "$role: живого времени (LiveTime) в файле нет — известно только полное " +
                "время $realSeconds с"
        } else if (realSeconds != null && liveSeconds > realSeconds) {
            warnings += "$role: живое время $liveSeconds с больше полного $realSeconds с — " +
                "значения взяты как есть"
        }

        // ChannelPitch — ширина амплитудного бина звукового МКА; у RadiaCode и
        // Atom Spectra равен 1. Иной шаг мы не пересчитываем, а называем.
        val channelPitch = child(element, "ChannelPitch")?.let { text(it).trim().toDoubleOrNull() }
        if (channelPitch != null && abs(channelPitch - 1.0) > 1e-9) {
            warnings += "$role: шаг канала $channelPitch — шкала прибора не совпадает с номером " +
                "канала, энергии считаны по калибровке файла"
        }

        return BecqMoniSpectrum(
            counts = counts,
            realSeconds = realSeconds,
            liveSeconds = liveSeconds,
            calibration = parseCalibration(element, role, warnings),
            channelPitch = channelPitch,
            validPulseCount = child(element, "ValidPulseCount")
                ?.let { text(it).trim().toLongOrNull() },
            totalPulseCount = child(element, "TotalPulseCount")
                ?.let { text(it).trim().toLongOrNull() },
        )
    }

    private fun parseCalibration(
        element: Element,
        role: String,
        warnings: MutableList<String>,
    ): BecqCalibration? {
        val calibration = child(element, "EnergyCalibration")
        val values = calibration?.let { child(it, "Coefficients") }
            ?.let { children(it, "Coefficient") }
            ?.mapIndexed { index, c ->
                val raw = text(c).trim()
                raw.toDoubleOrNull()
                    ?: throw BecqMoniXmlException(
                        "$role: коэффициент калибровки №${index + 1} не число («$raw»)",
                    )
            }
            .orEmpty()
        if (values.isEmpty()) {
            warnings += "$role: калибровки энергии в файле нет — шкала осталась в каналах"
            return null
        }

        val declaredOrder = calibration?.let { child(it, "PolynomialOrder") }
            ?.let { text(it).trim().toIntOrNull() }
        if (declaredOrder != null && declaredOrder != values.size - 1) {
            warnings += "$role: заявлена степень $declaredOrder, коэффициентов ${values.size} — " +
                "использованы фактические"
        }

        // Отброшенный ненулевой член при ch³ и выше сместил бы все энергии,
        // поэтому такой файл не читается, а называется неподдержанным.
        val extra = values.drop(MAX_ORDER + 1)
        if (extra.any { it != 0.0 }) {
            throw BecqMoniXmlException(
                "$role: калибровка степени ${values.size - 1} не поддержана — приложение " +
                    "считает E = a0 + a1·ch + a2·ch², а отбросить член при ch³ нельзя",
            )
        }
        return BecqCalibration(
            a0 = values[0],
            a1 = values.getOrElse(1) { 0.0 },
            a2 = values.getOrElse(2) { 0.0 },
        )
    }

    /** @return секунды из элемента [name] или null, если элемента нет. */
    private fun seconds(
        parent: Element,
        name: String,
        role: String,
        warnings: MutableList<String>,
    ): Double? {
        val raw = child(parent, name)?.let { text(it).trim() }?.ifEmpty { null } ?: return null
        val value = raw.toDoubleOrNull()
        if (value == null || value <= 0.0) {
            warnings += "$role: $name «$raw» не является положительным числом секунд — " +
                "время не импортировано"
            return null
        }
        return value
    }

    private fun parseTime(
        element: Element?,
        zone: ZoneId,
        label: String,
        warnings: MutableList<String>,
    ): Long? {
        val raw = element?.let { text(it).trim() }?.ifEmpty { null } ?: return null
        // BecqMoni пишет время со смещением зоны, RadiaCode — локальное без неё.
        runCatching { return OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(zone).toInstant().toEpochMilli()
        }
        warnings += "$label «$raw» не распознано — время не импортировано"
        return null
    }

    /**
     * Разборщик XML с теми же ограничениями, что в [RcXml]: DTD и внешние
     * сущности выключены (поверхность XXE), каждая необязательная настройка —
     * отдельным `runCatching`, потому что реализация JAXP на Android бросает
     * `UnsupportedOperationException` там, где настольная JVM молча работает.
     */
    private fun parseDocument(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        // Формат без пространств имён: теги читаются как есть.
        factory.isNamespaceAware = false
        runCatching { factory.isExpandEntityReferences = false }
        runCatching { factory.isXIncludeAware = false }
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return try {
            factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        } catch (e: UnsupportedOperationException) {
            // Ловится ПЕРВЫМ: UOE — это RuntimeException, иначе ветка мертва.
            throw BecqMoniXmlException("разборщик XML этого устройства отказал: ${e.message}", e)
        } catch (e: Exception) {
            throw BecqMoniXmlException("файл не является корректным XML: ${e.message}", e)
        }
    }

    private fun child(parent: Element, name: String): Element? {
        var node = parent.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE && (node as Element).tagName == name) {
                return node
            }
            node = node.nextSibling
        }
        return null
    }

    private fun children(parent: Element, name: String): List<Element> {
        val result = mutableListOf<Element>()
        var node = parent.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE && (node as Element).tagName == name) {
                result += node
            }
            node = node.nextSibling
        }
        return result
    }

    /** Текст и CDATA элемента: BecqMoni пишет имя и заметку пробы через CDATA. */
    private fun text(element: Element): String {
        val sb = StringBuilder()
        var node = element.firstChild
        while (node != null) {
            if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                sb.append(node.nodeValue)
            }
            node = node.nextSibling
        }
        return sb.toString()
    }
}

/** Калибровка энергии: E(кэВ) = a0 + a1·ch + a2·ch². */
data class BecqCalibration(val a0: Double, val a1: Double, val a2: Double)

/** Спектр BecqMoni (EnergySpectrum или BackgroundEnergySpectrum). */
data class BecqMoniSpectrum(
    val counts: List<Int>,
    /** Полное время накопления (MeasurementTime), секунды; null если его нет. */
    val realSeconds: Double?,
    /** Живое время (LiveTime), секунды; null — файл его не содержит. */
    val liveSeconds: Double?,
    /** null, если калибровки в файле нет: шкала остаётся в каналах. */
    val calibration: BecqCalibration?,
    /** ChannelPitch — ширина амплитудного бина; у RadiaCode 1, часто отсутствует. */
    val channelPitch: Double?,
    /** Импульсы, попавшие в спектр; null если поля нет (файлы RadiaCode). */
    val validPulseCount: Long?,
    /** Импульсы, зарегистрированные всего; разница с valid — мёртвое время. */
    val totalPulseCount: Long?,
) {
    val channelCount: Int get() = counts.size
}

/** Одна запись ResultData файла BecqMoni. */
data class BecqMoniResult(
    val deviceName: String?,
    val sampleName: String?,
    val sampleLocation: String?,
    val sampleNote: String?,
    /** epoch millis; в файле время либо со смещением зоны, либо локальное. */
    val startMillis: Long?,
    val endMillis: Long?,
    val spectrum: BecqMoniSpectrum,
    val background: BecqMoniSpectrum?,
)

data class BecqMoniParseResult(val data: BecqMoniResult, val warnings: List<String>)

/** Файл BecqMoni, который нельзя прочитать без догадок. */
class BecqMoniXmlException(message: String, cause: Throwable? = null) : Exception(message, cause)
