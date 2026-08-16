package app.radiacode.ui.text

/**
 * Каталог области «A/B эксперимент» (спец §9, §16, §24).
 *
 * Лестница вердиктов ЗАКРЫТА и переносится в другой язык целиком:
 * `согласуется` / `изменилось` / `сильное свидетельство изменения` —
 * `consistent` / `changed` / `strong evidence of change`, и ничего больше.
 * «Процента похожести» нет ни на одном языке: у него нет определённого
 * статистического смысла, пока метрика не проверена на данных RC-110.
 *
 * Вместе с текстом переносится и оговорка: функция экспериментальная,
 * математика проверена на синтетике и НЕ валидирована на реальных
 * измерениях прибора. Английский обязан отказываться от утверждения ровно
 * там же, где отказывается русский.
 *
 * Формулы (net = G − B·(t_G/t_B), σ по IAEA, χ²-подобный z, отношение
 * правдоподобия) от языка не зависят и подставляются как есть.
 */
interface ExperimentStrings {

    // --- навигация и список ---

    val back: String
    val listTitle: String
    val detailTitle: String
    val newExperiment: String
    val emptyList: String
    val emptyHint: String
    val loading: String

    // --- геометрия ---

    val geometry: String
    val geometryPrompt: String
    val geometryPlaceholder: String
    val geometryKeptNote: String

    /** «геометрия не описана» — в списке, где ясно, о чём речь. */
    val geometryUndescribedInList: String

    /** «не описана» — под заголовком «Геометрия». */
    val geometryUndescribed: String

    /** «она не описана» — внутри просьбы повторить геометрию прогона A. */
    val geometryUndescribedInline: String

    // --- создание ---

    val scenario: String
    val note: String
    val notePlaceholder: String
    val create: String
    val cancel: String

    // --- прогоны ---

    val runs: String
    val runsEmpty: String
    val holdGeometryNote: String
    val stopRun: String
    val distancePlaceholder: String
    val shieldingPlaceholder: String
    val notConnected: String
    val durationManual: String
    val runUnfinishedShort: String
    val deleteRun: String
    val spectrumMissing: String

    /** Прогон записан, спектра в нём нет: сравнение останется только по дозе. */
    val runWithoutSpectrum: String

    /** Предупреждения сравнения: что именно не сравнивалось и почему. */
    val warnDoseMissing: String
    val warnSpectrumMissing: String
    fun warnCalibrationApart(deltaKeV: String): String
    val warnChannelCount: String
    val totalCountLabel: String

    fun runInProgress(elapsed: String, planned: String?): String
    fun runHeadline(letter: String, role: String): String
    fun repeatGeometry(letter: String, geometry: String): String
    fun startRun(letter: String): String
    fun runUnfinished(label: String): String
    fun finishRun(label: String): String
    fun recording(elapsed: String, planned: String?): String
    fun spectrumCounts(counts: String): String
    fun doseMean(value: String, samples: Int): String
    fun distanceRow(distance: String): String
    fun shieldingRow(material: String): String

    // --- сравнение ---

    val conclusion: String
    val verdictScopeNote: String
    val columnMetric: String
    val columnVerdict: String
    val rowDose: String
    val rowTotalCounts: String
    val rowSpectrum: String
    val doseAuxNote: String
    val needTwoRuns: String

    /** Формула нетто и σ по IAEA — числа приходят готовыми. */
    fun netLine(net: String, sigma: String): String

    // --- серия по расстоянию ---

    val distanceSeries: String
    val columnMeasured: String
    val columnInverseSquare: String
    val referencePoint: String

    // --- отчёт и удаление ---

    val reportToFile: String
    val delete: String
    val deleteTitle: String
    val deleteBody: String
    val reportSaved: String
    val reportFailed: String

    // --- вердикты (спец §8) ---

    val verdictConsistent: String
    val verdictChanged: String
    val verdictStrongEvidence: String

    fun headlineConsistent(a: String, b: String): String
    fun headlineChanged(a: String, b: String): String
    fun headlineStrongEvidence(a: String, b: String): String

    // --- статистика ---

    val methodPoisson: String
    val methodChiSquare: String

    fun methodExplanationPoisson(minCounts: Int): String
    fun methodExplanationChiSquare(minCounts: Int): String

    // --- сценарии ---

