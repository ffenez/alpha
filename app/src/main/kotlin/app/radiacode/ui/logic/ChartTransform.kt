package app.radiacode.ui.logic

/**
 * Визуальное преобразование готового кадра — основа перепроекции под пальцем.
 *
 * ## Зачем
 *
 * Сейчас каждое движение пальца пересобирает кадр целиком: отбор колонок,
 * устойчивые границы оси, эпизоды, гистограмма, проекция в пиксели. Шестьдесят
 * раз в секунду, пока идёт жест. Правильный путь — двигать УЖЕ ПОСЧИТАННУЮ
 * геометрию, а кадр пересобирать один раз, когда движение улеглось.
 *
 * Это и есть та величина, которой она двигается: сдвиг в пикселях и растяжение
 * по горизонтали относительно точки под пальцами.
 *
 * ## Почему отдельным типом
 *
 * Преобразование обязано быть проверяемым БЕЗ экрана: ошибка здесь не «криво
 * нарисовано», а «курсор показывает не то время». Поэтому здесь нет ни Compose,
 * ни Canvas — только арифметика, и она закрыта тестами.
 *
 * Единица измерения — пиксели поля графика, а начало отсчёта — левый край:
 * так же, как у массивов, которые готовит `ChartProjection`.
 */
data class ChartTransform(
    /** Сдвиг вправо, px: положительный двигает картинку по ходу пальца. */
    val dxPx: Float = 0f,
    /** Растяжение по X: >1 — приблизили, <1 — отдалили. */
    val scaleX: Float = 1f,
    /** Точка, вокруг которой растягивали, px от левого края. */
    val focusPx: Float = 0f,
) {
    val isIdentity: Boolean get() = dxPx == 0f && scaleX == 1f

    /** Куда уехал пиксель [x] исходного кадра. */
    fun mapX(x: Float): Float = focusPx + (x - focusPx) * scaleX + dxPx

    /** Какой пиксель ИСХОДНОГО кадра оказался в точке [x] на экране. */
    fun unmapX(x: Float): Float {
        if (scaleX == 0f) return x
        return focusPx + (x - dxPx - focusPx) / scaleX
    }

    /** Сдвиг пальцем поверх уже накопленного. */
    fun pan(deltaPx: Float): ChartTransform = copy(dxPx = dxPx + deltaPx)

    /**
     * Щипок вокруг точки [focus].
     *
     * Точка под пальцами обязана остаться на месте — это и есть «focal point
     * preserving»: иначе щипок у правого края растягивал бы середину экрана.
     * Поэтому новый сдвиг подбирается так, чтобы `mapX` не двинул исходный
     * пиксель, лежащий под пальцами.
     */
    fun zoom(factor: Float, focus: Float): ChartTransform {
        if (factor <= 0f || !factor.isFinite()) return this
        val source = unmapX(focus)
        val next = copy(scaleX = scaleX * factor, focusPx = focusPx)
        return next.copy(dxPx = next.dxPx + (focus - next.mapX(source)))
    }

    /**
     * Какое ВРЕМЯ показывает точка экрана после преобразования.
     *
     * Кадр посчитан для окна [fromMillis]..[toMillis] и ширины [widthPx];
     * преобразование сдвинуло картинку, но не пересчитало данные, поэтому
     * время читается через обратное отображение.
     */
    fun timeAt(x: Float, fromMillis: Long, toMillis: Long, widthPx: Float): Long {
        if (widthPx <= 0f) return fromMillis
        val fraction = (unmapX(x) / widthPx).coerceIn(0f, 1f)
        return fromMillis + ((toMillis - fromMillis) * fraction).toLong()
    }

    /** Окно, которое ВИДНО после преобразования: его и пересобирают на затихании. */
    fun visibleWindow(fromMillis: Long, toMillis: Long, widthPx: Float): ChartWindow {
        if (widthPx <= 0f) return ChartWindow(fromMillis, toMillis)
        // БЕЗ зажима в границы готового кадра — в отличие от [timeAt].
        //
        // Курсор обязан оставаться внутри данных: показывать время там, где
        // измерений нет, нельзя. А видимое окно — наоборот, ровно то место,
        // КУДА уехали, и оно почти всегда выходит за пределы кадра: иначе
        // пересобирать было бы нечего, и график упирался бы в невидимую
        // стенку на первом же сдвиге.
        val span = (toMillis - fromMillis).toDouble()
        val from = fromMillis + (span * (unmapX(0f) / widthPx)).toLong()
        val to = fromMillis + (span * (unmapX(widthPx) / widthPx)).toLong()
        return if (to > from) ChartWindow(from, to) else ChartWindow(fromMillis, toMillis)
    }

    companion object {
        val IDENTITY = ChartTransform()
    }
}
