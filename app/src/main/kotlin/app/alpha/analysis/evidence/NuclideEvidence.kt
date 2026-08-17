package app.alpha.analysis.evidence

/**
 * Класс доказательства вместо процента уверенности.
 *
 * Процент подразумевает вероятностную модель, которой у нас нет: чтобы сказать
 * «87 %», нужны матрица отклика детектора, кривая эффективности и калибровка.
 * Класс же говорит ровно то, что установлено, и его можно проверить построчно.
 *
 * Формулировки для человека («спектр совместим с…») здесь СОЗНАТЕЛЬНО
 * отсутствуют: потолок осторожности — политика текста, а не математики, и
 * живёт он в UI. Иначе научную модель пришлось бы ломать из-за текстовой
 * политики интерфейса.
 */
enum class EvidenceClass {
    /** Одна совместимая линия, подтвердить нечем. */
    WEAK,

    /** Несколько независимых совместимых линий, противоречий нет. */
    SUPPORTED,

    /** Все совпавшие линии неразличимы с линиями других нуклидов. */
    AMBIGUOUS,

    /** Ожидавшаяся видимой линия отсутствует либо отношения не сходятся. */
    CONTRADICTED,
}

/** Что именно противоречит кандидату. */
enum class ContradictionKind {
    /** Линия обязана была быть видна ([LineObservability.EXPECTED_OBSERVABLE]) — её нет. */
    MISSING_EXPECTED_OBSERVABLE_LINE,

    /** Отношения площадей разошлись с ожидаемыми (возможно только при модели ε). */
    INTENSITY_RATIO_INCONSISTENT,
}

data class Contradiction(
    val kind: ContradictionKind,
    /** Линия, о которой речь; null — противоречие относится к набору целиком. */
    val line: LibraryLine?,
)

/** Состояние одной линии кандидата. */
data class LineEvidence(
    val line: LibraryLine,
    /** Совпадение по энергии; null — линия в спектре не найдена. */
    val match: EnergyMatch?,
    val observability: LineObservability,
    /**
     * У найденного пика есть объяснение-артефакт (511, escape, сумма,
     * обратное рассеяние). Совпадение от этого не отменяется — но нуклид,
     * стоящий на одном таком пике, подкреплён слабее.
     */
    val explainedByArtifact: Boolean = false,
)

/**
 * Итог по одному кандидату — СТРУКТУРА, а не балл.
 *
 * Ни одного взвешенного суммирования: веса вида «0,4·энергия + 0,3·число
 * линий» выглядят научно, но берутся из воздуха, и проверить их нечем.
 */
data class NuclideEvidence(
    val nuclide: String,
    val chain: String?,
    val energyEvidence: List<EnergyMatch>,
    val lines: List<LineEvidence>,
    val matchedLines: Int,
    /** Линии, которые мы имели основание увидеть: найденные + ожидавшиеся видимыми. */
    val expectedObservableLines: Int,
    val missingExpectedLines: List<LibraryLine>,
    val resolutionAmbiguities: List<ResolutionAmbiguity>,
    val intensityConsistency: IntensityConsistency,
    val calibrationConsistency: CalibrationDiagnostic,
    val contradictions: List<Contradiction>,
    val classification: EvidenceClass,
    val context: ContextEvidence = ContextEvidence(),
)

/**
 * Полный результат разбора одного спектра.
 *
 * [unexplainedPeaks] — пики, которым не нашлось ни линии кандидата, ни
 * объяснения-артефакта. Это ЧЕСТНЫЙ остаток, и он показывается: спектр, в
 * котором «объяснено всё», обычно означает слишком щедрую библиотеку, а не
 * хороший анализ.
 */
data class SpectrumEvidence(
    val peaks: List<ObservedPeak>,
    val artifactExplanations: List<PeakExplanation>,
    val candidates: List<NuclideEvidence>,
    val unexplainedPeaks: List<ObservedPeak>,
    val calibration: CalibrationDiagnostic,
    val context: ContextEvidence = ContextEvidence(),
)
