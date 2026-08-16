package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

class RampScaleTest {

    /**
     * Главное свойство абсолютной шкалы: её границы не зависят от маршрута.
     * Поэтому одно и то же значение красится одинаково и в прогулке по двору,
     * и в поездке — иначе цвет нельзя было бы сравнивать вообще ни с чем.
     */
    @Test
    fun `the absolute scale does not depend on what was measured`() {
        val scale = TrackMap.absoluteScale(usualLow = 0.09f, usualHigh = 0.14f, factor = 2f)
        assertNotNull(scale)
        assertEquals(MapColorScale.ABSOLUTE, scale.mode)
        assertEquals(TrackMap.RAMP_STEPS - 1, scale.bounds.size)
        // Внутри обычного диапазона — только зелёные ступени.
        assertEquals(0, TrackMap.bucket(0.09f, scale))
        assertEquals(1, TrackMap.bucket(0.14f, scale))
        // Выше обычного цвет уходит вверх, а на верху шкалы — багровый.
        assertTrue(TrackMap.bucket(0.16f, scale) >= 2)
        assertEquals(TrackMap.RAMP_STEPS - 1, TrackMap.bucket(0.29f, scale))
        assertEquals(TrackMap.RAMP_STEPS - 1, TrackMap.bucket(3f, scale))
        assertEquals(0.09f, scale.low)
        assertEquals(0.28f, scale.high)
    }

    /** Множитель человека двигает ВЕРХ шкалы, а не её смысл. */
    @Test
    fun `the ceiling follows the multiplier`() {
        val gentle = TrackMap.absoluteScale(0.09f, 0.14f, factor = 3f)
        assertNotNull(gentle)
        assertEquals(0.42f, gentle.high, 1e-4f)
        // То же значение при более далёком верхе — ступенью ниже.
        val strict = TrackMap.absoluteScale(0.09f, 0.14f, factor = 1.5f)
        assertNotNull(strict)
        assertTrue(TrackMap.bucket(0.20f, gentle) < TrackMap.bucket(0.20f, strict))
    }

    @Test
    fun `a degenerate band has no absolute scale`() {
        assertNull(TrackMap.absoluteScale(0.14f, 0.14f, 2f))
        assertNull(TrackMap.absoluteScale(0.20f, 0.14f, 2f))
        assertNull(TrackMap.absoluteScale(0f, 0f, 2f))
        assertNull(TrackMap.absoluteScale(Float.NaN, 0.14f, 2f))
    }

    /** Растяжение по маршруту: границы — семичастные квантили его значений. */
    @Test
    fun `the route contrast spreads the ramp over the route itself`() {
        val values = (1..7).map { it.toFloat() }
        val scale = TrackMap.contrastScale(values)
        assertNotNull(scale)
        assertEquals(MapColorScale.ROUTE_CONTRAST, scale.mode)
        assertEquals(1f, scale.low)
        assertEquals(7f, scale.high)
        assertEquals(0, TrackMap.bucket(1f, scale))
        assertEquals(TrackMap.RAMP_STEPS - 1, TrackMap.bucket(7f, scale))
        // Порядок на входе не важен — считается по отсортированным.
        assertEquals(scale, TrackMap.contrastScale(values.reversed()))
    }

    @Test
    fun `empty values give no scale`() {
        assertNull(TrackMap.contrastScale(emptyList()))
    }

    /**
     * Ровный маршрут складывается в нижнюю ступень и в растянутом режиме:
     * показывать разброс там, где его нет, значило бы рисовать событие.
     */
    @Test
    fun `a near-constant track collapses to the lowest step`() {
        val scale = TrackMap.contrastScale(List(50) { 0.11f })
        assertNotNull(scale)
        assertEquals(0, TrackMap.bucket(0.11f, scale))
    }

    @Test
    fun `bucket edges are inclusive on the low side`() {
        val scale = TrackMap.RampScale(
            bounds = listOf(1f, 2f, 3f, 4f, 5f, 6f),
            mode = MapColorScale.ROUTE_CONTRAST,
            low = 0f,
            high = 7f,
        )
        assertEquals(0, TrackMap.bucket(1f, scale))
        assertEquals(1, TrackMap.bucket(1.5f, scale))
        assertEquals(5, TrackMap.bucket(6f, scale))
        assertEquals(6, TrackMap.bucket(6.1f, scale))
    }
}

