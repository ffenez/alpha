@file:OptIn(ExperimentalLayoutApi::class)

package app.radiacode.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import app.radiacode.data.db.ProfileEntity
import app.radiacode.data.db.ProfileNetworkEntity
import app.radiacode.device.ConnectionState
import app.radiacode.service.Notifications
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTab
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.RadioMark
import app.radiacode.ui.feedback.FeedbackSelfTest
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.NavConfig
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.SelfTestText
import app.radiacode.ui.logic.NavEntry
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.baselineCollectedWording
import app.radiacode.ui.logic.freshnessLabel
import app.radiacode.ui.logic.heldWording
import app.radiacode.ui.logic.learningWording
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Настройки (SPEC: opens separately, not a tab). Sections: Тревоги, Профили,
 * Обычный фон, Прибор, Единицы, Интерфейс, Проверка, О приложении.
 */
@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = "← Назад", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "Настройки", color = colors.ink)
        }

        AlarmsSection(graph)
        ProfilesSection(graph)
        BaselineSection(graph)
        DeviceSection(graph)
        UnitsSection(graph)
        InterfaceSection(graph)
        SelfTestSection()
        AboutSection()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = LocalAppTypography.current.labelSmall,
        color = LocalAppColors.current.ink2,
    )
}

// --- Тревоги ---

@Composable
private fun AlarmsSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val sensitivity by graph.settings.alarmSensitivity
        .collectAsState(initial = AlarmSensitivity.NORMAL)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val customL1 by graph.settings.customAlarmL1MicroSvH
        .collectAsState(initial = AppSettings.DEFAULT_CUSTOM_L1_MICRO_SV_H)
    val customL2 by graph.settings.customAlarmL2MicroSvH
        .collectAsState(initial = AppSettings.DEFAULT_CUSTOM_L2_MICRO_SV_H)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle("Тревоги")
            Text(
                text = "Тревога срабатывает не от одиночного скачка: уровень должен " +
                    "превысить порог — по абсолютной величине или относительно " +
                    "обычного фона места — и продержаться указанное время.",
                style = type.bodySmall,
                color = colors.ink2,
            )

            SensitivityOption(
                title = "Обычная",
                selected = sensitivity == AlarmSensitivity.NORMAL,
                description = presetDescription(
                    alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f),
                    unit,
                ),
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.NORMAL) }
                },
            )
            SensitivityOption(
                title = "Высокая",
                selected = sensitivity == AlarmSensitivity.HIGH,
                description = presetDescription(
                    alarmThresholds(AlarmSensitivity.HIGH, 0f, 0f),
                    unit,
                ),
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.HIGH) }
                },
            )
            SensitivityOption(
                title = "Своя",
                selected = sensitivity == AlarmSensitivity.CUSTOM,
                description = "уровни мощности дозы задаются вручную",
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.CUSTOM) }
                },
            )

            if (sensitivity == AlarmSensitivity.CUSTOM) {
                CustomLevels(graph, unit, customL1, customL2)
            }

            AppDivider()
            AlarmSoundRow()
        }
    }
}

/** Deep link into the system settings of the «Тревога» notification channel. */
@Composable
private fun AlarmSoundRow() {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // The link needs the channel even if the service never ran.
                    Notifications.ensureChannels(context)
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.ALARM_CHANNEL_ID),
                        )
                    }
                },
            ),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "Звук и вибрация тревоги", style = type.label, color = colors.ink)
            Text(
                text = "мелодия и вибрация настраиваются в системных настройках " +
                    "уведомления «Тревога»",
                style = type.bodySmall,
                color = colors.muted,
            )
        }
        Text(text = "›", style = type.title, color = colors.ink2)
    }
}

@Composable
private fun SensitivityOption(
    title: String,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
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
                onClick = onSelect,
            ),
    ) {
        RadioMark(selected)
        Column {
            Text(
                text = title,
                style = type.label,
                color = colors.ink,
            )
            Text(text = description, style = type.bodySmall, color = colors.muted)
        }
    }
}

private fun presetDescription(thresholds: AlarmThresholds, unit: DoseUnitSetting): String =
    "от ${DoseFormat.rateWithUnit(thresholds.l1MicroSvH, unit)} или " +
        "${formatFactor(thresholds.relativeFactor)}× к P90 профиля, " +
        heldWording(thresholds.persistenceSeconds.toLong())

