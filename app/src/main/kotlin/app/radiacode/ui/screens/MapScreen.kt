package app.radiacode.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.radiacode.AppGraph
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.MapTrackScope
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.TrackPointEntity
import app.radiacode.device.DoseUnits
import app.radiacode.service.MeasurementService
import app.radiacode.service.ServiceStatus
import app.radiacode.ui.components.AppButton
import app.radiacode.data.export.SeriesExport
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.GridBin
import app.radiacode.ui.logic.GridCell
import app.radiacode.ui.logic.MIN_CONFIDENT_POINTS
import app.radiacode.ui.logic.GridStats
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.MapBounds
import app.radiacode.ui.logic.MapHotspot
import app.radiacode.ui.logic.MapTrackPoint
import app.radiacode.ui.logic.MapViewport
import app.radiacode.ui.logic.MyPosition
import app.radiacode.ui.logic.PositionFix
import app.radiacode.ui.logic.PositionState
import app.radiacode.ui.logic.TileStatus
import app.radiacode.ui.logic.TrackGrid
import app.radiacode.ui.logic.TrackMap
import app.radiacode.ui.logic.TrackMetric
import app.radiacode.ui.map.MapLayerColors
import app.radiacode.ui.map.MapTapInfo
import app.radiacode.ui.map.TileStats
import app.radiacode.ui.map.TrackMapView
import app.radiacode.ui.map.anyLocationProviderEnabled
import app.radiacode.ui.map.rememberMyPosition
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.MapCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.DoseRampColors
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Overlay rebuild cadence while recording (1 Hz points come in faster). */
private const val LIVE_THROTTLE_MILLIS = 5_000L
private const val HOTSPOT_EVENT_LIMIT = 500

/** Camera settle before the accumulated map is re-queried. */
private const val GRID_DEBOUNCE_MILLIS = 250L

/** Everything loaded from Room for the displayed track. */
@Immutable
private data class TrackData(
    val trackSessionId: Long?,
    val startedAt: Long?,
    val endedAt: Long?,
    val points: List<MapTrackPoint>,
    val hotspots: List<MapHotspot>,
) {
    val hasTrack: Boolean get() = trackSessionId != null

    companion object {
        val EMPTY = TrackData(null, null, null, emptyList(), emptyList())
    }
}

/** Derived rendering artifacts; recomputed on data or metric change. */
@Immutable
private data class RenderModel(
    /** Downsampled for rendering; every number below is from the full list. */
    val renderedPoints: List<MapTrackPoint>,
    val thresholds: TrackMap.RampThresholds?,
    val bounds: MapBounds?,
    /** min/max of the selected metric over the full track. */
    val range: Pair<Float, Float>?,
    val summary: TrackMap.Summary,
    val distanceMeters: Double,
)

/**
 * «Все записи»: the accumulated map of one viewport. [stats] and the cells come
 * from the grid histogram; [pointCount] and [maxValue] are the exact aggregate
 * over the full matching set, so the summary never describes only what fitted
 * on screen.
 */
@Immutable
private data class GridData(
    val cells: List<GridCell>,
    val thresholds: TrackMap.RampThresholds?,
    val cellMeters: Double,
    /** min/max of the cell medians — what the ramp legend labels. */
    val range: Pair<Float, Float>?,
    val stats: GridStats?,
    val pointCount: Int,
    val maxValue: Float?,
    val firstTime: Long?,
    val lastTime: Long?,
    val hotspots: List<MapHotspot>,
) {
    val isEmpty: Boolean get() = pointCount == 0

    /** True when the row cap truncated the histogram (see TrackGrid). */
    val partial: Boolean get() = stats != null && stats.count < pointCount

    companion object {
        val EMPTY = GridData(
            emptyList(), null, 0.0, null, null, 0, null, null, null, emptyList(),
        )
    }
}

/**
 * Карта (SPEC «Map», roadmap 6): full-bleed map with the GPS track colored by
 * dose rate (CPS by toggle) on the amber ramp, hotspot markers from events,
 * tap cards with raw values, and start/stop of track recording through
 * [MeasurementService].
 *
 * Two scopes ([MapTrackScope]): «эта запись» shows the recording in progress
 * (or the newest finished one), «все записи» aggregates every fix ever
 * recorded into a grid heat map — the accumulated radiation map of everywhere
 * the user has measured.
 *
 * Location on this screen is foreground-scoped by scientific instruction §3.3:
 * the own-position marker subscribes in [rememberMyPosition] on resume and
 * releases on pause. Track recording is the only other consumer and keeps its
 * own subscription in the service, alive exactly between Старт and Стоп.
 */
