package app.alpha.ui.logic

import app.alpha.analysis.GammaLineLibrary
import app.alpha.analysis.Nuclide
import app.alpha.analysis.NuclideGammaLine
import app.alpha.analysis.NuclideOrigin
import app.alpha.analysis.evidence.DataSource
import app.alpha.analysis.evidence.EvidenceClass
import app.alpha.analysis.evidence.LineObservability
import app.alpha.ui.text.NuclideRu
import app.alpha.ui.text.NuclideStrings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Статус карточки — потолок осторожности задан спецификацией (§12). */
enum class NuclideCardStatus {
    /** Что-то совпало, но совпадение никогда не становится обнаружением. */
    POSSIBLE_MATCH,

    /** Ожидаемая линия не выделена либо отношения разошлись — противоречие. */
    NOT_CONFIRMED,

    /** Все совпавшие линии прибор не отличает от чужих — победителя нет. */
    AMBIGUOUS,

    /** Спектра нет: карточка открыта как справочник. «Не оценивалось» ≠ «нет». */
    NOT_EVALUATED,
}

/** Тон статуса: ровно те же три роли, что у `WhyTone` (без «опасно»). */
enum class NuclideCardTone { DATA, UNCERTAIN, NEUTRAL }

data class NuclideStatusBlock(
    val status: NuclideCardStatus,
    val tone: NuclideCardTone,
    /** «ВОЗМОЖНОЕ СОВПАДЕНИЕ» — короткая шапка статуса. */
    val headline: String,
    /** Одна-две фразы: что именно совпало и почему этого мало. */
    val detail: String,
)

/**
 * Результат по одной ожидаемой линии — РОВНО то, что сказал движок.
 * [NOT_EVALUATED] существует отдельно от [NOT_FOUND] сознательно: спектр,
 * который не сопоставляли, не является спектром без линии. [UNDETERMINED] —
 * тоже отдельно: линию искали, но ждать ли её видимой, оценить нечем (нет
 * континуума или опорной линии), и «не найдена» здесь ничего не доказывает.
 */
enum class NuclideLineVerdict {
    MATCHED, NOT_FOUND, INDISTINGUISHABLE, UNDETERMINED, OUT_OF_SCALE, NOT_EVALUATED,
}

/**
 * Строка таблицы «Проверка по линиям».
 *
 * [energyKeV] отдаётся наружу намеренно: строка НАЖИМАЕТСЯ и показывает эту
 * энергию отметкой на спектре — модель уже знает число, экрану не придётся его
 * пересчитывать. [actionLabel] — что произойдёт по нажатию: строка таблицы сама
 * по себе не выглядит кнопкой, и экранный диктор обязан назвать действие.
 */
data class NuclideLineRow(
    val energyKeV: Float,
    val energyText: String,
    val actionLabel: String,
    val yieldText: String,
    val verdict: NuclideLineVerdict,
    val verdictText: String,
    /**
     * Измеренная энергия пика — только если её знает движок ([CheckedLine.measuredKeV]).
     * Null означает «не знаем», и UI обязан молчать, а не выдумывать ΔE.
     */
    val measuredKeV: Float?,
    /** «пик 1109,3 кэВ · ΔE −11,0 кэВ»; null там же, где null [measuredKeV]. */
    val measuredText: String?,
)

/** Строка блока «О нуклиде»: подпись слева, значение справа. */
data class NuclideFact(val label: String, val value: String)

/** Provenance одной библиотечной линии — источник и его неопределённости. */
/**
 * Откуда взяты числа линий — ОДНОЙ строкой.
 *
 * Раскрывающийся раздел «Источник и неопределённости» с построчным списком
 * снят: он повторял для каждой линии одно и то же — тот же источник и тот же
 * честный отказ назвать неопределённость, которой в выборке ENSDF нет. Само
 * утверждение об источнике осталось: числа в справочной карточке обязаны
 * называть, откуда они, иначе это не справка.
 */
data class NuclideProvenance(
    /** «Данные линий: ENSDF (IAEA Live Chart / NNDC NuDat 3)». */
    val summary: String,
)

