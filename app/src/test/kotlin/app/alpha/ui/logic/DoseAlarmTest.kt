package app.alpha.ui.logic

import app.alpha.ui.text.MonitorRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Абсолютный уровень: два заголовка, которые имеют право прервать человека.
 *
 * Проверяется не «красиво ли написано», а физика порогов: ниже повышенного
 * уровня экран обязан молчать (иначе через неделю не читается ни одна строка),
 * а каждый заголовок обязан называть, чем он измеряется.
 */
class DoseAlarmTest {

    @Test
    fun `обычная жизнь заголовка не поднимает`() {
        // Квартира, улица, гранитная набережная, самолёт на эшелоне.
        for (value in listOf(0.08f, 0.15f, 0.4f, 3f, 9.9f)) {
            assertEquals(DoseAlarmLevel.NONE, DoseAlarm.of(value), "$value")
            assertNull(DoseAlarm.headline(DoseAlarm.of(value), MonitorRu), "$value")
        }
        assertEquals(DoseAlarmLevel.NONE, DoseAlarm.of(null))
    }

    @Test
    fun `повышенный уровень начинается там, где неделя даёт годовой предел`() {
        // 10 мкЗв/ч · 168 ч = 1,68 мЗв — больше годового предела для населения.
        assertEquals(DoseAlarmLevel.ELEVATED, DoseAlarm.of(10f))
        assertEquals(DoseAlarmLevel.ELEVATED, DoseAlarm.of(999f))
        assertTrue(DoseAlarm.ELEVATED_MICRO_SVH * 168 > 1000f)
    }

    @Test
    fun `уходить — там, где час даёт годовой предел`() {
        assertEquals(DoseAlarmLevel.LEAVE, DoseAlarm.of(1000f))
        assertEquals(DoseAlarmLevel.LEAVE, DoseAlarm.of(50_000f))
        assertEquals(1000f, DoseAlarm.LEAVE_MICRO_SVH)
    }

    @Test
    fun `каждый заголовок называет, чем он измеряется`() {
        for (level in listOf(DoseAlarmLevel.ELEVATED, DoseAlarmLevel.LEAVE)) {
            val note = DoseAlarm.note(level, 30f, MonitorRu)
            assertTrue(!note.isNullOrBlank(), "$level")
        }
        // Отношение печатается СО ЗНАМЕНАТЕЛЕМ: «×200 к природному фону».
        assertTrue(
            DoseAlarm.note(DoseAlarmLevel.ELEVATED, 30f, MonitorRu)!!.contains("природному фону"),
        )
    }

    @Test
    fun `отношение к природному фону округляется по порядку величины`() {
        // Порядок величины важнее разрядов: «×67» и «×6700» читаются с
        // одного взгляда, а «×66,7» и «×6666» — нет.
        assertEquals("×67", DoseAlarm.timesNatural(10f))
        assertEquals("×200", DoseAlarm.timesNatural(30f))
        assertEquals("×6700", DoseAlarm.timesNatural(1000f))
    }

    @Test
    fun `заголовок тревоги не выносит приговор`() {
        val texts = listOfNotNull(
            MonitorRu.alarmElevated,
            MonitorRu.alarmElevatedNote("×70"),
            MonitorRu.alarmLeave,
            MonitorRu.alarmLeaveNote,
        )
        // Правило вывода приложения: наблюдение и критерий, а не приговор.
        for (text in texts) {
            for (word in listOf("норма", "безопас", "опасн", "допустим")) {
                assertTrue(!text.lowercase().contains(word), "«$word» в «$text»")
            }
        }
    }
}
