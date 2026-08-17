package app.alpha.ui.logic

/**
 * Профиль маршрута: как менялось измерение по ходу прогулки.
 *
 * Ось времени, а не пути: пройденное расстояние между двумя фиксами известно
 * приблизительно, а момент — точно. К тому же курсор связывает график с
 * картой по времени, и вторая шкала породила бы вторую правду о том же следе.
 *
 * Здесь только счёт: где на графике оказалась точка и что попало под палец.
 * Всё, что рисуется, живёт в `ui/components/RouteProfileChart`.
 */
object RouteProfile {

    /**
     * Доля от левого края для точки маршрута: 0 — первая, 1 — последняя.
     * Маршрут длиной в один миг — единственная точка, и она слева.
     */
    fun fractionOf(timeMillis: Long, from: Long, to: Long): Float {
        val span = (to - from).toFloat()
        if (span <= 0f) return 0f
        return ((timeMillis - from) / span).coerceIn(0f, 1f)
    }

    /**
     * Какая точка маршрута под пальцем: ближайшая по времени к доле [fraction].
     *
     * Ближайшая, а не «та, что левее»: палец ставят НА место графика, и
     * округление в одну сторону смещало бы курсор на полшага при каждом касании.
     */
    fun indexAt(times: List<Long>, fraction: Float): Int? {
        if (times.isEmpty()) return null
        val from = times.first()
        val to = times.last()
        if (to <= from) return 0
        val target = from + ((to - from) * fraction.coerceIn(0f, 1f)).toLong()
        var best = 0
        var bestDistance = Long.MAX_VALUE
        times.forEachIndexed { index, time ->
            val distance = kotlin.math.abs(time - target)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /** Точка с этой меткой времени — связь карты с графиком (тап по следу). */
    fun indexOfTime(times: List<Long>, timeMillis: Long): Int? {
        if (times.isEmpty()) return null
        var best = 0
        var bestDistance = Long.MAX_VALUE
        times.forEachIndexed { index, time ->
            val distance = kotlin.math.abs(time - timeMillis)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /**
     * Границы оси значений: от минимума до максимума измеренного.
     *
     * Ноль не притягивается: профиль показывает, ГДЕ уровень менялся, и
     * прижатая к нулю линия скрывала бы ровно то, ради чего на неё смотрят.
     * Совпавшие края разводятся на 5 %, иначе линия легла бы на границу поля.
     */
    fun bounds(values: List<Float>): ClosedFloatingPointRange<Float>? {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return null
        val min = finite.min()
        val max = finite.max()
        if (max > min) return min..max
        val pad = (kotlin.math.abs(min) * 0.05f).coerceAtLeast(0.01f)
        return (min - pad)..(max + pad)
    }
}
