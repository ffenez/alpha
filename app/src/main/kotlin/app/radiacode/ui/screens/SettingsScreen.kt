@file:OptIn(ExperimentalLayoutApi::class)

package app.radiacode.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import app.radiacode.ui.theme.Motion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import app.radiacode.R
import app.radiacode.AppGraph
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.context.ContextConfig
import app.radiacode.context.NetworkIdentity
import app.radiacode.data.AppSettings
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.MonitorBlocks
import app.radiacode.data.ThemeSetting
import app.radiacode.data.db.ProfileEntity
import app.radiacode.data.db.ProfileNetworkEntity
import app.radiacode.device.ConnectionState
import app.radiacode.device.DeviceModel
import app.radiacode.service.Notifications
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTab
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.RadioMark
import app.radiacode.service.DeviceControlHub
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.NavConfig
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.ProfileDeletion
import app.radiacode.ui.logic.NavEntry
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.baselineCollectedWording
import app.radiacode.ui.logic.ReleaseNotes
import app.radiacode.ui.logic.freshnessLabel
import app.radiacode.ui.logic.heldWording
import app.radiacode.ui.logic.learningWording
import app.radiacode.ui.text.AppLanguage
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.CalibrationCatalogue
import app.radiacode.ui.text.NotificationCatalogue
import app.radiacode.ui.text.ReleaseCatalogue
import app.radiacode.ui.text.RuStrings
import app.radiacode.ui.text.Strings
import app.radiacode.ui.theme.AppSkin
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.radiacode.baseline.Admission
import app.radiacode.data.export.CrashLog
import app.radiacode.data.export.DebugBundle
import app.radiacode.data.export.RcResultData
import app.radiacode.data.export.RcSpectrum
import app.radiacode.data.export.RcXml
import app.radiacode.data.export.SpectrumExport
import app.radiacode.data.export.DebugReport
import app.radiacode.data.export.DebugSnapshot
import app.radiacode.data.export.SpectrumTraffic
import app.radiacode.device.DoseUnits
import app.radiacode.ui.logic.MonitorStatus
import app.radiacode.ui.logic.SearchFeedbackMode
import app.radiacode.ui.logic.statusDetail
import app.radiacode.ui.logic.statusHeadline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Разделы настроек, сгруппированные по тому, ЧЕМ они управляют.
 *
 * Плоский список смешивал три разные вещи: как измеряется (тревоги, профили),
 * как ведёт себя приложение (вид, уведомления) и служебное. «Обычный фон» и
 * вовсе стоял отдельным разделом уровня «Прибор», хотя это часть профилей:
 * фон принадлежит МЕСТУ и настраивается там же, где место.
 *
 * «Отладка» ушла с первого уровня: пункт «отчёт о состоянии приложения в
 * файл» рядом с «Прибором» выдаёт сборку разработчика. Она осталась
 * findable — внутри «О приложении», — потому что отчёты нужны для разбора
 * полевых случаев, и прятать её за семь нажатий значило бы их не получать.
 */
private enum class SettingsGroup {
    MEASUREMENT,
    APP,
    OTHER,
    ;

    fun title(strings: Strings): String = when (this) {
        MEASUREMENT -> strings.groupMeasurement
        APP -> strings.groupApp
        OTHER -> strings.groupOther
    }
}

private enum class SettingsCategory(val group: SettingsGroup) {
    ALARMS(SettingsGroup.MEASUREMENT),
    PROFILES(SettingsGroup.MEASUREMENT),
    SOUND(SettingsGroup.APP),
    VIEW(SettingsGroup.APP),
    DEVICE(SettingsGroup.OTHER),
    ABOUT(SettingsGroup.OTHER),
    ;

    fun title(s: Strings): String = when (this) {
        ALARMS -> s.settingsAlarms
        PROFILES -> s.settingsProfiles
        SOUND -> s.settingsNotifications
        VIEW -> s.settingsView
        DEVICE -> s.settingsDevice
        ABOUT -> s.settingsAbout
    }

    fun subtitle(s: Strings): String = when (this) {
        ALARMS -> s.settingsAlarmsSub
        PROFILES -> s.settingsProfilesSub
        SOUND -> s.settingsNotificationsSub
        VIEW -> s.settingsViewSub
        DEVICE -> s.settingsDeviceSub
        ABOUT -> s.settingsAboutSub
    }
}

