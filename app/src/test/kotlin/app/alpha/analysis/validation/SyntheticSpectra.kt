package app.alpha.analysis.validation

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.PeakDetection
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Детерминированные синтетические спектры — стенд для поиска пиков и движка
 * доказательств, по образцу [SyntheticSeries] для временных рядов.
 *
 * ## Зачем
 *
 * До сих пор `PeakDetection` и движок доказательств проверялись на спектрах,
 * собранных руками под конкретный случай: «вот горб — найди его». Такой тест
 * отвечает на вопрос «работает ли код», но не на два главных: **сколько пиков
 * машина находит там, где их нет**, и **с какой площади она начинает
 * находить настоящие**. Оба — числа, и получить их можно только на данных, у
 * которых известна истина.
 *
 * ## Чего этот стенд НЕ доказывает
 *
 * Пики здесь рисуются ТОЙ ЖЕ моделью разрешения ([PeakDetection.fwhmKeV]),
 * которой анализ их потом ищет. Значит стенд меряет согласованность анализа с
 * НАШЕЙ моделью прибора, а не с реальностью: систематическая ошибка модели
 * (несимметричный пик, хвост, нелинейность шкалы) здесь невидима по
 * построению. Заменой измерениям на приборе он не является и заявлять по нему
 * «алгоритм работает на реальных спектрах» нельзя.
 *
 * ## Модель
 *
 * 1. **Фотопик** — гауссиана по ЭНЕРГИИ с полушириной нашей модели;
 *    интегрируется по ширине канала, поэтому нелинейность калибровки
 *    учитывается сама собой.
 * 2. **Комптоновский континуум** — равномерная полка от нуля до комптоновского
 *    края `E·2α/(1+2α)`, α = E/511 кэВ, площадью `comptonToPeak` от площади
 *    пика. Форма Клейна–Нишины СОЗНАТЕЛЬНО не воспроизводится: стенду нужен
 *    правдоподобный подпор под пиком, а не спектроскопическая точность, и
 *    рисовать точную физику полки, не имея её проверки, было бы видимостью
 *    строгости.
 * 3. **Континуум фона** — экспонента по энергии: грубое, но общепринятое
 *    приближение формы природного фона сцинтиллятора.
 * 4. **Шум** — пуассоновский поканально: именно он превращает «картинку» в
 *    измерение и создаёт ложные пики, ради счёта которых стенд и существует.
 *
 * Случайность — фиксированный LCG с заданным зерном ([SyntheticSeries.Lcg]):
 * один и тот же seed даёт один и тот же спектр на любой JVM, поэтому прогон
 * стенда — повторяемое измерение, а не лотерея (требование §22).
 */
object SyntheticSpectra {

    /** Калибровка живого RC-110 из полевого отчёта — не выдуманные числа. */
    val RC110_CALIBRATION = EnergyCalibration(a0 = 6.88f, a1 = 2.338f, a2 = 3.87e-4f)

    /**
     * @param energyKeV энергия линии.
     * @param netCounts площадь ФОТОПИКА (нетто), импульсов.
     */
    data class Line(val energyKeV: Double, val netCounts: Double)

    /**
     * Природный фон помещения: экспонента с амплитудой [amplitude] импульсов в
     * нулевом канале и постоянной спада [decayPerKeV].
     *
     * **Инженерные значения по умолчанию** подобраны так, чтобы суммарный счёт
     * 1024-канального спектра за час при ≈25 имп/с был порядка 10⁵ — тот же
     * порядок, что у реальной часовой записи прибора.
     */
    data class Background(
        val amplitude: Double = 900.0,
        val decayPerKeV: Double = 0.0055,
    )

