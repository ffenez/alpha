package app.alpha.data.export

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcXmlTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun sampleData(background: RcSpectrum? = null) = RcResultData(
        deviceModel = "RadiaCode-110",
        sampleName = "Проба №1 <гранит>",
        sampleNote = "заметка",
        startMillis = ZonedDateTime.of(2026, 8, 9, 10, 0, 0, 0, zone).toInstant().toEpochMilli(),
        endMillis = ZonedDateTime.of(2026, 8, 9, 12, 30, 45, 0, zone).toInstant().toEpochMilli(),
        spectrum = RcSpectrum(
            name = "Проба №1 <гранит>",
            serialNumber = "RC-110-001234",
            a0 = -5.5f,
            a1 = 2.4168f,
            a2 = 3.9769E-4f,
            measurementSeconds = 9045L,
            counts = List(1024) { (it * 7) % 100 },
        ),
        background = background,
    )

    private fun backgroundSpectrum() = RcSpectrum(
        name = "фон",
        serialNumber = "RC-110-001234",
        a0 = -5.5f,
        a1 = 2.4168f,
        a2 = 3.9769E-4f,
        measurementSeconds = 3600L,
        counts = List(1024) { it % 5 },
    )

    // --- roundtrip ---

    @Test
    fun `write then parse preserves every field`() {
        val data = sampleData(background = backgroundSpectrum())
        val xml = RcXml.write(data, zone)
        val parsed = RcXml.parse(xml, zone)

        assertEquals(emptyList(), parsed.warnings)
        assertEquals(data, parsed.data)
    }

    @Test
    fun `roundtrip without optional fields`() {
        val data = RcResultData(
            deviceModel = null,
            sampleName = null,
            sampleNote = null,
            startMillis = null,
            endMillis = null,
            spectrum = RcSpectrum(
                name = null,
                serialNumber = null,
                a0 = 0f,
                a1 = 2.4f,
                a2 = 0f,
                measurementSeconds = 60L,
                counts = listOf(1, 2, 3, 4),
            ),
            background = null,
        )
        val parsed = RcXml.parse(RcXml.write(data, zone), zone)
        assertEquals(emptyList(), parsed.warnings)
        assertEquals(data, parsed.data)
    }

    @Test
    fun `cdata terminator inside sample name survives roundtrip`() {
        val data = sampleData().let {
            it.copy(sampleName = "a]]>b", spectrum = it.spectrum.copy(name = "a]]>b"))
        }
        val parsed = RcXml.parse(RcXml.write(data, zone), zone)
        assertEquals("a]]>b", parsed.data.sampleName)
        assertEquals("a]]>b", parsed.data.spectrum.name)
    }

    // --- written structure fidelity (what other tools will read) ---

    @Test
    fun `written file carries the canonical structure`() {
        val xml = RcXml.write(sampleData(background = backgroundSpectrum()), zone)

        assertTrue(xml.startsWith("<?xml version=\"1.0\"?>"))
        assertTrue("<ResultDataFile" in xml)
        assertTrue("<FormatVersion>120920</FormatVersion>" in xml)
        assertTrue("<ResultDataList>" in xml)
        assertTrue("<DeviceConfigReference>" in xml)
        assertTrue("<Name>RadiaCode-110</Name>" in xml)
        assertTrue("<SampleInfo>" in xml)
        assertTrue("<Name><![CDATA[Проба №1 <гранит>]]></Name>" in xml)
        assertTrue("<StartTime>2026-08-09T10:00:00</StartTime>" in xml)
        assertTrue("<EndTime>2026-08-09T12:30:45</EndTime>" in xml)
        assertTrue("<NumberOfChannels>1024</NumberOfChannels>" in xml)
        assertTrue("<ChannelPitch>1</ChannelPitch>" in xml)
        assertTrue("<SerialNumber>RC-110-001234</SerialNumber>" in xml)
        assertTrue("<PolynomialOrder>2</PolynomialOrder>" in xml)
        // MeasurementTime is seconds — the classic ms/s confusion is the known
        // community bug; 9045 s must be written as-is.
        assertTrue("<MeasurementTime>9045</MeasurementTime>" in xml)
        assertTrue("<BackgroundEnergySpectrum>" in xml)
        assertTrue("<MeasurementTime>3600</MeasurementTime>" in xml)
        assertTrue("<Visible>true</Visible>" in xml)
        assertEquals(2048, Regex("<DataPoint>").findAll(xml).count())
    }

    @Test
    fun `written coefficients keep order a0 a1 a2`() {
        val xml = RcXml.write(sampleData(), zone)
        val coefficients = Regex("<Coefficient>([^<]+)</Coefficient>")
            .findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(3, coefficients.size)
        assertEquals(-5.5, coefficients[0].toDouble(), 1e-6)
        assertEquals(2.4168, coefficients[1].toDouble(), 1e-6)
        assertEquals(3.9769E-4, coefficients[2].toDouble(), 1e-9)
    }

    // --- tolerant parsing of real-world files ---

    /** Mirrors the structure quirks of real exported files. */
    private fun realWorldXml(
        formatVersion: String = "120920",
        calibrationBlock: String = """
            <EnergyCalibration>
              <PolynomialOrder>2</PolynomialOrder>
              <Coefficients>
                <Coefficient>-11.3706</Coefficient>
                <Coefficient>2.46757</Coefficient>
                <Coefficient>3.9769E-4</Coefficient>
              </Coefficients>
            </EnergyCalibration>
        """,
        measurementTime: String = "<MeasurementTime>172800</MeasurementTime>",
        channels: String = "<NumberOfChannels>4</NumberOfChannels>",
        backgroundBlock: String = "",
        extraResultData: String = "",
    ) = """
        <?xml version="1.0"?>
        <ResultDataFile xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <FormatVersion>$formatVersion</FormatVersion>
          <ResultDataList>
            <ResultData>
              <DeviceConfigReference>
                <Name>RadiaCode-102</Name>
              </DeviceConfigReference>
              <SampleInfo>
                <Name><![CDATA[Th-232-BG]]></Name>
                <Note><![CDATA[]]></Note>
              </SampleInfo>
              <BackgroundSpectrumFile>BG 35hr</BackgroundSpectrumFile>

               <StartTime>2023-10-17T22:30:56</StartTime>
         <EndTime>2023-10-19T22:30:56</EndTime>

         <EnergySpectrum>
          $channels
          <ChannelPitch>1</ChannelPitch>
          <SpectrumName><![CDATA[Th-232-BG]]></SpectrumName>
          <Comment></Comment>
          <SerialNumber>RC-102-000115</SerialNumber>
          $calibrationBlock
          $measurementTime
          <Spectrum>
            <DataPoint>859</DataPoint>
        <DataPoint>5770</DataPoint>
        <DataPoint>2875</DataPoint>
        <DataPoint>1340</DataPoint>
          </Spectrum>
         </EnergySpectrum>
         $backgroundBlock
              <Visible>true</Visible>
              <PulseCollection>
                <Format>Base64 encoded binary</Format>
                <Pulses />
              </PulseCollection>
            </ResultData>
            $extraResultData
          </ResultDataList>
        </ResultDataFile>
    """.trimIndent()

    @Test
    fun `parses a real-world shaped file`() {
        val parsed = RcXml.parse(realWorldXml(), zone)

        assertEquals(emptyList(), parsed.warnings)
        val data = parsed.data
        assertEquals("RadiaCode-102", data.deviceModel)
        assertEquals("Th-232-BG", data.sampleName)
        assertNull(data.sampleNote)
        assertEquals(
            ZonedDateTime.of(2023, 10, 17, 22, 30, 56, 0, zone).toInstant().toEpochMilli(),
            data.startMillis,
        )
        assertEquals(
            ZonedDateTime.of(2023, 10, 19, 22, 30, 56, 0, zone).toInstant().toEpochMilli(),
            data.endMillis,
        )
        assertEquals("Th-232-BG", data.spectrum.name)
        assertEquals("RC-102-000115", data.spectrum.serialNumber)
        assertEquals(-11.3706f, data.spectrum.a0, 1e-4f)
        assertEquals(2.46757f, data.spectrum.a1, 1e-5f)
        assertEquals(3.9769E-4f, data.spectrum.a2, 1e-8f)
        assertEquals(172800L, data.spectrum.measurementSeconds)
        assertEquals(listOf(859, 5770, 2875, 1340), data.spectrum.counts)
        assertNull(data.background)
    }

    @Test
    fun `parses background spectrum block`() {
        val background = """
         <BackgroundEnergySpectrum>
          <NumberOfChannels>4</NumberOfChannels>
          <ChannelPitch>1</ChannelPitch>
          <SpectrumName><![CDATA[BG 35hr]]></SpectrumName>
          <SerialNumber>RC-102-000115</SerialNumber>
          <EnergyCalibration>
            <PolynomialOrder>2</PolynomialOrder>
            <Coefficients>
              <Coefficient>-11.3706</Coefficient>
              <Coefficient>2.46757</Coefficient>
              <Coefficient>3.9769E-4</Coefficient>
            </Coefficients>
          </EnergyCalibration>
          <MeasurementTime>127800</MeasurementTime>
          <Spectrum>
            <DataPoint>13</DataPoint>
            <DataPoint>93</DataPoint>
            <DataPoint>51</DataPoint>
            <DataPoint>28</DataPoint>
          </Spectrum>
         </BackgroundEnergySpectrum>
        """
        val parsed = RcXml.parse(realWorldXml(backgroundBlock = background), zone)

        assertEquals(emptyList(), parsed.warnings)
        val bg = assertNotNull(parsed.data.background)
        assertEquals("BG 35hr", bg.name)
        assertEquals(127800L, bg.measurementSeconds)
        assertEquals(listOf(13, 93, 51, 28), bg.counts)
    }

    @Test
    fun `plain text instead of CDATA parses too`() {
        val xml = realWorldXml().replace("<![CDATA[Th-232-BG]]>", "Th-232-BG")
        val parsed = RcXml.parse(xml, zone)
        assertEquals("Th-232-BG", parsed.data.sampleName)
        assertEquals("Th-232-BG", parsed.data.spectrum.name)
    }

    @Test
    fun `foreign format version warns but parses`() {
        val parsed = RcXml.parse(realWorldXml(formatVersion = "999999"), zone)
        assertEquals(listOf(859, 5770, 2875, 1340), parsed.data.spectrum.counts)
        assertTrue(parsed.warnings.any { "999999" in it })
    }

    @Test
    fun `missing calibration warns and defaults to channel equals keV`() {
        val parsed = RcXml.parse(realWorldXml(calibrationBlock = ""), zone)
        assertEquals(0f, parsed.data.spectrum.a0)
        assertEquals(1f, parsed.data.spectrum.a1)
        assertEquals(0f, parsed.data.spectrum.a2)
        assertTrue(parsed.warnings.any { "калибровка" in it })
    }

    @Test
    fun `first-order calibration pads missing coefficient`() {
        val block = """
            <EnergyCalibration>
              <PolynomialOrder>1</PolynomialOrder>
              <Coefficients>
                <Coefficient>0</Coefficient>
                <Coefficient>3.0</Coefficient>
              </Coefficients>
            </EnergyCalibration>
        """
        val parsed = RcXml.parse(realWorldXml(calibrationBlock = block), zone)
        assertEquals(0f, parsed.data.spectrum.a0)
        assertEquals(3f, parsed.data.spectrum.a1)
        assertEquals(0f, parsed.data.spectrum.a2)
        assertTrue(parsed.warnings.any { "порядка 1" in it })
    }

    @Test
    fun `missing measurement time warns and yields zero`() {
        val parsed = RcXml.parse(realWorldXml(measurementTime = ""), zone)
        assertEquals(0L, parsed.data.spectrum.measurementSeconds)
        assertTrue(parsed.warnings.any { "время измерения" in it })
    }

    @Test
    fun `channel count mismatch warns and keeps actual channels`() {
        val parsed = RcXml.parse(
            realWorldXml(channels = "<NumberOfChannels>1024</NumberOfChannels>"),
            zone,
        )
        assertEquals(4, parsed.data.spectrum.counts.size)
        assertTrue(parsed.warnings.any { "1024" in it && "4" in it })
    }

    @Test
    fun `multiple ResultData entries import the first with a warning`() {
        val second = """
            <ResultData>
              <EnergySpectrum>
                <Spectrum><DataPoint>1</DataPoint></Spectrum>
              </EnergySpectrum>
            </ResultData>
        """
        val parsed = RcXml.parse(realWorldXml(extraResultData = second), zone)
        assertEquals(listOf(859, 5770, 2875, 1340), parsed.data.spectrum.counts)
        assertTrue(parsed.warnings.any { "2 записей" in it })
    }

    @Test
    fun `broken background drops with warning, main spectrum survives`() {
        val broken = """
         <BackgroundEnergySpectrum>
          <MeasurementTime>10</MeasurementTime>
         </BackgroundEnergySpectrum>
        """
        val parsed = RcXml.parse(realWorldXml(backgroundBlock = broken), zone)
        assertNull(parsed.data.background)
        assertEquals(4, parsed.data.spectrum.counts.size)
        assertTrue(parsed.warnings.any { "фоновый спектр не прочитан" in it })
    }

    @Test
    fun `unparseable time warns and imports without it`() {
        val xml = realWorldXml().replace("2023-10-17T22:30:56", "17/10/2023")
        val parsed = RcXml.parse(xml, zone)
        assertNull(parsed.data.startMillis)
        assertTrue(parsed.warnings.any { "17/10/2023" in it })
    }

    @Test
    fun `offset time is accepted`() {
        val xml = realWorldXml().replace(
            "<StartTime>2023-10-17T22:30:56</StartTime>",
            "<StartTime>2023-10-17T22:30:56Z</StartTime>",
        )
        val parsed = RcXml.parse(xml, zone)
        assertEquals(
            ZonedDateTime.of(2023, 10, 17, 22, 30, 56, 0, ZoneId.of("UTC"))
                .toInstant().toEpochMilli(),
            parsed.data.startMillis,
        )
    }

    // --- honest failures ---

    @Test
    fun `foreign root element fails`() {
        assertFailsWith<RcXmlException> {
            RcXml.parse("<N42InstrumentData></N42InstrumentData>", zone)
        }
    }

    @Test
    fun `not xml fails`() {
        assertFailsWith<RcXmlException> { RcXml.parse("counts: 1 2 3", zone) }
    }

    @Test
    fun `missing spectrum fails`() {
        val xml = """
            <ResultDataFile>
              <FormatVersion>120920</FormatVersion>
              <ResultDataList><ResultData></ResultData></ResultDataList>
            </ResultDataFile>
        """.trimIndent()
        assertFailsWith<RcXmlException> { RcXml.parse(xml, zone) }
    }

    @Test
    fun `non numeric data point fails`() {
        val xml = realWorldXml().replace("<DataPoint>859</DataPoint>", "<DataPoint>abc</DataPoint>")
        assertFailsWith<RcXmlException> { RcXml.parse(xml, zone) }
    }
}
