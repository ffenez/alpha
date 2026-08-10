package app.radiacode.ui.map

import android.annotation.SuppressLint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.radiacode.ui.logic.MapBounds
import app.radiacode.ui.logic.TrackMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature

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

/** Colors the map layers take from the app theme (ARGB ints). */
data class MapLayerColors(
    val route: Int,
    val ramp: List<Int>,
    val metricMissing: Int,
    val hotspotFill: Int,
    val hotspotStroke: Int,
)

/**
 * MapLibre bridge: renders the prebuilt track/hotspot GeoJSON (pure logic in
 * [TrackMap]) as a line + two circle layers, forwards feature taps, follows
 * the track bounds until the user pans, and falls back to a local blank-ground
 * style when the style can't be downloaded (first launch offline).
 */
@Composable
fun TrackMapView(
    styleUrl: String,
    fallbackStyleJson: String,
    layerColors: MapLayerColors,
    trackGeoJson: String?,
    hotspotGeoJson: String?,
    bounds: MapBounds?,
    /** Increment to re-enable auto-fit after the user panned away. */
    recenterTick: Int,
    onTap: (MapTapInfo?) -> Unit,
    onTilesUnavailable: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val holder = remember {
        MapHolder(onTap = onTap, onTilesUnavailable = onTilesUnavailable)
    }
    holder.onTap = onTap
    holder.onTilesUnavailable = onTilesUnavailable

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapSetup.ensureInitialized(context)
            MapView(context).also { mapView ->
                mapView.onCreate(null)
                holder.attach(mapView)
            }
        },
        update = {
            holder.apply(styleUrl, fallbackStyleJson, layerColors)
            holder.setData(trackGeoJson, hotspotGeoJson)
            holder.fitBounds(bounds, recenterTick)
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = holder.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
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

private const val SOURCE_TRACK = "track-source"
private const val SOURCE_HOTSPOTS = "hotspot-source"
private const val LAYER_ROUTE = "track-route"
private const val LAYER_POINTS = "track-points"
private const val LAYER_HOTSPOTS = "track-hotspots"

/** Touch slop around a tap when hit-testing rendered features, px. */
private const val TAP_SLOP_PX = 24f

private class MapHolder(
    var onTap: (MapTapInfo?) -> Unit,
    var onTilesUnavailable: (Boolean) -> Unit,
) {
    var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var styleUrl: String? = null
    private var fallbackJson: String? = null
    private var colors: MapLayerColors? = null
    private var fallbackActive = false
    private var userGestured = false
    private var fittedBounds: MapBounds? = null
    private var fittedRecenterTick = -1
    private var pendingBounds: MapBounds? = null
    private var pendingTrack: String? = null
    private var pendingHotspots: String? = null
    private var destroyed = false

    fun attach(mapView: MapView) {
        this.mapView = mapView
        mapView.addOnDidFailLoadingMapListener {
            // The style could not be fetched (offline first launch): keep the
            // track visible on a local blank ground and tell the screen.
            if (style == null && !fallbackActive) {
                fallbackActive = true
                onTilesUnavailable(true)
                val json = fallbackJson ?: return@addOnDidFailLoadingMapListener
                map?.setStyle(Style.Builder().fromJson(json)) { loaded -> onStyleLoaded(loaded) }
            }
        }
        mapView.getMapAsync { loadedMap ->
            if (destroyed) return@getMapAsync
            map = loadedMap
            loadedMap.uiSettings.apply {
                // Attribution stays (OpenFreeMap/OSM requirement); the MapLibre
                // wordmark is optional and off to keep the card clean.
                isLogoEnabled = false
                isAttributionEnabled = true
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            loadedMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    userGestured = true
                }
            }
            loadedMap.addOnMapClickListener { latLng -> handleTap(latLng) }
            applyStyleIfNeeded()
            refitIfPending()
        }
    }

    fun apply(styleUrl: String, fallbackStyleJson: String, layerColors: MapLayerColors) {
        this.fallbackJson = fallbackStyleJson
        this.colors = layerColors
        if (this.styleUrl != styleUrl) {
            this.styleUrl = styleUrl
            style = null
            fallbackActive = false
            applyStyleIfNeeded()
        }
    }

    private fun applyStyleIfNeeded() {
        val map = map ?: return
        val url = styleUrl ?: return
        if (style != null) return
        map.setStyle(Style.Builder().fromUri(url)) { loaded ->
            fallbackActive = false
            onTilesUnavailable(false)
            onStyleLoaded(loaded)
        }
    }

    private fun onStyleLoaded(loaded: Style) {
        if (destroyed) return
        style = loaded
        val layerColors = colors ?: return
        if (loaded.getSource(SOURCE_TRACK) == null) {
            loaded.addSource(GeoJsonSource(SOURCE_TRACK))
            loaded.addSource(GeoJsonSource(SOURCE_HOTSPOTS))

            loaded.addLayer(
                LineLayer(LAYER_ROUTE, SOURCE_TRACK).withProperties(
                    PropertyFactory.lineColor(layerColors.route),
                    PropertyFactory.lineWidth(2f),
                    PropertyFactory.lineOpacity(0.55f),
                    PropertyFactory.lineJoin("round"),
                    PropertyFactory.lineCap("round"),
                ).withFilter(
                    Expression.eq(
                        Expression.get(TrackMap.PROP_KIND),
                        Expression.literal(TrackMap.KIND_ROUTE),
                    ),
                ),
            )
            loaded.addLayer(
                CircleLayer(LAYER_POINTS, SOURCE_TRACK).withProperties(
                    PropertyFactory.circleColor(rampExpression(layerColors)),
                    PropertyFactory.circleRadius(4f),
                    PropertyFactory.circleOpacity(0.9f),
                ).withFilter(
                    Expression.eq(
                        Expression.get(TrackMap.PROP_KIND),
                        Expression.literal(TrackMap.KIND_POINT),
                    ),
                ),
            )
            loaded.addLayer(
                CircleLayer(LAYER_HOTSPOTS, SOURCE_HOTSPOTS).withProperties(
                    PropertyFactory.circleColor(layerColors.hotspotFill),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor(layerColors.hotspotStroke),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
            )
        }
        pendingTrack?.let { setSource(SOURCE_TRACK, it) }
        pendingHotspots?.let { setSource(SOURCE_HOTSPOTS, it) }
    }

    /** Ramp bucket → amber step; missing metric (b = -1) → muted. */
    private fun rampExpression(layerColors: MapLayerColors): Expression =
        Expression.match(
            Expression.toNumber(Expression.get(TrackMap.PROP_BUCKET)),
            Expression.color(layerColors.metricMissing),
            *layerColors.ramp.mapIndexed { index, color ->
                Expression.stop(index, Expression.color(color))
            }.toTypedArray(),
        )

    fun setData(trackGeoJson: String?, hotspotGeoJson: String?) {
        if (trackGeoJson !== pendingTrack) {
            pendingTrack = trackGeoJson
            setSource(SOURCE_TRACK, trackGeoJson ?: EMPTY_COLLECTION)
        }
        if (hotspotGeoJson !== pendingHotspots) {
            pendingHotspots = hotspotGeoJson
            setSource(SOURCE_HOTSPOTS, hotspotGeoJson ?: EMPTY_COLLECTION)
        }
    }

    private fun setSource(id: String, geoJson: String) {
        val source = style?.getSourceAs<GeoJsonSource>(id) ?: return
        source.setGeoJson(geoJson)
    }

    fun fitBounds(bounds: MapBounds?, recenterTick: Int) {
        if (recenterTick != fittedRecenterTick) {
            fittedRecenterTick = recenterTick
            userGestured = false
            fittedBounds = null
        }
        pendingBounds = bounds
        refitIfPending()
    }

    private fun refitIfPending() {
        val map = map ?: return
        val bounds = pendingBounds ?: return
        if (userGestured || bounds == fittedBounds) return
        fittedBounds = bounds
        val density = mapView?.resources?.displayMetrics?.density ?: 1f
        val padding = (48 * density).toInt()
        val degenerate = bounds.maxLatitude - bounds.minLatitude < 1e-6 &&
            bounds.maxLongitude - bounds.minLongitude < 1e-6
        if (degenerate) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(bounds.minLatitude, bounds.minLongitude),
                    16.0,
                ),
            )
        } else {
            map.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.from(
                        bounds.maxLatitude,
                        bounds.maxLongitude,
                        bounds.minLatitude,
                        bounds.minLongitude,
                    ),
                    padding,
                ),
            )
        }
    }

    private fun handleTap(latLng: LatLng): Boolean {
        val map = map ?: return false
        val screen = map.projection.toScreenLocation(latLng)
        val box = RectF(
            screen.x - TAP_SLOP_PX,
            screen.y - TAP_SLOP_PX,
            screen.x + TAP_SLOP_PX,
            screen.y + TAP_SLOP_PX,
        )
        // Hotspots first: they sit above track points and are rarer.
        val hotspot = map.queryRenderedFeatures(box, LAYER_HOTSPOTS).firstOrNull()
        if (hotspot != null) {
            onTap(
                MapTapInfo.Hotspot(
                    id = hotspot.longProp(TrackMap.PROP_ID) ?: 0L,
                    timestamp = hotspot.longProp(TrackMap.PROP_TIME) ?: 0L,
                    doseMicroSvH = hotspot.floatProp(TrackMap.PROP_DOSE),
                    typicalMicroSvH = hotspot.floatProp(TrackMap.PROP_TYPICAL),
                ),
            )
            return true
        }
        val point = map.queryRenderedFeatures(box, LAYER_POINTS).firstOrNull()
        if (point != null) {
            onTap(
                MapTapInfo.TrackPoint(
                    timestamp = point.longProp(TrackMap.PROP_TIME) ?: 0L,
                    doseMicroSvH = point.floatProp(TrackMap.PROP_DOSE),
                    cps = point.floatProp(TrackMap.PROP_CPS),
                ),
            )
            return true
        }
        onTap(null)
        return false
    }

    @SuppressLint("Lifecycle") // onDestroy is forwarded from onDispose, not an Activity callback.
    fun destroy() {
        destroyed = true
        mapView?.onDestroy()
        mapView = null
        map = null
        style = null
    }

    companion object {
        private const val EMPTY_COLLECTION = """{"type":"FeatureCollection","features":[]}"""
    }
}

private fun Feature.floatProp(key: String): Float? =
    if (hasNonNullValueForProperty(key)) getNumberProperty(key).toFloat() else null

private fun Feature.longProp(key: String): Long? =
    if (hasNonNullValueForProperty(key)) getNumberProperty(key).toLong() else null
