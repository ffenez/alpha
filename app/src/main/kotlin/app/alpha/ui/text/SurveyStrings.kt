package app.alpha.ui.text

/**
 * Радиоэлементная съёмка: станции и величины K, eU, eTh.
 *
 * Три отказа, которые перевод обязан переносить дословно:
 *
 *  - величины называются **eU** и **eTh** — «эквивалентные»: меряются дочерние
 *    продукты в предположении равновесия ряда, и радон это предположение
 *    нарушает. Ни в одном языке они не становятся «ураном» и «торием»;
 *  - процентов калия и ppm тория не бывает без эталонных площадок, и текст
 *    говорит это прямо, а не умалчивает;
 *  - станция «отличается от съёмки» — это результат сравнения с медианой
 *    соседей, а не суждение о веществе. Ни «жила», ни «залежь», ни «аномалия
 *    урана» в выводе не появляются: их называет человек, у которого есть
 *    геология места.
 */
interface SurveyStrings {

    val title: String
    val subtitle: String

    /** Пустой экран учит первому действию, а не сообщает об отсутствии данных. */
    val emptyTitle: String
    val emptyBody: String

    val recordStation: String
    val recordingHint: String

    /** Отказы записи станции — каждый называет, чего именно не хватает. */
    val needSpectrum: String
    val needPosition: String
    val recorded: String

    val quantityPotassium: String
    val quantityUranium: String
    val quantityThorium: String
    val quantityUraniumToThorium: String
    val quantityThoriumToPotassium: String

    /** «12 станций · 4 отличаются» — что показано и сколько выделилось. */
    fun stationsCount(total: Int, notable: Int): String

    /** «0,34 ±0,02 c⁻¹» — величина со своей неопределённостью. */
    fun valueWithSigma(value: String, sigma: String, unit: String): String

    /** Отношение безразмерно, поэтому единицы у него нет. */
    fun ratioWithSigma(value: String, sigma: String): String

    val unitCps: String

    /** «×1,8 к медиане съёмки (2,1σ)» — отличие всегда со знаменателем. */
    fun aboveSurvey(ratio: String, sigmas: String): String
    fun belowSurvey(ratio: String, sigmas: String): String

    /** Отличия нет — и названо, при каком разбросе его искали. */
    fun notDifferent(sigmas: String): String

    /** Сравнивать не с чем: съёмка ещё не набрана. */
    fun tooFewStations(need: Int): String

    /** «линия не набрана» — площадь ниже предела Карри. */
    val belowLimit: String

    /** Накопление станции и её точность. */
    fun accumulation(duration: String): String
    fun accuracy(meters: String): String
    fun height(cm: String): String
    fun pressure(hpa: String): String
    val heightUnknown: String

    /** Прибор станции: окна считаются его разрешением. */
    fun device(name: String): String
    val deviceUnknown: String

    val exportCsv: String
    val exportSaved: String

    val methodTitle: String
    val methodDwell: String
    val methodGeometry: String
    val methodRadon: String
    val methodRatios: String
    val methodNoUnits: String
    val methodStripping: String
}

object SurveyRu : SurveyStrings {

    override val title = "Съёмка U-Th-K"
    override val subtitle = "калий, уран и торий по станциям"

    override val emptyTitle = "Станций пока нет"
    override val emptyBody =
        "Станция — это спектр, снятый на одной точке. Сбросьте накопление, оставьте прибор " +
            "на месте на полчаса и нажмите «Снять станцию». Точки в 100–1000 м друг от друга " +
            "покажут, чем места отличаются между собой."

    override val recordStation = "Снять станцию"
    override val recordingHint = "Спектр и координаты запишутся как есть — сколько накоплено"

    override val needSpectrum = "Нечего записывать: накопленного спектра нет"
    override val needPosition = "Координат нет: нужен доступ к местоположению и связь со спутниками"
    override val recorded = "Станция записана"

    override val quantityPotassium = "K"
    override val quantityUranium = "eU"
    override val quantityThorium = "eTh"
    override val quantityUraniumToThorium = "eU/eTh"
    override val quantityThoriumToPotassium = "eTh/K"

    override fun stationsCount(total: Int, notable: Int) =
        "$total станций · отличается $notable"

    override fun valueWithSigma(value: String, sigma: String, unit: String) =
        "$value ±$sigma $unit"

    override fun ratioWithSigma(value: String, sigma: String) = "$value ±$sigma"

    override val unitCps = "с⁻¹"

    override fun aboveSurvey(ratio: String, sigmas: String) =
        "×$ratio к медиане съёмки (${sigmas}σ)"

    override fun belowSurvey(ratio: String, sigmas: String) =
        "×$ratio к медиане съёмки (${sigmas}σ)"

    override fun notDifferent(sigmas: String) =
        "от медианы съёмки не отличается (${sigmas}σ)"

    override fun tooFewStations(need: Int) =
        "сравнивать не с чем: нужно хотя бы $need соседних станций"

    override val belowLimit = "линия не набрана"

    override fun accumulation(duration: String) = "накопление $duration"
    override fun accuracy(meters: String) = "±$meters м"
    override fun height(cm: String) = "$cm см над землёй"
    override fun pressure(hpa: String) = "$hpa гПа"
    override val heightUnknown = "высота не указана"

    override fun device(name: String) = "прибор: $name"
    override val deviceUnknown = "прибор не опознан — окна по осторожному профилю"

    override val exportCsv = "Выгрузить CSV"
    override val exportSaved = "Файл сохранён"

