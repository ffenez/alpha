package app.alpha.analysis

import app.alpha.analysis.evidence.ResolutionSource
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
    /**
     * ИЗМЕРЕННАЯ ширина на половине высоты нетто-структуры, кэВ; null — не
     * измерялась (пик собран вручную в тесте).
     *
     * Это не ожидаемая по модели разрешения ширина ([expectedFwhmKeV]), а
     * полученная на этих отсчётах: гейт формы требует её у каждого принятого
     * пика.
     */
    val fwhmKeV: Float? = null,
)

/**
 * Simple, robust peak search for scintillator spectra. Method:
 *
 *  1. smooth the counts with a centered moving average (radius 2) — the
 *     search runs on the smoothed series, sums run on the raw counts;
 *  2. a candidate is a local maximum of the smoothed series within ± half the
 *     expected peak width, where the width is FWHM-aware: [fwhmKeV] models the
 *     scintillator resolution as FWHM(E) = R₆₆₂·√(662·E) (relative resolution
 *     ∝ 1/√E). R₆₆₂ belongs to the CONNECTED MODEL — 8,4 % for the CsI(Tl)
 *     models, 7,4 % for the GAGG 103G — and is passed in, never assumed;
 *  3. континуум под пиком — ЛИНЕЙНЫЙ: две боковые полосы (по одной ширине
 *     пика с каждой стороны) дают уровень под центром (полусумма средних) и
 *     наклон (их разность). Площадь считается по уровню — net = gross −
 *     continuum·width, — а вычитание внутри окна идёт по наклонной, иначе
 *     центроида уезжает в сторону более высокого края;
 *  4. значимость = нетто / σ(нетто), где Var(net) = валовые импульсы +
 *     width²·B̂/m (m — каналов в боковых полосах): учитывается и статистика
 *     окна пика, и неопределённость ОЦЕНКИ континуума [IAEA]; кандидаты
 *     below [DEFAULT_MIN_SIGNIFICANCE] are noise and dropped, как и окна с
 *     менее чем [MIN_GROSS_COUNTS] импульсами, где нормальное приближение
 *     Пуассона неприменимо;
 *  5. структура принимается за пик, только если её наблюдаемая FWHM лежит в
 *     0,5–2,5 ожидаемой по модели разрешения: одноканальный выброс — не пик.
 *     Гейт работает лишь тогда, когда высота центрального канала измерима
 *     ([SHAPE_MIN_HEIGHT_SIGNIFICANCE]): у слабой широкой линии полувысота —
 *     пуассоновский шум, и такая линия остаётся принятой с fwhmKeV = null;
 *  6. перекрывающиеся в пределах FWHM кандидаты сливаются, сильнейший по
 *     значимости побеждает.
 *
 * Pure JVM, deterministic; tested on synthetic spectra.
 */
object PeakDetection {

    /**
     * Relative FWHM at 662 keV **по умолчанию** — 8,4 % (CsI(Tl) у RC-103 и
     * RC-110). У 103G кристалл GAGG и разрешение 7,4 %, поэтому число
     * передаётся снаружи: применять к чужому прибору чужое разрешение значит
     * искать пики не той ширины.
     */
    const val RESOLUTION_662 = 0.08f

    /** Poisson significance gate: 4σ over the local continuum. */
    const val DEFAULT_MIN_SIGNIFICANCE = 4f

    /**
     * Порог поиска по умолчанию, кэВ — для моделей со шкалой от 20 кэВ.
     *
     * Число НЕ приколочено к одной модели: вызывающий передаёт
     * [DeviceModel.peakFloorKeV] своего прибора, а это значение остаётся
     * запасным для случая, когда прибор неизвестен.
     */
    const val DEFAULT_MIN_ENERGY_KEV = 40f

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
     * Во сколько раз высота центрального канала должна превышать собственную
     * пуассоновскую неопределённость, чтобы ширину на половине высоты вообще
     * имело смысл мерить.
     *
     * **Инженерный параметр.** Ширина меряется по ОДНОМУ каналу в центре:
     * пока его нетто-высота сравнима со своим шумом, «полувысота» — случайная
     * величина, и первый же провал вниз обрывает спуск на соседнем канале.
     * Именно так широкая линия 2614,5 кэВ (Tl-208) с несколькими импульсами на
     * канал получала наблюдаемую ширину 5 каналов при ожидаемых 36 и
     * отбраковывалась как «одноканальный выброс». Три сигмы — та же граница,
     * с которой в спектрометрии вообще говорят о наличии структуры.
     */
    private const val SHAPE_MIN_HEIGHT_SIGNIFICANCE = 3f

