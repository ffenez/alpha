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
import app.alpha.ui.components.FingerprintDimensionRows
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.BackgroundCard
import app.alpha.ui.text.BackgroundCardCatalogue
import app.alpha.ui.components.BreathingAura
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.LedMeter
import app.alpha.ui.components.SearchChartSpec
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
import app.alpha.analysis.Fingerprint
import app.alpha.analysis.FingerprintComparison
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
import app.alpha.ui.logic.InstrumentIndicator
import app.alpha.ui.logic.LocalBackground
import app.alpha.ui.logic.LocalBackgroundMachine
import app.alpha.ui.logic.NavigateEngine
import app.alpha.ui.logic.NavigateState
import app.alpha.ui.logic.NavigateTrend
import app.alpha.ui.logic.SearchMode
import app.alpha.ui.logic.SearchBaseline
import app.alpha.ui.logic.SearchEngine
import app.alpha.ui.logic.SearchFeedbackMode
import app.alpha.ui.logic.SearchLevel
import app.alpha.ui.logic.SearchSpectrumHint
import app.alpha.ui.logic.SearchState
import app.alpha.ui.logic.SearchPulse
import app.alpha.ui.logic.SearchUiState
import app.alpha.ui.logic.SearchUiStates
import app.alpha.ui.logic.SearchTone
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.SearchVerdict
import app.alpha.ui.logic.SearchVibro
import app.alpha.ui.logic.SearchWhyInput
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.logic.backgroundBand
import app.alpha.ui.logic.ledLevel
import app.alpha.ui.text.FingerprintCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SearchCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

/** Click feedback goes silent when the 1 Hz stream stops delivering. */
private const val FEEDBACK_STALE_MILLIS = 5_000L

/** How often the verdict is re-evaluated without a new reading. */
private const val TICK_MILLIS = 500L

/**
 * Как часто пересчитывается сводка отпечатка, мс.
 *
 * **Инженерный параметр**: сравнение идёт по окну в минуты, и чаще, чем раз в
 * пять секунд, его результат меняться не может — а запрос тяжёлый.
 */
