package app.alpha.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.alpha.AppGraph
import app.alpha.device.DeviceModel
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindow
import app.alpha.analysis.NuclideInfoLibrary
import app.alpha.analysis.DecayFamilies
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.SpectrumDisplay
import app.alpha.analysis.SpectrumEdge
import app.alpha.analysis.SpectrumMerge
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.data.export.N42
import app.alpha.data.export.RcXml
import app.alpha.data.export.SpectrumReportFactory
import app.alpha.data.export.html.SpectrumReportHtml
import app.alpha.data.export.SpectrumExport
import app.alpha.data.toEntity
import app.alpha.data.toSpectrum
import app.alpha.device.ConnectionState
import app.alpha.protocol.Spectrum
import app.alpha.service.SpectrumHub
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Card
import app.alpha.ui.components.EvidenceTag
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Segmented
import androidx.compose.material3.Slider
import app.alpha.ui.logic.SpectrumScale
import app.alpha.ui.logic.DeviceActionBlock
import app.alpha.ui.logic.SpectrumSource
import app.alpha.ui.logic.SpectrumSources
import app.alpha.ui.components.SpectrumChart
import app.alpha.ui.components.SpectrumChartSpec
import app.alpha.ui.components.NuclideInfoDialog
import app.alpha.ui.components.SpectrumLineMark
import app.alpha.ui.components.SpectrumPeakMark
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.PeakEvidenceBridge
import app.alpha.ui.logic.PeakMatch
import app.alpha.ui.logic.PeakRow
import app.alpha.ui.logic.involves
import app.alpha.ui.logic.primaryNuclide
import app.alpha.ui.logic.SpectrumHighlight
import app.alpha.ui.logic.Evidence
import app.alpha.ui.logic.SpectrumFormat
import app.alpha.ui.logic.SpectrumFrames
import app.alpha.ui.logic.SpectrumPlot
import app.alpha.ui.logic.SpectrumInfo
import app.alpha.ui.logic.SpectrumInfoLevel
import app.alpha.ui.logic.SpectrumInfoSection
import app.alpha.ui.logic.SpectrumViewOptions
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.ExportCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrumCatalogue
import app.alpha.ui.text.SpectrumStrings
import app.alpha.ui.text.NuclideCatalogue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.alpha.ui.theme.Motion
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Спектр: строка действий и её диалоги — сохранение в историю, запись фона,
 * выбор снимка, продолжение накопления, экспорт.
 *
 * Вынесено из `SpectrumScreen`, где рядом жили и разбор спектра, и управление
 * прибором, и таблица пиков. Поведение не менялось; подписи под кнопками
 * по-прежнему живут в справке «i», а не под ними.
 */

/**
 * Нижние действия экрана Спектр: `[Сохранить снимок] [Сделать фоном] [⋯]`.
 *
 * Раньше здесь стояли две строки по три кнопки (запись фона, сохранение,
 * сброс + импорт и два экспорта) и полоса пояснений под ними — шесть
 * равновеликих кнопок на экране, где обычных действий два. Частые действия
 * остались снаружи, редкие (сброс накопления, обмен файлами) уехали в «⋯»,
 * а объяснение форматов — туда же, к самим кнопкам экспорта.
 *
 * Строка живёт ВНЕ содержимого спектра: импорт чужого файла работает и без
 * прибора, поэтому кнопки просто гаснут, когда показывать нечего.
 */
