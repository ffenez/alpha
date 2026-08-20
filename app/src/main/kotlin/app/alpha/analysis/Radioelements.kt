package app.alpha.analysis

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Калий, уран и торий по трём линиям — основа радиоэлементной съёмки
 * (МАГАТЭ, TECDOC-1363).
 *
 * ## Почему окна считаются от прибора, а не берутся из таблицы
 *
 * Стандартные окна МАГАТЭ (1370–1570, 1660–1860, 2410–2810 кэВ) заданы под
 * NaI 3″×3″. У меньшего кристалла с худшим разрешением те же границы захватят
 * лишний континуум и потеряют часть пика. Поэтому окно строится как
 * `E ± [WINDOW_HALF_FWHM]·FWHM(E)` по разрешению КОНКРЕТНОГО прибора
 * ([PeakDetection.fwhmKeV]): 101, 110 и неопознанный прибор получают каждый
 * своё окно, а не подогнанное руками.
 *
 * Коэффициент 1,4 выбран так, чтобы на NaI 3″×3″ окна совпали со
 * стандартными: при разрешении 7 % на 662 кэВ FWHM(1460) ≈ 69 кэВ, а окно
 * МАГАТЭ — ±100 кэВ, то есть ±1,45·FWHM.
 *
 * ## Континуум — SNIP, а не боковые полосы
 *
 * Классическая оценка «среднее двух полос по краям окна» здесь не годится по
 * двум измеренным причинам. Первая: шкала приборов серии кончается около
 * 2,8 МэВ, и у линии Tl-208 (2614,5 кэВ) ВЕРХНЕЙ полосы не существует.
 * Вторая: континуум падает круто, и оценка по полосам систематически завышает
 * его под пиком — на реальном 10-часовом спектре прибора это давало
 * ОТРИЦАТЕЛЬНЫЕ площади урана и тория.
 *
 * Поэтому подложка снимается [SnipContinuum] — тем же движком, что рисует её
 * на экране спектра, и с тем же разрешением прибора.
 *
 * ## Что здесь считается и чего здесь нет
 *
 * Считается ЧИСТАЯ площадь линии над локальным континуумом с её σ и пределом
 * Карри. Нет пересчёта в проценты калия и ppm: он требует эталонных
 * калибровочных площадок, которых у прибора этого класса не бывает. Для
 * выделения зон достаточно отношений — они не зависят от чувствительности
 * прибора и от того, как он держался.
 *
 * Величины называются eU и eTh («эквивалентные»): меряются дочерние продукты
 * в предположении равновесия ряда. Радон это предположение нарушает, и eU
 * дышит вместе с погодой.
 */
object Radioelements {

    /** K-40, единственная линия калия. */
    const val K40_KEV = 1460.8f

    /** Bi-214 — рабочая линия уранового ряда. */
    const val BI214_KEV = 1764.5f

    /** Tl-208 — рабочая линия ториевого ряда. */
    const val TL208_KEV = 2614.5f

    /** Полуширина окна в долях FWHM прибора; вывод числа — в KDoc класса. */
    const val WINDOW_HALF_FWHM = 1.4f

    /** Односторонний 95 % критерий Карри. */
    const val SIGMAS = 1.645f

    enum class Element { K, U, TH }

    /**
     * Линия на станции: чистая площадь, её неопределённость и предел, ниже
     * которого утверждать нечего.
     *
     * @param sigmaCounts σ измеренной площади (Пуассон + оценка континуума).
     * @param criticalCounts предел Карри L_C: σ ПРИ ОТСУТСТВИИ линии, умноженная
     *   на [SIGMAS]. Площадь ниже него не отличается от нуля.
     */
    data class Measure(
        val element: Element,
        val energyKeV: Float,
        val fromKeV: Float,
        val toKeV: Float,
        val netCounts: Float,
        val sigmaCounts: Float,
        val criticalCounts: Float,
        val seconds: Long,
    ) {
        /** Линия видна над континуумом по критерию Карри. */
        val detected: Boolean get() = netCounts > criticalCounts

        /** Скорость счёта в линии, с⁻¹. */
        val cps: Float get() = if (seconds > 0) netCounts / seconds else 0f

        val cpsSigma: Float get() = if (seconds > 0) sigmaCounts / seconds else 0f

        /**
         * Относительная неопределённость, доля; null — площадь неотличима от
         * нуля, и делить на неё нельзя.
         */
        val relativeSigma: Float?
            get() = if (netCounts > 0f) sigmaCounts / netCounts else null
    }

