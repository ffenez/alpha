package app.alpha.ui.logic

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Math of the accumulated radiation map («все записи»). */
class TrackGridTest {

    private fun bin(
        latKey: Int = 0,
        lonKey: Int = 0,
        valueKey: Int,
        count: Int,
        minValue: Float,
        maxValue: Float = minValue,
        minTime: Long = 1_000,
        maxTime: Long = 2_000,
    ) = GridBin(latKey, lonKey, valueKey, count, minValue, maxValue, minTime, maxTime)

    // --- cell size ladder ---

    @Test
    fun `cell size follows zoom on a 1-2-5 ladder`() {
        val street = TrackGrid.cellMeters(zoom = 19.0, latitude = 0.0)
        val district = TrackGrid.cellMeters(zoom = 15.0, latitude = 0.0)
        val city = TrackGrid.cellMeters(zoom = 12.0, latitude = 0.0)
        for (meters in listOf(street, district, city)) {
            val mantissa = meters / Math.pow(10.0, Math.floor(Math.log10(meters)))
            assertTrue(
                abs(mantissa - 1) < 1e-9 || abs(mantissa - 2) < 1e-9 || abs(mantissa - 5) < 1e-9,
                "$meters is not a 1/2/5 step",
            )
        }
        assertTrue(street < district, "zooming in must not enlarge cells")
        assertTrue(district < city)
    }

    @Test
    fun `cells shrink with latitude because meridians converge`() {
        val equator = TrackGrid.cellMeters(zoom = 16.0, latitude = 0.0)
        val north = TrackGrid.cellMeters(zoom = 16.0, latitude = 60.0)
        assertTrue(north < equator)
    }

    @Test
    fun `cell size stays inside sane bounds at any zoom`() {
        assertEquals(TrackGrid.MIN_CELL_METERS, TrackGrid.cellMeters(zoom = 30.0, latitude = 0.0))
        assertTrue(TrackGrid.cellMeters(zoom = 1.0, latitude = 0.0) <= TrackGrid.MAX_CELL_METERS)
    }

    // --- viewport query ---

    @Test
    fun `the query pads the viewport and derives square-ish steps`() {
        val viewport = MapViewport(
            bounds = MapBounds(
                minLatitude = 55.70,
                maxLatitude = 55.80,
                minLongitude = 37.50,
                maxLongitude = 37.70,
            ),
            zoom = 14.0,
        )
        val query = TrackGrid.query(viewport)

        val latPad = 0.10 * TrackGrid.VIEWPORT_PADDING_FRACTION
        assertEquals(55.70 - latPad, query.minLatitude, 1e-9)
        assertEquals(55.80 + latPad, query.maxLatitude, 1e-9)
        assertTrue(query.minLongitude < 37.50 && query.maxLongitude > 37.70)

        assertEquals(query.cellMeters / TrackGrid.METERS_PER_DEGREE_LATITUDE, query.latStepDeg, 1e-12)
        // A degree of longitude is shorter at 55°N, so its step is wider.
        assertTrue(query.lonStepDeg > query.latStepDeg)
    }

    // --- cell keys: must mirror the SQL exactly ---

    @Test
    fun `cell keys round-trip to the cell they came from`() {
        val step = 0.001
        val latitude = -33.5004
        val longitude = -70.6501
        val south = TrackGrid.cellSouth(TrackGrid.latKey(latitude, step), step)
        val west = TrackGrid.cellWest(TrackGrid.lonKey(longitude, step), step)
        assertTrue(south <= latitude && latitude < south + step, "latitude outside its own cell")
        assertTrue(west <= longitude && longitude < west + step, "longitude outside its own cell")
    }

    @Test
    fun `keys stay monotone in the southern and western hemispheres`() {
        val step = 0.001
        assertTrue(TrackGrid.latKey(-33.5005, step) < TrackGrid.latKey(-33.4995, step))
        assertTrue(TrackGrid.lonKey(-70.6505, step) < TrackGrid.lonKey(-70.6495, step))
    }

    @Test
    fun `neighbouring coordinates share a cell`() {
        val step = 0.001
        assertEquals(TrackGrid.latKey(55.75001, step), TrackGrid.latKey(55.75099, step))
        assertTrue(TrackGrid.latKey(55.75001, step) != TrackGrid.latKey(55.7511, step))
    }

    // --- value binning ---

    @Test
    fun `value bins span the exact range of the matching set`() {
        val bins = TrackGrid.valueBins(min = 0.1f, max = 0.5f, bins = 4)
        assertEquals(0.1f, bins.min)
        assertEquals(0.1f, bins.step, 1e-6f)
        assertEquals(0, bins.key(0.10f))
        assertEquals(1, bins.key(0.21f))
        assertEquals(3, bins.key(0.45f))
    }

    @Test
    fun `a degenerate range still produces a usable step`() {
        val bins = TrackGrid.valueBins(min = 0.12f, max = 0.12f)
        assertTrue(bins.step > 0f)
        assertEquals(0, bins.key(0.12f))
    }

    // --- order statistics over the histogram ---

