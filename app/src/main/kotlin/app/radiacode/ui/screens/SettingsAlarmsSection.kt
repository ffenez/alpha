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
 * Настройки → Тревоги: пресеты чувствительности, свои уровни L1/L2 и канал
 * звука тревоги.
 *
 * Вынесено из `SettingsScreen` по той же причине, что и профили: восемь
 * независимых разделов в одном файле заставляют читать семь чужих, чтобы
 * поправить один. Поведение не менялось.
 */

@Composable
internal fun AlarmsSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val sensitivity by graph.settings.alarmSensitivity
        .collectAsState(initial = AlarmSensitivity.NORMAL)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val customL1 by graph.settings.customAlarmL1MicroSvH
        .collectAsState(initial = AppSettings.DEFAULT_CUSTOM_L1_MICRO_SV_H)
    val customL2 by graph.settings.customAlarmL2MicroSvH
        .collectAsState(initial = AppSettings.DEFAULT_CUSTOM_L2_MICRO_SV_H)

    // Порог — это число, которое сравнивают с ДРУГИМИ числами: с тем, что
    // прибор показывает сейчас, и с тем, что здесь обычно. Без них «0,30» —
    // абстракция, и именно поэтому в поле «поставил 0,1» оказалось сюрпризом.
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val activeBaseline = (baselineState as? BaselineState.Active)?.baseline
    val currentDose = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.settingsAlarms)
            Text(
                text = strings.alarmsIntro,
                style = type.bodySmall,
                color = colors.ink2,
            )
            StatGrid(
                cells = listOf(
                    StatCell(
                        currentDose?.let { DoseFormat.rate(it, unit) } ?: "—",
                        strings.nowLabel,
                    ),
                    StatCell(
                        activeBaseline?.let {
                            DoseFormat.range(it.doseLowMicroSvH, it.doseHighMicroSvH, unit)
                        } ?: "—",
                        strings.usuallyHere,
                    ),
                    StatCell(
                        DoseFormat.rate(
                            alarmThresholds(sensitivity, customL1, customL2).l1MicroSvH,
                            unit,
                        ),
                        strings.thresholdL1,
                    ),
                ),
            )
            if (activeBaseline == null) {
                Text(
                    text = strings.noBandToCompare,
                    style = type.footnote,
                    color = colors.muted,
                )
            }

            SensitivityOption(
                title = strings.sensitivityNormal,
                selected = sensitivity == AlarmSensitivity.NORMAL,
                description = presetDescription(
                    alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f),
                    unit,
                    strings,
                ),
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.NORMAL) }
                },
            )
            SensitivityOption(
                title = strings.sensitivityHigh,
                selected = sensitivity == AlarmSensitivity.HIGH,
                description = presetDescription(
                    alarmThresholds(AlarmSensitivity.HIGH, 0f, 0f),
                    unit,
                    strings,
                ),
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.HIGH) }
                },
            )
            SensitivityOption(
                title = strings.sensitivityCustom,
                selected = sensitivity == AlarmSensitivity.CUSTOM,
                description = strings.sensitivityCustomNote,
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.CUSTOM) }
                },
            )

            if (sensitivity == AlarmSensitivity.CUSTOM) {
                CustomLevels(graph, unit, customL1, customL2)
            }
            Text(
                text = strings.alarmSoundElsewhere,
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

/** Deep link into the system settings of the «Тревога» notification channel. */
@Composable
internal fun AlarmSoundRow() {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
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
                    // Имя канала при этом пишется на языке интерфейса — иначе
                    // человек попадает в системные настройки к чужому слову.
                    Notifications.ensureChannels(
                        context,
                        NotificationCatalogue.of(strings.language),
                    )
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
            Text(text = strings.alarmSoundTitle, style = type.label, color = colors.ink)
            Hint(
                text = strings.alarmSoundNote,
                style = type.bodySmall,
                color = colors.muted,
            )
        }
        Text(text = "›", style = type.title, color = colors.ink2)
    }
}

@Composable
internal fun SensitivityOption(
    title: String,
    selected: Boolean,
    description: String,
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

internal fun presetDescription(
    thresholds: AlarmThresholds,
    unit: DoseUnitSetting,
    strings: Strings = RuStrings,
): String = strings.alarmPreset(
    level = DoseFormat.rateWithUnit(thresholds.l1MicroSvH, unit, s = strings),
    factor = formatFactor(thresholds.relativeFactor),
    held = heldWording(thresholds.persistenceSeconds.toLong(), strings),
)

internal fun formatFactor(factor: Float): String =
    if (factor == factor.toInt().toFloat()) "${factor.toInt()}" else "$factor"

@Composable
internal fun CustomLevels(
    graph: AppGraph,
    unit: DoseUnitSetting,
    storedL1MicroSvH: Float,
    storedL2MicroSvH: Float,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
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
        LevelField(strings.level1WithUnit(DoseFormat.rateUnitLabel(unit, s = strings)), l1Text) { l1Text = it }
        LevelField(strings.level2WithUnit(DoseFormat.rateUnitLabel(unit, s = strings)), l2Text) { l2Text = it }
        error?.let { Text(text = it, style = type.bodySmall, color = colors.warn) }
        AppButton(
            text = strings.saveLevels,
            onClick = {
                val l1 = parseLevelToMicroSv(l1Text, unit)
                val l2 = parseLevelToMicroSv(l2Text, unit)
                when {
                    l1 == null || l2 == null -> error = strings.enterNumbers
                    l1 <= 0f -> error = strings.level1MustBePositive
                    l2 < l1 -> error = strings.level2BelowLevel1
                    else -> {
                        error = null
                        scope.launch { graph.settings.setCustomAlarmLevels(l1, l2) }
                    }
                }
            },
        )
        Hint(
            text = strings.levelsNote,
            style = type.bodySmall,
            color = colors.muted,
        )
    }
}

@Composable
internal fun LevelField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
        Text(text = label, style = type.bodySmall, color = colors.ink2)
        AppTextField(value = value, onValueChange = onChange, numeric = true)
    }
}

/** Parses the display-unit input back to stored µSv/h; comma tolerated. */
internal fun parseLevelToMicroSv(text: String, unit: DoseUnitSetting): Float? {
    val value = text.trim().replace(',', '.').toFloatOrNull() ?: return null
    return when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> value
        DoseUnitSetting.MICRO_ROENTGEN -> value / DoseFormat.MICRO_R_PER_MICRO_SV
    }
}

// --- Профили ---

internal val PROFILE_ICONS = listOf("⌂", "▣", "⌾", "◈", "→", "○", "☾", "✦")