class ManualScaleTest {

    /**
     * Границы задаёт человек, и приложение не спорит с ними — только приводит
     * в порядок: сортирует и убирает повторы. Цвет между ними означает ровно
     * то, что человек задал, и ничего сверх того.
     */
    @Test
    fun `hand-set bounds are sorted and deduplicated`() {
        val scale = TrackMap.manualScale(listOf(0.30f, 0.05f, 0.10f, 0.10f, 0.20f))
        assertNotNull(scale)
        assertEquals(MapColorScale.MANUAL, scale.mode)
        assertEquals(listOf(0.05f, 0.10f, 0.20f, 0.30f), scale.bounds)
        assertEquals(0, TrackMap.bucket(0.04f, scale))
        assertEquals(2, TrackMap.bucket(0.15f, scale))
        assertEquals(4, TrackMap.bucket(0.31f, scale))
    }

    @Test
    fun `one bound is not a scale, and nonsense is dropped`() {
        assertNull(TrackMap.manualScale(listOf(0.10f)))
        assertNull(TrackMap.manualScale(listOf(-1f, 0f, Float.NaN)))
    }

    /** Больше ступеней, чем у шкалы цветов, взять неоткуда. */
    @Test
    fun `no more bounds than the ramp has steps`() {
        val many = (1..20).map { it * 0.1f }
        val scale = TrackMap.manualScale(many)
        assertNotNull(scale)
        assertEquals(TrackMap.RAMP_STEPS - 1, scale.bounds.size)
    }

    @Test
    fun `the chosen mode picks the hand-set scale`() {
        val scale = TrackMap.scaleFor(
            mode = MapColorScale.MANUAL,
            usualBand = 0.09f to 0.14f,
            factor = 2f,
            values = listOf(0.10f, 0.30f),
            manualAnchors = listOf(0.05f, 0.10f, 0.20f),
        )
        assertEquals(MapColorScale.MANUAL, scale!!.mode)

        // Границ не задали — шкала честно становится растянутой по маршруту.
        val fallback = TrackMap.scaleFor(
            mode = MapColorScale.MANUAL,
            usualBand = 0.09f to 0.14f,
            factor = 2f,
            values = listOf(0.10f, 0.30f),
            manualAnchors = emptyList(),
        )
        assertEquals(MapColorScale.ROUTE_CONTRAST, fallback!!.mode)
    }
}

/** Границы пишет человек, а не парсер: разделитель угадывать он не обязан. */
class MapAnchorsTest {

    /**
     * Запятая бывает и разделителем, и десятичным знаком: «0,05 0,1» — два
     * числа, а «0.05,0.1» — тоже два, и правило деления объявлено заранее.
     */
    @Test
    fun `the separator is decided before the comma is read`() {
        // Есть пробел — делит он, запятая означает дробную часть.
        assertEquals(listOf(0.05f, 0.1f, 0.2f), MapAnchors.parse("0,05 0,1 0,2"))
        assertEquals(listOf(0.05f, 0.1f), MapAnchors.parse("0.05; 0.1"))
        // Ничего, кроме запятых, — делит запятая.
        assertEquals(listOf(0.05f, 0.1f, 0.2f), MapAnchors.parse("0.05,0.1,0.2"))
        assertEquals(listOf(10f, 20f, 40f), MapAnchors.parse("40;10;20"))
        assertEquals(emptyList(), MapAnchors.parse("  "))
        // Мусор просто не попадает в границы — поле не ругается на человека.
        assertEquals(listOf(0.1f), MapAnchors.parse("0,1 abc -5 0"))
    }

    @Test
    fun `what was typed comes back readable`() {
        assertEquals("0,05 0,1 0,2", MapAnchors.format(listOf(0.05f, 0.1f, 0.2f)))
        assertEquals("10 20 40", MapAnchors.format(listOf(10f, 20f, 40f)))
    }
}

class ScaleChoiceTest {

    private val values = listOf(0.10f, 0.12f, 0.14f, 0.30f)

