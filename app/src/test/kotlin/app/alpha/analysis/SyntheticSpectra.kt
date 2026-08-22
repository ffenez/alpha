package app.alpha.analysis

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Спектры для тестов, ПОСТРОЕННЫЕ по параметрам, а не снятые прибором.
 *
 * ## Почему построенные
 *
 * Измерения человека — его данные, и в репозитории им не место. Значит, всё,
 * что раньше проверялось на записанных спектрах, должно проверяться на
 * спектрах, у которых известна ИСТИНА: где стоит линия, какой она ширины,
 * сколько в ней импульсов и какой под ней континуум.
 *
 * ## Чем это лучше и чем хуже
 *
 * Лучше: истина известна, и тест сравнивает ответ движка с ней, а не с
 * числом, снятым однажды на одном приборе.
 *
 * Хуже: построенный спектр содержит ровно те особенности, которые в него
 * заложены. Поэтому особенности, из-за которых тесты когда-то появились,
 * заданы здесь ЯВНО и параметрами: несимметричный хвост линии
 * ([Line.tailFraction]), наклонный континуум ([continuumSlope]), высокая
 * статистика ([scale]). Если движок сломается на особенности, которой здесь
 * нет, тест этого не заметит — и это честная граница, а не скрытый риск.
 *
 * ## Известные границы стенда
 *
 * Хвост линии добавляется односторонней экспонентой `if (d < 0)`, поэтому в
 * САМОМ центре линии профиль имеет ступеньку: слева от центра плотность выше
 * на величину хвоста. Настоящая линия так себя не ведёт (там свёртка, а не
 * склейка), и следствий два. Первое: подгонка формы ExpGaussExp на таком
 * профиле уводит центр ВЛЕВО сильнее центра тяжести, поэтому утверждение
 * «подгонка формы приближает подпись к табличной энергии» на этом стенде не
 * проверяется. Второе: при доле хвоста выше ≈0,285 половина высоты
 * оказывается внутри хвоста, измеренная ширина схлопывается и гейт формы
 * отбраковывает линию — поэтому во всех тестах доля не больше 0,18.
 *
 * ## Шум
 *
 * Пуассоновский, с заданным зерном: тест обязан быть повторяемым. Большие
 * средние берутся гауссовым приближением — при N > 30 различие ниже
 * собственной погрешности проверок.
 */
object SyntheticSpectra {

    /** Шкала прибора серии: E = a0 + a1·ch + a2·ch², 1024 канала до ≈2,8 МэВ. */
    val CALIBRATION = EnergyCalibration(6.88f, 2.3377f, 3.871e-4f)

    const val CHANNELS = 1024

    /**
     * Линия спектра.
     *
     * @param energyKeV положение центра.
     * @param counts площадь линии, импульсы.
     * @param tailFraction доля площади в низкоэнергетическом хвосте: у
     *   сцинтиллятора линия несимметрична, и центр тяжести площади смещён
     *   вниз относительно самого высокого канала — ровно то расхождение,
     *   ради которого пик несёт и центр, и максимум.
     */
    data class Line(
        val energyKeV: Double,
        val counts: Double,
        val tailFraction: Double = 0.18,
    )

