package app.radiacode.ui.text

/**
 * Строки спектрограммы (экран `SpectrogramScreen`).
 *
 * Область объясняет КАРТИНКУ, поэтому её оговорки — часть измерения, а не
 * вежливость:
 *
 * - «Интенсивность» и «Форма» — два физически разных вопроса к одним данным.
 *   Первый режим красит имп/с на полосу по ОДНОЙ шкале всего окна, поэтому
 *   столбцы сравнимы; второй нормирует каждый столбец отдельно и показывает
 *   состав, но НЕ интенсивность. Английский обязан сохранить этот отказ:
 *   «shape» не имеет права звучать как «сколько».
 * - Пустая колонка — «измерений не было», а не «нулевая интенсивность»:
 *   молчание прибора и посчитанный ноль это разные факты, и пропуски не
 *   заполняются.
 * - Шаг колонки и объединение полос — параметры отображения, названные вместе
 *   с числом, на котором они стоят: иначе укрупнение читается как потеря
 *   разрешения без причины, а случайные светлые строчки — как спектральные
 *   линии.
 * - История спектрограммы живёт только в памяти приложения; обещать
 *   сохранение нельзя ни на одном языке.
 */
interface SpectrogramStrings {

    val title: String

    /** Полная формулировка паузы — в справке: показ стоит, запись идёт. */
    val paused: String

    /** Подпись самого чипа паузы: два слова рядом со значком. */
    val pausedTag: String

    val noLink: String
    val warmingUp: String

    /** Полная формулировка «нет связи» — в справке. */
    val offlineHistory: String

    /**
     * Тот же факт чипом в шапке: картинка занимает весь экран, и статус связи
     * не имеет права отнимать у неё строку.
     */
    val offlineTag: String

    /**
     * Честный ответ на «почему в фоне так редко»: частоту выбирает человек
     * (ADR 007), а открытый экран Спектра или Спектрограммы всегда даёт 5 с.
     * Последняя фраза — про хранение: история срезов лежит на устройстве и
     * переживает перезапуск, и это ровно то, что теперь можно обещать.
     */
    val backgroundNote: String

    // --- частота записи (ADR 007) ---

    /**
     * Ступени названы СВОИМ ЧИСЛОМ, а прилагательное идёт подписью: от
     * «сбалансированного» режима нельзя узнать, какой интервал получит
     * история, а от «30 с» — можно.
     */
    val rateTitle: String
    val rateDetailed: String
    val rateBalanced: String
    val rateEconomy: String

    /** Правило одно и названо вслух: открытый экран = 5 с. */

    /** Объём истории при выбранной частоте — факт, а не пугание батареей. */
    fun rateVolume(megabytesPerDay: String): String

    /** Что происходит со старыми срезами. */
    fun rateThinning(days: Int, minutes: Int): String

    /** Ступени окна: «15м» / «15m». */
    fun windowMinutes(minutes: Int): String

    /** Ступени окна: «2ч» / «2h». */
    fun windowHours(hours: Int): String

    val modeIntensity: String
    val modeShape: String

    /** Подпись полосы под картинкой: величина и её единица. */
    fun doseStripLabel(unit: String): String

    /** Единица оси энергии внутри поля картинки. */
    val energyUnit: String

    val legendZero: String

    /** Верх шкалы в режиме формы: доля внутри столбца, а не количество. */
    val legendColumnMax: String

    /** Верх общей шкалы: «1,2 имп/с» / «1.2 counts/s». */
    fun legendRate(value: String): String

    // --- карточка выбранного момента ---
    fun measuredSeconds(seconds: Long): String
    fun countsPerSecond(value: String): String
    fun countsInColumn(counts: Int): String
    fun meanEnergy(keV: Int): String

    // --- «Как построена картинка» ---
    val infoTitle: String
    val statIntervals: String
    /** Сколько срезов лежит в базе — видимое доказательство, что история цела. */
    val statStored: String
    val statRecorded: String
    val statColumnStep: String
    val statBands: String

    /** Значение шага: «10 с» / «10 s». */
    fun secondsValue(seconds: Long): String

    /** Часть окна без записи: молчание прибора, а не нулевой счёт. */
    fun coverageNote(recorded: String, window: String): String

    /** Шаг колонки: правило и число, на котором оно стоит. */
    fun stepNote(reason: String): String
    val stepCollecting: String
    fun stepPerBand(perBand: String): String
    fun stepPerBandInstead(perBand: String, atPoll: String): String

    fun bandsMerged(perGroup: Int, bandCount: Int, minCounts: Int): String

    val intensityNote: String
    val shapeNote: String
    fun energyRangeNote(minKeV: Int, maxKeV: Int): String
}

object SpectrogramRu : SpectrogramStrings {

    override val title = "Спектрограмма"
    override val paused = "показ остановлен · запись продолжается"
    override val pausedTag = "пауза"
    override val noLink = "нет соединения с прибором"
    override val warmingUp = "накапливаем первые интервалы… столбцы появятся через ~10 с"
    override val offlineHistory = "нет соединения — показана записанная история"
    override val offlineTag = "нет связи · история"
    override val backgroundNote =
        "Частоту записи выбираете вы — ниже. Пока открыт экран Спектра или " +
            "Спектрограммы, запись идёт раз в 5 с. История срезов хранится на " +
            "устройстве и переживает перезапуск приложения."

