package app.alpha.ui.text

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

    /** Куда ведёт строка события журнала: «на карте ›» / «на графике ›». */
    val openOnMap: String
    val openOnChart: String

    /**
     * Эпизод журнала: название вида, интервал, пределы и порог.
     *
     * «Отклонение» на всё сразу не годилось: разница с обычным, подтверждённое
     * изменение и достижение назначенного порога — разные утверждения
     * (`history_semantic_events_redesign.md`).
     */
    val filterEvents: String

    /** Пустое состояние вкладки событий. */
    val noEventsYet: String
    val eventsExplained: String
    val levelChangeTitle: String
    val thresholdTitle: String
    fun episodeSpan(from: String, to: String, duration: String): String
    fun episodeOngoing(from: String, duration: String): String
    fun episodeRange(low: String, high: String): String
    fun episodeUsually(rate: String): String
    val episodeThresholdLabel: String
    fun episodeRatio(times: String): String

    /** Сокращения месяцев, 12 штук, январь первым. */
    val months: List<String>

    /** Месяцы в родительном падеже: «14 августа» — заголовок дня в списке. */
    val monthsGenitive: List<String>
    val today: String
    val yesterday: String

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
    val days90: String

    /** Экран накопленной дозы: подпись периода, покрытие и разбор дня. */
    fun forDays(days: Int): String
    fun measuredWithCoverage(duration: String, percent: String): String
    fun dayDose(date: String, dose: String, duration: String): String
    val dayWithoutData: String
    val averageFullDay: String
    val maxDay: String
    val tapDayHint: String

    /** Заголовок раскрываемой годовой оценки — условие, а не термин. */

    /**
     * Склеенная запись: сколько кусков и сколько перерыва внутри.
     *
     * Склейка не имеет права быть незаметной: человек должен видеть, что
     * измерение шло с перерывом, а не гадать, почему числа не сходятся с
     * длительностью.
     */
    fun mergedPieces(pieces: Int, gap: String): String

    /** «начата 14 авг 01:29» — у идущей записи важно, когда она началась. */
    fun startedAt(moment: String): String

    /** «данных 15 ч 56 мин» — сколько времени прибор действительно писал. */
    fun dataFor(duration: String): String

    /** «пропуски 9 мин» — и сколько времени в записи его нет. */
    fun gapsFor(duration: String): String


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

    // ------------------------------------------------------------- маршруты
    /** Фильтры журнала: что он показывает. */
    /** Заголовок строки снимка спектра, которому не дали имени. */
    val spectrumTitle: String
    val filterAll: String
    val filterSessions: String
    val filterRoutes: String
    val filterSpectra: String
    val filterFood: String
    val noFoodYet: String
    val foodExplained: String

    /** Подпись маршрута без имени: «Маршрут · 18:51». */
    fun routeAuto(time: String): String
    val routesTitle: String
    val noRoutesYet: String
    val routesExplained: String
    /** Маршрут ещё пишется — числа по нему ещё меняются. */
    val routeRecording: String
    fun routeMeasurements(count: String): String
    val routeRename: String
    val routeNameHint: String
    val routeCompare: String
    fun routeCompareCount(count: Int): String
    val routeCompareTitle: String
    val routeCompareNeedTwo: String
    /**
     * Сравнение честно называет свою границу: одинаковая шкала делает цвета
     * сопоставимыми, но не превращает разницу чисел в вывод о различии.
     */
    val routeCompareCaveat: String
    val routeOpen: String
    /** Разница по участкам — только для пары маршрутов. */
    val routeInterrupted: String
    val routeExport: String
    val routeUndo: String
    fun routesDeleted(count: Int): String
    fun routeDeleteTitle(count: Int): String
    val routeDeleteBody: String

    /** Снимок спектра: удаляется он сам, накопление прибора не трогается. */
    val spectrumDeleteTitle: String
    val spectrumDeleteBody: String

    /** Сессия: исчезают её измерения, снимки и маршруты остаются. */
    val sessionDeleteTitle: String
    val sessionDeleteBody: String
    val routeDiff: String

    /** Итог сравнения: сколько мест сопоставлено. */
    fun routeComparePlaces(matched: Int): String

    /** Сколько участков отличается — главное число экрана. */
    fun routeCompareDiffering(count: Int): String

    /** «19 — выше у маршрута 1»: направление названо вместе с маршрутом. */
    fun routeCompareHigherOn(count: Int, route: Int): String

    /** Остаток: сопоставлено минус отличающиеся. */
    fun routeCompareSame(count: Int): String

    /** Ограничение, которое нельзя прятать в справку. */
    val routeCompareDescriptive: String

    /** Номер маршрута в списке — им подписаны строки и направления. */
    fun routeNumber(index: Int): String

    /** Показан ли маршрут на карте. */
    val routeOnMap: String

    // --- «Пояснение»: методика, а не результат ---
    val routeMethodTitle: String
    val routeMethodPatchTitle: String
    fun routeMethodPatch(cell: String, minPoints: Int): String
    val routeMethodTypicalTitle: String
    val routeMethodTypical: String
    val routeMethodDifferenceTitle: String
    val routeMethodDifference: String

    /** То же простыми словами — для включённых подсказок. */
    val routeMethodDifferenceSimple: String
    val routeMethodLimitTitle: String
    val routeMethodLimit: String
    val routeMethodColourTitle: String
    val statDistance: String
    val statDose: String
    val measurementsUntouched: String
    val markWhatToDelete: String

    fun sessions(n: Int): String
    fun spectra(n: Int): String
    fun events(n: Int): String

    /**
     * Счётчик той вкладки, которая открыта.
     *
     * «37 сессий» на вкладке «Все» считало не то, что лежало на экране: там
     * же стояли маршруты, снимки и исследования. Счётчик обязан говорить о
     * видимом.
     */
    /** Запись без единого измерения — коротко, в рабочем списке. */
    val noMeasurements: String

    /** Удаление исследования: названо по имени и по последствию. */
    fun studyDeleteTitle(name: String): String
    val studyDeleteBody: String

    fun records(n: Int): String
    fun routes(n: Int): String
    fun studies(n: Int): String
}