@Composable
internal fun SpectrumActionsBar(
    graph: AppGraph,
    spectrum: Spectrum?,
    connected: Boolean,
    hubState: SpectrumHub.State,
    serialNumber: String?,
    /** Continuation mode: «Сохранить» persists the merged sum instead. */
    onSaveOverride: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val type = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hub = graph.spectrumHub

    val backgroundEntity by graph.measurementRepository.backgroundReference()
        .collectAsState(initial = null)

    var notice by remember { mutableStateOf<SpectrumFileNotice?>(null) }
    var savedAtMillis by remember { mutableStateOf<Long?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val e = ExportCatalogue.of(strings.language)
    val saver = rememberFileSaver { ok ->
        if (ok) {
            savedAtMillis = System.currentTimeMillis()
        } else {
            notice = SpectrumFileNotice(
                title = strings.exportFailedTitle,
                lines = listOf(strings.exportFailedBody),
                isError = true,
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch { notice = importRcXmlFile(graph, context, uri, s = t) }
        }
    }

    // Живой спектр выгружается тем же окном форматов, что и сохранённый
    // снимок: разница между ними — только в том, есть ли у записи прошлое.
    if (exporting && spectrum != null) {
        val now = System.currentTimeMillis()
        val entity = spectrum.toEntity(timestamp = now, accumulated = false)
        EntityExportSheet(
            title = e.export,
            groups = spectrumExportGroups(
                entity = entity,
                e = e,
                appVersion = appVersionName(context),
                language = strings.language,
                saver = saver,
                onPicked = { exporting = false },
            ),
            onDismiss = { exporting = false },
        )
    }

    // Приборные действия остаются на своих местах и просто гаснут: исчезнув,
    // они заставили бы искать, куда делась кнопка. Причина названа строкой
    // ниже — «недоступно» без причины хуже отсутствия.
    val deviceBlock = SpectrumSources.deviceActionBlock(viewingSnapshot = false, connected)
    val deviceActionsEnabled = deviceBlock == DeviceActionBlock.NONE
    // Кнопки НАЗЫВАЮТ РЕЗУЛЬТАТ и объясняют себя одной строкой: «Записать
    // фон» и «Сохранить» звучали как одно и то же действие, и разницу
    // приходилось спрашивать. Снимок уходит в журнал; фон объявляет спектр
    // обычной обстановкой — именно его вычитает режим «− фон».
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            // Подписей под кнопками нет: что делает каждая, сказано в справке
            // «i». Постоянные две строки мелким шрифтом читаются один раз, а
            // место у самого частого действия занимают всегда.
            AppButton(
                text = t.saveSnapshot,
                onClick = onSaveOverride ?: { hub.request(SpectrumHub.Command.SAVE_SNAPSHOT) },
                // Снимок уже сохранён — сохранять его во второй раз незачем.
                enabled = spectrum != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            AppButton(
                text = t.setAsBackground,
                onClick = { hub.request(SpectrumHub.Command.RECORD_BACKGROUND) },
                enabled = deviceActionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Цель нажатия не меньше пальца: у кнопки из одного символа ширина
        // иначе получается вдвое меньше высоты.
        AppButton(
            text = "⋯",
            onClick = { moreOpen = true },
            modifier = Modifier.defaultMinSize(minWidth = Dimens.touchTarget),
        )
    }

    // Статус фона — компактной строкой у своей кнопки: «фон: 11 авг · 51 ч».
    // Пока фона нет, на его месте стоит объяснение, что он даёт: пустое
    // состояние учит первому действию, а не молчит.
    val background = backgroundEntity
    val h = HistoryCatalogue.of(strings.language)
    Text(
        text = if (background != null) {
            t.backgroundRecorded(
                at = HistoryFormat.day(background.timestamp, s = h),
                accumulation = HistoryFormat.duration(background.durationSeconds, h),
            )
        } else {
            strings.noSpectrumBackground
        },
        style = type.footnote,
        color = colors.muted,
        modifier = Modifier.padding(horizontal = Dimens.space1),
    )
    // Подтверждения последнего действия — отдельной строкой и только когда
    // они есть: в строке состояния фона им места нет.
    val confirmation = buildString {
        hubState.lastSavedAtMillis?.let { append(t.snapshotSavedAt(timeOfDay(it))) }
        hubState.lastBackgroundAtMillis?.let {
            if (isNotEmpty()) append(" · ")
            append(t.backgroundRecordedAt(timeOfDay(it)))
        }
        savedAtMillis?.let { append(strings.savedToPrefix).append(timeOfDay(it)) }
    }.trim().trimStart('·').trim()
    if (confirmation.isNotBlank()) {
        Text(
            text = confirmation,
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = Dimens.space1),
        )
    }

    if (moreOpen) {
        SpectrumMoreDialog(
            t = t,
            hasSpectrum = spectrum != null,
            connected = deviceActionsEnabled,
            onReset = { moreOpen = false; confirmReset = true },
            onImport = { moreOpen = false; importLauncher.launch(arrayOf("*/*")) },
            onExport = { moreOpen = false; exporting = true },
            onDismiss = { moreOpen = false },
        )
    }

    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(strings.resetSpectrumTitle, style = type.title, color = colors.ink)
                    Text(text = strings.resetSpectrumBody, style = type.body, color = colors.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        AppButton(
                            text = strings.reset,
                            onClick = {
                                confirmReset = false
                                hub.request(SpectrumHub.Command.RESET)
                            },
                        )
                        AppButton(text = strings.cancel, onClick = { confirmReset = false })
                    }
                }
            }
        }
    }

    notice?.let { current ->
        SpectrumFileNoticeDialog(notice = current, onDismiss = { notice = null })
    }
}