/**
 * Настройки (SPEC: opens separately, not a tab): a list of categories, one
 * screen deep. The back button goes one level at a time, so «назад» always
 * means what it looks like.
 */
@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    var category by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    // Диагностика калибровки — экран внутри «Прибора». Своя «назад» у него
    // есть (BackHandler композиции ниже), поэтому системный жест закрывает
    // сначала её, а не весь раздел.
    var calibrationOpen by rememberSaveable { mutableStateOf(false) }

    // Системная «назад» (в том числе жест от края) обязана значить ровно то же,
    // что кнопка на экране: один шаг вверх. Без этого свайп из открытого
    // раздела закрывал сразу все настройки и выбрасывал на Главную.
    BackHandler(enabled = category != null) {
        if (calibrationOpen) calibrationOpen = false else category = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        val open = category
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(
                text = "← ${strings.back}",
                // Один шаг вверх, чем бы этот шаг ни был: сначала закрывается
                // экран внутри раздела, потом сам раздел, потом Настройки.
                onClick = {
                    when {
                        calibrationOpen -> calibrationOpen = false
                        open == null -> onBack()
                        else -> category = null
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            Chip(text = open?.title(strings) ?: strings.settings, color = colors.ink)
        }

        AnimatedContent(
            targetState = open,
            transitionSpec = {
                (fadeIn(Motion.screen()) + scaleIn(Motion.screen(), initialScale = 0.97f))
                    .togetherWith(fadeOut(tween(Motion.SCREEN_EXIT_MILLIS)))
            },
            label = "settingsCategory",
        ) { openCategory ->
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        when (openCategory) {
            null -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                for (group in SettingsGroup.entries) {
                    val items = SettingsCategory.entries.filter { it.group == group }
                    if (items.isEmpty()) continue
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                        Text(
                            text = group.title(strings).uppercase(),
                            style = LocalAppTypography.current.labelSmall,
                            color = colors.ink2,
                            modifier = Modifier.padding(start = Dimens.space1),
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                items.forEachIndexed { index, entry ->
                                    if (index > 0) AppDivider()
                                    CategoryRow(entry) { category = entry }
                                }
                            }
                        }
                    }
                }
            }
            SettingsCategory.ALARMS -> AlarmsSection(graph)
            // Фон принадлежит МЕСТУ: профили и обучение фона — один раздел.
            SettingsCategory.PROFILES -> {
                ProfilesSection(graph)
                BaselineSection(graph)
                RetentionSection(graph)
            }
            SettingsCategory.SOUND -> SoundSection(graph)
            SettingsCategory.VIEW -> {
                LanguageSection(graph)
                SkinSection(graph)
                ThemeSection(graph)
                ScaleSection(graph)
                UnitsSection(graph)
                InterfaceSection(graph)
            }
            SettingsCategory.DEVICE -> {
                if (calibrationOpen) {
                    CalibrationScreen(graph) { calibrationOpen = false }
                } else {
                    DeviceSection(graph)
                    BootSection(graph)
                    DeviceSignalsSection(graph)
                    SpectrumRateSection(graph)
                    SpectralRangesSection(graph)
                    CalibrationEntry { calibrationOpen = true }
                }
            }
            SettingsCategory.ABOUT -> {
                LicensesSection()
                DebugSection(graph)
            }
        }
        }
        }
    }
}

/** Вход в диагностику калибровки по природному фону (Настройки → Прибор). */
@Composable
private fun CalibrationEntry(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val c = CalibrationCatalogue.of(LocalStrings.current.language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.touchTarget)
                .clickable(onClick = onClick),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = c.entryTitle, style = type.label, color = colors.ink)
                Text(text = c.entrySubtitle, style = type.footnote, color = colors.muted)
            }
            Text(text = "›", style = type.value, color = colors.ink2)
        }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = category.title(strings), style = type.label, color = colors.ink)
            Text(text = category.subtitle(strings), style = type.footnote, color = colors.muted)
        }
        Text(text = "›", style = type.title, color = colors.ink2)
    }
}

// --- Звук ---

/**
 * Всё, что звучит и вибрирует, в одном месте: отклик Поиска (тот же самый
 * выбор, что на экране Поиска — одна настройка, две двери) и ссылка на
 * системный канал тревоги.
 */
