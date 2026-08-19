package app.alpha.analysis.evidence

import app.alpha.analysis.Peak
import kotlin.math.abs

/**
 * Параметры разбора. Всё, что зависит от прибора или от того, чего у нас пока
 * нет, приходит СНАРУЖИ, а не зашито в движок.
 *
 * @param resolution модель разрешения ЭТОГО прибора (по умолчанию √E-модель)
 * @param efficiency кривая эффективности; null — количественная проверка
 *   отношений не выполняется, и это состояние по умолчанию
 * @param continuum континуум спектра; null — вопрос «должна ли была линия быть
 *   видна» остаётся без ответа, и отсутствие линии ничего не доказывает
 * @param energyRangeKeV шкала прибора: линия вне её не «пропала», её негде было увидеть
 */
data class EvidenceOptions(
    val resolution: ResolutionModel = SqrtResolution(),
    val efficiency: DetectorEfficiencyModel? = null,
    val continuum: ContinuumModel? = null,
    val energyRangeKeV: ClosedFloatingPointRange<Double> = 20.0..3000.0,
    val library: List<LibraryLine> = EvidenceLineLibrary.LINES,
    val maxZ: Double = EnergyMatching.MAX_ACCEPTABLE_Z,
    /**
     * Порог значимости, с которым работал поиск пиков. Отсутствие линии
     * доказывает что-либо только выше него: линию, которую сам поиск не
     * объявил бы находкой, нельзя считать пропавшей.
     */
    val minSignificance: Double = LineObservabilityRule.DEFAULT_MIN_SIGNIFICANCE,
    val context: ContextEvidence = ContextEvidence(),
)

/**
 * Движок спектральных доказательств: каскад независимых проверок вместо
 * вопроса «какая известная линия оказалась рядом».
 *
 * Порядок стадий (9.md §6, §11, §15) существенен:
 *
 *  1. **Артефакты** — 511, escape, суммы, обратное рассеяние объясняются ДО
 *     того, как ради пика вводится новый нуклид;
 *  2. **Энергетическая приемлемость** — нет ни одной линии с |z| ≤ maxZ,
 *     кандидат не создаётся вовсе;
 *  3. **Весь набор линий** — что совпало, что ожидалось видимым и не нашлось;
 *  4. **Группы неразрешимости** — где прибор физически не даёт выбора,
 *     победитель не назначается;
 *  5. **Отношения интенсивностей** — количественно только при модели ε;
 *  6. **Диагностика калибровки** — общая для спектра, не для кандидата.
 *
 * Единого балла нет ни на одной стадии.
 */
object EvidenceEngine {

    /**
     * Версия математики движка — синхронизирована с
     * [app.alpha.analysis.AlgorithmVersions.PEAK_EVIDENCE] и запинена
     * тестом: изменение каскада или классификации обязано приходить вместе с
     * осознанным бампом.
     */
    const val ALGORITHM_VERSION = 1

    /** Разбор по уже найденным пикам детектора. */
    fun analyse(detected: List<Peak>, options: EvidenceOptions): SpectrumEvidence =
        analysePeaks(detected.map { ObservedPeak.from(it, options.resolution) }, options)

    fun analysePeaks(peaks: List<ObservedPeak>, options: EvidenceOptions): SpectrumEvidence {
        val artifacts = ArtifactInterpreter.explain(peaks, options.resolution)
        val explained = artifacts.map { it.peak }.toSet()

        val perNuclide = options.library.groupBy { it.nuclide }
        val matchesByNuclide = perNuclide.mapValues { (_, lines) ->
            lines.mapNotNull { line -> bestMatch(peaks, line, options) }
        }.filterValues { it.isNotEmpty() }

        val calibration = calibrationOf(matchesByNuclide.values.flatten(), options, peaks)

        val candidates = matchesByNuclide.map { (nuclide, matches) ->
            evidenceFor(
                nuclide = nuclide,
                lines = perNuclide.getValue(nuclide),
                matches = matches,
                explainedPeaks = explained,
                calibration = calibration,
                options = options,
                observedPeaks = peaks,
            )
        }.sortedByDescending { it.matchedLines }

        val matchedPeaks = matchesByNuclide.values.flatten().map { it.peak }.toSet()
        val unexplained = peaks.filter { it !in matchedPeaks && it !in explained }
        return SpectrumEvidence(
            peaks = peaks,
            artifactExplanations = artifacts,
            candidates = candidates,
            unexplainedPeaks = unexplained,
            calibration = calibration,
            context = options.context,
        )
    }

    /** Лучшее совпадение линии среди пиков — по наименьшему |z|. */
    private fun bestMatch(
        peaks: List<ObservedPeak>,
        line: LibraryLine,
        options: EvidenceOptions,
    ): EnergyMatch? = peaks
        .mapNotNull { EnergyMatching.match(it, line, options.maxZ) }
        .minByOrNull { abs(it.z) }

