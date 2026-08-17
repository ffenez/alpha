package app.alpha.ui.text

/**
 * Отпечаток места: три измерения, их вердикты, эталон и обязательная оговорка.
 *
 * Формулировки этой области несут главное ограничение функции: совпадение
 * отпечатка НЕ доказывает, что прибор в том же месте, а расхождение НЕ
 * называет причину. Перевод обязан отказываться ровно там же — в английском
 * нет ни «detected», ни «matches»: критерий проверял ОТЛИЧИЕ и его не нашёл,
 * а это не то же самое, что доказанное равенство.
 */
interface FingerprintStrings {

    // ------------------------------------------------------- три измерения
    val doseDimension: String
    val countDimension: String
    val shapeDimension: String

    // ----------------------------------------------- числа под вердиктами
    val referenceMissing: String

    /**
     * Единицы полосы эталона. Отпечаток сравнивает величины в тех единицах, в
     * которых собран эталон, поэтому переводится только ОБОЗНАЧЕНИЕ.
     */
    val unitDose: String
    val unitCount: String
    fun needsWindow(minutes: Long): String
    fun nowVsReference(now: String, low: String, high: String, unit: String): String
    val differentChannelGrid: String

    // ---------------------------------------------------------- вывод
    val headlineNoReference: String
    val headlineNotEnough: String
    val headlineBothChanged: String
    val headlineIntensityChanged: String
    val headlineShapeChanged: String
    val headlineNoDifference: String
    fun hardnessExplains(now: String, reference: String, direction: String): String
    val hardnessFlat: String
    fun hardnessAbove(percent: Int): String
    fun hardnessBelow(percent: Int): String
    val caveat: String

    // ------------------------------------------------ состояние измерения
    val stateSame: String
    val stateChanged: String
    val stateNotEnoughData: String
    val stateNotEvaluated: String
    fun changeToReference(percent: Int): String

    // ------------------------------------------------------------- экран
    val chooseProfileFirst: String
    val referenceSection: String
    val referenceNotCreatedYet: String
    fun referenceCreated(day: String, accumulated: String, spectrum: String): String
    val referenceFrozenExplanation: String
    val createReference: String
    val updateReference: String
    val updateReferenceNote: String

    // --------------------------------------- зрелость профиля (репозиторий)
    val maturityNoBaseline: String
    fun maturityNeedsHours(needHours: Long, haveHours: Long): String
    fun maturityThinSpectrum(counts: Long, needCounts: Long): String
}

object FingerprintRu : FingerprintStrings {
    override val doseDimension = "Мощность дозы"
    override val countDimension = "Скорость счёта"
    override val shapeDimension = "Форма спектра"

    override val referenceMissing = "эталон места не создан"
    override val unitDose = "мкЗв/ч"
    override val unitCount = "с⁻¹"

    override fun needsWindow(minutes: Long) = "нужно $minutes мин подходящих измерений"

    override fun nowVsReference(now: String, low: String, high: String, unit: String) =
        "сейчас $now · эталон $low–$high $unit"

    override val differentChannelGrid = "спектры сняты с разной сеткой каналов"

    override val headlineNoReference = "Эталон этого места ещё не создан"
    override val headlineNotEnough = "Пока недостаточно измерений для сравнения с эталоном"
    override val headlineBothChanged =
        "Изменились интенсивность и энергетический характер регистрируемого излучения"
    override val headlineIntensityChanged =
        "Интенсивность отличается от эталона, у энергетического характера отличий не найдено"
    override val headlineShapeChanged =
        "Изменился энергетический характер, у интенсивности отличий не найдено"
    override val headlineNoDifference = "Отличий от эталона этого места не найдено"

    override fun hardnessExplains(now: String, reference: String, direction: String) =
        "Жёсткость $now против $reference — $direction. Это производная величина: она не " +
            "голосует в выводе, а объясняет, почему доза и счёт разошлись."

    override val hardnessFlat = "отличий не найдено"

    override fun hardnessAbove(percent: Int) = "выше эталона на $percent %"

    override fun hardnessBelow(percent: Int) = "ниже эталона на $percent %"

    override val caveat =
        "Совпадение отпечатка не доказывает, что прибор в том же месте, а расхождение не " +
            "называет причину: это описание наблюдения, а не оценка вреда."

    override val stateSame = "отличий не найдено"
    override val stateChanged = "отличается"
    override val stateNotEnoughData = "мало данных"
    override val stateNotEvaluated = "не оценивалось"

    override fun changeToReference(percent: Int) =
        if (percent >= 0) "+$percent % к эталону" else "$percent % к эталону"

    override val chooseProfileFirst =
        "сначала выберите профиль на Главной — отпечаток принадлежит месту"
    override val referenceSection = "Эталон"
    override val referenceNotCreatedYet =
        "ещё не создан — приложение создаст его само, когда у места наберётся достаточно " +
            "подходящих измерений и спектра"

    override fun referenceCreated(day: String, accumulated: String, spectrum: String) =
        "создан $day · накопление $accumulated · спектр $spectrum"

