package app.alpha.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.alpha.AppGraph
import app.alpha.baseline.BaselineState
import app.alpha.data.DoseUnitSetting
import app.alpha.device.DoseUnits
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Hint
import app.alpha.ui.components.MapGestureLock
import app.alpha.ui.components.Segmented
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.DoseTint
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.MapColorScale
import app.alpha.ui.logic.GridCell
import app.alpha.ui.logic.MapTrackPoint
import app.alpha.ui.logic.RouteDiff
import app.alpha.ui.logic.TrackGrid
import app.alpha.ui.logic.RouteFormat
import app.alpha.ui.logic.RouteSummary
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.logic.TrackMetric
import app.alpha.ui.map.MapLayerColors
import app.alpha.ui.map.TrackMapView
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.MapCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.TrackRampColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Один сравниваемый маршрут: его числа и его точки. */
private data class ComparedRoute(
    val summary: RouteSummary,
    val points: List<MapTrackPoint>,
)

/**
 * Сравнение маршрутов.
 *
 * Главное здесь — ОДНА шкала цвета на все маршруты: сравнивать цвета,
 * растянутые каждый по своему маршруту, бессмысленно, потому что багровое на
 * одном и багровое на другом означали бы разные величины. Поэтому шкала
 * абсолютная, от обычного фона места, и она же подписана числами в строках.
 *
 * Чего экран НЕ делает: не объявляет разницу значимой. Маршруты проходят по
 * разной геометрии, в разное время и с разной длительностью, и различие
 * средних само по себе о различии условий не говорит — об этом сказано на
 * экране, а не только здесь.
 */
