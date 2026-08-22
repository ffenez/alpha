package app.alpha.ui.text

/**
 * Калибровка (диагностика) — экран Настройки → Прибор.
 *
 * Три правила, которые перевод обязан перенести вместе со словами:
 *
 * 1. **«Принять модель» ≠ «прибор откалиброван».** Калибровку прибора
 *    приложение не меняет и менять не умеет; принимается НАША модель его
 *    разрешения. В обоих языках это сказано словами, а не подразумевается.
 * 2. **Экстраполяция названа экстраполяцией.** Ниже измеренной области
 *    ширина линии не измерена, а продолжена, и допуск там остаётся широким.
 * 3. **Отклик измерен для распределённого источника в стенах.** К близкому
 *    точечному источнику он неприменим, и для точечной геометрии
 *    количественная проверка отношений остаётся ОТКАЗОМ — в английском тоже.
 */
interface CalibrationStrings {

    /**
     * Кривую нарисовать нельзя: подгонка не дала конечных значений. Пустое
     * поле без объяснения читалось бы как поломка отрисовки.
     */
    val chartUnavailable: String

    // --- вход и шапка ---
    val entryTitle: String
    val entrySubtitle: String
    val screenTitle: String
    val tag: String
    val intro: String

    // --- материал ---
    val materialTitle: String
    fun longAccumulation(hours: String, intervals: Int, from: String, to: String): String
    fun radonAccumulation(hours: String, intervals: Int): String
    fun radonNotEnough(needHours: Int, haveHours: String): String
    val radonExplained: String
    val materialCollected: String
    /** Прибор не спектрометр: проверка по гамма-линиям к нему неприменима. */
    val notASpectrometer: String
    val notASpectrometerWhy: String

    val noMaterial: String
    val noMaterialExplained: String
    val readingMaterial: String

    // --- таблица линий ---
    val linesTitle: String
    val colLine: String
    val colTable: String
    val colObserved: String
    val colDelta: String
    val colWidth: String
    val colSignificance: String
    val unitKeV: String
    val sourceLong: String
    val sourceRadon: String
    val noLinesFound: String
    fun notFound(lines: String): String
    fun blendNote(line: String, shift: String, nuclide: String): String
    val rejectedTitle: String
    fun rejectedBlend(line: String, neighbours: String): String
    fun rejectedShift(line: String): String

    // --- модель разрешения ---
    val resolutionTitle: String
    fun formula(a: String, b: String, c: String): String
    fun measuredRange(from: String, to: String): String
    fun extrapolatedBelow(energy: String): String
    val extrapolationNote: String
    val legendMeasured: String
    val legendFitted: String
    val legendApproximation: String
    val legendExtrapolated: String
    val axisEnergy: String
    val axisFwhm: String
    fun refusalNotEnoughLines(have: Int, need: Int): String
    fun refusalNarrowSpan(span: String, need: String): String
    val refusalNotMonotone: String
    val refusalNegativeNoise: String
    fun refusalPrefix(reason: String): String

    // --- принятие модели ---
    val accept: String
    val revert: String

    /**
     * Поправка энергетической шкалы: заголовок, предложение с числами,
     * состояние принятой поправки и отказ.
     */
    val correctionTitle: String
    val correctionNote: String
    fun correctionOffer(before: String, after: String, lines: Int): String
    fun correctionShift(energyKeV: String, shift: String): String
    val correctionAccept: String
    val correctionRevert: String
    fun correctionAcceptedState(date: String, lines: Int): String
    val correctionNotOffered: String
    val correctionAcceptedNote: String
    fun acceptedState(date: String, points: Int): String

    /** Та же строка для модели, которую приложение сняло и приняло само. */
    fun acceptedAutoState(date: String, points: Int): String
    val acceptedAutoNote: String
    val acceptedNote: String
    /**
     * Чем сейчас описывается ширина линий, пока измеренной модели нет.
     *
     * Процент приходит ОТ ПРИБОРА: у 103 и 110 это 8,4 %, у 103G — 7,4 %, а у
     * приборов, для которых вендор разрешение не публиковал (101, 102,
     * неопознанный), действует консервативная оценка серии — и она так и
     * называется, вендорской её объявлять нельзя.
     */
    fun approximationState(percent: String, vendorPublished: Boolean): String
    fun otherDevice(serial: String): String

