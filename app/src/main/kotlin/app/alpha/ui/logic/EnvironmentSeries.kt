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

    enum class Kind { PRESSURE, FIELD, PHONE_TEMPERATURE }

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
     * @return ряды, в которых есть хотя бы [MIN_POINTS] точек: одна точка — не
     *   ряд, и рисовать её линией значило бы показать движение, которого никто
     *   не измерял.
     */
    fun of(
        rows: List<EnvironmentEntity>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        columnCount: Int,
    ): List<Series> = Kind.entries.mapNotNull { kind ->
        series(kind, rows, alignedFromMillis, bucketMillis, columnCount)
    }

    private fun series(
        kind: Kind,
        rows: List<EnvironmentEntity>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        columnCount: Int,
    ): Series? {
        val sums = DoubleArray(columnCount)
        val counts = IntArray(columnCount)
        for (row in rows) {
            val value = value(kind, row) ?: continue
            val index = ((row.timestamp - alignedFromMillis) / bucketMillis).toInt()
            if (index !in 0 until columnCount) continue
            sums[index] += value.toDouble()
            counts[index]++
        }
        val columns = (0 until columnCount).map { i ->
            if (counts[i] == 0) null else (sums[i] / counts[i]).toFloat()
        }
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
        Kind.PHONE_TEMPERATURE -> row.phoneTempC
    }

    /**
     * Полоса для ряда, который не менялся. Числа — цена деления величины:
     * гектопаскаль, микротесла, градус. Меньше не имеет смысла показывать.
     */
    private fun marginFor(kind: Kind, value: Float): Float = when (kind) {
        Kind.PRESSURE -> 1f
        Kind.FIELD -> 1f
        Kind.PHONE_TEMPERATURE -> 1f
    }.coerceAtLeast(abs(value) * 1e-4f)

    const val MIN_POINTS = 2

    private const val MARGIN_FRACTION = 0.15f
}
