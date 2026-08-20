package app.alpha.ui.text

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

    /**
     * Подпись охвата ряда. Отдельно от выбранного окна сознательно: окно —
     * это запрос, а охват — то, за что ДЕЙСТВИТЕЛЬНО есть снимки. Без него
     * «24 ч» и «7 д» на одинаковых данных выглядели одинаково, и разницу
     * между «прибор столько не работал» и «экран не работает» увидеть было
     * нечем.
     */
    fun spanMinutes(value: Int): String
    fun spanHours(value: Int): String
    fun spanDays(value: Int): String
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
    fun doseRateSummary(avg: String, min: String, max: String): String
    val countRateLabel: String
    fun countRateSummary(avg: String, max: String): String
    val sessionDoseLabel: String
    val trackOnMap: String

    // --- график сессии ---
    val chartTitle: String

    // --- условия вокруг измерения (датчики телефона) ---

    /** Заголовок карточки условий. */
    val conditionsTitle: String

    /** Подписи переключателя рядов и их единицы. */
    val conditionPressure: String
    val conditionField: String
    val conditionDeviceTemp: String

    /** «1008,1–1014,0 гПа за сессию» — размах ряда со своей единицей. */
    fun conditionRange(low: String, high: String, unit: String): String

    val unitHpa: String
    val unitMicroTesla: String
    val unitCelsius: String

    /** Оговорки к рядам условий: они меняют прочтение картинки. */
    /** «давление −6 гПа за окно: 1014 → 1008» — факт без причинности. */
    fun pressureChange(delta: String, from: String, to: String, unit: String): String

    /** Почему давление стоит рядом с радоном — и чего это НЕ доказывает. */
    val radonPressureNote: String

    val conditionPressureNote: String
    val conditionFieldNote: String
    val conditionDeviceTempNote: String
    val noChartData: String
    val statMin: String
    val statMedian: String
    val statMax: String
    val sd: String
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
    ): String
    val noGroundPoints: String
    val cosmicNote: String

    // --- события сессии ---
    val eventsTitle: String
    val deviationEvent: String
    val excursionEvent: String

    // --- Радон ---
    val radonTag: String

    // --- единый шаблон результата (аналитика) ---
    /**
     * Категориальный ответ вместо статистического остатка.
     *
     * `−0,29 сейчас` в роли главного числа не читается никак: ни «мало», ни
     * «много», ни «ничего нет», — а означает ровно последнее. Число со знаком
     * осталось, но переехало в подробности.
     */
    val radonResultNotable: String
    val radonResultPlain: String
    val radonResultNoData: String
    val radonMeaningNotable: String
    val radonMeaningPlain: String
    val radonMeaningNoData: String
    val radonLimit: String

    val lineResultExcess: String
    val lineResultPlain: String
    val lineResultNoData: String
    fun lineMeaningExcess(line: String): String
    fun lineMeaningPlain(line: String): String
    val lineMeaningNoData: String

    /** «13 часовых интервалов · охват 11 ч» — объём измерения одной строкой. */
    fun measuredIntervals(intervals: String, span: String): String
    fun hourIntervals(count: Int): String

    /** Подписи технического уровня: те же величины числами. */
    val detailNet: String
    val detailSignificance: String
    val detailSignificanceNote: String
    val detailWindow: String
    val detailContinuum: String
    val detailContinuumValue: String
    val detailCurrent: String
    val detailMedian: String
    val detailToMedian: String
    fun windowAround(energy: String, halfWidth: String): String
    fun netWithSigma(net: String, sigma: String): String
    fun sigmaUnits(value: String): String
    val window24h: String
    val window7d: String
    val readingSnapshots: String
    val ventilationCheck: String
    val hourlyTitle: String
    val now: String
    val radonChartNote: String
    val trendRising: String
    val trendFalling: String
    val trendFlat: String
    val trendUnknown: String
    val trendWindow: String
}

object SessionRadonRu : SessionRadonStrings {

    override val lineTrendTitle = "Линия во времени"
    override fun spanMinutes(value: Int) = "$value мин"
    override fun spanHours(value: Int) = "$value ч"
    override fun spanDays(value: Int) = "$value д"
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

    override fun doseRateSummary(avg: String, min: String, max: String) =
        "ср $avg · мин $min · макс $max"

    override val countRateLabel = "скорость счёта"

    override fun countRateSummary(avg: String, max: String) = "ср $avg · макс $max"

    override val sessionDoseLabel = "доза за сессию"
    override val trackOnMap = "трек · на карте"

    override val chartTitle = "Мощность дозы · вся сессия"

    override val conditionsTitle = "Условия"
    override val conditionPressure = "давление"
    override val conditionField = "поле"
    override val conditionDeviceTemp = "прибор"

