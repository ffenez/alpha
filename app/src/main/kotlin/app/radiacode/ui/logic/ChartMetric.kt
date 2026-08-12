package app.radiacode.ui.logic

import app.radiacode.analysis.Hardness
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
    fun spanLimitNote(metric: ChartMetric): String? = when (metric) {
        ChartMetric.DOSE -> null
        ChartMetric.COUNT_RATE, ChartMetric.HARDNESS ->
            "Окна длиннее ${label(QuantilePaths.EXACT_MAX_SPAN_MILLIS)} у этой величины пока " +
                "нет: предагрегация посчитана для мощности дозы, а перебирать всю сырую " +
                "историю на каждое открытие нельзя."
    }

    fun unitLabel(metric: ChartMetric, unit: DoseUnitSetting): String = when (metric) {
        ChartMetric.DOSE -> DoseFormat.rateUnitLabel(unit)
        ChartMetric.COUNT_RATE -> "с⁻¹"
        ChartMetric.HARDNESS -> "(мкрем/ч)/(имп/с)"
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

    /** Строка анатомии графика — та же для всех величин, с их единицами. */
    fun anatomy(metric: ChartMetric): String = when (metric) {
        ChartMetric.DOSE ->
            "Линия — медиана интервала. Полосы показывают наблюдаемый разброс значений, " +
                "а не погрешность прибора."
        ChartMetric.COUNT_RATE ->
            "Линия — медиана интервала. Полосы — наблюдаемый разброс счёта, не погрешность " +
                "прибора. Счёт событий детектора не является мерой опасности."
        ChartMetric.HARDNESS ->
            "Линия — медиана интервала. Отношение считается по каждому отсчёту, а не по " +
                "средним корзины. " + Hardness.EXPLANATION
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

    private fun label(spanMillis: Long): String =
        ChartWindows.PERIODS.lastOrNull { it.second <= spanMillis }?.first ?: "6ч"
}
