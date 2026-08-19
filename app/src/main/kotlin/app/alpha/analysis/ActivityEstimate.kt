package app.alpha.analysis

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Активность источника по площади фотопика — с неопределённостью или как
 * верхняя граница.
 *
 * ## Расчёт
 *
 *     A = N / (t · ε(E) · p)
 *
 * где N — нетто-площадь фотопика (импульсы), t — время накопления (с), ε —
 * эффективность регистрации в этой геометрии ([EfficiencyCurve]), p — квантовый
 * выход линии (доля распадов, дающих этот квант). Единица — беккерель,
 * распад в секунду.
 *
 * Неопределённость складывается из независимых относительных вкладов:
 *
 *     (σ_A/A)² = (σ_N/N)² + (σ_ε/ε)² + (σ_p/p)²
 *
 * Вклад ε обычно ведущий: у сцинтилляционной калибровки по одному-двум
 * источникам он редко ниже десяти процентов.
 *
 * ## Когда числа нет
 *
 * Если линия не различима на фоне ([DetectionLimits]), активность не
 * называется: вместо неё даётся ВЕРХНЯЯ ГРАНИЦА — та активность, которая при
 * этой эффективности и этом времени дала бы уже заметный пик. Это ответ на
 * вопрос «сколько там могло быть», и он честнее нуля с погрешностью.
 *
 * ## Границы
 *
 * Число относится к ТОМУ источнику в ТОЙ геометрии, в которой снята кривая
 * эффективности. Самопоглощение в образце, каскадные совпадения и отличие
 * геометрии образца от геометрии калибровки не учитываются и могут менять
 * результат в разы.
 */
data class ActivityEstimate(
    /** Активность, Бк; null — линия не различима, смотри [upperBecquerel]. */
    val becquerel: Double?,
    /** Стандартная неопределённость активности, Бк; null вместе с [becquerel]. */
    val sigmaBecquerel: Double?,
    /** Верхняя граница активности, Бк — есть всегда. */
    val upperBecquerel: Double,
    /** Минимальная активность, которую это измерение различило бы, Бк. */
    val detectableBecquerel: Double,
) {
    /** Относительная неопределённость активности; null без самой активности. */
    val relativeSigma: Double?
        get() = if (becquerel != null && sigmaBecquerel != null && becquerel > 0.0) {
            sigmaBecquerel / becquerel
        } else {
            null
        }
}

object ActivityMath {

    /**
     * Активность по площади фотопика.
     *
     * @param netCounts нетто-площадь пика, импульсы
     * @param netSigma стандартная неопределённость площади, импульсы
     * @param seconds время накопления, с
     * @param efficiency эффективность на энергии линии
     * @param intensity квантовый выход линии, доля (0…1)
     * @param intensitySigma неопределённость выхода, доля; null — не учтена
     * @param detectable предел обнаружения площади, импульсы — из
     *   [DetectionLimitsMath] по тем же окнам; ниже него активность не
     *   называется
     * @return null, когда любой множитель непригоден (нулевое время, нулевая
     *   эффективность, нулевой выход): активность тогда не определена, а не
     *   «равна нулю»
     */
    fun of(
        netCounts: Double,
        netSigma: Double,
        seconds: Double,
        efficiency: EfficiencyValue,
        intensity: Double,
        intensitySigma: Double? = null,
        detectable: Double,
    ): ActivityEstimate? {
        if (seconds <= 0.0 || !seconds.isFinite()) return null
        if (efficiency.efficiency <= 0.0 || !efficiency.efficiency.isFinite()) return null
        if (intensity <= 0.0 || intensity > 1.0) return null
        if (netSigma < 0.0 || !netSigma.isFinite()) return null

        val perCount = 1.0 / (seconds * efficiency.efficiency * intensity)
        val relativeSquared = efficiency.relativeSigma * efficiency.relativeSigma +
            (intensitySigma?.let { (it / intensity) * (it / intensity) } ?: 0.0)

        val detectableActivity = detectable * perCount
        // Верхняя граница площади: наблюдённое нетто плюс 1,645 σ. Тот же
        // односторонний квантиль, что у пределов обнаружения — иначе «граница»
        // и «предел» отвечали бы на вопрос с разной уверенностью.
        val upperCounts = netCounts + DetectionLimitsMath.DEFAULT_SIGMAS * netSigma
        val upperActivity = upperCounts.coerceAtLeast(0.0) * perCount

        val resolved = netCounts > detectable
        val activity = if (resolved) netCounts * perCount else null
        val sigma = if (resolved && netCounts > 0.0) {
            val countRelative = netSigma / netCounts
            activity!! * sqrt(countRelative * countRelative + relativeSquared)
        } else {
            null
        }

        return ActivityEstimate(
            becquerel = activity,
            sigmaBecquerel = sigma,
            upperBecquerel = upperActivity,
            detectableBecquerel = detectableActivity,
        )
    }

