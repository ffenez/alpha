package app.alpha.ui.text

/**
 * Строки спектрограммы (экран `SpectrogramScreen`).
 *
 * Оговорки области — часть измерения:
 *
 * - «Интенсивность» и «Форма» — два разных вопроса к одним данным. Первый
 *   режим красит имп/с на полосу по одной шкале всего окна, поэтому столбцы
 *   сравнимы; второй нормирует каждый столбец отдельно и показывает состав, а
 *   не интенсивность. Английский обязан сохранить этот отказ.
 * - Пустая колонка означает «измерений не было», а не «нулевая
 *   интенсивность»; пропуски не заполняются.
 * - Шаг колонки и объединение полос — параметры отображения, названные вместе
 *   с числом, на котором стоят.
 */
interface SpectrogramStrings {

    /**
     * Где снят срез: координаты и точность. Спектрограмма отвечает «когда»,
     * а маршрут — «где»; вместе они дают «что и где фонило».
     */
    fun placeAt(latitude: String, longitude: String, accuracy: String): String

    /** Действие: открыть карту на этом месте. */
    val showOnMap: String


    /**
     * Прибор без энергетического разрешения: картинка остаётся честной как
     * «интенсивность во времени», но полосы у неё не энергии.
     */
    fun notSpectrometer(device: String): String


    val title: String

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
     * Частота записи в фоне выбирается человеком (ADR 007); открытый экран
     * Спектра или Спектрограммы всегда даёт 5 с. История срезов лежит на
     * устройстве и переживает перезапуск.
     */
    val backgroundNote: String

    /**
     * Чем спектрограмма является и почему сброс накопления её не трогает.
     *
     * Вопрос из поля: «сбросил накопление в Спектре, а тут по-прежнему два
     * часа — почему». Потому что это разные вещи: накопление — сумма, которую
     * держит прибор, а спектрограмма — запись того, что уже прошло через него.
     */
    val recordNote: String

    /** Стереть записанную спектрограмму — по явной команде. */
    val clearHistory: String
    val clearConfirmTitle: String
    val clearConfirmBody: String

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

    /**
     * Единица оси энергии внутри поля — только единица. Выбранная шкала видна
     * по засечкам (50/100/300… против 500/1000/1500…) и названа в «⋮» и в
     * справке.
     */
    val energyUnit: String

    /**
     * Чип шкалы энергии называет ТЕКУЩУЮ шкалу, а не то, чем она станет:
     * то же правило, что у «лог/лин» на графике дозы. Нажатие переключает.
     */
    val axisLog: String
    val axisLinear: String

    /** Чем одна ось отличается от другой — в технических подробностях. */
    val energyScaleLogNote: String
    val energyScaleLinearNote: String

    /** «146 кэВ» — энергия как значение, одинаково в прицеле и в карточке. */
    fun energyValue(keV: Int): String

    val legendZero: String

    /** Заголовок шкалы цвета: величина и единица. */
    val legendIntensityTitle: String

    /** В режиме формы величины нет — и подпись говорит именно это. */
    val legendShapeTitle: String


    /** Верх шкалы в режиме формы: доля внутри столбца, а не количество. */
    val legendColumnMax: String

    /** Верх общей шкалы: «1,2 имп/с» / «1.2 counts/s». */
    fun legendRate(value: String): String

    // --- карточка выбранного момента ---

    /** Реально измеренное время колонки, а не ширина ячейки сетки. */
    fun windowSeconds(seconds: Long): String
    fun countsPerSecond(value: String): String

    /** Подписи под числами карточки: величина, а не её единица. */
    val keyDoseRate: String
    val keyCount: String
    val keyMeanEnergy: String

    // --- техническая строка карточки момента ---

    /** Сумма импульсов колонки: она объясняет три величины над собой. */
    fun countsInColumn(counts: Int): String

    /** Ячейка покрыта не полностью: пропуск записи, а не спад интенсивности. */
    fun partialColumn(measured: Long, step: Long): String

    /**
     * Почему из момента нельзя открыть полный спектр: в истории лежат полосы,
     * а не каналы. Восстанавливать спектр из агрегата приложение не станет.
     */
    val noStoredSpectrum: String

    // --- справка ---

    /** Заголовок первого уровня: как читать картинку. */
    val helpTitle: String

