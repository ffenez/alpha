package app.alpha.analysis

import kotlin.math.sqrt

/**
 * Пределы обнаружения по Кюри: что прибор МОГ БЫ заметить за это время.
 *
 * ## Зачем это здесь
 *
 * Приложение уже умеет честно говорить «различие не принято». Но само по себе
 * это утверждение пустое: не принято при какой чувствительности? За десять
 * секунд не принимается почти ничто, за час — почти всё принимается. Пределы
 * обнаружения превращают отказ в измерение: «за это время заметили бы
 * превышение от ×1,4, меньшего мы бы и не увидели».
 *
 * Это ровно то, чего требует правило проекта «непринятие различия ≠
 * равенство»: у отказа появляется числовая граница вместо оговорки словами.
 *
 * ## Величины (в терминах Кюри, 1968)
 *
 *  - **[criticalRate] L_C** — критический уровень: столько нетто-скорости
 *    даёт чистая статистика фона при заданной доле ложных тревог. Наблюдение
 *    ниже L_C неотличимо от фона.
 *  - **[detectableRate] L_D** — предел обнаружения: настоящее превышение
 *    такой величины будет замечено с заданной вероятностью. L_D ≈ k²/t +
 *    2·L_C — квадратичный член учитывает статистику самого сигнала.
 *  - **[upperRate] L_U** — верхняя граница наблюдённого нетто: даже когда
 *    различия нет, «не больше чем» сказать можно.
 *
 * ## Модель шума
 *
 * Обе выдержки конечны, поэтому дисперсия нетто-скорости складывается из
 * обеих: σ² = R_f/t_f + R_b/t_b. Формула L_C ниже — та же величина, записанная
 * через фон, потому что на пороге принятия решения сигнала ещё нет и обе
 * скорости равны фоновой.
 *
 * ## Чего эти числа НЕ означают
 *
 * Они говорят о СКОРОСТИ СЧЁТА, а не об активности источника: перевод в
 * беккерели требует эффективности прибора в конкретной геометрии, которой у
 * приложения нет. И они не учитывают систематику — дрейф усиления, изменение
 * положения прибора, «фон» другого места: это статистический предел, то есть
 * оптимистичная граница.
 *
 * Источник формул: L. A. Currie, «Limits for Qualitative Detection and
 * Quantitative Determination», Anal. Chem. 40 (1968) 586; в паре
 * «проба — фон» с раздельными выдержками, как в методике МДА
 * (Исаев, Бабенко, Казимиров, Гришин, Иевлев).
 */
data class DetectionLimits(
    /** Критический уровень, с⁻¹: ниже него нетто неотличимо от фона. */
    val criticalRate: Double,
    /** Предел обнаружения, с⁻¹: такое превышение будет замечено. */
    val detectableRate: Double,
    /** Верхняя граница наблюдённого нетто, с⁻¹. */
    val upperRate: Double,
    /** Наблюдённое нетто, с⁻¹ (может быть отрицательным). */
    val netRate: Double,
    /** Скорость счёта фона, с⁻¹ — знаменатель отношений ниже. */
    val backgroundRate: Double,
    /** Во сколько σ считались пределы. */
    val sigmas: Double,
) {

    /**
     * Минимально заметное ПРЕВЫШЕНИЕ как отношение к фону: 1 + L_D/R_b.
     *
     * Именно это число человек и хочет знать, стоя с прибором: во сколько раз
     * должно вырасти показание, чтобы приложение об этом сказало. Null — фон
     * нулевой, и отношение не определено.
     */
    val detectableRatio: Double?
        get() = if (backgroundRate > 0.0) 1.0 + detectableRate / backgroundRate else null

    /** Верхняя граница отношения к фону при наблюдённом нетто. */
    val upperRatio: Double?
        get() = if (backgroundRate > 0.0) 1.0 + upperRate / backgroundRate else null

    /** Различимо ли наблюдённое нетто на фоне собственной статистики. */
    val aboveCritical: Boolean get() = netRate > criticalRate
}

