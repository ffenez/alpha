package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Уход шкалы прибора от ЕГО СОБСТВЕННОЙ температуры.
 *
 * ## Что здесь измеряется
 *
 * Световыход CsI(Tl) и коэффициент усиления фотоприёмника зависят от
 * температуры, поэтому один и тот же гамма-квант в жару и в холод даёт разный
 * номер канала. Наблюдаемая величина — ОТНОСИТЕЛЬНОЕ положение линии:
 * `наблюдённая энергия / табличная`. Единица означает «шкала на этой энергии
 * верна», 0,98 — «линия стоит на 2 % ниже таблицы».
 *
 * ## Почему это положение линии, а не «усиление шкалы»
 *
 * По ОДНОЙ линии усиление и смещение нуля неразделимы: любую пару
 * (наклон, сдвиг), дающую в этой точке то же значение, данные объяснят
 * одинаково (та же причина, по которой [ScaleCorrection] требует минимум двух
 * опорных линий). Поэтому здесь честно говорится о сдвиге НА ЭНЕРГИИ этой
 * линии, а «усиление всей шкалы» не называется.
 *
 * ## Зависимость линейная
 *
 * В бытовом диапазоне температур (единицы — десятки градусов) и световыход, и
 * усиление меняются плавно, и первый член разложения описывает их с запасом.
 * Квадратичный член потребовал бы больше точек, чем даёт природный фон за
 * месяц, и подгонялся бы под разброс.
 */
data class GainDrift(
    /** Относительное положение линии при [referenceC]. */
    val atReference: Double,
    /** 1σ этого положения. */
    val atReferenceSigma: Double,
    /** Изменение относительного положения на градус. */
    val perDegree: Double,
    /** 1σ наклона. */
    val perDegreeSigma: Double,
    /** Температура, к которой отнесено [atReference], °C. */
    val referenceC: Double,
    /** Сколько накоплений вошло в подгонку. */
    val points: Int,
    val minC: Double,
    val maxC: Double,
    /** Энергия линии, по которой всё измерено, кэВ. */
    val lineKeV: Double,
) {
    /** Относительное положение линии при заданной температуре. */
    fun at(temperatureC: Double): Double =
        atReference + perDegree * (temperatureC - referenceC)

    /**
     * 1σ предсказания при заданной температуре. Опора взята в средней
     * температуре наблюдений, поэтому свободный член и наклон не
     * коррелируют, и их вклады складываются в квадратуре.
     */
    fun sigmaAt(temperatureC: Double): Double {
        val shift = (temperatureC - referenceC) * perDegreeSigma
        return sqrt(atReferenceSigma * atReferenceSigma + shift * shift)
    }

    /**
     * Отличается ли найденный наклон от нуля по критерию 1,645σ (тот же
     * односторонний критерий, что у пределов линий). Это НЕ вердикт «дрейф
     * есть»: критерий проверял отличие и не нашёл его — так и говорится.
     */
    val slopeResolved: Boolean get() = abs(perDegree) > SIGMAS * perDegreeSigma

    companion object {
        const val SIGMAS = 1.645
    }
}

object GainDriftFit {

    /**
     * Одно наблюдение: накопление, в котором измерена линия, и температура
     * прибора за то же время.
     *
     * @param relative наблюдённая энергия линии, делённая на табличную.
     * @param sigma 1σ этого отношения (из неопределённости центроида).
     */
    data class Point(
        val temperatureC: Double,
        val relative: Double,
        val sigma: Double,
    )

    /**
     * Взвешенная прямая `относительное положение = a + k·(T − T̄)`.
     *
     * Отсчёт от средней температуры, а не от нуля: так наклон и свободный член
     * не коррелируют, и σ наклона перестаёт зависеть от того, где выбран ноль.
     *
     * @return null, если точек мало или они стоят в узком диапазоне температур
     *   — по ним наклон не определён, и «ноль ± много» лучше не показывать
     *   вовсе, чем показывать как измерение.
     */
    fun fit(points: List<Point>, lineKeV: Double): GainDrift? {
        val usable = points.filter {
            it.sigma > 0.0 && it.sigma.isFinite() && it.relative > 0.0 && it.temperatureC.isFinite()
        }
        if (usable.size < MIN_POINTS) return null
        val minC = usable.minOf { it.temperatureC }
        val maxC = usable.maxOf { it.temperatureC }
        if (maxC - minC < MIN_SPAN_C) return null

        var sw = 0.0
        var swt = 0.0
        for (point in usable) {
            val w = 1.0 / (point.sigma * point.sigma)
            sw += w
            swt += w * point.temperatureC
        }
        if (sw <= 0.0) return null
        val mean = swt / sw

        var sxx = 0.0
        var sxy = 0.0
        var sy = 0.0
        for (point in usable) {
            val w = 1.0 / (point.sigma * point.sigma)
            val x = point.temperatureC - mean
            sxx += w * x * x
            sxy += w * x * point.relative
            sy += w * point.relative
        }
        if (sxx <= 0.0) return null
        val slope = sxy / sxx
        val intercept = sy / sw

        // Разброс точек вокруг прямой сравнивается с их же заявленными σ.
        // Если он больше, неопределённость наклона расширяется: значит,
        // положение линии меняет не только температура (влажность, старение,
        // смена условий измерения), и делать вид, что σ centroid'а описывает
        // всё, нельзя.
        var chi2 = 0.0
        for (point in usable) {
            val w = 1.0 / (point.sigma * point.sigma)
            val residual = point.relative - (intercept + slope * (point.temperatureC - mean))
            chi2 += w * residual * residual
        }
        val degrees = (usable.size - 2).coerceAtLeast(1)
        val scatter = max(1.0, sqrt(chi2 / degrees))
        val sigmaSlope = scatter / sqrt(sxx)
        val sigmaIntercept = scatter / sqrt(sw)

        return GainDrift(
            atReference = intercept,
            atReferenceSigma = sigmaIntercept,
            perDegree = slope,
            perDegreeSigma = sigmaSlope,
            referenceC = mean,
            points = usable.size,
            minC = minC,
            maxC = maxC,
            lineKeV = lineKeV,
        )
    }

    /**
     * Минимум накоплений — **инженерный параметр**. Две точки задают прямую без
     * остатка, три не оставляют возможности заметить разброс; с четырёх
     * появляются две степени свободы, по которым виден масштаб рассеяния.
     */
    const val MIN_POINTS = 4

    /**
     * Минимальный размах температур — **инженерный параметр**, °C. Ожидаемый
     * ход у сцинтилляционного тракта — доли процента на градус, то есть на
     * пяти градусах это единицы промилле шкалы: у линии 1461 кэВ около 3 кэВ,
     * что уже сравнимо с неопределённостью центроида на часовом накоплении.
     * На меньшем размахе наклон определялся бы шумом.
     */
    const val MIN_SPAN_C = 5.0
}
