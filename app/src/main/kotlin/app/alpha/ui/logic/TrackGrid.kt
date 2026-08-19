package app.alpha.ui.logic

import app.alpha.ui.text.MapRu
import app.alpha.ui.text.MapStrings
import app.alpha.ui.text.uiDecimal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** What the map engine currently shows: geographic box plus zoom level. */
data class MapViewport(val bounds: MapBounds, val zoom: Double)

/**
 * Parameters of one accumulated-map query: the (padded) viewport box and the
 * grid step. Mirrors exactly what the SQL aggregation binds, so the pure code
 * here and the query in `TrackDao` cannot drift apart.
 */
data class GridQuery(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val latStepDeg: Double,
    val lonStepDeg: Double,
    /** Nominal cell side in meters — what the legend tells the user. */
    val cellMeters: Double,
)

/**
 * Value quantization of the histogram: bin index = `(value − min) / step`,
 * truncated. Chosen from the exact min/max of the *whole* matching set, so
 * every returned bin is comparable across cells.
 *
 * The authoritative binning happens in SQL, in double precision; [key] is the
 * same rule in Kotlin's `Float` and can land a value exactly on a boundary in
 * the neighbouring bin. Nothing depends on the two agreeing bit for bit: the
 * index is only ever used to keep bins ordered by value, and every reported
 * number comes from the exact `MIN`/`MAX`/`COUNT` of the bin itself.
 */
data class ValueBins(val min: Float, val step: Float) {
    fun key(value: Float): Int = ((value - min) / step).toInt()
}

/**
 * One row of the SQL aggregation: how many points of one cell fall into one
 * value bin, with the exact extremes and time span of that (cell, bin) pair.
 */
data class GridBin(
    val latKey: Int,
    val lonKey: Int,
    val valueKey: Int,
    val count: Int,
    val minValue: Float,
    val maxValue: Float,
    val minTime: Long,
    val maxTime: Long,
)

/**
 * Сколько точек должно быть в клетке, чтобы её медиана что-то описывала.
 *
 * **Инженерный параметр отображения.** Медиана одной точки — это сама точка, и
 * одиночный шумный отсчёт красил клетку в верх рампы наравне с кварталом,
 * пройденным сотню раз. Ниже порога клетка рисуется бледнее — измерение
 * остаётся показанным, но не притворяется таким же уверенным.
 */
const val MIN_CONFIDENT_POINTS = 5

/** One aggregated cell: what gets painted and what a tap reports. */
data class GridCell(
    val latKey: Int,
    val lonKey: Int,
    val southLatitude: Double,
    val northLatitude: Double,
    val westLongitude: Double,
    val eastLongitude: Double,
    val count: Int,
    /** Median of the metric inside the cell — robust, like everywhere else. */
    val median: Float,
    val p10: Float,
    val p90: Float,
    val minValue: Float,
    val maxValue: Float,
    val fromMillis: Long,
    val toMillis: Long,
) {
    val centerLatitude: Double get() = (southLatitude + northLatitude) / 2
    val centerLongitude: Double get() = (westLongitude + eastLongitude) / 2

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude >= southLatitude && latitude < northLatitude &&
            longitude >= westLongitude && longitude < eastLongitude
}

/** Order statistics of a histogram (a cell, or the whole visible set). */
data class GridStats(
    val count: Int,
    val median: Float,
    val p10: Float,
    val p90: Float,
    val minValue: Float,
    val maxValue: Float,
    val fromMillis: Long,
    val toMillis: Long,
)

/**
 * Accumulated radiation map: the math of «все записи».
 *
 * The problem it solves: track points from *all* recordings are potentially
 * hundreds of thousands of rows, and loading them into memory to draw a
 * heat map would be both slow and pointless — at any zoom the screen has far
 * fewer pixels than there are fixes.
 *
 * Approach — **grid aggregation in SQL**, in two bounded queries per view:
 *  1. an aggregate over the visible box (count, min, max, time span) — one
 *     row, computed over the FULL matching set. Every number the summary card
 *     shows comes from here, never from what happened to be drawn;
 *  2. a `GROUP BY (cell, value bin)` histogram. The result size is bounded by
 *     cells × [VALUE_BINS] instead of by the number of points, and it carries
 *     enough information for exact counts and for order statistics
 *     (median, P10–P90) accurate to one bin width — which is why the cells can
 *     be colored by the **median** and not by the mean.
 *
 * Mean is deliberately not used: a single hot fix inside a cell would drag the
 * whole cell's color, exactly the failure mode the rest of the app avoids by
 * working in quantiles.
 *
 * Cell size follows the zoom so a cell is always ≈ [TARGET_CELL_DP] on screen:
 * at street zoom cells are meters (nearly one cell per fix), at city zoom they
 * merge into a real heat map. Sizes snap to a 1/2/5·10ᵏ ladder so panning does
 * not reshuffle the grid on every pixel of zoom.
 */
