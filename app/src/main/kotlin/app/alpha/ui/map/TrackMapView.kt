package app.alpha.ui.map

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
import app.alpha.ui.logic.GridCell
import app.alpha.ui.logic.MIN_CONFIDENT_POINTS
import app.alpha.ui.logic.MapBounds
import app.alpha.ui.logic.MapHotspot
import app.alpha.ui.logic.MapTrackPoint
import app.alpha.ui.logic.MapViewport
import app.alpha.ui.logic.PositionFix
import app.alpha.ui.logic.TileFilter
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.logic.TrackMetric
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay

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

    /** A cell of the accumulated map («все записи»). */
    data class Cell(val cell: GridCell, val cellMeters: Double) : MapTapInfo
}

/** Colors the map overlays take from the app theme (ARGB ints). */
data class MapLayerColors(
    val ramp: List<Int>,
    val metricMissing: Int,
    val hotspotFill: Int,
    val hotspotStroke: Int,
    /** «Я на карте»: data teal dot with a ring in the surface color. */
    val position: Int,
    val positionRing: Int,
)

/** Live tile counters for the honest status line (see `TileStatus`). */
data class TileStats(val loaded: Int, val failed: Int)

/**
 * osmdroid bridge: raster OSM tiles with the track drawn as a continuous line
 * colored by the measurement, hotspot markers on top, feature taps forwarded,
 * and the camera following the track bounds until the user pans.
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
    scale: TrackMap.RampScale?,
    /** Где след прерывается: индексы без отрезка к предыдущей точке. */
    lineBreaks: BooleanArray,
    hotspots: List<MapHotspot>,
    bounds: MapBounds?,
    /** Increment to re-enable auto-fit after the user panned away. */
    recenterTick: Int,
    onTap: (MapTapInfo?) -> Unit,
    onTileStats: (TileStats) -> Unit,
    modifier: Modifier = Modifier,
    /** Accumulated map: aggregated cells instead of individual fixes. */
    cells: List<GridCell> = emptyList(),
    cellMeters: Double = 0.0,
    cellScale: TrackMap.RampScale? = null,
    /** Own position; null = nothing to draw (no permission or no fix yet). */
    position: PositionFix? = null,
    /** A fix that stopped refreshing is drawn dimmed, never silently removed. */
    positionStale: Boolean = false,
    /** Increment to center the camera on [position] («⌖ я»). */
    centerOnPositionTick: Int = 0,
    /**
     * Точка, выбранная на графике профиля: карта и график показывают ОДИН
     * момент маршрута, поэтому курсор у них общий.
     */
    cursor: MapTrackPoint? = null,
    /** Visible box and zoom after every camera change (accumulated map query). */
    onViewport: (MapViewport) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapHolder(onTap = onTap, onTileStats = onTileStats) }
    holder.onTap = onTap
    holder.onTileStats = onTileStats
    holder.onViewport = onViewport

    AndroidView(
        modifier = modifier,
        factory = { context ->
            OsmSetup.ensureInitialized(context)
            MapView(context).also { holder.attach(it) }
        },
        update = {
            holder.applyTheme(dark, layerColors)
            holder.setData(points, metric, scale, lineBreaks, hotspots)
            holder.setCells(cells, cellMeters, cellScale)
            holder.setPosition(position, positionStale)
            holder.setCursor(cursor)
            holder.fitBounds(bounds, recenterTick)
            holder.centerOnPosition(centerOnPositionTick)
            // A mode switch does not move the camera, so nothing would fire the
            // scroll/zoom listener; report the current view once per update.
            // Equal viewports are dropped by Compose state equality.
            holder.requestViewport()
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
/** Ширина следа: линия читается на тайлах и остаётся целью для пальца. */
private const val ROUTE_WIDTH_DP = 4.5f
private const val FIT_PADDING_DP = 40f
private const val DEGENERATE_ZOOM = 16.0
private const val DEFAULT_ZOOM = 4.0

/** Курсор профиля на карте: кольцо, а не точка — под ним видно измерение. */
private const val CURSOR_RADIUS_DP = 9f

/** Own-position marker: dot and the surface-colored ring around it, dp. */
private const val POSITION_DOT_DP = 6f
private const val POSITION_RING_DP = 2.5f

/** «⌖ я» never leaves the user zoomed out to a country. */
private const val POSITION_MIN_ZOOM = 16.0

private const val ACCURACY_FILL_ALPHA = 36
private const val ACCURACY_STROKE_ALPHA = 96
private const val STALE_DOT_ALPHA = 110

/** Accumulated-map cells: translucent so the streets under them stay legible. */
private const val CELL_ALPHA = 190

/** Клетка с малым числом точек — та же краска, но заметно бледнее. */
private const val CELL_ALPHA_SPARSE = 80
private const val CELL_GAP_PX = 1f

private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

/** Camera reports are delayed until the pan/zoom settles. */
private const val VIEWPORT_REPORT_DELAY_MILLIS = 250L

/** Tile counters change per tile; the status line does not need every one. */
private const val TILE_STATS_THROTTLE_MILLIS = 250L

private class MapHolder(
    var onTap: (MapTapInfo?) -> Unit,
    var onTileStats: (TileStats) -> Unit,
) {
    var mapView: MapView? = null
    var onViewport: (MapViewport) -> Unit = {}
    private var pointsOverlay: DotOverlay<MapTrackPoint>? = null
    private var hotspotOverlay: DotOverlay<MapHotspot>? = null
    private var cellOverlay: CellOverlay? = null
    private var positionOverlay: PositionOverlay? = null
    private var cursorOverlay: CursorOverlay? = null
    private var appliedDark: Boolean? = null
    private var userGestured = false
    private var fittedBounds: MapBounds? = null
    private var fittedRecenterTick = -1
    private var centeredPositionTick = 0
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

        // Accumulated map: below the route so a single recording stays
        // readable on top of the heat map of everything recorded before.
        val cells = CellOverlay { cell, meters -> onTap(MapTapInfo.Cell(cell, meters)) }
        cellOverlay = cells
        mapView.overlays.add(cells)

        // Один слой на весь след: непрерывная линия, окрашенная по измерению,
        // и она же держит попадание пальца. Отдельной серой подложки под ней
        // больше нет — линия сама и есть маршрут, а не украшение поверх точек.
        val trackDots = DotOverlay<MapTrackPoint>(
            radiusPx = POINT_RADIUS_DP * density,
            slopPx = TAP_SLOP_DP * density,
            strokeWidthPx = 0f,
            lineWidthPx = ROUTE_WIDTH_DP * density,
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

        // Курсор профиля — над следом, но под «я на карте»: он показывает
        // момент маршрута, а не место человека, и путать их нельзя.
        val cursorRing = CursorOverlay(radiusPx = CURSOR_RADIUS_DP * density)
        cursorOverlay = cursorRing
        mapView.overlays.add(cursorRing)

        // Topmost: the user's own position is never hidden by data.
        val myPosition = PositionOverlay(
            dotRadiusPx = POSITION_DOT_DP * density,
            ringWidthPx = POSITION_RING_DP * density,
        )
        positionOverlay = myPosition
        mapView.overlays.add(myPosition)

        // Camera changes drive the accumulated-map query; osmdroid fires these
        // per scroll pixel, so the report is delayed into a settled camera.
        mapView.addMapListener(
            DelayedMapListener(
                object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        reportViewport()
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        reportViewport()
                        return false
                    }
                },
                VIEWPORT_REPORT_DELAY_MILLIS,
            ),
        )
        mapView.addOnFirstLayoutListener { _, _, _, _, _ -> reportViewport() }

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

    fun requestViewport() {
        reportViewport()
    }

    private fun reportViewport() {
        val mapView = mapView ?: return
        if (!mapView.isLayoutOccurred) return
        val box = mapView.boundingBox ?: return
        onViewport(
            MapViewport(
                bounds = MapBounds(
                    minLatitude = box.latSouth,
                    maxLatitude = box.latNorth,
                    minLongitude = box.lonWest,
                    maxLongitude = box.lonEast,
                ),
                zoom = mapView.zoomLevelDouble,
            ),
        )
    }

    fun applyTheme(dark: Boolean, colors: MapLayerColors) {
        val mapView = mapView ?: return
        pointsOverlay?.colors = colors.ramp.toIntArray()
        pointsOverlay?.fallbackColor = colors.metricMissing
        hotspotOverlay?.colors = intArrayOf(colors.hotspotFill)
        hotspotOverlay?.strokeColor = colors.hotspotStroke
        cellOverlay?.colors = colors.ramp.toIntArray()
        cellOverlay?.fallbackColor = colors.metricMissing
        cursorOverlay?.color = colors.position
        positionOverlay?.fillColor = colors.position
        positionOverlay?.ringColor = colors.positionRing
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
        scale: TrackMap.RampScale?,
        lineBreaks: BooleanArray,
        hotspots: List<MapHotspot>,
    ) {
        val mapView = mapView ?: return
        val dots = pointsOverlay ?: return
        if (!dots.sameItems(points) || dots.metric != metric || dots.scale != scale) {
            dots.metric = metric
            dots.scale = scale
            dots.breaks = lineBreaks
            dots.setItems(points) { point ->
                val value = TrackMap.metricValue(point, metric)
                if (value != null && scale != null) TrackMap.bucket(value, scale) else -1
            }
            mapView.invalidate()
        }
        val hotspotDots = hotspotOverlay ?: return
        if (!hotspotDots.sameItems(hotspots)) {
            hotspotDots.setItems(hotspots) { 0 }
            mapView.invalidate()
        }
    }

    fun setCells(
        cells: List<GridCell>,
        cellMeters: Double,
        scale: TrackMap.RampScale?,
    ) {
        val mapView = mapView ?: return
        val overlay = cellOverlay ?: return
        if (overlay.sameCells(cells) && overlay.scale == scale) return
        overlay.setCells(cells, cellMeters, scale)
        mapView.invalidate()
    }

    fun setCursor(point: MapTrackPoint?) {
        val mapView = mapView ?: return
        val overlay = cursorOverlay ?: return
        if (overlay.point == point) return
        overlay.point = point
        mapView.invalidate()
    }

    fun setPosition(position: PositionFix?, stale: Boolean) {
        val mapView = mapView ?: return
        val overlay = positionOverlay ?: return
        if (overlay.fix == position && overlay.stale == stale) return
        overlay.fix = position
        overlay.stale = stale
        mapView.invalidate()
    }

    /** «⌖ я»: center on the fix, keeping the zoom the user chose. */
    fun centerOnPosition(tick: Int) {
        if (tick == centeredPositionTick) return
        centeredPositionTick = tick
        val mapView = mapView ?: return
        val fix = positionOverlay?.fix ?: return
        // The user asked for this camera, so stop the track auto-fit fighting it.
        userGestured = true
        if (mapView.zoomLevelDouble < POSITION_MIN_ZOOM) {
            mapView.controller.setZoom(POSITION_MIN_ZOOM)
        }
        mapView.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
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
        cellOverlay = null
        positionOverlay = null
        cursorOverlay = null
    }
}

/**
 * Accumulated map layer: one filled square per aggregated cell, colored by the
 * cell's **median** on the same amber ramp as the track.
 *
 * Squares, not dots: a cell is an area statement («здесь измерено столько-то»),
 * and a dot would suggest a single measured point. Semi-transparent so the
 * street layout underneath stays readable — the map has to remain a map.
 */
private class CellOverlay(
    private val onSelect: (GridCell, Double) -> Unit,
) : Overlay() {

    var colors: IntArray = intArrayOf()
    var fallbackColor: Int = 0
    var scale: TrackMap.RampScale? = null
        private set

    private var cells: List<GridCell> = emptyList()
    private var cellMeters: Double = 0.0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val southWest = GeoPoint(0.0, 0.0)
    private val northEast = GeoPoint(0.0, 0.0)
    private val cornerA = Point()
    private val cornerB = Point()

    fun sameCells(other: List<GridCell>): Boolean = cells === other

    fun setCells(
        newCells: List<GridCell>,
        meters: Double,
        newScale: TrackMap.RampScale?,
    ) {
        cells = newCells
        cellMeters = meters
        scale = newScale
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (cells.isEmpty()) return
        val ramp = scale
        val width = canvas.width
        val height = canvas.height
        for (cell in cells) {
            southWest.setCoords(cell.southLatitude, cell.westLongitude)
            northEast.setCoords(cell.northLatitude, cell.eastLongitude)
            projection.toPixels(southWest, cornerA)
            projection.toPixels(northEast, cornerB)
            val left = minOf(cornerA.x, cornerB.x).toFloat()
            val right = maxOf(cornerA.x, cornerB.x).toFloat()
            val top = minOf(cornerA.y, cornerB.y).toFloat()
            val bottom = maxOf(cornerA.y, cornerB.y).toFloat()
            if (right < 0 || bottom < 0 || left > width || top > height) continue
            fillPaint.color = if (ramp != null) {
                colors.getOrElse(TrackMap.bucket(cell.median, ramp)) { fallbackColor }
            } else {
                fallbackColor
            }
            // Клетка из горстки фиксов не должна выглядеть так же уверенно,
            // как клетка из сотен: медиана по двум точкам — это одна из них.
            fillPaint.alpha = if (cell.count < MIN_CONFIDENT_POINTS) {
                CELL_ALPHA_SPARSE
            } else {
                CELL_ALPHA
            }
            // A hairline inset keeps neighbouring cells distinguishable.
            canvas.drawRect(left, top, right - CELL_GAP_PX, bottom - CELL_GAP_PX, fillPaint)
        }
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        if (cells.isEmpty()) return false
        val tapped = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
        val cell = cells.lastOrNull { it.contains(tapped.latitude, tapped.longitude) }
            ?: return false
        onSelect(cell, cellMeters)
        return true
    }
}

/**
 * Курсор профиля: где на маршруте стоит выбранный момент.
 *
 * Кольцо, а не залитая точка: под ним остаётся видно цвет самого следа, то
 * есть то самое измерение, о котором говорит карточка.
 */
private class CursorOverlay(private val radiusPx: Float) : Overlay() {

    var point: MapTrackPoint? = null
    var color: Int = 0

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val center = GeoPoint(0.0, 0.0)
    private val pixels = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val current = point ?: return
        center.setCoords(current.latitude, current.longitude)
        projection.toPixels(center, pixels)
        ringPaint.color = color
        ringPaint.strokeWidth = radiusPx / 3f
        canvas.drawCircle(pixels.x.toFloat(), pixels.y.toFloat(), radiusPx, ringPaint)
    }
}

