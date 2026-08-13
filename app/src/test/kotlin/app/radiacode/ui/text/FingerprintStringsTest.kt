package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Перевод переносит ПРАВИЛО, а не слова: там, где русский отказывается
 * утверждать равенство или называть вред, английский обязан отказываться так
 * же. Проверка идёт по обоим каталогам одним списком.
 */
class FingerprintStringsTest {

    private val catalogues = FingerprintCatalogue.all

    @Test
    fun `nothing in either language promises safety`() {
        val forbidden = listOf(
            Regex("""\bбезопасн\w*\b"""),
            Regex("""\bопасн\w*\b"""),
            Regex("""\bдопустим\w*\b"""),
            Regex("""\bнорма\b"""),
            Regex("""\bnormal\b(?! distribution)"""),
            Regex("""\bsafe\b"""),
            Regex("""\bdangerous\b"""),
            Regex("""\bharmless\b"""),
        )
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                for (word in forbidden) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word» in: $text")
                }
            }
        }
    }

    @Test
    fun `no verdict claims equality with the reference`() {
        // Критерий проверял ОТЛИЧИЕ и его не нашёл — это не доказанное
        // совпадение. Слова, которыми такой вывод превращают в утверждение о
        // равенстве, запрещены в обоих языках.
        val equality = Regex(
            """совпада|такой же|идентичн|\bравен\b|\bmatches\b|\bidentical\b|\bthe same as\b""",
        )
        val verdicts = catalogues.flatMap {
            listOf(
                it.headlineNoDifference, it.headlineIntensityChanged, it.headlineShapeChanged,
                it.stateSame, it.hardnessFlat,
            )
        }
        for (text in verdicts) {
            assertTrue(!equality.containsMatchIn(text.lowercase()), "утверждение равенства: $text")
        }
    }

    @Test
    fun `the caveat keeps both halves of the limitation`() {
        // Половина оговорки — это другая оговорка: без второй части экран
        // начинает выглядеть так, будто расхождение называет причину.
        assertTrue(FingerprintRu.caveat.contains("не доказывает"), FingerprintRu.caveat)
        assertTrue(FingerprintRu.caveat.contains("не называет причину"), FingerprintRu.caveat)
        assertTrue(FingerprintEn.caveat.contains("does not prove"), FingerprintEn.caveat)
        assertTrue(FingerprintEn.caveat.contains("does not name its cause"), FingerprintEn.caveat)
    }

    @Test
    fun `hardness is called derived and explicitly casts no vote`() {
        // Жёсткость — частное дозы и счёта; если бы она голосовала наравне,
        // одно событие считалось бы дважды. Это сказано на обоих языках.
        assertTrue(FingerprintRu.hardnessExplains("0,5", "0,4", "выше").contains("не голосует"))
        assertTrue(FingerprintEn.hardnessExplains("0.5", "0.4", "above").contains("casts no vote"))
    }

    @Test
    fun `the language picks the catalogue and the two really differ`() {
        assertEquals(FingerprintRu, FingerprintCatalogue.of(AppLanguage.RU))
        assertEquals(FingerprintEn, FingerprintCatalogue.of(AppLanguage.EN))
        assertEquals(FingerprintRu, FingerprintCatalogue.of(AppLanguage.SYSTEM))
        assertTrue(FingerprintRu.allTexts().none { it in FingerprintEn.allTexts() })
        assertEquals(FingerprintRu.allTexts().size, FingerprintEn.allTexts().size)
    }

    @Test
    fun `units carry over to the english catalogue`() {
        // Обозначение единицы — часть числа: непереведённое «мкЗв/ч» в
        // английском интерфейсе делает строку нечитаемой ровно там, где
        // сравниваются величины.
        assertEquals("µSv/h", FingerprintEn.unitDose)
        assertEquals("s⁻¹", FingerprintEn.unitCount)
    }
}