/** Всё, что показывает карточка, сверху вниз. Собирается чистой функцией. */
data class NuclideCardModel(
    val title: String,
    val status: NuclideStatusBlock,
    val sectionLineCheck: String,
    val columnLine: String,
    val columnYield: String,
    val columnResult: String,
    val lineCheck: List<NuclideLineRow>,
    /** Строки таблицы нажимаются — сказано один раз под таблицей, а не в каждой. */
    val lineTapHint: String,
    /**
     * Отношение нетто-площадей двух найденных линий: наблюдаемое с 1σ,
     * табличное — и прямая оговорка, почему их нельзя сравнивать между собой.
     * Пусто, когда найдено меньше двух линий.
     */
    val ratio: List<String>,
    val yieldNote: String,
    val sectionAbout: String,
    val about: List<NuclideFact>,
    val sectionEveryday: String,
    val everyday: String,
    val sectionStrengthen: String,
    val strengthen: List<String>,
    /** Особенность именно этого нуклида — то, чего из таблицы не вывести. */
    val strengthenNote: String,
    val sectionLimitation: String,
    val limitation: String,
    val allLinesLabel: String,
    val allLines: List<String>,
    val provenance: NuclideProvenance,
)

/** Все тексты карточки — для проверок, действующих на каждую формулировку. */
fun NuclideCardModel.allTexts(): List<String> =
    listOf(
        title, status.headline, status.detail,
        sectionLineCheck, columnLine, columnYield, columnResult, yieldNote, lineTapHint,
        sectionAbout, sectionEveryday, everyday,
        sectionStrengthen, strengthenNote, sectionLimitation, limitation,
        allLinesLabel, provenance.summary,
    ) +
        ratio +
        lineCheck.flatMap {
            listOfNotNull(it.energyText, it.actionLabel, it.yieldText, it.verdictText, it.measuredText)
        } +
        about.flatMap { listOf(it.label, it.value) } +
        strengthen + allLines

/**
 * Сборка справки о нуклиде, открываемой из строки кандидата на Спектре.
 *
 * ## Главное правило
 *
 * **У карточки нет собственной логики «найдена / не найдена».** Она печатает
 * тот самый [NuclideCheck], который [PeakEvidenceBridge] построил из вердикта
 * движка доказательств для строки кандидата: читаются только `classification`,
 * `foundLines`, `ambiguousWith` и поля каждой [CheckedLine]. Ни одна энергия
 * здесь не сравнивается с другой заново — иначе список и справка со временем
 * разошлись бы, и человек получил бы два разных ответа на один вопрос.
 *
 * ## Границы формулировок (пинятся тестами)
 *
 *  - §12 — потолок «возможное совпадение»; слово «обнаружен» не пишется ни на
 *    одном языке, и «не оценивалось» никогда не превращается в «не найдено»;
 *  - §23 — никакой безопасности, вреда и доз: карточка про нуклид, а не про
 *    состояние места;
 *  - отношения интенсивностей без кривой эффективности детектора не выносятся
 *    в вывод (`DetectorEfficiency.AVAILABLE = false`).
 */
object NuclideCard {

    fun title(nuclide: Nuclide): String = "${nuclide.symbol} · ${nuclide.name}"

    /** «природный · ряд Th-232» / «искусственный». */
    fun originLine(nuclide: Nuclide, s: NuclideStrings = NuclideRu): String {
        val origin = when (nuclide.origin) {
            NuclideOrigin.NATURAL -> s.originNatural
            NuclideOrigin.ARTIFICIAL -> s.originArtificial
        }
        return nuclide.chain?.let { s.originWithChain(origin, it) } ?: origin
    }

    /**
     * «609,3 кэВ · 45,5 % на распад». Десятичная запятая одна на все языки:
     * числа карточки печатает общий форматтер приложения.
     */
    fun lineText(energyKeV: Float, intensityPercent: Float, s: NuclideStrings = NuclideRu): String =
        s.gammaLine(decimal(energyKeV, 1), decimal(intensityPercent, 1))

