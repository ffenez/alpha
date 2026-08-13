package app.radiacode.analysis.evidence

import kotlin.math.sqrt

/** Геометрия, в которой измерен относительный отклик. */
enum class ResponseGeometry {
    /**
     * Источник РАСПРЕДЕЛЁН в стенах и грунте, между ним и кристаллом лежит
     * бетон. Отклик, измеренный так, содержит и эффективность детектора, и
     * ослабление в материале, и вклад рассеянных фотонов — разделить их
     * нечем.
     */
    DISTRIBUTED_BACKGROUND,
}

/** Почему количественная проверка отношений НЕ выполняется. */
enum class ResponseRefusal {
    /** Ни у одного нуклида не измерено двух линий. */
    NO_SINGLE_NUCLIDE_PAIR,

    /**
     * Спрошено про точечный источник рядом с прибором. Отклик, измеренный на
     * фоне стен, к такой геометрии неприменим: телесный угол, ослабление и
     * доля рассеянного излучения другие.
     */
    POINT_GEOMETRY_NOT_COVERED,
}

/**
 * Точка относительного отклика: отношение откликов на двух линиях ОДНОГО
 * нуклида.
 *
 * @param ratio ε(E_выс)/ε(E_низ) — отношение откликов, безразмерное
 * @param sigma 1σ отношения по правилу частного
 * @param observedAreaRatio наблюдённое отношение нетто-площадей
 * @param yieldRatio табличное отношение выходов
 */
data class RelativeResponsePoint(
    val nuclide: String,
    val lowerKeV: Double,
    val upperKeV: Double,
    val ratio: Double,
    val sigma: Double,
    val observedAreaRatio: Double,
    val yieldRatio: Double,
    val geometry: ResponseGeometry,
)

/**
 * Частичный относительный отклик БЕЗ единого поверочного источника.
 *
 * ## Почему это вообще возможно
 *
 * Две линии одного нуклида излучаются одними и теми же ядрами: на каждый
 * распад приходится Iγ₁ фотонов одной энергии и Iγ₂ другой, и это отношение
 * задано схемой распада. Активность в отношение не входит — она сокращается.
 * Геометрия не входит тоже, пока обе линии приходят из одного объёма. Поэтому
 *
 *     ε(E₂)/ε(E₁) = (A₂/A₁) / (Iγ₂/Iγ₁)
 *
 * — измеримая величина, для которой не нужно знать ни активность стен, ни
 * расстояние до них. В нашей библиотеке такая пара ровно одна: Bi-214
 * 1120,3 и 1764,5 кэВ.
 *
 * ## Чем это НЕ является
 *
 * Полученное число — отклик на РАСПРЕДЕЛЁННЫЙ источник вместе с ослаблением
 * в бетоне и вкладом рассеянных фотонов. К близкому точечному источнику оно
 * неприменимо: там другой телесный угол, нет слоя материала между источником
 * и кристаллом и другая доля рассеяния. Для точечной геометрии
 * количественная проверка отношений остаётся отказом
 * ([ResponseRefusal.POINT_GEOMETRY_NOT_COVERED]) — ровно как в ADR 006, где
 * [IntensityConsistencyEvaluator] возвращает `NotEvaluated` без модели
 * эффективности.
 *
 * Вторая граница: точек мало и все они выше 1 МэВ. Кривой ε(E) отсюда не
 * получается, поэтому отрицательное доказательство ADR 006 остаётся
 * односторонним.
 */
object RelativeResponseEstimator {

    /** Минимальное расстояние между линиями пары, кэВ — **инженерный параметр**. */
    const val MIN_SEPARATION_KEV = 300.0

    /**
     * Все пары линий одного нуклида среди измеренных, от низкой энергии к
     * высокой. Пустой список — [ResponseRefusal.NO_SINGLE_NUCLIDE_PAIR].
     */
    fun estimate(measurements: List<MeasuredLine>): List<RelativeResponsePoint> {
        val byNuclide = measurements.groupBy { it.line.nuclide }
        val points = mutableListOf<RelativeResponsePoint>()
        for ((nuclide, lines) in byNuclide) {
            val sorted = lines.sortedBy { it.line.energyKeV }
            for (i in sorted.indices) {
                for (j in (i + 1) until sorted.size) {
                    point(nuclide, sorted[i], sorted[j])?.let(points::add)
                }
            }
        }
        return points.sortedWith(compareBy({ it.nuclide }, { it.lowerKeV }))
    }

    private fun point(
        nuclide: String,
        lower: MeasuredLine,
        upper: MeasuredLine,
    ): RelativeResponsePoint? {
        if (upper.line.energyKeV - lower.line.energyKeV < MIN_SEPARATION_KEV) return null
        val yieldLow = lower.line.intensityPercent
        val yieldHigh = upper.line.intensityPercent
        if (!(yieldLow > 0.0) || !(yieldHigh > 0.0)) return null
        if (!(lower.netArea > 0.0) || !(upper.netArea > 0.0)) return null
        val areaRatio = upper.netArea / lower.netArea
        val yieldRatio = yieldHigh / yieldLow
        val ratio = areaRatio / yieldRatio
        // Правило частного: (σ_R/R)² = (σ₁/A₁)² + (σ₂/A₂)². Неопределённости
        // табличных выходов источник не дал (ADR 006), поэтому в σ входит
        // только статистика площадей — число является оценкой снизу.
        val relative = sqrt(
            square(upper.netAreaSigma / upper.netArea) +
                square(lower.netAreaSigma / lower.netArea),
        )
        return RelativeResponsePoint(
            nuclide = nuclide,
            lowerKeV = lower.line.energyKeV,
            upperKeV = upper.line.energyKeV,
            ratio = ratio,
            sigma = ratio * relative,
            observedAreaRatio = areaRatio,
            yieldRatio = yieldRatio,
            geometry = ResponseGeometry.DISTRIBUTED_BACKGROUND,
        )
    }

    /**
     * Применим ли измеренный отклик к геометрии вопроса. Для точечного
     * источника рядом с прибором — нет, и это отказ, а не оговорка мелким
     * шрифтом.
     */
    fun refusalForPointSource(): ResponseRefusal = ResponseRefusal.POINT_GEOMETRY_NOT_COVERED

    private fun square(x: Double) = x * x
}
