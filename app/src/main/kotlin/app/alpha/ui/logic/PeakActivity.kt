package app.alpha.ui.logic

import app.alpha.analysis.ActivityEstimate
import app.alpha.analysis.ActivityMath
import app.alpha.analysis.EfficiencyCurve
import app.alpha.analysis.GammaLineLibrary
import app.alpha.analysis.Peak
import app.alpha.analysis.PeakDetection
import kotlin.math.abs

/**
 * Активность по одному фотопику — там, где для этого есть всё нужное.
 *
 * Нужны четыре вещи, и без любой из них числа не будет: кривая эффективности в
 * этой геометрии, кандидат-нуклид у пика, квантовый выход его линии и время
 * накопления. Отсутствие любой из них — не повод «прикинуть»: активность,
 * посчитанная по чужой геометрии или по угаданному выходу, ошибается в разы, и
 * такое число хуже его отсутствия.
 */
object PeakActivity {

    /**
     * Насколько далеко от энергии пика ищется линия кандидата, в ожидаемых
     * FWHM. **Инженерный параметр**: 1,0 — тот же допуск, что при калибровке
     * эффективности; шире начинают попадать соседние линии того же нуклида с
     * другим выходом.
     */
    const val MATCH_FWHM = 1.0f

    /**
     * @param peak фотопик с измеренной нетто-площадью
     * @param nuclide кандидат, к которому пик отнесён движком доказательств
     * @param curve кривая эффективности этой геометрии
     * @param seconds время накопления спектра, с
     * @param resolution662 разрешение прибора — задаёт допуск поиска линии
     * @return null, когда линия кандидата не найдена, выход неизвестен, кривая
     *   не покрывает эту энергию или время накопления неизвестно
     */
    fun of(
        peak: Peak,
        nuclide: String?,
        curve: EfficiencyCurve?,
        seconds: Long,
        resolution662: Float = PeakDetection.RESOLUTION_662,
    ): ActivityEstimate? {
        if (nuclide == null || curve == null || seconds <= 0L) return null
        val tolerance = MATCH_FWHM * PeakDetection.expectedFwhmKeV(peak.energyKeV, resolution662)
        val line = GammaLineLibrary.LINES
            .filter { it.isotope == nuclide }
            .filter { abs(it.energyKeV - peak.energyKeV) <= tolerance }
            .maxByOrNull { it.intensityPercent }
            ?: return null
        val intensity = line.intensityPercent / 100.0
        if (intensity <= 0.0) return null
        val efficiency = curve.efficiencyAt(peak.energyKeV.toDouble()) ?: return null
        if (peak.significance <= 0f) return null
        val netSigma = peak.netCounts.toDouble() / peak.significance
        // Предел обнаружения площади: та же односторонняя граница, что у
        // пределов Кюри, выраженная через σ самой площади — окон фона у
        // отдельного пика нет, континуум уже вычтен.
        val detectable = DETECTABLE_SIGMAS * netSigma
        return ActivityMath.of(
            netCounts = peak.netCounts.toDouble(),
            netSigma = netSigma,
            seconds = seconds.toDouble(),
            efficiency = efficiency,
            intensity = intensity,
            intensitySigma = line.intensityUncertaintyPercent?.let { it / 100.0 },
            detectable = detectable,
        )
    }

    /**
     * Во сколько σ площади линия считается различимой. **Инженерный
     * параметр**: 4 — тот же порог, по которому пик вообще попадает в список
     * ([PeakDetection.DEFAULT_MIN_SIGNIFICANCE]); называть активность по
     * структуре, не прошедшей этот порог, значило бы противоречить самому
     * списку пиков.
     */
    const val DETECTABLE_SIGMAS = PeakDetection.DEFAULT_MIN_SIGNIFICANCE.toDouble()
}
