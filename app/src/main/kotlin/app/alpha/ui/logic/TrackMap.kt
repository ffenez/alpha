package app.alpha.ui.logic

import app.alpha.ui.text.MapRu
import app.alpha.ui.text.MapStrings
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Metric coloring the track (SPEC «Map»: dose rate by default, CPS toggle). */
enum class TrackMetric { DOSE, CPS }

/**
 * Чем задаются границы цвета следа.
 *
 * [ABSOLUTE] — обычным фоном места: одно значение всегда одного цвета, и
 * маршруты сравнимы между собой. [ROUTE_CONTRAST] — самим маршрутом: цвет
 * растянут по его собственным значениям, поэтому мелкие различия видны, а
 * сравнение с другим маршрутом теряет смысл. [MANUAL] — границы задал
 * человек: он же и отвечает за то, что они означают, поэтому названий вроде
 * «безопасно» у них нет и быть не может.
 */
enum class MapColorScale { ABSOLUTE, ROUTE_CONTRAST, MANUAL }

/**
 * Границы ручной шкалы как их пишет и читает человек.
 *
 * Одна строка вместо набора ползунков: границ до шести, они связаны порядком,
 * и править их удобнее списком, чем шестью отдельными полями. Разделителем
 * годится и запятая, и пробел, и точка с запятой — угадывать, какой именно
 * ждёт приложение, человек не обязан; десятичная часть пишется и точкой, и
 * запятой.
 */
object MapAnchors {

    /**
     * Запятая бывает и разделителем, и десятичным знаком, и угадывать нельзя —
     * «0,05,0,1» одинаково законно читается как два числа и как четыре.
     * Поэтому правило деления объявлено заранее:
     *
     *  - есть пробел или «;» — они и делят, а запятая означает дробную часть
     *    («0,05 0,1 0,2» — три числа);
     *  - запятых больше нет ничего — тогда делит запятая, а дробную часть
     *    пишут точкой («0.05,0.1,0.2»).
     *
     * Мусор просто не попадает в границы: поле не ругается на человека
     * посреди набора.
     */
    fun parse(text: String): List<Float> {
        val hasOtherSeparator = text.any { it.isWhitespace() || it == ';' }
        val pieces = if (hasOtherSeparator) {
            text.split(' ', '\t', '\n', ';')
        } else {
            text.split(',')
        }
        return pieces
            .mapNotNull { piece ->
                val cleaned = if (hasOtherSeparator) piece.replace(',', '.') else piece
                cleaned.trim().toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
            }
            .distinct()
            .sorted()
    }

    fun format(anchors: List<Float>): String = anchors.joinToString(" ") { value ->
        val text = if (value >= 10f) {
            String.format(java.util.Locale.US, "%.0f", value)
        } else {
            String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
        text.replace('.', ',')
    }
}

/** One track point for map logic — no Room or osmdroid types (JVM-tested). */
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
 * Pure track-map logic: ramp bucketing, downsampling, bounds, summaries and
 * the screen-space hit test. The osmdroid bridge (`ui/map`) consumes only
 * data classes from here and never puts map-engine types back.
 */
object TrackMap {

    /**
     * Rendering cap. A multi-hour 1 Hz walk is tens of thousands of points
     * with no visual gain at track scale — an even stride keeps the route
     * shape and bounds while capping the per-frame projection cost of the
     * osmdroid overlay. Thresholds, legend and the summary are always
     * computed from the FULL point list, so downsampling never changes
     * reported numbers.
     */
    const val MAX_RENDERED_POINTS = 2000

    /** Ступеней в шкале следа: зелёный → багровый (`TrackRampColors`). */
    const val RAMP_STEPS = 7

    /**
     * Шкала цвета следа.
     *
     * [bounds] — верхние границы ступеней, кроме последней (их всегда
     * `RAMP_STEPS - 1`), [low] и [high] — то, что подписано под шкалой.
     * Значение выше последней границы попадает в верхнюю ступень: шкала не
     * обрывается, она насыщается.
     */
    data class RampScale(
        val bounds: List<Float>,
        val mode: MapColorScale,
        val low: Float,
        val high: Float,
    )

    /**
     * Шкала по обычному фону МЕСТА — одинаковая для всех маршрутов.
     *
     * Опоры не выдуманы: низ — нижняя граница обычного диапазона места,
     * перелом — его верх (P90), а верх шкалы — тот же множитель обычного,
     * которым окрашено главное число на Главной. Отсюда следует главное
     * свойство: одно и то же значение получает один и тот же цвет на любом
     * маршруте, и зелёное означает «внутри обычного для этого места», а не
     * «мало по сравнению с соседним участком».
     *
     * Внутри обычного диапазона — две зелёные ступени; выше — пять, от
     * жёлто-зелёной до багровой, и багровая начинается ровно на верху шкалы.
     */
    fun absoluteScale(usualLow: Float, usualHigh: Float, factor: Float): RampScale? {
        if (!usualLow.isFinite() || !usualHigh.isFinite()) return null
        if (usualHigh <= 0f || usualHigh <= usualLow) return null
        val ceiling = usualHigh * factor
        if (ceiling <= usualHigh) return null
        val step = (ceiling - usualHigh) / 4f
        return RampScale(
            bounds = listOf(
                (usualLow + usualHigh) / 2f,
                usualHigh,
                usualHigh + step,
                usualHigh + 2 * step,
                usualHigh + 3 * step,
                ceiling,
            ),
            mode = MapColorScale.ABSOLUTE,
            low = usualLow,
            high = ceiling,
        )
    }

