package app.radiacode.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.data.export.html.RoutePrivacy
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppMenu
import app.radiacode.ui.components.AppMenuItem
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Hint
import app.radiacode.ui.text.ExportCatalogue
import app.radiacode.ui.text.ExportStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Экспорт одной записи: единый набор форматов и один способ их сохранить.
 *
 * ## Почему это здесь, а не в каждом экране
 *
 * Раньше каждая запись отдавала свой единственный формат своей кнопкой: сессия —
 * CSV, маршрут — GPX, опыт — текст. Кнопки выглядели по-разному, назывались
 * форматами и не давали того, что нужно чаще всего — читаемого отчёта. Форматы
 * собраны в одно меню, поэтому «сохранить» на любой записи выглядит одинаково,
 * а список пунктов зависит только от того, что у записи есть.
 *
 * ## Файл пишет человек, а не приложение
 *
 * Каждое сохранение проходит через системный диалог: приложение не выбирает
 * папку и не создаёт файлы само. Тип файла объявляется заранее — так системный
 * выбор папки предлагает подходящее имя и приложение для просмотра.
 */
internal enum class ExportFile(val mime: String, val extension: String) {
    HTML("text/html", "html"),
    CSV("text/csv", "csv"),
    JSON("application/json", "json"),

    /** GeoJSON: собственный тип понимают не все, поэтому файл остаётся JSON. */
    GEOJSON("application/json", "geojson"),
    GPX("application/gpx+xml", "gpx"),
}

/** Умеет отдать текст в файл, выбранный человеком. */
internal class FileSaver(
    private val pending: MutableState<String?>,
    private val launch: (ExportFile, String) -> Unit,
) {
    fun save(file: ExportFile, name: String, content: String) {
        pending.value = content
        launch(file, name)
    }
}

/**
 * Готовит сохранение в файл: пять контрактов создания документа и общее
 * состояние «что именно сохраняем».
 *
 * Контракт `CreateDocument` фиксирует тип файла в момент регистрации, поэтому
 * лаунчер на каждый тип — не дублирование, а требование системного API.
 */
@Composable
internal fun rememberFileSaver(onDone: (Boolean) -> Unit): FileSaver {
    val context = LocalContext.current
    val scope: CoroutineScope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<String?>(null) }
    val handle: (Uri?) -> Unit = { uri ->
        val content = pending.value
        pending.value = null
        if (uri != null && content != null) {
            scope.launch { onDone(writeTextToUri(context, uri, content)) }
        }
    }
    val html = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFile.HTML.mime),
        handle,
    )
    val csv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFile.CSV.mime),
        handle,
    )
    val json = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFile.JSON.mime),
        handle,
    )
    val geojson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFile.GEOJSON.mime),
        handle,
    )
    val gpx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFile.GPX.mime),
        handle,
    )
    return remember(html, csv, json, geojson, gpx) {
        FileSaver(pending) { file, name ->
            when (file) {
                ExportFile.HTML -> html.launch(name)
                ExportFile.CSV -> csv.launch(name)
                ExportFile.JSON -> json.launch(name)
                ExportFile.GEOJSON -> geojson.launch(name)
                ExportFile.GPX -> gpx.launch(name)
            }
        }
    }
}


/** Файл пакетной выгрузки: имя, тип и содержимое. */
internal data class ExportDocument(val name: String, val mime: String, val content: String)

/** Умеет разложить несколько файлов по папке, выбранной человеком. */
internal class FolderSaver(
    private val pending: MutableState<(suspend () -> List<ExportDocument>)?>,
    private val launch: () -> Unit,
) {
    fun save(build: suspend () -> List<ExportDocument>) {
        pending.value = build
        launch()
    }
}

/**
 * Пакетная выгрузка: человек выбирает ПАПКУ, приложение кладёт в неё по файлу
 * на запись.
 *
 * Папка, а не отдельный диалог на каждый файл: сохранить десять отчётов
 * десятью системными диалогами невозможно физически. Содержимое готовится
 * только после выбора папки — читать базу ради файлов, от которых человек
 * откажется, незачем.
 */
@Composable
internal fun rememberFolderSaver(onDone: (saved: Int, failed: Int) -> Unit): FolderSaver {
    val context = LocalContext.current
    val scope: CoroutineScope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<(suspend () -> List<ExportDocument>)?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        val build = pending.value
        pending.value = null
        if (tree != null && build != null) {
            scope.launch {
                var saved = 0
                var failed = 0
                for (document in build()) {
                    if (writeTextToFolder(context, tree, document)) saved++ else failed++
                }
                onDone(saved, failed)
            }
        }
    }
    return remember(picker) { FolderSaver(pending) { picker.launch(null) } }
}

private suspend fun writeTextToFolder(
    context: android.content.Context,
    tree: Uri,
    document: ExportDocument,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val target = DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            document.mime,
            document.name,
        ) ?: return@runCatching false
        context.contentResolver.openOutputStream(target)?.use { stream ->
            stream.write(document.content.toByteArray(Charsets.UTF_8))
        } ?: return@runCatching false
        true
    }.getOrDefault(false)
}

/** Пункт меню экспорта: что получится и в каком формате. */
internal data class ExportOption(
    val title: String,
    val hint: String,
    val onPick: () -> Unit,
)

