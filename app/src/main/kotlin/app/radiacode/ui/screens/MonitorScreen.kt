package app.radiacode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.Hardness
import app.radiacode.baseline.Admission
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.context.MeasurementContext
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.ExclusionSummary
import app.radiacode.data.MonitorBlocks
import app.radiacode.data.db.ProfileEntity
import app.radiacode.device.ConnectionState
import app.radiacode.device.DoseUnits
import app.radiacode.service.BatteryOptimization
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppIcons
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.EvidenceTag
import app.radiacode.ui.components.ProfilePickerDialog
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.components.StatusDot
import app.radiacode.ui.components.WhySheet
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.freshnessChipLabel
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.BaselineSnapshot
import app.radiacode.ui.logic.ProfileShift
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.TrendAvailability
import app.radiacode.ui.logic.TrendFit
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.WhyInput
import app.radiacode.ui.logic.learningWording
import app.radiacode.ui.logic.statusDetail
import app.radiacode.ui.logic.statusHeadline
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.radiacode.ui.components.DoseChart
import app.radiacode.ui.logic.ChartMetrics
import app.radiacode.ui.logic.ChartSnapshot
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.ui.logic.TrendPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Как часто перечитываются графики Главной. Прибор пишет раз в секунду, но
 * колонка карточки покрывает минуты — чаще обновлять нечего, а каждый лишний
 * проход это запрос в базу.
 */
private const val CHART_REFRESH_MILLIS = 15_000L

/**
 * Окно тренда на Главной — ЧАС, независимо от того, какое окно выбрано у
 * карточки графика.
 *
 * Тренд считался по окну карточки, а оно с некоторых пор общее с
 * полноэкранным графиком и запоминается: стоило выбрать там «5м», и правило
 * доступности (размах ≥10 мин) переставало выполняться НАВСЕГДА — плитка
 * показывала вечный прочерк «нужно 10 мин · есть 6 мин», хотя измерений
 * накопились часы. Величина, подписанная «Тренд/ч», обязана иметь собственное
 * названное окно, а не зависеть от того, что человек рассматривает рядом.
 */
private const val TREND_WINDOW_MILLIS = 3_600_000L

/** Как это окно называется в подписи под значением. */
private const val TREND_WINDOW_LABEL = "1 ч"

/**
 * Загруженный кадр одной величины: окно и снимок ровно те же, что у
 * полноэкранного графика, поэтому тап по карточке увеличивает картинку, а не
 * заменяет её другой.
 */
@Immutable
private data class LoadedChart(
    val window: ChartWindow,
    val snapshot: ChartSnapshot,
)

/**
 * Монитор (Главная): the 2-3 second answer — current dose rate with its
 * uncertainty, count rate, hour trend, dose today, whether the level differs
 * from the usual level of this place. Baseline state and the live deviation
 * picture come from the measurement service (single source); this screen
 * only renders [MonitorStatus].
 */