    // --- энергетическая шкала ---
    val scaleTitle: String
    fun sigmaCal(keV: String, percent: String): String
    val sigmaCalUpperBound: String
    fun shiftResolved(value: String, sigma: String): String
    fun shiftNotResolved(value: String, sigma: String): String
    val shiftNotEvaluated: String

    /** Разброса не оценить: линий меньше, чем нужно рассеянию. */
    fun scatterNotEvaluated(have: Int, need: Int): String

    /** Сдвиг не объявляется: разброс шкалы не оценён, значимость считать нечем. */
    val shiftNeedsSigma: String
    val noCorrection: String
    fun scaleRange(from: String, to: String): String

    // --- относительный отклик ---
    val responseTitle: String

    /** Заголовок того же раздела, когда считать нечего. */
    val responseTitleNone: String
    fun responsePoint(nuclide: String, upper: String, lower: String, ratio: String, sigma: String): String
    val responseWhy: String
    val responseCaveat: String
    val responsePointGeometry: String
    fun responseFewPoints(count: Int): String
    val responseNone: String

    // --- чего не хватает ---
    val missingTitle: String
    fun needHours(have: String, need: Int): String
    fun needLines(lines: String): String
    val needMore: String
}

object CalibrationRu : CalibrationStrings {
    override val chartUnavailable = "кривую пока не построить: измерений не хватает"

    override val entryTitle = "Проверка спектральной калибровки"
    override val entrySubtitle = "энергетическая шкала и разрешение детектора"
    override val screenTitle = "Калибровка (диагностика)"
    override val tag = "диагностика"
    override val intro =
        "Приложение разбирает уже накопленные снимки спектра и по известным линиям " +
            "природного фона измеряет, какой ширины пики даёт этот прибор и насколько его " +
            "энергетическая шкала расходится с таблицами. Поверочные источники для этого " +
            "не нужны и не используются."

    override val materialTitle = "Материал"
    override fun longAccumulation(hours: String, intervals: Int, from: String, to: String) =
        "длинное накопление: $hours · интервалов: $intervals · $from → $to"

    override fun radonAccumulation(hours: String, intervals: Int) =
        "радоновые часы: $hours · интервалов: $intervals"

    override fun radonNotEnough(needHours: Int, haveHours: String) =
        "радоновых часов не набралось: нужно $needHours ч, есть $haveHours"

    override val radonExplained =
        "Часы с наибольшим радоном берутся отдельно: после дождя продукты распада радона " +
            "дают линии Bi-214 в разы ярче, и в такие часы их удаётся измерить там, где в " +
            "среднем за сутки они тонут в континууме."
    override val materialCollected =
        "Собирается само: снимок спектра пишется раз в 10 минут, пока прибор на связи. " +
            "Складываются РАЗНОСТИ соседних снимков, поэтому одни и те же импульсы не " +
            "считаются дважды."
    override val notASpectrometer = "Этот прибор не строит спектр по каналам"
    override val notASpectrometerWhy =
        "У органического пластикового сцинтиллятора фотопиков нет: импульс несёт долю энергии " +
            "фотона, а не всю её. Опорных линий природного фона в таком спектре не появится " +
            "ни за какое время, поэтому энергетическую шкалу и ширину линий здесь измерять " +
            "не по чему."

    override val noMaterial = "Снимков спектра пока нет"
    override val noMaterialExplained =
        "Снимки появляются сами, пока прибор на связи. Первые опорные линии обычно " +
            "поднимаются над континуумом за несколько часов записи."
    override val readingMaterial = "Читаю снимки…"

    override val linesTitle = "Опорные линии"
    override val colLine = "линия"
    override val colTable = "табл."
    override val colObserved = "набл."
    override val colDelta = "ΔE"
    override val colWidth = "ширина"
    override val colSignificance = "знач."
    override val unitKeV = "кэВ"
    override val sourceLong = "длинное"
    override val sourceRadon = "радон"
    override val noLinesFound = "Ни одной опорной линии измерить не удалось"
    override fun notFound(lines: String) = "пригодны, но не найдены: $lines"