    /**
     * Наибольшая доля нетто-площади, которую может нести ОДИН канал.
     *
     * **Инженерный параметр.** У фотопика с шириной по разрешению прибора на
     * центральный канал приходится около четверти площади окна; одиночный
     * выброс несёт всю. Половина — граница между этими случаями с запасом в
     * обе стороны. Проверка работает и там, где ширину измерить нельзя: у
     * выброса сглаживание создаёт рядом плато, и кандидат встаёт на канал, где
     * собственной высоты нет вовсе.
     */
    private const val SPIKE_MAX_SHARE = 0.5f

    /**
     * Наименьшее число валовых импульсов в окне пика, при котором нормальное
     * приближение Пуассона ещё применимо и знаменатель значимости можно
     * называть σ.
     *
     * **Инженерный параметр**: при 20 импульсах асимметрия распределения
     * Пуассона (1/√N ≈ 22 %) уже заметна, но σ остаётся оценкой порядка
     * величины; ниже — числа вида «4σ» не имеют смысла, потому что само
     * распределение не гауссово.
     */
    const val MIN_GROSS_COUNTS = 20f

    /**
     * Наблюдаемая ширина на половине высоты нетто-структуры, в каналах; null,
     * если склоны не опустились до половины внутри окна поиска (структура шире
     * окна — её ширину это окно не измеряет).
     *
     * [continuumAt] — континуум под каналом: он наклонный, и вычитать под
     * склоном одно и то же число значило бы мерить ширину чужой ступеньки.
     */
    private fun fwhmChannels(
        counts: List<Int>,
        center: Int,
        half: Int,
        continuumAt: (Int) -> Float,
    ): Float? {
        val peakNet = counts[center] - continuumAt(center)
        if (peakNet <= 0f) return null
        val halfLevel = peakNet / 2f
        var left: Int? = null
        for (j in center downTo (center - 3 * half).coerceAtLeast(0)) {
            if (counts[j] - continuumAt(j) <= halfLevel) {
                left = j
                break
            }
        }
        var right: Int? = null
        for (j in center..(center + 3 * half).coerceAtMost(counts.size - 1)) {
            if (counts[j] - continuumAt(j) <= halfLevel) {
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

    fun fwhmKeV(energyKeV: Float, resolution662: Float = RESOLUTION_662): Float =
        resolution662 * sqrt(662f * max(energyKeV, 1f))

    /**
     * Ожидаемая ширина, которой руководствуется анализ: измеренная модель
     * разрешения, если она принята на этом приборе
     * ([app.alpha.analysis.evidence.ResolutionSource]), иначе √E-приближение.
     *
     * Приближение остаётся доступным как чистая функция от одной вендорской
     * точки: по нему считаются ROI радонового индикатора, и ряд обязан
     * оставаться сравнимым независимо от того, принята ли модель.
     */
    fun expectedFwhmKeV(energyKeV: Float, resolution662: Float = RESOLUTION_662): Float =
        ResolutionSource.fwhmKeV(energyKeV.toDouble())?.toFloat()
            ?: fwhmKeV(energyKeV, resolution662)

    /** Half the expected peak width in channels at [channel] (≥ 2). */
    fun halfWidthChannels(
        calibration: EnergyCalibration,
        channel: Int,
        resolution662: Float = RESOLUTION_662,
    ): Int {
        val keVPerChannel = max(calibration.a1 + 2f * calibration.a2 * channel, 0.1f)
        val energy = calibration.energyAt(channel.toFloat())
        return max(2, (expectedFwhmKeV(energy, resolution662) / 2f / keVPerChannel).roundToInt())
    }

    fun detect(
        counts: List<Int>,
        calibration: EnergyCalibration,
        minSignificance: Float = DEFAULT_MIN_SIGNIFICANCE,
        /** Разрешение ЭТОГО прибора: у 103G оно лучше, чем у 103 и 110. */
        resolution662: Float = RESOLUTION_662,
        /** Порог поиска ЭТОГО прибора: у моделей разная нижняя граница шкалы. */
        minEnergyKeV: Float = DEFAULT_MIN_ENERGY_KEV,
        /**
         * Дисперсия отсчёта в каждом канале; null — пуассоновская (Var = N).
         *
         * Нужна там, где [counts] уже не сырые импульсы: у разности «образец
         * минус фон» дисперсия равна `sample + r²·background`, и без этого
         * значимость линии в проверке продукта считалась бы по заниженному
         * знаменателю.
         */
        variance: List<Float>? = null,
    ): List<Peak> {
        require(variance == null || variance.size == counts.size) {
            "дисперсия задана для ${variance?.size} каналов из ${counts.size}"
        }
        // Крайний канал — граница шкалы, а не точка спектра ([SpectrumEdge]):
        // сюда поиск пиков не заходит вовсе.
        val n = SpectrumEdge.lastAnalysableChannel(counts.size) + 1
        if (n < 32) return emptyList()
        val smoothed = SpectrumDisplay.movingAverage(counts.map { it.toFloat() })

        val candidates = mutableListOf<Peak>()
        for (i in 2 until n - 2) {
            if (calibration.energyAt(i.toFloat()) < minEnergyKeV) continue
            val half = halfWidthChannels(calibration, i, resolution662)
            if (i - 3 * half < 0 || i + 3 * half >= n) continue

            var isMax = true
            for (j in (i - half)..(i + half)) {
                if (smoothed[j] > smoothed[i]) {
                    isMax = false
                    break
                }
            }
            if (!isMax) continue

            // Боковые полосы считаются ПОРОЗНЬ: их разность и есть наклон
            // континуума. Уровень под центром (полусумма) — то же число, что
            // и прежнее среднее по обеим полосам, поэтому площадь и значимость
            // от этого не меняются; меняется только вычитание внутри окна.
            var leftSide = 0.0
            var leftCount = 0
            for (j in (i - 3 * half)..(i - half - 1)) {
                leftSide += counts[j]; leftCount++
            }
            var rightSide = 0.0
            var rightCount = 0
            for (j in (i + half + 1)..(i + 3 * half)) {
                rightSide += counts[j]; rightCount++
            }
            val sideCount = leftCount + rightCount
            val leftMean = (leftSide / leftCount).toFloat()
            val rightMean = (rightSide / rightCount).toFloat()
            val continuum = (leftMean + rightMean) / 2f
            // Центры полос: i ∓ (2·half + 0,5); расстояние между ними
            // 4·half + 1 каналов, и наклон на канал — разность средних на нём.
            val slope = (rightMean - leftMean) / (4f * half + 1f)
            val continuumAt = { j: Int -> continuum + slope * (j - i) }

            var gross = 0f
            for (j in (i - half)..(i + half)) gross += counts[j]
            val width = 2 * half + 1
            val net = gross - continuum * width
            if (net <= 0f) continue
            // Нормальное приближение: ниже [MIN_GROSS_COUNTS] распределение
            // валовых импульсов не гауссово, и «σ» было бы просто числом.
            if (gross < MIN_GROSS_COUNTS) continue
            // Значимость = нетто / σ(нетто). Дисперсия нетто складывается из
            // статистики самого окна пика и неопределённости ОЦЕНКИ
            // континуума (IAEA, «Investigation of Uncertainty Sources in the
            // Determination of Gamma Emitting Radionuclides»):
            //     Var(net) = gross + width² · B̂ / m,
            // где B̂ — континуум на канал, m — число каналов боковых полос.
            // Прежняя формула net/√(B·width) делила только на шум фона и
            // завышала значимость, а её результат назывался «SNR».
            // Var(net) = Var(Σ окна) + width²·Var(B̂), Var(B̂) = Var(Σ полос)/m².
            // При пуассоновских отсчётах это в точности gross + width²·B̂/m.
            val varianceNet = if (variance == null) {
                gross + width.toFloat() * width * continuum / sideCount
            } else {
                var windowVariance = 0f
                for (j in (i - half)..(i + half)) windowVariance += variance[j]
                var sideVariance = 0f
                for (j in (i - 3 * half)..(i - half - 1)) sideVariance += variance[j]
                for (j in (i + half + 1)..(i + 3 * half)) sideVariance += variance[j]
                windowVariance +
                    width.toFloat() * width * sideVariance / (sideCount.toFloat() * sideCount)
            }
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
            // Одиночный выброс: один канал несёт почти всю нетто-площадь.
            // Проверка не зависит от того, измерима ли ширина, поэтому ловит и
            // выброс, рядом с которым сглаживание поставило кандидата.
            var maxChannelNet = 0f
            for (j in (i - half)..(i + half)) {
                val value = counts[j] - continuumAt(j)
                if (value > maxChannelNet) maxChannelNet = value
            }
            if (maxChannelNet > SPIKE_MAX_SHARE * net) continue

            // Ширина меряется по высоте центрального канала, поэтому гейт
            // применим, только когда эта высота измерима: у слабой широкой
            // линии (единицы импульсов на канал) «полувысота» — шум, и гейт
            // отбраковывал бы настоящие линии. Тогда ширина не измерена
            // (fwhmKeV = null), а не «не прошла».
            val centerNet = counts[i] - continuum
            val centerSigma = sqrt(max(counts[i].toFloat(), 1f) + continuum / sideCount)
            val shapeMeasurable = centerNet >= SHAPE_MIN_HEIGHT_SIGNIFICANCE * centerSigma
            val observedFwhm = fwhmChannels(counts, i, half, continuumAt)
            if (shapeMeasurable) {
                if (observedFwhm == null) continue
                val ratio = observedFwhm / (2f * half).coerceAtLeast(1f)
                if (ratio < MIN_WIDTH_RATIO || ratio > MAX_WIDTH_RATIO) continue
            }

            // Centroid over net counts refines the peak energy. Континуум под
            // окном НАКЛОННЫЙ: вычитание одного уровня оставляет внутри окна
            // остаточную ступеньку и сдвигает центроиду в сторону более
            // высокого края (на синтетике с известной истиной — на 4–5 кэВ
            // при 662 и 1461 кэВ).
            var weightSum = 0.0
            var weightedChannel = 0.0
            for (j in (i - half)..(i + half)) {
                val weight = max(0f, counts[j] - continuumAt(j))
                weightSum += weight
                weightedChannel += weight.toDouble() * j
            }
            val centroid = if (weightSum > 0) (weightedChannel / weightSum).toFloat() else i.toFloat()
            candidates += Peak(
                channel = i,
                energyKeV = calibration.energyAt(centroid),
                netCounts = net,
                significance = significance,
                // Ширина канала на шкале меняется, поэтому каналы переводятся
                // в кэВ производной калибровки в самом пике. null — высота
                // центра не измерима: спуск до полувысоты пошёл бы по шуму, и
                // число было бы шириной случайного провала, а не структуры.
                fwhmKeV = observedFwhm
                    ?.takeIf { shapeMeasurable }
                    ?.times(max(calibration.a1 + 2f * calibration.a2 * i, 0.01f)),
            )
        }

        // Merge candidates within one FWHM of a stronger accepted peak.
        val accepted = mutableListOf<Peak>()
        for (peak in candidates.sortedByDescending { it.significance }) {
            val overlaps = accepted.any {
                abs(it.energyKeV - peak.energyKeV) < expectedFwhmKeV(peak.energyKeV, resolution662)
            }
            if (!overlaps) accepted += peak
        }
        return accepted
    }
}