    override val referenceFrozenExplanation =
        "Текущий профиль обновляется автоматически и отвечает на вопрос «что обычно здесь " +
            "сейчас». Эталон заморожен и отвечает на вопрос «как здесь было тогда» — поэтому " +
            "постепенное изменение обстановки видно как расхождение между ними."
    override val createReference = "Создать эталон сейчас"
    override val updateReference = "Обновить эталон"
    override val updateReferenceNote =
        "Обновление нужно после ремонта, переезда или смены прибора: прежний эталон останется " +
            "в истории места."

    override val maturityNoBaseline = "обычный диапазон ещё не собран"

    override fun maturityNeedsHours(needHours: Long, haveHours: Long) =
        "нужно $needHours ч подходящих измерений, собрано $haveHours ч"

    override fun maturityThinSpectrum(counts: Long, needCounts: Long) =
        "опорный спектр ещё тонкий: $counts импульсов из $needCounts"
}

object FingerprintEn : FingerprintStrings {
    override val doseDimension = "Dose rate"
    override val countDimension = "Count rate"
    override val shapeDimension = "Spectrum shape"

    override val referenceMissing = "this place has no reference yet"
    override val unitDose = "µSv/h"
    override val unitCount = "s⁻¹"

    override fun needsWindow(minutes: Long) = "$minutes min of admitted measurements needed"

    override fun nowVsReference(now: String, low: String, high: String, unit: String) =
        "now $now · reference $low–$high $unit"

    override val differentChannelGrid = "the spectra were taken on different channel grids"

    override val headlineNoReference = "This place has no reference yet"
    override val headlineNotEnough = "Not enough measurements yet to compare with the reference"
    override val headlineBothChanged =
        "Both the intensity and the energy character of the registered radiation changed"
    override val headlineIntensityChanged =
        "The intensity differs from the reference; no difference was found in the energy character"
    override val headlineShapeChanged =
        "The energy character changed; no difference was found in the intensity"
    override val headlineNoDifference = "No difference from this place's reference was found"

    override fun hardnessExplains(now: String, reference: String, direction: String) =
        "Hardness $now against $reference — $direction. It is a derived quantity: it casts no " +
            "vote in the conclusion, it explains why dose and count diverged."

    override val hardnessFlat = "no difference found"

    override fun hardnessAbove(percent: Int) = "$percent % above the reference"

    override fun hardnessBelow(percent: Int) = "$percent % below the reference"

    override val caveat =
        "A matching fingerprint does not prove the instrument is in the same place, and a " +
            "difference does not name its cause: this describes an observation, not harm."

    override val stateSame = "no difference found"
    override val stateChanged = "differs"
    override val stateNotEnoughData = "not enough data"
    override val stateNotEvaluated = "not evaluated"

    override fun changeToReference(percent: Int) =
        if (percent >= 0) "+$percent % vs reference" else "$percent % vs reference"

    override val chooseProfileFirst =
        "pick a profile on the Monitor first — a fingerprint belongs to a place"
    override val referenceSection = "Reference"
    override val referenceNotCreatedYet =
        "not created yet — the app will create it on its own once this place has accumulated " +
            "enough admitted measurements and spectrum"

    override fun referenceCreated(day: String, accumulated: String, spectrum: String) =
        "created $day · accumulated $accumulated · spectrum $spectrum"

    override val referenceFrozenExplanation =
        "The current profile keeps updating and answers «what is usual here now». The reference " +
            "is frozen and answers «how it was here back then» — which is why a gradual change " +
            "of the surroundings shows up as a divergence between the two."
    override val createReference = "Create the reference now"
    override val updateReference = "Update the reference"
    override val updateReferenceNote =
        "Update it after repairs, a move or a change of instrument: the previous reference stays " +
            "in this place's history."

    override val maturityNoBaseline = "the historical range has not been collected yet"

    override fun maturityNeedsHours(needHours: Long, haveHours: Long) =
        "$needHours h of admitted measurements needed, $haveHours h collected"

    override fun maturityThinSpectrum(counts: Long, needCounts: Long) =
        "the reference spectrum is still thin: $counts counts out of $needCounts"
}

val FingerprintCatalogue = AreaCatalogue(ru = FingerprintRu, en = FingerprintEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun FingerprintStrings.allTexts(): List<String> = listOf(
    doseDimension, countDimension, shapeDimension,
    referenceMissing, unitDose, unitCount,
    needsWindow(15), nowVsReference("0,15", "0,12", "0,18", unitDose),
    differentChannelGrid,
    headlineNoReference, headlineNotEnough, headlineBothChanged, headlineIntensityChanged,
    headlineShapeChanged, headlineNoDifference,
    hardnessExplains("0,52", "0,49", hardnessFlat), hardnessFlat,
    hardnessAbove(12), hardnessBelow(12), caveat,
    stateSame, stateChanged, stateNotEnoughData, stateNotEvaluated,
    changeToReference(12), changeToReference(-12),
    chooseProfileFirst, referenceSection, referenceNotCreatedYet,
    referenceCreated("12.08 14:30", "24 ч", "6 ч"), referenceFrozenExplanation,
    createReference, updateReference, updateReferenceNote,
    maturityNoBaseline, maturityNeedsHours(24, 6), maturityThinSpectrum(8_000, 20_000),
)