private const val FINGERPRINT_REFRESH_MILLIS = 5_000L

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
    /**
     * Проверка вместо наведения. Режим приходит СВЕРХУ, от [InstrumentScreen]:
     * это один и тот же прибор с разным знаменателем, и хранить выбор в двух
     * местах значило бы иметь два разных ответа на вопрос «что сейчас».
     */
    verifying: Boolean = false,
    /** Вид шкалы прибора — общий для всех режимов, из Настроек. */
    indicator: InstrumentIndicator = InstrumentIndicator.DIAL,
    /** Перейти к проверке — режимом владеет родитель. */
    onGoToVerify: () -> Unit = {},
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
    // Режима внутри поиска больше нет: вопрос один, а «проверка» — фаза, в
    // которую он переходит сам, когда отличие держится ([SearchLadder]).
    // Вид индикатора «Наведения» — стрелка или прямая шкала.
    // Ровный счёт как повод предложить проверку: состояние живёт между
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
    // С чем сравнивать — один выбор на весь экран. Поставленная рукой точка
    // отсчёта идёт первой: поиск и проверка перестают быть разными режимами и
    // становятся одним вопросом с разным знаменателем.
    val reference = SearchReferences.choose(recorded, check, learned, navigate.reference)
    val learnedInUse = reference is SearchReference.Learned
    val markedInUse = reference is SearchReference.Marked
    val record = when (reference) {
        // Точка отсчёта — такое же окно счёта, как записанный эталон, только
        // снятое за секунды: движку сравнения нужна именно эта пара чисел.
        is SearchReference.Marked -> BackgroundRecord(
            window = reference.reference.window,
            atMillis = reference.reference.atMillis + deviceClockOffset,
            targetSamples = reference.reference.window.seconds.toInt(),
            profileId = activeProfileId,
            profileName = null,
            deviceSerial = deviceSerial,
        )
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
        is SearchReference.Marked -> null
        is SearchReference.Learned -> reference.background.low..reference.background.high
        else -> recorded?.let { backgroundBand(it) }
    }

    LaunchedEffect(sample, record) {
        val s = sample ?: return@LaunchedEffect
        if (s.deviceTimestampMillis == lastSeenTimestamp) return@LaunchedEffect
        lastSeenTimestamp = s.deviceTimestampMillis
        lastSampleReceivedAt = s.receivedAtMillis
        deviceClockOffset = lastSampleReceivedAt - s.deviceTimestampMillis
        search = SearchEngine.onReading(
            state = search,
            timeMillis = s.deviceTimestampMillis,
            cps = s.countRate,
            background = record,
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
    LaunchedEffect(resumed, record) {
        while (resumed) {
            delay(TICK_MILLIS)
            val instrumentNow = System.currentTimeMillis() - deviceClockOffset
            search = SearchEngine.onTick(
                state = search,
                background = record,
                nowMillis = instrumentNow,
            )
            navigate = NavigateEngine.onTick(state = navigate, nowMillis = instrumentNow)
        }
    }

    // The search tone follows the **ratio of the decision window**, not the
    // raw rate (redesign §7). The engine glides to the target, so a step in
    // the ratio is never a step in the audio.
    // Секундный тик: по нему живёт признак свежести потока, а «идут ли данные»
    // одинаково важно во всех состояниях экрана.
    LaunchedEffect(resumed) {
        while (resumed) {
            nowTick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Отношение, которое ведёт тон и вибрацию, — то же самое, что показывает
    // прибор: знаменатель выбран один раз ([SearchReferences]), и пока точка
    // отсчёта стоит, счёт сравнивается с ней.
    val ratio = navigate.referenceRatio ?: search.comparison?.ratio
    LaunchedEffect(mode, ratio, clickerActive) {
        clicker.setSearchTone(
            enabled = clickerActive && mode == SearchFeedbackMode.TONE,
            targetHz = SearchTone.frequencyHz(ratio),
        )
    }

    // Непрерывного пульса нет: он означал бы вибрацию всё время, пока прибор
    // в руке. Вибро отвечает на СОБЫТИЯ — счёт начал расти, найден новый
    // максимум, — и живёт в эффекте ниже.
    // Вибро «Наведения»: отклик на событие, а не непрерывная дрожь. Событий
    // два — счёт начал расти и найден новый максимум; оба приглушены порогом
    // и паузой.
    var lastTrend by remember { mutableStateOf(NavigateTrend.COLLECTING) }
    var pulsedPeak by remember { mutableStateOf(0.0) }
    var lastPulseAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(navigate.trend, navigate.peak, mode, resumed) {
        val trend = navigate.trend
        val peak = navigate.peak?.ratePerSecond ?: 0.0
        val rose = trend == NavigateTrend.RISING && lastTrend == NavigateTrend.NO_CHANGE
        val newPeak = pulsedPeak > 0.0 && peak > pulsedPeak * PEAK_PULSE_FACTOR
        lastTrend = trend
        if (peak > pulsedPeak) pulsedPeak = peak
        val active = resumed && mode == SearchFeedbackMode.VIBRO
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
        (backgroundRun is LocalBackground.Running || true)
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    val cps = sample?.countRate
    // Сводка отпечатка места: тот же расчёт, что на его экране, — «Проверка»
    // отвечает ровно на этот вопрос, и считать его вторым способом было бы
    // вторым источником истины.
    val fingerprintStrings = FingerprintCatalogue.of(strings.language)
    var fingerprint by remember { mutableStateOf<FingerprintComparison?>(null) }
    LaunchedEffect(activeProfileId, resumed, fingerprintStrings) {
        while (resumed && false) {
            val profileId = activeProfileId
            fingerprint = if (profileId == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        Fingerprint.compare(
                            window = graph.fingerprintRepository.window(profileId),
                            reference = graph.fingerprintRepository.reference(profileId),
                            s = fingerprintStrings,
                        )
                    }.getOrNull()
                }
            }
            delay(FINGERPRINT_REFRESH_MILLIS)
        }
    }

    // Одно состояние на весь экран: «ждём данные», стрелка, отношение и
    // видимость действия выводятся из него, а не из отдельных условий по месту.
    val searchUi = SearchUiStates.of(
        cps = cps,
        receivedAtMillis = sample?.receivedAtMillis,
        nowMillis = nowTick,
        connected = connection is ConnectionState.Connected,
        navigate = navigate,
    )
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
        // Переключателя режима здесь нет: он один на весь прибор и стоит в его
        // шапке ([InstrumentScreen]). Канал отклика выбирается в Настройках →
        // Уведомления и отклик.
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
                    backgroundRecorded = record != null,
                    insideBackground = SearchTone.frequencyHz(ratio) == null,
                ),
                t,
            )
        }
        val navSilence = when {
            mode != SearchFeedbackMode.TONE -> null
            navigate.reference == null -> t.navToneNoReference
            SearchTone.frequencyHz(ratio) == null -> t.navToneAtReference
            else -> null
        }
        (reason ?: navSilence)?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }

        // Один экран на весь поиск: прибор сверху, под ним то, что относится к
        // МЕСТУ, а не к текущему движению — фон, отпечаток, спектральная
        // подсказка. Отдельного режима «Проверка» нет: вывод об отличии
        // дозревает сам, пока прибор стоит, и показан прямо в приборе.
        NavigateSection(
            ui = searchUi,
            indicator = indicator,
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
            // Пока сравнение идёт с точкой отсчёта, вердикт строит сама секция:
            // её формулировки называют этот знаменатель. С фоном места говорит
            // лестница подтверждения — у неё свои слова про фон.
            verdict = if (markedInUse) {
                null
            } else {
                SearchVerdict.headline(level, search.direction, record != null, strings)
            },
            // Набор подтверждения идёт ВСЕГДА, пока держится отличие: отдельный
            // режим для этого не нужен, нужна честная полоска «сколько ещё».
            decision = search.decision?.takeIf { level != SearchLevel.BACKGROUND },
            decisionNote = search.decision?.let { decision ->
                if (decision.atLimit) {
                    t.decisionTooSmall
                } else {
                    t.decisionRemaining(decision.remainingSeconds.toInt())
                }
            },
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
        )

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

        // Отпечаток места живёт здесь, а не на Главной: это тот же вопрос, с
        // которым открывают Проверку — «здесь не так, как обычно?» — только
        // заданный про место целиком. Не кнопка во всю ширину, а сам ответ:
        // общий вывод и три составляющие, каждая со своей готовностью. Тап
        // открывает подробности.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenFingerprint,
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.placeFingerprint.uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "›", style = type.label, color = colors.muted)
                }
                val comparison = fingerprint
                if (comparison == null) {
                    Text(
                        text = t.waitingStream,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                } else {
                    StatusRow(
                        text = Fingerprint.headline(comparison, fingerprintStrings),
                        color = if (comparison.anyChanged) colors.warn else colors.ink,
                    )
                    FingerprintDimensionRows(
                        comparison = comparison,
                        t = fingerprintStrings,
                        detailed = false,
                    )
                }
            }
        }

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