    /** Правила чтения — по одному на строку, без вложенных оговорок. */
    val howToRead: List<String>

    /** Строка раскрытия: всё, что нужно редко. */
    val technicalTitle: String

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

    override fun placeAt(latitude: String, longitude: String, accuracy: String) =
        "$latitude, $longitude · ±$accuracy м"
    override val showOnMap = "На карте"

    override fun notSpectrometer(device: String) =
        "$device не разделяет энергии: полосы этой картинки — не энергии, а деление " +
            "шкалы прибора. Читать её как спектр во времени нельзя."

    override val title = "Спектрограмма"
    override val noLink = "нет соединения с прибором"
    override val warmingUp = "накапливаем первые интервалы… столбцы появятся через ~10 с"
    override val offlineHistory = "нет соединения — показана записанная история"
    override val offlineTag = "нет связи · история"
    override val clearHistory = "Очистить спектрограмму"
    override val clearConfirmTitle = "Стереть записанную спектрограмму?"
    override val clearConfirmBody =
        "Уйдут все записанные срезы — и то, что на экране, и сохранённое в приложении. " +
            "Измерения, снимки спектра и маршруты не затрагиваются."

    override val recordNote =
        "Спектрограмма — запись измеренного: каждая полоса это интервал между опросами. " +
            "Сброс накопления в Спектре обнуляет сумму прибора, а записанное здесь остаётся."

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

    override val energyUnit = "кэВ"
    override val axisLog = "лог"
    override val axisLinear = "лин"
    override val energyScaleLogNote =
        "Ось энергии геометрическая: равная высота — равное отношение энергий, и низ " +
            "спектра получает свою долю строк. Расстояние по вертикали не равно разнице в кэВ."
    override val energyScaleLinearNote =
        "Ось энергии равномерная: равная высота — равные кэВ, расстояния можно сравнивать " +
            "линейкой. Всё, что ниже 300 кэВ, при этом умещается в десятую часть поля."
    override fun energyValue(keV: Int) = "$keV кэВ"

    override val legendZero = "0"
    override val legendIntensityTitle = "Интенсивность · имп/с"
    override val legendShapeTitle = "Форма · доля от максимума столбца"
    override val legendColumnMax = "макс. столбца"
    override fun legendRate(value: String) = "$value имп/с"

    override fun windowSeconds(seconds: Long) = "окно $seconds с"
    override fun countsPerSecond(value: String) = "$value имп/с"

    override val keyDoseRate = "мощность"
    override val keyCount = "счёт"
    override val keyMeanEnergy = "ср. энергия"

    override fun countsInColumn(counts: Int) = "$counts имп в колонке"

    override fun partialColumn(measured: Long, step: Long) =
        "Измерено $measured с из $step с шага: остальное время прибор не писал. Скорость " +
            "считается по измеренному, поэтому неполная колонка не выглядит спадом."

    override val noStoredSpectrum =
        "Полный спектр этого момента не открыть: в истории спектрограммы лежат " +
            "энергетические полосы, а не каналы. Восстанавливать спектр из них приложение " +
            "не станет — это была бы придуманная кривая. Полные спектры сохраняются " +
            "снимками на экране Спектра."

    override val helpTitle = "Как читать спектрограмму"

    override val howToRead = listOf(
        "По горизонтали — время, слева старое.",
        "По вертикали — энергия зарегистрированных импульсов.",
        "Насыщенный цвет — выше интенсивность; пустая колонка означает, что измерений не было.",
        "Касание по картинке разворачивает её во весь экран.",
        "Там ведите пальцем: курсор покажет момент, а маркер — энергию под пальцем.",
    )

    override val technicalTitle = "Технические подробности →"

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

    override fun placeAt(latitude: String, longitude: String, accuracy: String) =
        "$latitude, $longitude · ±$accuracy m"
    override val showOnMap = "On the map"

    override fun notSpectrometer(device: String) =
        "$device does not separate energies: the bands of this picture are not energies " +
            "but a division of the instrument scale. It cannot be read as a spectrum " +
            "over time."