@Composable
fun MonitorScreen(
    graph: AppGraph,
    onOpenMetricChart: (ChartMetric) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenChart: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val deviation by graph.serviceStatus.deviation.collectAsState()
    val thresholds by graph.settings.alarmThresholds
        .collectAsState(initial = alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f))
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val blocks by graph.settings.monitorBlocks.collectAsState(initial = MonitorBlocks())
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val contextState by graph.contextHub.state.collectAsState()
    val admission by graph.serviceStatus.admission.collectAsState()
    val frozen by graph.settings.baselineFrozen.collectAsState(initial = false)
    val whyExpanded by graph.settings.whyCalculationsExpanded.collectAsState(initial = false)

    // 1 s wall-clock ticker drives the staleness indicator and held durations.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val freshness = Freshness.of(sample?.timestamp, nowMillis)

    // Графики Главной читаются тем же путём, что и полноэкранный (ADR 004):
    // одно окно, один снимок, один кадр. Величины, блоки которых выключены,
    // не читаются вовсе.
    val savedSpans by graph.settings.chartSpans.collectAsState(initial = emptyMap())
    val chartMetrics = remember(blocks.countRateChart, blocks.hardnessChart) {
        buildList {
            add(ChartMetric.DOSE)
            if (blocks.countRateChart) add(ChartMetric.COUNT_RATE)
            if (blocks.hardnessChart) add(ChartMetric.HARDNESS)
        }
    }
    var charts by remember { mutableStateOf<Map<ChartMetric, LoadedChart>>(emptyMap()) }
    var trend by remember { mutableStateOf<TrendAvailability?>(null) }
    var doseTodayMicroSv by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(chartMetrics, savedSpans) {
        while (true) {
            val now = System.currentTimeMillis()
            charts = withContext(Dispatchers.IO) {
                chartMetrics.associateWith { metric ->
                    val window = ChartMetrics.startWindow(metric, savedSpans, now)
                    LoadedChart(window, loadSnapshot(graph, window, metric))
                }
            }
            trend = withContext(Dispatchers.IO) {
                val hour = ChartWindows.latest(TREND_WINDOW_MILLIS, now)
                TrendFit.availability(
                    loadSnapshot(graph, hour, ChartMetric.DOSE).buckets
                        .filter { it.midMillis >= hour.fromMillis }
                        .map { TrendPoint(it.midMillis, it.median) },
                )
            }
            doseTodayMicroSv = withContext(Dispatchers.IO) { loadDoseToday(graph) }
            delay(CHART_REFRESH_MILLIS)
        }
    }

    var showProfilePicker by remember { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf(false) }

    // Сравнение с эталоном места — тоже запрос, и тоже только ради «Почему?»
    // и вкладки отпечатка: считается, когда шторка открывается.
    var fingerprint by remember {
        mutableStateOf<app.radiacode.analysis.FingerprintComparison?>(null)
    }
    LaunchedEffect(activeProfile?.id, showWhy) {
        val id = activeProfile?.id
        fingerprint = if (id == null || !showWhy) {
            null
        } else {
            app.radiacode.analysis.Fingerprint.compare(
                window = graph.fingerprintRepository.window(id),
                reference = graph.fingerprintRepository.reference(id),
            )
        }
    }

    // Exclusion breakdown is a query, not a stream: it only feeds «Почему?».
    var exclusions by remember { mutableStateOf<List<ExclusionSummary>>(emptyList()) }
    LaunchedEffect(activeProfile?.id, showWhy) {
        val id = activeProfile?.id
        exclusions = if (showWhy && id != null) graph.baselineRepository.exclusions(id) else emptyList()
    }

    val doseMicroSvH = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
    val status = MonitorStatus.of(
        doseRateMicroSvH = doseMicroSvH,
        baselineState = baselineState,
        deviation = deviation,
        thresholds = thresholds,
        nowMillis = nowMillis,
    )

    val colors = LocalAppColors.current
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
            Chip(
                text = profileChipText(activeProfile, profiles, contextState),
                color = colors.ink,
                onClick = { showProfilePicker = true },
            )
            Spacer(Modifier.weight(1f))
            ConnectionChip(connection, serviceRunning)
            FreshnessChip(freshness)
            Icon(
                imageVector = AppIcons.Lambda,
                contentDescription = "Настройки",
                tint = colors.ink2,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenSettings,
                    ),
            )
        }

        HeroCard(
            doseMicroSvH = doseMicroSvH,
            errPercent = sample?.doseRateErr,
            cps = sample?.countRate,
            trend = trend,
            trendWindowLabel = TREND_WINDOW_LABEL,
            doseTodayMicroSv = doseTodayMicroSv,
            status = status,
            baselineState = baselineState,
            unit = unit,
            stale = freshness !is Freshness.Fresh,
            blocks = blocks,
            admission = admission,
            frozen = frozen,
            onWhy = { showWhy = true },
        )

        val baseline = (baselineState as? BaselineState.Active)?.baseline
        val alert = status is MonitorStatus.Alert
        for (metric in chartMetrics) key(metric) {
            val loaded = charts[metric]
            val frame = remember(loaded, unit, thresholds, baseline, alert) {
                loaded?.let {
                    buildFrame(
                        snapshot = it.snapshot,
                        window = it.window,
                        unit = unit,
                        logScale = false,
                        thresholds = thresholds,
                        baseline = baseline,
                        endpointAlert = alert && metric == ChartMetric.DOSE,
                        metric = metric,
                        xLabelCount = 3,
                    )
                }
            }
            MetricChartCard(
                metric = metric,
                frame = frame,
                windowLabel = loaded?.let { windowLabel(metric, it.window) },
                hasBaselineBand = baseline != null,
                unit = unit,
                showStats = blocks.stats,
                onOpen = {
                    if (metric == ChartMetric.DOSE) onOpenChart() else onOpenMetricChart(metric)
                },
            )
        }

        if (blocks.cpsHint) {
            Text(
                text = "CPS — счёт событий детектора, не мера опасности",
                style = LocalAppTypography.current.footnote,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = Dimens.space1),
            )
        }

        BatteryBanner()
    }

    if (showProfilePicker) {
        ProfilePickerDialog(
            profiles = profiles,
            activeProfileId = activeProfile?.id,
            manual = contextState.isManual,
            contextWording = contextWording(contextState),
            onSelect = { id -> scope.launch { graph.profileRepository.selectManually(id) } },
            onReturnToAuto = { scope.launch { graph.profileRepository.returnToAuto() } },
            onCreate = { name ->
                scope.launch {
                    val id = graph.profileRepository.add(name)
                    graph.profileRepository.selectManually(id)
                }
            },
            onDismiss = { showProfilePicker = false },
        )
    }

    if (showWhy) {
        // §7: after hours of a held deviation the app may ask whether the place
        // itself changed. It never decides that by itself — a source that stays
        // put would otherwise redefine the room it is in.
        val profile = activeProfile
        val shiftOffered = profile != null && ProfileShift.shouldOffer(
            status = status,
            declinedAtMillis = profile.shiftDeclinedAtMillis,
            nowMillis = System.currentTimeMillis(),
        )
        WhySheet(
            expanded = whyExpanded,
            onExpandedChange = { scope.launch { graph.settings.setWhyCalculationsExpanded(it) } },
            offerProfileShift = shiftOffered,
            onUpdateProfile = {
                val profileId = profile?.id
                val current = (baselineState as? BaselineState.Active)?.baseline
                if (profileId != null && current != null) {
                    scope.launch {
                        graph.baselineRepository.startNewPeriod(
                            profileId = profileId,
                            stats = BaselineSnapshot.encode(current),
                        )
                    }
                }
                showWhy = false
            },
            onKeepProfile = {
                profile?.id?.let { id ->
                    scope.launch { graph.baselineRepository.declineShift(id) }
                }
                showWhy = false
            },
            input = WhyInput(
                status = status,
                baselineState = baselineState,
                doseRateMicroSvH = doseMicroSvH,
                cps = sample?.countRate,
                freshness = freshness,
                thresholds = thresholds,
                admission = admission,
                exclusions = exclusions,
                unit = unit,
                profileName = activeProfile?.let { ProfileTree.displayName(it, profiles) },
                contextWording = contextWording(contextState),
                fingerprint = fingerprint,
            ),
            onDismiss = { showWhy = false },
        )
    }
}

