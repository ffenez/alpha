package app.radiacode.ui.logic

import app.radiacode.baseline.Baseline
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
        assertNull(DoseTint.fraction(0.30f, baseline = null, alarmMicroSvH = 0.30f))
        assertNull(DoseTint.fraction(null, baseline = baseline, alarmMicroSvH = 0.30f))
        assertNull(DoseTint.fraction(Float.NaN, baseline, 0.30f))
    }

    /** Внутри обычного диапазона значения не «лучше» и не «хуже» друг друга. */
    @Test
    fun `everything inside the usual range looks the same`() {
        assertEquals(0f, DoseTint.fraction(0.05f, baseline, 0.30f))
        assertEquals(0f, DoseTint.fraction(0.12f, baseline, 0.30f))
        assertEquals(0f, DoseTint.fraction(0.14f, baseline, 0.30f))
    }

    @Test
    fun `above the range the colour walks towards the threshold and stops there`() {
        val middle = DoseTint.fraction(0.22f, baseline, 0.30f)!!
        assertTrue(middle > 0f && middle < 1f, "$middle")
        assertEquals(1f, DoseTint.fraction(0.30f, baseline, 0.30f))
        // За порогом цвет уже не меняется: дальше говорит вывод, а не оттенок.
        assertEquals(1f, DoseTint.fraction(3.0f, baseline, 0.30f))
    }

    /**
     * Порог ниже обычного диапазона — не шкала, а противоречие: тогда
     * единственная честная привязка это сам диапазон.
     */
    @Test
    fun `a threshold below the place band is not used as the top`() {
        val withBadThreshold = DoseTint.fraction(0.28f, baseline, alarmMicroSvH = 0.10f)!!
        val withoutThreshold = DoseTint.fraction(0.28f, baseline, alarmMicroSvH = null)!!

        assertEquals(withoutThreshold, withBadThreshold)
        // Верх без порога — вдвое выше P90, то есть 0,28 и есть насыщение.
        assertEquals(1f, withoutThreshold)
    }
}
