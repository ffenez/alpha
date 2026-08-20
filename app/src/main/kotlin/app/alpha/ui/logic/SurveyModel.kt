package app.alpha.ui.logic

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.Radioelements
import app.alpha.data.db.SurveyStationEntity
import kotlin.math.sqrt

/**
 * Радиоэлементная съёмка: станции, их линии и сравнение между собой.
 *
 * Главная величина съёмки — не абсолютный счёт, а ОТНОШЕНИЯ и отклонение
 * станции от остальных. Абсолюты зависят от того, как держали прибор, сколько
 * стояли и какая была погода; отношения и сравнение с собственной медианой
 * съёмки от этого свободны.
 *
 * Проценты калия и ppm тория здесь не появляются ни при каких условиях: для
 * них нужны эталонные калибровочные площадки.
 */
object SurveyModel {

    /** Одна станция с разобранным спектром. */
    data class Station(
        val entity: SurveyStationEntity,
        val measures: List<Radioelements.Measure>,
        /** Модель прибора, чьим разрешением считались окна. */
        val deviceName: String?,
        /**
         * Подогнан ли профиль под этот прибор: у 101 и 102 разрешение не
         * опубликовано, и окна взяты с запасом серии.
         */
        val tunedProfile: Boolean = true,
    ) {
        fun measure(element: Radioelements.Element): Radioelements.Measure? =
            measures.firstOrNull { it.element == element }

        val potassium get() = measure(Radioelements.Element.K)
        val uranium get() = measure(Radioelements.Element.U)
        val thorium get() = measure(Radioelements.Element.TH)

        /** eU/eTh — отношение, по которому отличают гидротермальные зоны. */
        val uraniumToThorium: Radioelements.Ratio?
            get() = Radioelements.ratio(uranium, thorium)

        /** eTh/K — отношение, по которому отличают породы. */
        val thoriumToPotassium: Radioelements.Ratio?
            get() = Radioelements.ratio(thorium, potassium)

        /** Все три линии набраны выше предела: станция полноценная. */
        val complete: Boolean
            get() = measures.size == 3 && measures.all { it.detected }

        val seconds: Long get() = measures.firstOrNull()?.seconds ?: 0L
    }

    /**
     * Величина, по которой раскрашивают станции.
     *
     * Отношения стоят рядом с элементами не для симметрии: зоны выделяют
     * именно они, а элементы отвечают на вопрос «сколько здесь всего».
     */
    enum class Quantity { K, U, TH, U_TH, TH_K }

    fun value(station: Station, quantity: Quantity): Float? = when (quantity) {
        Quantity.K -> station.potassium?.takeIf { it.detected }?.cps
        Quantity.U -> station.uranium?.takeIf { it.detected }?.cps
        Quantity.TH -> station.thorium?.takeIf { it.detected }?.cps
        Quantity.U_TH -> station.uraniumToThorium?.value
        Quantity.TH_K -> station.thoriumToPotassium?.value
    }

    /** Неопределённость той же величины, в тех же единицах. */
    fun sigma(station: Station, quantity: Quantity): Float? = when (quantity) {
        Quantity.K -> station.potassium?.takeIf { it.detected }?.cpsSigma
        Quantity.U -> station.uranium?.takeIf { it.detected }?.cpsSigma
        Quantity.TH -> station.thorium?.takeIf { it.detected }?.cpsSigma
        Quantity.U_TH -> station.uraniumToThorium?.sigma
        Quantity.TH_K -> station.thoriumToPotassium?.sigma
    }

