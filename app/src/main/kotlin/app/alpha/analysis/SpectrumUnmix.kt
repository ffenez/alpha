package app.alpha.analysis

import app.alpha.analysis.evidence.MeasuredResolution
import app.alpha.analysis.evidence.ResolutionModel
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
 * Итерации идут до СХОДИМОСТИ ([CONVERGENCE]), а не до назначенного числа шагов.
 * Остановка по счёту шагов у этой задачи не безобидна: вложенная модель с лишней
 * формой не может описывать данные хуже, чем модель без неё, а недосчитанная —
 * описывала (C больше на 21 при 200 шагах), и лишняя форма при этом уносила
 * 0,9 % счёта, которого ей никто не давал. Скорость возвращает ньютоновский
 * рывок ([newtonJump]): те же данные сходятся за 10 итераций вместо 2500.
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
 * ## Почему σ доли берётся из ОБРАТНОЙ информационной матрицы
 *
 * Формы не ортогональны: ториевый ряд и собственный фон прибора перекрываются
 * почти на всей шкале, и подгонка может обменять долю одной формы на долю
 * другой почти без ущерба правдоподобию. Диагональ кривизны `Σ N·T_j²/M²`
 * отвечает на другой вопрос — «как быстро портится правдоподобие, если двигать
 * ТОЛЬКО долю j, держа чужие доли на найденных значениях». Чужие доли не даны,
 * они оценены из тех же данных, и их собственный разброс перетекает в долю j.
 *
 * Поэтому неопределённость доли — корень диагонального элемента ОБРАТНОЙ
 * информационной матрицы `I_jl = Σ N·T_j·T_l/M²`. Для положительно определённой
 * матрицы `(I⁻¹)_jj ≥ 1/I_jj` всегда, с равенством только при полной
 * ортогональности форм: диагональ даёт нижнюю границу, а не ответ. Правка
 * односторонняя — σ и предел обнаружения только растут, «обнаружено»
 * встречается реже. Для этого прибора ложное «обнаружено» тяжелее пропуска.
 *
 * Матрица, которая не обращается (две неразличимые формы), даёт σ = NaN — так же
 * как нулевая кривизна. Доли, не разделимые в принципе, не имеют разброса,
 * который можно назвать числом.
 *
 * ## Почему предел обнаружения считается при НУЛЕВОЙ доле
 *
 * Предел Карри ([Component.criticalScale]) отвечает на вопрос «какой множитель
 * ещё объясним отсутствием этой формы», то есть относится к гипотезе H₀ «доли
 * нет». Значит и разброс оценки берётся при нулевой доле, а не при найденной:
 * подставить в предел готовую [Component.sigma] нельзя — она измерена вокруг
 * найденного оптимума и на заметной доле систематически другая.
 *
 * Вклад данных в этот разброс информационная матрица даёт аналитически: она
 * строится по модели `M₀` БЕЗ этой формы (`I₀_jl = Σ T_j·T_l/M₀`, ожидание счёта
 * при H₀ равно самой `M₀`), включая строку и столбец проверяемой формы, и берётся
 * корень её обращённого диагонального элемента. Вклад шума шаблонов — тем же
 * бутстрэпом, что и
 * [Component.sigmaTemplate], но подгонкой при `a = 0`. Без этой добавки короткий
 * шаблон получает такой же порог, как длинный, и [Component.detected] говорит
 * «есть» там, где полная неопределённость этого уже не держит.
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
         * Вклад статистики ДАННЫХ в неопределённость множителя при шаблоне,
         * принятом за точную форму: `√((I⁻¹)_jj)` по информационной матрице
         * правдоподобия. Не диагональ кривизны — она считает чужие доли
         * известными и занижает σ тем сильнее, чем больше формы перекрываются.
         * NaN, когда матрица не обращается: доли неразличимых форм не разделены.
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
         *
         * `1,645·√(σ_данные,0² + σ_шаблоны,0²)`, обе величины — разброс оценки
         * множителя при гипотезе «этой формы нет»: данные дают его обращённой
         * информационной матрицей, построенной по модели БЕЗ этой формы,
         * шаблоны — бутстрэпом при нулевой доле. При разложении с ОДНОЙ формой
         * модели без неё не существует, оба слагаемых нулевые, и предел равен
         * нулю: сравнивать единственную форму не с чем.
         *
         * NaN, когда матрица гипотезы не обращается: порога у неразделимых форм
         * нет, и [detected] тогда ложно при любом множителе.
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
     * @param targetResolution действующая модель разрешения этого прибора,
     *   если приложение измерило её по линиям ([MeasuredResolution]); null —
     *   работает паспортная форма FWHM ∝ √E от [resolution662].
     * @param templates формы, по которым раскладываем; шаблон, который не
     *   приводится к этому прибору, молча не пропадает — он выбрасывает всю
     *   попытку, потому что состав без одной из форм означает другое.
     * @param scalePrior измеренный температурный ход шкалы, пересчитанный на
     *   нынешнюю температуру прибора: он говорит, ГДЕ искать усиление, но не
     *   заменяет поиск — величина по-прежнему определяется по самому спектру.
     *   null — сетка остаётся широкой и центрированной на калибровке прибора.
     * @return null, если ни один шаблон не привёлся или спектр пуст.
     */
    fun of(
        counts: List<Int>,
        calibration: EnergyCalibration,
        resolution662: Float,
        templates: List<SpectrumTemplate>,
        fitScale: Boolean = true,
        targetResolution: ResolutionModel? = null,
        scalePrior: ScalePrior? = null,
    ): Result? {
        if (counts.size < SpectrumTemplate.MIN_CHANNELS || templates.isEmpty()) return null
        if (counts.sumOf { it.toLong() } <= 0L) return null

        var best: Result? = null
        var bestScale: EnergyCalibration? = null
        var bestShapes: List<List<Double>>? = null
        val gains = if (fitScale) gainGrid(scalePrior) else listOf(1.0)
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
                targetResolution = targetResolution,
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
                    bestShapes = adapted
                }
            }
        }
        val found = best ?: return null
        val scale = bestScale ?: return found
        val shapes = bestShapes ?: return found
        return withTemplateSigma(found, counts, templates, shapes, scale, adaptTo)
    }

    /**
     * Дополнить вкладом статистики шаблонов и неопределённость долей, и предел
     * обнаружения.
     *
     * Реплика: сырой счёт КАЖДОГО шаблона пересемплируется поканально из
     * Пуассона со средним, равным измеренному счёту канала, шаблоны заново
     * приводятся к прибору. Дальше одна и та же реплика читается дважды.
     *
     * 1. **Разброс найденной доли** — [Component.sigmaTemplate]: доли заново
     *    подгоняются EM со старта в найденном оптимуме.
     * 2. **Разброс доли при гипотезе H₀** — добавка к [Component.criticalScale]:
     *    для каждой формы EM подгоняет фон БЕЗ неё, а её собственная доля
     *    оценивается [zeroScale] на остатке. Предел Карри относится к гипотезе
     *    «доли нет», поэтому её разброс и меряется при нулевой доле, а не берётся
     *    от найденной.
     *
     * Данные в репликах НЕ пересемплируются: их вклад в предел уже посчитан
     * аналитически по кривизне (`zeroCurvature` в [fit]), и пересемплировка
     * добавила бы его второй раз.
     *
     * Усиление и смещение ФИКСИРОВАНЫ на найденных: масштабные параметры
     * определены счётом всего спектра, их собственный разброс от шума шаблона мал
     * против шага сетки, а повторение перебора умножило бы стоимость разложения
     * на размер сетки.
     *
     * @param shapes формы основной подгонки — шаблоны, приведённые к прибору при
     *   найденной шкале.
     * @param adapt приведение шаблона к прибору — то же самое, которым получены
     *   [shapes].
     * @return тот же результат с заполненным [Component.sigmaTemplate] и пределом,
     *   поднятым на шум шаблонов; при отказе приведения пересемплированного
     *   шаблона — исходный результат (вклад шаблона остаётся нулевым, σ и предел
     *   занижены, но состав не меняется).
     */
    private fun withTemplateSigma(
        result: Result,
        counts: List<Int>,
        templates: List<SpectrumTemplate>,
        shapes: List<List<Double>>,
        calibration: EnergyCalibration,
        adapt: (SpectrumTemplate, EnergyCalibration) -> List<Double>?,
    ): Result {
        val k = templates.size
        val start = DoubleArray(k) { result.components[it].scale }
        // Фон гипотезы H₀ на НАСТОЯЩИХ шаблонах: он же старт EM в репликах.
        // Пересемплировка шаблона двигает фон на доли процента, поэтому реплике
        // хватает BOOTSTRAP_ITERATIONS вместо полного счёта итераций.
        val zeroStart = Array(k) { index ->
            val rest = withoutIndex(shapes, index)
            if (rest.isEmpty()) {
                DoubleArray(0)
            } else {
                emScales(counts, rest, equalShareStart(counts, rest), ITERATIONS)
            }
        }
        val random = Random(bootstrapSeed(counts))
        val replicas = Array(k) { DoubleArray(BOOTSTRAP_REPLICAS) }
        val zeroReplicas = Array(k) { DoubleArray(BOOTSTRAP_REPLICAS) }
        for (replica in 0 until BOOTSTRAP_REPLICAS) {
            val resampled = templates.map { template ->
                val jittered = template.copy(
                    counts = template.counts.map { poisson(it.toDouble(), random) },
                )
                adapt(jittered, calibration) ?: return result
            }
            val scales = emScales(counts, resampled, start, BOOTSTRAP_ITERATIONS)
            for (index in 0 until k) {
                replicas[index][replica] = scales[index]
                zeroReplicas[index][replica] =
                    zeroScale(counts, resampled, index, zeroStart[index])
            }
        }
        return result.copy(
            components = result.components.mapIndexed { index, component ->
                // 1,645·√(σ_данные,0² + σ_шаблоны,0²) = √(предел_данных² + 1,645²·σ²).
                val templateLimit = SIGMAS * deviation(zeroReplicas[index])
                val dataLimit = component.criticalScale
                component.copy(
                    sigmaTemplate = deviation(replicas[index]),
                    criticalScale = sqrt(dataLimit * dataLimit + templateLimit * templateLimit),
                )
            },
        )
    }

    /**
     * Оценка доли формы [index] при гипотезе H₀ на одной бутстрэп-реплике.
     *
     * Фон гипотезы подгоняется EM по всем формам, КРОМЕ [index], а сама доля
     * берётся одним шагом взвешенной проекции остатка на форму:
     * `a = Σ(N − M₀)·T/M₀ ÷ Σ T²/M₀` — это решение линеаризованного уравнения
     * правдоподобия Пуассона в точке `a = 0`, то есть ровно тот отклик, который
     * шум шаблонов наводит на «пустую» долю.
     *
     * Полной EM с этой формой тут быть не может: мультипликативная итерация
     * умножает текущую долю на поправку, и из нулевого старта не выходит вовсе, а
     * ненулевой старт означал бы уже другую гипотезу.
     *
     * Фон в знаменателе ограничен снизу [MIN_ZERO_MODEL] — без этого предел
     * определяли бы несколько почти пустых каналов верхней части шкалы.
     *
     * @param start доли фона в порядке форм без [index]; пустой — форма
     *   единственная.
     * @return оценка доли; 0, когда фона нет (единственная форма) или форма
     *   нигде не имеет положительного счёта.
     */
    private fun zeroScale(
        counts: List<Int>,
        templates: List<List<Double>>,
        index: Int,
        start: DoubleArray,
    ): Double {
        if (start.isEmpty()) return 0.0
        val rest = withoutIndex(templates, index)
        val scales = emScales(counts, rest, start, BOOTSTRAP_ITERATIONS)
        val shape = templates[index]
        var numerator = 0.0
        var denominator = 0.0
        for (channel in counts.indices) {
            val t = shape[channel]
            if (t <= 0.0) continue
            var background = 0.0
            for (other in rest.indices) background += scales[other] * rest[other][channel]
            val model = max(background, MIN_ZERO_MODEL)
            numerator += (counts[channel] - model) * t / model
            denominator += t * t / model
        }
        return if (denominator > 0.0) numerator / denominator else 0.0
    }

    /** Формы без [index], исходный порядок сохранён. */
    private fun withoutIndex(templates: List<List<Double>>, index: Int): List<List<Double>> =
        templates.filterIndexed { position, _ -> position != index }

    /**
     * Старт EM: каждая форма несёт равную долю измеренного счёта. Нулевой старт
     * у мультипликативных итераций — ловушка: ноль умножается в ноль.
     */
    private fun equalShareStart(counts: List<Int>, templates: List<List<Double>>): DoubleArray {
        val total = counts.sumOf { it.toDouble() }
        val k = templates.size
        return DoubleArray(k) { index ->
            val templateSum = templates[index].sum()
            if (templateSum > 0.0) total / (k * templateSum) else 0.0
        }
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
        val total = counts.sumOf { it.toDouble() }
        val scales = emScales(counts, templates, equalShareStart(counts, templates), ITERATIONS)

        val model = modelOf(templates, scales, n)

        val sigmas = marginalSigmas(
            informationMatrix(templates, model) { channel -> counts[channel].toDouble() },
        )

        // Модели без ЕДИНСТВЕННОЙ формы не существует: её предел остаётся нулевым.
        val critical = DoubleArray(k)
        if (k > 1) {
            for (index in 0 until k) {
                // При отсутствии этой формы её место занимают остальные: вклад
                // ДАННЫХ в предел Карри считается по модели БЕЗ неё, а ожидаемый
                // счёт при такой гипотезе равен самой этой модели. Вклад шума
                // шаблонов добавляет withTemplateSigma.
                val zeroModel = DoubleArray(n) { channel ->
                    model[channel] - scales[index] * templates[index][channel]
                }
                val zeroSigma = marginalSigmas(
                    informationMatrix(templates, zeroModel) { channel -> zeroModel[channel] },
                )[index]
                critical[index] = SIGMAS * zeroSigma
            }
        }

        val cash = cash(counts, model)
        var expected = 0.0
        var variance = 0.0
        val residual = DoubleArray(n)
        for (channel in 0 until n) {
            val m = max(model[channel], MIN_MODEL)
            val data = counts[channel].toDouble()
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
     * Информационная матрица правдоподобия Пуассона по долям, k×k:
     * `I_jl = Σ_канал w·T_j·T_l / M²`, где `w` — ожидаемый счёт канала.
     *
     * Для найденной модели весом служит измеренный счёт (наблюдённая
     * информация), для гипотезы «доли нет» — сама модель H₀. Каналы с `M ≤ 0`
     * пропускаются: там доли на правдоподобие не влияют.
     *
     * @param model модель по каналам; её длина задаёт число каналов.
     * @param weight ожидаемый счёт канала.
     */
    private fun informationMatrix(
        templates: List<List<Double>>,
        model: DoubleArray,
        weight: (Int) -> Double,
    ): Array<DoubleArray> {
        val k = templates.size
        val matrix = Array(k) { DoubleArray(k) }
        for (channel in model.indices) {
            val m = model[channel]
            if (m <= 0.0) continue
            val w = weight(channel) / (m * m)
            if (w <= 0.0) continue
            for (row in 0 until k) {
                val t = templates[row][channel]
                if (t <= 0.0) continue
                val scaled = w * t
                for (column in row until k) {
                    matrix[row][column] += scaled * templates[column][channel]
                }
            }
        }
        for (row in 0 until k) {
            for (column in 0 until row) matrix[row][column] = matrix[column][row]
        }
        return matrix
    }

    /**
     * Маргинальные σ долей: `√((I⁻¹)_jj)` по информационной матрице [matrix].
     *
     * Обращение — через разложение Холецкого: матрица симметрична и при
     * линейно независимых формах положительно определена, а k здесь единицы.
     * Столбец `j` обратной матрицы получается решением `L y = e_j`, и
     * `(I⁻¹)_jj = |y|²`, потому что `I⁻¹ = (L⁻¹)ᵀ·L⁻¹`.
     *
     * @return σ по формам; NaN на ВСЕХ позициях, когда разложение не проходит —
     *   формы линейно зависимы (в пределе — совпадают), и разделить их доли
     *   нечем. Единственная форма даёт матрицу 1×1, то есть прежнее `1/√I₀₀`.
     */
    private fun marginalSigmas(matrix: Array<DoubleArray>): DoubleArray {
        val k = matrix.size
        val lower = cholesky(matrix) ?: return DoubleArray(k) { Double.NaN }
        return DoubleArray(k) { index ->
            var norm = 0.0
            val column = DoubleArray(k)
            for (row in index until k) {
                var sum = if (row == index) 1.0 else 0.0
                for (previous in index until row) sum -= lower[row][previous] * column[previous]
                val value = sum / lower[row][row]
                column[row] = value
                norm += value * value
            }
            sqrt(norm)
        }
    }

    /**
     * Решение `matrix · x = rhs` для симметричной положительно определённой
     * матрицы: прямая и обратная подстановки по множителю Холецкого.
     *
     * @return null, когда матрица не раскладывается (формы линейно зависимы).
     */
    private fun solve(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
        val k = matrix.size
        val lower = cholesky(matrix) ?: return null
        val x = DoubleArray(k)
        for (row in 0 until k) {
            var sum = rhs[row]
            for (previous in 0 until row) sum -= lower[row][previous] * x[previous]
            x[row] = sum / lower[row][row]
        }
        for (row in k - 1 downTo 0) {
            var sum = x[row]
            for (next in row + 1 until k) sum -= lower[next][row] * x[next]
            x[row] = sum / lower[row][row]
        }
        return x
    }

    /**
     * Нижнетреугольный множитель Холецкого: `matrix = L·Lᵀ`.
     *
     * @return null, когда ведущий элемент не положителен относительно исходной
     *   диагонали ([SINGULAR_PIVOT]) — матрица вырождена или численно неотличима
     *   от вырожденной.
     */
    private fun cholesky(matrix: Array<DoubleArray>): Array<DoubleArray>? {
        val k = matrix.size
        val lower = Array(k) { DoubleArray(k) }
        for (row in 0 until k) {
            for (column in 0..row) {
                var sum = matrix[row][column]
                for (previous in 0 until column) {
                    sum -= lower[row][previous] * lower[column][previous]
                }
                if (row == column) {
                    if (!(sum > SINGULAR_PIVOT * matrix[row][row])) return null
                    lower[row][row] = sqrt(sum)
                } else {
                    lower[row][column] = sum / lower[column][column]
                }
            }
        }
        return lower
    }

    /**
     * Доли по мультипликативным итерациям EM — до СХОДИМОСТИ, а не до заданного
     * числа шагов.
     *
     * ## Почему критерий считается в импульсах, а не в долях
     *
     * Форма, которой в спектре нет, у EM гаснет геометрически: множитель падает
     * примерно на 0,16 % за итерацию и относительное изменение доли так и
     * держится на 1,6·10⁻³, сколько ни считай (проверено: 200 000 итераций,
     * доля дошла до 10⁻¹³⁹, критерий по относительному изменению не сработал ни
     * разу). Поэтому сходимость меряется сдвигом ВКЛАДА формы, отнесённым ко
     * всему измеренному счёту: `|Δa_j|·ΣT_j / ΣN`. Это прямо тот вопрос, ради
     * которого считается разложение — «изменился ли состав», — и величина у него
     * конечная.
     *
     * ## Почему ускорение
     *
     * Одного EM для этого мало: до [CONVERGENCE] на реальном фоне с лишней
     * формой ему нужно 2500–5900 итераций. Каждая [ACCELERATE_EVERY]-я итерация
     * поэтому дополняется ньютоновским рывком ([newtonJump]) — с ним те же
     * данные сходятся за 10 итераций, то есть ДЕШЕВЛЕ прежних фиксированных 200,
     * и при этом действительно в оптимуме.
     *
     * @param start стартовые доли; на бутстрэп-репликах это уже найденный
     *   оптимум, и критерий останавливает их за единицы итераций.
     * @param maxIterations потолок; при [CONVERGENCE] и ускорении на реальных
     *   данных он не достигается — это защита от незамеченного расхождения, а не
     *   рабочий режим.
     * @return доли, неотрицательные по построению.
     */
    private fun emScales(
        counts: List<Int>,
        templates: List<List<Double>>,
        start: DoubleArray,
        maxIterations: Int,
    ): DoubleArray {
        val n = counts.size
        val k = templates.size
        val scales = start.copyOf()
        val areas = DoubleArray(k) { templates[it].sum() }
        val total = counts.sumOf { it.toDouble() }.coerceAtLeast(1.0)
        val model = DoubleArray(n)
        var iteration = 0
        while (iteration < maxIterations) {
            iteration++
            for (channel in 0 until n) {
                var value = 0.0
                for (index in 0 until k) value += scales[index] * templates[index][channel]
                model[channel] = value
            }
            var moved = 0.0
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
                val next = if (denominator > 0.0 && numerator > 0.0) {
                    scales[index] * numerator / denominator
                } else {
                    0.0
                }
                val shift = abs(next - scales[index]) * areas[index] / total
                if (shift > moved) moved = shift
                scales[index] = next
            }
            if (moved < CONVERGENCE) break
            // Единственная форма приходит в оптимум первой же итерацией: у неё
            // множитель равен ΣN/ΣT независимо от старта.
            if (k > 1 && iteration % ACCELERATE_EVERY == 0) newtonJump(counts, templates, scales)
        }
        return scales
    }

    /**
     * Один ньютоновский шаг по долям, принимаемый ТОЛЬКО при улучшении статистики
     * Кэша.
     *
     * Логарифм правдоподобия Пуассона вогнут по долям при линейной модели, а его
     * отрицательный гессиан — та же информационная матрица [informationMatrix].
     * Шаг `Δa = I⁻¹·∇` поэтому берёт ровно ту связку форм, вдоль которой EM ползёт
     * медленнее всего.
     *
     * Доля не обнуляется целиком, а ограничена снизу [SHRINK_FLOOR] от текущей:
     * форма, которой в спектре нет, гаснет за шаг в миллион раз, но остаётся
     * положительной, и EM может её вернуть, если рывок ошибся. Ошибиться рывок
     * почти не может — кандидат принимается лишь тогда, когда Кэш не вырос, а при
     * отказе длина шага делится пополам ([BACKTRACKS] проб). Если матрица не
     * обращается, шага нет: EM продолжает сам.
     *
     * [scales] изменяется на месте.
     */
    private fun newtonJump(
        counts: List<Int>,
        templates: List<List<Double>>,
        scales: DoubleArray,
    ) {
        val n = counts.size
        val k = templates.size
        val model = modelOf(templates, scales, n)
        val gradient = DoubleArray(k)
        for (channel in 0 until n) {
            val m = model[channel]
            if (m <= 0.0) continue
            val ratio = counts[channel] / m - 1.0
            for (index in 0 until k) gradient[index] += templates[index][channel] * ratio
        }
        val direction = solve(
            informationMatrix(templates, model) { channel -> counts[channel].toDouble() },
            gradient,
        ) ?: return

        val current = cash(counts, model)
        var factor = 1.0
        repeat(BACKTRACKS) {
            val candidate = DoubleArray(k) {
                max(scales[it] + factor * direction[it], scales[it] * SHRINK_FLOOR)
            }
            if (cash(counts, modelOf(templates, candidate, n)) <= current) {
                candidate.copyInto(scales)
                return
            }
            factor /= 2.0
        }
    }

    /** Модель по каналам: `M = Σ aᵢ·Tᵢ`. */
    private fun modelOf(templates: List<List<Double>>, scales: DoubleArray, channels: Int) =
        DoubleArray(channels) { channel ->
            var value = 0.0
            for (index in templates.indices) value += scales[index] * templates[index][channel]
            value
        }

    /**
     * Статистика Кэша `C = 2·Σ(M − N·ln M) + 2·Σ(N·ln N − N)`: вторая сумма от
     * долей не зависит и держит C неотрицательной, что делает сравнение двух
     * подгонок одного спектра прямым.
     */
    private fun cash(counts: List<Int>, model: DoubleArray): Double {
        var cash = 0.0
        for (channel in counts.indices) {
            val m = max(model[channel], MIN_MODEL)
            val data = counts[channel].toDouble()
            cash += 2.0 * (m - data * ln(m))
            if (data > 0.0) cash += 2.0 * (data * ln(data) - data)
        }
        return cash
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

    /**
     * Доля исходного диагонального элемента, ниже которой ведущий элемент
     * Холецкого считается нулевым. Слагаемые матрицы — суммы по тысяче каналов,
     * и у зависимых форм ведущий элемент падает до уровня накопленной ошибки
     * double (относительно 1e-16), а не до точного нуля.
     */
    private const val SINGULAR_PIVOT = 1e-12

    /**
     * Пол модели гипотезы H₀ в проекции [zeroScale], импульсы.
     *
     * Вес канала в проекции равен `T²/M₀` и при `M₀ → 0` расходится: в верхней
     * части шкалы фоновая форма набирает доли импульса, и такой вес держится не
     * на измерении, а на одном-двух импульсах шаблона — от реплики к реплике он
     * менялся на порядок и тянул за собой весь предел. **Один импульс** — та же
     * граница, что у масштаба остатков (`sqrt(max(m, 1))` в [fit]): ниже одного
     * ожидаемого импульса пуассоновский канал не отличает 0 от 1, и разрешать
     * модель мельче нечем. Ценой служит консервативный вес самых пустых каналов.
     */
    private const val MIN_ZERO_MODEL = 1.0

    /**
     * Порог сходимости EM: сдвиг вклада формы, отнесённый ко всему измеренному
     * счёту (`|Δa|·ΣT / ΣN`).
     *
     * 10⁻⁷ выбран по цене ошибки, а не по красоте числа. На реальном фоне с
     * лишней формой останов при 10⁻⁵ оставляет в ней 0,3 % счёта — это ровно
     * уровень предела обнаружения, то есть недосчитанная подгонка сама по себе
     * даёт ложное «обнаружено». При 10⁻⁷ остаётся 6·10⁻⁵ и меньше — на два
     * порядка ниже и предела, и пуассоновского шума измерения (1/√N ≈ 2·10⁻³
     * при 3·10⁵ импульсов).
     */
    private const val CONVERGENCE = 1e-7

    /**
     * Потолок итераций EM основной подгонки.
     *
     * С ускорением реальные разложения сходятся за 10–20 итераций, поэтому
     * потолок — защита от незамеченного расхождения, а не рабочий режим. 2000
     * итераций стоят порядка 2000·n·k операций: даже упёршись в потолок, одна
     * подгонка остаётся дешевле приведения шаблонов.
     */
    private const val ITERATIONS = 2000

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
     * Потолок итераций EM на бутстрэп-реплику. Останавливает реплику тот же
     * критерий [CONVERGENCE], что и основную подгонку: реплика стартует с уже
     * найденных долей и отличается от них на статистику шаблона, поэтому до
     * потолка не доходит, а качество ответа задаёт критерий, а не число шагов.
     */
    private const val BOOTSTRAP_ITERATIONS = 200

    /**
     * Каждая ACCELERATE_EVERY-я итерация EM дополняется ньютоновским рывком
     * ([newtonJump]).
     *
     * Рывок стоит примерно три итерации EM (градиент, матрица и до [BACKTRACKS]
     * проб статистики Кэша), поэтому на восьмой итерации он добавляет к цене
     * порядка 40 %, а убирает тысячи итераций: 2500–5900 шагов чистого EM против
     * 10 шагов с рывком на тех же данных.
     */
    private const val ACCELERATE_EVERY = 8

    /** Сколько раз длина ньютоновского шага делится пополам, прежде чем сдаться. */
    private const val BACKTRACKS = 10

    /**
     * Нижняя граница доли в ньютоновском шаге — доля текущего значения. Форма,
     * которой в спектре нет, гаснет за шаг в миллион раз, но не обнуляется:
     * ноль у мультипликативных итераций необратим, а положительную долю EM
     * вернёт, если рывок ошибся.
     */
    private const val SHRINK_FLOOR = 1e-6

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
     * Где искать усиление шкалы, когда это уже измерено.
     *
     * Приложение само измеряет, как шкала прибора уходит от его температуры, и
     * умеет предсказать уход на СЕЙЧАС ([app.alpha.analysis.GainDrift]). Знать
     * это до подгонки полезно вдвойне: сетка становится мельче там, где ответ
     * действительно лежит, и перестаёт упираться в собственный край, когда
     * прибор ушёл дальше обычного.
     *
     * Предсказание НЕ заменяет подгонку: оно двигает окно поиска, а величину
     * по-прежнему определяют данные. Ошибка в предсказании не подмешивает
     * состав молча — она лишь смещает окно, и подгонка внутри него честно
     * выбирает лучшее.
     *
     * @param gain ожидаемое усиление (1,0 — калибровка прибора верна).
     * @param sigma 1σ этого ожидания; чем оно неувереннее, тем шире окно.
     */
    data class ScalePrior(val gain: Double, val sigma: Double)

    /**
     * Сетка усиления вокруг ожидания.
     *
     * Без предсказания — прежние ±3 % шагом 0,5 %: измеренный на приборе ход
     * около 2 %, и сетка обязана накрывать его с запасом.
     *
     * С предсказанием полуширина окна равна 3σ ожидания, но не меньше
     * [MIN_PRIOR_HALF_WIDTH]: три σ накрывают истину с вероятностью 0,997, а
     * нижняя граница не даёт окну схлопнуться, когда предсказание выглядит
     * точнее, чем бывает на самом деле. Число точек то же, поэтому шаг мельче
     * ровно во столько раз, во сколько уже окно.
     */
    private fun gainGrid(prior: ScalePrior?): List<Double> {
        if (prior == null || !prior.gain.isFinite() || !prior.sigma.isFinite()) return GAIN_GRID
        val half = max(PRIOR_SIGMAS * abs(prior.sigma), MIN_PRIOR_HALF_WIDTH)
        val steps = (GAIN_GRID.size - 1) / 2
        return (-steps..steps).map { prior.gain + half * it / steps }
    }

    /** Сколько σ предсказания накрывает окно поиска. */
    private const val PRIOR_SIGMAS = 3.0

    /**
     * Наименьшая полуширина окна вокруг предсказания — **инженерный
     * параметр**. Полпроцента: у прибора, кроме температуры, шкалу двигают
     * старение и влажность, и обещать точность лучше половины процента по
     * одной измеренной зависимости нельзя.
     */
    private const val MIN_PRIOR_HALF_WIDTH = 0.005

    /**
     * Сетка усиления по умолчанию: ±3 % шагом 0,5 %. Измеренный дрейф шкалы
     * прибора около 2 %, и сетка обязана его накрывать с запасом.
     */
    private val GAIN_GRID = listOf(0.97, 0.975, 0.98, 0.985, 0.99, 0.995, 1.0, 1.005, 1.01, 1.02, 1.03)

    /** Сетка смещения: ±10 кэВ. Больше — это уже не смещение, а другая шкала. */
    private val OFFSET_GRID = listOf(-10.0, -5.0, 0.0, 5.0, 10.0)
}