@OptIn(FlowPreview::class)
@Composable
fun MapScreen(graph: AppGraph) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    val uiScope = rememberCoroutineScope()
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val recording by graph.serviceStatus.trackRecording.collectAsState()
    // Почему в следе ещё нет точек: ждём спутников, нет разрешения или
    // определение места выключено в системе.
    val trackLocation by graph.serviceStatus.trackLocation.collectAsState()

    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    val metric = if (metricIndex == 0) TrackMetric.DOSE else TrackMetric.CPS

    // Which scope to draw: the stored choice, or the default for what exists.
    // A running recording overrides it in memory only — the user's stored
    // preference is never rewritten behind their back.
    val storedScope by graph.settings.mapTrackScope.collectAsState(initial = null)
    var hasRecordings by remember { mutableStateOf(false) }
    var justStopped by remember { mutableStateOf(false) }
    var sessionScope by remember { mutableStateOf<MapTrackScope?>(null) }
    val scope = sessionScope ?: MapTrackScope.resolve(storedScope, hasRecordings)
    val setScope: (MapTrackScope) -> Unit = { chosen ->
        justStopped = false
        sessionScope = chosen
        uiScope.launch { graph.settings.setMapTrackScope(chosen) }
    }

    var data by remember { mutableStateOf<TrackData?>(null) }
    LaunchedEffect(recording?.sessionId) {
        val active = recording
        // A recording is what the user wants to watch: show it, and keep it on
        // screen after Стоп instead of falling back to an empty state.
        if (active != null) {
            justStopped = false
            sessionScope = MapTrackScope.CURRENT
        }
        val session = if (active != null) {
            graph.trackRepository.session(active.sessionId)
        } else {
            graph.trackRepository.latestSession()
        }
        hasRecordings = session != null
        if (session == null) {
            data = TrackData.EMPTY
            return@LaunchedEffect
        }
        val live = active != null
        val pointsFlow = graph.trackRepository.points(session.id)
        val throttled = if (live) {
            // First emission immediately, then at most one rebuild per 5 s.
            merge(pointsFlow.take(1), pointsFlow.sample(LIVE_THROTTLE_MILLIS))
        } else {
            pointsFlow
        }
        combine(
            throttled,
            graph.measurementRepository.recentEvents(HOTSPOT_EVENT_LIMIT),
        ) { points, events ->
            TrackData(
                trackSessionId = session.id,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                points = points.map { it.toMapPoint() },
                hotspots = events.mapNotNull {
                    it.toHotspot(session.startedAt, session.endedAt)
                },
            )
        }.collect { data = it }
    }

    // Recording stopped: the track stays on screen and the map offers the
    // accumulated view, which is where that recording now lives too.
    var wasRecording by remember { mutableStateOf(recording != null) }
    LaunchedEffect(recording) {
        if (wasRecording && recording == null) justStopped = true
        wasRecording = recording != null
    }

    // Wall clock for the recording-duration chip and the age of the fix: 1 Hz
    // while recording (the duration is read as a running number), lazy otherwise.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(if (recording != null) 1_000 else 10_000)
        }
    }

    // Location permission and provider state, re-checked on resume (the user
    // may change both in system settings).
    var hasLocation by remember { mutableStateOf(hasAnyLocation(context)) }
    var providersEnabled by remember { mutableStateOf(anyLocationProviderEnabled(context)) }
    var gpsEnabled by remember { mutableStateOf(isGpsEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocation = hasAnyLocation(context)
                gpsEnabled = isGpsEnabled(context)
                providersEnabled = anyLocationProviderEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Человек мог выбрать «Приблизительно» — это тоже разрешение, и запись
        // с ним начинается: грубый след честнее отсутствующего.
        hasLocation = results.values.any { it } || hasAnyLocation(context)
        if (hasLocation) startTrackRecording(context)
    }

    // §3.3: subscribed only while this screen is resumed, released on leave.
    val fix = rememberMyPosition(hasPermission = hasLocation)
    val positionState = MyPosition.state(hasLocation, providersEnabled, fix)

    // «Все записи»: one query per settled camera, on IO, bounded by the grid.
    var viewport by remember { mutableStateOf<MapViewport?>(null) }
    var grid by remember { mutableStateOf<GridData?>(null) }
    var gridTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording, scope) {
        // Points arrive while recording; refresh the accumulated map slowly.
        while (recording != null && scope == MapTrackScope.ALL) {
            delay(LIVE_THROTTLE_MILLIS)
            gridTick++
        }
    }
    LaunchedEffect(scope, metric, viewport, gridTick) {
        if (scope != MapTrackScope.ALL) return@LaunchedEffect
        val current = viewport ?: return@LaunchedEffect
        delay(GRID_DEBOUNCE_MILLIS)
        grid = withContext(Dispatchers.IO) { loadGrid(graph, current, metric) }
    }

    // First camera of the accumulated map: everywhere the user ever recorded.
    var allBounds by remember { mutableStateOf<MapBounds?>(null) }
    LaunchedEffect(scope, hasRecordings) {
        if (scope == MapTrackScope.ALL && allBounds == null) {
            allBounds = withContext(Dispatchers.IO) {
                graph.trackRepository.allPointsBounds()?.let { row ->
                    MapBounds(
                        minLatitude = row.minLatitude ?: return@let null,
                        maxLatitude = row.maxLatitude ?: return@let null,
                        minLongitude = row.minLongitude ?: return@let null,
                        maxLongitude = row.maxLongitude ?: return@let null,
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            if (hasRecordings) {
                Segmented(
                    options = listOf(t.scopeCurrent, t.scopeAll),
                    selectedIndex = if (scope == MapTrackScope.ALL) 1 else 0,
                    onSelect = {
                        setScope(if (it == 1) MapTrackScope.ALL else MapTrackScope.CURRENT)
                    },
                    modifier = Modifier.width(210.dp),
                )
            } else {
                Chip(text = t.mapTitle, color = colors.ink)
            }
            Spacer(Modifier.weight(1f))
            if (!gpsEnabled) {
                Chip(
                    text = t.gpsOff,
                    color = colors.warn,
                    dot = colors.warn,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                            )
                        }
                    },
                )
            }
            val d = data
            if (
                scope == MapTrackScope.CURRENT && recording == null &&
                d != null && d.hasTrack && d.startedAt != null
            ) {
                Chip(text = t.lastRecording(HistoryFormat.dayTime(d.startedAt, nowMillis, s = h)))
            }
        }

        val d = data
        TrackMapCard(
            graph = graph,
            data = if (scope == MapTrackScope.CURRENT) d else null,
            grid = if (scope == MapTrackScope.ALL) grid else null,
            initialBounds = if (scope == MapTrackScope.ALL) allBounds else null,
            metric = metric,
            metricIndex = metricIndex,
            onMetricSelect = { metricIndex = it },
            unit = unit,
            position = fix,
            positionState = positionState,
            trackWaiting = recording != null &&
                trackLocation == ServiceStatus.TrackLocation.WAITING,
            nowMillis = nowMillis,
            onViewport = { viewport = it },
            emptyState = {
                val nothingDrawn = if (scope == MapTrackScope.ALL) {
                    grid?.isEmpty == true
                } else {
                    d != null && d.points.isEmpty()
                }
                if (nothingDrawn) {
                    MapEmptyState(
                        scope = scope,
                        recording = recording != null,
                        hasRecordings = hasRecordings,
                        location = trackLocation,
                    )
                }
            },
            recordingChips = {
                val active = recording
                if (active != null) {
                    Chip(
                        text = t.recordingFor(
                            HistoryFormat.duration((nowMillis - active.startedAt) / 1000, s = h),
                        ),
                        color = colors.ok,
                        dot = colors.ok,
                    )
                    // Сколько точек уже записано. Без этого числа «идёт 12 мин»
                    // одинаково выглядит и когда след пишется, и когда система
                    // не даёт ни одной координаты.
                    val written = d?.points?.size ?: 0
                    Chip(
                        text = t.recordedPoints(HistoryFormat.count(written)),
                        color = if (written > 0) colors.ink2 else colors.warn,
                    )
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        when {
            scope == MapTrackScope.ALL -> grid?.takeIf { !it.isEmpty }?.let {
                AreaSummaryCard(grid = it, metric = metric, unit = unit, nowMillis = nowMillis)
            }
            d != null && d.hasTrack && d.points.isNotEmpty() -> RouteSummaryCard(
                data = d,
                unit = unit,
                title = if (recording != null) t.routeMine else t.route,
            )
        }

        if (justStopped && scope == MapTrackScope.CURRENT) {
            AppButton(
                text = t.showAllRecordings,
                onClick = { setScope(MapTrackScope.ALL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when {
            !hasLocation -> LocationPermissionCard(
                onRequest = { permissionLauncher.launch(arrayOf(FINE_LOCATION, COARSE_LOCATION)) },
            )
            recording != null -> AppButton(
                text = t.stopRecording,
                onClick = { stopTrackRecording(context) },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> AppButton(
                text = t.startRecording,
                onClick = { startTrackRecording(context) },
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Read-only map of a past session's track, opened from История. Merges every
 * track session overlapping the measurement session (usually one) plus the
 * hotspots of that period.
 */
@Composable
fun SessionTrackMapScreen(graph: AppGraph, sessionId: Long, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    val metric = if (metricIndex == 0) TrackMetric.DOSE else TrackMetric.CPS

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingGpx by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val gpxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val content = pendingGpx
        pendingGpx = null
        if (uri != null && content != null) {
            scope.launch {
                notice = if (writeTextToUri(context, uri, content)) t.exportSaved else t.exportFailed
            }
        }
    }

    var data by remember { mutableStateOf<TrackData?>(null) }
    LaunchedEffect(sessionId) {
        val summary = graph.sessionRepository.summary(sessionId)
        if (summary == null) {
            data = TrackData.EMPTY
            return@LaunchedEffect
        }
        val to = summary.endedAt ?: System.currentTimeMillis()
        val trackSessions = graph.trackRepository.sessionsOverlapping(summary.startedAt, to)
        val points = trackSessions
            .flatMap { graph.trackRepository.points(it.id).first() }
            .sortedBy { it.timestamp }
            .map { it.toMapPoint() }
        val hotspots = graph.sessionRepository
            .deviationEvents(summary.startedAt, to, limit = HOTSPOT_EVENT_LIMIT)
            .mapNotNull { it.toHotspot(summary.startedAt, to) }
        data = TrackData(
            trackSessionId = trackSessions.firstOrNull()?.id,
            startedAt = summary.startedAt,
            endedAt = to,
            points = points,
            hotspots = hotspots,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = t.back, onClick = onBack)
            Spacer(Modifier.weight(1f))
            // Трек в GPX: стандарт, который открывают карты и GIS. Сохранение —
            // явное действие через системный диалог; ничего не уходит само.
            data?.trackSessionId?.let { trackId ->
                Chip(
                    text = t.exportGpx,
                    color = colors.dataText,
                    onClick = {
                        scope.launch {
                            val points = graph.trackRepository.points(trackId).first()
                            pendingGpx = SeriesExport.gpx(points, t.sessionTrack)
                            gpxLauncher.launch(
                                SeriesExport.fileName(
                                    points.firstOrNull()?.timestamp
                                        ?: System.currentTimeMillis(),
                                    "gpx",
                                ),
                            )
                        }
                    },
                )
                Spacer(Modifier.width(Dimens.space2))
            }
            Chip(text = t.sessionTrack, color = colors.ink)
        }
        notice?.let { Text(text = it, style = type.footnote, color = colors.muted) }

        val d = data
        if (d != null && !d.hasTrack) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t.noTrackInSession,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
        } else {
            TrackMapCard(
                graph = graph,
                data = d,
                grid = null,
                initialBounds = null,
                metric = metric,
                metricIndex = metricIndex,
                onMetricSelect = { metricIndex = it },
                unit = unit,
                position = null,
                positionState = PositionState.NO_PERMISSION,
                trackWaiting = false,
                nowMillis = System.currentTimeMillis(),
                onViewport = {},
                emptyState = {},
                recordingChips = {},
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (d != null && d.points.isNotEmpty()) {
                RouteSummaryCard(data = d, unit = unit, title = t.route)
            }
        }
    }
}

// --- map card with overlays ---

@Composable
private fun TrackMapCard(
    graph: AppGraph,
    data: TrackData?,
    grid: GridData?,
    initialBounds: MapBounds?,
    metric: TrackMetric,
    metricIndex: Int,
    onMetricSelect: (Int) -> Unit,
    unit: DoseUnitSetting,
    position: PositionFix?,
    positionState: PositionState,
    /** Карточка следа уже говорит «жду первые точки» — чип тогда молчит. */
    trackWaiting: Boolean,
    nowMillis: Long,
    onViewport: (MapViewport) -> Unit,
    emptyState: @Composable () -> Unit,
    recordingChips: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)

    val render = remember(data, metric) {
        val points = data?.points.orEmpty()
        val metricValues = points.mapNotNull { TrackMap.metricValue(it, metric) }
        val thresholds = TrackMap.rampThresholds(metricValues)
        RenderModel(
            renderedPoints = TrackMap.downsample(points),
            thresholds = thresholds,
            bounds = TrackMap.bounds(points),
            range = TrackMap.valueRange(points, metric),
            summary = TrackMap.summary(points),
            distanceMeters = TrackMap.distanceMeters(points),
        )
    }

    var tap by remember { mutableStateOf<MapTapInfo?>(null) }
    var recenterTick by remember { mutableIntStateOf(0) }
    var followTick by remember { mutableIntStateOf(0) }
    // Tile diagnostics: we are blind in the field, so the screen says whether
    // tiles actually arrive and, if none ever do, names the likely cause.
    var tiles by remember { mutableStateOf(TileStats(0, 0)) }
    var mapShownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var waitedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mapShownAt, tiles.loaded) {
        while (tiles.loaded == 0) {
            waitedMillis = System.currentTimeMillis() - mapShownAt
            delay(1_000)
        }
    }

    val layerColors = MapLayerColors(
        route = colors.ink2.toArgb(),
        ramp = DoseRampColors.map { it.toArgb() },
        metricMissing = colors.muted.toArgb(),
        hotspotFill = colors.crit.toArgb(),
        hotspotStroke = colors.surface.toArgb(),
        position = colors.data.toArgb(),
        positionRing = colors.surface.toArgb(),
    )
    val fitBounds = if (grid != null) initialBounds else render.bounds

    Card(modifier = modifier, contentPadding = 0.dp) {
        TrackMapView(
            dark = colors.isDark,
            layerColors = layerColors,
            points = if (grid != null) emptyList() else render.renderedPoints,
            metric = metric,
            thresholds = render.thresholds,
            hotspots = grid?.hotspots ?: data?.hotspots.orEmpty(),
            bounds = fitBounds,
            recenterTick = recenterTick,
            onTap = { tap = it },
            onTileStats = { tiles = it },
            modifier = Modifier.fillMaxSize(),
            cells = grid?.cells.orEmpty(),
            cellMeters = grid?.cellMeters ?: 0.0,
            cellThresholds = grid?.thresholds,
            position = position.takeIf { MyPosition.markerVisible(positionState, position) },
            positionStale = position != null && MyPosition.isStale(position, nowMillis),
            centerOnPositionTick = followTick,
            onViewport = onViewport,
        )

        // Top-left: recording state, coverage, position and tile honesty.
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.TopStart).padding(Dimens.space2),
        ) {
            recordingChips()
            if (grid == null && render.distanceMeters > 0) {
                Chip(text = TrackMap.formatDistance(render.distanceMeters, t))
            }
            if (grid != null && !grid.isEmpty) {
                Chip(
                    text = t.pointsAndCells(
                        HistoryFormat.count(grid.pointCount),
                        HistoryFormat.count(grid.cells.size),
                    ),
                )
            }
            MyPosition.chipText(
                state = positionState,
                fix = position,
                nowMillis = nowMillis,
                s = t,
                trackWaiting = trackWaiting,
            )?.let { text ->
                Chip(
                    text = text,
                    color = if (positionState == PositionState.WAITING_FIX) {
                        colors.ink2
                    } else {
                        colors.dataText
                    },
                    dot = if (positionState == PositionState.FIXED) colors.data else null,
                )
            }
            // Honest tile state, always visible: «загружаются…» while the
            // first tiles are on their way, a count once they arrive, and a
            // named cause when nothing ever comes.
            val hint = TileStatus.networkHint(tiles.loaded, tiles.failed, waitedMillis, t)
            Chip(
                text = TileStatus.line(tiles.loaded, tiles.failed, waitedMillis, t),
                color = if (hint != null) colors.warn else colors.ink2,
                dot = if (hint != null) colors.warn else null,
            )
            if (hint != null) {
                Card(
                    background = colors.surface,
                    modifier = Modifier.widthIn(max = 260.dp),
                ) {
                    Text(text = hint, style = type.footnote, color = colors.warn)
                }
            }
        }

        // Top-right: back to the track, or to the user.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.space2),
        ) {
            if (MyPosition.markerVisible(positionState, position)) {
                Chip(text = t.centerOnMe, onClick = { followTick++ })
            }
            if (grid == null && render.bounds != null) {
                Chip(text = t.centerOnRoute, onClick = { recenterTick++ })
            }
            if (grid != null && initialBounds != null) {
                Chip(text = t.centerOnAll, onClick = { recenterTick++ })
            }
        }

        // Bottom-right: metric toggle over the ramp legend.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.space2),
        ) {
            Segmented(
                options = listOf(t.metricDose, t.metricCps),
                selectedIndex = metricIndex,
                onSelect = onMetricSelect,
                modifier = Modifier.width(150.dp),
            )
            val legendRange = if (grid != null) grid.range else render.range
            legendRange?.let { (min, max) ->
                LegendBar(
                    minLabel = legendLabel(min, metric, unit),
                    maxLabel = legendLabel(max, metric, unit),
                    // A cell is an area statement, so the legend says what one
                    // cell means before its colors mean anything.
                    caption = grid?.let {
                        val sparse = it.cells.count { cell -> cell.count < MIN_CONFIDENT_POINTS }
                        listOfNotNull(
                            t.cellSize(TrackGrid.formatCellSize(it.cellMeters, t)),
                            t.median,
                            if (sparse > 0) t.paleCells(sparse, MIN_CONFIDENT_POINTS) else null,
                        ).joinToString(" · ")
                    },
                )
            }
        }

        // Empty state teaches the first action (design language).
        emptyState()

        tap?.let { info ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = Dimens.space2, end = Dimens.space2, bottom = 44.dp),
            ) {
                when (info) {
                    is MapTapInfo.TrackPoint -> TrackPointCard(info, unit)
                    is MapTapInfo.Hotspot -> HotspotCard(graph, info, unit)
                    is MapTapInfo.Cell -> CellCard(info, metric, unit)
                }
            }
        }
    }
}

/** Ramp swatches with the honest min/max of what is drawn. */
@Composable
private fun LegendBar(minLabel: String, maxLabel: String, caption: String? = null) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
            .background(colors.surface)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = minLabel, style = type.axis, color = colors.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DoseRampColors.forEach { step ->
                    Box(
                        Modifier
                            .size(width = 14.dp, height = 8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(step),
                    )
                }
            }
            Text(text = maxLabel, style = type.axis, color = colors.ink2)
        }
        caption?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
    }
}