    @Test
    fun `the chosen mode decides, and the place band is what absolute needs`() {
        val absolute = TrackMap.scaleFor(
            MapColorScale.ABSOLUTE,
            usualBand = 0.09f to 0.14f,
            factor = 2f,
            values = values,
        )
        assertNotNull(absolute)
        assertEquals(MapColorScale.ABSOLUTE, absolute.mode)

        val contrast = TrackMap.scaleFor(
            MapColorScale.ROUTE_CONTRAST,
            usualBand = 0.09f to 0.14f,
            factor = 2f,
            values = values,
        )
        assertNotNull(contrast)
        assertEquals(MapColorScale.ROUTE_CONTRAST, contrast.mode)
    }

    /**
     * Без обычного фона места абсолютная шкала не на чём держится, и
     * приложение не выдумывает опоры: оно переходит к растяжению и говорит об
     * этом режимом в легенде.
     */
    @Test
    fun `without a place band the absolute mode falls back and says so`() {
        val scale = TrackMap.scaleFor(
            MapColorScale.ABSOLUTE,
            usualBand = null,
            factor = 2f,
            values = values,
        )
        assertNotNull(scale)
        assertEquals(MapColorScale.ROUTE_CONTRAST, scale.mode)
    }

    @Test
    fun `nothing measured, nothing to colour`() {
        assertNull(TrackMap.scaleFor(MapColorScale.ABSOLUTE, null, 2f, emptyList()))
    }
}

/**
 * Пропуск координат не соединяется прямой: линия через дыру утверждает, что
 * человек прошёл именно так, а этого никто не измерял.
 */
class LineBreaksTest {

    @Test
    fun `a continuous walk has no breaks except its start`() {
        val points = (0..5).map { point(timestamp = it * 1_000L, lat = 55.0 + it * 0.0001) }
        val breaks = TrackMap.lineBreaks(points)
        assertTrue(breaks[0])
        assertTrue(breaks.drop(1).none { it })
    }

    @Test
    fun `a long silence breaks the line`() {
        val points = listOf(
            point(timestamp = 0L),
            point(timestamp = (TrackMap.LINE_GAP_SECONDS + 1) * 1_000L, lat = 55.001),
        )
        assertTrue(TrackMap.lineBreaks(points)[1])
    }

    @Test
    fun `a teleport breaks the line even without a time gap`() {
        val points = listOf(
            point(timestamp = 0L, lat = 55.0),
            point(timestamp = 1_000L, lat = 55.1),
        )
        assertTrue(TrackMap.lineBreaks(points)[1])
    }

    @Test
    fun `no points, no breaks`() {
        assertEquals(0, TrackMap.lineBreaks(emptyList()).size)
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

class NearestIndexTest {

    private val xs = floatArrayOf(0f, 100f, 200f)
    private val ys = floatArrayOf(0f, 100f, 200f)

    @Test
    fun `tap inside the slop selects the nearest point`() {
        assertEquals(1, TrackMap.nearestIndex(xs, ys, 104f, 96f, 16f))
    }

    @Test
    fun `tap outside the slop of every point selects nothing`() {
        assertEquals(-1, TrackMap.nearestIndex(xs, ys, 150f, 150f, 16f))
    }

    @Test
    fun `the closest of two candidates wins`() {
        val closeXs = floatArrayOf(0f, 10f)
        val closeYs = floatArrayOf(0f, 0f)
        assertEquals(1, TrackMap.nearestIndex(closeXs, closeYs, 8f, 0f, 20f))
        assertEquals(0, TrackMap.nearestIndex(closeXs, closeYs, 2f, 0f, 20f))
    }

    @Test
    fun `slop is a radius, not a square`() {
        // (12, 12) is 16.97 px away — outside a 16 px radius.
        assertEquals(-1, TrackMap.nearestIndex(floatArrayOf(0f), floatArrayOf(0f), 12f, 12f, 16f))
        assertEquals(0, TrackMap.nearestIndex(floatArrayOf(0f), floatArrayOf(0f), 11f, 11f, 16f))
    }

    @Test
    fun `no points - no hit`() {
        assertEquals(-1, TrackMap.nearestIndex(FloatArray(0), FloatArray(0), 0f, 0f, 16f))
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
