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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import app.radiacode.ui.logic.DragReorder
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import app.radiacode.ui.logic.SettingsSearch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
import app.radiacode.ui.components.NavArrow
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTab
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.AppSwitch
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.ChoiceSettingRow
import app.radiacode.ui.components.SettingRow
import app.radiacode.ui.components.SettingsDivider
import app.radiacode.ui.components.SettingsSection
import app.radiacode.ui.components.SwitchSettingRow
import app.radiacode.ui.components.SettingsTopBar
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.RadioMark
import app.radiacode.service.DeviceControlHub
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.DoseTint
import app.radiacode.ui.logic.MapAnchors
import app.radiacode.ui.logic.MapColorScale
import app.radiacode.ui.logic.TrackMap
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
import app.radiacode.data.export.TrackDiagnostics
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
    DATA(SettingsGroup.SYSTEM),
    ABOUT(SettingsGroup.SYSTEM),
    ;

    fun title(s: Strings): String = when (this) {
        ALARMS -> s.settingsAlarms
        PROFILES -> s.settingsProfiles
        SOUND -> s.settingsNotifications
        VIEW -> s.settingsView
        DEVICE -> s.settingsDevice
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // На широком экране список разделов и открытый раздел стоят рядом:
        // на планшете и в ландшафте колонка настроек занимала треть ширины, а
        // остальные две трети оставались пустыми, и каждый переход туда-обратно
        // перерисовывал весь экран ради одного столбца.
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
                        // Пустая половина учит первому действию, а не молчит.
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
            // Один заголовок на экран, он же кнопка возврата: крупная кнопка
            // «← Назад» и чип с названием страницы справа говорили одно и то же
            // дважды и занимали высоту, которой в настройках всегда не хватает.
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
 * колонка раздела становится теснее телефонной, и делить экран незачем.
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
        // Фон принадлежит МЕСТУ: профили и обучение фона — один раздел.
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
        // Всё, что не ежедневная настройка: хранилище, темп фоновой записи и
        // отчёты для разбора. Раньше отчёт жил в «О приложении» — рядом с
        // версией и лицензиями, где его никто не ищет, зато он выдавал сборку
        // разработчика каждому, кто зашёл посмотреть версию.
        SettingsCategory.DATA -> {
            RetentionSection(graph)
            SpectrumRateSection(graph)
            DebugSection(graph)
        }
        SettingsCategory.ABOUT -> LicensesSection()
    }
}

