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

    /**
     * Главное свойство: вес модели ограничен СОБСТВЕННЫМ разбросом места, а не
     * длиной наблюдений. При 25,2 с⁻¹ и P10–P90 22–28 счёт здесь гуляет на
     * ±10 % сам по себе, и вес соответствует секундам, а не часу: иначе любое
     * +2 %, которое в этом месте бывает просто так, объявлялось бы событием.
     */
    @Test
    fun `the weight follows the spread of the place, not the length of learning`() {
        val week = AdaptiveBackground.of(baseline(accumulatedSeconds = 7 * 24 * 3600L))!!
        val sigma = week.spreadSigmaCps
        val expected = (25.2f / (sigma * sigma)).toLong()
        assertEquals(expected, week.effectiveExposureSeconds)
        assertTrue(
            week.effectiveExposureSeconds < AdaptiveBackground.MAX_EFFECTIVE_SECONDS,
            "разброс места обязан связывать раньше часового потолка",
        )

        // Неделя наблюдений НЕ даёт больше веса, чем час.
        val hour = AdaptiveBackground.of(baseline(accumulatedSeconds = 3_600L))!!
        assertEquals(hour.effectiveCounts, week.effectiveCounts, 1.0)
        // При этом честная величина профиля сохраняется как есть.
        assertEquals(7 * 24 * 3600L, week.observedSeconds)
    }

    /** Место без разброса — тогда связывает часовой потолок. */
    @Test
    fun `an impossibly steady place is still capped by the hour`() {
        val steady = AdaptiveBackground(
            cps = 25.2f,
            low = 25.2f,
            high = 25.2f,
            observedSeconds = 7 * 24 * 3600L,
        )
        assertEquals(
            AdaptiveBackground.MAX_EFFECTIVE_SECONDS,
            steady.effectiveExposureSeconds,
        )
    }

    @Test
    fun `a short profile never claims more than it observed`() {
        val short = AdaptiveBackground.of(baseline(accumulatedSeconds = 1_200L))!!
        assertTrue(short.effectiveExposureSeconds <= 1_200L)
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