    /**
     * Сценарии: три ответа на вопрос «что с чем сравниваем».
     *
     * Прежний список из четырёх пунктов смешивал два разных вопроса — что с
     * чем («фон и объект», «место и место») и что меняется между прогонами
     * («расстояние», «экранирование»). Вопросов два, поэтому и выборов два.
     */
    val scenarioObject: String
    val scenarioObjectHint: String
    val scenarioPlaces: String
    val scenarioPlacesHint: String
    val scenarioCustom: String
    val scenarioCustomHint: String
    val templateDistance: String
    val templateShielding: String
    val templateOther: String

    // --- условия опыта, разложенные на повторяемые части ---
    val conditionsTitle: String
    val conditionDistance: String
    val conditionPlacement: String
    val conditionOrientation: String
    val conditionDuration: String
    val conditionsUnset: String
    val placementTable: String
    val placementHand: String
    val placementTripod: String
    val placementFloor: String
    val orientationScreenUp: String
    val orientationScreenToObject: String
    val orientationBackToObject: String
    val orientationEdge: String

    /** «Повторите условия A · 5 см · прибор на столе · 10 мин». */
    fun repeatConditions(letter: String, summary: String): String

    val kindBackgroundVsObject: String
    val kindPlaceVsPlace: String
    val kindDistance: String
    val kindShielding: String
    val hintBackgroundVsObject: String
    val hintPlaceVsPlace: String
    val hintDistance: String
    val hintShielding: String
    val roleObject: String
    val roleBackground: String
    /** Прогон с образцом продукта. */
    val roleSample: String
    val kindFood: String
    val hintFood: String
    val roleWithoutMaterial: String
    val roleWithMaterial: String

    fun rolePlace(letter: String): String
    fun rolePoint(letter: String): String
    fun roleRun(letter: String): String

    // --- обязательные предупреждения (спец §16, §24) ---

    val distanceWarning: String
    val shieldingWarning: String
    val experimentalBadge: String

    /**
     * Одна фраза о том, что здесь делают. Абзац про синтетическую проверку и
     * отсутствие валидации на приборе уехал под «i»: он объясняет ЗРЕЛОСТЬ
     * функции, а не то, как ею пользоваться, и стоял на месте первого шага.
     */
    val experimentalLead: String
    val experimentalNote: String

    // --- единицы ---

    val countsPerSecond: String

    fun seconds(value: Long): String
    fun minutes(value: Long): String
    fun minutesSeconds(minutes: Long, seconds: Long): String
    fun centimeters(value: Int): String
    fun meters(value: String): String
}

object ExperimentRu : ExperimentStrings {

    override val back = "← Назад"
    override val listTitle = "A/B эксперимент"
    override val detailTitle = "Эксперимент"
    override val newExperiment = "Новый эксперимент"
    override val emptyList = "экспериментов пока нет"
    override val emptyHint =
        "Эксперимент — два измерения в одинаковой геометрии: например, объект у прибора " +
            "и тот же прибор без объекта. Приложение сравнит их по счёту, энергетическим " +
            "окнам и спектру."
    override val loading = "читаю эксперимент…"

    override val geometry = "Геометрия"
    override val geometryPrompt =
        "Опишите геометрию один раз: где лежит объект, на каком расстоянии и как " +
            "повёрнут прибор. Это описание покажется при каждом следующем прогоне — " +
            "повторить его точно и есть смысл A/B."
    override val geometryPlaceholder = "например: образец на столе, прибор экраном вверх, 5 см"
    override val geometryKeptNote =
        "Геометрия сохраняется вместе с экспериментом и показывается перед каждым " +
            "прогоном — без неё сравнение теряет смысл."
    override val geometryUndescribedInList = "геометрия не описана"
    override val geometryUndescribed = "не описана"
    override val geometryUndescribedInline = "она не описана"

    override val scenario = "Сценарий"
    override val note = "Заметка"
    override val notePlaceholder = "что проверяем"
    override val create = "Создать"
    override val cancel = "Отмена"

    override val runs = "Прогоны"
    override val runsEmpty = "прогонов пока нет — начните первый"
    // «Новой нормой» эксперимент не становится буквально: интервал не входит
    // в статистику обычного фона. Слово «норма» на экране не пишем.
    override val holdGeometryNote =
        "Держите геометрию неизменной. Интервал исключён из статистики обычного фона — " +
            "эксперимент не должен становиться тем, с чем приложение сравнивает потом."
    override val stopRun = "Остановить прогон"
    override val distancePlaceholder = "расстояние, см"
    override val shieldingPlaceholder = "материал между объектом и прибором (пусто = без него)"
    override val notConnected = "нет соединения с прибором — прогон записывать нечем"
    override val durationManual = "вручную"
    override val runUnfinishedShort = "не завершён"
    override val deleteRun = "удалить"
    override val spectrumMissing = "спектр не записан"
    override val runWithoutSpectrum =
        "спектр прогона не записан — сравнение будет только по мощности дозы"

