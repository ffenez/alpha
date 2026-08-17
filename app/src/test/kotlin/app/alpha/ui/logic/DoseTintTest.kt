package app.alpha.ui.logic

import app.alpha.baseline.Baseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Цвет главного числа — отношение к обычному фону МЕСТА, и ничего сверх того.
 *
 * Проверяется главное: без основания сравнения цвета нет вовсе, внутри
 * обычного диапазона он один и тот же, а за порогом перестаёт меняться —
 * дальше решает вывод словами, а не оттенок.
 */
class DoseTintTest {

    private val baseline = Baseline(
        doseLowMicroSvH = 0.09f,
        doseMedianMicroSvH = 0.11f,
        doseHighMicroSvH = 0.14f,
        doseP25MicroSvH = 0.10f,
        doseP75MicroSvH = 0.13f,
        doseMadMicroSvH = 0.01f,
        cpsLow = 18f,
        cpsMedian = 22f,
        cpsHigh = 27f,
        accumulatedSeconds = 26 * 3600L,
        sampleCount = 26 * 3600L,
        bucketCount = 1560,
    )

    @Test
    fun `without a place band there is no colour at all`() {
        assertNull(DoseTint.fraction(0.30f, baseline = null))
        assertNull(DoseTint.fraction(null, baseline = baseline))
        assertNull(DoseTint.fraction(Float.NaN, baseline))
    }

    /** Внутри обычного диапазона значения не «лучше» и не «хуже» друг друга. */
    @Test
    fun `everything inside the usual range looks the same`() {
        assertEquals(0f, DoseTint.fraction(0.05f, baseline))
        assertEquals(0f, DoseTint.fraction(0.12f, baseline))
        assertEquals(0f, DoseTint.fraction(0.14f, baseline))
    }

    /**
     * Верх шкалы — МНОЖИТЕЛЬ обычного, и его задаёт человек: у каждого места
     * свой уровень, и абсолютное «багровое от 0,30» означало бы в одном месте
     * вдвое выше обычного, а в другом — вдесятеро.
     */
    @Test
    fun `the top of the scale is a multiple of the usual, and it is settable`() {
        // По умолчанию вдвое: 0,14 → 0,28.
        val middle = DoseTint.fraction(0.21f, baseline)!!
        assertTrue(middle > 0f && middle < 1f, "$middle")
        assertEquals(1f, DoseTint.fraction(0.28f, baseline))
        // За верхом цвет уже не меняется: дальше говорит вывод, а не оттенок.
        assertEquals(1f, DoseTint.fraction(3.0f, baseline))

        // Множитель втрое отодвигает насыщение: то же значение уже не багровое.
        assertTrue(DoseTint.fraction(0.28f, baseline, factor = 3f)!! < 1f)
        // …и наоборот, полтора — приближает. Допуск здесь не косметика:
        // 0,14 × 1,5 у float чуть больше, чем 0,21, и требовать точной
        // единицы значило бы проверять представление чисел, а не шкалу.
        assertEquals(1f, DoseTint.fraction(0.21f, baseline, factor = 1.5f)!!, 1e-4f)
    }

    @Test
    fun `an absurd multiplier cannot break the scale`() {
        // Множитель внутри обычного разброса выправляется до допустимого.
        assertTrue(DoseTint.of(0.15f, 0.14f, factor = 0.5f)!! in 0f..1f)
        assertTrue(DoseTint.of(0.15f, 0.14f, factor = 1_000f)!! in 0f..1f)
    }

    @Test
    fun `the multiplier is written without trailing zeros`() {
        assertEquals("2", DoseTint.factorLabel(2f))
        assertEquals("1,5", DoseTint.factorLabel(1.5f))
    }
}
