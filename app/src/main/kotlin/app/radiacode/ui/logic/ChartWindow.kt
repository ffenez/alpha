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

    /**
     * Окно, которое открывается по умолчанию, — **5 минут**.
     *
     * Было шесть часов, и это оказалось главной причиной жалобы «графики не
     * обновляются в реальном времени». Потери данных нет: при шестичасовом окне
     * колонка получается около полутора минут (а на подтянутом к истории —
     * около полуминуты), и новая точка появляется раз в это время. Край ползёт,
     * линия стоит — на глаз это неотличимо от замершего графика.
     *
     * Пять минут при секундной записи — это триста измерений и колонка в пару
     * секунд: видно, как график живёт, и видно каждое движение. Кому нужен
     * час или сутки — выбирает ступень, и выбор запоминается по величине.
     */
    val DEFAULT_PERIOD_INDEX: Int = PERIODS.indexOfFirst { it.second == 5L * 60_000L }

    /**
     * Padding factor of the loaded range around the visible window. Gestures
     * re-project an already-loaded snapshot, so the loader deliberately fetches
     * a quarter-span of context on each side: a pan of up to 25 % of the window
     * shows real data instantly and the debounced reload only refines the
     * resolution afterwards.
     */
    /**
     * Сколько окон запаса читать с каждой стороны.
     * **Инженерный параметр**: одно окно — уверенный рывок пальцем целиком
     * укладывается в прочитанное. Четверти, с которой начинали, хватало на
     * лёгкий сдвиг, а на настоящий жест — нет.
     */
    const val LOAD_PADDING_FRACTION = 1.0f

    /**
     * Наименьший запас с каждой стороны.
     * **Инженерный параметр**: час. На коротких окнах доля окна даёт минуты, а
     * жест пальцем за минуты и проходит; час хода без запроса — это уже «не
     * подгружается», а не «подгружается реже». Ограничение сверху прежнее:
     * граница точного пути чтения.
     */
    const val MIN_LOAD_PADDING_MILLIS = 3_600_000L

    /**
     * Visible window → the range to ask the database for (right edge ≤ now).
     *
     * ## Запас — это и есть отзывчивость жеста
     *
     * Сдвиг внутри прочитанного не требует запроса: снимок неизменен, меняется
     * проекция. Значит чем шире запас, тем дольше палец водит график без
     * похода в базу. Прежняя четверть окна кончалась после первого же
     * уверенного рывка, и загрузка была видна глазом.
     *
     * ## Но запас не имеет права менять ПУТЬ ЧТЕНИЯ
     *
     * Метод квантилей выбирается по длине ЗАГРУЖАЕМОГО диапазона: до шести
     * часов — точные порядковые статистики сырых отсчётов, дальше — слияние
     * почасовых скетчей. Раздуть запас так, чтобы окно перескочило границу,
     * значит молча сменить метод — и подпись под графиком стала бы говорить
     * «приближение» там, где человек ничего не менял. Поэтому у окна, которое
     * читается точно, запас ограничен остатком до границы.
     */
    fun loadRange(
        window: ChartWindow,
        nowMillis: Long,
        paddingFraction: Float = LOAD_PADDING_FRACTION,
        minPaddingMillis: Long = MIN_LOAD_PADDING_MILLIS,
    ): ChartWindow {
        val span = window.spanMillis
        // На коротком окне доля окна — это минуты, и запас кончался почти
        // сразу. Чтение по секундам стоит строки на секунду, поэтому там
        // выгоднее брать запас АБСОЛЮТНЫЙ: час вперёд и час назад с
        // пятиминутного окна — семь тысяч строк, то есть треть бюджета
        // точного пути, и час хода пальцем без единого запроса.
        val wanted = maxOf(
            (span * paddingFraction).toLong(),
            minPaddingMillis,
        )
        val exactLimit = QuantilePaths.EXACT_MAX_SPAN_MILLIS
        val pad = if (span >= exactLimit) {
            // Длинное окно и так на скетчах: там строка — это час, и лишний
            // запас стоит десятков строк, а не десятков тысяч.
            wanted
        } else {
            wanted.coerceAtMost((exactLimit - span) / 2)
        }.coerceAtLeast(0L)
        val to = minOf(window.toMillis + pad, nowMillis)
        return ChartWindow(window.fromMillis - pad, maxOf(to, window.toMillis))
    }

    /**
     * Окно, укороченное до фактически накопленной истории.
     *
     * ## Зачем
     *
     * Выбранная ступень — это МАКСИМУМ, а не обещание, что данные за неё есть.
     * Сразу после установки (или после чистки журнала) шестичасовое окно
     * рисовало пять с половиной пустых часов, а всё накопленное сжималось в
     * несколько пикселей у правого края. Хуже того, у дозы это меняло САМ ПУТЬ
     * ЧТЕНИЯ: окно длиннее шести часов уходит на почасовые скетчи (ADR 004), и
     * вся короткая история складывалась в ОДНУ часовую колонку — то есть в одну
     * точку, тогда как счёт и жёсткость, у которых длинного пути нет, честно
     * показывали ряд. Один и тот же поток измерений выглядел на трёх карточках
     * по-разному.
     *
     * Поэтому левый край подтягивается к первому измерению, а правый остаётся
     * «сейчас»: окно растёт вместе с историей и, дорастя до ступени,
     * превращается в скользящее.
     *
     * @param earliestMillis момент первого измерения; null — измерений нет,
     *   окно остаётся как выбрано (рисовать нечего в любом случае).
     * @param minSpanMillis нижняя граница: одно измерение не должно давать
     *   вырожденное окно нулевой ширины.
     */
    fun limitedByHistory(
        window: ChartWindow,
        earliestMillis: Long?,
        minSpanMillis: Long = MIN_HISTORY_SPAN_MILLIS,
    ): ChartWindow {
        if (earliestMillis == null || earliestMillis <= window.fromMillis) return window
        val from = minOf(earliestMillis, window.toMillis - minSpanMillis)
        return ChartWindow(from, window.toMillis)
    }

    /**
     * Самое узкое окно, которое имеет смысл рисовать.
     * **Инженерный параметр**: минута — первая ступень лестницы и шестьдесят
     * записей прибора; уже неё график перестаёт быть графиком.
     */
    const val MIN_HISTORY_SPAN_MILLIS = 60_000L

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
