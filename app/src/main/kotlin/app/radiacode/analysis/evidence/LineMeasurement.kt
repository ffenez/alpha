package app.radiacode.analysis.evidence

import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.SpectrumEdge
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Измерение одной опорной линии в накопленном спектре.
 *
 * @param observedKeV центроид нетто-структуры
 * @param observedSigmaKeV 1σ центроида, FWHM/(2,355·√N) — ОЦЕНКА СНИЗУ
 *   (не включает неопределённость вычитания континуума)
 * @param fwhmKeV ИЗМЕРЕННАЯ ширина на половине высоты, а не ожидаемая
 * @param fwhmSigmaKeV 1σ измеренной ширины, FWHM/√(2N) — тоже оценка снизу
 * @param deltaKeV E_набл − E_табл; знак сохранён, он и есть остаток
 */
data class MeasuredLine(
    val line: LibraryLine,
    val observedKeV: Double,
    val observedSigmaKeV: Double,
    val fwhmKeV: Double,
    val fwhmSigmaKeV: Double,
    val netArea: Double,
    val netAreaSigma: Double,
    val significance: Double,
    /** Предсказанный вклад соседей того же ряда, см. [CalibrationLineCandidate]. */
    val blendBiasKeV: Double,
    /** Какое накопление дало это измерение — для строки «откуда материал». */
    val sourceId: String,
) {
    val deltaKeV: Double get() = observedKeV - line.energyKeV
}

/**
 * Измерение ширины и положения известной линии в накопленном спектре.
 *
 * Это НЕ поиск пиков: энергия известна заранее, вопрос только в том, что
 * прибор в этом месте показал. Отличие принципиальное для ширины —
 * [app.radiacode.analysis.PeakDetection] берёт FWHM из МОДЕЛИ разрешения (и
 * обязан, иначе гейт формы стал бы тавтологией), а здесь модель и измеряется,
 * поэтому ширина берётся только из данных.
 *
 * ## Почему ширина по половине высоты, а не по второму моменту
 *
 * Второй момент нетто-профиля точнее при чистой линии, но он взвешивает
 * отклонения квадратом расстояния, поэтому слабый сателлит на краю окна
 * (1847,4 кэВ рядом с 1764,5) раздувает его на десятки процентов, а
 * усечение окна занижает на единицы. Пересечение половины высоты к далёкому
 * сателлиту почти нечувствительно, а его смещение из-за наклона континуума
 * снимается вычитанием подложки. Цена — больший статистический разброс, и он
 * честно входит в вес точки при подгонке.
 */
object LineMeasurement {

    /**
     * Полуширина ROI в единицах ожидаемой FWHM — **инженерный параметр**.
     * Одна FWHM в каждую сторону это ±2,355σ, то есть 98 % площади гауссова
     * пика; шире — в сумму заходит соседняя структура, уже — теряется хвост,
     * и нетто-площадь перестаёт быть площадью линии.
     */
    const val ROI_HALF_WIDTH_FWHM = 1.0

    /**
     * Насколько центр структуры может отстоять от табличной энергии, в
     * единицах ожидаемой FWHM — **инженерный параметр**. Полширины это около
     * 3 % шкалы на 1,5 МэВ: столько мы готовы искать сдвиг калибровки, не
     * рискуя поймать соседнюю структуру.
     */
    const val SEARCH_HALF_WIDTH_FWHM = 0.5

    /**
     * Минимальная значимость измерения — тот же порог, что у диагностики
     * калибровки ([CalibrationDiagnostics.RELIABLE_MIN_SIGNIFICANCE]): у
     * слабого пика плавают и центроид, и ширина.
     */
    const val MIN_SIGNIFICANCE = CalibrationDiagnostics.RELIABLE_MIN_SIGNIFICANCE

