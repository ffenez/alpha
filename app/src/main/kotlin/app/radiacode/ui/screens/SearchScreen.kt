package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiacode.AppGraph
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.LedMeter
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.BackgroundRef
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.backgroundBand
import app.radiacode.ui.logic.deltaPercent
import app.radiacode.ui.logic.ledLevel
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val WINDOW_SECONDS = 60
private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Поиск: full-screen CPS mode (SPEC: answers only "where is the signal
 * stronger"). Big CPS with its Poisson 1σ, local background reference with
 * honest averaging and age, the 60 s tape with the фон ±2σ band so a
 * statistically significant excess is visible, thin intensity bar. Dose,
 * spectra and long-term stats deliberately stay off this screen.
 */
@Composable
fun SearchScreen(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val storedBackground by graph.settings.searchBackgroundCps.collectAsState(initial = null)

    // Last 60 s of CPS, fed by the 1 Hz sample flow; survives tab switches
    // only while composed — Search is a live mode, not a log.
    val window = remember { mutableStateOf(listOf<Float>()) }
    var measuring by remember { mutableStateOf<BackgroundRef.Measuring?>(null) }
    var lastSeenTimestamp by remember { mutableLongStateOf(0L) }
    // Wall-clock of the last completed background measurement in this
    // session; the stored reference itself carries no timestamp.
    var backgroundRecordedAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(sample) {
        val s = sample ?: return@LaunchedEffect
        if (s.timestamp == lastSeenTimestamp) return@LaunchedEffect
        lastSeenTimestamp = s.timestamp
        window.value = (window.value + s.countRate).takeLast(WINDOW_SECONDS)
        measuring?.let { active ->
            when (val next = active.onSample(s.countRate)) {
                is BackgroundRef.Ready -> {
                    measuring = null
                    backgroundRecordedAt = System.currentTimeMillis()
                    scope.launch { graph.settings.setSearchBackgroundCps(next.cps) }
                }
                is BackgroundRef.Measuring -> measuring = next
                BackgroundRef.None -> measuring = null
            }
        }
    }

    val cps = sample?.countRate
    val background = storedBackground
    val band = background?.let { backgroundBand(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(text = "Поиск источника", color = colors.ink)
            Spacer(Modifier.weight(1f))
            Chip(text = "звук · вибро · скоро", color = colors.muted)
        }

        Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space4) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            ) {
                Text(
                    text = "Скорость счёта".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Text(
                    text = cps?.let { Uncertainty.num1(it) } ?: "—",
                    style = type.valueHero.copy(fontSize = 52.sp, lineHeight = 54.sp),
                    color = if (cps != null) colors.ink else colors.muted,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = cps?.let { Uncertainty.cpsSigmaLine(it) } ?: "с⁻¹",
                    style = type.footnote,
                    color = colors.ink2,
                )

                if (background != null && cps != null) {
                    val delta = deltaPercent(cps, background)
                    val significant = band != null && cps > band.endInclusive
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = Dimens.space2),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("фон", style = type.bodySmall, color = colors.ink2)
                            Text(
                                text = Uncertainty.num1(background),
                                style = type.value,
                                color = colors.ink,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "к фону",
                                style = type.bodySmall,
                                color = if (significant) colors.warn else colors.ink2,
                            )
                            Text(
                                text = delta?.let { if (it >= 0) "+$it%" else "−${-it}%" } ?: "—",
                                style = type.value,
                                color = if (significant) colors.warn else colors.ink,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "локальный фон не замерен",
                        style = type.bodySmall,
                        color = colors.muted,
                        modifier = Modifier.padding(top = Dimens.space2),
                    )
                }

                LedMeter(
                    level = if (cps != null) ledLevel(cps, background) else 0f,
                    modifier = Modifier.padding(top = Dimens.space3),
                )
                if (background == null) {
                    Text(
                        text = "индикатор заработает после замера фона",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Последние 60 с".uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                    )
                    Spacer(Modifier.weight(1f))
                    if (band != null) {
                        Text(
                            text = "полоса: фон ±2σ",
                            style = type.footnote,
                            color = colors.muted,
                        )
                    }
                }
                val values = window.value
                if (values.isEmpty()) {
                    Text(
                        text = "ждём поток данных…",
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                } else {
                    val columns: List<Float?> =
                        List(WINDOW_SECONDS - values.size) { null } + values
                    val dataMax = values.max()
                    val yMax = maxOf(
                        dataMax * 1.25f,
                        band?.let { it.endInclusive * 1.1f } ?: 0f,
                        1f,
                    )
                    BarChart(
                        spec = BarChartSpec(
                            values = columns,
                            yMax = yMax,
                            band = band,
                            refLine = background,
                            dimAtOrBelow = band?.endInclusive,
                            xStartLabel = "−60 с",
                            xEndLabel = "сейчас",
                        ),
                    )
                    StatGrid(
                        cells = listOf(
                            StatCell(Uncertainty.num1(values.sum() / values.size), "ср 60с"),
                            StatCell(Uncertainty.num1(dataMax), "макс"),
                            StatCell(
                                backgroundRecordedAt?.let { timeOfDay(it) }
                                    ?: if (background != null) "ранее" else "—",
                                "фон записан",
                            ),
                            StatCell("60 с", "усреднение"),
                        ),
                    )
                }
            }
        }

        val active = measuring
        if (active == null) {
            AppButton(
                text = if (background == null) {
                    "Записать локальный фон · ${BackgroundRef.DEFAULT_TARGET_SAMPLES} с"
                } else {
                    "Перезамерить фон · ${BackgroundRef.DEFAULT_TARGET_SAMPLES} с"
                },
                onClick = { measuring = BackgroundRef.startMeasuring() },
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Отойдите от предполагаемого источника и держите прибор " +
                    "неподвижно ${BackgroundRef.DEFAULT_TARGET_SAMPLES} секунд — " +
                    "среднее станет точкой сравнения.",
                style = type.bodySmall,
                color = colors.muted,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(
                    text = "замер фона · ${active.sampleCount}/${active.targetSamples} с",
                    style = type.value,
                    color = colors.dataText,
                    modifier = Modifier.weight(1f),
                )
                AppButton(text = "Отмена", onClick = { measuring = null })
            }
        }

        Text(
            text = "CPS реагирует быстрее дозы — ведите прибор вдоль поверхности",
            style = type.footnote,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HH_MM)