    override val warnDoseMissing =
        "мощность дозы не сравнивалась: в одном из прогонов нет измерений (прибор был отключён?)"
    override val warnSpectrumMissing = "спектр не сравнивался: в одном из прогонов он не записан"

    override fun warnCalibrationApart(deltaKeV: String) =
        "калибровки прогонов расходятся на $deltaKeV кэВ — поканальное сравнение и окна не " +
            "считались, сравнивается только полный счёт"

    override val warnChannelCount =
        "у прогонов разное число каналов — поканальное сравнение невозможно"
    override val totalCountLabel = "полный счёт"

    override fun runInProgress(elapsed: String, planned: String?) =
        "идёт прогон · $elapsed" + if (planned != null) " из $planned" else ""

    override fun runHeadline(letter: String, role: String) = "Прогон $letter · $role"

    override fun repeatGeometry(letter: String, geometry: String) =
        "Повторите геометрию прогона $letter: $geometry"

    override fun startRun(letter: String) = "Начать прогон $letter"

    override fun runUnfinished(label: String) =
        "прогон $label остался незавершённым (приложение закрывали?) — завершите или удалите его"

    override fun finishRun(label: String) = "Завершить прогон $label"

    override fun recording(elapsed: String, planned: String?) =
        "запись $elapsed" + if (planned != null) "/$planned" else ""

    override fun spectrumCounts(counts: String) = "спектр $counts имп"

    override fun doseMean(value: String, samples: Int) = "доза ср $value мкЗв/ч, n=$samples"

    override fun distanceRow(distance: String) = "расстояние $distance"

    override fun shieldingRow(material: String) = "материал: $material"

    override val conclusion = "Вывод"
    override val verdictScopeNote =
        "Вердикт относится к различию двух измерений, а не к опасности и не к тому, " +
            "что именно найдено (спец §2)."
    override val columnMetric = "показатель"
    override val columnVerdict = "вывод"
    override val rowDose = "доза, мкЗв/ч"
    override val rowTotalCounts = "полный счёт, имп/с"
    override val rowSpectrum = "полный спектр"
    override val doseAuxNote =
        "строка дозы — вспомогательная: секундные показания прибора коррелированы, " +
            "её неопределённость — оценка снизу"
    override val needTwoRuns = "для сравнения нужно два завершённых прогона"

    override fun netLine(net: String, sigma: String) =
        "нетто = G − B·(t_G/t_B) = $net ±$sigma имп (σ по IAEA: √(G + B·(t_G/t_B)²))"

    override val distanceSeries = "Серия по расстоянию"
    override val columnMeasured = "измерено, имп/с"
    override val columnInverseSquare = "1/r² от первой"
    override val referencePoint = "опора"

    override val reportToFile = "Отчёт в файл"
    override val delete = "Удалить"
    override val deleteTitle = "Удалить эксперимент?"
    override val deleteBody =
        "Прогоны и их результаты удалятся вместе с ним. Сырые измерения и спектры " +
            "прогонов останутся в базе."
    override val reportSaved = "отчёт сохранён"
    override val reportFailed = "отчёт не записался — попробуйте другую папку"

    override val verdictConsistent = "различий не видно"
    override val verdictChanged = "есть различие"
    override val verdictStrongEvidence = "сильные свидетельства различия"

    override fun headlineConsistent(a: String, b: String) =
        "Измерения $a и $b согласуются между собой"

    override fun headlineChanged(a: String, b: String) =
        "Между $a и $b есть статистическое различие"

    override fun headlineStrongEvidence(a: String, b: String) =
        "Между $a и $b сильные свидетельства различия"

    override val methodPoisson = "Пуассон, отношение правдоподобия"
    override val methodChiSquare = "χ²-подобный, z = нетто/σ"

    override fun methodExplanationPoisson(minCounts: Int) =
        "мало импульсов (< $minCounts в прогоне) — использован пуассоновский критерий " +
            "отношения правдоподобия"

