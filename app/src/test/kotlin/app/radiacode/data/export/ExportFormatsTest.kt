package app.radiacode.data.export

import app.radiacode.analysis.AbExperiment
import app.radiacode.data.SessionSummary
import app.radiacode.data.db.RangeStats
import app.radiacode.data.db.SampleEntity
import app.radiacode.data.db.TrackPointEntity
import app.radiacode.data.export.backup.Json
import app.radiacode.data.export.backup.double
import app.radiacode.data.export.backup.obj
import app.radiacode.data.export.backup.str
import app.radiacode.ui.text.AppLanguage
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Машинные форматы выгрузки: их читает не человек, а другая программа, поэтому
 * проверяется разбираемость, а не вид.
 *
 * Числа при этом проверяются на СМЫСЛ: единицы прибора наружу не уезжают, и
 * координаты не теряют точности больше, чем обещано.
 */
class ExportFormatsTest {

    private val zone = ZoneId.of("UTC")

    private fun points(count: Int = 5) = (0 until count).map { index ->
        TrackPointEntity(
            id = index.toLong() + 1,
            sessionId = 1,
            timestamp = 1_700_000_000_000L + index * 1000L,
            latitude = 55.755_419_9 + index * 0.001,
            longitude = 37.617_644_4 + index * 0.001,
            accuracyMeters = 8f,
            doseRate = 0.0012f,
            countRate = 12f,
            altitudeMeters = 145.0,
        )
    }

    @Test
    fun `маршрут в GeoJSON разбирается и содержит линию`() {
        val text = GeoJson.route(points(), "прогулка")
        val root = Json.parse(text) as Json.Value.Obj
        assertEquals("FeatureCollection", root.str("type"))
        val features = root.fields["features"] as Json.Value.Arr
        // Одна линия + по точке на измерение.
        assertEquals(1 + 5, features.items.size)
        val line = (features.items.first() as Json.Value.Obj).obj("geometry")!!
        assertEquals("LineString", line.str("type"))
    }

    @Test
    fun `измерения можно не включать`() {
        val text = GeoJson.route(points(), "прогулка", includeMeasurements = false)
        val root = Json.parse(text) as Json.Value.Obj
        val features = root.fields["features"] as Json.Value.Arr
        assertEquals(1, features.items.size)
        assertFalse(text.contains("doseRateMicroSvH"))
    }

    @Test
    fun `координаты округляются до обещанной точности`() {
        // Шесть знаков — около десяти сантиметров: точнее фикса телефона не
        // бывает, а лишние разряды удваивают файл.
        val text = GeoJson.route(points(count = 1), "прогулка", includeMeasurements = false)
        assertTrue(text.contains("55.75542"), "широта округлена не так: $text")
        assertFalse(text.contains("55.7554199"), "разряды, которых прибор не измерял")
    }

    @Test
    fun `мощность дозы уезжает в единицах человека`() {
        val text = GeoJson.route(points(count = 1), "прогулка")
        // Сырое значение прибора (0,0012) наружу не уходит: в файле микрозиверты.
        assertFalse(text.contains("0.0012"), "сырые единицы прибора в файле")
        assertTrue(text.contains("doseRateMicroSvH"))
    }

    // ------------------------------------------------------------- сессия

    private fun samples(count: Int = 4) = (0 until count).map { index ->
        SampleEntity(
            id = index.toLong() + 1,
            timestamp = 1_700_000_000_000L + index * 1000L,
            doseRate = 0.0012f,
            doseRateErr = 5f,
            countRate = 12f,
            countRateErr = 3f,
            flags = 0,
            realTimeFlags = 0,
        )
    }

    private fun summary() = SessionSummary(
        id = 1,
        profileId = null,
        profileName = "дом",
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_100_000L,
        stats = RangeStats(
            sampleCount = 4,
            avgDoseRate = 0.0012f,
            minDoseRate = 0.0011f,
            maxDoseRate = 0.0013f,
            avgCountRate = 12f,
            maxCountRate = 14f,
        ),
        doseMicroSv = 0.42,
        hasSpectrum = false,
        hasTrack = false,
    )

    @Test
    fun `сессия в JSON разбирается и несёт сводку с рядом`() {
        val text = ReportFactories.sessionJson(summary(), samples())
        val root = Json.parse(text) as Json.Value.Obj
        assertEquals("radiacode-session", root.str("type"))
        assertEquals("дом", root.str("profile"))
        val rows = root.fields["samples"] as Json.Value.Arr
        assertEquals(4, rows.items.size)
        val first = rows.items.first() as Json.Value.Obj
        assertTrue((first.double("doseRateMicroSvH") ?: 0.0) > 0.0)
    }

    @Test
    fun `отчёт сессии знает своё имя и подпись`() {
        val report = ReportFactories.session(
            summary = summary(),
            samples = samples(),
            events = emptyList(),
            appName = "RadiaCode Companion",
            appVersion = "0.7.9",
            language = AppLanguage.RU,
            zone = zone,
            nowMillis = 1_700_000_200_000L,
        )
        assertEquals("дом", report.title)
        assertTrue(report.footer.contains("0.7.9"))
        // Пустых рядов в отчёте нет: жёсткость считается не всегда.
        assertTrue(report.series.all { it.points.isNotEmpty() })
    }

    // --------------------------------------------------------------- опыт

    private fun runs() = listOf(
        AbExperiment.RunData(
            id = 1,
            label = "A",
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_000_600_000L,
            durationSeconds = 600,
            counts = List(64) { 10 },
        ),
        AbExperiment.RunData(
            id = 2,
            label = "B, с экраном",
            startedAt = 1_700_001_000_000L,
            endedAt = 1_700_001_600_000L,
            durationSeconds = 600,
            counts = List(64) { 20 },
        ),
    )

    @Test
    fun `таблица опыта считает скорость счёта и защищает запятую`() {
        val csv = ReportFactories.experimentCsv(runs())
        val lines = csv.trim().lines()
        assertEquals("run,started_ms,duration_s,total_counts,cps", lines.first())
        assertEquals(3, lines.size)
        assertTrue(lines[1].endsWith("640,1.067"), "скорость счёта: ${lines[1]}")
        // Имя с запятой не должно разваливать столбцы.
        assertTrue(lines[2].startsWith("\"B, с экраном\","), lines[2])
    }
}
