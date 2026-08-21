package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Разложение спектра по шаблонам — полноспектральный анализ.
 *
 * ## Что считается
 *
 * Измеренный спектр представляется суммой известных форм:
 * `N(канал) ≈ Σ aᵢ · Tᵢ(канал)`, где `Tᵢ` — шаблон, приведённый к этому
 * прибору ([SpectrumTemplate.adapt]), а `aᵢ ≥ 0` — его доля. Отрицательных
 * долей не бывает: источник не может «убавить» излучение.
 *
 * ## Почему Пуассон, а не χ²
 *
 * В верхней части шкалы у сцинтиллятора этого класса в канале **единицы
 * импульсов**. χ² предполагает гауссовы ошибки и на таком счёте смещён:
 * вариант Неймана занижает модель, вариант Пирсона перевешивает пустые каналы.
 * Поэтому подгонка идёт по правдоподобию Пуассона (статистика Кэша, та же, что
 * у подгонки формы линии), а коэффициенты ищутся мультипликативными
 * итерациями EM — они по построению неотрицательны и сходятся монотонно.
 *
 * ## Почему усиление подгоняется
 *
 * У реального прибора шкала уезжает: на измеренном спектре 110-го линия K-40
 * стоит примерно на 1432 кэВ вместо 1460,8 — это −2 %, то есть больше половины
 * ширины линии на этой энергии. Если оставить шкалу как есть, подгонка
 * компенсирует расхождение ЧУЖИМИ компонентами: добавит тория там, где его
 * нет. Поэтому усиление и смещение ищутся вместе с долями — перебором по сетке
 * с уточнением, а не «доверием к калибровке прибора».
 *
 * ## Чего здесь нет
 *
 * Активности в беккерелях. Разложение говорит, какая доля счёта объясняется
 * каждой формой; переход к активности требует известной геометрии и эталона.
 */
object SpectrumUnmix {

    /** Вклад одной формы в измеренный спектр. */
    data class Component(
        val name: String,
        /** Множитель к шаблону: во сколько раз он входит в измеренное. */
        val scale: Double,
        /** Неопределённость множителя (кривизна правдоподобия). */
        val sigma: Double,
        /** Сколько импульсов измеренного спектра объясняет эта форма. */
        val counts: Double,
        /** Предел Карри для множителя: ниже него доля неотличима от нуля. */
        val criticalScale: Double,
    ) {
        val detected: Boolean get() = scale > criticalScale
    }

    /**
     * @param cash статистика Кэша C = 2·Σ(model − data·ln model): чем меньше,
     *   тем ближе модель. Сравнивать её с числом степеней свободы НЕЛЬЗЯ —
     *   для оценки согласия есть [cashExpected] и [cashDeviation].
     * @param cashExpected ожидание C при верной модели (приближение Каастры).
     * @param cashDeviation (C − ожидание) / σ(ожидания): сколько σ до
     *   согласия. Это и есть замена χ²/ndf, у которой на малых счётах нет
     *   смысла.
     * @param explainedFraction доля объяснённого счёта — ВСПОМОГАТЕЛЬНОЕ
     *   число для человека, а не критерий: широкая форма поглощает остаток и
     *   даёт 99 % при неверном составе.
     * @param gain подобранное усиление шкалы (1 = калибровка прибора верна).
     * @param offsetKeV подобранное смещение шкалы, кэВ.
     */
    data class Result(
        val components: List<Component>,
        val cash: Double,
        val cashExpected: Double,
        val cashDeviation: Double,
        val explainedFraction: Double,
        val gain: Double,
        val offsetKeV: Double,
        /** Остатки по каналам в единицах σ — где именно модель не сошлась. */
        val residualSigma: List<Double>,
    ) {
        /** Модель описывает данные: отклонение статистики в пределах порога. */
        val consistent: Boolean get() = abs(cashDeviation) <= CONSISTENT_SIGMAS
    }