    override fun blendNote(line: String, shift: String, nuclide: String) =
        "у $line рядом стоит линия того же $nuclide: по ядерным данным она смещает центроид " +
            "на $shift. Этот сдвиг сидит внутри ΔE и уходом шкалы не является."

    override val rejectedTitle = "Почему годятся не все линии"
    override fun rejectedBlend(line: String, neighbours: String) =
        "$line — внутри её ширины стоит $neighbours из другого ряда: доля каждой в слитой " +
            "структуре зависит от местного соотношения активностей, и энергия слияния " +
            "заранее неизвестна"

    override fun rejectedShift(line: String) =
        "$line — соседи своего ряда смещают центроид слишком сильно: измерялась бы группа, " +
            "а не линия"

    override val resolutionTitle = "Ширина линий: модель разрешения"
    override fun formula(a: String, b: String, c: String) =
        "FWHM(E) = √($a + $b·E + $c·E²), кэВ"

    override fun measuredRange(from: String, to: String) = "измерено: $from — $to кэВ"
    override fun extrapolatedBelow(energy: String) = "ниже $energy кэВ — экстраполяция"
    override val extrapolationNote =
        "Природных линий, которые этот прибор разделяет, ниже 1 МэВ практически нет: " +
            "583,2 и 609,3 кэВ расходятся на 26 кэВ при ширине около 53, а 238,6 и 242,0 — " +
            "на 3 кэВ. Поэтому ниже измеренной области ширина не измерена, а продолжена, и " +
            "допуск там остаётся широким намеренно: узкое окно ищет структуру там, где её нет."
    override val legendMeasured = "измеренные линии"
    override val legendFitted = "подогнанная кривая"
    override val legendApproximation = "приближение √E"
    override val legendExtrapolated = "экстраполяция"
    override val axisEnergy = "кэВ →"
    override val axisFwhm = "FWHM, кэВ ↑"

    override fun refusalNotEnoughLines(have: Int, need: Int) =
        "измеренных линий $have из $need"

    override fun refusalNarrowSpan(span: String, need: String) =
        "линии лежат слишком тесно: размах $span кэВ из $need"

    override val refusalNotMonotone =
        "подгонка дала убывающую с энергией ширину — такого детектора не бывает"
    override val refusalNegativeNoise =
        "свободный член вышел отрицательным: у нижнего края шкалы кривая ушла бы под ноль"

    override fun refusalPrefix(reason: String) = "Модель не построена: $reason"

    override val accept = "Принять измеренную модель"
    override val revert = "Вернуть приближение"
    override val correctionTitle = "Поправка энергетической шкалы"
    override val correctionNote = "Приложение может показывать энергии со своей поправкой, " +
        "совмещающей найденные линии с табличными. Калибровку самого прибора это не " +
        "меняет: другой программе он отдаст прежние коэффициенты."
    override fun correctionOffer(before: String, after: String, lines: Int) =
        "По $lines линиям: расхождение с таблицей $before → $after кэВ"
    override fun correctionShift(energyKeV: String, shift: String) = "на $energyKeV кэВ сдвиг $shift"
    override val correctionAccept = "Применить поправку"
    override val correctionRevert = "Снять поправку"
    override fun correctionAcceptedState(date: String, lines: Int) =
        "Поправка применяется с $date, посчитана по $lines линиям"
    override val correctionNotOffered = "Поправка не предлагается: линии стоят на своих местах " +
        "или их слишком мало, чтобы отличить сдвиг шкалы от разброса"
    override val correctionAcceptedNote = "Пока поправка включена, энергии на всех экранах " +
        "показаны с ней, и об этом сказано у шкалы спектра."
    override fun acceptedState(date: String, points: Int) =
        "действует измеренная модель · принята $date · по линиям: $points"

    override fun acceptedAutoState(date: String, points: Int) =
        "действует измеренная модель · снята сама $date · по линиям: $points"