    /**
     * Измеряет линию [candidate] в спектре [counts]. `null` — структуру
     * измерить нечем: ROI не помещается в шкалу, нетто отрицательное,
     * значимость ниже порога или склоны не опустились до половины высоты
     * внутри ROI (последнее означает, что там не линия, а ступень).
     *
     * [startResolution] задаёт только РАЗМЕР окон; измеренная ширина от неё
     * не зависит.
     */
    fun measure(
        counts: List<Int>,
        calibration: EnergyCalibration,
        candidate: CalibrationLineCandidate,
        startResolution: ResolutionModel,
        sourceId: String,
    ): MeasuredLine? {
        val energy = candidate.line.energyKeV
        val fwhmExpected = startResolution.fwhmKeV(energy)
        if (fwhmExpected <= 0.0) return null
        val last = SpectrumEdge.lastAnalysableChannel(counts.size)
        val roi = channelRange(calibration, energy, ROI_HALF_WIDTH_FWHM * fwhmExpected) ?: return null
        val width = roi.last - roi.first + 1
        val leftBand = (roi.first - width) until roi.first
        val rightBand = (roi.last + 1)..(roi.last + width)
        if (leftBand.first < 0 || rightBand.last > last) return null

        val continuum = continuumLine(counts, leftBand, rightBand) ?: return null
        val net = DoubleArray(width) { i ->
            counts[roi.first + i] - continuum.at((roi.first + i).toDouble())
        }
        val smooth = smooth(net)

        // Центр структуры ищется рядом с табличной энергией, а не по всему ROI:
        // иначе в широком окне «побеждает» соседний склон.
        val searchRange = channelRange(
            calibration,
            energy,
            SEARCH_HALF_WIDTH_FWHM * fwhmExpected,
        ) ?: return null
        var peakIndex = -1
        var peakValue = Double.NEGATIVE_INFINITY
        for (ch in searchRange) {
            val i = ch - roi.first
            if (i !in smooth.indices) continue
            if (smooth[i] > peakValue) {
                peakValue = smooth[i]
                peakIndex = i
            }
        }
        if (peakIndex < 0 || peakValue <= 0.0) return null

        val fwhmChannels = halfHeightWidth(smooth, peakIndex) ?: return null
        val fwhmKeV = calibration.energyAt((roi.first + peakIndex + fwhmChannels / 2f).toFloat())
            .toDouble() -
            calibration.energyAt((roi.first + peakIndex - fwhmChannels / 2f).toFloat()).toDouble()
        if (fwhmKeV <= 0.0) return null

        // Площадь и центроид считаются по СЫРОМУ нетто (сглаживание — только
        // для поиска максимума и склонов), в окне ±FWHM вокруг центра.
        val halfArea = max(1, ceil(fwhmChannels.toDouble()).toInt())
        val from = max(0, peakIndex - halfArea)
        val to = minOf(width - 1, peakIndex + halfArea)
        var netArea = 0.0
        var gross = 0.0
        var weight = 0.0
        var weightedChannel = 0.0
        for (i in from..to) {
            netArea += net[i]
            gross += counts[roi.first + i]
            val w = max(0.0, net[i])
            weight += w
            weightedChannel += w * (roi.first + i)
        }
        if (netArea <= 0.0 || weight <= 0.0) return null

        val bandChannels = leftBand.count() + rightBand.count()
        val bandSum = leftBand.sumOf { counts[it].toDouble() } +
            rightBand.sumOf { counts[it].toDouble() }
        val areaWidth = (to - from + 1).toDouble()
        // Та же дисперсия нетто-площади, что в PeakDetection (IAEA):
        // статистика окна плюс неопределённость ОЦЕНКИ континуума.
        val variance = gross + areaWidth * areaWidth * (bandSum / bandChannels) / bandChannels
        val sigmaNet = sqrt(max(variance, 1.0))
        val significance = netArea / sigmaNet
        if (significance < MIN_SIGNIFICANCE) return null

        val centroidChannel = weightedChannel / weight
        val observed = calibration.energyAt(centroidChannel.toFloat()).toDouble()
        return MeasuredLine(
            line = candidate.line,
            observedKeV = observed,
            observedSigmaKeV = fwhmKeV / (2.3548 * sqrt(netArea)),
            fwhmKeV = fwhmKeV,
            // σ ширины при N нетто-импульсах: FWHM/√(2N) — стандартная ошибка
            // оценки масштаба гауссова распределения. Вклад вычитания
            // континуума не включён, поэтому число — оценка снизу.
            fwhmSigmaKeV = fwhmKeV / sqrt(2.0 * netArea),
            netArea = netArea,
            netAreaSigma = sigmaNet,
            significance = significance,
            blendBiasKeV = candidate.blendBiasKeV,
            sourceId = sourceId,
        )
    }