    /**
     * @param check результат ТОГО ЖЕ разбора движка, что дал кандидата в
     *   таблице ([PeakEvidenceBridge.analyse]); null — карточка открыта без
     *   спектра, и статус это честно говорит.
     */
    fun build(
        nuclide: Nuclide,
        check: NuclideCheck?,
        s: NuclideStrings = NuclideRu,
    ): NuclideCardModel = NuclideCardModel(
        title = title(nuclide),
        status = status(check, s),
        sectionLineCheck = s.sectionLineCheck,
        columnLine = s.columnLine,
        columnYield = s.columnYield,
        columnResult = s.columnResult,
        lineCheck = lineRows(nuclide, check, s),
        lineTapHint = s.lineShowHint,
        ratio = ratioLines(check, s),
        yieldNote = s.yieldNote,
        sectionAbout = s.sectionAbout,
        about = listOf(
            NuclideFact(s.labelOrigin, originLine(nuclide, s)),
            NuclideFact(s.labelHalfLife, nuclide.halfLife),
            NuclideFact(s.labelDecay, nuclide.decay),
        ),
        sectionEveryday = s.sectionEveryday,
        everyday = nuclide.everyday,
        sectionStrengthen = s.sectionConfirmation,
        strengthen = strengthen(nuclide, check, s),
        strengthenNote = nuclide.confirmation,
        sectionLimitation = s.sectionLimitation,
        limitation = s.limits,
        allLinesLabel = s.allLinesLabel,
        allLines = nuclide.lines.map { lineText(it.energyKeV, it.intensityPercent, s) },
        provenance = provenance(nuclide, s),
    )

    /**
     * Статусный блок. Считается ИСКЛЮЧИТЕЛЬНО по полям результата движка:
     * `classification` решает формулировку, `foundLines`/`lines.size` дают
     * счёт. Своего критерия «сколько линий достаточно» у карточки нет.
     */
    private fun status(c: NuclideCheck?, s: NuclideStrings): NuclideStatusBlock {
        if (c == null) {
            return NuclideStatusBlock(
                status = NuclideCardStatus.NOT_EVALUATED,
                tone = NuclideCardTone.NEUTRAL,
                headline = s.statusNotEvaluated,
                detail = s.statusNotEvaluatedDetail,
            )
        }
        val counted = s.matchedOfChecked(c.foundLines, c.lines.size)
        return when (c.classification) {
            EvidenceClass.CONTRADICTED -> {
                // Названы ровно две линии: совпавшая и та ожидаемая заметная,
                // которой нет. Обе берутся из результата, а не ищутся заново.
                val matched = c.lines
                    .filter { it.measuredKeV != null }
                    .maxByOrNull { it.intensityPercent }
                val missing = c.lines
                    .filter {
                        it.measuredKeV == null &&
                            it.observability == LineObservability.EXPECTED_OBSERVABLE
                    }
                    .maxByOrNull { it.intensityPercent }
                NuclideStatusBlock(
                    status = NuclideCardStatus.NOT_CONFIRMED,
                    tone = NuclideCardTone.UNCERTAIN,
                    headline = s.statusNotConfirmed,
                    detail = if (matched != null && missing != null) {
                        s.missingStrongLine(
                            matched = round0(matched.energyKeV),
                            missing = round0(missing.energyKeV),
                        )
                    } else {
                        // Противоречие без пропавшей линии — разошлись
                        // отношения площадей (возможно только при модели ε).
                        s.contradictsExpectedLines
                    },
                )
            }
            EvidenceClass.AMBIGUOUS -> NuclideStatusBlock(
                status = NuclideCardStatus.AMBIGUOUS,
                tone = NuclideCardTone.UNCERTAIN,
                headline = s.statusAmbiguous,
                detail = s.ambiguousDetail(
                    (listOf(c.nuclide) + c.ambiguousWith).joinToString(" / "),
                ),
            )
            // Несколько линий — свидетельство сильнее одной, но потолок тот же.
            EvidenceClass.SUPPORTED -> possible(counted, s.multiLineStronger, s)
            EvidenceClass.WEAK -> possible(counted, s.notEnoughToConfirm, s)
        }
    }