    override fun methodExplanationChiSquare(minCounts: Int) =
        "импульсов достаточно (≥ $minCounts в каждом прогоне) — использован χ²-подобный " +
            "критерий z = нетто/σ"

    override val scenarioObject = "Объект"
    override val scenarioObjectHint = "A — фон без объекта, B — тот же замер с объектом."
    override val scenarioPlaces = "Два места"
    override val scenarioPlacesHint = "A — первое место, B — второе. Прибор и время одинаковы."
    override val scenarioCustom = "Свои условия"
    override val scenarioCustomHint = "Что такое A и B, называете вы. Между прогонами меняется одно."
    override val templateDistance = "расстояние"
    override val templateShielding = "экранирование"
    override val templateOther = "другое"

    override val conditionsTitle = "Условия"
    override val conditionDistance = "Расстояние"
    override val conditionPlacement = "Положение прибора"
    override val conditionOrientation = "Ориентация"
    override val conditionDuration = "Длительность прогона"
    override val conditionsUnset = "условия не заданы"
    override val placementTable = "на столе"
    override val placementHand = "в руке"
    override val placementTripod = "на штативе"
    override val placementFloor = "на полу"
    override val orientationScreenUp = "экраном вверх"
    override val orientationScreenToObject = "экраном к объекту"
    override val orientationBackToObject = "тыльной стороной к объекту"
    override val orientationEdge = "боком"

    override fun repeatConditions(letter: String, summary: String) =
        "Повторите условия $letter · $summary"

    override val kindBackgroundVsObject = "Фон и объект"
    override val kindPlaceVsPlace = "Место и место"
    override val kindDistance = "Расстояние"
    override val kindShielding = "Экранирование"
    override val hintBackgroundVsObject =
        "A — объект у детектора, B — тот же детектор без объекта. Геометрия должна быть " +
            "одинаковой, иначе сравнивается не объект, а положение прибора."
    override val hintPlaceVsPlace =
        "A и B — два места. Сравниваются измерения как они есть; вывод относится к этим " +
            "двум измерениям, а не к местам вообще."
    override val hintDistance =
        "Серия прогонов на известных расстояниях от объекта. Плюс, по возможности, прогон " +
            "фона без объекта — без него дальние точки будут в основном фоном."
    override val hintShielding =
        "A — без материала, B — с материалом, в остальном всё то же самое. Универсальных " +
            "коэффициентов ослабления из такого опыта не выводится."
    override val roleObject = "объект"
    override val roleBackground = "фон"
    override val roleSample = "продукт"
    override val kindFood = "Продукт"
    override val hintFood = "фон и образец в одной геометрии"
    override val roleWithoutMaterial = "без материала"
    override val roleWithMaterial = "с материалом"

    override fun rolePlace(letter: String) = "место $letter"

    override fun rolePoint(letter: String) = "точка $letter"

    override fun roleRun(letter: String) = "прогон $letter"

    override val distanceWarning =
        "Сравнение с идеализированной зависимостью 1/r² — только ориентир. Реальный " +
            "источник не точечный, излучение рассеивается на воздухе и окружении, а фон " +
            "с расстоянием не убывает вовсе. Совпадение с кривой не доказывает геометрию, " +
            "расхождение не означает ошибку измерения."
    override val shieldingWarning =
        "Из этого опыта не выводятся коэффициенты ослабления материала: домашняя " +
            "геометрия неконтролируема, спектр источника неизвестен, а рассеянное " +
            "излучение приходит в детектор в обход материала."
    override val experimentalBadge = "Экспериментально"
    override val experimentalLead = "Сравните два измерения в одинаковых условиях."
    override val experimentalNote =
        "Функция экспериментальная: статистика реализована и проверена на синтетике, " +
            "но пока не валидирована на реальных измерениях прибора. Вердикт говорит о " +
            "различии между двумя измерениями, а не об опасности и не о том, что найдено."

    override val countsPerSecond = "имп/с"

    override fun seconds(value: Long) = "$value с"

    override fun minutes(value: Long) = "$value мин"

    override fun minutesSeconds(minutes: Long, seconds: Long) = "$minutes мин $seconds с"

    override fun centimeters(value: Int) = "$value см"

    override fun meters(value: String) = "$value м"
}

/**
 * Английский каталог области.
 *
 * Переведено по смыслу: где русский текст отказывается утверждать («вердикт
 * относится к различию двух измерений, а не к опасности»), английский
 * отказывается ровно так же. Лестница вердиктов и оговорка об отсутствии
 * валидации на приборе перенесены целиком.
 */
