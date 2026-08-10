package app.radiacode.ui.map

import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.radiacode.ui.logic.MapBounds
import app.radiacode.ui.logic.MapHotspot
import app.radiacode.ui.logic.MapTrackPoint
import app.radiacode.ui.logic.TileFilter
import app.radiacode.ui.logic.TrackMap
import app.radiacode.ui.logic.TrackMetric
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

/** Parsed tap on a rendered feature; null tap dismisses the info card. */
sealed interface MapTapInfo {
    data class TrackPoint(
        val timestamp: Long,
        val doseMicroSvH: Float?,
        val cps: Float?,
    ) : MapTapInfo

    data class Hotspot(
        val id: Long,
        val timestamp: Long,
        val doseMicroSvH: Float?,
        val typicalMicroSvH: Float?,
    ) : MapTapInfo
}

/** Colors the map overlays take from the app theme (ARGB ints). */
data class MapLayerColors(
    val route: Int,
    val ramp: List<Int>,
    val metricMissing: Int,
    val hotspotFill: Int,
    val hotspotStroke: Int,
)

/** Live tile counters for the honest status line (see `TileStatus`). */
data class TileStats(val loaded: Int, val failed: Int)

/**
 * osmdroid bridge: raster OSM tiles with the track drawn as a route polyline
 * plus one small ramp-colored dot per fix, hotspot markers on top, feature
 * taps forwarded, and the camera following the track bounds until the user
 * pans.
 *
 * Why raster osmdroid and not a vector GL engine: MapLibre GL Native rendered
 * a grey screen on the target device twice (Vulkan artifact, then the OpenGL
 * artifact), and we cannot debug a GPU pipeline blind. osmdroid draws tiles
 * through the ordinary `Canvas` API on the window's normal hardware layer —
 * no engine surface of its own, so there is no grey-screen failure mode.
 *
 * Tiles come from the OSM standard («Mapnik») raster tile servers, so the
 * OSM Foundation tile usage policy applies: a distinctive User-Agent (set in
 * [OsmSetup]) and no bulk downloading. That is why offline pre-caching of a
 * region is deliberately NOT implemented — only the ordinary display cache of
 * what the user actually looked at.
 */
@Composable
fun TrackMapView(
    dark: Boolean,
    layerColors: MapLayerColors,
    /** Already downsampled to [TrackMap.MAX_RENDERED_POINTS] by the caller. */
    points: List<MapTrackPoint>,
    metric: TrackMetric,
    thresholds: TrackMap.RampThresholds?,
    hotspots: List<MapHotspot>,
    bounds: MapBounds?,
    /** Increment to re-enable auto-fit after the user panned away. */
    recenterTick: Int,
    onTap: (MapTapInfo?) -> Unit,
    onTileStats: (TileStats) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapHolder(onTap = onTap, onTileStats = onTileStats) }
    holder.onTap = onTap
    holder.onTileStats = onTileStats

    AndroidView(
        modifier = modifier,
        factory = { context ->
            OsmSetup.ensureInitialized(context)
            MapView(context).also { holder.attach(it) }
        },
        update = {
            holder.applyTheme(dark, layerColors)
            holder.setData(points, metric, thresholds, hotspots)
            holder.fitBounds(bounds, recenterTick)
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = holder.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }
}

/** Touch slop around a tap when hit-testing rendered dots, dp. */
private const val TAP_SLOP_DP = 16f
private const val POINT_RADIUS_DP = 3.5f
private const val HOTSPOT_RADIUS_DP = 7f
private const val ROUTE_WIDTH_DP = 2.5f
private const val FIT_PADDING_DP = 40f
private const val DEGENERATE_ZOOM = 16.0
private const val DEFAULT_ZOOM = 4.0

/** Tile counters change per tile; the status line does not need every one. */
private const val TILE_STATS_THROTTLE_MILLIS = 250L