/**
 * Выбор второго снимка для сравнения — тот же список, что в Истории, но без
 * самого открытого снимка: сравнивать спектр с самим собой нечего.
 */
@Composable
internal fun SnapshotPickerDialog(
    spectra: List<SpectrumSnapshotEntity>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    val h = HistoryCatalogue.of(strings.language)
    val now = System.currentTimeMillis()
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = strings.chooseSnapshotToCompare,
                    style = type.title,
                    color = colors.ink,
                )
                if (spectra.isEmpty()) {
                    Text(text = strings.noData, style = type.bodySmall, color = colors.muted)
                }
                for (entity in spectra) {
                    AppButton(
                        text = SpectrumExport.title(entity) + " · " +
                            HistoryFormat.dayTime(entity.timestamp, now, s = h),
                        onClick = { onPick(entity.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = strings.close,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Меню «⋯»: редкие операции экрана. Пояснение о форматах стоит здесь, рядом
 * с кнопками экспорта, а не полосой мелкого текста под всем экраном — его
 * читают один раз, ровно в момент выбора формата.
 */
/** Имя приложения в подписи отчёта: его читают там, где приложения нет. */
private const val REPORT_APP_NAME = "Alpha"

@Composable
internal fun SpectrumMoreDialog(
    t: SpectrumStrings,
    hasSpectrum: Boolean,
    connected: Boolean,
    onReset: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.moreActions, style = type.title, color = colors.ink)
                AppButton(
                    text = t.resetAccumulation,
                    onClick = onReset,
                    enabled = connected,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = strings.importAction,
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Один пункт вместо четырёх кнопок форматов: форматы живут в
                // окне экспорта, одном на всё приложение.
                AppButton(
                    text = ExportCatalogue.of(strings.language).export,
                    onClick = onExport,
                    enabled = hasSpectrum,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = strings.cancel,
                    style = type.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onDismiss)
                        .padding(Dimens.space1),
                )
            }
        }
    }
}

/**
 * Плашка режима «продолжить накопление»: честно объясняет семантику — прибор
 * копит независимо, сумма существует только для показа и сохранения.
 */
@Composable
internal fun ContinuationBanner(
    entity: SpectrumSnapshotEntity,
    merging: Boolean,
    invalidReason: String?,
    onStop: () -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.continuationTitle,
                    style = type.label,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Chip(text = strings.disable, color = colors.ink2, onClick = onStop)
            }
            Text(
                text = SpectrumExport.title(entity) +
                    strings.snapshotDeltaPrefix + SpectrumFormat.accumulationClock(entity.durationSeconds),
                style = type.valueSmall,
                color = colors.ink2,
            )
            when {
                invalidReason != null -> Text(
                    text = strings.sumImpossible(invalidReason),
                    style = type.footnote,
                    color = colors.warn,
                )
                merging -> Text(
                    text = strings.sumShown,
                    style = type.footnote,
                    color = colors.muted,
                )
                else -> Text(
                    text = strings.noLiveAccumulation,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            Hint(
                text = strings.continuationWarning,
            )
        }
    }
}