    override val rateTitle = "Спектрограмма в фоне"
    override val rateDetailed = "5 с · подробно"
    override val rateBalanced = "30 с · обычно"
    override val rateEconomy = "10 мин · экономно"


    override fun rateVolume(megabytesPerDay: String) =
        "При закрытом экране это ≈$megabytesPerDay МБ истории в сутки."

    override fun rateThinning(days: Int, minutes: Int) =
        "Срезы старше $days сут сливаются по $minutes мин: складываются импульсы и " +
            "измеренное время, скорость считается после деления. Через пропуск записи " +
            "срезы не сливаются."

    override fun windowMinutes(minutes: Int) = "${minutes}м"
    override fun windowHours(hours: Int) = "${hours}ч"

    override val modeIntensity = "Интенсивность"
    override val modeShape = "Форма"

    override fun doseStripLabel(unit: String) = "мощность дозы, $unit"
    override val energyUnit = "кэВ"

    override val legendZero = "0"
    override val legendColumnMax = "макс. столбца"
    override fun legendRate(value: String) = "$value имп/с"

    override fun measuredSeconds(seconds: Long) = "измерено $seconds с"
    override fun countsPerSecond(value: String) = "$value с⁻¹"
    override fun countsInColumn(counts: Int) = "$counts имп в колонке"
    override fun meanEnergy(keV: Int) = "ср. энергия $keV кэВ"

    override val infoTitle = "Как построена картинка"
    override val statIntervals = "интервалов"
    override val statStored = "в базе"
    override val statRecorded = "записи"
    override val statColumnStep = "шаг колонки"
    override val statBands = "полос"

    override fun secondsValue(seconds: Long) = "$seconds с"

    override fun coverageNote(recorded: String, window: String) =
        "записи $recorded из $window окна — остальное время прибор не писал, " +
            "такие колонки пустые"

    override fun stepNote(reason: String) =
        "Шаг колонки подобран по статистике: $reason. Колонка складывает опросы, " +
            "попавшие в её интервал, — импульсы суммируются, ничего не додумывается. " +
            "Мельче самого длинного среза в окне шаг не бывает."

    override val stepCollecting = "накапливаем статистику"
    override fun stepPerBand(perBand: String) = "≈$perBand имп на полосу в колонке"
    override fun stepPerBandInstead(perBand: String, atPoll: String) =
        "≈$perBand имп на полосу вместо $atPoll при шаге опроса"

    override fun bandsMerged(perGroup: Int, bandCount: Int, minCounts: Int) =
        "Энергетические полосы объединены по $perGroup: при $bandCount полосах на них " +
            "приходилось меньше $minCounts импульсов, и случайные светлые строчки " +
            "читались бы как спектральные линии. Исходные каналы не меняются."

    override val intensityNote =
        "Интенсивность. Цвет соответствует скорости регистрации событий в " +
            "энергетической полосе, имп/с. Для всего отображаемого окна — единая " +
            "логарифмическая шкала, поэтому столбцы сравнимы между собой."

    override val shapeNote =
        "Форма. Каждый временной спектр нормируется независимо. Режим " +
            "предназначен для сравнения энергетического распределения и не " +
            "показывает абсолютную интенсивность."

    override fun energyRangeNote(minKeV: Int, maxKeV: Int) =
        "Диапазон энергий $minKeV–$maxKeV кэВ, шкала полос геометрическая. " +
            "Пустая колонка — измерений в этой ячейке не было; пропуски не заполняются."
}

object SpectrogramEn : SpectrogramStrings {

    override val title = "Spectrogram"
    override val paused = "display paused · recording continues"
    override val pausedTag = "paused"
    override val noLink = "no link to the instrument"
    override val warmingUp = "collecting the first intervals… columns appear in ~10 s"
    override val offlineHistory = "no link — showing the recorded history"
    override val offlineTag = "no link · history"
    // «переживает перезапуск» — теперь это обещание можно давать: срезы лежат в
    // базе. «Kept on the device», а не «cached» — кэш можно потерять молча.
    override val backgroundNote =
        "You choose the recording rate below. While the Spectrum or Spectrogram screen " +
            "is open, recording runs every 5 s. The slice history is kept on the device " +
            "and survives an app restart."

    override val rateTitle = "Spectrogram in the background"
    override val rateDetailed = "5 s · detailed"
    // «usual», а не «normal»: второе на этом экране читалось бы как «норма»,
    // то есть как оценка уровня, а не как частота записи.
    override val rateBalanced = "30 s · usual"
    override val rateEconomy = "10 min · frugal"


    override fun rateVolume(megabytesPerDay: String) =
        "With the screen closed that is ≈$megabytesPerDay MB of history per day."