    override fun conditionRange(low: String, high: String, unit: String) =
        "$low–$high $unit за сессию"

    override val unitHpa = "гПа"
    override val unitMicroTesla = "мкТл"
    override val unitCelsius = "°C"

    override fun pressureChange(delta: String, from: String, to: String, unit: String) =
        "давление $delta $unit за окно: $from → $to"

    override val radonPressureNote =
        "Падение давления часто совпадает с ростом радоновых продуктов: воздух легче выходит из " +
            "почвы. Совпадение двух рядов причиной не является — экран показывает оба и не " +
            "делает вывода."

    override val conditionPressureNote =
        "Давление меняется и от погоды, и от высоты: один гектопаскаль — это около восьми метров " +
            "подъёма. По одному ряду эти две причины не разделить."
    override val conditionFieldNote =
        "Поле — магнитометр телефона. Собственные магниты телефона обычно сильнее того, что " +
            "вокруг, поэтому смысл имеет изменение вдоль пути, а не само число."
    override val conditionDeviceTempNote =
        "Температура датчика внутри прибора. К воздуху она ближе всего, что здесь есть, но " +
            "корпус остывает и нагревается медленно: после переноса в другое место значение " +
            "несколько минут догоняет новую температуру, и сравнивать раньше нечего."
    override val noChartData = "данных для графика нет"
    override val statMin = "мин"
    override val statMedian = "медиана"
    override val statMax = "макс"

    override val sd = "SD"

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
    ) = "на эшелоне фон ×$factor от вашего наземного медианного " +
        "($flightMedian против $groundMedian, медианы этой записи)"

    override val noGroundPoints =
        "наземных точек с дозой в этой записи нет — множитель к наземному фону не считается"
    override val cosmicNote =
        "рост фона на эшелоне — ожидаемое космическое излучение, не неисправность прибора"

    override val eventsTitle = "События сессии"
    override val deviationEvent = "отклонение"
    override val excursionEvent = "точка превышения"

    override val radonTag = "Продукты радона"

    override val radonResultNotable = "Признак продуктов радона заметен"
    override val radonResultPlain = "Признак не выражен"
    override val radonResultNoData = "Данных пока мало"
    override val radonMeaningNotable =
        "В окнах Bi-214 и Pb-214 событий устойчиво больше, чем в соседних участках " +
            "спектра. Это дочерние продукты распада радона."
    override val radonMeaningPlain =
        "В окнах Bi-214 и Pb-214 событий не больше, чем в соседних участках спектра."
    override val radonMeaningNoData =
        "Индикатор строится по снимкам спектра: пока прибор подключён, они пишутся " +
            "автоматически. Первые точки появятся через час-два измерения."
    override val radonLimit =
        "Это косвенный признак, а не измерение концентрации: перевод в Бк/м³ " +
            "требует радон-монитора, прибор по объёмной активности не откалиброван."

    override val lineResultExcess = "Линия выделяется над локальным фоном спектра"
    override val lineResultPlain = "Линия не выделяется"
    override val lineResultNoData = "Данных пока мало"
    override fun lineMeaningExcess(line: String) =
        "В окне $line событий устойчиво больше, чем в соседних участках спектра."
    override fun lineMeaningPlain(line: String) =
        "В окне $line событий не больше, чем в соседних участках спектра."
    override val lineMeaningNoData =
        "Нужны снимки спектра за несколько часов подряд: их делает служба, пока " +
            "прибор подключён."

    override fun measuredIntervals(intervals: String, span: String) =
        "$intervals · охват $span"
    override fun hourIntervals(count: Int): String {
        val tail = count % 100
        val last = count % 10
        val word = when {
            tail in 11..14 -> "часовых интервалов"
            last == 1 -> "часовой интервал"
            last in 2..4 -> "часовых интервала"
            else -> "часовых интервалов"
        }
        return "$count $word"
    }

    override val detailNet = "Избыток над локальным фоном спектра"
    override val detailSignificance = "Насколько уверенно отличается от фона"
    override val detailSignificanceNote =
        "Избыток в долях собственной погрешности. С трёх говорят, что он есть; " +
            "минус означает, что в самом окне оказалось меньше, чем по соседству."
    override val detailWindow = "Энергетическое окно"
    override val detailContinuum = "Локальный фон спектра"
    override val detailContinuumValue = "оценён по участкам слева и справа от окна"
    override val detailCurrent = "Сейчас"
    override val detailMedian = "Медиана окна"
    override val detailToMedian = "Сейчас к медиане"
    override fun windowAround(energy: String, halfWidth: String) = "$energy ± $halfWidth кэВ"
    override fun netWithSigma(net: String, sigma: String) = "$net ± $sigma имп/с"
    override fun sigmaUnits(value: String) = "$value σ"
    override val window24h = "24 ч"
    override val window7d = "7 д"
    override val readingSnapshots = "читаю снимки спектра…"
    override val ventilationCheck =
        "Проверка проветриванием: проветрите помещение и последите за показателем " +
            "ближайшие часы. Если вклад продуктов распада радона заметен, после смены " +
            "воздухообмена показатель может измениться. Это косвенная проверка, а не " +
            "измерение концентрации."

    override val hourlyTitle = "Индикатор по часам"
    override val now = "сейчас"
    override val radonChartNote = "пунктир — медиана окна · пропуски — часы без измерений"
    override val trendRising = "↗ растёт"
    override val trendFalling = "↘ спадает"
    // Правило сравнивает проекцию наклона с разбросом: оно может НЕ найти
    // направления, но не может доказать постоянство. «Стабильно» утверждало
    // бы второе — и в русском, и в английском.
    override val trendFlat = "— направление не выделено"
    override val trendUnknown = "тренд: мало данных"
    override val trendWindow = "тренд последних 6 часов"

}