private fun formatFactor(factor: Float): String =
    if (factor == factor.toInt().toFloat()) "${factor.toInt()}" else "$factor"

@Composable
private fun CustomLevels(
    graph: AppGraph,
    unit: DoseUnitSetting,
    storedL1MicroSvH: Float,
    storedL2MicroSvH: Float,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    // Inputs are in the display unit; stored values stay µSv/h.
    var l1Text by remember(storedL1MicroSvH, unit) {
        mutableStateOf(DoseFormat.rate(storedL1MicroSvH, unit))
    }
    var l2Text by remember(storedL2MicroSvH, unit) {
        mutableStateOf(DoseFormat.rate(storedL2MicroSvH, unit))
    }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        LevelField("уровень 1, ${DoseFormat.rateUnitLabel(unit)}", l1Text) { l1Text = it }
        LevelField("уровень 2, ${DoseFormat.rateUnitLabel(unit)}", l2Text) { l2Text = it }
        error?.let { Text(text = it, style = type.bodySmall, color = colors.warn) }
        AppButton(
            text = "Сохранить уровни",
            onClick = {
                val l1 = parseLevelToMicroSv(l1Text, unit)
                val l2 = parseLevelToMicroSv(l2Text, unit)
                when {
                    l1 == null || l2 == null -> error = "Введите числа, например 0,30"
                    l1 <= 0f -> error = "Уровень 1 должен быть больше нуля"
                    l2 < l1 -> error = "Уровень 2 не может быть ниже уровня 1"
                    else -> {
                        error = null
                        scope.launch { graph.settings.setCustomAlarmLevels(l1, l2) }
                    }
                }
            },
        )
        Text(
            text = "Уровень 1 — линия тревоги на графиках и порог отклонения; " +
                "уровень 2 — сильное превышение.",
            style = type.bodySmall,
            color = colors.muted,
        )
    }
}

@Composable
private fun LevelField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
        Text(text = label, style = type.bodySmall, color = colors.ink2)
        AppTextField(value = value, onValueChange = onChange, numeric = true)
    }
}

/** Parses the display-unit input back to stored µSv/h; comma tolerated. */
private fun parseLevelToMicroSv(text: String, unit: DoseUnitSetting): Float? {
    val value = text.trim().replace(',', '.').toFloatOrNull() ?: return null
    return when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> value
        DoseUnitSetting.MICRO_ROENTGEN -> value / DoseFormat.MICRO_R_PER_MICRO_SV
    }
}

// --- Профили ---

private val PROFILE_ICONS = listOf("⌂", "▣", "⌾", "◈", "→", "○", "☾", "✦")

/**
 * Профили (spec §3.1): create/rename/icon/archive, nesting «Дом / Спальня»,
 * the two automation switches and the Wi-Fi binding of the current network.
 * Управление местами из v0.x переехало сюда целиком.
 */
@Composable
private fun ProfilesSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val networks by graph.profileRepository.networks().collectAsState(initial = emptyList())
    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val network by graph.contextHub.network.collectAsState()
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    // Baseline summary per profile, refreshed when the list changes.
    var baselines by remember { mutableStateOf<Map<Long, BaselineState>>(emptyMap()) }
    LaunchedEffect(profiles) {
        baselines = profiles.associate { it.id to graph.baselineRepository.state(it.id) }
    }

    var expandedId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val nodes = ProfileTree.tree(profiles)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle("Профили")
            Text(
                text = "Профиль — обстановка со своим обычным фоном: дом, офис, дача. " +
                    "Приложение может включать его само, когда телефон в знакомой сети " +
                    "Wi-Fi. При удалении профиля измерения остаются в журнале.",
                style = type.bodySmall,
                color = colors.ink2,
            )

            nodes.forEach { node ->
                (listOf(node.profile) + node.children).forEach { profile ->
                    ProfileSettingsRow(
                        profile = profile,
                        allProfiles = profiles,
                        nested = profile.parentId != null,
                        active = profile.id == activeProfile?.id,
                        baselineLine = baselineSummary(baselines[profile.id], unit),
                        boundNetworks = networks.filter { it.profileId == profile.id },
                        currentNetworkHash = network.hash,
                        currentNetworkLabel = network.label,
                        expanded = expandedId == profile.id,
                        onToggle = {
                            expandedId = if (expandedId == profile.id) null else profile.id
                        },
                        graph = graph,
                        scope = scope,
                        onCollapse = { expandedId = null },
                    )
                }
            }

            AppDivider()
            if (adding) {
                AppTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "название профиля",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = "Добавить",
                        primary = true,
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch { graph.profileRepository.add(newName.trim()) }
                            newName = ""
                            adding = false
                        },
                    )
                    AppButton(text = "Отмена", onClick = { adding = false })
                }
            } else {
                AppButton(text = "+ Свой профиль", onClick = { adding = true })
            }

            val missing = ProfileTree.PRESETS.filter { preset ->
                profiles.none { it.name.equals(preset.name, ignoreCase = true) }
            }
            if (missing.isNotEmpty()) {
                Text(text = "Готовые:", style = type.footnote, color = colors.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    missing.forEach { preset ->
                        Chip(
                            text = "+ ${preset.icon} ${preset.name}",
                            color = colors.dataText,
                            onClick = { scope.launch { graph.profileRepository.create(preset) } },
                        )
                    }
                }
            }
        }
    }
}

