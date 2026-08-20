package app.alpha.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrippingCalibrationTest {

    private fun measure(
        element: Radioelements.Element,
        cps: Float,
        seconds: Long,
        detected: Boolean = true,
    ): Radioelements.Measure {
        val net = cps * seconds
        return Radioelements.Measure(
            element = element,
            energyKeV = 1000f,
            fromKeV = 900f,
            toKeV = 1100f,
            netCounts = net,
            sigmaCounts = kotlin.math.sqrt(net.coerceAtLeast(1f)),
            criticalCounts = if (detected) net / 2f else net * 2f,
            seconds = seconds,
        )
    }

    private fun sample(
        k: Float,
        u: Float,
        th: Float,
        seconds: Long = 600L,
        thoriumDetected: Boolean = true,
        uraniumDetected: Boolean = true,
    ) = StrippingCalibration.Sample(
        measures = listOf(
            measure(Radioelements.Element.K, k, seconds),
            measure(Radioelements.Element.U, u, seconds, uraniumDetected),
            measure(Radioelements.Element.TH, th, seconds, thoriumDetected),
        ),
        seconds = seconds,
    )

    @Test
    fun `коэффициенты считаются по превышению над фоном`() {
        val background = sample(k = 0.30f, u = 0.05f, th = 0.03f)
        // Ториевый источник: +1,00 в своём окне, +0,40 в урановом, +0,70 в калиевом.
        val thorium = sample(k = 1.00f, u = 0.45f, th = 1.03f)
        val result = StrippingCalibration.of(background, thorium, uranium = null)
        val stripping = assertNotNull(result.stripping)
        assertEquals(0.40f, stripping.thoriumIntoUranium, 1e-3f)
        assertEquals(0.70f, stripping.thoriumIntoPotassium, 1e-3f)
        assertEquals(0f, stripping.uraniumIntoPotassium)
        assertEquals(StrippingCalibration.Refusal.MISSING_MEASUREMENT, result.uraniumRefusal)
    }

    @Test
    fun `разное время измерений не путает счёт`() {
        // Фон снимали втрое дольше источника: вычитаются СКОРОСТИ, а не
        // импульсы, иначе поправка зависела бы от терпения человека.
        val background = sample(k = 0.30f, u = 0.05f, th = 0.03f, seconds = 1_800L)
        val thorium = sample(k = 1.00f, u = 0.45f, th = 1.03f, seconds = 600L)
        val stripping = assertNotNull(
            StrippingCalibration.of(background, thorium, uranium = null).stripping,
        )
        assertEquals(0.40f, stripping.thoriumIntoUranium, 1e-3f)
    }

    @Test
    fun `урановый источник даёт гамму с уже снятым торием`() {
        val background = sample(k = 0.30f, u = 0.05f, th = 0.03f)
        val thorium = sample(k = 1.00f, u = 0.45f, th = 1.03f)
        // В урановом источнике есть и торий: +0,10 в ториевом окне.
        val uranium = sample(k = 0.30f + 0.80f + 0.07f, u = 0.05f + 1.00f + 0.04f, th = 0.13f)
        val stripping = assertNotNull(
            StrippingCalibration.of(background, thorium, uranium).stripping,
        )
        // Чистый уран = 1,04 − α·0,10 = 1,00; калий от урана = 0,87 − β·0,10 = 0,80.
        assertEquals(0.80f, stripping.uraniumIntoPotassium, 0.02f)
    }

    @Test
    fun `без фона коэффициенты не считаются вовсе`() {
        val result = StrippingCalibration.of(
            background = null,
            thorium = sample(1f, 1f, 1f),
            uranium = null,
        )
        assertNull(result.stripping)
        assertEquals(StrippingCalibration.Refusal.MISSING_MEASUREMENT, result.thoriumRefusal)
    }

    @Test
    fun `слабый источник назван слабым, а не посчитан`() {
        val background = sample(k = 0.30f, u = 0.05f, th = 0.03f)
        val weak = sample(k = 0.31f, u = 0.06f, th = 0.04f, thoriumDetected = false)
        val result = StrippingCalibration.of(background, weak, uranium = null)
        assertNull(result.stripping)
        assertEquals(StrippingCalibration.Refusal.SOURCE_TOO_WEAK, result.thoriumRefusal)
    }

    @Test
    fun `источник ниже фона не даёт отрицательных коэффициентов`() {
        val background = sample(k = 0.50f, u = 0.20f, th = 0.10f)
        val thorium = sample(k = 0.40f, u = 0.15f, th = 0.60f)
        val stripping = assertNotNull(
            StrippingCalibration.of(background, thorium, uranium = null).stripping,
        )
        assertTrue(stripping.thoriumIntoUranium >= 0f)
        assertTrue(stripping.thoriumIntoPotassium >= 0f)
    }
}
