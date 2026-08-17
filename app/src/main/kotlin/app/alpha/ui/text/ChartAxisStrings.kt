package app.alpha.ui.text

/**
 * Подписи самих графиков: величины, единицы, ступени окна, события, курсор,
 * распределение и метод квантилей.
 *
 * Отдельно от [ChartTextStrings] (справка «i») сознательно: там объяснения,
 * которые читают один раз, здесь — то, что подписывает каждую картинку.
 *
 * Отказы этой области: маркер ▲ говорит о СРАВНЕНИИ («максимум интервала выше
 * P90 профиля»), а не об аномалии; отношение всегда называет знаменатель;
 * оценка по средним под-корзин прямо помечена как не имеющая доказанной
 * границы ошибки.
 */
interface ChartAxisStrings {

    // ------------------------------------------------------------ величины
    val doseTitle: String
    val countRateTitle: String
    val hardnessTitle: String
    val unitCountRate: String
    val unitHardness: String
    val cpsFootnote: String
    fun longWindowsUnavailable(limit: String): String

    // ----------------------------------------------- ступени лестницы окон
    /**
     * Метка живой оси: «сейчас», «−30 с», «−4 мин».
     *
     * Одна функция на обе единицы: секунды до минуты, дальше минуты — на
     * коротком окне «−90 с» читается хуже, чем «−1,5 мин», а «−0,5 мин» хуже,
     * чем «−30 с».
     */
    fun agoLabel(seconds: Long): String

    val stepMinutes: String
    val stepHours: String
    val stepDays: String

    // ----------------------------------------------------------- события
    val aboveL1: String
    val aboveProfileP90: String
    val aboveL1Short: String
    val aboveProfileP90Short: String
    val markerAboveL1: String
    val markerAboveProfileP90: String

    // ------------------------------------------------------------ курсор
    fun ratio(number: String, denominator: String): String
    val denominatorProfileP90: String
    val denominatorProfileMedian: String
    val profileP90Explained: String
    val profileMedianExplained: String
    fun atMoment(time: String): String
    fun atInterval(from: String, to: String): String

    // ------------------------------------------------------------- тренд
    val trendUnavailable: String
    fun needBins(need: Int, have: Int): String

    /** Компактно для плитки: без внутренних чисел алгоритма. */
    val notEnoughData: String
    fun needShort(need: String): String
    fun needSpan(need: String, have: String): String

    // -------------------------------------------------- метод квантилей
    val quantilesExact: String
    fun quantilesSketch(k: Int, version: Int, rankError: String): String
    val quantilesEstimate: String

    // ------------------------------------------------------ распределение
    /** Подпись самой полосы распределения: что за картинка под графиком. */
    val histogramCaption: String

    val histogramCountAxis: String
    val histogramInsufficient: String
    val histogramNoData: String

    // ---------------------------------------------------------- покрытие
    fun coverage(covered: String, window: String): String
}

object ChartAxisRu : ChartAxisStrings {

    override fun agoLabel(seconds: Long): String = when {
        seconds <= 0 -> "сейчас"
        seconds < 60 -> "−$seconds с"
        seconds < 3_600 -> "−${seconds / 60} мин"
        seconds < 86_400 -> "−${seconds / 3_600} ч"
        else -> "−${seconds / 86_400} д"
    }

    override val doseTitle = "Мощность дозы"
    override val countRateTitle = "Скорость счёта"
    override val hardnessTitle = "Жёсткость"
    override val unitCountRate = "с⁻¹"
    override val unitHardness = "(мкрем/ч)/(имп/с)"
    override val cpsFootnote =
        "Скорость счёта (CPS) — сколько событий детектор регистрирует за секунду. Она " +
            "удобна для поиска изменений и источников, но сама по себе не показывает дозу: " +
            "вклад события зависит в том числе от его энергии. Это не мера опасности."

    override fun longWindowsUnavailable(limit: String) =
        "Окна длиннее $limit у этой величины пока нет: предагрегация посчитана для мощности " +
            "дозы, а перебирать всю сырую историю на каждое открытие нельзя."

    override val stepMinutes = "м"
    override val stepHours = "ч"
    override val stepDays = "д"

    override val aboveL1 = "выше порога L1"
    override val aboveProfileP90 = "выше исторического P90 профиля"
    override val aboveL1Short = "> L1"
    override val aboveProfileP90Short = "> P90 профиля"
    override val markerAboveL1 = "максимум интервала выше порога L1"
    override val markerAboveProfileP90 = "максимум интервала выше P90 профиля"

    override fun ratio(number: String, denominator: String) = "×$number к $denominator"

    override val denominatorProfileP90 = "P90 профиля"
    override val denominatorProfileMedian = "медиане профиля"
    override val profileP90Explained =
        "P90 профиля — уровень, ниже которого оставались 90 % исторических измерений этого " +
            "места; это описание истории, а не норматив"
    override val profileMedianExplained =
        "медиана профиля — половина исторических измерений этого места была ниже"

    override fun atMoment(time: String) = "в $time"

    override fun atInterval(from: String, to: String) = "в $from–$to"

    override val trendUnavailable = "тренд недоступен"

