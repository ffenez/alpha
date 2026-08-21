package app.alpha.data.export

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeFileTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun file(vararg lines: String) = lines.joinToString("\n")

    /** Файл в том виде, в каком его пишет ORTEC-совместимая программа. */
    private fun fullFile() = file(
        "\$SPEC_ID:",
        "Проба №1 гранит",
        "\$SPEC_REM:",
        "DET# 1",
        "\$DATE_MEA:",
        "08/09/2026 10:00:00",
        "\$MEAS_TIM:",
        "600 612",
        "\$DATA:",
        "0 7",
        "       0",
        "       5",
        "      11",
        "     107",
        "      12",
        "       3",
        "       1",
        "       0",
        "\$ROI:",
        "0",
        "\$ENER_FIT:",
        "-5.500000 2.416800",
        "\$MCA_CAL:",
        "3",
        "-5.500000E+000 2.416800E+000 3.976900E-004 keV",
        "\$SHAPE_CAL:",
        "3",
        "1.000000E+000 0.000000E+000 0.000000E+000",
    )

    @Test
    fun `обычный файл прочитан целиком и без замечаний`() {
        val result = SpeFile.parse(fullFile(), zone)

        assertEquals(emptyList(), result.warnings)
        val data = result.data
        assertEquals("Проба №1 гранит", data.title)
        assertEquals(8, data.channelCount)
        assertEquals(listOf(0, 5, 11, 107, 12, 3, 1, 0), data.counts)
        assertEquals(0, data.firstChannel)
        assertEquals(600.0, data.liveSeconds)
        assertEquals(612.0, data.realSeconds)
        assertEquals(
            ZonedDateTime.of(2026, 8, 9, 10, 0, 0, 0, zone).toInstant().toEpochMilli(),
            data.startMillis,
        )
        val calibration = assertNotNull(data.calibration)
        assertEquals(-5.5, calibration.a0, 1e-9)
        assertEquals(2.4168, calibration.a1, 1e-9)
        assertEquals(3.9769E-4, calibration.a2, 1e-12)
    }

    @Test
    fun `квадратичная калибровка MCA CAL важнее линейной ENER FIT`() {
        // В полном файле обе секции; выигрывает та, что несёт член при ch².
        val calibration = assertNotNull(SpeFile.parse(fullFile(), zone).data.calibration)
        assertTrue(calibration.a2 > 0.0, "квадратичный член потерян: $calibration")
    }

    @Test
    fun `калибровка из одной секции ENER FIT линейна`() {
        val text = file(
            "\$MEAS_TIM:",
            "100 100",
            "\$DATA:",
            "0 3",
            "1 2 3 4",
            "\$ENER_FIT:",
            "-5.500000 2.416800",
        )

        val result = SpeFile.parse(text, zone)

        assertEquals(emptyList(), result.warnings)
        val calibration = assertNotNull(result.data.calibration)
        assertEquals(-5.5, calibration.a0, 1e-9)
        assertEquals(2.4168, calibration.a1, 1e-9)
        assertEquals(0.0, calibration.a2)
    }

    @Test
    fun `без калибровки спектр читается, а шкала остаётся в каналах`() {
        val text = file(
            "\$MEAS_TIM:",
            "300 300",
            "\$DATA:",
            "0 3",
            "1",
            "2",
            "3",
            "4",
        )

        val result = SpeFile.parse(text, zone)

        assertNull(result.data.calibration)
        assertEquals(listOf(1, 2, 3, 4), result.data.counts)
        assertTrue(
            result.warnings.any { it.contains("калибровки энергии в файле нет") },
            "отсутствие калибровки не названо: ${result.warnings}",
        )
    }

    @Test
    fun `число каналов не совпадает с объявленным — взяты фактические и названы оба числа`() {
        val text = file(
            "\$MEAS_TIM:",
            "100 100",
            "\$DATA:",
            "0 1023",
            "1 2 3 4 5",
        )

        val result = SpeFile.parse(text, zone)

        assertEquals(5, result.data.channelCount)
        val warning = result.warnings.single { it.contains("каналов") }
        assertTrue(warning.contains("1024"), "нет заявленного числа каналов: $warning")
        assertTrue(warning.contains("5"), "нет фактического числа каналов: $warning")
    }

    @Test
    fun `спектр не с нулевого канала называет пропуск`() {
        val text = file(
            "\$MEAS_TIM:",
            "100 100",
            "\$DATA:",
            "16 19",
            "1 2 3 4",
        )

        val result = SpeFile.parse(text, zone)

        assertEquals(16, result.data.firstChannel)
        assertTrue(
            result.warnings.any { it.contains("канала 16") },
            "смещение начала не названо: ${result.warnings}",
        )
    }

    @Test
    fun `чужой текст без секций отвергнут`() {
        val error = assertFailsWith<SpeFileException> {
            SpeFile.parse("<html><body>не спектр</body></html>", zone)
        }
        assertTrue(
            error.message!!.contains("не файл спектра SPE"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `файл без секции DATA отвергнут`() {
        val error = assertFailsWith<SpeFileException> {
            SpeFile.parse(file("\$SPEC_ID:", "проба", "\$MEAS_TIM:", "100 100"), zone)
        }
        assertTrue(
            error.message!!.contains("DATA"),
            "отказ не называет отсутствующую секцию: ${error.message}",
        )
    }

    @Test
    fun `файл без живого времени отвергнут, а не дополнен нулём`() {
        val error = assertFailsWith<SpeFileException> {
            SpeFile.parse(file("\$DATA:", "0 3", "1 2 3 4"), zone)
        }
        assertTrue(
            error.message!!.contains("MEAS_TIM"),
            "отказ не называет отсутствующую секцию: ${error.message}",
        )
    }

    @Test
    fun `нулевое живое время отвергнуто`() {
        val error = assertFailsWith<SpeFileException> {
            SpeFile.parse(file("\$MEAS_TIM:", "0 0", "\$DATA:", "0 3", "1 2 3 4"), zone)
        }
        assertTrue(
            error.message!!.contains("живое время"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `нечисловой отсчёт называет номер канала`() {
        val error = assertFailsWith<SpeFileException> {
            SpeFile.parse(file("\$MEAS_TIM:", "100 100", "\$DATA:", "0 3", "1 2 ??? 4"), zone)
        }
        assertTrue(
            error.message!!.contains("канал №2") && error.message!!.contains("???"),
            "отказ не показывает, где сломано: ${error.message}",
        )
    }

    @Test
    fun `испорченный диапазон каналов отвергнут`() {
        val error = assertFailsWith<SpeFileException> {
            SpeFile.parse(file("\$MEAS_TIM:", "100 100", "\$DATA:", "начало", "1 2"), zone)
        }
        assertTrue(
            error.message!!.contains("диапазон"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `запятая вместо десятичной точки — отказ, а не догадка`() {
        val error = assertFailsWith<SpeFileException> {
            SpeFile.parse(file("\$MEAS_TIM:", "600,5 600,5", "\$DATA:", "0 1", "1 2"), zone)
        }
        assertTrue(
            error.message!!.contains("MEAS_TIM"),
            "причина отказа не названа: ${error.message}",
        )
    }

    @Test
    fun `перевод строки Windows и лишние секции не мешают`() {
        val text = fullFile().replace("\n", "\r\n")

        val result = SpeFile.parse(text, zone)

        assertEquals(emptyList(), result.warnings)
        assertEquals(8, result.data.channelCount)
        assertEquals(600.0, result.data.liveSeconds)
    }

    @Test
    fun `нечитаемая дата не роняет разбор`() {
        val text = fullFile().replace("08/09/2026 10:00:00", "позавчера")

        val result = SpeFile.parse(text, zone)

        assertNull(result.data.startMillis)
        assertEquals(8, result.data.channelCount)
        assertTrue(
            result.warnings.any { it.contains("дата измерения") },
            "неразобранная дата не названа: ${result.warnings}",
        )
    }
}
