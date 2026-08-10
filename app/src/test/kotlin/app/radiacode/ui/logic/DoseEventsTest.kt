package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoseEventsTest {

    /**
     * A column with a level [level] and, optionally, a spike [spike] far above
     * its own bulk. The quantiles are set explicitly so the marker rule is
     * tested on its own definition, not on the folding.
     */
    private fun column(
        index: Int,
        level: Float,
        spike: Float = level,
        spread: Float = 0f,
        samples: Int = 60,
    ) = ChartBucket(
        startMillis = index * 60_000L,
        endMillis = (index + 1) * 60_000L,
        min = level - spread,
        max = spike,
        median = level,
        q10 = level - spread,
        q25 = level - spread / 2f,
        q75 = level + spread / 2f,
        q90 = level + spread,
        sampleCount = samples,
        minAtMillis = index * 60_000L,
        maxAtMillis = index * 60_000L + 30_000L,
        extremeWindowMillis = 1_000L,
    )

    // --- extremum markers (spec §7, §21) ---

    @Test
    fun `a spike above the alarm level is marked as an alarm extremum`() {
        val quiet = column(0, level = 0.10f, spread = 0.01f)
        val spiky = column(1, level = 0.10f, spike = 0.50f, spread = 0.01f)
        val markers = DoseExtremes.markers(
            buckets = listOf(quiet, spiky),
            alarmMicroSvH = 0.30f,
            baselineP90MicroSvH = 0.14f,
        )
        assertEquals(1, markers.size)
        assertEquals(1, markers.single().bucketIndex)
        assertEquals(DoseReference.ALARM_L1, markers.single().reference)
        assertEquals(0.50f, markers.single().valueMicroSvH)
        assertEquals(spiky.maxAtMillis, markers.single().atMillis)
    }

    @Test
    fun `a spike above the profile P90 but below the alarm names the profile`() {
        val spiky = column(0, level = 0.10f, spike = 0.20f, spread = 0.01f)
        val markers = DoseExtremes.markers(listOf(spiky), 0.30f, 0.14f)
        assertEquals(DoseReference.BASELINE_P90, markers.single().reference)
    }

    @Test
    fun `an elevated but steady column is a level, not a transient`() {
        // Everything in the column sits above the profile P90, but the top
        // sample is close to the column's own Q90 — that is a step, and the
        // median line already shows it.
        val steady = column(0, level = 0.40f, spike = 0.42f, spread = 0.02f)
        assertFalse(DoseExtremes.standsOut(steady))
        assertTrue(DoseExtremes.markers(listOf(steady), 0.30f, 0.14f).isEmpty())
    }

    @Test
    fun `a single-sample column is never called a transient`() {
        val single = ChartBucket(
            startMillis = 0,
            endMillis = 60_000,
            min = 9f,
            max = 9f,
            median = 9f,
            q10 = 9f,
            q25 = 9f,
            q75 = 9f,
            q90 = 9f,
            sampleCount = 1,
        )
        assertNull(DoseExtremes.classify(single, 0.30f, 0.14f))
    }

    @Test
    fun `without a reference nothing is notable`() {
        val spiky = column(0, level = 0.10f, spike = 0.50f, spread = 0.01f)
        assertTrue(
            DoseExtremes.markers(listOf(spiky), alarmMicroSvH = null, baselineP90MicroSvH = null)
                .isEmpty(),
        )
        // A zero alarm level means «not configured», not «everything alarms».
        assertTrue(DoseExtremes.markers(listOf(spiky), 0f, 0f).isEmpty())
    }

    @Test
    fun `the marker rule is the Tukey step above the column's own Q90`() {
        val iqr = 0.02f
        val level = 0.10f
        // Q90 = level + spread; the step is 1.5 · IQR with IQR = spread.
        val justUnder = column(0, level = level, spike = level + iqr + 1.4f * iqr, spread = iqr)
        val justOver = column(0, level = level, spike = level + iqr + 1.6f * iqr, spread = iqr)
        assertFalse(DoseExtremes.standsOut(justUnder))
        assertTrue(DoseExtremes.standsOut(justOver))
        assertEquals(1.5f, DoseExtremes.IQR_STEP)
    }

    // --- episodes (spec §20) ---

    @Test
    fun `an episode grows from the journal event across the columns above the level`() {
        val columns = (0 until 10).map { i ->
            val v = if (i in 3..6) 0.9f else 0.1f
            ChartBucket(
                startMillis = i * 1_000L,
                endMillis = (i + 1) * 1_000L,
                min = v,
                max = v,
                median = v,
                sampleCount = 1,
                maxAtMillis = i * 1_000L,
            )
        }
        val episodes = DoseEpisodes.around(columns, listOf(4_500L), alarmMicroSvH = 0.3f)
        assertEquals(1, episodes.size)
        val episode = episodes.single()
        assertEquals(3_000L, episode.fromMillis)
        assertEquals(7_000L, episode.toMillis)
        assertEquals(0.9f, episode.peak)
        assertEquals(3_000L, episode.peakAtMillis)
        assertEquals(DoseReference.ALARM_L1, episode.reference)
    }

    @Test
    fun `an episode above the profile P90 only is a different class of event`() {
        val columns = (0 until 6).map { i ->
            val v = if (i in 2..3) 0.20f else 0.10f
            ChartBucket(i * 1_000L, (i + 1) * 1_000L, v, v, v, sampleCount = 1)
        }
        val episodes = DoseEpisodes.around(
            buckets = columns,
            eventTimesMillis = listOf(2_500L),
            alarmMicroSvH = 0.30f,
            baselineP90MicroSvH = 0.14f,
        )
        val episode = episodes.single()
        assertEquals(DoseReference.BASELINE_P90, episode.reference)
        // It grows along the P90 crossing, not along the alarm level.
        assertEquals(2_000L, episode.fromMillis)
        assertEquals(4_000L, episode.toMillis)
    }

    @Test
    fun `the two references are worded differently and both name the reference`() {
        assertEquals("выше порога L1", referenceWording(DoseReference.ALARM_L1))
        assertEquals(
            "выше исторического P90 профиля",
            referenceWording(DoseReference.BASELINE_P90),
        )
        assertEquals("> L1", referenceWordingShort(DoseReference.ALARM_L1))
        assertEquals("> P90 профиля", referenceWordingShort(DoseReference.BASELINE_P90))
        for (reference in DoseReference.entries) {
            val text = referenceWording(reference)
            assertTrue(text.contains("L1") || text.contains("P90"), text)
        }
    }

    @Test
    fun `two events inside one run produce one band`() {
        val columns = (0 until 6).map { i ->
            val v = if (i in 1..4) 0.9f else 0.1f
            ChartBucket(i * 1_000L, (i + 1) * 1_000L, v, v, v, sampleCount = 1)
        }
        val episodes = DoseEpisodes.around(columns, listOf(1_500L, 3_500L), 0.3f)
        assertEquals(1, episodes.size)
    }

    @Test
    fun `an event with no column above the level still gets a one-column band`() {
        val columns = (0 until 4).map { i ->
            ChartBucket(i * 1_000L, (i + 1) * 1_000L, 0.1f, 0.1f, 0.1f, sampleCount = 1)
        }
        val episodes = DoseEpisodes.around(columns, listOf(2_500L), alarmMicroSvH = 5f)
        assertEquals(1, episodes.size)
        assertEquals(2_000L, episodes.single().fromMillis)
        assertEquals(3_000L, episodes.single().toMillis)
        assertEquals(DoseReference.ALARM_L1, episodes.single().reference)
    }

    @Test
    fun `without any reference an event cannot be classified and is dropped`() {
        val columns = listOf(ChartBucket(0, 1_000, 0.1f, 0.1f, 0.1f, sampleCount = 1))
        assertTrue(
            DoseEpisodes.around(columns, listOf(500L), alarmMicroSvH = null).isEmpty(),
        )
    }

    @Test
    fun `events outside the columns are ignored`() {
        val columns = listOf(ChartBucket(0, 1_000, 0.1f, 0.1f, 0.1f, sampleCount = 1))
        assertTrue(DoseEpisodes.around(columns, listOf(99_000L), 0.3f).isEmpty())
    }
}
