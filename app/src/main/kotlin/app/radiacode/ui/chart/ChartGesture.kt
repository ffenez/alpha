package app.radiacode.ui.chart

import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartWindows

/**
 * Состояние жеста: для какого окна ПОСЧИТАН кадр и какое окно ВИДНО сейчас.
 *
 * ## Зачем два окна
 *
 * Каждое движение пальца пересобирало кадр целиком: отбор колонок, устойчивые
 * границы оси, эпизоды, маркеры, гистограмма, статистика окна с сортировкой
 * тысяч агрегатов — шестьдесят раз в секунду, пока идёт жест. Работа при этом
 * почти вся выбрасывалась: между двумя кадрами жеста картинка отличается
 * сдвигом, а не содержанием.
 *
 * Здесь их двое. [frame] — окно, для которого геометрия уже построена;
 * [visible] — окно, в которое человек уехал пальцем прямо сейчас. Пока они
 * различаются, экран ДВИГАЕТ готовую картинку ([transform]), а не считает
 * новую. Когда движение улеглось, [commit] делает видимое окно посчитанным, и
 * кадр пересобирается один раз.
 *
 * ## Почему преобразование выводится, а не копится
 *
 * Пиксельные приращения жеста можно было бы складывать прямо в
 * [ChartTransform]. Тогда границы (край «сейчас», начало истории, пределы
 * масштаба) пришлось бы проверять в пикселях — второй раз, другой арифметикой,
 * рядом с уже проверенной в [Viewports]. Вместо этого жест двигает ОКНО
 * обычными операциями, а преобразование выводится из пары «нарисованный
 * диапазон → видимое окно»: клампы остаются в одном месте, а картинка не может
 * разъехаться с состоянием.
 */
data class ChartGesture(
    /** Окно, для которого построен кадр. */
    val frame: Viewport,
    /** Окно, которое видно на экране сейчас. */
    val visible: Viewport,
    /**
     * Диапазон, для которого построена ГЕОМЕТРИЯ: окно кадра плюс запас слева
     * и справа. Без запаса первый же сдвиг открывал бы пустое поле — колонок
     * за краем посчитанного окна просто нет.
     */
    val rendered: ChartWindow,
) {

    /** Движется ли картинка относительно посчитанного кадра. */
    val moved: Boolean
        get() = visible.startMillis != frame.startMillis || visible.endMillis != frame.endMillis

    /**
     * Преобразование, которым надо двигать готовую картинку, чтобы на экране
     * оказалось [visible].
     *
     * Кадр нарисован для [rendered] на всю ширину поля; преобразование
     * растягивает его так, чтобы видимое окно (с тем же воздухом справа, что и
     * у кадра) заняло эту ширину целиком.
     */
    fun transform(widthPx: Float): ChartTransform {
        if (widthPx <= 0f) return ChartTransform.IDENTITY
        val renderedSpan = rendered.spanMillis.toDouble()
        val target = ChartWindows.withRightPadding(visible.window())
        if (renderedSpan <= 0.0 || target.spanMillis <= 0L) return ChartTransform.IDENTITY
        val scale = renderedSpan / target.spanMillis
        // Пиксель, на котором в готовом кадре стоит левый край видимого окна.
        val leftPx = widthPx * ((target.fromMillis - rendered.fromMillis) / renderedSpan)
        return ChartTransform(
            dxPx = (-leftPx * scale).toFloat(),
            scaleX = scale.toFloat(),
            focusPx = 0f,
        )
    }

    /** Хватает ли нарисованного диапазона на то, что видно сейчас. */
    fun covered(): Boolean {
        val target = ChartWindows.withRightPadding(visible.window())
        return rendered.fromMillis <= target.fromMillis && rendered.toMillis >= target.toMillis
    }

    fun pan(deltaFraction: Float, bounds: ViewportBounds): ChartGesture =
        copy(visible = Viewports.pan(visible, deltaFraction, bounds))

    fun zoom(factor: Float, focusFraction: Float, bounds: ViewportBounds): ChartGesture =
        copy(visible = Viewports.zoom(visible, factor, focusFraction, bounds))

    /** Курсор на экране останавливает слежение: ряд не уезжает из-под показания. */
    fun holdForCursor(): ChartGesture = copy(visible = visible.copy(followLiveEdge = false))

    /** Такт слежения двигает ОБА окна: живой край не жест, пересчитывать нечего. */
    fun followTick(bounds: ViewportBounds): ChartGesture {
        if (!visible.followLiveEdge) return this
        return of(Viewports.followTick(visible, bounds), bounds)
    }

    /** Движение улеглось: видимое окно становится посчитанным. */
    fun commit(bounds: ViewportBounds): ChartGesture = of(visible, bounds)

    /** Явная установка окна (пресет, возврат к краю) — без промежуточного жеста. */
    fun withViewport(next: Viewport, bounds: ViewportBounds): ChartGesture = of(next, bounds)

    companion object {

        /**
         * Запас геометрии с каждой стороны окна, в долях окна.
         *
         * **Инженерный параметр**: половина окна. Столько проходит уверенный
         * рывок пальцем до того, как движение успеет улечься; больше — значит
         * складывать вдвое больше колонок на каждой пересборке ради случая,
         * который и так закрыт: уехали за запас — кадр пересобирается сразу,
         * не дожидаясь паузы, потому что рисовать нечего.
         */
        const val HEADROOM_FRACTION = 0.5f

        /**
         * Окно и его нарисованный диапазон.
         *
         * Вправо запас не заходит за предел времени: правее «сейчас» ещё нечего
         * измерять, и рисовать там нечего — кроме постоянного воздуха у живого
         * края, который добавляет [ChartWindows.withRightPadding].
         */
        fun of(viewport: Viewport, bounds: ViewportBounds): ChartGesture {
            val pad = (viewport.spanMillis * HEADROOM_FRACTION).toLong()
            val air = ChartWindows.withRightPadding(viewport.window()).toMillis - viewport.endMillis
            val to = minOf(viewport.endMillis + pad, bounds.edgeMillis) + air
            return ChartGesture(
                frame = viewport,
                visible = viewport,
                rendered = ChartWindow(viewport.startMillis - pad, maxOf(to, viewport.endMillis)),
            )
        }
    }
}
