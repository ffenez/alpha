package app.alpha.data.export

import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * RadiaCode XML spectrum format («RC-XML», FormatVersion 120920) — the format
 * the official RadiaCode app exports and community tools exchange.
 *
 * Clean-room implementation from the published format structure (element
 * names and semantics observed in real exported files); no code from other
 * implementations. Energy calibration is the same quadratic the device
 * reports: E(keV) = a0 + a1·ch + a2·ch².
 *
 * Times inside the file are local wall-clock without a zone designator
 * (`yyyy-MM-dd'T'HH:mm:ss`), as in official exports — [write]/[parse] take an
 * explicit [ZoneId] to convert from/to epoch millis deterministically.
 *
 * The parser is tolerant by design: optional elements may be missing, text
 * may be CDATA or plain, unknown elements are ignored, a foreign
 * FormatVersion produces a warning instead of a failure. Only structurally
 * unusable input (no spectrum, unreadable channel data) throws
 * [RcXmlException].
 */
object RcXml {

    const val FORMAT_VERSION = 120920

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    // --- writing ---

    fun write(data: RcResultData, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder(64 * 1024)
        sb.append("<?xml version=\"1.0\"?>\n")
        sb.append(
            "<ResultDataFile xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n",
        )
        sb.append("  <FormatVersion>").append(FORMAT_VERSION).append("</FormatVersion>\n")
        sb.append("  <ResultDataList>\n")
        sb.append("    <ResultData>\n")
        data.deviceModel?.let {
            sb.append("      <DeviceConfigReference>\n")
            sb.append("        <Name>").append(escape(it)).append("</Name>\n")
            sb.append("      </DeviceConfigReference>\n")
        }
        sb.append("      <SampleInfo>\n")
        sb.append("        <Name>").append(cdata(data.sampleName.orEmpty())).append("</Name>\n")
        sb.append("        <Note>").append(cdata(data.sampleNote.orEmpty())).append("</Note>\n")
        sb.append("      </SampleInfo>\n")
        data.startMillis?.let {
            sb.append("      <StartTime>").append(formatTime(it, zone)).append("</StartTime>\n")
        }
        data.endMillis?.let {
            sb.append("      <EndTime>").append(formatTime(it, zone)).append("</EndTime>\n")
        }
        appendSpectrum(sb, "EnergySpectrum", data.spectrum)
        data.background?.let { appendSpectrum(sb, "BackgroundEnergySpectrum", it) }
        sb.append("      <Visible>true</Visible>\n")
        sb.append("    </ResultData>\n")
        sb.append("  </ResultDataList>\n")
        sb.append("</ResultDataFile>\n")
        return sb.toString()
    }

    private fun appendSpectrum(sb: StringBuilder, tag: String, s: RcSpectrum) {
        sb.append("      <").append(tag).append(">\n")
        sb.append("        <NumberOfChannels>").append(s.counts.size)
            .append("</NumberOfChannels>\n")
        sb.append("        <ChannelPitch>1</ChannelPitch>\n")
        sb.append("        <SpectrumName>").append(cdata(s.name.orEmpty()))
            .append("</SpectrumName>\n")
        s.serialNumber?.let {
            sb.append("        <SerialNumber>").append(escape(it)).append("</SerialNumber>\n")
        }
        sb.append("        <EnergyCalibration>\n")
        sb.append("          <PolynomialOrder>2</PolynomialOrder>\n")
        sb.append("          <Coefficients>\n")
        for (c in listOf(s.a0, s.a1, s.a2)) {
            // Float.toString is locale-independent; E-notation is accepted
            // by every known consumer (official files use it for a2).
            sb.append("            <Coefficient>").append(c).append("</Coefficient>\n")
        }
        sb.append("          </Coefficients>\n")
        sb.append("        </EnergyCalibration>\n")
        // MeasurementTime is in SECONDS per the format.
        sb.append("        <MeasurementTime>").append(s.measurementSeconds)
            .append("</MeasurementTime>\n")
        sb.append("        <Spectrum>\n")
        for (count in s.counts) {
            sb.append("          <DataPoint>").append(count).append("</DataPoint>\n")
        }
        sb.append("        </Spectrum>\n")
        sb.append("      </").append(tag).append(">\n")
    }

    private fun formatTime(epochMillis: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
            .format(TIME_FORMAT)

    private fun escape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }

    /** CDATA section; a literal `]]>` inside the text splits into two sections. */
    private fun cdata(text: String): String =
        "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"

    // --- parsing ---