object HistoryRu : HistoryStrings {

    override val openOnMap = "на карте ›"
    override val openOnChart = "на графике ›"
    override val filterEvents = "События"
    override val noEventsYet = "Событий пока нет"
    override val eventsExplained = "Сюда попадает подтверждённое изменение уровня и " +
        "достижение назначенного порога — один эпизод одной записью. Обычные колебания фона " +
        "событием не становятся: они видны на графике."
    override val levelChangeTitle = "Изменение уровня"
    override val thresholdTitle = "Превышение порога"
    override fun episodeSpan(from: String, to: String, duration: String) =
        "$from–$to · $duration"
    override fun episodeOngoing(from: String, duration: String) = "с $from · $duration · идёт"
    override fun episodeRange(low: String, high: String) = "$low–$high"
    override fun episodeUsually(rate: String) = "обычно $rate"
    override val episodeThresholdLabel = "порог"
    override fun episodeRatio(times: String) = "×$times к обычному"
    override val months = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    override val monthsGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    override val today = "Сегодня"
    override val yesterday = "Вчера"

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
    override val days90 = "90 дней"
    override fun forDays(days: Int) = "за $days ${plural(days, "день", "дня", "дней")}"
    override fun measuredWithCoverage(duration: String, percent: String) =
        "измерено $duration · $percent % периода"
    override fun dayDose(date: String, dose: String, duration: String) =
        "$date · $dose · записано $duration"
    override val dayWithoutData = "измерений в этот день не было"
    override val averageFullDay = "в среднем за полные сутки"
    override val maxDay = "больше всего за сутки"
    override val tapDayHint = "Нажмите на столбец, чтобы увидеть день."

    override fun mergedPieces(pieces: Int, gap: String) = "с перерывами · $gap без записи"
    override fun startedAt(moment: String) = "начата $moment"
    override fun dataFor(duration: String) = "данных $duration"
    override fun gapsFor(duration: String) = "пропуски $duration"


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

