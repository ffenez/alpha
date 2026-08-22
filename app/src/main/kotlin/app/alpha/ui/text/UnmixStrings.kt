package app.alpha.ui.text

/**
 * Полноспектральное разложение: шаблоны и состав.
 *
 * Четыре отказа, которые перевод обязан переносить дословно:
 *
 *  - разложение даёт ДОЛЮ формы в спектре, а не активность: беккерели требуют
 *    известной геометрии и эталона, и ни на одном языке доля ими не становится;
 *  - шаблон чужого прибора остаётся чужим: приложение приводит его, но говорит
 *    об этом, потому что доля полного поглощения к комптону задана кристаллом;
 *  - «доля объяснённого» — вспомогательное число, а не критерий согласия:
 *    широкая форма поглощает остаток и даёт девяносто девять процентов при
 *    неверном составе;
 *  - подобранное усиление шкалы — это не исправление прибора, а признание, что
 *    его шкала уехала; текст называет величину, а не прячет её.
 */
interface UnmixStrings {

    val title: String
    val subtitle: String

    val emptyTitle: String
    val emptyBody: String

    /** Действия с библиотекой. */
    val recordTemplate: String
    val recordHint: String
    val templatesTitle: String
    val deleteTemplate: String

    /** Досъёмка: шаблон того же прибора становится точнее с каждым сеансом. */
    val appendTemplate: String
    val appendConfirmTitle: String
    fun appendConfirmBody(name: String, have: String, add: String): String
    fun appended(name: String, total: String, gain: String): String
    val appendRefused: String
    val appendTooShort: String

    /** Шаблон из чужого файла: `.spe` или BecqMoni XML. */
    val importTemplate: String
    val importUnreadable: String
    val importNotRecognised: String
    val importNoScale: String
    val importNoTime: String
    val importedDefaultName: String
    fun imported(name: String): String

    /** Отказы записи и разбора. */
    val needSpectrum: String
    val needTemplates: String
    val failed: String

    /** Собственный фон прибора, собранный приложением без участия человека. */
    val autoBackgroundName: String
    val autoBackgroundNote: String

    /** «Th-232 · 7,7 ч · RadiaCode-110» — что за форма и откуда. */
    fun templateLine(name: String, duration: String, device: String): String
    val deviceUnknown: String

    /** «разрешение измерено по своим линиям: 8,1 % на 662, 4,4 % на 2615 кэВ». */
    fun resolutionMeasured(at662: String, at2615: String): String

    /** Годность шаблона к текущему прибору. */
    val fitnessOwn: String
    val fitnessForeign: String
    val fitnessRefused: String

    /** «Th-232 — 38 % счёта (±2 %)». */
    fun componentShare(name: String, percent: String, sigma: String): String

    /** Форма не отличается от нуля: доля ниже предела Карри. */
    fun componentBelowLimit(name: String, limitPercent: String): String

    /** «объяснено 96 % счёта» — вспомогательное число. */
    fun explained(percent: String): String

    /** Согласие модели с данными в единицах σ статистики Кэша. */
    fun agreementOk(sigmas: String): String
    fun agreementBad(sigmas: String): String

    /** «шкала подобрана: усиление ×0,98, смещение +5 кэВ». */
    fun scaleFitted(gain: String, offset: String): String
    val scaleAsMeasured: String

    val methodTitle: String
    val methodWhole: String
    val methodPoisson: String
    val methodScale: String
    val methodDevice: String
    val methodResolution: String
    val methodNoBecquerel: String
}

object UnmixRu : UnmixStrings {

    override val title = "Разложение"
    override val subtitle = "состав спектра по измеренным формам"

    override val emptyTitle = "Шаблонов пока нет"
    override val emptyBody =
        "Шаблон — это спектр известного источника, снятый вашим прибором: ториевая сетка, " +
            "калийная соль, урановое стекло. Собственный фон приложение соберёт само из " +
            "накопленных снимков, остальные формы записываются кнопкой ниже."

    override val recordTemplate = "Записать шаблон"
    override val recordHint = "Берётся то, что накоплено сейчас, вместе с калибровкой прибора"
    override val templatesTitle = "Шаблоны"
    override val deleteTemplate = "Удалить"

    override val appendTemplate = "Дополнить"
    override val appendConfirmTitle = "Дополнить шаблон"
    override fun appendConfirmBody(name: String, have: String, add: String) =
        "«$name»: к $have добавится $add. Счёт складывается, форма становится точнее — вернуть " +
            "прежний шаблон после этого нельзя."

    override fun appended(name: String, total: String, gain: String) =
        "«$name» дополнен: теперь $total, шкала записи приведена ×$gain"

    override val appendRefused = "Дополнить не удалось: запись не приводится к шаблону"
    override val appendTooShort = "Набрано слишком мало: по такой записи не измерить сдвиг шкалы"