private fun baselineSummary(state: BaselineState?, unit: DoseUnitSetting): String = when (state) {
    null -> "…"
    is BaselineState.Learning -> learningWording(state)
    is BaselineState.Active ->
        DoseFormat.range(
            state.baseline.doseLowMicroSvH,
            state.baseline.doseHighMicroSvH,
            unit,
        ) + " ${DoseFormat.rateUnitLabel(unit)} · " + baselineCollectedWording(state.baseline)
}

/** Extended per-profile statistics (spec §4.1) shown inside the expanded row. */
private fun baselineStatsLine(state: BaselineState?, unit: DoseUnitSetting): String? {
    val baseline = (state as? BaselineState.Active)?.baseline ?: return null
    return "медиана ${DoseFormat.rate(baseline.doseMedianMicroSvH, unit)} · " +
        "P25–P75 ${DoseFormat.range(baseline.doseP25MicroSvH, baseline.doseP75MicroSvH, unit)} · " +
        "MAD ${DoseFormat.rate(baseline.doseMadMicroSvH, unit)} · " +
        "n ${baseline.bucketCount} корзин"
}

@Composable
private fun ProfileSettingsRow(
    profile: ProfileEntity,
    allProfiles: List<ProfileEntity>,
    nested: Boolean,
    active: Boolean,
    baselineLine: String,
    boundNetworks: List<ProfileNetworkEntity>,
    currentNetworkHash: String?,
    currentNetworkLabel: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    graph: AppGraph,
    scope: kotlinx.coroutines.CoroutineScope,
    onCollapse: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    var renameText by remember(profile.name, expanded) { mutableStateOf(profile.name) }
    var confirmingDelete by remember(expanded) { mutableStateOf(false) }
    var baselineState by remember(profile.id) { mutableStateOf<BaselineState?>(null) }
    LaunchedEffect(profile.id, expanded) {
        if (expanded) baselineState = graph.baselineRepository.state(profile.id)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.space1),
        modifier = Modifier.padding(start = if (nested) Dimens.space3 else 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.touchTarget)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = listOf(profile.icon, profile.name)
                            .filter { it.isNotBlank() }
                            .joinToString(" "),
                        style = type.label,
                        color = if (profile.archived) colors.muted else colors.ink,
                    )
                    if (active) Chip(text = "активен", color = colors.ok)
                    if (profile.archived) Chip(text = "в архиве", color = colors.muted)
                }
                Text(
                    text = if (profile.archived) "профиль скрыт из выбора" else baselineLine,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            Text(text = if (expanded) "−" else "+", style = type.title, color = colors.ink2)
        }

        if (!expanded) return@Column

        if (confirmingDelete) {
            Text(
                text = "Удалить профиль «${profile.name}»? Его измерения останутся " +
                    "в журнале без привязки к профилю.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                AppButton(
                    text = "Удалить",
                    onClick = {
                        scope.launch { graph.profileRepository.delete(profile.id) }
                        onCollapse()
                    },
                )
                AppButton(text = "Отмена", onClick = { confirmingDelete = false })
            }
            AppDivider()
            return@Column
        }

        baselineStatsLine(baselineState, unit)?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }

        AppTextField(value = renameText, onValueChange = { renameText = it })
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = "Сохранить имя",
                enabled = renameText.isNotBlank() && renameText.trim() != profile.name,
                onClick = {
                    scope.launch { graph.profileRepository.rename(profile.id, renameText.trim()) }
                    onCollapse()
                },
            )
        }

        Text(text = "Значок", style = type.footnote, color = colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            PROFILE_ICONS.forEach { icon ->
                Chip(
                    text = icon,
                    color = if (icon == profile.icon) colors.dataText else colors.ink2,
                    onClick = { scope.launch { graph.profileRepository.setIcon(profile.id, icon) } },
                )
            }
        }

        BlockToggleRow("Включать автоматически по Wi-Fi", profile.autoActivate) { on ->
            scope.launch { graph.profileRepository.setAutoActivate(profile.id, on) }
        }
        BlockToggleRow("Учить обычный фон", profile.baselineLearning) { on ->
            scope.launch { graph.profileRepository.setBaselineLearning(profile.id, on) }
        }

        // --- Wi-Fi ---
        Text(
            text = "Сети Wi-Fi. Сеть узнаётся по адресу роутера, а не по имени: " +
                "разрешение на геолокацию для этого не нужно.",
            style = type.footnote,
            color = colors.muted,
        )
        boundNetworks.forEach { bound ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.touchTarget),
            ) {
                Text(
                    text = NetworkIdentity.displayLabel(bound.label, bound.networkHash),
                    style = type.valueSmall,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Chip(
                    text = "отвязать",
                    color = colors.ink2,
                    onClick = { scope.launch { graph.profileRepository.unbindNetwork(bound.id) } },
                )
            }
        }
        when {
            currentNetworkHash == null -> Text(
                text = "телефон сейчас не в сети Wi-Fi",
                style = type.footnote,
                color = colors.muted,
            )
            boundNetworks.any { it.networkHash == currentNetworkHash } -> Text(
                text = "текущая сеть уже привязана к этому профилю",
                style = type.footnote,
                color = colors.muted,
            )
            else -> AppButton(
                text = "Привязать текущую сеть",
                onClick = {
                    scope.launch {
                        graph.profileRepository.bindNetwork(
                            profileId = profile.id,
                            hash = currentNetworkHash,
                            label = currentNetworkLabel,
                        )
                    }
                },
            )
        }

        // --- nesting ---
        val parents = ProfileTree.parentCandidates(allProfiles, profile.id)
            .filter { it.id != profile.id }
        if (parents.isNotEmpty() || profile.parentId != null) {
            Text(text = "Вложить в профиль", style = type.footnote, color = colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Chip(
                    text = "самостоятельный",
                    color = if (profile.parentId == null) colors.dataText else colors.ink2,
                    onClick = { scope.launch { graph.profileRepository.setParent(profile.id, null) } },
                )
                parents.forEach { parent ->
                    Chip(
                        text = parent.name,
                        color = if (profile.parentId == parent.id) colors.dataText else colors.ink2,
                        onClick = {
                            scope.launch {
                                graph.profileRepository.setParent(profile.id, parent.id)
                            }
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            if (profile.archived) {
                AppButton(
                    text = "Вернуть из архива",
                    onClick = {
                        scope.launch { graph.profileRepository.setArchived(profile.id, false) }
                    },
                )
            } else {
                AppButton(
                    text = "В архив",
                    enabled = ProfileTree.canArchive(allProfiles, profile.id),
                    onClick = {
                        scope.launch { graph.profileRepository.setArchived(profile.id, true) }
                        onCollapse()
                    },
                )
            }
            AppButton(text = "Удалить профиль", onClick = { confirmingDelete = true })
        }
        AppDivider()
    }
}

// --- Обычный фон ---

/**
 * Управление обучением baseline: ручная заморозка (условие 7 допуска, spec
 * §4.2) и grace period авто-контекста (spec §3.4). Оба параметра меняют то,
 * какие интервалы вообще попадают в статистику, поэтому объясняются словами.
 */
@Composable
private fun BaselineSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val frozen by graph.settings.baselineFrozen.collectAsState(initial = false)
    val grace by graph.settings.contextGraceMillis
        .collectAsState(initial = ContextConfig.DEFAULT_GRACE_MILLIS)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle("Обычный фон")
            Text(
                text = "Обычный фон профиля пополняется только из пригодных измерений. " +
                    "Не учитываются: Поиск и опыты, обрыв потока, полчаса после " +
                    "отклонения и время, пока место не подтверждено. Сами измерения " +
                    "записываются всегда.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            BlockToggleRow("Заморозить обучение", frozen) { on ->
                scope.launch { graph.settings.setBaselineFrozen(on) }
            }
            AppDivider()
            Text(
                text = "Сколько ждать, прежде чем считать, что телефон покинул знакомую " +
                    "сеть. Всё это время профиль остаётся прежним, но фон не пополняется.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                ContextConfig.ALLOWED_GRACE_MILLIS.forEach { millis ->
                    Chip(
                        text = "${millis / 60_000} мин",
                        color = if (millis == grace) colors.dataText else colors.ink2,
                        onClick = { scope.launch { graph.settings.setContextGraceMillis(millis) } },
                    )
                }
            }
        }
    }
}

