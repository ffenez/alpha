package app.radiacode.ui.text

/**
 * Строки сравнения двух спектров (экран `SpectrumCompareScreen` и чистое
 * форматирование `CompareFormat`).
 *
 * Область целиком построена на ОТКАЗЕ утверждать равенство: критерий проверяет
 * ОТЛИЧИЕ, поэтому «различие не выделено» нельзя перевести как «in the noise»
 * или «the spectra are the same» — это разные утверждения, и второе сильнее
 * того, что посчитано. По той же причине превышение называется «устойчивым», а
 * не «значимым»: значимость определена только там, где рядом стоит число.
 *
 * Про z: это разность скоростей, делённая на СВОЮ неопределённость, а не
 * «значение в единицах σ» — σ здесь принадлежит самой разности, обе стороны
 * пуассоновские.
 */
interface CompareStrings {

    val title: String
    val snapshotsMissing: String
    val snapshotsReading: String
    val modeInterval: String
    val modeRates: String

    val intervalImpossible: String
    val intervalImpossibleHint: String
    val intervalChartCaption: String
    val saveSnapshot: String
    fun snapshotSaved(label: String): String
    val fileNotWritten: String

    val ratesImpossible: String
    val chartPairTitle: String
    val chartPairCaption: String
    val chartDiffTitle: String
    val chartDiffCaption: String

    val columnEnergy: String
    val columnDiff: String
    val columnZ: String
    val columnVerdict: String
    val zExplanation: String

    val verdictNoDifference: String
    val verdictPossibleExcess: String
    val verdictExcess: String
    val verdictPossibleDeficit: String
    val verdictDeficit: String
}

object CompareRu : CompareStrings {

    override val title = "Сравнение"
    override val snapshotsMissing = "снимки не найдены"
    override val snapshotsReading = "читаю снимки…"
    override val modeInterval = "A−B интервал"
    override val modeRates = "Скорости счёта"

    override val intervalImpossible = "Интервал вычесть нельзя"
    override val intervalImpossibleHint =
        "Этот режим — для двух снимков одного непрерывного накопления: " +
            "позднее минус раннее даёт спектр только за промежуток между ними."
    override val intervalChartCaption =
        "спектр за интервал между снимками · кэВ по горизонтали"
    override val saveSnapshot = "Сохранить снимок"
    override fun snapshotSaved(label: String) =
        "снимок «$label» сохранён — он появился в списке спектров"
    override val fileNotWritten = "файл не записался — попробуйте другую папку"

    override val ratesImpossible = "Сравнить скорости нельзя"
    override val chartPairTitle = "A и B к одному времени накопления"
    override val chartPairCaption = "бирюзовая линия — A · серая — B, приведённый к времени A"
    override val chartDiffTitle = "Разность скоростей A−B, имп/с"
    override val chartDiffCaption =
        "полосы — ±1σ и ±2σ Пуассона (σ = √N с приведением к имп/с): " +
            "линия внутри полос — различие не отличимо от шума"

    override val columnEnergy = "кэВ"
    override val columnDiff = "Δ имп/с"
    override val columnZ = "z"
    override val columnVerdict = "вывод"
    override val zExplanation =
        "z — разность скоростей диапазона, делённая на её " +
            "собственную неопределённость (σ разности, обе стороны " +
            "пуассоновские); |z| < 2 — неотличимо от шума, ≥ 4 — " +
            "устойчивое различие"

    override val verdictNoDifference = "различие не выделено"
    override val verdictPossibleExcess = "возможное превышение"
    override val verdictExcess = "устойчивое превышение"
    override val verdictPossibleDeficit = "возможное снижение"
    override val verdictDeficit = "устойчивое снижение"
}

object CompareEn : CompareStrings {

    override val title = "Comparison"
    override val snapshotsMissing = "snapshots not found"
    override val snapshotsReading = "reading the snapshots…"
    override val modeInterval = "A−B interval"
    override val modeRates = "Count rates"

    override val intervalImpossible = "This interval cannot be subtracted"
    override val intervalImpossibleHint =
        "This mode is for two snapshots of one continuous accumulation: " +
            "the later one minus the earlier one leaves the spectrum of the gap between them."
    override val intervalChartCaption =
        "spectrum of the interval between the snapshots · keV runs horizontally"
    override val saveSnapshot = "Save snapshot"
    override fun snapshotSaved(label: String) =
        "the snapshot «$label» is saved — it appeared in the list of spectra"
    override val fileNotWritten = "the file was not written — try another folder"

    override val ratesImpossible = "The count rates cannot be compared"
    override val chartPairTitle = "A and B at the same accumulation time"
    override val chartPairCaption =
        "teal line — A · grey — B, rescaled to the accumulation time of A"
    override val chartDiffTitle = "Rate difference A−B, counts/s"
    // «различие не отличимо от шума» — отказ различить, а не утверждение о
    // равенстве: «the same» здесь сказать нельзя.
    override val chartDiffCaption =
        "bands — Poisson ±1σ and ±2σ (σ = √N converted to counts/s): " +
            "a line inside the bands is a difference not distinguishable from noise"

    override val columnEnergy = "keV"
    override val columnDiff = "Δ counts/s"
    override val columnZ = "z"
    override val columnVerdict = "conclusion"
    override val zExplanation =
        "z — the rate difference of the range divided by its own " +
            "uncertainty (the σ of the difference itself, Poisson on both " +
            "sides); |z| < 2 — not distinguishable from noise, ≥ 4 — " +
            "a sustained difference"

    // Критерий проверял ОТЛИЧИЕ: «no difference detected» отказывается его
    // назвать, «identical»/«match» утверждали бы равенство, которого никто не
    // проверял. «Sustained», а не «significant»: значимость определена только
    // рядом с числом.
    override val verdictNoDifference = "no difference detected"
    override val verdictPossibleExcess = "possible excess"
    override val verdictExcess = "sustained excess"
    override val verdictPossibleDeficit = "possible decrease"
    override val verdictDeficit = "sustained decrease"
}

val CompareCatalogue = AreaCatalogue(ru = CompareRu, en = CompareEn)

/**
 * Все тексты области — для проверок, которые обязаны действовать на каждую
 * строку. Список ведётся руками: рефлексии в тестовом classpath нет, а
 * забытая строка означала бы непроверенный текст.
 */
fun CompareStrings.allTexts(): List<String> = listOf(
    title, snapshotsMissing, snapshotsReading, modeInterval, modeRates,
    intervalImpossible, intervalImpossibleHint, intervalChartCaption, saveSnapshot,
    snapshotSaved("A−B"), fileNotWritten,
    ratesImpossible, chartPairTitle, chartPairCaption, chartDiffTitle, chartDiffCaption,
    columnEnergy, columnDiff, columnZ, columnVerdict, zExplanation,
    verdictNoDifference, verdictPossibleExcess, verdictExcess,
    verdictPossibleDeficit, verdictDeficit,
)
