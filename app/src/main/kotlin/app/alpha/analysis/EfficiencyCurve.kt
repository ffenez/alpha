package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Кривая эффективности регистрации ε(E) — доля γ-квантов линии, попавших в
 * фотопик.
 *
 * ## Зачем она нужна
 *
 * Площадь фотопика говорит, сколько импульсов зарегистрировано, и ничего не
 * говорит об активности источника: между ними стоит эффективность прибора в
 * ЭТОЙ геометрии. Без ε любое число в беккерелях было бы выдумано, поэтому
 * приложение до сих пор их и не показывало. С измеренной ε активность
 * становится расчётом:
 *
 *     A = N / (t · ε(E) · p)
 *
 * где N — площадь фотопика, t — время накопления, p — квантовый выход линии.
 *
 * ## Модель
 *
 * Общепринятая параметризация: логарифм эффективности — многочлен от
 * логарифма энергии,
 *
 *     ln ε = Σ aᵢ · (ln E)ⁱ
 *
 * (Gray & Ahmad, Nucl. Instr. Meth. A237 (1985) 577; та же форма в ANSI
 * N42.14). Она описывает и рост при малых энергиях, и спад при больших, и
 * линейна по коэффициентам — значит, решается взвешенным МНК, а не поиском.
 *
 * Степень выбирается по числу точек ([orderFor]): подгонять кубику по трём
 * точкам — значит провести кривую точно через них и получить нулевые остатки
 * при нулевом знании.
 *
 * ## Границы
 *
 * - Кривая принадлежит ГЕОМЕТРИИ, в которой снята. Другое расстояние до
 *   источника, другой держатель, другая сторона прибора — другая ε. Ничто в
 *   этом коде не может это проверить, поэтому геометрию называет тот, кто
 *   калибровал.
 * - За пределами диапазона калибровочных точек значение НЕ выдаётся
 *   ([efficiencyAt] возвращает null): экстраполяция логарифмического
 *   многочлена расходится, и «активность» там была бы произвольной.
 * - Самопоглощение в образце и совпадения по каскаду не учитываются: это
 *   поправки к геометрии, которых у приложения нет.
 */
