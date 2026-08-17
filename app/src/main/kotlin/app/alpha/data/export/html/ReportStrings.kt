package app.alpha.data.export.html

/**
 * Слова отчётов.
 *
 * Отчёт читают там, где приложения нет: в браузере, в переписке, на бумаге.
 * Поэтому он объясняет сам себя и держится тех же правил, что экраны: не
 * выносит приговор измерению, не говорит «обнаружен», а
 * совпадение по энергии называет совпадением по энергии.
 */
interface ReportStrings {

    val accumulation: String
    val totalCounts: String
    val channels: String
    val countsUnit: String
    val spectrumSection: String
    val peaksSection: String
    val peaksNote: String
    /** Подписи кнопок под графиком: масштаб и «во весь экран». */
    val chartLabels: HtmlChart.Labels

    val recordsSection: String
    val elapsedAxisNote: String
    val comparisonDisclaimer: String
    val detailsSection: String
    val notesSection: String
    val energy: String
    val area: String
    val significance: String
    val candidate: String
    val eventsSection: String

    /** Разделы отчётов о сессии и маршруте. */
    val doseSection: String
    val countSection: String
    val hardnessSection: String
    val routeSection: String
    val trackSection: String
    val average: String
    val range: String
    val accumulatedDose: String
    val measurements: String
    val distance: String
    val duration: String
    val maximum: String
    val privacyTrimmed: String
    val privacyDropped: String
    val openOnMap: String

    /** Разделы отчёта об опыте. */
    val resultLabel: String
    val geometrySection: String
    val runsSection: String
    val comparisonSection: String
    val runLabel: String
    val quantity: String
    val experimentDisclaimer: String

    /** Строка под графиком при наведении: «662,0 кэВ · 1 240 импульсов». */
    fun peakReadout(energyKeV: Double, counts: Double): String

    fun keV(value: Double): String

    /** Подпись внизу страницы: чем и когда собран отчёт. */
    fun madeBy(appName: String, version: String, dateTime: String): String
}

object ReportRu : ReportStrings {
    override val accumulation = "накопление"
    override val totalCounts = "импульсов"
    override val channels = "каналов"
    override val countsUnit = "импульсы"
    override val spectrumSection = "Спектр"
    override val peaksSection = "Найденные пики"
    override val peaksNote =
        "Совпадение по энергии — гипотеза, а не вывод о веществе: одну и ту же энергию " +
            "дают разные источники, и заключение делается по совокупности линий."
    override val chartLabels = HtmlChart.Labels(
        linear = "Лин",
        logarithmic = "Лог",
        fullScreen = "Во весь экран",
    )

    override val recordsSection = "Записи"
    override val elapsedAxisNote = "По горизонтали — время от начала каждой записи, " +
        "а не календарное: записи сделаны в разное время."
    override val comparisonDisclaimer = "Кривые наложены для наблюдения. Проверку различия " +
        "делает опыт A/B, где записана геометрия."
    override val detailsSection = "Детали"
    override val notesSection = "Примечания"
    override val energy = "Энергия, кэВ"
    override val area = "Площадь, импульсы"
    override val significance = "Значимость"
    override val candidate = "Возможное совпадение"
    override val eventsSection = "События"
    override val doseSection = "Мощность дозы"
    override val countSection = "Скорость счёта"
    override val hardnessSection = "Жёсткость"
    override val routeSection = "Маршрут"
    override val trackSection = "След"
    override val average = "среднее"
    override val range = "диапазон"
    override val accumulatedDose = "накопленная доза"
    override val measurements = "измерений"
    override val distance = "длина"
    override val duration = "длительность"
    override val maximum = "максимум"
    override val privacyTrimmed = "Начало и конец маршрута скрыты."
    override val privacyDropped = "Координаты в отчёт не включены."
    override val openOnMap = "Открыть координаты на карте"
    override val resultLabel = "Результат"
    override val geometrySection = "Геометрия"
    override val runsSection = "Прогоны"
    override val comparisonSection = "Сравнение"
    override val runLabel = "Прогон"
    override val quantity = "Величина"
    override val experimentDisclaimer =
        "Результат говорит о различии измерений в этой геометрии, а не о том, какое " +
            "вещество перед прибором и что оно делает со здоровьем."

    override fun peakReadout(energyKeV: Double, counts: Double): String =
        "${number(energyKeV, 1)} кэВ · ${number(counts, 0)} импульсов"

    override fun keV(value: Double): String = "${number(value, 0)}"

    override fun madeBy(appName: String, version: String, dateTime: String): String =
        "Создано $appName $version · $dateTime"
}

object ReportEn : ReportStrings {
    override val accumulation = "accumulation"
    override val totalCounts = "counts"
    override val channels = "channels"
    override val countsUnit = "counts"
    override val spectrumSection = "Spectrum"
    override val peaksSection = "Peaks found"
    override val peaksNote =
        "A match in energy is a hypothesis, not a detection: different sources share " +
            "energies, and a conclusion follows from the set of lines, not from one."
    override val chartLabels = HtmlChart.Labels(
        linear = "Lin",
        logarithmic = "Log",
        fullScreen = "Full screen",
    )

    override val recordsSection = "Records"
    override val elapsedAxisNote = "The horizontal axis is time from the start of each " +
        "record, not calendar time: the records were made at different times."
    override val comparisonDisclaimer = "The curves are overlaid for observation. Testing a " +
        "difference is what an A/B experiment does, with the geometry written down."
    override val detailsSection = "Details"
    override val notesSection = "Notes"
    override val energy = "Energy, keV"
    override val area = "Area, counts"
    override val significance = "Significance"
    override val candidate = "Possible match"
    override val eventsSection = "Events"
    override val doseSection = "Dose rate"
    override val countSection = "Count rate"
    override val hardnessSection = "Hardness"
    override val routeSection = "Route"
    override val trackSection = "Track"
    override val average = "average"
    override val range = "range"
    override val accumulatedDose = "accumulated dose"
    override val measurements = "measurements"
    override val distance = "length"
    override val duration = "duration"
    override val maximum = "maximum"
    override val privacyTrimmed = "The start and the end of the route are hidden."
    override val privacyDropped = "Coordinates are not included in the report."
    override val openOnMap = "Open the coordinates on a map"
    override val resultLabel = "Result"
    override val geometrySection = "Geometry"
    override val runsSection = "Runs"
    override val comparisonSection = "Comparison"
    override val runLabel = "Run"
    override val quantity = "Quantity"
    override val experimentDisclaimer =
        "The result speaks about a difference between these measurements in this " +
            "geometry, not about what the substance is or what it does to health."

    override fun peakReadout(energyKeV: Double, counts: Double): String =
        "${number(energyKeV, 1)} keV · ${number(counts, 0)} counts"

    override fun keV(value: Double): String = number(value, 0)

    override fun madeBy(appName: String, version: String, dateTime: String): String =
        "Made by $appName $version · $dateTime"
}

/** Число с запятой в дробной части: отчёт читают там же, где и приложение. */
private fun number(value: Double, decimals: Int): String =
    String.format(java.util.Locale.US, "%.${decimals}f", value).replace('.', ',')
