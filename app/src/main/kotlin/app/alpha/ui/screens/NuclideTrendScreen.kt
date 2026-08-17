@file:OptIn(ExperimentalLayoutApi::class)

package app.alpha.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.NuclideTrend
import app.alpha.analysis.RadonTrend
import app.alpha.data.toSpectrum
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.BarChart
import app.alpha.ui.components.BarChartSpec
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.ResultCard
import app.alpha.ui.logic.LineTrendReport
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SessionRadonCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
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
                // Список линий ПЕРЕНОСИТСЯ, а не уезжает вбок. Горизонтальная
                // прокрутка обрезала последние линии по краю экрана, и что
                // список продолжается, было видно только если по нему провести.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space1),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space1),
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
        when {
            series == null -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(text = t.readingSession, style = type.bodySmall, color = colors.muted)
            }
            else -> {
                val span = series.takeIf { it.isNotEmpty() }?.let {
                    spanText(t, it.first().atMillis, it.last().atMillis)
                }
                val summary = NuclideTrend.summary(series)
                ResultCard(
                    result = LineTrendReport.build(
                        line = line,
                        summary = summary,
                        spanText = span,
                        t = t,
                    ),
                ) {
                    if (series.isNotEmpty()) {
                        // Столбики стоят в ЧАСОВОЙ СЕТКЕ, а не подряд: час без
                        // измерений — настоящий пробел, а не сдвинутый сосед.
                        val columns = hourColumns(series)
                        // Ноль на своём месте, отрицательные столбики — вниз.
                        // Прижатый к нулю столбик означал бы «ровно ноль»,
                        // хотя число под ним говорит «−0,02».
                        val high = series.maxOf { it.netCps }
                        val low = series.minOf { it.netCps }
                        BarChart(
                            spec = BarChartSpec(
                                values = columns,
                                yMax = if (high > 0f) high * 1.15f else high * 0.2f + 0.01f,
                                yMin = if (low < 0f) low * 1.15f else 0f,
                                refLine = 0f,
                                xStartLabel = span,
                                xEndLabel = t.now,
                            ),
                            height = 96.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Часовая сетка от первой точки до последней: час без измерений — `null`.
 *
 * Ряд прореживается до одной точки в час, но часы, в которые прибор не писал,
 * в нём просто отсутствуют. Нарисованные подряд, такие точки склеивали разрыв
 * в непрерывную картинку: отсутствие измерения становилось похоже на
 * измерение.
 */
internal fun hourColumns(series: List<NuclideTrend.Point>): List<Float?> {
    if (series.isEmpty()) return emptyList()
    val firstHour = series.first().atMillis / RadonTrend.HOUR_MILLIS
    val lastHour = series.last().atMillis / RadonTrend.HOUR_MILLIS
    val byHour = series.associateBy { it.atMillis / RadonTrend.HOUR_MILLIS }
    val count = (lastHour - firstHour + 1).toInt().coerceAtMost(MAX_COLUMNS)
    return List(count) { i -> byHour[firstHour + i]?.netCps }
}

/**
 * Потолок числа столбиков: за неделю часовых слотов 168, и это ещё рисуемо,
 * но испорченные приборные метки времени способны дать любое число.
 */
private const val MAX_COLUMNS = 400

/**
 * Охват ряда словами: минуты, часы или дни — что человек и назовёт, глядя на
 * тот же промежуток. Единица выбирается по величине, а не по окну: у одних и
 * тех же данных запрос «7 д» и запрос «24 ч» дают ОДИН охват, и в этом весь
 * смысл подписи.
 */
internal fun spanText(
    t: app.alpha.ui.text.SessionRadonStrings,
    fromMillis: Long,
    toMillis: Long,
): String {
    val minutes = ((toMillis - fromMillis).coerceAtLeast(0L) + 30_000L) / 60_000L
    return when {
        minutes < 90L -> t.spanMinutes(minutes.toInt())
        minutes < 48L * 60L -> t.spanHours(((minutes + 30L) / 60L).toInt())
        else -> t.spanDays(((minutes + 12L * 60L) / (24L * 60L)).toInt())
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