    override val spectrumTitle = "Спектр"
    override val filterAll = "Все"
    override val filterSessions = "Сессии"
    override val filterRoutes = "Маршруты"
    override val filterSpectra = "Спектры"
    override val filterFood = "Продукты"
    override val noFoodYet = "Измерений продуктов пока нет"
    override val foodExplained =
        "Измерение продукта — на экране Спектра: фон и образец в одной " +
            "геометрии, а здесь остаётся запись с результатом."

    override fun routeAuto(time: String) = "Маршрут · $time"
    override val routesTitle = "Маршруты"
    override val noRoutesYet = "Маршрутов пока нет"
    override val routesExplained =
        "Маршрут записывается на Карте: пока запись идёт, точки с координатами " +
            "и показаниями сохраняются сами и остаются здесь после неё."
    override val routeRecording = "идёт запись"
    override fun routeMeasurements(count: String) = "$count измерений"
    override val routeRename = "Переименовать"
    override val routeNameHint = "Название маршрута"
    override val routeCompare = "Сравнить"
    override fun routeCompareCount(count: Int) = "Сравнить ($count)"
    override val routeCompareTitle = "Сравнение маршрутов"
    override val routeCompareNeedTwo = "выберите хотя бы два маршрута"
    override val routeCompareCaveat =
        "Шкала цвета у всех маршрутов одна, поэтому цвета сравнимы. Разница " +
            "чисел сама по себе различием не является: маршруты проходят по " +
            "разной геометрии и в разное время."
    override val routeOpen = "Открыть"
    override val routeInterrupted = "прервана"
    override val routeExport = "Экспорт GPX"
    override val routeUndo = "Отменить"
    override fun routesDeleted(count: Int) =
        if (count == 1) "Маршрут удалён" else "Удалено маршрутов: $count"
    override fun routeDeleteTitle(count: Int) =
        if (count == 1) "Удалить маршрут?" else "Удалить маршруты: $count?"
    override val routeDeleteBody =
        "Точки маршрута и его метки исчезнут с карты и из накопленных записей. " +
            "Измерения прибора за это время останутся."
    override val spectrumDeleteTitle = "Удалить снимок спектра?"
    override val spectrumDeleteBody =
        "Исчезнет сохранённый снимок: каналы, калибровка и время накопления. " +
            "Накопление в самом приборе и другие записи не меняются."
    override val sessionDeleteTitle = "Удалить сессию?"
    override val sessionDeleteBody =
        "Исчезнут измерения этой сессии и посчитанная по ним статистика. " +
            "Снимки спектра и маршруты остаются в журнале."
    override val routeDiff = "Разница"
    override fun routeComparePlaces(matched: Int) =
        "Сравнено $matched ${plural(matched, "место", "места", "мест")}"
    override fun routeCompareDiffering(count: Int) =
        "$count ${plural(count, "участок", "участка", "участков")} отличается"
    override fun routeCompareHigherOn(count: Int, route: Int) =
        "$count — значения выше у маршрута $route"
    override fun routeCompareSame(count: Int) =
        "$count — без заметной разницы"
    override val routeCompareDescriptive =
        "описательное сравнение, не проверка значимости"
    override fun routeNumber(index: Int) = "маршрут $index"
    override val routeOnMap = "на карте"
    override val routeMethodTitle = "Как сравниваются маршруты"
    override val routeMethodPatchTitle = "Участок"
    override fun routeMethodPatch(cell: String, minPoints: Int) =
        "Карта делится на клетки $cell. Клетка участвует в сравнении, только если в " +
            "обоих маршрутах есть не меньше $minPoints измерений."
    override val routeMethodTypicalTitle = "Типичное значение"
    override val routeMethodTypical =
        "Для каждого участка сравниваются медианы измерений."
    override val routeMethodDifferenceTitle = "Когда показывается различие"
    override val routeMethodDifference =
        "Различие отмечается, когда диапазоны P10–P90 двух маршрутов не перекрываются."
    override val routeMethodDifferenceSimple =
        "P10–P90 — полоса, в которую попали восемь измерений участка из десяти: " +
            "самые низкие и самые высокие отброшены. Полосы двух маршрутов разошлись — " +
            "разница видна и без статистики."
    override val routeMethodLimitTitle = "Ограничение метода"
    override val routeMethodLimit =
        "Измерения вдоль маршрута идут подряд и зависят друг от друга, поэтому это " +
            "описательное сравнение, а не статистическая проверка значимости."
    override val routeMethodColourTitle = "Цвет на карте"
    override val statDistance = "путь"
    override val statDose = "доза"


