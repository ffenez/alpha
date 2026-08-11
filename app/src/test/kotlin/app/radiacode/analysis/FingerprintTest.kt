package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Отпечаток места должен различать две ситуации, которые один порог путает:
 * «того же поля стало больше» и «пришло другое поле» (ADR 005).
 */
class FingerprintTest {

    private fun spectrum(scale: Double = 1.0, linePeak: Int = 0, size: Int = 1024): List<Int> {
        val raw = List(size) { i -> 1.0 / (1.0 + i * 0.02) }
        val sum = raw.sum()
        return List(size) { i ->
            val base = (raw[i] / sum * 200_000 * scale).toInt()
            if (i == 400) base + linePeak else base
        }
    }

    private fun reference(
        doseMedian: Float = 0.15f,
        cpsMedian: Float = 22f,
        spectrum: List<Int> = spectrum(),
    ) = FingerprintReference(
        doseLowMicroSvH = doseMedian * 0.93f,
        doseMedianMicroSvH = doseMedian,
        doseHighMicroSvH = doseMedian * 1.13f,
        cpsLow = cpsMedian * 0.9f,
        cpsMedian = cpsMedian,
        cpsHigh = cpsMedian * 1.1f,
        spectrum = spectrum,
        spectrumSeconds = 72 * 3600L,
        createdAtMillis = 1_000_000L,
        accumulatedSeconds = 72 * 3600L,
    )

    private fun window(
        doseMedian: Float = 0.15f,
        cpsMedian: Float = 22f,
        spectrum: List<Int> = spectrum(),
        seconds: Long = 3600L,
    ) = FingerprintWindow(
        doseMedianMicroSvH = doseMedian,
        cpsMedian = cpsMedian,
        spectrum = spectrum,
        spectrumSeconds = seconds,
        seconds = seconds,
    )

    @Test
    fun `the same place matches itself on every dimension`() {
        val comparison = Fingerprint.compare(window(), reference())
        assertTrue(comparison.verdicts.all { it.state == FingerprintState.SAME }, "${comparison.verdicts}")
        assertTrue(!comparison.anyChanged)
        assertEquals("Совпадает с эталоном этого места", Fingerprint.headline(comparison))
    }

    /** Сценарий A: того же поля стало больше. */
    @Test
    fun `more of the same field moves intensity but not the spectral character`() {
        val comparison = Fingerprint.compare(
            window = window(
                doseMedian = 0.15f * 2.75f,
                cpsMedian = 22f * 2.8f,
                spectrum = spectrum(scale = 2.8),
            ),
            reference = reference(),
        )
        assertEquals(FingerprintState.CHANGED, comparison.of(FingerprintDimension.DOSE)?.state)
        assertEquals(FingerprintState.CHANGED, comparison.of(FingerprintDimension.COUNT_RATE)?.state)
        // Ярче — не значит другое: суммы в χ² сокращаются.
        assertEquals(FingerprintState.SAME, comparison.of(FingerprintDimension.SPECTRUM)?.state)
        assertEquals(
            "Интенсивность отличается от эталона, энергетический характер — нет",
            Fingerprint.headline(comparison),
        )
        // Жёсткость почти не двигается — ради этого она и нужна.
        val change = assertNotNull(comparison.hardnessChangePercent)
        assertTrue(kotlin.math.abs(change) <= Fingerprint.HARDNESS_FLAT_PERCENT, "$change %")
    }

    /** Сценарий B: изменился и характер. */
    @Test
    fun `a new line changes the shape and shows up in hardness`() {
        val comparison = Fingerprint.compare(
            window = window(
                doseMedian = 0.27f,
                cpsMedian = 25f,
                spectrum = spectrum(linePeak = 40_000),
            ),
            reference = reference(),
        )
        assertEquals(FingerprintState.CHANGED, comparison.of(FingerprintDimension.SPECTRUM)?.state)
        assertTrue(comparison.anyChanged)
        assertEquals(
            "Изменились интенсивность и энергетический характер регистрируемого излучения",
            Fingerprint.headline(comparison),
        )
        val line = assertNotNull(Fingerprint.hardnessLine(comparison))
        assertTrue(line.contains("выше эталона"), line)
        assertTrue(line.contains("не голосует в выводе"), line)
    }

