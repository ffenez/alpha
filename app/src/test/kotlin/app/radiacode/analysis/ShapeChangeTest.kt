package app.radiacode.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole point of this test is the property a plain «спектр вырос» check
 * would not have: **brightness must not look like a change of shape**
 * (redesign §13).
 */
class ShapeChangeTest {

    /** A smooth continuum on the 96-band grid, scaled to [total] counts. */
    private fun continuum(total: Double, bands: Int = 96): DoubleArray {
        val raw = DoubleArray(bands) { i -> 1.0 / (1.0 + i * 0.15) }
        val sum = raw.sum()
        return DoubleArray(bands) { i -> raw[i] / sum * total }
    }

    /** The same continuum with a line added at [band]. */
    private fun withLine(total: Double, band: Int, lineFraction: Double): DoubleArray {
        val base = continuum(total * (1.0 - lineFraction))
        base[band] += total * lineFraction
        return base
    }

    @Test
    fun `the same shape at a different brightness is not a change of shape`() {
        val reference = continuum(4_000.0)
        val brighter = continuum(40_000.0)
        val result = ShapeChange.compare(reference, brighter)

        assertEquals(ShapeVerdict.CONSISTENT, result.verdict, "z = ${result.z}")
        assertTrue(result.z < ShapeChange.Z_CHANGED, "z = ${result.z}")
    }

    @Test
    fun `a new line on top of the same continuum is a change of shape`() {
        val reference = continuum(20_000.0)
        val withPeak = withLine(20_000.0, band = 60, lineFraction = 0.15)
        val result = ShapeChange.compare(reference, withPeak)

        assertEquals(ShapeVerdict.CHANGED, result.verdict, "z = ${result.z}")
        assertTrue(result.z > ShapeChange.Z_CHANGED)
        assertTrue(result.bins >= ShapeChange.MIN_BINS)
        assertEquals(result.bins - 1, result.degreesOfFreedom)
    }

    @Test
    fun `thin data answers I do not know, never consistent`() {
        val thin = continuum(ShapeChange.MIN_TOTAL_COUNTS - 1)
        val fat = continuum(20_000.0)
        assertEquals(ShapeVerdict.NOT_ENOUGH_DATA, ShapeChange.compare(thin, fat).verdict)
        assertEquals(ShapeVerdict.NOT_ENOUGH_DATA, ShapeChange.compare(fat, thin).verdict)
        assertEquals(
            ShapeVerdict.NOT_ENOUGH_DATA,
            ShapeChange.compare(DoubleArray(96), DoubleArray(96)).verdict,
        )
    }

    @Test
    fun `merging keeps every bin above the minimum and loses no counts`() {
        val reference = continuum(5_000.0)
        val excursion = continuum(5_000.0)
        val result = ShapeChange.compare(reference, excursion)

        // Nothing may be thrown away: the totals in the result are the raw ones.
        assertEquals(5_000.0, result.referenceCounts, 1e-6)
        assertEquals(5_000.0, result.excursionCounts, 1e-6)
        assertTrue(result.bins in ShapeChange.MIN_BINS..96, "${result.bins}")
    }

    @Test
    fun `identical spectra produce no evidence at all`() {
        val same = continuum(10_000.0)
        val result = ShapeChange.compare(same, same.copyOf())
        assertEquals(0.0, result.chiSquare, 1e-9)
        assertEquals(0.0, result.z, 1e-9, "agreement is never negative evidence")
        assertEquals(ShapeVerdict.CONSISTENT, result.verdict)
    }

    @Test
    fun `the research line never claims a nuclide`() {
        val changed = ShapeChange.compare(
            continuum(20_000.0),
            withLine(20_000.0, band = 60, lineFraction = 0.15),
        )
        val text = ShapeChange.detail(changed)
        assertTrue(text.contains("χ²"), text)
        for (word in listOf("изотоп", "нуклид", "источник", "обнаруж")) {
            assertTrue(!text.lowercase().contains(word), "«$word» in: $text")
        }

        val thin = ShapeChange.compare(continuum(10.0), continuum(10.0))
        assertTrue(ShapeChange.detail(thin).contains("мало"), ShapeChange.detail(thin))
    }
}
