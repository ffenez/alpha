package app.alpha.ui.logic

/**
 * Фиксированный диапазон времени, на котором открывается полноэкранный график
 * из Истории: начало и конец сессии.
 *
 * Диапазон существует ровно для того, чтобы у графика был ДРУГОЙ край. Живой
 * график преследует «сейчас», исторический — стоит на прошлом: чип «⌖ сейчас»
 * там бессмыслен, потому что «сейчас» к этим измерениям не относится.
 */
data class ChartRange(val fromMillis: Long, val toMillis: Long) {
    val spanMillis: Long get() = (toMillis - fromMillis).coerceAtLeast(0L)
}

object ChartRanges {

    /**
     * Диапазон сессии. У идущей сессии конца ещё нет — правым краем становится
     * [nowMillis]: это честнее, чем нарисовать конец там, где запись не
     * закончилась.
     */
    fun of(startedAtMillis: Long, endedAtMillis: Long?, nowMillis: Long): ChartRange =
        ChartRange(startedAtMillis, (endedAtMillis ?: nowMillis).coerceAtLeast(startedAtMillis))

    /** Живой ли это график: диапазона нет — окно едет за «сейчас». */
    fun followsLiveEdge(range: ChartRange?): Boolean = range == null

    /**
     * Правый предел жестов.
     *
     * У живого графика это «сейчас» — дальше данных нет. У исторического это
     * конец диапазона: экран открыт из сессии и показывает её, а не то, что
     * прибор писал после неё.
     */
    fun edgeMillis(range: ChartRange?, nowMillis: Long): Long = range?.toMillis ?: nowMillis

    /**
     * Окно, на котором график открывается по диапазону.
     *
     * Три правила, и все три видны в тестах:
     * 1. обычно окно РАВНО диапазону — человек попросил показать сессию;
     * 2. величина, которая не умеет длинных окон (счёт и жёсткость читаются
     *    точным путём, [ChartMetrics.maxSpanMillis]), показывает последний
     *    кусок сессии допустимой длины, а не молча врёт масштабом;
     * 3. сессия короче минимального окна графика ([ChartWindows.MIN_SPAN_MILLIS])
     *    ложится в него ПО ЦЕНТРУ: прижатая к правому краю короткая запись
     *    читалась бы как «запись оборвалась», хотя она просто короткая.
     */
    fun initialWindow(range: ChartRange, maxSpanMillis: Long): ChartWindow {
        val allowed = maxSpanMillis.coerceIn(
            ChartWindows.MIN_SPAN_MILLIS,
            ChartWindows.MAX_SPAN_MILLIS,
        )
        val span = range.spanMillis.coerceAtMost(allowed)
        if (span >= ChartWindows.MIN_SPAN_MILLIS) {
            return ChartWindow(range.toMillis - span, range.toMillis)
        }
        val pad = (ChartWindows.MIN_SPAN_MILLIS - span) / 2
        val to = range.toMillis + pad
        return ChartWindow(to - ChartWindows.MIN_SPAN_MILLIS, to)
    }

    /**
     * Стоит ли окно ровно на диапазоне — состояние чипа «⌖ сессия».
     *
     * Подсвеченный чип означает «названное состояние включено» (правило панели
     * графика), поэтому сравнение идёт с допуском в доле окна: щипок оставляет
     * микроскопические расхождения, а человек видит тот же кадр.
     */
    fun atFullRange(
        window: ChartWindow,
        range: ChartRange,
        maxSpanMillis: Long,
        tolerance: Double = EDGE_TOLERANCE,
    ): Boolean {
        val full = initialWindow(range, maxSpanMillis)
        val slack = (full.spanMillis * tolerance).toLong().coerceAtLeast(1L)
        return kotlin.math.abs(window.fromMillis - full.fromMillis) <= slack &&
            kotlin.math.abs(window.toMillis - full.toMillis) <= slack
    }

    /** Допуск совпадения окна с диапазоном. **Инженерный параметр.** */
    const val EDGE_TOLERANCE = 0.02
}
