package app.alpha.ui.logic

import app.alpha.data.db.EnvironmentEntity
import kotlin.math.abs

/**
 * Ряды условий на той же временной сетке, что и график дозы.
 *
 * Две особенности, из-за которых нужен отдельный слой, а не вызов
 * [ChartMapping.toColumns]:
 *
 *  - в одну колонку графика попадает много сводок (окно 10 с против колонки в
 *    минуты), поэтому значения усредняются, а не берутся последним;
 *  - давление живёт в узкой полосе около 1000 гПа, и шкала от нуля превратила
 *    бы его в прямую. Поэтому ряд отдаётся СМЕЩЁННЫМ на свою базу, а подписи
 *    делений остаются настоящими значениями.
 */
object EnvironmentSeries {

    /**
     * Температура здесь — ПРИБОРА, а не телефона. Телефон меряет свою батарею:
     * её задаёт нагрузка процессора и зарядка, а не воздух, и рядом с
     * температурой прибора она читалась бы как равная величина среды. Её место
     * — диагностика в настройках, где видно, не грел ли телефон прибор.
     */
    enum class Kind { PRESSURE, FIELD, DEVICE_TEMPERATURE, TEMPERATURE_DRIFT }

    /**
     * @param plot значения, смещённые на [base] — то, что рисует график.
     * @param base что прибавить к делению, чтобы получить настоящее значение.
     * @param min/[max] настоящие крайние значения ряда.
     */
    data class Series(
        val kind: Kind,
        val plot: List<Float?>,
        val base: Float,
        val span: Float,
        val min: Float,
        val max: Float,
        val last: Float,
    ) {
        /** Деления шкалы: доля высоты → настоящее значение. */
        fun ticks(count: Int = 3): List<Pair<Float, Float>> {
            if (span <= 0f) return emptyList()
            return (0 until count).map { i ->
                val value = min + (max - min) * i / (count - 1).coerceAtLeast(1)
                (value - base) to value
            }
        }
    }

    /**
     * @param deviceTemperature пары «момент — °C» из редких данных прибора; их
     *   шаг около минуты, и в колонку графика их попадает меньше, чем сводок
     *   датчиков — усреднение то же.
     * @return ряды, в которых есть хотя бы [MIN_POINTS] точек: одна точка — не
     *   ряд, и рисовать её линией значило бы показать движение, которого никто
     *   не измерял.
     */
    fun of(
        rows: List<EnvironmentEntity>,
        deviceTemperature: List<Pair<Long, Float>> = emptyList(),
        alignedFromMillis: Long,
        bucketMillis: Long,
        columnCount: Int,
    ): List<Series> {
        fun points(kind: Kind) = rows.mapNotNull { row ->
            value(kind, row)?.let { row.timestamp to it }
        }

        val device = buckets(deviceTemperature, alignedFromMillis, bucketMillis, columnCount)
        val phone = buckets(
            rows.mapNotNull { row -> row.phoneTempC?.let { row.timestamp to it } },
            alignedFromMillis,
            bucketMillis,
            columnCount,
        )
        // Дрейф считается ПО КОЛОНКАМ, а не по сырым отсчётам: у прибора шаг
        // около минуты, у телефона десять секунд, и вычитать их «ближайший к
        // ближайшему» значило бы придумывать пары, которых не было.
        val drift = (0 until columnCount).map { i ->
            val a = device[i]
            val b = phone[i]
            if (a == null || b == null) null else a - b
        }

        return Kind.entries.mapNotNull { kind ->
            val columns = when (kind) {
                Kind.DEVICE_TEMPERATURE -> device
                Kind.TEMPERATURE_DRIFT -> drift
                else -> buckets(points(kind), alignedFromMillis, bucketMillis, columnCount)
            }
            series(kind, columns)
        }
    }

    private fun buckets(
        points: List<Pair<Long, Float>>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        columnCount: Int,
    ): List<Float?> {
        val sums = DoubleArray(columnCount)
        val counts = IntArray(columnCount)
        for ((atMillis, value) in points) {
            val index = ((atMillis - alignedFromMillis) / bucketMillis).toInt()
            if (index !in 0 until columnCount) continue
            sums[index] += value.toDouble()
            counts[index]++
        }
        return (0 until columnCount).map { i ->
            if (counts[i] == 0) null else (sums[i] / counts[i]).toFloat()
        }
    }

    private fun series(kind: Kind, columns: List<Float?>): Series? {
        val present = columns.filterNotNull()
        if (present.size < MIN_POINTS) return null

        val min = present.min()
        val max = present.max()
        // Поле у графика: пустая полоса сверху и снизу, иначе линия ложится на
        // рамку и её крайние точки не видно. Для ряда без изменений берётся
        // символическая полоса, чтобы прямая шла посередине.
        val spread = max - min
        val margin = if (spread > 0f) spread * MARGIN_FRACTION else marginFor(kind, max)
        val base = min - margin
        val span = (max + margin) - base
        return Series(
            kind = kind,
            plot = columns.map { it?.minus(base) },
            base = base,
            span = span,
            min = min,
            max = max,
            last = present.last(),
        )
    }

    private fun value(kind: Kind, row: EnvironmentEntity): Float? = when (kind) {
        Kind.PRESSURE -> row.pressureHpa
        Kind.FIELD -> row.magneticUt
        // Эти два ряда строятся не из сводок датчиков телефона.
        Kind.DEVICE_TEMPERATURE, Kind.TEMPERATURE_DRIFT -> null
    }

    /**
     * Полоса для ряда, который не менялся. Числа — цена деления величины:
     * гектопаскаль, микротесла, градус. Меньше не имеет смысла показывать.
     */
    private fun marginFor(kind: Kind, value: Float): Float = when (kind) {
        Kind.PRESSURE -> 1f
        Kind.FIELD -> 1f
        Kind.DEVICE_TEMPERATURE, Kind.TEMPERATURE_DRIFT -> 1f
    }.coerceAtLeast(abs(value) * 1e-4f)

    const val MIN_POINTS = 2

    private const val MARGIN_FRACTION = 0.15f
}