    /**
     * @param seed зерно шума; null — шум не накладывается (чистая модель, для
     *   проверки самой геометрии пика).
     * @return counts по каналам, как их отдаёт прибор.
     */
    fun build(
        lines: List<Line> = emptyList(),
        background: Background? = Background(),
        calibration: EnergyCalibration = RC110_CALIBRATION,
        channels: Int = 1024,
        resolution662: Float = PeakDetection.RESOLUTION_662,
        comptonToPeak: Double = 0.5,
        seed: Long? = 1L,
    ): List<Int> {
        val expected = DoubleArray(channels)
        val edges = DoubleArray(channels + 1) { i ->
            calibration.energyAt(i - 0.5f).toDouble()
        }

        if (background != null) {
            for (i in 0 until channels) {
                val e = (edges[i] + edges[i + 1]) / 2.0
                if (e <= 0) continue
                expected[i] += background.amplitude * exp(-background.decayPerKeV * e)
            }
        }

        for (line in lines) {
            addPhotopeak(expected, edges, line, resolution662)
            if (comptonToPeak > 0) addCompton(expected, edges, line, comptonToPeak)
        }

        if (seed == null) return expected.map { it.toInt() }
        val rng = SyntheticSeries.Lcg(seed)
        return expected.map { poisson(rng, it) }
    }

    /**
     * Гауссиана по энергии, проинтегрированная по ширине канала.
     *
     * Считается через функцию ошибок: суммировать значение плотности в центре
     * канала нельзя — при полуширине в пару каналов это заметно искажает и
     * площадь, и форму, а стенд меряет именно площадь.
     */
    private fun addPhotopeak(
        expected: DoubleArray,
        edges: DoubleArray,
        line: Line,
        resolution662: Float,
    ) {
        val fwhm = PeakDetection.fwhmKeV(line.energyKeV.toFloat(), resolution662).toDouble()
        val sigma = fwhm / (2.0 * sqrt(2.0 * ln(2.0)))
        if (sigma <= 0) return
        for (i in expected.indices) {
            val lowZ = (edges[i] - line.energyKeV) / (sigma * sqrt(2.0))
            val highZ = (edges[i + 1] - line.energyKeV) / (sigma * sqrt(2.0))
            val share = 0.5 * (erf(highZ) - erf(lowZ))
            if (share > 0) expected[i] += line.netCounts * share
        }
    }

    private fun addCompton(
        expected: DoubleArray,
        edges: DoubleArray,
        line: Line,
        comptonToPeak: Double,
    ) {
        val alpha = line.energyKeV / 511.0
        val edge = line.energyKeV * (2 * alpha) / (1 + 2 * alpha)
        if (edge <= 0) return
        val area = line.netCounts * comptonToPeak
        for (i in expected.indices) {
            val low = edges[i]
            val high = edges[i + 1]
            if (high <= 0 || low >= edge) continue
            val covered = (minOf(high, edge) - maxOf(low, 0.0)).coerceAtLeast(0.0)
            expected[i] += area * covered / edge
        }
    }

    /**
     * Пуассоновский отсчёт канала.
     *
     * До λ = 30 — точный метод Кнута; выше он требует λ умножений на отсчёт, а
     * в нижних каналах фона λ доходит до сотен. Там берётся нормальное
     * приближение с поправкой на непрерывность: при λ ≥ 30 расхождение
     * распределений уже ниже собственного разброса самого шума, а стенд меряет
     * доли ложных срабатываний, а не хвосты распределения одного канала.
     */
    private fun poisson(rng: SyntheticSeries.Lcg, lambda: Double): Int {
        if (lambda <= 0) return 0
        if (lambda < 30.0) return rng.poisson(lambda)
        val u1 = rng.nextDouble()
        val u2 = rng.nextDouble()
        val gauss = sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
        return (lambda + gauss * sqrt(lambda) + 0.5).toInt().coerceAtLeast(0)
    }

    /** Abramowitz–Stegun 7.1.26: |ошибка| < 1,5·10⁻⁷ — достаточно для площадей. */
    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val z = kotlin.math.abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * z)
        val y = 1.0 - (
            ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t +
                0.254829592
            ) * t * exp(-z * z)
        return sign * y
    }
}