object DetectionLimitsMath {

    /**
     * Во сколько σ считаются пределы по умолчанию.
     *
     * **Инженерный параметр**: 1,645 — односторонний квантиль нормального
     * распределения для 5 %, классический выбор Кюри для α = β = 0,05. Порог
     * односторонний, потому что вопрос односторонний: интересует превышение,
     * а не «отличие в любую сторону».
     */
    const val DEFAULT_SIGMAS = 1.645

    /**
     * Пределы для пары «текущее окно — фон».
     *
     * @param current окно измерения; null или непригодное — пределов нет
     * @param background окно фона (или точки отсчёта)
     * @param sigmas во сколько σ; см. [DEFAULT_SIGMAS]
     * @return null, когда хотя бы одно окно непригодно: у пределов не бывает
     *   значения «примерно», их либо можно посчитать, либо нет.
     */
    fun of(
        current: CountWindow?,
        background: CountWindow?,
        sigmas: Double = DEFAULT_SIGMAS,
    ): DetectionLimits? {
        if (current == null || background == null) return null
        if (!current.usable || !background.usable) return null
        if (sigmas <= 0.0 || !sigmas.isFinite()) return null
        val tf = current.seconds
        val tb = background.seconds
        if (tf <= 0.0 || tb <= 0.0) return null

        val backgroundRate = background.ratePerSecond
        val currentRate = current.ratePerSecond
        val netRate = currentRate - backgroundRate

        // σ нетто-скорости НА ПОРОГЕ РЕШЕНИЯ: сигнала там ещё нет, поэтому обе
        // скорости равны фоновой, и дисперсия складывается из обеих выдержек.
        val sigmaAtZero = sqrt(backgroundRate / tf + backgroundRate / tb)
        val critical = sigmas * sigmaAtZero

        // L_D = k²/t_f + 2·L_C: квадратичный член — вклад статистики самого
        // сигнала, без него предел обнаружения занижен вдвое на малых счётах.
        val detectable = sigmas * sigmas / tf + 2.0 * critical

        // Верхняя граница считается по НАБЛЮДЁННЫМ скоростям: здесь сигнал уже
        // предполагается, и его собственная статистика входит в дисперсию.
        val sigmaObserved = sqrt(currentRate / tf + backgroundRate / tb)
        val upper = netRate + sigmas * sigmaObserved

        return DetectionLimits(
            criticalRate = critical,
            detectableRate = detectable,
            upperRate = upper,
            netRate = netRate,
            backgroundRate = backgroundRate,
            sigmas = sigmas,
        )
    }

    /**
     * Сколько секунд нужно накопить, чтобы заметить превышение в [ratio] раз.
     *
     * Обратная задача к [of] при равных выдержках (t_f = t_b = t): условие
     * L_D(t) = R_b·(ratio − 1) сводится к квадратному уравнению относительно
     * √t. Отвечает на вопрос «сколько ещё держать», не подменяя измерение
     * обещанием: null, когда превышение недостижимо ни за какое разумное
     * время (нулевой фон или ratio ≤ 1).
     */
    fun secondsFor(
        backgroundRate: Double,
        ratio: Double,
        sigmas: Double = DEFAULT_SIGMAS,
    ): Double? {
        if (backgroundRate <= 0.0 || !backgroundRate.isFinite()) return null
        if (ratio <= 1.0 || !ratio.isFinite()) return null
        val excess = backgroundRate * (ratio - 1.0)
        // excess·t = k² + 2k·√(2·R_b·t)  →  a·x² − b·x − c = 0, где x = √t.
        val a = excess
        val b = 2.0 * sigmas * sqrt(2.0 * backgroundRate)
        val c = sigmas * sigmas
        val discriminant = b * b + 4.0 * a * c
        if (discriminant <= 0.0) return null
        val root = (b + sqrt(discriminant)) / (2.0 * a)
        val seconds = root * root
        return seconds.takeIf { it.isFinite() && it > 0.0 }
    }
}
