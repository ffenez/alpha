package app.radiacode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.Hardness
import app.radiacode.analysis.HardnessValue
import app.radiacode.baseline.Admission
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
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
import app.radiacode.ui.components.TrendChart
import app.radiacode.ui.components.TrendChartSpec
import app.radiacode.ui.components.WhySheet
import app.radiacode.ui.logic.ChartMapping
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.BaselineSnapshot
import app.radiacode.ui.logic.ProfileShift
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.TimeAxis
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHART_COLUMNS = 48
private const val CHART_WINDOW_MILLIS = 60L * 60_000L
private const val CHART_BUCKET_MILLIS = CHART_WINDOW_MILLIS / CHART_COLUMNS
private const val CHART_REFRESH_MILLIS = 15_000L

/** Hour-chart snapshot loaded off the 1 Hz path; values in µSv/h. */
@Immutable
private data class HourChart(
    val columns: List<Float?>,
    /** Alias of [columns] for readers that also take [cpsColumns]. */
    val stats: ChartMapping.Stats?,
    /** Raw 1 Hz samples inside the window (the honest n of the statgrid). */
    val sampleCount: Int,
    val fromMillis: Long,
    val toMillis: Long,
    val doseTodayMicroSv: Double,
    /** Same buckets, count rate — drawn only when the block is on. */
    val cpsColumns: List<Float?> = emptyList(),
) {
    val doseColumns: List<Float?> get() = columns
}

/** Жёсткость за час: the same buckets, read as dose per count. */
@Immutable
private data class HardnessChart(
    val columns: List<Float?>,
    val current: HardnessValue?,
    val fromMillis: Long,
    val toMillis: Long,
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

    var hourChart by remember { mutableStateOf<HourChart?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            hourChart = loadHourChart(graph)
            delay(CHART_REFRESH_MILLIS)
        }
    }

    // Жёсткость is dose per count: the hour chart already holds both, so the
    // block costs a mapping and no query at all.
    val hardnessChart = remember(hourChart, blocks.hardnessChart) {
        if (blocks.hardnessChart) hardnessOf(hourChart) else null
    }

    var showProfilePicker by remember { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf(false) }

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
            trendMicroSvHPerHour = hourChart?.let {
                TrendFit.slopePerHour(it.columns, CHART_BUCKET_MILLIS)
            },
            doseTodayMicroSv = hourChart?.doseTodayMicroSv,
            status = status,
            baselineState = baselineState,
            unit = unit,
            stale = freshness !is Freshness.Fresh,
            blocks = blocks,
            admission = admission,
            frozen = frozen,
            onWhy = { showWhy = true },
        )

        HourChartCard(
            chart = hourChart,
            baseline = (baselineState as? BaselineState.Active)?.baseline,
            thresholds = thresholds,
            unit = unit,
            alert = status is MonitorStatus.Alert,
            onOpen = onOpenChart,
            showStats = blocks.stats,
        )

        if (blocks.countRateChart) {
            CountRateChartCard(chart = hourChart, showStats = blocks.stats)
        }

        if (blocks.hardnessChart) {
            HardnessChartCard(chart = hardnessChart)
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
    when (freshness) {
        Freshness.NoData -> Chip(text = "нет данных", color = colors.muted)
        is Freshness.Fresh -> Chip(text = "${freshness.ageSeconds} с")
        is Freshness.Stale ->
            Chip(text = "прервано ${freshness.ageSeconds} с", color = colors.warn)
    }
}

