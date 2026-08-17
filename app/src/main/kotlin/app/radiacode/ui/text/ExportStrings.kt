package app.radiacode.ui.text

/**
 * Экспорт: одно меню на все записи приложения.
 *
 * Пункты названы РЕЗУЛЬТАТОМ, а формат идёт следом мелким шрифтом: человеку
 * нужен «отчёт, который откроется в браузере», а «HTML» — это ответ на другой
 * вопрос. Формат при этом не спрятан: тот, кому важен именно он, читает вторую
 * половину строки.
 *
 * Координаты — отдельный разговор перед сохранением: файл маршрута уезжает из
 * телефона, и то, что вместе с ним уезжает адрес дома, человек должен решить
 * сам, а не узнать потом.
 */
interface ExportStrings {

    /** Действие, открывающее меню. */
    val export: String

    // --- пункты меню: результат + формат ---
    val report: String
    val reportHint: String
    val table: String
    val tableHint: String
    val data: String
    val dataHint: String
    val mapData: String
    val mapDataHint: String
    val track: String
    val trackHint: String
    val text: String
    val textHint: String
    val oneReport: String
    val oneReportHint: String
    val separateFiles: String
    val separateFilesHint: String

    val saved: String
    fun filesSaved(count: Int): String
    val failed: String
    val preparing: String

    // --- координаты маршрута ---
    val coordinatesTitle: String
    val coordinatesNote: String
    val coordinatesFull: String
    val coordinatesTrimmed: String
    val coordinatesNone: String

    // --- выбор нескольких записей ---
    val select: String
    fun selected(count: Int): String
    val selectAll: String
    val clearSelection: String
    val nothingSelected: String
}

internal object ExportRu : ExportStrings {
    override val export = "Экспорт"

    override val report = "Отчёт"
    override val reportHint = "HTML · откроется в браузере"
    override val table = "Таблица"
    override val tableHint = "CSV · для Excel и обработки"
    override val data = "Данные"
    override val dataHint = "JSON · все числа как есть"
    override val mapData = "Карта"
    override val mapDataHint = "GeoJSON · для карт и ГИС"
    override val track = "След"
    override val trackHint = "GPX · для навигаторов"
    override val text = "Текст"
    override val textHint = "TXT · чтобы вставить в сообщение"
    override val oneReport = "Один отчёт"
    override val oneReportHint = "записи на одном графике и в общей таблице"
    override val separateFiles = "Отдельные файлы"
    override val separateFilesHint = "по отчёту на запись, в выбранную папку"

    override val saved = "файл сохранён"
    override fun filesSaved(count: Int) = "сохранено файлов: $count"
    override val failed = "сохранить не удалось: выберите другую папку"
    override val preparing = "готовим файл…"

    override val coordinatesTitle = "Координаты в файле"
    override val coordinatesNote =
        "Начало и конец маршрута обычно у дома. Решите это до того, как отправите файл."
    override val coordinatesFull = "Полный маршрут"
    override val coordinatesTrimmed = "Без начала и конца"
    override val coordinatesNone = "Без координат, только измерения"

    override val select = "Выбрать"
    override fun selected(count: Int) = "выбрано: $count"
    override val selectAll = "Выбрать все"
    override val clearSelection = "Снять выбор"
    override val nothingSelected = "Отметьте записи, которые нужно сохранить"
}

internal object ExportEn : ExportStrings {
    override val export = "Export"

    override val report = "Report"
    override val reportHint = "HTML · opens in a browser"
    override val table = "Table"
    override val tableHint = "CSV · for spreadsheets"
    override val data = "Data"
    override val dataHint = "JSON · every number as recorded"
    override val mapData = "Map"
    override val mapDataHint = "GeoJSON · for maps and GIS"
    override val track = "Track"
    override val trackHint = "GPX · for navigators"
    override val text = "Text"
    override val textHint = "TXT · to paste into a message"
    override val oneReport = "One report"
    override val oneReportHint = "records on one chart and in one table"
    override val separateFiles = "Separate files"
    override val separateFilesHint = "one report per record, into a folder you pick"

    override val saved = "file saved"
    override fun filesSaved(count: Int) = "files saved: $count"
    override val failed = "could not save: choose another folder"
    override val preparing = "preparing the file…"

    override val coordinatesTitle = "Coordinates in the file"
    override val coordinatesNote =
        "A route usually starts and ends at home. Decide this before you send the file."
    override val coordinatesFull = "Whole route"
    override val coordinatesTrimmed = "Without the start and the end"
    override val coordinatesNone = "No coordinates, measurements only"

    override val select = "Select"
    override fun selected(count: Int) = "selected: $count"
    override val selectAll = "Select all"
    override val clearSelection = "Clear selection"
    override val nothingSelected = "Tick the records you want to save"
}

val ExportCatalogue = AreaCatalogue(ru = ExportRu, en = ExportEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun ExportStrings.allTexts(): List<String> = listOf(
    export,
    report, reportHint, table, tableHint, data, dataHint,
    mapData, mapDataHint, track, trackHint, text, textHint,
    oneReport, oneReportHint, separateFiles, separateFilesHint,
    saved, filesSaved(2), failed, preparing,
    coordinatesTitle, coordinatesNote,
    coordinatesFull, coordinatesTrimmed, coordinatesNone,
    select, selected(3), selectAll, clearSelection, nothingSelected,
)
