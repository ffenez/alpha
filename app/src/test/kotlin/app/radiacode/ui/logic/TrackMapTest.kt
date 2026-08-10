package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.json.JSONObject

private fun point(
    timestamp: Long = 0L,
    lat: Double = 55.0,
    lon: Double = 37.0,
    accuracy: Float = 5f,
    dose: Float? = 0.10f,
    cps: Float? = 20f,
) = MapTrackPoint(
    timestamp = timestamp,
    latitude = lat,
    longitude = lon,
    accuracyMeters = accuracy,
    doseMicroSvH = dose,
    cps = cps,
)

class RampThresholdsTest {

    @Test
    fun `quartiles of a uniform ramp`() {
        val values = (1..9).map { it.toFloat() } // 1..9
        val t = TrackMap.rampThresholds(values)
        assertNotNull(t)
        assertEquals(3f, t.q1)
        assertEquals(5f, t.q2)
        assertEquals(7f, t.q3)
    }

    @Test
    fun `interpolates between ranks`() {
        val t = TrackMap.rampThresholds(listOf(0f, 1f)) // positions 0.25, 0.5, 0.75
        assertNotNull(t)
        assertEquals(0.25f, t.q1)
        assertEquals(0.5f, t.q2)
        assertEquals(0.75f, t.q3)
    }

    @Test
    fun `empty values give no thresholds`() {
        assertNull(TrackMap.rampThresholds(emptyList()))
    }

    @Test
    fun `unsorted input is handled`() {
        val t = TrackMap.rampThresholds(listOf(9f, 1f, 5f, 3f, 7f))
        assertNotNull(t)
        assertEquals(3f, t.q1)
        assertEquals(5f, t.q2)
        assertEquals(7f, t.q3)
    }

    @Test
    fun `bucket edges are inclusive on the low side`() {
        val t = TrackMap.RampThresholds(q1 = 1f, q2 = 2f, q3 = 3f)
        assertEquals(0, TrackMap.bucket(0.5f, t))
        assertEquals(0, TrackMap.bucket(1f, t))
        assertEquals(1, TrackMap.bucket(1.5f, t))
        assertEquals(1, TrackMap.bucket(2f, t))
        assertEquals(2, TrackMap.bucket(2.5f, t))
        assertEquals(2, TrackMap.bucket(3f, t))
        assertEquals(3, TrackMap.bucket(3.1f, t))
    }

    @Test
    fun `near-constant track collapses to the lightest bucket`() {
        val t = TrackMap.rampThresholds(List(50) { 0.11f })
        assertNotNull(t)
        assertEquals(0, TrackMap.bucket(0.11f, t))
    }
}

class ValueRangeTest {

    @Test
    fun `dose range over full track`() {
        val points = listOf(
            point(dose = 0.08f),
            point(dose = 0.31f),
            point(dose = null),
            point(dose = 0.12f),
        )
        assertEquals(0.08f to 0.31f, TrackMap.valueRange(points, TrackMetric.DOSE))
    }

    @Test
    fun `cps metric uses cps values`() {
        val points = listOf(point(cps = 18f), point(cps = 44f))
        assertEquals(18f to 44f, TrackMap.valueRange(points, TrackMetric.CPS))
    }

    @Test
    fun `all-null metric yields no range`() {
        assertNull(TrackMap.valueRange(listOf(point(dose = null)), TrackMetric.DOSE))
    }
}

class DownsampleTest {

    @Test
    fun `short lists pass through untouched`() {
        val points = List(10) { point(timestamp = it.toLong()) }
        assertSame(points, TrackMap.downsample(points, maxPoints = 10))
    }

    @Test
    fun `long lists are capped and keep both endpoints`() {
        val points = List(10_001) { point(timestamp = it.toLong()) }
        val sampled = TrackMap.downsample(points, maxPoints = 2000)
        assertTrue(sampled.size <= 2000, "size ${sampled.size}")
        assertSame(points.first(), sampled.first())
        assertSame(points.last(), sampled.last())
    }

    @Test
    fun `sampled points keep chronological order`() {
        val points = List(5000) { point(timestamp = it.toLong()) }
        val sampled = TrackMap.downsample(points, maxPoints = 100)
        assertEquals(sampled.map { it.timestamp }, sampled.map { it.timestamp }.sorted())
    }
}

