package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Подгонка формы линии: гауссово ядро с экспоненциальными хвостами (ExpGaussExp).
 *
 * ## Зачем не гауссиана
 *
 * У сцинтилляционного детектора пик не симметричен: неполный сбор света и
 * многократное комптоновское рассеяние в кристалле дают хвост со стороны
 * низких энергий. Симметричная гауссиана тянет центр в этот хвост, и энергия
 * линии оказывается ниже истинной — тем сильнее, чем длиннее хвост. Ошибка
 * центроида напрямую превращается в ошибку сопоставления с линией нуклида.
 *
 * Форма ExpGaussExp (S. Das, arXiv:1603.08591) — гауссиана, которая с
 * заданных точек `z = −kL` и `z = +kR` продолжается экспонентами, сшитыми по
 * значению и производной:
 *
 *     z = (x − μ)/σ
 *     f = A·exp(−z²/2),               −kL ≤ z ≤ kR
 *     f = A·exp(kL²/2 + kL·z),        z < −kL
 *     f = A·exp(kR²/2 − kR·z),        z > kR
 *
 * Пять параметров формы (A, μ, σ, kL, kR) вместо трёх, плюс два на континуум
 * под окном — уровень и наклон.
 *
 * ## Почему континуум подгоняется, а не берётся готовым
 *
 * Оценка из боковых полос ([PeakDetection]) — прямая, а настоящая подложка под
 * окном шириной в три полуширины заметно выгнута: на реальном спектре с
 * миллионами импульсов расхождение прямой с подложкой даёт C/ndf около 3,7 при
 * идеальной в остальном форме, и годная линия отбраковывалась бы именно там,
 * где статистики больше всего. Поэтому уровень и наклон входят в подгонку, а
 * оценка полос служит начальным приближением и границей правдоподобия
 * ([MAX_CONTINUUM_FACTOR]): уйти от неё в разы, «съев» пик, подгонка не может.
 *
 * ## Критерий согласия
 *
 * Не χ², а статистика Кэша (Cash, ApJ 228 (1979) 939):
 *
 *     C = 2·Σ [ m_i − n_i + n_i·ln(n_i/m_i) ]
 *
 * — отношение правдоподобий для пуассоновских отсчётов. χ² с весом 1/n на
 * каналах с единицами импульсов систематически занижает амплитуду; на хвостах
 * линии таких каналов большинство. C распределена как χ² с тем же числом
 * степеней свободы, поэтому C/ndf читается так же.
 *
 * ## Границы метода
 *
 * Подгонка описывает ОДНУ линию на известном континууме. Дублет она опишет
 * плохо — это видно по C/ndf, и результат с большим C/ndf отбрасывается
 * ([MAX_REDUCED_C]). Неопределённость центроида даётся статистической оценкой
 * σ_μ ≈ σ/√N, где N — площадь нетто: полная ковариационная матрица здесь не
 * считается, и на это опираться нельзя.
 */
object PeakShapeFit {

    /**
     * Предельное C/ndf, при котором форма считается описанной. **Инженерный
     * параметр**: 3 — при пяти параметрах и трёх десятках каналов это уже
     * заведомая непригодность модели (для χ²-подобной статистики p ≈ 10⁻⁶ при
     * ndf = 25), а не статистическая флуктуация. Дублет и наложение линий
     * дают именно такие значения.
     */
    const val MAX_REDUCED_C = 3.0

    /**
     * Предельный сдвиг центра от исходной оценки, в σ формы. **Инженерный
     * параметр**: 1,5 — подгонка уточняет положение линии, а не ищет другую.
     * Ушедший дальше центр означает, что симплекс сошёлся к соседнему пику.
     */
    const val MAX_CENTER_SHIFT_SIGMA = 1.5

    /**
     * Пределы точек сшивки хвостов, в σ. **Инженерные параметры**: ниже 0,5 σ
     * экспонента подменяет собой само ядро, и «форма» перестаёт быть
     * гауссовой; выше 4 σ хвост лежит там, где отсчётов уже нет, и параметр
     * не определён данными.
     */
    const val MIN_TAIL = 0.5
    const val MAX_TAIL = 4.0

    /**
     * Во сколько раз подогнанный уровень континуума может отличаться от оценки
     * боковых полос. **Инженерный параметр**: 2 — подложка под окном выгнута,
     * но не в разы; больший разброс означает, что подгонка перераспределила
     * импульсы между пиком и подложкой, и площади верить нельзя.
     */
    const val MAX_CONTINUUM_FACTOR = 2.0

