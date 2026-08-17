package app.alpha.ui.text

/**
 * Каталог области «Проверить продукт».
 *
 * Название функции выбрано осторожно: прибор ищет ГАММА-излучающую добавку к
 * фону и спектральные отличия, а не доказывает безопасность еды. Поэтому в
 * каталоге нет ни «безопасно», ни «чисто», ни беккерелей: без валидированной
 * эффективностной калибровки под конкретную геометрию и матрицу число в Бк/кг
 * было бы псевдоточным (IAEA TRS-295, EPA MARLAP гл. 15).
 *
 * Справка «как измерять» — не украшение экрана, а часть метода: при скрининге
 * сравниваются два счёта, и всё, что меняется между ними, кроме самого
 * образца, уходит прямо в результат. Поэтому она объясняет не «как нажимать»,
 * а что именно портит измерение.
 */
interface FoodStrings {

    val title: String
    val subtitle: String

    // --- шаги ---
    val stepBackground: String
    val stepSample: String
    val stepResult: String
    val start: String
    val backgroundHint: String
    val sampleHint: String

    // --- сведения об образце ---
    /** Живая скорость счёта во время прогона: «сейчас 25,1 имп/с». */
    fun countRateNow(value: String): String

    val sampleName: String
    val sampleMass: String
    val container: String
    val note: String
    val addPhoto: String
    val changePhoto: String
    val photoAttached: String

    // --- геометрия ---
    val geometryJarHalf: String
    val geometryJarLitre: String
    val geometryCup: String
    val geometryBag: String
    val geometryPlate: String
    val geometryCustom: String
    val geometryJarHint: String
    val geometryCupHint: String
    val geometryBagHint: String
    val geometryPlateHint: String
    val geometryCustomHint: String

    // --- вывод ---
    val verdictNoDifference: String
    val verdictNoDifferenceBody: String
    val verdictExcess: String
    val verdictExcessBody: String
    val verdictLine: String
    fun verdictLineBody(energy: String): String
    val verdictNotEnough: String
    val verdictNotEnoughBody: String
    val screeningDisclaimer: String
    fun sensitivityLine(fraction: String, cps: String): String
    fun recommendedLine(duration: String, fraction: String): String
    val continueMeasuring: String
    /** Экспорт измерения одним файлом: образец и фон вместе. */
    val exportMeasurement: String

    // --- справка ---
    val guideTitle: String
    /** Разделы справки: заголовок и абзац. Порядок — порядок действий. */
    fun guide(): List<Pair<String, String>>
}

object FoodRu : FoodStrings {

    override val title = "Проверить продукт"
    override val subtitle =
        "Сравнение образца с фоном в одной и той же геометрии. Это скрининг " +
            "гамма-излучения, а не лабораторный анализ."

    override val stepBackground = "1. Фон"
    override val stepSample = "2. Продукт"
    override val stepResult = "3. Результат"
    override val start = "Начать"
    override val backgroundHint =
        "Уберите образец, оставьте прибор на месте. Фон снимается там же и так " +
            "же, как потом будет стоять продукт."
    override val sampleHint =
        "Поставьте образец в то же положение и не двигайте прибор до конца " +
            "измерения."

    override fun countRateNow(value: String) = "сейчас $value имп/с"

    override val sampleName = "Что измеряем"
    override val sampleMass = "Масса, г"
    override val container = "Ёмкость"
    override val note = "Заметка"
    override val addPhoto = "Фото образца"
    override val changePhoto = "Другое фото"
    override val photoAttached = "фото выбрано"

