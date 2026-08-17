package app.alpha.data.export

import app.alpha.data.db.TrackPointEntity
import app.alpha.data.export.backup.Json
import app.alpha.device.DoseUnits

/**
 * Маршрут в GeoJSON — для карт и ГИС.
 *
 * ## Что внутри и почему так
 *
 * Линия маршрута — одна `LineString`: у неё нет тяжёлых свойств, поэтому файл
 * с десятками тысяч точек остаётся читаемым. Значения измерений при этом не
 * теряются: они едут отдельными точками с их временем и величинами, и это
 * решение человека — включать их или нет (§21 ТЗ).
 *
 * Координаты пишутся с шестью знаками после запятой: это около десяти
 * сантиметров на местности — точнее, чем даёт GPS телефона, и незачем делать
 * файл вдвое больше ради разрядов, которых прибор не измерял.
 */
object GeoJson {

    /** Знаков после запятой в координатах: ~0,1 м, точнее фикса не бывает. */
    const val COORDINATE_DECIMALS = 6

    fun route(
        points: List<TrackPointEntity>,
        name: String,
        includeMeasurements: Boolean = true,
    ): String {
        val out = StringBuilder(points.size * 48 + 512)
        val w = Json.Writer(out)
        w.beginObject()
            .field("type", "FeatureCollection")
            .field("name", name)
            .name("features")
        w.beginArray()

        // Сам маршрут: геометрия без свойств на каждую точку.
        w.beginObject()
            .field("type", "Feature")
            .name("properties")
        w.beginObject()
            .field("name", name)
            .field("points", points.size.toLong())
            .endObject()
        w.name("geometry")
        w.beginObject()
            .field("type", "LineString")
            .name("coordinates")
        w.beginArray()
        for (point in points) {
            w.beginArray()
            w.value(round(point.longitude))
            w.value(round(point.latitude))
            point.altitudeMeters?.let { w.value(round(it)) }
            w.endArray()
        }
        w.endArray()
        w.endObject()
        w.endObject()

        if (includeMeasurements) {
            for (point in points) {
                w.beginObject()
                    .field("type", "Feature")
                    .name("properties")
                w.beginObject()
                    .field("timestamp", point.timestamp)
                    .field(
                        "doseRateMicroSvH",
                        point.doseRate?.let { DoseUnits.rawToMicroSievertPerHour(it).toDouble() },
                    )
                    .field("countRate", point.countRate?.toDouble())
                    .field("accuracyMeters", point.accuracyMeters.toDouble())
                    .endObject()
                w.name("geometry")
                w.beginObject()
                    .field("type", "Point")
                    .name("coordinates")
                w.beginArray()
                w.value(round(point.longitude))
                w.value(round(point.latitude))
                w.endArray()
                w.endObject()
                w.endObject()
            }
        }

        w.endArray()
        w.endObject()
        return out.toString()
    }

    private fun round(value: Double): Double {
        val factor = Math.pow(10.0, COORDINATE_DECIMALS.toDouble())
        return Math.round(value * factor) / factor
    }
}
