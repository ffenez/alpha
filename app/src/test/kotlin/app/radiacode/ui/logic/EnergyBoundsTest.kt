package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyWindowSpec
import app.radiacode.analysis.EnergyWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Границы диапазонов как цепочка чисел: движение ручки, пресеты и — главное —
 * чтение настройки, записанной прошлой версией приложения.
 */
class EnergyBoundsTest {

    private val defaults = listOf(100f, 300f, 700f, 1500f)

    @Test
    fun `three ranges are four bounds and back`() {
        assertEquals(defaults, EnergyBounds.boundsOf(EnergyWindows.DEFAULTS))
        assertEquals(EnergyWindows.DEFAULTS, EnergyBounds.toSpecs(defaults))
        assertEquals(defaults, EnergyBounds.defaults())
    }

    @Test
    fun `the stored setting of the previous version is read without loss`() {
        // Формат на диске не менялся: пары «начало:конец».
        val legacy = "100:300,300:700,700:1500"
        val specs = EnergyWindows.parse(legacy)
        assertEquals(EnergyWindows.DEFAULTS, specs)
        assertEquals(defaults, EnergyBounds.parseStored(legacy))
        // И обратно — та же строка, то есть обновление не переписывает настройку.
        assertEquals(legacy, EnergyBounds.formatStored(EnergyBounds.parseStored(legacy)))
    }

    @Test
    fun `a custom two-window setting survives the round trip`() {
        val legacy = "50:250,250:900"
        assertEquals(listOf(50f, 250f, 900f), EnergyBounds.parseStored(legacy))
        assertEquals(legacy, EnergyBounds.formatStored(EnergyBounds.parseStored(legacy)))
    }

    @Test
    fun `a gap left by the old editor stays visible instead of disappearing`() {
        // Прежний редактор позволял оставить дыру между окнами. Она не
        // исчезает молча: пропущенный кусок становится видимым диапазоном.
        val gapped = listOf(EnergyWindowSpec(100f, 300f), EnergyWindowSpec(700f, 1500f))
        assertEquals(listOf(100f, 300f, 700f, 1500f), EnergyBounds.boundsOf(gapped))
    }

    @Test
    fun `a bound never overtakes its neighbour`() {
        // Тянем вторую границу далеко вправо — она упирается в третью.
        val moved = EnergyBounds.move(defaults, 1, 5000f)
        assertEquals(700f - EnergyBounds.MIN_SPAN_KEV, moved[1])
        assertTrue(moved.zipWithNext().all { (a, b) -> a < b })

        // И влево — упирается в первую.
        val back = EnergyBounds.move(defaults, 1, -100f)
        assertEquals(100f + EnergyBounds.MIN_SPAN_KEV, back[1])
    }

    @Test
    fun `outer bounds stop at the scale of the instrument`() {
        val low = EnergyBounds.move(defaults, 0, -50f, minKeV = 20f, maxKeV = 2800f)
        assertEquals(20f, low[0])
        val high = EnergyBounds.move(defaults, 3, 9000f, minKeV = 20f, maxKeV = 2800f)
        assertEquals(2800f, high[3])
    }

    @Test
    fun `a moved bound is a whole number of keV`() {
        val moved = EnergyBounds.move(defaults, 2, 712.34f)
        assertEquals(712f, moved[2])
    }

    @Test
    fun `the result of any move is still a valid window set`() {
        var bounds = defaults
        for (keV in listOf(-1000f, 250f, 299f, 305f, 1490f, 4000f)) {
            bounds = EnergyBounds.move(bounds, 1, keV)
            assertNull(
                EnergyWindows.validate(EnergyBounds.toSpecs(bounds)),
                "границы после движения: $bounds",
            )
        }
    }

    @Test
    fun `the pixel under the finger becomes an energy and back`() {
        assertEquals(20f, EnergyBounds.keVAt(0f, 20f, 2820f))
        assertEquals(1420f, EnergyBounds.keVAt(0.5f, 20f, 2820f))
        assertEquals(2820f, EnergyBounds.keVAt(1f, 20f, 2820f))
        // За полем доля прижимается к краю: жест не выносит границу за шкалу.
        assertEquals(0f, EnergyBounds.fractionOf(-100f, 20f, 2820f))
        assertEquals(1f, EnergyBounds.fractionOf(9000f, 20f, 2820f))
        assertEquals(0.5f, EnergyBounds.fractionOf(1420f, 20f, 2820f), 1e-4f)
    }

    @Test
    fun `only a bound under the finger is grabbed`() {
        assertEquals(1, EnergyBounds.grab(defaults, 310f, toleranceKeV = 50f))
        assertEquals(2, EnergyBounds.grab(defaults, 690f, toleranceKeV = 50f))
        // Мимо ручек — ничего не берётся, иначе жест правил бы чужое число.
        assertNull(EnergyBounds.grab(defaults, 500f, toleranceKeV = 50f))
        // Между двумя ручками выбирается ближайшая.
        assertEquals(1, EnergyBounds.grab(defaults, 320f, toleranceKeV = 400f))
    }

    @Test
    fun `presets are named states, not hidden defaults`() {
        assertEquals(
            EnergyBounds.Preset.DEFAULT,
            EnergyBounds.presetOf(defaults, 20f, 2800f),
        )
        val whole = EnergyBounds.fullScale(20f, 2800f)
        assertEquals(4, whole.size)
        assertEquals(20f, whole.first())
        assertEquals(2800f, whole.last())
        assertTrue(whole.zipWithNext().all { (a, b) -> a < b })
        assertEquals(
            EnergyBounds.Preset.FULL_SCALE,
            EnergyBounds.presetOf(whole, 20f, 2800f),
        )
        assertEquals(
            EnergyBounds.Preset.CUSTOM,
            EnergyBounds.presetOf(listOf(90f, 310f, 690f, 1490f), 20f, 2800f),
        )
    }

    @Test
    fun `an impossible scale falls back to the defaults instead of inventing bounds`() {
        assertEquals(defaults, EnergyBounds.fullScale(100f, 105f))
    }
}
