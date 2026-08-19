package app.alpha.ui.text

/**
 * Тексты калибровки эффективности регистрации и активности в беккерелях.
 *
 * Область выделена в отдельный каталог, потому что она отвечает на свой
 * вопрос: не «что за линия», а «сколько там активности» — и почти каждая
 * строка здесь называет условие, без которого числа не будет.
 */
interface EfficiencyStrings {

    /** Строка входа в разделе «Прибор» и заголовок самого экрана. */
    val entryTitle: String
    val entrySubtitle: String
    val screenTitle: String
    val intro: String

    /** Кривой нет: что это даёт и что для этого нужно. */
    val notCalibrated: String
    val notCalibratedWhy: String

    /** Состояние кривой: точек, диапазон, согласие. */
    fun pointsCount(count: Int): String
    fun rangeLine(fromKeV: String, toKeV: String): String
    fun agreementLine(reducedChi: String): String
    val agreementUnknown: String
    fun geometryLine(geometry: String): String
    val geometryUnnamed: String
    val geometryWarning: String

    /** Таблица точек. */
    val colEnergy: String
    val colEfficiency: String
    val colSigma: String
    val colSource: String
    fun efficiencyPercent(value: String): String

    /** Действия. */
    val addFromSpectrum: String
    val removePoint: String
    val resetCurve: String
    val resetCurveConfirm: String

    /** Лист «эталонный источник». */
    val sheetTitle: String
    val sheetIntro: String
    val nuclideLabel: String
    val activityLabel: String
    val activityPlaceholder: String
    val activitySigmaLabel: String
    val activitySigmaPlaceholder: String
    val certifiedAtLabel: String
    val certifiedAtPlaceholder: String
    val geometryLabel: String
    val geometryPlaceholder: String
    val apply: String
    val cancel: String

    /** Результат разбора эталона. */
    fun decayedTo(becquerel: String, at: String): String
    fun linesMeasured(found: Int, total: Int): String
    fun linesMissed(energies: String): String
    val nothingMeasured: String
    val badActivity: String
    val badDate: String
    val noSpectrum: String

    /** Активность у пика. */
    fun activityValue(becquerel: String, percent: String): String
    fun activityUpper(becquerel: String): String
    fun activityDetectable(becquerel: String): String
    val activityGeometryNote: String

    /** Единицы активности. */
    val unitBq: String
    val unitKBq: String
    val unitMBq: String
}

object EfficiencyRu : EfficiencyStrings {
    override val entryTitle = "Эффективность регистрации"
    override val entrySubtitle = "калибровка по эталону — активность в беккерелях"
    override val screenTitle = "Эффективность регистрации"
    override val intro = "Площадь фотопика говорит, сколько импульсов зарегистрировано. " +
        "Чтобы перевести её в активность, нужно знать, какая доля квантов линии попадает " +
        "в фотопик. Эту долю измеряют по источнику с известной активностью."

    override val notCalibrated = "Кривая не построена"
    override val notCalibratedWhy = "Пока её нет, активность не показывается нигде: без " +
        "эффективности число в беккерелях было бы произвольным. Нужен источник с " +
        "паспортом — активность на дату аттестации."

    override fun pointsCount(count: Int) = "Точек: $count"
    override fun rangeLine(fromKeV: String, toKeV: String) = "Диапазон: $fromKeV–$toKeV кэВ"
    override fun agreementLine(reducedChi: String) = "Согласие: χ²/ndf $reducedChi"
    override val agreementUnknown = "Согласие: не проверяется — точек столько же, " +
        "сколько коэффициентов"
    override fun geometryLine(geometry: String) = "Геометрия: $geometry"
    override val geometryUnnamed = "Геометрия: не названа"
    override val geometryWarning = "Кривая действует только для той укладки образца, при " +
        "которой снята. Другое расстояние — другая эффективность, и ошибка будет в разы, " +
        "а не в процентах."

    override val colEnergy = "кэВ"
    override val colEfficiency = "ε"
    override val colSigma = "±"
    override val colSource = "эталон"
    override fun efficiencyPercent(value: String) = "$value %"

    override val addFromSpectrum = "Добавить по эталону"
    override val removePoint = "Убрать"
    override val resetCurve = "Сбросить кривую"
    override val resetCurveConfirm = "Убрать все точки? Активность перестанет показываться."

    override val sheetTitle = "Эталонный источник"
    override val sheetIntro = "Спектр на экране снят с этим источником. Приложение найдёт " +
        "его линии и посчитает эффективность на каждой."
    override val nuclideLabel = "Нуклид"
    override val activityLabel = "Активность по паспорту, Бк"
    override val activityPlaceholder = "например 37000"
    override val activitySigmaLabel = "Неопределённость активности, %"
    override val activitySigmaPlaceholder = "например 5"
    override val certifiedAtLabel = "Дата аттестации"
    override val certifiedAtPlaceholder = "дд.мм.гггг"
    override val geometryLabel = "Геометрия"
    override val geometryPlaceholder = "например вплотную к торцу"
    override val apply = "Посчитать и сохранить"
    override val cancel = "Отмена"

    override fun decayedTo(becquerel: String, at: String) =
        "На день измерения: $becquerel (пересчёт по периоду полураспада, $at)"
    override fun linesMeasured(found: Int, total: Int) = "Измерено линий: $found из $total"
    override fun linesMissed(energies: String) =
        "Не найдены пики линий: $energies кэВ — эффективность там не измерена"
    override val nothingMeasured = "Ни одной линии эталона не найдено: спектр набран мало " +
        "или источник не тот"
    override val badActivity = "Активность указывается числом больше нуля"
    override val badDate = "Дата указывается как дд.мм.гггг и не может быть позже измерения"
    override val noSpectrum = "Нет спектра, по которому считать"

