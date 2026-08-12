package app.radiacode.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A detected spectral peak (energy refined by the net-count centroid). */
data class Peak(
    val channel: Int,
    val energyKeV: Float,
    val netCounts: Float,
    /**
     * Значимость нетто-площади: нетто / σ(нетто). Не «SNR»: имя
     * signal-to-noise ничего не говорит о том, ЧТО в знаменателе, а здесь это
     * стандартная неопределённость нетто-площади (см. [PeakDetection]).
     */
    val significance: Float,
)

/**
 * Simple, robust peak search for scintillator spectra. Method:
 *
 *  1. smooth the counts with a centered moving average (radius 2) — the
 *     search runs on the smoothed series, sums run on the raw counts;
 *  2. a candidate is a local maximum of the smoothed series within ± half the
 *     expected peak width, where the width is FWHM-aware: [fwhmKeV] models the
 *     RC-110 CsI(Tl) resolution as FWHM(E) = R₆₆₂·√(662·E) (relative
 *     resolution ∝ 1/√E, ~8 % at 662 keV per the RadiaCode 110 spec sheet);
 *  3. the local continuum under the peak is the mean of two side windows
 *     (one peak-width away on each side); net = gross − continuum·width;
 *  4. significance is Poisson: SNR = net / √(continuum·width + 1); candidates
 *     below [DEFAULT_MIN_SIGNIFICANCE] are noise and dropped;
 *  5. overlapping candidates within one FWHM merge, strongest SNR wins.
 *
 * Pure JVM, deterministic; tested on synthetic spectra.
 */
object PeakDetection {

    /** Relative FWHM at 662 keV (~8 % for the RC-110 CsI(Tl) crystal). */
    const val RESOLUTION_662 = 0.08f

    /** Poisson significance gate: 4σ over the local continuum. */
    const val DEFAULT_MIN_SIGNIFICANCE = 4f

    /** Below this the RC-110 response is dominated by threshold effects. */
    private const val MIN_ENERGY_KEV = 40f

    /**
     * Во сколько раз наблюдаемая ширина структуры может отличаться от
     * ожидаемой FWHM, чтобы структуру ещё считали пиком.
     *
     * **Инженерные параметры**, а не константы физики: разрешение прибора
     * задано моделью [fwhmKeV], но реальная линия шире из-за наложений и уже
     * не бывает — узкая структура это либо одиночный выброс, либо артефакт.
     * Границы выбраны широкими намеренно: цель — отсечь заведомо не-пики, а
     * не отбирать «красивые» пики.
     */
    private const val MIN_WIDTH_RATIO = 0.5f
    private const val MAX_WIDTH_RATIO = 2.5f

    /**
     * Наблюдаемая ширина на половине высоты нетто-структуры, в каналах; null,
     * если склоны не опустились до половины внутри окна поиска (структура шире
     * окна — её ширину это окно не измеряет).
     */
    private fun fwhmChannels(
        counts: List<Int>,
        center: Int,
        half: Int,
        continuum: Float,
    ): Float? {
        val peakNet = counts[center] - continuum
        if (peakNet <= 0f) return null
        val halfLevel = peakNet / 2f
        var left: Int? = null
        for (j in center downTo (center - 3 * half).coerceAtLeast(0)) {
            if (counts[j] - continuum <= halfLevel) {
                left = j
                break
            }
        }
        var right: Int? = null
        for (j in center..(center + 3 * half).coerceAtMost(counts.size - 1)) {
            if (counts[j] - continuum <= halfLevel) {
                right = j
                break
            }
        }
        // Если склон не успел опуститься до половины только с одной стороны
        // (пик на круто растущем континууме), ширина берётся по другой
        // половине; если ни с одной — ширина не измерена.
        val leftWidth = left?.let { center - it }
        val rightWidth = right?.let { it - center }
        return when {
            leftWidth != null && rightWidth != null -> (leftWidth + rightWidth).toFloat()
            leftWidth != null -> 2f * leftWidth
            rightWidth != null -> 2f * rightWidth
            else -> null
        }
    }

    fun fwhmKeV(energyKeV: Float): Float =
        RESOLUTION_662 * sqrt(662f * max(energyKeV, 1f))

