package app.alpha.ui.logic

import app.alpha.analysis.NuclideTrend
import app.alpha.analysis.RadonTrend
import app.alpha.ui.screens.hourColumns
import app.alpha.ui.text.SessionRadonEn
import app.alpha.ui.text.SessionRadonRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Главный ответ аналитических экранов — категория, а числа под ней.
 *
 * Проверяется ровно то, что было сломано: отрицательный остаток вычитания
 * («−0,29», «значимость −12,6») в роли результата, отсутствие данных под видом
 * нуля и часовая сетка, в которой пропущенный час превращался в соседний
 * столбик.
 */
class AnalyticsReportsTest {

    private val line = NuclideTrend.OFFERED.first { it.nuclide == "Cs-137" }

    private fun summary(net: Float, sigma: Float, points: Int = 13) = NuclideTrend.Summary(
        netCps = net,
        sigmaCps = sigma,
        significance = if (sigma > 0f) net / sigma else 0f,
        points = points,
        seconds = points * 3600L,
    )

    @Test
    fun `a negative net is not a result, it is «the line does not stand out»`() {
        val report = LineTrendReport.build(line, summary(-0.02f, 0.15f), "11 ч", SessionRadonRu)

        assertEquals(SessionRadonRu.lineResultPlain, report.verdict)
        assertEquals(ResultTone.PLAIN, report.tone)
        // Число со знаком не потеряно — оно на техническом уровне.
        assertTrue(report.details.any { it.value.contains("-0,02") || it.value.contains("−0,02") })
        assertTrue(report.details.any { it.label == SessionRadonRu.detailSignificance })
        // …и на первом уровне его нет.
        assertTrue(!report.verdict.contains("0,02"), report.verdict)
        assertTrue(!report.meaning.contains("σ"), report.meaning)
    }

    @Test
    fun `a resolved excess says so and keeps the same numbers underneath`() {
        val report = LineTrendReport.build(line, summary(1.2f, 0.2f), "11 ч", SessionRadonRu)

        assertEquals(SessionRadonRu.lineResultExcess, report.verdict)
        assertEquals(ResultTone.NOTABLE, report.tone)
        assertTrue(report.details.any { it.label == SessionRadonRu.detailNet })
    }

    @Test
    fun `no data is its own answer and offers no numbers to read`() {
        val report = LineTrendReport.build(line, null, null, SessionRadonRu)

        assertEquals(SessionRadonRu.lineResultNoData, report.verdict)
        assertEquals(ResultTone.UNKNOWN, report.tone)
        assertNull(report.measurement)
        assertTrue(report.details.isEmpty(), "${report.details}")
    }

    /** «13 часовых интервалов · охват 11 ч» — сколько измерено и за какой срок. */
    @Test
    fun `the volume of the measurement names its unit`() {
        val report = LineTrendReport.build(line, summary(0.1f, 0.5f), "11 ч", SessionRadonRu)
        val measurement = report.measurement.orEmpty()

        assertTrue(measurement.contains("13"), measurement)
        assertTrue(measurement.contains("часов"), measurement)
        assertTrue(measurement.contains("11 ч"), measurement)
        // Русское число согласовано: «1 часовой интервал», а не «1 часовых».
        assertEquals("1 часовой интервал", SessionRadonRu.hourIntervals(1))
        assertEquals("2 часовых интервала", SessionRadonRu.hourIntervals(2))
        assertEquals("11 часовых интервалов", SessionRadonRu.hourIntervals(11))
        assertEquals("21 часовой интервал", SessionRadonRu.hourIntervals(21))
    }

    /** Радон не измеряет концентрацию, и это стоит в самой карточке вывода. */
    @Test
    fun `the radon card refuses becquerels where the answer is`() {
        val point = RadonTrend.HourPoint(
            hourStartMillis = 0L,
            rateCps = -0.29f,
            sigmaCps = 0.11f,
            seconds = 3600L,
        )
        val report = RadonReport.build(point, median = -0.31f, hours = 13, spanText = "11 ч", t = SessionRadonRu)

        assertEquals(SessionRadonRu.radonResultPlain, report.verdict)
        assertTrue(report.limitation.contains("Бк/м"), report.limitation)
        assertTrue(!report.verdict.contains("0,29"), report.verdict)
        assertTrue(report.details.any { it.value.contains("0,29") }, "${report.details}")
    }

    @Test
    fun `an English report carries the same structure`() {
        val report = LineTrendReport.build(line, summary(1.2f, 0.2f), "11 h", SessionRadonEn)

        assertEquals(SessionRadonEn.lineResultExcess, report.verdict)
        assertTrue(report.measurement.orEmpty().contains("13 hourly intervals"))
    }

    /**
     * Час без измерений — настоящий пробел. Нарисованные подряд, часовые точки
     * склеивали разрыв: отсутствие измерения становилось похоже на измерение.
     */
    @Test
    fun `an hour without measurements stays a hole in the picture`() {
        val hour = RadonTrend.HOUR_MILLIS
        val points = listOf(0L, 1L, 4L).map { h ->
            NuclideTrend.Point(
                atMillis = h * hour + 60_000L,
                seconds = 3600L,
                netCps = 1f + h,
                sigmaCps = 0.1f,
                significance = 10f,
            )
        }

        val columns = hourColumns(points)

        assertEquals(5, columns.size)
        assertEquals(listOf(1f, 2f, null, null, 5f), columns)
    }

    @Test
    fun `an empty series draws nothing at all`() {
        assertEquals(emptyList(), hourColumns(emptyList()))
    }
}
