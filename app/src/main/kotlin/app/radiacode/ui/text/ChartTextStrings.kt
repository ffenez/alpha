package app.radiacode.ui.text

/**
 * Строки полноэкранного графика и его справки «как читать этот график».
 *
 * Здесь живут формулировки, которые обязаны оставаться научно точными на
 * любом языке: линия — медиана интервала, полосы — НАБЛЮДАЕМЫЙ РАЗБРОС
 * измерений (не погрешность прибора и не доверительный интервал), маркер ▲ —
 * факт СРАВНЕНИЯ, а не признак аномалии, а квантили названы тем методом,
 * которым посчитано именно это окно. Английский отказывается от утверждения
 * ровно там же, где отказывается русский.
 */
interface ChartTextStrings {

    // --- справка «как читать этот график» ---

    val infoTitle: String
    val sectionAnatomy: String
    val sectionReferences: String
    val sectionNumbers: String
    val sectionGestures: String

    val anatomyAxis: String
    val anatomyMedianLine: String

    /** Второй уровень: что такое медиана и почему она устойчива. */
    val anatomyMedianDetail: String
    val anatomyEnvelopes: String

    /** Второй уровень: P25–P75 и P10–P90 и чем они НЕ являются. */
    val anatomyEnvelopesDetail: String
    val anatomyHardnessRatio: String
    val anatomyGaps: String

    val referenceProfileBand: String
    val referenceProfileBandMissing: String
    val referenceAlarmLine: String
    val referenceAlarmAbsent: String
    val referenceMarkers: String
    val referenceEpisodes: String

    val quantilesExact: String
    val quantilesExactDetail: String

    /** [rankError] — уже отформатированная доля вида «≈ 1,8 %». */
    fun quantilesSketch(rankError: String): String
    fun quantilesSketchDetail(rankError: String): String
    val quantilesSubBucketMeans: String
    val quantilesSubBucketMeansDetail: String
    val sampleCountNote: String
    val sampleCountDetail: String

    /** Кнопка второго уровня справки. */
    val showDetails: String
    val hideDetails: String
    /**
     * Чип сглаживания. Правило панели: название постоянное, подсвечен =
     * названное состояние ВКЛЮЧЕНО, — поэтому чип называет сглаживание, а не
     * подробность: включают именно его, а подробный вид это его отсутствие.
     */
    val smoothChip: String
    val detailNote: String
    val smoothedNote: String

    val logScaleNote: String
    fun logScaleDroppedNote(buckets: Int): String

    val gestureZoomPan: String
    val gestureCursor: String
    val gestureMarkerTap: String
    val gestureDoubleTap: String

    /** Исторический режим: двойное нажатие возвращает к диапазону сессии. */
    val gestureDoubleTapRange: String

    /** Исторический режим: откуда взят кадр и почему он не обновляется. */
    val historicalRangeNote: String

    // --- сам экран ---

    val loadingLog: String
    val emptyWindow: String

    /** [duration] приходит уже отформатированной («6ч», «30 мин»). */
    fun windowLabel(duration: String): String
    fun windowSheetTitle(duration: String): String
    fun windowStatsLine(
        p10: String,
        median: String,
        p90: String,
        samples: String,
        duration: String,
    ): String
    val moreDetails: String
    /** Чип «события»: метки кратковременных отклонений над полем. */
    val eventsChip: String
    /** «3 события» в карточке курсора и что они означают. */
    fun cursorEvents(count: Int): String
    val cursorEventsNote: String
    val distribution: String
    val windowStatistics: String

    val nowChip: String

    /** Исторический режим вместо «⌖ сейчас»: вернуться к диапазону сессии. */
    val sessionChip: String

    /** Шапка исторического графика: «12 авг 14:03–16:18 · 2 ч 15 мин». */
    fun sessionRangeLabel(range: String, duration: String): String
    val pausedChip: String
    val logChip: String

    val median: String
    val min: String
    val max: String
    val samplesLabel: String
    val spreadDefinitions: String

    fun quantileMethodLine(label: String): String
    fun preAggregationProgress(percent: Int, done: Int, total: Int): String
    val computing: String
    val compareWithRaw: String