/**
 * Корень настроек: четыре группы и текущее состояние каждой строки.
 *
 * Значение справа — не украшение: чаще всего в настройки заходят посмотреть,
 * ЧТО стоит сейчас, а не менять. Пока значения не было, за ответом «какой у
 * меня режим тревоги» приходилось открывать раздел и возвращаться.
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
        // Поиск ищет СЛОВОМ, которым настройку называют про себя: «звук»,
        // «фон», «батарея». Разделов семь — это уже больше, чем держится в
        // голове, и перебирать их по очереди человек не обязан.
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
 * Что и по каким словам ищется в настройках.
 *
 * Индекс перечисляет не все настройки подряд, а те, которые ищут: слово, с
 * которым человек приходит («звук», «фон», «батарея», «язык»), ведёт в раздел,
 * где это лежит. Подписи берутся из каталога строк, поэтому поиск говорит на
 * языке интерфейса; слова поиска — свои для каждого языка по той же причине.
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
)

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
                // Отклик меняют быстро и прямо в поле, поэтому выбор стоит
                // здесь целиком, а не за строкой со значением.
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
            // Настройка, зависящая от выбора, ПОЯВЛЯЕТСЯ, а не тускнеет:
            // выключенная строка занимает место и заставляет гадать, чем её
            // включить. Высота тона относится только к кликам — им она и
            // принадлежит.
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
        track = trackDiagnostics(graph, now),
    )
    return DebugReport.build(snapshot) { millis ->
        REPORT_STAMP.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }
}

/**
 * Состояние записи следа для отчёта — без единой координаты.
 *
 * Отвечает на три вопроса, которые по экрану не различить: дошла ли подписка
 * до системы, приходят ли фиксы вообще и доезжают ли они до базы.
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
 * Настройки → Интерфейс: как приложение выглядит и что показывает.
 *
 * Экран собран из четырёх групп вместо восьми карточек: язык, оформление,
 * тема и единицы отвечают на вопрос «как это выглядит», масштаб — «какого
 * размера», цвета — «что подсвечивать», Главная — «что на ней есть». Редкий
 * выбор (язык, стиль, единицы, шкала карты) стоит строкой со значением и
 * раскрывается списком: постоянный переключатель ради выбора, который делают
 * раз в жизни прибора, занимает строку экрана навсегда.
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
            // Оформление — не то же, что тема: тема отвечает на вопрос
            // «сколько вокруг света», оформление — «как это выглядит».
            ChoiceSettingRow(
                title = strings.skinTitle,
                options = AppSkin.entries,
                selected = skin,
                label = { it.title(strings) },
                onSelect = { scope.launch { graph.settings.setSkin(it) } },
            )
            SettingsDivider()
            // Тему меняют часто и понимают сразу — она стоит выбором целиком.
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
 * Частый выбор прямо в строке: название слева, сегменты справа.
 *
 * Отдельный элемент, а не строка с раскрытием, ровно для тех настроек, где
 * вариантов два-три и их меняют по ходу дела: лишнее нажатие ради выбора,
 * который и так виден целиком, — это работа на пустом месте.
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
 *
 * Настройки, зависящие от выключателя, ПОЯВЛЯЮТСЯ, а не тускнеют: строка,
 * которую нельзя нажать, занимает место и заставляет гадать, чем её включить.
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
        // Пояснения объясняют экран, а не измеряют: тому, кто носит прибор
        // каждый день, они через неделю становятся шумом. Состояния («нет
        // связи», «прибор не подключён») выключатель НЕ трогает — экран без
        // них выглядел бы работающим, когда он не работает.
        SwitchSettingRow(
            title = strings.hintsTitle,
            subtitle = strings.hintsNote,
            checked = hints,
            onChange = { on -> scope.launch { graph.settings.setHintsVisible(on) } },
        )
        SettingsDivider()
        // Цвет главного числа читается быстрее слов, но кому-то меняющийся
        // оттенок мешает — поэтому выключатель, а не умолчание.
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
                // уровень, и «от 0,30» означало бы в одном месте вдвое выше
                // обычного, а в другом — вдесятеро.
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
        // Чем заданы границы цвета следа на карте. Растяжение по маршруту —
        // аналитический режим: оно находит малые различия, но красит ровную
        // прогулку во всю шкалу, поэтому выбирается осознанно.
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
                // Границы ручной шкалы — по одной строке на величину: у дозы и
                // у счёта они физически разные, и общей быть не может.
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
            // «Заморозить обучение» — термин из машины, а не из жизни: он
            // называл ВЫКЛЮЧЕНИЕ через включение. Настройка говорит, что
            // происходит, когда она включена, и это состояние по умолчанию.
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
 * Настройки → Прибор: состояние связи, что делать после перезагрузки, сигналы
 * самого прибора и спектральный анализ.
 *
 * Сверху — компактная сводка состояния: имя прибора, связь, батарея,
 * температура, поток. Это не настройка, а ответ на вопрос «а он вообще на
 * связи», с которого экран и открывают; поэтому она стоит до списка, а не
 * растворена в нём строками «модель», «серийник», «прошивка».
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
 * Сводка о приборе: имя, связь и живые числа.
 *
 * Числа стоят рядом друг с другом, а не строками «подпись — значение» на всю
 * ширину: батарею, температуру и поток читают вместе, одним взглядом.
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
            // Состояние связи несут и точка, и слово: цвет один различие не
            // переносит.
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
                // Серийник и прошивка нужны раз в жизни — в переписке с
                // поддержкой и в отчёте. Они здесь, но одной приглушённой
                // строкой, а не тремя равновесными.
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
 * Сигналы САМОГО прибора: он пищит и вибрирует без телефона.
 *
 * Отдельно от звука приложения намеренно — это разные вещи, и человек должен
 * понимать, что произойдёт, когда телефон в кармане или выключен.
 *
 * Состояние честное: прибор подтверждает запись, но опросить его текущее
 * значение мы не умеем, поэтому до первой команды в сеансе оно НЕИЗВЕСТНО —
 * так и написано под названием, вместо выключенного тумблера, который
 * выглядел бы как факт.
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
 * Спектральный анализ: энергетические окна и проверка калибровки.
 *
 * Две вещи про одно — про то, как читается спектр, — и стоят они вместе, а не
 * двумя отдельными карточками среди настроек связи.
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
 * показывает Монитор.
 *
 * Раньше это была часть общей карточки «Интерфейс» вперемешку с цветами и
 * шкалой карты. Здесь всё про один экран, и видно, что настраивается именно
 * он: сверху вкладки в их порядке, ниже блоки самой Главной.
 *
 * Стрелки ↑/↓ остались вместо перетаскивания: их видно и ими попадают пальцем,
 * а перетаскивание в списке из пяти строк требует своей механики захвата, и
 * без неё длинное нажатие конфликтует с прокруткой страницы.
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
            // Строки «Главная — всегда видна» здесь нет: если вкладку нельзя
            // убрать, это не настройка, а сообщение о том, как устроено
            // приложение, и место ему не в списке переключателей.
            // Порядок меняют перетаскиванием: стрелки говорили с приложением по
            // одной команде за раз — переставить последнюю вкладку в начало
            // стоило четырёх нажатий, и после каждого список подпрыгивал.
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
            // Взятая строка приподнята плоскостью и рамкой, а не тенью:
            // глубина здесь задаётся ступенями поверхностей.
            .background(if (dragging) colors.surface2 else Color.Transparent)
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .onSizeChanged { onMeasured(it.height.toFloat()) }
            .padding(horizontal = Dimens.space3, vertical = Dimens.space1),
    ) {
        // Ручка перетаскивания — отдельная цель: перетаскивание всей строки
        // отбирало бы у списка прокрутку.
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
        // Двоичное состояние — переключателем. Слова «вкл/выкл» справа
        // притворялись переключателем, но не показывали, что строку можно
        // нажать, и не давали привычной цели для пальца.
        AppSwitch(checked = enabled, onChange = onChange)
    }
}

// --- Лицензии ---