    override val geometryJarHalf = "Банка 0,5 л"
    override val geometryJarLitre = "Банка 1 л"
    override val geometryCup = "Кружка"
    override val geometryBag = "Пакет"
    override val geometryPlate = "Тарелка"
    override val geometryCustom = "Своя"
    override val geometryJarHint =
        "Заполнить до плечиков, прибор плашмя к боковой стенке, всегда одной " +
            "и той же стороной."
    override val geometryCupHint =
        "Заполнить до одной и той же метки, прибор к стенке вплотную."
    override val geometryBagHint =
        "Расправить пакет, положить прибор сверху всей плоскостью, толщина " +
            "слоя одинаковая."
    override val geometryPlateHint =
        "Худший случай: тонкий слой даёт мало сигнала. Годится для сравнения " +
            "с таким же тонким слоем."
    override val geometryCustomHint =
        "Опишите положение так, чтобы его можно было повторить через месяц."

    override val verdictNoDifference = "Отличий от фона не найдено"
    override val verdictNoDifferenceBody =
        "За это время дополнительного гамма-сигнала над фоном не набралось."
    override val verdictExcess = "Образец отличается от фона"
    override val verdictExcessBody =
        "Скорость счёта с образцом устойчиво выше фоновой. Отдельной линии пока " +
            "не выделено."
    override val verdictLine = "В спектре появился дополнительный компонент"
    override fun verdictLineBody(energy: String) =
        "Избыток наблюдается около $energy. Совпадения по энергии недостаточно, " +
            "чтобы назвать нуклид, — нужны выдержка и остальные линии."
    override val verdictNotEnough = "Данных пока мало"
    override val verdictNotEnoughBody =
        "Импульсов набралось слишком мало, чтобы сравнивать. Продолжите " +
            "измерение."
    override val screeningDisclaimer =
        "Это скрининг: прибор ищет гамма-излучающую добавку к фону. Отсутствие " +
            "отличий не доказывает отсутствия радионуклидов, а количество в " +
            "беккерелях без калибровки под эту геометрию и этот продукт " +
            "приложение не считает."
    override fun sensitivityLine(fraction: String, cps: String) =
        "заметной была бы добавка от $fraction фона ($cps)"
    override fun recommendedLine(duration: String, fraction: String) =
        "чтобы заметить $fraction фона, копить около $duration на каждый шаг"
    override val continueMeasuring = "Копить дальше"
    override val exportMeasurement = "Экспорт N42 (образец и фон)"

    override val guideTitle = "Как измерять правильно"

    override fun guide(): List<Pair<String, String>> = listOf(
        "Главное правило" to
            "Сравниваются два счёта: без образца и с образцом. Всё, что " +
            "изменится между ними, кроме самого образца, попадёт в результат " +
            "как «отличие». Поэтому задача — не менять больше ничего: ни " +
            "места, ни положения прибора, ни ёмкости.",

        "Место" to
            "Выберите место подальше от стен, пола из бетона, гранита и " +
            "керамической плитки: они сами дают заметный гамма-фон, и вблизи " +
            "них разброс фона больше. Одно и то же место для всех измерений " +
            "делает результаты сравнимыми между собой.",

        "Положение прибора" to
            "Прибор кладётся вплотную к ёмкости — плашмя к стенке или под дно, " +
            "одной и той же стороной. Держать в руках нельзя: рука двигается, " +
            "экранирует часть излучения и добавляет собственный калий. Отметьте " +
            "положение прибора и ёмкости, чтобы повторить его в следующий раз.",

        "Ёмкость и заполнение" to
            "Одна и та же ёмкость, заполненная до одной и той же метки. Чем " +
            "больше вещества вокруг детектора, тем больше сигнал: тонкий слой " +
            "на тарелке — худший случай. Толстые стенки и стекло поглощают " +
            "мягкое излучение, поэтому для сравнения важна не «правильная» " +
            "ёмкость, а одна и та же.",

        "Подготовка образца" to
            "Продукт лучше измельчить и перемешать: комок в углу ёмкости и та " +
            "же масса, распределённая вокруг детектора, дают разный счёт. " +
            "Сухое и влажное — разные плотности, поэтому сравнивать имеет " +
            "смысл одинаково подготовленные образцы.",

        "Сколько копить" to
            "Время считается от фонового счёта, а не назначается круглым " +
            "числом: чтобы заметить добавку в 5 % от фона, нужно порядка " +
            "нескольких минут, в 2 % — уже около получаса на каждый шаг. " +
            "Приложение показывает и то, какая добавка была бы заметна за " +
            "уже набранное время.",

        "Фон и образец поровну" to
            "Фон снимается столько же, сколько образец, и желательно рядом по " +
            "времени: фон меняется в течение суток — от погоды, дождя, " +
            "проветривания. Если между шагами прошло много времени, фон стоит " +
            "снять заново.",

        "Чего ждать от результата" to
            "Прибор видит гамма-излучение. Стронций-90 и другие чисто " +
            "бета-излучающие нуклиды он не увидит вовсе. Мягкие линии " +
            "поглощаются самим продуктом и стенками ёмкости. Поэтому «отличий " +
            "не найдено» означает ровно одно: за это время и с этой " +
            "чувствительностью добавки не набралось.",

        "Природный калий" to
            "Богатые калием продукты — курага, бобовые, картофель, орехи, " +
            "заменители соли — дают настоящую линию калия-40 около 1461 кэВ. " +
            "Это природный компонент любой пищи, а не загрязнение; он был в " +
            "продуктах всегда.",
    )
}