    fun exactPathRefused(maxRows: Int): String
    val noRawSamplesInHours: String
    fun diagnosticsHeader(samples: String): String
    fun sketchCountMismatch(sketchCount: String): String
    fun rankErrorSuffix(percent: String): String
    fun maxRankError(percent: String): String

    val cursorNoData: String
    val cursorNoDataDetail: String
    fun cursorMaxAgainstAlarm(max: String, level: String): String
    fun cursorMaxAgainstProfileP90(max: String, level: String): String
    val bucketQuantilesSketch: String
    val bucketQuantilesCoarse: String
    val historicalProfile: String
}

object ChartTextRu : ChartTextStrings {

    override val infoTitle = "Как читать этот график"
    override val sectionAnatomy = "Что нарисовано"
    override val sectionReferences = "С чем сравнивается"
    override val sectionNumbers = "Откуда числа"
    override val sectionGestures = "Жесты"

    override val anatomyAxis = "Ось значений подогнана к наблюдаемым данным, а не к нулю: " +
        "показаны те уровни, на которых прибор реально был. Порог тревоги входит в кадр, " +
        "только когда данные к нему подходят; далёкий порог обозначен указателем у верхней " +
        "кромки."
    override val anatomyMedianLine = "Линия показывает типичный уровень за этот промежуток " +
        "времени. Она построена по медиане: половина измерений была выше неё, половина ниже."
    override val anatomyMedianDetail = "Крупное число на Главной и последняя точка графика " +
        "отвечают на разные вопросы и совпадать не обязаны: число — это ПОСЛЕДНЕЕ показание " +
        "прибора, точка — медиана своего промежутка. " +
        "Медиана — это 50-й процентиль (P50). Она меньше " +
        "реагирует на единичные всплески, чем среднее значение, поэтому линия описывает " +
        "уровень, а не его выбросы."
    override val anatomyEnvelopes = "Затенённые области показывают, насколько менялись " +
        "показания: внутренняя — средние 50 % измерений, внешняя — 80 %. Это наблюдавшийся " +
        "разброс значений, а не погрешность прибора."
    override val anatomyEnvelopesDetail = "Границы областей — P25–P75 и P10–P90. Это описание " +
        "разброса наблюдений, а не доверительный интервал и не неопределённость измерения: " +
        "в метрологии у неё отдельный строгий смысл."
    override val anatomyHardnessRatio = "Отношение берётся по каждому отсчёту, а не делением " +
        "средних интервала: среднее отношений и отношение средних — разные числа."
    // Раньше текст обещал штриховку; DoseChart с §1 ТЗ рисует пропуск спокойной
    // плоскостью (диагональ читалась как ошибка рендера). Справка обязана
    // описывать то, что на экране.
    override val anatomyGaps = "Пропуски не соединяются линией: если прибор молчал, на этом " +
        "месте ровная заливка, а не прямая от точки до точки."

    override val referenceProfileBand = "Серая полоса с пунктиром — обычный диапазон этого " +
        "места: P10–P90 его исторических измерений. Это статистика места, а не норматив " +
        "радиационной безопасности — внутри полосы лежит около 80 % прошлых измерений, " +
        "и только это она и означает."
    override val referenceProfileBandMissing = "Исторический диапазон профиля ещё не собран, " +
        "поэтому серой полосы нет."
    override val referenceAlarmLine = "Красный пунктир — ваш порог тревоги L1. Если он выше " +
        "кадра, у верхней кромки стоит указатель с его значением."
    override val referenceAlarmAbsent = "Порога тревоги на этом графике нет: он задан в " +
        "единицах дозы."
    override val referenceMarkers = "▲ над полем — максимум интервала, оказавшийся выше " +
        "названной величины: залитый — выше порога L1, контурный — выше P90 профиля. Это " +
        "сравнение, а не признак аномалии: выше P90 по определению лежит около 10 % подходящих " +
        "исторических измерений этого места. Нажмите на треугольник — откроется карточка с " +
        "обоими числами и точным временем."
    override val referenceEpisodes = "Вертикальные полосы — эпизоды из журнала событий; " +
        "подпись называет, чего именно они выше, и их расчётную длительность."