    // «Через пропуск не сливаются» — тот же отказ, что и в остальном экране:
    // пропуск остаётся пропуском, «merged across a gap» не бывает.
    override fun rateThinning(days: Int, minutes: Int) =
        "Slices older than $days days are merged into $minutes-minute ones: counts and " +
            "measured time are added up and the rate is computed after the division. " +
            "Slices are never merged across a gap in the recording."

    override fun windowMinutes(minutes: Int) = "${minutes}m"
    override fun windowHours(hours: Int) = "${hours}h"

    override val modeIntensity = "Intensity"
    override val modeShape = "Shape"

    override fun doseStripLabel(unit: String) = "dose rate, $unit"
    override val energyUnit = "keV"

    override val legendZero = "0"
    // Верх шкалы формы — максимум ВНУТРИ столбца, а не «больше излучения»:
    // «column max» удерживает это, «high» уже утверждало бы количество.
    override val legendColumnMax = "column max"
    override fun legendRate(value: String) = "$value counts/s"

    override fun measuredSeconds(seconds: Long) = "measured $seconds s"
    override fun countsPerSecond(value: String) = "$value s⁻¹"
    override fun countsInColumn(counts: Int) = "$counts counts in the column"
    override fun meanEnergy(keV: Int) = "mean energy $keV keV"

    override val infoTitle = "How this image is built"
    override val statIntervals = "intervals"
    override val statStored = "stored"
    override val statRecorded = "recorded"
    override val statColumnStep = "column step"
    override val statBands = "bands"

    override fun secondsValue(seconds: Long) = "$seconds s"

    // «прибор не писал» — молчание прибора, а не измеренный ноль: пустые
    // колонки остаются пустыми, и фраза не имеет права звучать как «zero».
    override fun coverageNote(recorded: String, window: String) =
        "$recorded recorded out of the $window window — the rest of the time the " +
            "instrument was not writing, and such columns stay empty"

    // «ничего не додумывается» — счёт только складывается: «nothing is made up»,
    // никакой интерполяции и сглаживания.
    override fun stepNote(reason: String) =
        "The column step is chosen from the statistics: $reason. A column adds together " +
            "the polls that fall inside it — counts are summed, nothing is made up. The " +
            "step is never finer than the longest slice in the window."

    override val stepCollecting = "collecting the statistics"
    override fun stepPerBand(perBand: String) = "≈$perBand counts per band in a column"
    override fun stepPerBandInstead(perBand: String, atPoll: String) =
        "≈$perBand counts per band instead of $atPoll at the poll step"

    override fun bandsMerged(perGroup: Int, bandCount: Int, minCounts: Int) =
        "Energy bands are merged $perGroup at a time: with $bandCount bands each of " +
            "them held fewer than $minCounts counts, and random bright rows would read " +
            "as spectral lines. The source channels are left unchanged."

    // «единая шкала окна» — то, что делает столбцы сравнимыми; без этой
    // оговорки цвет читался бы как абсолютная величина в любом режиме.
    override val intensityNote =
        "Intensity. Colour follows the rate of events registered in an energy band, " +
            "counts/s. One logarithmic scale covers the whole displayed window, so the " +
            "columns are comparable with each other."

    // Режим показывает СОСТАВ и не показывает интенсивность — отказ, который
    // обязан остаться отказом: «brightness = composition», не «= how much».
    override val shapeNote =
        "Shape. Every time spectrum is scaled independently. The mode is meant for " +
            "comparing the energy distribution and does not show absolute intensity."

    // «измерений не было» ≠ «ноль импульсов»: пропуск остаётся пропуском.
    override fun energyRangeNote(minKeV: Int, maxKeV: Int) =
        "Energy range $minKeV–$maxKeV keV, the band scale is geometric. An empty " +
            "column means there were no measurements in that cell; gaps are not filled in."
}

val SpectrogramCatalogue = AreaCatalogue(ru = SpectrogramRu, en = SpectrogramEn)

/**
 * Все тексты области — для проверок, действующих на каждую строку. Список
 * ведётся руками: рефлексии в тестовом classpath нет, а забытая строка
 * означала бы непроверенный текст.
 */
fun SpectrogramStrings.allTexts(): List<String> = listOf(
    title, paused, pausedTag, noLink, warmingUp, offlineHistory, offlineTag, backgroundNote,
    windowMinutes(15), windowHours(2),
    modeIntensity, modeShape, doseStripLabel("µSv/h"), energyUnit,
    legendZero, legendColumnMax, legendRate("1,2"),
    measuredSeconds(5), countsPerSecond("24,3"), countsInColumn(120), meanEnergy(310),
    rateTitle, rateDetailed, rateBalanced, rateEconomy, rateVolume("1,3"), rateThinning(7, 5),
    infoTitle, statIntervals, statStored, statRecorded, statColumnStep, statBands,
    secondsValue(10), coverageNote("02:30", "05:00"),
    stepNote(stepPerBand("1,4")), stepCollecting,
    stepPerBand("1,4"), stepPerBandInstead("1,4", "0,3"),
    bandsMerged(4, 64, 12),
    intensityNote, shapeNote, energyRangeNote(20, 3000),
)
