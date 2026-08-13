package app.radiacode.data.export

import app.radiacode.data.db.SampleEntity
import app.radiacode.data.db.TrackPointEntity
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ряд измерений и трек в открытых форматах. Файл живёт дольше настройки и
 * читается чужой программой, поэтому проверяется не «похоже на CSV», а ровно
 * то, из-за чего такие файлы обычно не открываются: единицы, разделители и
 * часовые пояса.
 */
class SeriesExportTest {

    private val zone = ZoneId.of("UTC")
    private val at = 1_700_000_000_000L

    private fun sample(timestamp: Long, doseRateRaw: Float, countRate: Float) = SampleEntity(
        timestamp = timestamp,
        doseRate = doseRateRaw,
        doseRateErr = 15f,
        countRate = countRate,
        countRateErr = 10f,
        flags = 0,
        realTimeFlags = 0,
    )

    @Test
    fun `csv names its units in the header and keeps them out of the settings`() {
        // Единицы файла не зависят от того, как в этот день был настроен
        // экран: мкР/ч на экране не должны превратить столбец в мкР/ч молча.
        val csv = SeriesExport.csv(listOf(sample(at, 0.0004f, 25f)), zone)
        val header = csv.lineSequence().first()

        assertTrue(header.contains("dose_rate_uSv_h"), header)
        assertTrue(header.contains("count_rate_cps"), header)
    }

    @Test
    fun `numbers use a dot, and time appears twice`() {
        // Запятая в дробях сломала бы разбор везде, кроме русской локали, а
        // одна только локальная метка сделала бы файл неоднозначным при смене
        // часового пояса.
        val row = SeriesExport.csv(listOf(sample(at, 0.0004f, 25.5f)), zone)
            .lineSequence().drop(1).first()
        val columns = row.split(',')

        assertEquals(6, columns.size, row)
        assertEquals(at.toString(), columns[0])
        assertTrue(columns[1].startsWith("2023-11-14T22:13:20"), columns[1])
        assertTrue(columns.drop(2).none { it.contains(',') }, row)
        assertTrue(columns[4].startsWith("25.5"), columns[4])
    }

    @Test
    fun `raw device units are converted, not copied`() {
        // В базе доза лежит в сырых единицах прибора (×10⁴); выгрузить их как
        // мкЗв/ч значило бы ошибиться на четыре порядка. 0,0000142 — типичный
        // домашний фон 0,142 мкЗв/ч из полевого отчёта.
        val value = SeriesExport.csv(listOf(sample(at, 0.0000142f, 25f)), zone)
            .lineSequence().drop(1).first().split(',')[2].toDouble()

        assertTrue(value in 0.14..0.145, "$value")
    }

    private fun point(timestamp: Long, lat: Double, lon: Double, doseRate: Float?) =
        TrackPointEntity(
            sessionId = 1,
            timestamp = timestamp,
            latitude = lat,
            longitude = lon,
            accuracyMeters = 8f,
            doseRate = doseRate,
            countRate = null,
        )

    @Test
    fun `gpx carries coordinates, UTC time and the reading as an extension`() {
        val gpx = SeriesExport.gpx(
            listOf(point(at, 55.751244, 37.618423, 0.0004f)),
            trackName = "Трек",
        )

        assertTrue(gpx.contains("""<gpx version="1.1""""), gpx)
        assertTrue(gpx.contains("""lat="55.7512440""""), gpx)
        assertTrue(gpx.contains("<time>2023-11-14T22:13:20Z</time>"), gpx)
        assertTrue(gpx.contains("<rc:doseRateMicroSvH>"), gpx)
    }

    @Test
    fun `a point without a reading carries no reading`() {
        // Ноль вместо пропуска был бы измерением, которого не было: фикс может
        // прийти раньше первого отсчёта прибора.
        val gpx = SeriesExport.gpx(listOf(point(at, 55.0, 37.0, null)), "Трек")

        assertFalse(gpx.contains("<extensions>"), gpx)
        assertTrue(gpx.contains("<trkpt"), gpx)
    }

    @Test
    fun `a track name with markup does not break the file`() {
        val gpx = SeriesExport.gpx(listOf(point(at, 55.0, 37.0, 0.0004f)), "Дом & <сад>")

        assertTrue(gpx.contains("<name>Дом &amp; &lt;сад&gt;</name>"), gpx)
    }

    @Test
    fun `file names are sortable and carry their extension`() {
        val name = SeriesExport.fileName(at, "csv", zone)

        assertEquals("radiacode-20231114-221320.csv", name)
    }
}