    /**
     * Собрать спектр.
     *
     * @param lines линии; ширина каждой берётся из [resolution662] по
     *   паспортной форме FWHM = R·√(662·E).
     * @param continuum счёт континуума в канале у нижнего края шкалы.
     * @param continuumSlope во сколько раз континуум спадает к верхнему краю:
     *   у настоящего фона он наклонный, и подгонка подложки обязана это
     *   выдерживать.
     * @param scale общий множитель: им задаётся статистика — от получаса до
     *   многих часов накопления.
     * @param seed зерно шума; один и тот же spектр при одном зерне.
     */
    fun build(
        lines: List<Line>,
        continuum: Double = 300.0,
        continuumSlope: Double = 40.0,
        resolution662: Float = 0.084f,
        scale: Double = 1.0,
        seed: Int = 20260101,
        calibration: EnergyCalibration = CALIBRATION,
        channels: Int = CHANNELS,
        noise: Boolean = true,
    ): List<Int> {
        val values = DoubleArray(channels)
        val topEnergy = calibration.energyAt((channels - 1).toFloat()).toDouble()
        for (channel in 0 until channels) {
            val energy = calibration.energyAt(channel.toFloat()).toDouble()
            // Континуум спадает экспоненциально: у природного фона и у
            // источника подложка убывает с энергией, и линейная модель на
            // трёх декадах шкалы врала бы у краёв.
            val decay = ln(continuumSlope) / topEnergy.coerceAtLeast(1.0)
            values[channel] += continuum * exp(-decay * energy)
        }
        for (line in lines) {
            val fwhm = PeakDetection.fwhmKeV(line.energyKeV.toFloat(), resolution662).toDouble()
            val sigma = fwhm / FWHM_TO_SIGMA
            val peakArea = line.counts * (1.0 - line.tailFraction)
            val tailArea = line.counts * line.tailFraction
            for (channel in 0 until channels) {
                val energy = calibration.energyAt(channel.toFloat()).toDouble()
                val width = channelWidth(calibration, channel)
                val d = energy - line.energyKeV
                // Гауссово ядро линии.
                values[channel] += peakArea * width *
                    exp(-0.5 * (d / sigma).pow(2)) / (sigma * sqrt(2.0 * Math.PI))
                // Хвост: односторонняя экспонента влево с масштабом в одну σ.
                if (d < 0.0) {
                    values[channel] += tailArea * width * exp(d / sigma) / sigma
                }
            }
        }
        val random = Random(seed)
        return List(channels) { channel ->
            val mean = values[channel] * scale
            if (!noise) mean.toInt() else poisson(mean, random)
        }
    }

    /**
     * Природный фон: калий, ториевый и урановый ряды на спадающем континууме.
     * Доли линий заданы так, чтобы на часах накопления они были измеримы, а на
     * минутах — нет.
     */
    fun naturalBackground(
        scale: Double = 1.0,
        seed: Int = 20260102,
        resolution662: Float = 0.084f,
    ): List<Int> = build(
        lines = listOf(
            Line(energyKeV = 238.6, counts = 9_000.0),
            Line(energyKeV = 351.9, counts = 6_000.0),
            Line(energyKeV = 583.2, counts = 4_000.0),
            Line(energyKeV = 609.3, counts = 5_000.0),
            Line(energyKeV = 911.2, counts = 2_500.0),
            Line(energyKeV = 1460.8, counts = 6_000.0),
            Line(energyKeV = 1764.5, counts = 1_200.0),
            Line(energyKeV = 2614.5, counts = 900.0),
        ),
        continuum = 900.0,
        scale = scale,
        seed = seed,
        resolution662 = resolution662,
    )

    /**
     * Ториевый источник: те же линии ряда Th-232, но на порядок сильнее фона,
     * с крутым комптоновским континуумом.
     */
    fun thoriumSource(
        scale: Double = 1.0,
        seed: Int = 20260103,
        resolution662: Float = 0.084f,
    ): List<Int> = build(
        lines = listOf(
            Line(energyKeV = 238.6, counts = 120_000.0),
            Line(energyKeV = 338.3, counts = 30_000.0),
            Line(energyKeV = 583.2, counts = 90_000.0),
            Line(energyKeV = 911.2, counts = 60_000.0),
            Line(energyKeV = 968.9, counts = 35_000.0),
            Line(energyKeV = 1588.2, counts = 8_000.0),
            Line(energyKeV = 2614.5, counts = 40_000.0),
        ),
        continuum = 12_000.0,
        continuumSlope = 120.0,
        scale = scale,
        seed = seed,
        resolution662 = resolution662,
    )

    /** Ширина канала по энергии, кэВ: E(ch+½) − E(ch−½). */
    private fun channelWidth(calibration: EnergyCalibration, channel: Int): Double {
        val hi = calibration.energyAt(channel + 0.5f).toDouble()
        val lo = calibration.energyAt(channel - 0.5f).toDouble()
        return (hi - lo).coerceAtLeast(1e-6)
    }

    /** Пуассоновский отсчёт; выше 30 — гауссово приближение. */
    fun poisson(mean: Double, random: Random): Int {
        if (mean <= 0.0) return 0
        if (mean > 30.0) {
            val value = mean + sqrt(mean) * gaussian(random)
            return value.toInt().coerceAtLeast(0)
        }
        var product = 1.0
        var count = 0
        val limit = exp(-mean)
        while (true) {
            product *= random.nextDouble()
            if (product <= limit) return count
            count++
            if (count > 1_000) return count
        }
    }

    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    private const val FWHM_TO_SIGMA = 2.354820045
}
