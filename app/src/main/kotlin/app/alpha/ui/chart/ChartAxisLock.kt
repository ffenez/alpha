package app.alpha.ui.chart

import kotlin.math.abs
import kotlin.math.hypot

/** Что именно двигает начавшийся жест. */
enum class GestureAxis {
    /** Ещё не решено: движение не вышло за порог дрожания руки. */
    UNDECIDED,

    /** Время: перемещение по горизонтали и щипок. */
    TIME,

    /** Значения: перемещение кадра оси вверх и вниз. */
    VALUE,

    /** Масштаб значений: жест начался на шкале справа. */
    VALUE_SCALE,
}

/**
 * Ось, за которую держится палец.
 *
 * ## Зачем замок
 *
 * Горизонтальное перемещение по времени — главный жест графика, и он почти
 * никогда не бывает идеально горизонтальным. Если бы вертикальная
 * составляющая тут же двигала ось значений, любой сдвиг во времени незаметно
 * уводил бы шкалу — а вместе с ней и автоподбор, который после этого молча
 * перестал бы работать. Поэтому направление выбирается ОДИН раз за жест и
 * дальше не меняется: палец ведёт либо время, либо значения.
 *
 * ## Почему шкала — отдельный случай
 *
 * Жест, начатый на полосе значений справа, СЖИМАЕТ и растягивает ось, а не
 * двигает её: это знакомая модель аналитических графиков, и она даёт то, чего
 * перемещением не получить, — увидеть далёкий порог, не теряя из виду сами
 * измерения (ТЗ §6).
 */
class ChartAxisLock(
    private val slopPx: Float,
    private val gutterPx: Float,
) {

    var axis: GestureAxis = GestureAxis.UNDECIDED
        private set

    private var accX = 0f
    private var accY = 0f
    private var startXPx: Float? = null

    /**
     * Приращение жеста → ось, которой оно принадлежит.
     *
     * @param positionXPx где палец сейчас (для первого события — где он начал).
     * @param widthPx ширина поля: по ней узнаётся полоса шкалы справа.
     * @param vertical разрешены ли вертикальные жесты вовсе (у миниатюры на
     *   Главной — нет: там вертикаль принадлежит прокрутке страницы).
     */
    fun update(
        positionXPx: Float,
        widthPx: Float,
        panXPx: Float,
        panYPx: Float,
        zoom: Float,
        vertical: Boolean,
    ): GestureAxis {
        if (startXPx == null) startXPx = positionXPx
        if (axis != GestureAxis.UNDECIDED) return axis
        // Щипок всегда про время: расстояние между пальцами масштабирует окно,
        // и делить его между осями значило бы менять сразу оба масштаба.
        if (zoom != 1f) {
            axis = GestureAxis.TIME
            return axis
        }
        if (!vertical) {
            axis = GestureAxis.TIME
            return axis
        }
        if (startXPx!! >= widthPx - gutterPx) {
            axis = GestureAxis.VALUE_SCALE
            return axis
        }
        accX += panXPx
        accY += panYPx
        if (hypot(accX, accY) < slopPx) return GestureAxis.UNDECIDED
        axis = if (abs(accY) > VERTICAL_DOMINANCE * abs(accX)) {
            GestureAxis.VALUE
        } else {
            GestureAxis.TIME
        }
        return axis
    }

    /** Палец оторвался: следующий жест выбирает ось заново. */
    fun reset() {
        axis = GestureAxis.UNDECIDED
        accX = 0f
        accY = 0f
        startXPx = null
    }

    companion object {

        /**
         * Во сколько раз вертикальное движение должно превысить горизонтальное,
         * чтобы жест считался движением ОСИ.
         *
         * **Инженерный параметр**: полтора. Меньше — и обычное перемещение по
         * времени, которое рука ведёт слегка наискось, начнёт уводить шкалу;
         * больше — и намеренный вертикальный жест приходится делать неестественно
         * ровно.
         */
        const val VERTICAL_DOMINANCE = 1.5f
    }
}

/**
 * Одно приращение жеста, уже разложенное по осям.
 *
 * Экран получает не «пиксели пальца», а доли поля: от размера картинки
 * поведение зависеть не должно, а окно и кадр оси измеряются в долях.
 */
data class ChartGestureInput(
    val axis: GestureAxis,
    /** Доля ширины: положительная — палец идёт вправо, в прошлое. */
    val panXFraction: Float,
    /** Доля высоты: положительная — палец идёт вниз, в кадр приходит большее. */
    val panYFraction: Float,
    /** Множитель щипка за кадр; 1 — щипка нет. */
    val zoom: Float,
    /** Где между пальцами по горизонтали, 0..1. */
    val focusXFraction: Float,
)
