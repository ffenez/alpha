package app.radiacode.ui.chart

import app.radiacode.ui.logic.ChartWindow

/**
 * Что видно на графике — единственное состояние движка Charts V2.
 *
 * ## Почему окно, а не ступень
 *
 * До сих пор настоящим состоянием был ИНДЕКС ступени лестницы (1м, 5м, 15м…),
 * а окно из него вычислялось. Щипок при этом переводил на соседнюю ступень
 * целиком: пальцы разъезжались, картинка стояла, потом прыгала. Это и читалось
 * как «график не держится за палец» — между двумя ступенями показать было
 * нечего, потому что промежуточного состояния не существовало.
 *
 * Здесь состояние — сами границы времени. Ступени остаются, но становятся тем,
 * чем они есть: пресетами, то есть кнопками, которые ставят окно в известное
 * значение. После щипка окно может быть каким угодно — 3 мин 42 с, 8 мин 17 с —
 * и график в нём остаётся (ТЗ Charts V2 §3).
 *
 * ## Цена и как она платится
 *
 * От длины окна зависят ширина колонки и путь чтения квантилей (ADR 004:
 * до шести часов — точные порядковые статистики, дальше — слияние почасовых
 * скетчей). При произвольных окнах ширина колонки становится произвольной, и
 * одно и то же место истории после разных жестов выглядит чуть по-разному.
 * Это осознанный размен: числа под графиком по-прежнему называют СВОЙ метод
 * («приближение» у скетчей), а перечитывание после жеста пересобирает колонки
 * под фактическое окно. Взамен исчезает главная неправда прежнего управления —
 * график, который не следует за пальцем.
 *
 * ## Правила
 *
 * - Правый край никогда не заходит за [ViewportBounds.edgeMillis]: измерять
 *   будущее нечем.
 * - Слежение за живым краем — свойство ОКНА, а не отдельный режим экрана:
 *   ушли жестом — выключается, вернулись к краю — включается само.
 * - Ось значений имеет свой режим ([YMode]): автоподбор под видимые данные или
 *   ручной масштаб, заданный человеком.
 */
data class Viewport(
    val startMillis: Long,
    val endMillis: Long,
    /** Едет ли окно вместе с «сейчас». */
    val followLiveEdge: Boolean = true,
    val yMode: YMode = YMode.AUTO,
) {
    val spanMillis: Long get() = endMillis - startMillis

    fun window(): ChartWindow = ChartWindow(startMillis, endMillis)

    /** Доля 0..1 по горизонтали → время под ней. */
    fun timeAt(fraction: Float): Long =
        startMillis + (spanMillis * fraction.coerceIn(0f, 1f)).toLong()
}

/** Как выбирается диапазон оси значений. */
enum class YMode {
    /** Ось следует видимым данным (устойчивые границы, §7 ТЗ). */
    AUTO,

    /** Масштаб задан рукой и не подстраивается, пока его не вернут в АВТО. */
    MANUAL,
}

/**
 * Границы, в которых окно имеет право двигаться.
 *
 * @param edgeMillis правый предел времени: «сейчас» на живом графике, конец
 *   сессии — на историческом.
 * @param earliestMillis начало истории; null — неизвестно, левый предел не
 *   применяется.
 * @param minSpanMillis самое узкое окно, которое имеет смысл рисовать.
 * @param maxSpanMillis самое широкое окно, которое величина умеет показать
 *   честно (у счёта и жёсткости предагрегации нет — см. `ChartMetrics`).
 */
data class ViewportBounds(
    val edgeMillis: Long,
    val earliestMillis: Long? = null,
    val minSpanMillis: Long = Viewports.MIN_SPAN_MILLIS,
    val maxSpanMillis: Long = Viewports.MAX_SPAN_MILLIS,
)

object Viewports {

    /**
     * Самое узкое окно. **Инженерный параметр**: прибор пишет раз в секунду,
     * и минута — это шестьдесят измерений; уже неё график перестаёт быть
     * графиком и становится строкой чисел.
     */
    const val MIN_SPAN_MILLIS = 60_000L

    /** Самое широкое окно: месяц — предел хранения почасовых скетчей. */
    const val MAX_SPAN_MILLIS = 30L * 24 * 3_600_000L

    /**
     * Ближе этого к правому пределу окно считается стоящим на живом крае.
     *
     * **Инженерный параметр**: доля окна, а не константа — на минутном окне
     * полсекунды это много, на суточном ничто.
     */
    const val FOLLOW_SNAP_FRACTION = 0.02f

    /**
     * Насколько далеко можно уехать левее начала истории.
     *
     * **Инженерный параметр**: половина окна. Ноль означал бы стену ровно на
     * первом измерении — палец упирается, и непонятно, кончились данные или
     * сломался жест. Половина окна показывает саму границу истории и место за
     * ней, но не даёт улететь в пустоту на часы (ТЗ §5.2 «не перелетать
     * дальше доступной истории»).
     */
    const val PAN_BEYOND_HISTORY_FRACTION = 0.5f