    override fun needBins(need: Int, have: Int) = "нужно $need интервалов · есть $have"
    override val notEnoughData = "мало данных"
    override fun needShort(need: String) = "нужно $need"

    override fun needSpan(need: String, have: String) =
        "нужно $need измерений · есть $have"

    override val quantilesExact = "точные порядковые статистики сырых отсчётов"

    override fun quantilesSketch(k: Int, version: Int, rankError: String) =
        "KLL-скетч, k=$k, v$version, ошибка ранга ≈ $rankError"

    override val quantilesEstimate =
        "оценка по средним коротких интервалов — без доказанной границы точности"

    override val histogramCaption = "распределение за окно"
    override val histogramCountAxis = "показаний прибора (≈1 в секунду)"
    override val histogramInsufficient = "недостаточно данных для распределения"
    override val histogramNoData = "нет измерений в окне"

    override fun coverage(covered: String, window: String) = "данных: $covered из $window"
}

object ChartAxisEn : ChartAxisStrings {

    override fun agoLabel(seconds: Long): String = when {
        seconds <= 0 -> "now"
        seconds < 60 -> "−$seconds s"
        seconds < 3_600 -> "−${seconds / 60} min"
        seconds < 86_400 -> "−${seconds / 3_600} h"
        else -> "−${seconds / 86_400} d"
    }

    override val doseTitle = "Dose rate"
    override val countRateTitle = "Count rate"
    override val hardnessTitle = "Hardness"
    override val unitCountRate = "s⁻¹"
    override val unitHardness = "(µrem/h)/(counts/s)"
    override val cpsFootnote =
        "The count rate (CPS) is how many events the detector registers per second. It is " +
            "handy for spotting changes and sources, but on its own it does not show the " +
            "dose: the contribution of an event depends, among other things, on its " +
            "energy. It is not a measure of harm."

    override fun longWindowsUnavailable(limit: String) =
        "Windows longer than $limit are not available for this quantity yet: the " +
            "pre-aggregation is computed for dose rate, and sweeping the whole raw history on " +
            "every open is not an option."

    override val stepMinutes = "m"
    override val stepHours = "h"
    override val stepDays = "d"

    override val aboveL1 = "above the L1 threshold"
    override val aboveProfileP90 = "above the profile's historical P90"
    override val aboveL1Short = "> L1"
    override val aboveProfileP90Short = "> profile P90"
    override val markerAboveL1 = "the interval's maximum is above the L1 threshold"
    override val markerAboveProfileP90 = "the interval's maximum is above the profile's P90"

    override fun ratio(number: String, denominator: String) = "×$number of $denominator"

    override val denominatorProfileP90 = "the profile's P90"
    override val denominatorProfileMedian = "the profile's median"
    override val profileP90Explained =
        "the profile's P90 is the level below which 90 % of this place's historical " +
            "measurements stayed; it describes history, it is not a regulatory limit"
    override val profileMedianExplained =
        "the profile's median — half of this place's historical measurements were below it"

    override fun atMoment(time: String) = "at $time"

    override fun atInterval(from: String, to: String) = "at $from–$to"

    override val trendUnavailable = "trend unavailable"

    override fun needBins(need: Int, have: Int) = "$need intervals needed · $have present"
    override val notEnoughData = "not enough data"
    override fun needShort(need: String) = "$need needed"

    override fun needSpan(need: String, have: String) =
        "$need of measurements needed · $have present"

    override val quantilesExact = "exact order statistics of the raw readings"

    override fun quantilesSketch(k: Int, version: Int, rankError: String) =
        "KLL sketch, k=$k, v$version, rank error ≈ $rankError"

    override val quantilesEstimate =
        "an estimate from sub-bucket means — with no proven bound on its accuracy"

    override val histogramCaption = "distribution over the window"
    override val histogramCountAxis = "instrument readings (≈1 per second)"
    override val histogramInsufficient = "not enough data for a distribution"
    override val histogramNoData = "no measurements in the window"

    override fun coverage(covered: String, window: String) = "data: $covered out of $window"
}

val ChartAxisCatalogue = AreaCatalogue(ru = ChartAxisRu, en = ChartAxisEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun ChartAxisStrings.allTexts(): List<String> = listOf(
    agoLabel(0), agoLabel(30), agoLabel(240),
    doseTitle, countRateTitle, hardnessTitle, unitCountRate, unitHardness, cpsFootnote,
    longWindowsUnavailable("6 ч"), stepMinutes, stepHours, stepDays,
    aboveL1, aboveProfileP90, aboveL1Short, aboveProfileP90Short,
    markerAboveL1, markerAboveProfileP90,
    ratio("4,8", denominatorProfileP90), denominatorProfileP90, denominatorProfileMedian,
    profileP90Explained, profileMedianExplained, atMoment("12:30"),
    atInterval("12:30", "12:35"),
    trendUnavailable, needBins(12, 3), needSpan("10 мин", "4 мин"),
    notEnoughData, needShort("10 мин"),
    quantilesExact, quantilesSketch(128, 1, "1,2 %"), quantilesEstimate,
    histogramCaption, histogramCountAxis, histogramInsufficient, histogramNoData,
    coverage("47 мин", "6 ч"),
)
