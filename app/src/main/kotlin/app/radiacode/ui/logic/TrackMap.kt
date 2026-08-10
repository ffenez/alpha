package app.radiacode.ui.logic

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Metric coloring the track (SPEC «Map»: dose rate by default, CPS toggle). */
enum class TrackMetric { DOSE, CPS }

/** One track point for map logic — no Room or MapLibre types (JVM-tested). */
data class MapTrackPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    /** Dose rate at this point, µSv/h (converted from raw once, at the edge). */
    val doseMicroSvH: Float?,
    val cps: Float?,
)

/** One hotspot event with coordinates for the map layer. */
data class MapHotspot(
    val id: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    /** Dose rate at the event, µSv/h. */
    val doseMicroSvH: Float?,
    /** Baseline typical high at event time, µSv/h; null = no baseline then. */
    val typicalMicroSvH: Float?,
)

/** Geographic bounding box of the rendered track (camera fitting). */
data class MapBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

/**
 * Pure track-map logic: ramp bucketing, downsampling, GeoJSON building,
 * summaries. The MapLibre bridge (`ui/map`) consumes only plain strings and
 * data classes from here.
 */
object TrackMap {

    /**
     * Rendering cap. MapLibre handles far more circles, but a multi-hour 1 Hz
     * walk is tens of thousands of points with no visual gain at track scale —
     * an even stride keeps the route shape and bounds while capping GeoJSON
     * rebuild cost during live recording. Thresholds, legend and the summary
     * are always computed from the FULL point list, so downsampling never
     * changes reported numbers.
     */
    const val MAX_RENDERED_POINTS = 2000

    /** Amber dose ramp of the design language, light→dark = low→high. */
    const val BUCKET_COUNT = 4

    /**
     * Ramp thresholds: quartiles (P25/P50/P75) of the visible track's values.
     *
     * Quantile-based, not fixed: absolute dose ranges differ by an order of
     * magnitude between environments (0.05–0.15 µSv/h indoors vs µSv/h-scale
     * near sources), so any fixed steps would paint a whole ordinary walk in
     * one color. Quartiles always spread the ramp over the variation that is
     * actually present; the legend's min/max labels give the absolute scale.
     * A near-constant track collapses to the lightest step — honest: there is
     * no variation to show.
     */
    data class RampThresholds(val q1: Float, val q2: Float, val q3: Float)