    override val quantilesExact = "Для этого периода график построен непосредственно по " +
        "сохранённым измерениям."
    override val quantilesExactDetail = "Квантили точные: считаются по всем сырым отсчётам окна."
    override fun quantilesSketch(rankError: String) = "Период длинный, поэтому приложение " +
        "использует сжатую историю — статистические границы приближённые."
    override fun quantilesSketchDetail(rankError: String) = "Квантили собраны из почасовых " +
        "сжатых выжимок распределения. Ошибка ранга ≈ $rankError."
    override val quantilesSubBucketMeans = "Для этого периода статистика пока приблизительная: " +
        "приложение ещё обрабатывает накопленную историю."
    override val quantilesSubBucketMeansDetail = "Пока сжатые выжимки строятся, квантили " +
        "оценены по средним под-интервалов — это оценка без доказанной границы точности. Она " +
        "заменится точной, как только предварительный расчёт дойдёт до этих часов."
    override val sampleCountNote = "n — сколько показаний прибора вошло в статистику этого окна."
    override val sampleCountDetail = "Прибор пишет примерно раз в секунду, поэтому n близко к " +
        "числу секунд измерений; при пропусках оно меньше — фактическое покрытие окна " +
        "подписано отдельно."
    override val showDetails = "Подробнее"
    override val hideDetails = "Свернуть подробности"
    override val smoothChip = "сглаживание"
    override val detailNote =
        "Подробный вид: линия идёт по самим измерениям окна. На длинном окне " +
            "она ведётся по крайним значениям интервалов, поэтому пики и провалы " +
            "остаются видны."
    override val smoothedNote =
        "Сглаженный вид: линия — медиана интервала, заливки — разброс измерений " +
            "внутри него (P25–P75 и P10–P90). Числа окна от вида не зависят."
    override val logScaleNote = "Шкала логарифмическая: равные расстояния означают равные " +
        "отношения, а не равные разности."
    override fun logScaleDroppedNote(buckets: Int) = "Шкала логарифмическая: интервалов с " +
        "нулём не показано — $buckets. Ноль на такой шкале не существует, и рисовать его на " +
        "месте наименьшего значения было бы неправдой."

    override val gestureZoomPan = "Щипок — масштаб времени, перетаскивание — сдвиг окна."
    override val gestureCursor = "Долгое нажатие ставит курсор: он показывает интервал, его " +
        "медиану, разброс, мин/макс со временем и сравнение с профилем."
    override val gestureMarkerTap = "Нажатие на треугольник над полем открывает ту же карточку " +
        "сразу на этом интервале."
    override val gestureDoubleTap = "Двойное нажатие возвращает выбранное окно к живому краю."
    override val gestureDoubleTapRange =
        "Двойное нажатие возвращает выбранное окно к концу сессии."
    override val historicalRangeNote = "Показан сохранённый диапазон: измерения этой сессии уже " +
        "записаны, новые сюда не приходят."

    override val loadingLog = "читаем журнал…"
    override val emptyWindow = "в этом окне нет измерений"
    override fun windowLabel(duration: String) = "окно $duration"
    override fun windowSheetTitle(duration: String) = "Окно $duration"
    override fun windowStatsLine(
        p10: String,
        median: String,
        p90: String,
        samples: String,
        duration: String,
    ) = "P10 $p10 · медиана $median · P90 $p90 · n $samples · $duration"
    override val moreDetails = "подробнее ▴"
    override val eventsChip = "события"
    override fun cursorEvents(count: Int) = when {
        count % 10 == 1 && count % 100 != 11 -> "$count событие"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "$count события"
        else -> "$count событий"
    }
    override val cursorEventsNote = "кратковременные отклонения"
    override val distribution = "Распределение"
    override val windowStatistics = "Статистика окна"

    override val nowChip = "⌖ сейчас"
    override val sessionChip = "⌖ сессия"
    override fun sessionRangeLabel(range: String, duration: String) = "$range · $duration"
    override val pausedChip = "пауза"
    override val logChip = "лог"

    override val median = "медиана"
    override val min = "мин"
    override val max = "макс"
    override val samplesLabel = "измерений"
    override val spreadDefinitions = "SD — наблюдаемый разброс значений · " +
        "MAD = median(|xᵢ − медиана|), робастный разброс · IQR = P75 − P25"