object TrackGrid {

    /** Target on-screen cell side, dp. Big enough to tap, small enough to read. */
    const val TARGET_CELL_DP = 34.0

    /** Ground resolution of web-Mercator zoom 0 at the equator, m/px (256-px tiles). */
    const val EQUATOR_METERS_PER_PIXEL = 156_543.03392

    const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    /** Cell side is clamped into this range regardless of zoom. */
    const val MIN_CELL_METERS = 2.0
    const val MAX_CELL_METERS = 200_000.0

    /**
     * Value bins per query. 32 bins over the exact visible range put every
     * quantile within ~3 % of the range — invisible on a four-step color ramp,
     * and the cell card shows the exact min/max next to it anyway.
     */
    const val VALUE_BINS = 32

    /**
     * Hard cap on histogram rows crossing the DB boundary. A phone screen
     * holds ~2 000 cells; even if every cell used every value bin the query
     * stays inside this. If the cap ever truncates, the screen says how many
     * points the picture is built from instead of pretending.
     */
    const val MAX_HISTOGRAM_ROWS = 80_000

    /** Fixes worse than this are excluded — same guard as the route distance. */
    const val MAX_ACCURACY_METERS = TrackMap.MAX_ACCURACY_METERS

    /** Viewport padding so a small pan does not reveal an unpainted edge. */
    const val VIEWPORT_PADDING_FRACTION = 0.2

    /**
     * Cell side in meters for a zoom level, snapped to the 1/2/5 ladder.
     *
     * Derived from the nominal (256-px tile) ground resolution; with
     * `setTilesScaledToDpi(true)` one nominal pixel is one dp on screen, so
     * [TARGET_CELL_DP] is what the user actually sees.
     */
    fun cellMeters(zoom: Double, latitude: Double): Double {
        val metersPerPixel =
            EQUATOR_METERS_PER_PIXEL * cos(Math.toRadians(latitude.coerceIn(-85.0, 85.0))) /
                2.0.pow(zoom)
        return niceMeters(metersPerPixel * TARGET_CELL_DP)
            .coerceIn(MIN_CELL_METERS, MAX_CELL_METERS)
    }

    /** Nearest 1/2/5·10ᵏ at or below [raw] — a stable ladder, not a smooth zoom. */
    fun niceMeters(raw: Double): Double {
        if (raw <= 0 || !raw.isFinite()) return MIN_CELL_METERS
        val decade = 10.0.pow(floor(log10(raw)))
        val mantissa = raw / decade
        val step = when {
            mantissa >= 5.0 -> 5.0
            mantissa >= 2.0 -> 2.0
            else -> 1.0
        }
        return step * decade
    }

    /**
     * Query parameters for a viewport: the box padded by
     * [VIEWPORT_PADDING_FRACTION] and the grid step. Longitude steps are taken
     * at the center latitude, so cells are near-square in the current view.
     */
    fun query(viewport: MapViewport): GridQuery {
        val bounds = viewport.bounds
        val latSpan = (bounds.maxLatitude - bounds.minLatitude).coerceAtLeast(0.0)
        val lonSpan = (bounds.maxLongitude - bounds.minLongitude).coerceAtLeast(0.0)
        val latPad = latSpan * VIEWPORT_PADDING_FRACTION
        val lonPad = lonSpan * VIEWPORT_PADDING_FRACTION
        val centerLatitude = (bounds.minLatitude + bounds.maxLatitude) / 2
        val meters = cellMeters(viewport.zoom, centerLatitude)
        val latStep = meters / METERS_PER_DEGREE_LATITUDE
        val lonScale = cos(Math.toRadians(centerLatitude.coerceIn(-85.0, 85.0)))
        val lonStep = latStep / lonScale.coerceAtLeast(0.05)
        return GridQuery(
            minLatitude = (bounds.minLatitude - latPad).coerceIn(-90.0, 90.0),
            maxLatitude = (bounds.maxLatitude + latPad).coerceIn(-90.0, 90.0),
            minLongitude = (bounds.minLongitude - lonPad).coerceIn(-180.0, 180.0),
            maxLongitude = (bounds.maxLongitude + lonPad).coerceIn(-180.0, 180.0),
            latStepDeg = latStep,
            lonStepDeg = lonStep,
            cellMeters = meters,
        )
    }