object ExperimentEn : ExperimentStrings {

    override val back = "← Back"
    override val listTitle = "A/B experiment"
    override val detailTitle = "Experiment"
    override val newExperiment = "New experiment"
    override val emptyList = "no experiments yet"
    override val emptyHint =
        "An experiment is two measurements in the same geometry: an object next to the " +
            "instrument and the same instrument without it, for example. The app compares " +
            "them by count, energy windows and spectrum."
    override val loading = "reading the experiment…"

    override val geometry = "Geometry"
    override val geometryPrompt =
        "Describe the geometry once: where the object lies, at what distance and how the " +
            "instrument is turned. This description is shown before every following run — " +
            "repeating it exactly is what A/B is for."
    override val geometryPlaceholder =
        "for example: sample on the table, instrument screen up, 5 cm"
    override val geometryKeptNote =
        "The geometry is stored with the experiment and shown before every run — without " +
            "it the comparison means nothing."
    override val geometryUndescribedInList = "geometry not described"
    override val geometryUndescribed = "not described"
    override val geometryUndescribedInline = "it was not described"

    override val scenario = "Scenario"
    override val note = "Note"
    override val notePlaceholder = "what is being checked"
    override val create = "Create"
    override val cancel = "Cancel"

    override val runs = "Runs"
    override val runsEmpty = "no runs yet — start the first one"
    override val holdGeometryNote =
        "Keep the geometry unchanged. This interval is excluded from the usual background " +
            "statistics — an experiment must not become what the app compares against later."
    override val stopRun = "Stop the run"
    override val distancePlaceholder = "distance, cm"
    override val shieldingPlaceholder =
        "material between the object and the instrument (empty = none)"
    override val notConnected = "no link to the instrument — there is nothing to record a run with"
    override val durationManual = "manual"
    override val runUnfinishedShort = "unfinished"
    override val deleteRun = "delete"
    override val spectrumMissing = "spectrum not recorded"
    override val runWithoutSpectrum =
        "the run's spectrum was not recorded — the comparison will be by dose rate only"

    override val warnDoseMissing =
        "dose rate was not compared: one of the runs has no measurements (was the instrument " +
            "disconnected?)"
    override val warnSpectrumMissing =
        "the spectrum was not compared: one of the runs did not record one"

    override fun warnCalibrationApart(deltaKeV: String) =
        "the runs' calibrations differ by $deltaKeV keV — the per-channel comparison and the " +
            "windows were not computed, only the total count is compared"

    override val warnChannelCount =
        "the runs have a different number of channels — a per-channel comparison is not possible"
    override val totalCountLabel = "total count"

    override fun runInProgress(elapsed: String, planned: String?) =
        "run in progress · $elapsed" + if (planned != null) " of $planned" else ""

    override fun runHeadline(letter: String, role: String) = "Run $letter · $role"

    override fun repeatGeometry(letter: String, geometry: String) =
        "Repeat the geometry of run $letter: $geometry"

    override fun startRun(letter: String) = "Start run $letter"

    override fun runUnfinished(label: String) =
        "run $label was left unfinished (was the app closed?) — finish it or delete it"

    override fun finishRun(label: String) = "Finish run $label"

    override fun recording(elapsed: String, planned: String?) =
        "recording $elapsed" + if (planned != null) "/$planned" else ""

    override fun spectrumCounts(counts: String) = "spectrum $counts counts"

    override fun doseMean(value: String, samples: Int) = "dose avg $value µSv/h, n=$samples"

    override fun distanceRow(distance: String) = "distance $distance"

    override fun shieldingRow(material: String) = "material: $material"

    override val conclusion = "Conclusion"
    override val verdictScopeNote =
        "The verdict is about the difference between the two measurements — not about harm " +
            "and not about what exactly was found (spec §2)."
    override val columnMetric = "metric"
    override val columnVerdict = "conclusion"
    override val rowDose = "dose, µSv/h"
    override val rowTotalCounts = "total count, counts/s"
    override val rowSpectrum = "full spectrum"
    override val doseAuxNote =
        "the dose row is auxiliary: the instrument's one-second readings are correlated, " +
            "so its uncertainty is a lower bound"
    override val needTwoRuns = "a comparison needs two finished runs"

