package app.radiacode.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.radiacode.ui.theme.Motion
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
import app.radiacode.AppGraph
import app.radiacode.device.ConnectionState
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.BackgroundCard
import app.radiacode.ui.text.BackgroundCardCatalogue
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
import app.radiacode.data.DoseUnitSetting
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.LocalBackground
import app.radiacode.ui.logic.LocalBackgroundMachine
import app.radiacode.ui.logic.NavigateEngine
import app.radiacode.ui.logic.NavigateInfo
import app.radiacode.ui.logic.SearchInfoInput
import app.radiacode.ui.logic.NavigateState
import app.radiacode.ui.logic.NavigateTrend
import app.radiacode.ui.logic.SearchMode
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
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SearchCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val backgroundCard = BackgroundCardCatalogue.of(strings.language)
    val t = SearchCatalogue.of(strings.language)
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
    val doseUnit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    // Два РЕЖИМА — два вопроса, а не «точный» и «быстрый»: «Наведение»
    // отвечает «куда вести прибор сейчас», «Проверка» — «держится ли
    // превышение над записанным фоном». Выбор запоминается.
    val modeId by graph.settings.searchMode.collectAsState(initial = null)
    val screenMode = SearchMode.of(modeId)
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
    // Карточка «i»: свёрнута по умолчанию — она объясняет экран, а не измеряет.
    var infoOpen by remember { mutableStateOf(false) }
    // «Наведение» держит своё состояние здесь, а не внутри секции: переключение
    // режима не должно стирать точку отсчёта и зафиксированный максимум.
    var navigate by remember { mutableStateOf(NavigateState()) }

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
    // Пока Поиск на экране, его измерения — эксперимент и не учат обычный
    // фон места (спец §18). Частота опроса здесь ни при чём: её держит окно
    // приложения целиком.
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
        navigate = NavigateEngine.onReading(
            state = navigate,
            timeMillis = s.timestamp,
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
    // raw rate: that is what makes it a signal a person can walk towards
    // instead of a stream of chatter (redesign §7). The engine glides to the
    // target, so a step in the ratio is never a step in the audio.
    // В «Наведении» знаменатель другой — точка отсчёта, а не записанный фон, —
    // и именно его несёт шкала дуги: глаз и ухо обязаны говорить одно и то же.
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
        // В «Наведении» непрерывная дрожь неуместна: там вибро — короткий
        // отклик на СОБЫТИЯ, и он живёт в эффекте ниже.
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

    // Вибро «Наведения»: короткий отклик на СОБЫТИЕ, а не непрерывная дрожь.
    // Событий ровно два — счёт начал расти и найден новый максимум, — и оба
    // приглушены порогом и паузой: телефон, дрожащий каждую секунду, перестаёт
    // что-либо сообщать.
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
    // this screen is in the foreground: the user is watching a 45 s countdown
    // and should not have to poke the screen. Scoped to the screen — released
    // on pause and on leaving the composition, never app-wide or persistent.
    // …and for the whole time «Наведение» is on screen: that mode is read while
    // the instrument is being walked over a surface, and a display that sleeps
    // mid-sweep is a display that has to be woken with the other hand.
    val view = LocalView.current
    val keepAwake = resumed &&
        (backgroundRun is LocalBackground.Running || screenMode == SearchMode.NAVIGATE)
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
        // Плашки с названием экрана нет: экран назван во вкладке снизу, и
        // повторять это сверху незачем. Кнопки звука и вибрации тоже убраны —
        // канал отклика выбирается и включается в Настройках → Уведомления и
        // отклик, а на рабочем экране они занимали место и требовали подписи
        // о том, что именно включено.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Spacer(Modifier.weight(1f))
            Chip(text = t.infoChip, color = colors.ink2, onClick = { infoOpen = !infoOpen })
        }
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
        // Вопрос режима, канал отклика и техника окон — под «i». Три постоянных
        // пояснения над первым числом стоили экрану весь первый viewport, а
        // измерение начиналось ниже него.
        AnimatedVisibility(
            visible = infoOpen,
            enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
            exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
        ) {
            SearchInfoCard(
                rows = NavigateInfo.rows(
                    input = SearchInfoInput(
                        navigating = navigating,
                        feedback = mode,
                        fastSeconds = navigate.fast?.seconds,
                        localSeconds = navigate.local?.seconds,
                        bandLevelPercent = navigate.referenceComparison
                            ?.takeIf { it.ratioLow.isFinite() && it.ratioHigh.isFinite() }
                            ?.let { (it.confidenceLevel * 100).roundToInt() },
                        channelNow = when {
                            mode == SearchFeedbackMode.TONE -> SearchTone.pitchLabel(ratio, t)
                            // В «Наведении» вибро отвечает на события, поэтому
                            // каденции у него нет и обещать её нельзя.
                            mode == SearchFeedbackMode.VIBRO && !navigating ->
                                SearchVibro.cadenceLabel(ratio, t)

                            else -> null
                        },
                    ),
                    strings = strings,
                    t = t,
                ),
                title = t.infoTitle,
                closeText = t.hide,
                onClose = { infoOpen = false },
            )
        }

        // Выключенный канал уже назван приписки ради у самих кнопок — второй раз
        // целым предложением он бы стал постоянным пояснением. Остальные причины
        // молчания это СОСТОЯНИЯ (нет прибора, нет потока, тихий режим), и они
        // появляются только когда наступили.
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
                    // В «Наведении» знаменатель другой, и молчание объясняет он
                    // же: фраза про записанный фон говорила бы здесь о другой
                    // величине.
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
                state = navigate,
                spot = spot,
                nowMillis = System.currentTimeMillis() - deviceClockOffset,
                cps = cps,
                // Доза печатается ровно так, как её даёт измерительная модель:
                // общий формат приложения плюс СОБСТВЕННАЯ относительная
                // погрешность прибора. Четыре знака без неё читались как
                // точность, которой у величины нет.
                doseLine = sample?.doseRate?.let { rate ->
                    val value = DoseFormat.rateWithUnit(rate, doseUnit, strings)
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
                onResetPeak = { navigate = NavigateEngine.resetPeak(navigate) },
                onMeasureHere = { graph.spotMeasure.start(navigate.reference) },
                onCancelMeasure = { graph.spotMeasure.cancel() },
                onDismissMeasure = { graph.spotMeasure.dismiss() },
                onGoToVerify = {
                    graph.spotMeasure.dismiss()
                    scope.launch { graph.settings.setSearchMode(SearchMode.VERIFY.id) }
                },
            )
            return@Column
        }

        // ---------------------------------------------------------- the answer
        Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space4) {
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
                Text(
                    text = cps?.let { Uncertainty.num1(it) } ?: "—",
                    style = type.valueHero.copy(fontSize = 52.sp, lineHeight = 54.sp),
                    color = if (cps != null) colors.ink else colors.muted,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = cps?.let { Uncertainty.cpsSigmaLine(it) } ?: t.cpsUnit,
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
                            Text(strings.backgroundTag, style = type.bodySmall, color = colors.ink2)
                            Text(
                                text = Uncertainty.num1(record.cps),
                                style = type.value,
                                color = colors.ink,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = t.toBackground, style = type.bodySmall, color = colors.ink2)
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

                // Сам вывод открывает разбор: вопрос «почему так решено»
                // задают, глядя именно на эту строку.
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
                    StatusRow(
                        text = SearchVerdict.headline(
                            level,
                            search.direction,
                            record != null,
                            strings,
                        ),
                        color = levelColor,
                    )
                    // Объяснения вывода под ним нет: оно повторяло то, что
                    // ниже показывают шкала и лента, а полностью разбор
                    // открывается нажатием на сам вывод.
                    // Подписи «почему такой вывод ›» нет — как и на Главной:
                    // нажимается сам вывод, а приглашение к нажатию занимало
                    // строку под каждым состоянием и повторяло то, что уже
                    // сообщает цвет ссылки.
                }

                // Чип направления и подпись «по последним 10 с» убраны с
                // «Проверки»: направление изменения — вопрос НАВЕДЕНИЯ, и там
                // оно показано модулем целиком. Здесь оно повторяло то же
                // третий раз, между выводом и шкалой. Сам расчёт направления
                // не тронут — он живёт в режиме наведения и в «Почему?».

                LedMeter(
                    level = if (cps != null) ledLevel(cps, record?.cps) else 0f,
                    modifier = Modifier.padding(top = Dimens.space3),
                )
                if (record == null) {
                    Text(
                        text = t.meterNeedsBackground,
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        }

        // ----------------------------------------------------------- the tape
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                // Заголовка ленты и подписи полосы на экране нет: величину
                // называет ось графика, а что такое полоса — справка «i».
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
                            baselineLabel = record?.let {
                                t.baselineLabel(Uncertainty.num1(it.cps))
                            },
                            xStartLabel = t.tapeStartLabel,
                            xEndLabel = strings.nowLabel,
                            excursionLabel = search.comparison
                                ?.takeIf { search.ladder.confirmed }
                                ?.let { SearchVerdict.ratioShort(it) }
                                ?.let { t.excursionLabel(it) },
                        ),
                    )
                    val values = points.map { it.cps }
                    StatGrid(
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
                    Text(
                        text = t.backgroundRunNote,
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
        } else {
            // Карточка фона: первый уровень — значение, основание и ОДНА
            // строка с названной причиной, если фон непригоден; абзацы
            // объяснений уехали под «i» (ТЗ §10). Модель собирает чистая
            // `SearchBaseline.card`, поэтому состав карточки проверяется тестом.
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                BackgroundCard(
                    model = SearchBaseline.card(
                        record = record,
                        check = check ?: BackgroundCheck.USABLE,
                        rateText = record?.let { Uncertainty.num1(it.cps) }.orEmpty(),
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
                    Text(
                        text = t.energyToneHint,
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }
            if (energyToneEnabled) {
                Text(
                    text = t.energyToneScale,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

/**
 * Карточка «i»: всё, что объясняет экран, — вопрос режима, окна решения, дуга,
 * лента, канал отклика и граница режима.
 *
 * Содержимое собрано чистой [NavigateInfo]: экран только рисует пары
 * «заголовок — абзац», поэтому проверять, что именно уехало под «i», можно
 * JVM-тестом, а не глазами.
 */
@Composable
private fun SearchInfoCard(
    rows: List<NavigateInfo.Row>,
    title: String,
    closeText: String,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(text = title, style = type.label, color = colors.ink)
            for (row in rows) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = row.title, style = type.labelSmall, color = colors.ink2)
                    Text(text = row.body, style = type.footnote, color = colors.muted)
                }
            }
            AppButton(text = closeText, onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun timeOfDay(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HH_MM)
