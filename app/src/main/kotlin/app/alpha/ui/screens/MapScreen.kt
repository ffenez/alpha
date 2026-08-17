package app.alpha.ui.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.alpha.AppGraph
import app.alpha.data.DoseUnitSetting
import app.alpha.data.MapTrackScope
import app.alpha.baseline.BaselineState
import app.alpha.data.db.EventEntity
import app.alpha.data.db.TrackPointEntity
import app.alpha.device.DoseUnits
import app.alpha.service.MeasurementService
import app.alpha.service.ServiceStatus
import app.alpha.ui.components.AppButton
import app.alpha.data.export.GeoJson
import app.alpha.data.export.ReportFactories
import app.alpha.data.export.SeriesExport
import app.alpha.data.export.html.RoutePrivacy
import app.alpha.data.export.html.RouteReportHtml
import app.alpha.data.export.html.RouteTrim
import app.alpha.ui.text.ExportCatalogue
import app.alpha.ui.components.Card
import app.alpha.ui.components.ConfirmDialog
import app.alpha.ui.components.EntityHeader
import app.alpha.ui.components.RenameDialog
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Hint
import app.alpha.ui.components.MapGestureLock
import app.alpha.ui.components.RouteProfileChart
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.GridBin
import app.alpha.ui.logic.GridCell
import app.alpha.ui.logic.MIN_CONFIDENT_POINTS
import app.alpha.ui.logic.GridStats
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.RouteFormat
import app.alpha.ui.logic.DoseTint
import app.alpha.ui.logic.MapBounds
import app.alpha.ui.logic.MapColorScale
import app.alpha.ui.logic.MapHotspot
import app.alpha.ui.logic.MapTrackPoint
import app.alpha.ui.logic.MapViewport
import app.alpha.ui.logic.MyPosition
import app.alpha.ui.logic.PositionFix
import app.alpha.ui.logic.PositionState
import app.alpha.ui.logic.RouteProfile
import app.alpha.ui.logic.TileStatus
import app.alpha.ui.logic.TrackGrid
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.logic.TrackMetric
import app.alpha.ui.map.MapLayerColors
import app.alpha.ui.map.MapTapInfo
import app.alpha.ui.map.TileStats
import app.alpha.ui.map.TrackMapView
import app.alpha.ui.map.anyLocationProviderEnabled
import app.alpha.ui.map.rememberMyPosition
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.MapCatalogue
import app.alpha.ui.text.SearchCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.TrackRampColors
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
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
    val scale: TrackMap.RampScale?,
    val bounds: MapBounds?,
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
    val scale: TrackMap.RampScale?,
    val cellMeters: Double,
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
            emptyList(), null, 0.0, null, 0, null, null, null, emptyList(),
        )
    }
}

