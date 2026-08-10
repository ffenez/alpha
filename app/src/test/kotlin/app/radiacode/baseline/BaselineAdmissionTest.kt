package app.radiacode.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One test per condition of spec §4.2 plus the ordering guarantee: the
 * reported reason must always be the FIRST unmet condition, otherwise the
 * user is told about a symptom instead of the cause.
 */
class BaselineAdmissionTest {

    private fun input(
        learning: Boolean = true,
        contextReliable: Boolean = true,
        ageMillis: Long = 1_000,
        experiment: Boolean = false,
        quarantineUntil: Long? = null,
        now: Long = 100_000,
        dose: Float = 0.12f,
        cps: Float = 21f,
        cpsErr: Float = 5f,
        doseErr: Float = 8f,
        frozen: Boolean = false,
    ) = AdmissionInput(
        profileLearningEnabled = learning,
        contextReliable = contextReliable,
        sampleAgeMillis = ageMillis,
        experimentActive = experiment,
        quarantineUntilMillis = quarantineUntil,
        nowMillis = now,
        doseRateMicroSvH = dose,
        countRateCps = cps,
        countRateErrPercent = cpsErr,
        doseRateErrPercent = doseErr,
        manuallyFrozen = frozen,
    )

    private fun reasonOf(input: AdmissionInput): BaselineExclusion? =
        (BaselineAdmission.evaluate(input) as? Admission.Excluded)?.reason

    @Test
    fun `a normal sample under a learning profile is admitted`() {
        assertEquals(Admission.Admitted, BaselineAdmission.evaluate(input()))
        assertNull(BaselineAdmission.evaluate(input()).storageKey)
    }

    @Test
    fun `condition 1 profile learning off`() {
        assertEquals(BaselineExclusion.LEARNING_OFF, reasonOf(input(learning = false)))
    }

    @Test
    fun `condition 2 context not reliable`() {
        assertEquals(
            BaselineExclusion.CONTEXT_UNCERTAIN,
            reasonOf(input(contextReliable = false)),
        )
    }

    @Test
    fun `condition 3 stale stream stops learning at the same threshold as the UI`() {
        val threshold = BaselineAdmission.STALE_AFTER_SECONDS * 1000L
        assertNull(reasonOf(input(ageMillis = threshold)))
        assertEquals(BaselineExclusion.STREAM_STALE, reasonOf(input(ageMillis = threshold + 1)))
    }

    @Test
    fun `condition 4 search or experiment`() {
        assertEquals(BaselineExclusion.EXPERIMENT, reasonOf(input(experiment = true)))
    }

    @Test
    fun `condition 5 quarantine window`() {
        assertEquals(
            BaselineExclusion.QUARANTINE,
            reasonOf(input(now = 100_000, quarantineUntil = 100_001)),
        )
        assertNull(reasonOf(input(now = 100_000, quarantineUntil = 100_000)))
    }

    @Test
    fun `condition 6 unusable statistics`() {
        assertEquals(BaselineExclusion.STATISTICS_UNUSABLE, reasonOf(input(cps = 0f)))
        assertEquals(BaselineExclusion.STATISTICS_UNUSABLE, reasonOf(input(dose = -1f)))
        assertEquals(BaselineExclusion.STATISTICS_UNUSABLE, reasonOf(input(dose = Float.NaN)))
        assertEquals(BaselineExclusion.STATISTICS_UNUSABLE, reasonOf(input(cpsErr = 60f)))
        assertEquals(BaselineExclusion.STATISTICS_UNUSABLE, reasonOf(input(doseErr = 51f)))
        assertNull(reasonOf(input(cpsErr = BaselineAdmission.MAX_RELATIVE_ERROR_PERCENT)))
    }

    @Test
    fun `condition 7 manual freeze`() {
        assertEquals(BaselineExclusion.MANUAL_FREEZE, reasonOf(input(frozen = true)))
    }

    @Test
    fun `the first unmet condition wins`() {
        val everything = input(
            learning = false,
            contextReliable = false,
            ageMillis = 60_000,
            experiment = true,
            quarantineUntil = 200_000,
            cps = 0f,
            frozen = true,
        )
        assertEquals(BaselineExclusion.LEARNING_OFF, reasonOf(everything))
        assertEquals(
            BaselineExclusion.CONTEXT_UNCERTAIN,
            reasonOf(everything.copy(profileLearningEnabled = true)),
        )
        assertEquals(
            BaselineExclusion.STREAM_STALE,
            reasonOf(everything.copy(profileLearningEnabled = true, contextReliable = true)),
        )
    }

    @Test
    fun `storage keys survive a round trip and are stable`() {
        BaselineExclusion.entries.forEach { reason ->
            assertEquals(reason, BaselineExclusion.fromStorage(reason.storageKey))
        }
        // Keys are an on-disk contract: pin them so a rename cannot pass review.
        assertEquals(
            listOf(
                "learning_off",
                "context_uncertain",
                "stream_stale",
                "experiment",
                "quarantine",
                "statistics_unusable",
                "manual_freeze",
            ),
            BaselineExclusion.entries.map { it.storageKey },
        )
        assertNull(BaselineExclusion.fromStorage(null))
        assertNull(BaselineExclusion.fromStorage("who_knows"))
    }

    @Test
    fun `quarantine is measured from the end of an excursion`() {
        val window = QuarantineWindow(windowMillis = 30_000L)
        assertNull(window.untilMillis)

        window.onSample(nowMillis = 1_000, deviationActive = true)
        assertEquals(31_000L, window.untilMillis)

        // Still deviating 10 s later: the deadline moves with the excursion.
        window.onSample(nowMillis = 11_000, deviationActive = true)
        assertEquals(41_000L, window.untilMillis)

        // Excursion over: the deadline stays where the episode ended.
        window.onSample(nowMillis = 12_000, deviationActive = false)
        assertEquals(41_000L, window.untilMillis)

        val quarantined = AdmissionInput(
            profileLearningEnabled = true,
            contextReliable = true,
            sampleAgeMillis = 0,
            experimentActive = false,
            quarantineUntilMillis = window.untilMillis,
            nowMillis = 40_999,
            doseRateMicroSvH = 0.1f,
            countRateCps = 20f,
            countRateErrPercent = 5f,
            doseRateErrPercent = 8f,
            manuallyFrozen = false,
        )
        assertEquals(BaselineExclusion.QUARANTINE, reasonOf(quarantined))
        assertNull(reasonOf(quarantined.copy(nowMillis = 41_000)))

        window.clear()
        assertNull(window.untilMillis)
    }

    @Test
    fun `default quarantine window is documented and non-trivial`() {
        assertTrue(BaselineAdmission.QUARANTINE_MILLIS >= 10L * 60_000L)
        assertEquals(30L * 60_000L, BaselineAdmission.QUARANTINE_MILLIS)
    }
}