    override val importTemplate = "Из файла"
    override val importUnreadable = "Файл не прочитан"
    override val importNotRecognised = "Не похоже на спектр в .spe или BecqMoni XML."
    override val importNoScale = "В файле нет шкалы энергий — приводить форму не к чему"
    override val importNoTime = "В файле нет времени накопления — доля считается от скорости счёта"
    override val importedDefaultName = "Шаблон из файла"
    override fun imported(name: String) = "«$name» добавлен: шаблон снят другим прибором"

    override val needSpectrum = "Нечего записывать: накопленного спектра нет"
    override val needTemplates = "Нужен хотя бы один шаблон, пригодный этому прибору"
    override val failed = "Разложить не удалось: шаблоны не приводятся к этой шкале"

    override val autoBackgroundName = "Фон прибора"
    override val autoBackgroundNote =
        "собран сам из накопленных снимков и обновляется по мере накопления"

    override fun templateLine(name: String, duration: String, device: String) =
        "$name · $duration · $device"

    override val deviceUnknown = "прибор не указан"

    override fun resolutionMeasured(at662: String, at2615: String) =
        "разрешение прибора измерено по своим линиям: $at662 % на 662 кэВ, $at2615 % на 2615 кэВ"

    override val fitnessOwn = "снят этим прибором"
    override val fitnessForeign = "снят другим прибором — форма приведена, но она чужая"
    override val fitnessRefused = "не применим: у этого прибора разрешение лучше"

    override fun componentShare(name: String, percent: String, sigma: String) =
        "$name — $percent % счёта (±$sigma %)"

    override fun componentBelowLimit(name: String, limitPercent: String) =
        "$name — не отличается от нуля (заметно было бы от $limitPercent %)"

    override fun explained(percent: String) = "объяснено $percent % счёта"

    override fun agreementOk(sigmas: String) =
        "модель описывает спектр: отклонение статистики ${sigmas}σ"

    override fun agreementBad(sigmas: String) =
        "модель спектр НЕ описывает: отклонение ${sigmas}σ — состава не хватает или формы чужие"

    override fun scaleFitted(gain: String, offset: String) =
        "шкала подобрана: усиление ×$gain, смещение $offset кэВ"

    override val scaleAsMeasured = "шкала прибора принята как есть"

    override val methodTitle = "Как это считается"
    override val methodWhole =
        "Спектр объясняется целиком: доли форм подбираются по всем каналам, а не по трём окнам. " +
            "На получасовой записи это разница между восемьюстами импульсами и всеми пятьюдесятью " +
            "тысячами."
    override val methodPoisson =
        "Подгонка идёт по правдоподобию Пуассона (статистика Кэша): в верхней части шкалы в " +
            "канале единицы импульсов, и χ² там смещён. Согласие оценивается отклонением " +
            "статистики от её ожидания, а не отношением χ² к числу степеней свободы."
    override val methodScale =
        "Усиление и смещение шкалы подбираются вместе с составом. Иначе уехавшая шкала " +
            "компенсируется чужими формами: подгонка добавит тория там, где его нет."
    override val methodDevice =
        "Форма отклика принадлежит прибору: доля полного поглощения к комптону задана размером " +
            "кристалла. Шаблон другого прибора приводится уширением линий, но остаётся чужим, и " +
            "экран об этом говорит."
    override val methodResolution =
        "Ширина линии измеряется по вашим же спектрам как функция энергии: FWHM² = a + b·E. " +
            "Паспортное «столько-то процентов на 662 кэВ» — частный случай с a = 0, и на 2615 кэВ " +
            "он расходится с измеренной шириной на десятки процентов."

    override val methodNoBecquerel =
        "Разложение даёт долю формы в спектре, а не активность. Беккерели требуют известной " +
            "геометрии и эталонного источника."
}

object UnmixEn : UnmixStrings {

    override val title = "Decomposition"
    override val subtitle = "spectrum composition from measured shapes"

    override val emptyTitle = "No templates yet"
    override val emptyBody =
        "A template is a spectrum of a known source taken by your instrument: a thorium mantle, " +
            "potassium salt, uranium glass, a clean background. Accumulate one and press " +
            "\"Record a template\"; two or three shapes already make a composition."

    override val recordTemplate = "Record a template"
    override val recordHint = "Takes what is accumulated now, together with the instrument calibration"
    override val templatesTitle = "Templates"
    override val deleteTemplate = "Delete"

    override val appendTemplate = "Add to it"
    override val appendConfirmTitle = "Add to the template"
    override fun appendConfirmBody(name: String, have: String, add: String) =
        "\"$name\": $add will be added to $have. The counts are summed and the shape gets more " +
            "precise — the previous template cannot be brought back."

    override fun appended(name: String, total: String, gain: String) =
        "\"$name\" extended: now $total, the record scale was aligned by ×$gain"

    override val appendRefused = "Could not add: the record does not adapt to the template"
    override val appendTooShort = "Too little accumulated: this record cannot show the scale shift"

