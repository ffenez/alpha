package app.radiacode.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.radiacode.service.StreamTrace

/**
 * Отчёт нужен ровно для разбора наблюдений «поставил порог, а на экране
 * ничего» — значит в нём обязаны быть и порог, и отсчёт выдержки, и та самая
 * строка, которую человек видел на экране.
 */
class DebugReportTest {

    private val now = 1_700_000_000_000L

    private val snapshot = DebugSnapshot(
        appVersion = "0.1.0-alpha",
        androidSdk = 34,
        deviceModel = "Google Pixel 7",
        instrumentSerial = "RC-110-000123",
        instrumentFirmware = "4.8",
        instrumentModel = "RadiaCode-110",
        instrumentModelKnown = true,
        spectrumFormatVersion = 1,
        instrumentConfig = listOf("SpecFormatVersion=1"),
        connectionFailure = null,
        spectrumChannels = 1024,
        spectrumCalibration = "a0=-10.0 a1=2.4 a2=3.0E-4",
        spectrumSeconds = 3_600L,
        seqGapTotal = 0,
        reconnectCount = 0,
        chartsRefreshedAgoSeconds = 12L,
        chartsRefreshCount = 5,
        clockCorrectionMillis = -96_000L,
        serviceRunning = true,
        connection = "подключён",
        doseRateMicroSvH = 0.17f,
        doseErrPercent = 8f,
        countRate = 24.6f,
        sampleAgeSeconds = 1,
        profileName = "Дом",
        contextWording = "AutoKnown",
        statusHeadline = "Выше порога тревоги",
        statusDetail = "порог L1 0,10 мкЗв/ч превышен · держится 40 с из 120 с до тревоги",
        baselineWording = "собран за 26 ч",
        admissionWording = "измерения учитываются",
        alarmL1MicroSvH = 0.10f,
        alarmL2MicroSvH = 0.30f,
        alarmPersistenceSeconds = 120,
        alarmRelativeFactor = 2f,
        alarmSensitivity = "CUSTOM",
        aboveUsualSinceMillis = now - 40_000L,
        alarmConditionSinceMillis = now - 40_000L,
        alertSinceMillis = null,
        nowMillis = now,
        sampleCount = 412_003,
        sessionCount = 12,
        spectrumCount = 340,
        minuteStatCount = 6_000,
        hourSketchCount = 100,
        doseUnit = "мкЗв/ч",
        theme = "Системная",
        searchFeedbackMode = "клики",
        searchBackgroundWording = "25,4 с⁻¹ · 45 показаний",
        fingerprintWording = "не создан",
    )

    private fun stamp(millis: Long) = "T${(millis - now) / 1000}"

    private val report = DebugReport.build(snapshot) { stamp(it) }

    @Test
    fun `the report answers the question it exists for`() {
        // Порог, значение и отсчёт выдержки — три числа, из-за которых
        // «ничего не происходит» перестаёт быть загадкой.
        assertTrue(report.contains("L1: 0.1 мкЗв/ч"), report)
        assertTrue(report.contains("мощность дозы: 0.17"), report)
        assertTrue(report.contains("минимальная длительность: 120 с"), report)
        assertTrue(report.contains("держится 40 с"), report)
        assertTrue(report.contains("тревога подтверждена с: нет"), report)
    }

    @Test
    fun `the wording on screen is quoted, not rebuilt`() {
        assertTrue(report.contains("статус: Выше порога тревоги"), report)
        assertTrue(report.contains(snapshot.statusDetail!!), report)
    }

    @Test
    fun `nothing personal travels with it, and the file says so`() {
        assertTrue(report.contains(DebugReport.PRIVACY_NOTE))
        // Ищем в теле отчёта, без самой оговорки: она как раз и говорит, что
        // координат здесь нет.
        val body = report.replace(DebugReport.PRIVACY_NOTE, "").lowercase()
        for (word in listOf("широта", "долгота", "latitude", "longitude", "координат")) {
            assertTrue(!body.contains(word), "«$word» in the report")
        }
        // Объёмы — да, содержимое — нет.
        assertTrue(report.contains("измерений: 412003"))
        assertTrue(!report.contains("counts=["))
    }

    @Test
    fun `versions of the algorithms are listed for reproducibility`() {
        assertTrue(report.contains("rate_comparison: v"), report)
        assertTrue(report.contains("fingerprint: v"), report)
        assertTrue(report.contains("версия: 0.1.0-alpha"), report)
    }

