package app.alpha.analysis.evidence

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Совпадение наблюдённого пика с библиотечной линией по энергии.
 *
 * [z] — стандартизованное расстояние: во сколько раз расхождение больше
 * суммарной неопределённости. Число сравнимо между энергиями, в отличие от
 * «ΔE в кэВ» и от «ΔE в процентах».
 */
data class EnergyMatch(
    val peak: ObservedPeak,
    val line: LibraryLine,
    /** E_obs − E_ref, кэВ; знак сохранён — он нужен диагностике калибровки. */
    val deltaKeV: Double,
    /** √(σ_obs² + σ_ref² + σ_cal²), кэВ. */
    val sigmaKeV: Double,
    val z: Double,
    /**
     * Вошла ли неопределённость табличной энергии в знаменатель. `false`
     * означает, что источник её не дал (ENSDF-выборка), и знаменатель занижен
     * ровно на этот неизвестный вклад — то есть z ЗАВЫШЕН, что осторожно.
     */
    val referenceUncertaintyKnown: Boolean,
)

/**
 * Энергетическое совпадение через стандартизованное расстояние
 *
 *     z_E = (E_obs − E_ref) / √(σ_obs² + σ_ref² + σ_cal²)
 *
 * вместо жёсткого допуска `|ΔE| < max(2 %, FWHM/2)`.
 *
 * ## Почему так лучше
 *
 * Жёсткий допуск не различает сильный пик с центроидом ±0,4 кэВ и слабую
 * структуру, положение которой известно с точностью ±8 кэВ: обоим он даёт одно
 * и то же окно. z связывает допуск с ТЕМ, ЧТО ДЕЙСТВИТЕЛЬНО ИЗВЕСТНО, поэтому
 * хорошая статистика автоматически сужает окно, а плохая — расширяет.
 *
 * ## Что здесь неизвестно
 *
 * σ_ref у наших линий отсутствует (см. [LibraryLine]); σ_cal у прибора не
 * измерена. Поэтому σ_cal — **инженерный параметр**, а не характеристика
 * RadiaCode, и назван так во всех местах, где встречается.
 */
object EnergyMatching {

    /**
     * Относительная часть σ_cal — **инженерный параметр**.
     *
     * Точность заводской энергетической калибровки RadiaCode вендором не
     * опубликована, собственных калибровочных измерений у приложения нет.
     * 1 % от энергии выбран как порядок величины, при котором линия 662 кэВ
     * получает σ_cal ≈ 6,6 кэВ — сравнимо с наблюдаемым сдвигом заводской
     * калибровки на приборах серии, но заметно меньше FWHM (≈ 56 кэВ на той же
     * энергии), то есть параметр не подменяет собой разрешение.
     *
     * Изменение этого числа сдвигает границу приемлемости совпадений, поэтому
     * оно живёт одной константой и заменяется на измеренное, когда появится
     * калибровочная процедура ([CalibrationDiagnostics] уже умеет её оценивать).
     */
    const val CALIBRATION_SIGMA_FRACTION = 0.01

    /**
     * Нижняя граница σ_cal, кэВ — **инженерный параметр**. У 59,5 кэВ один
     * процент дал бы 0,6 кэВ, то есть калибровку точнее, чем ширина канала
     * прибора; полкэВ — грубая, но не обманывающая нижняя оценка.
     */
    const val CALIBRATION_SIGMA_FLOOR_KEV = 0.5

    /**
     * Порог приемлемости совпадения по z — **инженерный параметр**.
     *
     * 3σ это соглашение, а не физика: при нормальном приближении оно
     * соответствует ~0,3 % ложных отбрасываний истинных совпадений. Порог
     * НЕ является вердиктом — он лишь решает, создавать ли кандидата (Stage A
     * из 9.md); сила доказательства определяется дальше набором линий.
     */
    const val MAX_ACCEPTABLE_Z = 3.0

    /** σ_cal(E) — инженерная оценка неопределённости энергетической калибровки. */
    fun calibrationSigmaKeV(energyKeV: Double): Double =
        max(CALIBRATION_SIGMA_FLOOR_KEV, CALIBRATION_SIGMA_FRACTION * energyKeV)

    /** Полная σ расхождения: измерение ⊕ таблица ⊕ калибровка. */
    fun combinedSigmaKeV(peak: ObservedPeak, line: LibraryLine): Double {
        val sigmaObs = peak.centroidUncertaintyKeV.takeIf { it.isFinite() } ?: 0.0
        // Неизвестная неопределённость таблицы входит нулём СОЗНАТЕЛЬНО: это
        // делает знаменатель меньше, z больше и совпадение строже. Обратное
        // (подставить правдоподобное число) сделало бы движок мягче на
        // основании выдумки.
        val sigmaRef = line.energyUncertaintyKeV ?: 0.0
        val sigmaCal = calibrationSigmaKeV(line.energyKeV)
        return sqrt(sigmaObs * sigmaObs + sigmaRef * sigmaRef + sigmaCal * sigmaCal)
    }

    /** z-оценка совпадения; знак сохраняет направление сдвига. */
    fun z(peak: ObservedPeak, line: LibraryLine): Double {
        val sigma = combinedSigmaKeV(peak, line)
        if (sigma <= 0.0) return Double.NaN
        return (peak.centroidKeV - line.energyKeV) / sigma
    }

    /** Совпадение, если |z| ≤ [maxZ]; иначе null. */
    fun match(
        peak: ObservedPeak,
        line: LibraryLine,
        maxZ: Double = MAX_ACCEPTABLE_Z,
    ): EnergyMatch? {
        val sigma = combinedSigmaKeV(peak, line)
        if (!sigma.isFinite() || sigma <= 0.0) return null
        val delta = peak.centroidKeV - line.energyKeV
        val z = delta / sigma
        if (!z.isFinite() || abs(z) > maxZ) return null
        return EnergyMatch(
            peak = peak,
            line = line,
            deltaKeV = delta,
            sigmaKeV = sigma,
            z = z,
            referenceUncertaintyKnown = line.energyUncertaintyKeV != null,
        )
    }
}
