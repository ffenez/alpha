package app.radiacode.ui.screens

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
import app.radiacode.AppGraph
import app.radiacode.device.DeviceModel
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindow
import app.radiacode.analysis.NuclideInfoLibrary
import app.radiacode.analysis.DecayFamilies
import app.radiacode.analysis.PeakDetection
import app.radiacode.analysis.SpectrumDisplay
import app.radiacode.analysis.SpectrumEdge
import app.radiacode.analysis.SpectrumMerge
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.export.N42
import app.radiacode.data.export.RcXml
import app.radiacode.data.export.SpectrumExport
import app.radiacode.data.toEntity
import app.radiacode.data.toSpectrum
import app.radiacode.device.ConnectionState
import app.radiacode.protocol.Spectrum
import app.radiacode.service.SpectrumHub
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.EvidenceTag
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Segmented
import androidx.compose.material3.Slider
import app.radiacode.ui.logic.SpectrumScale
import app.radiacode.ui.logic.DeviceActionBlock
import app.radiacode.ui.logic.SpectrumSource
import app.radiacode.ui.logic.SpectrumSources
import app.radiacode.ui.components.SpectrumChart
import app.radiacode.ui.components.SpectrumChartSpec
import app.radiacode.ui.components.NuclideInfoDialog
import app.radiacode.ui.components.SpectrumLineMark
import app.radiacode.ui.components.SpectrumPeakMark
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.PeakEvidenceBridge
import app.radiacode.ui.logic.PeakMatch
import app.radiacode.ui.logic.PeakRow
import app.radiacode.ui.logic.involves
import app.radiacode.ui.logic.primaryNuclide
import app.radiacode.ui.logic.SpectrumHighlight
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.SpectrumFormat
import app.radiacode.ui.logic.SpectrumFrames
import app.radiacode.ui.logic.SpectrumPlot
import app.radiacode.ui.logic.SpectrumInfo
import app.radiacode.ui.logic.SpectrumInfoLevel
import app.radiacode.ui.logic.SpectrumInfoSection
import app.radiacode.ui.logic.SpectrumViewOptions
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SpectrumCatalogue
import app.radiacode.ui.text.SpectrumStrings
import app.radiacode.ui.text.NuclideCatalogue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.radiacode.ui.theme.Motion
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
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
    /** Просмотр снимка: приборных действий у него нет, и это сказано словами. */
    viewingSnapshot: Boolean = false,
    snapshotEntity: SpectrumSnapshotEntity? = null,
    onCompareWith: (Long) -> Unit = {},
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
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var comparePickerOpen by remember { mutableStateOf(false) }
    // Сравнивать снимок есть с чем только тогда, когда снимков больше одного.
    val otherSpectra by graph.measurementRepository.savedSpectra()
        .collectAsState(initial = emptyList())

    fun onWritten(ok: Boolean) {
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

    val exportXmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            scope.launch { onWritten(writeTextToUri(context, uri, content)) }
        }
    }
    val exportN42Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            scope.launch { onWritten(writeTextToUri(context, uri, content)) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch { notice = importRcXmlFile(graph, context, uri, s = t) }
        }
    }

    // Экспорт и импорт готовятся здесь, а вызываются из меню «⋯».
    // У снимка экспортируется ОН САМ — со своим временем, меткой и без
    // серийника подключённого сейчас прибора: этот прибор его не снимал.
    val exportSerial = if (viewingSnapshot) null else serialNumber
    val onExportXml: () -> Unit = {
        if (spectrum != null) {
            val now = snapshotEntity?.timestamp ?: System.currentTimeMillis()
            val entity = snapshotEntity ?: spectrum.toEntity(timestamp = now, accumulated = false)
            pendingExport = RcXml.write(
                SpectrumExport.toResultData(
                    entity = entity,
                    background = if (viewingSnapshot) null else backgroundEntity,
                    serialNumber = exportSerial,
                    appVersion = appVersionName(context),
                ),
            )
            exportXmlLauncher.launch(SpectrumExport.fileName(now, "xml"))
        }
    }
    val onExportN42: () -> Unit = {
        if (spectrum != null) {
            val now = snapshotEntity?.timestamp ?: System.currentTimeMillis()
            val entity = snapshotEntity ?: spectrum.toEntity(timestamp = now, accumulated = false)
            pendingExport = N42.write(
                foreground = SpectrumExport.toN42Measurement(entity, N42.CLASS_FOREGROUND),
                background = if (viewingSnapshot) null else backgroundEntity?.let {
                    SpectrumExport.toN42Measurement(it, N42.CLASS_BACKGROUND)
                },
                serialNumber = exportSerial,
                model = SpectrumExport.modelFromSerial(exportSerial),
                softwareVersion = appVersionName(context),
                // Спец §22: метод, нормализация, калибровка и версии
                // алгоритмов едут вместе с файлом.
                remarks = SpectrumExport.metadataLines(entity, appVersionName(context)),
            )
            exportN42Launcher.launch(SpectrumExport.fileName(now, "n42"))
        }
    }

    // Приборные действия остаются на своих местах и просто гаснут: исчезнув,
    // они заставили бы искать, куда делась кнопка. Причина названа строкой
    // ниже — «недоступно» без причины хуже отсутствия.
    val deviceBlock = SpectrumSources.deviceActionBlock(viewingSnapshot, connected)
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
                enabled = spectrum != null && !viewingSnapshot,
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
    if (viewingSnapshot) {
        Text(
            text = t.snapshotNoDevice,
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = Dimens.space1),
        )
    } else Text(
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
            viewingSnapshot = viewingSnapshot,
            onCompare = { moreOpen = false; comparePickerOpen = true },
            onReset = { moreOpen = false; confirmReset = true },
            onImport = { moreOpen = false; importLauncher.launch(arrayOf("*/*")) },
            onExportXml = { moreOpen = false; onExportXml() },
            onExportN42 = { moreOpen = false; onExportN42() },
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

    if (comparePickerOpen) {
        SnapshotPickerDialog(
            spectra = otherSpectra.filter { it.id != snapshotEntity?.id },
            onPick = { id ->
                comparePickerOpen = false
                onCompareWith(id)
            },
            onDismiss = { comparePickerOpen = false },
        )
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
@Composable
internal fun SpectrumMoreDialog(
    t: SpectrumStrings,
    hasSpectrum: Boolean,
    connected: Boolean,
    /** Просмотр снимка: импорта чужого файла отсюда нет, зато есть сравнение. */
    viewingSnapshot: Boolean = false,
    onCompare: () -> Unit = {},
    onReset: () -> Unit,
    onImport: () -> Unit,
    onExportXml: () -> Unit,
    onExportN42: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.moreActions, style = type.title, color = colors.ink)
                if (viewingSnapshot) {
                    AppButton(
                        text = strings.compareWithAnother,
                        onClick = onCompare,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = t.resetAccumulation,
                    onClick = onReset,
                    enabled = connected,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!viewingSnapshot) {
                    AppButton(
                        text = strings.importAction,
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = strings.exportXml,
                    onClick = onExportXml,
                    enabled = hasSpectrum,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = strings.exportN42,
                    onClick = onExportN42,
                    enabled = hasSpectrum,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = strings.exportFormatsNote,
                    style = type.footnote,
                    color = colors.muted,
                )
                if (viewingSnapshot) {
                    Text(
                        text = t.snapshotNoDevice,
                        style = type.footnote,
                        color = colors.muted,
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
            Text(
                text = strings.continuationWarning,
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}
