package app.radiacode.ui.text

import app.radiacode.data.export.backup.BackupCounts
import app.radiacode.data.export.backup.BackupProblem
import app.radiacode.data.export.backup.BackupStage
import app.radiacode.data.export.backup.RestoreSummary

/**
 * Слова раздела «Данные и резервные копии».
 *
 * Правило то же, что во всём приложении: человек читает про свои данные, а не
 * про устройство программы. Внутри копии есть манифест, NDJSON и контрольные
 * суммы — на экране этих слов нет ни одного. Отказ при этом называет причину:
 * «копия повреждена» без указания, ЧТО именно не сошлось, оставляет человека
 * без следующего шага.
 */
interface BackupStrings {

    val sectionBackup: String
    val sectionStorage: String
    val createBackup: String
    val createBackupNote: String
    val restoreBackup: String
    val restoreBackupNote: String

    /**
     * Где живёт выгрузка отдельной записи.
     *
     * Копия и экспорт решают разные задачи, и их путают: копия возвращает
     * приложение к себе самому, а отчёт или таблица уезжают к другому
     * человеку. Второй путь не повторяется в настройках отдельным экраном —
     * он назван там, где его ищут, у самих записей.
     */
    val exportWhere: String
    val dataSize: String

    val saving: String
    val checking: String
    val restoring: String
    val saved: String
    val restored: String
    val failed: String
    val close: String
    val cancel: String

    val backupFound: String
    val contains: String
    val howToRestore: String
    val merge: String
    val mergeNote: String
    val replace: String
    val replaceNote: String
    val restoreAction: String

    // --- что и за какое время сохранять ---
    val whatToSave: String
    val periodTitle: String
    val periodAll: String
    val periodYear: String
    val periodMonth: String
    val periodWeek: String

    /** Что попадёт в копию при выбранном периоде — одной строкой. */
    fun periodSince(date: String): String

    /** У прочитанной копии период уже в прошлом: «в копии записи с …». */
    fun savedPeriod(date: String): String
    val periodEverything: String
    val saveAction: String
    val nothingChosen: String

    val partSettings: String
    val partProfiles: String
    val partMeasurements: String
    val partRoutes: String
    val partSpectra: String
    val partExperiments: String
    val settingsRestored: String

    fun stageName(stage: BackupStage): String
    fun problem(problem: BackupProblem): String
    fun contentLines(counts: BackupCounts): List<String>
    fun summaryAdded(summary: RestoreSummary): List<String>
    fun summarySkipped(summary: RestoreSummary): List<String>
}

object BackupRu : BackupStrings {

    override val sectionBackup = "Резервная копия"
    override val sectionStorage = "Хранение"
    override val createBackup = "Создать резервную копию"
    override val createBackupNote = "Один файл со всей историей и настройками"
    override val restoreBackup = "Восстановить из копии"
    override val restoreBackupNote = "Сначала копия читается и проверяется"
    override val exportWhere = "Отдельная сессия, маршрут, спектр или опыт выгружаются " +
        "из Журнала: у каждой записи есть «Экспорт» — отчёт для чтения или файл для обработки."
    override val dataSize = "Размер данных"

    override val saving = "Создание резервной копии"
    override val checking = "Проверка копии"
    override val restoring = "Восстановление"
    override val saved = "Копия сохранена"
    override val restored = "Готово"
    override val failed = "Не получилось"
    override val close = "Закрыть"
    override val cancel = "Отмена"

    override val backupFound = "Резервная копия"
    override val contains = "Содержит"
    override val howToRestore = "Как восстановить?"
    override val merge = "Объединить"
    override val mergeNote = "Добавит недостающее, ничего не удаляя"
    override val replace = "Заменить"
    override val replaceNote = "Текущие данные будут заменены содержимым копии"
    override val restoreAction = "Восстановить"

    override val whatToSave = "Что включить в копию?"
    override val periodTitle = "За какое время?"
    override val periodAll = "Всё время"
    override val periodYear = "Год"
    override val periodMonth = "Месяц"
    override val periodWeek = "Неделя"
    override fun periodSince(date: String) =
        "В копию попадут записи с $date; настройки и профили — целиком."
    override fun savedPeriod(date: String) = "в копии записи с $date"
    override val periodEverything = "В копию попадёт вся история."
    override val saveAction = "Выбрать файл"
    override val nothingChosen = "Отметьте хотя бы одну часть"