    override val acceptedAutoNote =
        "Модель снимается по линиям природного фона в уже накопленных снимках и обновляется " +
            "сама. «Вернуть приближение» отключает её; после этого она заменится только по " +
            "вашему решению."

    override val acceptedNote =
        "Принять — значит, что поиск пиков и допуски совпадения станут ждать линии " +
            "ИЗМЕРЕННОЙ ширины вместо приближённой. Калибровку самого прибора это не " +
            "меняет и изменить не может: уточняется наша модель его разрешения, а не его " +
            "настройки."
    override fun approximationState(percent: String, vendorPublished: Boolean) =
        if (vendorPublished) {
            "действует приближение √E по одной вендорской точке ($percent % на 662 кэВ)"
        } else {
            "разрешение этого прибора вендором не опубликовано: действует приближение √E по " +
                "консервативной оценке серии ($percent % на 662 кэВ)"
        }

    override fun otherDevice(serial: String) =
        "модель измерена на приборе $serial, сейчас подключён другой — она не действует"

    override val scaleTitle = "Энергетическая шкала"
    override fun sigmaCal(keV: String, percent: String) = "разброс шкалы: ±$keV кэВ ($percent)"
    override val sigmaCalUpperBound =
        "разброс остатков не превышает статистического — это верхняя граница, а не " +
            "измеренная величина"

    override fun shiftResolved(value: String, sigma: String) =
        "систематический сдвиг выделен: $value ± $sigma кэВ"

    override fun shiftNotResolved(value: String, sigma: String) =
        "систематический сдвиг не выделен на фоне своей неопределённости: $value ± $sigma кэВ"

    override val shiftNotEvaluated = "остатков слишком мало, чтобы говорить о сдвиге"
    override fun scatterNotEvaluated(have: Int, need: Int) =
        "разброс шкалы не оценивается: линий $have из $need"
    override val shiftNeedsSigma =
        "сдвиг не объявляется: без оценённого разброса шкалы его значимость считать не по " +
            "чему — сравнивать пришлось бы с одной статистикой центроида, а она заведомо " +
            "меньше настоящей ошибки шкалы"
    override val noCorrection =
        "Шкала не правится ни здесь, ни где-либо ещё. Сдвиг оценён по совпадениям, " +
            "полученным при этой же калибровке, и тихая коррекция подтвердила бы любую " +
            "первоначальную догадку."

    override fun scaleRange(from: String, to: String) =
        "проверено в диапазоне $from — $to кэВ; за его пределами шкала не проверялась"

    override val responseTitle = "Относительный отклик: частично"
    override val responseTitleNone = "Относительный отклик"
    override fun responsePoint(
        nuclide: String,
        upper: String,
        lower: String,
        ratio: String,
        sigma: String,
    ) = "$nuclide $upper / $lower кэВ — отклик $ratio ± $sigma"

    override val responseWhy =
        "Обе линии излучают одни и те же ядра, поэтому их отношение не зависит ни от " +
            "активности стен, ни от расстояния до них: активность сокращается."
    override val responseCaveat =
        "Это отклик на РАСПРЕДЕЛЁННЫЙ источник в стенах — вместе с ослаблением в бетоне и " +
            "вкладом рассеянных фотонов. К близкому точечному источнику он неприменим: там " +
            "другой телесный угол и нет слоя материала между источником и кристаллом."
    override val responsePointGeometry =
        "Для точечной геометрии количественная проверка отношений остаётся отказом."

    override fun responseFewPoints(count: Int) =
        "точек всего $count, и все выше 1 МэВ — кривой эффективности отсюда не получается"

    override val responseNone =
        "Двух линий одного нуклида не измерено — относительный отклик не считается"

    override val missingTitle = "Чего не хватает"
    override fun needHours(have: String, need: Int) = "часов записи: $have из $need"
    override fun needLines(lines: String) = "не измерены линии: $lines"
    override val needMore =
        "Оставьте прибор на связи: снимок спектра пишется раз в 10 минут, и материал " +
            "соберётся сам."
}