    override val measurementsUntouched = "Измерения и маршруты не затрагиваются."

    override val markWhatToDelete = "Отметьте, что удалить"

    override fun sessions(n: Int) = "$n ${plural(n, "сессия", "сессии", "сессий")}"

    override fun spectra(n: Int) = "$n ${plural(n, "спектр", "спектра", "спектров")}"

    override fun events(n: Int) = "$n ${plural(n, "событие", "события", "событий")}"

    override val noMeasurements = "нет измерений"

    override fun studyDeleteTitle(name: String) = "Удалить «$name»?"
    override val studyDeleteBody =
        "Уйдут прогоны и результат этого исследования. Измерения журнала останутся."

    override fun records(n: Int) = "$n ${plural(n, "запись", "записи", "записей")}"

    override fun routes(n: Int) = "$n ${plural(n, "маршрут", "маршрута", "маршрутов")}"

    override fun studies(n: Int) =
        "$n ${plural(n, "исследование", "исследования", "исследований")}"

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

    override val openOnMap = "on the map ›"
    override val openOnChart = "on the chart ›"
    override val filterEvents = "Events"
    override val noEventsYet = "No events yet"
    override val eventsExplained = "A confirmed level change and a reached threshold land " +
        "here — one episode as one record. Ordinary background fluctuation does not become an " +
        "event: it is visible on the chart."
    override val levelChangeTitle = "Level change"
    override val thresholdTitle = "Threshold reached"
    override fun episodeSpan(from: String, to: String, duration: String) =
        "$from–$to · $duration"
    override fun episodeOngoing(from: String, duration: String) =
        "since $from · $duration · ongoing"
    override fun episodeRange(low: String, high: String) = "$low–$high"
    override fun episodeUsually(rate: String) = "usually $rate"
    override val episodeThresholdLabel = "threshold"
    override fun episodeRatio(times: String) = "×$times of usual"
    override val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    override val monthsGenitive = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    override val today = "Today"
    override val yesterday = "Yesterday"

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
    override val days90 = "90 days"
    override fun forDays(days: Int) = "over $days days"
    override fun measuredWithCoverage(duration: String, percent: String) =
        "measured $duration · $percent % of the period"
    override fun dayDose(date: String, dose: String, duration: String) =
        "$date · $dose · recorded $duration"
    override val dayWithoutData = "nothing was measured on that day"
    override val averageFullDay = "average over full days"
    override val maxDay = "most in a day"
    override val tapDayHint = "Tap a bar to see the day."

    override fun mergedPieces(pieces: Int, gap: String) = "with breaks · $gap not recorded"
    override fun startedAt(moment: String) = "started $moment"
    override fun dataFor(duration: String) = "$duration of data"
    override fun gapsFor(duration: String) = "$duration missing"


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

    override val spectrumTitle = "Spectrum"
    override val filterAll = "All"
    override val filterSessions = "Sessions"
    override val filterRoutes = "Routes"
    override val filterSpectra = "Spectra"
    override val filterFood = "Products"
    override val noFoodYet = "No product measurements yet"
    override val foodExplained =
        "A product is measured on the Spectrum screen: the background and the " +
            "sample in one geometry, and the record stays here."