    /**
     * Шкала, растянутая по самому маршруту: границы — семичастные квантили
     * его значений.
     *
     * Это аналитический режим, и он назван так на экране. Растяжение находит
     * малые пространственные различия там, где абсолютная шкала окрашивает
     * весь маршрут одинаково, но ценой сопоставимости: маршрут 0,14–0,16
     * получит полную шкалу до багрового, хотя ничего не происходило. Поэтому
     * по умолчанию он не включён, а легенда всегда называет режим.
     */
    fun contrastScale(values: List<Float>): RampScale? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val bounds = (1 until RAMP_STEPS).map { quantile(sorted, it.toDouble() / RAMP_STEPS) }
        return RampScale(
            bounds = bounds,
            mode = MapColorScale.ROUTE_CONTRAST,
            low = sorted.first(),
            high = sorted.last(),
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

    /**
     * Какая шкала красит след при выбранном режиме.
     *
     * Абсолютная шкала требует обычного фона места: пока его нет, сравнивать
     * не с чем, и шкала честно становится растянутой по маршруту — легенда
     * называет режим, поэтому подмена не остаётся незамеченной.
     */
    fun scaleFor(
        mode: MapColorScale,
        usualBand: Pair<Float, Float>?,
        factor: Float,
        values: List<Float>,
        manualAnchors: List<Float> = emptyList(),
    ): RampScale? {
        when (mode) {
            MapColorScale.MANUAL -> manualScale(manualAnchors)?.let { return it }
            MapColorScale.ABSOLUTE ->
                if (usualBand != null) {
                    absoluteScale(usualBand.first, usualBand.second, factor)?.let { return it }
                }
            MapColorScale.ROUTE_CONTRAST -> Unit
        }
        return contrastScale(values)
    }

    /**
     * Шкала по границам, которые задал человек.
     *
     * Границ ровно [RAMP_STEPS]-1: между ними цвет и переключается. Значения
     * приводятся в порядок и очищаются от повторов — шкала, у которой две
     * границы совпали, просто теряет ступень, а не ломается.
     */
    fun manualScale(anchors: List<Float>): RampScale? {
        val clean = anchors.filter { it.isFinite() && it > 0f }.distinct().sorted()
        if (clean.size < 2) return null
        val bounds = clean.take(RAMP_STEPS - 1)
        return RampScale(
            bounds = bounds,
            mode = MapColorScale.MANUAL,
            low = bounds.first(),
            high = bounds.last(),
        )
    }

    /** Границы по умолчанию для ручной шкалы — от них человек и отталкивается. */
    val DEFAULT_MANUAL_DOSE = listOf(0.05f, 0.10f, 0.20f, 0.30f, 0.60f, 1.00f)
    val DEFAULT_MANUAL_CPS = listOf(10f, 20f, 40f, 80f, 160f, 320f)

    /** Ступень 0..[RAMP_STEPS]-1; вырожденная шкала складывается в нижнюю. */
    fun bucket(value: Float, scale: RampScale): Int {
        scale.bounds.forEachIndexed { index, bound -> if (value <= bound) return index }
        return scale.bounds.size
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

    /**
     * Разрыв следа: дольше этого без координат — линия не рисуется.
     *
     * Точки пишутся раз в секунду; полторы минуты без единой означают, что
     * координат не было, а не что человек шёл по прямой. Прямая через такой
     * пропуск — выдуманный маршрут, и она врёт тем убедительнее, чем длиннее.
     */
    const val LINE_GAP_SECONDS = 90L

    /**
     * И отдельно — скачок: провайдер иногда отдаёт фикс с другого конца
     * города через секунду после предыдущего. Такой отрезок тоже не рисуется.
     */
    const val LINE_JUMP_METERS = 500.0

    /**
     * Где линия прерывается: `true` на индексе i означает, что отрезка от
     * i-1 к i нет. Первая точка всегда начинает отрезок.
     */
    fun lineBreaks(points: List<MapTrackPoint>): BooleanArray {
        val breaks = BooleanArray(points.size)
        if (points.isEmpty()) return breaks
        breaks[0] = true
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val seconds = (current.timestamp - previous.timestamp) / 1000
            val jump = haversineMeters(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
            )
            breaks[index] = seconds > LINE_GAP_SECONDS || jump > LINE_JUMP_METERS
        }
        return breaks
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

    // --- screen-space hit test ---

    /**
     * Index of the rendered point nearest to a tap, or -1 when nothing is
     * within [slopPx]. Screen coordinates only, so the whole tap-to-card
     * behaviour is JVM-testable without a map engine: the osmdroid overlay
     * only projects geo points to pixels and calls this.
     */
    fun nearestIndex(
        xs: FloatArray,
        ys: FloatArray,
        tapX: Float,
        tapY: Float,
        slopPx: Float,
    ): Int {
        var best = -1
        var bestDistance = slopPx * slopPx
        for (index in xs.indices) {
            val dx = xs[index] - tapX
            val dy = ys[index] - tapY
            val distance = dx * dx + dy * dy
            if (distance <= bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

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
    fun formatDistance(meters: Double, s: MapStrings = MapRu): String = when {
        meters >= 10_000 ->
            String.format(Locale.US, "%.0f", meters / 1000) + " " + s.unitKilometers
        meters >= 1_000 ->
            String.format(Locale.US, "%.1f", meters / 1000).replace('.', ',') +
                " " + s.unitKilometers
        else -> String.format(Locale.US, "%.0f", meters) + " " + s.unitMeters
    }

    /** CPS with one decimal below 10 («9,4»), whole above («27»). */
    fun formatCps(cps: Float): String =
        if (cps < 10f) {
            String.format(Locale.US, "%.1f", cps).replace('.', ',')
        } else {
            String.format(Locale.US, "%.0f", cps)
        }
}