object FoodEn : FoodStrings {

    override val title = "Check a product"
    override val subtitle =
        "A comparison of the sample against the background in the same geometry. " +
            "This is gamma screening, not a laboratory analysis."

    override val stepBackground = "1. Background"
    override val stepSample = "2. Product"
    override val stepResult = "3. Result"
    override val start = "Start"
    override val backgroundHint =
        "Take the sample away and leave the instrument where it is. The " +
            "background is taken where the product will stand."
    override val sampleHint =
        "Put the sample in the same position and do not move the instrument " +
            "until the run is over."

    override fun countRateNow(value: String) = "now $value cps"

    override val sampleName = "What is measured"
    override val sampleMass = "Mass, g"
    override val container = "Container"
    override val note = "Note"
    override val addPhoto = "Photo of the sample"
    override val changePhoto = "Another photo"
    override val photoAttached = "photo chosen"

    override val geometryJarHalf = "Jar 0.5 l"
    override val geometryJarLitre = "Jar 1 l"
    override val geometryCup = "Mug"
    override val geometryBag = "Bag"
    override val geometryPlate = "Plate"
    override val geometryCustom = "Own"
    override val geometryJarHint =
        "Fill to the shoulder, the instrument flat against the side wall, " +
            "always the same side."
    override val geometryCupHint =
        "Fill to the same mark, the instrument tight against the wall."
    override val geometryBagHint =
        "Flatten the bag, put the instrument on top with its whole face, keep " +
            "the layer equally thick."
    override val geometryPlateHint =
        "The worst case: a thin layer gives little signal. Fine for comparing " +
            "against an equally thin layer."
    override val geometryCustomHint =
        "Describe the position so that it can be repeated a month later."

    override val verdictNoDifference = "No difference from the background"
    override val verdictNoDifferenceBody =
        "Over this time no additional gamma signal above the background built up."
    override val verdictExcess = "The sample differs from the background"
    override val verdictExcessBody =
        "The count rate with the sample stays above the background one. No " +
            "separate line has been resolved yet."
    override val verdictLine = "An additional component appeared in the spectrum"
    override fun verdictLineBody(energy: String) =
        "The excess sits near $energy. A match in energy alone is not enough to " +
            "name a nuclide — that needs exposure and the other lines."
    override val verdictNotEnough = "Not enough data yet"
    override val verdictNotEnoughBody =
        "Too few counts have accumulated to compare. Keep measuring."
    override val screeningDisclaimer =
        "This is screening: the instrument looks for a gamma-emitting addition " +
            "to the background. No difference does not prove the absence of " +
            "radionuclides, and the app does not compute becquerels without a " +
            "calibration for this geometry and this product."
    override fun sensitivityLine(fraction: String, cps: String) =
        "an addition from $fraction of the background would have shown ($cps)"
    override fun recommendedLine(duration: String, fraction: String) =
        "to notice $fraction of the background, collect about $duration per step"
    override val continueMeasuring = "Keep collecting"
    override val exportMeasurement = "Export N42 (sample and background)"

