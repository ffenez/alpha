package app.radiacode.data.export

import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class N42Test {

    private val zone = ZoneId.of("Europe/Moscow")
    private val ns = "http://physics.nist.gov/N42/2011/N42"
    private val uuid = UUID.fromString("00000000-0000-4000-8000-000000000042")

    private fun foreground() = N42.Measurement(
        classCode = N42.CLASS_FOREGROUND,
        startMillis = ZonedDateTime.of(2026, 8, 9, 12, 0, 0, 0, zone).toInstant().toEpochMilli(),
        durationSeconds = 600L,
        a0 = -5.5f,
        a1 = 2.4f,
        a2 = 4.0E-4f,
        counts = List(1024) { it % 7 },
    )

    private fun background() = foreground().copy(
        classCode = N42.CLASS_BACKGROUND,
        durationSeconds = 3600L,
        counts = List(1024) { it % 3 },
    )

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun childElements(parent: Element): List<Element> {
        val result = mutableListOf<Element>()
        var node = parent.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) result += node as Element
            node = node.nextSibling
        }
        return result
    }

    private fun child(parent: Element, name: String): Element? =
        childElements(parent).firstOrNull { it.localName == name }

    @Test
    fun `document is well-formed with the N42-2011 namespace`() {
        val document = parse(
            N42.write(foreground(), background(), "RC-110-000042", "RadiaCode-110",
                "0.1.0-alpha", zone, uuid),
        )
        val root = document.documentElement
        assertEquals("RadInstrumentData", root.localName)
        assertEquals(ns, root.namespaceURI)
        assertEquals(uuid.toString(), root.getAttribute("n42DocUUID"))
        // Every element of the document lives in the N42 namespace.
        fun check(element: Element) {
            assertEquals(ns, element.namespaceURI, element.localName)
            childElements(element).forEach { check(it) }
        }
        check(root)
    }

    @Test
    fun `top-level element order follows the schema sequence`() {
        val document = parse(
            N42.write(foreground(), background(), "RC-110-000042", "RadiaCode-110",
                "0.1.0-alpha", zone, uuid),
        )
        val names = childElements(document.documentElement).map { it.localName }
        assertEquals(
            listOf(
                "RadInstrumentDataCreatorName",
                "RadInstrumentInformation",
                "RadDetectorInformation",
                "EnergyCalibration",
                "EnergyCalibration",
                "RadMeasurement",
                "RadMeasurement",
            ),
            names,
        )
    }

    @Test
    fun `instrument block carries manufacturer, serial, model, class and software`() {
        val root = parse(
            N42.write(foreground(), null, "RC-110-000042", "RadiaCode-110",
                "0.1.0-alpha", zone, uuid),
        ).documentElement
        val info = assertNotNull(child(root, "RadInstrumentInformation"))

        assertEquals("RadiaCode", child(info, "RadInstrumentManufacturerName")?.textContent)
        assertEquals("RC-110-000042", child(info, "RadInstrumentIdentifier")?.textContent)
        assertEquals("RadiaCode-110", child(info, "RadInstrumentModelName")?.textContent)
        assertEquals(
            "Spectroscopic Personal Radiation Detector",
            child(info, "RadInstrumentClassCode")?.textContent,
        )
        val version = assertNotNull(child(info, "RadInstrumentVersion"))
        assertEquals("Software", child(version, "RadInstrumentComponentName")?.textContent)
        assertEquals(
            "0.1.0-alpha",
            child(version, "RadInstrumentComponentVersion")?.textContent,
        )
    }

    @Test
    fun `detector is a gamma CsI scintillator`() {
        val root = parse(N42.write(foreground(), zone = zone)).documentElement
        val detector = assertNotNull(child(root, "RadDetectorInformation"))
        assertEquals("detector-1", detector.getAttribute("id"))
        assertEquals("Gamma", child(detector, "RadDetectorCategoryCode")?.textContent)
        assertEquals("CsI", child(detector, "RadDetectorKindCode")?.textContent)
    }

    @Test
    fun `calibration coefficients are ordered a0 a1 a2`() {
        val root = parse(N42.write(foreground(), zone = zone)).documentElement
        val calibration = assertNotNull(child(root, "EnergyCalibration"))
        val values = assertNotNull(child(calibration, "CoefficientValues"))
            .textContent.trim().split(Regex("\\s+")).map { it.toDouble() }
        assertEquals(3, values.size)
        assertEquals(-5.5, values[0], 1e-6)
        assertEquals(2.4, values[1], 1e-6)
        assertEquals(4.0E-4, values[2], 1e-9)
    }

    @Test
    fun `measurement carries class code, start time and ISO durations`() {
        val root = parse(N42.write(foreground(), zone = zone)).documentElement
        val measurement = assertNotNull(child(root, "RadMeasurement"))
        assertEquals(
            "Foreground",
            child(measurement, "MeasurementClassCode")?.textContent,
        )
        assertEquals(
            "2026-08-09T12:00:00+03:00",
            child(measurement, "StartDateTime")?.textContent,
        )
        assertEquals("PT600S", child(measurement, "RealTimeDuration")?.textContent)
        val spectrum = assertNotNull(child(measurement, "Spectrum"))
        assertEquals("PT600S", child(spectrum, "LiveTimeDuration")?.textContent)
    }

    @Test
    fun `channel data holds every channel as plain integers`() {
        val root = parse(N42.write(foreground(), zone = zone)).documentElement
        val spectrum = assertNotNull(child(assertNotNull(child(root, "RadMeasurement")), "Spectrum"))
        val channelData = assertNotNull(child(spectrum, "ChannelData"))
        assertEquals("None", channelData.getAttribute("compressionCode"))
        val values = channelData.textContent.trim().split(Regex("\\s+")).map { it.toInt() }
        assertEquals(1024, values.size)
        assertEquals(List(1024) { it % 7 }, values)
    }

    @Test
    fun `spectrum references point at declared calibration and detector ids`() {
        val root = parse(N42.write(foreground(), background(), zone = zone)).documentElement
        val calibrationIds = childElements(root)
            .filter { it.localName == "EnergyCalibration" }
            .map { it.getAttribute("id") }
            .toSet()
        val measurements = childElements(root).filter { it.localName == "RadMeasurement" }
        assertEquals(2, measurements.size)
        for (measurement in measurements) {
            val spectrum = assertNotNull(child(measurement, "Spectrum"))
            assertEquals("detector-1", spectrum.getAttribute("radDetectorInformationReference"))
            assertTrue(spectrum.getAttribute("energyCalibrationReference") in calibrationIds)
            assertTrue(spectrum.getAttribute("id").isNotEmpty())
        }
        assertEquals(
            listOf("Foreground", "Background"),
            measurements.map { child(it, "MeasurementClassCode")?.textContent },
        )
    }

    @Test
    fun `omitted serial keeps the identifier out instead of inventing one`() {
        val root = parse(N42.write(foreground(), zone = zone)).documentElement
        val info = assertNotNull(child(root, "RadInstrumentInformation"))
        assertNull(child(info, "RadInstrumentIdentifier"))
    }

    @Test
    fun `background measurement times differ from foreground`() {
        val root = parse(N42.write(foreground(), background(), zone = zone)).documentElement
        val backgroundMeasurement = childElements(root)
            .filter { it.localName == "RadMeasurement" }[1]
        assertEquals(
            "PT3600S",
            child(backgroundMeasurement, "RealTimeDuration")?.textContent,
        )
    }

    @Test
    fun `snapshot mapping brackets the accumulation`() {
        val end = ZonedDateTime.of(2026, 8, 9, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val entity = app.radiacode.data.db.SpectrumSnapshotEntity(
            id = 1,
            timestamp = end,
            accumulated = false,
            durationSeconds = 600,
            a0 = -5.5f,
            a1 = 2.4f,
            a2 = 4.0E-4f,
            channelCount = 4,
            counts = app.radiacode.data.SpectrumBlob.encode(listOf(1, 2, 3, 4)),
        )
        val measurement = SpectrumExport.toN42Measurement(entity, N42.CLASS_FOREGROUND)
        assertEquals(end - 600_000L, measurement.startMillis)
        assertEquals(600L, measurement.durationSeconds)
        assertEquals(listOf(1, 2, 3, 4), measurement.counts)
    }
}