    override fun routeAuto(time: String) = "Route · $time"
    override val routesTitle = "Routes"
    override val noRoutesYet = "No routes yet"
    override val routesExplained =
        "A route is recorded on the Map: while recording runs, points with " +
            "coordinates and readings are stored by themselves and stay here."
    override val routeRecording = "recording"
    override fun routeMeasurements(count: String) = "$count measurements"
    override val routeRename = "Rename"
    override val routeNameHint = "Route name"
    override val routeCompare = "Compare"
    override fun routeCompareCount(count: Int) = "Compare ($count)"
    override val routeCompareTitle = "Route comparison"
    override val routeCompareNeedTwo = "pick at least two routes"
    override val routeCompareCaveat =
        "All routes share one colour scale, so the colours are comparable. A " +
            "difference in the numbers is not by itself a difference: the " +
            "routes run over different geometry and at different times."
    override val routeOpen = "Open"
    override val routeInterrupted = "interrupted"
    override val routeExport = "Export GPX"
    override val routeUndo = "Undo"
    override fun routesDeleted(count: Int) =
        if (count == 1) "Route deleted" else "Routes deleted: $count"
    override fun routeDeleteTitle(count: Int) =
        if (count == 1) "Delete the route?" else "Delete $count routes?"
    override val routeDeleteBody =
        "The route's points and markers disappear from the map and from the " +
            "accumulated recordings. The instrument's measurements stay."
    override val spectrumDeleteTitle = "Delete the spectrum snapshot?"
    override val spectrumDeleteBody =
        "The saved snapshot goes: channels, calibration and accumulation time. " +
            "The instrument's own accumulation and the other records stay."
    override val sessionDeleteTitle = "Delete the session?"
    override val sessionDeleteBody =
        "The measurements of this session and the statistics computed from them go. " +
            "Spectrum snapshots and routes stay in the log."
    override val routeDiff = "Difference"
    override fun routeComparePlaces(matched: Int) =
        "$matched ${if (matched == 1) "place" else "places"} compared"
    override fun routeCompareDiffering(count: Int) =
        "$count ${if (count == 1) "patch differs" else "patches differ"}"
    override fun routeCompareHigherOn(count: Int, route: Int) =
        "$count — higher on route $route"
    override fun routeCompareSame(count: Int) =
        "$count — no visible difference"
    override val routeCompareDescriptive =
        "a descriptive comparison, not a test of significance"
    override fun routeNumber(index: Int) = "route $index"
    override val routeOnMap = "on the map"
    override val routeMethodTitle = "How the routes are compared"
    override val routeMethodPatchTitle = "Patch"
    override fun routeMethodPatch(cell: String, minPoints: Int) =
        "The map is split into $cell cells. A cell takes part in the comparison only " +
            "if both routes have at least $minPoints measurements in it."
    override val routeMethodTypicalTitle = "Typical value"
    override val routeMethodTypical =
        "For each patch the medians of the measurements are compared."
    override val routeMethodDifferenceTitle = "When a difference is shown"
    override val routeMethodDifference =
        "A difference is marked when the P10–P90 ranges of the two routes do not overlap."
    override val routeMethodDifferenceSimple =
        "P10–P90 is the band that holds eight of the ten measurements of a patch: the " +
            "lowest and the highest are left out. When the bands of the two routes come " +
            "apart, the difference is visible without any statistics."
    override val routeMethodLimitTitle = "Limit of the method"
    override val routeMethodLimit =
        "Measurements along a route are consecutive and depend on each other, so this is " +
            "a descriptive comparison, not a statistical test of significance."
    override val routeMethodColourTitle = "Colour on the map"
    override val statDistance = "distance"
    override val statDose = "dose"


    override val measurementsUntouched = "Measurements and tracks are left untouched."

    override val markWhatToDelete = "Tick what to delete"

    override fun sessions(n: Int) = if (n == 1) "$n session" else "$n sessions"

    override fun spectra(n: Int) = if (n == 1) "$n spectrum" else "$n spectra"

    override fun events(n: Int) = if (n == 1) "$n deviation event" else "$n deviation events"