    /**
     * Активность эталона на дату измерения: A = A₀ · 2^(−Δt/T½).
     *
     * @param certifiedBecquerel паспортная активность на дату аттестации
     * @param elapsedSeconds время от даты аттестации до измерения, с
     * @param halfLifeSeconds период полураспада, с
     * @return null при непригодных данных; отрицательный интервал не
     *   пересчитывается — источник не может быть измерен до аттестации
     */
    fun decayed(
        certifiedBecquerel: Double,
        elapsedSeconds: Double,
        halfLifeSeconds: Double,
    ): Double? {
        if (certifiedBecquerel <= 0.0 || !certifiedBecquerel.isFinite()) return null
        if (halfLifeSeconds <= 0.0 || !halfLifeSeconds.isFinite()) return null
        if (elapsedSeconds < 0.0 || !elapsedSeconds.isFinite()) return null
        return certifiedBecquerel * Math.pow(2.0, -elapsedSeconds / halfLifeSeconds)
    }

    /**
     * Точка кривой эффективности по линии эталона: ε = N / (t · A · p).
     *
     * @param netCounts нетто-площадь фотопика, импульсы
     * @param netSigma стандартная неопределённость площади, импульсы
     * @param seconds время накопления, с
     * @param activityBecquerel активность эталона НА МОМЕНТ измерения
     * @param activityRelativeSigma относительная неопределённость активности
     *   эталона (паспортная); типично 0,03…0,10
     * @param intensity квантовый выход линии, доля
     * @param energyKeV энергия линии
     * @param nuclide имя эталона
     * @return null, когда точка не определена (нулевая площадь, время или
     *   активность)
     */
    fun efficiencyPoint(
        netCounts: Double,
        netSigma: Double,
        seconds: Double,
        activityBecquerel: Double,
        activityRelativeSigma: Double,
        intensity: Double,
        energyKeV: Double,
        nuclide: String,
    ): EfficiencyPoint? {
        if (netCounts <= 0.0 || seconds <= 0.0 || activityBecquerel <= 0.0) return null
        if (intensity <= 0.0 || intensity > 1.0) return null
        if (energyKeV <= 0.0) return null
        val efficiency = netCounts / (seconds * activityBecquerel * intensity)
        if (!efficiency.isFinite() || efficiency <= 0.0) return null
        val countRelative = netSigma / netCounts
        val relative = sqrt(
            countRelative * countRelative +
                activityRelativeSigma * activityRelativeSigma,
        )
        if (!relative.isFinite() || relative <= 0.0) return null
        return EfficiencyPoint(
            energyKeV = energyKeV,
            efficiency = efficiency,
            relativeSigma = relative,
            nuclide = nuclide,
        )
    }

    /** Период полураспада из строки вида «30,08 года» не берётся — только числом. */
    fun halfLifeSecondsFromYears(years: Double): Double = years * SECONDS_PER_YEAR

    /**
     * Секунд в юлианском году (365,25 суток) — та же условность, в которой
     * приводятся периоды полураспада в справочниках.
     */
    const val SECONDS_PER_YEAR = 365.25 * 24 * 3600

    /** Натуральный логарифм двух — для пересчёта периода в постоянную распада. */
    val LN2: Double = ln(2.0)
}
