package app.alpha.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.alpha.ui.theme.Motion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.lerp
import app.alpha.ui.logic.DoseTint
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.alpha.AppGraph
import app.alpha.device.ConnectionState
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.BackgroundCard
import app.alpha.ui.text.BackgroundCardCatalogue
import app.alpha.ui.components.BreathingAura
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.LedMeter
import app.alpha.ui.components.SearchChartSpec
import app.alpha.ui.components.NavigateGaugeSpec
import app.alpha.ui.components.SearchRateChart
import app.alpha.ui.components.SearchWhySheet
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.MetricTile
import app.alpha.ui.components.MetricTileBox
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.components.StatusRow
import app.alpha.ui.feedback.Feedback
import app.alpha.ui.feedback.GeigerClicker
import app.alpha.baseline.BaselineState
import app.alpha.ui.logic.AdaptiveBackground
import app.alpha.ui.logic.BackgroundCheck
import app.alpha.ui.logic.BackgroundRecord
import app.alpha.ui.logic.SearchReference
import app.alpha.ui.logic.SearchReferences
import app.alpha.ui.logic.BackgroundRef
import app.alpha.ui.logic.ClickRate
import app.alpha.ui.logic.EnergyTone
import app.alpha.ui.logic.FeedbackReason
import app.alpha.ui.logic.FeedbackState
import app.alpha.data.DoseUnitSetting
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.LocalBackground
import app.alpha.ui.logic.LocalBackgroundMachine
import app.alpha.ui.logic.NavigateArc
import app.alpha.ui.logic.NavigateScaleState
import app.alpha.ui.logic.NavigateEngine
import app.alpha.ui.logic.NavigateState
import app.alpha.ui.logic.NavigateTrend
import app.alpha.ui.logic.SearchMode
import app.alpha.ui.logic.SearchStillness
import app.alpha.ui.logic.SearchBaseline
import app.alpha.ui.logic.SearchEngine
import app.alpha.ui.logic.SearchFeedbackMode
import app.alpha.ui.logic.SearchLevel
import app.alpha.ui.logic.SearchSpectrumHint
import app.alpha.ui.logic.SearchState
import app.alpha.ui.logic.SearchPulse
import app.alpha.ui.logic.SearchUiState
import app.alpha.ui.logic.SearchUiStates
import app.alpha.ui.logic.VerifyScale
import app.alpha.ui.logic.SearchTone
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.SearchVerdict
import app.alpha.ui.logic.SearchVibro
import app.alpha.ui.logic.SearchWhyInput
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.logic.backgroundBand
import app.alpha.ui.logic.ledLevel
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SearchCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
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

/** A new maximum has to beat the last announced one by this much to buzz. */
private const val PEAK_PULSE_FACTOR = 1.10

