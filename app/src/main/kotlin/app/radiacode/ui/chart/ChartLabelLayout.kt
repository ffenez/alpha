package app.radiacode.ui.chart

/**
 * Кто уступает место, когда две подписи на графике попадают в одну строку.
 *
 * ## Зачем
 *
 * Подписи оси значений, названный порог и значение под курсором живут в одной
 * колонке пикселей и рисовались независимо: когда порог L1 оказывался рядом с
 * линией сетки, «L1 0,30» и «0,29» ложились друг на друга — и обе переставали
 * читаться. Текст поверх текста хуже отсутствующего текста: пропавшую подпись
 * видно, а слипшуюся человек пытается прочесть.
 *
 * ## Правило
 *
 * У каждой подписи есть приоритет ([LabelPriority]). При столкновении остаётся
 * старшая, младшая скрывается целиком — не сдвигается: сдвинутая подпись оси
 * стоит не у своей линии, то есть врёт о значении.
 */
object ChartLabelLayout {

    /** Подпись-претендент на строку пикселей. */
    data class Label(
        /** Верхняя граница текста, px. */
        val topPx: Float,
        /** Высота текста, px. */
        val heightPx: Float,
        val priority: LabelPriority,
    )

    /**
     * Минимальный зазор между подписями, px.
     * **Инженерный параметр**: два пикселя — меньше выглядит как слипание даже
     * при формально не пересекающихся прямоугольниках.
     */
    const val GAP_PX = 2f

    /**
     * Какие подписи рисовать: индексы тех, что уцелели.
     *
     * Порядок разрешения — по приоритету, а не по порядку в списке: иначе
     * исход зависел бы от того, в каком порядке слои добавили свои подписи.
     */
    fun visible(labels: List<Label>): Set<Int> {
        val order = labels.indices.sortedWith(
            compareBy({ labels[it].priority.ordinal }, { labels[it].topPx }),
        )
        val kept = ArrayList<Int>(labels.size)
        for (index in order) {
            val label = labels[index]
            val collides = kept.any { other -> overlaps(label, labels[other]) }
            if (!collides) kept += index
        }
        return kept.toSet()
    }

    private fun overlaps(a: Label, b: Label): Boolean =
        a.topPx < b.topPx + b.heightPx + GAP_PX && b.topPx < a.topPx + a.heightPx + GAP_PX
}

/**
 * Кто важнее, когда подписи не помещаются вместе (ТЗ §25).
 *
 * Порядок объявления и есть приоритет: значение под курсором человек ищет
 * прямо сейчас, текущее значение — то, ради чего график открыт, порог —
 * названная граница, а подпись оси всегда восстановима по соседним.
 */
enum class LabelPriority {
    CURSOR_VALUE,
    CURRENT_VALUE,
    ALARM_THRESHOLD,
    SELECTED_EVENT,
    EVENT,
    AXIS_TICK,
}