    /** Каналы, покрывающие [centerKeV] ± [halfWidthKeV]; null — выходит за шкалу. */
    private fun channelRange(
        calibration: EnergyCalibration,
        centerKeV: Double,
        halfWidthKeV: Double,
    ): IntRange? {
        val lo = calibration.channelAt((centerKeV - halfWidthKeV).toFloat()).toInt()
        val hi = ceil(calibration.channelAt((centerKeV + halfWidthKeV).toFloat())).toInt()
        if (lo < 0 || hi <= lo) return null
        return lo..hi
    }

    /** Прямая по средним двух боковых полос — континуум под линией. */
    private fun continuumLine(
        counts: List<Int>,
        left: IntRange,
        right: IntRange,
    ): Continuum? {
        if (left.isEmpty() || right.isEmpty()) return null
        val leftMean = left.sumOf { counts[it].toDouble() } / left.count()
        val rightMean = right.sumOf { counts[it].toDouble() } / right.count()
        val leftCenter = (left.first + left.last) / 2.0
        val rightCenter = (right.first + right.last) / 2.0
        if (rightCenter <= leftCenter) return null
        val slope = (rightMean - leftMean) / (rightCenter - leftCenter)
        return Continuum(leftCenter, leftMean, slope)
    }

    private class Continuum(
        private val anchorChannel: Double,
        private val anchorValue: Double,
        private val slope: Double,
    ) {
        fun at(channel: Double): Double = anchorValue + slope * (channel - anchorChannel)
    }

    /** Центрированное скользящее среднее радиуса 2 — как в поиске пиков. */
    private fun smooth(values: DoubleArray): DoubleArray {
        val out = DoubleArray(values.size)
        for (i in values.indices) {
            var sum = 0.0
            var n = 0
            for (j in (i - 2)..(i + 2)) {
                if (j in values.indices) {
                    sum += values[j]
                    n++
                }
            }
            out[i] = sum / n
        }
        return out
    }

    /**
     * Ширина на половине высоты в каналах с линейной интерполяцией между
     * отсчётами. `null` — хотя бы один склон не опустился до половины внутри
     * окна: это не линия, а ступень или наложение шире ROI.
     */
    private fun halfHeightWidth(profile: DoubleArray, peakIndex: Int): Float? {
        val half = profile[peakIndex] / 2.0
        if (half <= 0.0) return null
        var left: Double? = null
        for (i in peakIndex downTo 1) {
            if (profile[i - 1] <= half && profile[i] > half) {
                left = crossing(i - 1, profile[i - 1], i, profile[i], half)
                break
            }
        }
        var right: Double? = null
        for (i in peakIndex until profile.size - 1) {
            if (profile[i + 1] <= half && profile[i] > half) {
                right = crossing(i, profile[i], i + 1, profile[i + 1], half)
                break
            }
        }
        if (left == null || right == null) return null
        val width = right - left
        return if (width > 0.0) width.toFloat() else null
    }

    private fun crossing(x1: Int, y1: Double, x2: Int, y2: Double, level: Double): Double {
        val dy = y2 - y1
        if (abs(dy) < 1e-9) return (x1 + x2) / 2.0
        return x1 + (level - y1) / dy * (x2 - x1)
    }
}
