package app.alpha.ui.logic

/**
 * Форма подробного ряда: что соединяется линией, а что стоит штрихом.
 *
 * ## Почему это отдельная функция, а не пара строк в рисовании
 *
 * Подробный ряд — прореживание по крайним значениям колонки. Пока колонка
 * шириной в пиксель-другой, минимум и максимум — это соседние измерения, и
 * ломаная «максимум → минимум → максимум следующей» совпадает с самими
 * измерениями. Но ширина колонки задана НЕ только экраном: на длинных окнах
 * она приходит из хранения (ADR 004: колонка = час), и тогда та же ломаная
 * рисует час равномерного роста от минимума прошлого часа к максимуму
 * текущего и вертикальный сброс обратно — процесс, которого не было. На
 * шестичасовом окне это семь треугольников с размахом 0,06…0,17 мкЗв/ч при
 * ровных часовых медианах 0,115…0,125.
 *
 * Поэтому связь между колонками несёт МЕДИАНА (утверждение о времени между
 * колонками), а размах колонки — вертикальный штрих в её собственном x
 * (утверждение о том, что было внутри колонки). При узкой колонке штрихи
 * стоят вплотную и картинка та же, что была; при широкой получается честный
 * график размаха вместо выдуманного тренда.
 */
object ChartDetailShape {

    /** Вертикальный штрих размаха одной колонки. */
    data class RangeStroke(val x: Float, val topY: Float, val bottomY: Float)

    /**
     * Ломаные по медианам колонок: список отрезков, каждый — точки (x, y).
     * Разрыв во времени ([segmentStart]) начинает новую ломаную: перо через
     * пропуск не идёт.
     */
    fun medianPolylines(
        x: FloatArray,
        medianY: FloatArray,
        plottable: BooleanArray,
        segmentStart: BooleanArray,
    ): List<List<Pair<Float, Float>>> {
        val out = mutableListOf<List<Pair<Float, Float>>>()
        var current = mutableListOf<Pair<Float, Float>>()
        for (i in x.indices) {
            if (!plottable[i]) {
                if (current.isNotEmpty()) { out += current; current = mutableListOf() }
                continue
            }
            if (segmentStart[i] && current.isNotEmpty()) {
                out += current
                current = mutableListOf()
            }
            current += x[i] to medianY[i]
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /**
     * Штрихи размаха: по одному на колонку, где минимум и максимум различны.
     * Колонка, у которой они совпали, размаха не имеет — её несёт медиана.
     */
    fun rangeStrokes(
        x: FloatArray,
        minY: FloatArray,
        maxY: FloatArray,
        plottable: BooleanArray,
    ): List<RangeStroke> {
        val out = mutableListOf<RangeStroke>()
        for (i in x.indices) {
            if (!plottable[i]) continue
            // Ось экрана растёт вниз: максимум величины — меньший y.
            if (minY[i] != maxY[i]) out += RangeStroke(x[i], maxY[i], minY[i])
        }
        return out
    }
}