@Composable
private fun SoundSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val modeId by graph.settings.searchFeedbackMode.collectAsState(initial = null)
    val mode = SearchFeedbackMode.of(modeId) ?: SearchFeedbackMode.OFF
    val energyTone by graph.settings.searchEnergyToneEnabled.collectAsState(initial = false)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.searchFeedbackTitle)
            Segmented(
                options = SearchFeedbackMode.entries.map { it.title(strings) },
                selectedIndex = SearchFeedbackMode.entries.indexOf(mode),
                onSelect = { index ->
                    scope.launch {
                        graph.settings.setSearchFeedbackMode(
                            SearchFeedbackMode.entries[index].id,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = when (mode) {
                    SearchFeedbackMode.OFF -> strings.feedbackOnScreenOnly
                    SearchFeedbackMode.CLICKS ->
                        strings.feedbackClicks
                    SearchFeedbackMode.TONE ->
                        strings.feedbackTone
                    SearchFeedbackMode.VIBRO ->
                        strings.feedbackVibro
                },
                style = type.bodySmall,
                color = colors.ink2,
            )
            if (mode == SearchFeedbackMode.CLICKS) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Chip(
                        text = strings.energyTone,
                        color = if (energyTone) colors.dataText else colors.muted,
                        dot = if (energyTone) colors.data else null,
                        onClick = {
                            scope.launch {
                                graph.settings.setSearchEnergyToneEnabled(!energyTone)
                            }
                        },
                    )
                    Hint(
                        text = strings.energyToneNote,
                    )
                }
            }
            AppDivider()
            SectionTitle(strings.alarmTitle)
            AlarmSoundRow()
        }
    }
}

// --- Отладка ---

/**
 * Отчёт «что сейчас думает приложение» — для разбора наблюдений вида
 * «поставил порог, а на экране ничего». Выключен по умолчанию: это инструмент
 * разбора, а не повседневная функция.
 */
@Composable
private fun DebugSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val enabled by graph.settings.debugReportEnabled.collectAsState(initial = false)
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Одна строка от человека стоит десяти строк догадок по цифрам: что он
    // делал и что ожидал увидеть, из отчёта не восстанавливается.
    var problem by rememberSaveable { mutableStateOf("") }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val content = pending
        pending = null
        if (uri != null && content != null) {
            scope.launch {
                notice = if (writeBytesToUri(context, uri, content)) {
                    strings.archiveSaved
                } else {
                    strings.archiveFailed
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.debugTitle)
            BlockToggleRow(strings.stateReport, enabled) {
                scope.launch { graph.settings.setDebugReportEnabled(it) }
            }
            Hint(
                text = strings.debugBundleNote,
                style = type.bodySmall,
                color = colors.ink2,
            )
            Hint(
                text = DebugBundle.PRIVACY_NOTE,
            )
            if (enabled) {
                Text(text = strings.whatIsWrong, style = type.label, color = colors.ink)
                AppTextField(
                    value = problem,
                    onValueChange = { problem = it },
                    placeholder = strings.whatIsWrongHint,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = strings.saveDebugArchive,
                    onClick = {
                        scope.launch {
                            val now = System.currentTimeMillis()
                            pending = buildDebugBundle(graph, context, problem, now)
                            saveLauncher.launch(
                                DebugBundle.fileName(now) { millis ->
                                    FILE_STAMP.format(
                                        Instant.ofEpochMilli(millis)
                                            .atZone(ZoneId.systemDefault()),
                                    )
                                },
                            )
                        }
                    },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            notice?.let {
                Text(text = it, style = type.footnote, color = colors.ink2)
            }
        }
    }
}

/**
 * Собирает снимок состояния для отчёта. Строки статуса берутся ТЕМИ ЖЕ
 * функциями, что рисуют экран: отчёт, в котором формулировки пересобраны
 * заново, отвечал бы на другой вопрос.
 */
/**
 * Архив отладки: опись, отчёт о состоянии, описание проблемы словами человека
 * и спектры в их собственном формате.
 *
 * Одна кнопка вместо трёх просьб: разбирать случай по половине данных нельзя,
 * а собрать их вручную человек в поле не обязан.
 */
private suspend fun buildDebugBundle(
    graph: AppGraph,
    context: android.content.Context,
    problem: String,
    nowMillis: Long,
): ByteArray {
    val report = buildDebugReport(graph, context)
    val serial = (graph.serviceStatus.connection.value as? ConnectionState.Connected)
        ?.info?.serialNumber
    val model = SpectrumExport.modelFromSerial(serial)
    val entries = mutableListOf<DebugBundle.Entry>()

    if (problem.isNotBlank()) {
        entries += DebugBundle.Entry("problem.txt", problem.trim())
    }
    entries += DebugBundle.Entry("report.txt", report)

    // Журнал падений — то, ради чего архив чаще всего и присылают. Пустой
    // журнал кладётся тоже: «падений не записано» это ответ, а отсутствие
    // файла читалось бы как «забыли положить».
    entries += DebugBundle.Entry(
        name = CrashLog.FILE_NAME,
        content = CrashLog.bundleText(
            runCatching { graph.crashLogFile.readText() }.getOrDefault(""),
        ),
    )

    // Спектр в формате RC-XML, а не вклеенный в текст: так он остаётся файлом,
    // который открывается другими программами и сравнивается с эталонами.
    graph.spectrumHub.state.value.spectrum?.let { live ->
        entries += DebugBundle.Entry(
            name = "spectrum.xml",
            content = RcXml.write(
                RcResultData(
                    deviceModel = model,
                    sampleName = "debug",
                    sampleNote = null,
                    startMillis = nowMillis - live.durationSeconds * 1000L,
                    endMillis = nowMillis,
                    spectrum = RcSpectrum(
                        name = "spectrum",
                        serialNumber = serial,
                        a0 = live.a0,
                        a1 = live.a1,
                        a2 = live.a2,
                        measurementSeconds = live.durationSeconds,
                        counts = live.counts,
                    ),
                    background = null,
                ),
            ),
        )
    }
    graph.measurementRepository.backgroundReference().first()?.let { entity ->
        entries += DebugBundle.Entry(
            name = "background.xml",
            content = RcXml.write(
                RcResultData(
                    deviceModel = model,
                    sampleName = "background",
                    sampleNote = null,
                    startMillis = entity.timestamp - entity.durationSeconds * 1000L,
                    endMillis = entity.timestamp,
                    spectrum = SpectrumExport.toRcSpectrum(entity, serial, "background"),
                    background = null,
                ),
            ),
        )
    }

    val stamp = FILE_STAMP.format(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()))
    // Описи README в архиве нет: имена файлов говорят сами за себя, а
    // оговорка приватности стоит на экране, где архив создают, — читают её
    // именно там.
    return DebugBundle.zip(entries)
}