    /** Окно заданной длины, прижатое к правому пределу. */
    fun atEdge(spanMillis: Long, bounds: ViewportBounds, yMode: YMode = YMode.AUTO): Viewport {
        val span = spanMillis.coerceIn(bounds.minSpanMillis, bounds.maxSpanMillis)
        return Viewport(
            startMillis = bounds.edgeMillis - span,
            endMillis = bounds.edgeMillis,
            followLiveEdge = true,
            yMode = yMode,
        )
    }

    /**
     * Такт слежения: длительность окна сохраняется, правый край встаёт на
     * текущий предел. Не следящее окно не трогается вовсе.
     */
    fun followTick(viewport: Viewport, bounds: ViewportBounds): Viewport {
        if (!viewport.followLiveEdge) return viewport
        return viewport.copy(
            startMillis = bounds.edgeMillis - viewport.spanMillis,
            endMillis = bounds.edgeMillis,
        )
    }

    /** Сдвиг на долю окна: положительная доля — позже во времени (вправо). */
    fun pan(viewport: Viewport, deltaFraction: Float, bounds: ViewportBounds): Viewport =
        panMillis(viewport, (viewport.spanMillis * deltaFraction).toLong(), bounds)

    /** Сдвиг на заданное время: положительное — позже. */
    fun panMillis(viewport: Viewport, deltaMillis: Long, bounds: ViewportBounds): Viewport =
        clamp(
            viewport.copy(
                startMillis = viewport.startMillis + deltaMillis,
                endMillis = viewport.endMillis + deltaMillis,
            ),
            bounds,
        )

    /**
     * Щипок вокруг точки под пальцами.
     *
     * [factor] > 1 — пальцы разошлись, окно КОРОЧЕ (приблизили). Время под
     * [focusFraction] остаётся на месте: иначе щипок у правого края растягивал
     * бы середину экрана (ТЗ §5.3).
     */
    fun zoom(
        viewport: Viewport,
        factor: Float,
        focusFraction: Float,
        bounds: ViewportBounds,
    ): Viewport {
        if (factor <= 0f || !factor.isFinite()) return viewport
        val focus = focusFraction.coerceIn(0f, 1f)
        val focusTime = viewport.timeAt(focus)
        val span = (viewport.spanMillis / factor).toLong()
            .coerceIn(bounds.minSpanMillis, bounds.maxSpanMillis)
        val start = focusTime - (span * focus).toLong()
        return clamp(viewport.copy(startMillis = start, endMillis = start + span), bounds)
    }

    /** Явная длина окна (выбор пресета) — у правого предела. */
    fun withSpan(viewport: Viewport, spanMillis: Long, bounds: ViewportBounds): Viewport =
        atEdge(spanMillis, bounds, viewport.yMode)

    /** Возврат к правому пределу с сохранением длины окна. */
    fun jumpToEdge(viewport: Viewport, bounds: ViewportBounds): Viewport =
        atEdge(viewport.spanMillis, bounds, viewport.yMode)

    /** Стоит ли окно на живом крае (с точностью до [FOLLOW_SNAP_FRACTION]). */
    fun atLiveEdge(viewport: Viewport, bounds: ViewportBounds): Boolean =
        bounds.edgeMillis - viewport.endMillis <=
            (viewport.spanMillis * FOLLOW_SNAP_FRACTION).toLong()

    /**
     * Приведение окна в допустимые границы и решение о слежении.
     *
     * Порядок важен: сперва длина (её ограничивают величина и хранение), потом
     * положение. Слежение — следствие положения, а не отдельное решение: окно,
     * доехавшее до правого предела, и есть просьба следить дальше.
     */
    fun clamp(viewport: Viewport, bounds: ViewportBounds): Viewport {
        val span = viewport.spanMillis
            .coerceIn(bounds.minSpanMillis, bounds.maxSpanMillis)
        var end = viewport.endMillis.coerceAtMost(bounds.edgeMillis)
        val earliest = bounds.earliestMillis
        if (earliest != null) {
            // Левее истории — только на половину окна: столько, чтобы увидеть
            // саму границу данных, и не столько, чтобы потеряться в пустоте.
            val minEnd = earliest - (span * PAN_BEYOND_HISTORY_FRACTION).toLong() + span
            if (end < minEnd) end = minEnd.coerceAtMost(bounds.edgeMillis)
        }
        val result = Viewport(
            startMillis = end - span,
            endMillis = end,
            followLiveEdge = false,
            yMode = viewport.yMode,
        )
        return result.copy(followLiveEdge = atLiveEdge(result, bounds))
    }
}
