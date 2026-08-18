package app.alpha.ui.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyScaleTest {

    @Test
    fun `nothing to show keeps the smallest frame`() {
        assertEquals(NavigateArc.LADDER.first(), VerifyScale.requiredFactor(null), 1e-9)
        assertEquals(
            NavigateArc.LADDER.first(),
            VerifyScale.requiredFactor(Double.NaN, Double.NEGATIVE_INFINITY, 0.0),
            1e-9,
        )
    }

    @Test
    fun `the frame holds the interval, not just the estimate`() {
        // Оценка помещается в базовый кадр ×4, верхний конец интервала — нет:
        // кадр, подобранный по одной стрелке, показал бы измерение точнее, чем
        // оно есть.
        val tight = VerifyScale.requiredFactor(3.2)
        val wide = VerifyScale.requiredFactor(3.2, low = 2.1, high = 9.0)
        assertEquals(4.0, tight, 1e-9)
        assertTrue("интервал не раздвинул кадр", wide > tight)
    }

    @Test
    fun `an open-ended interval does not blow the frame away`() {
        // Пустое фоновое окно даёт бесконечный верхний конец; кадр обязан
        // остаться конечным и следовать оценке.
        assertEquals(
            VerifyScale.requiredFactor(3.2),
            VerifyScale.requiredFactor(3.2, low = 2.1, high = Double.POSITIVE_INFINITY),
            1e-9,
        )
    }

    @Test
    fun `a deficit is framed by its distance from the reference`() {
        // ×0,2 отстоит от фона в пять раз — расстояние считается по обе
        // стороны от ×1 одинаково, и кадр берётся тот же, что для ×5.
        assertEquals(
            VerifyScale.requiredFactor(5.0),
            VerifyScale.requiredFactor(0.2),
            1e-9,
        )
        assertEquals(8.0, VerifyScale.requiredFactor(0.2), 1e-9)
    }

    @Test
    fun `only a confirmed difference colours the needle`() {
        assertEquals(NavigateTrend.COLLECTING, VerifyScale.trend(SearchLevel.UNKNOWN))
        assertEquals(NavigateTrend.NO_CHANGE, VerifyScale.trend(SearchLevel.BACKGROUND))
        // Различие показывается, но приговор ему не вынесен: цвет тревоги был
        // бы этим приговором.
        assertEquals(NavigateTrend.NO_CHANGE, VerifyScale.trend(SearchLevel.POSSIBLE_CHANGE))
        assertEquals(NavigateTrend.RISING, VerifyScale.trend(SearchLevel.CONFIRMED_EXCESS))
        assertEquals(NavigateTrend.FALLING, VerifyScale.trend(SearchLevel.CONFIRMED_DEFICIT))
    }
}