    @Test
    fun `stats count every point of the histogram, not the drawn subset`() {
        val stats = TrackGrid.stats(
            listOf(
                bin(valueKey = 0, count = 10, minValue = 0.10f, maxValue = 0.11f),
                bin(valueKey = 1, count = 5, minValue = 0.12f, maxValue = 0.13f),
                bin(valueKey = 9, count = 1, minValue = 0.90f, maxValue = 0.90f),
            ),
        )
        assertEquals(16, stats.count)
        assertEquals(0.10f, stats.minValue)
        assertEquals(0.90f, stats.maxValue)
    }

    @Test
    fun `the median ignores a single hot fix that would move a mean`() {
        val bins = listOf(
            bin(valueKey = 0, count = 9, minValue = 0.10f, maxValue = 0.10f),
            bin(valueKey = 31, count = 1, minValue = 5.00f, maxValue = 5.00f),
        )
        val stats = TrackGrid.stats(bins)
        val mean = (9 * 0.10f + 5.00f) / 10
        assertEquals(0.10f, stats.median, 1e-6f)
        assertTrue(mean > 0.5f, "the mean would have been dragged to $mean")
        assertEquals(0.10f, stats.p10, 1e-6f)
        assertEquals(5.00f, stats.p90, 1e-6f)
    }

    @Test
    fun `quantiles land on values that were actually measured`() {
        // 100 points: 0.10 ×90, 0.30 ×10 → P90 is the first high one.
        val stats = TrackGrid.stats(
            listOf(
                bin(valueKey = 0, count = 90, minValue = 0.10f, maxValue = 0.10f),
                bin(valueKey = 20, count = 10, minValue = 0.30f, maxValue = 0.30f),
            ),
        )
        assertEquals(0.10f, stats.p10, 1e-6f)
        assertEquals(0.10f, stats.median, 1e-6f)
        assertEquals(0.30f, stats.p90, 1e-6f)
    }

    @Test
    fun `a single point is its own median`() {
        val stats = TrackGrid.stats(listOf(bin(valueKey = 3, count = 1, minValue = 0.42f)))
        assertEquals(0.42f, stats.median)
        assertEquals(0.42f, stats.p10)
        assertEquals(0.42f, stats.p90)
        assertEquals(1, stats.count)
    }

    @Test
    fun `the time span covers every bin of the set`() {
        val stats = TrackGrid.stats(
            listOf(
                bin(valueKey = 0, count = 2, minValue = 0.1f, minTime = 500, maxTime = 900),
                bin(valueKey = 1, count = 3, minValue = 0.2f, minTime = 100, maxTime = 4_000),
            ),
        )
        assertEquals(100, stats.fromMillis)
        assertEquals(4_000, stats.toMillis)
    }

    // --- cells ---

    @Test
    fun `bins are grouped into cells with their own geography and counts`() {
        val query = GridQuery(
            minLatitude = 55.0,
            maxLatitude = 56.0,
            minLongitude = 37.0,
            maxLongitude = 38.0,
            latStepDeg = 0.001,
            lonStepDeg = 0.002,
            cellMeters = 100.0,
        )
        val latKey = TrackGrid.latKey(55.75, query.latStepDeg)
        val lonKey = TrackGrid.lonKey(37.60, query.lonStepDeg)
        val cells = TrackGrid.cells(
            listOf(
                bin(latKey, lonKey, valueKey = 0, count = 3, minValue = 0.10f, maxValue = 0.11f),
                bin(latKey, lonKey, valueKey = 2, count = 1, minValue = 0.20f),
                bin(latKey + 1, lonKey, valueKey = 0, count = 9, minValue = 0.09f),
            ),
            query,
        )

        assertEquals(2, cells.size)
        // Densest last: it is drawn on top of its sparser neighbours.
        assertEquals(9, cells.last().count)
        val first = cells.first { it.latKey == latKey }
        assertEquals(4, first.count)
        assertEquals(0.10f, first.minValue)
        assertEquals(0.20f, first.maxValue)
        assertTrue(first.contains(55.75, 37.60), "a cell must contain the point that built it")
        assertEquals(query.latStepDeg, first.northLatitude - first.southLatitude, 1e-12)
        assertEquals(query.lonStepDeg, first.eastLongitude - first.westLongitude, 1e-12)
    }

    @Test
    fun `every point of the query ends up in exactly one cell`() {
        val query = GridQuery(55.0, 56.0, 37.0, 38.0, 0.001, 0.002, 100.0)
        val bins = (0 until 20).map {
            bin(
                latKey = it % 3,
                lonKey = it % 2,
                valueKey = it % 5,
                count = it + 1,
                minValue = 0.1f + it * 0.01f,
            )
        }
        val cells = TrackGrid.cells(bins, query)
        assertEquals(bins.sumOf { it.count }, cells.sumOf { it.count })
        assertEquals(bins.sumOf { it.count }, TrackGrid.stats(bins).count)
    }

    // --- formatting ---

    @Test
    fun `cell size is spoken in meters and kilometers`() {
        assertEquals("20 м", TrackGrid.formatCellSize(20.0))
        assertEquals("1,5 км", TrackGrid.formatCellSize(1_500.0))
        assertEquals("50 км", TrackGrid.formatCellSize(50_000.0))
        assertEquals("0,5 м", TrackGrid.formatCellSize(0.5))
    }
}
