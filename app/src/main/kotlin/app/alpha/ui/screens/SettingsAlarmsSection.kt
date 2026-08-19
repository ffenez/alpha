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
import app.alpha.ui.components.Card
import app.alpha.ui.components.SettingRow
import app.alpha.ui.components.SettingsDivider
import app.alpha.ui.components.SettingsSection
import app.alpha.ui.components.Chip
import app.alpha.ui.components.RadioMark
import app.alpha.service.DeviceControlHub
import app.alpha.ui.components.Segmented
import app.alpha.ui.components.StatCell
import app.alpha.ui.components.StatGrid
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
import app.alpha.data.export.SpectrumTraffic
import app.alpha.device.DoseUnits
import app.alpha.ui.logic.MonitorStatus
import app.alpha.ui.logic.statusDetail
import app.alpha.ui.logic.statusHeadline
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
    // прибор показывает сейчас, и с тем, что здесь обычно. Они остались, но
    // ушли под «Как это работает?»: экран настройки отвечает на вопрос «что
    // выбрано», а справка — на вопрос «почему именно так».
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)
    val baselineState by graph.serviceStatus.baseline.collectAsState()
    val activeBaseline = (baselineState as? BaselineState.Active)?.baseline
    val currentDose = sample?.let { DoseUnits.rawToMicroSievertPerHour(it.doseRate) }
    val thresholds = alarmThresholds(sensitivity, customL1, customL2)
    var explained by rememberSaveable { mutableStateOf(false) }

    val modes = listOf(AlarmSensitivity.NORMAL, AlarmSensitivity.HIGH, AlarmSensitivity.CUSTOM)
    SettingsSection(title = strings.alarmModeTitle) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimens.space3,
                vertical = Dimens.space2,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            // Режимов три, они взаимоисключающие и их меняют быстро — это
            // ровно тот случай, для которого существует сегментированный
            // выбор. Три строки с радио-метками и абзацем описания у каждой
            // читались как документ, а не как выбор из трёх.
            Segmented(
                options = modes.map { modeTitle(it, strings) },
                selectedIndex = modes.indexOf(sensitivity).coerceAtLeast(0),
                onSelect = { index ->
                    scope.launch { graph.settings.setAlarmSensitivity(modes[index]) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(
                text = modeSummary(sensitivity, thresholds, strings),
                style = type.footnote,
                color = colors.muted,
            )
        }
        SettingsDivider()
        SettingRow(
            title = strings.thresholdNow,
            // Критерий порога — данные: он говорит, ОТ ЧЕГО считается «×N».
            subtitle = strings.relativeCriterion(formatFactor(thresholds.relativeFactor)),
            subtitleIsExplanation = false,
            value = DoseFormat.rateWithUnit(thresholds.l1MicroSvH, unit, s = strings),
            valueHighlighted = true,
        )
        if (sensitivity == AlarmSensitivity.CUSTOM) {
            SettingsDivider()
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.space3,
                    vertical = Dimens.space2,
                ),
            ) {
                CustomLevels(graph, unit, customL1, customL2)
            }
        }
        SettingsDivider()
        SettingRow(
            title = strings.howItWorks,
            onClick = { explained = !explained },
        )
        AnimatedVisibility(
            visible = explained,
            enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
            exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = Dimens.space3,
                    end = Dimens.space3,
                    bottom = Dimens.space3,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Hint(text = strings.alarmsIntro, style = type.bodySmall, color = colors.ink2)
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
                            DoseFormat.rate(thresholds.l1MicroSvH, unit),
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
                for (mode in modes) {
                    if (mode == AlarmSensitivity.CUSTOM) continue
                    Text(
                        text = modeTitle(mode, strings) + " · " +
                            presetDescription(alarmThresholds(mode, 0f, 0f), unit, strings),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                Text(
                    text = strings.alarmSoundElsewhere,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

/** Название режима чувствительности на языке интерфейса. */
private fun modeTitle(mode: AlarmSensitivity, s: Strings): String = when (mode) {
    AlarmSensitivity.NORMAL -> s.sensitivityNormal
    AlarmSensitivity.HIGH -> s.sensitivityHigh
    AlarmSensitivity.CUSTOM -> s.sensitivityCustom
}

/**
 * Одна строка о выбранном режиме: чем он отличается и сколько держится
 * превышение до подтверждения. Формулы и пороги остальных режимов — под
 * «Как это работает?».
 */
private fun modeSummary(
    mode: AlarmSensitivity,
    thresholds: AlarmThresholds,
    s: Strings,
): String {
    val held = heldWording(thresholds.persistenceSeconds.toLong(), s)
    return when (mode) {
        AlarmSensitivity.NORMAL -> s.sensitivityNormalNote(held)
        AlarmSensitivity.HIGH -> s.sensitivityHighNote(held)
        AlarmSensitivity.CUSTOM -> s.sensitivityCustomNote
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
        NavArrow()
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