    override fun quantileMethodLine(label: String) = "метод квантилей: $label"
    override fun preAggregationProgress(percent: Int, done: Int, total: Int) =
        "предагрегация истории: $percent % ($done из $total ч)"
    override val computing = "считаем…"
    override val compareWithRaw = "сверить с сырыми"

    override fun exactPathRefused(maxRows: Int) =
        "точный путь отказался: в окне больше $maxRows отсчётов"
    override val noRawSamplesInHours = "в этих часах нет сырых отсчётов"
    override fun diagnosticsHeader(samples: String) = "точные против скетча · n $samples"
    override fun sketchCountMismatch(sketchCount: String) =
        " (скетч знает $sketchCount — сравниваются разные данные)"
    override fun rankErrorSuffix(percent: String) = " (ранг $percent)"
    override fun maxRankError(percent: String) = "максимальная ошибка ранга $percent"

    override val cursorNoData = "нет данных"
    override val cursorNoDataDetail = "прибор не писал в этот момент"
    override fun cursorMaxAgainstAlarm(max: String, level: String) = "макс $max · L1 $level"
    override fun cursorMaxAgainstProfileP90(max: String, level: String) =
        "макс $max · P90 профиля $level"
    override val bucketQuantilesSketch = "квантили интервала — почасовые скетчи; мин/макс и " +
        "время — точные"
    override val bucketQuantilesCoarse = "квантили интервала — грубая оценка, предагрегация ещё " +
        "строится"
    // Человеческое имя вместо статистического: «P10–P90 профиля» на экране
    // читается как норматив безопасности, хотя это просто диапазон, в котором
    // здесь обычно и бывает. Сами числа и их смысл — в справке «i».
    override val historicalProfile = "обычный диапазон места"
}

/**
 * English catalogue of the chart. Where the Russian text refuses to claim
 * something — the bands are the observed spread and not an uncertainty, the
 * marker is a comparison and not an anomaly, the estimate has no proven error
 * bound — the English text refuses exactly the same claim.
 */
object ChartTextEn : ChartTextStrings {

    override val infoTitle = "How to read this chart"
    override val sectionAnatomy = "What is drawn"
    override val sectionReferences = "What it is compared with"
    override val sectionNumbers = "Where the numbers come from"
    override val sectionGestures = "Gestures"

    override val anatomyAxis = "The value axis is fitted to the observed data, not tied to " +
        "zero: it shows the levels the instrument actually was at. The alarm threshold enters " +
        "the frame only when the data comes close to it; a distant threshold is shown by a " +
        "pointer at the top edge."
    override val anatomyMedianLine = "The line shows the typical level over this stretch of " +
        "time. It is built from the median: half of the measurements were above it, half below."
    override val anatomyMedianDetail = "The big number on the Monitor and the last point of " +
        "the chart answer different questions and need not agree: the number is the LATEST " +
        "reading from the instrument, the point is the median of its interval. " +
        "The median is the 50th percentile (P50). It reacts to " +
        "single spikes less than the mean does, so the line describes the level rather than " +
        "its outliers."
    override val anatomyEnvelopes = "The shaded areas show how much the readings varied: the " +
        "inner one holds the middle 50 % of the measurements, the outer one 80 %. This is the " +
        "spread that was observed, not the instrument's uncertainty."
    override val anatomyEnvelopesDetail = "The edges of the areas are P25–P75 and P10–P90. " +
        "They describe the spread of the observations, not a confidence interval and not the " +
        "uncertainty of the measurement: in metrology that term has its own strict meaning."
    override val anatomyHardnessRatio = "The ratio is taken sample by sample, not by dividing " +
        "the interval's means: the mean of ratios and the ratio of means are different numbers."
    override val anatomyGaps = "Gaps are not joined by the line: where the instrument was " +
        "silent there is a flat fill, not a straight line from point to point."