    override val noMeasurements = "no measurements"

    override fun studyDeleteTitle(name: String) = "Delete «$name»?"
    override val studyDeleteBody =
        "The runs and the result of this study will go. The journal measurements stay."

    override fun records(n: Int) = if (n == 1) "$n record" else "$n records"

    override fun routes(n: Int) = if (n == 1) "$n route" else "$n routes"

    override fun studies(n: Int) = if (n == 1) "$n study" else "$n studies"
}

val HistoryCatalogue = AreaCatalogue(ru = HistoryRu, en = HistoryEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun HistoryStrings.allTexts(): List<String> = months + monthsGenitive + listOf(
    openOnMap, openOnChart,
    filterEvents, noEventsYet, eventsExplained,
    levelChangeTitle, thresholdTitle,
    episodeSpan("14:31", "14:47", "16 мин"), episodeOngoing("14:31", "16 мин"),
    episodeRange("0,14", "0,17"), episodeUsually("0,16 мкЗв/ч"),
    episodeThresholdLabel, episodeRatio("1,4"),
    today, yesterday,
    spectrumTitle,
    routeComparePlaces(91), routeCompareDiffering(34), routeCompareHigherOn(19, 1),
    routeCompareSame(57), routeCompareDescriptive, routeNumber(1), routeOnMap,
    routeMethodTitle, routeMethodPatchTitle, routeMethodPatch("30 м", 5),
    routeMethodTypicalTitle, routeMethodTypical, routeMethodDifferenceTitle,
    routeMethodDifference, routeMethodDifferenceSimple, routeMethodLimitTitle,
    routeMethodLimit, routeMethodColourTitle,
    filterAll, filterSessions, filterRoutes, filterSpectra, filterFood,
    noFoodYet, foodExplained,
    routeAuto("18:51"), routesTitle, noRoutesYet, routesExplained, routeRecording,
    routeMeasurements("4 654"), routeRename, routeNameHint,
    routeCompare, routeCompareCount(2), routeCompareTitle, routeCompareNeedTwo,
    routeCompareCaveat, routeOpen, statDistance, statDose,
    routeInterrupted, routeExport, routeUndo, routesDeleted(1), routesDeleted(3),
    noMeasurements, studyDeleteTitle("Калий"), studyDeleteBody,
    records(1), records(3), records(11), routes(1), routes(2), studies(1), studies(5),
    routeDeleteTitle(1), routeDeleteTitle(3), routeDeleteBody,
    spectrumDeleteTitle, spectrumDeleteBody, sessionDeleteTitle, sessionDeleteBody,
    routeDiff,
    seconds(45), minutes(12), hours(8), hoursMinutes(8, 12),
    // Причина подставляется каталогом Монитора — здесь стоит её образец на
    // языке человека, а не имя механизма движка.
    admissionYes, admissionPartial(minutes(12), MonitorRu.exclusionQuarantine), admissionNoData,
    admissionNo(MonitorRu.exclusionQuarantine),
    doseGlance("2,36", "2,36", "2,36"), days90,
    forDays(30), measuredWithCoverage("49 ч 46 мин", "6,9"),
    dayDose("18 авг", "2,32 мкЗв", "15 ч 47 мин"), dayWithoutData,
    averageFullDay, maxDay, tapDayHint, mergedPieces(3, "12 мин"), infoTitle,
    doseProjection("1,4 мЗв"), doseProjectionBasis("0,155", hours(23)),
    doseProjectionCaveatShort, doseProjectionCaveat, doseProjectionUnavailable(minutes(12)),
    delete, deleteCount(3), deleteSelectedTitle, deleteSpectraTitle(2), deleteSessionsTitle(3),
    sessionsWithDuration(sessions(3), hours(8)), samplesGone("41 203"), eventsInside(events(2)),
    spectraFromList(spectra(2)), cannotUndo, tracksAndSpectraStay, measurementsUntouched,
    markWhatToDelete,
    sessions(1), sessions(3), sessions(11), spectra(1), spectra(2), events(1), events(5),
)
