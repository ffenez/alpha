package app.radiacode.ui.text

/**
 * Деталка сессии и Радон.
 *
 * Две области с общими оговорками об ЧЕСТНОСТИ ГРАФИКА, поэтому и каталог
 * один: подпись «линия — среднее по интервалу» и радоновая «это не Бк/м³»
 * живут по одному правилу — текст обязан называть, ЧТО именно посчитано, и
 * отказываться от того, чего прибор не измерял.
 *
 * Три места, где перевод обязан отказываться ровно так же, как русский:
 * - радон — ВСЕГДА «относительный индикатор продуктов распада»; ни в одном
 *   языке он не становится концентрацией и не получает Бк/м³ (упоминание
 *   единицы остаётся только в ОТРИЦАНИИ, которым оно и было);
 * - направление тренда — «направление не выделено», а не «стабильно»:
 *   правило сравнивает наклон с разбросом и может НЕ найти направления, но
 *   доказать постоянство оно не может;
 * - две шкалы полёта (доза и высота) делят ось времени и НЕ совмещаются по
 *   вертикали — в английском это тоже сказано словами.
 */
interface SessionRadonStrings {

    /** Ряд нетто-счёта в окне линии выбранного нуклида. */
    val lineTrendTitle: String
    val lineNetRate: String
    val lineSignificance: String
    val linePoints: String
    val lineResolved: String
    val lineNotResolved: String
    val lineTrendCaveat: String

    /** Экспорт ряда измерений сессии — открытый формат, явное действие. */
    val exportCsv: String
    val exportSaved: String
    val exportFailed: String

    // --- деталка сессии ---
    val sessionTag: String
    val sessionNotFound: String
    val readingSession: String
    val runningNow: String
    val samplesLabel: String
    val doseRateLabel: String
    fun doseRateSummary(avg: String, min: String, max: String, unit: String): String
    val countRateLabel: String
    fun countRateSummary(avg: String, max: String): String
    val sessionDoseLabel: String
    val trackOnMap: String

    // --- график сессии ---
    val chartTitle: String
    val noChartData: String
    val statMin: String
    val statMedian: String
    val statMax: String
    fun sdWithUnit(unit: String): String
    val chartLineNote: String

    /** Явное действие карточки: открыть тот же период полноэкранным графиком. */
    val openFullChart: String

    /** Чем полноэкранный график отличается от этой картинки. */
    val fullChartNote: String

    // --- полёт ---
    val altitudeTitle: String
    val noAltitudePoints: String
    val altitudeNote: String
    fun flightFactor(
        factor: String,
        flightMedian: String,
        groundMedian: String,
        unit: String,
    ): String
    val noGroundPoints: String
    val cosmicNote: String

    // --- события сессии ---
    val eventsTitle: String
    val deviationEvent: String
    val excursionEvent: String

    // --- Радон ---
    val radonTag: String
    val window24h: String
    val window7d: String
    val readingSnapshots: String
    val noRadonDataYet: String
    val radonEmptyExplained: String
    val radonCaveat: String
    val ventilationCheck: String
    val hourlyTitle: String
    val roiRateUnit: String
    val noMeasurementsInWindow: String
    val now: String
    val radonChartNote: String
    val toMedian: String
    val hoursOfData: String
    val trendRising: String
    val trendFalling: String
    val trendFlat: String
    val trendUnknown: String
    val trendWindow: String
    fun currentPoint(rate: String, sigma: String, duration: String): String
}

object SessionRadonRu : SessionRadonStrings {

    override val lineTrendTitle = "Линия во времени"
    override val lineNetRate = "нетто, с⁻¹"
    override val lineSignificance = "значимость"
    override val linePoints = "точек"
    override val lineResolved = "Линия выделяется над континуумом за всё накопленное время."
    override val lineNotResolved =
        "Линия не выделяется над континуумом: нетто того же порядка, что его " +
            "собственная неопределённость."
    override val lineTrendCaveat =
        "Это относительная величина: без измеренной кривой эффективности прибора " +
            "перевод в беккерели невозможен. Сравнивать можно место с местом и время " +
            "со временем одним прибором. В окне считается всё, что попало в энергию, " +
            "поэтому это индикатор линии, а не присутствия нуклида."

    override val exportCsv = "CSV"
    override val exportSaved = "файл сохранён"
    override val exportFailed = "файл не записался — попробуйте другую папку"
    override val sessionTag = "Сессия"
    override val sessionNotFound = "сессия не найдена"
    override val readingSession = "читаю сессию…"
    override val runningNow = "идёт"
    override val samplesLabel = "измерений"
    override val doseRateLabel = "мощность дозы"

    override fun doseRateSummary(avg: String, min: String, max: String, unit: String) =
        "ср $avg · мин $min · макс $max $unit"

    override val countRateLabel = "скорость счёта"

    override fun countRateSummary(avg: String, max: String) = "ср $avg · макс $max с⁻¹"