    private fun possible(counted: String, tail: String, s: NuclideStrings) = NuclideStatusBlock(
        status = NuclideCardStatus.POSSIBLE_MATCH,
        tone = NuclideCardTone.DATA,
        headline = s.statusPossibleMatch,
        detail = "$counted $tail",
    )

    /**
     * Таблица проверки. Сортировка — по выходу вниз (сильная линия первой):
     * именно её отсутствие что-то значит, поэтому она должна попадаться глазу
     * раньше следовых.
     *
     * Без спектра таблицы проверки нет вовсе — библиотечные линии показываются
     * как справочные строки без вердикта, потому что вердикта никто не выносил.
     */
    private fun lineRows(
        nuclide: Nuclide,
        c: NuclideCheck?,
        s: NuclideStrings,
    ): List<NuclideLineRow> {
        if (c == null) {
            return nuclide.lines
                .sortedByDescending { it.intensityPercent }
                .map { line -> referenceRow(line, s) }
        }
        return c.lines.sortedByDescending { it.intensityPercent }.map { check ->
            val verdict = when {
                check.measuredKeV != null -> NuclideLineVerdict.MATCHED
                check.observability == LineObservability.EXPECTED_OBSERVABLE ->
                    NuclideLineVerdict.NOT_FOUND
                check.observability == LineObservability.BELOW_DETECTION_LIMIT ->
                    NuclideLineVerdict.INDISTINGUISHABLE
                check.observability == LineObservability.OUT_OF_RANGE ->
                    NuclideLineVerdict.OUT_OF_SCALE
                else -> NuclideLineVerdict.UNDETERMINED
            }
            // Измеренная энергия существует ровно тогда, когда движок положил
            // в проверку найденный пик; сами мы пик ни с чем не сопоставляем.
            val measured = check.measuredKeV
            NuclideLineRow(
                energyKeV = check.energyKeV,
                energyText = "${decimal(check.energyKeV, 1)} ${s.unitKeV}",
                actionLabel = s.lineShowAction(decimal(check.energyKeV, 1)),
                yieldText = s.lineYield(decimal(check.intensityPercent, 1)),
                verdict = verdict,
                verdictText = verdictText(verdict, s),
                measuredKeV = measured,
                measuredText = measured?.let {
                    s.peakDelta(decimal(it, 1), signed(it - check.energyKeV))
                },
            )
        }
    }

    private fun referenceRow(line: NuclideGammaLine, s: NuclideStrings) = NuclideLineRow(
        energyKeV = line.energyKeV,
        energyText = "${decimal(line.energyKeV, 1)} ${s.unitKeV}",
        actionLabel = s.lineShowAction(decimal(line.energyKeV, 1)),
        yieldText = s.lineYield(decimal(line.intensityPercent, 1)),
        verdict = NuclideLineVerdict.NOT_EVALUATED,
        verdictText = s.lineNotEvaluated,
        measuredKeV = null,
        measuredText = null,
    )

    private fun verdictText(verdict: NuclideLineVerdict, s: NuclideStrings): String =
        when (verdict) {
            NuclideLineVerdict.MATCHED -> s.lineMatched
            NuclideLineVerdict.NOT_FOUND -> s.lineNotFound
            NuclideLineVerdict.INDISTINGUISHABLE -> s.lineTooWeak
            NuclideLineVerdict.UNDETERMINED -> s.lineUndetermined
            NuclideLineVerdict.OUT_OF_SCALE -> s.lineOutOfScale
            NuclideLineVerdict.NOT_EVALUATED -> s.lineNotEvaluated
        }

