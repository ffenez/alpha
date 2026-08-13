package app.radiacode.ui.logic

import app.radiacode.ui.text.ChartAxisRu
import app.radiacode.ui.text.ChartAxisStrings

/**
 * Visible time window of the fullscreen live chart. Pure math for pinch-zoom,
 * pan and live-follow (window ↔ pixel-fraction mapping); JVM-tested. The
 * screen holds one [ChartWindow] as state and feeds gestures through
 * [ChartWindows].
 */
data class ChartWindow(val fromMillis: Long, val toMillis: Long) {
    val spanMillis: Long get() = toMillis - fromMillis
}

object ChartWindows {

    /** Zoom bounds: 1 minute … 30 days. */
    const val MIN_SPAN_MILLIS = 60_000L
    const val MAX_SPAN_MILLIS = 30L * 24 * 3_600_000L

    /**
     * Лестница окон графика.
     *
     * Шесть ступеней от 15 минут до месяца оставляли зияние там, где человек
     * смотрит чаще всего: между «сейчас» и часом. Ряд построен как 1-2-3-5-10-30
     * внутри каждой единицы времени — знакомый шаг циферблата, в котором глаз
     * не считает нули, — и продолжается в часы и дни. Ниже минуты идти незачем:
     * прибор пишет раз в секунду, и окно короче минуты показывало бы горстку
     * отсчётов.
     */
    /** Ступень лестницы: число и единица времени, из которых собрана подпись. */
    data class Step(val amount: Int, val unit: TimeUnit, val millis: Long)

    enum class TimeUnit { MINUTES, HOURS, DAYS }

    val STEPS: List<Step> = listOf(
        Step(1, TimeUnit.MINUTES, 60_000L),
        Step(2, TimeUnit.MINUTES, 2L * 60_000L),
        Step(3, TimeUnit.MINUTES, 3L * 60_000L),
        Step(5, TimeUnit.MINUTES, 5L * 60_000L),
        Step(10, TimeUnit.MINUTES, 10L * 60_000L),
        Step(30, TimeUnit.MINUTES, 30L * 60_000L),
        Step(1, TimeUnit.HOURS, 3_600_000L),
        Step(2, TimeUnit.HOURS, 2L * 3_600_000L),
        Step(3, TimeUnit.HOURS, 3L * 3_600_000L),
        Step(6, TimeUnit.HOURS, 6L * 3_600_000L),
        Step(12, TimeUnit.HOURS, 12L * 3_600_000L),
        Step(1, TimeUnit.DAYS, 24L * 3_600_000L),
        Step(2, TimeUnit.DAYS, 2L * 24 * 3_600_000L),
        Step(7, TimeUnit.DAYS, 7L * 24 * 3_600_000L),
        Step(30, TimeUnit.DAYS, MAX_SPAN_MILLIS),
    )

    /**
     * Подпись ступени на языке интерфейса: «6ч» / «6h».
     *
     * Единица собирается из каталога, а не хранится строкой: подпись чипа
     * обязана следовать языку, а сама ступень — нет, она число.
     */
    fun stepLabel(step: Step, s: ChartAxisStrings = ChartAxisRu): String = step.amount.toString() +
        when (step.unit) {
            TimeUnit.MINUTES -> s.stepMinutes
            TimeUnit.HOURS -> s.stepHours
            TimeUnit.DAYS -> s.stepDays
        }

    fun periodLabel(index: Int, s: ChartAxisStrings = ChartAxisRu): String =
        stepLabel(STEPS[index], s)

    /** Ступени в прежнем виде «подпись → длительность» (подпись русская). */
    val PERIODS: List<Pair<String, Long>> = STEPS.map { stepLabel(it) to it.millis }

    /** Индекс окна, которое открывается по умолчанию (6 ч). */
    val DEFAULT_PERIOD_INDEX: Int = PERIODS.indexOfFirst { it.second == 6L * 3_600_000L }

    /**
     * Padding factor of the loaded range around the visible window. Gestures
     * re-project an already-loaded snapshot, so the loader deliberately fetches
     * a quarter-span of context on each side: a pan of up to 25 % of the window
     * shows real data instantly and the debounced reload only refines the
     * resolution afterwards.
     */
    const val LOAD_PADDING_FRACTION = 0.25f

    /** Visible window → the range to ask the database for (right edge ≤ now). */
    fun loadRange(window: ChartWindow, nowMillis: Long): ChartWindow {
        val pad = (window.spanMillis * LOAD_PADDING_FRACTION).toLong()
        val to = minOf(window.toMillis + pad, nowMillis)
        return ChartWindow(window.fromMillis - pad, maxOf(to, window.toMillis))
    }

    /** True when [window] is fully inside an already-loaded [loaded] range. */
    fun covers(loaded: ChartWindow, window: ChartWindow): Boolean =
        loaded.fromMillis <= window.fromMillis && loaded.toMillis >= window.toMillis

    /**
     * Ступень лестницы, ближайшая к фактическому окну.
     *
     * Щипок меняет окно плавно, а лестница дискретна — и до сих пор выбранный
     * чип оставался там, где его нажали в последний раз, то есть врал: на
     * экране час, подсвечено «6ч». Ближайшая ступень ищется по ОТНОШЕНИЮ
     * длительностей, а не по разности: между 1м и 2м столько же «расстояния»,
     * сколько между 1ч и 2ч, и глаз воспринимает их одинаково.
     */
    fun nearestPeriodIndex(spanMillis: Long, among: List<Int> = PERIODS.indices.toList()): Int {
        if (among.isEmpty()) return 0
        val span = spanMillis.coerceAtLeast(1L).toDouble()
        return among.minByOrNull { index ->
            val period = PERIODS[index].second.toDouble()
            kotlin.math.abs(kotlin.math.ln(span / period))
        } ?: among.first()
    }