    override val sessionDoseLabel = "доза за сессию · расчёт"
    override val trackOnMap = "трек · на карте"

    override val chartTitle = "Мощность дозы · вся сессия"
    override val noChartData = "данных для графика нет"
    override val statMin = "мин"
    override val statMedian = "медиана"
    override val statMax = "макс"

    override fun sdWithUnit(unit: String) = "SD, $unit"

    override val chartLineNote =
        "линия — среднее по интервалу (на полноэкранном графике — медиана); пропуски не " +
            "соединяются"

    override val openFullChart = "открыть график ▸"
    override val fullChartNote = "на полном экране тот же период показан медианой интервала с " +
        "квантильными конвертами P25–P75 и P10–P90, поэтому на всплеске числа расходятся с " +
        "этой картинкой: среднее тянется за выбросом, медиана — нет"

    override val altitudeTitle = "Высота · та же ось времени"
    override val noAltitudePoints = "высотных точек для графика нет"
    override val altitudeNote =
        "метры GPS-высоты · график дозы выше делит с этим ту же ось времени — шкалы не " +
            "совмещаются"

    override fun flightFactor(
        factor: String,
        flightMedian: String,
        groundMedian: String,
        unit: String,
    ) = "на эшелоне фон ×$factor от вашего наземного медианного " +
        "($flightMedian против $groundMedian $unit, медианы этой записи)"

    override val noGroundPoints =
        "наземных точек с дозой в этой записи нет — множитель к наземному фону не считается"
    override val cosmicNote =
        "рост фона на эшелоне — ожидаемое космическое излучение, не неисправность прибора"

    override val eventsTitle = "События сессии"
    override val deviationEvent = "отклонение"
    override val excursionEvent = "точка превышения"

    override val radonTag = "Радон"
    override val window24h = "24 ч"
    override val window7d = "7 д"
    override val readingSnapshots = "читаю снимки спектра…"
    override val noRadonDataYet = "данных пока нет"
    override val radonEmptyExplained =
        "Индикатор строится по снимкам спектра: пока прибор подключён, они пишутся " +
            "автоматически (раз в ~10 мин, чаще при открытом Спектре). Первые точки " +
            "появятся через час-два измерения."
    override val radonCaveat =
        "Относительный индикатор радоновых продуктов распада — net-скорость счёта в окнах " +
            "Bi-214 (609 кэВ) и Pb-214 (352 кэВ). Это не концентрация радона в Бк/м³: " +
            "прибор не откалиброван по объёмной активности."
    override val ventilationCheck =
        "Проверка: проветрите помещение и наблюдайте спад — продукты распада радона " +
            "вымываются воздухообменом за десятки минут."

    override val hourlyTitle = "Индикатор по часам"
    override val roiRateUnit = "имп/с в ROI"
    override val noMeasurementsInWindow = "в выбранном окне измерений не было"
    override val now = "сейчас"
    override val radonChartNote = "пунктир — медиана окна · пропуски — часы без измерений"
    override val toMedian = "к медиане"
    override val hoursOfData = "часов данных"
    override val trendRising = "↗ растёт"
    override val trendFalling = "↘ спадает"
    // Правило сравнивает проекцию наклона с разбросом: оно может НЕ найти
    // направления, но не может доказать постоянство. «Стабильно» утверждало
    // бы второе — и в русском, и в английском.
    override val trendFlat = "— направление не выделено"
    override val trendUnknown = "тренд: мало данных"
    override val trendWindow = "тренд последних 6 часов"

    override fun currentPoint(rate: String, sigma: String, duration: String) =
        "текущая точка: $rate ± $sigma имп/с (1σ) за $duration"
}

object SessionRadonEn : SessionRadonStrings {

    override val lineTrendTitle = "A line over time"
    override val lineNetRate = "net, s⁻¹"
    override val lineSignificance = "significance"
    override val linePoints = "points"
    override val lineResolved = "The line stands out above the continuum over all the time collected."
    override val lineNotResolved =
        "The line does not stand out above the continuum: the net is of the same " +
            "order as its own uncertainty."
    override val lineTrendCaveat =
        "This is a relative quantity: without a measured efficiency curve there is no " +
            "conversion to becquerels. Compare place with place and time with time on " +
            "one instrument. The window counts everything that falls into that energy, " +
            "so it indicates a line, not the presence of a nuclide."

    override val exportCsv = "CSV"
    override val exportSaved = "file saved"
    override val exportFailed = "the file was not written — try another folder"
    override val sessionTag = "Session"
    override val sessionNotFound = "session not found"
    override val readingSession = "reading the session…"
    override val runningNow = "running"
    override val samplesLabel = "measurements"
    override val doseRateLabel = "dose rate"

    override fun doseRateSummary(avg: String, min: String, max: String, unit: String) =
        "avg $avg · min $min · max $max $unit"

    override val countRateLabel = "count rate"

    override fun countRateSummary(avg: String, max: String) = "avg $avg · max $max s⁻¹"