    /**
     * Коэффициенты стриппинга — сколько чужой линии попадает в окно.
     *
     * Снимаются на конкретном приборе по источникам (ториевая сетка, соль KCl,
     * урановое стекло); универсальных значений не бывает, они зависят от
     * кристалла и его окружения. Пока не измерены — [NONE], и приложение
     * говорит, что «уран» частично торий.
     *
     * @param thoriumIntoUranium α: доля счёта тория в окне урана.
     * @param thoriumIntoPotassium β: доля счёта тория в окне калия.
     * @param uraniumIntoPotassium γ: доля счёта урана в окне калия.
     */
    data class Stripping(
        val thoriumIntoUranium: Float,
        val thoriumIntoPotassium: Float,
        val uraniumIntoPotassium: Float,
    ) {
        val isNone: Boolean
            get() = thoriumIntoUranium == 0f &&
                thoriumIntoPotassium == 0f &&
                uraniumIntoPotassium == 0f

        companion object {
            val NONE = Stripping(0f, 0f, 0f)
        }
    }

    /**
     * Три линии одного спектра.
     *
     * @param resolution662 разрешение прибора на 662 кэВ, доля.
     * @return только те линии, окно которых целиком помещается в шкалу; у
     *   приборов серии верх шкалы около 3 МэВ, и торий у самого края.
     */
    fun measure(
        counts: List<Int>,
        calibration: EnergyCalibration,
        seconds: Long,
        resolution662: Float,
    ): List<Measure> {
        val continuum = SnipContinuum.of(counts, calibration, resolution662)
        if (continuum.size != counts.size) return emptyList()
        return listOfNotNull(
            measureLine(Element.K, K40_KEV, counts, continuum, calibration, seconds, resolution662),
            measureLine(
                Element.U, BI214_KEV, counts, continuum, calibration, seconds, resolution662,
            ),
            measureLine(
                Element.TH, TL208_KEV, counts, continuum, calibration, seconds, resolution662,
            ),
        )
    }

    /**
     * Чистая площадь линии над подложкой SNIP.
     *
     * σ² = gross + B: Пуассон измеренного счёта плюс неопределённость самой
     * подложки — она построена по тем же импульсам и точнее пуассоновской быть
     * не может. Предел Карри считается по σ ПРИ ОТСУТСТВИИ линии: в окне тогда
     * только подложка, σ₀ = √(2B).
     *
     * @param continuum подложка того же спектра, канал в канал.
     */
    fun measureLine(
        element: Element,
        energyKeV: Float,
        counts: List<Int>,
        continuum: List<Float>,
        calibration: EnergyCalibration,
        seconds: Long,
        resolution662: Float,
    ): Measure? {
        val half = WINDOW_HALF_FWHM * PeakDetection.fwhmKeV(energyKeV, resolution662)
        val fromKeV = energyKeV - half
        val toKeV = energyKeV + half
        val lo = calibration.channelAt(fromKeV).toInt()
        val hi = ceil(calibration.channelAt(toKeV)).toInt()
        if (lo <= 0 || hi >= counts.size || hi <= lo) return null

        if (continuum.size != counts.size) return null

        var gross = 0.0
        var base = 0.0
        for (ch in lo..hi) {
            gross += counts[ch]
            base += continuum[ch]
        }
        val net = gross - base
        val sigma = sqrt(gross + base)
        val sigmaZero = sqrt(2.0 * base)

        return Measure(
            element = element,
            energyKeV = energyKeV,
            fromKeV = fromKeV,
            toKeV = toKeV,
            netCounts = net.toFloat(),
            sigmaCounts = sigma.toFloat(),
            criticalCounts = (SIGMAS * sigmaZero).toFloat(),
            seconds = seconds,
        )
    }