private class MapHolder(
    var onTap: (MapTapInfo?) -> Unit,
    var onTileStats: (TileStats) -> Unit,
) {
    var mapView: MapView? = null
    private var pointsOverlay: DotOverlay<MapTrackPoint>? = null
    private var hotspotOverlay: DotOverlay<MapHotspot>? = null
    private var route: Polyline? = null
    private var appliedDark: Boolean? = null
    private var userGestured = false
    private var fittedBounds: MapBounds? = null
    private var fittedRecenterTick = -1
    private var loaded = 0
    private var failed = 0
    private var lastStatsAt = 0L
    private var tileHandler: Handler? = null

    fun attach(mapView: MapView) {
        this.mapView = mapView
        val density = mapView.resources.displayMetrics.density
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setTilesScaledToDpi(true)
        mapView.setMultiTouchControls(true)
        mapView.isHorizontalMapRepetitionEnabled = false
        mapView.isVerticalMapRepetitionEnabled = false
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.controller.setZoom(DEFAULT_ZOOM)

        // OSM attribution is a licence requirement, not decoration.
        mapView.overlays.add(CopyrightOverlay(mapView.context))

        // Bottom-most: notices real navigation gestures so auto-fit stops
        // fighting the user. A plain tap is not a gesture — it opens a card.
        mapView.overlays.add(GestureWatcher { userGestured = true })

        // Overlays are offered a tap topmost-first, so this one — below the
        // dot layers — only runs when the tap hit nothing and must dismiss
        // the open info card.
        mapView.overlays.add(EmptyTapOverlay { onTap(null) })

        // Plain Polyline (no MapView argument): no default info window, and
        // the click listener returns false so a tap near the line falls
        // through to the dot layers instead of being swallowed.
        val routeLine = Polyline().apply {
            outlinePaint.strokeWidth = ROUTE_WIDTH_DP * density
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            setOnClickListener { _, _, _ -> false }
        }
        route = routeLine
        mapView.overlays.add(routeLine)

        val trackDots = DotOverlay<MapTrackPoint>(
            radiusPx = POINT_RADIUS_DP * density,
            slopPx = TAP_SLOP_DP * density,
            strokeWidthPx = 0f,
            latitude = { it.latitude },
            longitude = { it.longitude },
        ) { point ->
            onTap(
                MapTapInfo.TrackPoint(
                    timestamp = point.timestamp,
                    doseMicroSvH = point.doseMicroSvH,
                    cps = point.cps,
                ),
            )
        }
        pointsOverlay = trackDots
        mapView.overlays.add(trackDots)

        val hotspotDots = DotOverlay<MapHotspot>(
            radiusPx = HOTSPOT_RADIUS_DP * density,
            slopPx = TAP_SLOP_DP * density,
            strokeWidthPx = 2f * density,
            latitude = { it.latitude },
            longitude = { it.longitude },
        ) { hotspot ->
            onTap(
                MapTapInfo.Hotspot(
                    id = hotspot.id,
                    timestamp = hotspot.timestamp,
                    doseMicroSvH = hotspot.doseMicroSvH,
                    typicalMicroSvH = hotspot.typicalMicroSvH,
                ),
            )
        }
        hotspotOverlay = hotspotDots
        mapView.overlays.add(hotspotDots)

        val handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                when (message.what) {
                    MapTileProviderBase.MAPTILE_SUCCESS_ID -> loaded++
                    MapTileProviderBase.MAPTILE_FAIL_ID -> failed++
                    else -> return
                }
                val now = System.currentTimeMillis()
                if (loaded + failed <= 1 || now - lastStatsAt >= TILE_STATS_THROTTLE_MILLIS) {
                    lastStatsAt = now
                    onTileStats(TileStats(loaded, failed))
                }
            }
        }
        tileHandler = handler
        mapView.tileProvider.tileRequestCompleteHandlers.add(handler)
    }

    fun applyTheme(dark: Boolean, colors: MapLayerColors) {
        val mapView = mapView ?: return
        pointsOverlay?.colors = colors.ramp.toIntArray()
        pointsOverlay?.fallbackColor = colors.metricMissing
        hotspotOverlay?.colors = intArrayOf(colors.hotspotFill)
        hotspotOverlay?.strokeColor = colors.hotspotStroke
        route?.outlinePaint?.color = colors.route
        if (appliedDark != dark) {
            appliedDark = dark
            // Light theme: raster OSM tiles are already a light map, so no
            // filter at all — filtering would only cost fidelity.
            mapView.overlayManager.tilesOverlay.setColorFilter(
                if (dark) ColorMatrixColorFilter(TileFilter.darkMatrix()) else null,
            )
            mapView.invalidate()
        }
    }

    fun setData(
        points: List<MapTrackPoint>,
        metric: TrackMetric,
        thresholds: TrackMap.RampThresholds?,
        hotspots: List<MapHotspot>,
    ) {
        val mapView = mapView ?: return
        val dots = pointsOverlay ?: return
        if (!dots.sameItems(points) || dots.metric != metric || dots.thresholds != thresholds) {
            dots.metric = metric
            dots.thresholds = thresholds
            dots.setItems(points) { point ->
                val value = TrackMap.metricValue(point, metric)
                if (value != null && thresholds != null) TrackMap.bucket(value, thresholds) else -1
            }
            route?.setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
            mapView.invalidate()
        }
        val hotspotDots = hotspotOverlay ?: return
        if (!hotspotDots.sameItems(hotspots)) {
            hotspotDots.setItems(hotspots) { 0 }
            mapView.invalidate()
        }
    }

    fun fitBounds(bounds: MapBounds?, recenterTick: Int) {
        val mapView = mapView ?: return
        if (recenterTick != fittedRecenterTick) {
            fittedRecenterTick = recenterTick
            userGestured = false
            fittedBounds = null
        }
        if (bounds == null || userGestured || bounds == fittedBounds) return
        fittedBounds = bounds
        val fit = Runnable { applyBounds(mapView, bounds) }
        if (mapView.isLayoutOccurred) fit.run() else mapView.addOnFirstLayoutListener { _, _, _, _, _ -> fit.run() }
    }

    private fun applyBounds(mapView: MapView, bounds: MapBounds) {
        val degenerate = bounds.maxLatitude - bounds.minLatitude < 1e-6 &&
            bounds.maxLongitude - bounds.minLongitude < 1e-6
        if (degenerate) {
            mapView.controller.setZoom(DEGENERATE_ZOOM)
            mapView.controller.setCenter(GeoPoint(bounds.minLatitude, bounds.minLongitude))
        } else {
            val padding = (FIT_PADDING_DP * mapView.resources.displayMetrics.density).toInt()
            mapView.zoomToBoundingBox(
                BoundingBox(
                    bounds.maxLatitude,
                    bounds.maxLongitude,
                    bounds.minLatitude,
                    bounds.minLongitude,
                ),
                false,
                padding,
            )
        }
    }

    fun destroy() {
        val mapView = mapView ?: return
        tileHandler?.let { mapView.tileProvider.tileRequestCompleteHandlers.remove(it) }
        tileHandler = null
        mapView.onDetach()
        this.mapView = null
        pointsOverlay = null
        hotspotOverlay = null
        route = null
    }
}