/** Готовые пункты для форматов — чтобы названия не расходились по экранам. */
internal object ExportOptions {
    fun report(s: ExportStrings, onPick: () -> Unit) = ExportOption(s.report, s.reportHint, onPick)
    fun table(s: ExportStrings, onPick: () -> Unit) = ExportOption(s.table, s.tableHint, onPick)
    fun data(s: ExportStrings, onPick: () -> Unit) = ExportOption(s.data, s.dataHint, onPick)
    fun map(s: ExportStrings, onPick: () -> Unit) = ExportOption(s.mapData, s.mapDataHint, onPick)
    fun track(s: ExportStrings, onPick: () -> Unit) = ExportOption(s.track, s.trackHint, onPick)
    fun text(s: ExportStrings, onPick: () -> Unit) = ExportOption(s.text, s.textHint, onPick)
    fun oneReport(s: ExportStrings, onPick: () -> Unit) =
        ExportOption(s.oneReport, s.oneReportHint, onPick)
    fun separateFiles(s: ExportStrings, onPick: () -> Unit) =
        ExportOption(s.separateFiles, s.separateFilesHint, onPick)
}

/**
 * Чип «Экспорт» с меню форматов в языке терминала.
 *
 * Меню, а не ряд кнопок: экспорт — редкое действие, и три-четыре равновеликие
 * кнопки рядом с записью весят больше, чем сама запись.
 */
@Composable
internal fun ExportMenuChip(
    options: List<ExportOption>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val strings = LocalStrings.current
    val s = ExportCatalogue.of(strings.language)
    val colors = LocalAppColors.current
    ExportMenuHost(options = options, modifier = modifier) { onOpen ->
        Chip(
            text = s.export,
            color = if (enabled) colors.dataText else colors.muted,
            onClick = { if (enabled) onOpen() },
        )
    }
}

/** То же меню, но кнопкой — там, где экспорт стоит в ряду главных действий. */
@Composable
internal fun ExportMenuButton(
    options: List<ExportOption>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val strings = LocalStrings.current
    val s = ExportCatalogue.of(strings.language)
    ExportMenuHost(options = options, modifier = modifier) { onOpen ->
        AppButton(
            text = s.export,
            onClick = onOpen,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Якорь и меню под ним.
 *
 * Меню раскрывается ПОД якорем: экспорт стоит то в верхней строке записи, то в
 * ряду действий внизу, и высота якоря известна только после замера — именно она
 * отделяет список от кнопки, которой его открыли.
 */
@Composable
private fun ExportMenuHost(
    options: List<ExportOption>,
    modifier: Modifier = Modifier,
    anchor: @Composable (onOpen: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { Dimens.space1.roundToPx() }
    Box(
        modifier = modifier
            .wrapContentSize(Alignment.TopEnd)
            .onSizeChanged { anchorHeight = it.height },
    ) {
        anchor { open = true }
        AppMenu(
            expanded = open,
            onDismiss = { open = false },
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, anchorHeight + gap),
        ) {
            for (option in options) {
                ExportMenuRow(option) { open = false; option.onPick() }
            }
        }
    }
}

/** Строка меню: результат крупно, формат — приглушённой строкой под ним. */
@Composable
private fun ExportMenuRow(option: ExportOption, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column {
        AppMenuItem(text = option.title, onClick = onClick)
        Text(
            text = option.hint,
            style = type.footnote,
            color = colors.muted,
            modifier = Modifier.padding(
                start = Dimens.space3,
                end = Dimens.space3,
                bottom = 6.dp,
            ),
        )
    }
}

/**
 * Выбор формата отдельным окном.
 *
 * Нужен там, где экспорт вызван ИЗ меню строки: всплывающее меню поверх меню
 * теряет якорь и открывается неизвестно где. Окно же честно говорит, что
 * разговор продолжается — и следующим шагом будет вопрос о координатах.
 */
@Composable
internal fun ExportFormatDialog(
    options: List<ExportOption>,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val s = ExportCatalogue.of(strings.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = s.export, style = type.title, color = colors.ink)
                for (option in options) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AppButton(
                            text = option.title,
                            onClick = option.onPick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(text = option.hint, style = type.footnote, color = colors.muted)
                    }
                }
                AppButton(
                    text = strings.cancel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Выбор координат перед выгрузкой маршрута.
 *
 * Спрашивается ДО сохранения и только у маршрута: у сессии координат нет, и
 * лишний вопрос там был бы шумом. Вариант по умолчанию — полный маршрут: молча
 * урезать данные приложение не вправе, а решение принимает человек.
 */
@Composable
internal fun RoutePrivacyDialog(
    onPick: (RoutePrivacy) -> Unit,
    onDismiss: () -> Unit,
    /** Файл без координат имеет смысл только там, где остаётся что показать. */
    allowNoCoordinates: Boolean = true,
) {
    val strings = LocalStrings.current
    val s = ExportCatalogue.of(strings.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(text = s.coordinatesTitle, style = type.title, color = colors.ink)
                Hint(text = s.coordinatesNote)
                AppButton(
                    text = s.coordinatesFull,
                    onClick = { onPick(RoutePrivacy.FULL) },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = s.coordinatesTrimmed,
                    onClick = { onPick(RoutePrivacy.TRIM_ENDS) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowNoCoordinates) {
                    AppButton(
                        text = s.coordinatesNone,
                        onClick = { onPick(RoutePrivacy.NO_COORDINATES) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppButton(
                    text = strings.cancel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Имя приложения в подписи отчёта — его читают там, где приложения нет. */
internal const val REPORT_APP = "RadiaCode Companion"
