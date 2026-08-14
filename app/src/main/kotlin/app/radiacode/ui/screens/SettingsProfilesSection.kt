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
import app.radiacode.ui.text.MonitorRu
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
 * Настройки → Профили: список профилей, их правка и удаление.
 *
 * Вынесено из `SettingsScreen`, который дорос до двух тысяч строк: экран
 * настроек — это НЕ один экран, а восемь независимых разделов, и держать их в
 * одном файле значит каждый раз читать семь чужих, чтобы поправить один.
 * Логика не тронута: composable'ы те же, только `private` стало `internal` —
 * иначе соседний файл того же пакета их не увидит.
 */

/**
 * Профили (spec §3.1): create/rename/icon/archive, nesting «Дом / Спальня»,
 * the two automation switches and the Wi-Fi binding of the current network.
 * Управление местами из v0.x переехало сюда целиком.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun ProfilesSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
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
            SectionTitle(strings.profilesTitle)
            Hint(
                text = strings.profilesIntro,
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
                        baselineLine = baselineSummary(
                            state = baselines[profile.id],
                            unit = unit,
                            learning = profile.baselineLearning,
                        ),
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
                    placeholder = strings.profileNameHint,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = strings.add,
                        primary = true,
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch { graph.profileRepository.add(newName.trim()) }
                            newName = ""
                            adding = false
                        },
                    )
                    AppButton(text = strings.cancel, onClick = { adding = false })
                }
            } else {
                AppButton(text = strings.ownProfile, onClick = { adding = true })
            }

            val missing = ProfileTree.PRESETS.filter { preset ->
                profiles.none { it.name.equals(preset.name, ignoreCase = true) }
            }
            if (missing.isNotEmpty()) {
                Text(text = strings.presets, style = type.footnote, color = colors.muted)
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

internal fun baselineSummary(
    state: BaselineState?,
    unit: DoseUnitSetting,
    strings: Strings = RuStrings,
    /**
     * Собирает ли этот профиль обычный фон вообще.
     *
     * «В пути» и «Без места» описывают положение, а не комнату, и фон им не
     * собирается по устройству. Полоса прогресса «0 ч из 3» обещала им конец,
     * которого не будет: она не «ещё не набрала», она не наберёт никогда.
     */
    learning: Boolean = true,
): String = when {
    !learning -> MonitorRu.usualBackgroundNotCollected
    else -> when (state) {
        null -> "…"
        is BaselineState.Learning -> learningWording(state)
        is BaselineState.Active ->
            DoseFormat.range(
                state.baseline.doseLowMicroSvH,
                state.baseline.doseHighMicroSvH,
                unit,
            ) + " ${DoseFormat.rateUnitLabel(unit, s = strings)} · " +
                baselineCollectedWording(state.baseline)
    }
}

