package app.radiacode.ui.logic

/**
 * Геометрия картинки FWHM(E) — отдельно от рисования и с тестами.
 *
 * ## Почему это не может жить внутри Canvas
 *
 * Полевой урок разбора импорта: **JVM-тесты не ловят то, что падает только на
 * устройстве**. Compose-канва в модульных тестах не выполняется, поэтому
 * координата, ставшая `NaN`, доезжает до `drawPath` уже на телефоне — и
 * приложение закрывается без единой красной строки в наших прогонах.
 *
 * Источники нечисла здесь настоящие, а не гипотетические: подгонка модели
 * разрешения может вернуть неопределённые коэффициенты, измеренных линий
 * может не оказаться вовсе, а границы измеренного диапазона — прийти
 * пустыми. Поэтому вся арифметика вынесена сюда: она обязана возвращать
 * КОНЕЧНЫЕ числа в пределах поля при любом входе, а если рисовать нечего —
 * честно сказать об этом, а не отдать `NaN`.
 */
object CalibrationChart {

    /** Верх шкалы ширины; null — рисовать нечего. */
    fun axisTop(
        fittedAtTop: Double,
        approximationAtTop: Double,
        measuredWidths: List<Double>,
        headroom: Double = 1.15,
    ): Double? {
        val candidates = (listOf(fittedAtTop, approximationAtTop) + measuredWidths)
            .filter { it.isFinite() && it > 0.0 }
        val top = candidates.maxOrNull() ?: return null
        val scaled = top * headroom
        return if (scaled.isFinite() && scaled > 0.0) scaled else null
    }

    /**
     * Доля вдоль оси, зажатая в [0, 1]; null — значение не число.
     *
     * Зажим — не косметика: точка за пределами поля рисуется по чужой
     * геометрии, а `NaN` роняет канву.
     */
    fun fraction(value: Double, span: Double): Float? {
        if (!value.isFinite() || !span.isFinite() || span <= 0.0) return null
        return (value / span).coerceIn(0.0, 1.0).toFloat()
    }

    /** Точки кривой по равномерной сетке; пропускает всё, что не число. */
    fun curveFractions(
        maxEnergy: Double,
        axisTop: Double,
        steps: Int,
        widthAt: (Double) -> Double,
    ): List<Pair<Float, Float>> {
        if (steps <= 0 || !maxEnergy.isFinite() || maxEnergy <= 0.0) return emptyList()
        val result = ArrayList<Pair<Float, Float>>(steps + 1)
        for (index in 0..steps) {
            val energy = maxEnergy * index / steps
            val x = fraction(energy, maxEnergy) ?: continue
            val y = fraction(widthAt(energy), axisTop) ?: continue
            result += x to y
        }
        return result
    }

    /**
     * Доля поля, занятая областью ЭКСТРАПОЛЯЦИИ слева и справа.
     *
     * Пустая пара означает «измеренного диапазона нет» — тогда затенять
     * нечего, и это верный ответ: закрасить всё поле было бы утверждением,
     * которого никто не делал.
     */
    fun extrapolationBands(
        measuredFromKeV: Double,
        measuredToKeV: Double,
        maxEnergy: Double,
    ): Pair<Float, Float>? {
        val from = fraction(measuredFromKeV, maxEnergy) ?: return null
        val to = fraction(measuredToKeV, maxEnergy) ?: return null
        if (to <= from) return null
        return from to (1f - to)
    }
}
