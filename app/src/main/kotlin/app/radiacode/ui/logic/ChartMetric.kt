package app.radiacode.ui.logic

import app.radiacode.analysis.Hardness
import app.radiacode.ui.text.ChartAxisRu
import app.radiacode.ui.text.ChartAxisStrings
import app.radiacode.ui.text.RuStrings
import app.radiacode.ui.text.Strings
import app.radiacode.data.DoseUnitSetting

/**
 * Что именно рисует полноэкранный график.
 *
 * Экран один на все три величины намеренно: жесты, окна, конверты, курсор,
 * гистограмма и правила честности (разрывы не соединяются, экстремум не
 * растягивает конверт) не должны зависеть от того, какое число смотрят.
 * Различаются только источник данных, единицы и то, какие окна для величины
 * вообще посчитаны.
 */
enum class ChartMetric(val id: String, val title: String) {
    DOSE("dose", "Мощность дозы"),
    COUNT_RATE("cps", "Скорость счёта"),
    HARDNESS("hardness", "Жёсткость"),
    ;

    companion object {
        fun of(id: String?): ChartMetric = entries.firstOrNull { it.id == id } ?: DOSE
    }
}

/** Единицы, формат и границы применимости величины на графике. */
object ChartMetrics {

    /** Название величины на языке интерфейса. */
    fun title(metric: ChartMetric, s: Strings = RuStrings): String = when (metric) {
        ChartMetric.DOSE -> s.doseRate
        ChartMetric.COUNT_RATE -> s.countRate
        ChartMetric.HARDNESS -> s.hardness
    }


    /**
     * Самое длинное окно, которое величина умеет показать **честно**.
     *
     * У дозы есть предагрегация (ADR 004: минутные скаляры и почасовые
     * скетчи), поэтому ей доступны все окна вплоть до 30 дней. Счёт и
     * жёсткость такой предагрегации не имеют, а перебирать миллионы сырых
     * строк на каждое открытие запрещено спецификацией графика (§12) — им
     * доступны окна, которые точный путь читает целиком. Это ограничение
     * названо в интерфейсе словами, а не спрятано отключённой кнопкой без
     * объяснения.
     */
    fun maxSpanMillis(metric: ChartMetric): Long = when (metric) {
        ChartMetric.DOSE -> ChartWindows.MAX_SPAN_MILLIS
        ChartMetric.COUNT_RATE, ChartMetric.HARDNESS -> QuantilePaths.EXACT_MAX_SPAN_MILLIS
    }

    /** Индексы периодов [ChartWindows.PERIODS], доступные величине. */
    fun periodIndices(metric: ChartMetric): List<Int> {
        val limit = maxSpanMillis(metric)
        return ChartWindows.PERIODS.indices.filter { ChartWindows.PERIODS[it].second <= limit }
    }

    /** Почему длинных окон нет; null у величины, у которой они есть. */
    fun spanLimitNote(
        metric: ChartMetric,
        s: ChartAxisStrings = ChartAxisRu,
    ): String? = when (metric) {
        ChartMetric.DOSE -> null
        ChartMetric.COUNT_RATE, ChartMetric.HARDNESS ->
            s.longWindowsUnavailable(label(QuantilePaths.EXACT_MAX_SPAN_MILLIS, s))
    }

    fun unitLabel(
        metric: ChartMetric,
        unit: DoseUnitSetting,
        s: ChartAxisStrings = ChartAxisRu,
        units: Strings = RuStrings,
    ): String = when (metric) {
        ChartMetric.DOSE -> DoseFormat.rateUnitLabel(unit, units)
        ChartMetric.COUNT_RATE -> s.unitCountRate
        ChartMetric.HARDNESS -> s.unitHardness
    }

    /** Значение без единицы — для осей, курсора и статистики. */
    fun format(metric: ChartMetric, value: Float, unit: DoseUnitSetting): String = when (metric) {
        ChartMetric.DOSE -> DoseFormat.rate(value, unit)
        ChartMetric.COUNT_RATE -> Uncertainty.num1(value)
        ChartMetric.HARDNESS -> Hardness.format(value.toDouble())
    }

    fun formatWithUnit(metric: ChartMetric, value: Float, unit: DoseUnitSetting): String =
        "${format(metric, value, unit)} ${unitLabel(metric, unit)}"

    /**
     * Порог тревоги L1 рисуется только на дозе: он задан в единицах дозы, и
     * переносить его на счёт или на отношение было бы выдумкой.
     */
    fun showsAlarmLevel(metric: ChartMetric): Boolean = metric == ChartMetric.DOSE

    /**
     * Полоса обычного диапазона профиля тоже дозовая — у счёта своя полоса в
     * baseline, но она не показывается здесь, пока не будет посчитана тем же
     * путём (иначе на графике окажутся две статистики с разной родословной).
     */
    fun showsProfileBand(metric: ChartMetric): Boolean = metric == ChartMetric.DOSE

    /**
     * Самый узкий кадр, который величина имеет право показать.
     *
     * Автомасштаб подгоняет ось к наблюдаемым значениям, и без нижней границы
     * размаха практически постоянный фон растянулся бы на весь экран: шум
     * ±0,002 мкЗв/ч выглядел бы как размашистые скачки. Числа — **инженерные
     * параметры отображения**, а не свойства прибора: примерно шаг, ниже
     * которого разница уже не значима для глаза (доза — 0,04 мкЗв/ч, счёт —
     * около одной пуассоновской σ секундного отсчёта фона, жёсткость — 0,05).
     */
    fun minAxisSpan(metric: ChartMetric): Float = when (metric) {
        ChartMetric.DOSE -> 0.04f
        ChartMetric.COUNT_RATE -> 5f
        ChartMetric.HARDNESS -> 0.05f
    }

    /**
     * Постоянные оговорки под величиной на карточке Главной.
     *
     * Они не про то, как устроен график (это уехало в справку по кнопке «i»),
     * а про то, чем является само число: их нельзя показать один раз и убрать.
     */
    fun footnotes(
        metric: ChartMetric,
        s: ChartAxisStrings = ChartAxisRu,
    ): List<String> = when (metric) {
        ChartMetric.DOSE -> emptyList()
        ChartMetric.COUNT_RATE -> listOf(s.cpsFootnote)
        ChartMetric.HARDNESS -> listOf(Hardness.EXPLANATION, Hardness.PURPOSE)
    }

    /**
     * Окно, с которого величина открывается, — общее для карточки Главной и
     * для полноэкранного графика.
     *
     * Человек однажды выбрал окно на полноэкранном; карточка обязана
     * показывать ровно его, иначе тап по карточке не увеличивает картинку, а
     * подменяет её другой.
     */
    fun startWindow(
        metric: ChartMetric,
        savedSpans: Map<String, Long>,
        nowMillis: Long,
    ): ChartWindow {
        val periods = periodIndices(metric)
        val default = periods.lastOrNull { it <= ChartWindows.DEFAULT_PERIOD_INDEX } ?: 0
        val span = (savedSpans[metric.id] ?: ChartWindows.PERIODS[default].second)
            .coerceAtMost(maxSpanMillis(metric))
        return ChartWindows.latest(span, nowMillis)
    }

    private fun label(spanMillis: Long, s: ChartAxisStrings): String =
        ChartWindows.STEPS.lastOrNull { it.millis <= spanMillis }
            ?.let { ChartWindows.stepLabel(it, s) }
            ?: ChartWindows.stepLabel(ChartWindows.STEPS[ChartWindows.DEFAULT_PERIOD_INDEX], s)
}