class DistanceTest {

    @Test
    fun `haversine matches a known geodesic`() {
        // Moscow Kremlin -> Red Square-ish, ~1.11 km per 0.01 deg latitude.
        val d = TrackMap.haversineMeters(55.75, 37.62, 55.76, 37.62)
        assertTrue(d in 1100.0..1120.0, "d=$d")
    }

    @Test
    fun `stationary jitter below the segment floor adds nothing`() {
        // ~1.1 m apart — below MIN_SEGMENT_METERS.
        val points = List(60) { point(lat = 55.0 + (it % 2) * 0.00001) }
        assertEquals(0.0, TrackMap.distanceMeters(points))
    }

    @Test
    fun `inaccurate fixes are excluded`() {
        val points = listOf(
            point(lat = 55.00),
            point(lat = 55.01, accuracy = 500f), // bad fix would add ~2 km
            point(lat = 55.00, lon = 37.0),
        )
        assertEquals(0.0, TrackMap.distanceMeters(points))
    }

    @Test
    fun `walk distance accumulates`() {
        val points = List(11) { point(lat = 55.0 + it * 0.001) } // ~111 m steps
        val d = TrackMap.distanceMeters(points)
        assertTrue(d in 1100.0..1130.0, "d=$d")
    }
}

class BoundsTest {

    @Test
    fun `bounds cover all points`() {
        val points = listOf(
            point(lat = 55.0, lon = 37.0),
            point(lat = 55.2, lon = 36.8),
            point(lat = 54.9, lon = 37.1),
        )
        val b = TrackMap.bounds(points)
        assertNotNull(b)
        assertEquals(54.9, b.minLatitude)
        assertEquals(55.2, b.maxLatitude)
        assertEquals(36.8, b.minLongitude)
        assertEquals(37.1, b.maxLongitude)
    }

    @Test
    fun `no points - no bounds`() {
        assertNull(TrackMap.bounds(emptyList()))
    }
}

class SummaryTest {

    @Test
    fun `summary aggregates dose over all points`() {
        val points = listOf(
            point(dose = 0.10f),
            point(dose = 0.20f),
            point(dose = null),
        )
        val s = TrackMap.summary(points)
        assertEquals(3, s.pointCount)
        assertNotNull(s.avgDoseMicroSvH)
        assertEquals(0.15f, s.avgDoseMicroSvH, absoluteTolerance = 1e-6f)
        assertEquals(0.20f, s.maxDoseMicroSvH)
    }

    @Test
    fun `summary with no dose data`() {
        val s = TrackMap.summary(listOf(point(dose = null)))
        assertEquals(1, s.pointCount)
        assertNull(s.avgDoseMicroSvH)
        assertNull(s.maxDoseMicroSvH)
    }
}

class TrackGeoJsonTest {

    private fun features(json: String) =
        JSONObject(json).getJSONArray("features")

    @Test
    fun `track collection has a route line and one feature per point`() {
        val points = listOf(
            point(timestamp = 1000, lat = 55.1, lon = 37.2, dose = 0.10f, cps = 21f),
            point(timestamp = 2000, lat = 55.2, lon = 37.3, dose = 0.30f, cps = 44f),
        )
        val thresholds = TrackMap.rampThresholds(listOf(0.10f, 0.30f))
        val json = TrackMap.trackGeoJson(points, TrackMetric.DOSE, thresholds)

        val parsed = features(json)
        assertEquals(3, parsed.length()) // route + 2 points

        val route = parsed.getJSONObject(0)
        assertEquals("route", route.getJSONObject("properties").getString("kind"))
        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(2, coords.length())
        // GeoJSON order is [lon, lat].
        assertEquals(37.2, coords.getJSONArray(0).getDouble(0))
        assertEquals(55.1, coords.getJSONArray(0).getDouble(1))

        val first = parsed.getJSONObject(1)
        val props = first.getJSONObject("properties")
        assertEquals("pt", props.getString("kind"))
        assertEquals(1000L, props.getLong("t"))
        assertEquals(0.10, props.getDouble("dose"), 1e-6)
        assertEquals(21.0, props.getDouble("cps"), 1e-6)
        assertTrue(props.getInt("b") in 0..3)

        val second = parsed.getJSONObject(2).getJSONObject("properties")
        assertEquals(3, second.getInt("b")) // 0.30 is above q3 of {0.10, 0.30}
    }

