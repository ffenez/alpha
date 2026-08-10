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
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.TrackPointEntity
import app.radiacode.device.DoseUnits
import app.radiacode.service.MeasurementService
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.MapBounds
import app.radiacode.ui.logic.MapHotspot
import app.radiacode.ui.logic.MapTrackPoint
import app.radiacode.ui.logic.TileStatus
import app.radiacode.ui.logic.TrackMap
import app.radiacode.ui.logic.TrackMetric
import app.radiacode.ui.map.MapLayerColors
import app.radiacode.ui.map.MapTapInfo
import app.radiacode.ui.map.TileStats
import app.radiacode.ui.map.TrackMapView
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.DoseRampColors
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.take

/** Overlay rebuild cadence while recording (1 Hz points come in faster). */
private const val LIVE_THROTTLE_MILLIS = 5_000L
private const val HOTSPOT_EVENT_LIMIT = 500

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
 * Карта (SPEC «Map», roadmap 6): full-bleed map with the GPS track colored by
 * dose rate (CPS by toggle) on the amber ramp, hotspot markers from events,
 * tap cards with raw values, and start/stop of track recording through
 * [MeasurementService]. While nothing records, the newest finished track is
 * shown read-only.
 */
@OptIn(FlowPreview::class)
@Composable
fun MapScreen(graph: AppGraph) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val recording by graph.serviceStatus.trackRecording.collectAsState()

    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    val metric = if (metricIndex == 0) TrackMetric.DOSE else TrackMetric.CPS

    var data by remember { mutableStateOf<TrackData?>(null) }
    LaunchedEffect(recording?.sessionId) {
        val active = recording
        val session = if (active != null) {
            graph.trackRepository.session(active.sessionId)
        } else {
            graph.trackRepository.latestSession()
        }
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

    // 1 s wall clock for the recording-duration chip.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording) {
        while (recording != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Location permission and GPS provider state, re-checked on resume (the
    // user may change both in system settings).
    var hasLocation by remember { mutableStateOf(hasFineLocation(context)) }
    var gpsEnabled by remember { mutableStateOf(isGpsEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocation = hasFineLocation(context)
                gpsEnabled = isGpsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasLocation = results[FINE_LOCATION] == true || hasFineLocation(context)
        if (hasLocation) startTrackRecording(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Chip(text = "Карта", color = colors.ink)
            Spacer(Modifier.weight(1f))
            if (!gpsEnabled) {
                Chip(
                    text = "GPS выключен",
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
            if (recording == null && d != null && d.hasTrack && d.startedAt != null) {
                Chip(text = "последняя · " + HistoryFormat.dayTime(d.startedAt, nowMillis))
            }
        }

        val d = data
        TrackMapCard(
            graph = graph,
            data = d,
            metric = metric,
            metricIndex = metricIndex,
            onMetricSelect = { metricIndex = it },
            unit = unit,
            recordingChips = {
                val active = recording
                if (active != null) {
                    Chip(
                        text = "запись · " +
                            HistoryFormat.duration((nowMillis - active.startedAt) / 1000),
                        color = colors.ok,
                        dot = colors.ok,
                    )
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        if (d != null && d.hasTrack && d.points.isNotEmpty()) {
            RouteSummaryCard(
                data = d,
                unit = unit,
                title = if (recording != null) "Мой маршрут" else "Маршрут",
            )
        }

        when {
            !hasLocation -> LocationPermissionCard(
                onRequest = { permissionLauncher.launch(arrayOf(FINE_LOCATION, COARSE_LOCATION)) },
            )
            recording != null -> AppButton(
                text = "Остановить запись",
                onClick = { stopTrackRecording(context) },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> AppButton(
                text = "Начать запись маршрута",
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
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    val metric = if (metricIndex == 0) TrackMetric.DOSE else TrackMetric.CPS

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
            AppButton(text = "← Назад", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "Трек сессии", color = colors.ink)
        }

        val d = data
        if (d != null && !d.hasTrack) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "трек в этой сессии не записан",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
        } else {
            TrackMapCard(
                graph = graph,
                data = d,
                metric = metric,
                metricIndex = metricIndex,
                onMetricSelect = { metricIndex = it },
                unit = unit,
                recordingChips = {},
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (d != null && d.points.isNotEmpty()) {
                RouteSummaryCard(data = d, unit = unit, title = "Маршрут")
            }
        }
    }
}

// --- map card with overlays ---

@Composable
private fun TrackMapCard(
    graph: AppGraph,
    data: TrackData?,
    metric: TrackMetric,
    metricIndex: Int,
    onMetricSelect: (Int) -> Unit,
    unit: DoseUnitSetting,
    recordingChips: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

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
    )

    Card(modifier = modifier, contentPadding = 0.dp) {
        TrackMapView(
            dark = colors.isDark,
            layerColors = layerColors,
            points = render.renderedPoints,
            metric = metric,
            thresholds = render.thresholds,
            hotspots = data?.hotspots.orEmpty(),
            bounds = render.bounds,
            recenterTick = recenterTick,
            onTap = { tap = it },
            onTileStats = { tiles = it },
            modifier = Modifier.fillMaxSize(),
        )

        // Top-left: recording state, distance, offline notice.
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.TopStart).padding(Dimens.space2),
        ) {
            recordingChips()
            if (render.distanceMeters > 0) {
                Chip(text = TrackMap.formatDistance(render.distanceMeters))
            }
            // Honest tile state, always visible: «загружаются…» while the
            // first tiles are on their way, a count once they arrive, and a
            // named cause when nothing ever comes.
            val hint = TileStatus.networkHint(tiles.loaded, tiles.failed, waitedMillis)
            Chip(
                text = TileStatus.line(tiles.loaded, tiles.failed, waitedMillis),
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

        // Top-right: re-center on the track after a manual pan.
        if (render.bounds != null) {
            Chip(
                text = "⌖ маршрут",
                onClick = { recenterTick++ },
                modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.space2),
            )
        }

        // Bottom-right: metric toggle over the ramp legend.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.space2),
        ) {
            Segmented(
                options = listOf("Доза", "CPS"),
                selectedIndex = metricIndex,
                onSelect = onMetricSelect,
                modifier = Modifier.width(150.dp),
            )
            render.range?.let { (min, max) ->
                LegendBar(
                    minLabel = legendLabel(min, metric, unit),
                    maxLabel = legendLabel(max, metric, unit),
                )
            }
        }

        // Empty state teaches the first action (design language).
        if (data != null && !data.hasTrack) {
            Card(
                background = colors.surface,
                modifier = Modifier.align(Alignment.Center).padding(Dimens.space4),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                    Text(
                        text = "Маршрутов пока нет",
                        style = type.label,
                        color = colors.ink,
                    )
                    Text(
                        text = "Начните запись — маршрут окрасится мощностью дозы, " +
                            "устойчивые превышения станут метками.",
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                }
            }
        }

        tap?.let { info ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = Dimens.space2, end = Dimens.space2, bottom = 44.dp),
            ) {
                when (info) {
                    is MapTapInfo.TrackPoint -> TrackPointCard(info, unit)
                    is MapTapInfo.Hotspot -> HotspotCard(graph, info, unit)
                }
            }
        }
    }
}

/** Ramp swatches with the honest min/max of the visible track. */
@Composable
private fun LegendBar(minLabel: String, maxLabel: String) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(colors.surface)
            .padding(horizontal = 9.dp, vertical = 5.dp),
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
}

private fun legendLabel(value: Float, metric: TrackMetric, unit: DoseUnitSetting): String =
    when (metric) {
        TrackMetric.DOSE -> DoseFormat.rate(value, unit)
        TrackMetric.CPS -> TrackMap.formatCps(value)
    }

// --- tap cards ---

@Composable
private fun TrackPointCard(info: MapTapInfo.TrackPoint, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
            Text(
                text = "Точка маршрута".uppercase(),
                style = type.overline,
                color = colors.muted,
            )
            Text(
                text = listOfNotNull(
                    info.doseMicroSvH?.let { DoseFormat.rateWithUnit(it, unit) },
                    info.cps?.let { TrackMap.formatCps(it) + " CPS" },
                    HistoryFormat.dayTime(info.timestamp, System.currentTimeMillis()),
                ).joinToString(" · "),
                style = type.value,
                color = colors.ink,
            )
        }
    }
}

/** Loaded lazily for the hotspot card: CPS at the moment and dwell time. */
@Immutable
private data class HotspotExtras(val cps: Float?, val dwellSeconds: Long?)

@Composable
private fun HotspotCard(graph: AppGraph, info: MapTapInfo.Hotspot, unit: DoseUnitSetting) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

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
                text = "Точка превышения".uppercase(),
                style = type.overline,
                color = colors.crit,
            )
            Text(
                text = listOfNotNull(
                    info.doseMicroSvH?.let { DoseFormat.rateWithUnit(it, unit) },
                    extras?.cps?.let { TrackMap.formatCps(it) + " CPS" },
                    HistoryFormat.dayTime(info.timestamp, System.currentTimeMillis()),
                ).joinToString(" · "),
                style = type.value,
                color = colors.ink,
            )
            info.typicalMicroSvH?.let {
                Text(
                    text = "обычно здесь " + DoseFormat.rateWithUnit(it, unit),
                    style = type.footnote,
                    color = colors.ink2,
                )
            }
            extras?.dwellSeconds?.let { dwell ->
                Text(
                    text = "показания устойчивы ${HistoryFormat.duration(dwell)} · расчёт",
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
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
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
                    text = DoseFormat.rateUnitLabel(unit),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            StatGrid(
                cells = listOf(
                    StatCell(
                        summary.avgDoseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                        "ср",
                    ),
                    StatCell(
                        summary.maxDoseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                        "макс",
                    ),
                    StatCell(HistoryFormat.count(summary.pointCount), "точек"),
                    StatCell(HistoryFormat.count(data.hotspots.size), "меток"),
                ),
            )
        }
    }
}

@Composable
private fun LocationPermissionCard(onRequest: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = "Для записи маршрута нужна геолокация",
                style = type.label,
                color = colors.ink,
            )
            Text(
                text = "Точки трека привязываются к GPS-координатам. Они сохраняются " +
                    "только на этом телефоне и никуда не отправляются. Если запрос " +
                    "не показывается — включите доступ в настройках Android.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            AppButton(
                text = "Разрешить геолокацию",
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

private fun isGpsEnabled(context: Context): Boolean =
    (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        .isProviderEnabled(LocationManager.GPS_PROVIDER)

private fun startTrackRecording(context: Context) {
    ContextCompat.startForegroundService(context, MeasurementService.startTrackIntent(context))
}

private fun stopTrackRecording(context: Context) {
    ContextCompat.startForegroundService(context, MeasurementService.stopTrackIntent(context))
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
