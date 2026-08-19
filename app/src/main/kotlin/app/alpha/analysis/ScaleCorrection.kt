package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Поправка энергетической шкалы по опорным линиям — ЯВНАЯ, по просьбе человека.
 *
 * ## Почему не автоматически
 *
 * Усиление сцинтилляционного тракта плывёт от температуры и со временем, и
 * линия K-40 может стоять не на 1460,8, а на 1430 кэВ. Соблазн подвинуть шкалу
 * молча велик, и именно поэтому этого здесь нет: молчаливая подгонка означала
 * бы, что показанная энергия зависит от того, какие линии приложение нашло в
 * последний раз, а два одинаковых спектра давали бы разные числа. Поправка
 * применяется только после того, как её увидели и приняли, и снимается одним
 * действием.
 *
 * ## Модель
 *
 *     E_испр = c₀ + c₁·E_изм
 *
 * Линейная по построению: у сцинтиллятора уход шкалы — это дрейф усиления
 * (множитель) и смещение нуля (сдвиг), и обе эти величины линейны. Квадратичный
 * член описывал бы нелинейность самого кристалла, которая за время между
 * измерениями не меняется, — он уже сидит в калибровке прибора.
 *
 * Одной линии НЕ ХВАТАЕТ ([ScaleCorrectionMath.MIN_REFERENCES]). Сдвиг и наклон
 * по одной точке неразделимы, а чистый множитель, снятый с неё, экстраполирует
 * куда угодно: на реальном спектре, где расхождение с таблицей у K-40 около
 * −29 кэВ, а у Tl-208 −31 кэВ, множитель из одного K-40 предсказывает на
 * 2614,5 кэВ поправку +52 кэВ — вдвое больше настоящей и в другую сторону от
 * истины. Линии обязаны ещё и РАЗОЙТИСЬ по шкале
 * ([ScaleCorrectionMath.MIN_SPAN_RATIO]): две близкие точки задают прямую своим
 * малым промежутком, и она расходится за их пределами.
 *
 * ## Что она НЕ делает
 *
 * Не меняет калибровку самого прибора: коэффициенты в его памяти остаются
 * прежними, и другой программе он отдаст те же числа. Это поправка ПОКАЗА и
 * анализа внутри приложения, и там, где она действует, об этом сказано.
 */
data class ScaleCorrection(
    /** Свободный член, кэВ. */
    val offsetKeV: Double,
    /** Множитель шкалы; 1,0 — наклон не менялся. */
    val gain: Double,
    /** Опорные линии, по которым посчитана поправка. */
    val references: List<Reference>,
    /** СКО остатков до поправки, кэВ. */
    val residualBeforeKeV: Double,
    /** СКО остатков после поправки, кэВ. */
    val residualAfterKeV: Double,
) {

    /** Исправленная энергия. */
    fun apply(energyKeV: Double): Double = offsetKeV + gain * energyKeV

    /**
     * Насколько поправка двигает точку шкалы, кэВ — для строки «до и после».
     */
    fun shiftAt(energyKeV: Double): Double = apply(energyKeV) - energyKeV

    /**
     * Поправка, применённая к калибровке прибора.
     *
     * E' = c₀ + c₁·(a₀ + a₁·ch + a₂·ch²) — та же квадратичная форма, поэтому
     * результат остаётся [EnergyCalibration], и весь остальной код о поправке
     * не знает.
     */
    fun applyTo(calibration: EnergyCalibration): EnergyCalibration = EnergyCalibration(
        a0 = (offsetKeV + gain * calibration.a0).toFloat(),
        a1 = (gain * calibration.a1).toFloat(),
        a2 = (gain * calibration.a2).toFloat(),
    )

    /**
     * @property tableKeV табличная энергия линии
     * @property measuredKeV энергия того же пика в спектре
     * @property nuclide чья это линия
     */
    data class Reference(
        val tableKeV: Double,
        val measuredKeV: Double,
        val nuclide: String,
    )
}

