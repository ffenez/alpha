package app.alpha.analysis.evidence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Правило «когда модель разрешения принимается сама».
 *
 * Проверяется ровно то, что нельзя увидеть на экране: что фон не перебивает
 * решение человека и не держится за коэффициенты чужого прибора.
 */
class ResolutionAdoptionTest {

    private val now = 1_800_000_000_000L

    private fun fitted(points: Int = 4) = ResolutionFitOutcome.Fitted(
        ResolutionFitResult(
            a = 100.0,
            b = 6.0,
            c = 0.0,
            points = List(points) { 500.0 + 500.0 * it },
            quadratic = false,
            extrapolatedBelowKeV = 500.0,
            extrapolatedAboveKeV = 2615.0,
        ),
    )

    private fun stored(
        serial: String?,
        automatic: Boolean,
        atMillis: Long = now,
        points: Int = 4,
        version: Int = 1,
    ) = AcceptedResolution(
        a = 90.0,
        b = 6.0,
        c = 0.0,
        deviceSerial = serial,
        acceptedAtMillis = atMillis,
        points = points,
        lowestKeV = 500.0,
        highestKeV = 2615.0,
        algorithmVersion = version,
        automatic = automatic,
    )

    @Test
    fun `отказ подгонки ничего не принимает`() {
        assertNull(
            ResolutionAdoption.decide(
                fit = ResolutionFitOutcome.Refused(ResolutionFitRefusal.NOT_ENOUGH_LINES, 2, 300.0),
                serial = "RC-110-000000",
                stored = null,
                nowMillis = now,
                algorithmVersion = 1,
            ),
        )
    }

    @Test
    fun `первая удавшаяся подгонка принимается без человека`() {
        val next = assertNotNull(
            ResolutionAdoption.decide(fitted(), "RC-110-000000", null, now, 1),
        )
        assertTrue(next.automatic, "модель обязана быть помечена как снятая сама")
        assertEquals(4, next.points)
    }

    @Test
    fun `принятое человеком фон не трогает`() {
        assertNull(
            ResolutionAdoption.decide(
                fit = fitted(points = 5),
                serial = "RC-110-000000",
                stored = stored("RC-110-000000", automatic = false),
                nowMillis = now + ResolutionAdoption.REFRESH_MILLIS,
                algorithmVersion = 1,
            ),
        )
    }

    @Test
    fun `чужой прибор заменяется даже если модель принял человек`() {
        // Коэффициенты описывают конкретный кристалл: держаться за чужие
        // значит искать пики не той ширины.
        val next = assertNotNull(
            ResolutionAdoption.decide(
                fit = fitted(),
                serial = "RC-103-000001",
                stored = stored("RC-110-000000", automatic = false),
                nowMillis = now,
                algorithmVersion = 1,
            ),
        )
        assertEquals("RC-103-000001", next.deviceSerial)
    }

    @Test
    fun `своя свежая запись не переписывается тем же самым`() {
        assertNull(
            ResolutionAdoption.decide(
                fit = fitted(points = 4),
                serial = "RC-110-000000",
                stored = stored("RC-110-000000", automatic = true),
                nowMillis = now + 3_600_000L,
                algorithmVersion = 1,
            ),
        )
    }

    @Test
    fun `больше линий или неделя давности — повод обновиться`() {
        assertNotNull(
            ResolutionAdoption.decide(
                fit = fitted(points = 5),
                serial = "RC-110-000000",
                stored = stored("RC-110-000000", automatic = true),
                nowMillis = now,
                algorithmVersion = 1,
            ),
            "подгонка по большему числу линий должна заменить прежнюю",
        )
        assertNotNull(
            ResolutionAdoption.decide(
                fit = fitted(points = 4),
                serial = "RC-110-000000",
                stored = stored("RC-110-000000", automatic = true),
                nowMillis = now + ResolutionAdoption.REFRESH_MILLIS,
                algorithmVersion = 1,
            ),
            "через неделю прибор мог уехать по усилению",
        )
    }

    @Test
    fun `флаг «снято само» переживает запись и чтение`() {
        val auto = assertNotNull(
            ResolutionAdoption.decide(fitted(), "RC-110-000000", null, now, 1),
        )
        assertEquals(true, AcceptedResolution.decode(auto.encode())?.automatic)
        // Записи прежних версий поля не имеют — они приняты человеком.
        val legacy = auto.encode().split(';').filterNot { it.startsWith("auto=") }.joinToString(";")
        assertEquals(false, AcceptedResolution.decode(legacy)?.automatic)
    }
}
