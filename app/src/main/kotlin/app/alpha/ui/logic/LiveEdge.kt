package app.alpha.ui.logic

/**
 * Живой край графика между секундными тиками.
 *
 * Точки приходят раз в секунду, и окно, привязанное к «сейчас», сдвигалось
 * тоже раз в секунду — на пятиминутном окне это скачок примерно в один
 * пиксель, который глаз читает как рывок. Между тиками сдвигается ТОЛЬКО
 * окно просмотра: кадр, агрегация и сами значения не пересчитываются, поэтому
 * плавно едет картинка, а не данные.
 *
 * Правило движения приложения при этом не нарушено: измеренное не
 * интерполируется — перемещается система координат, в которой оно нарисовано.
 */
object LiveEdge {

    /**
     * Ниже какого сдвига за секунду анимация не нужна, пикселей.
     *
     * **Инженерный параметр.** Полпикселя в секунду — граница, за которой
     * движение перестаёт быть различимым: на шестичасовом окне это 0,015 px/с,
     * и покадровая перерисовка тратила бы батарею на то, чего не видно.
     * На пятиминутном окне выходит около 1 px/с — там движение и нужно.
     */
    const val MIN_PIXELS_PER_SECOND = 0.5

    /** Стоит ли двигать край покадрово при таком окне и такой ширине поля. */
    fun smooth(spanMillis: Long, plotWidthPx: Float): Boolean {
        if (spanMillis <= 0L || plotWidthPx <= 0f) return false
        return pixelsPerSecond(spanMillis, plotWidthPx) >= MIN_PIXELS_PER_SECOND
    }

    /** Сколько пикселей проезжает край за секунду. */
    fun pixelsPerSecond(spanMillis: Long, plotWidthPx: Float): Double =
        plotWidthPx.toDouble() * 1000.0 / spanMillis

    /**
     * Окно, доведённое от последнего тика [tickMillis] до момента кадра
     * [frameMillis]. Сдвиг никогда не отрицательный (время не идёт назад) и не
     * больше секунды: больший разрыв означает, что тик просто ещё не пришёл, и
     * тянуть окно дальше значило бы показывать время, которого не измеряли.
     */
    fun shifted(window: ChartWindow, tickMillis: Long, frameMillis: Long): ChartWindow {
        val shift = (frameMillis - tickMillis).coerceIn(0L, MAX_SHIFT_MILLIS)
        if (shift == 0L) return window
        return ChartWindow(window.fromMillis + shift, window.toMillis + shift)
    }

    /** Дольше секунды окно не тянется: следующий тик всё равно поставит край. */
    const val MAX_SHIFT_MILLIS = 1_000L
}