/** «⌂ Дом · авто ▾» — profile plus how it was chosen (spec §17 layout). */
private fun profileChipText(
    active: ProfileEntity?,
    profiles: List<ProfileEntity>,
    context: MeasurementContext,
): String {
    val name = active?.let { ProfileTree.displayName(it, profiles) } ?: "Профиль?"
    val icon = active?.icon.orEmpty()
    val prefix = if (icon.isBlank()) "" else "$icon "
    return "$prefix$name · ${contextModeWord(context)} ▾"
}

private fun contextModeWord(context: MeasurementContext): String = when (context) {
    is MeasurementContext.Manual -> "вручную"
    is MeasurementContext.AutoUncertain -> "не подтв."
    else -> "авто"
}

/** One honest phrase about how the current profile was chosen (spec §3.4). */
fun contextWording(context: MeasurementContext): String = when (context) {
    is MeasurementContext.AutoKnown -> "выбран автоматически по знакомой сети"
    is MeasurementContext.AutoUncertain ->
        "сеть пропала — место не подтверждено, обучение приостановлено"
    MeasurementContext.AutoTransit -> "знакомой сети нет — «В пути»"
    MeasurementContext.NoContext -> "место определить нельзя — «Без места»"
    is MeasurementContext.Manual -> "выбран вручную"
}

