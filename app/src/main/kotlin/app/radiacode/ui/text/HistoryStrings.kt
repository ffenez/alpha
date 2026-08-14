package app.radiacode.ui.text

/**
 * История: длительности, даты, участие сессии в обычном фоне, проекция дозы
 * и формулировки удаления.
 *
 * Числительные каждый язык склоняет САМ (`sessions(n)`, `spectra(n)`,
 * `events(n)`): русская тройка «сессия/сессии/сессий» и английская пара
 * «session/sessions» — разные правила, и попытка выразить их одной функцией
 * с тремя аргументами навязала бы английскому русскую грамматику.
 *
 * Отдельно про удаление: это единственное место приложения, где данные
 * действительно исчезают. Английский обязан говорить об этом так же прямо —
 * никаких «clean up» вместо «delete».
 */
interface HistoryStrings {

    /** Сокращения месяцев, 12 штук, январь первым. */
    val months: List<String>

    // ------------------------------------------------------- длительности
    fun seconds(value: Long): String
    fun minutes(value: Long): String
    fun hours(value: Long): String
    fun hoursMinutes(hours: Long, minutes: Long): String

    // ------------------------------ участие сессии в обычном фоне (спец §20)
    val admissionYes: String
    fun admissionPartial(excluded: String, reason: String): String
    val admissionNoData: String
    fun admissionNo(reason: String): String

    // ------------------------------------ накопленная доза: легенда и справка
    /**
     * Обозначение полого столбца в легенде графика. Смысл прежний: такой день
     * измерен не полностью и с полным днём физически не сравним, — но на
     * рабочем экране он теперь назван, а объяснён в справке.
     */
    /**
     * Свёрнутая карточка накопленной дозы: три числа одной строкой.
     *
     * Блок занимал полэкрана Истории графиком, проекцией и объяснениями, хотя
     * приходят сюда за последними записями. Свёрнутый он отвечает на тот же
     * вопрос — «сколько набралось» — тремя числами и одной строкой о том, за
     * какое реально измеренное время.
     */
    fun doseGlance(today: String, week: String, month: String): String
    fun measuredFor(duration: String): String
    fun recordedOfPeriod(duration: String): String
    val days90: String

    /** Заголовок раскрываемой годовой оценки — условие, а не термин. */

    /**
     * Склеенная запись: сколько кусков и сколько перерыва внутри.
     *
     * Склейка не имеет права быть незаметной: человек должен видеть, что
     * измерение шло с перерывом, а не гадать, почему числа не сходятся с
     * длительностью.
     */
    fun mergedPieces(pieces: Int, gap: String): String


    /** Заголовок справки «i» карточки накопленной дозы. */
    val infoTitle: String

    // --------------------------------------------- проекция дозы (спец §6)
    fun doseProjection(doseWithUnit: String): String
    fun doseProjectionBasis(rateWithUnit: String, measured: String): String

    /**
     * Короткий отказ, который стоит прямо под проекцией: что это за величина и
     * чем она НЕ является. Перечень того, что в неё не входит, уехал в справку
     * ([doseProjectionCaveat]) — сокращается перечисление, а не сам отказ.
     */
    val doseProjectionCaveatShort: String

    val doseProjectionCaveat: String
    fun doseProjectionUnavailable(measured: String): String

    // ------------------------------------------------------------ удаление
    val delete: String
    fun deleteCount(count: Int): String
    val deleteSelectedTitle: String
    fun deleteSpectraTitle(count: Int): String
    fun deleteSessionsTitle(count: Int): String
    fun sessionsWithDuration(sessions: String, duration: String): String
    fun samplesGone(count: String): String
    fun eventsInside(events: String): String
    fun spectraFromList(spectra: String): String
    val cannotUndo: String
    val tracksAndSpectraStay: String
    val measurementsUntouched: String
    val markWhatToDelete: String

    fun sessions(n: Int): String
    fun spectra(n: Int): String
    fun events(n: Int): String
}

object HistoryRu : HistoryStrings {
    override val months = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    override fun seconds(value: Long) = "$value с"