    /**
     * Отклонение станции от медианы съёмки, выраженное в собственных σ.
     *
     * Медиана берётся по ОСТАЛЬНЫМ станциям: сравнивать станцию с набором, в
     * который она входит, значит сравнивать её отчасти с самой собой — на
     * десятке точек это заметно смещает вывод.
     *
     * @return null, когда сравнивать не с чем: меньше [MIN_STATIONS] соседей
     *   или величина на этой станции не измерена.
     */
    fun deviation(
        station: Station,
        others: List<Station>,
        quantity: Quantity,
    ): Deviation? {
        val value = value(station, quantity) ?: return null
        val sigma = sigma(station, quantity) ?: return null
        val neighbours = others
            .filter { it.entity.id != station.entity.id }
            .mapNotNull { value(it, quantity) }
        if (neighbours.size < MIN_STATIONS) return null
        val median = median(neighbours) ?: return null
        if (median <= 0f) return null
        // Разброс самой съёмки нужен наравне с σ станции: если все станции
        // расходятся между собой, отличие одной из них ничего не выделяет.
        val spread = medianAbsoluteDeviation(neighbours, median)
        val combined = sqrt(sigma * sigma + spread * spread)
        if (combined <= 0f) return null
        return Deviation(
            ratioToMedian = value / median,
            sigmas = (value - median) / combined,
            median = median,
            spread = spread,
            neighbours = neighbours.size,
        )
    }

    /**
     * @param sigmas во сколько σ станция уходит от медианы: знак — направление.
     * @param spread разброс съёмки (MAD), в тех же единицах, что величина.
     */
    data class Deviation(
        val ratioToMedian: Float,
        val sigmas: Float,
        val median: Float,
        val spread: Float,
        val neighbours: Int,
    ) {
        /** Отличие принято: |σ| выше порога. */
        val notable: Boolean get() = kotlin.math.abs(sigmas) >= NOTABLE_SIGMAS

        val above: Boolean get() = sigmas > 0f
    }

    /**
     * Доля величины в диапазоне съёмки — то, чем красят точку на карте.
     *
     * Диапазон берётся УСТОЙЧИВЫЙ: медиана ± [SPREAD_SIGMAS]·MAD, а не крайние
     * значения и не перцентили. Причина измеренная: на десятке станций
     * перцентили почти не обрезают, и одна выброшенная точка сжимала все
     * остальные в один цвет — обычная станция получала 0,005 вместо середины
     * шкалы. Выброс при этом не теряется: он упирается в край шкалы.
     */
    fun normalize(values: List<Float>, value: Float): Float? {
        if (values.size < MIN_STATIONS) return null
        val median = median(values) ?: return null
        val spread = medianAbsoluteDeviation(values, median)
        val half = if (spread > 0f) {
            SPREAD_SIGMAS * spread
        } else {
            // Все станции одинаковы: сравнивать нечего, кроме самих крайних.
            val range = (values.max() - values.min()) / 2f
            if (range > 0f) range else return null
        }
        return ((value - (median - half)) / (2f * half)).coerceIn(0f, 1f)
    }

    /** Разбор спектра станции окнами ЕЁ прибора. */
    fun station(
        entity: SurveyStationEntity,
        counts: List<Int>,
        calibration: EnergyCalibration,
        seconds: Long,
        resolution662: Float,
        stripping: Radioelements.Stripping = Radioelements.Stripping.NONE,
        deviceName: String? = null,
        tunedProfile: Boolean = true,
    ): Station = Station(
        entity = entity,
        measures = Radioelements.strip(
            Radioelements.measure(counts, calibration, seconds, resolution662),
            stripping,
        ),
        deviceName = deviceName,
        tunedProfile = tunedProfile,
    )

    private fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }

    /** MAD, приведённая к σ нормального распределения (×1,4826). */
    private fun medianAbsoluteDeviation(values: List<Float>, median: Float): Float {
        val deviations = values.map { kotlin.math.abs(it - median) }
        return (median(deviations) ?: 0f) * MAD_TO_SIGMA
    }

    /** Меньше трёх соседей — это не съёмка, а отдельные измерения. */
    const val MIN_STATIONS = 3

    /** Порог «станция отличается»: тот же 1,645σ, что у пределов Карри. */
    const val NOTABLE_SIGMAS = 1.645f

    private const val MAD_TO_SIGMA = 1.4826f

    /** Полуширина цветовой шкалы в устойчивых σ съёмки. */
    private const val SPREAD_SIGMAS = 2f
}