    /**
     * Cell index of a coordinate. The `+ offset` shift is not decoration: the
     * SQL side has no `floor()` on every supported SQLite version and uses
     * `CAST(… AS INTEGER)`, which truncates *towards zero* — shifting latitude
     * by 90° and longitude by 180° makes the argument non-negative, where
     * truncation and floor agree. This function must stay identical to the SQL.
     */
    fun latKey(latitude: Double, stepDeg: Double): Int = ((latitude + 90.0) / stepDeg).toInt()

    fun lonKey(longitude: Double, stepDeg: Double): Int = ((longitude + 180.0) / stepDeg).toInt()

    fun cellSouth(latKey: Int, stepDeg: Double): Double = latKey * stepDeg - 90.0

    fun cellWest(lonKey: Int, stepDeg: Double): Double = lonKey * stepDeg - 180.0

    /**
     * Value bins from the exact extremes of the matching set. A degenerate
     * range (every point equal) still yields a positive step so the SQL
     * division is safe and every point lands in bin 0.
     */
    fun valueBins(min: Float, max: Float, bins: Int = VALUE_BINS): ValueBins {
        val span = (max - min).toDouble()
        val step = if (span > 0) span / bins else abs(min.toDouble()).coerceAtLeast(1.0)
        return ValueBins(min = min, step = step.toFloat())
    }

    /**
     * Group histogram rows into cells. Rows may arrive in any order; cells come
     * back in descending point count so the densest (most trustworthy) cells
     * are drawn last, on top.
     */
    fun cells(bins: List<GridBin>, query: GridQuery): List<GridCell> =
        bins.groupBy { it.latKey to it.lonKey }
            .map { (key, cellBins) ->
                val stats = stats(cellBins)
                val (latKey, lonKey) = key
                val south = cellSouth(latKey, query.latStepDeg)
                val west = cellWest(lonKey, query.lonStepDeg)
                GridCell(
                    latKey = latKey,
                    lonKey = lonKey,
                    southLatitude = south,
                    northLatitude = south + query.latStepDeg,
                    westLongitude = west,
                    eastLongitude = west + query.lonStepDeg,
                    count = stats.count,
                    median = stats.median,
                    p10 = stats.p10,
                    p90 = stats.p90,
                    minValue = stats.minValue,
                    maxValue = stats.maxValue,
                    fromMillis = stats.fromMillis,
                    toMillis = stats.toMillis,
                )
            }
            .sortedBy { it.count }

    /**
     * Order statistics over a set of histogram rows. Counts, extremes and the
     * time span are exact; quantiles land inside the bin that holds the order
     * statistic and are reported as the middle of the values actually measured
     * in that bin — so they are never a value nobody measured near.
     */
    fun stats(bins: List<GridBin>): GridStats {
        require(bins.isNotEmpty()) { "stats of an empty histogram" }
        val sorted = bins.sortedBy { it.valueKey }
        val total = sorted.sumOf { it.count }
        return GridStats(
            count = total,
            median = quantile(sorted, total, 0.5),
            p10 = quantile(sorted, total, 0.1),
            p90 = quantile(sorted, total, 0.9),
            minValue = sorted.first().minValue,
            maxValue = sorted.last().maxValue,
            fromMillis = sorted.minOf { it.minTime },
            toMillis = sorted.maxOf { it.maxTime },
        )
    }

    /**
     * Quantile of a value-sorted histogram: the bin holding order statistic
     * `floor(p · n)` (no interpolation — an interpolated value between two
     * bins would be a number nobody measured).
     */
    private fun quantile(sortedByValue: List<GridBin>, total: Int, p: Double): Float {
        val rank = floor(p * total).toLong().coerceIn(0L, (total - 1).toLong())
        var seen = 0L
        for (bin in sortedByValue) {
            seen += bin.count
            if (rank < seen) return (bin.minValue + bin.maxValue) / 2f
        }
        val last = sortedByValue.last()
        return (last.minValue + last.maxValue) / 2f
    }

    /** «20 м» / «1,5 км» for the legend line «клетка ≈ …». */
    fun formatCellSize(meters: Double, s: MapStrings = MapRu): String = when {
        meters >= 1_000 -> {
            val km = meters / 1000
            if (km >= 10) {
                String.format(Locale.US, "%.0f", km) + " " + s.unitKilometers
            } else {
                String.format(Locale.US, "%.1f", km).uiDecimal() + " " + s.unitKilometers
            }
        }
        meters >= 1 -> String.format(Locale.US, "%.0f", meters) + " " + s.unitMeters
        else -> String.format(Locale.US, "%.1f", meters).uiDecimal() + " " + s.unitMeters
    }
}