    override val partSettings = "Настройки"
    override val partProfiles = "Профили"
    override val partMeasurements = "История измерений"
    override val partRoutes = "Маршруты"
    override val partSpectra = "Спектры"
    override val partExperiments = "Эксперименты"
    override val settingsRestored = "Настройки восстановлены"

    override fun stageName(stage: BackupStage): String = when (stage) {
        BackupStage.PROFILES -> "профили"
        BackupStage.SETTINGS -> "настройки"
        BackupStage.SESSIONS -> "сессии"
        BackupStage.MEASUREMENTS -> "история измерений"
        BackupStage.EVENTS -> "события"
        BackupStage.RARE -> "состояние прибора"
        BackupStage.ROUTES -> "маршруты"
        BackupStage.POINTS -> "точки маршрутов"
        BackupStage.SPECTRA -> "спектры"
        BackupStage.SPECTROGRAM -> "спектрограмма"
        BackupStage.EXPERIMENTS -> "эксперименты"
        BackupStage.FINISHING -> "завершение"
    }

    override fun problem(problem: BackupProblem): String = when (problem) {
        BackupProblem.NotABackup -> "Это не резервная копия приложения."
        is BackupProblem.TooNew ->
            "Копия создана более новой версией приложения. Обновите приложение, " +
                "чтобы её восстановить."
        is BackupProblem.Missing -> "В копии не хватает части «${problem.entry}»."
        is BackupProblem.Corrupted ->
            "Копия повреждена: часть «${problem.entry}» не совпала с контрольной суммой. " +
                "Текущие данные не тронуты."
        is BackupProblem.Unreadable -> "Файл не читается: ${problem.message}"
    }

    override fun contentLines(counts: BackupCounts): List<String> = buildList {
        if (counts.measurements > 0) add("${HistoryCount.of(counts.measurements)} измерений")
        if (counts.sessions > 0) add("${HistoryCount.of(counts.sessions)} сессий")
        if (counts.routes > 0) add("${HistoryCount.of(counts.routes)} маршрутов")
        if (counts.spectra > 0) add("${HistoryCount.of(counts.spectra)} спектров")
        if (counts.experiments > 0) add("${HistoryCount.of(counts.experiments)} экспериментов")
        if (counts.slices > 0) add("${HistoryCount.of(counts.slices)} срезов спектрограммы")
    }

    override fun summaryAdded(summary: RestoreSummary): List<String> =
        summary.added.filterValues { it > 0 }.map { (stage, count) ->
            "Добавлено: ${HistoryCount.of(count)} — ${stageName(stage)}"
        }

    override fun summarySkipped(summary: RestoreSummary): List<String> =
        summary.skipped.filterValues { it > 0 }.map { (stage, count) ->
            "Уже было: ${HistoryCount.of(count)} — ${stageName(stage)}"
        }
}

object BackupEn : BackupStrings {

    override val sectionBackup = "Backup"
    override val sectionStorage = "Storage"
    override val createBackup = "Create a backup"
    override val createBackupNote = "One file with the whole history and the settings"
    override val restoreBackup = "Restore from a backup"
    override val restoreBackupNote = "The copy is read and checked first"
    override val exportWhere = "A single session, route, spectrum or experiment is exported " +
        "from the History: every record has «Export» — a report to read or a file to process."
    override val dataSize = "Data size"

    override val saving = "Creating the backup"
    override val checking = "Checking the backup"
    override val restoring = "Restoring"
    override val saved = "Backup saved"
    override val restored = "Done"
    override val failed = "It did not work"
    override val close = "Close"
    override val cancel = "Cancel"

    override val backupFound = "Backup"
    override val contains = "Contains"
    override val howToRestore = "How should it be restored?"
    override val merge = "Merge"
    override val mergeNote = "Adds what is missing and deletes nothing"
    override val replace = "Replace"
    override val replaceNote = "Current data will be replaced by the backup"
    override val restoreAction = "Restore"

    override val whatToSave = "What goes into the copy?"
    override val periodTitle = "Which period?"
    override val periodAll = "All time"
    override val periodYear = "A year"
    override val periodMonth = "A month"
    override val periodWeek = "A week"
    override fun periodSince(date: String) =
        "The copy will hold records since $date; settings and profiles in full."
    override fun savedPeriod(date: String) = "the copy holds records since $date"
    override val periodEverything = "The copy will hold the whole history."
    override val saveAction = "Choose a file"
    override val nothingChosen = "Tick at least one part"

