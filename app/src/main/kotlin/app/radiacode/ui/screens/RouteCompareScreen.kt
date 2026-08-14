package app.radiacode.ui.screens

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
import app.radiacode.AppGraph
import app.radiacode.baseline.BaselineState
import app.radiacode.data.DoseUnitSetting
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.DoseTint
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.MapColorScale
import app.radiacode.ui.logic.MapTrackPoint
import app.radiacode.ui.logic.RouteFormat
import app.radiacode.ui.logic.RouteSummary
import app.radiacode.ui.logic.TrackMap
import app.radiacode.ui.logic.TrackMetric
import app.radiacode.ui.map.MapLayerColors
import app.radiacode.ui.map.TrackMapView
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.MapCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import app.radiacode.ui.theme.TrackRampColors
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

    val shown = routes.filter { it.summary.id !in hidden }
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
                    ramp = TrackRampColors.map { it.toArgb() },
                    metricMissing = colors.muted.toArgb(),
                    hotspotFill = colors.crit.toArgb(),
                    hotspotStroke = colors.surface.toArgb(),
                    position = colors.data.toArgb(),
                    positionRing = colors.surface.toArgb(),
                ),
                points = drawn.first,
                metric = metric,
                scale = scale,
                lineBreaks = drawn.second,
                hotspots = emptyList(),
                bounds = TrackMap.bounds(drawn.first),
                recenterTick = 0,
                onTap = {},
                onTileStats = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        Segmented(
            options = listOf(t.metricDose, t.metricCps),
            selectedIndex = metricIndex,
            onSelect = { metricIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )

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
