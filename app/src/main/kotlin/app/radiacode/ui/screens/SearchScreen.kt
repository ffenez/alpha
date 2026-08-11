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
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.LedMeter
import app.radiacode.ui.components.SearchChartSpec
import app.radiacode.ui.components.SearchRateChart
import app.radiacode.ui.components.SearchWhySheet
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.StatusRow
import app.radiacode.ui.feedback.Feedback
import app.radiacode.ui.feedback.GeigerClicker
import app.radiacode.ui.logic.BackgroundCheck
import app.radiacode.ui.logic.BackgroundRef
import app.radiacode.ui.logic.ClickRate
import app.radiacode.ui.logic.EnergyTone
import app.radiacode.ui.logic.FeedbackReason
import app.radiacode.ui.logic.FeedbackState
import app.radiacode.ui.logic.LocalBackground
import app.radiacode.ui.logic.LocalBackgroundMachine
import app.radiacode.ui.logic.SearchBaseline
import app.radiacode.ui.logic.SearchEngine
import app.radiacode.ui.logic.SearchFeedbackMode
import app.radiacode.ui.logic.SearchLevel
import app.radiacode.ui.logic.SearchSpectrumHint
import app.radiacode.ui.logic.SearchState
import app.radiacode.ui.logic.SearchTone
import app.radiacode.ui.logic.SearchVerdict
import app.radiacode.ui.logic.SearchVibro
import app.radiacode.ui.logic.SearchWhyInput
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.backgroundBand
import app.radiacode.ui.logic.ledLevel
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/** Click feedback goes silent when the 1 Hz stream stops delivering. */
private const val FEEDBACK_STALE_MILLIS = 5_000L

/** How often the verdict is re-evaluated without a new reading. */
private const val TICK_MILLIS = 500L

/**
 * Поиск: a localisation instrument, not a dosimeter screen with a decorative
 * chart (search redesign).
 *
 * The chain is «скорость счёта → сравнение с записанным фоном → статистическая
 * уверенность → направление изменения», and every step of it lives in
 * [SearchEngine], not here: this file only feeds readings in and draws what
 * comes out. That is what keeps the redesign's release criterion (§14) true —
 * how often Compose recomposes cannot change the conclusion.
 *
 * Dose, spectra and long-term statistics deliberately stay off this screen.
 */