    /**
     * Итераций симплекса. **Инженерный параметр**: семь параметров сходятся
     * медленнее пяти, и 400 шагов на реальных пиках останавливались до
     * сходимости.
     */
    private const val MAX_ITERATIONS = 900

    /** Относительный порог остановки по разбросу вершин симплекса. */
    private const val TOLERANCE = 1e-4

    data class Shape(
        /** Высота ядра над континуумом, импульсов в канале. */
        val amplitude: Double,
        /** Центр линии, каналы (дробные). */
        val centerChannel: Double,
        /** σ гауссова ядра, каналы. */
        val sigmaChannels: Double,
        /** Точка сшивки левого хвоста, в σ. */
        val tailLeft: Double,
        /** Точка сшивки правого хвоста, в σ. */
        val tailRight: Double,
    ) {
        /** Значение формы в канале (без континуума). */
        fun at(channel: Double): Double {
            val z = (channel - centerChannel) / sigmaChannels
            return when {
                z < -tailLeft -> amplitude * exp(tailLeft * tailLeft / 2.0 + tailLeft * z)
                z > tailRight -> amplitude * exp(tailRight * tailRight / 2.0 - tailRight * z)
                else -> amplitude * exp(-z * z / 2.0)
            }
        }

        /**
         * Асимметрия: во сколько раз левый хвост длиннее правого.
         *
         * Больше единицы — хвост со стороны низких энергий, обычная картина у
         * сцинтиллятора. Точка сшивки обратна длине хвоста, поэтому отношение
         * перевёрнуто.
         */
        val asymmetry: Double get() = tailRight / tailLeft
    }

    data class Result(
        val shape: Shape,
        /** Подогнанный уровень континуума под центром линии, импульсов. */
        val continuumAtCenter: Double,
        /** Подогнанный наклон континуума, импульсов на канал. */
        val continuumSlope: Double,
        /** Площадь линии над континуумом, импульсов. */
        val netCounts: Double,
        /** Статистическая неопределённость центра, каналы. */
        val centerSigmaChannels: Double,
        /** Статистика Кэша, делённая на число степеней свободы. */
        val reducedC: Double,
        /** Число степеней свободы подгонки. */
        val degreesOfFreedom: Int,
    ) {
        /** FWHM формы, каналы — численно по самой [Shape], а не 2,355·σ. */
        val fwhmChannels: Double get() = fwhmOf(shape)
    }