    override val importTemplate = "From a file"
    override val importUnreadable = "The file was not read"
    override val importNotRecognised = "This does not look like a .spe or BecqMoni XML spectrum."
    override val importNoScale = "The file has no energy scale — there is nothing to adapt the shape to"
    override val importNoTime = "The file has no accumulation time — a share is counted from the count rate"
    override val importedDefaultName = "Template from a file"
    override fun imported(name: String) = "\"$name\" added: the template was taken by another instrument"

    override val needSpectrum = "Nothing to record: there is no accumulated spectrum"
    override val needTemplates = "At least one template fit for this instrument is needed"
    override val failed = "Decomposition failed: the templates do not adapt to this scale"

    override val autoBackgroundName = "Instrument background"
    override val autoBackgroundNote =
        "assembled on its own from the recorded snapshots and refreshed as they accumulate"

    override fun templateLine(name: String, duration: String, device: String) =
        "$name · $duration · $device"

    override val deviceUnknown = "instrument not stated"

    override fun resolutionMeasured(at662: String, at2615: String) =
        "instrument resolution measured on its own lines: $at662 % at 662 keV, $at2615 % at 2615 keV"

    override val fitnessOwn = "taken by this instrument"
    override val fitnessForeign = "taken by another instrument — adapted, but the shape is foreign"
    override val fitnessRefused = "not applicable: this instrument resolves better"

    override fun componentShare(name: String, percent: String, sigma: String) =
        "$name — $percent % of the counts (±$sigma %)"

    override fun componentBelowLimit(name: String, limitPercent: String) =
        "$name — not different from zero (it would show from $limitPercent %)"

    override fun explained(percent: String) = "$percent % of the counts explained"

    override fun agreementOk(sigmas: String) =
        "the model describes the spectrum: statistic deviation ${sigmas}σ"

    override fun agreementBad(sigmas: String) =
        "the model does NOT describe the spectrum: deviation ${sigmas}σ — a shape is missing or foreign"

    override fun scaleFitted(gain: String, offset: String) =
        "scale fitted: gain ×$gain, offset $offset keV"

    override val scaleAsMeasured = "the instrument scale is taken as it is"

    override val methodTitle = "How this is computed"
    override val methodWhole =
        "The whole spectrum is explained: the shares are fitted over every channel rather than " +
            "over three windows. On a half-hour record that is the difference between eight " +
            "hundred counts and all fifty thousand."
    override val methodPoisson =
        "The fit uses the Poisson likelihood (the Cash statistic): in the upper part of the scale " +
            "a channel holds single counts and χ² is biased there. Agreement is judged by the " +
            "deviation of the statistic from its expectation, not by χ² over degrees of freedom."
    override val methodScale =
        "Gain and offset are fitted together with the composition. Otherwise a drifted scale is " +
            "compensated by foreign shapes: the fit adds thorium where there is none."
    override val methodDevice =
        "The response shape belongs to the instrument: the full-absorption to Compton ratio is " +
            "set by the crystal size. A template from another instrument is adapted by widening " +
            "its lines, but it stays foreign, and the screen says so."
    override val methodResolution =
        "The line width is measured on your own spectra as a function of energy: FWHM² = a + b·E. " +
            "The datasheet \"so many percent at 662 keV\" is the special case a = 0, and at " +
            "2615 keV it departs from the measured width by tens of percent."

    override val methodNoBecquerel =
        "Decomposition gives the share of a shape in the spectrum, not an activity. Becquerels " +
            "need a known geometry and a certified source."
}

val UnmixCatalogue = AreaCatalogue(ru = UnmixRu, en = UnmixEn)

fun UnmixStrings.allTexts(): List<String> = listOf(
    title, subtitle, emptyTitle, emptyBody,
    recordTemplate, recordHint, templatesTitle, deleteTemplate,
    appendTemplate, appendConfirmTitle, appendConfirmBody("Th-232", "7,7 ч", "30 мин"),
    appended("Th-232", "8,2 ч", "0,99"), appendRefused, appendTooShort,
    importTemplate, importUnreadable, importNotRecognised, importNoScale, importNoTime,
    importedDefaultName, imported("Th-232"),
    needSpectrum, needTemplates, failed,
    autoBackgroundName, autoBackgroundNote,
    templateLine("Th-232", "7,7 ч", "RadiaCode-110"), deviceUnknown,
    resolutionMeasured("8,1", "4,4"),
    fitnessOwn, fitnessForeign, fitnessRefused,
    componentShare("Th-232", "38", "2"), componentBelowLimit("Cs-137", "4"),
    explained("96"), agreementOk("1,2"), agreementBad("8,4"),
    scaleFitted("0,98", "+5"), scaleAsMeasured,
    methodTitle, methodWhole, methodPoisson, methodScale, methodDevice, methodResolution,
    methodNoBecquerel,
)