    override val partSettings = "Settings"
    override val partProfiles = "Profiles"
    override val partMeasurements = "Measurement history"
    override val partRoutes = "Routes"
    override val partSpectra = "Spectra"
    override val partExperiments = "Experiments"
    override val settingsRestored = "Settings restored"

    override fun stageName(stage: BackupStage): String = when (stage) {
        BackupStage.PROFILES -> "profiles"
        BackupStage.SETTINGS -> "settings"
        BackupStage.SESSIONS -> "sessions"
        BackupStage.MEASUREMENTS -> "measurement history"
        BackupStage.EVENTS -> "events"
        BackupStage.RARE -> "instrument state"
        BackupStage.ROUTES -> "routes"
        BackupStage.POINTS -> "route points"
        BackupStage.SPECTRA -> "spectra"
        BackupStage.SPECTROGRAM -> "spectrogram"
        BackupStage.EXPERIMENTS -> "experiments"
        BackupStage.FINISHING -> "finishing"
    }

    override fun problem(problem: BackupProblem): String = when (problem) {
        BackupProblem.NotABackup -> "This is not a backup of the app."
        is BackupProblem.TooNew ->
            "This backup was made by a newer version of the app. Update the app to restore it."
        is BackupProblem.Missing -> "The backup is missing the «${problem.entry}» part."
        is BackupProblem.Corrupted ->
            "The backup is damaged: «${problem.entry}» does not match its checksum. " +
                "Current data is untouched."
        is BackupProblem.Unreadable -> "The file cannot be read: ${problem.message}"
    }

    override fun contentLines(counts: BackupCounts): List<String> = buildList {
        if (counts.measurements > 0) add("${HistoryCount.of(counts.measurements)} measurements")
        if (counts.sessions > 0) add("${HistoryCount.of(counts.sessions)} sessions")
        if (counts.routes > 0) add("${HistoryCount.of(counts.routes)} routes")
        if (counts.spectra > 0) add("${HistoryCount.of(counts.spectra)} spectra")
        if (counts.experiments > 0) add("${HistoryCount.of(counts.experiments)} experiments")
        if (counts.slices > 0) add("${HistoryCount.of(counts.slices)} spectrogram slices")
    }

    override fun summaryAdded(summary: RestoreSummary): List<String> =
        summary.added.filterValues { it > 0 }.map { (stage, count) ->
            "Added: ${HistoryCount.of(count)} — ${stageName(stage)}"
        }

    override fun summarySkipped(summary: RestoreSummary): List<String> =
        summary.skipped.filterValues { it > 0 }.map { (stage, count) ->
            "Already there: ${HistoryCount.of(count)} — ${stageName(stage)}"
        }
}

/** Большое число с разделителями разрядов — «1 248 331». */
internal object HistoryCount {
    fun of(value: Long): String = value.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}

val BackupCatalogue = AreaCatalogue(ru = BackupRu, en = BackupEn)

/** Все строки области — для проверок, действующих на каждый язык. */
fun BackupStrings.allTexts(): List<String> = listOf(
    sectionBackup, sectionStorage, createBackup, createBackupNote, exportWhere,
    restoreBackup, restoreBackupNote, dataSize,
    saving, checking, restoring, saved, restored, failed, close, cancel,
    backupFound, contains, howToRestore, merge, mergeNote, replace, replaceNote,
    whatToSave, periodTitle, periodAll, periodYear, periodMonth, periodWeek,
    periodSince("17 июля 2026"), savedPeriod("17 июля 2026"), periodEverything,
    saveAction, nothingChosen,
    restoreAction, partSettings, partProfiles, partMeasurements, partRoutes,
    partSpectra, partExperiments, settingsRestored,
) + BackupStage.entries.map { stageName(it) } + listOf(
    problem(BackupProblem.NotABackup),
    problem(BackupProblem.TooNew(2, 1)),
    problem(BackupProblem.Missing("measurements")),
    problem(BackupProblem.Corrupted("measurements")),
    problem(BackupProblem.Unreadable("нет доступа")),
) + contentLines(
    BackupCounts(
        measurements = 1_248_331,
        sessions = 42,
        routes = 18,
        spectra = 27,
        experiments = 6,
        slices = 1_000,
    ),
)