// --- Прибор ---

@Composable
private fun DeviceSection(graph: AppGraph) {
    val colors = LocalAppColors.current
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
            SectionTitle("Прибор")

            when (val state = connection) {
                is ConnectionState.Connected -> {
                    InfoRow("серийный номер", state.info.serialNumber)
                    InfoRow("прошивка", state.info.firmware.toString())
                    InfoRow("bluetooth", "подключено")
                }
                is ConnectionState.Connecting -> InfoRow("bluetooth", "подключение…")
                is ConnectionState.Reconnecting ->
                    InfoRow("bluetooth", "переподключение, попытка ${state.attempt}")
                ConnectionState.Disconnected ->
                    InfoRow("bluetooth", if (serviceRunning) "нет соединения" else "служба остановлена")
            }

            rareData?.let { rare ->
                InfoRow("батарея прибора", "${rare.batteryPercent.toInt()} %")
                InfoRow("температура", "${rare.temperature.toInt()} °C")
            }

            when (freshness) {
                Freshness.NoData -> InfoRow("поток", "данных ещё нет")
                is Freshness.Fresh -> InfoRow("поток", "активен · 1 Гц")
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
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle("Единицы")
            UnitOption(
                title = "мкЗв/ч",
                subtitle = "микрозиверты в час — единица СИ",
                selected = unit == DoseUnitSetting.MICRO_SIEVERT,
                onSelect = {
                    scope.launch { graph.settings.setDoseUnit(DoseUnitSetting.MICRO_SIEVERT) }
                },
            )
            UnitOption(
                title = "мкР/ч",
                subtitle = "микрорентгены в час · 1 мкЗв/ч = 100 мкР/ч",
                selected = unit == DoseUnitSetting.MICRO_ROENTGEN,
                onSelect = {
                    scope.launch { graph.settings.setDoseUnit(DoseUnitSetting.MICRO_ROENTGEN) }
                },
            )
            Text(
                text = "Пересчёт только для отображения: измерения хранятся в исходных " +
                    "единицах прибора без потери точности.",
                style = type.bodySmall,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun UnitOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
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
                onClick = onSelect,
            ),
    ) {
        RadioMark(selected)
        Column {
            Text(
                text = title,
                style = type.value,
                color = colors.ink,
            )
            Text(text = subtitle, style = type.bodySmall, color = colors.muted)
        }
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
            SectionTitle("Интерфейс")

            Text(
                text = "Вкладки меню: порядок и видимость. Настройки остаются " +
                    "доступны через шестерёнку на Главной.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = Dimens.touchTarget),
            ) {
                Text(text = AppTab.HOME.title, style = type.label, color = colors.ink)
                Spacer(Modifier.weight(1f))
                Text(text = "всегда видна", style = type.bodySmall, color = colors.muted)
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
                    text = "Кроме Главной должна остаться хотя бы одна вкладка.",
                    style = type.bodySmall,
                    color = colors.warn,
                )
            }

            AppDivider()
            Text(
                text = "Блоки Главной. Число, статус и график остаются всегда.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            BlockToggleRow("Тренд/ч", blocks.trend) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(trend = it)) }
            }
            BlockToggleRow("Доза сегодня", blocks.doseToday) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(doseToday = it)) }
            }
            BlockToggleRow("Статистика под графиком (мин/медиана/макс/SD/n)", blocks.stats) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(stats = it)) }
            }
            BlockToggleRow("Подсказка о CPS", blocks.cpsHint) {
                scope.launch { graph.settings.setMonitorBlocks(blocks.copy(cpsHint = it)) }
            }

            AppDivider()
            AppButton(
                text = "Вернуть меню и блоки по умолчанию",
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
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.touchTarget),
    ) {
        Text(
            text = entry.tab.title,
            style = type.label,
            color = if (entry.visible) colors.ink else colors.muted,
            modifier = Modifier.weight(1f),
        )
        ArrowButton(text = "↑", enabled = canMoveUp) { onMove(-1) }
        ArrowButton(text = "↓", enabled = canMoveDown) { onMove(1) }
        Text(
            text = if (entry.visible) "видна" else "скрыта",
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
private fun BlockToggleRow(title: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
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
        Text(
            text = title,
            style = type.bodySmall,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (enabled) "вкл" else "выкл",
            style = type.value,
            color = if (enabled) colors.ink else colors.muted,
        )
    }
}

// --- Проверка ---

/**
 * Two probes that answer «does the feedback engine work at all», bypassing
 * every gate of the Поиск screen. Without them a field report of «no sound»
 * cannot be told apart from wrong wiring — and we have no logs from the
 * user's phone.
 */
@Composable
private fun SelfTestSection() {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val context = LocalContext.current
    var soundResult by remember { mutableStateOf<String?>(null) }
    var vibrationResult by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle("Проверка")
            Text(
                text = "Короткая проба звука и вибрации — без прибора и без " +
                    "настроек Поиска. Помогает понять, молчит сам телефон или " +
                    "приложение не получает данные.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                AppButton(
                    text = "Проверить звук",
                    onClick = {
                        soundResult = SelfTestText.sound(FeedbackSelfTest.playClicks(context))
                    },
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "Проверить вибрацию",
                    onClick = {
                        vibrationResult = SelfTestText.vibration(FeedbackSelfTest.pulse(context))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            soundResult?.let { ResultLine(label = "звук", text = it) }
            vibrationResult?.let { ResultLine(label = "вибрация", text = it) }
        }
    }
}

/** Honest one-line outcome: green only when nothing stood in the way. */
@Composable
private fun ResultLine(label: String, text: String) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val clean = text == "звук воспроизведён" || text == "импульс отправлен"
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        Chip(
            text = label,
            color = if (clean) colors.ok else colors.warn,
            dot = if (clean) colors.ok else colors.warn,
        )
        Text(
            text = text,
            style = type.bodySmall,
            color = if (clean) colors.ink2 else colors.warn,
            modifier = Modifier.weight(1f),
        )
    }
}

