package app.radiacode.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.NuclideTrend
import app.radiacode.analysis.RadonTrend
import app.radiacode.data.toSpectrum
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SessionRadonCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Ряд нетто-счёта в окне линии выбранного нуклида.
 *
 * ## Зачем экран
 *
 * Полная скорость счёта не говорит, ЧЕМ вызван подъём. Окно вокруг конкретной
 * линии отвечает на другой вопрос — сколько событий приходится на энергию,
 * характерную для этого нуклида, сверх подпирающего континуума. По такому ряду
 * видно, где цезия больше, чем рядом, и растёт ли радон к утру.
 *
 * Радоновый экран — частный случай этого же расчёта с зашитыми линиями; он
 * оставлен как есть, потому что там к ряду прилагается своя интерпретация
 * (суточный ход, проветривание).
 *
 * ## Что экран не утверждает
 *
 * Ни активности, ни концентрации: без измеренной кривой эффективности перевод
 * в беккерели невозможен. Сравнивать можно место с местом и время со временем
 * ОДНИМ прибором. И окно не принадлежит нуклиду — в нём считается всё, что
 * попало в энергию, поэтому это индикатор ЛИНИИ, а не доказательство
 * присутствия: доказательствами занимается движок на экране Спектра.
 */
@Composable
fun NuclideTrendScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SessionRadonCatalogue.of(strings.language)

    BackHandler { onBack() }

    var lineIndex by rememberSaveable { mutableIntStateOf(0) }
    var days by rememberSaveable { mutableIntStateOf(1) }
    val line = NuclideTrend.OFFERED[lineIndex]
    var points by remember { mutableStateOf<List<NuclideTrend.Point>?>(null) }

    // Пересчёт ведут сами данные: новый приборный снимок — единственный повод
    // для нового ряда. Таймер остаётся редкой страховкой от незамеченного
    // сигнала таблицы.
    LaunchedEffect(lineIndex, days) {
        graph.measurementRepository.deviceSnapshotsChanged().collectLatest {
            points = loadLineTrend(graph, line, days)
        }
    }
    LaunchedEffect(lineIndex, days) {
        while (true) {
            delay(FALLBACK_REFRESH_MILLIS)
            points = loadLineTrend(graph, line, days)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = "← ${strings.back}", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(
                text = if (days == 1) t.window24h else t.window7d,
                color = colors.ink2,
                onClick = { days = if (days == 1) 7 else 1 },
            )
        }

        // Выбор линии, а не нуклида: у Tl-208 их две, и они дают разные ряды —
        // 2614,5 кэВ виден на любом фоне, 583,2 тонет в соседях.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.lineTrendTitle.uppercase(), style = type.labelSmall, color = colors.ink2)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                ) {
                    NuclideTrend.OFFERED.forEachIndexed { index, offered ->
                        Chip(
                            text = offered.label,
                            color = if (index == lineIndex) colors.dataText else colors.ink2,
                            selected = index == lineIndex,
                            onClick = { lineIndex = index },
                        )
                    }
                }
            }
        }

        val series = points
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                when {
                    series == null -> Text(
                        text = t.readingSession,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                    series.isEmpty() -> Text(
                        text = t.noMeasurementsInWindow,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                    else -> {
                        val max = series.maxOf { it.netCps + it.sigmaCps }
                        BarChart(
                            spec = BarChartSpec(
                                values = series.map { it.netCps },
                                yMax = if (max > 0f) max * 1.15f else 1f,
                                refLine = 0f,
                                xStartLabel = if (days == 1) t.window24h else t.window7d,
                                xEndLabel = t.now,
                            ),
                            height = 96.dp,
                        )
                        val summary = NuclideTrend.summary(series)
                        if (summary != null) {
                            StatGrid(
                                cells = listOf(
                                    StatCell(
                                        Uncertainty.num1(summary.netCps) + " ±" +
                                            Uncertainty.num1(summary.sigmaCps),
                                        t.lineNetRate,
                                    ),
                                    StatCell(
                                        Uncertainty.num1(summary.significance),
                                        t.lineSignificance,
                                    ),
                                    StatCell("${summary.points}", t.linePoints),
                                ),
                            )
                            // Вердикт о том, выделяется ли линия ВООБЩЕ: без
                            // него положительное среднее при σ того же порядка
                            // читалось бы как находка.
                            Text(
                                text = if (summary.resolved) {
                                    t.lineResolved
                                } else {
                                    t.lineNotResolved
                                },
                                style = type.bodySmall,
                                color = if (summary.resolved) colors.ink2 else colors.muted,
                            )
                        }
                    }
                }
            }
        }

        Text(text = t.lineTrendCaveat, style = type.footnote, color = colors.muted)
    }
}

/** Ряд строится по тем же прореженным до часа снимкам, что и радоновый. */
private suspend fun loadLineTrend(
    graph: AppGraph,
    line: NuclideTrend.Line,
    days: Int,
): List<NuclideTrend.Point> {
    val now = System.currentTimeMillis()
    val from = now - days * 24L * RadonTrend.HOUR_MILLIS
    val metas = graph.measurementRepository
        .deviceSnapshotMeta(from - RadonTrend.HOUR_MILLIS, now)
        .map { RadonTrend.Meta(it.id, it.timestamp, it.durationSeconds) }
    val snapshots = RadonTrend.selectHourlyIds(metas).mapNotNull { id ->
        graph.measurementRepository.spectrumById(id)?.let { entity ->
            val spectrum = entity.toSpectrum()
            NuclideTrend.Snapshot(
                atMillis = entity.timestamp,
                durationSeconds = spectrum.durationSeconds,
                counts = spectrum.counts,
                calibration = EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2),
            )
        }
    }
    return NuclideTrend.series(snapshots, line)
}

/** Страховка на случай незамеченного сигнала таблицы; основной повод — снимок. */
private const val FALLBACK_REFRESH_MILLIS = 5L * 60_000L
