package app.radiacode.data.export

import app.radiacode.data.db.SampleEntity
import app.radiacode.data.db.TrackPointEntity
import app.radiacode.device.DoseUnits
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ряд измерений и трек в открытых форматах: CSV и GPX.
 *
 * ## Зачем именно эти два
 *
 * До сих пор наружу уезжали только СПЕКТРЫ (RC-XML, N42) — то есть срез, а не
 * ход измерения. Ряд во времени невозможно было ни построить в чужой
 * программе, ни приложить к отчёту, ни просто сохранить у себя в понятном
 * виде. CSV читает всё, что умеет таблицы; GPX — стандарт треков, который
 * открывают карты и GIS. Оба текстовые и не требуют ни библиотеки, ни
 * зависимости.
 *
 * ## Что уезжает
 *
 * Ровно то, что человек выбрал: измерения одной сессии либо точки одного
 * трека. Экспорт — ЯВНОЕ действие через системный диалог сохранения, как и у
 * спектров; никакой автоматической выгрузки в приложении нет и не появляется.
 * Поэтому в CSV координат нет вовсе (они принадлежат треку), а в GPX нет
 * ничего, кроме точек и их измерений.
 *
 * ## Время
 *
 * В CSV метка идёт ДВАЖДЫ: миллисекундами эпохи (машине — без часовых поясов и
 * двусмысленностей) и локальным временем ISO-8601 со смещением (человеку —
 * чтобы столбец читался глазами). В GPX — только UTC, как требует схема.
 */
object SeriesExport {

    private val LOCAL_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** «radiacode-20260813-184500.csv». */
    fun fileName(
        timestampMillis: Long,
        extension: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = "radiacode-" +
        Instant.ofEpochMilli(timestampMillis).atZone(zone).format(FILE_STAMP) +
        "." + extension

    /**
     * Измерения сессии.
     *
     * Значения — в единицах СИ, а не в тех, что выбраны на экране: файл живёт
     * дольше настройки и читается другой программой, поэтому единица названа в
     * заголовке столбца и не зависит от того, как в этот день был настроен
     * интерфейс. Разделитель — запятая, десятичная точка: с запятой в дробях
     * CSV перестал бы разбираться где угодно, кроме русской локали.
     */
    fun csv(samples: List<SampleEntity>, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            appendLine(
                "timestamp_ms,timestamp_local,dose_rate_uSv_h,dose_rate_err_percent," +
                    "count_rate_cps,count_rate_err_percent",
            )
            for (s in samples) {
                append(s.timestamp)
                append(',')
                append(Instant.ofEpochMilli(s.timestamp).atZone(zone).format(LOCAL_STAMP))
                append(',')
                append(num(DoseUnits.rawToMicroSievertPerHour(s.doseRate)))
                append(',')
                append(num(s.doseRateErr))
                append(',')
                append(num(s.countRate))
                append(',')
                append(num(s.countRateErr))
                appendLine()
            }
        }

    /**
     * Точки маршрута таблицей.
     *
     * То же правило, что и у измерений сессии: единицы СИ в заголовке столбца,
     * десятичная точка. Координаты идут как есть — решение о том, что уезжает
     * в файл, принимается ДО выгрузки, а не урезанием чисел в столбце.
     */
    fun trackCsv(points: List<TrackPointEntity>, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            appendLine(
                "timestamp_ms,timestamp_local,latitude,longitude,altitude_m,accuracy_m," +
                    "dose_rate_uSv_h,count_rate_cps",
            )
            for (p in points) {
                append(p.timestamp)
                append(',')
                append(Instant.ofEpochMilli(p.timestamp).atZone(zone).format(LOCAL_STAMP))
                append(',')
                append(num(p.latitude))
                append(',')
                append(num(p.longitude))
                append(',')
                append(p.altitudeMeters?.let { num(it) } ?: "")
                append(',')
                append(num(p.accuracyMeters))
                append(',')
                append(p.doseRate?.let { num(DoseUnits.rawToMicroSievertPerHour(it)) } ?: "")
                append(',')
                append(p.countRate?.let { num(it) } ?: "")
                appendLine()
            }
        }

    /**
     * Трек в GPX 1.1.
     *
     * Мощность дозы кладётся в `<extensions>` собственного пространства имён:
     * схема GPX своих полей для неё не имеет, а класть измерение в `<name>`
     * или `<cmt>` значит превращать число в подпись, которую потом никто не
     * разберёт машинно. Точки без координат в трек не попадают по определению.
     */
    fun gpx(points: List<TrackPointEntity>, trackName: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="Alpha" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:rc="https://github.com/radiacode-alpha/gpx/1">""",
        )
        appendLine("  <trk>")
        appendLine("    <name>${escape(trackName)}</name>")
        appendLine("    <trkseg>")
        for (p in points) {
            appendLine("""      <trkpt lat="${num(p.latitude)}" lon="${num(p.longitude)}">""")
            p.altitudeMeters?.let { appendLine("        <ele>${num(it)}</ele>") }
            appendLine("        <time>${Instant.ofEpochMilli(p.timestamp)}</time>")
            // Точка без показания — обычное дело: фикс пришёл раньше первого
            // отсчёта. Ноль вместо пропуска был бы измерением, которого не было.
            p.doseRate?.let { rate ->
                appendLine("        <extensions>")
                appendLine(
                    "          <rc:doseRateMicroSvH>" +
                        num(DoseUnits.rawToMicroSievertPerHour(rate)) +
                        "</rc:doseRateMicroSvH>",
                )
                appendLine("        </extensions>")
            }
            appendLine("      </trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    /** Точка как десятичный разделитель — иначе файл не разберёт никто. */
    private fun num(value: Float): String = String.format(Locale.US, "%.6g", value).trim()

    private fun num(value: Double): String = String.format(Locale.US, "%.7f", value).trim()

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