    @Test
    fun `missing metric marks the point with bucket -1`() {
        val points = listOf(point(dose = null, cps = null))
        val json = TrackMap.trackGeoJson(points, TrackMetric.DOSE, null)
        val props = features(json).getJSONObject(1).getJSONObject("properties")
        assertEquals(-1, props.getInt("b"))
        assertTrue(!props.has("dose"))
        assertTrue(!props.has("cps"))
    }

    @Test
    fun `cps metric buckets by cps`() {
        val points = listOf(point(dose = null, cps = 100f))
        val thresholds = TrackMap.RampThresholds(10f, 20f, 30f)
        val json = TrackMap.trackGeoJson(points, TrackMetric.CPS, thresholds)
        val props = features(json).getJSONObject(1).getJSONObject("properties")
        assertEquals(3, props.getInt("b"))
    }

    @Test
    fun `hotspot collection carries id dose and typical`() {
        val hotspots = listOf(
            MapHotspot(
                id = 7,
                timestamp = 5000,
                latitude = 55.5,
                longitude = 37.5,
                doseMicroSvH = 0.42f,
                typicalMicroSvH = 0.11f,
            ),
            MapHotspot(
                id = 8,
                timestamp = 6000,
                latitude = 55.6,
                longitude = 37.6,
                doseMicroSvH = null,
                typicalMicroSvH = null,
            ),
        )
        val parsed = features(TrackMap.hotspotGeoJson(hotspots))
        assertEquals(2, parsed.length())
        val first = parsed.getJSONObject(0).getJSONObject("properties")
        assertEquals(7L, first.getLong("id"))
        assertEquals(0.42, first.getDouble("dose"), 1e-6)
        assertEquals(0.11, first.getDouble("typ"), 1e-6)
        val second = parsed.getJSONObject(1).getJSONObject("properties")
        assertTrue(!second.has("dose"))
        assertTrue(!second.has("typ"))
    }

    @Test
    fun `empty inputs produce valid empty collections`() {
        assertEquals(1, features(TrackMap.trackGeoJson(emptyList(), TrackMetric.DOSE, null)).length())
        assertEquals(0, features(TrackMap.hotspotGeoJson(emptyList())).length())
    }
}

class DwellTest {

    @Test
    fun `dwell lasts until dose drops below the re-arm floor`() {
        val samples = listOf(
            10_000L to 0.50f,
            11_000L to 0.48f,
            12_000L to 0.45f,
            13_000L to 0.20f, // below 0.5 * 0.8 = 0.4
            14_000L to 0.50f,
        )
        assertEquals(2L, TrackMap.dwellSeconds(samples, 10_000L, 0.50f))
    }

    @Test
    fun `a sampling gap breaks continuity`() {
        val samples = listOf(
            10_000L to 0.50f,
            11_000L to 0.50f,
            20_000L to 0.50f, // 9 s gap
        )
        assertEquals(1L, TrackMap.dwellSeconds(samples, 10_000L, 0.50f))
    }

    @Test
    fun `samples before the event are ignored`() {
        val samples = listOf(
            5_000L to 0.05f,
            10_000L to 0.50f,
            11_000L to 0.50f,
        )
        assertEquals(1L, TrackMap.dwellSeconds(samples, 10_000L, 0.50f))
    }

    @Test
    fun `no samples - zero dwell`() {
        assertEquals(0L, TrackMap.dwellSeconds(emptyList(), 10_000L, 0.50f))
    }
}

class TrackFormatTest {

    @Test
    fun `distance formats by magnitude`() {
        assertEquals("340 м", TrackMap.formatDistance(340.4))
        assertEquals("1,2 км", TrackMap.formatDistance(1_234.0))
        assertEquals("12 км", TrackMap.formatDistance(12_300.0))
    }

    @Test
    fun `cps formats with one decimal below ten`() {
        assertEquals("9,4", TrackMap.formatCps(9.44f))
        assertEquals("27", TrackMap.formatCps(27.4f))
    }
}