    override fun minutes(value: Long) = "$value мин"

    override fun hours(value: Long) = "$value ч"

    override fun hoursMinutes(hours: Long, minutes: Long) = "$hours ч $minutes мин"

    override val admissionYes = "учтено в обычном фоне"

    override fun admissionPartial(excluded: String, reason: String) =
        "в обычный фон: не всё ($excluded — $reason)"

    override val admissionNoData = "измерений нет"

    override fun admissionNo(reason: String) = "не учтено в обычном фоне: $reason"

    override fun doseGlance(today: String, week: String, month: String) =
        "$today сегодня · $week за 7 д · $month за 30 д"
    override fun measuredFor(duration: String) = "измерено $duration"
    override fun recordedOfPeriod(duration: String) = "записано $duration из выбранного периода"
    override val days90 = "90 д"

    override fun mergedPieces(pieces: Int, gap: String) = "с перерывами · $gap без записи"


    override val infoTitle = "Как это посчитано"

    override fun doseProjection(doseWithUnit: String) =
        "если средняя измеренная внешняя фотонная мощность дозы останется такой же — " +
            "за год ≈ $doseWithUnit"

    override fun doseProjectionBasis(rateWithUnit: String, measured: String) =
        "по $measured измерений · средняя $rateWithUnit"

    override val doseProjectionCaveatShort =
        "Это проекция внешнего гамма-фона по данным прибора, а не годовая " +
            "эффективная доза человека."

    override val doseProjectionCaveat =
        "Это не годовая эффективная доза человека: в неё не входят внутреннее " +
            "облучение, радон, медицинские процедуры и всё время, когда прибор " +
            "не измерял или не был рядом."

    override fun doseProjectionUnavailable(measured: String) =
        "измерений пока мало ($measured) — за год пересчитывать не из чего"

    override val delete = "Удалить"

    override fun deleteCount(count: Int) = "Удалить · $count"

    override val deleteSelectedTitle = "Удалить выбранное?"

    override fun deleteSpectraTitle(count: Int) = "Удалить ${spectra(count)}?"

    override fun deleteSessionsTitle(count: Int) =
        "Удалить ${count} ${plural(count, "сессию", "сессии", "сессий")}?"

    override fun sessionsWithDuration(sessions: String, duration: String) =
        "$sessions · $duration измерений"

    override fun samplesGone(count: String) = "$count записей прибора будут удалены навсегда"

    override fun eventsInside(events: String) = "$events отклонения внутри этих периодов"

    override fun spectraFromList(spectra: String) = "$spectra из списка"

    override val cannotUndo = "Отменить удаление нельзя. "

    override val tracksAndSpectraStay =
        "Записанные маршруты на Карте и сохранённые спектры остаются — их удаляют " +
            "отдельно. Обычный фон профиля пересчитается без удалённых измерений."

    override val measurementsUntouched = "Измерения и маршруты не затрагиваются."

    override val markWhatToDelete = "Отметьте, что удалить"

    override fun sessions(n: Int) = "$n ${plural(n, "сессия", "сессии", "сессий")}"

    override fun spectra(n: Int) = "$n ${plural(n, "спектр", "спектра", "спектров")}"

    override fun events(n: Int) = "$n ${plural(n, "событие", "события", "событий")}"

    /** Русские числительные: 1 сессия · 2 сессии · 11 сессий. */
    private fun plural(n: Int, one: String, few: String, many: String): String {
        if (n % 100 in 11..14) return many
        return when (n % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }
}

object HistoryEn : HistoryStrings {
    override val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    override fun seconds(value: Long) = "$value s"

    override fun minutes(value: Long) = "$value min"

    override fun hours(value: Long) = "$value h"

    override fun hoursMinutes(hours: Long, minutes: Long) = "$hours h $minutes min"

    override val admissionYes = "counted in the usual background"

    override fun admissionPartial(excluded: String, reason: String) =
        "not all of it counted in the usual background ($excluded — $reason)"

    override val admissionNoData = "no measurements"

