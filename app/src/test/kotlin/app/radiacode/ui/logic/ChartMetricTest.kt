package app.radiacode.ui.logic

import app.radiacode.data.DoseUnitSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Полноэкранный график один на три величины, поэтому проверяется ровно то, чем
 * они отличаются: единицы, доступные окна и какие опорные линии на них имеют
 * смысл.
 */
class ChartMetricTest {

    private val unit = DoseUnitSetting.MICRO_SIEVERT

    @Test
    fun `ids round trip and an unknown id falls back to dose`() {
        for (metric in ChartMetric.entries) {
            assertEquals(metric, ChartMetric.of(metric.id))
        }
        assertEquals(ChartMetric.DOSE, ChartMetric.of("что-то другое"))
        assertEquals(ChartMetric.DOSE, ChartMetric.of(null))
    }

    @Test
    fun `each metric carries its own unit and format`() {
        assertEquals("мкЗв/ч", ChartMetrics.unitLabel(ChartMetric.DOSE, unit))
        assertEquals("с⁻¹", ChartMetrics.unitLabel(ChartMetric.COUNT_RATE, unit))
        assertEquals("(мкрем/ч)/(имп/с)", ChartMetrics.unitLabel(ChartMetric.HARDNESS, unit))

        assertEquals("0,16", ChartMetrics.format(ChartMetric.DOSE, 0.16f, unit))
        assertEquals("24,7", ChartMetrics.format(ChartMetric.COUNT_RATE, 24.7f, unit))
        assertEquals("0,52", ChartMetrics.format(ChartMetric.HARDNESS, 0.52f, unit))
    }

    @Test
    fun `only dose offers the windows the pre-aggregation was built for`() {
        val dose = ChartMetrics.periodIndices(ChartMetric.DOSE)
        assertEquals(ChartWindows.PERIODS.indices.toList(), dose)
        assertNull(ChartMetrics.spanLimitNote(ChartMetric.DOSE))

        for (metric in listOf(ChartMetric.COUNT_RATE, ChartMetric.HARDNESS)) {
            val periods = ChartMetrics.periodIndices(metric)
            assertTrue(periods.isNotEmpty(), "$metric")
            val longest = ChartWindows.PERIODS[periods.last()].second
            assertEquals(QuantilePaths.EXACT_MAX_SPAN_MILLIS, longest, "$metric")
            // …и почему их нет, сказано словами, а не отключённой кнопкой.
            val note = assertNotNull(ChartMetrics.spanLimitNote(metric))
            assertTrue(note.contains("предагрегация"), note)
        }
    }

    @Test
    fun `the alarm level and the profile band belong to dose only`() {
        assertTrue(ChartMetrics.showsAlarmLevel(ChartMetric.DOSE))
        assertTrue(ChartMetrics.showsProfileBand(ChartMetric.DOSE))
        for (metric in listOf(ChartMetric.COUNT_RATE, ChartMetric.HARDNESS)) {
            // Порог L1 задан в единицах дозы: переносить его на счёт или на
            // отношение было бы выдумкой.
            assertTrue(!ChartMetrics.showsAlarmLevel(metric), "$metric")
            assertTrue(!ChartMetrics.showsProfileBand(metric), "$metric")
        }
    }

    @Test
    fun `every anatomy line says the bands are spread, not instrument error`() {
        for (metric in ChartMetric.entries) {
            val text = ChartMetrics.anatomy(metric)
            assertTrue(text.contains("медиана"), text)
            assertTrue(text.contains("не погрешность") || text.contains("не мера опасности"), text)
        }
        // У счёта — обязательная оговорка про то, что это не мера опасности.
        assertTrue(
            ChartMetrics.anatomy(ChartMetric.COUNT_RATE).contains("не является мерой опасности"),
        )
        // У жёсткости — что отношение берётся по отсчётам, а не по средним.
        assertTrue(
            ChartMetrics.anatomy(ChartMetric.HARDNESS).contains("по каждому отсчёту"),
        )
    }
}

/** §2 ТЗ: неполное окно называется словами. */
class ChartCoverageTest {

    private fun stats(sampleCount: Int, spanMillis: Long) = WindowStats(
        min = 0.1f,
        p10 = 0.11f,
        q25 = 0.12f,
        median = 0.13f,
        q75 = 0.14f,
        p90 = 0.15f,
        max = 0.16f,
        mad = 0.01f,
        sd = 0.01f,
        sampleCount = sampleCount,
        spanMillis = spanMillis,
    )

    @Test
    fun `a fully covered window says nothing`() {
        val sixHours = 6 * 3600_000L
        assertNull(coverageWording(stats(6 * 3600, sixHours), sixHours))
        // Пара процентов пропусков BLE — это не «неполное окно».
        assertNull(coverageWording(stats((6 * 3600 * 0.97).toInt(), sixHours), sixHours))
    }

    @Test
    fun `a partly covered window says how much of it there is`() {
        val sixHours = 6 * 3600_000L
        val text = assertNotNull(coverageWording(stats(47 * 60, sixHours), sixHours))
        assertTrue(text.startsWith("данных: 47 мин"), text)
        assertTrue(text.contains("из 6 ч"), text)
    }

    @Test
    fun `no stats at all means no claim about coverage`() {
        assertNull(coverageWording(null, 3600_000L))
    }
}