    override fun activityValue(becquerel: String, percent: String) = "$becquerel ± $percent %"
    override fun activityUpper(becquerel: String) = "не больше $becquerel"
    override fun activityDetectable(becquerel: String) = "различили бы от $becquerel"
    override val activityGeometryNote = "Число относится к геометрии калибровки"

    override val unitBq = "Бк"
    override val unitKBq = "кБк"
    override val unitMBq = "МБк"
}

object EfficiencyEn : EfficiencyStrings {
    override val entryTitle = "Detection efficiency"
    override val entrySubtitle = "calibrate with a standard — activity in becquerels"
    override val screenTitle = "Detection efficiency"
    override val intro = "A photopeak area tells how many counts were registered. Turning it " +
        "into activity needs the fraction of the line's quanta that land in the photopeak. " +
        "That fraction is measured with a source of known activity."

    override val notCalibrated = "No curve built"
    override val notCalibratedWhy = "Until there is one, activity is shown nowhere: without " +
        "efficiency a number in becquerels would be arbitrary. A certified source is " +
        "needed — activity at the certification date."

    override fun pointsCount(count: Int) = "Points: $count"
    override fun rangeLine(fromKeV: String, toKeV: String) = "Range: $fromKeV–$toKeV keV"
    override fun agreementLine(reducedChi: String) = "Agreement: χ²/ndf $reducedChi"
    override val agreementUnknown = "Agreement: not checked — as many points as coefficients"
    override fun geometryLine(geometry: String) = "Geometry: $geometry"
    override val geometryUnnamed = "Geometry: not named"
    override val geometryWarning = "The curve holds only for the sample placement it was " +
        "measured in. A different distance means a different efficiency, and the error is " +
        "in factors, not percent."

    override val colEnergy = "keV"
    override val colEfficiency = "ε"
    override val colSigma = "±"
    override val colSource = "standard"
    override fun efficiencyPercent(value: String) = "$value %"

    override val addFromSpectrum = "Add from a standard"
    override val removePoint = "Remove"
    override val resetCurve = "Reset the curve"
    override val resetCurveConfirm = "Remove every point? Activity will stop being shown."

    override val sheetTitle = "Reference standard"
    override val sheetIntro = "The spectrum on screen was taken with this source. The app " +
        "will find its lines and compute the efficiency at each."
    override val nuclideLabel = "Nuclide"
    override val activityLabel = "Certified activity, Bq"
    override val activityPlaceholder = "e.g. 37000"
    override val activitySigmaLabel = "Activity uncertainty, %"
    override val activitySigmaPlaceholder = "e.g. 5"
    override val certifiedAtLabel = "Certification date"
    override val certifiedAtPlaceholder = "dd.mm.yyyy"
    override val geometryLabel = "Geometry"
    override val geometryPlaceholder = "e.g. against the end cap"
    override val apply = "Compute and save"
    override val cancel = "Cancel"

    override fun decayedTo(becquerel: String, at: String) =
        "On the measurement day: $becquerel (decay-corrected, $at)"
    override fun linesMeasured(found: Int, total: Int) = "Lines measured: $found of $total"
    override fun linesMissed(energies: String) =
        "No peaks for the lines: $energies keV — efficiency there is not measured"
    override val nothingMeasured = "No line of the standard was found: too short an " +
        "accumulation, or a different source"
    override val badActivity = "Activity is given as a number above zero"
    override val badDate = "The date is dd.mm.yyyy and cannot be later than the measurement"
    override val noSpectrum = "No spectrum to compute from"

    override fun activityValue(becquerel: String, percent: String) = "$becquerel ± $percent %"
    override fun activityUpper(becquerel: String) = "no more than $becquerel"
    override fun activityDetectable(becquerel: String) = "would be told apart from $becquerel"
    override val activityGeometryNote = "The number holds for the calibration geometry"

    override val unitBq = "Bq"
    override val unitKBq = "kBq"
    override val unitMBq = "MBq"
}

val EfficiencyCatalogue = AreaCatalogue(ru = EfficiencyRu, en = EfficiencyEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun EfficiencyStrings.allTexts(): List<String> = listOf(
    entryTitle, entrySubtitle, screenTitle, intro,
    notCalibrated, notCalibratedWhy,
    pointsCount(3), rangeLine("59", "1332"), agreementLine("1,2"), agreementUnknown,
    geometryLine("вплотную"), geometryUnnamed, geometryWarning,
    colEnergy, colEfficiency, colSigma, colSource, efficiencyPercent("1,2"),
    addFromSpectrum, removePoint, resetCurve, resetCurveConfirm,
    sheetTitle, sheetIntro, nuclideLabel, activityLabel, activityPlaceholder,
    activitySigmaLabel, activitySigmaPlaceholder, certifiedAtLabel, certifiedAtPlaceholder,
    geometryLabel, geometryPlaceholder, apply, cancel,
    decayedTo("32 кБк", "12.08.2026"), linesMeasured(2, 4), linesMissed("121,8 · 344,3"),
    nothingMeasured, badActivity, badDate, noSpectrum,
    activityValue("1,2 кБк", "12"), activityUpper("30 Бк"), activityDetectable("30 Бк"),
    activityGeometryNote, unitBq, unitKBq, unitMBq,
)