object SessionRadonEn : SessionRadonStrings {

    override val lineTrendTitle = "A line over time"
    override fun spanMinutes(value: Int) = "$value min"
    override fun spanHours(value: Int) = "$value h"
    override fun spanDays(value: Int) = "$value d"
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

    override fun doseRateSummary(avg: String, min: String, max: String) =
        "avg $avg · min $min · max $max"

    override val countRateLabel = "count rate"

    override fun countRateSummary(avg: String, max: String) = "avg $avg · max $max s⁻¹"

    override val sessionDoseLabel = "dose over the session"
    override val trackOnMap = "track · on the map"

    override val chartTitle = "Dose rate · whole session"

    override val conditionsTitle = "Conditions"
    override val conditionPressure = "pressure"
    override val conditionField = "field"
    override val conditionDeviceTemp = "instrument"

    override fun conditionRange(low: String, high: String, unit: String) =
        "$low–$high $unit over the session"

    override val unitHpa = "hPa"
    override val unitMicroTesla = "µT"
    override val unitCelsius = "°C"

    override fun pressureChange(delta: String, from: String, to: String, unit: String) =
        "pressure $delta $unit over the window: $from → $to"

    override val radonPressureNote =
        "A falling pressure often coincides with rising radon daughters: air leaves the ground " +
            "more easily. A coincidence of two series is not a cause — the screen shows both and " +
            "draws no conclusion."

    override val conditionPressureNote =
        "Pressure changes with the weather and with height alike: one hectopascal is about eight " +
            "metres of climb. One series cannot separate the two causes."
    override val conditionFieldNote =
        "The field comes from the phone's magnetometer. The phone's own magnets are usually " +
            "stronger than what surrounds it, so the change along the way carries the meaning, " +
            "not the number itself."
    override val conditionDeviceTempNote =
        "The temperature of the sensor inside the instrument. It is the closest thing to air " +
            "temperature here, but the case warms and cools slowly: after a move it takes some " +
            "minutes to catch up, and there is nothing to compare before that."
    override val noChartData = "no data for the chart"
    override val statMin = "min"
    override val statMedian = "median"
    override val statMax = "max"