    override val referenceProfileBand = "The grey band with the dashed edge is the historical " +
        "P10–P90 range of this profile. It is a statistic of the place, not a regulatory limit " +
        "— about 80 % of the past measurements lie inside it, and that is all it means."
    override val referenceProfileBandMissing = "The historical range of the profile has not " +
        "been collected yet, so there is no grey band."
    override val referenceAlarmLine = "The red dashed line is your L1 alarm threshold. If it " +
        "is above the frame, a pointer at the top edge carries its value."
    override val referenceAlarmAbsent = "This chart has no alarm threshold: the threshold is " +
        "set in dose units."
    override val referenceMarkers = "▲ above the field is an interval maximum that came out " +
        "above the named value: filled — above the L1 threshold, outlined — above the " +
        "profile's P90. This is a comparison, not a sign of an anomaly: by definition about " +
        "10 % of the usable historical measurements of this place lie above P90. Tap the " +
        "triangle to open a card with both numbers and the exact time."
    override val referenceEpisodes = "The vertical bands are episodes from the event log; the " +
        "caption names what exactly they are above, and their computed duration."

    override val quantilesExact = "For this period the chart is built directly from the stored " +
        "measurements."
    override val quantilesExactDetail = "The quantiles are exact: computed over every raw " +
        "sample of the window."
    override fun quantilesSketch(rankError: String) = "The period is long, so the app uses the " +
        "compressed history — the statistical bounds are approximate."
    override fun quantilesSketchDetail(rankError: String) = "The quantiles are assembled from " +
        "hourly compressed digests of the distribution. Rank error ≈ $rankError."
    override val quantilesSubBucketMeans = "For this period the statistics are still " +
        "approximate: the app is processing the accumulated history."
    override val quantilesSubBucketMeansDetail = "While the compressed digests are being " +
        "built, the quantiles are estimated from the means of sub-intervals — an estimate " +
        "with no proven bound on its accuracy. It will be replaced by the exact one as soon " +
        "as the pre-aggregation reaches these hours."
    override val sampleCountNote = "n is how many readings of the instrument went into the " +
        "statistics of this window."
    override val sampleCountDetail = "The instrument writes about once a second, so n is close " +
        "to the seconds of measurement; with gaps it is smaller — the actual coverage of the " +
        "window is captioned separately."
    override val showDetails = "More detail"
    override val hideDetails = "Hide the details"
    override val smoothChip = "smoothing"
    override val detailNote =
        "Detailed view: the line follows the measurements themselves. Over a long " +
            "window it follows the extremes of each interval, so peaks and dips stay " +
            "visible."
    override val smoothedNote =
        "Smoothed view: the line is the median of an interval and the fills are the " +
            "spread of measurements inside it (P25–P75 and P10–P90). The numbers of " +
            "the window do not depend on the view."
    override val logScaleNote = "The scale is logarithmic: equal distances mean equal ratios, " +
        "not equal differences."
    override fun logScaleDroppedNote(buckets: Int) = "The scale is logarithmic: $buckets " +
        "intervals containing zero are not shown. Zero does not exist on such a scale, and " +
        "drawing it in place of the smallest value would be untrue."

    override val gestureZoomPan = "Pinch changes the time scale, dragging moves the window."
    override val gestureCursor = "A long press places the cursor: it shows the interval, its " +
        "median, the spread, min/max with their times and the comparison with the profile."
    override val gestureMarkerTap = "Tapping a triangle above the field opens the same card " +
        "straight on that interval."
    override val gestureDoubleTap = "A double tap returns the selected window to the live edge."
    override val gestureDoubleTapRange =
        "A double tap returns the selected window to the end of the session."
    override val historicalRangeNote = "A stored range is shown: the measurements of this " +
        "session are already written, new ones do not arrive here."

    override val loadingLog = "reading the log…"
    override val emptyWindow = "no measurements in this window"
    override fun windowLabel(duration: String) = "window $duration"
    override fun windowSheetTitle(duration: String) = "Window $duration"
    override fun windowStatsLine(
        p10: String,
        median: String,
        p90: String,
        samples: String,
        duration: String,
    ) = "P10 $p10 · median $median · P90 $p90 · n $samples · $duration"
    override val moreDetails = "details ▴"
    override val eventsChip = "events"
    override fun cursorEvents(count: Int) =
        if (count == 1) "$count event" else "$count events"
    override val cursorEventsNote = "short deviations"
    override val distribution = "Distribution"
    override val windowStatistics = "Window statistics"

    override val nowChip = "⌖ now"
    override val sessionChip = "⌖ session"
    override fun sessionRangeLabel(range: String, duration: String) = "$range · $duration"
    override val pausedChip = "paused"
    override val logChip = "log"

