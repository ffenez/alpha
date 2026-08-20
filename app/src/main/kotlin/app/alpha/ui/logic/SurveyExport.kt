package app.alpha.ui.logic

import app.alpha.analysis.Radioelements
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Выгрузка съёмки в CSV.
 *
 * Станционная съёмка почти всегда доезжает до чужого ГИС, поэтому файл содержит
 * ВСЁ, из чего складывался экран: площади с их σ и пределами, отношения,
 * геометрию, давление и длительность. Числа пишутся с точкой и без пробелов —
 * это машинный формат, а не текст для чтения; разделитель дробной части здесь
 * не зависит от языка интерфейса.
 */
object SurveyExport {

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val ISO = DateTimeFormatter.ISO_INSTANT

    fun fileName(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "alpha-survey-" + Instant.ofEpochMilli(nowMillis).atZone(zone).format(STAMP) + ".csv"

    val HEADER = listOf(
        "station_id",
        "time_utc",
        "latitude",
        "longitude",
        "accuracy_m",
        "height_cm",
        "pressure_hpa",
        "live_time_s",
        "device",
        "k_cps", "k_sigma", "k_limit_cps", "k_detected",
        "eu_cps", "eu_sigma", "eu_limit_cps", "eu_detected",
        "eth_cps", "eth_sigma", "eth_limit_cps", "eth_detected",
        "eu_eth", "eu_eth_sigma",
        "eth_k", "eth_k_sigma",
        "note",
    )

    fun csv(stations: List<SurveyModel.Station>): String = buildString {
        append(HEADER.joinToString(",")).append('\n')
        for (station in stations.sortedBy { it.entity.timestamp }) {
            val e = station.entity
            val cells = mutableListOf(
                e.id.toString(),
                Instant.ofEpochMilli(e.timestamp).let(ISO::format),
                num(e.latitude, 7),
                num(e.longitude, 7),
                num(e.accuracyMeters.toDouble(), 1),
                e.heightCm?.toString().orEmpty(),
                e.pressureHpa?.let { num(it.toDouble(), 1) }.orEmpty(),
                station.seconds.toString(),
                station.deviceName.orEmpty(),
            )
            for (element in listOf(
                Radioelements.Element.K,
                Radioelements.Element.U,
                Radioelements.Element.TH,
            )) {
                val measure = station.measure(element)
                cells += measure?.let { num(it.cps.toDouble(), 6) }.orEmpty()
                cells += measure?.let { num(it.cpsSigma.toDouble(), 6) }.orEmpty()
                cells += measure?.let {
                    num((it.criticalCounts / it.seconds.coerceAtLeast(1)).toDouble(), 6)
                }.orEmpty()
                cells += measure?.detected?.toString().orEmpty()
            }
            val uth = station.uraniumToThorium
            val thk = station.thoriumToPotassium
            cells += uth?.let { num(it.value.toDouble(), 4) }.orEmpty()
            cells += uth?.let { num(it.sigma.toDouble(), 4) }.orEmpty()
            cells += thk?.let { num(it.value.toDouble(), 4) }.orEmpty()
            cells += thk?.let { num(it.sigma.toDouble(), 4) }.orEmpty()
            cells += quote(e.note.orEmpty())
            append(cells.joinToString(",")).append('\n')
        }
    }

    private fun num(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    /** Заметка — единственное поле со свободным текстом, поэтому экранируется. */
    private fun quote(text: String): String =
        if (text.isEmpty()) "" else "\"" + text.replace("\"", "\"\"") + "\""
}