private fun legendLabel(value: Float, metric: TrackMetric, unit: DoseUnitSetting): String =
    when (metric) {
        TrackMetric.DOSE -> DoseFormat.rate(value, unit)
        TrackMetric.CPS -> TrackMap.formatCps(value)
    }

// --- tap cards ---

@Composable
private fun TrackPointCard(info: MapTapInfo.TrackPoint, unit: DoseUnitSetting) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Text(
                text = t.trackPoint.uppercase(),
                style = type.overline,
                color = colors.muted,
            )
            Text(
                text = listOfNotNull(
                    info.doseMicroSvH?.let { DoseFormat.rateWithUnit(it, unit, s = strings) },
                    info.cps?.let { TrackMap.formatCps(it) + " CPS" },
                    HistoryFormat.dayTime(info.timestamp, System.currentTimeMillis(), s = h),
                ).joinToString(" · "),
                style = type.value,
                color = colors.ink,
            )
        }
    }
}

/** Tap on an aggregated cell: what was measured there, robustly. */
@Composable
private fun CellCard(info: MapTapInfo.Cell, metric: TrackMetric, unit: DoseUnitSetting) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MapCatalogue.of(strings.language)
    val cell = info.cell
    val now = System.currentTimeMillis()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Text(
                text = t.cellSize(TrackGrid.formatCellSize(info.cellMeters, t)).uppercase(),
                style = type.overline,
                color = colors.muted,
            )
            Text(
                text = t.medianValue(metricWithUnit(strings, cell.median, metric, unit)),
                style = type.value,
                color = colors.ink,
            )
            Text(
                text = t.cellSpread(
                    p10 = legendLabel(cell.p10, metric, unit),
                    p90 = legendLabel(cell.p90, metric, unit),
                    min = legendLabel(cell.minValue, metric, unit),
                    max = legendLabel(cell.maxValue, metric, unit),
                ),
                style = type.footnote,
                color = colors.ink2,
            )
            Text(
                text = t.cellCoverage(
                    points = HistoryFormat.count(cell.count),
                    from = HistoryFormat.dayTime(cell.fromMillis, now, s = h),
                    to = HistoryFormat.dayTime(cell.toMillis, now, s = h),
                ),
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

/** Loaded lazily for the hotspot card: CPS at the moment and dwell time. */
@Immutable
private data class HotspotExtras(val cps: Float?, val dwellSeconds: Long?)

@Composable
private fun HotspotCard(graph: AppGraph, info: MapTapInfo.Hotspot, unit: DoseUnitSetting) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)

    var extras by remember(info) { mutableStateOf<HotspotExtras?>(null) }
    LaunchedEffect(info) {
        val samples = graph.measurementRepository
            .samples(info.timestamp - 2_000, info.timestamp + DWELL_WINDOW_MILLIS)
            .first()
        val nearest = samples
            .filter { kotlin.math.abs(it.timestamp - info.timestamp) <= 2_000 }
            .minByOrNull { kotlin.math.abs(it.timestamp - info.timestamp) }
        val dwell = info.doseMicroSvH?.let { eventDose ->
            TrackMap.dwellSeconds(
                samples = samples.map {
                    it.timestamp to DoseUnits.rawToMicroSievertPerHour(it.doseRate)
                },
                eventTimestamp = info.timestamp,
                eventDoseMicroSvH = eventDose,
            )
        }
        extras = HotspotExtras(cps = nearest?.countRate, dwellSeconds = dwell)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Text(
                text = t.excursionPoint.uppercase(),
                style = type.overline,
                color = colors.crit,
            )
            Text(
                text = listOfNotNull(
                    info.doseMicroSvH?.let { DoseFormat.rateWithUnit(it, unit, s = strings) },
                    extras?.cps?.let { TrackMap.formatCps(it) + " CPS" },
                    HistoryFormat.dayTime(info.timestamp, System.currentTimeMillis(), s = h),
                ).joinToString(" · "),
                style = type.value,
                color = colors.ink,
            )
            info.typicalMicroSvH?.let {
                Text(
                    text = t.usuallyHere(DoseFormat.rateWithUnit(it, unit, s = strings)),
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
            extras?.dwellSeconds?.let { dwell ->
                Text(
                    text = t.steadyReadings(HistoryFormat.duration(dwell, s = h)),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

private const val DWELL_WINDOW_MILLIS = 10L * 60_000

// --- summary and states ---

@Composable
private fun RouteSummaryCard(data: TrackData, unit: DoseUnitSetting, title: String) {
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    val summary = remember(data) { TrackMap.summary(data.points) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = DoseFormat.rateUnitLabel(unit, s = strings),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        summary.avgDoseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                        t.statAvg,
                    ),
                    StatCell(
                        summary.maxDoseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                        t.statMax,
                    ),
                    StatCell(HistoryFormat.count(summary.pointCount), t.statPoints),
                    StatCell(HistoryFormat.count(data.hotspots.size), t.statMarkers),
                ),
            )
        }
    }
}

/**
 * «Все записи» summary. Точек, макс and the period are the exact aggregate of
 * every fix in view; the median comes from the same histogram that colors the
 * cells — never from the subset that happened to be drawn.
 */
@Composable
private fun AreaSummaryCard(
    grid: GridData,
    metric: TrackMetric,
    unit: DoseUnitSetting,
    nowMillis: Long,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val strings = LocalStrings.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t.inThisView.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (metric == TrackMetric.DOSE) {
                        DoseFormat.rateUnitLabel(unit, s = strings)
                    } else {
                        "CPS"
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        grid.stats?.let { legendLabel(it.median, metric, unit) } ?: "—",
                        t.statMedian,
                    ),
                    StatCell(
                        grid.maxValue?.let { legendLabel(it, metric, unit) } ?: "—",
                        t.statMax,
                    ),
                    StatCell(HistoryFormat.count(grid.pointCount), t.statPoints),
                    StatCell(HistoryFormat.count(grid.cells.size), t.statCells),
                ),
            )
            val period = if (grid.firstTime != null && grid.lastTime != null) {
                t.recordedFromTo(
                    from = HistoryFormat.dayTime(grid.firstTime, nowMillis, s = h),
                    to = HistoryFormat.dayTime(grid.lastTime, nowMillis, s = h),
                )
            } else {
                null
            }
            Text(
                text = listOfNotNull(
                    period,
                    t.onlyAccurateFixes(TrackGrid.MAX_ACCURACY_METERS.toInt()),
                    if (grid.partial) {
                        t.builtFromPoints(HistoryFormat.count(grid.stats?.count ?: 0))
                    } else {
                        null
                    },
                ).joinToString(" · "),
                style = type.footnote,
                color = if (grid.partial) colors.warn else colors.muted,
            )
        }
    }
}

