package app.alpha.analysis.evidence

import app.alpha.analysis.EnergyCalibration
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Континуум спектра: сколько импульсов приходится на 1 кэВ около энергии.
 *
 * Нужен ровно для одного вопроса — «а была ли эта линия вообще обязана быть
 * видна?». Без континуума ответить на него нельзя, и тогда отсутствие линии не
 * является доказательством ничего.
 */
fun interface ContinuumModel {
    /** Импульсов на 1 кэВ у [energyKeV]; null — область вне спектра. */
    fun countsPerKeV(energyKeV: Double): Double?
}

/**
 * Континуум прямо по гистограмме прибора: среднее число импульсов на канал в
 * окне ±FWHM вокруг энергии, пересчитанное в импульсы на кэВ.
 *
 * Оценка грубая и СОЗНАТЕЛЬНО не вычитает пики: она применяется там, где линии
 * как раз НЕТ, а если пик всё же есть — оценка завышается, порог обнаружения
 * растёт, и движок становится осторожнее, а не смелее.
 */
class HistogramContinuum(
    private val counts: List<Int>,
    private val calibration: EnergyCalibration,
    private val resolution: ResolutionModel,
) : ContinuumModel {

    override fun countsPerKeV(energyKeV: Double): Double? {
        if (counts.isEmpty()) return null
        val center = calibration.channelAt(energyKeV.toFloat()).toDouble()
        if (!center.isFinite() || center < 0 || center >= counts.size) return null
        val keVPerChannel = keVPerChannelAt(center)
        if (keVPerChannel <= 0.0) return null
        val halfWindow = (resolution.fwhmKeV(energyKeV) / keVPerChannel).roundToInt().coerceAtLeast(1)
        val from = (center.roundToInt() - halfWindow).coerceAtLeast(0)
        val to = (center.roundToInt() + halfWindow).coerceAtMost(counts.size - 1)
        if (to < from) return null
        var sum = 0.0
        for (i in from..to) sum += counts[i]
        val perChannel = sum / (to - from + 1)
        return perChannel / keVPerChannel
    }

    /** Ширина канала в кэВ — производная калибровочного полинома. */
    private fun keVPerChannelAt(channel: Double): Double =
        (calibration.a1 + 2f * calibration.a2 * channel.toFloat()).toDouble()
}

/**
 * Ожидалась ли линия ВИДИМОЙ. Отсутствие линии становится отрицательным
 * доказательством только в состоянии [EXPECTED_OBSERVABLE]; во всех остальных
 * «не видно» означает «нечем судить», и это разные вещи.
 */
enum class LineObservability {
    /** Линия найдена в спектре. */
    OBSERVED,

    /** Линия обязана была быть видна, и её отсутствие — довод против кандидата. */
    EXPECTED_OBSERVABLE,

    /** Ожидаемая площадь ниже порога обнаружения на этой статистике. */
    BELOW_DETECTION_LIMIT,

    /** Энергия вне шкалы прибора — вопрос о видимости не поставлен. */
    OUT_OF_RANGE,

    /** Судить не о чем: нет опорной линии, континуума или модели эффективности. */
    UNDETERMINED,
}

/**
 * Порог обнаружения линии на имеющемся континууме.
 *
 * Используется формула Карри (L. A. Currie, Anal. Chem. 40 (1968) 586):
 * предел обнаружения L_D = 2,71 + 4,65·√B импульсов, где B — фоновые импульсы
 * в области пика. Допущение формулы — фон известен точно; у нас он оценён по
 * тому же спектру, поэтому реальный порог ВЫШЕ расчётного, и вывод «линия
 * обязана быть видна» остаётся осторожным ровно в нужную сторону.
 *
 * Область пика берётся шириной в одну FWHM — то же окно, по которому
 * [app.alpha.analysis.PeakDetection] считает нетто-площадь. Совпадение
 * окон обязательно: иначе порог считался бы для одной области, а площадь — для
 * другой.
 */
object DetectionLimit {

    fun currieCounts(backgroundCounts: Double): Double =
        2.71 + 4.65 * sqrt(maxOf(backgroundCounts, 0.0))