@Composable
private fun ConnectionChip(connection: ConnectionState, serviceRunning: Boolean) {
    val colors = LocalAppColors.current
    val (dot, text) = when {
        connection is ConnectionState.Connected -> colors.ok to "RC-110 · 1 Гц"
        connection is ConnectionState.Connecting -> colors.warn to "подключение"
        connection is ConnectionState.Reconnecting -> colors.warn to "переподкл."
        !serviceRunning -> colors.muted to "служба выкл."
        else -> colors.muted to "нет связи"
    }
    Chip(text = text, dot = dot)
}

@Composable
private fun FreshnessChip(freshness: Freshness) {
    val colors = LocalAppColors.current
    // Пока поток идёт, подписи нет вовсе: чип говорит об ОТСТАВАНИИ данных.
    val label = freshnessChipLabel(freshness) ?: return
    when (freshness) {
        Freshness.NoData -> Chip(text = label, color = colors.muted)
        is Freshness.Fresh -> Chip(text = label, color = colors.warn)
        is Freshness.Stale -> Chip(text = label, color = colors.warn)
    }
}

/**
 * Карточка главного экрана: величина → состояние → плитки → действия.
 *
 * Порядок — это ответ на вопросы в том порядке, в каком их задают: «сколько
 * сейчас», «это обычно для этого места», «а что ещё известно», «почему так
 * решено». Раньше карточка делилась пополам: слева крупное число, справа
 * колонка «Счёт / Тренд / Сегодня» мелким шрифтом — и вход в «Почему?»
 * оказывался чипом в хвосте строки статуса, где его было не найти. Теперь
 * вспомогательные величины стоят плитками во всю ширину, а оба входа —
 * «Почему такой вывод?» и «Отпечаток места» — отдельной строкой действий.
 */
@Composable
private fun HeroCard(
    doseMicroSvH: Float?,
    errPercent: Float?,
    cps: Float?,
    trend: TrendAvailability?,
    trendWindowLabel: String?,
    doseTodayMicroSv: Double?,
    status: MonitorStatus,
    baselineState: BaselineState?,
    unit: DoseUnitSetting,
    stale: Boolean,
    blocks: MonitorBlocks = MonitorBlocks(),
    admission: Admission = Admission.Admitted,
    frozen: Boolean = false,
    onWhy: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            // 1. Величина, ради которой открывают приложение. По центру: это
            // единственный элемент экрана, который читают издалека и мельком —
            // ему нужна ось симметрии, а не левый край текста.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Мощность дозы".uppercase(),
                        style = type.labelSmall,
                        color = colors.ink2,
                    )
                    EvidenceTag(Evidence.MEASURED, Modifier.padding(start = 6.dp))
                }
                Text(
                    text = doseMicroSvH?.let { DoseFormat.rate(it, unit) } ?: "—",
                    style = type.valueHero,
                    color = if (doseMicroSvH == null || stale) colors.muted else colors.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = listOfNotNull(
                        DoseFormat.rateUnitLabel(unit),
                        Uncertainty.errPercentLabel(errPercent),
                    ).joinToString(" · "),
                    style = type.footnote,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // 2. Состояние фона: во всю ширину, без соседей по строке.
            // Red is reserved for the confirmed alarm; amber for «выше
            // обычного»; normal states never shout (design rule).
            val statusColor = when {
                stale || status == MonitorStatus.Unknown -> colors.muted
                status is MonitorStatus.Alert -> colors.crit
                status is MonitorStatus.AboveUsual -> colors.warn
                status is MonitorStatus.Fixed && status.above -> colors.warn
                else -> colors.ok
            }
            // Сам вывод — и есть кнопка «почему»: вопрос задают, глядя именно
            // на эту строку, и отдельная кнопка рядом с ней была лишним шагом
            // между вопросом и ответом.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusChip))
                    .clickable(onClick = onWhy)
                    .padding(vertical = Dimens.space1),
                verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    StatusDot(statusColor)
                    Text(
                        text = statusHeadline(status),
                        style = type.label,
                        color = statusColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // The verdict leans on a statistical model, not on a reading.
                    if (status is MonitorStatus.Usual || status is MonitorStatus.AboveUsual ||
                        status is MonitorStatus.Alert
                    ) {
                        EvidenceTag(Evidence.STATISTICALLY_DETECTED)
                    }
                }
                // Эталон, по которому сделан вывод, стоит под ним ВСЕГДА: без
                // диапазона и объёма истории «в обычном диапазоне» — это
                // утверждение, которое нечем проверить.
                statusDetail(status, unit)?.let { detail ->
                    Text(
                        text = detail,
                        style = type.footnote,
                        color = colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                }
                (baselineState as? BaselineState.Learning)?.let { learning ->
                    Text(
                        text = learningWording(learning),
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                // Пополняется ли статистика прямо сейчас — вопрос, который
                // человек задаёт, глядя на объём истории. Молчание означало
                // «да», и это было незаметно; теперь ответ есть в обе стороны.
                Text(
                    text = admissionNote(admission, frozen) ?: ADMISSION_OK_NOTE,
                    style = type.footnote,
                    color = if (admission is Admission.Excluded || frozen) {
                        colors.warn
                    } else {
                        colors.muted
                    },
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "почему такой вывод ›",
                    style = type.footnote,
                    color = colors.dataText,
                )
            }

            // 3. Плитки: то, что дополняет главное число, а не спорит с ним.
            val tiles = buildList {
                add(
                    HeroTile(
                        label = "Счёт",
                        value = cps?.let { Uncertainty.cpsWithSigma(it) } ?: "—",
                    ),
                )
                if (blocks.trend) {
                    val slope = (trend as? TrendAvailability.Ready)?.result?.slopeMicroSvHPerHour
                    add(
                        HeroTile(
                            label = "Тренд/ч",
                            value = slope?.let { TrendFit.label(it, unit) } ?: "—",
                            valueColor = trendWarnColor(slope, status),
                            evidence = Evidence.CALCULATED,
                            // Прочерк без причины неотличим от поломки: плитка
                            // говорит, чего именно не хватает — или за какое
                            // окно посчитан показанный наклон.
                            note = when {
                                trend == null -> null
                                slope != null -> trendWindowLabel?.let { "за $it" }
                                else -> TrendFit.unavailableNote(trend)
                            },
                        ),
                    )
                }
                if (blocks.doseToday) {
                    add(
                        HeroTile(
                            label = "Сегодня",
                            value = doseTodayMicroSv?.let { DoseFormat.doseWithUnit(it, unit) }
                                ?: "—",
                            // Integral of the measured rate, not a measured dose.
                            evidence = Evidence.CALCULATED,
                        ),
                    )
                }
            }
            if (tiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    for (tile in tiles) {
                        HeroTileBox(tile, Modifier.weight(1f))
                    }
                }
            }

        }
    }
}