    /**
     * Разложить измеренный спектр по шаблонам.
     *
     * @param counts измеренный спектр, сырые импульсы.
     * @param calibration калибровка ЭТОГО прибора (стартовая точка для поиска
     *   усиления и смещения).
     * @param resolution662 разрешение этого прибора.
     * @param templates формы, по которым раскладываем; шаблон, который не
     *   приводится к этому прибору, молча не пропадает — он выбрасывает всю
     *   попытку, потому что состав без одной из форм означает другое.
     * @return null, если ни один шаблон не привёлся или спектр пуст.
     */
    fun of(
        counts: List<Int>,
        calibration: EnergyCalibration,
        resolution662: Float,
        templates: List<SpectrumTemplate>,
        fitScale: Boolean = true,
    ): Result? {
        if (counts.size < SpectrumTemplate.MIN_CHANNELS || templates.isEmpty()) return null
        if (counts.sumOf { it.toLong() } <= 0L) return null

        var best: Result? = null
        val gains = if (fitScale) GAIN_GRID else listOf(1.0)
        val offsets = if (fitScale) OFFSET_GRID else listOf(0.0)
        for (gain in gains) {
            for (offset in offsets) {
                val shifted = EnergyCalibration(
                    a0 = (calibration.a0 * gain + offset).toFloat(),
                    a1 = (calibration.a1 * gain).toFloat(),
                    a2 = (calibration.a2 * gain).toFloat(),
                )
                val adapted = templates.map { template ->
                    SpectrumTemplate.adapt(
                        template = template,
                        targetCalibration = shifted,
                        targetChannels = counts.size,
                        targetResolution662 = resolution662,
                    ) ?: return@of null
                }
                val fitted = fit(counts, adapted, templates.map { it.name }, gain, offset)
                if (best == null || fitted.cash < best!!.cash) best = fitted
            }
        }
        return best
    }

    /** Одна подгонка при ФИКСИРОВАННОЙ шкале: только доли. */
    private fun fit(
        counts: List<Int>,
        templates: List<List<Double>>,
        names: List<String>,
        gain: Double,
        offset: Double,
    ): Result {
        val n = counts.size
        val k = templates.size
        // Старт: каждая форма несёт равную долю измеренного счёта. Нулевой
        // старт у мультипликативных итераций — ловушка: ноль умножается в ноль.
        val total = counts.sumOf { it.toDouble() }
        val scales = DoubleArray(k) { index ->
            val templateSum = templates[index].sum()
            if (templateSum > 0.0) total / (k * templateSum) else 0.0
        }

        val model = DoubleArray(n)
        repeat(ITERATIONS) {
            for (channel in 0 until n) {
                var value = 0.0
                for (index in 0 until k) value += scales[index] * templates[index][channel]
                model[channel] = value
            }
            for (index in 0 until k) {
                var numerator = 0.0
                var denominator = 0.0
                for (channel in 0 until n) {
                    val t = templates[index][channel]
                    if (t <= 0.0) continue
                    denominator += t
                    val m = model[channel]
                    if (m > 0.0) numerator += counts[channel] * t / m
                }
                if (denominator > 0.0 && numerator > 0.0) {
                    scales[index] *= numerator / denominator
                } else {
                    scales[index] = 0.0
                }
            }
        }

        for (channel in 0 until n) {
            var value = 0.0
            for (index in 0 until k) value += scales[index] * templates[index][channel]
            model[channel] = value
        }

        // σ множителя из кривизны правдоподобия: d²C/da² = Σ N·T²/M².
        val sigmas = DoubleArray(k)
        val critical = DoubleArray(k)
        for (index in 0 until k) {
            var curvature = 0.0
            var zeroCurvature = 0.0
            for (channel in 0 until n) {
                val t = templates[index][channel]
                if (t <= 0.0) continue
                val m = model[channel]
                if (m > 0.0) curvature += counts[channel] * t * t / (m * m)
                // При отсутствии этой формы её место занимают остальные:
                // предел Карри считается по модели БЕЗ неё.
                val without = m - scales[index] * t
                if (without > 0.0) zeroCurvature += without * t * t / (without * without)
            }
            sigmas[index] = if (curvature > 0.0) 1.0 / sqrt(curvature) else Double.NaN
            critical[index] = if (zeroCurvature > 0.0) SIGMAS * (1.0 / sqrt(zeroCurvature)) else 0.0
        }

        var cash = 0.0
        var expected = 0.0
        var variance = 0.0
        val residual = DoubleArray(n)
        for (channel in 0 until n) {
            val m = max(model[channel], MIN_MODEL)
            val data = counts[channel].toDouble()
            cash += 2.0 * (m - data * ln(m))
            if (data > 0.0) cash += 2.0 * (data * ln(data) - data)
            expected += kaastraExpectation(m)
            variance += kaastraVariance(m)
            residual[channel] = (data - m) / sqrt(max(m, 1.0))
        }

        val explained = (0 until n).sumOf { minOf(model[it], counts[it].toDouble()) }

        return Result(
            components = (0 until k).map { index ->
                Component(
                    name = names[index],
                    scale = scales[index],
                    sigma = sigmas[index],
                    counts = scales[index] * templates[index].sum(),
                    criticalScale = critical[index],
                )
            },
            cash = cash,
            cashExpected = expected,
            cashDeviation = if (variance > 0.0) (cash - expected) / sqrt(variance) else 0.0,
            explainedFraction = if (total > 0.0) explained / total else 0.0,
            gain = gain,
            offsetKeV = offset,
            residualSigma = residual.toList(),
        )
    }