/** Extended per-profile statistics (spec §4.1) shown inside the expanded row. */
internal fun baselineStatsLine(
    state: BaselineState?,
    unit: DoseUnitSetting,
    s: Strings = RuStrings,
): String? {
    val baseline = (state as? BaselineState.Active)?.baseline ?: return null
    return s.baselineStats(
        median = DoseFormat.rate(baseline.doseMedianMicroSvH, unit),
        iqr = DoseFormat.range(baseline.doseP25MicroSvH, baseline.doseP75MicroSvH, unit),
        mad = DoseFormat.rate(baseline.doseMadMicroSvH, unit),
        buckets = baseline.bucketCount,
    )
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun ProfileSettingsRow(
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
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    var renameText by remember(profile.name, expanded) { mutableStateOf(profile.name) }
    var confirmingDelete by remember(profile.id) { mutableStateOf(false) }
    // Pure guard, recomputed from the live list: the button can explain itself.
    val deletion = ProfileDeletion.evaluate(allProfiles, profile.id)
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
                    if (active) Chip(text = strings.active, color = colors.ok)
                    if (profile.archived) Chip(text = strings.archived, color = colors.muted)
                }
                Text(
                    text = if (profile.archived) strings.hiddenFromPicker else baselineLine,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            Text(text = if (expanded) "−" else "+", style = type.title, color = colors.ink2)
        }

        if (!expanded) return@Column

        baselineStatsLine(baselineState, unit, strings)?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }

        AppTextField(value = renameText, onValueChange = { renameText = it })
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = strings.saveName,
                enabled = renameText.isNotBlank() && renameText.trim() != profile.name,
                onClick = {
                    scope.launch { graph.profileRepository.rename(profile.id, renameText.trim()) }
                    onCollapse()
                },
            )
        }

        Text(text = strings.icon, style = type.footnote, color = colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            PROFILE_ICONS.forEach { icon ->
                Chip(
                    text = icon,
                    color = if (icon == profile.icon) colors.dataText else colors.ink2,
                    onClick = { scope.launch { graph.profileRepository.setIcon(profile.id, icon) } },
                )
            }
        }

        BlockToggleRow(strings.autoByWifi, profile.autoActivate) { on ->
            scope.launch { graph.profileRepository.setAutoActivate(profile.id, on) }
        }
        BlockToggleRow(strings.learnBackground, profile.baselineLearning) { on ->
            scope.launch { graph.profileRepository.setBaselineLearning(profile.id, on) }
        }

        // --- Wi-Fi ---
        Hint(
            text = strings.wifiNote,
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
                    text = strings.unbind,
                    color = colors.ink2,
                    onClick = { scope.launch { graph.profileRepository.unbindNetwork(bound.id) } },
                )
            }
        }
        when {
            currentNetworkHash == null -> Text(
                text = strings.notOnWifi,
                style = type.footnote,
                color = colors.muted,
            )
            boundNetworks.any { it.networkHash == currentNetworkHash } -> Text(
                text = strings.networkAlreadyBound,
                style = type.footnote,
                color = colors.muted,
            )
            else -> AppButton(
                text = strings.bindCurrentNetwork,
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
            Text(text = strings.nestInProfile, style = type.footnote, color = colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Chip(
                    text = strings.standalone,
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
                    text = strings.unarchive,
                    onClick = {
                        scope.launch { graph.profileRepository.setArchived(profile.id, false) }
                    },
                )
            } else {
                AppButton(
                    text = strings.archiveAction,
                    enabled = ProfileTree.canArchive(allProfiles, profile.id),
                    onClick = {
                        scope.launch { graph.profileRepository.setArchived(profile.id, true) }
                        onCollapse()
                    },
                )
            }
            AppButton(
                text = strings.deleteProfile,
                enabled = deletion is ProfileDeletion.Allowed,
                onClick = { confirmingDelete = true },
            )
        }
        (deletion as? ProfileDeletion.Blocked)?.let { blocked ->
            Text(
                text = ProfileDeletion.blockedWording(blocked),
                style = type.footnote,
                color = colors.muted,
            )
        }
        AppDivider()
    }

    // A dialog, not an inline block: the confirmation must not depend on the
    // panel it replaces still being on screen.
    if (confirmingDelete && deletion is ProfileDeletion.Allowed) {
        ConfirmDeleteProfileDialog(
            profileName = profile.name,
            onConfirm = {
                confirmingDelete = false
                scope.launch { graph.profileRepository.delete(profile.id) }
                onCollapse()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** Confirmation for «Удалить профиль»: says out loud that measurements stay. */
@Composable
internal fun ConfirmDeleteProfileDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = strings.deleteProfileQuestion, style = type.title, color = colors.ink)
                Text(
                    text = ProfileDeletion.confirmWording(profileName),
                    style = type.bodySmall,
                    color = colors.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(text = strings.delete, onClick = onConfirm)
                    AppButton(text = strings.cancel, onClick = onDismiss)
                }
            }
        }
    }
}

// --- Обычный фон ---
