package app.radiacode.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        serviceRunning = true,
        connection = "подключён",
        doseRateMicroSvH = 0.17f,
        doseErrPercent = 8f,
        countRate = 24.6f,
        sampleAgeSeconds = 1,
        profileName = "Дом",
        contextWording = "AutoKnown",
        statusHeadline = "Выше вашего порога тревоги",
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
        assertTrue(report.contains("статус: Выше вашего порога тревоги"), report)
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
}
