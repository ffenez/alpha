package app.alpha.analysis.evidence

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

    /**
     * Разброс шкалы не оценён, поэтому значимость сдвига определить нечем.
     *
     * Остаток отклоняется от нуля не только из-за статистики центроида:
     * заводская калибровка задана тремя коэффициентами на весь диапазон,
     * усиление плывёт, отклик кристалла нелинеен. Эта систематическая часть
     * (σ_cal) и есть главный вклад в разброс остатков, а оценивается она
     * только по трём линиям и более. Без неё знаменатель значимости занижен, и
     * «выделенным» оказался бы почти любой сдвиг — поэтому он не объявляется
     * вовсе.
     */
    SIGMA_NOT_ESTIMATED,
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
    /**
     * χ² однородности остатков вокруг взвешенного среднего и его число
     * степеней свободы: по ним видно, имеет ли среднее смысл. Null — остатков
     * меньше двух, считать нечего.
     */
    val chiSquare: Double? = null,
    val degreesOfFreedom: Int? = null,
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

    /**
     * @param systematicSigmaKeV σ_cal как функция энергии — систематическая
     *   часть отклонения остатка от нуля. Null означает, что её взять неоткуда
     *   (измеренного разброса нет): тогда значимость сдвига не считается,
     *   потому что её знаменатель был бы заведомо занижен.
     */
    fun evaluate(
        residuals: List<CalibrationResidual>,
        systematicSigmaKeV: ((Double) -> Double)? = null,
    ): CalibrationDiagnostic {
        val usable = residuals.filter { it.sigmaKeV.isFinite() && it.sigmaKeV > 0.0 }
        if (usable.size < MIN_RESIDUALS) {
            return CalibrationDiagnostic(usable, null, null, null, CalibrationVerdict.NOT_EVALUATED)
        }
        if (systematicSigmaKeV == null) {
            return CalibrationDiagnostic(
                residuals = usable,
                shiftKeV = null,
                shiftUncertaintyKeV = null,
                slopePerKeV = slope(usable),
                verdict = CalibrationVerdict.SIGMA_NOT_ESTIMATED,
            )
        }
        // Веса 1/σ², где σ² = σ_стат² + σ_cal²: систематическая часть входит в
        // знаменатель наравне со статистикой, иначе яркая линия получила бы
        // почти бесконечный вес, а значимость сдвига — заниженный знаменатель.
        var weightSum = 0.0
        var weighted = 0.0
        for (r in usable) {
            val systematic = systematicSigmaKeV(r.energyKeV)
            val total = r.sigmaKeV * r.sigmaKeV + systematic * systematic
            val w = 1.0 / total
            weightSum += w
            weighted += w * r.deltaKeV
        }
        val shift = weighted / weightSum
        val sigmaShift = sqrt(1.0 / weightSum)
        // χ² однородности остатков вокруг среднего — при тех же полных σ. Он
        // не решает вердикт, а сопровождает его числом: по нему видно, описан
        // ли уход шкалы одной постоянной или расходится по энергии.
        val degreesOfFreedom = usable.size - 1
        val chiSquare = usable.sumOf {
            val d = it.deltaKeV - shift
            val systematic = systematicSigmaKeV(it.energyKeV)
            d * d / (it.sigmaKeV * it.sigmaKeV + systematic * systematic)
        }
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
            chiSquare = chiSquare,
            degreesOfFreedom = degreesOfFreedom,
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
