package app.alpha.ui.chart

import app.alpha.ui.logic.ChartWindow
import app.alpha.ui.logic.ChartWindows

/**
 * Состояние жеста: для какого окна ПОСЧИТАН кадр и какое окно ВИДНО сейчас.
 *
 * ## Зачем два окна
 *
 * Пересборка кадра — отбор колонок, устойчивые границы оси, эпизоды, маркеры,
 * гистограмма, статистика окна с сортировкой тысяч агрегатов. Делать её на
 * каждом кадре жеста незачем: между двумя кадрами картинка отличается
 * сдвигом, а не содержанием.
 *
 * [frame] — окно, для которого геометрия построена; [visible] — окно, в
 * которое уехал жест. Пока они различаются, экран двигает готовую картинку
 * ([transform]); когда движение улеглось, [commit] делает видимое окно
 * посчитанным, и кадр пересобирается один раз.
 *
 * ## Почему преобразование выводится, а не копится
 *
 * Пиксельные приращения пришлось бы клампить в пикселях — второй раз и другой
 * арифметикой, рядом с уже проверенной в [Viewports]. Жест двигает ОКНО, а
 * преобразование выводится из пары «нарисованный диапазон → видимое окно»:
 * клампы остаются в одном месте, картинка не расходится с состоянием.
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

    /**
     * Такт слежения: живой край двигает видимое окно, кадр остаётся.
     *
     * Геометрия строится с запасом и в будущее ([Companion.of]), поэтому край
     * может ехать, пока запас не кончится; после этого кадр пересобирается.
     */
    fun followTick(bounds: ViewportBounds): ChartGesture {
        if (!visible.followLiveEdge) return this
        val next = copy(visible = Viewports.followTick(visible, bounds))
        return if (next.covered()) next else of(next.visible, bounds)
    }

    /**
     * Пора ли пересобирать кадр посреди жеста.
     *
     * Отдаление уводит видимое окно за нарисованный диапазон почти сразу, но
     * пересборка на каждое событие указателя вернула бы ту работу, ради
     * которой заведены два окна. Поэтому она ограничена частотой: не чаще
     * одной за [MIN_COMMIT_INTERVAL_MILLIS]. Между ними по краям кадра видна
     * пустота — там ещё не посчитано.
     */
    fun shouldCommit(nowMillis: Long, lastCommitMillis: Long): Boolean =
        !covered() && nowMillis - lastCommitMillis >= MIN_COMMIT_INTERVAL_MILLIS

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
         * Как часто кадр имеет право пересобираться, пока жест идёт.
         *
         * **Инженерный параметр**: 90 мс — около шести кадров экрана. Реже —
         * и при быстром отдалении по краям подолгу висит пустота; чаще — и
         * пересборка снова начинает есть кадры жеста.
         */
        const val MIN_COMMIT_INTERVAL_MILLIS = 90L

        /**
         * Окно и его нарисованный диапазон.
         *
         * Запас берётся с обеих сторон, в том числе ВПРАВО, за предел времени.
         * Рисовать там нечего — измерений в будущем не бывает, — но именно
         * туда уезжает окно, пока оно следит за живым краем: без правого запаса
         * каждый такт слежения требовал бы пересборки кадра, а это самая
         * дорогая работа графика.
         */
        fun of(viewport: Viewport, bounds: ViewportBounds): ChartGesture {
            val pad = (viewport.spanMillis * HEADROOM_FRACTION).toLong()
            val air = ChartWindows.withRightPadding(viewport.window()).toMillis - viewport.endMillis
            return ChartGesture(
                frame = viewport,
                visible = viewport,
                rendered = ChartWindow(
                    viewport.startMillis - pad,
                    viewport.endMillis + pad + air,
                ),
            )
        }
    }
}