object CalibrationEn : CalibrationStrings {
    override val chartUnavailable =
        "the curve cannot be drawn yet: there are not enough measurements"

    override val entryTitle = "Spectral calibration check"
    override val entrySubtitle = "the energy scale and the detector's resolution"
    override val screenTitle = "Calibration (diagnostics)"
    override val tag = "diagnostics"
    override val intro =
        "The app goes through the spectrum snapshots it has already collected and uses " +
            "known natural background lines to measure how wide this instrument's peaks are " +
            "and how far its energy scale departs from the tables. No check sources are " +
            "needed and none are used."

    override val materialTitle = "Material"
    override fun longAccumulation(hours: String, intervals: Int, from: String, to: String) =
        "long accumulation: $hours · intervals: $intervals · $from → $to"

    override fun radonAccumulation(hours: String, intervals: Int) =
        "radon-rich hours: $hours · intervals: $intervals"

    override fun radonNotEnough(needHours: Int, haveHours: String) =
        "not enough radon-rich hours: $needHours h needed, $haveHours collected"

    override val radonExplained =
        "The hours richest in radon are taken separately: after rain the radon progeny make " +
            "the Bi-214 lines several times brighter, and in those hours they can be " +
            "measured where a whole-day average buries them in the continuum."
    override val materialCollected =
        "Collected on its own: a spectrum snapshot is written every 10 minutes while the " +
            "instrument is connected. What gets added up are the DIFFERENCES between " +
            "consecutive snapshots, so the same counts are never added twice."
    override val notASpectrometer = "This instrument does not build a channel spectrum"
    override val notASpectrometerWhy =
        "An organic plastic scintillator has no photopeaks: a pulse carries a fraction of the " +
            "photon energy rather than all of it. No natural background lines will ever appear " +
            "in such a spectrum, so there is nothing here to measure the energy scale or the " +
            "line width against."

    override val noMaterial = "No spectrum snapshots yet"
    override val noMaterialExplained =
        "Snapshots appear on their own while the instrument is connected. The first " +
            "reference lines usually rise above the continuum after a few hours of recording."
    override val readingMaterial = "Reading snapshots…"

    override val linesTitle = "Reference lines"
    override val colLine = "line"
    override val colTable = "table"
    override val colObserved = "seen"
    override val colDelta = "ΔE"
    override val colWidth = "width"
    override val colSignificance = "signif."
    override val unitKeV = "keV"
    override val sourceLong = "long"
    override val sourceRadon = "radon"
    override val noLinesFound = "Not a single reference line could be measured"
    override fun notFound(lines: String) = "suitable but not found: $lines"

    override fun blendNote(line: String, shift: String, nuclide: String) =
        "$line has a neighbouring line of the same $nuclide: per nuclear data it shifts the " +
            "centroid by $shift. That shift sits inside ΔE and is not a scale drift."

    override val rejectedTitle = "Why not every line qualifies"
    override fun rejectedBlend(line: String, neighbours: String) =
        "$line — $neighbours from another chain sits inside its width: how much each " +
            "contributes to the blend depends on the local ratio of activities, so the " +
            "energy of the blend is not known in advance"

    override fun rejectedShift(line: String) =
        "$line — neighbours from its own chain shift the centroid too far: what would be " +
            "measured is a group, not a line"

    override val resolutionTitle = "Line width: resolution model"
    override fun formula(a: String, b: String, c: String) =
        "FWHM(E) = √($a + $b·E + $c·E²), keV"

    override fun measuredRange(from: String, to: String) = "measured: $from — $to keV"
    override fun extrapolatedBelow(energy: String) = "below $energy keV — extrapolated"
    override val extrapolationNote =
        "Natural lines this instrument can separate are all but absent below 1 MeV: 583.2 " +
            "and 609.3 keV are 26 keV apart at a width of about 53, and 238.6 and 242.0 are " +
            "3 keV apart. So below the measured range the width is not measured but " +
            "continued, and the tolerance there is deliberately kept wide: a narrow window " +
            "looks for structure where there is none."
    override val legendMeasured = "measured lines"
    override val legendFitted = "fitted curve"
    override val legendApproximation = "√E approximation"
    override val legendExtrapolated = "extrapolated"
    override val axisEnergy = "keV →"
    override val axisFwhm = "FWHM, keV ↑"

