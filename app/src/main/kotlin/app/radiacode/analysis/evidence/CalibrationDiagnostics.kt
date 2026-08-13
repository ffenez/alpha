package app.radiacode.analysis.evidence

import kotlin.math.abs
import kotlin.math.sqrt

/** Остаток по одному надёжному совпадению: ΔE(E) с его неопределённостью. */
data class CalibrationResidual(
    val energyKeV: Double,
    val deltaKeV: Double,
    val sigmaKeV: Double,
)

/** Вердикт диагностики — только диагностика, никакой коррекции. */
enum class CalibrationVerdict {
    /** Надёжных совпадений слишком мало, чтобы что-то говорить о калибровке. */
    NOT_EVALUATED,

    /** Систематический сдвиг не выделен на фоне собственной неопределённости. */
    CONSISTENT,

    /** Сдвиг выделен: значение и его неопределённость в [shiftKeV]/[shiftUncertaintyKeV]. */
    POSSIBLE_SYSTEMATIC_SHIFT,
}

/**
 * Диагностика энергетической калибровки по надёжным совпадениям.
 *
 * @param shiftKeV взвешенное среднее остатков (постоянная составляющая сдвига)
 * @param shiftUncertaintyKeV 1σ этого среднего
 * @param slopePerKeV наклон ΔE(E) — есть только при трёх и более остатках
 */
data class CalibrationDiagnostic(
    val residuals: List<CalibrationResidual>,
    val shiftKeV: Double?,
    val shiftUncertaintyKeV: Double?,
    val slopePerKeV: Double?,
    val verdict: CalibrationVerdict,
)

/**
 * Оценка систематического сдвига энергетической шкалы.
 *
 * ## Только диагностика
 *
 * Движок НИКОГДА не правит энергии молча. Причина простая: сдвиг оценивается
 * по совпадениям, а совпадения получены при той же калибровке — тихая
 * коррекция замкнула бы петлю и подтвердила бы любую первоначальную гипотезу.
 * Автокоррекция допустима только после отдельной проверенной процедуры
 * калибровки по источнику с известными линиями.
 *
 * ## Что значит «надёжное совпадение»
 *
 * Пик значим ([RELIABLE_MIN_SIGNIFICANCE]) И у линии нет неразрешимых
 * альтернатив из других нуклидов: остаток по линии, которую нельзя отличить от
 * соседней, измеряет не сдвиг шкалы, а нашу неспособность выбрать линию.
 *
 * ## Почему σ остатка НЕ включает σ_cal
 *
 * В [EnergyMatching] неопределённость калибровки стоит в знаменателе z — там
 * она мешает признать совпадение. Здесь она ИСКОМАЯ величина, и класть её в
 * знаменатель значило бы прятать ровно тот сигнал, который ищется: сдвиг в
 * 10 кэВ на фоне «σ_cal = 15 кэВ» никогда не был бы выделен. Поэтому остаток
 * взвешивается только СТАТИСТИЧЕСКОЙ неопределённостью — σ центроида (и σ
 * табличной энергии, когда источник её даёт).
 *
 * ## Известное ограничение
 *
 * Совпадения ищутся в окне ±3σ_cal, поэтому сдвиг больше ~3 % шкалы не
 * диагностируется — при нём линии просто перестают совпадать, и кандидаты не
 * создаются. Это состояние («сильных совпадений нет вовсе») само по себе
 * сигнал, но отличить его от «в спектре ничего нет» движок не умеет; для этого
 * нужна калибровочная процедура по источнику с известными линиями.
 */
object CalibrationDiagnostics {

    /**
     * Минимальная значимость пика для участия в диагностике — **инженерный
     * параметр**. Порог поиска пиков 4σ; здесь взят вдвое строже, потому что у
     * слабого пика центроид сам по себе плавает.
     */
    const val RELIABLE_MIN_SIGNIFICANCE = 8.0

    /** Меньше этого числа остатков — вердикт [CalibrationVerdict.NOT_EVALUATED]. */
    const val MIN_RESIDUALS = 2

    /** Во сколько σ должен уложиться сдвиг, чтобы считаться выделенным. */
    const val SHIFT_SIGNIFICANCE = 2.0

    /**
     * Нижняя граница σ остатка, кэВ — **инженерный параметр**. Формула σ
     * центроида при большой статистике даёт сотые доли кэВ; на сцинтилляторе
     * такая точность положения не бывает достижимой (дрейф усиления, форма
     * линии, вычитание континуума), и без пола один яркий пик получил бы
     * почти бесконечный вес.
     */
    const val MIN_RESIDUAL_SIGMA_KEV = 0.1

    /**
     * σ остатка одного совпадения: только статистика измерения и, если она
     * известна, неопределённость табличной энергии — см. KDoc объекта.
     */
    fun residualSigmaKeV(match: EnergyMatch): Double {
        val sigmaObs = match.peak.centroidUncertaintyKeV.takeIf { it.isFinite() } ?: 0.0
        val sigmaRef = match.line.energyUncertaintyKeV ?: 0.0
        return maxOf(sqrt(sigmaObs * sigmaObs + sigmaRef * sigmaRef), MIN_RESIDUAL_SIGMA_KEV)
    }

    fun evaluate(residuals: List<CalibrationResidual>): CalibrationDiagnostic {
        val usable = residuals.filter { it.sigmaKeV.isFinite() && it.sigmaKeV > 0.0 }
        if (usable.size < MIN_RESIDUALS) {
            return CalibrationDiagnostic(usable, null, null, null, CalibrationVerdict.NOT_EVALUATED)
        }
        // Взвешенное среднее с весами 1/σ²: остаток по слабому пику не должен
        // тянуть оценку так же сильно, как по сильному.
        var weightSum = 0.0
        var weighted = 0.0
        for (r in usable) {
            val w = 1.0 / (r.sigmaKeV * r.sigmaKeV)
            weightSum += w
            weighted += w * r.deltaKeV
        }
        val shift = weighted / weightSum
        val sigmaShift = sqrt(1.0 / weightSum)
        val verdict = if (abs(shift) > SHIFT_SIGNIFICANCE * sigmaShift) {
            CalibrationVerdict.POSSIBLE_SYSTEMATIC_SHIFT
        } else {
            CalibrationVerdict.CONSISTENT
        }
        return CalibrationDiagnostic(
            residuals = usable,
            shiftKeV = shift,
            shiftUncertaintyKeV = sigmaShift,
            slopePerKeV = slope(usable),
            verdict = verdict,
        )
    }

    /**
     * Наклон ΔE(E) — взвешенная линейная регрессия. Возвращается как ЧИСЛО ДЛЯ
     * ЧЕЛОВЕКА: три точки на 3 МэВ шкалы не дают права утверждать, что
     * калибровка «уходит с энергией», но растущие остатки видно сразу.
     */
    private fun slope(residuals: List<CalibrationResidual>): Double? {
        if (residuals.size < 3) return null
        var sw = 0.0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (r in residuals) {
            val w = 1.0 / (r.sigmaKeV * r.sigmaKeV)
            sw += w
            sx += w * r.energyKeV
            sy += w * r.deltaKeV
            sxx += w * r.energyKeV * r.energyKeV
            sxy += w * r.energyKeV * r.deltaKeV
        }
        val denominator = sw * sxx - sx * sx
        if (abs(denominator) < 1e-12) return null
        return (sw * sxy - sx * sy) / denominator
    }
}