/**
 * Цена частоты опроса спектра — ИЗМЕРЕННАЯ (ADR 007): счётчики службы, а не
 * оценка по выбранной ступени. Служба не работает — «в час» не считается.
 */
private suspend fun spectrumTraffic(graph: AppGraph, nowMillis: Long): SpectrumTraffic {
    val startedAt = graph.serviceStatus.serviceStartedAtMillis
    return SpectrumTraffic(
        policy = graph.settings.spectrumPollPolicy.first().id,
        requests = graph.serviceStatus.spectrumRequests,
        payloadBytes = graph.serviceStatus.spectrumPayloadBytes,
        serviceUptimeMillis = if (startedAt > 0L) (nowMillis - startedAt).coerceAtLeast(0L) else 0L,
        storedSlices = graph.spectrogramRepository.count(),
    )
}

private suspend fun buildDebugReport(
    graph: AppGraph,
    context: android.content.Context,
): String {
    val now = System.currentTimeMillis()
    val sample = graph.measurementRepository.latestSample().first()
    val baselineState = graph.serviceStatus.baseline.value
    val deviation = graph.serviceStatus.deviation.value
    val thresholds = graph.settings.alarmThresholds.first()
    val unit = graph.settings.doseUnit.first()
    val profile = graph.profileRepository.activeProfile().first()
    val dose = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
    val status = MonitorStatus.of(
        doseRateMicroSvH = dose,
        baselineState = baselineState,
        deviation = deviation,
        thresholds = thresholds,
        nowMillis = now,
    )
    val connection = graph.serviceStatus.connection.value
    val connected = connection as? ConnectionState.Connected
    val spectrum = graph.spectrumHub.state.value.spectrum
    val background = graph.searchBackground.first()
    val fingerprint = profile?.id?.let { graph.fingerprintRepository.entity(it) }

    val snapshot = DebugSnapshot(
        appVersion = appVersionName(context) ?: "—",
        androidSdk = android.os.Build.VERSION.SDK_INT,
        deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        instrumentSerial = connected?.info?.serialNumber,
        instrumentFirmware = connected?.info?.firmware?.toString(),
        instrumentModel = connected?.info?.model?.displayName,
        instrumentModelKnown = connected?.info?.model?.let { it != DeviceModel.UNKNOWN } ?: false,
        spectrumFormatVersion = connected?.info?.spectrumFormatVersion,
        instrumentConfig = connected?.info?.configurationLines.orEmpty(),
        connectionFailure = graph.serviceStatus.lastConnectionFailure,
        spectrumChannels = spectrum?.counts?.size,
        spectrumCalibration = spectrum?.let {
            "a0=${it.a0} a1=${it.a1} a2=${it.a2}"
        },
        spectrumSeconds = spectrum?.durationSeconds,
        seqGapTotal = graph.serviceStatus.seqGapTotal,
        reconnectCount = graph.serviceStatus.reconnectCount,
        streamTicks = graph.streamTrace.snapshot(),
        chartPasses = graph.chartTrace.snapshot(),
        chartsRefreshedAgoSeconds = graph.serviceStatus.chartsRefreshedAtMillis
            ?.let { (now - it) / 1000L },
        chartsRefreshCount = graph.serviceStatus.chartsRefreshCount,
        clockCorrectionMillis = graph.serviceStatus.deviceClockCorrectionMillis,
        serviceRunning = graph.serviceStatus.serviceRunning.value,
        connection = when (connection) {
            is ConnectionState.Connected -> "подключён"
            is ConnectionState.Connecting -> "подключается"
            else -> "не подключён"
        },
        doseRateMicroSvH = dose,
        doseErrPercent = sample?.doseRateErr,
        countRate = sample?.countRate,
        sampleAgeSeconds = sample?.let { (now - it.timestamp) / 1000L },
        profileName = profile?.name,
        contextWording = graph.contextHub.state.value::class.simpleName,
        statusHeadline = statusHeadline(status),
        statusDetail = statusDetail(status, unit),
        baselineWording = when (val state = baselineState) {
            is BaselineState.Active -> baselineCollectedWording(state.baseline)
            is BaselineState.Learning -> learningWording(state)
            null -> "нет данных"
        },
        admissionWording = when (val admission = graph.serviceStatus.admission.value) {
            is Admission.Excluded -> "исключено: ${admission.reason.label}"
            else -> "измерения учитываются"
        },
        alarmL1MicroSvH = thresholds.l1MicroSvH,
        alarmL2MicroSvH = thresholds.l2MicroSvH,
        alarmPersistenceSeconds = thresholds.persistenceSeconds,
        alarmRelativeFactor = thresholds.relativeFactor,
        alarmSensitivity = graph.settings.alarmSensitivity.first().name,
        aboveUsualSinceMillis = deviation.aboveUsualSince,
        alarmConditionSinceMillis = deviation.alarmConditionSince,
        alertSinceMillis = deviation.alertSince,
        nowMillis = now,
        sampleCount = graph.database.sampleDao().count(),
        sessionCount = graph.sessionRepository.count(),
        spectrumCount = graph.database.spectrumDao().count(),
        minuteStatCount = graph.database.preAggregateDao().minuteCount(),
        hourSketchCount = graph.database.preAggregateDao().hourCount(0, now),
        doseUnit = DoseFormat.rateUnitLabel(unit),
        theme = graph.settings.themeSetting.first().label,
        searchFeedbackMode = SearchFeedbackMode.of(
            graph.settings.searchFeedbackMode.first(),
        )?.label ?: "нет",
        searchBackgroundWording = background
            ?.let { "${it.cps} с⁻¹ · ${it.window.samples}" }
            ?: "не записан",
        fingerprintWording = fingerprint
            ?.let { "создан ${REPORT_STAMP.format(Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()))}" }
            ?: "не создан",
        spectrumTraffic = spectrumTraffic(graph, now),
    )
    return DebugReport.build(snapshot) { millis ->
        REPORT_STAMP.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
}

private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
private val REPORT_STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

// --- Вид: тема ---

/**
 * Язык интерфейса.
 *
 * Переключается мгновенно, без пересоздания активности: язык — это выбор
 * каталога строк, а не системная локаль процесса. Список открытый — добавить
 * язык значит добавить каталог.
 */
@Composable
private fun LanguageSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val current by graph.settings.language.collectAsState(initial = AppLanguage.SYSTEM)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.languageTitle)
            Segmented(
                options = AppLanguage.entries.map {
                    if (it == AppLanguage.SYSTEM) strings.languageSystem else it.nativeName
                },
                selectedIndex = AppLanguage.entries.indexOf(current),
                onSelect = { index ->
                    scope.launch { graph.settings.setLanguage(AppLanguage.entries[index]) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


/**
 * Вариант оформления.
 *
 * Отдельно от светлой/тёмной темы: та отвечает на вопрос «сколько вокруг
 * света», а этот — «как это выглядит». 8-bit существует и в светлом, и в
 * тёмном варианте, и меняет только токены — цвета, шрифт и радиусы, — не
 * трогая ни формулировки, ни расчёты.
 */
@Composable
private fun SkinSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val current by graph.settings.skin.collectAsState(initial = AppSkin.TERMINAL)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.skinTitle)
            Segmented(
                options = AppSkin.entries.map { it.title(strings) },
                selectedIndex = AppSkin.entries.indexOf(current),
                onSelect = { index ->
                    scope.launch { graph.settings.setSkin(AppSkin.entries[index]) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThemeSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val theme by graph.settings.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.themeTitle)
            Segmented(
                options = ThemeSetting.entries.map { it.title(strings) },
                selectedIndex = ThemeSetting.entries.indexOf(theme),
                onSelect = { index ->
                    scope.launch { graph.settings.setThemeSetting(ThemeSetting.entries[index]) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = LocalAppTypography.current.labelSmall,
        color = LocalAppColors.current.ink2,
    )
}

// --- Тревоги ---

/**
 * Управление обучением baseline: ручная заморозка (условие 7 допуска, spec
 * §4.2) и grace period авто-контекста (spec §3.4). Оба параметра меняют то,
 * какие интервалы вообще попадают в статистику, поэтому объясняются словами.
 */
@Composable
private fun BaselineSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val frozen by graph.settings.baselineFrozen.collectAsState(initial = false)
    val grace by graph.settings.contextGraceMillis
        .collectAsState(initial = ContextConfig.DEFAULT_GRACE_MILLIS)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.usualBackgroundTitle)
            Hint(
                text = strings.usualBackgroundIntro,
                style = type.bodySmall,
                color = colors.ink2,
            )
            BlockToggleRow(strings.freezeLearning, frozen) { on ->
                scope.launch { graph.settings.setBaselineFrozen(on) }
            }
            AppDivider()
            Text(
                text = strings.graceNote,
                style = type.bodySmall,
                color = colors.ink2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                ContextConfig.ALLOWED_GRACE_MILLIS.forEach { millis ->
                    Chip(
                        text = strings.minutes(millis / 60_000),
                        color = if (millis == grace) colors.dataText else colors.ink2,
                        onClick = { scope.launch { graph.settings.setContextGraceMillis(millis) } },
                    )
                }
            }
        }
    }
}

// --- Прибор ---

/**
 * Сигналы САМОГО прибора: он пищит и вибрирует без телефона.
 *
 * Отдельно от звука приложения намеренно — это разные вещи, и человек должен
 * понимать, что произойдёт, когда телефон в кармане или выключен.
 *
 * Состояние показывается честно: прибор подтверждает запись, но опросить
 * текущее значение мы не умеем, поэтому до первой команды в этом сеансе
 * состояние НЕИЗВЕСТНО — и так и написано, вместо выключенного тумблера,
 * который выглядел бы как факт.
 */
@Composable
private fun DeviceSignalsSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val connection by graph.serviceStatus.connection.collectAsState()
    val applied by graph.deviceControlHub.applied.collectAsState()
    val desired by graph.deviceControlHub.desired.collectAsState()
    val failed by graph.deviceControlHub.failed.collectAsState()
    val connected = connection is ConnectionState.Connected

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.deviceSignals)
            Hint(
                text = strings.deviceSignalsNote,
                style = type.bodySmall,
                color = colors.ink2,
            )
            DeviceSignalRow(
                title = strings.deviceSound,
                state = applied.sound,
                asked = desired.sound,
                rejected = failed.sound == true,
                enabled = connected,
                onSet = { graph.deviceControlHub.request(DeviceControlHub.Command.Sound(it)) },
            )
            DeviceSignalRow(
                title = strings.deviceVibro,
                state = applied.vibro,
                asked = desired.vibro,
                rejected = failed.vibro == true,
                enabled = connected,
                onSet = { graph.deviceControlHub.request(DeviceControlHub.Command.Vibro(it)) },
            )
            Text(
                text = if (connected) {
                    strings.deviceSignalsUnknownNote
                } else {
                    strings.deviceSignalsOfflineNote
                },
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

/**
 * Продолжать ли измерение после перезагрузки телефона.
 *
 * Живёт рядом с прибором, а не в «Уведомлениях»: решение о том, поднимать ли
 * связь самому, относится к прибору. По умолчанию выключено — приложение не
 * начинает обмен по Bluetooth без спроса.
 */
@Composable
private fun BootSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val enabled by graph.settings.startOnBoot.collectAsState(initial = false)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.startOnBootTitle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = strings.startOnBootNote,
                    style = type.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Chip(
                    text = if (enabled) strings.on else strings.off,
                    color = if (enabled) colors.dataText else colors.ink2,
                    selected = enabled,
                    onClick = { scope.launch { graph.settings.setStartOnBoot(!enabled) } },
                )
            }
        }
    }
}