    override val median = "median"
    override val min = "min"
    override val max = "max"
    override val samplesLabel = "samples"
    override val spreadDefinitions = "SD — the observed spread of the values · " +
        "MAD = median(|xᵢ − median|), a robust spread · IQR = P75 − P25"

    override fun quantileMethodLine(label: String) = "quantile method: $label"
    override fun preAggregationProgress(percent: Int, done: Int, total: Int) =
        "history pre-aggregation: $percent % ($done of $total h)"
    override val computing = "computing…"
    override val compareWithRaw = "check against raw"

    override fun exactPathRefused(maxRows: Int) =
        "the exact path refused: the window holds more than $maxRows samples"
    override val noRawSamplesInHours = "there are no raw samples in these hours"
    override fun diagnosticsHeader(samples: String) = "exact against sketch · n $samples"
    override fun sketchCountMismatch(sketchCount: String) =
        " (the sketch knows $sketchCount — different data is being compared)"
    override fun rankErrorSuffix(percent: String) = " (rank $percent)"
    override fun maxRankError(percent: String) = "maximum rank error $percent"

    override val cursorNoData = "no data"
    override val cursorNoDataDetail = "the instrument was not writing at this moment"
    override fun cursorMaxAgainstAlarm(max: String, level: String) = "max $max · L1 $level"
    override fun cursorMaxAgainstProfileP90(max: String, level: String) =
        "max $max · profile P90 $level"
    override val bucketQuantilesSketch = "interval quantiles come from hourly sketches; " +
        "min/max and their times are exact"
    override val bucketQuantilesCoarse = "interval quantiles are a rough estimate, " +
        "pre-aggregation is still being built"
    override val historicalProfile = "usual range of this place"
}

val ChartTextCatalogue = AreaCatalogue(ru = ChartTextRu, en = ChartTextEn)

/** Все строки области — для проверок, действующих на каждый язык. */
fun ChartTextStrings.allTexts(): List<String> = listOf(
    infoTitle, sectionAnatomy, sectionReferences, sectionNumbers, sectionGestures,
    anatomyAxis, anatomyMedianLine, anatomyMedianDetail,
    anatomyEnvelopes, anatomyEnvelopesDetail, anatomyHardnessRatio, anatomyGaps,
    referenceProfileBand, referenceProfileBandMissing, referenceAlarmLine,
    referenceAlarmAbsent, referenceMarkers, referenceEpisodes,
    quantilesExact, quantilesExactDetail,
    quantilesSketch("≈ 1,8 %"), quantilesSketchDetail("≈ 1,8 %"),
    quantilesSubBucketMeans, quantilesSubBucketMeansDetail,
    sampleCountNote, sampleCountDetail, showDetails, hideDetails,
    smoothChip, detailNote, smoothedNote,
    logScaleNote, logScaleDroppedNote(3),
    gestureZoomPan, gestureCursor, gestureMarkerTap, gestureDoubleTap, gestureDoubleTapRange,
    historicalRangeNote, sessionChip, sessionRangeLabel("12 авг 14:03–16:18", "2 ч 15 мин"),
    loadingLog, emptyWindow, windowLabel("6ч"), windowSheetTitle("6ч"),
    windowStatsLine("0,09", "0,11", "0,14", "21 600", "6ч"),
    moreDetails, eventsChip, cursorEvents(1), cursorEvents(3), cursorEvents(11),
    cursorEventsNote, distribution, windowStatistics,
    nowChip, pausedChip, logChip,
    median, min, max, samplesLabel, spreadDefinitions,
    quantileMethodLine("KLL k=128"), preAggregationProgress(40, 12, 30),
    computing, compareWithRaw,
    exactPathRefused(200_000), noRawSamplesInHours, diagnosticsHeader("21 600"),
    sketchCountMismatch("21 000"), rankErrorSuffix("0,12 %"), maxRankError("0,31 %"),
    cursorNoData, cursorNoDataDetail,
    cursorMaxAgainstAlarm("0,42", "0,30"), cursorMaxAgainstProfileP90("0,42", "0,14"),
    bucketQuantilesSketch, bucketQuantilesCoarse, historicalProfile,
)