    @Test
    fun `a changed shape at unchanged intensity is its own sentence`() {
        val comparison = Fingerprint.compare(
            window = window(spectrum = spectrum(linePeak = 40_000)),
            reference = reference(),
        )
        assertEquals(FingerprintState.SAME, comparison.of(FingerprintDimension.DOSE)?.state)
        assertEquals(FingerprintState.CHANGED, comparison.of(FingerprintDimension.SPECTRUM)?.state)
        assertEquals(
            "Интенсивность как в эталоне, а энергетический характер изменился",
            Fingerprint.headline(comparison),
        )
    }

    @Test
    fun `without a reference nothing is evaluated, and it says so`() {
        val comparison = Fingerprint.compare(window(), reference = null)
        assertTrue(comparison.verdicts.all { it.state == FingerprintState.NOT_EVALUATED })
        assertTrue(!comparison.evaluated)
        assertEquals("Эталон этого места ещё не создан", Fingerprint.headline(comparison))
        assertNull(Fingerprint.hardnessLine(comparison))
    }

    @Test
    fun `a short window says so instead of claiming a match`() {
        val comparison = Fingerprint.compare(
            window = window(seconds = Fingerprint.MIN_WINDOW_SECONDS - 1),
            reference = reference(),
        )
        assertTrue(comparison.verdicts.all { it.state == FingerprintState.NOT_ENOUGH_DATA })
        assertEquals(
            "Пока недостаточно измерений для сравнения с эталоном",
            Fingerprint.headline(comparison),
        )
    }

    @Test
    fun `a different channel grid is not a comparison`() {
        val comparison = Fingerprint.compare(
            window = window(spectrum = spectrum(size = 512)),
            reference = reference(),
        )
        val shape = assertNotNull(comparison.of(FingerprintDimension.SPECTRUM))
        assertEquals(FingerprintState.NOT_ENOUGH_DATA, shape.state)
        assertTrue(shape.detail.contains("сеткой каналов"), shape.detail)
    }

    @Test
    fun `every verdict carries the numbers it stands on`() {
        val comparison = Fingerprint.compare(window(doseMedian = 0.30f), reference())
        val dose = assertNotNull(comparison.of(FingerprintDimension.DOSE))
        assertTrue(dose.detail.contains("сейчас 0,30"), dose.detail)
        assertTrue(dose.detail.contains("эталон 0,14–0,17"), dose.detail)
        assertEquals(100, dose.changePercent)
    }

    @Test
    fun `no wording promises a place, a cause or a danger`() {
        val texts = FingerprintDimension.entries.map { it.title } + listOf(
            Fingerprint.CAVEAT,
            Fingerprint.headline(Fingerprint.compare(window(doseMedian = 0.30f), reference())),
            Fingerprint.hardnessLine(
                Fingerprint.compare(window(doseMedian = 0.30f), reference()),
            ).orEmpty(),
        )
        // Whole words: the caveat itself has to be able to say «а не оценка
        // опасности», which denies the claim rather than making it.
        val forbidden = listOf(
            Regex("\\bопасн(о|ый|ая|ое)\\b"),
            Regex("\\bбезопасн\\w*\\b"),
            Regex("источник найден"),
            Regex("\\bнуклид\\w*\\b"),
            Regex("\\bизотоп\\w*\\b"),
        )
        for (text in texts) {
            val lower = text.lowercase()
            for (word in forbidden) {
                assertTrue(!word.containsMatchIn(lower), "«$word» in: $text")
            }
        }
        assertTrue(Fingerprint.CAVEAT.contains("не доказывает"), Fingerprint.CAVEAT)
        assertTrue(Fingerprint.CAVEAT.contains("не называет причину"), Fingerprint.CAVEAT)
    }

    @Test
    fun `maturity thresholds are stated, not implied`() {
        assertTrue(Fingerprint.MATURITY_SECONDS >= 3 * 3600L)
        assertTrue(Fingerprint.MATURITY_SPECTRUM_COUNTS > 0)
        assertTrue(Fingerprint.MIN_WINDOW_SECONDS in 60L..3600L)
    }
}
