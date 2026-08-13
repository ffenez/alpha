package app.radiacode.device

import app.radiacode.protocol.Command
import app.radiacode.protocol.RealTimeData
import app.radiacode.protocol.Vs
import app.radiacode.protocol.Vsfr
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeviceConnectionTest {

    // 2023-11-14 22:13:20 UTC
    private val now = 1_700_000_000_000L

    private suspend fun kotlinx.coroutines.test.TestScope.establish(
        fake: FakeRadiaCode,
    ): Pair<DeviceConnection, FakeDeviceLink> {
        val link = FakeDeviceLink(fake)
        val client = ProtocolClient(link)
        backgroundScope.launch { link.notifications.collect(client::onNotification) }
        val conn = DeviceConnection.establish(client, "AA:BB:CC:DD:EE:FF", clock = { now }, zone = ZoneId.of("UTC"))
        return conn to link
    }

    @Test
    fun `init sequence follows the cdump order with exact payloads`() = runTest {
        val fake = FakeRadiaCode()
        establish(fake)

        val commands = fake.requests.map { it.first }
        assertEquals(
            listOf(
                Command.SET_EXCHANGE,
                Command.SET_TIME,
                Command.WR_VIRT_SFR,
                Command.RD_VIRT_STRING, // serial
                Command.GET_VERSION,
                Command.RD_VIRT_STRING, // configuration
            ),
            commands,
        )

        assertContentEquals(Command.SET_EXCHANGE_PAYLOAD, fake.requests[0].second)

        // SET_TIME: <day><month><year-2000><0><second><minute><hour><0> for 2023-11-14 22:13:20 UTC.
        assertContentEquals(byteArrayOf(14, 11, 23, 0, 20, 13, 22, 0), fake.requests[1].second)

        // WR_VIRT_SFR DEVICE_TIME := 0.
        assertContentEquals(Wire.u32(Vsfr.DEVICE_TIME) + Wire.u32(0), fake.requests[2].second)

        assertContentEquals(Wire.u32(Vs.SERIAL_NUMBER.toLong()), fake.requests[3].second)
        assertContentEquals(Wire.u32(Vs.CONFIGURATION.toLong()), fake.requests[5].second)
    }

    @Test
    fun `captures device info and base time`() = runTest {
        val fake = FakeRadiaCode()
        val (conn, _) = establish(fake)

        assertEquals(now + 128_000, conn.baseTimeMillis)
        assertEquals("RC-110-001234", conn.info.serialNumber)
        assertEquals("AA:BB:CC:DD:EE:FF", conn.info.address)
        assertEquals(4, conn.info.firmware.targetMajor)
        assertEquals(8, conn.info.firmware.targetMinor)
        assertEquals(1, conn.info.spectrumFormatVersion)
        assertTrue("DeviceName=RadiaCode-110" in conn.configurationText)
    }

    @Test
    fun `rejects firmware older than 4_8`() = runTest {
        val fake = FakeRadiaCode(targetVersion = 4 to 7)
        assertFailsWith<UnsupportedFirmwareException> { establish(fake) }
    }

    @Test
    fun `DATA_BUF records get base_time anchored timestamps`() = runTest {
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 3,
            tsOffset10ms = -250, // 2.5 s before base time
            countRate = 12.5f,
            doseRate = 0.0005f,
            countRateErr10 = 15,
            doseRateErr10 = 20,
        )
        val (conn, _) = establish(fake)

        val result = conn.readDataBuf()
        assertEquals(1, result.records.size)
        assertEquals(0, result.seqGaps)
        val rt = result.records[0] as RealTimeData
        // Пин ОБНОВЛЁН осознанно: раньше метка была now+125,5 с — на две
        // минуты В БУДУЩЕМ, и это был не тест, а слепок дефекта (полевой отчёт
        // показывал «возраст показания: −96 с»). Запись не может прийти
        // раньше, чем сделана, поэтому база стягивается измерением: новейшая
        // запись ответа прибивается к часам телефона.
        assertEquals(now, rt.timestampMillis)
        assertEquals(-(128_000L - 2_500L), conn.clockCorrectionMillis)
        assertEquals(12.5f, rt.countRate)
        assertEquals(0.0005f, rt.doseRate)
        assertEquals(1.5f, rt.countRateErr)
        assertEquals(2.0f, rt.doseRateErr)
    }

    @Test
    fun `a single stale buffered record does not move the base`() = runTest {
        val fake = FakeRadiaCode()
        // Первый ответ: свежая запись с завышенной базой — поправка измеряется.
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = 0, countRate = 10f, doseRate = 0.0004f,
        )
        // Второй ответ: запись из БУФЕРА, на 40 с старше. РАЗОВОЕ отставание —
        // честная задержка переноса, и «чинить» его нельзя: база не двигается
        // (вверх она идёт только по минимуму окна, см. следующий тест).
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 2, tsOffset10ms = -4_000, countRate = 10f, doseRate = 0.0004f,
        )
        val (conn, _) = establish(fake)

        conn.readDataBuf()
        val corrected = conn.clockCorrectionMillis
        assertEquals(-128_000L, corrected)

        val second = conn.readDataBuf()
        assertEquals(corrected, conn.clockCorrectionMillis)
        assertEquals(now - 40_000, second.records[0].timestampMillis)
    }

    @Test
    fun `an over-correction from a buffered record heals itself`() = runTest {
        // Полевой дефект: первый ответ принёс запись из буфера с большим
        // смещением, односторонний храповик вычел лишнее — и дальше метки
        // оседали на десятки секунд в прошлом навсегда: зелёный кружок связи,
        // «нет новых данных · 31 с» и графики с постоянным отставанием.
        val fake = FakeRadiaCode()
        // Первая запись опережает базу ещё на 30 с сверх 128 — вычтется 158 с.
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = 3_000, countRate = 10f, doseRate = 0.0004f,
        )
        // Дальше идут нормальные свежие записи: относительно съехавшей базы
        // каждая выглядит старой на 30 с.
        repeat(DeviceConnection.LAG_WINDOW_RESPONSES) {
            fake.dataBufPayloads += realTimeDataRecord(
                seq = 2 + it, tsOffset10ms = 0, countRate = 10f, doseRate = 0.0004f,
            )
        }
        val (conn, _) = establish(fake)

        conn.readDataBuf()
        assertEquals(-158_000L, conn.clockCorrectionMillis)

        // Окно ответов накапливается, и база поднимается обратно.
        var last = 0L
        repeat(DeviceConnection.LAG_WINDOW_RESPONSES) {
            last = conn.readDataBuf().records[0].timestampMillis
        }
        assertEquals(-128_000L, conn.clockCorrectionMillis)
        assertEquals(now, last)
    }


    @Test
    fun `reads and decodes a v1 spectrum`() = runTest {
        val fake = FakeRadiaCode()
        // duration 600 s, calibration, then 1024 zero channels: RLE word (1024 << 4) | code 0.
        fake.spectrumPayload = Wire.u32(600) +
            Wire.f32(-6.0f) + Wire.f32(2.4f) + Wire.f32(0.0004f) +
            Wire.u16((1024 shl 4) or 0)
        val (conn, _) = establish(fake)

        val spectrum = conn.readSpectrum()
        assertEquals(600, spectrum.durationSeconds)
        assertEquals(1024, spectrum.counts.size)
        assertTrue(spectrum.counts.all { it == 0 })
        assertEquals(-6.0f, spectrum.a0)
    }

    @Test
    fun `resets use canonical write commands`() = runTest {
        val fake = FakeRadiaCode()
        val (conn, _) = establish(fake)
        fake.requests.clear()

        conn.resetSpectrum()
        conn.resetDose()

        assertEquals(Command.WR_VIRT_STRING, fake.requests[0].first)
        assertContentEquals(Wire.u32(Vs.SPECTRUM.toLong()) + Wire.u32(0), fake.requests[0].second)

        assertEquals(Command.WR_VIRT_SFR, fake.requests[1].first)
        assertContentEquals(Wire.u32(Vsfr.DOSE_RESET) + Wire.u32(0), fake.requests[1].second)
    }

    @Test
    fun `all writes respect the 18-byte chunk limit`() = runTest {
        val fake = FakeRadiaCode()
        val (_, link) = establish(fake)
        assertTrue(link.chunkSizes.isNotEmpty())
        assertTrue(link.chunkSizes.all { it <= 18 })
    }
}