    override fun refusalNotEnoughLines(have: Int, need: Int) =
        "measured lines: $have of $need"

    override fun refusalNarrowSpan(span: String, need: String) =
        "the lines sit too close together: span $span keV of $need"

    override val refusalNotMonotone =
        "the fit came out with the width falling as energy rises — no detector behaves that way"
    override val refusalNegativeNoise =
        "the constant term came out negative: at the bottom of the scale the curve would " +
            "go below zero"

    override fun refusalPrefix(reason: String) = "No model was built: $reason"

    override val accept = "Use the measured model"
    override val revert = "Go back to the approximation"
    override val correctionTitle = "Energy scale correction"
    override val correctionNote = "The app can show energies with its own correction that lines " +
        "the found peaks up with the table. This does not change the instrument's own " +
        "calibration: another program will still read its original coefficients."
    override fun correctionOffer(before: String, after: String, lines: Int) =
        "Over $lines lines: departure from the table $before → $after keV"
    override fun correctionShift(energyKeV: String, shift: String) =
        "at $energyKeV keV the shift is $shift"
    override val correctionAccept = "Apply the correction"
    override val correctionRevert = "Drop the correction"
    override fun correctionAcceptedState(date: String, lines: Int) =
        "Applied since $date, computed over $lines lines"
    override val correctionNotOffered = "No correction is offered: the lines sit where they " +
        "should, or there are too few to tell a scale shift from scatter"
    override val correctionAcceptedNote = "While the correction is on, energies on every screen " +
        "are shown with it, and the spectrum scale says so."
    override fun acceptedState(date: String, points: Int) =
        "the measured model is in use · accepted $date · lines used: $points"

    override fun acceptedAutoState(date: String, points: Int) =
        "the measured model is in use · taken on its own $date · lines used: $points"

    override val acceptedAutoNote =
        "The model is measured on natural background lines in the snapshots already recorded, " +
            "and it refreshes itself. \"Go back to the approximation\" turns it off; after that " +
            "it is replaced only by your decision."

    override val acceptedNote =
        "Using it means peak search and match tolerances will expect lines of the MEASURED " +
            "width instead of the approximated one. It does not change the instrument's own " +
            "calibration and cannot: what is refined is our model of its resolution, not its " +
            "settings."
    override fun approximationState(percent: String, vendorPublished: Boolean) =
        if (vendorPublished) {
            "the √E approximation from a single vendor point is in use ($percent % at 662 keV)"
        } else {
            "this instrument's resolution is not published by the vendor: the √E approximation " +
                "uses the conservative estimate for the series ($percent % at 662 keV)"
        }

    override fun otherDevice(serial: String) =
        "the model was measured on instrument $serial and a different one is connected — " +
            "it is not in use"

    override val scaleTitle = "Energy scale"
    override fun sigmaCal(keV: String, percent: String) = "scale spread: ±$keV keV ($percent)"
    override val sigmaCalUpperBound =
        "the spread of the residuals does not exceed the statistical one — this is an upper " +
            "bound, not a measured value"

    override fun shiftResolved(value: String, sigma: String) =
        "a systematic shift stands out: $value ± $sigma keV"

    override fun shiftNotResolved(value: String, sigma: String) =
        "no systematic shift stands out against its own uncertainty: $value ± $sigma keV"

    override val shiftNotEvaluated = "too few residuals to say anything about a shift"
    override fun scatterNotEvaluated(have: Int, need: Int) =
        "the scale scatter is not estimated: $have lines of $need"
    override val shiftNeedsSigma =
        "no shift is claimed: without an estimated scale scatter there is nothing to weigh its " +
            "significance against — the centroid statistics alone are known to be smaller than " +
            "the real error of the scale"
    override val noCorrection =
        "The scale is never corrected, here or anywhere else. The shift is estimated from " +
            "matches obtained under that same calibration, and a silent correction would " +
            "confirm whatever the first guess was."

