package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileFilterTest {

    private val dark = TileFilter.darkMatrix()

    @Test
    fun `paper ground turns dark`() {
        val (r, g, b) = TileFilter.apply(dark, 255, 255, 255)
        assertTrue(r < 40 && g < 40 && b < 40, "white → $r,$g,$b")
    }

    @Test
    fun `dark labels turn light`() {
        val (r, g, b) = TileFilter.apply(dark, 0, 0, 0)
        assertTrue(r > 190 && g > 190 && b > 190, "black → $r,$g,$b")
    }

    @Test
    fun `water stays blue, not orange`() {
        // Mapnik water is a pale blue; a plain inversion would make it orange.
        val (r, g, b) = TileFilter.apply(dark, 170, 211, 223)
        assertTrue(b > r, "water → $r,$g,$b should keep blue dominant over red")
        assertTrue(b < 128, "water must land on the dark side: $r,$g,$b")
    }

    @Test
    fun `forest stays green, not magenta`() {
        // Mapnik woodland; INVERT_COLORS alone would make this magenta.
        val (r, g, b) = TileFilter.apply(dark, 205, 235, 176)
        assertTrue(g > r && g > b, "forest → $r,$g,$b should keep green dominant")
    }

    @Test
    fun `hue rotation preserves luma of greys`() {
        val matrix = TileFilter.hueRotate180Matrix()
        val (r, g, b) = TileFilter.apply(matrix, 120, 120, 120)
        // ±1 for float rounding in the 4×5 multiply.
        assertTrue(r in 119..121 && g in 119..121 && b in 119..121, "grey → $r,$g,$b")
    }

    @Test
    fun `concat applies the right-hand matrix first`() {
        // invert twice = identity
        val twice = TileFilter.concat(TileFilter.invertMatrix(), TileFilter.invertMatrix())
        val (r, g, b) = TileFilter.apply(twice, 30, 90, 200)
        assertEquals(30, r)
        assertEquals(90, g)
        assertEquals(200, b)
    }
}

class TileStatusTest {

    // Счётчика тайлов на экране больше нет ни в каком режиме: осталось только
    // состояние «карта пуста, потому что тайлы не приходят».

    @Test
    fun `silence before the stall window says nothing`() {
        assertNull(TileStatus.networkHint(0, 0, 1_000))
    }

    @Test
    fun `silence past the stall window names the likely cause`() {
        assertNotNull(TileStatus.networkHint(0, 0, TileStatus.STALL_MILLIS))
    }

    @Test
    fun `only failures hint immediately`() {
        val hint = TileStatus.networkHint(0, 4, 500)
        assertNotNull(hint)
        assertTrue(hint.contains("Сеть"), hint)
    }

    @Test
    fun `a loaded tile ends the hint, failures or not`() {
        assertNull(TileStatus.networkHint(42, 0, 60_000))
        assertNull(TileStatus.networkHint(42, 3, 60_000))
    }
}