    /**
     * Снимает протечку чужих линий по измеренным коэффициентам.
     *
     * Порядок обязателен: торий чист (его окно самое верхнее, сыпаться в него
     * нечему), из урана вычитается торий, из калия — уже очищенный уран и
     * торий. Обратный порядок вычитал бы загрязнённое из загрязнённого.
     *
     * σ растёт: вычитаемое известно с погрешностью, и она складывается
     * квадратично. Предел Карри переносится без изменения — он описывает
     * континуум, а не протечку.
     */
    fun strip(measures: List<Measure>, stripping: Stripping): List<Measure> {
        if (stripping.isNone) return measures
        val th = measures.firstOrNull { it.element == Element.TH } ?: return measures
        val u = measures.firstOrNull { it.element == Element.U }
        val k = measures.firstOrNull { it.element == Element.K }

        val strippedU = u?.let {
            val net = it.netCounts - stripping.thoriumIntoUranium * th.netCounts
            val sigma = sqrt(
                it.sigmaCounts * it.sigmaCounts +
                    stripping.thoriumIntoUranium * stripping.thoriumIntoUranium *
                    th.sigmaCounts * th.sigmaCounts,
            )
            it.copy(netCounts = net, sigmaCounts = sigma)
        }
        val strippedK = k?.let {
            val fromU = strippedU?.netCounts ?: 0f
            val sigmaU = strippedU?.sigmaCounts ?: 0f
            val net = it.netCounts -
                stripping.uraniumIntoPotassium * fromU -
                stripping.thoriumIntoPotassium * th.netCounts
            val sigma = sqrt(
                it.sigmaCounts * it.sigmaCounts +
                    stripping.uraniumIntoPotassium * stripping.uraniumIntoPotassium *
                    sigmaU * sigmaU +
                    stripping.thoriumIntoPotassium * stripping.thoriumIntoPotassium *
                    th.sigmaCounts * th.sigmaCounts,
            )
            it.copy(netCounts = net, sigmaCounts = sigma)
        }
        return measures.map { measure ->
            when (measure.element) {
                Element.U -> strippedU ?: measure
                Element.K -> strippedK ?: measure
                Element.TH -> measure
            }
        }
    }

    /** Отношение двух линий с переносом ошибки. */
    data class Ratio(val value: Float, val sigma: Float) {
        /** Относительная неопределённость отношения, доля. */
        val relativeSigma: Float get() = if (value > 0f) sigma / value else Float.MAX_VALUE
    }

    /**
     * Отношение площадей.
     *
     * @return null, если знаменатель неотличим от нуля: отношение к «ничему»
     *   не число, а деление на шум. Именно отношения выделяют вещество —
     *   они не зависят ни от чувствительности прибора, ни от того, как близко
     *   его держали к земле.
     */
    fun ratio(numerator: Measure?, denominator: Measure?): Ratio? {
        if (numerator == null || denominator == null) return null
        // ОБЕ линии обязаны быть набраны: отношение, у которого числитель ниже
        // предела Карри, — это отношение шума к числу, а выглядит оно как
        // измеренная величина.
        if (!numerator.detected || !denominator.detected) return null
        if (numerator.netCounts <= 0f || denominator.netCounts <= 0f) return null
        val value = numerator.netCounts / denominator.netCounts
        val relative = sqrt(
            (numerator.sigmaCounts / numerator.netCounts).let { it * it } +
                (denominator.sigmaCounts / denominator.netCounts).let { it * it },
        )
        return Ratio(value, value * relative)
    }
}