    /** Фоновые импульсы в области пика; null — континуум в этой точке неизвестен. */
    fun backgroundCounts(
        energyKeV: Double,
        continuum: ContinuumModel,
        resolution: ResolutionModel,
    ): Double? {
        val perKeV = continuum.countsPerKeV(energyKeV) ?: return null
        if (!perKeV.isFinite() || perKeV < 0.0) return null
        return perKeV * resolution.fwhmKeV(energyKeV)
    }
}

/**
 * Правило «должна ли была эта линия быть видна».
 *
 * Ожидаемая площадь линии считается от УЖЕ НАЙДЕННОЙ линии того же нуклида:
 * A_pred = A_ref · (Iγ / Iγ_ref) · (ε(E) / ε(E_ref)).
 *
 * Отношение эффективностей неизвестно — измеренной кривой у нас нет. Поэтому
 * без модели эффективности правило пользуется ОДНОСТОРОННИМ доводом, а не
 * подставляет ε = 1:
 *
 *  - эффективность полного поглощения у малого сцинтиллятора выше 150 кэВ
 *    убывает с ростом энергии (сечение фотоэффекта падает круче, чем растёт
 *    комптоновский вклад) — это качественное свойство, а не наша подгонка;
 *  - значит для линии НИЖЕ опорной ε(E) ≥ ε(E_ref), и A_ref·(Iγ/Iγ_ref) —
 *    оценка СНИЗУ: если даже она выше порога обнаружения, линия обязана была
 *    быть видна;
 *  - для линии ВЫШЕ опорной то же число — оценка СВЕРХУ: если даже она ниже
 *    порога, линию нельзя было увидеть, и её отсутствие ничего не значит;
 *  - во всех прочих случаях ответ [LineObservability.UNDETERMINED].
 *
 * Ниже 150 кэВ довод не применяется вовсе: там на ход ε(E) влияет поглощение в
 * корпусе и окне детектора, и монотонности нет.
 */
object LineObservabilityRule {

    /** Ниже этой энергии односторонний довод о ходе ε(E) не используется. */
    const val EFFICIENCY_MONOTONE_MIN_KEV = 150.0

    fun evaluate(
        line: LibraryLine,
        referenceLine: LibraryLine,
        referenceArea: Double,
        continuum: ContinuumModel?,
        resolution: ResolutionModel,
        efficiency: DetectorEfficiencyModel? = null,
        energyRangeKeV: ClosedFloatingPointRange<Double>,
    ): LineObservability {
        if (line.energyKeV !in energyRangeKeV) return LineObservability.OUT_OF_RANGE
        if (continuum == null) return LineObservability.UNDETERMINED
        if (referenceLine.intensityPercent <= 0.0 || referenceArea <= 0.0) {
            return LineObservability.UNDETERMINED
        }
        val background = DetectionLimit.backgroundCounts(line.energyKeV, continuum, resolution)
            ?: return LineObservability.UNDETERMINED
        val limit = DetectionLimit.currieCounts(background)
        val byYield = referenceArea * (line.intensityPercent / referenceLine.intensityPercent)

        val epsilon = efficiency?.relativeEfficiency(line.energyKeV)?.value
        val epsilonRef = efficiency?.relativeEfficiency(referenceLine.energyKeV)?.value
        if (epsilon != null && epsilonRef != null && epsilonRef > 0.0) {
            val predicted = byYield * (epsilon / epsilonRef)
            return if (predicted > limit) {
                LineObservability.EXPECTED_OBSERVABLE
            } else {
                LineObservability.BELOW_DETECTION_LIMIT
            }
        }

        val bothAboveThreshold = line.energyKeV >= EFFICIENCY_MONOTONE_MIN_KEV &&
            referenceLine.energyKeV >= EFFICIENCY_MONOTONE_MIN_KEV
        if (!bothAboveThreshold) return LineObservability.UNDETERMINED
        return when {
            // Оценка снизу выше порога — линию обязаны были увидеть.
            line.energyKeV <= referenceLine.energyKeV && byYield > limit ->
                LineObservability.EXPECTED_OBSERVABLE
            // Оценка сверху ниже порога — увидеть её было нечем.
            line.energyKeV >= referenceLine.energyKeV && byYield < limit ->
                LineObservability.BELOW_DETECTION_LIMIT
            else -> LineObservability.UNDETERMINED
        }
    }
}