    override fun scaleRange(from: String, to: String) =
        "checked over $from — $to keV; outside that range the scale was not checked"

    override val responseTitle = "Relative response: partial"
    override val responseTitleNone = "Relative response"
    override fun responsePoint(
        nuclide: String,
        upper: String,
        lower: String,
        ratio: String,
        sigma: String,
    ) = "$nuclide $upper / $lower keV — response $ratio ± $sigma"

    override val responseWhy =
        "Both lines are emitted by the very same nuclei, so their ratio depends neither on " +
            "the activity of the walls nor on the distance to them: the activity cancels."
    override val responseCaveat =
        "This is the response to a DISTRIBUTED source in the walls — together with the " +
            "attenuation in the concrete and the contribution of scattered photons. It does " +
            "not apply to a point source held close to the instrument: the solid angle is " +
            "different and there is no layer of material between source and crystal."
    override val responsePointGeometry =
        "For point geometry a quantitative check of the ratios remains a refusal."

    override fun responseFewPoints(count: Int) =
        "only $count point(s), all above 1 MeV — no efficiency curve follows from that"

    override val responseNone =
        "No two lines of one nuclide were measured — the relative response is not computed"

    override val missingTitle = "What is missing"
    override fun needHours(have: String, need: Int) = "hours recorded: $have of $need"
    override fun needLines(lines: String) = "lines not measured: $lines"
    override val needMore =
        "Leave the instrument connected: a spectrum snapshot is written every 10 minutes " +
            "and the material will collect itself."
}

val CalibrationCatalogue = AreaCatalogue(ru = CalibrationRu, en = CalibrationEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun CalibrationStrings.allTexts(): List<String> = listOf(
    chartUnavailable,
    entryTitle, entrySubtitle, screenTitle, tag, intro,
    materialTitle, longAccumulation("41 ч", 246, "03.08", "12.08"),
    radonAccumulation("7 ч", 42), radonNotEnough(6, "2 ч"),
    radonExplained, materialCollected, noMaterial, noMaterialExplained, readingMaterial,
    linesTitle, colLine, colTable, colObserved, colDelta, colWidth, colSignificance,
    unitKeV, sourceLong, sourceRadon, noLinesFound, notFound("1120,3 · 1764,5"),
    blendNote("1764,5", "+3,6 кэВ", "Bi-214"),
    rejectedTitle, rejectedBlend("583,2", "609,3"), rejectedShift("1377,7"),
    resolutionTitle, formula("210", "3,4", "0,00012"),
    measuredRange("1120", "2614"), extrapolatedBelow("1120"), extrapolationNote,
    legendMeasured, legendFitted, legendApproximation, legendExtrapolated,
    axisEnergy, axisFwhm,
    refusalNotEnoughLines(2, 3), refusalNarrowSpan("340", "500"),
    refusalNotMonotone, refusalNegativeNoise, refusalPrefix("…"),
    accept, revert, acceptedState("12.08", 4), acceptedNote,
    acceptedAutoState("12.08", 4), acceptedAutoNote,
    correctionTitle, correctionNote, correctionOffer("31,2", "1,4", 3), correctionShift("1460,8", "+30,8"),
    correctionAccept, correctionRevert, correctionAcceptedState("12.08", 3), correctionNotOffered,
    correctionAcceptedNote, approximationState("8,4", true), approximationState("8,4", false),
    otherDevice("RC-110-000115"),
    scaleTitle, sigmaCal("4,1", "0,3 %"), sigmaCalUpperBound,
    scatterNotEvaluated(2, 3), shiftNeedsSigma,
    shiftResolved("−2,3", "0,8"), shiftNotResolved("−0,4", "0,8"), shiftNotEvaluated,
    noCorrection, scaleRange("1120", "2614"),
    responseTitle, responseTitleNone, responsePoint("Bi-214", "1764,5", "1120,3", "0,62", "0,05"),
    responseWhy, responseCaveat, responsePointGeometry, responseFewPoints(1), responseNone,
    missingTitle, needHours("12 ч", 24), needLines("1460,8"), needMore,
)
