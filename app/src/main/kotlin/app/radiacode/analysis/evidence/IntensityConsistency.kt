package app.radiacode.analysis.evidence

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Кривая относительной эффективности регистрации полного поглощения.
 *
 * Единственный вход, через который количественная проверка отношений линий
 * вообще может включиться. Реализации у приложения СЕЙЧАС НЕТ, и подставлять
 * правдоподобную формулу нельзя: между 600 и 1500 кэВ у кристалла такого
 * размера эффективность отличается в разы, поэтому выдуманная ε превратила бы
 * честную оговорку в уверенный неверный вывод.
 *
 * Появится она из измерения: источники с известной активностью в фиксированной
 * геометрии, снятые тем же прибором.
 */
fun interface DetectorEfficiencyModel {
    /** Относительная эффективность на энергии; null — вне области применимости. */
    fun relativeEfficiency(energyKeV: Double): Estimate?
}

/** Наблюдённое отношение нетто-площадей двух линий. */
data class ObservedRatio(
    val numerator: EnergyMatch,
    val denominator: EnergyMatch,
    /** A₁/A₂ — сырое отношение, показывается всегда. */
    val observed: Double,
    /** 1σ отношения по правилу частного независимых величин. */
    val sigma: Double,
    /** Iγ₁/Iγ₂ — отношение табличных выходов БЕЗ поправки на эффективность. */
    val expectedByYield: Double,
)

/** Причина, по которой количественная проверка не выполнялась. */
enum class NotEvaluatedReason {
    /** Нет измеренной кривой эффективности — основной случай сегодня. */
    NO_EFFICIENCY_MODEL,

    /** Совпала одна линия: отношение строить не из чего. */
    TOO_FEW_MATCHED_LINES,

    /** Кривая есть, но не покрывает энергии этих линий. */
    EFFICIENCY_OUT_OF_RANGE,
}

/** Результат проверки отношений интенсивностей. */
sealed interface IntensityConsistency {

    /** Сырые отношения показываются в любом случае — это измерение. */
    val ratios: List<ObservedRatio>

    /** Количественного вывода нет; названа причина. */
    data class NotEvaluated(
        val reason: NotEvaluatedReason,
        override val ratios: List<ObservedRatio>,
    ) : IntensityConsistency

    /** Кривая эффективности была, вывод количественный. */
    data class Evaluated(
        override val ratios: List<ObservedRatio>,
        /** z по каждому отношению: (R_obs − R_exp)/σ. */
        val z: List<Double>,
        val consistent: Boolean,
    ) : IntensityConsistency
}

/**
 * Проверка отношений нетто-площадей.
 *
 * Сырое отношение R_obs = A₁/A₂ считается ВСЕГДА — это измерение, и человек
 * имеет право его видеть. Количественный вывод делается только при наличии
 * [DetectorEfficiencyModel]: физически ожидаемое отношение равно
 * (Iγ₁·ε(E₁))/(Iγ₂·ε(E₂)), и без ε сравнение R_obs с Iγ₁/Iγ₂ было бы
 * сравнением измеренного с величиной, которой ничто не соответствует.
 */
object IntensityConsistencyEvaluator {

    /** Порог согласия по |z| — **инженерный параметр** того же рода, что в [EnergyMatching]. */
    const val MAX_ACCEPTABLE_Z = 3.0

    /**
     * σ отношения по правилу частного независимых величин:
     * (σ_R/R)² = (σ₁/A₁)² + (σ₂/A₂)².
     *
     * Независимость — ДОПУЩЕНИЕ: обе площади получены из одного спектра и
     * делят с ним оценку континуума, поэтому σ здесь оценка снизу.
     */
    fun sigmaOfRatio(a: Double, sigmaA: Double, b: Double, sigmaB: Double): Double {
        if (a <= 0.0 || b <= 0.0) return Double.NaN
        val r = a / b
        return r * sqrt((sigmaA / a) * (sigmaA / a) + (sigmaB / b) * (sigmaB / b))
    }

    fun evaluate(
        matches: List<EnergyMatch>,
        efficiency: DetectorEfficiencyModel? = null,
    ): IntensityConsistency {
        if (matches.size < 2) {
            return IntensityConsistency.NotEvaluated(
                NotEvaluatedReason.TOO_FEW_MATCHED_LINES,
                emptyList(),
            )
        }
        val ordered = matches.sortedByDescending { it.peak.netArea }
        val denominator = ordered.first()
        val ratios = ordered.drop(1).mapNotNull { numerator -> ratioOf(numerator, denominator) }
        if (ratios.isEmpty()) {
            return IntensityConsistency.NotEvaluated(
                NotEvaluatedReason.TOO_FEW_MATCHED_LINES,
                emptyList(),
            )
        }
        if (efficiency == null) {
            return IntensityConsistency.NotEvaluated(
                NotEvaluatedReason.NO_EFFICIENCY_MODEL,
                ratios,
            )
        }
        val z = mutableListOf<Double>()
        for (ratio in ratios) {
            val epsNum = efficiency.relativeEfficiency(ratio.numerator.line.energyKeV)?.value
            val epsDen = efficiency.relativeEfficiency(ratio.denominator.line.energyKeV)?.value
            if (epsNum == null || epsDen == null || epsDen <= 0.0) {
                return IntensityConsistency.NotEvaluated(
                    NotEvaluatedReason.EFFICIENCY_OUT_OF_RANGE,
                    ratios,
                )
            }
            // Неопределённость табличных выходов не входит в z: у линий
            // библиотеки её нет (см. LibraryLine). Когда появится источник с
            // неопределённостями, её место — в знаменателе рядом с σ_R.
            val expected = ratio.expectedByYield * (epsNum / epsDen)
            z += (ratio.observed - expected) / ratio.sigma
        }
        return IntensityConsistency.Evaluated(
            ratios = ratios,
            z = z,
            consistent = z.all { it.isFinite() && abs(it) <= MAX_ACCEPTABLE_Z },
        )
    }

    private fun ratioOf(numerator: EnergyMatch, denominator: EnergyMatch): ObservedRatio? {
        val a = numerator.peak.netArea
        val b = denominator.peak.netArea
        if (a <= 0.0 || b <= 0.0) return null
        if (denominator.line.intensityPercent <= 0.0) return null
        return ObservedRatio(
            numerator = numerator,
            denominator = denominator,
            observed = a / b,
            sigma = sigmaOfRatio(a, numerator.peak.netAreaUncertainty, b, denominator.peak.netAreaUncertainty),
            expectedByYield = numerator.line.intensityPercent / denominator.line.intensityPercent,
        )
    }
}
