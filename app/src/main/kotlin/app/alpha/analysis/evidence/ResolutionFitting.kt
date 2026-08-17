package app.alpha.analysis.evidence

/**
 * Подгонка FWHM²(E) = a + b·E + c·E² по ИЗМЕРЕННЫМ ширинам линий.
 *
 * ## Почему именно эта форма
 *
 * Три члена отвечают трём физическим вкладам: `a` — не зависящий от энергии
 * шум тракта, `b·E` — статистика носителей заряда (тот самый ход ∝ √E, на
 * котором стоит [SqrtResolution]), `c·E²` — систематика, растущая
 * пропорционально сигналу (неоднородность светосбора, дрейф усиления). Форма
 * стандартна для спектрометрических пакетов; линейна по параметрам, поэтому
 * решается взвешенным МНК без итераций.
 *
 * ## Ограничение монотонности — физика, а не косметика
 *
 * Ширина фотопика не может убывать с энергией: это означало бы, что
 * относительная флуктуация числа носителей растёт при их уменьшении. МНК
 * такого ограничения не знает и на четырёх шумных точках легко выдаёт
 * убывающую ветвь. Поэтому решение принимается, только если
 * d(FWHM²)/dE = b + 2cE ≥ 0 на всём диапазоне [0, E_макс], а свободный член
 * неотрицателен. Не выполнилось — кривая упрощается до двух членов; и там не
 * вышло — отказ с названной причиной, а не «лучшая из плохих».
 *
 * ## Почему по трём точкам не берётся квадратичный член
 *
 * Три точки и три параметра — это интерполяция, а не подгонка: кривая пройдёт
 * через них ровно и не оставит ни одной степени свободы, по которой можно
 * было бы заметить, что данные ей не соответствуют. Двухчленная форма на трёх
 * точках оставляет одну степень свободы и физически полна для сцинтиллятора,
 * у которого систематика мала.
 */
object ResolutionFitting {

    /**
     * Минимум измеренных линий — **инженерный параметр**. Две точки задают
     * прямую в координатах (E, FWHM²) без остатка, то есть подгонка не может
     * ошибиться и, значит, ничего не проверяет.
     */
    const val MIN_POINTS = 3

    /** С этого числа точек берётся квадратичный член. */
    const val MIN_POINTS_QUADRATIC = 4

    /**
     * Минимальный размах точек по энергии, кэВ — **инженерный параметр**.
     * Ниже него экстраполяция на весь диапазон 40–3000 кэВ опирается на
     * различие энергий, сравнимое с самой шириной линии.
     */
    const val MIN_SPAN_KEV = 500.0

    /**
     * Подгонка по измеренным линиям. Точки берутся по ТАБЛИЧНОЙ энергии
     * (аргумент модели — истинная энергия, а не наблюдённая: иначе сдвиг
     * шкалы просочился бы в модель ширины).
     */
    fun fit(measurements: List<MeasuredLine>): ResolutionFitOutcome {
        val points = measurements
            .filter { it.fwhmKeV > 0.0 && it.fwhmSigmaKeV > 0.0 && it.fwhmSigmaKeV.isFinite() }
            .distinctBy { it.line.energyKeV }
            .sortedBy { it.line.energyKeV }
        val span = if (points.size < 2) {
            0.0
        } else {
            points.last().line.energyKeV - points.first().line.energyKeV
        }
        if (points.size < MIN_POINTS) {
            return ResolutionFitOutcome.Refused(
                ResolutionFitRefusal.NOT_ENOUGH_LINES,
                points.size,
                span,
            )
        }
        if (span < MIN_SPAN_KEV) {
            return ResolutionFitOutcome.Refused(
                ResolutionFitRefusal.NARROW_ENERGY_SPAN,
                points.size,
                span,
            )
        }
        return solve(points, span)
    }

