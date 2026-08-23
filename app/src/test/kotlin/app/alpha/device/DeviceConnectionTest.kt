package app.alpha.device

import app.alpha.protocol.Command
import app.alpha.protocol.RealTimeData
import app.alpha.protocol.Vs
import app.alpha.protocol.Vsfr
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
    fun `the newest record of a reply lands on the moment it arrived`() = runTest {
        // Прибор пишет раз в секунду и отдаёт написанное по первому запросу,
        // поэтому самая свежая запись ответа сделана практически в момент его
        // прихода. База равна наблюдению, а не сходится к нему: время
        // сходимости — это ровно то время, которое экран говорил «нет новых
        // данных», а метки лежали в прошлом и не пролезали в базу.
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = 3_000, countRate = 10f, doseRate = 0.0004f,
        )
        val (conn, _) = establish(fake)

        val first = conn.readDataBuf()

        assertEquals(now, first.records.maxOf { it.timestampMillis })
        // Одним ответом, без окна и без ожидания.
        assertEquals(-158_000L, conn.clockCorrectionMillis)
    }

    @Test
    fun `испорченная метка не уводит время сеанса`() = runTest {
        // Полевой отчёт 23.08: прибор прислал ОДНУ запись со смещением
        // +352 797 117 (·10 мс) — почти сорок суток вперёд. База уехала на
        // −980 ч, живые записи стали «историей 979 ч 59 мин», поток замолчал
        // и на экране связь выглядела потерянной до перезапуска.
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = -12_700, countRate = 10f, doseRate = 0.0004f,
        )
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 2, tsOffset10ms = 352_797_117, countRate = 11f, doseRate = 0.0005f,
        )
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 3, tsOffset10ms = -12_700, countRate = 12f, doseRate = 0.0006f,
        )
        val (conn, _) = establish(fake)

        conn.readDataBuf()
        val anchored = conn.clockCorrectionMillis

        val garbage = conn.readDataBuf()
        assertEquals(0, garbage.records.size, "запись из будущего попала в результат")
        assertEquals(1, conn.garbageRecords)
        assertEquals(anchored, conn.clockCorrectionMillis, "испорченная метка подвинула базу")

        val live = conn.readDataBuf()
        assertEquals(now, live.records.single().timestampMillis)
        assertTrue(conn.synchronised, "поток обязан остаться живым")
    }

    @Test
    fun `застрявшая база восстанавливается сама`() = runTest {
        // Если база всё же уехала, поток перестаёт быть живым. Через минуту
        // молчания якорь ставится заново по правдоподобной записи — иначе
        // единственным лекарством остаётся перезапуск приложения.
        var moment = now
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = -12_700, countRate = 10f, doseRate = 0.0004f,
        )
        // Вторая порция приходит через две минуты и на час старше: якорь
        // сдвинулся бы больше допустимого дрейфа, но поток уже застрял.
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 2, tsOffset10ms = -12_700 - 360_000, countRate = 11f, doseRate = 0.0005f,
        )
        val link = FakeDeviceLink(fake)
        val client = ProtocolClient(link)
        backgroundScope.launch { link.notifications.collect(client::onNotification) }
        val conn = DeviceConnection.establish(
            client = client,
            address = "AA:BB:CC:DD:EE:FF",
            clock = { moment },
            zone = ZoneId.of("UTC"),
        )

        conn.readDataBuf()
        val first = conn.clockCorrectionMillis

        moment += 2 * 60_000L
        val late = conn.readDataBuf()

        assertTrue(
            conn.clockCorrectionMillis != first,
            "база не переставилась после минуты без живых записей",
        )
        assertEquals(moment, late.records.single().timestampMillis)
    }

    @Test
    fun `накопленное прибором не прибивается к моменту подключения`() = runTest {
        // Прибор хранит автономные наблюдения и отдаёт их первыми ответами.
        // Новейшая запись такой порции сама историческая: якорь по ней
        // означал бы, что всё накопленное случилось сейчас.
        val fake = FakeRadiaCode()
        val twoHoursAgo10ms = -((128_000L + 2 * 3_600_000L) / 10).toInt()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = twoHoursAgo10ms, countRate = 10f, doseRate = 0.0004f,
        )
        val (conn, _) = establish(fake)

        val result = conn.readDataBuf()

        assertEquals(0L, conn.clockCorrectionMillis, "базу подвинула историческая запись")
        assertEquals(now - 2 * 3_600_000L, result.records.single().timestampMillis)
        assertTrue(!conn.synchronised, "слив накопленного не может считаться живым потоком")
    }

    @Test
    fun `догнав живое, база снова стягивается измерением`() = runTest {
        val fake = FakeRadiaCode()
        val hourAgo10ms = -((128_000L + 3_600_000L) / 10).toInt()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = hourAgo10ms, countRate = 10f, doseRate = 0.0004f,
        )
        // Вторая порция уже живая: смещение соответствует моменту приёма.
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 2, tsOffset10ms = -12_700, countRate = 11f, doseRate = 0.0005f,
        )
        val (conn, _) = establish(fake)

        conn.readDataBuf()
        assertEquals(0L, conn.clockCorrectionMillis)

        val live = conn.readDataBuf()
        assertTrue(conn.synchronised, "живой ответ обязан считаться живым")
        assertEquals(now, live.records.single().timestampMillis)
    }

    @Test
    fun `earlier records of the same reply keep their spacing`() = runTest {
        // Сдвигается ВЕСЬ ответ целиком: интервалы между записями — это
        // измерение прибора, и переписывать их нельзя.
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = 0, countRate = 10f, doseRate = 0.0004f,
        ) + realTimeDataRecord(
            seq = 2, tsOffset10ms = 100, countRate = 11f, doseRate = 0.0005f,
        )
        val (conn, _) = establish(fake)

        val stamps = conn.readDataBuf().records.map { it.timestampMillis }.sorted()

        assertEquals(listOf(now - 1_000, now), stamps)
    }

    @Test
    fun `a foreign record group does not set the time base`() = runTest {
        // Полевой отчёт: в одном ответе новейшая запись свежа до миллисекунд, а
        // RealTimeData из него же — на полминуты старше. Пока якорь брал
        // максимум по ВСЕМ группам, ряд измерений — единственный, который
        // попадает в `samples` и на графики, — систематически уезжал в прошлое:
        // «возраст показания 30 с» и график, обрывающийся до «сейчас», при
        // исправно идущем потоке.
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = 0, countRate = 10f, doseRate = 0.0004f,
        ) + rawDataRecord(seq = 2, tsOffset10ms = 3_000, countRate = 10f, doseRate = 0.0004f)
        val (conn, _) = establish(fake)

        val result = conn.readDataBuf()
        val measurement = result.records.filterIsInstance<RealTimeData>().single()

        // Ряд измерений встал на «сейчас», а не на полминуты раньше.
        assertEquals(now, measurement.timestampMillis)
    }

    @Test
    fun `a stalled instrument does not drag the time base forward`() = runTest {
        // Прибор перестал писать: его последняя запись стареет с каждым
        // ответом. Это простой ПОТОКА, а не уход часов — поднимать по нему базу
        // означало бы штамповать старое показание сегодняшним временем.
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 1, tsOffset10ms = 0, countRate = 10f, doseRate = 0.0004f,
        )
        val (conn, _) = establish(fake)
        conn.readDataBuf()
        val corrected = conn.clockCorrectionMillis

        // Тот же ответ повторяется: новейшая запись не продвинулась.
        repeat(5) {
            fake.dataBufPayloads += realTimeDataRecord(
                seq = 1, tsOffset10ms = 0, countRate = 10f, doseRate = 0.0004f,
            )
            conn.readDataBuf()
        }
        assertEquals(corrected, conn.clockCorrectionMillis)
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