/**
 * Карта (SPEC «Map», roadmap 6): full-bleed map with the GPS track drawn as a
 * continuous line colored by dose rate (CPS by toggle) on the green→crimson
 * ramp, hotspot markers from events, tap cards with raw values, and start/stop
 * of track recording through [MeasurementService].
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

    // Чем заданы границы цвета следа и от какого «обычно здесь» они считаются.
    val scaleMode by graph.settings.mapColorScale.collectAsState(initial = MapColorScale.ABSOLUTE)
    val tintFactor by graph.settings.doseTintFactor
        .collectAsState(initial = DoseTint.DEFAULT_FACTOR)
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val baseline = (baselineState as? BaselineState.Active)?.baseline
    val usualBand = baseline?.let {
        when (metric) {
            TrackMetric.DOSE -> it.doseLowMicroSvH to it.doseHighMicroSvH
            TrackMetric.CPS -> it.cpsLow to it.cpsHigh
        }
    }
    val manualDose by graph.settings.manualDoseAnchors
        .collectAsState(initial = TrackMap.DEFAULT_MANUAL_DOSE)
    val manualCps by graph.settings.manualCpsAnchors
        .collectAsState(initial = TrackMap.DEFAULT_MANUAL_CPS)
    val manualAnchors = when (metric) {
        TrackMetric.DOSE -> manualDose
        TrackMetric.CPS -> manualCps
    }
    // Счётчик тайлов — диагностика: виден при включённом отладочном отчёте.
    val debugReport by graph.settings.debugReportEnabled.collectAsState(initial = false)

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
        // У начатой записи маршрута может не быть строки в журнале: она
        // появляется с первой координатой. Тогда экран говорит «жду первые
        // точки», а не показывает прошлую прогулку.
        val session = when {
            active?.sessionId != null -> graph.trackRepository.session(active.sessionId!!)
            active != null -> null
            else -> graph.trackRepository.latestSession()
        }
        if (active != null && session == null) {
            data = TrackData.EMPTY
            return@LaunchedEffect
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
        // «Приблизительно» — тоже разрешение, и запись с ним начинается.
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
    LaunchedEffect(
        scope, metric, viewport, gridTick, scaleMode, usualBand, tintFactor, manualAnchors,
    ) {
        if (scope != MapTrackScope.ALL) return@LaunchedEffect
        val current = viewport ?: return@LaunchedEffect
        delay(GRID_DEBOUNCE_MILLIS)
        grid = withContext(Dispatchers.IO) {
            loadGrid(graph, current, metric, scaleMode, usualBand, tintFactor, manualAnchors)
        }
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
            scaleMode = scaleMode,
            usualBand = usualBand,
            tintFactor = tintFactor,
            manualAnchors = manualAnchors,
            showTileStats = debugReport,
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
            gpsOff = hasLocation && !gpsEnabled,
            onFixGps = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            // Идущая запись останавливается там же, где показана.
            recordingSince = recording?.startedAt,
            onToggleRecording = if (hasLocation) {
                {
                    if (recording != null) {
                        stopTrackRecording(context)
                    } else {
                        startTrackRecording(context)
                    }
                }
            } else {
                null
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
                scaleMode = scaleMode,
                scaleIsAbsolute = usualBand != null,
            )
        }

        if (justStopped && scope == MapTrackScope.CURRENT) {
            AppButton(
                text = t.showAllRecordings,
                onClick = { setScope(MapTrackScope.ALL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val active = recording
        when {
            !hasLocation -> LocationPermissionCard(
                onRequest = { permissionLauncher.launch(arrayOf(FINE_LOCATION, COARSE_LOCATION)) },
            )
            // Идущая запись видна значком на карте; внизу остаётся только
            // причина отсутствия точек.
            active != null -> TrackLocationNotice(state = trackLocation)
            // Начать маршрут — обычная кнопка под картой: это действие, а не
            // состояние, и место действия внизу, под большим пальцем. С
            // началом записи кнопка исчезает, а состояние переезжает значком
            // в правый верхний угол карты.
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
fun SessionTrackMapScreen(
    graph: AppGraph,
    sessionId: Long,
    onBack: () -> Unit,
    onOpenChart: ((from: Long, to: Long) -> Unit)? = null,
) {
    val t = MapCatalogue.of(LocalStrings.current.language)
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
    TrackDetailScreen(
        graph = graph,
        data = data,
        title = t.sessionTrack,
        onBack = onBack,
        onOpenChart = onOpenChart,
    )
}

/**
 * Сохранённый маршрут, открытый из Истории.
 *
 * Ключ — сам маршрут, а не сессия измерения: одна прогулка может лежать внутри
 * одной сессии, поперёк двух или вовсе без неё.
 */
@Composable
fun RouteMapScreen(
    graph: AppGraph,
    routeId: Long,
    onBack: () -> Unit,
    onOpenChart: ((from: Long, to: Long) -> Unit)? = null,
) {
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val t = MapCatalogue.of(strings.language)
    var data by remember { mutableStateOf<TrackData?>(null) }
    var title by remember { mutableStateOf(t.route) }
    LaunchedEffect(routeId) {
        val session = graph.trackRepository.session(routeId)
        if (session == null) {
            data = TrackData.EMPTY
            return@LaunchedEffect
        }
        val to = session.endedAt ?: System.currentTimeMillis()
        val points = graph.trackRepository.points(routeId).first().map { it.toMapPoint() }
        val hotspots = graph.sessionRepository
            .deviationEvents(session.startedAt, to, limit = HOTSPOT_EVENT_LIMIT)
            .mapNotNull { it.toHotspot(session.startedAt, to) }
        title = session.name.trim()
            .ifEmpty { HistoryFormat.dayTime(session.startedAt, System.currentTimeMillis(), s = h) }
        data = TrackData(
            trackSessionId = routeId,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            points = points,
            hotspots = hotspots,
        )
    }
    TrackDetailScreen(
        graph = graph,
        data = data,
        title = title,
        onBack = onBack,
        onOpenChart = onOpenChart,
    )
}

