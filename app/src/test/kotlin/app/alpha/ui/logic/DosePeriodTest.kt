package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Приёмка из `accumulated_dose_ui_redesign.md`: главное число — сумма ровно
 * тех суток, что нарисованы; пропущенные дни не считаются нулём; покрытие
 * делится на ПРОШЕДШУЮ часть периода.
 */
class DosePeriodTest {

    private fun day(microSv: Float, hours: Double) =
        DailyDose.Day(microSv, (hours * 3_600).toLong())

    private val fullDay = 86_400L

    @Test
    fun `итог периода равен сумме нарисованных суток`() {
        val days = List(30) { day(0.3f, 24.0) }
        val period = DosePeriods.of(days, 7, todayElapsedSeconds = fullDay)
        assertEquals(7, period.daily.size)
        assertEquals(2.1, period.microSv, 1e-6)
        // Сумма столбиков и есть число над ними.
        assertEquals(period.daily.sumOf { it.microSv.toDouble() }, period.microSv, 1e-9)
    }

    @Test
    fun `период длиннее истории берёт то, что есть`() {
        val days = List(3) { day(1.0f, 24.0) }
        val period = DosePeriods.of(days, 30, todayElapsedSeconds = fullDay)
        assertEquals(3, period.daily.size)
        assertEquals(3.0, period.microSv, 1e-6)
    }

    @Test
    fun `день без измерений не добавляет нуля и не считается измеренным`() {
        val days = listOf(day(2.0f, 24.0), day(0f, 0.0), day(1.0f, 24.0))
        val period = DosePeriods.of(days, 3, todayElapsedSeconds = fullDay)
        assertEquals(3.0, period.microSv, 1e-6)
        assertEquals(2, period.measuredDays, "пустой день посчитан измеренным")
    }

    @Test
    fun `покрытие считается от прошедшей части периода`() {
        // Двое полных суток позади и половина сегодняшних; измерено всё.
        val days = listOf(day(1f, 24.0), day(1f, 24.0), day(0.5f, 12.0))
        val period = DosePeriods.of(days, 3, todayElapsedSeconds = 12 * 3_600L)
        assertEquals(2 * fullDay + 12 * 3_600L, period.elapsedSeconds)
        assertEquals(1.0f, period.coverage, 1e-4f)
    }

    @Test
    fun `неизмеренный месяц даёт малое покрытие, а не полное`() {
        val days = List(29) { day(0f, 0.0) } + day(0.3f, 2.0)
        val period = DosePeriods.of(days, 30, todayElapsedSeconds = fullDay)
        assertEquals(2 * 3_600L, period.measuredSeconds)
        assertTrue(period.coverage < 0.01f, "покрытие ${period.coverage}")
    }

    @Test
    fun `среднее берётся только по полным суткам`() {
        // Полный день на 3,0 и огрызок на 0,1: среднее по обоим было бы 1,55,
        // что ниже любого настоящего дня.
        val days = listOf(day(3.0f, 24.0), day(0.1f, 0.5))
        val period = DosePeriods.of(days, 7, todayElapsedSeconds = fullDay)
        assertEquals(3.0f, period.averageFullDayMicroSv!!, 1e-4f)
    }

    @Test
    fun `без полных суток среднего нет`() {
        val days = listOf(day(0.1f, 1.0), day(0.2f, 2.0))
        val period = DosePeriods.of(days, 7, todayElapsedSeconds = fullDay)
        assertNull(period.averageFullDayMicroSv)
        // Максимум при этом есть: он не требует полноты.
        assertEquals(0.2f, period.maxDayMicroSv!!, 1e-6f)
    }

    @Test
    fun `без измерений вовсе нет ни среднего, ни максимума`() {
        val period = DosePeriods.of(List(7) { day(0f, 0.0) }, 7, todayElapsedSeconds = fullDay)
        assertNull(period.averageFullDayMicroSv)
        assertNull(period.maxDayMicroSv)
        assertEquals(0, period.measuredDays)
        assertEquals(0.0, period.microSv, 1e-9)
    }

    @Test
    fun `пустой ряд не ломает покрытие`() {
        val period = DosePeriods.of(emptyList(), 30, todayElapsedSeconds = fullDay)
        assertEquals(0L, period.elapsedSeconds)
        assertEquals(0f, period.coverage)
    }

    @Test
    fun `переключение периода меняет число, а не только картинку`() {
        val days = List(30) { day(1.0f, 24.0) }
        val week = DosePeriods.of(days, 7, fullDay)
        val month = DosePeriods.of(days, 30, fullDay)
        assertTrue(month.microSv > week.microSv, "${month.microSv} против ${week.microSv}")
        assertEquals(7.0, week.microSv, 1e-6)
        assertEquals(30.0, month.microSv, 1e-6)
    }
}