@Composable
fun SearchScreen(graph: AppGraph, onOpenSpectrum: () -> Unit = {}) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val background by graph.searchBackground.collectAsState(initial = null)
    val activeProfileId by graph.contextHub.activeProfileId.collectAsState()
    val feedbackModeId by graph.settings.searchFeedbackMode.collectAsState(initial = null)
    val mode = SearchFeedbackMode.of(feedbackModeId) ?: SearchFeedbackMode.OFF
    val soundFlavour by graph.settings.searchSoundFlavour.collectAsState(initial = "clicks")
    val energyToneEnabled by graph.settings.searchEnergyToneEnabled.collectAsState(initial = false)
    val connection by graph.serviceStatus.connection.collectAsState()

    // The 45 s background measurement is owned by the app graph, not by this
    // composable: leaving the tab or the display sleeping must not destroy it.
    val backgroundRun by graph.localBackground.state.collectAsState()

    var search by remember { mutableStateOf(SearchState()) }
    var lastSeenTimestamp by remember { mutableLongStateOf(0L) }
    // Wall-clock of the last received sample (device timestamps may drift).
    var lastSampleReceivedAt by remember { mutableLongStateOf(0L) }
    // Windows are built in the instrument's time base; between readings the
    // phone clock is the only thing that moves, so the offset between the two
    // clocks is what turns «сейчас» into that base. Recomputed on every reading.
    var deviceClockOffset by remember { mutableLongStateOf(0L) }
    var whyOpen by remember { mutableStateOf(false) }

    // --- Geiger-style feedback: foreground-only, this screen only ---
    val clicker = remember { GeigerClicker(context) }
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

    val soundMode = mode == SearchFeedbackMode.CLICKS || mode == SearchFeedbackMode.TONE
    val clickerActive = soundMode && resumed
    // Silence must be explainable: these are polled once a second so the
    // screen can name the actual reason instead of just staying quiet.
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
    // plain default tick. It pitches the *clicks*, so it never runs together
    // with the search tone, whose pitch already carries the ratio.
    val toneActive = clickerActive && mode == SearchFeedbackMode.CLICKS && energyToneEnabled
    // The 5 s spectrum poll stays attached for the whole time Поиск is on
    // screen, not only for «тон по энергии»: the spectral-shape question of
    // §13 needs the *minutes before* an excursion, which cannot be collected
    // retroactively once one starts. It costs one spectrum request every 5 s
    // while this screen is in the foreground, and nothing when it is not.
    DisposableEffect(resumed) {
        if (resumed) graph.spectrumHub.attach()
        onDispose { if (resumed) graph.spectrumHub.detach() }
    }
    val spectrumSlices by graph.spectrogramStore.slices.collectAsState()
    LaunchedEffect(toneActive) {
        if (!toneActive) {
            clicker.setToneBand(null)
            return@LaunchedEffect
        }
        while (true) {
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

    LaunchedEffect(sample, background) {
        val s = sample ?: return@LaunchedEffect
        if (s.timestamp == lastSeenTimestamp) return@LaunchedEffect
        lastSeenTimestamp = s.timestamp
        lastSampleReceivedAt = System.currentTimeMillis()
        deviceClockOffset = lastSampleReceivedAt - s.timestamp
        search = SearchEngine.onReading(
            state = search,
            timeMillis = s.timestamp,
            cps = s.countRate,
            background = background,
        )
        clicker.setRate(ClickRate.clicksPerSecond(s.countRate))
        dataFresh = true
    }

    // Between readings the verdict must still be able to *expire*: a stream
    // that stops leaves the decision window empty, and the ladder falls back
    // to «ждём данные» instead of freezing on the last conclusion.
    LaunchedEffect(resumed, background) {
        while (resumed) {
            delay(TICK_MILLIS)
            search = SearchEngine.onTick(
                state = search,
                background = background,
                nowMillis = System.currentTimeMillis() - deviceClockOffset,
            )
        }
    }

    // The search tone follows the **ratio of the decision window**, not the
    // raw rate: that is what makes it a signal a person can walk towards
    // instead of a stream of chatter (redesign §7). The engine glides to the
    // target, so a step in the ratio is never a step in the audio.
    val ratio = search.comparison?.ratio
    LaunchedEffect(mode, ratio, clickerActive) {
        clicker.setSearchTone(
            enabled = clickerActive && mode == SearchFeedbackMode.TONE,
            targetHz = SearchTone.frequencyHz(ratio),
        )
    }

    // The silent equivalent: pulse cadence carries the same ratio, so the
    // instrument can be searched with the phone in a pocket. It repeats on
    // purpose — «холодно/горячо» is the whole point — unlike the σ-step
    // policy, which fires once per newly reached step.
    LaunchedEffect(mode, resumed) {
        if (mode != SearchFeedbackMode.VIBRO) return@LaunchedEffect
        // Do-Not-Disturb is polled once a second, not once per pulse: at the
        // fastest cadence the pulses are 120 ms apart, and asking the system
        // eight times a second for an answer that changes once an hour is
        // work for nothing.
        var dndCheckedAt = 0L
        var dndAllows = true
        while (resumed) {
            val now = System.currentTimeMillis()
            if (now - dndCheckedAt >= 1_000L) {
                dndCheckedAt = now
                dndAllows = Feedback.dndAllowsFeedback(context)
            }
            val interval = SearchVibro.intervalMillis(search.comparison?.ratio)
            if (interval == null || !dndAllows) {
                delay(SearchVibro.SLOW_INTERVAL_MILLIS / 2)
                continue
            }
            Feedback.pulse(context)
            delay(interval)
        }
    }

    // Keep the display awake only while a background measurement runs AND
    // this screen is in the foreground: the user is watching a 45 s countdown
    // and should not have to poke the screen. Scoped to the screen — released
    // on pause and on leaving the composition, never app-wide or persistent.
    val view = LocalView.current
    val keepAwake = backgroundRun is LocalBackground.Running && resumed
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    val cps = sample?.countRate
    val record = background
    val deviceSerial = (connection as? ConnectionState.Connected)?.info?.serialNumber
    val check = record?.check(System.currentTimeMillis(), activeProfileId, deviceSerial)
    val band = record?.let { backgroundBand(it) }
    val level = search.level
    // Shape of the spectrum during the excursion vs the two minutes before it
    // — a separate research observation, never part of the count verdict.
    val excursionStartWallClock = search.ladder
        .takeIf { it.confirmed }
        ?.differentSinceMillis
        ?.plus(deviceClockOffset)
    val shape = remember(excursionStartWallClock, spectrumSlices) {
        SearchSpectrumHint.compare(
            slices = spectrumSlices,
            excursionStartMillis = excursionStartWallClock,
            nowMillis = System.currentTimeMillis(),
        )
    }
    val levelColor = when (level) {
        SearchLevel.UNKNOWN -> colors.muted
        SearchLevel.BACKGROUND -> colors.ok
        SearchLevel.POSSIBLE_CHANGE -> colors.ink2
        SearchLevel.CONFIRMED_EXCESS -> colors.warn
        SearchLevel.CONFIRMED_DEFICIT -> colors.ink2
    }

    if (whyOpen) {
        SearchWhySheet(
            input = SearchWhyInput(
                cps = cps,
                background = record,
                comparison = search.comparison,
                heldMillis = search.ladder.differentSinceMillis?.let {
                    (System.currentTimeMillis() - deviceClockOffset - it).coerceAtLeast(0L)
                },
                streamFresh = search.comparison != null,
            ),
            headline = SearchVerdict.headline(level, search.direction, record != null),
            explanation = SearchVerdict.explanation(level, search.comparison),
            onDismiss = { whyOpen = false },
        )
    }

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
            // Две маленькие кнопки, как и было: экран Поиска включает канал, а
            // не выбирает его — выбор между кликами и тоном живёт в Настройках,
            // и кнопка «звук» возвращает именно то, что там выбрано.
            val soundOn = mode == SearchFeedbackMode.CLICKS || mode == SearchFeedbackMode.TONE
            Chip(
                text = "звук",
                color = if (soundOn) colors.dataText else colors.muted,
                dot = if (soundOn) colors.data else null,
                onClick = {
                    val next = if (soundOn) {
                        SearchFeedbackMode.OFF
                    } else {
                        SearchFeedbackMode.of(soundFlavour) ?: SearchFeedbackMode.CLICKS
                    }
                    scope.launch { graph.settings.setSearchFeedbackMode(next.id) }
                },
            )
            Chip(
                text = "вибро",
                color = if (mode == SearchFeedbackMode.VIBRO) colors.dataText else colors.muted,
                dot = if (mode == SearchFeedbackMode.VIBRO) colors.data else null,
                onClick = {
                    val next = if (mode == SearchFeedbackMode.VIBRO) {
                        SearchFeedbackMode.OFF
                    } else {
                        SearchFeedbackMode.VIBRO
                    }
                    scope.launch { graph.settings.setSearchFeedbackMode(next.id) }
                },
            )
        }
        Text(
            text = when (mode) {
                SearchFeedbackMode.OFF -> "сигнал только на экране · канал выбирается в Настройках"
                SearchFeedbackMode.CLICKS ->
                    "щелчок на каждый зарегистрированный импульс"
                SearchFeedbackMode.TONE ->
                    "тон: выше — дальше от записанного фона" +
                        (SearchTone.pitchLabel(ratio)?.let { " · сейчас $it" } ?: "")
                SearchFeedbackMode.VIBRO ->
                    "чаще пульс — дальше от записанного фона" +
                        (SearchVibro.cadenceLabel(ratio)?.let { " · сейчас $it" } ?: "")
            },
            style = type.footnote,
            color = colors.muted,
        )

        val reason = FeedbackReason.line(
            FeedbackState(
                mode = mode,
                deviceConnected = connection is ConnectionState.Connected,
                dataFresh = dataFresh,
                dndBlocked = dndBlocked,
                audioUnavailable = audioUnavailable,
                volumeZero = volumeZero,
                backgroundRecorded = record != null,
                insideBackground = SearchTone.frequencyHz(ratio) == null,
            ),
        )
        if (reason != null) {
            Text(text = reason, style = type.footnote, color = colors.muted)
        }

        // ---------------------------------------------------------- the answer
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

                if (record != null) {
                    val delta = SearchVerdict.deltaPercent(search.comparison)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = Dimens.space2),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("фон", style = type.bodySmall, color = colors.ink2)
                            Text(
                                text = Uncertainty.num1(record.cps),
                                style = type.value,
                                color = colors.ink,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "к фону", style = type.bodySmall, color = colors.ink2)
                            Text(
                                text = delta?.let { if (it >= 0) "+$it %" else "−${-it} %" } ?: "—",
                                style = type.value,
                                color = if (level == SearchLevel.CONFIRMED_EXCESS) {
                                    colors.warn
                                } else {
                                    colors.ink
                                },
                            )
                        }
                    }
                }

                StatusRow(
                    text = SearchVerdict.headline(level, search.direction, record != null),
                    color = levelColor,
                    modifier = Modifier.padding(top = Dimens.space2),
                )
                Text(
                    text = SearchVerdict.explanation(level, search.comparison),
                    style = type.footnote,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Dimens.space2),
                ) {
                    SearchVerdict.directionLabel(search.direction)?.let {
                        Chip(text = it, color = colors.ink2)
                    }
                    Chip(
                        text = "Почему?",
                        color = colors.dataText,
                        onClick = { whyOpen = true },
                    )
                }

                LedMeter(
                    level = if (cps != null) ledLevel(cps, record?.cps) else 0f,
                    modifier = Modifier.padding(top = Dimens.space3),
                )
                if (record == null) {
                    Text(
                        text = "индикатор заработает после замера фона",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }

        // ----------------------------------------------------------- the tape
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "с⁻¹ · последние 60 секунд".uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                    )
                    Spacer(Modifier.weight(1f))
                    if (band != null) {
                        Text(
                            text = "полоса — ожидаемые колебания фона",
                            style = type.footnote,
                            color = colors.muted,
                        )
                    }
                }
                val points = search.points
                if (points.isEmpty()) {
                    Text(
                        text = "ждём поток данных…",
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                } else {
                    val now = points.last().timeMillis
                    SearchRateChart(
                        spec = SearchChartSpec(
                            points = points,
                            nowMillis = now,
                            spanMillis = SearchEngine.TAPE_MILLIS,
                            yTop = search.scale?.top ?: 10f,
                            band = band,
                            baseline = record?.cps,
                            baselineLabel = record?.let { "фон ${Uncertainty.num1(it.cps)}" },
                            excursionLabel = search.comparison
                                ?.takeIf { search.ladder.confirmed }
                                ?.let { SearchVerdict.ratioShort(it) }
                                ?.let { "устойчиво $it к фону" },
                        ),
                    )
                    val values = points.map { it.cps }
                    StatGrid(
                        cells = listOf(
                            StatCell(Uncertainty.num1(values.sum() / values.size), "ср 60 с"),
                            StatCell(Uncertainty.num1(values.max()), "макс"),
                            StatCell(
                                "${SearchEngine.DECISION_WINDOW_MILLIS / 1000} с",
                                "окно решения",
                            ),
                            StatCell(
                                record?.let { timeOfDay(it.atMillis) } ?: "—",
                                "фон записан",
                            ),
                        ),
                    )
                }
                SearchVerdict.spikeLine(search.ladder.spikes)?.let {
                    Text(text = it, style = type.footnote, color = colors.muted)
                }
            }
        }

        // ------------------------------------------- the spectral side question
        val invitation = SearchSpectrumHint.invitation(shape)
        val shapeNote = SearchSpectrumHint.note(shape)
        if (shapeNote != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    if (invitation != null) {
                        StatusRow(text = invitation, color = colors.warn)
                    }
                    Text(text = shapeNote, style = type.footnote, color = colors.muted)
                    if (invitation != null) {
                        AppButton(
                            text = "Открыть спектр",
                            onClick = onOpenSpectrum,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // ------------------------------------------------------- the reference
        val run = backgroundRun
        if (run is LocalBackground.Running) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
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
                        text = "Отойдите от предполагаемого источника и держите прибор " +
                            "неподвижно. Замер продолжается на других вкладках и при " +
                            "погасшем экране — результат будет здесь.",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    if (record != null && check != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Фон ${Uncertainty.num1(record.cps)} с⁻¹ · записан " +
                                    timeOfDay(record.atMillis),
                                style = type.bodySmall,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            Chip(
                                text = SearchBaseline.statusLine(check),
                                color = if (check == BackgroundCheck.USABLE) {
                                    colors.ink2
                                } else {
                                    colors.warn
                                },
                            )
                        }
                        Text(
                            text = "${record.window.samples} показаний · экспозиция " +
                                "${record.window.seconds.toInt()} с · качество: " +
                                record.quality.label +
                                (record.profileName?.let { " · профиль «$it»" } ?: ""),
                            style = type.footnote,
                            color = colors.muted,
                        )
                    } else {
                        Text(
                            text = "Локальный фон не записан",
                            style = type.bodySmall,
                            color = colors.ink,
                        )
                        Text(
                            text = "Отойдите от предполагаемого источника и держите прибор " +
                                "неподвижно ${BackgroundRef.DEFAULT_TARGET_SAMPLES} секунд — " +
                                "среднее станет точкой сравнения.",
                            style = type.footnote,
                            color = colors.muted,
                        )
                    }

                    SearchBaseline.proposal(check ?: BackgroundCheck.USABLE, record)?.let {
                        Text(text = it, style = type.bodySmall, color = colors.warn)
                    }

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
                    }

                    AppButton(
                        text = if (record == null) {
                            "Замерить фон · ${BackgroundRef.DEFAULT_TARGET_SAMPLES} с"
                        } else {
                            "Перезамерить фон · ${BackgroundRef.DEFAULT_TARGET_SAMPLES} с"
                        },
                        onClick = { graph.localBackground.start() },
                        // Primary only while the search cannot run: with a
                        // usable reference the user's job is to walk, not to
                        // press this.
                        primary = record == null || check != BackgroundCheck.USABLE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

        // «Тон по энергии» is a research toggle on top of the *clicks*: it
        // steers their pitch, so it only appears in that mode.
        if (mode == SearchFeedbackMode.CLICKS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Chip(
                    text = "тон по энергии",
                    color = if (energyToneEnabled) colors.dataText else colors.muted,
                    dot = if (energyToneEnabled) colors.data else null,
                    onClick = {
                        scope.launch {
                            graph.settings.setSearchEnergyToneEnabled(!energyToneEnabled)
                        }
                    },
                )
                if (energyToneEnabled) {
                    Text(
                        text = "клик выше при жёстких гамма — 3 ступени по среднему кэВ",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
            if (energyToneEnabled) {
                Text(
                    text = "тон: <300 кэВ — ниже · 300–1000 — обычный · >1000 — выше; " +
                        "по среднему кэВ спектра за 5 с, без потока спектра — обычные клики",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HH_MM)