    /**
     * Совпадает ли окно со ступенью настолько, чтобы подсветить её как
     * выбранную. Внутри допуска — да; после щипка окно обычно между ступенями,
     * и тогда не подсвечено ничего: подсвеченный чип означает «ровно это
     * окно», а не «где-то рядом».
     */
    fun matchesPeriod(spanMillis: Long, index: Int, tolerance: Double = PERIOD_TOLERANCE): Boolean {
        val period = PERIODS.getOrNull(index)?.second ?: return false
        val ratio = spanMillis.toDouble() / period
        return kotlin.math.abs(ratio - 1.0) <= tolerance
    }

    /** Допуск совпадения окна со ступенью. **Инженерный параметр.** */
    const val PERIOD_TOLERANCE = 0.02

    /**
     * Ряд чипов стал длиннее экрана, поэтому он прокручивается, а не
     * подрезается: раньше видимое окно из четырёх чипов скользило вместе с
     * выбором, и соседние ступени приходилось угадывать. Экран сам подкручивает
     * ленту к выбранному чипу — это [scrollTargetIndex].
     *
     * Возвращает индекс, к которому нужно подвести ленту, чтобы выбранный чип
     * оказался не у самого края и было видно, что ряд продолжается.
     */
    fun scrollTargetIndex(selectedIndex: Int, lead: Int = 1): Int =
        (selectedIndex - lead).coerceIn(0, (PERIODS.size - 1).coerceAtLeast(0))

    /** Window ending at now with the given span. */
    fun latest(spanMillis: Long, nowMillis: Long): ChartWindow {
        val span = spanMillis.coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS)
        return ChartWindow(nowMillis - span, nowMillis)
    }

    /**
     * Доля окна, оставляемая воздухом справа от «сейчас».
     *
     * **Инженерный параметр**: 2 % ширины — заметно глазу и не крадёт данных.
     * Область правее «сейчас» НЕ является пропуском: там ещё нечего измерять,
     * поэтому она не штрихуется и не участвует в поиске пропусков.
     */
    const val RIGHT_PADDING_FRACTION = 0.02

    /** Кадр отрисовки: то же окно данных плюс воздух у живого края. */
    fun withRightPadding(window: ChartWindow): ChartWindow = ChartWindow(
        fromMillis = window.fromMillis,
        toMillis = window.toMillis + (window.spanMillis * RIGHT_PADDING_FRACTION).toLong(),
    )

    /** Live-follow tick: keep the span, pin the right edge to now. */
    fun follow(window: ChartWindow, nowMillis: Long): ChartWindow =
        latest(window.spanMillis, nowMillis)

    /**
     * Pan by a fraction of the span (positive = later in time). The right
     * edge clamps at now; the span never changes.
     */
    fun pan(window: ChartWindow, deltaFraction: Float, nowMillis: Long): ChartWindow {
        val shift = (window.spanMillis * deltaFraction).toLong()
        var from = window.fromMillis + shift
        var to = window.toMillis + shift
        if (to > nowMillis) {
            from -= to - nowMillis
            to = nowMillis
        }
        return ChartWindow(from, to)
    }

    /**
     * Zoom by [factor] (>1 = zoom in) keeping the time under [focusFraction]
     * (0..1 across the plot) fixed. Span clamps to [MIN_SPAN_MILLIS]..
     * [MAX_SPAN_MILLIS], the right edge clamps at now.
     */
    fun zoom(
        window: ChartWindow,
        factor: Float,
        focusFraction: Float,
        nowMillis: Long,
    ): ChartWindow {
        if (factor <= 0f) return window
        val span = (window.spanMillis / factor).toLong()
            .coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS)
        val focus = focusFraction.coerceIn(0f, 1f)
        val focusTime = timeAt(window, focus)
        var from = focusTime - (span * focus).toLong()
        var to = from + span
        if (to > nowMillis) {
            from -= to - nowMillis
            to = nowMillis
        }
        return ChartWindow(from, to)
    }

    /** Fraction (0..1) → epoch millis inside the window. */
    fun timeAt(window: ChartWindow, fraction: Float): Long =
        window.fromMillis + (window.spanMillis * fraction.coerceIn(0f, 1f)).toLong()

    /** Epoch millis → fraction (0..1) inside the window. */
    fun fraction(window: ChartWindow, timeMillis: Long): Float {
        if (window.spanMillis <= 0L) return 0f
        return ((timeMillis - window.fromMillis).toFloat() / window.spanMillis)
            .coerceIn(0f, 1f)
    }

    /** Downsampling bucket for the window at the given column count, ≥1 s. */
    fun bucketMillis(spanMillis: Long, columns: Int): Long =
        (spanMillis / columns.coerceAtLeast(1)).coerceAtLeast(1_000L)

    /**
     * The window sits at the live edge when now is within one bucket of the
     * right edge — panning back to «сейчас» re-enables following naturally.
     */
    fun isAtLiveEdge(window: ChartWindow, nowMillis: Long, bucketMillis: Long): Boolean =
        nowMillis - window.toMillis <= bucketMillis

    /**
     * Live refresh cadence: 1 Hz appends on short windows; long windows only
     * change a bucket every bucketMillis, so refreshing faster than a quarter
     * bucket (capped at 15 s) would waste queries without new pixels.
     */
    fun refreshMillis(bucketMillis: Long): Long =
        (bucketMillis / 4).coerceIn(1_000L, 15_000L)
}
