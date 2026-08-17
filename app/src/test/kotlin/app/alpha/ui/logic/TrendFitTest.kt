package app.alpha.ui.logic

import app.alpha.data.DoseUnitSetting
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendFitTest {

    private val minute = 60_000L

    /** A line of [n] one-minute bins rising by [perBucket] µSv/h per bin. */
    private fun line(n: Int, start: Float = 0.10f, perBucket: Float = 0.01f) =
        List(n) { start + perBucket * it }

    @Test
    fun `flat series has zero slope`() {
        val slope = TrendFit.slopePerHour(List(20) { 0.11f }, bucketMillis = minute)!!
        assertTrue(abs(slope) < 1e-6f)
    }

    @Test
    fun `linear rise recovers the exact per-hour slope`() {
        // +0.01 per bucket, bucket = 1 min -> +0.6/h.
        val slope = TrendFit.slopePerHour(line(20), bucketMillis = minute)!!
        assertTrue(abs(slope - 0.6f) < 1e-4f, "slope was $slope")
    }

    @Test
    fun `a single spike moves OLS but not Theil-Sen`() {
        val columns = line(20).toMutableList()
        columns[17] = columns[17] + 5f
        val theilSen = TrendFit.slopePerHour(columns, minute)!!
        val ols = TrendFit.researchOlsSlopePerHour(columns, minute)!!
        // Theil–Sen: 19 of the 190 pairwise slopes touch the spike; the median
        // of the rest is still the line.
        assertTrue(abs(theilSen - 0.6f) < 1e-3f, "Theil–Sen moved: $theilSen")
        // Least squares is dragged by several µSv/h per hour by that one bin.
        assertTrue(ols > 3f, "OLS should have been dragged, was $ols")
    }

    @Test
    fun `gaps are skipped, not interpolated`() {
        // Present bins at 0, 2, 4 ... minutes: real timestamps, same 0.6/h.
        val columns = (0 until 30).map { if (it % 2 == 0) 0.10f + 0.005f * it else null }
        val slope = TrendFit.slopePerHour(columns, bucketMillis = minute)!!
        assertTrue(abs(slope - 0.3f) < 1e-4f, "slope was $slope")
    }

    @Test
    fun `too few present bins is honest null`() {
        val columns = line(TrendFit.MIN_PRESENT_BINS - 1)
        assertNull(TrendFit.slopePerHour(columns, minute))
        assertNull(TrendFit.slopePerHour(listOf(null, 0.1f, null), minute))
        assertNull(TrendFit.slopePerHour(emptyList(), minute))
    }

    @Test
    fun `too short a time span is honest null`() {
        // 20 bins of 10 s = 190 s of span, far below the 10 min minimum.
        assertNull(TrendFit.slopePerHour(line(20), bucketMillis = 10_000L))
        // The same bins over minutes are enough.
        assertNotNull(TrendFit.slopePerHour(line(20), bucketMillis = minute))
    }

    @Test
    fun `the availability rule is exactly the documented one`() {
        val enough = TrendFit.fit(line(TrendFit.MIN_PRESENT_BINS), minute)
        assertNotNull(enough)
        assertEquals(TrendFit.MIN_PRESENT_BINS, enough.presentBins)
        assertEquals(TrendMethod.THEIL_SEN, enough.method)
        val n = TrendFit.MIN_PRESENT_BINS.toLong()
        assertEquals(n * (n - 1) / 2, enough.pairCount)
        assertTrue(enough.spanMillis >= TrendFit.MIN_SPAN_MILLIS)
    }

    @Test
    fun `large windows are subsampled deterministically`() {
        val points = (0 until 1_000).map {
            TrendPoint(timeMillis = it * 1_000L, valueMicroSvH = 0.10f + 0.0001f * it)
        }
        val first = TrendFit.fit(points)!!
        val second = TrendFit.fit(points)!!
        assertTrue(first.subsampled)
        assertTrue(first.pointsUsed <= TrendFit.MAX_PAIRED_POINTS + 1)
        assertEquals(1_000, first.presentBins)
        // No randomness anywhere: the same input is the same number, bit for bit.
        assertEquals(first.slopePerHourBits(), second.slopePerHourBits())
        // +0.0001 µSv/h per second = 0.36 per hour.
        assertTrue(abs(first.slopeMicroSvHPerHour - 0.36f) < 1e-3f)
        // The span is preserved exactly — the last bin is always kept.
        assertEquals(999_000L, first.spanMillis)
    }

    @Test
    fun `duplicate timestamps do not produce infinite slopes`() {
        // 13 distinct minutes, each measured twice with the same timestamp.
        val points = (0 until 26).map {
            TrendPoint(timeMillis = (it / 2) * minute, valueMicroSvH = 0.10f + 0.01f * (it / 2))
        }
        val fit = TrendFit.fit(points)!!
        assertTrue(fit.slopeMicroSvHPerHour.isFinite())
        assertTrue(abs(fit.slopeMicroSvHPerHour - 0.6f) < 1e-3f)
    }

    @Test
    fun `research OLS is available but is not the default`() {
        val columns = line(20)
        val ols = TrendFit.researchOlsSlope(TrendFit.toPoints(columns, minute))!!
        assertEquals(TrendMethod.OLS, ols.method)
        assertTrue(abs(ols.slopeMicroSvHPerHour - 0.6f) < 1e-3f)
        assertEquals(TrendMethod.THEIL_SEN, TrendFit.fit(columns, minute)!!.method)
    }

    @Test
    fun `label carries sign arrow and comma decimals`() {
        assertEquals("+0,004 ↗", TrendFit.label(0.004f, DoseUnitSetting.MICRO_SIEVERT))
        assertEquals("−0,012 ↘", TrendFit.label(-0.012f, DoseUnitSetting.MICRO_SIEVERT))
        // Below the flatness epsilon the arrow reads flat.
        assertEquals("+0,000 →", TrendFit.label(0.0002f, DoseUnitSetting.MICRO_SIEVERT))
        // µR display unit is 100×: one decimal.
        assertEquals("+0,4 ↗", TrendFit.label(0.004f, DoseUnitSetting.MICRO_ROENTGEN))
    }

    @Test
    fun `unavailable wording is the honest one`() {
        assertEquals("тренд недоступен", TrendFit.UNAVAILABLE)
    }

    private fun TrendResult.slopePerHourBits(): Int =
        java.lang.Float.floatToIntBits(slopeMicroSvHPerHour)

    @Test
    fun `an unavailable trend says which condition failed, with numbers`() {
        // Мало интервалов: причина называет и сколько есть, и сколько нужно.
        val few = (0 until 5).map { TrendPoint(it * 60_000L, 0.15f) }
        val fewResult = TrendFit.availability(few)
        assertTrue(fewResult is TrendAvailability.TooFewBins, "$fewResult")
        assertEquals(5, (fewResult as TrendAvailability.TooFewBins).present)
        val fewNote = TrendFit.unavailableNote(fewResult)
        assertTrue(fewNote!!.contains("5"), fewNote)
        assertTrue(fewNote.contains("${TrendFit.MIN_PRESENT_BINS}"), fewNote)

        // Интервалов хватает, но они занимают меньше десяти минут.
        val short = (0 until 20).map { TrendPoint(it * 10_000L, 0.15f) }
        val shortResult = TrendFit.availability(short)
        assertTrue(shortResult is TrendAvailability.TooShort, "$shortResult")
        val shortNote = TrendFit.unavailableNote(shortResult)
        assertTrue(shortNote!!.contains("мин"), shortNote)

        // Когда тренд есть, причины нет.
        val enough = (0 until 30).map { TrendPoint(it * 60_000L, 0.15f + it * 0.001f) }
        val ready = TrendFit.availability(enough)
        assertTrue(ready is TrendAvailability.Ready, "$ready")
        assertEquals(null, TrendFit.unavailableNote(ready))
        // Старый вход в тот же расчёт продолжает отдавать то же число.
        assertEquals(
            (ready as TrendAvailability.Ready).result.slopeMicroSvHPerHour,
            TrendFit.slopePerHour(enough)!!,
            1e-6f,
        )
    }

    @Test
    fun `a window shorter than the availability rule can never produce a trend`() {
        // Ровно та ловушка, в которую попала Главная: окно 5 мин (плюс запас
        // загрузки) физически не может дать размах 10 мин, сколько бы часов
        // измерений ни накопилось. Значит, окно тренда не имеет права
        // зависеть от того, какой масштаб выбран у графика рядом.
        val windowMillis = 5L * 60_000L
        val loaded = (windowMillis * 5 / 4)
        val bins = (0 until 200).map {
            TrendPoint(it * loaded / 200, 0.15f + it * 0.0001f)
        }
        val result = TrendFit.availability(bins)
        assertTrue(result is TrendAvailability.TooShort, "$result")

        // То же количество измерений в часовом окне тренд даёт.
        val hour = (0 until 200).map { TrendPoint(it * 3_600_000L / 200, 0.15f + it * 0.0001f) }
        assertTrue(TrendFit.availability(hour) is TrendAvailability.Ready)
    }
}
