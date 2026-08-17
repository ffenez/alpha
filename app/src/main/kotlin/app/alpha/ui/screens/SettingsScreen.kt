@file:OptIn(ExperimentalLayoutApi::class)

package app.alpha.ui.screens

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
import app.alpha.ui.theme.Motion
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
import app.alpha.R
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import app.alpha.ui.logic.DragReorder
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import app.alpha.ui.logic.SettingsSearch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import app.alpha.AppGraph
import app.alpha.baseline.AlarmSensitivity
import app.alpha.baseline.AlarmThresholds
import app.alpha.baseline.BaselineState
import app.alpha.baseline.alarmThresholds
import app.alpha.context.ContextConfig
import app.alpha.context.NetworkIdentity
import app.alpha.data.AppSettings
import app.alpha.data.DoseUnitSetting
import app.alpha.data.MonitorBlocks
import app.alpha.data.ThemeSetting
import app.alpha.data.db.ProfileEntity
import app.alpha.data.db.ProfileNetworkEntity
import app.alpha.device.ConnectionState
import app.alpha.device.DeviceModel
import app.alpha.service.Notifications
import app.alpha.ui.components.NavArrow
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.AppTab
import app.alpha.ui.components.AppTextField
import app.alpha.ui.components.AppSwitch
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChoiceSettingRow
import app.alpha.ui.components.SettingRow
import app.alpha.ui.components.SettingsDivider
import app.alpha.ui.components.SettingsSection
import app.alpha.ui.components.SwitchSettingRow
import app.alpha.ui.components.SettingsTopBar
import app.alpha.ui.components.Chip
import app.alpha.ui.components.RadioMark
import app.alpha.service.DeviceControlHub
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
import app.alpha.ui.logic.DoseTint
import app.alpha.ui.logic.MapAnchors
import app.alpha.ui.logic.MapColorScale
import app.alpha.ui.logic.TrackMap
import app.alpha.ui.logic.DoseFormat
import app.alpha.ui.logic.NavConfig
import app.alpha.ui.logic.ProfileTree
import app.alpha.ui.logic.ProfileDeletion
import app.alpha.ui.logic.NavEntry
import app.alpha.ui.logic.Freshness
import app.alpha.ui.logic.baselineCollectedWording
import app.alpha.ui.logic.ReleaseNotes
import app.alpha.ui.logic.freshnessLabel
import app.alpha.ui.logic.heldWording
import app.alpha.ui.logic.learningWording
import app.alpha.ui.text.AppLanguage
import app.alpha.ui.text.BackupCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.CalibrationCatalogue
import app.alpha.ui.text.NotificationCatalogue
import app.alpha.ui.text.ReleaseCatalogue
import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings
import app.alpha.ui.theme.AppSkin
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.alpha.baseline.Admission
import app.alpha.data.export.CrashLog
import app.alpha.data.export.DebugBundle
import app.alpha.data.export.RcResultData
import app.alpha.data.export.RcSpectrum
import app.alpha.data.export.RcXml
import app.alpha.data.export.SpectrumExport
import app.alpha.data.export.DebugReport
import app.alpha.data.export.DebugSnapshot
import app.alpha.data.export.TrackDiagnostics
import app.alpha.data.export.SpectrumTraffic
import app.alpha.device.DoseUnits
import app.alpha.ui.logic.MonitorStatus
import app.alpha.ui.logic.SearchFeedbackMode
import app.alpha.ui.logic.statusDetail
import app.alpha.ui.logic.statusHeadline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Разделы настроек, сгруппированные по тому, чем они управляют: измерение
 * (тревоги, профили), поведение приложения (вид, уведомления), служебное.
 * Обычный фон принадлежит профилям: он относится к МЕСТУ.
 *
 * Диагностика лежит внутри «О приложении», а не на первом уровне.
 */
private enum class SettingsGroup {
    MEASUREMENT,
    APP,
    DEVICE,
    SYSTEM,
    ;

    fun title(strings: Strings): String = when (this) {
        MEASUREMENT -> strings.groupMeasurement
        APP -> strings.groupApp
        DEVICE -> strings.groupDevice
        SYSTEM -> strings.groupSystem
    }
}

private enum class SettingsCategory(val group: SettingsGroup) {
    ALARMS(SettingsGroup.MEASUREMENT),
    PROFILES(SettingsGroup.MEASUREMENT),
    SOUND(SettingsGroup.APP),
    VIEW(SettingsGroup.APP),
    DEVICE(SettingsGroup.DEVICE),
    BACKUP(SettingsGroup.SYSTEM),
    DATA(SettingsGroup.SYSTEM),
    ABOUT(SettingsGroup.SYSTEM),
    ;

