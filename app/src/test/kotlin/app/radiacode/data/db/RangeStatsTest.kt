package app.radiacode.data.db

import app.radiacode.device.DoseUnits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Полевой дефект: в Истории мощность дозы показывалась нулями.
 *
 * Причина не в измерении и не в форматировании — экран печатал СЫРОЕ значение
 * прибора через форматтер мкЗв/ч. Обычный фон 0,15 мкЗв/ч хранится как
 * 0,000015, и формат с двумя знаками честно давал «0,00». Тест держит границу:
 * у сводки есть отдельные свойства в мкЗв/ч, и они действительно переводят.
 */
class RangeStatsTest {

    private fun stats(raw: Float?) = RangeStats(
        sampleCount = 3600,
        avgDoseRate = raw,
        minDoseRate = raw,
        maxDoseRate = raw,
        avgCountRate = 25f,
        maxCountRate = 30f,
    )

    @Test
    fun `the display values are in microsieverts per hour`() {
        // 0,15 мкЗв/ч в единицах прибора.
        val raw = 0.15f / DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR
        val s = stats(raw)
        assertEquals(0.15f, s.avgDoseRateMicroSvH!!, 1e-4f)
        assertEquals(0.15f, s.minDoseRateMicroSvH!!, 1e-4f)
        assertEquals(0.15f, s.maxDoseRateMicroSvH!!, 1e-4f)
    }

    @Test
    fun `an ordinary background does not round to zero at two decimals`() {
        // Ровно тот случай, который был на экране: два знака после запятой.
        val raw = 0.15f / DoseUnits.RAW_TO_MICRO_SIEVERT_PER_HOUR
        val shown = String.format(java.util.Locale.US, "%.2f", stats(raw).avgDoseRateMicroSvH)
        assertTrue(shown != "0.00", "мощность дозы снова печатается нулём: $shown")
        assertEquals("0.15", shown)
    }

    @Test
    fun `absent measurements stay absent, not zero`() {
        // Пустой диапазон — это «нет измерений», а не «измерено ноль»: подмена
        // одного другим и есть та двусмысленность, которую проект запрещает.
        val s = stats(null)
        assertNull(s.avgDoseRateMicroSvH)
        assertNull(s.minDoseRateMicroSvH)
        assertNull(s.maxDoseRateMicroSvH)
    }
}