/**
 * Empty states of the map. They exist to answer one field question — «строится
 * ли карта следа?» — plainly: nothing is written unless a recording is on.
 */
@Composable
private fun MapEmptyState(
    scope: MapTrackScope,
    recording: Boolean,
    hasRecordings: Boolean,
    location: ServiceStatus.TrackLocation = ServiceStatus.TrackLocation.WAITING,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    val title: String
    val body: String
    when {
        recording && location == ServiceStatus.TrackLocation.NO_PERMISSION -> {
            title = t.emptyNoPermissionTitle
            body = t.emptyNoPermissionBody
        }
        recording && location == ServiceStatus.TrackLocation.NO_PROVIDER -> {
            title = t.emptyNoProviderTitle
            body = t.emptyNoProviderBody
        }
        recording -> {
            title = t.emptyWaitingTitle
            body = t.emptyWaitingBody
        }
        scope == MapTrackScope.ALL -> {
            title = t.emptyAreaTitle
            body = t.emptyAreaBody
        }
        hasRecordings -> {
            title = t.emptyTrackTitle
            body = t.emptyTrackBody
        }
        else -> {
            title = t.emptyNoTracksTitle
            body = t.emptyNoTracksBody
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            background = colors.surface,
            modifier = Modifier.padding(Dimens.space4).widthIn(max = 320.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                Text(text = title, style = type.label, color = colors.ink)
                Text(text = body, style = type.bodySmall, color = colors.ink2)
            }
        }
    }
}