/** Что показано, когда статистика места пополняется как обычно. */
private const val ADMISSION_OK_NOTE = "обычный фон пополняется"

/** Одна плитка под главным числом. */
private data class HeroTile(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
    val evidence: Evidence? = null,
    /** Одна тихая строка под значением: за какое окно оно или чего не хватает. */
    val note: String? = null,
)

@Composable
private fun HeroTileBox(tile: HeroTile, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(colors.surface2)
            .padding(horizontal = Dimens.space2, vertical = Dimens.space2),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tile.label.uppercase(),
                style = type.overline,
                color = colors.muted,
                maxLines = 1,
            )
            if (tile.evidence != null) {
                EvidenceTag(tile.evidence, Modifier.padding(start = 4.dp))
            }
        }
        Text(
            text = tile.value,
            style = type.value,
            color = tile.valueColor ?: colors.ink,
            maxLines = 1,
        )
        tile.note?.let {
            Text(text = it, style = type.footnote, color = colors.muted, maxLines = 1)
        }
    }
}

/**
 * One line under the status when the baseline is NOT learning right now.
 * Silence means «учится» — saying that on every screen would be noise, but
 * hiding the opposite would make the statistics quietly unexplainable.
 */
private fun admissionNote(admission: Admission, frozen: Boolean): String? = when {
    admission is Admission.Excluded ->
        "обычный фон сейчас не пополняется: ${admission.reason.label}"
    frozen -> "обычный фон заморожен вручную"
    else -> null
}

@Composable
private fun trendWarnColor(trend: Float?, status: MonitorStatus): Color? {
    if (trend == null || trend <= TrendFit.FLAT_EPSILON_MICRO_SV) return null
    return when (status) {
        is MonitorStatus.AboveUsual, is MonitorStatus.Alert -> LocalAppColors.current.warn
        else -> null
    }
}

