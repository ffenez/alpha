package app.radiacode.analysis.evidence

/**
 * Неопределённость энергетической шкалы, измеренная по остаткам.
 *
 * @param sigmaKeV разброс остатков сверх статистического, кэВ
 * @param sigmaFraction тот же разброс в долях энергии — им заменяется
 *   инженерная [EnergyMatching.CALIBRATION_SIGMA_FRACTION]
 * @param statisticalOnly `true` — разброс остатков полностью объясняется
 *   статистикой центроидов; тогда σ_cal это ВЕРХНЯЯ ГРАНИЦА, а не измерение
 * @param shiftKeV систематический сдвиг (взвешенное среднее остатков)
 * @param shiftUncertaintyKeV 1σ этого сдвига
 * @param lowestEnergyKeV самая низкая энергия, на которой измерялось
 */
data class ScaleUncertainty(
    val residuals: List<CalibrationResidual>,
    val sigmaKeV: Double,
    val sigmaFraction: Double,
    val statisticalOnly: Boolean,
    val shiftKeV: Double?,
    val shiftUncertaintyKeV: Double?,
    val verdict: CalibrationVerdict,
    val lowestEnergyKeV: Double,
    val highestEnergyKeV: Double,
)

/**
 * σ_cal из остатков ΔE = E_набл − E_табл.
 *
 * ## Что такое σ_cal и почему её нельзя взять из статистики центроидов
 *
 * Центроид сильного пика известен с точностью долей кэВ, но линия всё равно
 * оказывается не там, где написано в таблице: заводская калибровка задана
 * тремя коэффициентами на весь диапазон, усиление плывёт с температурой,
 * отклик кристалла нелинеен. Разброс остатков ВОКРУГ СРЕДНЕГО и есть эта
 * неопределённость — то, чего статистика измерения не объясняет.
 *
 * Поэтому считается ИЗБЫТОЧНАЯ дисперсия: s² − ⟨σ_стат²⟩. Если разброс
 * остатков не превышает статистического, честный ответ не «σ_cal = 0», а
 * «σ_cal не больше наблюдаемого разброса» — так и помечается флагом
 * [ScaleUncertainty.statisticalOnly].
 *
 * ## Почему σ_cal получается завышенной
 *
 * В остаток входит предсказанный сдвиг слияния ([CalibrationLineCandidate]):
 * у 1764,5 кэВ соседняя линия того же Bi-214 сдвигает центроид на единицы
 * кэВ по ядерным данным, а не по вине шкалы. Вычитать этот сдвиг мы не стали
 * — он сам опирается на предположение о вековом равновесии. Ошибка идёт в
 * сторону БОЛЕЕ ШИРОКОГО окна совпадений, и это ровно та сторона, в которую
 * в этом приложении ошибаться безопасно.
 *
 * ## Автокоррекции нет
 *
 * Ни здесь, ни где-либо ещё шкала не правится. Причина в ADR 006: сдвиг
 * оценён по совпадениям, полученным при той же калибровке, и тихая коррекция
 * замкнула бы петлю.
 */
object ScaleUncertaintyEstimator {

    /**
     * Минимум остатков для оценки РАЗБРОСА — **инженерный параметр**. По двум
     * точкам выборочное стандартное отклонение существует формально, но
     * определяется одним числом и не отличает разброс от одной промашки.
     */
    const val MIN_RESIDUALS_FOR_SCATTER = 3

    fun estimate(measurements: List<MeasuredLine>): ScaleUncertainty? {
        val usable = measurements.filter {
            it.observedSigmaKeV.isFinite() && it.observedSigmaKeV > 0.0
        }
        if (usable.size < MIN_RESIDUALS_FOR_SCATTER) return null
        val residuals = usable.map {
            CalibrationResidual(
                energyKeV = it.line.energyKeV,
                deltaKeV = it.deltaKeV,
                sigmaKeV = maxOf(
                    it.observedSigmaKeV,
                    CalibrationDiagnostics.MIN_RESIDUAL_SIGMA_KEV,
                ),
            )
        }
        val diagnostic = CalibrationDiagnostics.evaluate(residuals)
        val mean = residuals.sumOf { it.deltaKeV } / residuals.size
        val scatter = residuals.sumOf { (it.deltaKeV - mean) * (it.deltaKeV - mean) } /
            (residuals.size - 1)
        val statistical = residuals.sumOf { it.sigmaKeV * it.sigmaKeV } / residuals.size
        val excess = scatter - statistical
        val statisticalOnly = excess <= 0.0
        val sigma = kotlin.math.sqrt(if (statisticalOnly) scatter else excess)
        val relativeMean = residuals.sumOf { it.deltaKeV / it.energyKeV } / residuals.size
        val relativeScatter = residuals.sumOf {
            val r = it.deltaKeV / it.energyKeV - relativeMean
            r * r
        } / (residuals.size - 1)
        return ScaleUncertainty(
            residuals = residuals,
            sigmaKeV = sigma,
            sigmaFraction = kotlin.math.sqrt(relativeScatter),
            statisticalOnly = statisticalOnly,
            shiftKeV = diagnostic.shiftKeV,
            shiftUncertaintyKeV = diagnostic.shiftUncertaintyKeV,
            verdict = diagnostic.verdict,
            lowestEnergyKeV = residuals.minOf { it.energyKeV },
            highestEnergyKeV = residuals.maxOf { it.energyKeV },
        )
    }
}
