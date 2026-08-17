package app.alpha.ui.logic

import kotlin.math.roundToInt

/**
 * Перетаскивание строки списка: на сколько позиций она уехала.
 *
 * ## Зачем отдельно
 *
 * Порядок вкладок меняли двумя стрелками ↑/↓. Стрелки честные, но это разговор
 * с приложением по одной команде за раз: переставить последнюю вкладку в
 * начало — четыре нажатия, и после каждого список подпрыгивает. Перетаскивание
 * говорит то же самое одним движением.
 *
 * Арифметика вынесена из композиции по той же причине, что и всё остальное:
 * ошибка здесь — это «строка встала не туда», и увидеть её на глаз можно
 * только повторив жест, а проверить числом — сразу.
 */
object DragReorder {

    /**
     * На сколько строк сместился палец от начала перетаскивания.
     *
     * Шаг считается по ПОЛОВИНЕ высоты строки: пока строка не перекрыла
     * соседнюю больше чем наполовину, порядок не меняется — иначе список
     * начинал бы переставляться от дрожания руки.
     */
    fun steps(offsetPx: Float, rowHeightPx: Float): Int {
        if (rowHeightPx <= 0f || !offsetPx.isFinite()) return 0
        return (offsetPx / rowHeightPx).roundToInt()
    }

    /**
     * Куда попадёт строка [from] после сдвига на [steps] позиций в списке из
     * [count] строк. За края списка строка не уезжает.
     */
    fun target(from: Int, steps: Int, count: Int): Int {
        if (count <= 0) return 0
        return (from + steps).coerceIn(0, count - 1)
    }

    /** Список с переставленной строкой; исходный не меняется. */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in items.indices || to !in items.indices) return items
        val out = items.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }
}