    /**
     * Цена частоты опроса спектра — ФАКТЫ (ADR 007). Отчёт печатает запросы,
     * байты и время работы; вывода про батарею в нём нет, потому что из этих
     * чисел он не следует.
     */
    @Test
    fun `the exchange trace tells a dead stream from one that is not being written`() {
        // Полевой случай, который трижды чинился по рассуждению вместо
        // наблюдения: «нет новых данных · 29 с» при зелёном кружке связи. На
        // экране «записи не пришли» и «пришли, но не записались» неразличимы.
        val report = DebugReport.build(
            snapshot.copy(
                streamTicks = listOf(
                    StreamTrace.Tick(
                        atMillis = snapshot.nowMillis - 2_000,
                        records = 1,
                        newestAgeMillis = 29_000,
                        correctionMillis = -158_000,
                        inserted = 0,
                        dropped = 1,
                    ),
                    StreamTrace.Tick(
                        atMillis = snapshot.nowMillis - 1_000,
                        records = 0,
                        newestAgeMillis = null,
                        correctionMillis = -158_000,
                        inserted = 0,
                        dropped = 0,
                    ),
                ),
            ),
            stamp = { "13.08.2026 11:40:36" },
        )

        assertTrue(report.contains("## Такты обмена"), report)
        // Обе величины, ради которых трасса и заведена.
        assertTrue(report.contains("29000 мс"), report)
        assertTrue(report.contains("0/1"), report)
        // Молча отброшенные строки названы числом, а не следом в тактах.
        assertTrue(report.contains("записей отброшено при вставке: 1"), report)
        // Пустой ответ — штатное состояние, и он назван словами.
        assertTrue(report.contains("нет записей"), report)
    }

    @Test
    fun `the spectrum traffic section reports measurements, not a battery verdict`() {
        val withTraffic = DebugReport.build(
            snapshot.copy(
                spectrumTraffic = SpectrumTraffic(
                    policy = "30s",
                    requests = 240,
                    payloadBytes = 480_000,
                    serviceUptimeMillis = 2L * 3_600_000L,
                    storedSlices = 5_760,
                ),
            ),
        ) { stamp(it) }
        assertTrue(withTraffic.contains("политика частоты: 30s"), withTraffic)
        assertTrue(withTraffic.contains("запросов спектра: 240 (≈120 в час)"), withTraffic)
        assertTrue(withTraffic.contains("байт ответов: 480000 (≈240000 в час)"), withTraffic)
        assertTrue(withTraffic.contains("срезов спектрограммы в базе: 5760"), withTraffic)
        assertTrue(!withTraffic.lowercase().contains("батаре"), withTraffic)
        // Без данных раздела нет вовсе: пустые нули читались бы как измерение.
        assertTrue(!report.contains("## Опрос спектра"), report)
    }

    @Test
    fun `a rate is not printed when the service has barely run`() {
        val traffic = SpectrumTraffic(
            policy = "5s",
            requests = 2,
            payloadBytes = 4_000,
            serviceUptimeMillis = 10_000L,
            storedSlices = 1,
        )
        assertEquals(null, traffic.requestsPerHour())
        assertEquals(null, traffic.bytesPerHour())
    }

    @Test
    fun `the file name carries the moment it was taken`() {
        assertEquals("radiacode-debug-T0.txt", DebugReport.fileName(now) { stamp(it) })
    }

    @Test
    fun `an empty state prints dashes instead of inventing values`() {
        val blank = snapshot.copy(
            doseRateMicroSvH = null,
            countRate = null,
            sampleAgeSeconds = null,
            instrumentSerial = null,
            instrumentFirmware = null,
            alarmConditionSinceMillis = null,
            aboveUsualSinceMillis = null,
        )
        val text = DebugReport.build(blank) { stamp(it) }
        assertTrue(text.contains("мощность дозы: — мкЗв/ч"), text)
        assertTrue(text.contains("серийный номер: —"), text)
        assertTrue(text.contains("условие тревоги выполняется с: нет"), text)
    }

    @Test
    fun `the report carries what an unknown instrument needs explained`() {
        // Разбор «не работает на другом приборе» невозможен без модели,
        // формата спектра, калибровки и причины отказа связи.
        val unknown = snapshot.copy(
            instrumentSerial = "SN-777",
            instrumentModel = "RadiaCode",
            instrumentModelKnown = false,
            spectrumFormatVersion = 2,
            instrumentConfig = listOf("SpecFormatVersion=2", "HwVersion=3"),
            connectionFailure = "UnsupportedFirmwareException: need target >= 4.8",
            spectrumChannels = 512,
        )
        val text = DebugReport.build(unknown) { stamp(it) }
        assertTrue(text.contains("модель: RadiaCode (не опознана — общий профиль)"), text)
        assertTrue(text.contains("формат спектра: 2"), text)
        assertTrue(text.contains("конфигурация: HwVersion=3"), text)
        assertTrue(text.contains("последняя ошибка связи: UnsupportedFirmwareException"), text)
        assertTrue(text.contains("каналов: 512"), text)
        assertTrue(text.contains("калибровка: a0="), text)
    }

    @Test
    fun `stream health is in the report for one's own instrument too`() {
        // «Показания идут рывками» разбирается этими двумя числами — на своём
        // приборе ровно так же, как на чужом.
        val glitchy = snapshot.copy(seqGapTotal = 17, reconnectCount = 4)
        val text = DebugReport.build(glitchy) { stamp(it) }
        assertTrue(text.contains("пропусков seq в DATA_BUF: 17"), text)
        assertTrue(text.contains("переподключений за сеанс: 4"), text)
    }
}
