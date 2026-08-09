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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import app.radiacode.AppGraph
import app.radiacode.ui.components.LedMeter
import app.radiacode.ui.components.PixelBox
import app.radiacode.ui.components.PixelButton
import app.radiacode.ui.components.PixelChart
import app.radiacode.ui.components.PixelChartSpec
import app.radiacode.ui.components.PixelTag
import app.radiacode.ui.components.StatusLine
import app.radiacode.ui.logic.BackgroundRef
import app.radiacode.ui.logic.backgroundBand
import app.radiacode.ui.logic.deltaPercent
import app.radiacode.ui.logic.ledLevel
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens
import kotlinx.coroutines.launch

private const val WINDOW_SECONDS = 60

/**
 * Поиск: full-screen CPS mode (SPEC: answers only "where is the signal
 * stronger"). Giant CPS, local background reference with honest averaging,
 * Poisson ±2σ band on the 60 s chart, LED intensity meter. Dose, spectra and
 * long-term stats deliberately stay off this screen.
 */
@Composable
fun SearchScreen(graph: AppGraph) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val scope = rememberCoroutineScope()

    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val storedBackground by graph.settings.searchBackgroundCps.collectAsState(initial = null)

    // Last 60 s of CPS, fed by the 1 Hz sample flow; survives tab switches
    // only while composed — Search is a live mode, not a log.
    val window = remember { mutableStateOf(listOf<Float>()) }
    var measuring by remember { mutableStateOf<BackgroundRef.Measuring?>(null) }
    var lastSeenTimestamp by remember { mutableStateOf(0L) }

    LaunchedEffect(sample) {
        val s = sample ?: return@LaunchedEffect
        if (s.timestamp == lastSeenTimestamp) return@LaunchedEffect
        lastSeenTimestamp = s.timestamp
        window.value = (window.value + s.countRate).takeLast(WINDOW_SECONDS)
        measuring?.let { active ->
            when (val next = active.onSample(s.countRate)) {
                is BackgroundRef.Ready -> {
                    measuring = null
                    scope.launch { graph.settings.setSearchBackgroundCps(next.cps) }
                }
                is BackgroundRef.Measuring -> measuring = next
                BackgroundRef.None -> measuring = null
            }
        }
    }

    val cps = sample?.countRate
    val background = storedBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelDimens.space4),
        verticalArrangement = Arrangement.spacedBy(PixelDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ПОИСК ИСТОЧНИКА", style = type.heading, color = colors.text)
            Spacer(Modifier.weight(1f))
            PixelTag(text = "CPS-режим")
        }

        PixelBox(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PixelDimens.space2),
            ) {
                val glow = if (colors.isDark) {
                    Shadow(color = colors.accent.copy(alpha = 0.55f), blurRadius = 24f)
                } else {
                    null
                }
                Text(
                    text = cps?.let { "${it.toInt()}" } ?: "—",
                    style = glow?.let { type.valueHuge.copy(shadow = it) } ?: type.valueHuge,
                    color = if (cps != null) colors.accent else colors.textMuted,
                    textAlign = TextAlign.Center,
                )
                Text("CPS", style = type.label, color = colors.textSecondary)

                if (background != null && cps != null) {
                    val delta = deltaPercent(cps, background)
                    Text(
                        text = "фон: ${background.toInt()} CPS · " +
                            (delta?.let { if (it >= 0) "+$it%" else "$it%" } ?: "—"),
                        style = type.value,
                        color = if ((delta ?: 0) > 25) colors.aboveUsual else colors.textSecondary,
                    )
                } else {
                    Text(
                        text = "локальный фон не замерен",
                        style = type.value,
                        color = colors.textMuted,
                    )
                }

                LedMeter(
                    level = if (cps != null) ledLevel(cps, background) else 0f,
                    modifier = Modifier.padding(top = PixelDimens.space2),
                )
                if (background == null) {
                    Text(
                        text = "индикатор заработает после замера фона",
                        style = type.labelSmall,
                        color = colors.textMuted,
                    )
                }
            }
        }

        PixelBox(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                Text("СКОРОСТЬ СЧЁТА · 60 С", style = type.label, color = colors.text)
                val values = window.value
                if (values.isEmpty()) {
                    StatusLine(text = "ждём поток данных", cursor = true, color = colors.textMuted)
                } else {
                    val columns: List<Float?> =
                        List(WINDOW_SECONDS - values.size) { null } + values
                    val dataMax = values.max()
                    val yMax = maxOf(
                        dataMax * 1.25f,
                        background?.let { backgroundBand(it).endInclusive * 1.1f } ?: 0f,
                        1f,
                    )
                    PixelChart(
                        spec = PixelChartSpec(
                            columns = columns,
                            yMax = yMax,
                            band = background?.let { backgroundBand(it) },
                            columnWidthPx = 2,
                            gapPx = 1,
                        ),
                        yMaxLabel = "${yMax.toInt()} CPS",
                        xStartLabel = "-60 с",
                        xEndLabel = "сейчас",
                    )
                    if (background != null) {
                        Text(
                            text = "штриховка — обычные колебания фона (±2σ)",
                            style = type.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }

        PixelBox(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                val active = measuring
                if (active == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
                    ) {
                        PixelButton(
                            text = if (background == null) "ЗАМЕРИТЬ ФОН" else "ПЕРЕЗАМЕРИТЬ ФОН",
                            onClick = { measuring = BackgroundRef.startMeasuring() },
                            primary = background == null,
                        )
                    }
                    Text(
                        text = "Отойдите от предполагаемого источника и держите прибор " +
                            "неподвижно ${BackgroundRef.DEFAULT_TARGET_SAMPLES} секунд — " +
                            "среднее станет точкой сравнения.",
                        style = type.bodySmall,
                        color = colors.textMuted,
                    )
                } else {
                    StatusLine(
                        text = "замер фона · ${active.sampleCount}/${active.targetSamples} с",
                        cursor = true,
                        color = colors.accent,
                    )
                    PixelButton(text = "ОТМЕНА", onClick = { measuring = null })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            PixelButton(text = "ЗВУК", onClick = {}, enabled = false)
            PixelButton(text = "ВИБРО", onClick = {}, enabled = false)
            PixelTag(text = "скоро", modifier = Modifier.align(Alignment.CenterVertically))
        }

        Text(
            text = "Режим поиска отвечает на один вопрос: где сигнал сильнее. " +
                "CPS реагирует на изменения быстрее дозы, но не показывает " +
                "опасность.",
            style = type.bodySmall,
            color = colors.textMuted,
        )
    }
}
