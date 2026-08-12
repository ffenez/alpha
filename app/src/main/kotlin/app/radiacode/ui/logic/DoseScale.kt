package app.radiacode.ui.logic

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Value → plot-fraction mapping of the dose axis, linear or logarithmic.
 *
 * Fraction 0 is the bottom of the plot, 1 the top. [fractionOrNull] is the
 * honest mapping: on a log scale a zero or negative value has **no** position
 * — it returns null and the chart draws a gap there instead of inventing a
 * floor. [DoseScales.logDroppedBuckets] counts those buckets so the screen can
 * say out loud how many were left out (SPEC §2: never present a computed
 * substitute as the measurement).
 *
 * Pure JVM, tested.
 */
sealed interface DoseScale {

    /** Bottom of the frame, µSv/h. */
    val minValue: Float

    /** Top of the frame, µSv/h. */
    val maxValue: Float

    val logarithmic: Boolean

    /** Position of [value], or null when this scale cannot show it honestly. */
    fun fractionOrNull(value: Float): Float?

    /** Gridline values inside the frame, ascending, «nice» steps. */
    fun ticks(): List<Float>
}

/**
 * Линейная шкала с ОБЕИМИ границами.
 *
 * Раньше низ был жёстко нулевым, и это делало график нечитаемым ровно в самом
 * частом случае: фон 0,14–0,18 мкЗв/ч на оси 0…0,30 занимал полосу в шестую
 * часть высоты, а весь остальной экран показывал пустоту между нулём и фоном —
 * область, в которой прибор не бывает. Ноль на оси мощности дозы не несёт
 * смысла «начала отсчёта»: интересен не сам уровень, а его ИЗМЕНЕНИЯ, и кадр
 * обязан быть подогнан к ним.
 *
 * Нулю по-прежнему разрешено быть низом кадра — когда данные к нему подходят
 * ([DoseScales] не опускает низ ниже нуля).
 */
data class LinearDoseScale(
    override val maxValue: Float,
    override val minValue: Float = 0f,
) : DoseScale {
    override val logarithmic: Boolean get() = false

    private val span: Float get() = maxValue - minValue

    override fun fractionOrNull(value: Float): Float? {
        if (span <= 0f) return null
        return ((value - minValue) / span).coerceIn(0f, 1f)
    }

    override fun ticks(): List<Float> {
        if (minValue <= 0f) return ChartMapping.yTicks(maxValue)
        if (span <= 0f) return emptyList()
        val step = ChartMapping.niceStep(span / TICK_COUNT.toDouble())
        val ticks = mutableListOf<Float>()
        var v = kotlin.math.ceil(minValue / step) * step
        // Края не подписываются: подпись у самой кромки поля обрезается и
        // читается как значение соседней линии.
        while (v < maxValue - span * EDGE_MARGIN) {
            if (v > minValue + span * EDGE_MARGIN) ticks += v.toFloat()
            v += step
        }
        return ticks
    }

    private companion object {
        const val TICK_COUNT = 4
        const val EDGE_MARGIN = 0.04f
    }
}

/**
 * Decade scale for wide-dynamic-range windows (7–30 days): a factor-of-ten
 * excursion no longer flattens the ordinary background into a line. Bounds
 * are whole decades so the gridlines are 1/2/5·10^k.
 */
data class LogDoseScale(
    override val minValue: Float,
    override val maxValue: Float,
) : DoseScale {
    override val logarithmic: Boolean get() = true

    private val logMin = log10(minValue.toDouble())
    private val logSpan = log10(maxValue.toDouble()) - logMin

    override fun fractionOrNull(value: Float): Float? {
        if (value <= 0f || logSpan <= 0.0) return null
        return ((log10(value.toDouble()) - logMin) / logSpan).toFloat().coerceIn(0f, 1f)
    }

    override fun ticks(): List<Float> {
        val result = mutableListOf<Float>()
        var decade = floor(logMin).toInt()
        val top = maxValue
        while (decade <= ceil(logMin + logSpan).toInt()) {
            val base = 10.0.pow(decade)
            for (mantissa in MANTISSAS) {
                val v = (base * mantissa).toFloat()
                if (v > minValue && v < top * 0.999f) result += v
            }
            decade++
        }
        return result
    }

    private companion object {
        val MANTISSAS = listOf(1.0, 2.0, 5.0)
    }
}

object DoseScales {

    /** Log frames never go below this — 1 nSv/h is far under any real reading. */
    const val LOG_FLOOR_MICRO_SV_H = 0.001f