    override val sd = "SD"

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
    ) = "at altitude the background is ×$factor of your ground median " +
        "($flightMedian against $groundMedian, medians of this recording)"

    override val noGroundPoints =
        "this recording has no ground points with a dose rate — the ratio to the ground " +
            "background is not computed"
    override val cosmicNote =
        "the higher background at altitude is the expected cosmic radiation, not an " +
            "instrument fault"

    override val eventsTitle = "Session events"
    override val deviationEvent = "deviation"
    override val excursionEvent = "excursion point"

    override val radonTag = "Radon daughters"

    override val radonResultNotable = "Radon daughters stand out"
    override val radonResultPlain = "No excess to speak of"
    override val radonResultNoData = "Not enough data yet"
    override val radonMeaningNotable =
        "The Bi-214 and Pb-214 windows hold steadily more events than the parts of " +
            "the spectrum next to them. These are radon decay products."
    override val radonMeaningPlain =
        "The Bi-214 and Pb-214 windows hold no more events than the parts of the " +
            "spectrum next to them."
    override val radonMeaningNoData =
        "The indicator is built from spectrum snapshots, written automatically while " +
            "the instrument is connected. The first points appear after an hour or two."
    override val radonLimit =
        "This is an indirect sign, not a measurement of concentration: Bq/m³ needs a " +
            "radon monitor, and this instrument is not calibrated for volume activity."

    override val lineResultExcess = "The line stands out above the local spectrum background"
    override val lineResultPlain = "The line does not stand out"
    override val lineResultNoData = "Not enough data yet"
    override fun lineMeaningExcess(line: String) =
        "The $line window holds steadily more events than the parts of the spectrum " +
            "next to it."
    override fun lineMeaningPlain(line: String) =
        "The $line window holds no more events than the parts of the spectrum next to it."
    override val lineMeaningNoData =
        "Spectrum snapshots over several hours in a row are needed; the service writes " +
            "them while the instrument is connected."

    override fun measuredIntervals(intervals: String, span: String) =
        "$intervals · $span covered"
    override fun hourIntervals(count: Int) =
        if (count == 1) "1 hourly interval" else "$count hourly intervals"

    override val detailNet = "Excess above the local spectrum background"
    override val detailSignificance = "How firmly it differs from the background"
    override val detailSignificanceNote =
        "The excess in units of its own uncertainty. From three it is called an " +
            "excess; a minus means the window itself held less than its neighbours."
    override val detailWindow = "Energy window"
    override val detailContinuum = "Local spectrum background"
    override val detailContinuumValue = "estimated from the parts left and right of the window"
    override val detailCurrent = "Now"
    override val detailMedian = "Median of the window"
    override val detailToMedian = "Now vs the median"
    override fun windowAround(energy: String, halfWidth: String) = "$energy ± $halfWidth keV"
    override fun netWithSigma(net: String, sigma: String) = "$net ± $sigma counts/s"
    override fun sigmaUnits(value: String) = "$value σ"
    override val window24h = "24 h"
    override val window7d = "7 d"
    override val readingSnapshots = "reading spectrum snapshots…"
    // Единица Бк/м³ остаётся ТОЛЬКО внутри отрицания — ею мы отказываемся от
    // концентрации, а не называем показанное число.
    override val ventilationCheck =
        "The airing check: air the room and watch the indicator over the next few hours. " +
            "If radon decay products contribute noticeably, the indicator may change once " +
            "the air exchange does. This is an indirect check, not a measurement of " +
            "concentration."

    override val hourlyTitle = "Indicator by hour"
    override val now = "now"
    override val radonChartNote =
        "the dashed line is the window median · gaps are hours without measurements"
    override val trendRising = "↗ rising"
    override val trendFalling = "↘ falling"
    // «no direction resolved», а не «steady»: правило не умеет доказывать
    // постоянство — тот же отказ, что и в русском.
    override val trendFlat = "— no direction resolved"
    override val trendUnknown = "trend: not enough data"
    override val trendWindow = "trend over the last 6 hours"

}

val SessionRadonCatalogue = AreaCatalogue(ru = SessionRadonRu, en = SessionRadonEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun SessionRadonStrings.allTexts(): List<String> = listOf(
    lineTrendTitle, spanMinutes(20), spanHours(6), spanDays(3),
    lineTrendCaveat,
    exportCsv, exportSaved, exportFailed,
    sessionTag, sessionNotFound, readingSession, runningNow, samplesLabel, doseRateLabel,
    doseRateSummary("0,15", "0,12", "0,21"),
    countRateLabel, countRateSummary("12", "31"), sessionDoseLabel, trackOnMap,
    chartTitle, noChartData, statMin, statMedian, statMax, sd,
    conditionsTitle, conditionPressure, conditionField, conditionDeviceTemp,
    conditionRange("1008,1", "1014,0", unitHpa), unitHpa, unitMicroTesla, unitCelsius,
    conditionPressureNote, conditionFieldNote, conditionDeviceTempNote,
    pressureChange("−6,0", "1014,0", "1008,0", unitHpa), radonPressureNote,
    chartLineNote, openFullChart, fullChartNote,
    altitudeTitle, noAltitudePoints, altitudeNote,
    flightFactor("1,8", "0,25", "0,14"),
    noGroundPoints, cosmicNote,
    eventsTitle, deviationEvent, excursionEvent,
    radonTag, radonResultNotable, radonResultPlain, radonResultNoData,
    radonMeaningNotable, radonMeaningPlain, radonMeaningNoData, radonLimit,
    lineResultExcess, lineResultPlain, lineResultNoData,
    lineMeaningExcess("Cs-137 · 661,7"), lineMeaningPlain("Cs-137 · 661,7"), lineMeaningNoData,
    measuredIntervals(hourIntervals(13), "11 ч"), hourIntervals(1), hourIntervals(2),
    detailNet, detailSignificance, detailSignificanceNote, detailWindow,
    detailContinuum, detailContinuumValue, detailCurrent, detailMedian, detailToMedian,
    windowAround("661,7", "52,9"), netWithSigma("0,12", "0,04"), sigmaUnits("3,1"),
    window24h, window7d, readingSnapshots,
    ventilationCheck, hourlyTitle, now,
    radonChartNote,
    trendRising, trendFalling, trendFlat, trendUnknown, trendWindow,
)