    fun title(s: Strings): String = when (this) {
        ALARMS -> s.settingsAlarms
        PROFILES -> s.settingsProfiles
        SOUND -> s.settingsNotifications
        VIEW -> s.settingsView
        DEVICE -> s.settingsDevice
        BACKUP -> s.settingsBackup
        DATA -> s.settingsData
        ABOUT -> s.settingsAbout
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
    // Диагностика калибровки — экран внутри «Прибора» со своим BackHandler,
    // поэтому системный жест закрывает сначала её.
    var calibrationOpen by rememberSaveable { mutableStateOf(false) }

    // Системная «назад» делает один шаг вверх, как и кнопка на экране.
    BackHandler(enabled = category != null) {
        if (calibrationOpen) calibrationOpen = false else category = null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // На широком экране список разделов и открытый раздел стоят рядом.
        val listDetail = maxWidth >= LIST_DETAIL_MIN_WIDTH
        val open = category
        if (listDetail) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(LIST_PANE_WIDTH)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.space3)
                        .padding(bottom = Dimens.space3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    SettingsTopBar(title = strings.settings, onBack = onBack)
                    SettingsRoot(graph, selected = open) { category = it }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.space3)
                        .padding(bottom = Dimens.space3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    if (open == null) {
                        // Пустая половина называет первое действие.
                        Text(
                            text = strings.settingsPickSection,
                            style = LocalAppTypography.current.body,
                            color = colors.muted,
                            modifier = Modifier.padding(
                                top = Dimens.space6,
                                start = Dimens.space2,
                            ),
                        )
                    } else {
                        SettingsTopBar(
                            title = open.title(strings),
                            onBack = {
                                if (calibrationOpen) calibrationOpen = false else category = null
                            },
                        )
                        SettingsDetail(
                            graph = graph,
                            category = open,
                            calibrationOpen = calibrationOpen,
                            onOpenCalibration = { calibrationOpen = true },
                            onCloseCalibration = { calibrationOpen = false },
                        )
                    }
                }
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.space3)
                .padding(bottom = Dimens.space3),
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            // Один заголовок на экран, он же кнопка возврата.
            SettingsTopBar(
                title = open?.title(strings) ?: strings.settings,
                onBack = {
                    when {
                        calibrationOpen -> calibrationOpen = false
                        open == null -> onBack()
                        else -> category = null
                    }
                },
            )