    override fun netLine(net: String, sigma: String) =
        "net = G − B·(t_G/t_B) = $net ±$sigma counts (σ per IAEA: √(G + B·(t_G/t_B)²))"

    override val distanceSeries = "Distance series"
    override val columnMeasured = "measured, counts/s"
    override val columnInverseSquare = "1/r² from the first"
    override val referencePoint = "reference"

    override val reportToFile = "Report to file"
    override val delete = "Delete"
    override val deleteTitle = "Delete the experiment?"
    override val deleteBody =
        "The runs and their results are deleted with it. The raw measurements and the run " +
            "spectra stay in the database."
    override val reportSaved = "report saved"
    override val reportFailed = "the report was not written — try another folder"

    // Лестница §8 целиком: критерий проверяет ОТЛИЧИЕ, поэтому «consistent»
    // говорит о несостоявшемся различии, а не о равенстве измерений.
    override val verdictConsistent = "no difference singled out"
    override val verdictChanged = "there is a difference"
    override val verdictStrongEvidence = "strong evidence of a difference"

    override fun headlineConsistent(a: String, b: String) =
        "Measurements $a and $b are consistent with each other"

    override fun headlineChanged(a: String, b: String) =
        "There is a statistical difference between $a and $b"

    override fun headlineStrongEvidence(a: String, b: String) =
        "Strong evidence of a difference between $a and $b"

    override val methodPoisson = "Poisson, likelihood ratio"
    override val methodChiSquare = "χ²-like, z = net/σ"

    override fun methodExplanationPoisson(minCounts: Int) =
        "few counts (< $minCounts in a run) — the Poisson likelihood-ratio test was used"

    override fun methodExplanationChiSquare(minCounts: Int) =
        "counts are sufficient (≥ $minCounts in each run) — the χ²-like test z = net/σ was used"

    override val scenarioObject = "Object"
    override val scenarioObjectHint = "A is the background without the object, B is the same measurement with it."
    override val scenarioPlaces = "Two places"
    override val scenarioPlacesHint = "A is the first place, B the second. Same instrument, same duration."
    override val scenarioCustom = "Your own conditions"
    override val scenarioCustomHint = "You name what A and B are. One thing changes between runs."
    override val templateDistance = "distance"
    override val templateShielding = "shielding"
    override val templateOther = "something else"

    override val conditionsTitle = "Conditions"
    override val conditionDistance = "Distance"
    override val conditionPlacement = "Instrument placement"
    override val conditionOrientation = "Orientation"
    override val conditionDuration = "Run duration"
    override val conditionsUnset = "conditions not described"
    override val placementTable = "on a table"
    override val placementHand = "in the hand"
    override val placementTripod = "on a tripod"
    override val placementFloor = "on the floor"
    override val orientationScreenUp = "screen up"
    override val orientationScreenToObject = "screen towards the object"
    override val orientationBackToObject = "back towards the object"
    override val orientationEdge = "on its edge"

    override fun repeatConditions(letter: String, summary: String) =
        "Repeat the conditions of $letter · $summary"

    override val kindBackgroundVsObject = "Background and object"
    override val kindPlaceVsPlace = "Place and place"
    override val kindDistance = "Distance"
    override val kindShielding = "Shielding"
    override val hintBackgroundVsObject =
        "A — the object next to the detector, B — the same detector without it. The geometry " +
            "must be identical, otherwise what is compared is the position of the instrument, " +
            "not the object."
    override val hintPlaceVsPlace =
        "A and B are two places. The measurements are compared as they are; the conclusion " +
            "is about these two measurements, not about the places in general."
    override val hintDistance =
        "A series of runs at known distances from the object. Plus, where possible, a " +
            "background run without the object — without it the far points are mostly " +
            "background."
    override val hintShielding =
        "A — without the material, B — with it, everything else the same. Universal " +
            "attenuation coefficients do not follow from such an experiment."
    override val roleObject = "object"
    override val roleBackground = "background"
    override val roleSample = "sample"
    override val kindFood = "Product"
    override val hintFood = "background and sample in one geometry"
    override val roleWithoutMaterial = "without the material"
    override val roleWithMaterial = "with the material"

    override fun rolePlace(letter: String) = "place $letter"

    override fun rolePoint(letter: String) = "point $letter"

    override fun roleRun(letter: String) = "run $letter"