/**
 * Строка сигнала прибора: вкл, выкл, «неизвестно» и «прибор не принял».
 *
 * Переключатель стоит там, куда его поставил ЧЕЛОВЕК ([asked]), а подпись под
 * ним говорит, чем это кончилось. Раньше он показывал только подтверждённое
 * прибором состояние и молча отскакивал назад при любой неудаче — выглядело
 * это как «кнопка не работает», и узнать причину было неоткуда.
 */
@Composable
private fun DeviceSignalRow(
    title: String,
    state: Boolean?,
    asked: Boolean?,
    rejected: Boolean,
    enabled: Boolean,
    onSet: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = type.label, color = colors.ink)
            Text(
                text = when {
                    rejected -> strings.stateRejected
                    state == true -> strings.stateOnByApp
                    state == false -> strings.stateOffByApp
                    else -> strings.stateUnknown
                },
                style = type.footnote,
                color = when {
                    rejected -> colors.warn
                    state == null -> colors.muted
                    else -> colors.ink2
                },
            )
        }
        Segmented(
            options = listOf(strings.off, strings.on),
            selectedIndex = if ((asked ?: state) == true) 1 else 0,
            onSelect = { if (enabled) onSet(it == 1) },
            enabled = { enabled },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DeviceSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val rareData by graph.measurementRepository.latestRareData().collectAsState(initial = null)
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val freshness = Freshness.of(sample?.timestamp, nowMillis)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.instrumentTitle)

            when (val state = connection) {
                is ConnectionState.Connected -> {
                    InfoRow(strings.modelLabel, state.info.model.displayName)
                    InfoRow(strings.serialNumber, state.info.serialNumber)
                    InfoRow(strings.firmware, state.info.firmware.toString())
                    InfoRow("bluetooth", strings.bluetoothConnected)
                }
                is ConnectionState.Connecting -> InfoRow("bluetooth", strings.bluetoothConnecting)
                is ConnectionState.Reconnecting ->
                    InfoRow("bluetooth", strings.bluetoothReconnecting(state.attempt))
                ConnectionState.Disconnected ->
                    InfoRow("bluetooth", if (serviceRunning) strings.bluetoothNoLink else strings.serviceStopped)
            }

            rareData?.let { rare ->
                InfoRow(strings.instrumentBattery, "${rare.batteryPercent.toInt()} %")
                InfoRow(strings.temperature, "${rare.temperature.toInt()} °C")
            }

            when (freshness) {
                Freshness.NoData -> InfoRow(strings.stream, strings.noData)
                is Freshness.Fresh -> InfoRow(strings.stream, strings.streamActive)
                is Freshness.Stale -> Text(
                    text = freshnessLabel(freshness),
                    style = type.value,
                    color = colors.warn,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = type.bodySmall, color = colors.muted)
        Spacer(Modifier.weight(1f))
        Text(text = value, style = type.valueSmall, color = colors.ink)
    }
}

