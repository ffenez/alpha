package app.radiacode.analysis.evidence

import app.radiacode.analysis.EnergyCalibration

/**
 * Одно накопление, в котором ищутся опорные линии.
 *
 * @param id устойчивый ключ источника («full», «radon») — уезжает в
 *   [MeasuredLine.sourceId], чтобы в таблице было видно, откуда число
 */
data class CalibrationAccumulation(
    val id: String,
    val counts: List<Int>,
    val calibration: EnergyCalibration,
    val seconds: Long,
    /** Сколько интервальных снимков сложено. */
    val intervalCount: Int,
    /** Сколько часов стенных часов покрыто. */
    val hoursCovered: Int,
    val fromMillis: Long,
    val toMillis: Long,
)

/**
 * Диагностика калибровки прибора по природному фону: что измерено, что
 * измерить не удалось и почему.
 */
data class CalibrationReport(
    val accumulations: List<CalibrationAccumulation>,
    /** Все линии инвентаря с вердиктом пригодности — таблица «почему не эта». */
    val candidates: List<CalibrationLineCandidate>,
    val measurements: List<MeasuredLine>,
    /** Пригодные линии, которых в накоплениях не нашлось. */
    val notFound: List<CalibrationLineCandidate>,
    val fit: ResolutionFitOutcome,
    val scale: ScaleUncertainty?,
    val response: List<RelativeResponsePoint>,
) {
    val totalSeconds: Long get() = accumulations.maxOfOrNull { it.seconds } ?: 0L
}

/**
 * Диагностика калибровки ПО ПРИРОДНОМУ ФОНУ — без единого поверочного
 * источника.
 *
 * Материал берётся из того, что приложение уже накопило само (снимки спектра
 * раз в 10 минут и радоновый тренд), поэтому от человека не требуется ни
 * одного действия и ни одного введённого числа.
 *
 * Что получается:
 *  - **модель разрешения** FWHM(E) = √(a + bE + cE²) по ИЗМЕРЕННЫМ ширинам
 *    ([ResolutionFitting]) — вместо √E-приближения с одной вендорской точкой;
 *  - **σ_cal** из разброса остатков ([ScaleUncertaintyEstimator]) — вместо
 *    инженерного 1 % в [EnergyMatching];
 *  - **частичный относительный отклик** по паре линий одного нуклида
 *    ([RelativeResponseEstimator]) — с обязательной оговоркой о геометрии.
 *
 * Чего НЕ получается и не получится этим путём: кривой эффективности ε(E) на
 * весь диапазон, калибровки в области ниже ~1 МэВ (там природных линий,
 * которые прибор разделяет, попросту нет) и любой коррекции шкалы — движок
 * только измеряет, править калибровку прибора он не умеет и не будет.
 */
object BackgroundCalibration {

    /** Версия математики этой диагностики; см. `AlgorithmVersions`. */
    const val ALGORITHM_VERSION = 1

    /**
     * Разбирает накопления. Линия ищется в КАЖДОМ из них, остаётся измерение
     * с большей значимостью: длинная сумма даёт статистику, радоновая — тот
     * же Bi-214 без разбавления спокойными часами, и заранее не известно,
     * какая из двух окажется лучше на конкретной линии.
     */
    fun analyse(
        accumulations: List<CalibrationAccumulation>,
        startResolution: ResolutionModel,
    ): CalibrationReport {
        val candidates = CalibrationLineSelection.evaluateAll(startResolution)
        val usable = candidates.filter { it.usable }
        val measurements = mutableListOf<MeasuredLine>()
        val notFound = mutableListOf<CalibrationLineCandidate>()
        for (candidate in usable) {
            val best = accumulations.mapNotNull { accumulation ->
                LineMeasurement.measure(
                    counts = accumulation.counts,
                    calibration = accumulation.calibration,
                    candidate = candidate,
                    startResolution = startResolution,
                    sourceId = accumulation.id,
                )
            }.maxByOrNull { it.significance }
            if (best == null) notFound += candidate else measurements += best
        }
        measurements.sortBy { it.line.energyKeV }
        return CalibrationReport(
            accumulations = accumulations,
            candidates = candidates,
            measurements = measurements,
            notFound = notFound,
            fit = ResolutionFitting.fit(measurements),
            scale = ScaleUncertaintyEstimator.estimate(measurements),
            response = RelativeResponseEstimator.estimate(measurements),
        )
    }
}