    override val methodTitle = "Как это считается"
    override val methodDwell =
        "Окна линий строятся по разрешению вашего прибора, а не по таблице: у каждой модели " +
            "оно своё. Площадь линии считается над подложкой SNIP, у неё есть σ и предел, ниже " +
            "которого линия считается ненабранной."
    override val methodGeometry =
        "Счёт сильно зависит от того, как высоко держали прибор и что под ним. Станции сравнимы " +
            "между собой только при одинаковой геометрии — высоту стоит записывать."
    override val methodRadon =
        "eU меряет продукты распада радона, а их в воздухе становится больше при падении " +
            "давления. Станция, снятая в такой день, поднимет eU без всякой геологии — " +
            "давление записывается вместе со станцией."
    override val methodRatios =
        "Зоны выделяют отношения eU/eTh и eTh/K: они не зависят ни от чувствительности " +
            "прибора, ни от того, как долго стояли."
    override val methodNoUnits =
        "Процентов калия и ppm тория здесь нет и не будет: для них нужны эталонные " +
            "калибровочные площадки."
    override val methodStripping =
        "Часть счёта тория попадает в окно урана. Снять её можно только измеренными " +
            "коэффициентами вашего прибора; пока их нет, eU завышен на ториевых точках."
}

object SurveyEn : SurveyStrings {

    override val title = "U-Th-K survey"
    override val subtitle = "potassium, uranium and thorium by station"

    override val emptyTitle = "No stations yet"
    override val emptyBody =
        "A station is a spectrum taken at one point. Reset the accumulation, leave the " +
            "instrument in place for half an hour and press \"Record a station\". Points 100 to " +
            "1000 m apart will show how the places differ from one another."

    override val recordStation = "Record a station"
    override val recordingHint = "The spectrum and the position are stored as they are"

    override val needSpectrum = "Nothing to record: there is no accumulated spectrum"
    override val needPosition = "No position: location access and a satellite fix are needed"
    override val recorded = "Station recorded"

    override val quantityPotassium = "K"
    override val quantityUranium = "eU"
    override val quantityThorium = "eTh"
    override val quantityUraniumToThorium = "eU/eTh"
    override val quantityThoriumToPotassium = "eTh/K"

    override fun stationsCount(total: Int, notable: Int) =
        "$total stations · $notable differ"

    override fun valueWithSigma(value: String, sigma: String, unit: String) =
        "$value ±$sigma $unit"

    override fun ratioWithSigma(value: String, sigma: String) = "$value ±$sigma"

    override val unitCps = "s⁻¹"

    override fun aboveSurvey(ratio: String, sigmas: String) =
        "×$ratio of the survey median (${sigmas}σ)"

    override fun belowSurvey(ratio: String, sigmas: String) =
        "×$ratio of the survey median (${sigmas}σ)"

    override fun notDifferent(sigmas: String) =
        "does not differ from the survey median (${sigmas}σ)"

    override fun tooFewStations(need: Int) =
        "nothing to compare with: at least $need neighbouring stations are needed"

    override val belowLimit = "line not collected"

    override fun accumulation(duration: String) = "accumulated $duration"
    override fun accuracy(meters: String) = "±$meters m"
    override fun height(cm: String) = "$cm cm above ground"
    override fun pressure(hpa: String) = "$hpa hPa"
    override val heightUnknown = "height not recorded"

    override fun device(name: String) = "instrument: $name"
    override val deviceUnknown = "instrument not identified — cautious window profile"

    override val exportCsv = "Export CSV"
    override val exportSaved = "File saved"

    override val methodTitle = "How this is computed"
    override val methodDwell =
        "The line windows are built from the resolution of your instrument rather than from a " +
            "table: every model has its own. The line area is taken above a SNIP continuum, it " +
            "carries a σ and a limit below which the line counts as not collected."
    override val methodGeometry =
        "The count depends strongly on how high the instrument was held and on what lies under " +
            "it. Stations compare only under the same geometry — the height is worth recording."
    override val methodRadon =
        "eU measures radon daughters, and there are more of them in the air when the pressure " +
            "falls. A station taken on such a day raises eU with no geology behind it — the " +
            "pressure is recorded with the station."
    override val methodRatios =
        "Zones are told apart by the eU/eTh and eTh/K ratios: they depend neither on the " +
            "sensitivity of the instrument nor on how long you stood there."
    override val methodNoUnits =
        "There are no percent of potassium and no ppm of thorium here, and there will not be: " +
            "those need certified calibration pads."
    override val methodStripping =
        "Part of the thorium count lands in the uranium window. It can only be removed with " +
            "coefficients measured on your instrument; until then eU is overstated on thorium " +
            "points."
}

val SurveyCatalogue = AreaCatalogue(ru = SurveyRu, en = SurveyEn)

fun SurveyStrings.allTexts(): List<String> = listOf(
    title, subtitle, emptyTitle, emptyBody, recordStation, recordingHint,
    needSpectrum, needPosition, recorded,
    quantityPotassium, quantityUranium, quantityThorium,
    quantityUraniumToThorium, quantityThoriumToPotassium,
    stationsCount(12, 4), valueWithSigma("0,34", "0,02", unitCps),
    ratioWithSigma("1,80", "0,20"), unitCps,
    aboveSurvey("1,8", "2,1"), belowSurvey("0,6", "−2,4"), notDifferent("0,8"),
    tooFewStations(3), belowLimit,
    accumulation("30 мин"), accuracy("8"), height("100"), pressure("1013,2"), heightUnknown,
    device("RadiaCode-110"), deviceUnknown,
    exportCsv, exportSaved,
    methodTitle, methodDwell, methodGeometry, methodRadon, methodRatios, methodNoUnits,
    methodStripping,
)
