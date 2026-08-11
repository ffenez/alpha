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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.radiacode.AppGraph
import app.radiacode.device.ConnectionState
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.BarChart
import app.radiacode.ui.components.BarChartSpec
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.LedMeter
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.feedback.Feedback
import app.radiacode.ui.feedback.GeigerClicker
import app.radiacode.ui.logic.BackgroundRef
import app.radiacode.ui.logic.LocalBackground
import app.radiacode.ui.logic.LocalBackgroundMachine
import app.radiacode.ui.logic.ClickRate
import app.radiacode.ui.logic.EnergyTone
import app.radiacode.ui.logic.FeedbackReason
import app.radiacode.ui.logic.FeedbackState
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.VibrationPolicy
import app.radiacode.ui.logic.backgroundBand
import app.radiacode.ui.logic.deltaPercent
import app.radiacode.ui.logic.ledLevel
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WINDOW_SECONDS = 60
private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/** Click feedback goes silent when the 1 Hz stream stops delivering. */
private const val FEEDBACK_STALE_MILLIS = 5_000L

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
    val context = LocalContext.current

    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val storedBackground by graph.settings.searchBackgroundCps.collectAsState(initial = null)
    val soundEnabled by graph.settings.searchSoundEnabled.collectAsState(initial = false)
    val vibrationEnabled by graph.settings.searchVibrationEnabled.collectAsState(initial = false)
    val energyToneEnabled by graph.settings.searchEnergyToneEnabled.collectAsState(initial = false)

    // Last 60 s of CPS, fed by the 1 Hz sample flow; survives tab switches
    // only while composed — Search is a live mode, not a log.
    val window = remember { mutableStateOf(listOf<Float>()) }
    // The 45 s background measurement is owned by the app graph, not by this
    // composable: leaving the tab or the display sleeping must not destroy it.
    val backgroundRun by graph.localBackground.state.collectAsState()
    var lastSeenTimestamp by remember { mutableLongStateOf(0L) }
    // Wall-clock of the last received sample (device timestamps may drift).
    var lastSampleReceivedAt by remember { mutableLongStateOf(0L) }

    // --- Geiger-style feedback: foreground-only, this screen only ---
    val clicker = remember { GeigerClicker(context) }
    // Recreated on background change: σ-steps are relative to the reference.
    val vibrationPolicy = remember(storedBackground) { VibrationPolicy() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Поиск asks the service for a shorter DATA_BUF poll while it is on
    // screen: the same ~1 Hz records, picked up about twice as fast.
    DisposableEffect(resumed) {
        if (resumed) graph.fastPollHub.attach()
        onDispose { if (resumed) graph.fastPollHub.detach() }
    }

    val clickerActive = soundEnabled && resumed
    // Silence must be explainable: these are polled once a second so the
    // screen can name the actual reason instead of just staying quiet.
    val connection by graph.serviceStatus.connection.collectAsState()
    var dndBlocked by remember { mutableStateOf(false) }
    var audioUnavailable by remember { mutableStateOf(false) }
    var volumeZero by remember { mutableStateOf(false) }
    var dataFresh by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        while (resumed) {
            dndBlocked = !Feedback.dndAllowsFeedback(context)
            audioUnavailable = clicker.audioUnavailable
            volumeZero = clicker.volumeZero
            dataFresh = lastSampleReceivedAt > 0 &&
                System.currentTimeMillis() - lastSampleReceivedAt <= FEEDBACK_STALE_MILLIS
            delay(1_000)
        }
    }
    DisposableEffect(clickerActive) {
        if (clickerActive) clicker.start()
        onDispose { clicker.stop() }
    }
    // «Тон по энергии»: needs the 5 s spectrum poll — attach a hub watcher
    // while active, then steer the click pitch from the newest interval
    // slice's mean photon energy; stale/no data honestly falls back to the
    // plain default tick.
    val toneActive = clickerActive && energyToneEnabled
    DisposableEffect(toneActive) {
        if (toneActive) graph.spectrumHub.attach()
        onDispose {
            if (toneActive) graph.spectrumHub.detach()
            clicker.setToneBand(null)
        }
    }
    LaunchedEffect(toneActive) {
        while (toneActive) {
            val slice = graph.spectrogramStore.latest.value
            val band = if (
                slice != null &&
                EnergyTone.isFresh(slice.timestampMillis, System.currentTimeMillis())
            ) {
                EnergyTone.bandForMeanEnergy(slice.meanEnergyKeV)
            } else {
                null
            }
            clicker.setToneBand(band)
            delay(1_000)
        }
    }
    // Honest silence: no fresh samples — no clicks, whatever the last CPS was.
    LaunchedEffect(clickerActive) {
        while (clickerActive) {
            delay(1_000)
            if (System.currentTimeMillis() - lastSampleReceivedAt > FEEDBACK_STALE_MILLIS) {
                clicker.setRate(0f)
            }
        }
    }

    LaunchedEffect(sample) {
        val s = sample ?: return@LaunchedEffect
        if (s.timestamp == lastSeenTimestamp) return@LaunchedEffect
        lastSeenTimestamp = s.timestamp
        lastSampleReceivedAt = System.currentTimeMillis()
        window.value = (window.value + s.countRate).takeLast(WINDOW_SECONDS)
        clicker.setRate(ClickRate.clicksPerSecond(s.countRate))
        if (vibrationEnabled && vibrationPolicy.onSample(s.countRate, storedBackground)) {
            Feedback.pulse(context)
        }
        dataFresh = true
    }

    // Keep the display awake only while a background measurement runs AND
    // this screen is in the foreground: the user is watching a 45 s countdown
    // and should not have to poke the screen. Scoped to the screen — released
    // on pause and on leaving the composition, never app-wide or persistent.
    // (The measurement itself survives the screen going dark; this is only
    // about not making the user fight the display timeout while watching.)
    val view = LocalView.current
    val keepAwake = backgroundRun is LocalBackground.Running && resumed
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Chip(text = "Поиск источника", color = colors.ink)
            Spacer(Modifier.weight(1f))
            // Feature toggles: state is dot + color together, never color alone.
            Chip(
                text = "звук",
                color = if (soundEnabled) colors.dataText else colors.muted,
                dot = if (soundEnabled) colors.data else null,
                onClick = {
                    scope.launch { graph.settings.setSearchSoundEnabled(!soundEnabled) }
                },
            )
            Chip(
                text = "вибро",
                color = if (vibrationEnabled) colors.dataText else colors.muted,
                dot = if (vibrationEnabled) colors.data else null,
                onClick = {
                    scope.launch { graph.settings.setSearchVibrationEnabled(!vibrationEnabled) }
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Chip(
                text = "тон по энергии",
                color = if (energyToneEnabled) colors.dataText else colors.muted,
                dot = if (energyToneEnabled) colors.data else null,
                onClick = {
                    scope.launch { graph.settings.setSearchEnergyToneEnabled(!energyToneEnabled) }
                },
            )
            if (energyToneEnabled) {
                Text(
                    text = if (soundEnabled) {
                        "клик выше при жёстких гамма — 3 ступени по среднему кэВ"
                    } else {
                        "заработает вместе со «звук»"
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
        if (energyToneEnabled && soundEnabled) {
            Text(
                text = "тон: <300 кэВ — ниже · 300–1000 — обычный · >1000 — выше; " +
                    "по среднему кэВ спектра за 5 с, без потока спектра — обычные клики",
                style = type.footnote,
                color = colors.muted,
            )
        }

        val reason = FeedbackReason.line(
            FeedbackState(
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                deviceConnected = connection is ConnectionState.Connected,
                dataFresh = dataFresh,
                dndBlocked = dndBlocked,
                audioUnavailable = audioUnavailable,
                volumeZero = volumeZero,
                backgroundRecorded = storedBackground != null,
            ),
        )
        if (reason != null) {
            Text(text = reason, style = type.footnote, color = colors.muted)
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
                        text = if (vibrationEnabled) {
                            "индикатор и вибро-пульсы заработают после замера фона"
                        } else {
                            "индикатор заработает после замера фона"
                        },
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
                                (backgroundRun as? LocalBackground.Done)?.let { timeOfDay(it.atMillis) }
                                    ?: if (background != null) "ранее" else "—",
                                "фон записан",
                            ),
                            StatCell("60 с", "усреднение"),
                        ),
                    )
                }
            }
        }

        val run = backgroundRun
        if (run is LocalBackground.Running) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(
                    text = "замер фона · ${run.collected}/${run.target} с",
                    style = type.value,
                    color = colors.dataText,
                    modifier = Modifier.weight(1f),
                )
                AppButton(text = "Отмена", onClick = { graph.localBackground.cancel() })
            }
            Text(
                text = "замер продолжается на других вкладках и при погасшем экране — " +
                    "результат будет здесь",
                style = type.footnote,
                color = colors.muted,
            )
        } else {
            AppButton(
                text = if (background == null) {
                    "Записать локальный фон · ${BackgroundRef.DEFAULT_TARGET_SAMPLES} с"
                } else {
                    "Перезамерить фон · ${BackgroundRef.DEFAULT_TARGET_SAMPLES} с"
                },
                onClick = { graph.localBackground.start() },
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (run is LocalBackground.Aborted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Text(
                        text = LocalBackgroundMachine.abortWording(run),
                        style = type.bodySmall,
                        color = colors.warn,
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        text = "скрыть",
                        color = colors.ink2,
                        onClick = { graph.localBackground.dismiss() },
                    )
                }
            } else {
                Text(
                    text = "Отойдите от предполагаемого источника и держите прибор " +
                        "неподвижно ${BackgroundRef.DEFAULT_TARGET_SAMPLES} секунд — " +
                        "среднее станет точкой сравнения.",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
        }

        Text(
            text = "CPS реагирует быстрее дозы — ведите прибор вдоль поверхности. " +
                "Пока открыт этот экран, показания забираются чаще: они приходят " +
                "с меньшей задержкой, но сам прибор измеряет раз в секунду.",
            style = type.footnote,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HH_MM)