    /**
     * Отношение площадей двух найденных линий. Вердикта о согласии с табличным
     * отношением НЕТ и быть не может, пока нет измеренной кривой эффективности
     * детектора (`DetectorEfficiency.AVAILABLE = false`): наблюдаемое и
     * табличное стоят рядом и порознь, с названной причиной.
     */
    private fun ratioLines(c: NuclideCheck?, s: NuclideStrings): List<String> {
        val ratios = c?.ratios.orEmpty()
        if (ratios.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        for (ratio in ratios) {
            lines += s.netAreaRatio(
                fromKeV = round0(ratio.fromKeV),
                toKeV = round0(ratio.toKeV),
                value = decimal(ratio.observed.toFloat(), 2),
                sigma = decimal(ratio.sigma.toFloat(), 2),
            )
            if (ratio.expectedByYield.isFinite()) {
                lines += s.expectedByYield(decimal(ratio.expectedByYield.toFloat(), 2))
            }
        }
        lines += s.efficiencyNotCalibrated
        return lines
    }

    /**
     * «Что усилило бы гипотезу» — список, вычисляемый ИЗ БИБЛИОТЕКИ, а не
     * общие слова: называются конкретные ненайденные линии этого нуклида и
     * линии соседей по ряду распада.
     *
     * Чего здесь нет: утверждения, что короткий период полураспада сам по себе
     * подтверждает нуклид. Спад проверяем только СЕРИЕЙ сопоставимых измерений
     * при известных условиях, а одно накопление такой серией не является.
     */
    private fun strengthen(
        nuclide: Nuclide,
        c: NuclideCheck?,
        s: NuclideStrings,
    ): List<String> {
        val bullets = mutableListOf<String>()
        val pending = if (c == null) {
            nuclide.lines.map { it.energyKeV to it.intensityPercent }
        } else {
            c.lines.filter { it.measuredKeV == null }.map { it.energyKeV to it.intensityPercent }
        }
        if (pending.isNotEmpty()) {
            val listed = pending
                .sortedByDescending { it.second }
                .take(3)
                .joinToString(" · ") { decimal(it.first, 1) }
            bullets += s.bulletOtherLines("$listed ${s.unitKeV}")
        } else {
            bullets += s.bulletHoldsUp
        }
        chainNeighbours(nuclide, s)?.let { bullets += s.bulletChainLines(it) }
        if (nuclide.lines.size > 1) bullets += s.bulletNoContradiction
        bullets += s.bulletMoreStatistics
        return bullets
    }

    /** «Pb-214 351,9 кэВ» — самые яркие линии соседей по тому же ряду. */
    private fun chainNeighbours(nuclide: Nuclide, s: NuclideStrings): String? {
        val chain = nuclide.chain ?: return null
        val neighbours = GammaLineLibrary.LINES
            .filter { it.chain == chain && it.isotope != nuclide.symbol }
            .groupBy { it.isotope }
            .mapNotNull { (isotope, lines) ->
                val strongest = lines.maxByOrNull { it.intensityPercent } ?: return@mapNotNull null
                "$isotope ${decimal(strongest.energyKeV, 1)} ${s.unitKeV}"
            }
            .sorted()
        return neighbours.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Provenance берётся из САМИХ данных линий, а не из подписи внизу карточки:
     * источник хранится в [NuclideGammaLine.source], и если завтра часть линий
     * приедет из DDEP, подпись изменится сама.
     */
    private fun provenance(nuclide: Nuclide, s: NuclideStrings): NuclideProvenance {
        val sources = nuclide.lines.map { it.source }.distinct().sorted()
        return NuclideProvenance(
            summary = s.lineDataSource(sources.joinToString(" · ") { sourceName(it, s) }),
        )
    }

    private fun sourceName(source: DataSource, s: NuclideStrings): String = when (source) {
        DataSource.DDEP -> s.sourceDdep
        DataSource.ENSDF -> s.sourceEnsdf
    }

    private fun decimal(value: Float, digits: Int): String =
        String.format(Locale.US, "%.${digits}f", value).replace('.', ',')

    /** «1120» — энергия линии в статусной фразе, где доли кэВ ничего не решают. */
    private fun round0(value: Float): String = value.roundToInt().toString()

    /**
     * ΔE со знаком: «−11,0». Минус — типографский (U+2212), как во всех числах
     * приложения. Точность ΔE ограничена шириной канала и калибровкой шкалы,
     * поэтому число стоит рядом с измеренной энергией, а не отдельно от неё.
     */
    private fun signed(delta: Float): String {
        val text = decimal(abs(delta), 1)
        return if (delta < 0f) "−$text" else "+$text"
    }
}