    override fun admissionNo(reason: String) = "not counted in the usual background: $reason"

    override fun doseGlance(today: String, week: String, month: String) =
        "$today today · $week over 7 d · $month over 30 d"
    override fun measuredFor(duration: String) = "measured for $duration"
    override fun recordedOfPeriod(duration: String) = "$duration recorded within the period"
    override val days90 = "90 d"

    override fun mergedPieces(pieces: Int, gap: String) = "with breaks · $gap not recorded"


    override val infoTitle = "How this is counted"

    override fun doseProjection(doseWithUnit: String) =
        "if the average measured external photon dose rate stays the same — " +
            "over a year ≈ $doseWithUnit"

    override fun doseProjectionBasis(rateWithUnit: String, measured: String) =
        "from $measured of measurements · average $rateWithUnit"

    override val doseProjectionCaveatShort =
        "This projects the external gamma background measured by the instrument; " +
            "it is not a person's annual effective dose."

    override val doseProjectionCaveat =
        "This is not a person's annual effective dose: it excludes internal exposure, " +
            "radon, medical procedures and all the time the instrument was not measuring " +
            "or not nearby."

    override fun doseProjectionUnavailable(measured: String) =
        "too few measurements so far ($measured) — there is nothing to scale to a year"

    override val delete = "Delete"

    override fun deleteCount(count: Int) = "Delete · $count"

    override val deleteSelectedTitle = "Delete the selected items?"

    override fun deleteSpectraTitle(count: Int) = "Delete ${spectra(count)}?"

    override fun deleteSessionsTitle(count: Int) = "Delete ${sessions(count)}?"

    override fun sessionsWithDuration(sessions: String, duration: String) =
        "$sessions · $duration of measurements"

    override fun samplesGone(count: String) =
        "$count instrument records will be deleted permanently"

    override fun eventsInside(events: String) = "$events inside those periods"

    override fun spectraFromList(spectra: String) = "$spectra from the list"

    override val cannotUndo = "Deletion cannot be undone. "

    override val tracksAndSpectraStay =
        "Recorded tracks on the Map and saved spectra stay — they are deleted separately. " +
            "The profile's usual background will be recomputed without the deleted measurements."

    override val measurementsUntouched = "Measurements and tracks are left untouched."

    override val markWhatToDelete = "Tick what to delete"

    override fun sessions(n: Int) = if (n == 1) "$n session" else "$n sessions"

    override fun spectra(n: Int) = if (n == 1) "$n spectrum" else "$n spectra"

    override fun events(n: Int) = if (n == 1) "$n deviation event" else "$n deviation events"
}

val HistoryCatalogue = AreaCatalogue(ru = HistoryRu, en = HistoryEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun HistoryStrings.allTexts(): List<String> = months + listOf(
    seconds(45), minutes(12), hours(8), hoursMinutes(8, 12),
    // Причина подставляется каталогом Монитора — здесь стоит её образец на
    // языке человека, а не имя механизма движка.
    admissionYes, admissionPartial(minutes(12), MonitorRu.exclusionQuarantine), admissionNoData,
    admissionNo(MonitorRu.exclusionQuarantine),
    doseGlance("2,36", "2,36", "2,36"), measuredFor("15 ч 33 мин"),
    recordedOfPeriod("15 ч 33 мин"), days90, mergedPieces(3, "12 мин"), infoTitle,
    doseProjection("1,4 мЗв"), doseProjectionBasis("0,155", hours(23)),
    doseProjectionCaveatShort, doseProjectionCaveat, doseProjectionUnavailable(minutes(12)),
    delete, deleteCount(3), deleteSelectedTitle, deleteSpectraTitle(2), deleteSessionsTitle(3),
    sessionsWithDuration(sessions(3), hours(8)), samplesGone("41 203"), eventsInside(events(2)),
    spectraFromList(spectra(2)), cannotUndo, tracksAndSpectraStay, measurementsUntouched,
    markWhatToDelete,
    sessions(1), sessions(3), sessions(11), spectra(1), spectra(2), events(1), events(5),
)