            AnimatedContent(
                targetState = open,
                transitionSpec = {
                    (fadeIn(Motion.screen()) + scaleIn(Motion.screen(), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(Motion.SCREEN_EXIT_MILLIS)))
                },
                label = "settingsCategory",
            ) { openCategory ->
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                    if (openCategory == null) {
                        SettingsRoot(graph, selected = null) { category = it }
                    } else {
                        SettingsDetail(
                            graph = graph,
                            category = openCategory,
                            calibrationOpen = calibrationOpen,
                            onOpenCalibration = { calibrationOpen = true },
                            onCloseCalibration = { calibrationOpen = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ширина, с которой список разделов и открытый раздел помещаются рядом.
 * **Инженерный параметр**: 720 dp — планшет и телефон в ландшафте; уже неё
 * колонка раздела становится теснее телефонной.
 */
private val LIST_DETAIL_MIN_WIDTH = 720.dp

/** Ширина колонки со списком разделов на широком экране. */
private val LIST_PANE_WIDTH = 320.dp

/** Содержимое одного раздела настроек — одно и то же в обеих раскладках. */
@Composable
private fun SettingsDetail(
    graph: AppGraph,
    category: SettingsCategory,
    calibrationOpen: Boolean,
    onOpenCalibration: () -> Unit,
    onCloseCalibration: () -> Unit,
) {
    when (category) {
        SettingsCategory.ALARMS -> AlarmsSection(graph)
        // Фон принадлежит месту: профили и обучение фона — один раздел.
        SettingsCategory.PROFILES -> {
            ProfilesSection(graph)
            BaselineSection(graph)
        }
        SettingsCategory.SOUND -> SoundSection(graph)
        SettingsCategory.VIEW -> InterfaceScreen(graph)
        SettingsCategory.DEVICE -> {
            if (calibrationOpen) {
                CalibrationScreen(graph, onCloseCalibration)
            } else {
                DeviceScreen(graph, onOpenCalibration)
            }
        }
        // Перенос данных: копия целиком, восстановление, занятое место.
        SettingsCategory.BACKUP -> BackupSection(graph)
        // Диагностика: то, что нужно для разбора жалобы.
        SettingsCategory.DATA -> {
            SpectrumRateSection(graph)
            DebugSection(graph)
        }
        SettingsCategory.ABOUT -> LicensesSection()
    }
}

/**
 * Корень настроек: четыре группы и текущее состояние каждой строки. Значение
 * справа отвечает на вопрос «что стоит сейчас» без входа в раздел.
 */
@Composable
private fun SettingsRoot(
    graph: AppGraph,
    selected: SettingsCategory?,
    onOpen: (SettingsCategory) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val summaries = settingsSummaries(graph)
    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(strings) { settingsSearchIndex(strings) }
    val hits = remember(query, index) { SettingsSearch.find(query, index) }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        // Поиск ищет словом, которым настройку называют: «звук», «фон»,
        // «батарея».
        AppTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = strings.settingsSearchPlaceholder,
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isNotBlank()) {
            if (hits.isEmpty()) {
                Text(
                    text = strings.settingsSearchEmpty,
                    style = type.footnote,
                    color = colors.muted,
                    modifier = Modifier.padding(start = Dimens.space2),
                )
            } else {
                SettingsSection {
                    hits.forEachIndexed { position, hit ->
                        if (position > 0) SettingsDivider()
                        SettingRow(
                            title = hit.title,
                            subtitle = hit.section,
                            onClick = {
                                SettingsCategory.entries
                                    .firstOrNull { it.name == hit.categoryId }
                                    ?.let(onOpen)
                            },
                        )
                    }
                }
            }
            return@Column
        }
        for (group in SettingsGroup.entries) {
            val items = SettingsCategory.entries.filter { it.group == group }
            if (items.isEmpty()) continue
            SettingsSection(title = group.title(strings)) {
                items.forEachIndexed { index, entry ->
                    if (index > 0) SettingsDivider()
                    SettingRow(
                        title = summaries.title(entry) ?: entry.title(strings),
                        value = summaries.value(entry),
                        valueHighlighted = entry == selected,
                        onClick = { onOpen(entry) },
                    )
                }
            }
        }
    }
}

/**
 * Индекс поиска по настройкам: слово запроса ведёт в раздел. Подписи берутся
 * из каталога строк, слова поиска — свои для каждого языка.
 */
private fun settingsSearchIndex(strings: Strings): List<SettingsSearch.Entry> = listOf(
    SettingsSearch.Entry(
        categoryId = SettingsCategory.ALARMS.name,
        title = strings.settingsAlarms,
        section = strings.groupMeasurement,
        keywords = strings.searchWordsAlarms,
    ),
    SettingsSearch.Entry(
        categoryId = SettingsCategory.PROFILES.name,
        title = strings.settingsProfiles,
        section = strings.groupMeasurement,
        keywords = strings.searchWordsProfiles,
    ),
    SettingsSearch.Entry(
        categoryId = SettingsCategory.SOUND.name,
        title = strings.settingsNotifications,
        section = strings.groupApp,
        keywords = strings.searchWordsSound,
    ),
    SettingsSearch.Entry(
        categoryId = SettingsCategory.VIEW.name,
        title = strings.settingsView,
        section = strings.groupApp,
        keywords = strings.searchWordsView,
    ),
    SettingsSearch.Entry(
        categoryId = SettingsCategory.DEVICE.name,
        title = strings.settingsDevice,
        section = strings.groupDevice,
        keywords = strings.searchWordsDevice,
    ),
    SettingsSearch.Entry(
        categoryId = SettingsCategory.BACKUP.name,
        title = strings.settingsBackup,
        section = strings.groupSystem,
        keywords = strings.searchWordsBackup,
    ),
    SettingsSearch.Entry(
        categoryId = SettingsCategory.DATA.name,
        title = strings.settingsData,
        section = strings.groupSystem,
        keywords = strings.searchWordsData,
    ),
    SettingsSearch.Entry(
        categoryId = SettingsCategory.ABOUT.name,
        title = strings.settingsAbout,
        section = strings.groupSystem,
        keywords = strings.searchWordsAbout,
    ),
) + leafSearchIndex(strings)

/**
 * Отдельные настройки, а не только разделы: ищут то, что собираются менять
 * («тема», «единицы», «язык»), и находка называет саму настройку, а раздел
 * стоит подписью. В индекс попадает то, что меняют осознанно.
 */
private fun leafSearchIndex(strings: Strings): List<SettingsSearch.Entry> {
    val backup = BackupCatalogue.of(strings.language)
    return listOf(
        SettingsSearch.Entry(
            categoryId = SettingsCategory.VIEW.name,
            title = strings.languageTitle,
            section = strings.settingsView,
            keywords = listOf("язык", "language", "русский", "english", "перевод"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.VIEW.name,
            title = strings.themeTitle,
            section = strings.settingsView,
            keywords = listOf("тема", "тёмная", "светлая", "theme", "dark", "light", "оформление"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.VIEW.name,
            title = strings.unitsTitle,
            section = strings.settingsView,
            keywords = listOf("единицы", "зиверт", "рентген", "мкзв", "мкр", "units", "sievert"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.VIEW.name,
            title = strings.scaleTitle,
            section = strings.settingsView,
            keywords = listOf("шрифт", "масштаб", "крупнее", "мельче", "font", "size"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.VIEW.name,
            title = strings.homeLayoutTitle,
            section = strings.settingsView,
            keywords = listOf("вкладки", "порядок", "главная", "плитки", "tabs", "layout"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.ALARMS.name,
            title = strings.thresholdNow,
            section = strings.settingsAlarms,
            keywords = listOf("порог", "тревога", "чувствительность", "threshold", "alarm"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.BACKUP.name,
            title = backup.createBackup,
            section = strings.settingsBackup,
            keywords = listOf("копия", "резервная", "бэкап", "backup", "перенос", "сохранить"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.BACKUP.name,
            title = backup.restoreBackup,
            section = strings.settingsBackup,
            keywords = listOf("восстановить", "restore", "вернуть", "импорт"),
        ),
        SettingsSearch.Entry(
            categoryId = SettingsCategory.DATA.name,
            title = strings.retentionTitle,
            section = strings.settingsData,
            keywords = listOf("хранение", "история", "удаление", "срок", "память", "retention"),
        ),
    )
}

/** Текущее состояние каждой категории — то, что видно до входа в неё. */
@Composable
private fun settingsSummaries(graph: AppGraph): SettingsSummaries {
    val strings = LocalStrings.current
    val sensitivity by graph.settings.alarmSensitivity
        .collectAsState(initial = AlarmSensitivity.NORMAL)
    val feedbackId by graph.settings.searchFeedbackMode.collectAsState(initial = null)
    val theme by graph.settings.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)
    val fontScale by graph.settings.fontScalePercent.collectAsState(initial = 100)
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val activeProfileId by graph.settings.activeProfileId.collectAsState(initial = null)
    val connection by graph.serviceStatus.connection.collectAsState()
    val rareData by graph.measurementRepository.latestRareData().collectAsState(initial = null)
    val connected = connection as? ConnectionState.Connected
    val battery = rareData?.batteryPercent?.toInt()
    val profileName = profiles.firstOrNull { it.id == activeProfileId }?.name
    return SettingsSummaries(
        alarms = when (sensitivity) {
            AlarmSensitivity.NORMAL -> strings.sensitivityNormal
            AlarmSensitivity.HIGH -> strings.sensitivityHigh
            AlarmSensitivity.CUSTOM -> strings.sensitivityCustom
        },
        profiles = profileName ?: strings.settingsProfilesNone,
        sound = (SearchFeedbackMode.of(feedbackId) ?: SearchFeedbackMode.OFF).title(strings),
        view = listOfNotNull(
            theme.title(strings),
            "$fontScale %".takeIf { fontScale != 100 },
        ).joinToString(" · "),
        deviceTitle = connected?.info?.model?.displayName,
        device = when {
            connected != null && battery != null -> "${strings.bluetoothConnected} · $battery %"
            connected != null -> strings.bluetoothConnected
            else -> strings.bluetoothNoLink
        },
        about = ReleaseNotes.current,
    )
}

private class SettingsSummaries(
    val alarms: String,
    val profiles: String,
    val sound: String,
    val view: String,
    val deviceTitle: String?,
    val device: String,
    val about: String,
) {
    fun value(category: SettingsCategory): String? = when (category) {
        SettingsCategory.ALARMS -> alarms
        SettingsCategory.PROFILES -> profiles
        SettingsCategory.SOUND -> sound
        SettingsCategory.VIEW -> view
        SettingsCategory.DEVICE -> device
        SettingsCategory.BACKUP -> null
        SettingsCategory.DATA -> null
        SettingsCategory.ABOUT -> about
    }

    /** Прибор называет себя своим именем, когда оно известно. */
    fun title(category: SettingsCategory): String? =
        if (category == SettingsCategory.DEVICE) deviceTitle else null
}

@Composable
private fun SoundSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val modeId by graph.settings.searchFeedbackMode.collectAsState(initial = null)
    val mode = SearchFeedbackMode.of(modeId) ?: SearchFeedbackMode.OFF
    val energyTone by graph.settings.searchEnergyToneEnabled.collectAsState(initial = false)

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        SettingsSection(title = strings.searchFeedbackTitle) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.space3,
                    vertical = Dimens.space2,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                // Отклик меняют в поле, поэтому выбор стоит здесь целиком.
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
                        SearchFeedbackMode.CLICKS -> strings.feedbackClicks
                        SearchFeedbackMode.TONE -> strings.feedbackTone
                        SearchFeedbackMode.VIBRO -> strings.feedbackVibro
                    },
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            // Зависимая настройка появляется, а не тускнеет: высота тона
            // относится только к кликам.
            AnimatedVisibility(
                visible = mode == SearchFeedbackMode.CLICKS,
                enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
                exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
            ) {
                Column {
                    SettingsDivider()
                    SwitchSettingRow(
                        title = strings.energyTone,
                        subtitle = strings.energyToneNote,
                        checked = energyTone,
                        onChange = { on ->
                            scope.launch { graph.settings.setSearchEnergyToneEnabled(on) }
                        },
                    )
                }
            }
        }
        SettingsSection(title = strings.alarmTitle) {
            AlarmSoundRow()
        }
    }
}

// --- Отладка ---

/**
 * Отчёт о состоянии приложения для разбора наблюдений. Выключен по умолчанию:
 * инструмент разбора, а не повседневная функция.
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
    // Описание действий и ожидаемого результата из отчёта не восстанавливается.
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
 * Снимок состояния для отчёта. Строки статуса берутся теми же функциями, что
 * рисуют экран.
 */
/**
 * Архив отладки: опись, отчёт о состоянии, описание проблемы и спектры в
 * собственном формате.
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

    // Пустой журнал падений кладётся тоже: «падений не записано» — это ответ,
    // а отсутствие файла читалось бы как потеря.
    entries += DebugBundle.Entry(
        name = CrashLog.FILE_NAME,
        content = CrashLog.bundleText(
            runCatching { graph.crashLogFile.readText() }.getOrDefault(""),
        ),
    )

    // Спектр в формате RC-XML: файл открывается другими программами.
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
    // Описи в архиве нет: имена файлов называют содержимое, оговорка
    // приватности стоит на экране создания.
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
        track = trackDiagnostics(graph, now),
    )
    return DebugReport.build(snapshot) { millis ->
        REPORT_STAMP.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
}

/**
 * Состояние записи следа для отчёта, без единой координаты: дошла ли подписка
 * до системы, приходят ли фиксы и доезжают ли они до базы.
 */
private fun trackDiagnostics(graph: AppGraph, nowMillis: Long): TrackDiagnostics {
    val diagnostics = graph.serviceStatus.trackDiagnostics.value
    return TrackDiagnostics(
        recording = graph.serviceStatus.trackRecording.value != null,
        state = graph.serviceStatus.trackLocation.value.name.lowercase(),
        precise = diagnostics.precise,
        providersEnabled = diagnostics.enabled,
        providersSubscribed = diagnostics.providers,
        fixes = diagnostics.fixes,
        points = diagnostics.points,
        lastFixAgeSeconds = diagnostics.lastFixMillis?.let { (nowMillis - it) / 1000L },
        lastProvider = diagnostics.lastProvider,
        lastAccuracyMeters = diagnostics.lastAccuracyMeters,
    )
}

private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
private val REPORT_STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

// --- Интерфейс ---

/**
 * Настройки → Интерфейс. Четыре группы: язык, оформление, тема и единицы —
 * «как выглядит»; масштаб — «какого размера»; цвета — «что подсвечивать»;
 * Главная — «что на ней есть». Редкий выбор стоит строкой со значением и
 * раскрывается списком.
 */
@Composable
private fun InterfaceScreen(graph: AppGraph) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val language by graph.settings.language.collectAsState(initial = AppLanguage.SYSTEM)
    val skin by graph.settings.skin.collectAsState(initial = AppSkin.TERMINAL)
    val theme by graph.settings.themeSetting.collectAsState(initial = ThemeSetting.SYSTEM)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        SettingsSection(title = strings.interfaceTitle) {
            ChoiceSettingRow(
                title = strings.languageTitle,
                options = AppLanguage.entries,
                selected = language,
                label = {
                    if (it == AppLanguage.SYSTEM) strings.languageSystem else it.nativeName
                },
                onSelect = { scope.launch { graph.settings.setLanguage(it) } },
            )
            SettingsDivider()
            // Оформление и тема — разные вещи: тема отвечает на «сколько
            // вокруг света», оформление — «как это выглядит».
            ChoiceSettingRow(
                title = strings.skinTitle,
                options = AppSkin.entries,
                selected = skin,
                label = { it.title(strings) },
                onSelect = { scope.launch { graph.settings.setSkin(it) } },
            )
            SettingsDivider()
            // Тему меняют часто, поэтому она стоит выбором целиком.
            SettingsChoiceRowInline(
                title = strings.themeTitle,
                options = ThemeSetting.entries.map { it.title(strings) },
                selectedIndex = ThemeSetting.entries.indexOf(theme),
                onSelect = { index ->
                    scope.launch {
                        graph.settings.setThemeSetting(ThemeSetting.entries[index])
                    }
                },
            )
            SettingsDivider()
            ChoiceSettingRow(
                title = strings.unitsTitle,
                options = listOf(DoseUnitSetting.MICRO_SIEVERT, DoseUnitSetting.MICRO_ROENTGEN),
                selected = unit,
                label = {
                    if (it == DoseUnitSetting.MICRO_SIEVERT) strings.unitMicroSv else strings.unitMicroR
                },
                onSelect = { scope.launch { graph.settings.setDoseUnit(it) } },
            )
        }
        ScaleSection(graph)
        ColorsSection(graph)
        HomeLayoutSection(graph)
    }
}

/**
 * Частый выбор прямо в строке: название слева, сегменты справа — для настроек
 * с двумя-тремя вариантами.
 */
@Composable
private fun SettingsChoiceRowInline(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    weight: Float = 1.4f,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .padding(horizontal = Dimens.space3, vertical = Dimens.space2),
    ) {
        Text(
            text = title,
            style = type.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Segmented(
            options = options,
            selectedIndex = selectedIndex.coerceAtLeast(0),
            onSelect = onSelect,
            modifier = Modifier.weight(weight),
        )
    }
}

/**
 * Цвета: чем приложение подсвечивает отклонение и чем красит след на карте.
 * Зависимые настройки появляются, а не тускнеют.
 */
@Composable
private fun ColorsSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val hints by graph.settings.hintsVisible.collectAsState(initial = false)
    val tint by graph.settings.doseTint.collectAsState(initial = true)
    val factor by graph.settings.doseTintFactor.collectAsState(initial = DoseTint.DEFAULT_FACTOR)
    val mapScale by graph.settings.mapColorScale.collectAsState(initial = MapColorScale.ABSOLUTE)

    SettingsSection(title = strings.colorsTitle) {
        // Выключатель не трогает состояния («нет связи», «прибор не
        // подключён»): без них экран выглядел бы работающим.
        SwitchSettingRow(
            title = strings.hintsTitle,
            subtitle = strings.hintsNote,
            checked = hints,
            onChange = { on -> scope.launch { graph.settings.setHintsVisible(on) } },
        )
        SettingsDivider()
        // Цвет главного числа включается отдельно.
        SwitchSettingRow(
            title = strings.doseTintTitle,
            subtitle = strings.doseTintNote,
            checked = tint,
            onChange = { on -> scope.launch { graph.settings.setDoseTint(on) } },
        )
        AnimatedVisibility(
            visible = tint,
            enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
            exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
        ) {
            Column {
                SettingsDivider()
                // Множитель, а не абсолютное значение: у каждого места свой
                // уровень.
                SettingsChoiceRowInline(
                    title = strings.doseTintFactorTitle,
                    options = DoseTint.FACTORS.map {
                        strings.doseTintFactorLabel(DoseTint.factorLabel(it))
                    },
                    selectedIndex = DoseTint.FACTORS.indexOfFirst { it == factor },
                    onSelect = { index ->
                        scope.launch { graph.settings.setDoseTintFactor(DoseTint.FACTORS[index]) }
                    },
                )
            }
        }
        SettingsDivider()
        // Чем заданы границы цвета следа на карте. Растяжение по маршруту
        // находит малые различия, но красит ровную прогулку во всю шкалу.
        ChoiceSettingRow(
            title = strings.mapScaleTitle,
            options = MapColorScale.entries,
            selected = mapScale,
            label = {
                when (it) {
                    MapColorScale.ABSOLUTE -> strings.mapScaleAbsolute
                    MapColorScale.ROUTE_CONTRAST -> strings.mapScaleContrast
                    MapColorScale.MANUAL -> strings.mapScaleManual
                }
            },
            onSelect = { scope.launch { graph.settings.setMapColorScale(it) } },
        )
        AnimatedVisibility(
            visible = mapScale == MapColorScale.MANUAL,
            enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
            exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
        ) {
            val doseAnchors by graph.settings.manualDoseAnchors
                .collectAsState(initial = TrackMap.DEFAULT_MANUAL_DOSE)
            val cpsAnchors by graph.settings.manualCpsAnchors
                .collectAsState(initial = TrackMap.DEFAULT_MANUAL_CPS)
            var doseText by remember(doseAnchors) { mutableStateOf(MapAnchors.format(doseAnchors)) }
            var cpsText by remember(cpsAnchors) { mutableStateOf(MapAnchors.format(cpsAnchors)) }
            Column(
                modifier = Modifier.padding(
                    start = Dimens.space3,
                    end = Dimens.space3,
                    bottom = Dimens.space3,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                // Границы ручной шкалы — по строке на величину: у дозы и у
                // счёта они физически разные.
                AppTextField(
                    value = doseText,
                    onValueChange = {
                        doseText = it
                        scope.launch { graph.settings.setManualDoseAnchors(it) }
                    },
                    placeholder = strings.mapScaleDoseAnchors,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = cpsText,
                    onValueChange = {
                        cpsText = it
                        scope.launch { graph.settings.setManualCpsAnchors(it) }
                    },
                    placeholder = strings.mapScaleCpsAnchors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = strings.mapScaleManualHint,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
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
            // Настройка называет состояние «обучение идёт», а не выключение
            // через включение; это состояние по умолчанию.
            BlockToggleRow(
                title = strings.updateBackground,
                enabled = !frozen,
                subtitle = strings.updateBackgroundNote,
            ) { on ->
                scope.launch { graph.settings.setBaselineFrozen(!on) }
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
 * Настройки → Прибор: состояние связи, поведение после перезагрузки, сигналы
 * прибора и спектральный анализ. Сверху — сводка состояния (имя, связь,
 * батарея, температура, поток).
 */
@Composable
private fun DeviceScreen(graph: AppGraph, onOpenCalibration: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val startOnBoot by graph.settings.startOnBoot.collectAsState(initial = false)

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        DeviceStatusCard(graph)
        SettingsSection {
            SwitchSettingRow(
                title = strings.startOnBootTitle,
                subtitle = strings.startOnBootNote,
                checked = startOnBoot,
                onChange = { on -> scope.launch { graph.settings.setStartOnBoot(on) } },
            )
        }
        DeviceSignalsSection(graph)
        SpectralAnalysisSection(graph, onOpenCalibration)
    }
}

/**
 * Сводка о приборе: имя, связь и живые числа. Числа стоят рядом, а не
 * строками «подпись — значение» на всю ширину.
 */
@Composable
private fun DeviceStatusCard(graph: AppGraph) {
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
    val connected = connection as? ConnectionState.Connected

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = connected?.info?.model?.displayName ?: strings.instrumentTitle,
                style = type.title,
                color = colors.ink,
            )
            // Состояние связи несут и точка, и слово: одним цветом различие не
            // переносится.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(
                    text = if (connected != null) "●" else "○",
                    style = type.footnote,
                    color = if (connected != null) colors.data else colors.muted,
                )
                Text(
                    text = when (val state = connection) {
                        is ConnectionState.Connected -> strings.bluetoothConnected
                        is ConnectionState.Connecting -> strings.bluetoothConnecting
                        is ConnectionState.Reconnecting ->
                            strings.bluetoothReconnecting(state.attempt)
                        ConnectionState.Disconnected ->
                            if (serviceRunning) strings.bluetoothNoLink else strings.serviceStopped
                    },
                    style = type.body,
                    color = if (connected != null) colors.ink else colors.ink2,
                )
            }
            val cells = buildList {
                rareData?.let { rare ->
                    add(StatCell("${rare.batteryPercent.toInt()} %", strings.instrumentBattery))
                    add(StatCell("${rare.temperature.toInt()} °C", strings.temperature))
                }
                add(
                    StatCell(
                        when (freshness) {
                            Freshness.NoData -> strings.noData
                            is Freshness.Fresh -> strings.streamActive
                            is Freshness.Stale -> freshnessLabel(freshness)
                        },
                        strings.stream,
                    ),
                )
            }
            StatGrid(cells = cells)
            connected?.let { state ->
                // Серийник и прошивка — одной приглушённой строкой: нужны для
                // переписки с поддержкой и отчёта.
                Text(
                    text = "${strings.serialNumber} ${state.info.serialNumber} · " +
                        "${strings.firmware} ${state.info.firmware}",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

/**
 * Сигналы самого прибора: он пищит и вибрирует без телефона.
 *
 * Текущее значение с прибора не читается, поэтому до первой команды в сеансе
 * состояние НЕИЗВЕСТНО и подписано словом, а не выключенным тумблером.
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

    SettingsSection(title = strings.deviceSignals) {
        DeviceSignalRow(
            title = strings.deviceSound,
            state = applied.sound,
            asked = desired.sound,
            rejected = failed.sound == true,
            enabled = connected,
            onSet = { graph.deviceControlHub.request(DeviceControlHub.Command.Sound(it)) },
        )
        SettingsDivider()
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
            modifier = Modifier.padding(
                start = Dimens.space3,
                end = Dimens.space3,
                bottom = Dimens.space2,
            ),
        )
    }
}

/**
 * Спектральный анализ: энергетические окна и проверка калибровки — обе
 * настройки о том, как читается спектр.
 */
@Composable
private fun SpectralAnalysisSection(graph: AppGraph, onOpenCalibration: () -> Unit) {
    val strings = LocalStrings.current
    val c = CalibrationCatalogue.of(LocalStrings.current.language)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        SpectralRangesSection(graph)
        SettingsSection {
            SettingRow(
                title = c.entryTitle,
                subtitle = c.entrySubtitle,
                onClick = onOpenCalibration,
            )
        }
    }
}

/**
 * Строка сигнала прибора: вкл, выкл, «неизвестно» и «прибор не принял».
 * Переключатель показывает запрошенное состояние ([asked]), подпись под ним —
 * исход команды.
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
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .padding(horizontal = Dimens.space3, vertical = Dimens.space2),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = type.body, color = colors.ink)
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

/**
 * Настройка Главной: какие вкладки видны, в каком они порядке и какие блоки
 * показывает Монитор. Сверху вкладки в их порядке, ниже блоки Главной.
 */
@Composable
private fun HomeLayoutSection(graph: AppGraph) {
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

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        SettingsSection(title = strings.homeLayoutTitle) {
            // Строки «Главная — всегда видна» нет: невыключаемая вкладка —
            // не настройка. Порядок меняется перетаскиванием.
            var rowHeightPx by remember { mutableFloatStateOf(0f) }
            var dragging by remember { mutableStateOf<AppTab?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            entries.forEachIndexed { index, entry ->
                if (index > 0) SettingsDivider()
                NavTabRow(
                    entry = entry,
                    dragging = dragging == entry.tab,
                    dragOffsetPx = if (dragging == entry.tab) dragOffset else 0f,
                    onMeasured = { height -> if (rowHeightPx == 0f) rowHeightPx = height },
                    onDragStart = {
                        dragging = entry.tab
                        dragOffset = 0f
                    },
                    onDrag = { delta ->
                        dragOffset += delta
                        val steps = DragReorder.steps(dragOffset, rowHeightPx)
                        if (steps != 0) {
                            val from = entries.indexOfFirst { it.tab == entry.tab }
                            val to = DragReorder.target(from, steps, entries.size)
                            if (to != from) {
                                dragOffset -= (to - from) * rowHeightPx
                                save(NavConfig.move(entries, entry.tab, to - from))
                            }
                        }
                    },
                    onDragEnd = {
                        dragging = null
                        dragOffset = 0f
                    },
                    onToggle = { _ ->
                        val toggled = NavConfig.toggle(entries, entry.tab)
                        if (toggled == null) guardNote = true else save(toggled)
                    },
                )
            }
            if (guardNote) {
                Text(
                    text = strings.atLeastOneTab,
                    style = type.footnote,
                    color = colors.warn,
                    modifier = Modifier.padding(
                        start = Dimens.space3,
                        end = Dimens.space3,
                        bottom = Dimens.space2,
                    ),
                )
            }
        }
        SettingsSection(title = strings.monitorBlocksNote) {
            SwitchSettingRow(
                title = strings.blockTrend,
                checked = blocks.trend,
                onChange = { on ->
                    scope.launch { graph.settings.setMonitorBlocks(blocks.copy(trend = on)) }
                },
            )
            SettingsDivider()
            SwitchSettingRow(
                title = strings.blockDoseToday,
                checked = blocks.doseToday,
                onChange = { on ->
                    scope.launch { graph.settings.setMonitorBlocks(blocks.copy(doseToday = on)) }
                },
            )
            SettingsDivider()
            SwitchSettingRow(
                title = strings.blockCountChart,
                checked = blocks.countRateChart,
                onChange = { on ->
                    scope.launch { graph.settings.setMonitorBlocks(blocks.copy(countRateChart = on)) }
                },
            )
            SettingsDivider()
            SwitchSettingRow(
                title = strings.blockHardnessChart,
                checked = blocks.hardnessChart,
                onChange = { on ->
                    scope.launch { graph.settings.setMonitorBlocks(blocks.copy(hardnessChart = on)) }
                },
            )
            SettingsDivider()
            SwitchSettingRow(
                title = strings.blockStats,
                checked = blocks.stats,
                onChange = { on ->
                    scope.launch { graph.settings.setMonitorBlocks(blocks.copy(stats = on)) }
                },
            )
            SettingsDivider()
            SettingRow(
                title = strings.resetInterface,
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
    dragging: Boolean,
    dragOffsetPx: Float,
    onMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val haptics = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
            // Взятая строка приподнята плоскостью и рамкой: глубина задаётся
            // ступенями поверхностей.
            .background(if (dragging) colors.surface2 else Color.Transparent)
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .onSizeChanged { onMeasured(it.height.toFloat()) }
            .padding(horizontal = Dimens.space3, vertical = Dimens.space1),
    ) {
        // Ручка перетаскивания — отдельная цель: перетаскивание всей строки
        // отбирает у списка прокрутку.
        Text(
            text = "≡",
            style = type.title,
            color = if (dragging) colors.dataText else colors.ink2,
            modifier = Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = Dimens.touchTarget)
                .wrapContentHeight()
                .pointerInput(entry.tab) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDragStart()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            textAlign = TextAlign.Center,
        )
        Text(
            text = entry.tab.title(LocalStrings.current),
            style = type.body,
            color = if (entry.visible) colors.ink else colors.muted,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = entry.visible, onChange = onToggle)
    }
}

@Composable
internal fun BlockToggleRow(
    title: String,
    enabled: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
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
        Column(Modifier.weight(1f)) {
            Text(text = title, style = type.body, color = colors.ink)
            if (subtitle != null) {
                Text(text = subtitle, style = type.footnote, color = colors.muted)
            }
        }
        // Двоичное состояние — переключателем: слова «вкл/выкл» не дают цели
        // для пальца.
        AppSwitch(checked = enabled, onChange = onChange)
    }
}

// --- Лицензии ---