    /** Half the expected peak width in channels at [channel] (≥ 2). */
    fun halfWidthChannels(calibration: EnergyCalibration, channel: Int): Int {
        val keVPerChannel = max(calibration.a1 + 2f * calibration.a2 * channel, 0.1f)
        val energy = calibration.energyAt(channel.toFloat())
        return max(2, (fwhmKeV(energy) / 2f / keVPerChannel).roundToInt())
    }

    fun detect(
        counts: List<Int>,
        calibration: EnergyCalibration,
        minSignificance: Float = DEFAULT_MIN_SIGNIFICANCE,
    ): List<Peak> {
        // Крайний канал — граница шкалы, а не точка спектра ([SpectrumEdge]):
        // сюда поиск пиков не заходит вовсе.
        val n = SpectrumEdge.lastAnalysableChannel(counts.size) + 1
        if (n < 32) return emptyList()
        val smoothed = SpectrumDisplay.movingAverage(counts.map { it.toFloat() })

        val candidates = mutableListOf<Peak>()
        for (i in 2 until n - 2) {
            if (calibration.energyAt(i.toFloat()) < MIN_ENERGY_KEV) continue
            val half = halfWidthChannels(calibration, i)
            if (i - 3 * half < 0 || i + 3 * half >= n) continue

            var isMax = true
            for (j in (i - half)..(i + half)) {
                if (smoothed[j] > smoothed[i]) {
                    isMax = false
                    break
                }
            }
            if (!isMax) continue

            var side = 0.0
            var sideCount = 0
            for (j in (i - 3 * half)..(i - half - 1)) {
                side += counts[j]; sideCount++
            }
            for (j in (i + half + 1)..(i + 3 * half)) {
                side += counts[j]; sideCount++
            }
            val continuum = (side / sideCount).toFloat()

            var gross = 0f
            for (j in (i - half)..(i + half)) gross += counts[j]
            val width = 2 * half + 1
            val net = gross - continuum * width
            if (net <= 0f) continue
            // Значимость = нетто / σ(нетто). Дисперсия нетто складывается из
            // статистики самого окна пика и неопределённости ОЦЕНКИ
            // континуума (IAEA, «Investigation of Uncertainty Sources in the
            // Determination of Gamma Emitting Radionuclides»):
            //     Var(net) = gross + width² · B̂ / m,
            // где B̂ — континуум на канал, m — число каналов боковых полос.
            // Прежняя формула net/√(B·width) делила только на шум фона и
            // завышала значимость, а её результат назывался «SNR».
            val varianceNet = gross + width.toFloat() * width * continuum / sideCount
            val significance = net / sqrt(max(varianceNet, 1f))
            if (significance < minSignificance) continue
            // Форма: фотопик имеет конечную ширину, заданную разрешением
            // детектора. Структура заметно уже ожидаемой — это не пик, а
            // одиночный выброс; заметно шире — это континуум или наложение
            // линий, и центроид такой структуры ничего не значит.
            // Ширину обязан иметь КАЖДЫЙ принятый пик: структура, у центра
            // которой нет собственных нетто-импульсов (плато сглаживания
            // рядом с одиночным выбросом) или чьи склоны не опускаются до
            // половины в окне поиска, — не фотопик.
            val observedFwhm = fwhmChannels(counts, i, half, continuum) ?: continue
            val expectedFwhm = 2f * half
            val ratio = observedFwhm / expectedFwhm.coerceAtLeast(1f)
            if (ratio < MIN_WIDTH_RATIO || ratio > MAX_WIDTH_RATIO) continue

            // Centroid over net counts refines the peak energy.
            var weightSum = 0.0
            var weightedChannel = 0.0
            for (j in (i - half)..(i + half)) {
                val weight = max(0f, counts[j] - continuum)
                weightSum += weight
                weightedChannel += weight.toDouble() * j
            }
            val centroid = if (weightSum > 0) (weightedChannel / weightSum).toFloat() else i.toFloat()
            candidates += Peak(
                channel = i,
                energyKeV = calibration.energyAt(centroid),
                netCounts = net,
                significance = significance,
            )
        }

        // Merge candidates within one FWHM of a stronger accepted peak.
        val accepted = mutableListOf<Peak>()
        for (peak in candidates.sortedByDescending { it.significance }) {
            val overlaps = accepted.any {
                abs(it.energyKeV - peak.energyKeV) < fwhmKeV(peak.energyKeV)
            }
            if (!overlaps) accepted += peak
        }
        return accepted
    }
}