data class EfficiencyCurve(
    /** Коэффициенты aᵢ при (ln E)ⁱ, от нулевой степени. */
    val coefficients: List<Double>,
    /** Ковариационная матрица коэффициентов — из неё σ интерполяции. */
    val covariance: List<List<Double>>,
    /** Нижняя граница калибровки, кэВ. */
    val minEnergyKeV: Double,
    /** Верхняя граница калибровки, кэВ. */
    val maxEnergyKeV: Double,
    /** Точки, по которым построена кривая. */
    val points: List<EfficiencyPoint>,
    /**
     * χ²/ndf подгонки; null — степеней свободы нет (точек ровно столько же,
     * сколько коэффициентов), и согласие не проверяется.
     */
    val reducedChiSquare: Double?,
) {

    /**
     * Эффективность на энергии и её относительная неопределённость.
     *
     * @return null за пределами [minEnergyKeV]…[maxEnergyKeV] — экстраполяция
     *   не выдаётся
     */
    fun efficiencyAt(energyKeV: Double): EfficiencyValue? {
        if (energyKeV < minEnergyKeV || energyKeV > maxEnergyKeV) return null
        if (energyKeV <= 0.0) return null
        val x = basis(ln(energyKeV), coefficients.size)
        var logEfficiency = 0.0
        for (i in coefficients.indices) logEfficiency += coefficients[i] * x[i]
        // σ² (ln ε) = xᵀ·C·x — перенос неопределённости коэффициентов на
        // значение многочлена; относительная неопределённость самой ε равна
        // σ(ln ε) с точностью до второго порядка.
        var variance = 0.0
        for (i in coefficients.indices) {
            for (j in coefficients.indices) variance += x[i] * covariance[i][j] * x[j]
        }
        val efficiency = exp(logEfficiency)
        if (!efficiency.isFinite() || efficiency <= 0.0) return null
        val relative = sqrt(max(variance, 0.0))
        return EfficiencyValue(efficiency, relative.takeIf { it.isFinite() } ?: 0.0)
    }

    companion object {

        /**
         * Степень многочлена по числу точек. **Инженерный параметр**: на точку
         * приходится не больше одного коэффициента минус две степени свободы —
         * иначе кривая проходит через точки и не может не согласиться с ними.
         * Выше третьей степени не поднимаемся: на диапазоне сцинтиллятора
         * (десятки кэВ — единицы МэВ) четвёртая уже даёт изгибы между точками.
         */
        fun orderFor(pointCount: Int): Int = when {
            pointCount < 2 -> 0
            pointCount < 4 -> 1
            pointCount < 6 -> 2
            else -> 3
        }

        /** Минимум точек, по которым кривая вообще строится. */
        const val MIN_POINTS = 2

        /**
         * Построить кривую по измеренным точкам.
         *
         * @return null, когда точек меньше [MIN_POINTS], они лежат на одной
         *   энергии или система нормальных уравнений вырождена
         */
        fun of(points: List<EfficiencyPoint>): EfficiencyCurve? {
            val usable = points.filter {
                it.energyKeV > 0.0 && it.efficiency > 0.0 && it.relativeSigma > 0.0
            }
            if (usable.size < MIN_POINTS) return null
            val order = orderFor(usable.size)
            val terms = order + 1
            if (usable.map { it.energyKeV }.distinct().size < terms) return null

            // Взвешенный МНК в логарифмах: вес — обратный квадрат σ(ln ε),
            // а σ(ln ε) и есть относительная неопределённость точки.
            val normal = Array(terms) { DoubleArray(terms) }
            val right = DoubleArray(terms)
            for (point in usable) {
                val x = basis(ln(point.energyKeV), terms)
                val y = ln(point.efficiency)
                val weight = 1.0 / (point.relativeSigma * point.relativeSigma)
                for (i in 0 until terms) {
                    right[i] += weight * y * x[i]
                    for (j in 0 until terms) normal[i][j] += weight * x[i] * x[j]
                }
            }
            val covariance = invert(normal) ?: return null
            val coefficients = DoubleArray(terms)
            for (i in 0 until terms) {
                var sum = 0.0
                for (j in 0 until terms) sum += covariance[i][j] * right[j]
                coefficients[i] = sum
            }
            if (coefficients.any { !it.isFinite() }) return null

            val degreesOfFreedom = usable.size - terms
            val chi = if (degreesOfFreedom > 0) {
                var sum = 0.0
                for (point in usable) {
                    val x = basis(ln(point.energyKeV), terms)
                    var model = 0.0
                    for (i in 0 until terms) model += coefficients[i] * x[i]
                    val residual = (ln(point.efficiency) - model) / point.relativeSigma
                    sum += residual * residual
                }
                sum / degreesOfFreedom
            } else {
                null
            }

            return EfficiencyCurve(
                coefficients = coefficients.toList(),
                covariance = covariance.map { it.toList() },
                minEnergyKeV = usable.minOf { it.energyKeV },
                maxEnergyKeV = usable.maxOf { it.energyKeV },
                points = usable.sortedBy { it.energyKeV },
                reducedChiSquare = chi,
            )
        }

        /** Степенной базис (1, x, x², …) длины [terms]. */
        private fun basis(x: Double, terms: Int): DoubleArray {
            val result = DoubleArray(terms)
            var value = 1.0
            for (i in 0 until terms) {
                result[i] = value
                value *= x
            }
            return result
        }

        /**
         * Обращение симметричной матрицы методом Гаусса — Жордана с выбором
         * ведущего элемента. Матрица нормальных уравнений мала (≤ 4×4), и
         * разложение здесь избыточно.
         *
         * @return null при вырождении: точки не задают выбранную степень
         */
        private fun invert(source: Array<DoubleArray>): Array<DoubleArray>? {
            val n = source.size
            val a = Array(n) { source[it].copyOf() }
            val inverse = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
            for (column in 0 until n) {
                var pivot = column
                for (row in column until n) {
                    if (abs(a[row][column]) > abs(a[pivot][column])) pivot = row
                }
                if (abs(a[pivot][column]) < 1e-12) return null
                val tmp = a[column]; a[column] = a[pivot]; a[pivot] = tmp
                val tmpInverse = inverse[column]
                inverse[column] = inverse[pivot]
                inverse[pivot] = tmpInverse

                val lead = a[column][column]
                for (j in 0 until n) {
                    a[column][j] /= lead
                    inverse[column][j] /= lead
                }
                for (row in 0 until n) {
                    if (row == column) continue
                    val factor = a[row][column]
                    if (factor == 0.0) continue
                    for (j in 0 until n) {
                        a[row][j] -= factor * a[column][j]
                        inverse[row][j] -= factor * inverse[column][j]
                    }
                }
            }
            return inverse
        }
    }
}

/**
 * Точка калибровки эффективности: линия известного источника.
 *
 * @property energyKeV энергия линии
 * @property efficiency измеренная ε = N / (t · A · p), доля
 * @property relativeSigma относительная неопределённость ε (счёт, активность
 *   источника, квантовый выход) — она же σ(ln ε)
 * @property nuclide чей это источник — чтобы точку можно было убрать целиком
 */
data class EfficiencyPoint(
    val energyKeV: Double,
    val efficiency: Double,
    val relativeSigma: Double,
    val nuclide: String,
)

/** Значение эффективности с относительной неопределённостью. */
data class EfficiencyValue(
    val efficiency: Double,
    /** σ(ε)/ε — относительная, потому что подгонка велась в логарифмах. */
    val relativeSigma: Double,
)
