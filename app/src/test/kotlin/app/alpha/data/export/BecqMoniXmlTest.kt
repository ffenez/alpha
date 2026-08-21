package app.alpha.data.export

import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BecqMoniXmlTest {

    private val zone = ZoneId.of("Europe/Moscow")

    /**
     * Файл в том виде, в каком его пишет BecqMoni (сокращён по числу каналов).
     * Структура — по образцу Atom Nano 3 из byBenPuls/spectrumconverter.
     */
    private fun file(
        calibration: String = """
            <EnergyCalibration>
              <PolynomialOrder>1</PolynomialOrder>
              <Coefficients>
                <Coefficient>-9.86440677966101</Coefficient>
                <Coefficient>2.7457627118644066</Coefficient>
              </Coefficients>
            </EnergyCalibration>
        """,
        times: String = """
            <MeasurementTime>1267.1013579</MeasurementTime>
            <LiveTime>1200.5</LiveTime>
        """,
        channels: String = "<NumberOfChannels>6</NumberOfChannels>",
    ) = """<?xml version="1.0"?>
<ResultDataFile xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <FormatVersion>120920</FormatVersion>
  <ResultDataList>
    <ResultData>
      <SampleInfo>
        <Name><![CDATA[Проба №1]]></Name>
        <Location><![CDATA[подвал]]></Location>
        <Time>2022-08-30T12:34:08.7842751+05:00</Time>
        <Weight>1</Weight>
        <Volume>1</Volume>
        <Note><![CDATA[заметка]]></Note>
      </SampleInfo>
      <DeviceConfigReference>
        <Name>Atom Nano 3</Name>
        <Guid>af57897a-90e6-495b-8574-8c06e793f333</Guid>
      </DeviceConfigReference>
      <BackgroundSpectrumFile />
      <StartTime>2022-08-30T12:40:42.3915298+05:00</StartTime>
      <EndTime>2022-08-30T13:00:48.7774373+05:00</EndTime>
      <PresetTime>360000</PresetTime>
      <EnergySpectrum>
        $channels
        <ChannelPitch>1</ChannelPitch>
        $calibration
        <ValidPulseCount>5042</ValidPulseCount>
        <TotalPulseCount>5243</TotalPulseCount>
        $times
        <NumberOfSamples>243194828</NumberOfSamples>
        <Spectrum>
          <DataPoint>0</DataPoint>
          <DataPoint>7</DataPoint>
          <DataPoint>19</DataPoint>
          <DataPoint>103</DataPoint>
          <DataPoint>5</DataPoint>
          <DataPoint>1</DataPoint>
        </Spectrum>
      </EnergySpectrum>
      <Visible>true</Visible>
      <PulseCollection>
        <Format>Base64 encoded binary</Format>
        <Pulses />
      </PulseCollection>
    </ResultData>
  </ResultDataList>
</ResultDataFile>
"""

    @Test
    fun `обычный файл прочитан целиком и без замечаний`() {
        val result = BecqMoniXml.parse(file(), zone)

        assertEquals(emptyList(), result.warnings)
        val data = result.data
        assertEquals("Atom Nano 3", data.deviceName)
        assertEquals("Проба №1", data.sampleName)
        assertEquals("подвал", data.sampleLocation)
        assertEquals("заметка", data.sampleNote)
        assertEquals(
            OffsetDateTime.parse("2022-08-30T12:40:42.3915298+05:00").toInstant().toEpochMilli(),
            data.startMillis,
        )
        assertEquals(
            OffsetDateTime.parse("2022-08-30T13:00:48.7774373+05:00").toInstant().toEpochMilli(),
            data.endMillis,
        )
        assertNull(data.background)

        val spectrum = data.spectrum
        assertEquals(6, spectrum.channelCount)
        assertEquals(listOf(0, 7, 19, 103, 5, 1), spectrum.counts)
        assertEquals(1267.1013579, spectrum.realSeconds)
        assertEquals(1200.5, spectrum.liveSeconds)
        assertEquals(5042L, spectrum.validPulseCount)
        assertEquals(5243L, spectrum.totalPulseCount)
        assertEquals(1.0, spectrum.channelPitch)
    }

    @Test
    fun `калибровка первой степени читается без квадратичного члена`() {
        val calibration = assertNotNull(BecqMoniXml.parse(file(), zone).data.spectrum.calibration)

        assertEquals(-9.86440677966101, calibration.a0, 1e-12)
        assertEquals(2.7457627118644066, calibration.a1, 1e-12)
        assertEquals(0.0, calibration.a2)
    }

    @Test
    fun `квадратичная калибровка прочитана целиком`() {
        val text = file(
            calibration = """
                <EnergyCalibration>
                  <PolynomialOrder>2</PolynomialOrder>
                  <Coefficients>
                    <Coefficient>-5.5</Coefficient>
                    <Coefficient>2.4168</Coefficient>
                    <Coefficient>3.9769E-04</Coefficient>
                  </Coefficients>
                </EnergyCalibration>
            """,
        )

        val result = BecqMoniXml.parse(text, zone)

        assertEquals(emptyList(), result.warnings)
        val calibration = assertNotNull(result.data.spectrum.calibration)
        assertEquals(3.9769E-4, calibration.a2, 1e-12)
    }

    @Test
    fun `калибровка выше второй степени не поддержана и названа`() {
        val text = file(
            calibration = """
                <EnergyCalibration>
                  <PolynomialOrder>3</PolynomialOrder>
                  <Coefficients>
                    <Coefficient>-5.5</Coefficient>
                    <Coefficient>2.4168</Coefficient>
                    <Coefficient>3.9769E-04</Coefficient>
                    <Coefficient>1.1E-08</Coefficient>
                  </Coefficients>
                </EnergyCalibration>
            """,
        )

        val error = assertFailsWith<BecqMoniXmlException> { BecqMoniXml.parse(text, zone) }

        assertTrue(
            error.message!!.contains("степени 3"),
            "отказ не называет степень многочлена: ${error.message}",
        )
    }

    @Test
    fun `нулевой коэффициент третьей степени разбору не мешает`() {
        val text = file(
            calibration = """
                <EnergyCalibration>
                  <PolynomialOrder>3</PolynomialOrder>
                  <Coefficients>
                    <Coefficient>0</Coefficient>
                    <Coefficient>2.4168</Coefficient>
                    <Coefficient>0</Coefficient>
                    <Coefficient>0</Coefficient>
                  </Coefficients>
                </EnergyCalibration>
            """,
        )

        val calibration = assertNotNull(BecqMoniXml.parse(text, zone).data.spectrum.calibration)

        assertEquals(2.4168, calibration.a1, 1e-12)
    }

    @Test
    fun `без калибровки спектр читается, а шкала остаётся в каналах`() {
        val result = BecqMoniXml.parse(file(calibration = ""), zone)

        assertNull(result.data.spectrum.calibration)
        assertEquals(6, result.data.spectrum.channelCount)
        assertTrue(
            result.warnings.any { it.contains("калибровки энергии в файле нет") },
            "отсутствие калибровки не названо: ${result.warnings}",
        )
    }

    @Test
    fun `файл без живого времени сообщает, что известно только полное`() {
        val text = file(times = "<MeasurementTime>1267.1013579</MeasurementTime>")

        val result = BecqMoniXml.parse(text, zone)

        assertNull(result.data.spectrum.liveSeconds)
        assertEquals(1267.1013579, result.data.spectrum.realSeconds)
        assertTrue(
            result.warnings.any { it.contains("LiveTime") },
            "отсутствие живого времени не названо: ${result.warnings}",
        )
    }

    @Test
    fun `файл без единого времени измерения отвергнут`() {
        val error = assertFailsWith<BecqMoniXmlException> {
            BecqMoniXml.parse(file(times = ""), zone)
        }

        assertTrue(
            error.message!!.contains("MeasurementTime"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `число каналов не совпадает с объявленным — взяты фактические и названы оба числа`() {
        val text = file(channels = "<NumberOfChannels>1024</NumberOfChannels>")

        val result = BecqMoniXml.parse(text, zone)

        assertEquals(6, result.data.spectrum.channelCount)
        val warning = result.warnings.single()
        assertTrue(warning.contains("1024"), "нет заявленного числа каналов: $warning")
        assertTrue(warning.contains("6"), "нет фактического числа каналов: $warning")
    }

    @Test
    fun `фоновый спектр читается рядом с основным`() {
        val background = """
            <BackgroundEnergySpectrum>
              <NumberOfChannels>6</NumberOfChannels>
              <MeasurementTime>3600</MeasurementTime>
              <LiveTime>3550</LiveTime>
              <Spectrum>
                <DataPoint>1</DataPoint>
                <DataPoint>1</DataPoint>
                <DataPoint>2</DataPoint>
                <DataPoint>3</DataPoint>
                <DataPoint>1</DataPoint>
                <DataPoint>0</DataPoint>
              </Spectrum>
            </BackgroundEnergySpectrum>
        """
        val text = file().replace("<Visible>true</Visible>", background + "<Visible>true</Visible>")

        val result = BecqMoniXml.parse(text, zone)

        val parsed = assertNotNull(result.data.background)
        assertEquals(listOf(1, 1, 2, 3, 1, 0), parsed.counts)
        assertEquals(3550.0, parsed.liveSeconds)
        // Калибровки у фонового блока нет — это замечание, а не отказ.
        assertTrue(
            result.warnings.any { it.contains("фоновый спектр: калибровки") },
            "молчание о некалиброванном фоне: ${result.warnings}",
        )
    }

    @Test
    fun `чужой корневой элемент отвергнут`() {
        val error = assertFailsWith<BecqMoniXmlException> {
            BecqMoniXml.parse("<RadInstrumentData><Spectrum/></RadInstrumentData>", zone)
        }

        assertTrue(
            error.message!!.contains("не файл спектра BecqMoni"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `испорченный XML отвергнут с внятной причиной`() {
        val error = assertFailsWith<BecqMoniXmlException> {
            BecqMoniXml.parse(file().substring(0, 400), zone)
        }

        assertTrue(
            error.message!!.contains("не является корректным XML"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `файл без каналов отвергнут`() {
        val text = file().replace(Regex("<Spectrum>.*?</Spectrum>", RegexOption.DOT_MATCHES_ALL), "")

        val error = assertFailsWith<BecqMoniXmlException> { BecqMoniXml.parse(text, zone) }

        assertTrue(
            error.message!!.contains("Spectrum"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `нечисловой отсчёт называет номер канала`() {
        val text = file().replace("<DataPoint>19</DataPoint>", "<DataPoint>много</DataPoint>")

        val error = assertFailsWith<BecqMoniXmlException> { BecqMoniXml.parse(text, zone) }

        assertTrue(
            error.message!!.contains("канал №2") && error.message!!.contains("много"),
            "отказ не показывает, где сломано: ${error.message}",
        )
    }

    @Test
    fun `внешняя сущность не раскрывается`() {
        val text = """<?xml version="1.0"?>
            <!DOCTYPE ResultDataFile [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <ResultDataFile><ResultDataList><ResultData>
            <EnergySpectrum><MeasurementTime>&secret;</MeasurementTime></EnergySpectrum>
            </ResultData></ResultDataList></ResultDataFile>
        """.trimIndent()

        assertFailsWith<BecqMoniXmlException> { BecqMoniXml.parse(text, zone) }
    }
}