    /**
     * Подогнать форму к окну спектра.
     *
     * @param counts отсчёты по каналам
     * @param range каналы окна подгонки включительно; должно быть не меньше
     *   [MIN_CHANNELS] каналов и охватывать пик целиком
     * @param continuumAt континуум под каналом, импульсов — берётся готовым
     * @param centerGuess исходная оценка центра, каналы
     * @param sigmaGuess исходная оценка σ, каналы
     * @return null, когда окно мало, нетто-площадь неположительна, симплекс не
     *   сошёлся, центр ушёл дальше [MAX_CENTER_SHIFT_SIGMA] или C/ndf выше
     *   [MAX_REDUCED_C] — то есть всегда, когда форме верить нельзя
     */
    fun fit(
        counts: List<Int>,
        range: IntRange,
        continuumAt: (Int) -> Double,
        centerGuess: Double,
        sigmaGuess: Double,
        /** Предел согласия; вынесен параметром, чтобы его можно было измерить. */
        maxReducedC: Double = MAX_REDUCED_C,
    ): Result? {
        val n = range.last - range.first + 1
        if (n < MIN_CHANNELS) return null
        if (range.first < 0 || range.last >= counts.size) return null
        if (sigmaGuess <= 0.0 || !sigmaGuess.isFinite()) return null

        val channels = range.toList()
        val observed = channels.map { counts[it].toDouble() }
        // Начальный континуум — оценка боковых полос: её уровень под центром и
        // её наклон. Дальше оба уточняются подгонкой.
        val startLevel = max(continuumAt(centerGuess.toInt().coerceIn(range)), 0.0)
        val startSlope = (continuumAt(range.last) - continuumAt(range.first)) /
            (range.last - range.first).toDouble()
        val netGuess = observed.indices.sumOf {
            observed[it] - (startLevel + startSlope * (channels[it] - centerGuess))
        }
        // Слабой линии подгонка не помогает, а мешает: она снимает СИСТЕМАТИКУ
        // хвоста, а на малой площади хвост неотличим от шума, и семь свободных
        // параметров добавляют разброс вместо того, чтобы убрать смещение.
        if (netGuess < MIN_FIT_COUNTS) return null
        // Высота ядра из площади: A = N / (σ·√(2π)) для чистой гауссианы —
        // хвосты её только увеличивают, поэтому это заведомо нижняя оценка,
        // и симплекс идёт вверх.
        val amplitudeGuess = netGuess / (sigmaGuess * sqrt(2.0 * Math.PI))

        val start = doubleArrayOf(
            amplitudeGuess, centerGuess, sigmaGuess, 2.0, 2.0, startLevel, startSlope,
        )
        val objective = { p: DoubleArray ->
            val candidate = shapeOf(p)
            if (candidate == null) {
                Double.MAX_VALUE
            } else {
                cash(candidate, p[5], p[6], centerGuess, channels, observed)
            }
        }
        val best = nelderMead(start, objective) ?: return null
        val shape = shapeOf(best) ?: return null
        val level = best[5]
        val slope = best[6]
        if (!level.isFinite() || !slope.isFinite() || level < 0.0) return null
        // Континуум не имеет права уйти от оценки полос в разы: иначе подгонка
        // просто переложила импульсы из пика в подложку или обратно.
        if (startLevel > 0.0) {
            val factor = level / startLevel
            if (factor > MAX_CONTINUUM_FACTOR || factor < 1.0 / MAX_CONTINUUM_FACTOR) return null
        }

        if (abs(shape.centerChannel - centerGuess) > MAX_CENTER_SHIFT_SIGMA * sigmaGuess) {
            return null
        }
        val degreesOfFreedom = n - PARAMETERS
        if (degreesOfFreedom <= 0) return null
        val reduced = cash(shape, level, slope, centerGuess, channels, observed) / degreesOfFreedom
        if (!reduced.isFinite() || reduced > maxReducedC) return null

        val net = channels.sumOf { shape.at(it.toDouble()) }
        if (net <= 0.0) return null
        // σ_μ = σ/√N — статистическая оценка неопределённости центра тяжести
        // для N независимых отсчётов; полная ковариация подгонки не считается.
        val centerSigma = shape.sigmaChannels / sqrt(net)

        return Result(
            shape = shape,
            continuumAtCenter = level,
            continuumSlope = slope,
            netCounts = net,
            centerSigmaChannels = centerSigma,
            reducedC = reduced,
            degreesOfFreedom = degreesOfFreedom,
        )
    }

    /** FWHM формы численно: у ExpGaussExp она не равна 2,355·σ при коротких хвостах. */
    fun fwhmOf(shape: Shape): Double {
        val half = shape.amplitude / 2.0
        fun edge(direction: Int): Double {
            var lo = shape.centerChannel
            var hi = shape.centerChannel + direction * 10.0 * shape.sigmaChannels
            repeat(60) {
                val mid = (lo + hi) / 2.0
                if (shape.at(mid) > half) lo = mid else hi = mid
            }
            return (lo + hi) / 2.0
        }
        return edge(1) - edge(-1)
    }

    private fun shapeOf(p: DoubleArray): Shape? {
        val amplitude = p[0]
        val sigma = p[2]
        if (!p.all { it.isFinite() }) return null
        if (amplitude <= 0.0 || sigma <= 0.0) return null
        val left = p[3].coerceIn(MIN_TAIL, MAX_TAIL)
        val right = p[4].coerceIn(MIN_TAIL, MAX_TAIL)
        return Shape(amplitude, p[1], sigma, left, right)
    }

    /**
     * Статистика Кэша: 2·Σ[m − n + n·ln(n/m)]. Канал с нулём отсчётов даёт
     * вклад 2·m — предел n·ln(n/m) при n → 0 равен нулю.
     */
    private fun cash(
        shape: Shape,
        continuumLevel: Double,
        continuumSlope: Double,
        continuumOrigin: Double,
        channels: List<Int>,
        observed: List<Double>,
    ): Double {
        var sum = 0.0
        for (i in channels.indices) {
            val channel = channels[i].toDouble()
            val continuum = continuumLevel + continuumSlope * (channel - continuumOrigin)
            // Ожидание не бывает нулевым: логарифм от нуля сделал бы
            // статистику бесконечной там, где континуум просто пуст.
            val model = max(shape.at(channel) + continuum, MIN_EXPECTED)
            val data = observed[i]
            sum += model - data + if (data > 0.0) data * ln(data / model) else 0.0
        }
        return 2.0 * sum
    }