@Composable
fun RouteCompareScreen(graph: AppGraph, routeIds: List<Long>, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val h = HistoryCatalogue.of(strings.language)
    val t = MapCatalogue.of(strings.language)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val nowMillis = System.currentTimeMillis()
    // Карта на этом экране двигается пальцем — жест не уходит пейджеру.
    MapGestureLock()

    var metricIndex by rememberSaveable { mutableIntStateOf(0) }
    val metric = if (metricIndex == 0) TrackMetric.DOSE else TrackMetric.CPS

    val tintFactor by graph.settings.doseTintFactor
        .collectAsState(initial = DoseTint.DEFAULT_FACTOR)
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val usualBand = (baselineState as? BaselineState.Active)?.baseline?.let {
        when (metric) {
            TrackMetric.DOSE -> it.doseLowMicroSvH to it.doseHighMicroSvH
            TrackMetric.CPS -> it.cpsLow to it.cpsHigh
        }
    }

    var routes by remember { mutableStateOf<List<ComparedRoute>>(emptyList()) }
    var hidden by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(routeIds) {
        routes = withContext(Dispatchers.IO) {
            routeIds.mapNotNull { id ->
                val session = graph.trackRepository.session(id) ?: return@mapNotNull null
                val points = graph.trackRepository.points(id).first().map {
                    MapTrackPoint(
                        timestamp = it.timestamp,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracyMeters = it.accuracyMeters,
                        doseMicroSvH = it.doseRate?.let(DoseUnits::rawToMicroSievertPerHour),
                        cps = it.countRate,
                    )
                }
                ComparedRoute(graph.trackRepository.routeSummary(session), points)
            }
        }
    }

    // Разница по участкам: только когда сравниваются ровно два маршрута —
    // «выше/ниже» это утверждение о ПАРЕ, и у трёх маршрутов его нет.
    var showDiff by rememberSaveable { mutableStateOf(false) }
    val shown = routes.filter { it.summary.id !in hidden }
    val pair = shown.takeIf { it.size == 2 }
    val diff = remember(pair, metric) {
        pair?.let { RouteDiff.compare(it[0].points, it[1].points, metric) }
    }
    val diffCells = remember(diff) {
        diff?.differing?.map { cell ->
            GridCell(
                latKey = cell.latKey,
                lonKey = cell.lonKey,
                southLatitude = cell.southLatitude,
                northLatitude = cell.northLatitude,
                westLongitude = cell.westLongitude,
                eastLongitude = cell.eastLongitude,
                count = minOf(cell.countA, cell.countB),
                // В клетке рисуется НАПРАВЛЕНИЕ: 0 — ниже на первом, 1 — выше.
                median = if (cell.higher) 1f else 0f,
                p10 = cell.p10A,
                p90 = cell.p90A,
                minValue = cell.medianA,
                maxValue = cell.medianB,
                fromMillis = 0L,
                toMillis = 0L,
            )
        }.orEmpty()
    }
    val diffOn = showDiff && diff != null
    // Точки всех показанных маршрутов рисуются одним следом, но линия между
    // соседними маршрутами не проводится: разрывы считаются по каждому
    // маршруту отдельно, и первая точка следующего всегда начинает новый.
    val drawn = remember(shown, metric) {
        val points = ArrayList<MapTrackPoint>()
        val breaks = ArrayList<Boolean>()
        for (route in shown) {
            val sample = TrackMap.downsample(route.points, COMPARE_POINTS_PER_ROUTE)
            val routeBreaks = TrackMap.lineBreaks(sample)
            points += sample
            sample.indices.forEach { breaks += routeBreaks[it] }
        }
        points to breaks.toBooleanArray()
    }
    val scale = remember(drawn, usualBand, tintFactor, metric) {
        TrackMap.scaleFor(
            mode = MapColorScale.ABSOLUTE,
            usualBand = usualBand,
            factor = tintFactor,
            values = drawn.first.mapNotNull { TrackMap.metricValue(it, metric) },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = t.back, onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = h.routeCompareTitle, color = colors.ink)
        }

        if (routes.size < 2) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = h.routeCompareNeedTwo,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = 0.dp) {
            TrackMapView(
                dark = colors.isDark,
                layerColors = MapLayerColors(
                    ramp = if (diffOn) {
                        listOf(colors.data.toArgb(), colors.crit.toArgb())
                    } else {
                        TrackRampColors.map { it.toArgb() }
                    },
                    metricMissing = colors.muted.toArgb(),
                    hotspotFill = colors.crit.toArgb(),
                    hotspotStroke = colors.surface.toArgb(),
                    position = colors.data.toArgb(),
                    positionRing = colors.surface.toArgb(),
                ),
                // В режиме разницы след гасится: цвет клетки означает
                // направление, а не уровень, и две шкалы под одними красками
                // читались бы как одна.
                points = if (diffOn) emptyList() else drawn.first,
                metric = metric,
                scale = if (diffOn) null else scale,
                lineBreaks = if (diffOn) BooleanArray(0) else drawn.second,
                hotspots = emptyList(),
                bounds = TrackMap.bounds(drawn.first),
                recenterTick = 0,
                onTap = {},
                onTileStats = {},
                modifier = Modifier.fillMaxSize(),
                cells = if (diffOn) diffCells else emptyList(),
                cellMeters = RouteDiff.CELL_METERS,
                cellScale = if (diffOn) DIFF_SCALE else null,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Segmented(
                options = listOf(t.metricDose, t.metricCps),
                selectedIndex = metricIndex,
                onSelect = { metricIndex = it },
                modifier = Modifier.weight(1f),
            )
            // Разница включается только там, где ей есть о чём говорить.
            if (diff != null) {
                Chip(
                    text = h.routeDiff,
                    color = if (showDiff) colors.dataText else colors.ink2,
                    selected = showDiff,
                    onClick = { showDiff = !showDiff },
                )
            }
        }

        diff?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                    Text(
                        text = h.routeDiffSummary(
                            matched = result.matched,
                            higher = result.higher.size,
                            lower = result.lower.size,
                        ),
                        style = type.bodySmall,
                        color = colors.ink,
                    )
                    Text(
                        text = h.routeDiffMethod(
                            cell = TrackGrid.formatCellSize(RouteDiff.CELL_METERS, t),
                            minPoints = RouteDiff.MIN_POINTS_PER_CELL,
                        ),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                routes.forEachIndexed { index, route ->
                    if (index > 0) AppDivider()
                    val visible = route.summary.id !in hidden
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = RouteFormat.title(route.summary, nowMillis, h),
                                style = type.label,
                                color = if (visible) colors.ink else colors.muted,
                            )
                            Text(
                                text = listOfNotNull(
                                    HistoryFormat.duration(
                                        route.summary.durationSeconds,
                                        s = h,
                                    ),
                                    route.summary.distanceMeters
                                        ?.let { TrackMap.formatDistance(it, t) },
                                    route.summary.avgDoseMicroSvH?.let {
                                        "${t.statAvg} ${DoseFormat.rate(it, unit)}"
                                    },
                                    route.summary.maxDoseMicroSvH?.let {
                                        "${t.statMax} ${DoseFormat.rate(it, unit)}"
                                    },
                                ).joinToString(" · "),
                                style = type.footnote,
                                color = colors.ink2,
                            )
                        }
                        // Маршруты часто лежат друг на друге: погасив один,
                        // видно, чем отличается второй.
                        Chip(
                            text = if (visible) strings.on else strings.off,
                            color = if (visible) colors.dataText else colors.ink2,
                            selected = visible,
                            onClick = {
                                hidden = if (visible) {
                                    hidden + route.summary.id
                                } else {
                                    hidden - route.summary.id
                                }
                            },
                        )
                    }
                }
                Hint(text = h.routeCompareCaveat, style = type.footnote, color = colors.muted)
            }
        }
    }
}

/**
 * Сколько точек берётся от каждого маршрута.
 *
 * Меньше, чем у одиночного следа: сравнивают обычно несколько прогулок, и
 * общий бюджет отрисовки делится между ними. Числа в строках считаются по
 * полным маршрутам и от прореживания не зависят.
 */
private const val COMPARE_POINTS_PER_ROUTE = 800

/**
 * Шкала клеток разницы: два цвета, граница между ними — ноль и единица.
 * Значение клетки здесь не уровень, а направление, поэтому и ступени всего
 * две: ниже на первом маршруте и выше на нём.
 */
private val DIFF_SCALE = TrackMap.RampScale(
    bounds = listOf(0.5f),
    mode = MapColorScale.ABSOLUTE,
    low = 0f,
    high = 1f,
)
