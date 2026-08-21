package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

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
 * ## Почему шум шаблона входит в σ
 *
 * Шаблон — это тоже измерение, а не точная форма: у шаблона на полчаса в канале
 * верхней части шкалы единицы импульсов, и его собственный пуассоновский шум
 * переносится в долю. Кривизна правдоподобия по данным этого не видит вовсе,
 * поэтому σ, посчитанная только по ней, делает получасовой шаблон таким же
 * надёжным, как 70-часовой.
 *
 * Вклад шаблонов берётся параметрическим бутстрэпом ([Component.sigmaTemplate]),
 * а не аналитической поправкой Барлоу–Бистона. У Барлоу–Бистона на каждый канал
 * каждого шаблона заводится nuisance-параметр, и его решают вместе с долями —
 * это предполагает, что шаблон входит в модель поканально и без преобразований.
 * Здесь между сырым шаблоном и моделью стоят уширение и перекладка на чужую
 * шкалу ([SpectrumTemplate.adapt]), а сама шкала подбирается перебором: шум
 * канала шаблона расползается по соседям, и поканальных nuisance-параметров уже
 * нет. Бутстрэп не требует этого вывода — он гоняет ту же самую цепочку на
 * пересемплированном шаблоне и меряет разброс ответа, учитывая и уширение, и
 * перекладку.
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
        /**
         * Вклад статистики ДАННЫХ в неопределённость множителя: кривизна
         * правдоподобия при шаблоне, принятом за точную форму.
         */
        val sigmaData: Double,
        /**
         * Вклад статистики ШАБЛОНОВ: СКО множителя по бутстрэп-репликам, в
         * которых сырой счёт шаблонов пересемплирован из Пуассона. Ноль, когда
         * реплики не строились.
         */
        val sigmaTemplate: Double,
        /** Сколько импульсов измеренного спектра объясняет эта форма. */
        val counts: Double,
        /**
         * Предел Карри для множителя: ниже него доля неотличима от нуля.
         * Считается по статистике ДАННЫХ; шум шаблона в него не входит, поэтому
         * на коротком шаблоне предел оптимистичен.
         */
        val criticalScale: Double,
    ) {
        /**
         * Полная неопределённость множителя: √(sigmaData² + sigmaTemplate²).
         * Два источника независимы — данные и шаблон измерены порознь.
         */
        val sigma: Double get() = sqrt(sigmaData * sigmaData + sigmaTemplate * sigmaTemplate)

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
     * @param resolution662 разрешение этого прибора на 662 кэВ.
     * @param targetCurve разрешение этого прибора как функция энергии, если оно
     *   измерено по его собственным спектрам; null — работает паспортная форма
     *   FWHM ∝ √E от [resolution662].
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
        targetCurve: ResolutionCurve? = null,
    ): Result? {
        if (counts.size < SpectrumTemplate.MIN_CHANNELS || templates.isEmpty()) return null
        if (counts.sumOf { it.toLong() } <= 0L) return null

        var best: Result? = null
        var bestScale: EnergyCalibration? = null
        val gains = if (fitScale) GAIN_GRID else listOf(1.0)
        val offsets = if (fitScale) OFFSET_GRID else listOf(0.0)
        // Единственное место приведения: бутстрэп обязан гонять реплики ТЕМ ЖЕ
        // преобразованием, иначе разброс долей мерил бы разницу настроек
        // приведения, а не шум шаблона.
        val adaptTo: (SpectrumTemplate, EnergyCalibration) -> List<Double>? = { template, target ->
            SpectrumTemplate.adapt(
                template = template,
                targetCalibration = target,
                targetChannels = counts.size,
                targetResolution662 = resolution662,
                targetCurve = targetCurve,
            )
        }
        for (gain in gains) {
            for (offset in offsets) {
                val shifted = EnergyCalibration(
                    a0 = (calibration.a0 * gain + offset).toFloat(),
                    a1 = (calibration.a1 * gain).toFloat(),
                    a2 = (calibration.a2 * gain).toFloat(),
                )
                val adapted = templates.map { template ->
                    adaptTo(template, shifted) ?: return@of null
                }
                val fitted = fit(counts, adapted, templates.map { it.name }, gain, offset)
                if (best == null || fitted.cash < best!!.cash) {
                    best = fitted
                    bestScale = shifted
                }
            }
        }
        val found = best ?: return null
        val scale = bestScale ?: return found
        return withTemplateSigma(found, counts, templates, scale, adaptTo)
    }

    /**
     * Дополнить неопределённость долей вкладом статистики шаблонов.
     *
     * Реплика: сырой счёт КАЖДОГО шаблона пересемплируется поканально из
     * Пуассона со средним, равным измеренному счёту канала, шаблон заново
     * приводится к прибору и доли заново подгоняются EM. Разброс долей по
     * репликам и есть [Component.sigmaTemplate].
     *
     * Усиление и смещение при этом ФИКСИРОВАНЫ на найденных: масштабные
     * параметры определены счётом всего спектра, их собственный разброс от шума
     * шаблона мал против шага сетки, а повторение перебора умножило бы стоимость
     * разложения на размер сетки.
     *
     * @param adapt приведение шаблона к прибору — то же самое, которым получены
     *   формы основной подгонки.
     * @return тот же результат с заполненным [Component.sigmaTemplate]; при
     *   отказе приведения пересемплированного шаблона — исходный результат
     *   (вклад шаблона остаётся нулевым, σ занижена, но состав не меняется).
     */
    private fun withTemplateSigma(
        result: Result,
        counts: List<Int>,
        templates: List<SpectrumTemplate>,
        calibration: EnergyCalibration,
        adapt: (SpectrumTemplate, EnergyCalibration) -> List<Double>?,
    ): Result {
        val k = templates.size
        val start = DoubleArray(k) { result.components[it].scale }
        val random = Random(bootstrapSeed(counts))
        val replicas = Array(k) { DoubleArray(BOOTSTRAP_REPLICAS) }
        for (replica in 0 until BOOTSTRAP_REPLICAS) {
            val resampled = templates.map { template ->
                val jittered = template.copy(
                    counts = template.counts.map { poisson(it.toDouble(), random) },
                )
                adapt(jittered, calibration) ?: return result
            }
            val scales = emScales(counts, resampled, start, BOOTSTRAP_ITERATIONS)
            for (index in 0 until k) replicas[index][replica] = scales[index]
        }
        return result.copy(
            components = result.components.mapIndexed { index, component ->
                component.copy(sigmaTemplate = deviation(replicas[index]))
            },
        )
    }

    /**
     * Зерно бутстрэпа из самих данных: одинаковый спектр обязан давать
     * одинаковую σ. Экран пересчитывает разложение при каждом обновлении, и
     * случайное зерно заставило бы неопределённость мигать от вызова к вызову.
     */
    private fun bootstrapSeed(counts: List<Int>): Int {
        var total = 0L
        for (value in counts) total += value
        return (total * 31L + counts.size).toInt()
    }

    /** Выборочное СКО (делитель B−1): меньше двух реплик — разброса нет. */
    private fun deviation(values: DoubleArray): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        var sum = 0.0
        for (value in values) {
            val d = value - mean
            sum += d * d
        }
        return sqrt(sum / (values.size - 1))
    }

    /**
     * Пуассоновский отсчёт со средним [mean].
     *
     * Выше 30 импульсов берётся гауссово приближение: асимметрия Пуассона
     * равна 1/√λ, на λ = 30 это 0,18, и на разброс долей такая асимметрия не
     * влияет; метод Кнута стоил бы там порядка λ умножений на канал. Ниже 30 —
     * метод Кнута, точный по определению.
     */
    private fun poisson(mean: Double, random: Random): Int {
        if (mean <= 0.0) return 0
        if (mean > GAUSSIAN_FROM) {
            val value = mean + sqrt(mean) * gaussian(random)
            return value.roundToInt().coerceAtLeast(0)
        }
        val limit = exp(-mean)
        var product = 1.0
        var count = 0
        while (true) {
            product *= random.nextDouble()
            if (product <= limit) return count
            count++
            if (count >= KNUTH_LIMIT) return count
        }
    }

    /** Стандартная нормаль, преобразование Бокса — Мюллера. */
    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(MIN_UNIFORM)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
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
        val start = DoubleArray(k) { index ->
            val templateSum = templates[index].sum()
            if (templateSum > 0.0) total / (k * templateSum) else 0.0
        }
        val scales = emScales(counts, templates, start, ITERATIONS)

        val model = DoubleArray(n)
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
                    sigmaData = sigmas[index],
                    sigmaTemplate = 0.0,
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
     * Мультипликативные итерации EM для долей при фиксированных шаблонах.
     *
     * @param start стартовые доли; на бутстрэп-репликах это уже найденный
     *   оптимум, поэтому там хватает [BOOTSTRAP_ITERATIONS].
     * @return доли, неотрицательные по построению.
     */
    private fun emScales(
        counts: List<Int>,
        templates: List<List<Double>>,
        start: DoubleArray,
        iterations: Int,
    ): DoubleArray {
        val n = counts.size
        val k = templates.size
        val scales = start.copyOf()
        val model = DoubleArray(n)
        repeat(iterations) {
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
        return scales
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

    /**
     * Сколько бутстрэп-реплик шаблонов.
     *
     * СКО по B репликам само измерено с относительной ошибкой 1/√(2(B−1)):
     * при B = 14 это 20 %. Точнее не нужно — σ_template складывается в
     * квадратуре с σ_data, и 20 % на слагаемом дают проценты на сумме. Каждая
     * реплика стоит полного приведения шаблонов, поэтому цена линейна по B.
     */
    private const val BOOTSTRAP_REPLICAS = 14

    /**
     * Итераций EM на реплику. Реплика стартует с уже найденных долей и отличается
     * от них на статистику шаблона, то есть на доли процента: оптимум рядом, и
     * полные [ITERATIONS] здесь только жгли бы время.
     */
    private const val BOOTSTRAP_ITERATIONS = 30

    /** Выше этого среднего пуассоновский отсчёт берётся гауссовым приближением. */
    private const val GAUSSIAN_FROM = 30.0

    /** Обрыв цикла Кнута: при λ ≤ 30 отсчёт 400 — это +67σ, дальше не считаем. */
    private const val KNUTH_LIMIT = 400

    /** Ноль под логарифмом в преобразовании Бокса — Мюллера. */
    private const val MIN_UNIFORM = 1e-12

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