    /**
     * Кадр по наблюдаемым данным (robust autoscale).
     *
     * ## Почему не min/max и не ноль
     *
     * Ноль внизу превращал обычный фон в узкую полоску у верхнего края, а
     * буквальные min/max отдали бы масштаб одному выбросу: один всплеск
     * сжимал бы весь остальной ряд в линию. Поэтому кадр строится по
     * **порядковым статистикам колонок**: снизу — [ROBUST_LOW]-квантиль их
     * нижних границ (Q10), сверху — [ROBUST_HIGH]-квантиль верхних (Q90).
     * Выброс остаётся ВИДЕН — его несёт маркер над полем и карточка курсора
     * (CHART SPEC §7), — но кадр он больше не определяет.
     *
     * ## Пороги не растягивают кадр
     *
     * Порог тревоги и полоса профиля добавляются в кадр, только если они
     * РЯДОМ с данными (в пределах [NEAR_FRACTION] размаха). Далёкий L1 = 0,30
     * при фоне 0,15 растягивал ось вдвое и делал сам фон нечитаемым; вместо
     * этого график рисует у верхней кромки указатель «↑ L1 0,30» — тревога
     * не теряется, а обычные измерения остаются видны.
     *
     * ## Минимальный размах
     *
     * [minSpan] (и доля [MIN_SPAN_FRACTION] от центра кадра) не дают
     * практически постоянному фону растянуться на весь экран: без него шум
     * ±0,002 мкЗв/ч выглядел бы как размашистые «скачки».
     *
     * @param lows нижние границы колонок (Q10), @param highs верхние (Q90).
     */
    fun of(
        logarithmic: Boolean,
        lows: List<Float>,
        highs: List<Float>,
        minSpan: Float,
        alarmLevel: Float? = null,
        baselineBand: ClosedFloatingPointRange<Float>? = null,
    ): DoseScale {
        val low = percentile(lows, ROBUST_LOW)
        val high = percentile(highs, ROBUST_HIGH)?.let { maxOf(it, low ?: it) }
        if (low == null || high == null) {
            // Данных нет: кадр строится вокруг того, что вообще известно —
            // порога и полосы профиля, — либо остаётся минимальным.
            val levels = listOfNotNull(
                alarmLevel,
                baselineBand?.start,
                baselineBand?.endInclusive,
            )
            val top = maxOf(levels.maxOrNull() ?: minSpan, minSpan)
            return if (logarithmic) {
                LogDoseScale(decadeBelow(maxOf(top / 100f, LOG_FLOOR_MICRO_SV_H)), decadeAbove(top))
            } else {
                LinearDoseScale(top * (1f + PAD_FRACTION))
            }
        }

        val dataLow: Float = low
        val dataHigh: Float = high
        val near = maxOf((dataHigh - dataLow) * NEAR_FRACTION, minSpan * NEAR_FRACTION)
        var bottom: Float = dataLow
        var top: Float = dataHigh
        for (level in listOfNotNull(alarmLevel, baselineBand?.start, baselineBand?.endInclusive)) {
            if (level in (dataLow - near)..(dataHigh + near)) {
                bottom = minOf(bottom, level)
                top = maxOf(top, level)
            }
        }

        if (logarithmic) {
            val positiveMin = if (bottom > 0f) bottom else top / 100f
            val floorValue = decadeBelow(maxOf(positiveMin, LOG_FLOOR_MICRO_SV_H))
            return LogDoseScale(floorValue, decadeAbove(maxOf(top, floorValue * 10f)))
        }

        val pad = (top - bottom) * PAD_FRACTION
        var frameLow = bottom - pad
        var frameHigh = top + pad
        val floorSpan = maxOf(minSpan, (frameLow + frameHigh) / 2f * MIN_SPAN_FRACTION)
        if (frameHigh - frameLow < floorSpan) {
            val centre = (frameHigh + frameLow) / 2f
            frameLow = centre - floorSpan / 2f
            frameHigh = centre + floorSpan / 2f
        }
        // Отрицательных мощности дозы и счёта не бывает — ниже нуля кадр не
        // опускается, но и не «доворачивается» вверх: расширение кадра всегда
        // безопаснее сдвига.
        if (frameLow < 0f) frameLow = 0f
        return LinearDoseScale(maxValue = frameHigh, minValue = frameLow)
    }

    /** Порядковая статистика по правилу ближайшего ранга; null на пустом ряде. */
    private fun percentile(values: List<Float>, p: Float): Float? {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return null
        val sorted = finite.sorted()
        val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    /** Нижняя граница кадра — 2-й процентиль нижних границ колонок. */
    const val ROBUST_LOW = 0.02f

    /** Верхняя граница кадра — 98-й процентиль верхних границ колонок. */
    const val ROBUST_HIGH = 0.98f

    /** Поля кадра сверху и снизу — доля от размаха данных. */
    const val PAD_FRACTION = 0.15f

    /** Насколько близко к данным должен быть порог, чтобы войти в кадр. */
    const val NEAR_FRACTION = 0.5f

    /** Кадр не уже этой доли собственного центра. */
    const val MIN_SPAN_FRACTION = 0.15f

    /**
     * How many present buckets a log scale cannot place (value ≤ 0). The UI
     * must show this number instead of silently dropping data.
     */
    fun logDroppedBuckets(buckets: List<ChartBucket?>): Int =
        buckets.count { it != null && it.median <= 0f }

    private fun decadeBelow(value: Float): Float =
        10.0.pow(floor(log10(value.toDouble()))).toFloat()

    private fun decadeAbove(value: Float): Float =
        10.0.pow(ceil(log10(value.toDouble()))).toFloat()
}