    override val sessionDoseLabel = "dose over the session · calculated"
    override val trackOnMap = "track · on the map"

    override val chartTitle = "Dose rate · whole session"
    override val noChartData = "no data for the chart"
    override val statMin = "min"
    override val statMedian = "median"
    override val statMax = "max"

    override fun sdWithUnit(unit: String) = "SD, $unit"

    override val chartLineNote =
        "the line is the interval mean (on the full-screen chart it is the median); gaps " +
            "are not joined"

    override val openFullChart = "open the chart ▸"
    override val fullChartNote = "on the full screen the same period is drawn as the bucket " +
        "median with the P25–P75 and P10–P90 quantile envelopes, so on a spike the numbers " +
        "differ from this picture: the mean follows the outlier, the median does not"

    override val altitudeTitle = "Altitude · the same time axis"
    override val noAltitudePoints = "no altitude points for the chart"
    override val altitudeNote =
        "metres of GPS altitude · the dose chart above shares this time axis — the two " +
            "value scales are not combined"

    override fun flightFactor(
        factor: String,
        flightMedian: String,
        groundMedian: String,
        unit: String,
    ) = "at altitude the background is ×$factor of your ground median " +
        "($flightMedian against $groundMedian $unit, medians of this recording)"

    override val noGroundPoints =
        "this recording has no ground points with a dose rate — the ratio to the ground " +
            "background is not computed"
    override val cosmicNote =
        "the higher background at altitude is the expected cosmic radiation, not an " +
            "instrument fault"

    override val eventsTitle = "Session events"
    override val deviationEvent = "deviation"
    override val excursionEvent = "excursion point"

    override val radonTag = "Radon"
    override val window24h = "24 h"
    override val window7d = "7 d"
    override val readingSnapshots = "reading spectrum snapshots…"
    override val noRadonDataYet = "no data yet"
    override val radonEmptyExplained =
        "The indicator is built from spectrum snapshots: while the instrument is connected " +
            "they are recorded automatically (about every 10 min, more often with Spectrum " +
            "open). The first points appear after an hour or two of measuring."
    // Единица Бк/м³ остаётся ТОЛЬКО внутри отрицания — ею мы отказываемся от
    // концентрации, а не называем показанное число.
    override val radonCaveat =
        "A relative indicator of radon decay products — the net count rate in the Bi-214 " +
            "(609 keV) and Pb-214 (352 keV) windows. It is not a radon concentration in " +
            "Bq/m³: the instrument is not calibrated for volumetric activity."
    override val ventilationCheck =
        "A check: ventilate the room and watch the fall — radon decay products are flushed " +
            "out by air exchange within tens of minutes."

    override val hourlyTitle = "Indicator by hour"
    override val roiRateUnit = "counts/s in the ROI"
    override val noMeasurementsInWindow = "there were no measurements in the selected window"
    override val now = "now"
    override val radonChartNote =
        "the dashed line is the window median · gaps are hours without measurements"
    override val toMedian = "vs median"
    override val hoursOfData = "hours of data"
    override val trendRising = "↗ rising"
    override val trendFalling = "↘ falling"
    // «no direction resolved», а не «steady»: правило не умеет доказывать
    // постоянство — тот же отказ, что и в русском.
    override val trendFlat = "— no direction resolved"
    override val trendUnknown = "trend: not enough data"
    override val trendWindow = "trend over the last 6 hours"

    override fun currentPoint(rate: String, sigma: String, duration: String) =
        "current point: $rate ± $sigma counts/s (1σ) over $duration"
}

val SessionRadonCatalogue = AreaCatalogue(ru = SessionRadonRu, en = SessionRadonEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun SessionRadonStrings.allTexts(): List<String> = listOf(
    lineTrendTitle, lineNetRate, lineSignificance, linePoints, lineResolved,
    lineNotResolved, lineTrendCaveat,
    exportCsv, exportSaved, exportFailed,
    sessionTag, sessionNotFound, readingSession, runningNow, samplesLabel, doseRateLabel,
    doseRateSummary("0,15", "0,12", "0,21", "мкЗв/ч"),
    countRateLabel, countRateSummary("12", "31"), sessionDoseLabel, trackOnMap,
    chartTitle, noChartData, statMin, statMedian, statMax, sdWithUnit("мкЗв/ч"),
    chartLineNote, openFullChart, fullChartNote,
    altitudeTitle, noAltitudePoints, altitudeNote,
    flightFactor("1,8", "0,25", "0,14", "мкЗв/ч"),
    noGroundPoints, cosmicNote,
    eventsTitle, deviationEvent, excursionEvent,
    radonTag, window24h, window7d, readingSnapshots, noRadonDataYet, radonEmptyExplained,
    radonCaveat, ventilationCheck, hourlyTitle, roiRateUnit, noMeasurementsInWindow, now,
    radonChartNote, toMedian, hoursOfData,
    trendRising, trendFalling, trendFlat, trendUnknown, trendWindow,
    currentPoint("0,42", "0,05", "1 ч"),
)