    /**
     * Ожидание вклада канала в статистику Кэша при верной модели.
     *
     * Приближение Каастры и Ван-ден-Оорда (A&A 587, A151, 2016): у C-статистики
     * нет числа степеней свободы, но её ожидание и дисперсия считаются по
     * модели поканально. Без этого «согласие» пришлось бы оценивать на глаз.
     */
    private fun kaastraExpectation(model: Double): Double = when {
        model <= 0.5 -> -0.25 * model * model * model + 1.38 * model * model -
            2.0 * model * ln(model.coerceAtLeast(MIN_MODEL))
        model <= 2.0 -> -0.00335 * model * model * model * model * model +
            0.04259 * model * model * model * model - 0.27331 * model * model * model +
            1.381 * model * model - 2.0 * model * ln(model)
        model <= 5.0 -> 1.019275 + 0.1345 * Math.pow(model, 0.461 - 0.9 * ln(model))
        model <= 10.0 -> 1.00624 + 0.604 / Math.pow(model, 1.68)
        else -> 1.0 + 0.1649 / model + 0.226 / (model * model)
    }

    private fun kaastraVariance(model: Double): Double = when {
        model <= 0.1 -> {
            var sum = 0.0
            var term = -262.0
            for (power in intArrayOf(7, 6, 5, 4, 3, 2, 1, 0)) {
                term = when (power) {
                    7 -> -262.0
                    6 -> 195.0
                    5 -> 1865.0
                    4 -> -9721.0
                    3 -> 8353.0
                    2 -> -1697.0
                    1 -> 137.4
                    else -> 0.0
                }
                sum += term * Math.pow(model, power.toDouble())
            }
            sum.coerceAtLeast(0.0)
        }
        model <= 0.2 -> 4.23 + 2.145 * model - 0.5949 * model * model + 0.0872 * model * model * model
        model <= 3.0 -> 4.115 - 2.556 / model + 0.6 / (model * model)
        model <= 100.0 -> 2.0 + 20.0 / model + 20.0 / (model * model)
        else -> 2.0
    }

    /** Ниже этого значения модель считается нулевой — логарифм не берётся. */
    private const val MIN_MODEL = 1e-9

    /** Сколько итераций EM: дальше множители меняются меньше промилле. */
    private const val ITERATIONS = 200

    /** Односторонний 95 % критерий Карри — тот же, что у пределов линий. */
    private const val SIGMAS = 1.645

    /** Порог согласия: дальше этого модель данные не описывает. */
    const val CONSISTENT_SIGMAS = 3.0

    /**
     * Сетка усиления: ±3 % шагом 0,5 %. Измеренный дрейф шкалы прибора около
     * 2 %, и сетка обязана его накрывать с запасом.
     */
    private val GAIN_GRID = listOf(0.97, 0.975, 0.98, 0.985, 0.99, 0.995, 1.0, 1.005, 1.01, 1.02, 1.03)

    /** Сетка смещения: ±10 кэВ. Больше — это уже не смещение, а другая шкала. */
    private val OFFSET_GRID = listOf(-10.0, -5.0, 0.0, 5.0, 10.0)
}
