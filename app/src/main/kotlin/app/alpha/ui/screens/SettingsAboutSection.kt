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
import app.alpha.ui.components.DisclosureArrow
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.AppTab
import app.alpha.ui.components.AppTextField
import app.alpha.ui.components.Card
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
 * Настройки → О приложении: лицензии зависимостей, версия и история обновлений.
 *
 * Вынесено из `SettingsScreen` вместе с тревогами и профилями. Список
 * обновлений ведётся руками (`ui/logic/ReleaseNotes.kt`) — сообщения коммитов
 * написаны для тех, кто читает код, а здесь нужен ответ «что изменилось у меня
 * на экране».
 */

/**
 * The only «about» content left: the version and the notices the bundled
 * third-party work legally requires. Kept deliberately — the app ships a
 * Kotlin port of cdump/radiacode (MIT), Kable and osmdroid (Apache-2.0) and
 * IBM Plex (OFL), and renders OpenStreetMap data (ODbL): all four licences
 * require the notice to travel with the binary, so this section is not
 * decoration and must not be trimmed away.
 */
@Composable
internal fun LicensesSection() {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val context = LocalContext.current
    var licensesText by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    var showNotes by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showLicenses) {
        if (showLicenses && licensesText == null) {
            licensesText = runCatching {
                LICENSE_ASSETS.joinToString("\n\n" + "─".repeat(24) + "\n\n") { path ->
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }
            }.getOrElse { strings.licencesUnreadable }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            SectionTitle(strings.settingsAbout)
            VersionRow(showNotes) { showNotes = !showNotes }
            AnimatedVisibility(
                visible = showNotes,
                enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
                exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
            ) {
                ReleaseNotesList()
            }
            SectionTitle(strings.licencesTitle)
            Hint(
                text = strings.licencesBody,
                style = type.bodySmall,
            )
            AppButton(
                text = if (showLicenses) strings.hideLicences else strings.showLicences,
                onClick = { showLicenses = !showLicenses },
            )
            if (showLicenses) {
                Text(
                    text = licensesText ?: strings.reading,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
        }
    }
}

/**
 * Версия — и вход в короткую историю обновлений.
 *
 * Номер версии сам по себе не отвечает на вопрос, который человек задаёт,
 * нажимая на него: «а что изменилось?». Поэтому строка кликабельная, а под ней
 * раскрывается список последних обновлений человеческими словами.
 */
@Composable
internal fun VersionRow(expanded: Boolean, onToggle: () -> Unit) {
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
                onClick = onToggle,
            ),
    ) {
        Column(Modifier.weight(1f)) {
            // Имя из сборки плюс версия из неё же: под иконкой версии нет,
            // и это единственное место, где её ищут.
            Text(
                text = "${stringResource(R.string.app_name)} ${ReleaseNotes.current}",
                style = type.label,
                color = colors.ink,
            )
            Text(
                text = if (expanded) strings.recentUpdates else strings.whatChanged,
                style = type.footnote,
                color = colors.muted,
            )
        }
        DisclosureArrow(expanded = expanded)
    }
}

/** Последние [ReleaseNotes.SHOWN] обновлений — на языке интерфейса. */
@Composable
internal fun ReleaseNotesList() {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val notes = ReleaseCatalogue.of(LocalStrings.current.language)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        for (note in ReleaseNotes.shownIn(notes)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = note.version, style = type.axis, color = colors.ink2)
                    Text(text = note.title, style = type.label, color = colors.ink)
                }
                Text(text = note.summary, style = type.bodySmall, color = colors.muted)
            }
        }
    }
}

internal val LICENSE_ASSETS = listOf(
    "licenses/cdump_radiacode_NOTICE.txt",
    "licenses/ibm_plex_NOTICE.txt",
    "licenses/ibm_plex_sans_OFL.txt",
    "licenses/ibm_plex_mono_OFL.txt",
)