    private fun solve(points: List<MeasuredLine>, span: Double): ResolutionFitOutcome {
        val maxEnergy = points.last().line.energyKeV
        if (points.size >= MIN_POINTS_QUADRATIC) {
            val quad = weightedFit(points, degree = 2)
            if (quad != null && acceptable(quad[0], quad[1], quad[2], maxEnergy)) {
                return fitted(quad[0], quad[1], quad[2], points, quadratic = true)
            }
        }
        val linear = weightedFit(points, degree = 1)
            ?: return ResolutionFitOutcome.Refused(
                ResolutionFitRefusal.NOT_ENOUGH_LINES,
                points.size,
                span,
            )
        val a = linear[0]
        val b = linear[1]
        if (a < 0.0) {
            return ResolutionFitOutcome.Refused(
                ResolutionFitRefusal.NEGATIVE_NOISE_TERM,
                points.size,
                span,
            )
        }
        if (b < 0.0) {
            return ResolutionFitOutcome.Refused(
                ResolutionFitRefusal.NOT_MONOTONE,
                points.size,
                span,
            )
        }
        return fitted(a, b, 0.0, points, quadratic = false)
    }

    /**
     * Взвешенный МНК в координатах (E, FWHM²), базис 1, E, E².
     *
     * Вес 1/σ_y², где σ_y = 2·FWHM·σ_FWHM — правило переноса погрешности для
     * квадрата. Без него точка 2614,5 кэВ (самая яркая линия фона) и точка
     * 1120,3 кэВ (в разы слабее) вошли бы в кривую с одинаковым весом.
     */
    private fun weightedFit(points: List<MeasuredLine>, degree: Int): DoubleArray? {
        val n = degree + 1
        val normal = Array(n) { DoubleArray(n + 1) }
        for (p in points) {
            val x = p.line.energyKeV
            val y = p.fwhmKeV * p.fwhmKeV
            val sigmaY = 2.0 * p.fwhmKeV * p.fwhmSigmaKeV
            if (!(sigmaY > 0.0) || !sigmaY.isFinite()) return null
            val w = 1.0 / (sigmaY * sigmaY)
            val basis = DoubleArray(n) { k -> Math.pow(x, k.toDouble()) }
            for (i in 0 until n) {
                for (j in 0 until n) normal[i][j] += w * basis[i] * basis[j]
                normal[i][n] += w * basis[i] * y
            }
        }
        // Не-числа и бесконечности отвергаются здесь же: проверки ниже по
        // течению сравнивают (`a < 0`), а сравнение с NaN всегда ложно — без
        // этого фильтра вырожденное решение уехало бы в UI как «подгонка
        // удалась» и уронило композицию (смоук-регрессия
        // CalibrationNanRegressionTest).
        return gaussianSolve(normal, n)?.takeIf { coeffs -> coeffs.all { it.isFinite() } }
    }

    /** Гаусс с выбором главного элемента; null — матрица вырождена. */
    private fun gaussianSolve(matrix: Array<DoubleArray>, n: Int): DoubleArray? {
        for (col in 0 until n) {
            var pivot = col
            for (row in col until n) {
                if (kotlin.math.abs(matrix[row][col]) > kotlin.math.abs(matrix[pivot][col])) {
                    pivot = row
                }
            }
            if (kotlin.math.abs(matrix[pivot][col]) < 1e-12) return null
            val tmp = matrix[col]
            matrix[col] = matrix[pivot]
            matrix[pivot] = tmp
            for (row in 0 until n) {
                if (row == col) continue
                val factor = matrix[row][col] / matrix[col][col]
                for (k in col..n) matrix[row][k] -= factor * matrix[col][k]
            }
        }
        return DoubleArray(n) { matrix[it][n] / matrix[it][it] }
    }

    /** a ≥ 0 и FWHM² не убывает на [0, [maxEnergy]] — оба требования физические. */
    private fun acceptable(a: Double, b: Double, c: Double, maxEnergy: Double): Boolean =
        a >= 0.0 && b >= 0.0 && b + 2.0 * c * maxEnergy >= 0.0

    private fun fitted(
        a: Double,
        b: Double,
        c: Double,
        points: List<MeasuredLine>,
        quadratic: Boolean,
    ): ResolutionFitOutcome = ResolutionFitOutcome.Fitted(
        ResolutionFitResult(
            a = a,
            b = b,
            c = c,
            points = points.map { it.line.energyKeV },
            quadratic = quadratic,
            extrapolatedBelowKeV = points.first().line.energyKeV,
            extrapolatedAboveKeV = points.last().line.energyKeV,
        ),
    )
}
