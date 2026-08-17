package app.alpha.ui.logic

import java.util.concurrent.TimeUnit

/** Промежуток без измерений: рисуется штриховкой, а не пустотой. */
data class DataGap(val fromMillis: Long, val toMillis: Long) {
    val spanMillis: Long get() = toMillis - fromMillis
}

/** Полоса зебры — один час или одни сутки, чередующиеся по времени. */
data class TimeBand(val fromMillis: Long, val toMillis: Long, val shaded: Boolean)

/**
 * Фон поля графика, который **несёт данные**, а не украшает.
 *
 * Три слоя, и каждый отвечает на вопрос, который иначе остаётся без ответа:
 *
 *  - **Пропуски** ([gaps]) — «прибор молчал» против «уровень был низкий». На
 *    пустом поле эти две ситуации выглядят одинаково, а это самая опасная
 *    неоднозначность на графике измерений.
 *  - **До начала истории** ([historyStart]) — «сюда данные не доходят» против
 *    «здесь был ноль». Панорама в прошлое иначе упирается в пустоту, которую
 *    легко прочитать как измеренный ноль.
 *  - **Зебра времени** ([bands]) — на суточных и недельных окнах глазу нужна
 *    опора, чтобы отличить вечер от утра без чтения подписей.
 *
 * Всё это чистая арифметика по уже загруженным корзинам: рисование ничего не
 * решает, а тесты проверяют границы без экрана.
 */
object ChartBackground {

    /**
     * Промежутки без измерений внутри окна.
     *
     * Корзина считается пустой, если в ней нет ни одного отсчёта. Соседние
     * пустые корзины сливаются в один промежуток, а слишком короткие
     * ([minGapMillis]) отбрасываются: одна пропущенная секунда на 30-дневном
     * окне — это не пропуск данных, а обычная жизнь потока.
     */
    fun gaps(
        buckets: List<ChartBucket>,
        fromMillis: Long,
        toMillis: Long,
        bucketMillis: Long,
        minGapMillis: Long = minGapFor(toMillis - fromMillis),
    ): List<DataGap> {
        if (bucketMillis <= 0L || toMillis <= fromMillis) return emptyList()
        val present = buckets.filter { it.sampleCount > 0 }
            .map { it.startMillis }
            .toHashSet()
        val result = ArrayList<DataGap>()
        var gapStart: Long? = null
        var slot = alignDown(fromMillis, bucketMillis)
        while (slot < toMillis) {
            val empty = slot !in present
            if (empty && gapStart == null) gapStart = slot
            if (!empty && gapStart != null) {
                addGap(result, gapStart, slot, fromMillis, toMillis, minGapMillis)
                gapStart = null
            }
            slot += bucketMillis
        }
        gapStart?.let { addGap(result, it, slot, fromMillis, toMillis, minGapMillis) }
        return result
    }

    private fun addGap(
        into: MutableList<DataGap>,
        startMillis: Long,
        endMillis: Long,
        fromMillis: Long,
        toMillis: Long,
        minGapMillis: Long,
    ) {
        val from = maxOf(startMillis, fromMillis)
        val to = minOf(endMillis, toMillis)
        if (to - from >= minGapMillis) into += DataGap(from, to)
    }

    /**
     * Ниже этой длительности пропуск не рисуется.
     *
     * **Инженерный параметр**: одна сотая окна. На 15 минутах это 9 секунд —
     * заметная дыра; на 30 днях 7 часов — тоже заметная. Порог, привязанный к
     * окну, а не к абсолютному времени, держит фон одинаково спокойным на
     * любом масштабе.
     */
    fun minGapFor(spanMillis: Long): Long = (spanMillis / 100L).coerceAtLeast(1_000L)

    /**
     * Часть окна левее первого измерения вообще: сюда история не доходит.
     * Null, когда окно целиком внутри истории.
     */
    fun historyStart(
        earliestSampleMillis: Long?,
        fromMillis: Long,
        toMillis: Long,
    ): DataGap? {
        if (earliestSampleMillis == null) return DataGap(fromMillis, toMillis)
        if (earliestSampleMillis <= fromMillis) return null
        return DataGap(fromMillis, minOf(earliestSampleMillis, toMillis))
    }

    /**
     * Зебра времени: чередующиеся полосы часов или суток.
     *
     * Появляется только на длинных окнах ([MIN_ZEBRA_SPAN_MILLIS]) — на
     * пятиминутном окне это был бы шум, а не опора. Шаг выбирается так, чтобы
     * полос было немного: часы, пока их меньше [MAX_BANDS], дальше сутки.
     * Затенена каждая вторая полоса, считая от эпохи, поэтому картинка не
     * дёргается при прокрутке: полоса привязана к стенным часам, а не к краю
     * экрана.
     */
    fun bands(fromMillis: Long, toMillis: Long): List<TimeBand> {
        val span = toMillis - fromMillis
        if (span < MIN_ZEBRA_SPAN_MILLIS) return emptyList()
        val step = when {
            span / HOUR <= MAX_BANDS -> HOUR
            span / DAY <= MAX_BANDS -> DAY
            else -> return emptyList()
        }
        val result = ArrayList<TimeBand>()
        var start = alignDown(fromMillis, step)
        while (start < toMillis) {
            val end = start + step
            result += TimeBand(
                fromMillis = maxOf(start, fromMillis),
                toMillis = minOf(end, toMillis),
                shaded = (start / step) % 2L == 0L,
            )
            start = end
        }
        return result
    }

    /** Ниже этого окна зебра не рисуется. **Инженерный параметр.** */
    const val MIN_ZEBRA_SPAN_MILLIS = 6L * 3_600_000L

    /** Больше этого числа полос зебра перестаёт быть опорой. **Инженерный.** */
    const val MAX_BANDS = 40L

    private val HOUR = TimeUnit.HOURS.toMillis(1)
    private val DAY = TimeUnit.DAYS.toMillis(1)

    private fun alignDown(millis: Long, step: Long): Long =
        if (step <= 0L) millis else Math.floorDiv(millis, step) * step
}
