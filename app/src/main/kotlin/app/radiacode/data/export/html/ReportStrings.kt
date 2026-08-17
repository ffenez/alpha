package app.radiacode.data.export.html

/**
 * Слова отчётов.
 *
 * Отчёт читают там, где приложения нет: в браузере, в переписке, на бумаге.
 * Поэтому он объясняет сам себя и держится тех же правил, что экраны: не
 * называет измерение нормой или опасностью, не говорит «обнаружен», а
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
    val detailsSection: String
    val notesSection: String
    val energy: String
    val area: String
    val significance: String
    val candidate: String

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
    override val detailsSection = "Детали"
    override val notesSection = "Примечания"
    override val energy = "Энергия, кэВ"
    override val area = "Площадь, импульсы"
    override val significance = "Значимость"
    override val candidate = "Возможное совпадение"

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
    override val detailsSection = "Details"
    override val notesSection = "Notes"
    override val energy = "Energy, keV"
    override val area = "Area, counts"
    override val significance = "Significance"
    override val candidate = "Possible match"

    override fun peakReadout(energyKeV: Double, counts: Double): String =
        "${number(energyKeV, 1)} keV · ${number(counts, 0)} counts"

    override fun keV(value: Double): String = number(value, 0)

    override fun madeBy(appName: String, version: String, dateTime: String): String =
        "Made by $appName $version · $dateTime"
}

/** Число с запятой в дробной части: отчёт читают там же, где и приложение. */
private fun number(value: Double, decimals: Int): String =
    String.format(java.util.Locale.US, "%.${decimals}f", value).replace('.', ',')
