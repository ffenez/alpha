package app.alpha.analysis

import app.alpha.analysis.validation.SyntheticSpectra
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ряд нетто-счёта в окне линии — то, ради чего прибор носят: где цезия больше,
 * чем рядом, и растёт ли радон к утру.
 *
 * Проверяется на синтетических спектрах с ИЗВЕСТНОЙ площадью линии
 * ([SyntheticSpectra]), поэтому вопросы к ряду ставятся количественные, а не
 * «похоже на правду».
 */
class NuclideTrendTest {

    private val calibration = SyntheticSpectra.RC110_CALIBRATION
    private val now = 1_700_000_000_000L
    private val caesium = NuclideTrend.OFFERED.first { it.nuclide == "Cs-137" }

    /** Накопительные снимки: каждый следующий = предыдущий + интервал. */
    private fun accumulating(
        intervals: Int,
        netPerInterval: Double,
        seconds: Long = 600,
        seed: Long = 1L,
        energyKeV: Double = 661.7,
    ): List<NuclideTrend.Snapshot> {
        val out = mutableListOf<NuclideTrend.Snapshot>()
        var total = IntArray(1024)
        for (i in 0..intervals) {
            if (i > 0) {
                val slice = SyntheticSpectra.build(
                    lines = if (netPerInterval > 0) {
                        listOf(SyntheticSpectra.Line(energyKeV, netPerInterval))
                    } else {
                        emptyList()
                    },
                    calibration = calibration,
                    seed = seed + i,
                )
                total = IntArray(1024) { ch -> total[ch] + slice[ch] }
            }
            out += NuclideTrend.Snapshot(
                atMillis = now + i * seconds * 1000,
                durationSeconds = i * seconds,
                counts = total.toList(),
                calibration = calibration,
            )
        }
        return out
    }

    /**
     * Каждая предлагаемая линия обязана ДАВАТЬ ряд на шкале самого прибора.
     * Tl-208 2614,5 кэВ — самая заметная ториевая линия — не давала ни одной
     * точки: верхнее боковое окно континуума уходило за край 1024-канальной
     * шкалы, и расчёт отменялся целиком. Экран при этом писал «в выбранном
     * окне измерений не было» — ровно то же, что и при отсутствии данных,
     * так что отличить пустую историю от неработающей линии было нечем.
     */
    @Test
    fun `every offered line produces points on the instrument scale`() {
        for (line in NuclideTrend.OFFERED) {
            val points = NuclideTrend.series(
                accumulating(3, netPerInterval = 20_000.0, energyKeV = line.energyKeV.toDouble()),
                line,
            )
            assertEquals(3, points.size, "${line.label}: ряд пуст")
            val summary = NuclideTrend.summary(points)!!
            assertTrue(summary.resolved, "${line.label}: значимость ${summary.significance}")
        }
    }

    @Test
    fun `a line that is there shows up as a positive net rate`() {
        val points = NuclideTrend.series(accumulating(6, netPerInterval = 3_000.0), caesium)

        assertEquals(6, points.size)
        val summary = NuclideTrend.summary(points)!!
        assertTrue(summary.netCps > 0f, "нетто ${summary.netCps}")
        assertTrue(summary.resolved, "значимость ${summary.significance}")
        // 3000 импульсов за 600 с — единицы импульсов в секунду.
        assertTrue(summary.netCps in 1f..20f, "${summary.netCps}")
    }

    @Test
    fun `pure background does not pretend to carry the line`() {
        // Главное свойство: ряд не обязан быть положительным. Континуум под
        // окном оценивается по бокам, и без линии нетто колеблется около нуля.
        val summary = NuclideTrend.summary(
            NuclideTrend.series(accumulating(8, netPerInterval = 0.0), caesium),
        )!!

        assertTrue(!summary.resolved, "значимость ${summary.significance} на чистом фоне")
    }

    @Test
    fun `a stronger source gives a higher rate than a weaker one`() {
        // Сравнимость мест — единственное, ради чего этот ряд существует.
        val weak = NuclideTrend.summary(
            NuclideTrend.series(accumulating(6, netPerInterval = 1_500.0, seed = 20L), caesium),
        )!!
        val strong = NuclideTrend.summary(
            NuclideTrend.series(accumulating(6, netPerInterval = 6_000.0, seed = 20L), caesium),
        )!!

        assertTrue(strong.netCps > weak.netCps, "${strong.netCps} против ${weak.netCps}")
    }

    @Test
    fun `too short an interval is skipped, not shown as noise`() {
        val snapshots = accumulating(4, netPerInterval = 3_000.0, seconds = 10)

        assertTrue(NuclideTrend.series(snapshots, caesium).isEmpty())
    }

    @Test
    fun `a reset of the spectrum does not produce a negative point`() {
        // После сброса накопление начинается заново, и разность соседних
        // снимков стала бы отрицательной: такая пара пропускается.
        val normal = accumulating(3, netPerInterval = 3_000.0)
        val afterReset = normal + NuclideTrend.Snapshot(
            atMillis = normal.last().atMillis + 600_000,
            durationSeconds = 60,
            counts = List(1024) { 0 },
            calibration = calibration,
        )

        assertTrue(NuclideTrend.series(afterReset, caesium).all { it.netCps >= 0f })
    }

    @Test
    fun `the weighted mean listens to exposure`() {
        // Точка за десять минут знает больше, чем точка за минуту; складывать
        // их поровну значило бы портить оба числа.
        val long = NuclideTrend.Point(now, 600, netCps = 1f, sigmaCps = 0.1f, significance = 10f)
        val short = NuclideTrend.Point(now, 60, netCps = 5f, sigmaCps = 1f, significance = 5f)

        val mean = NuclideTrend.summary(listOf(long, short))!!.netCps

        assertTrue(mean < 2f, "среднее $mean уехало к короткой точке")
    }

    @Test
    fun `no snapshots, no summary`() {
        assertNull(NuclideTrend.summary(emptyList()))
    }
}