    fun parse(xml: String, zone: ZoneId = ZoneId.systemDefault()): RcParseResult {
        val warnings = mutableListOf<String>()
        val document = parseDocument(xml)
        val root = document.documentElement
            ?: throw RcXmlException("пустой XML-документ")
        if (root.tagName != "ResultDataFile") {
            throw RcXmlException(
                "это не файл спектра RadiaCode: корневой элемент <${root.tagName}>",
            )
        }

        val formatVersion = child(root, "FormatVersion")?.let { text(it).trim() }
        if (formatVersion == null) {
            warnings += "нет FormatVersion — файл прочитан по правилам версии $FORMAT_VERSION"
        } else if (formatVersion != FORMAT_VERSION.toString()) {
            warnings += "формат версии $formatVersion — файл прочитан по правилам " +
                "версии $FORMAT_VERSION, проверьте результат"
        }

        val list = child(root, "ResultDataList") ?: root
        val resultDatas = children(list, "ResultData")
        if (resultDatas.isEmpty()) throw RcXmlException("в файле нет элемента ResultData")
        if (resultDatas.size > 1) {
            warnings += "в файле ${resultDatas.size} записей — импортирована первая"
        }
        val result = resultDatas.first()

        val deviceModel = child(result, "DeviceConfigReference")
            ?.let { child(it, "Name") }?.let { text(it).trim().ifEmpty { null } }
        val sampleInfo = child(result, "SampleInfo")
        val sampleName = sampleInfo?.let { child(it, "Name") }
            ?.let { text(it).trim().ifEmpty { null } }
        val sampleNote = sampleInfo?.let { child(it, "Note") }
            ?.let { text(it).trim().ifEmpty { null } }

        val startMillis = parseTime(child(result, "StartTime"), zone, "StartTime", warnings)
        val endMillis = parseTime(child(result, "EndTime"), zone, "EndTime", warnings)

        val energySpectrum = child(result, "EnergySpectrum")
            ?: throw RcXmlException("в файле нет элемента EnergySpectrum")
        val spectrum = parseSpectrum(energySpectrum, "спектр", warnings)

        val background = child(result, "BackgroundEnergySpectrum")?.let { element ->
            try {
                parseSpectrum(element, "фоновый спектр", warnings)
            } catch (e: RcXmlException) {
                warnings += "фоновый спектр не прочитан (${e.message}) — импортирован " +
                    "только основной"
                null
            }
        }

        return RcParseResult(
            data = RcResultData(
                deviceModel = deviceModel,
                sampleName = sampleName,
                sampleNote = sampleNote,
                startMillis = startMillis,
                endMillis = endMillis,
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
    ): RcSpectrum {
        val name = child(element, "SpectrumName")?.let { text(it).trim().ifEmpty { null } }
        val serial = child(element, "SerialNumber")?.let { text(it).trim().ifEmpty { null } }

        val coefficients = child(element, "EnergyCalibration")
            ?.let { child(it, "Coefficients") }
            ?.let { children(it, "Coefficient") }
            ?.mapIndexed { index, c ->
                val raw = text(c).trim()
                raw.toDoubleOrNull()
                    ?: throw RcXmlException("$role: коэффициент калибровки №${index + 1} " +
                        "не число («$raw»)")
            }
            ?: emptyList()
        val calibration = when {
            coefficients.isEmpty() -> {
                warnings += "$role: калибровка энергии отсутствует — принято E = канал (кэВ)"
                listOf(0.0, 1.0, 0.0)
            }
            coefficients.size < 3 -> {
                warnings += "$role: калибровка порядка ${coefficients.size - 1} — " +
                    "недостающие коэффициенты приняты нулевыми"
                coefficients + List(3 - coefficients.size) { 0.0 }
            }
            coefficients.size > 3 -> {
                warnings += "$role: калибровка порядка ${coefficients.size - 1} " +
                    "не поддержана — использованы первые три коэффициента"
                coefficients.take(3)
            }
            else -> coefficients
        }

        val measurementSeconds = child(element, "MeasurementTime")?.let { node ->
            val raw = text(node).trim()
            val value = raw.toDoubleOrNull()
            if (value == null || value < 0) {
                warnings += "$role: время измерения нечитаемо («$raw») — принято 0 с"
                0L
            } else {
                Math.round(value)
            }
        } ?: run {
            warnings += "$role: время измерения отсутствует — принято 0 с"
            0L
        }

        val dataPoints = child(element, "Spectrum")?.let { children(it, "DataPoint") }
            ?: throw RcXmlException("$role: нет элемента Spectrum с каналами")
        if (dataPoints.isEmpty()) throw RcXmlException("$role: нет каналов (DataPoint)")
        val counts = dataPoints.mapIndexed { index, point ->
            val raw = text(point).trim()
            raw.toLongOrNull()?.toInt()
                ?: raw.toDoubleOrNull()?.roundToInt()
                ?: throw RcXmlException("$role: канал №${index + 1} не число («$raw»)")
        }

        val declaredChannels = child(element, "NumberOfChannels")?.let { text(it).trim().toIntOrNull() }
        if (declaredChannels != null && declaredChannels != counts.size) {
            warnings += "$role: заявлено $declaredChannels каналов, в файле ${counts.size} — " +
                "использованы фактические"
        }

        return RcSpectrum(
            name = name,
            serialNumber = serial,
            a0 = calibration[0].toFloat(),
            a1 = calibration[1].toFloat(),
            a2 = calibration[2].toFloat(),
            measurementSeconds = measurementSeconds,
            counts = counts,
        )
    }

    private fun parseTime(
        element: Element?,
        zone: ZoneId,
        label: String,
        warnings: MutableList<String>,
    ): Long? {
        val raw = element?.let { text(it).trim() }?.ifEmpty { null } ?: return null
        // Official files carry local time without a zone; tolerate an offset too.
        runCatching {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(zone).toInstant().toEpochMilli()
        }
        runCatching { return OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        warnings += "$label «$raw» не распознано — время не импортировано"
        return null
    }

    /**
     * Разборщик XML, настроенный так, чтобы работать НА УСТРОЙСТВЕ.
     *
     * ## Полевой дефект, из-за которого падал любой импорт
     *
     * `factory.isXIncludeAware = false` роняло приложение на телефоне с
     * `UnsupportedOperationException: This parser does not support
     * specification "Unknown" version "0.0"`. Причина в том, что базовый класс
     * JAXP `DocumentBuilderFactory` реализует `setXIncludeAware` броском
     * исключения, а реализация Android этот метод НЕ переопределяет — в
     * отличие от Xerces на настольной JVM, где он работает.
     *
     * Отсюда же главный урок: **JVM-тесты этот класс ошибок не ловят** — на
     * настольной машине тот же файл разбирался идеально, и падало только на
     * приборе. Поэтому каждая необязательная настройка разборщика вызывается
     * ОТДЕЛЬНО и в `runCatching`: она укрепляет разбор, но не имеет права его
     * ломать. Обязательным остаётся только то, без чего разбор неверен.
     */
    private fun parseDocument(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        // Обязательное: формат без пространств имён, и мы читаем теги как есть.
        factory.isNamespaceAware = false
        // Дальше — упрочнение. Каждый вызов свой: одна неподдержанная
        // настройка не должна утаскивать за собой остальные.
        runCatching { factory.isExpandEntityReferences = false }
        runCatching { factory.isXIncludeAware = false }
        // DTD в этом формате нет; отказ от них закрывает поверхность XXE.
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
            // Ловится ПЕРВЫМ, иначе ветка мертва (UOE — это RuntimeException).
            // Разборщик устройства может не поддержать что-то ещё: это наша
            // среда, а не плохой файл, — и падать приложение всё равно не
            // должно.
            throw RcXmlException("разборщик XML этого устройства отказал: ${e.message}", e)
        } catch (e: Exception) {
            throw RcXmlException("файл не является корректным XML: ${e.message}", e)
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

    /** Concatenated text + CDATA content of an element (both are legal here). */
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

/** One energy spectrum block (EnergySpectrum / BackgroundEnergySpectrum). */
data class RcSpectrum(
    val name: String?,
    val serialNumber: String?,
    /** E(keV) = a0 + a1·ch + a2·ch². */
    val a0: Float,
    val a1: Float,
    val a2: Float,
    /** Accumulation live time, seconds (format field MeasurementTime). */
    val measurementSeconds: Long,
    val counts: List<Int>,
)

/** One ResultData entry of an RC-XML file. */
data class RcResultData(
    /** DeviceConfigReference/Name, e.g. «RadiaCode-102». */
    val deviceModel: String?,
    val sampleName: String?,
    val sampleNote: String?,
    /** Epoch millis (file stores local wall time; converted via the zone). */
    val startMillis: Long?,
    val endMillis: Long?,
    val spectrum: RcSpectrum,
    val background: RcSpectrum?,
)

data class RcParseResult(val data: RcResultData, val warnings: List<String>)

/** Structurally unusable RC-XML input (parse gave up honestly). */
class RcXmlException(message: String, cause: Throwable? = null) : Exception(message, cause)
