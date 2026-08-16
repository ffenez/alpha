package app.radiacode.ui.logic

import app.radiacode.analysis.CountWindow
import app.radiacode.baseline.Baseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun baseline(
    cpsMedian: Float = 25.2f,
    accumulatedSeconds: Long = 26 * 3600L,
) = Baseline(
    doseLowMicroSvH = 0.09f,
    doseMedianMicroSvH = 0.11f,
    doseHighMicroSvH = 0.14f,
    doseP25MicroSvH = 0.10f,
    doseP75MicroSvH = 0.13f,
    doseMadMicroSvH = 0.01f,
    cpsLow = 22f,
    cpsMedian = cpsMedian,
    cpsHigh = 28f,
    accumulatedSeconds = accumulatedSeconds,
    sampleCount = accumulatedSeconds,
    bucketCount = 1560,
)

/**
 * Изученный фон — вес модели, а не проведённое измерение. Проверяется ровно
 * та граница, которая отделяет одно от другого.
 */
class AdaptiveBackgroundTest {

    @Test
    fun `the effective weight is capped, however long the profile learned`() {
        val week = AdaptiveBackground.of(baseline(accumulatedSeconds = 7 * 24 * 3600L))!!
        assertEquals(AdaptiveBackground.MAX_EFFECTIVE_SECONDS, week.effectiveExposureSeconds)
        // 25,2 × 3600 — псевдосчёты, а не 90 720 измеренных импульсов.
        assertEquals(25.2 * 3600, week.effectiveCounts, 1.0)

        // Неделя наблюдений НЕ даёт больше веса, чем час.
        val hour = AdaptiveBackground.of(baseline(accumulatedSeconds = 3_600L))!!
        assertEquals(hour.effectiveCounts, week.effectiveCounts, 1.0)
        // При этом честная величина профиля сохраняется как есть.
        assertEquals(7 * 24 * 3600L, week.observedSeconds)
    }

    @Test
    fun `a short profile is below the cap and says so by its own number`() {
        val short = AdaptiveBackground.of(baseline(accumulatedSeconds = 1_200L))!!
        assertEquals(1_200L, short.effectiveExposureSeconds)
        assertTrue(short.effectiveCounts < 25.2 * AdaptiveBackground.MAX_EFFECTIVE_SECONDS)
    }

    @Test
    fun `too little observation is not a background at all`() {
        assertNull(AdaptiveBackground.of(baseline(accumulatedSeconds = 60L)))
        assertNull(AdaptiveBackground.of(baseline(cpsMedian = 0f)))
        assertNull(AdaptiveBackground.of(null))
    }
}

/**
 * Записанный эталон и изученный фон не подменяют друг друга: у них разная
 * природа, и выбирается между ними по состоянию эталона, а не по удобству.
 */
class SearchReferenceTest {

    private val learned = AdaptiveBackground.of(baseline())!!

    private val record = BackgroundRecord(
        window = CountWindow(
            counts = 25.0 * 45,
            seconds = 45.0,
            samples = 45,
        ),
        atMillis = 1_000L,
        targetSamples = 45,
        profileId = 1L,
        profileName = "Дом",
        deviceSerial = "RC-110-42",
    )

    @Test
    fun `a usable recorded reference wins`() {
        val reference = SearchReferences.choose(record, BackgroundCheck.USABLE, learned)
        assertIs<SearchReference.Recorded>(reference)
    }

    /**
     * Эталон, который больше не годится, молча не используется — но и поиск
     * из-за этого не останавливается: место приложение знает и без записи.
     */
    @Test
    fun `an unusable reference steps aside for the learned background`() {
        for (check in listOf(
            BackgroundCheck.AGED,
            BackgroundCheck.PROFILE_CHANGED,
            BackgroundCheck.DEVICE_CHANGED,
            BackgroundCheck.LOW_QUALITY,
        )) {
            assertIs<SearchReference.Learned>(
                SearchReferences.choose(record, check, learned),
                "$check",
            )
        }
    }

    @Test
    fun `without a profile the stale record is still better than nothing`() {
        assertIs<SearchReference.Recorded>(
            SearchReferences.choose(record, BackgroundCheck.AGED, learned = null),
        )
        assertIs<SearchReference.None>(
            SearchReferences.choose(record = null, check = null, learned = null),
        )
    }

    @Test
    fun `nothing recorded, the learned background carries the search`() {
        val reference = SearchReferences.choose(null, null, learned)
        assertIs<SearchReference.Learned>(reference)
        assertEquals(25.2f, reference.rateCps)
    }
}
