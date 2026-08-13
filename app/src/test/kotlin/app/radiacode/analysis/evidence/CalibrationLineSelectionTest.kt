package app.radiacode.analysis.evidence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Отбор опорных линий: что прибор реально разделяет, а что для него одна
 * структура. Проверяется ПРАВИЛО, а не список — на другом разрешении ответы
 * обязаны меняться сами.
 */
class CalibrationLineSelectionTest {

    private val rc110 = SqrtResolution(0.084)

    private fun candidate(energyKeV: Double) =
        CalibrationLineSelection.evaluateAll(rc110).first { it.line.energyKeV == energyKeV }

    @Test
    fun `four lines this instrument separates are usable`() {
        val usable = CalibrationLineSelection.usable(rc110).map { it.line.energyKeV }
        for (energy in listOf(1120.3, 1460.8, 1764.5, 2614.5)) {
            assertTrue(energy in usable, "$energy должна быть пригодна: $usable")
        }
    }

    @Test
    fun `merged pairs are rejected and the blocker is named`() {
        // 583,2 и 609,3 расходятся на 26 кэВ при FWHM около 53: это половина
        // ширины, то есть одна структура.
        val bi609 = candidate(609.3)
        assertEquals(LineRejection.BLENDED_WITH_OTHER_ACTIVITY, bi609.rejection)
        assertTrue(bi609.blockers.any { it.energyKeV == 583.2 }, "${bi609.blockers}")

        // 238,6 и 242,0 — три килоэлектронвольта и разные ряды.
        val pb238 = candidate(238.6)
        assertEquals(LineRejection.BLENDED_WITH_OTHER_ACTIVITY, pb238.rejection)
        assertTrue(pb238.blockers.any { it.energyKeV == 242.0 })

        // 338,3 (Ac-228) и 351,9 (Pb-214) — четырнадцать.
        val pb352 = candidate(351.9)
        assertEquals(LineRejection.BLENDED_WITH_OTHER_ACTIVITY, pb352.rejection)
        assertTrue(pb352.blockers.any { it.energyKeV == 338.3 })
    }

    @Test
    fun `a group of own-chain lines is rejected by the predicted shift`() {
        // 1377,7 + 1401,5 + 1408,0 — все Bi-214: отношение известно, но
        // центроид слияния уезжает слишком далеко от табличной энергии.
        val group = candidate(1377.7)
        assertEquals(LineRejection.BLEND_SHIFTS_CENTROID, group.rejection)
    }

    @Test
    fun `annihilation with its unpredictable yield blocks the 510 keV line`() {
        val tl511 = candidate(510.8)
        assertEquals(LineRejection.BLENDED_WITH_OTHER_ACTIVITY, tl511.rejection)
        assertTrue(tl511.blockers.any { it.nuclide == BackgroundLineInventory.ANNIHILATION })
    }

    @Test
    fun `a small predicted blend shift stays inside the tolerance and is reported`() {
        // У 1764,5 рядом 1729,6 того же Bi-214: линия годится, но сдвиг
        // центроида по ядерным данным ненулевой и обязан быть назван.
        val bi1764 = candidate(1764.5)
        assertTrue(bi1764.usable)
        assertTrue(bi1764.blendBiasKeV < 0.0, "${bi1764.blendBiasKeV}")
        assertTrue(kotlin.math.abs(bi1764.blendBiasKeV) < 0.1 * bi1764.expectedFwhmKeV)
    }

    @Test
    fun `a wider detector separates fewer lines`() {
        // Правило вычисляется: вдвое худшее разрешение обязано сократить набор.
        val wide = CalibrationLineSelection.usable(SqrtResolution(0.168)).size
        val narrow = CalibrationLineSelection.usable(rc110).size
        assertTrue(wide < narrow, "wide=$wide narrow=$narrow")
    }
}