/** Общий экран одного следа: карта, сводка, экспорт. */
@Composable
private fun TrackDetailScreen(
    graph: AppGraph,
    data: TrackData?,
    title: String,
    onBack: () -> Unit,
    /** Открыть измерения этого отрезка времени полноэкранным графиком. */
    onOpenChart: ((from: Long, to: Long) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MapCatalogue.of(strings.language)
    val h = HistoryCatalogue.of(strings.language)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    // Карта открыта поверх вкладки — горизонтальный жест принадлежит ей.
    MapGestureLock()

    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    val metric = if (metricIndex == 0) TrackMetric.DOSE else TrackMetric.CPS

    // Шкала та же, что на карте: маршрут, открытый через неделю, выглядит так
    // же.
    val scaleMode by graph.settings.mapColorScale.collectAsState(initial = MapColorScale.ABSOLUTE)
    val tintFactor by graph.settings.doseTintFactor
        .collectAsState(initial = DoseTint.DEFAULT_FACTOR)
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val usualBand = (baselineState as? BaselineState.Active)?.baseline?.let {
        when (metric) {
            TrackMetric.DOSE -> it.doseLowMicroSvH to it.doseHighMicroSvH
            TrackMetric.CPS -> it.cpsLow to it.cpsHigh
        }
    }

    val manualDose by graph.settings.manualDoseAnchors
        .collectAsState(initial = TrackMap.DEFAULT_MANUAL_DOSE)
    val manualCps by graph.settings.manualCpsAnchors
        .collectAsState(initial = TrackMap.DEFAULT_MANUAL_CPS)
    val manualAnchors = when (metric) {
        TrackMetric.DOSE -> manualDose
        TrackMetric.CPS -> manualCps
    }

    // Имя маршрута правится здесь, поэтому экран держит своё значение:
    // параметр придёт заново при следующем открытии.
    var shownTitle by remember(title) { mutableStateOf(title) }

    // Курсор один на карту и на график: это один момент одной прогулки.
    var cursorIndex by remember(data) { mutableStateOf<Int?>(null) }
    val cursorPoint = data?.points?.getOrNull(cursorIndex ?: -1)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    val saver = rememberFileSaver { ok -> notice = if (ok) t.exportSaved else t.exportFailed }
    // Любой файл маршрута несёт координаты, поэтому вопрос о них задаётся один
    // раз — между выбором формата и системным диалогом сохранения.
    var pendingFormat by remember { mutableStateOf<ExportFile?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun exportRoute(trackId: Long, format: ExportFile, privacy: RoutePrivacy) {
        scope.launch {
            val all = graph.trackRepository.points(trackId).first()
            val kept = if (privacy == RoutePrivacy.FULL) all else RouteTrim.ends(all)
            val stamp = all.firstOrNull()?.timestamp ?: System.currentTimeMillis()
            val name = SeriesExport.fileName(stamp, format.extension)
            when (format) {
                ExportFile.HTML -> {
                    val session = graph.trackRepository.session(trackId)
                    val summary = session?.let { graph.trackRepository.routeSummary(it) }
                    if (summary != null) {
                        saver.save(
                            format,
                            name,
                            RouteReportHtml.render(
                                ReportFactories.route(
                                    summary = summary,
                                    points = all,
                                    privacy = privacy,
                                    appName = REPORT_APP,
                                    appVersion = appVersionName(context) ?: "",
                                    language = strings.language,
                                ),
                            ),
                        )
                    }
                }
                ExportFile.GEOJSON -> saver.save(format, name, GeoJson.route(kept, title))
                ExportFile.GPX -> saver.save(format, name, SeriesExport.gpx(kept, title))
                else -> saver.save(format, name, SeriesExport.trackCsv(kept))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        // Шапка маршрута — та же, что у сессии, спектра и опыта: имя, время
        // и «⋮».
        val trackId = data?.trackSessionId
        EntityHeader(
            title = shownTitle,
            subtitle = data?.let { d ->
                listOfNotNull(
                    d.startedAt?.let {
                        HistoryFormat.dayTime(it, System.currentTimeMillis(), s = h)
                    },
                    HistoryFormat.duration(
                        ((d.endedAt ?: System.currentTimeMillis()) - (d.startedAt ?: 0L)) / 1000,
                        s = h,
                    ),
                ).joinToString(" · ")
            },
            onBack = onBack,
            menu = if (trackId == null) {
                emptyList()
            } else {
                EntityMenus.route(
                    strings = strings,
                    export = ExportCatalogue.of(strings.language),
                    history = h,
                    // Сравнение маршрутов выбирают в Журнале, где видны оба.
                    canCompare = false,
                    onExport = { exporting = true },
                    onCompare = {},
                    onRename = { renaming = true },
                    onDelete = { confirmDelete = true },
                )
            },
        )
        trackId?.let { id ->
            if (exporting) {
                val e = ExportCatalogue.of(strings.language)
                EntityExportSheet(
                    title = e.export,
                    groups = listOf(
                        ExportGroup(
                            title = e.groupReport,
                            options = listOf(
                                ExportOptions.report(e) {
                                    exporting = false
                                    pendingFormat = ExportFile.HTML
                                },
                            ),
                        ),
                        ExportGroup(
                            title = e.groupExchange,
                            options = listOf(
                                ExportOptions.map(e) {
                                    exporting = false
                                    pendingFormat = ExportFile.GEOJSON
                                },
                                ExportOptions.track(e) {
                                    exporting = false
                                    pendingFormat = ExportFile.GPX
                                },
                            ),
                        ),
                        ExportGroup(
                            title = e.groupTable,
                            options = listOf(
                                ExportOptions.table(e) {
                                    exporting = false
                                    pendingFormat = ExportFile.CSV
                                },
                            ),
                        ),
                    ),
                    onDismiss = { exporting = false },
                )
            }
            pendingFormat?.let { format ->
                RoutePrivacyDialog(
                    // «Без координат» оставляет только ход измерения во
                    // времени: карты и следа в таком файле нет, поэтому
                    // вариант предлагается только отчёту.
                    allowNoCoordinates = format == ExportFile.HTML,
                    onPick = { privacy ->
                        pendingFormat = null
                        exportRoute(id, format, privacy)
                    },
                    onDismiss = { pendingFormat = null },
                )
            }
            if (renaming) {
                RenameDialog(
                    title = h.routeRename,
                    initial = shownTitle,
                    placeholder = h.routeNameHint,
                    clean = { RouteFormat.cleanName(it) },
                    onSave = { name ->
                        renaming = false
                        scope.launch {
                            graph.trackRepository.rename(id, name)
                            shownTitle = name.trim().ifEmpty { shownTitle }
                        }
                    },
                    onDismiss = { renaming = false },
                )
            }
            if (confirmDelete) {
                ConfirmDialog(
                    title = h.routeDeleteTitle(1),
                    body = h.routeDeleteBody,
                    confirmText = strings.delete,
                    onConfirm = {
                        confirmDelete = false
                        scope.launch {
                            graph.trackRepository.delete(id)
                            onBack()
                        }
                    },
                    onDismiss = { confirmDelete = false },
                )
            }
        }
        // Сводка маршрута — одна тусклая строка под заголовком: путь и
        // длительность отвечают, что это за прогулка.
        data?.takeIf { it.points.isNotEmpty() }?.let { d ->
            val summary = TrackMap.summary(d.points)
            Text(
                text = listOfNotNull(
                    TrackMap.formatDistance(TrackMap.distanceMeters(d.points), t),
                    HistoryFormat.duration(
                        ((d.endedAt ?: System.currentTimeMillis()) - (d.startedAt ?: 0L)) / 1000,
                        s = h,
                    ),
                    summary.avgDoseMicroSvH?.let { "${t.statAvg} ${DoseFormat.rate(it, unit)}" },
                    summary.maxDoseMicroSvH?.let {
                        "${t.statMax} ${DoseFormat.rateWithUnit(it, unit, s = strings)}"
                    },
                ).joinToString(" · "),
                style = type.footnote,
                color = colors.muted,
            )
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
                scaleMode = scaleMode,
                usualBand = usualBand,
                tintFactor = tintFactor,
                manualAnchors = manualAnchors,
                cursor = cursorPoint,
                onTrackPointTap = { timestamp ->
                    cursorIndex = RouteProfile.indexOfTime(
                        d?.points.orEmpty().map { it.timestamp },
                        timestamp,
                    )
                },
                onViewport = {},
                emptyState = {},
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (d != null && d.points.isNotEmpty()) {
                // График и карта показывают одно и то же: курсор общий.
                RouteProfileChart(
                    points = d.points,
                    metric = metric,
                    cursorIndex = cursorIndex,
                    onCursor = { cursorIndex = it },
                    valueLabel = { point ->
                        when (metric) {
                            TrackMetric.DOSE -> point.doseMicroSvH
                                ?.let { DoseFormat.rateWithUnit(it, unit, s = strings) }
                            TrackMetric.CPS -> point.cps
                                ?.let { TrackMap.formatCps(it) + " " + t.unitCps }
                        }
                    },
                    timeLabel = { point -> HistoryFormat.timeOfDay(point.timestamp) },
                    detailLabel = { point ->
                        listOfNotNull(
                            when (metric) {
                                TrackMetric.DOSE -> point.cps
                                    ?.let { TrackMap.formatCps(it) + " " + t.unitCps }
                                TrackMetric.CPS -> point.doseMicroSvH
                                    ?.let { DoseFormat.rateWithUnit(it, unit, s = strings) }
                            },
                            MyPosition.accuracy(point.accuracyMeters, t),
                        ).joinToString(" · ")
                    },
                    onOpen = onOpenChart?.let { open ->
                        {
                            val from = d.points.first().timestamp
                            val to = d.points.last().timestamp
                            open(from, to)
                        }
                    },
                )
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
    /** Чем заданы границы цвета: обычным фоном места или самим маршрутом. */
    scaleMode: MapColorScale = MapColorScale.ABSOLUTE,
    /** «Обычно здесь» для выбранной величины; null — сравнивать не с чем. */
    usualBand: Pair<Float, Float>? = null,
    tintFactor: Float = DoseTint.DEFAULT_FACTOR,
    /** Границы ручной шкалы для выбранной величины. */
    manualAnchors: List<Float> = emptyList(),
    /** Счётчик тайлов — только при включённом отладочном отчёте. */
    showTileStats: Boolean = false,
    /** Общий с графиком курсор: выбранный момент маршрута. */
    cursor: MapTrackPoint? = null,
    /** Тап по следу выбирает момент, а не только открывает карточку. */
    onTrackPointTap: (Long) -> Unit = {},
    onViewport: (MapViewport) -> Unit,
    emptyState: @Composable () -> Unit,
    /** Идущая запись следа: с какого момента она идёт; null — записи нет. */
    /** Определение места выключено в системе — чип об этом стоит на карте. */
    gpsOff: Boolean = false,
    onFixGps: (() -> Unit)? = null,
    recordingSince: Long? = null,
    /** Кнопка записи на самой карте; null — экран только смотрит (История). */
    onToggleRecording: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)

    val render = remember(data, metric, scaleMode, usualBand, tintFactor, manualAnchors) {
        val points = data?.points.orEmpty()
        val metricValues = points.mapNotNull { TrackMap.metricValue(it, metric) }
        RenderModel(
            renderedPoints = TrackMap.downsample(points),
            scale = TrackMap.scaleFor(
                mode = scaleMode,
                usualBand = usualBand,
                factor = tintFactor,
                values = metricValues,
                manualAnchors = manualAnchors,
            ),
            bounds = TrackMap.bounds(points),
            summary = TrackMap.summary(points),
            distanceMeters = TrackMap.distanceMeters(points),
        )
    }
    val lineBreaks = remember(render.renderedPoints) {
        TrackMap.lineBreaks(render.renderedPoints)
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
        ramp = TrackRampColors.map { it.toArgb() },
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
            scale = render.scale,
            lineBreaks = lineBreaks,
            hotspots = grid?.hotspots ?: data?.hotspots.orEmpty(),
            bounds = fitBounds,
            recenterTick = recenterTick,
            cursor = cursor,
            onTap = { info ->
                tap = info
                if (info is MapTapInfo.TrackPoint) onTrackPointTap(info.timestamp)
            },
            onTileStats = { tiles = it },
            modifier = Modifier.fillMaxSize(),
            cells = grid?.cells.orEmpty(),
            cellMeters = grid?.cellMeters ?: 0.0,
            cellScale = grid?.scale,
            position = position.takeIf { MyPosition.markerVisible(positionState, position) },
            positionStale = position != null && MyPosition.isStale(position, nowMillis),
            centerOnPositionTick = followTick,
            onViewport = onViewport,
        )

        // Сверху слева — пройденное расстояние: единственное число, которое
        // относится к нарисованному и меняется по мере ходьбы.
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.TopStart).padding(Dimens.space2),
        ) {
            if (grid == null && render.distanceMeters > 0) {
                Chip(text = TrackMap.formatDistance(render.distanceMeters, t))
            }
            // Счётчик тайлов — диагностика под отладочным отчётом, но о том,
            // что карта пуста из-за сети, экран говорит всегда.
            val hint = TileStatus.networkHint(tiles.loaded, tiles.failed, waitedMillis, t)
            if (showTileStats) {
                Chip(
                    text = TileStatus.line(tiles.loaded, tiles.failed, waitedMillis, t),
                    color = if (hint != null) colors.warn else colors.ink2,
                    dot = if (hint != null) colors.warn else null,
                )
            }
            if (hint != null) {
                Card(
                    background = colors.surface,
                    modifier = Modifier.widthIn(max = 260.dp),
                ) {
                    Text(text = hint, style = type.footnote, color = colors.warn)
                }
            }
            // Ожидание координат — состояние: пока фикса нет, это объяснение
            // неподвижной карте.
            MyPosition.chipText(
                state = positionState,
                fix = position,
                nowMillis = nowMillis,
                s = t,
                trackWaiting = trackWaiting,
            )?.takeIf { positionState == PositionState.WAITING_FIX }?.let { text ->
                Chip(text = text, color = colors.ink2)
            }
        }

        // Сверху справа — состояние: идущая запись и выключенное определение
        // места. Оба видны, не читая экран, и оба нажимаются там, где стоят.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.space2),
        ) {
            if (recordingSince != null && onToggleRecording != null) {
                RecordingBadge(
                    since = recordingSince,
                    nowMillis = nowMillis,
                    onClick = onToggleRecording,
                )
            }
            if (gpsOff) {
                Chip(
                    text = t.gpsOffAction,
                    color = colors.warn,
                    dot = colors.warn,
                    onClick = onFixGps,
                )
            }
        }

        // Снизу слева — переключатель величины, под большим пальцем.
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier
                .align(Alignment.BottomStart)
                // Снизу слева стоит указание авторства OpenStreetMap —
                // условие лицензии тайлов; отступ оставляет его видимым.
                .padding(start = Dimens.space2, end = Dimens.space2, bottom = ATTRIBUTION_SPACE),
        ) {
            Segmented(
                options = listOf(t.metricDose, t.metricCps),
                selectedIndex = metricIndex,
                onSelect = onMetricSelect,
                modifier = Modifier.width(METRIC_TOGGLE_WIDTH),
            )
        }

        // Снизу справа — камера: «я на карте» и «показать целиком» — два
        // разных действия с разными значками.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.space2),
        ) {
            val fitBoundsAvailable = if (grid != null) initialBounds != null else render.bounds != null
            if (fitBoundsAvailable) {
                MapIconButton(
                    icon = MapIcon.FIT,
                    description = if (grid != null) t.centerOnAll else t.centerOnRoute,
                    onClick = { recenterTick++ },
                )
            }
            if (MyPosition.markerVisible(positionState, position)) {
                MapIconButton(
                    icon = MapIcon.MY_LOCATION,
                    description = t.centerOnMe,
                    onClick = { followTick++ },
                    tint = colors.data,
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

/** Что делает кнопка поверх карты. Два действия — два разных рисунка. */
private enum class MapIcon { MY_LOCATION, FIT }

/** Место под указание авторства OSM в нижнем левом углу карты. */
private val ATTRIBUTION_SPACE = 22.dp

/** Переключатель величины: по содержимому, а не во всю ширину карты. */
private val METRIC_TOGGLE_WIDTH = 132.dp

/**
 * Круглая кнопка поверх карты: значок рисуется, а не набирается символом —
 * «⌖» в разных шрифтах выглядит по-разному и не везде отрисовывается. Цель
 * нажатия обычного мобильного размера; подпись остаётся для доступности.
 */
@Composable
private fun MapIconButton(
    icon: MapIcon,
    description: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val colors = LocalAppColors.current
    val color = tint ?: colors.ink
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Dimens.touchTarget)
            .clip(RoundedCornerShape(Dimens.touchTarget / 2))
            .background(colors.surface)
            .clickable(onClickLabel = description, onClick = onClick),
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 1.6.dp.toPx()
            when (icon) {
                // «Я на карте»: кольцо с точкой и четырьмя засечками по осям.
                MapIcon.MY_LOCATION -> {
                    val radius = size.minDimension / 2f
                    val ring = radius * 0.55f
                    drawCircle(color = color, radius = ring, style = Stroke(width = stroke))
                    drawCircle(color = color, radius = ring * 0.34f)
                    val tick = radius * 0.28f
                    listOf(
                        Offset(center.x, 0f) to Offset(center.x, tick),
                        Offset(center.x, size.height) to Offset(center.x, size.height - tick),
                        Offset(0f, center.y) to Offset(tick, center.y),
                        Offset(size.width, center.y) to Offset(size.width - tick, center.y),
                    ).forEach { (from, to) ->
                        drawLine(color = color, start = from, end = to, strokeWidth = stroke)
                    }
                }
                // «Показать целиком»: четыре угла рамки — жест «вместить всё».
                MapIcon.FIT -> {
                    val inset = size.minDimension * 0.16f
                    val arm = size.minDimension * 0.22f
                    val left = inset
                    val top = inset
                    val right = size.width - inset
                    val bottom = size.height - inset
                    listOf(
                        Offset(left, top) to Offset(left + arm, top),
                        Offset(left, top) to Offset(left, top + arm),
                        Offset(right, top) to Offset(right - arm, top),
                        Offset(right, top) to Offset(right, top + arm),
                        Offset(left, bottom) to Offset(left + arm, bottom),
                        Offset(left, bottom) to Offset(left, bottom - arm),
                        Offset(right, bottom) to Offset(right - arm, bottom),
                        Offset(right, bottom) to Offset(right, bottom - arm),
                    ).forEach { (from, to) ->
                        drawLine(color = color, start = from, end = to, strokeWidth = stroke)
                    }
                }
            }
        }
    }
}

/** Значение выбранной величины так, как его подписывают карточки карты. */
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
private fun RouteSummaryCard(
    data: TrackData,
    unit: DoseUnitSetting,
    title: String,
    /** Чем заданы границы цвета следа; на карте это место не занимает. */
    scaleMode: MapColorScale = MapColorScale.ABSOLUTE,
    scaleIsAbsolute: Boolean = true,
) {
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
                cells = listOfNotNull(
                    StatCell(
                        summary.avgDoseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                        t.statAvg,
                    ),
                    StatCell(
                        summary.maxDoseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                        t.statMax,
                    ),
                    // «Измерений», а не «точек»: считаются радиометрические
                    // измерения вдоль маршрута.
                    StatCell(HistoryFormat.count(summary.pointCount), t.statMeasurements),
                    // Клетка меток появляется с первой меткой: «0 меток» —
                    // не факт о маршруте.
                    data.hotspots.size.takeIf { it > 0 }?.let {
                        StatCell(HistoryFormat.count(it), t.statMarkers)
                    },
                ),
            )
            // Что означает цвет — здесь, а не поверх карты: объяснение живёт
            // вместе с остальными пояснениями и выключается вместе с ними.
            Hint(
                text = if (scaleIsAbsolute && scaleMode == MapColorScale.ABSOLUTE) {
                    t.scaleAbsolute
                } else {
                    t.scaleContrast
                },
                style = type.footnote,
                color = colors.muted,
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
    strings: app.alpha.ui.text.Strings,
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
    scaleMode: MapColorScale,
    usualBand: Pair<Float, Float>?,
    tintFactor: Float,
    manualAnchors: List<Float>,
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
        scale = TrackMap.scaleFor(
            mode = scaleMode,
            usualBand = usualBand,
            factor = tintFactor,
            values = medians,
            manualAnchors = manualAnchors,
        ),
        cellMeters = query.cellMeters,
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

/**
 * Значок ИДУЩЕЙ записи следа — на самой карте, сверху справа.
 *
 * Показывается только пока запись идёт: начинают её кнопкой под картой, а это
 * состояние, и стоит оно рядом с тем, что рисует. Нажатие останавливает.
 *
 * Точка дышит, пока идёт запись: неподвижная карта в помещении выглядит
 * одинаково и при работающей записи, и при остановленной. Цвет — зелёный
 * («идёт»), красный в этом приложении принадлежит подтверждённой тревоге.
 */
@Composable
private fun RecordingBadge(
    since: Long?,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = MapCatalogue.of(strings.language)
    val h = HistoryCatalogue.of(strings.language)
    val recording = since != null

    val pulse = rememberInfiniteTransition(label = "recording")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
        modifier = modifier
            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
            .background(colors.surface.copy(alpha = 0.92f))
            .border(
                LocalAppMetrics.current.border,
                if (recording) colors.ok else colors.line,
                RoundedCornerShape(LocalAppMetrics.current.radiusChip),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space2, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(RECORD_DOT)
                .graphicsLayer { this.alpha = if (recording) alpha else 1f }
                .clip(CircleShape)
                .background(if (recording) colors.ok else colors.ink2),
        )
        Text(
            text = if (since != null) {
                HistoryFormat.duration((nowMillis - since) / 1000, s = h)
            } else {
                t.startRecording
            },
            style = type.footnote,
            color = if (recording) colors.ink else colors.ink2,
        )
    }
}

/** Период дыхания точки записи: спокойный пульс, а не мигание тревоги. */
private const val PULSE_MILLIS = 900

private val RECORD_DOT = 9.dp

/**
 * Почему в идущей записи нет точек.
 *
 * Молчать здесь нельзя: неподвижная карта выглядит одинаково и когда телефон
 * ещё ловит спутники, и когда определение места выключено в системе — а во
 * втором случае ждать бесполезно, и человек должен об этом узнать от прибора,
 * а не через полчаса пустого следа.
 */
@Composable
private fun TrackLocationNotice(state: ServiceStatus.TrackLocation) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = MapCatalogue.of(LocalStrings.current.language)
    val (text, tone) = when (state) {
        ServiceStatus.TrackLocation.NO_PROVIDER -> t.emptyNoProviderTitle to colors.warn
        ServiceStatus.TrackLocation.NO_PERMISSION -> t.emptyNoPermissionTitle to colors.warn
        ServiceStatus.TrackLocation.WAITING -> t.emptyWaitingTitle to colors.muted
        ServiceStatus.TrackLocation.RECEIVING -> return
    }
    Text(text = text, style = type.footnote, color = tone)
}