    override val distanceWarning =
        "The comparison with the idealised 1/r² law is a guide only. A real source is not a " +
            "point, radiation scatters on the air and the surroundings, and the background " +
            "does not fall off with distance at all. Agreement with the curve does not prove " +
            "the geometry, and disagreement does not mean a measurement error."
    override val shieldingWarning =
        "Attenuation coefficients of the material do not follow from this experiment: the " +
            "home geometry is uncontrolled, the source spectrum is unknown, and scattered " +
            "radiation reaches the detector around the material."
    override val experimentalBadge = "Experimental"
    override val experimentalLead = "Compare two measurements taken in the same conditions."
    override val experimentalNote =
        "This feature is experimental: the statistics are implemented and checked on " +
            "synthetic data, but not yet validated on real instrument measurements. A verdict " +
            "speaks about the difference between two measurements, not about harm and not " +
            "about what was found."

    override val countsPerSecond = "counts/s"

    override fun seconds(value: Long) = "$value s"

    override fun minutes(value: Long) = "$value min"

    override fun minutesSeconds(minutes: Long, seconds: Long) = "$minutes min $seconds s"

    override fun centimeters(value: Int) = "$value cm"

    override fun meters(value: String) = "$value m"
}

val ExperimentCatalogue = AreaCatalogue(ru = ExperimentRu, en = ExperimentEn)

/**
 * Все тексты каталога — для проверок, действующих на каждый язык области.
 * Перечисление РУЧНОЕ: рефлексии в тестовом classpath нет, а забытая строка
 * означала бы непроверенный текст.
 */
fun ExperimentStrings.allTexts(): List<String> = listOf(
    back, listTitle, detailTitle, newExperiment, emptyList, emptyHint, loading,
    geometry, geometryPrompt, geometryPlaceholder, geometryKeptNote,
    geometryUndescribedInList, geometryUndescribed, geometryUndescribedInline,
    scenario, note, notePlaceholder, create, cancel,
    runs, runsEmpty, holdGeometryNote, stopRun, distancePlaceholder, shieldingPlaceholder,
    notConnected, durationManual, runUnfinishedShort, deleteRun, spectrumMissing,
    runWithoutSpectrum, warnDoseMissing, warnSpectrumMissing, warnCalibrationApart("5,0"),
    warnChannelCount, totalCountLabel,
    runInProgress("2 мин", null), runInProgress("2 мин", "5 мин"),
    runHeadline("A", roleObject), repeatGeometry("A", geometryUndescribedInline),
    startRun("B"), runUnfinished("B"), finishRun("B"),
    recording("30 с", null), recording("30 с", "5 мин"),
    spectrumCounts("1234"), doseMean("0,15", 42), distanceRow("10"), shieldingRow("Pb"),
    conclusion, verdictScopeNote, columnMetric, columnVerdict,
    rowDose, rowTotalCounts, rowSpectrum, doseAuxNote, needTwoRuns,
    netLine("+12", "4"),
    distanceSeries, columnMeasured, columnInverseSquare, referencePoint,
    reportToFile, delete, deleteTitle, deleteBody, reportSaved, reportFailed,
    verdictConsistent, verdictChanged, verdictStrongEvidence,
    headlineConsistent("A", "B"), headlineChanged("A", "B"), headlineStrongEvidence("A", "B"),
    methodPoisson, methodChiSquare,
    methodExplanationPoisson(25), methodExplanationChiSquare(25),
    scenarioObject, scenarioObjectHint, scenarioPlaces, scenarioPlacesHint,
    scenarioCustom, scenarioCustomHint, templateDistance, templateShielding, templateOther,
    conditionsTitle, conditionDistance, conditionPlacement, conditionOrientation,
    conditionDuration, conditionsUnset,
    placementTable, placementHand, placementTripod, placementFloor,
    orientationScreenUp, orientationScreenToObject, orientationBackToObject, orientationEdge,
    repeatConditions("A", "5 см · на столе · 10 мин"),
    kindBackgroundVsObject, kindPlaceVsPlace, kindDistance, kindShielding,
    hintBackgroundVsObject, hintPlaceVsPlace, hintDistance, hintShielding,
    roleObject, roleBackground, roleWithoutMaterial, roleWithMaterial,
    rolePlace("A"), rolePoint("A"), roleRun("A"),
    distanceWarning, shieldingWarning, experimentalBadge, experimentalNote,
    countsPerSecond, seconds(45), minutes(5), minutesSeconds(5, 30),
    centimeters(10), meters("1,50"),
)
