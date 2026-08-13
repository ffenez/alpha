package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.Peak
import app.radiacode.analysis.PeakDetection
import app.radiacode.analysis.evidence.EvidenceClass
import app.radiacode.analysis.evidence.EvidenceEngine
import app.radiacode.analysis.evidence.EvidenceLineLibrary
import app.radiacode.analysis.evidence.EvidenceOptions
import app.radiacode.analysis.evidence.HistogramContinuum
import app.radiacode.analysis.evidence.LineObservability
import app.radiacode.analysis.evidence.NuclideEvidence
import app.radiacode.analysis.evidence.ObservedPeak
import app.radiacode.analysis.evidence.PeakExplanation
import app.radiacode.analysis.evidence.ResolutionSource
import app.radiacode.analysis.evidence.SpectrumEvidence
import app.radiacode.analysis.evidence.SqrtResolution
import kotlin.math.abs

/** Вид артефакта спектрометрии — то, что стоит в ячейке вместо нуклида. */
enum class ArtifactKind { ANNIHILATION, SINGLE_ESCAPE, DOUBLE_ESCAPE, SUM, BACKSCATTER }

/**
 * Что стоит в колонке «возможное совпадение» у одного пика.
 *
 * Это СТРУКТУРА, а не текст: слова выбирает UI ([SpectrumFormat.matchCell] и
 * [SpectrumFormat.matchNotes]), движок и мост отдают только факты (ADR 006,
 * «потолок осторожности — политика текста»).
 */
sealed interface PeakMatch {

    /** Пику не нашлось ни совместимой линии, ни объяснения-артефакта. */
    data object None : PeakMatch

    /**
     * Артефакт (511, escape, сумма, рассеяние) подписывается артефактом, а не
     * нуклидом: пик объясняется уже наблюдаемым излучением, и вводить ради
     * него новый нуклид не обязательно. [compatibleNuclides] — нуклиды, чьи
     * линии тоже совместимы с этим пиком (511 кэВ и Tl-208 510,8 кэВ для
     * RC-110 неразличимы в принципе): подавлять их было бы такой же ошибкой,
     * как объявлять.
     */
    data class Artifact(
        val kind: ArtifactKind,
        /** Родительский пик (escape), кэВ; null — родитель не един. */
        val parentKeV: Float? = null,
        /** Слагаемые суммы каскада, кэВ. */
        val sumFirstKeV: Float? = null,
        val sumSecondKeV: Float? = null,
        /** Нуклид каскада суммы. */
        val cascadeNuclide: String? = null,
        val compatibleNuclides: List<String> = emptyList(),
    ) : PeakMatch

    /**
     * Прибор физически не разделяет линии этих нуклидов на энергии пика:
     * победитель не назначается, показывается ГРУППА. [natural] истинно,
     * только когда ВСЕ линии группы природные — иначе спокойная подпись
     * приглушила бы искусственного кандидата.
     */
    data class AmbiguousGroup(
        val nuclides: List<String>,
        val natural: Boolean,
    ) : PeakMatch

    /** Кандидат без противоречий: WEAK или SUPPORTED — никогда «обнаружен». */
    data class Candidate(
        val nuclide: String,
        val natural: Boolean,
        val classification: EvidenceClass,
        /** Сколько линий кандидата совпало во всём спектре. */
        val matchedLines: Int,
        /**
         * Нуклиды, чьи линии неотличимы от ЭТОЙ линии, хотя кандидат в целом
         * стоит на других, различимых линиях. Для честной оговорки в деталях.
         */
        val rivals: List<String> = emptyList(),
    ) : PeakMatch

    /**
     * Все кандидаты этого пика противоречат ожидаемым линиям: в колонке
     * прочерк, пометка — в деталях строки.
     */
    data class Contradicted(val nuclides: List<String>) : PeakMatch
}

/** Нуклид, чью справку открывает тап по строке; null — строка не нажимается. */
val PeakMatch.primaryNuclide: String?
    get() = when (this) {
        is PeakMatch.Candidate -> nuclide
        is PeakMatch.AmbiguousGroup -> nuclides.firstOrNull()
        is PeakMatch.Contradicted -> nuclides.firstOrNull()
        else -> null
    }

/** Участвует ли нуклид в этой строке — для подсветки пиков на графике. */
fun PeakMatch.involves(nuclide: String): Boolean = when (this) {
    is PeakMatch.Candidate -> this.nuclide == nuclide || nuclide in rivals
    is PeakMatch.AmbiguousGroup -> nuclide in nuclides
    is PeakMatch.Contradicted -> nuclide in nuclides
    is PeakMatch.Artifact -> nuclide in compatibleNuclides
    PeakMatch.None -> false
}

/** Строка таблицы пиков: сам пик и вердикт движка о его «совпадении». */
data class PeakRow(val peak: Peak, val match: PeakMatch)

/** Одна библиотечная линия кандидата глазами движка — вход строки карточки. */
data class CheckedLine(
    val energyKeV: Float,
    val intensityPercent: Float,
    /** Центроид совпавшего пика; null — линия в спектре не найдена. */
    val measuredKeV: Float?,
    val observability: LineObservability,
)