object ScaleCorrectionMath {

    /**
     * Насколько поправка обязана улучшить остатки, чтобы её предлагать.
     * **Инженерный параметр**: вдвое — меньшее улучшение на двух-трёх линиях
     * неотличимо от того, что прямая просто прошла через точки; предлагать
     * такую поправку значит предлагать подгонку под шум.
     */
    const val MIN_IMPROVEMENT = 2.0

    /**
     * Пределы правдоподобного множителя. **Инженерные параметры**: ±30 % —
     * дрейф усиления сцинтилляционного тракта такого порядка ещё бывает
     * (температура, старение ФЭУ/SiPM), больший означает, что линии
     * сопоставлены не с теми и поправка «исправит» шкалу в неверную сторону.
     */
    const val MIN_GAIN = 0.7
    const val MAX_GAIN = 1.3

    /**
     * Сколько опорных линий нужно. **Инженерный параметр**: 2 — минимум, на
     * котором сдвиг нуля отделим от наклона. По одной линии поправка была бы
     * чистым множителем, а он на настоящем приборе предсказывает высокие
     * энергии неверно (см. [ScaleCorrection]).
     */
    const val MIN_REFERENCES = 2

    /**
     * Во сколько раз обязаны разойтись крайние линии. **Инженерный параметр**:
     * 1,5 — при меньшем размахе прямая определяется коротким промежутком между
     * точками, и её продолжение к другим энергиям ничем не обосновано.
     */
    const val MIN_SPAN_RATIO = 1.5

    /**
     * Поправка по сопоставленным линиям.
     *
     * @param references пары «табличная линия — измеренная энергия»
     * @return null, когда линий нет, множитель выходит за пределы
     *   правдоподобия или поправка не улучшает остатки в [MIN_IMPROVEMENT] раз
     */
    fun of(references: List<ScaleCorrection.Reference>): ScaleCorrection? {
        val usable = references.filter { it.tableKeV > 0.0 && it.measuredKeV > 0.0 }
        if (usable.size < MIN_REFERENCES) return null
        val low = usable.minOf { it.tableKeV }
        val high = usable.maxOf { it.tableKeV }
        if (low <= 0.0 || high / low < MIN_SPAN_RATIO) return null

        val (offset, gain) = run {
            val n = usable.size
            val sumX = usable.sumOf { it.measuredKeV }
            val sumY = usable.sumOf { it.tableKeV }
            val sumXX = usable.sumOf { it.measuredKeV * it.measuredKeV }
            val sumXY = usable.sumOf { it.measuredKeV * it.tableKeV }
            val denominator = n * sumXX - sumX * sumX
            if (abs(denominator) < 1e-9) return null
            val slope = (n * sumXY - sumX * sumY) / denominator
            val intercept = (sumY - slope * sumX) / n
            intercept to slope
        }
        if (!offset.isFinite() || !gain.isFinite()) return null
        if (gain < MIN_GAIN || gain > MAX_GAIN) return null

        val before = rms(usable) { it.measuredKeV }
        val after = rms(usable) { offset + gain * it.measuredKeV }
        // Улучшение требуется СТРОГОЕ: при нулевом остатке до поправки
        // условие не выполняется, и поправка не предлагается — шкала уже на
        // месте, и двигать её незачем.
        if (after * MIN_IMPROVEMENT >= before) return null

        return ScaleCorrection(
            offsetKeV = offset,
            gain = gain,
            references = usable,
            residualBeforeKeV = before,
            residualAfterKeV = after,
        )
    }

    private fun rms(
        references: List<ScaleCorrection.Reference>,
        corrected: (ScaleCorrection.Reference) -> Double,
    ): Double {
        var sum = 0.0
        for (reference in references) {
            val residual = corrected(reference) - reference.tableKeV
            sum += residual * residual
        }
        return sqrt(sum / references.size)
    }
}