// --- Единицы ---

@Composable
private fun UnitsSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.unitsTitle)
            UnitOption(
                title = strings.unitMicroSv,
                selected = unit == DoseUnitSetting.MICRO_SIEVERT,
                onSelect = {
                    scope.launch { graph.settings.setDoseUnit(DoseUnitSetting.MICRO_SIEVERT) }
                },
            )
            UnitOption(
                title = strings.unitMicroR,
                selected = unit == DoseUnitSetting.MICRO_ROENTGEN,
                onSelect = {
                    scope.launch { graph.settings.setDoseUnit(DoseUnitSetting.MICRO_ROENTGEN) }
                },
            )
        }
    }
}

@Composable
private fun UnitOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            ),
    ) {
        RadioMark(selected)
        Text(text = title, style = type.value, color = colors.ink)
    }
}

// --- Интерфейс ---

/**
 * Кастомизация: видимость и порядок вкладок нижнего меню (Главная
 * фиксирована; минимум одна вкладка кроме неё) и необязательные блоки
 * Монитора. Дефолты совпадают с сегодняшним видом; сброс возвращает их.
 */
@Composable
private fun InterfaceSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val navRaw by graph.settings.navTabsRaw.collectAsState(initial = null)
    val entries = NavConfig.parse(navRaw)
    val blocks by graph.settings.monitorBlocks.collectAsState(initial = MonitorBlocks())
    var guardNote by remember { mutableStateOf(false) }

    fun save(newEntries: List<NavEntry>) {
        guardNote = false
        scope.launch { graph.settings.setNavTabsRaw(NavConfig.serialize(newEntries)) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.interfaceTitle)

            // Пояснения объясняют экран, а не измеряют: тому, кто носит прибор
            // каждый день, они через неделю становятся шумом. Состояния («нет
            // связи», «прибор не подключён») выключатель НЕ трогает — экран без
            // них выглядел бы работающим, когда он не работает.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = strings.hintsTitle,
                    style = type.label,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                val hints by graph.settings.hintsVisible.collectAsState(initial = true)
                Chip(
                    text = if (hints) strings.on else strings.off,
                    color = if (hints) colors.dataText else colors.ink2,
                    selected = hints,
                    onClick = { scope.launch { graph.settings.setHintsVisible(!hints) } },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Dimens.touchTarget),
            ) {
                Text(text = AppTab.HOME.title(LocalStrings.current), style = type.label, color = colors.ink)
                Spacer(Modifier.weight(1f))
                Text(text = strings.alwaysVisible, style = type.bodySmall, color = colors.muted)
            }
            entries.forEachIndexed { index, entry ->
                NavTabRow(
                    entry = entry,
                    canMoveUp = index > 0,
                    canMoveDown = index < entries.lastIndex,
                    onMove = { delta -> save(NavConfig.move(entries, entry.tab, delta)) },
                    onToggle = {
                        val toggled = NavConfig.toggle(entries, entry.tab)
                        if (toggled == null) guardNote = true else save(toggled)
                    },
                )
            }
            if (guardNote) {
                Text(
                    text = strings.atLeastOneTab,
                    style = type.bodySmall,
                    color = colors.warn,
                )
            }

            AppDivider()
            Hint(
                text = strings.monitorBlocksNote,
                style = type.bodySmall,
                color = colors.ink2,
            )
            BlockToggleRow(strings.blockTrend, blocks.trend) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(trend = it)) }
            }
            BlockToggleRow(strings.blockDoseToday, blocks.doseToday) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(doseToday = it)) }
            }
            BlockToggleRow(strings.blockCountChart, blocks.countRateChart) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(countRateChart = it)) }
            }
            BlockToggleRow(strings.blockHardnessChart, blocks.hardnessChart) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(hardnessChart = it)) }
            }
            BlockToggleRow(strings.blockStats, blocks.stats) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(stats = it)) }
            }

            AppDivider()
            AppButton(
                text = strings.resetInterface,
                onClick = {
                    guardNote = false
                    scope.launch { graph.settings.resetInterfaceCustomization() }
                },
            )
        }
    }
}

@Composable
private fun NavTabRow(
    entry: NavEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget),
    ) {
        Text(
            text = entry.tab.title(LocalStrings.current),
            style = type.label,
            color = if (entry.visible) colors.ink else colors.muted,
            modifier = Modifier.weight(1f),
        )
        ArrowButton(text = "↑", enabled = canMoveUp) { onMove(-1) }
        ArrowButton(text = "↓", enabled = canMoveDown) { onMove(1) }
        Text(
            text = if (entry.visible) strings.visible else strings.hidden,
            style = type.value,
            color = if (entry.visible) colors.ink else colors.muted,
            modifier = Modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = Dimens.touchTarget)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .wrapContentHeight(),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ArrowButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Text(
        text = text,
        style = type.title,
        color = if (enabled) colors.ink2 else colors.line,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = Dimens.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .wrapContentHeight(),
    )
}

@Composable
internal fun BlockToggleRow(title: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onChange(!enabled) },
            ),
    ) {
        Text(
            text = title,
            style = type.bodySmall,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (enabled) strings.onShort else strings.offShort,
            style = type.value,
            color = if (enabled) colors.ink else colors.muted,
        )
    }
}

// --- Лицензии ---