// --- О приложении ---

@Composable
private fun AboutSection() {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    var licensesText by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }

    LaunchedEffect(showLicenses) {
        if (showLicenses && licensesText == null) {
            licensesText = runCatching {
                LICENSE_ASSETS.joinToString("\n\n" + "─".repeat(24) + "\n\n") { path ->
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }
            }.getOrElse { "Не удалось прочитать файлы лицензий." }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle("О приложении")
            InfoRow("alpha", "версия $version")
            Text(
                text = "Данные измерений не покидают телефон: без телеметрии, " +
                    "аналитики и сетевых запросов.",
                style = type.bodySmall,
                color = colors.ink2,
            )
            AppDivider()
            Text(
                text = "Протокол RadiaCode — порт библиотеки cdump/radiacode (MIT). " +
                    "BLE — Kable (Apache-2.0). Карта — osmdroid (Apache-2.0), " +
                    "данные карты © участники OpenStreetMap. Шрифты IBM Plex Sans " +
                    "и IBM Plex Mono (OFL). Полные тексты — в файлах NOTICE " +
                    "внутри приложения.",
                style = type.bodySmall,
                color = colors.muted,
            )
            AppButton(
                text = if (showLicenses) "Скрыть лицензии" else "Показать лицензии",
                onClick = { showLicenses = !showLicenses },
            )
            if (showLicenses) {
                Text(
                    text = licensesText ?: "читаю…",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
        }
    }
}

private val LICENSE_ASSETS = listOf(
    "licenses/cdump_radiacode_NOTICE.txt",
    "licenses/ibm_plex_NOTICE.txt",
    "licenses/ibm_plex_sans_OFL.txt",
    "licenses/ibm_plex_mono_OFL.txt",
)