/** Отношение нетто-площадей двух найденных линий — как посчитал движок. */
data class CheckedRatio(
    val fromKeV: Float,
    val toKeV: Float,
    val observed: Double,
    val sigma: Double,
    val expectedByYield: Double,
)

/**
 * Результат движка по ОДНОМУ кандидату — то, что печатает карточка нуклида.
 *
 * Карточка ничего не пересчитывает: статус, вердикты линий и отношения берутся
 * отсюда как есть. Тип существует отдельно от [NuclideEvidence], чтобы карточка
 * зависела от плоских чисел, а не от внутренних структур движка.
 */
data class NuclideCheck(
    val nuclide: String,
    val classification: EvidenceClass,
    /** Соперники по неразрешимости (без самого кандидата). */
    val ambiguousWith: List<String>,
    val lines: List<CheckedLine>,
    val ratios: List<CheckedRatio>,
) {
    val foundLines: Int get() = lines.count { it.measuredKeV != null }
}

/** Полный вердикт моста по спектру: строки таблицы + карточки кандидатов. */
data class PeakEvidenceVerdict(
    val rows: List<PeakRow>,
    /** Проверка по каждому кандидату — вход [NuclideCard.build]. */
    val checks: Map<String, NuclideCheck>,
    /** Сырая структура движка — для будущих потребителей (экспорт, отчёты). */
    val evidence: SpectrumEvidence,
)

/**
 * Мост между движком доказательств (ADR 006) и экраном Спектра.
 *
 * ЕДИНСТВЕННЫЙ источник вердиктов о кандидатах: и колонка «возможное
 * совпадение», и справка нуклида читают один и тот же результат [analyse] —
 * два ответа на один вопрос расходиться не могут.
 *
 * Правила отображения (политика ТЕКСТА остаётся в UI, здесь — структура):
 *  - артефакт (511, escape, сумма, рассеяние) подписывается артефактом, а не
 *    нуклидом; совместимые нуклиды называются в деталях;
 *  - [EvidenceClass.CONTRADICTED] в колонке не показывается кандидатом —
 *    прочерк, пометка в деталях;
 *  - [EvidenceClass.AMBIGUOUS] показывается группой, победитель не назначается;
 *  - «обнаружен» не пишется никогда (пинится тестами строк).
 */
object PeakEvidenceBridge {

    /** Анализ ниже этого накопления — чтение шума (тот же порог, что был у матчера). */
    const val MIN_ANALYSIS_SECONDS = 60L

    /**
     * @param resolution662 разрешение ЭТОЙ модели прибора для √E-приближения;
     *   если человек принял ИЗМЕРЕННУЮ модель ([ResolutionSource]), действует
     *   она — тот же порядок, что у поиска пиков.
     * @param counts гистограмма спектра — из неё строится континуум для
     *   вопроса «обязана ли была линия быть видна»; пустая — вопрос не ставится.
     */
    fun analyse(
        peaks: List<Peak>,
        counts: List<Int>,
        calibration: EnergyCalibration,
        resolution662: Float = PeakDetection.RESOLUTION_662,
    ): PeakEvidenceVerdict {
        val resolution = ResolutionSource.modelOr(SqrtResolution(resolution662.toDouble()))
        val observed = peaks.map { ObservedPeak.from(it, resolution) }
        val options = EvidenceOptions(
            resolution = resolution,
            continuum = if (counts.isEmpty()) {
                null
            } else {
                HistogramContinuum(counts, calibration, resolution)
            },
        )
        val evidence = EvidenceEngine.analysePeaks(observed, options)
        val rows = peaks.indices.map { i ->
            PeakRow(peaks[i], matchFor(observed[i], evidence))
        }
        return PeakEvidenceVerdict(
            rows = rows,
            checks = evidence.candidates.associate { it.nuclide to checkOf(it) },
            evidence = evidence,
        )
    }

