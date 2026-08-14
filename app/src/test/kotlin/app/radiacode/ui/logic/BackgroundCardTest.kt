package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
import app.radiacode.ui.text.BackgroundCardCatalogue
import app.radiacode.ui.text.BackgroundCardEn
import app.radiacode.ui.text.BackgroundCardRu
import app.radiacode.ui.text.allTexts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Карточка записанного фона (ТЗ §10): короткая на рабочем экране, полная под
 * «i». Тест держит именно это распределение — что стоит перед глазами
 * человека, который сейчас ходит с прибором, а что он открывает по желанию.
 */
class BackgroundCardTest {

    private fun record(
        seconds: Int = 45,
        target: Int = 45,
        gapSeconds: Double = 0.0,
        profileId: Long? = 1L,
        serial: String? = "RC-110-TEST",
    ): BackgroundRecord {
        val times = LongArray(seconds) { it * 1_000L }
        val rates = DoubleArray(seconds) { i -> 24.0 + if (i % 2 == 0) 0.5 else -0.5 }
        val window = CountWindow.reconstruct(times, rates).copy(gapSeconds = gapSeconds)
        return BackgroundRecord(
            window = window,
            atMillis = 0L,
            targetSamples = target,
            profileId = profileId,
            profileName = "Дом",
            deviceSerial = serial,
        )
    }

    private fun card(
        check: BackgroundCheck,
        record: BackgroundRecord? = record(),
    ) = SearchBaseline.card(
        record = record,
        check = check,
        rateText = "24,1",
        day = "12.08",
        timeOfDay = "15:04",
        targetSeconds = 45,
    )

    @Test
    fun `a usable background is three short facts and no argument`() {
        val model = card(BackgroundCheck.USABLE)

        assertEquals("Фон: 24,1 имп/с", model.level)
        assertEquals("Записан 12.08 в 15:04 · измерение 45 с", model.basis)
        assertNull(model.reason, "пригодный фон ничего не требует")
        assertTrue(model.usable)
        // Пока сравнивать есть с чем, работа человека — ходить, а не нажимать.
        assertTrue(!model.actionPrimary)
        assertEquals("Обновить фон", model.action)
    }

    @Test
    fun `an aged background says so in one line and offers the action`() {
        val model = card(BackgroundCheck.AGED)
        val reason = assertNotNull(model.reason)

        assertTrue(reason.contains("давно"), reason)
        assertTrue(reason.contains("обновить"), reason)
        // Одна строка, а не абзац: перевод строки означал бы вернувшийся текст.
        assertTrue(!reason.contains("\n") && reason.length <= 80, reason)
        assertTrue(model.actionPrimary)
        assertEquals("Обновить фон", model.action)
    }

    /**
     * Каждая причина непригодности НАЗЫВАЕТСЯ. «Фон непригоден» без причины
     * заставляет гадать, что чинить, — и это единственное, чего карточка не
     * имеет права сделать, укоротившись.
     */
    @Test
    fun `every unusable state names its own cause`() {
        val cases = mapOf(
            BackgroundCheck.AGED to card(BackgroundCheck.AGED),
            BackgroundCheck.PROFILE_CHANGED to card(BackgroundCheck.PROFILE_CHANGED),
            BackgroundCheck.DEVICE_CHANGED to card(BackgroundCheck.DEVICE_CHANGED),
            BackgroundCheck.LOW_QUALITY to card(
                BackgroundCheck.LOW_QUALITY,
                record(seconds = 20, target = 45),
            ),
        )
        val reasons = cases.mapValues { assertNotNull(it.value.reason, "${it.key}") }

        assertTrue(reasons.getValue(BackgroundCheck.PROFILE_CHANGED).contains("«Дом»"))
        assertTrue(reasons.getValue(BackgroundCheck.DEVICE_CHANGED).contains("другим прибором"))
        assertTrue(reasons.getValue(BackgroundCheck.LOW_QUALITY).contains("не был закончен"))
        // Причины различимы: одинаковая строка на два состояния — это «непригоден».
        assertEquals(reasons.values.toSet().size, reasons.size)
        for (model in cases.values) assertTrue(model.actionPrimary)
    }

    /** Пропуски потока и подвижность прибора — разные причины и разные строки. */
    @Test
    fun `quality problems are told apart`() {
        val gappy = card(BackgroundCheck.LOW_QUALITY, record(gapSeconds = 9.0))
        val short = card(BackgroundCheck.LOW_QUALITY, record(seconds = 20, target = 45))

        assertTrue(assertNotNull(gappy.reason).contains("пропуски потока"))
        assertTrue(assertNotNull(short.reason).contains("не был закончен"))
    }

    /**
     * Ничего не потеряно: развёрнутое объяснение и параметры замера уходят на
     * второй уровень целиком.
     */
    @Test
    fun `the long explanation moves to the details, it does not disappear`() {
        val model = card(BackgroundCheck.AGED)

        assertTrue(model.details.size >= 2, "${model.details}")
        assertTrue(model.details.any { it.contains("больше получаса") }, "${model.details}")
        // «экспозиция 45 с» → «фон измерялся 45 с» (§3): длительность замера
        // называется тем, чем она является для человека с прибором в руках.
        assertTrue(model.details.any { it.contains("фон измерялся") }, "${model.details}")
        // На первом уровне этого абзаца нет — иначе сокращение было бы мнимым.
        assertTrue(!assertNotNull(model.reason).contains("больше получаса"))
    }

    @Test
    fun `without a background the card teaches the first action`() {
        val model = SearchBaseline.card(
            record = null,
            check = BackgroundCheck.USABLE,
            rateText = "—",
            day = "12.08",
            timeOfDay = "—",
            targetSeconds = 45,
        )

        assertEquals("Фон не записан", model.level)
        assertNull(model.basis)
        assertTrue(model.actionPrimary)
        assertEquals("Замерить фон · 45 с", model.action)
        assertTrue(model.details.isNotEmpty(), "первое действие объясняется под «i»")
    }

    @Test
    fun `the English card carries the same facts`() {
        val model = SearchBaseline.card(
            record = record(),
            check = BackgroundCheck.AGED,
            rateText = "24.1",
            day = "12.08",
            timeOfDay = "15:04",
            targetSeconds = 45,
            c = BackgroundCardEn,
            t = app.radiacode.ui.text.SearchEn,
        )

        assertEquals("Background: 24.1 counts/s", model.level)
        assertEquals("Recorded on 12.08 at 15:04 · 45 s measurement", model.basis)
        assertTrue(assertNotNull(model.reason).contains("refresh it"))
        assertEquals("Refresh the background", model.action)
    }

    @Test
    fun `no wording in either language promises safety`() {
        val forbidden = listOf(
            Regex("""\bбезопасн\w*\b"""),
            Regex("""\bопасн\w*\b"""),
            Regex("""\bдопустим\w*\b"""),
            Regex("""\bнорма\b"""),
            Regex("""\bsafe\b"""),
            Regex("""\bnormal\b"""),
        )
        for (catalogue in BackgroundCardCatalogue.all) {
            for (text in catalogue.allTexts()) {
                assertTrue(text.isNotBlank())
                for (word in forbidden) {
                    assertTrue(!word.containsMatchIn(text.lowercase()), "«$word»: $text")
                }
            }
        }
        assertEquals(BackgroundCardRu.allTexts().size, BackgroundCardEn.allTexts().size)
    }
}