/**
 * Карточка величины на Главной — миниатюра полноэкранного графика.
 *
 * Это буквально тот же кадр: те же корзины, медиана и квантильные конверты,
 * тот же фон с пропусками и границей истории, то же окно и те же правила
 * честности. Раньше здесь жил свой усреднённый ряд по своим корзинам, и тап
 * по карточке ПОДМЕНЯЛ картинку другой — человеку приходилось заново искать
 * на большом графике то, что он увидел на маленьком. Различие осталось одно:
 * миниатюрой нельзя управлять, её единственное действие — открыть во весь
 * экран.
 */
@Composable
private fun MetricChartCard(
    metric: ChartMetric,
    frame: ChartFrame?,
    windowLabel: String?,
    hasBaselineBand: Boolean,
    unit: DoseUnitSetting,
    showStats: Boolean,
    onOpen: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val cursor = remember { mutableStateOf<Float?>(null) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = metric.title.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                if (windowLabel != null) {
                    Text(text = windowLabel, style = type.footnote, color = colors.muted)
                }
                if (metric == ChartMetric.HARDNESS) {
                    EvidenceTag(Evidence.CALCULATED)
                }
                Spacer(Modifier.weight(1f))
                if (metric == ChartMetric.DOSE && hasBaselineBand) {
                    Text(
                        text = "полоса — P10–P90 профиля",
                        style = type.footnote,
                        color = colors.muted,
                    )
                } else {
                    Text(
                        text = ChartMetrics.unitLabel(metric, unit),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                // Tap affordance: the card opens the fullscreen live chart.
                Text(text = "⤢", style = type.label, color = colors.ink2)
            }

            if (frame == null || frame.spec.buckets.isEmpty()) {
                Text(
                    text = "накапливаем измерения…",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                DoseChart(
                    spec = frame.spec,
                    cursorFraction = cursor,
                    interactive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (metric == ChartMetric.DOSE) 168.dp else 132.dp),
                )
                val stats = frame.stats
                if (showStats && stats != null) {
                    StatGrid(
                        cells = listOf(
                            StatCell(ChartMetrics.format(metric, stats.min, unit), "мин"),
                            StatCell(ChartMetrics.format(metric, stats.median, unit), "медиана"),
                            StatCell(ChartMetrics.format(metric, stats.max, unit), "макс"),
                            StatCell(
                                ChartMetrics.format(metric, stats.sd, unit),
                                "SD, ${ChartMetrics.unitLabel(metric, unit)}",
                            ),
                            StatCell(HistoryFormat.count(stats.sampleCount), "n"),
                        ),
                    )
                }
            }

            for (line in ChartMetrics.footnotes(metric)) {
                Text(text = line, style = type.footnote, color = colors.muted)
            }
        }
    }
}

/**
 * Подпись окна карточки: ступень лестницы, если окно ей равно, иначе
 * фактическая длительность — то же правило, что у свёрнутого чипа периодов.
 */
private fun windowLabel(metric: ChartMetric, window: ChartWindow): String {
    val index = ChartWindows.nearestPeriodIndex(
        window.spanMillis,
        ChartMetrics.periodIndices(metric),
    )
    return if (ChartWindows.matchesPeriod(window.spanMillis, index)) {
        ChartWindows.PERIODS[index].first
    } else {
        HistoryFormat.duration(window.spanMillis / 1000)
    }
}

@Composable
private fun BatteryBanner() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(BatteryOptimization.isExempt(context)) }
    if (exempt) return
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = "Android может остановить измерение в фоне. Чтобы запись " +
                    "шла непрерывно, исключите приложение из оптимизации батареи.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            AppButton(
                text = "Разрешить работу в фоне",
                onClick = {
                    runCatching {
                        context.startActivity(BatteryOptimization.buildRequestIntent(context))
                    }
                    exempt = BatteryOptimization.isExempt(context)
                },
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * Доза за сегодня — единственное, что осталось отдельным запросом: она
 * считается от начала суток, а не по окну графика, и минутных корзин для неё
 * достаточно.
 */
private suspend fun loadDoseToday(graph: AppGraph): Double {
    val now = System.currentTimeMillis()
    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()
    val buckets = graph.measurementRepository.downsampledSamples(
        from = startOfDay,
        to = now,
        bucketMillis = 60_000L,
    )
    return ChartMapping.integrateDoseMicroSv(buckets)
}