/**
 * Batched dot layer: one filled circle per item, colored by a precomputed
 * index into [colors]. Deliberately a single custom overlay rather than
 * osmdroid `Marker`s — a marker is a View-like object with its own drawable
 * and info window, and 2000 of them would be unusable; here every frame is
 * one projection pass plus `drawCircle` per visible dot, and off-screen dots
 * cost only the projection.
 *
 * The projected pixels of the last frame are kept so a tap hit-tests exactly
 * what is on screen, through the pure [TrackMap.nearestIndex].
 */
private class DotOverlay<T>(
    private val radiusPx: Float,
    private val slopPx: Float,
    strokeWidthPx: Float,
    private val latitude: (T) -> Double,
    private val longitude: (T) -> Double,
    private val onSelect: (T) -> Unit,
) : Overlay() {

    var colors: IntArray = intArrayOf()
    var fallbackColor: Int = 0
    var strokeColor: Int = 0
    var metric: TrackMetric? = null
    var thresholds: TrackMap.RampThresholds? = null

    private var items: List<T> = emptyList()
    private var colorIndex: IntArray = IntArray(0)
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var projected = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }
    private val hasStroke = strokeWidthPx > 0f
    private val reusablePoint = Point()
    private val reusableGeoPoint = GeoPoint(0.0, 0.0)

    fun sameItems(other: List<T>): Boolean = items === other

    fun setItems(newItems: List<T>, index: (T) -> Int) {
        items = newItems
        colorIndex = IntArray(newItems.size) { index(newItems[it]) }
        xs = FloatArray(newItems.size)
        ys = FloatArray(newItems.size)
        projected = false
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (items.isEmpty()) return
        val width = canvas.width
        val height = canvas.height
        val margin = radiusPx + strokePaint.strokeWidth
        for (i in items.indices) {
            reusableGeoPoint.setCoords(latitude(items[i]), longitude(items[i]))
            projection.toPixels(reusableGeoPoint, reusablePoint)
            val x = reusablePoint.x.toFloat()
            val y = reusablePoint.y.toFloat()
            xs[i] = x
            ys[i] = y
            if (x < -margin || y < -margin || x > width + margin || y > height + margin) continue
            fillPaint.color = colorIndex[i].let { index ->
                if (index in colors.indices) colors[index] else fallbackColor
            }
            canvas.drawCircle(x, y, radiusPx, fillPaint)
            if (hasStroke) {
                strokePaint.color = strokeColor
                canvas.drawCircle(x, y, radiusPx, strokePaint)
            }
        }
        projected = true
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        if (!projected || items.isEmpty()) return false
        val index = TrackMap.nearestIndex(xs, ys, event.x, event.y, slopPx)
        if (index < 0) return false
        onSelect(items[index])
        return true
    }
}

/** Below the dots: a tap that hit nothing dismisses the info card. */
private class EmptyTapOverlay(private val onEmptyTap: () -> Unit) : Overlay() {
    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        onEmptyTap()
        return false
    }
}

/**
 * Marks that the user took the camera over: pans, flings, double-tap zooms
 * and pinches stop the automatic re-fit until «⌖ маршрут» is pressed.
 */
private class GestureWatcher(private val onGesture: () -> Unit) : Overlay() {
    override fun onScroll(
        first: MotionEvent?,
        second: MotionEvent?,
        distanceX: Float,
        distanceY: Float,
        mapView: MapView?,
    ): Boolean {
        onGesture()
        return false
    }

    override fun onFling(
        first: MotionEvent?,
        second: MotionEvent?,
        velocityX: Float,
        velocityY: Float,
        mapView: MapView?,
    ): Boolean {
        onGesture()
        return false
    }

    override fun onDoubleTap(event: MotionEvent?, mapView: MapView?): Boolean {
        onGesture()
        return false
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) onGesture() // pinch
        return false
    }
}
