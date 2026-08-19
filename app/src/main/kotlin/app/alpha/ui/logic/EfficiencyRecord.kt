package app.alpha.ui.logic

import app.alpha.analysis.EfficiencyCurve
import app.alpha.analysis.EfficiencyPoint
import app.alpha.data.JsonMap

/**
 * Сохранённая калибровка эффективности: точки и геометрия, в которой они сняты.
 *
 * Кривая не хранится — она пересчитывается из точек. Так удаление одной точки
 * не требует ничего пересобирать вручную, а изменение правила подгонки
 * (степень многочлена) действует и на старые калибровки.
 *
 * ## Почему геометрия — часть записи
 *
 * Эффективность принадлежит не прибору, а связке «прибор + расстояние +
 * положение образца». Одна и та же кривая, применённая к образцу на другом
 * расстоянии, даёт неверную активность — и ошибка не статистическая, а в разы.
 * Проверить геометрию приложение не может, поэтому её НАЗЫВАЕТ человек, и это
 * название стоит рядом с каждым числом в беккерелях.
 */
data class EfficiencyRecord(
    val points: List<EfficiencyPoint>,
    /** Как названа геометрия: «вплотную к торцу», «5 см от крышки». */
    val geometry: String,
    /** Когда запись последний раз менялась, мс эпохи. */
    val updatedAtMillis: Long,
) {

    /** Кривая по этим точкам; null — точек мало или они не задают наклон. */
    fun curve(): EfficiencyCurve? = EfficiencyCurve.of(points)

    fun encode(): String = JsonMap.of(
        "geometry" to geometry,
        "updatedAtMillis" to updatedAtMillis,
        // Точки — одной строкой: список списков в JsonMap не кладётся, а
        // заводить хранилище на четыре числа ради этого незачем.
        "points" to points.joinToString(";") { point ->
            listOf(
                point.energyKeV,
                point.efficiency,
                point.relativeSigma,
                point.nuclide.replace(';', ' ').replace(',', ' '),
            ).joinToString(",")
        },
    )

    companion object {

        fun decode(raw: String?): EfficiencyRecord? {
            val map = JsonMap.decode(raw)
            if (map.isEmpty()) return null
            val encoded = map["points"].orEmpty()
            if (encoded.isBlank()) return null
            val points = encoded.split(";").mapNotNull { part ->
                val fields = part.split(",")
                if (fields.size < 4) return@mapNotNull null
                val energy = fields[0].toDoubleOrNull() ?: return@mapNotNull null
                val efficiency = fields[1].toDoubleOrNull() ?: return@mapNotNull null
                val sigma = fields[2].toDoubleOrNull() ?: return@mapNotNull null
                if (energy <= 0.0 || efficiency <= 0.0 || sigma <= 0.0) return@mapNotNull null
                EfficiencyPoint(energy, efficiency, sigma, fields[3].trim())
            }
            if (points.isEmpty()) return null
            return EfficiencyRecord(
                points = points,
                geometry = map["geometry"].orEmpty(),
                updatedAtMillis = map["updatedAtMillis"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}