    /**
     * Симплекс Нелдера — Мида: производных у статистики Кэша по параметрам
     * формы с изломами нет в замкнутом виде, а численные на счётах с шумом
     * ведут себя хуже симплекса.
     */
    private fun nelderMead(start: DoubleArray, objective: (DoubleArray) -> Double): DoubleArray? {
        val dimension = start.size
        val simplex = Array(dimension + 1) { i ->
            start.copyOf().also {
                if (i > 0) {
                    val step = if (abs(it[i - 1]) > 1e-9) 0.1 * it[i - 1] else 0.1
                    it[i - 1] += step
                }
            }
        }
        val values = DoubleArray(dimension + 1) { objective(simplex[it]) }
        repeat(MAX_ITERATIONS) {
            val order = values.indices.sortedBy { values[it] }
            val bestIndex = order.first()
            val worstIndex = order.last()
            val secondWorstIndex = order[order.size - 2]
            val spread = abs(values[worstIndex] - values[bestIndex])
            if (spread <= TOLERANCE * (abs(values[bestIndex]) + TOLERANCE)) {
                return simplex[bestIndex]
            }

            val centroid = DoubleArray(dimension)
            for (index in order.dropLast(1)) {
                for (k in 0 until dimension) centroid[k] += simplex[index][k] / dimension
            }
            fun move(factor: Double) = DoubleArray(dimension) { k ->
                centroid[k] + factor * (centroid[k] - simplex[worstIndex][k])
            }

            val reflected = move(1.0)
            val reflectedValue = objective(reflected)
            when {
                reflectedValue < values[bestIndex] -> {
                    val expanded = move(2.0)
                    val expandedValue = objective(expanded)
                    if (expandedValue < reflectedValue) {
                        simplex[worstIndex] = expanded
                        values[worstIndex] = expandedValue
                    } else {
                        simplex[worstIndex] = reflected
                        values[worstIndex] = reflectedValue
                    }
                }

                reflectedValue < values[secondWorstIndex] -> {
                    simplex[worstIndex] = reflected
                    values[worstIndex] = reflectedValue
                }

                else -> {
                    val contracted = move(-0.5)
                    val contractedValue = objective(contracted)
                    if (contractedValue < values[worstIndex]) {
                        simplex[worstIndex] = contracted
                        values[worstIndex] = contractedValue
                    } else {
                        // Сжатие всего симплекса к лучшей вершине.
                        for (index in values.indices) {
                            if (index == bestIndex) continue
                            for (k in 0 until dimension) {
                                simplex[index][k] =
                                    simplex[bestIndex][k] +
                                    0.5 * (simplex[index][k] - simplex[bestIndex][k])
                            }
                            values[index] = objective(simplex[index])
                        }
                    }
                }
            }
        }
        val bestIndex = values.indices.minBy { values[it] }
        return simplex[bestIndex].takeIf { values[bestIndex].isFinite() }
    }

    /**
     * Минимальная нетто-площадь линии, при которой подгонка вообще имеет
     * смысл. **Инженерный параметр**: 500 импульсов — при них статистическая
     * неопределённость центра σ/√N составляет около FWHM/50, то есть заметно
     * меньше того смещения в десятки кэВ, ради которого форма и подгоняется.
     * На линии из 85 импульсов (проверено на реальном спектре) подгонка
     * уводила центр от истинного, а не к нему: хвост там неотличим от шума.
     */
    const val MIN_FIT_COUNTS = 500.0

    /** Параметров всего: A, μ, σ, kL, kR и два на континуум. */
    private const val PARAMETERS = 7

    /** Минимальное ожидание в канале, импульсов: логарифм от нуля не берётся. */
    private const val MIN_EXPECTED = 1e-6

    /**
     * Минимум каналов в окне подгонки. **Инженерный параметр**: 21 — на семь
     * параметров нужно заметно больше точек, чем параметров, иначе ndf
     * настолько мал, что C/ndf ничего не отбраковывает.
     */
    const val MIN_CHANNELS = 21
}
