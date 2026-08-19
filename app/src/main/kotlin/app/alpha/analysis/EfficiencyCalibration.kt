package app.alpha.analysis

import kotlin.math.abs

/**
 * Точки кривой эффективности по спектру эталонного источника.
 *
 * Для каждой линии эталона ищется фотопик, и по его нетто-площади считается
 * ε = N / (t · A · p) ([ActivityMath.efficiencyPoint]). Линия, для которой пика
 * нет, точки НЕ даёт: эффективность на этой энергии осталась неизмеренной, и
 * подставлять вместо неё ноль или соседнее значение нельзя.
 */
object EfficiencyCalibration {

    /**
     * Насколько далеко от табличной энергии принимается пик, в ожидаемых
     * FWHM. **Инженерный параметр**: 1,0 — шкала прибора может быть смещена, и
     * узкий допуск терял бы линии на исправном приборе; шире одной FWHM
     * начинают приниматься соседние линии, и площадь пошла бы не в ту точку.
     */
    const val MATCH_FWHM = 1.0f

    /**
     * Минимальная значимость пика, годного для калибровки. **Инженерный
     * параметр**: 6σ — выше рабочего порога поиска (4σ), потому что здесь
     * площадь идёт не в вывод «есть линия», а в знаменатель всех будущих
     * активностей; слабый пик задал бы кривую своей флуктуацией.
     */
    const val MIN_SIGNIFICANCE = 6f

    /**
     * @property points измеренные точки
     * @property missedKeV энергии линий эталона, для которых пик не найден
     */
    data class Outcome(
        val points: List<EfficiencyPoint>,
        val missedKeV: List<Double>,
    )

    /**
     * @param counts отсчёты спектра эталона
     * @param seconds время накопления, с
     * @param calibration энергетическая шкала спектра
     * @param source эталон
     * @param activityBecquerel активность эталона НА МОМЕНТ измерения
     * @param activityRelativeSigma относительная неопределённость активности
     * @param resolution662 разрешение прибора — задаёт допуск поиска
     */
    fun measure(
        counts: List<Int>,
        seconds: Long,
        calibration: EnergyCalibration,
        source: ReferenceSources.Source,
        activityBecquerel: Double,
        activityRelativeSigma: Double,
        resolution662: Float = PeakDetection.RESOLUTION_662,
        minEnergyKeV: Float = PeakDetection.DEFAULT_MIN_ENERGY_KEV,
    ): Outcome {
        if (seconds <= 0L || activityBecquerel <= 0.0) {
            return Outcome(emptyList(), source.lines.map { it.energyKeV })
        }
        val peaks = PeakDetection.detect(
            counts = counts,
            calibration = calibration,
            resolution662 = resolution662,
            minEnergyKeV = minEnergyKeV,
        ).filter { it.significance >= MIN_SIGNIFICANCE }

        val points = mutableListOf<EfficiencyPoint>()
        val missed = mutableListOf<Double>()
        for (line in source.lines) {
            val tolerance = MATCH_FWHM *
                PeakDetection.expectedFwhmKeV(line.energyKeV.toFloat(), resolution662)
            val peak = peaks
                .filter { abs(it.energyKeV - line.energyKeV) <= tolerance }
                .maxByOrNull { it.netCounts }
            if (peak == null) {
                missed += line.energyKeV
                continue
            }
            // σ площади восстанавливается из значимости: значимость и есть
            // нетто, делённое на свою σ ([PeakDetection]).
            val netSigma = if (peak.significance > 0f) {
                peak.netCounts.toDouble() / peak.significance
            } else {
                0.0
            }
            val point = ActivityMath.efficiencyPoint(
                netCounts = peak.netCounts.toDouble(),
                netSigma = netSigma,
                seconds = seconds.toDouble(),
                activityBecquerel = activityBecquerel,
                activityRelativeSigma = activityRelativeSigma,
                intensity = line.intensity,
                energyKeV = line.energyKeV,
                nuclide = source.nuclide,
            )
            if (point == null) missed += line.energyKeV else points += point
        }
        return Outcome(points, missed)
    }
}