@Composable
private fun HeroCard(
    doseMicroSvH: Float?,
    errPercent: Float?,
    cps: Float?,
    trendMicroSvHPerHour: Float?,
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
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Column(Modifier.weight(1.35f)) {
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
                Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(Dimens.border)
                            .fillMaxHeight()
                            .background(colors.line),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Dimens.space3),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KvRow("Счёт", cps?.let { Uncertainty.cpsWithSigma(it) } ?: "—")
                        if (blocks.trend) {
                            KvRow(
                                label = "Тренд/ч",
                                value = trendMicroSvHPerHour?.let { TrendFit.label(it, unit) }
                                    ?: "—",
                                valueColor = trendWarnColor(trendMicroSvHPerHour, status),
                            )
                        }
                        if (blocks.doseToday) {
                            KvRow(
                                label = "Сегодня",
                                value = doseTodayMicroSv?.let { DoseFormat.doseWithUnit(it, unit) }
                                    ?: "—",
                                // Integral of the measured rate, not a measured dose.
                                evidence = Evidence.CALCULATED,
                            )
                        }
                    }
                }
            }

            // Red is reserved for the confirmed alarm; amber for «выше
            // обычного»; normal states never shout (design rule).
            val statusColor = when {
                stale || status == MonitorStatus.Unknown -> colors.muted
                status is MonitorStatus.Alert -> colors.crit
                status is MonitorStatus.AboveUsual -> colors.warn
                status is MonitorStatus.Fixed && status.above -> colors.warn
                else -> colors.ok
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StatusDot(statusColor)
                Text(
                    text = statusHeadline(status),
                    style = type.label,
                    color = statusColor,
                )
                // The verdict leans on a statistical model, not on a reading.
                if (status is MonitorStatus.Usual || status is MonitorStatus.AboveUsual ||
                    status is MonitorStatus.Alert
                ) {
                    EvidenceTag(Evidence.STATISTICALLY_DETECTED)
                }
                Spacer(Modifier.weight(1f))
                Chip(text = "Почему?", color = colors.dataText, onClick = onWhy)
            }
            statusDetail(status, unit)?.let { detail ->
                Text(text = detail, style = type.footnote, color = colors.ink2)
            }
            (baselineState as? BaselineState.Learning)?.let { learning ->
                Text(
                    text = learningWording(learning),
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            // Learning is silent by nature, so the one case where it is NOT
            // happening must be visible without opening «Почему?».
            admissionNote(admission, frozen)?.let { note ->
                Text(text = note, style = type.footnote, color = colors.warn)
            }
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

@Composable
private fun KvRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    evidence: Evidence? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = type.bodySmall, color = colors.ink2)
        Spacer(Modifier.weight(1f))
        evidence?.let { EvidenceTag(it, Modifier.padding(end = 5.dp)) }
        Text(
            text = value,
            style = type.value,
            color = valueColor ?: colors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun HourChartCard(
    chart: HourChart?,
    baseline: Baseline?,
    thresholds: AlarmThresholds,
    unit: DoseUnitSetting,
    alert: Boolean,
    onOpen: () -> Unit = {},
    showStats: Boolean = true,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
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
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(
                    text = "Мощность дозы · час".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                if (baseline != null) {
                    Text(
                        text = "полоса — P10–P90 профиля",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                // Tap affordance: the card opens the fullscreen live chart.
                Text(text = "⤢", style = type.label, color = colors.ink2)
            }

            val stats = chart?.stats
            if (chart == null || stats == null) {
                Text(
                    text = "накапливаем измерения…",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                val alarmLevel = thresholds.l1MicroSvH
                val yMax = ChartMapping.yMax(
                    maxOf(stats.max, baseline?.doseHighMicroSvH ?: 0f),
                    alarmLevel,
                )
                TrendChart(
                    spec = TrendChartSpec(
                        columns = chart.columns,
                        yMax = yMax,
                        alarmLevel = alarmLevel,
                        alarmLabel = "L1 ${DoseFormat.rate(alarmLevel, unit)}",
                        band = baseline?.let { it.doseLowMicroSvH..it.doseHighMicroSvH },
                        yTicks = ChartMapping.yTicks(yMax).map { it to DoseFormat.rate(it, unit) },
                        xLabels = TimeAxis.labels(chart.fromMillis, chart.toMillis),
                        endpointAlert = alert,
                    ),
                )
                if (showStats) {
                    StatGrid(
                        cells = listOf(
                            StatCell(DoseFormat.rate(stats.min, unit), "мин"),
                            StatCell(DoseFormat.rate(stats.median, unit), "медиана"),
                            StatCell(DoseFormat.rate(stats.max, unit), "макс"),
                            StatCell(DoseFormat.rate(stats.sigma, unit), "SD, ${DoseFormat.rateUnitLabel(unit)}"),
                            StatCell(HistoryFormat.count(chart.sampleCount), "n"),
                        ),
                    )
                }
            }
        }
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

private suspend fun loadHourChart(graph: AppGraph): HourChart {
    val now = System.currentTimeMillis()
    val from = ChartMapping.alignedFrom(now, CHART_WINDOW_MILLIS, CHART_BUCKET_MILLIS)
    val buckets = graph.measurementRepository.downsampledSamples(
        from = from,
        to = now,
        bucketMillis = CHART_BUCKET_MILLIS,
    )
    val columns = ChartMapping.toColumns(
        buckets = buckets,
        alignedFromMillis = from,
        bucketMillis = CHART_BUCKET_MILLIS,
        columnCount = CHART_COLUMNS,
    ) { DoseUnits.rawToMicroSievertPerHour(it.avgDoseRate) }
    // The same buckets, read as count rate: the optional CPS chart costs one
    // more mapping, not one more query.
    val cpsColumns = ChartMapping.toColumns(
        buckets = buckets,
        alignedFromMillis = from,
        bucketMillis = CHART_BUCKET_MILLIS,
        columnCount = CHART_COLUMNS,
    ) { it.avgCountRate }

    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()
    val todayBuckets = graph.measurementRepository.downsampledSamples(
        from = startOfDay,
        to = now,
        bucketMillis = 60_000L,
    )

    return HourChart(
        columns = columns,
        stats = ChartMapping.stats(columns),
        sampleCount = buckets.sumOf { it.sampleCount },
        fromMillis = from,
        toMillis = now,
        doseTodayMicroSv = ChartMapping.integrateDoseMicroSv(todayBuckets),
        cpsColumns = cpsColumns,
    )
}

/**
 * Жёсткость за час — из тех же корзин, что и два других графика.
 *
 * The vendor defines the coefficient as dose per count, so it needs no spectra
 * at all: both rates are already in every bucket. That also makes it available
 * for the whole history instead of only where accumulated snapshots exist.
 */
private fun hardnessOf(chart: HourChart?): HardnessChart? {
    if (chart == null) return null
    val columns = chart.doseColumns.indices.map { i ->
        val dose = chart.doseColumns.getOrNull(i)
        val cps = chart.cpsColumns.getOrNull(i)
        if (dose == null || cps == null) {
            null
        } else {
            Hardness.of(
                doseRateMicroSvH = dose.toDouble(),
                countRate = cps.toDouble(),
                seconds = CHART_BUCKET_MILLIS / 1000.0,
            )?.value?.toFloat()
        }
    }
    return HardnessChart(
        columns = columns,
        current = columns.lastOrNull { it != null }?.let { last ->
            Hardness.of(
                doseRateMicroSvH = chart.doseColumns.lastOrNull { it != null }?.toDouble() ?: 0.0,
                countRate = chart.cpsColumns.lastOrNull { it != null }?.toDouble() ?: 0.0,
                seconds = CHART_BUCKET_MILLIS / 1000.0,
            )
        },
        fromMillis = chart.fromMillis,
        toMillis = chart.toMillis,
    )
}


/**
 * Отдельный график скорости счёта (Настройки → Вид → блоки Главной).
 *
 * Off by default and never above the dose chart: CPS is a **detection**
 * signal — it reacts faster than dose and is what a search leans on — but it
 * is not the quantity that describes the radiation situation, and the screen
 * must not let it look like one.
 */
@Composable
private fun CountRateChartCard(chart: HourChart?, showStats: Boolean) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Скорость счёта · час".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.weight(1f))
                Text(text = "с⁻¹", style = type.footnote, color = colors.muted)
            }
            val columns = chart?.cpsColumns.orEmpty()
            val stats = ChartMapping.stats(columns)
            if (chart == null || stats == null) {
                Text(
                    text = "накапливаем измерения…",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                val yMax = ChartMapping.yMax(stats.max, 0f)
                TrendChart(
                    spec = TrendChartSpec(
                        columns = columns,
                        yMax = yMax,
                        yTicks = ChartMapping.yTicks(yMax).map { it to Uncertainty.num1(it) },
                        xLabels = TimeAxis.labels(chart.fromMillis, chart.toMillis),
                    ),
                )
                if (showStats) {
                    StatGrid(
                        cells = listOf(
                            StatCell(Uncertainty.num1(stats.min), "мин"),
                            StatCell(Uncertainty.num1(stats.median), "медиана"),
                            StatCell(Uncertainty.num1(stats.max), "макс"),
                            StatCell(Uncertainty.num1(stats.sigma), "SD, с⁻¹"),
                        ),
                    )
                }
            }
            Text(
                text = "CPS — счёт событий детектора, не мера опасности: одно и то же " +
                    "число даёт и слабый близкий источник, и сильный далёкий.",
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

/**
 * График жёсткости (Настройки → Вид → блоки Главной).
 *
 * The product spec keeps this off Главная by default and demands the sentence
 * under it; both are honoured — but the choice is the user's, so the block can
 * be turned on. Hourly points, because that is the cadence at which the app
 * accumulates spectra in the background.
 */
@Composable
private fun HardnessChartCard(chart: HardnessChart?) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Жёсткость · час".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                EvidenceTag(Evidence.CALCULATED, Modifier.padding(start = 6.dp))
                Spacer(Modifier.weight(1f))
                chart?.current?.let {
                    Text(
                        text = Hardness.format(it.value),
                        style = type.value,
                        color = colors.ink,
                    )
                }
            }
            val columns = chart?.columns.orEmpty()
            if (chart == null || columns.all { it == null }) {
                Text(
                    text = "накапливаем измерения…",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            } else {
                val yMax = ChartMapping.yMax(
                    columns.filterNotNull().maxOrNull() ?: 1f,
                    0f,
                )
                TrendChart(
                    spec = TrendChartSpec(
                        columns = columns,
                        yMax = yMax,
                        yTicks = ChartMapping.yTicks(yMax)
                            .map { it to Hardness.format(it.toDouble()) },
                        xLabels = TimeAxis.labels(chart.fromMillis, chart.toMillis),
                    ),
                )
            }
            Text(
                text = Hardness.EXPLANATION,
                style = type.footnote,
                color = colors.muted,
            )
            Text(
                text = Hardness.PURPOSE,
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}