/** …and no two event pulses of «Наведение» come closer than this. */
private const val MIN_PULSE_GAP_MILLIS = 2_000L

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
fun SearchScreen(
    graph: AppGraph,
    onOpenSpectrum: () -> Unit = {},
    onOpenFingerprint: () -> Unit = {},
    /** Тап по ленте: та же скорость счёта во весь экран. */
    onOpenChart: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    // Та же настройка, что у блоков Главной: «статистика под графиком» — одно
    // решение на всё приложение.
    val showStats by graph.settings.monitorBlocks
        .collectAsState(initial = app.alpha.data.MonitorBlocks())
    val backgroundCard = BackgroundCardCatalogue.of(strings.language)
    val t = SearchCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Живое показание берётся из памяти службы, а не из базы — тем же путём,
    // что на Главной. Строку в базе может отбросить уникальный индекс, а
    // запись может быть выключена вовсе, и тогда экран показывал «ждём данные
    // прибора» при исправно идущем потоке.
    val sample by graph.serviceStatus.lastSample.collectAsState()
    val background by graph.searchBackground.collectAsState(initial = null)
    val activeProfileId by graph.contextHub.activeProfileId.collectAsState()
    val feedbackModeId by graph.settings.searchFeedbackMode.collectAsState(initial = null)
    val mode = SearchFeedbackMode.of(feedbackModeId) ?: SearchFeedbackMode.OFF
    val soundFlavour by graph.settings.searchSoundFlavour.collectAsState(initial = "clicks")
    val energyToneEnabled by graph.settings.searchEnergyToneEnabled.collectAsState(initial = false)
    val connection by graph.serviceStatus.connection.collectAsState()
    val doseUnit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    // Два режима — два вопроса: «Наведение» отвечает «куда вести прибор
    // сейчас», «Проверка» — «держится ли превышение над записанным фоном».
    // Выбор запоминается.
    val modeId by graph.settings.searchMode.collectAsState(initial = null)
    val screenMode = SearchMode.of(modeId)
    // Вид индикатора «Наведения» — стрелка или прямая шкала.
    // Ровный счёт как повод предложить проверку: состояние живёт между
    // кадрами, потому что «держится восемь секунд» — утверждение о прошлом.
    var stillness by remember { mutableStateOf(SearchStillness.State()) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val spot by graph.spotMeasure.state.collectAsState()

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
    val doseTint by graph.settings.doseTint.collectAsState(initial = true)
    val tintFactor by graph.settings.doseTintFactor
        .collectAsState(initial = DoseTint.DEFAULT_FACTOR)
    // «Наведение» держит состояние в графе, а не в композиции: точку отсчёта
    // ставит человек, и уход на другую вкладку её не отменяет.
    val navigateSession = graph.navigateSession
    var navigate by navigateSession::state

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
    // Пока Поиск на экране, его измерения — эксперимент и не учат обычный фон
    // места (спец §18).
    DisposableEffect(resumed) {
        if (resumed) graph.searchPresenceHub.attach()
        onDispose { if (resumed) graph.searchPresenceHub.detach() }
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
        if (s.deviceTimestampMillis == lastSeenTimestamp) return@LaunchedEffect
        lastSeenTimestamp = s.deviceTimestampMillis
        lastSampleReceivedAt = s.receivedAtMillis
        deviceClockOffset = lastSampleReceivedAt - s.deviceTimestampMillis
        search = SearchEngine.onReading(
            state = search,
            timeMillis = s.deviceTimestampMillis,
            cps = s.countRate,
            background = background,
        )
        navigate = NavigateEngine.onReading(
            state = navigate,
            timeMillis = s.deviceTimestampMillis,
            cps = s.countRate,
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
            val instrumentNow = System.currentTimeMillis() - deviceClockOffset
            search = SearchEngine.onTick(
                state = search,
                background = background,
                nowMillis = instrumentNow,
            )
            navigate = NavigateEngine.onTick(state = navigate, nowMillis = instrumentNow)
        }
    }

    // The search tone follows the **ratio of the decision window**, not the
    // raw rate (redesign §7). The engine glides to the target, so a step in
    // the ratio is never a step in the audio.
    // В «Наведении» знаменатель другой — точка отсчёта, а не записанный фон, —
    // и его же несёт шкала дуги.
    // Секундный тик идёт в ОБОИХ режимах: по нему живёт не только наблюдение
    // за неподвижностью, но и признак свежести потока — а «идут ли данные»
    // одинаково важно и в Наведении, и в Проверке.
    LaunchedEffect(search.direction, screenMode, resumed) {
        while (resumed) {
            nowTick = System.currentTimeMillis()
            if (screenMode == SearchMode.NAVIGATE) {
                stillness = SearchStillness.step(stillness, search.direction, nowTick)
            }
            delay(1_000)
        }
    }

    val ratio = if (screenMode == SearchMode.NAVIGATE) {
        navigate.referenceRatio
    } else {
        search.comparison?.ratio
    }
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
    LaunchedEffect(mode, resumed, screenMode) {
        if (mode != SearchFeedbackMode.VIBRO) return@LaunchedEffect
        // В «Наведении» вибро — короткий отклик на события, он живёт в
        // эффекте ниже.
        if (screenMode == SearchMode.NAVIGATE) return@LaunchedEffect
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

    // Вибро «Наведения»: отклик на событие, а не непрерывная дрожь. Событий
    // два — счёт начал расти и найден новый максимум; оба приглушены порогом
    // и паузой.
    var lastTrend by remember { mutableStateOf(NavigateTrend.COLLECTING) }
    var pulsedPeak by remember { mutableStateOf(0.0) }
    var lastPulseAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(navigate.trend, navigate.peak, mode, resumed, screenMode) {
        val trend = navigate.trend
        val peak = navigate.peak?.ratePerSecond ?: 0.0
        val rose = trend == NavigateTrend.RISING && lastTrend == NavigateTrend.NO_CHANGE
        val newPeak = pulsedPeak > 0.0 && peak > pulsedPeak * PEAK_PULSE_FACTOR
        lastTrend = trend
        if (peak > pulsedPeak) pulsedPeak = peak
        val active = resumed &&
            screenMode == SearchMode.NAVIGATE &&
            mode == SearchFeedbackMode.VIBRO
        if (!active || !(rose || newPeak)) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastPulseAt < MIN_PULSE_GAP_MILLIS) return@LaunchedEffect
        if (!Feedback.dndAllowsFeedback(context)) return@LaunchedEffect
        lastPulseAt = now
        Feedback.pulse(context)
    }

    // Keep the display awake only while a background measurement runs AND
    // this screen is in the foreground (45 s countdown), and for the whole
    // time «Наведение» is on screen — the instrument is walked over a surface
    // while the screen is read. Scoped to the screen: released on pause and on
    // leaving the composition.
    val view = LocalView.current
    val keepAwake = resumed &&
        (backgroundRun is LocalBackground.Running || screenMode == SearchMode.NAVIGATE)
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    val cps = sample?.countRate
    // Одно состояние на весь экран: «ждём данные», стрелка, отношение и
    // видимость действия выводятся из него, а не из отдельных условий по месту.
    val searchUi = SearchUiStates.of(
        cps = cps,
        receivedAtMillis = sample?.receivedAtMillis,
        nowMillis = nowTick,
        connected = connection is ConnectionState.Connected,
        navigate = navigate,
    )
    val deviceSerial = (connection as? ConnectionState.Connected)?.info?.serialNumber
    val recorded = background
    val check = recorded?.check(System.currentTimeMillis(), activeProfileId, deviceSerial)
    // Фон, изученный самим приложением: обычный фон места по скорости счёта.
    // Вступает, когда записанного эталона нет или он больше не годится.
    // Эталон он не подменяет: его вес ограничен разбросом самого места
    // (`AdaptiveBackground`).
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val learned = remember(baselineState) {
        AdaptiveBackground.of((baselineState as? BaselineState.Active)?.baseline)
    }
    val reference = SearchReferences.choose(recorded, check, learned)
    val learnedInUse = reference is SearchReference.Learned
    val record = when (reference) {
        is SearchReference.Recorded -> reference.record
        is SearchReference.Learned -> BackgroundRecord(
            window = reference.background.referenceWindow(),
            atMillis = System.currentTimeMillis(),
            targetSamples = reference.background.effectiveExposureSeconds.toInt(),
            profileId = activeProfileId,
            profileName = null,
            deviceSerial = deviceSerial,
        )
        SearchReference.None -> null
    }
    val band = when (reference) {
        is SearchReference.Learned -> reference.background.low..reference.background.high
        else -> recorded?.let { backgroundBand(it) }
    }
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
            headline = SearchVerdict.headline(level, search.direction, record != null, strings),
            explanation = SearchVerdict.explanation(level, search.comparison, strings),
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
        // Названия экрана нет: экран назван во вкладке снизу. Канал отклика
        // выбирается в Настройках → Уведомления и отклик.
        val navigating = screenMode == SearchMode.NAVIGATE
        Segmented(
            options = listOf(t.modeNavigate, t.modeVerify),
            selectedIndex = if (navigating) 0 else 1,
            onSelect = { index ->
                val next = if (index == 0) SearchMode.NAVIGATE else SearchMode.VERIFY
                scope.launch { graph.settings.setSearchMode(next.id) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Выключенный канал назван у самих кнопок. Остальные причины молчания
        // — состояния (нет прибора, нет потока, тихий режим), и они появляются
        // только когда наступили.
        val reason = if (mode == SearchFeedbackMode.OFF) {
            null
        } else {
            FeedbackReason.line(
                FeedbackState(
                    mode = mode,
                    deviceConnected = connection is ConnectionState.Connected,
                    dataFresh = dataFresh,
                    dndBlocked = dndBlocked,
                    audioUnavailable = audioUnavailable,
                    volumeZero = volumeZero,
                    // В «Наведении» знаменатель другой: фраза про записанный
                    // фон говорила бы о другой величине.
                    backgroundRecorded = navigating || record != null,
                    insideBackground = !navigating && SearchTone.frequencyHz(ratio) == null,
                ),
                t,
            )
        }
        val navSilence = when {
            !navigating || mode != SearchFeedbackMode.TONE -> null
            navigate.reference == null -> t.navToneNoReference
            SearchTone.frequencyHz(ratio) == null -> t.navToneAtReference
            else -> null
        }
        (reason ?: navSilence)?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }

        if (navigating) {
            NavigateSection(
                ui = searchUi,
                state = navigate,
                spot = spot,
                nowMillis = System.currentTimeMillis() - deviceClockOffset,
                cps = cps,
                // Доза печатается общим форматом приложения плюс собственная
                // относительная погрешность прибора.
                doseLine = sample?.doseRate?.let { rate ->
                    val value = DoseFormat.rate(rate, doseUnit)
                    Uncertainty.errPercentLabel(sample?.doseRateErr)
                        ?.let { "$value $it" } ?: value
                },
                referenceTime = navigate.reference?.let {
                    timeOfDay(it.atMillis + deviceClockOffset)
                },
                strings = strings,
                t = t,
                onMark = {
                    navigate = NavigateEngine.mark(
                        navigate,
                        System.currentTimeMillis() - deviceClockOffset,
                    )
                },
                onClearMark = { navigate = NavigateEngine.clearMark(navigate) },
                onResetPeak = { navigate = NavigateEngine.resetPeak(navigate) },
                onMeasureHere = { graph.spotMeasure.start(navigate.reference) },
                onCancelMeasure = { graph.spotMeasure.cancel() },
                onDismissMeasure = { graph.spotMeasure.dismiss() },
                onGoToVerify = {
                    graph.spotMeasure.dismiss()
                    scope.launch { graph.settings.setSearchMode(SearchMode.VERIFY.id) }
                },
                // Счёт держится ровно — экран ПРЕДЛАГАЕТ проверку, но не
                // начинает её сам: остановку приложение не видит, а запуск по
                // спокойному сигналу дал бы измерение с предрешённым
                // результатом (`SearchStillness`).
                offerVerify = SearchStillness.offering(stillness, nowTick),
                onOfferAccept = {
                    stillness = SearchStillness.dismiss(stillness)
                    scope.launch { graph.settings.setSearchMode(SearchMode.VERIFY.id) }
                },
                onOfferDismiss = { stillness = SearchStillness.dismiss(stillness) },
            )
            return@Column
        }

        // ---------------------------------------------------------- the answer
        // Кадр шкалы растёт сразу и сжимается с задержкой ([NavigateArc.next]):
        // стрелка, упёртая в конец, врёт, а кадр, дёргающийся вслед за шумом,
        // не даёт прочитать положение.
        var verifyScale by remember { mutableStateOf<NavigateScaleState?>(null) }
        LaunchedEffect(search.comparison) {
            verifyScale = NavigateArc.next(
                verifyScale,
                System.currentTimeMillis(),
                VerifyScale.requiredFactor(
                    ratio = search.comparison?.ratio,
                    low = search.comparison?.ratioLow,
                    high = search.comparison?.ratioHigh,
                ),
            )
        }
        val verifyFactor = verifyScale?.factor ?: NavigateArc.LADDER.first()
        // Цвет числа — отношение к записанному фону: то же правило, что у дозы
        // на Главной (`DoseTint`). Им же красится дыхание: один смысл — один цвет.
        val tintFraction = if (doseTint) DoseTint.of(cps, record?.cps, tintFactor) else null
        val numberTint by animateColorAsState(
            targetValue = when {
                cps == null -> colors.muted
                tintFraction == null -> colors.ink
                tintFraction <= 0f -> colors.ok
                tintFraction < 1f -> lerp(colors.warn, colors.crit, tintFraction)
                else -> colors.crit
            },
            animationSpec = Motion.normal(),
            label = "searchTint",
        )
        Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space4) {
            // Период дыхания считает то же отношение, что и высоту тона
            // ([SearchPulse]); знаменатель здесь — записанный фон места. Глаз и
            // ухо обязаны говорить одно. Измерение при этом не анимируется:
            // дышит подсветка, число меняется шагом.
            BreathingAura(
                live = cps != null,
                tint = numberTint,
                periodMillis = SearchPulse.periodMillis(ratio),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                ) {
                    Text(
                        text = strings.countRate.uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                    )
                    // Разбор открывает и само число: когда счёт держится на уровне
                    // фона, строка вывода пуста, а вопрос «почему так решено»
                    // остаётся.
                    Text(
                        text = cps?.let { Uncertainty.num1(it) } ?: "—",
                        style = type.valueHero.copy(fontSize = 52.sp, lineHeight = 54.sp),
                        color = numberTint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { whyOpen = true },
                            )
                            .padding(horizontal = Dimens.space2),
                    )
                    // Величина названа заголовком экрана, σ разбирается в «Почему»
                    // рядом с окном, по которому посчитана.


                    // Сам вывод открывает разбор.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                        modifier = Modifier
                            .padding(top = Dimens.space2)
                            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { whyOpen = true },
                            )
                            .padding(vertical = Dimens.space1, horizontal = Dimens.space2),
                    ) {
                        // На уровне фона экран молчит — это сказано цветом числа;
                        // любое другое состояние говорит словами.
                        if (level != SearchLevel.BACKGROUND) {
                            StatusRow(
                                text = SearchVerdict.headline(
                                    level,
                                    search.direction,
                                    record != null,
                                    strings,
                                ),
                                color = levelColor,
                            )
                        }
                        // Полный разбор открывается нажатием на сам вывод.
                    }

                    // Та же шкала прибора, что в «Наведении», но знаменатель
                    // другой — записанный фон места, и он назван подписью под
                    // ×1. Интервал нарисован сектором: одна стрелка без него
                    // показывала бы отношение точнее, чем оно измерено. Пока
                    // фона нет, шкала стоит пустой — прибор без показания это
                    // всё ещё прибор.
                    val comparison = search.comparison
                    NavigateIndicator(
                        spec = NavigateGaugeSpec(
                            ratio = comparison?.ratio,
                            peakRatio = null,
                            intervalLow = comparison?.ratioLow?.takeIf { it.isFinite() },
                            intervalHigh = comparison?.ratioHigh?.takeIf { it.isFinite() },
                            factor = verifyFactor,
                            trend = VerifyScale.trend(level),
                            referenceLabel = "1×",
                            lowLabel = "${NavigateArc.factorLabel(1.0 / verifyFactor)}×",
                            highLabel = "${NavigateArc.factorLabel(verifyFactor)}×",
                            referenceCaption = strings.backgroundTag,
                            lowCaption = t.navScaleWeaker,
                            highCaption = t.navScaleStronger,
                        ),
                    )

                    // Полоска показывает НАБОР ПОДТВЕРЖДЕНИЯ, а не уровень: она
                    // отвечает, сколько ещё держать прибор здесь. Отличия нет —
                    // полоски нет.
                    val decision = search.decision
                    if (decision != null && !decision.ready && level != SearchLevel.BACKGROUND) {
                        LedMeter(
                            level = decision.progress,
                            modifier = Modifier.padding(top = Dimens.space3),
                        )
                        Text(
                            text = if (decision.atLimit) {
                                t.decisionTooSmall
                            } else {
                                t.decisionRemaining(decision.remainingSeconds.toInt())
                            },
                            style = type.footnote,
                            color = colors.muted,
                        )
                    }
                    if (record == null) {
                        Hint(
                            text = t.meterNeedsBackground,
                            style = type.footnote,
                            color = colors.muted,
                        )
                    }
                }
            }
        }

        // Лента нажимается целиком и открывает полноэкранный график скорости
        // счёта — тот же, что у дозы: перекрестие, перелистывание, щипок, окна
        // и статистика окна.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenChart,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                // Величину называет ось графика, что такое полоса — справка «i».
                val points = search.points
                if (points.isEmpty()) {
                    Text(
                        text = t.waitingStream,
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
                            // Подписи у пунктира нет: число фона стоит плиткой
                            // над лентой.
                            baselineLabel = null,
                            xStartLabel = t.tapeStartLabel,
                            xEndLabel = strings.nowLabel,
                            excursionLabel = search.comparison
                                ?.takeIf { search.ladder.confirmed }
                                ?.let { SearchVerdict.ratioShort(it) }
                                ?.let { t.excursionLabel(it) },
                        ),
                    )
                    val values = points.map { it.cps }
                    if (showStats.stats) StatGrid(
                        cells = listOf(
                            StatCell(Uncertainty.num1(values.sum() / values.size), t.statMean60),
                            StatCell(Uncertainty.num1(values.max()), t.statMax),
                            StatCell(
                                strings.seconds(SearchEngine.DECISION_WINDOW_MILLIS / 1000),
                                t.statDecisionWindow,
                            ),
                            StatCell(
                                record?.let { timeOfDay(it.atMillis) } ?: "—",
                                t.statBackgroundTaken,
                            ),
                        ),
                    )
                }
            }
        }

        // ------------------------------------------- the spectral side question
        val invitation = SearchSpectrumHint.invitation(shape, t)
        val shapeNote = SearchSpectrumHint.note(shape, t)
        AnimatedVisibility(
            visible = shapeNote != null,
            enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
            exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    if (invitation != null) {
                        StatusRow(text = invitation, color = colors.warn)
                    }
                    // Отказ метода («данных пока мало») и его исход — не
                    // пояснение: без них карточка оставалась пустой.
                    Text(
                        text = shapeNote.orEmpty(),
                        style = type.footnote,
                        color = colors.muted,
                    )
                    if (invitation != null) {
                        AppButton(
                            text = t.openSpectrum,
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
                            text = t.backgroundRunTitle(run.collected, run.target),
                            style = type.value,
                            color = colors.dataText,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = strings.cancel,
                            onClick = { graph.localBackground.cancel() },
                        )
                    }
                    Hint(
                        text = t.backgroundRunNote,
                    )
                }
            }
        } else {
            // Карточка фона: первый уровень — значение, основание и ОДНА
            // строка с названной причиной, если фон непригоден; абзацы
            // объяснений уехали под «i» (ТЗ §10). Модель собирает чистая
            // `SearchBaseline.card`, поэтому состав карточки проверяется тестом.
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                // Пока с фоном всё в порядке, карточка не занимает экран.
                //
                // Она отвечает на вопрос «что не так с фоном и что с этим
                // делать», и когда ответа нет — «фон годится», — целая
                // карточка со значением, датой записи и кнопкой обновления
                // повторяет то, что уже стоит плиткой выше. Остаётся одна
                // тусклая строка: когда записан, — а действие возвращается
                // ровно тогда, когда оно нужно.
                val quiet = (check == null || check == BackgroundCheck.USABLE) &&
                    run !is LocalBackground.Aborted
                if (quiet) {
                    Text(
                        text = if (learnedInUse) {
                            t.backgroundLearnedTag.lowercase()
                        } else {
                            record?.let {
                                backgroundCard.recordedAt(
                                    HistoryFormat.day(it.atMillis),
                                    timeOfDay(it.atMillis),
                                )
                            }.orEmpty()
                        },
                        style = type.footnote,
                        color = colors.muted,
                    )
                } else BackgroundCard(
                    model = SearchBaseline.card(
                        record = record,
                        check = check ?: BackgroundCheck.USABLE,
                        rateText = record?.let { Uncertainty.num1(it.cps) }.orEmpty(),
                        day = record?.let { HistoryFormat.day(it.atMillis) }.orEmpty(),
                        timeOfDay = record?.let { timeOfDay(it.atMillis) }.orEmpty(),
                        targetSeconds = BackgroundRef.DEFAULT_TARGET_SAMPLES,
                        c = backgroundCard,
                        t = t,
                    ),
                    onAction = { graph.localBackground.start() },
                )

                if (run is LocalBackground.Aborted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        Text(
                            text = LocalBackgroundMachine.abortWording(run, t),
                            style = type.bodySmall,
                            color = colors.warn,
                            modifier = Modifier.weight(1f),
                        )
                        Chip(
                            text = t.hide,
                            color = colors.ink2,
                            onClick = { graph.localBackground.dismiss() },
                        )
                    }
                }
            }
        }

        // Отпечаток места живёт здесь, а не на Главной: это тот же вопрос,
        // с которым открывают Поиск — «здесь не так, как обычно?» — только
        // заданный не про сейчас, а про место целиком.
        AppButton(
            text = strings.placeFingerprint,
            onClick = onOpenFingerprint,
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
                    text = strings.energyTone,
                    color = if (energyToneEnabled) colors.dataText else colors.muted,
                    dot = if (energyToneEnabled) colors.data else null,
                    onClick = {
                        scope.launch {
                            graph.settings.setSearchEnergyToneEnabled(!energyToneEnabled)
                        }
                    },
                )
                if (energyToneEnabled) {
                    Hint(
                        text = t.energyToneHint,
                    )
                }
            }
            if (energyToneEnabled) {
                Hint(
                    text = t.energyToneScale,
                )
            }
        }
    }
}