    /**
     * Вердикт для одного пика. Сопоставление везде ПО ССЫЛКЕ (`===`): движок
     * возвращает те же экземпляры [ObservedPeak], что получил, а сравнение по
     * значению склеило бы два одинаковых пика.
     */
    private fun matchFor(peak: ObservedPeak, evidence: SpectrumEvidence): PeakMatch {
        // Кандидаты, одна из линий которых совпала с этим пиком.
        val claimants = evidence.candidates.mapNotNull { candidate ->
            candidate.energyEvidence.firstOrNull { it.peak === peak }
                ?.let { match -> candidate to match }
        }
        val alive = claimants.filter { it.first.classification != EvidenceClass.CONTRADICTED }

        // Артефакты — ДО нуклидов (порядок ADR 006).
        val artifact = evidence.artifactExplanations.filter { it.peak === peak }
        if (artifact.isNotEmpty()) {
            return artifactMatch(artifact, alive.map { it.first.nuclide }.distinct())
        }

        if (claimants.isEmpty()) return PeakMatch.None
        if (alive.isEmpty()) {
            return PeakMatch.Contradicted(claimants.map { it.first.nuclide }.distinct())
        }

        // Ближайшее по z совпадение среди живых кандидатов — «главное» у пика.
        val (primary, primaryMatch) = alive.minBy { (_, match) -> abs(match.z) }
        val ambiguity = primary.resolutionAmbiguities.firstOrNull { it.peak === peak }
        val aliveNuclides = alive.map { it.first.nuclide }.distinct()

        // Прибор не даёт выбора: либо у пика несколько живых кандидатов, либо
        // сам кандидат целиком стоит на неразличимых линиях.
        if (aliveNuclides.size > 1 || primary.classification == EvidenceClass.AMBIGUOUS) {
            val group = (ambiguity?.nuclides.orEmpty() + aliveNuclides).distinct()
            return PeakMatch.AmbiguousGroup(
                nuclides = group,
                natural = group.all { nuclide -> naturalNuclide(evidence, nuclide) },
            )
        }

        return PeakMatch.Candidate(
            nuclide = primary.nuclide,
            natural = primaryMatch.line.natural,
            classification = primary.classification,
            matchedLines = primary.matchedLines,
            rivals = ambiguity?.nuclides.orEmpty().filter { it != primary.nuclide },
        )
    }

    /**
     * Приоритет объяснений: аннигиляция → escape → сумма → рассеяние. Один пик
     * может иметь несколько объяснений — выбирать между ними по одному спектру
     * нечем (KDoc [app.radiacode.analysis.evidence.ArtifactInterpreter]), в
     * ячейку идёт первое по порядку каскада, остальное — детали.
     */
    private fun artifactMatch(
        explanations: List<PeakExplanation>,
        compatibleNuclides: List<String>,
    ): PeakMatch.Artifact {
        explanations.firstOrNull { it is PeakExplanation.Annihilation }?.let {
            return PeakMatch.Artifact(
                kind = ArtifactKind.ANNIHILATION,
                compatibleNuclides = compatibleNuclides,
            )
        }
        explanations.filterIsInstance<PeakExplanation.SingleEscape>().firstOrNull()?.let {
            return PeakMatch.Artifact(
                kind = ArtifactKind.SINGLE_ESCAPE,
                parentKeV = it.parent.centroidKeV.toFloat(),
                compatibleNuclides = compatibleNuclides,
            )
        }
        explanations.filterIsInstance<PeakExplanation.DoubleEscape>().firstOrNull()?.let {
            return PeakMatch.Artifact(
                kind = ArtifactKind.DOUBLE_ESCAPE,
                parentKeV = it.parent.centroidKeV.toFloat(),
                compatibleNuclides = compatibleNuclides,
            )
        }
        explanations.filterIsInstance<PeakExplanation.SumPeak>().firstOrNull()?.let {
            return PeakMatch.Artifact(
                kind = ArtifactKind.SUM,
                sumFirstKeV = it.cascade.firstKeV.toFloat(),
                sumSecondKeV = it.cascade.secondKeV.toFloat(),
                cascadeNuclide = it.cascade.nuclide,
                compatibleNuclides = compatibleNuclides,
            )
        }
        val backscatter = explanations.filterIsInstance<PeakExplanation.Backscatter>().first()
        return PeakMatch.Artifact(
            kind = ArtifactKind.BACKSCATTER,
            parentKeV = backscatter.parent?.centroidKeV?.toFloat(),
            compatibleNuclides = compatibleNuclides,
        )
    }

    /** Природность нуклида — по его линиям в библиотеке движка. */
    private fun naturalNuclide(evidence: SpectrumEvidence, nuclide: String): Boolean {
        val candidate = evidence.candidates.firstOrNull { it.nuclide == nuclide }
        val lines = candidate?.lines?.map { it.line }
            ?: EvidenceLineLibrary.linesOf(nuclide)
        return lines.isNotEmpty() && lines.all { it.natural }
    }

    /** Плоская проекция кандидата для карточки нуклида. */
    private fun checkOf(candidate: NuclideEvidence): NuclideCheck = NuclideCheck(
        nuclide = candidate.nuclide,
        classification = candidate.classification,
        ambiguousWith = candidate.resolutionAmbiguities
            .flatMap { it.nuclides }
            .filter { it != candidate.nuclide }
            .distinct(),
        lines = candidate.lines.map { line ->
            CheckedLine(
                energyKeV = line.line.energyKeV.toFloat(),
                intensityPercent = line.line.intensityPercent.toFloat(),
                measuredKeV = line.match?.peak?.centroidKeV?.toFloat(),
                observability = line.observability,
            )
        },
        ratios = candidate.intensityConsistency.ratios.map { ratio ->
            CheckedRatio(
                fromKeV = ratio.numerator.line.energyKeV.toFloat(),
                toKeV = ratio.denominator.line.energyKeV.toFloat(),
                observed = ratio.observed,
                sigma = ratio.sigma,
                expectedByYield = ratio.expectedByYield,
            )
        },
    )
}