    override val guideTitle = "How to measure properly"

    override fun guide(): List<Pair<String, String>> = listOf(
        "The main rule" to
            "Two counts are compared: without the sample and with it. Anything " +
            "that changes between them other than the sample itself lands in " +
            "the result as a «difference». So the task is to change nothing " +
            "else: not the place, not the position, not the container.",

        "The place" to
            "Pick a spot away from walls and from concrete, granite or ceramic " +
            "tile: they emit a noticeable gamma background of their own, and " +
            "near them the background varies more. The same spot every time " +
            "makes results comparable to each other.",

        "How the instrument lies" to
            "The instrument lies flat against the container — the same side to " +
            "the wall or under the bottom. Do not hold it: a hand moves, " +
            "shields part of the radiation and adds its own potassium. Mark the " +
            "position of both so it can be repeated next time.",

        "Container and filling" to
            "The same container filled to the same mark. The more matter around " +
            "the detector, the larger the signal: a thin layer on a plate is " +
            "the worst case. Thick walls and glass absorb soft radiation, so " +
            "what matters is not the «right» container but the same one.",

        "Preparing the sample" to
            "Grind and mix the product: a lump in the corner and the same mass " +
            "spread around the detector give different counts. Dry and wet mean " +
            "different densities, so compare samples prepared alike.",

        "How long to collect" to
            "The time follows from the background rate rather than a round " +
            "number: to notice an addition of 5 % of the background takes " +
            "minutes, 2 % takes about half an hour per step. The app also shows " +
            "which addition would have been visible in the time already spent.",

        "Equal time for both" to
            "The background is collected for as long as the sample and " +
            "preferably close in time: the background changes through the day — " +
            "with weather, rain, airing the room. If a lot of time passed " +
            "between the steps, take the background again.",

        "What the result can and cannot say" to
            "The instrument sees gamma radiation. Strontium-90 and other pure " +
            "beta emitters it does not see at all. Soft lines are absorbed by " +
            "the product and the container. So «no difference» means exactly " +
            "one thing: over this time and at this sensitivity no addition " +
            "built up.",

        "Natural potassium" to
            "Potassium-rich foods — dried apricots, beans, potatoes, nuts, salt " +
            "substitutes — give a real potassium-40 line near 1461 keV. That is " +
            "a natural component of any food, not contamination; it has always " +
            "been there.",
    )
}

val FoodCatalogue = AreaCatalogue(ru = FoodRu, en = FoodEn)

/** Все строки области — для проверок, действующих на каждый язык. */
fun FoodStrings.allTexts(): List<String> = listOf(
    title, subtitle,
    stepBackground, stepSample, stepResult, start, backgroundHint, sampleHint,
    countRateNow("25,1"),
    sampleName, sampleMass, container, note, addPhoto, changePhoto, photoAttached,
    geometryJarHalf, geometryJarLitre, geometryCup, geometryBag, geometryPlate,
    geometryCustom, geometryJarHint, geometryCupHint, geometryBagHint,
    geometryPlateHint, geometryCustomHint,
    verdictNoDifference, verdictNoDifferenceBody,
    verdictExcess, verdictExcessBody,
    verdictLine, verdictLineBody("662 кэВ"),
    verdictNotEnough, verdictNotEnoughBody, screeningDisclaimer,
    sensitivityLine("2 %", "0,5 имп/с"), recommendedLine("30 мин", "2 %"),
    continueMeasuring, exportMeasurement, guideTitle,
) + guide().flatMap { listOf(it.first, it.second) }