    /**
     * Диагностика калибровки считается ОДИН раз на спектр и кладётся всем
     * кандидатам: энергетическая шкала — свойство прибора, а не гипотезы о
     * нуклиде. Берутся только надёжные совпадения — значимые пики без
     * неразрешимых альтернатив (см. [CalibrationDiagnostics]).
     */
    private fun calibrationOf(
        matches: List<EnergyMatch>,
        options: EvidenceOptions,
        /** Найденные пики: по ним соперник проверяется своей яркой линией. */
        observedPeaks: List<ObservedPeak>,
    ): CalibrationDiagnostic {
        val reliable = matches
            .filter { it.peak.significance >= CalibrationDiagnostics.RELIABLE_MIN_SIGNIFICANCE }
            .filter {
                ResolutionAmbiguities.ambiguityFor(
                    peak = it.peak,
                    matched = it.line,
                    resolution = options.resolution,
                    lines = options.library,
                    // Соперник, чья яркая линия молчит, живым не считается и
                    // здесь: иначе достаточно положить в библиотеку соседа,
                    // чтобы диагностика перестала работать вовсе.
                    observedPeaks = observedPeaks,
                ) == null
            }
            // Один пик — один остаток: две линии одного нуклида, слитые
            // разрешением, дали бы два «независимых» остатка из одного числа.
            .groupBy { it.peak }
            .map { (_, group) -> group.minBy { abs(it.z) } }
        return CalibrationDiagnostics.evaluate(
            reliable.map {
                CalibrationResidual(
                    energyKeV = it.line.energyKeV,
                    deltaKeV = it.deltaKeV,
                    // Не it.sigmaKeV: в нём сидит σ_cal, то есть искомая
                    // величина — см. KDoc [CalibrationDiagnostics].
                    sigmaKeV = CalibrationDiagnostics.residualSigmaKeV(it),
                )
            },
            // Систематическая часть здесь — инженерная оценка σ_cal(E)
            // ([EnergyMatching]): измеренного разброса у движка нет, а без
            // всякой систематики значимость сдвига считалась бы по одной
            // статистике центроида и «выделяла» бы почти любой сдвиг.
            systematicSigmaKeV = EnergyMatching::calibrationSigmaKeV,
        )
    }

    private fun evidenceFor(
        nuclide: String,
        lines: List<LibraryLine>,
        matches: List<EnergyMatch>,
        explainedPeaks: Set<ObservedPeak>,
        calibration: CalibrationDiagnostic,
        options: EvidenceOptions,
        /** Все найденные пики — по ним соперник проверяется своей яркой линией. */
        observedPeaks: List<ObservedPeak>,
    ): NuclideEvidence {
        // Опорная линия — найденная с наибольшей нетто-площадью: от неё
        // считается ожидаемая площадь всех остальных линий.
        val reference = matches.maxBy { it.peak.netArea }
        val lineEvidence = lines.map { line ->
            val match = matches.firstOrNull { it.line == line }
            LineEvidence(
                line = line,
                match = match,
                observability = if (match != null) {
                    LineObservability.OBSERVED
                } else {
                    LineObservabilityRule.evaluate(
                        line = line,
                        referenceLine = reference.line,
                        referenceArea = reference.peak.netArea,
                        continuum = options.continuum,
                        resolution = options.resolution,
                        efficiency = options.efficiency,
                        energyRangeKeV = options.energyRangeKeV,
                        minSignificance = options.minSignificance,
                        // Занятые области спектра — ВСЕ найденные пики, а не
                        // только линии этого нуклида: линия, под которой стоит
                        // чужой пик, не выделяется отдельным максимумом, и
                        // «её нет» о ней сказать нельзя.
                        foundEnergiesKeV = observedPeaks.map { it.centroidKeV },
                    )
                },
                explainedByArtifact = match != null && match.peak in explainedPeaks,
            )
        }
        val missing = lineEvidence
            .filter { it.match == null && it.observability == LineObservability.EXPECTED_OBSERVABLE }
            .map { it.line }
        val ambiguities = matches.mapNotNull {
            ResolutionAmbiguities.ambiguityFor(
                peak = it.peak,
                matched = it.line,
                resolution = options.resolution,
                lines = options.library,
                observedPeaks = observedPeaks,
            )
        }
        val intensity = IntensityConsistencyEvaluator.evaluate(matches, options.efficiency)
        val contradictions = buildList {
            missing.forEach {
                add(Contradiction(ContradictionKind.MISSING_EXPECTED_OBSERVABLE_LINE, it))
            }
            if (intensity is IntensityConsistency.Evaluated && !intensity.consistent) {
                add(Contradiction(ContradictionKind.INTENSITY_RATIO_INCONSISTENT, null))
            }
        }
        return NuclideEvidence(
            nuclide = nuclide,
            chain = lines.firstNotNullOfOrNull { it.chain },
            energyEvidence = matches.sortedBy { it.line.energyKeV },
            lines = lineEvidence,
            matchedLines = matches.size,
            expectedObservableLines = matches.size + missing.size,
            missingExpectedLines = missing,
            resolutionAmbiguities = ambiguities,
            intensityConsistency = intensity,
            calibrationConsistency = calibration,
            contradictions = contradictions,
            classification = classify(matches, ambiguities, contradictions),
            context = options.context,
        )
    }

    /**
     * Классификация — правила, а не сумма баллов.
     *
     * Следствие, которое стоит назвать вслух: нуклид, у которого в библиотеке
     * ОДНА линия (Cs-137, K-40, Am-241), не может получить [EvidenceClass.SUPPORTED]
     * по одному спектру. Это не дефект — подтверждать его нечем, и притворяться,
     * что одна совпавшая энергия сильнее, чем она есть, движок не будет.
     */
    private fun classify(
        matches: List<EnergyMatch>,
        ambiguities: List<ResolutionAmbiguity>,
        contradictions: List<Contradiction>,
    ): EvidenceClass = when {
        contradictions.isNotEmpty() -> EvidenceClass.CONTRADICTED
        // Ни одной линии, которую прибор способен отличить от чужой.
        ambiguities.size == matches.size -> EvidenceClass.AMBIGUOUS
        matches.size >= 2 -> EvidenceClass.SUPPORTED
        else -> EvidenceClass.WEAK
    }
}
