package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Совпадение одной энергии — самое слабое свидетельство; проверка по
 * нескольким линиям должна усиливать вывод, но не превращать его в
 * «обнаружен», и не опровергать нуклид отсутствием слабой линии.
 */
class LineConsistencyTest {

    private fun peak(energyKeV: Float, net: Float) = Peak(
        channel = (energyKeV / 3f).toInt(),
        energyKeV = energyKeV,
        netCounts = net,
        significance = 10f,
    )

    private val tolerance: (Float) -> Float = { 15f }

    @Test
    fun `two found lines make it a multi-line hypothesis`() {
        // Co-60: каскад 1173 и 1332 кэВ, выходы почти равны.
        val result = assertNotNull(
            LineConsistency.check(
                "Co-60",
                listOf(peak(1173.2f, 5_000f), peak(1332.5f, 4_800f)),
                tolerance,
            ),
        )
        assertEquals(LineConsistency.Support.MULTI_LINE, result.support)
        assertEquals(2, result.foundLines)
        val ratio = assertNotNull(result.ratio)
        // Отношение считается по НЕТТО-ПЛОЩАДЯМ, а не по высотам каналов.
        assertEquals(4_800.0 / 5_000.0, ratio.observed, 1e-6)
        assertTrue(ratio.sigma > 0.0)
    }

    @Test
    fun `one line alone stays the weakest evidence`() {
        val result = assertNotNull(
            LineConsistency.check("Cs-137", listOf(peak(661.7f, 9_000f)), tolerance),
        )
        // У Cs-137 одна линия в библиотеке — «поддержки несколькими линиями»
        // здесь быть не может по определению.
        assertEquals(LineConsistency.Support.SINGLE_LINE, result.support)
        assertNull(result.ratio)
    }

    @Test
    fun `a weak missing line does not count against the candidate`() {
        // Bi-214: 609 кэВ (46 %) найдена, 1764 кэВ (15 %) — нет. Это ожидаемо
        // при скромной статистике и не является противоречием.
        val result = assertNotNull(
            LineConsistency.check("Bi-214", listOf(peak(609.3f, 3_000f)), tolerance),
        )
        val weak = result.lines.first { it.energyKeV > 1_700f }
        assertTrue(!weak.found)
        assertTrue(
            weak.expectedVisible == false || result.support != LineConsistency.Support.MULTI_LINE,
            "слабая линия не должна требовать объяснений: $weak",
        )
    }

    @Test
    fun `the expected ratio is the table one, with no invented efficiency`() {
        val result = assertNotNull(
            LineConsistency.check(
                "Co-60",
                listOf(peak(1173.2f, 5_000f), peak(1332.5f, 4_800f)),
                tolerance,
            ),
        )
        val ratio = assertNotNull(result.ratio)
        // Табличное отношение — ровно частное выходов, без множителей.
        val lines = NuclideInfoLibrary.of("Co-60")!!.lines.sortedByDescending { it.intensityPercent }
        assertEquals(
            (lines[0].intensityPercent / lines[1].intensityPercent).toDouble(),
            ratio.expectedByYield,
            1e-6,
        )
        // И приложение прямо говорит, что кривой эффективности у него нет.
        assertTrue(!DetectorEfficiency.AVAILABLE)
        assertTrue(DetectorEfficiency.UNAVAILABLE_NOTE.contains("не откалибрована"))
    }

    @Test
    fun `an unknown nuclide yields nothing instead of guessing`() {
        assertNull(LineConsistency.check("Xx-999", listOf(peak(100f, 10f)), tolerance))
    }
}