@Composable
private fun LocationPermissionCard(onRequest: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = t.locationTitle,
                style = type.label,
                color = colors.ink,
            )
            Text(
                text = t.locationBody,
                style = type.bodySmall,
                color = colors.ink2,
            )
            AppButton(
                text = t.locationAllow,
                onClick = onRequest,
                primary = true,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// --- plumbing ---

private const val FINE_LOCATION = android.Manifest.permission.ACCESS_FINE_LOCATION
private const val COARSE_LOCATION = android.Manifest.permission.ACCESS_COARSE_LOCATION

private fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Любое разрешение на место — точное или приблизительное.
 *
 * Системный диалог с Android 12 предлагает выбор, и «Приблизительно» это тоже
 * ДА: с ним след пишется грубее, но пишется. Прежняя проверка признавала
 * только точное, и на такой выбор кнопка записи молча переспрашивала
 * разрешение по кругу.
 */
private fun hasAnyLocation(context: Context): Boolean =
    hasFineLocation(context) ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

private fun isGpsEnabled(context: Context): Boolean =
    (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        .isProviderEnabled(LocationManager.GPS_PROVIDER)

private fun startTrackRecording(context: Context) {
    ContextCompat.startForegroundService(context, MeasurementService.startTrackIntent(context))
}

private fun stopTrackRecording(context: Context) {
    ContextCompat.startForegroundService(context, MeasurementService.stopTrackIntent(context))
}

private fun metricWithUnit(
    strings: app.radiacode.ui.text.Strings,
    value: Float,
    metric: TrackMetric,
    unit: DoseUnitSetting,
): String = when (metric) {
    TrackMetric.DOSE -> DoseFormat.rateWithUnit(value, unit, s = strings)
    TrackMetric.CPS -> TrackMap.formatCps(value) + " CPS"
}

/**
 * One accumulated-map refresh: an exact aggregate over the viewport, then the
 * grid histogram binned against that aggregate's range. Two bounded queries,
 * no raw point ever crosses into memory.
 */
private suspend fun loadGrid(
    graph: AppGraph,
    viewport: MapViewport,
    metric: TrackMetric,
): GridData {
    val query = TrackGrid.query(viewport)
    val useDose = metric == TrackMetric.DOSE
    val summary = graph.trackRepository.areaSummary(
        useDose = useDose,
        minLatitude = query.minLatitude,
        maxLatitude = query.maxLatitude,
        minLongitude = query.minLongitude,
        maxLongitude = query.maxLongitude,
        maxAccuracyMeters = TrackGrid.MAX_ACCURACY_METERS,
    )
    val minValue = summary.minValue
    val maxValue = summary.maxValue
    if (summary.valueCount == 0 || minValue == null || maxValue == null) {
        return GridData.EMPTY
    }
    val bins = TrackGrid.valueBins(minValue, maxValue)
    val rows = graph.trackRepository.gridHistogram(
        useDose = useDose,
        minLatitude = query.minLatitude,
        maxLatitude = query.maxLatitude,
        minLongitude = query.minLongitude,
        maxLongitude = query.maxLongitude,
        maxAccuracyMeters = TrackGrid.MAX_ACCURACY_METERS,
        latStepDeg = query.latStepDeg,
        lonStepDeg = query.lonStepDeg,
        valueMin = bins.min,
        valueStep = bins.step,
        limit = TrackGrid.MAX_HISTOGRAM_ROWS,
    )
    // Raw device units become µSv/h at this single edge (CLAUDE.md invariant).
    val factor = if (useDose) DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR else 1f
    val histogram = rows.map {
        GridBin(
            latKey = it.latKey,
            lonKey = it.lonKey,
            valueKey = it.valueKey,
            count = it.pointCount,
            minValue = it.minValue * factor,
            maxValue = it.maxValue * factor,
            minTime = it.minTime,
            maxTime = it.maxTime,
        )
    }
    val cells = TrackGrid.cells(histogram, query)
    val medians = cells.map { it.median }
    return GridData(
        cells = cells,
        thresholds = TrackMap.rampThresholds(medians),
        cellMeters = query.cellMeters,
        range = medians.minOrNull()?.let { min -> min to (medians.maxOrNull() ?: min) },
        stats = if (histogram.isEmpty()) null else TrackGrid.stats(histogram),
        pointCount = summary.valueCount,
        maxValue = maxValue * factor,
        firstTime = summary.firstTime,
        lastTime = summary.lastTime,
        hotspots = graph.measurementRepository.hotspotsInBounds(
            minLatitude = query.minLatitude,
            maxLatitude = query.maxLatitude,
            minLongitude = query.minLongitude,
            maxLongitude = query.maxLongitude,
        ).mapNotNull { it.toHotspot(from = Long.MIN_VALUE, to = null) },
    )
}

private fun TrackPointEntity.toMapPoint(): MapTrackPoint = MapTrackPoint(
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    doseMicroSvH = doseRate?.let(DoseUnits::rawToMicroSievertPerHour),
    cps = countRate,
)

private fun EventEntity.toHotspot(from: Long, to: Long?): MapHotspot? {
    if (source != EventEntity.SOURCE_HOTSPOT) return null
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    if (timestamp < from || (to != null && timestamp > to)) return null
    return MapHotspot(
        id = id,
        timestamp = timestamp,
        latitude = lat,
        longitude = lon,
        doseMicroSvH = doseRate?.let(DoseUnits::rawToMicroSievertPerHour),
        typicalMicroSvH = if (param1 > 0) param1 / 1000f else null,
    )
}
