package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A background without metadata is a number, not a reference (redesign §6):
 * these tests are about the questions the metadata makes answerable.
 */
class BackgroundRecordTest {

    private fun record(
        rate: Double = 25.0,
        seconds: Int = 45,
        atMillis: Long = 0L,
        profileId: Long? = 1L,
        serial: String? = "RC-110-TEST",
        target: Int = 45,
        gapSeconds: Double = 0.0,
    ): BackgroundRecord {
        val times = LongArray(seconds) { it * 1_000L }
        val rates = DoubleArray(seconds) { i -> rate + if (i % 2 == 0) 1.0 else -1.0 }
        val window = CountWindow.reconstruct(times, rates).copy(gapSeconds = gapSeconds)
        return BackgroundRecord(
            window = window,
            atMillis = atMillis,
            targetSamples = target,
            profileId = profileId,
            profileName = "Дом",
            deviceSerial = serial,
        )
    }

    @Test
    fun `the record survives a round trip through storage`() {
        val original = record(gapSeconds = 1.5)
        val restored = assertNotNull(BackgroundRecord.decode(original.encode()))

        assertEquals(original.window.samples, restored.window.samples)
        assertEquals(original.window.seconds, restored.window.seconds, 1e-3)
        assertEquals(original.window.counts, restored.window.counts, 1e-2)
        assertEquals(original.window.gapSeconds, restored.window.gapSeconds, 1e-3)
        assertEquals(original.atMillis, restored.atMillis)
        assertEquals(original.profileId, restored.profileId)
        assertEquals(original.profileName, restored.profileName)
        assertEquals(original.deviceSerial, restored.deviceSerial)
        assertEquals(original.cps, restored.cps, 1e-3f)
        // The scatter must survive too — without it the dispersion check is gone.
        assertEquals(original.window.fanoFactor!!, restored.window.fanoFactor!!, 1e-3)
    }

    @Test
    fun `garbage and half-written values decode to no reference at all`() {
        assertNull(BackgroundRecord.decode(null))
        assertNull(BackgroundRecord.decode(""))
        assertNull(BackgroundRecord.decode("not json"))
        assertNull(BackgroundRecord.decode("""{"counts":"100"}"""))
        assertNull(
            BackgroundRecord.decode("""{"counts":"100","seconds":"0","samples":"5","atMillis":"1"}"""),
            "a zero exposure is not a reference",
        )
    }

    @Test
    fun `a fresh reference in the same place with the same device is usable`() {
        val now = BackgroundRecord.FRESH_MILLIS / 2
        assertEquals(
            BackgroundCheck.USABLE,
            record().check(now, activeProfileId = 1L, deviceSerial = "RC-110-TEST"),
        )
        assertNull(SearchBaseline.proposal(BackgroundCheck.USABLE, record()))
    }

    @Test
    fun `age, place and instrument each make the reference stop applying`() {
        val fresh = record()
        assertEquals(
            BackgroundCheck.AGED,
            fresh.check(BackgroundRecord.FRESH_MILLIS + 1, 1L, "RC-110-TEST"),
        )
        assertEquals(
            BackgroundCheck.PROFILE_CHANGED,
            fresh.check(0L, activeProfileId = 2L, deviceSerial = "RC-110-TEST"),
        )
        assertEquals(
            BackgroundCheck.DEVICE_CHANGED,
            fresh.check(0L, activeProfileId = 1L, deviceSerial = "RC-110-OTHER"),
        )
    }

    @Test
    fun `an unknown context is not evidence that the place changed`() {
        assertEquals(
            BackgroundCheck.USABLE,
            record().check(0L, activeProfileId = null, deviceSerial = null),
        )
    }

    @Test
    fun `the quality of the recording is judged, not the radiation`() {
        assertEquals(BackgroundQuality.GOOD, record().quality)
        assertEquals(BackgroundQuality.SHORT, record(seconds = 20, target = 45).quality)
        assertEquals(
            BackgroundQuality.GAPPY,
            record(gapSeconds = BackgroundRecord.MAX_GAP_SECONDS + 1).quality,
        )

        // Wild scatter around the same mean: the instrument was not still.
        val times = LongArray(45) { it * 1_000L }
        val rates = DoubleArray(45) { i -> if (i % 2 == 0) 60.0 else 5.0 }
        val restless = BackgroundRecord(
            window = CountWindow.reconstruct(times, rates),
            atMillis = 0L,
            targetSamples = 45,
            profileId = null,
            profileName = null,
            deviceSerial = null,
        )
        assertEquals(BackgroundQuality.RESTLESS, restless.quality)
        assertEquals(BackgroundCheck.LOW_QUALITY, restless.check(0L, null, null))
    }

    @Test
    fun `every unusable reference gets a reason the user can act on`() {
        val cases = listOf(
            BackgroundCheck.AGED to record(),
            BackgroundCheck.PROFILE_CHANGED to record(),
            BackgroundCheck.DEVICE_CHANGED to record(),
            BackgroundCheck.LOW_QUALITY to record(seconds = 20, target = 45),
        )
        for ((check, rec) in cases) {
            val text = assertNotNull(SearchBaseline.proposal(check, rec), "$check")
            assertTrue(text.length > 20, "$check: $text")
            assertTrue(SearchBaseline.statusLine(check).isNotBlank())
            // The reason may never turn into a safety statement (§12).
            for (forbidden in listOf("норма", "безопас", "опасн")) {
                assertTrue(!text.lowercase().contains(forbidden), "$check says «$forbidden»: $text")
            }
        }
    }

    @Test
    fun `the reference carries its own uncertainty`() {
        val rec = record()
        // σ of a 25 s⁻¹ rate averaged over 45 s is ≈ 0,75 s⁻¹.
        assertTrue(rec.sigma > 0.6f && rec.sigma < 0.9f, "${rec.sigma}")
        assertTrue(rec.cps > 24.5f && rec.cps < 25.5f, "${rec.cps}")
    }
}
