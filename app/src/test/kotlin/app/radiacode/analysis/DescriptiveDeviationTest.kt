package app.radiacode.analysis

import app.radiacode.baseline.Baseline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DescriptiveDeviationTest {

    /** Profile: P10 0.08, P25 0.10, median 0.12, P75 0.14, P90 0.16 µSv/h. */
    private val profile = Baseline(
        doseLowMicroSvH = 0.08f,
        doseMedianMicroSvH = 0.12f,
        doseHighMicroSvH = 0.16f,
        doseP25MicroSvH = 0.10f,
        doseP75MicroSvH = 0.14f,
        doseMadMicroSvH = 0.02f,
        cpsLow = 8f,
        cpsMedian = 12f,
        cpsHigh = 16f,
        accumulatedSeconds = 36_000,
        sampleCount = 36_000,
        bucketCount = 600,
    )

    private fun window(
        median: Float = 0.12f,
        p25: Float = 0.11f,
        p75: Float = 0.13f,
        max: Float = 0.15f,
        observations: Int = 60,
        measuredSeconds: Long = 900,
        secondsAboveP90: Long = 0,
    ) = WindowSummary(
        medianMicroSvH = median,
        p25MicroSvH = p25,
        p75MicroSvH = p75,
        maxMicroSvH = max,
        observations = observations,
        measuredSeconds = measuredSeconds,
        secondsAboveProfileP90 = secondsAboveP90,
    )

    @Test
    fun `a window inside the profile says nothing`() {
        val statements = DescriptiveDeviation.statements(window(), profile)
        assertNotNull(statements)
        assertTrue(statements.isEmpty())
        assertEquals("в обычном диапазоне этого профиля", DescriptiveDeviation.headline(statements))
    }

    @Test
    fun `too few observations is not a comparison at all`() {
        val thin = window(observations = DescriptiveDeviation.MIN_OBSERVATIONS - 1)
        assertNull(DescriptiveDeviation.statements(thin, profile))
        assertEquals("мало измерений для сравнения с профилем", DescriptiveDeviation.headline(null))
    }

    @Test
    fun `median above P90 is reported against the band, with its numbers`() {
        val statements = DescriptiveDeviation.statements(window(median = 0.20f), profile)!!
        val band = statements.single { it.kind == DeviationKind.OUTSIDE_PROFILE_BAND }
        // §3: «исторический P10–P90» → «обычный диапазон этого места»; сама
        // P-нотация никуда не делась — она в числах строкой ниже.
        assertEquals("медиана за это время выше обычного диапазона места", band.text)
        assertEquals(
            listOf("медиана окна", "P10 профиля", "P90 профиля"),
            band.numbers.map { it.label },
        )
        assertEquals(0.20, band.numbers.first().value, 1e-6)
        assertTrue(band.numbers.all { it.unit == DeviationUnit.MICRO_SV_PER_HOUR })
        // The band statement replaces the softer shift statement, never doubles it.
        assertTrue(statements.none { it.kind == DeviationKind.MEDIAN_SHIFT })
    }

    @Test
    fun `median below P10 is reported the same way, downwards`() {
        val statements = DescriptiveDeviation.statements(window(median = 0.05f, max = 0.06f), profile)!!
        val band = statements.single { it.kind == DeviationKind.OUTSIDE_PROFILE_BAND }
        assertEquals("медиана за это время ниже обычного диапазона места", band.text)
    }

    @Test
    fun `a shift out of the profile's middle half is its own softer statement`() {
        val statements = DescriptiveDeviation.statements(window(median = 0.15f), profile)!!
        val shift = statements.single { it.kind == DeviationKind.MEDIAN_SHIFT }
        assertEquals("медиана сместилась вверх относительно обычной середины профиля", shift.text)
        assertEquals(
            listOf("медиана окна", "медиана профиля", "P25 профиля", "P75 профиля"),
            shift.numbers.map { it.label },
        )
    }

    @Test
    fun `a wider middle half is reported with both spreads`() {
        // Profile IQR = 0.04; the window's is 0.10 > 1.5 × 0.04.
        val statements = DescriptiveDeviation.statements(
            window(p25 = 0.07f, p75 = 0.17f, max = 0.18f),
            profile,
        )!!
        val spread = statements.single { it.kind == DeviationKind.SPREAD_WIDER }
        assertEquals("разброс измерений в окне шире обычного для профиля", spread.text)
        assertEquals(0.10, spread.numbers[0].value, 1e-5)
        assertEquals(0.04, spread.numbers[1].value, 1e-5)
    }

    @Test
    fun `spread just above the profile is not called wider`() {
        // IQR = 0.05 = 1.25 × profile, below the documented factor.
        val statements = DescriptiveDeviation.statements(window(p25 = 0.09f, p75 = 0.14f), profile)!!
        assertTrue(statements.none { it.kind == DeviationKind.SPREAD_WIDER })
    }

    @Test
    fun `a brief excursion above P90 is called a short spike, with its duration`() {
        val statements = DescriptiveDeviation.statements(
            window(max = 0.60f, secondsAboveP90 = 40),
            profile,
        )!!
        val spike = statements.single { it.kind == DeviationKind.SHORT_SPIKE }
        assertEquals("короткий всплеск: выше P90 профиля недолго, уровень не удержался", spike.text)
        val duration = spike.numbers.single { it.label == "время выше P90" }
        assertEquals(40.0, duration.value, 1e-9)
        assertEquals(DeviationUnit.SECONDS, duration.unit)
    }

    @Test
    fun `an excursion that held is not called a spike`() {
        val statements = DescriptiveDeviation.statements(
            window(max = 0.60f, secondsAboveP90 = DescriptiveDeviation.SHORT_SPIKE_MAX_SECONDS + 1),
            profile,
        )!!
        assertTrue(statements.none { it.kind == DeviationKind.SHORT_SPIKE })
    }

    @Test
    fun `no statement ever claims significance`() {
        val forbidden = listOf(
            "σ", "сигм", "значим", "достоверн", "вероятн", "p-value", "p=", "%",
            "опасн", "норма", "безопасн",
        )
        val all = listOf(
            DescriptiveDeviation.statements(window(median = 0.20f), profile),
            DescriptiveDeviation.statements(window(median = 0.05f, max = 0.06f), profile),
            DescriptiveDeviation.statements(window(median = 0.15f), profile),
            DescriptiveDeviation.statements(window(p25 = 0.07f, p75 = 0.17f, max = 0.18f), profile),
            DescriptiveDeviation.statements(window(max = 0.6f, secondsAboveP90 = 30), profile),
        ).flatMap { it.orEmpty() }
        assertTrue(all.isNotEmpty())
        for (statement in all) {
            val haystack = (statement.text + statement.numbers.joinToString { it.label }).lowercase()
            for (word in forbidden) {
                assertTrue(word !in haystack, "«$word» leaked into: ${statement.text}")
            }
        }
    }
}
