package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Скрининг продукта отвечает на вопрос «отличается ли образец от фона» — и
 * отказывается отвечать, когда данных мало. Проверяется в первую очередь этот
 * отказ: «отличий не найдено» после минуты набора было бы обещанием, которого
 * измерение не даёт.
 */
class FoodScreeningTest {

    private fun counting(cps: Double, seconds: Double) =
        AbAnalysis.Counting(counts = cps * seconds, seconds = seconds)

    @Test
    fun `a minute of counting says nothing at all`() {
        val result = FoodScreening.screen(
            background = counting(cps = 0.4, seconds = 60.0),
            sample = counting(cps = 0.5, seconds = 60.0),
        )
        assertEquals(FoodScreening.Verdict.NOT_ENOUGH_DATA, result.verdict)
        assertTrue(!result.enoughData)
    }

    @Test
    fun `equal rates over a long run are called no difference`() {
        val result = FoodScreening.screen(
            background = counting(cps = 25.0, seconds = 1_800.0),
            sample = counting(cps = 25.0, seconds = 1_800.0),
        )
        assertEquals(FoodScreening.Verdict.NO_DIFFERENCE, result.verdict)
        assertEquals(AbAnalysis.Verdict.CONSISTENT, result.comparison!!.verdict)
    }

    @Test
    fun `a steady excess without a line is not called a nuclide`() {
        val result = FoodScreening.screen(
            background = counting(cps = 25.0, seconds = 1_800.0),
            sample = counting(cps = 27.0, seconds = 1_800.0),
        )
        assertEquals(FoodScreening.Verdict.EXCESS_WITHOUT_LINE, result.verdict)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun `a line in the difference spectrum is reported as a spectral feature`() {
        val result = FoodScreening.screen(
            background = counting(cps = 25.0, seconds = 1_800.0),
            sample = counting(cps = 27.0, seconds = 1_800.0),
            lines = listOf(FoodScreening.Line(energyKev = 661.7f, significance = 5.2)),
        )
        assertEquals(FoodScreening.Verdict.SPECTRAL_FEATURE, result.verdict)
        assertEquals(661.7f, result.lines.single().energyKev)
    }

    /**
     * Счёт НИЖЕ фонового — это не «продукт чище фона», а признак того, что
     * условия между прогонами разъехались. Различие называется, но линией не
     * объявляется, даже если движок пиков что-то нашёл.
     */
    @Test
    fun `a deficit is a difference in conditions, never a line`() {
        val result = FoodScreening.screen(
            background = counting(cps = 27.0, seconds = 1_800.0),
            sample = counting(cps = 25.0, seconds = 1_800.0),
            lines = listOf(FoodScreening.Line(661.7f, 5.2)),
        )
        assertEquals(FoodScreening.Verdict.EXCESS_WITHOUT_LINE, result.verdict)
    }
}

/**
 * Чувствительность и рекомендованное время — то, что превращает «отличий не
 * найдено» из пустой фразы в утверждение с границей.
 */
class FoodSensitivityTest {

    @Test
    fun `sensitivity says what excess would have been visible`() {
        val sensitivity = FoodScreening.sensitivity(
            background = AbAnalysis.Counting(counts = 25.0 * 1_800, seconds = 1_800.0),
            sample = AbAnalysis.Counting(counts = 25.0 * 1_800, seconds = 1_800.0),
        )
        assertNotNull(sensitivity)
        // При равных выдержках σ_R = √(2·R/t) = √(50/1800) ≈ 0,167 имп/с,
        // заметное — три таких.
        assertEquals(0.5, sensitivity.detectableCps, 0.02)
        assertEquals(0.02, sensitivity.detectableFraction!!, 0.002)
    }

    @Test
    fun `longer runs see finer additions`() {
        fun detectable(seconds: Double) = FoodScreening.sensitivity(
            background = AbAnalysis.Counting(25.0 * seconds, seconds),
            sample = AbAnalysis.Counting(25.0 * seconds, seconds),
        )!!.detectableCps

        assertTrue(detectable(3_600.0) < detectable(600.0))
    }

    @Test
    fun `nothing measured, no sensitivity claimed`() {
        assertNull(
            FoodScreening.sensitivity(
                background = AbAnalysis.Counting(0.0, 0.0),
                sample = AbAnalysis.Counting(10.0, 60.0),
            ),
        )
    }

    /**
     * «Минимум 20 минут» — не круглое число, а следствие фоновой скорости
     * счёта: t = 2k²/(p²·R). Чем выше фон, тем быстрее набирается.
     */
    @Test
    fun `the recommended time follows from the background rate`() {
        // 5 % от фона 25 имп/с при 3σ: 2·9/(0,0025·25) = 288 с.
        assertEquals(288L, FoodScreening.recommendedSeconds(25.0, fraction = 0.05))
        // Вдвое более тонкая добавка — вчетверо дольше.
        assertEquals(1_152L, FoodScreening.recommendedSeconds(25.0, fraction = 0.025))
        // Более высокий фон набирается быстрее.
        assertTrue(
            FoodScreening.recommendedSeconds(50.0, 0.05)!! <
                FoodScreening.recommendedSeconds(25.0, 0.05)!!,
        )
        assertNull(FoodScreening.recommendedSeconds(0.0, 0.05))
    }
}
