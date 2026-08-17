package app.alpha.ui.logic

/**
 * Где два маршрута разошлись.
 *
 * Сравнивать маршруты можно только там, где они прошли по одному месту,
 * поэтому сопоставление идёт по КЛЕТКАМ: обе прогулки раскладываются на
 * квадраты в несколько десятков метров, и разговор идёт лишь о клетках, где
 * набралось достаточно измерений у обоих. Клетка, где прошёл только один, не
 * различие, а разная геометрия.
 *
 * Что здесь НЕ делается — и это главное. Никакой критерий значимости тут не
 * применяется: показания вдоль маршрута идут подряд во времени и соседние
 * зависимы, поэтому обычные пробы на разность средних дали бы уверенность,
 * которой в данных нет. Вместо вывода — описание: сравниваются медианы, а
 * различие называется видимым только когда разбросы (P10–P90) двух маршрутов
 * в этой клетке НЕ перекрываются. Перекрылись — сказано, что различия не
 * видно, и это не то же самое, что «одинаково».
 */
object RouteDiff {

    /**
     * Сторона клетки сопоставления, м.
     *
     * **Инженерный параметр.** Обычная городская точность фикса — единицы и
     * первые десятки метров, поэтому клетка мельче нескольких десятков метров
     * сравнивала бы не места, а ошибку приёмника; клетка крупнее — усредняла
     * бы разные места в одно.
     */
    const val CELL_METERS = 30.0

    /** Меньше этого числа фиксов в клетке — медиана по горстке, не сравниваем. */
    const val MIN_POINTS_PER_CELL = MIN_CONFIDENT_POINTS

    /** Клетка, где прошли оба маршрута. */
    data class Cell(
        val latKey: Int,
        val lonKey: Int,
        val southLatitude: Double,
        val northLatitude: Double,
        val westLongitude: Double,
        val eastLongitude: Double,
        val countA: Int,
        val countB: Int,
        val medianA: Float,
        val medianB: Float,
        val p10A: Float,
        val p90A: Float,
        val p10B: Float,
        val p90B: Float,
    ) {
        /** Отношение первого ко второму: у него всегда назван знаменатель. */
        val ratio: Float get() = if (medianB > 0f) medianA / medianB else Float.NaN

        /** Разбросы перекрылись — различия по этой клетке не видно. */
        val overlapping: Boolean get() = p10A <= p90B && p10B <= p90A

        val higher: Boolean get() = !overlapping && medianA > medianB
        val lower: Boolean get() = !overlapping && medianA < medianB
    }

    data class Result(
        val cells: List<Cell>,
        /** Клеток, где прошли оба и хватило измерений. */
        val matched: Int,
    ) {
        val higher: List<Cell> get() = cells.filter { it.higher }
        val lower: List<Cell> get() = cells.filter { it.lower }
        val differing: List<Cell> get() = cells.filter { !it.overlapping }
    }

    fun compare(
        first: List<MapTrackPoint>,
        second: List<MapTrackPoint>,
        metric: TrackMetric,
        cellMeters: Double = CELL_METERS,
        minPoints: Int = MIN_POINTS_PER_CELL,
    ): Result {
        if (first.isEmpty() || second.isEmpty()) return Result(emptyList(), matched = 0)
        val latStep = cellMeters / METERS_PER_DEGREE_LATITUDE
        val midLatitude = (first.first().latitude + second.first().latitude) / 2
        val lonStep = latStep / Math.cos(Math.toRadians(midLatitude)).coerceAtLeast(0.01)

        val binsA = bin(first, metric, latStep, lonStep)
        val binsB = bin(second, metric, latStep, lonStep)

        val cells = binsA.keys.intersect(binsB.keys).mapNotNull { key ->
            val a = binsA.getValue(key)
            val b = binsB.getValue(key)
            if (a.size < minPoints || b.size < minPoints) return@mapNotNull null
            val sortedA = a.sorted()
            val sortedB = b.sorted()
            Cell(
                latKey = key.first,
                lonKey = key.second,
                southLatitude = key.first * latStep - 90.0,
                northLatitude = (key.first + 1) * latStep - 90.0,
                westLongitude = key.second * lonStep - 180.0,
                eastLongitude = (key.second + 1) * lonStep - 180.0,
                countA = a.size,
                countB = b.size,
                medianA = percentile(sortedA, 0.5),
                medianB = percentile(sortedB, 0.5),
                p10A = percentile(sortedA, 0.1),
                p90A = percentile(sortedA, 0.9),
                p10B = percentile(sortedB, 0.1),
                p90B = percentile(sortedB, 0.9),
            )
        }.sortedWith(compareBy({ it.latKey }, { it.lonKey }))

        return Result(cells = cells, matched = cells.size)
    }

    private fun bin(
        points: List<MapTrackPoint>,
        metric: TrackMetric,
        latStep: Double,
        lonStep: Double,
    ): Map<Pair<Int, Int>, List<Float>> {
        val bins = HashMap<Pair<Int, Int>, MutableList<Float>>()
        for (point in points) {
            val value = TrackMap.metricValue(point, metric) ?: continue
            if (!value.isFinite()) continue
            val key = TrackGrid.latKey(point.latitude, latStep) to
                TrackGrid.lonKey(point.longitude, lonStep)
            bins.getOrPut(key) { mutableListOf() }.add(value)
        }
        return bins
    }

    /** Ближайший ранг — та же мера порядка, что и во всей накопленной карте. */
    private fun percentile(sorted: List<Float>, p: Double): Float {
        if (sorted.isEmpty()) return Float.NaN
        val rank = Math.ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
}
