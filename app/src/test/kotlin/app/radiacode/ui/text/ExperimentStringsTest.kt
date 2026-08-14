package app.radiacode.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Перевод области «A/B эксперимент» — перенос ПРАВИЛА, а не подстановка слов.
 *
 * Английский каталог обязан подчиняться тем же запретам, что и русский: у
 * приложения нет права сказать «safe» ровно по той же причине, по которой оно
 * не говорит «безопасно», а лестница вердиктов §8 закрыта на обоих языках.
 */
class ExperimentStringsTest {

    private val catalogues = ExperimentCatalogue.all

    @Test
    fun `every catalogue fills every string`() {
        for (catalogue in catalogues) {
            val texts = catalogue.allTexts()
            assertTrue(texts.isNotEmpty())
            for (text in texts) {
                assertTrue(text.isNotBlank(), "пустая строка в ${catalogue::class.simpleName}")
            }
        }
    }

    @Test
    fun `no catalogue may promise safety`() {
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
                    assertFalse(
                        word.containsMatchIn(text.lowercase()),
                        "«$word» в ${catalogue::class.simpleName}: $text",
                    )
                }
            }
        }
    }

    @Test
    fun `danger is only ever denied in Russian, never claimed`() {
        // `\b` в Java определён через ASCII-`\w`, поэтому регексы выше на
        // кириллице не срабатывают: русская половина проверяется подстрокой —
        // тем же правилом, что в `ExperimentFormatTest`, где «опасность»
        // разрешена только как отрицание.
        for (text in ExperimentRu.allTexts()) {
            for (word in listOf("безопасн", "допустим", "норма", "нормальн")) {
                assertFalse(text.contains(word), "«$word»: $text")
            }
            if (text.contains("опасн")) {
                assertTrue(
                    text.contains("не об опасности") || text.contains("не к опасности"),
                    "опасность можно только отрицать, но не утверждать: $text",
                )
            }
        }
    }

    @Test
    fun `the verdict ladder is closed and a similarity percentage does not exist`() {
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                // «% похожести» не имеет определённого статистического смысла,
                // пока метрика не проверена на данных прибора (спец §8).
                assertFalse(text.contains("похожест"), text)
                assertFalse(text.contains("similarity"), text)
                assertFalse(text.contains("%"), text)
            }
        }
        // Три ступени и ровно три: «согласуется» / «изменилось» / «сильное
        // свидетельство изменения» — и то же самое по-английски.
        assertEquals(
            3,
            setOf(
                ExperimentEn.verdictConsistent,
                ExperimentEn.verdictChanged,
                ExperimentEn.verdictStrongEvidence,
            ).size,
        )
        assertTrue(ExperimentEn.verdictStrongEvidence.contains("strong evidence"))
    }

    @Test
    fun `no verdict claims what was found or how it ended`() {
        for (catalogue in catalogues) {
            for (text in catalogue.allTexts()) {
                assertFalse(text.contains("обнаружен"), text)
                assertFalse(text.lowercase().contains("detected"), text)
            }
        }
    }

    @Test
    fun `the experimental warning survives the translation`() {
        // Спец §24: до валидации на приборе оговорка обязана быть на обоих
        // языках, и она обязана называть, что именно не проверено.
        //
        // Конкретная модель в оговорке не называется: приложение работает со
        // всей серией RadiaCode, и «не проверено на RC-110» читалось бы так,
        // будто на других приборах проверено.
        assertTrue(ExperimentRu.experimentalNote.contains("не валидирована"))
        assertTrue(ExperimentRu.experimentalNote.contains("измерениях прибора"))
        assertTrue(ExperimentEn.experimentalNote.contains("not yet validated"))
        assertTrue(ExperimentEn.experimentalNote.contains("instrument measurements"))
        assertTrue(ExperimentEn.experimentalBadge.lowercase().contains("experimental"))
        // Метка и одна фраза остаются на экране, разбор зрелости — под «i».
        assertTrue(ExperimentRu.experimentalLead.length < ExperimentRu.experimentalNote.length)
        assertTrue(ExperimentEn.experimentalLead.length < ExperimentEn.experimentalNote.length)
        for (note in listOf(ExperimentRu.experimentalNote, ExperimentEn.experimentalNote)) {
            assertTrue(!note.contains("RC-110"), note)
        }
    }

    @Test
    fun `mandated warnings of the specification are translated, not dropped`() {
        // §16: 1/r² — ориентир, а не доказательство геометрии.
        assertTrue(ExperimentEn.distanceWarning.contains("1/r²"))
        assertTrue(ExperimentEn.distanceWarning.contains("does not prove"))
        assertTrue(ExperimentEn.distanceWarning.contains("background"))
        // §16: коэффициенты ослабления из домашнего опыта не выводятся.
        assertTrue(ExperimentEn.shieldingWarning.lowercase().contains("attenuation coefficients"))
        assertTrue(ExperimentEn.shieldingWarning.contains("do not follow"))
    }

    @Test
    fun `catalogues differ where they must`() {
        assertTrue(ExperimentRu.listTitle != ExperimentEn.listTitle)
        assertTrue(ExperimentRu.verdictConsistent != ExperimentEn.verdictConsistent)
        assertTrue(ExperimentRu.countsPerSecond != ExperimentEn.countsPerSecond)
        assertEquals(ExperimentRu, ExperimentCatalogue.of(AppLanguage.RU))
        assertEquals(ExperimentEn, ExperimentCatalogue.of(AppLanguage.EN))
        // Незнакомый язык каталогу не приходит: он уже разрешён в AppLanguage.
        assertEquals(ExperimentRu, ExperimentCatalogue.of(AppLanguage.SYSTEM))
    }
}