/**
 * «Я на карте»: accuracy circle plus a heading-free dot.
 *
 * No heading arrow on purpose — the phone's compass in a pocket points at
 * nothing, and a wrong arrow is worse than none. The circle is the accuracy
 * the provider reports, so the marker never claims more precision than the fix
 * has; a fix that stopped refreshing is drawn faded, and the chip next to the
 * map says how old it is.
 */
private class PositionOverlay(
    private val dotRadiusPx: Float,
    private val ringWidthPx: Float,
) : Overlay() {

    var fix: PositionFix? = null
    var stale: Boolean = false
    var fillColor: Int = 0
    var ringColor: Int = 0

    private val circleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val center = GeoPoint(0.0, 0.0)
    private val north = GeoPoint(0.0, 0.0)
    private val centerPixels = Point()
    private val northPixels = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val current = fix ?: return
        center.setCoords(current.latitude, current.longitude)
        projection.toPixels(center, centerPixels)
        val x = centerPixels.x.toFloat()
        val y = centerPixels.y.toFloat()

        // Accuracy in pixels via a second projected point one accuracy-radius
        // north: independent of tile size, dpi scaling and zoom rounding.
        if (current.accuracyMeters > 0f) {
            north.setCoords(
                current.latitude + current.accuracyMeters / METERS_PER_DEGREE_LATITUDE,
                current.longitude,
            )
            projection.toPixels(north, northPixels)
            val radius = kotlin.math.abs(northPixels.y - centerPixels.y).toFloat()
            if (radius > dotRadiusPx) {
                circleFill.color = fillColor
                circleFill.alpha = if (stale) ACCURACY_FILL_ALPHA / 2 else ACCURACY_FILL_ALPHA
                canvas.drawCircle(x, y, radius, circleFill)
                circleStroke.color = fillColor
                circleStroke.alpha = if (stale) ACCURACY_STROKE_ALPHA / 2 else ACCURACY_STROKE_ALPHA
                circleStroke.strokeWidth = ringWidthPx / 2
                canvas.drawCircle(x, y, radius, circleStroke)
            }
        }

        ringPaint.color = ringColor
        ringPaint.alpha = 255
        ringPaint.strokeWidth = ringWidthPx
        canvas.drawCircle(x, y, dotRadiusPx + ringWidthPx / 2, ringPaint)
        dotPaint.color = fillColor
        dotPaint.alpha = if (stale) STALE_DOT_ALPHA else 255
        canvas.drawCircle(x, y, dotRadiusPx, dotPaint)
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
    /**
     * Ширина линии следа. Ноль — слой рисует только точки (метки превышений):
     * они не последовательность, соединять их нечем.
     */
    lineWidthPx: Float = 0f,
    private val latitude: (T) -> Double,
    private val longitude: (T) -> Double,
    private val onSelect: (T) -> Unit,
) : Overlay() {

    var colors: IntArray = intArrayOf()
    var fallbackColor: Int = 0
    var strokeColor: Int = 0
    var metric: TrackMetric? = null
    var scale: TrackMap.RampScale? = null

    /**
     * Разрывы следа: на этих индексах отрезок к предыдущей точке не рисуется,
     * потому что координат в этом промежутке не было. Точка, у которой нет
     * соседей ни с одной стороны, рисуется кружком — иначе одиночный фикс
     * после долгого пропуска исчез бы с карты совсем.
     */
    var breaks: BooleanArray = BooleanArray(0)

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
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hasStroke = strokeWidthPx > 0f
    private val hasLine = lineWidthPx > 0f
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
        val margin = radiusPx + strokePaint.strokeWidth + linePaint.strokeWidth
        // Сначала проекция всех точек: отрезок соединяет соседей, и рисовать
        // его можно только когда обе экранные координаты уже посчитаны.
        for (i in items.indices) {
            reusableGeoPoint.setCoords(latitude(items[i]), longitude(items[i]))
            projection.toPixels(reusableGeoPoint, reusablePoint)
            xs[i] = reusablePoint.x.toFloat()
            ys[i] = reusablePoint.y.toFloat()
        }
        for (i in items.indices) {
            val x = xs[i]
            val y = ys[i]
            val color = colorIndex[i].let { index ->
                if (index in colors.indices) colors[index] else fallbackColor
            }
            val broken = breaks.getOrElse(i) { true }
            val nextBroken = breaks.getOrElse(i + 1) { true }
            if (hasLine && !broken) {
                val previousX = xs[i - 1]
                val previousY = ys[i - 1]
                val visible = !(
                    (x < -margin && previousX < -margin) ||
                        (y < -margin && previousY < -margin) ||
                        (x > width + margin && previousX > width + margin) ||
                        (y > height + margin && previousY > height + margin)
                    )
                if (visible) {
                    // Цвет отрезка — по точке, В КОТОРУЮ он приходит: это
                    // измерение и есть то, что здесь намерено.
                    linePaint.color = color
                    canvas.drawLine(previousX, previousY, x, y, linePaint)
                }
            }
            // Кружком рисуется либо слой без линии (метки), либо точка, у
            // которой нет соседей: одиночный фикс обязан остаться видимым.
            val isolated = broken && nextBroken
            if (hasLine && !isolated) continue
            if (x < -margin || y < -margin || x > width + margin || y > height + margin) continue
            fillPaint.color = color
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
