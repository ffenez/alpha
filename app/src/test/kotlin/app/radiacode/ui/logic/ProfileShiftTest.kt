package app.radiacode.ui.logic

import app.radiacode.baseline.Baseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A long deviation may **never** become the new usual on its own (why-spec §7):
 * the app asks, the user answers, and the old period is kept either way.
 */
class ProfileShiftTest {

    private val baseline = Baseline(
        doseLowMicroSvH = 0.14f,
        doseMedianMicroSvH = 0.15f,
        doseHighMicroSvH = 0.17f,
        doseP25MicroSvH = 0.15f,
        doseP75MicroSvH = 0.16f,
        doseMadMicroSvH = 0.01f,
        cpsLow = 20f,
        cpsMedian = 25f,
        cpsHigh = 30f,
        accumulatedSeconds = 23 * 3600L,
        sampleCount = 82_800L,
        bucketCount = 1_441,
    )

    private val now = 1_000_000_000L

    @Test
    fun `a short deviation is never a reason to redefine the place`() {
        assertTrue(
            !ProfileShift.shouldOffer(
                MonitorStatus.AboveUsual(baseline, heldSeconds = 600),
                declinedAtMillis = null,
                nowMillis = now,
            ),
        )
        assertTrue(
            !ProfileShift.shouldOffer(
                MonitorStatus.Usual(baseline),
                declinedAtMillis = null,
                nowMillis = now,
            ),
        )
        assertTrue(
            !ProfileShift.shouldOffer(MonitorStatus.Unknown, null, now),
        )
    }

    @Test
    fun `the offer appears only after the deviation has held for hours`() {
        val justBefore = MonitorStatus.AboveUsual(
            baseline,
            heldSeconds = ProfileShift.OFFER_AFTER_SECONDS - 1,
        )
        assertTrue(!ProfileShift.shouldOffer(justBefore, null, now))

        val atThreshold = MonitorStatus.AboveUsual(
            baseline,
            heldSeconds = ProfileShift.OFFER_AFTER_SECONDS,
        )
        assertTrue(ProfileShift.shouldOffer(atThreshold, null, now))

        // The alarm state is the same situation, one rung up.
        val alarm = MonitorStatus.Alert(
            baseline,
            heldSeconds = ProfileShift.OFFER_AFTER_SECONDS,
            thresholdMicroSvH = 0.3f,
        )
        assertTrue(ProfileShift.shouldOffer(alarm, null, now))
    }

    @Test
    fun `keeping it as is stops the question for a day, then it may return`() {
        val status = MonitorStatus.AboveUsual(baseline, ProfileShift.OFFER_AFTER_SECONDS)
        assertTrue(!ProfileShift.shouldOffer(status, declinedAtMillis = now, nowMillis = now))
        assertTrue(
            !ProfileShift.shouldOffer(
                status,
                declinedAtMillis = now - ProfileShift.DECLINE_QUIET_MILLIS + 1,
                nowMillis = now,
            ),
        )
        assertTrue(
            ProfileShift.shouldOffer(
                status,
                declinedAtMillis = now - ProfileShift.DECLINE_QUIET_MILLIS,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `the wording asks, names the profile and never announces a change`() {
        val sentence = ProfileShift.sentence("Дом")
        assertTrue(sentence.contains("«Дом»"), sentence)
        assertTrue(sentence.contains("Возможно"), sentence)
        assertEquals("Уровень изменился надолго", ProfileShift.TITLE)
        assertEquals("Обновить профиль", ProfileShift.UPDATE_ACTION)
        assertEquals("Оставить как есть", ProfileShift.KEEP_ACTION)

        // What the button does is said before it is pressed, and the promise
        // about raw data is explicit.
        assertTrue(ProfileShift.EXPLANATION.contains("сохранится в истории"))
        assertTrue(ProfileShift.EXPLANATION.contains("Сырые измерения"))

        val nameless = ProfileShift.sentence(null)
        assertTrue(nameless.contains("профиля"), nameless)
        assertTrue(!nameless.contains("«»"), nameless)
    }

    @Test
    fun `the closed period keeps the numbers it was closed with`() {
        val encoded = BaselineSnapshot.encode(baseline)
        val (low, high) = assertNotNull(BaselineSnapshot.decodeRange(encoded))
        assertEquals(0.14f, low, 1e-4f)
        assertEquals(0.17f, high, 1e-4f)
        assertTrue(encoded.contains("median"), encoded)
        assertTrue(encoded.contains("bucketCount"), encoded)

        assertNull(BaselineSnapshot.decodeRange(null))
        assertNull(BaselineSnapshot.decodeRange("{}"))
    }
}