    fun rampThresholds(values: List<Float>): RampThresholds? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return RampThresholds(
            q1 = quantile(sorted, 0.25),
            q2 = quantile(sorted, 0.50),
            q3 = quantile(sorted, 0.75),
        )
    }

    /** Linear-interpolated quantile of an already sorted list. */
    private fun quantile(sorted: List<Float>, p: Double): Float {
        val position = p * (sorted.size - 1)
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val fraction = (position - lower).toFloat()
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
    }

    /** Ramp bucket 0..3 for a value; degenerate thresholds collapse to 0. */
    fun bucket(value: Float, thresholds: RampThresholds): Int = when {
        value <= thresholds.q1 -> 0
        value <= thresholds.q2 -> 1
        value <= thresholds.q3 -> 2
        else -> 3
    }

    fun metricValue(point: MapTrackPoint, metric: TrackMetric): Float? = when (metric) {
        TrackMetric.DOSE -> point.doseMicroSvH
        TrackMetric.CPS -> point.cps
    }

    /** Legend labels: min/max of the metric over the FULL track; null = no data. */
    fun valueRange(points: List<MapTrackPoint>, metric: TrackMetric): Pair<Float, Float>? {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var seen = false
        for (point in points) {
            val value = metricValue(point, metric) ?: continue
            seen = true
            if (value < min) min = value
            if (value > max) max = value
        }
        return if (seen) min to max else null
    }

    /**
     * Even-stride downsampling to at most [maxPoints]; first and last points
     * are always kept (route ends stay pinned). Extreme values may drop out of
     * the *rendering* — hotspots have their own event-driven layer, and all
     * numbers shown to the user come from the full list.
     */
    fun <T> downsample(points: List<T>, maxPoints: Int = MAX_RENDERED_POINTS): List<T> {
        require(maxPoints >= 2) { "maxPoints must fit at least the two endpoints" }
        if (points.size <= maxPoints) return points
        val stride = ceil(points.size.toDouble() / maxPoints).toInt()
        val result = ArrayList<T>(points.size / stride + 2)
        var index = 0
        while (index < points.size) {
            result += points[index]
            index += stride
        }
        if (result.last() !== points.last()) result += points.last()
        return result
    }

    /**
     * Track length: haversine sum over consecutive fixes. GPS jitter guards:
     * segments shorter than [MIN_SEGMENT_METERS] are treated as standing
     * still, fixes worse than [MAX_ACCURACY_METERS] are excluded — otherwise
     * a stationary hour inflates into hundreds of meters.
     */
    const val MIN_SEGMENT_METERS = 2.0
    const val MAX_ACCURACY_METERS = 50f

    fun distanceMeters(points: List<MapTrackPoint>): Double {
        var total = 0.0
        var previous: MapTrackPoint? = null
        for (point in points) {
            if (point.accuracyMeters > MAX_ACCURACY_METERS) continue
            val last = previous
            if (last != null) {
                val segment = haversineMeters(
                    last.latitude,
                    last.longitude,
                    point.latitude,
                    point.longitude,
                )
                if (segment >= MIN_SEGMENT_METERS) {
                    total += segment
                    previous = point
                }
                // Sub-threshold segment: keep the anchor, wait for real movement.
            } else {
                previous = point
            }
        }
        return total
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bounds(points: List<MapTrackPoint>): MapBounds? {
        if (points.isEmpty()) return null
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        for (point in points) {
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }
        return MapBounds(minLat, maxLat, minLon, maxLon)
    }

    /** «Мой маршрут» summary — always over the full point list. */
    data class Summary(
        val pointCount: Int,
        val avgDoseMicroSvH: Float?,
        val maxDoseMicroSvH: Float?,
    )

    fun summary(points: List<MapTrackPoint>): Summary {
        var sum = 0.0
        var max = -Float.MAX_VALUE
        var count = 0
        for (point in points) {
            val dose = point.doseMicroSvH ?: continue
            sum += dose
            if (dose > max) max = dose
            count++
        }
        return Summary(
            pointCount = points.size,
            avgDoseMicroSvH = if (count > 0) (sum / count).toFloat() else null,
            maxDoseMicroSvH = if (count > 0) max else null,
        )
    }

    // --- GeoJSON ---

    /** Feature property: ramp bucket 0..3; -1 = metric missing at this point. */
    const val PROP_BUCKET = "b"
    const val PROP_DOSE = "dose"
    const val PROP_CPS = "cps"
    const val PROP_TIME = "t"
    const val PROP_ID = "id"
    const val PROP_TYPICAL = "typ"
    const val KIND_ROUTE = "route"
    const val KIND_POINT = "pt"
    const val KIND_HOTSPOT = "hs"
    const val PROP_KIND = "kind"

    /**
     * Track FeatureCollection: one LineString (the route) + one Point feature
     * per rendered fix with the ramp bucket and the tap-card raw values.
     * Built by hand — the payload is machine-generated numbers only, and this
     * keeps org.json/gson out of pure logic.
     */
    fun trackGeoJson(
        points: List<MapTrackPoint>,
        metric: TrackMetric,
        thresholds: RampThresholds?,
    ): String {
        val sb = StringBuilder(points.size * 96 + 256)
        sb.append("""{"type":"FeatureCollection","features":[""")
        sb.append("""{"type":"Feature","properties":{"$PROP_KIND":"$KIND_ROUTE"},""")
        sb.append(""""geometry":{"type":"LineString","coordinates":[""")
        points.forEachIndexed { index, point ->
            if (index > 0) sb.append(',')
            appendLonLat(sb, point)
        }
        sb.append("]}}")
        for (point in points) {
            val value = metricValue(point, metric)
            val bucket = if (value != null && thresholds != null) {
                bucket(value, thresholds)
            } else {
                -1
            }
            sb.append(""",{"type":"Feature","properties":{"$PROP_KIND":"$KIND_POINT",""")
            sb.append(""""$PROP_BUCKET":$bucket,"$PROP_TIME":${point.timestamp}""")
            point.doseMicroSvH?.let { sb.append(""","$PROP_DOSE":${number(it)}""") }
            point.cps?.let { sb.append(""","$PROP_CPS":${number(it)}""") }
            sb.append("""},"geometry":{"type":"Point","coordinates":""")
            appendLonLat(sb, point)
            sb.append("}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    /** Hotspot FeatureCollection (crit markers layer). */
    fun hotspotGeoJson(hotspots: List<MapHotspot>): String {
        val sb = StringBuilder(hotspots.size * 112 + 64)
        sb.append("""{"type":"FeatureCollection","features":[""")
        hotspots.forEachIndexed { index, hotspot ->
            if (index > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"$PROP_KIND":"$KIND_HOTSPOT",""")
            sb.append(""""$PROP_ID":${hotspot.id},"$PROP_TIME":${hotspot.timestamp}""")
            hotspot.doseMicroSvH?.let { sb.append(""","$PROP_DOSE":${number(it)}""") }
            hotspot.typicalMicroSvH?.let { sb.append(""","$PROP_TYPICAL":${number(it)}""") }
            sb.append("""},"geometry":{"type":"Point","coordinates":""")
            sb.append('[').append(number(hotspot.longitude)).append(',')
                .append(number(hotspot.latitude)).append(']')
            sb.append("}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun appendLonLat(sb: StringBuilder, point: MapTrackPoint) {
        sb.append('[').append(number(point.longitude)).append(',')
            .append(number(point.latitude)).append(']')
    }

    /** Locale-independent JSON number (Double/Float.toString may use E-notation — valid JSON). */
    private fun number(value: Double): String = value.toString()

    private fun number(value: Float): String =
        if (value.isFinite()) value.toString() else "0"

    // --- hotspot dwell («показания устойчивы N с») ---

    /**
     * How long the readings stayed elevated after a hotspot event: seconds
     * from the event until the dose first drops below `eventDose ×`
     * [DWELL_FRACTION] (mirroring the detector's re-arm hysteresis) or until a
     * sampling gap longer than [DWELL_MAX_GAP_MILLIS] breaks continuity.
     * Calculated from raw 1 Hz samples — displayed as «расчёт».
     */
    const val DWELL_FRACTION = 0.8f
    const val DWELL_MAX_GAP_MILLIS = 3_000L

    fun dwellSeconds(
        samples: List<Pair<Long, Float>>,
        eventTimestamp: Long,
        eventDoseMicroSvH: Float,
    ): Long {
        val floor = eventDoseMicroSvH * DWELL_FRACTION
        var lastElevated = eventTimestamp
        for ((timestamp, dose) in samples) {
            if (timestamp < eventTimestamp) continue
            if (timestamp - lastElevated > DWELL_MAX_GAP_MILLIS) break
            if (dose < floor) break
            lastElevated = timestamp
        }
        return (lastElevated - eventTimestamp) / 1000L
    }

    // --- formatting ---

    /** «1,2 км» / «340 м» for the overlay chip. */
    fun formatDistance(meters: Double): String = when {
        meters >= 10_000 -> String.format(Locale.US, "%.0f км", meters / 1000)
        meters >= 1_000 -> String.format(Locale.US, "%.1f км", meters / 1000).replace('.', ',')
        else -> String.format(Locale.US, "%.0f м", meters)
    }

    /** CPS with one decimal below 10 («9,4»), whole above («27»). */
    fun formatCps(cps: Float): String =
        if (cps < 10f) {
            String.format(Locale.US, "%.1f", cps).replace('.', ',')
        } else {
            String.format(Locale.US, "%.0f", cps)
        }
}