    override val title = "Spectrogram"
    override val noLink = "no link to the instrument"
    override val warmingUp = "collecting the first intervals… columns appear in ~10 s"
    override val offlineHistory = "no link — showing the recorded history"
    override val offlineTag = "no link · history"
    // «переживает перезапуск» — теперь это обещание можно давать: срезы лежат в
    // базе. «Kept on the device», а не «cached» — кэш можно потерять молча.
    override val clearHistory = "Clear the spectrogram"
    override val clearConfirmTitle = "Erase the recorded spectrogram?"
    override val clearConfirmBody =
        "Every recorded stripe goes — both what is on the screen and what is stored in the " +
            "app. Measurements, spectrum snapshots and routes are left untouched."

    override val recordNote =
        "The spectrogram is a record of what was measured: each stripe is one interval " +
            "between polls. Resetting the accumulation on the Spectrum zeroes the instrument's " +
            "sum; what is recorded here stays."

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

    override val energyUnit = "keV"
    override val axisLog = "log"
    override val axisLinear = "lin"
    override val energyScaleLogNote =
        "The energy axis is geometric: equal heights are equal energy ratios, so the low end " +
            "of the spectrum gets its share of rows. Vertical distance is not a difference in keV."
    override val energyScaleLinearNote =
        "The energy axis is uniform: equal heights are equal keV, so distances can be " +
            "compared directly. Everything below 300 keV then fits into a tenth of the plot."
    override fun energyValue(keV: Int) = "$keV keV"

    override val legendZero = "0"
    override val legendIntensityTitle = "Intensity · counts/s"
    override val legendShapeTitle = "Shape · fraction of the column maximum"
    // Верх шкалы формы — максимум ВНУТРИ столбца, а не «больше излучения»:
    // «column max» удерживает это, «high» уже утверждало бы количество.
    override val legendColumnMax = "column max"
    override fun legendRate(value: String) = "$value counts/s"

    override fun windowSeconds(seconds: Long) = "window $seconds s"
    override fun countsPerSecond(value: String) = "$value counts/s"

    override val keyDoseRate = "dose rate"
    override val keyCount = "count rate"
    override val keyMeanEnergy = "mean energy"

    override fun countsInColumn(counts: Int) = "$counts counts in the column"

    override fun partialColumn(measured: Long, step: Long) =
        "Measured $measured s out of the $step s step: the rest of the time the instrument " +
            "was not writing. The rate is computed from the measured time, so a partial " +
            "column does not look like a drop."

    override val noStoredSpectrum =
        "The full spectrum of this moment cannot be opened: the spectrogram history holds " +
            "energy bands, not channels. The app will not rebuild a spectrum from them — " +
            "that would be an invented curve. Full spectra are kept as snapshots on the " +
            "Spectrum screen."

    override val helpTitle = "How to read the spectrogram"

    override val howToRead = listOf(
        "Horizontally — time, oldest on the left.",
        "Vertically — the energy of the registered counts.",
        "Deeper colour means higher intensity; an empty column means there were no measurements.",
        "A tap on the image expands it to the whole screen.",
        "There, drag a finger: the cursor gives the moment and the marker the energy under it.",
    )

    override val technicalTitle = "Technical details →"

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
    placeAt("55,7501", "37,6001", "8"), showOnMap,
    notSpectrometer("RadiaCode Zero"),
    recordNote, clearHistory, clearConfirmTitle, clearConfirmBody,
    title, noLink, warmingUp, offlineHistory, offlineTag, backgroundNote,
    windowMinutes(15), windowHours(2),
    modeIntensity, modeShape,
    energyUnit, axisLog, axisLinear,
    energyScaleLogNote, energyScaleLinearNote, energyValue(146),
    legendZero, legendColumnMax, legendRate("1,2"),
    legendIntensityTitle, legendShapeTitle,
    windowSeconds(26), countsPerSecond("24,3"),
    keyDoseRate, keyCount, keyMeanEnergy,
    countsInColumn(916), partialColumn(12, 30),
    noStoredSpectrum, helpTitle, technicalTitle,
    rateTitle, rateDetailed, rateBalanced, rateEconomy, rateVolume("1,3"), rateThinning(7, 5),
    statIntervals, statStored, statRecorded, statColumnStep, statBands,
    secondsValue(10), coverageNote("02:30", "05:00"),
    stepNote(stepPerBand("1,4")), stepCollecting,
    stepPerBand("1,4"), stepPerBandInstead("1,4", "0,3"),
    bandsMerged(4, 64, 12),
    intensityNote, shapeNote, energyRangeNote(20, 3000),
) + howToRead
