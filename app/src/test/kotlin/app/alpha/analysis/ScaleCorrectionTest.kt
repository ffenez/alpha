package app.alpha.analysis

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScaleCorrectionTest {

    private fun reference(table: Double, measured: Double, nuclide: String = "K-40") =
        ScaleCorrection.Reference(table, measured, nuclide)

    @Test
    fun `чистый дрейф усиления восстанавливается`() {
        // Шкала занижена на 2 %: измеренное = табличное · 0,98.
        val references = listOf(
            reference(661.7, 661.7 * 0.98, "Cs-137"),
            reference(1460.8, 1460.8 * 0.98, "K-40"),
            reference(2614.5, 2614.5 * 0.98, "Tl-208"),
        )
        val correction = ScaleCorrectionMath.of(references)
        assertNotNull(correction)
        assertEquals(1.0 / 0.98, correction.gain, 1e-6)
        assertEquals(0.0, correction.offsetKeV, 1e-6)
        assertTrue(correction.residualAfterKeV < 1e-6, "остаток ${correction.residualAfterKeV}")
    }

    @Test
    fun `сдвиг нуля восстанавливается вместе с наклоном`() {
        // Измеренное = табличное · 0,99 − 8 кэВ.
        val references = listOf(
            reference(661.7, 661.7 * 0.99 - 8.0),
            reference(1460.8, 1460.8 * 0.99 - 8.0),
            reference(2614.5, 2614.5 * 0.99 - 8.0),
        )
        val correction = ScaleCorrectionMath.of(references)
        assertNotNull(correction)
        for (r in references) {
            assertEquals(r.tableKeV, correction.apply(r.measuredKeV), 1e-6)
        }
    }

    @Test
    fun `одной линии не хватает`() {
        // Множитель из одной линии экстраполирует куда угодно: на реальном
        // спектре K-40 (−29 кэВ) предсказывал на 2614,5 поправку +52 кэВ при
        // настоящих −31.
        assertNull(ScaleCorrectionMath.of(listOf(reference(1460.8, 1430.0))))
    }

    @Test
    fun `близкие линии прямую не задают`() {
        // 1173 и 1332 расходятся всего в 1,14 раза: продолжение прямой к 2600
        // держится на промежутке в 160 кэВ.
        assertNull(
            ScaleCorrectionMath.of(
                listOf(
                    reference(1173.2, 1150.0, "Co-60"),
                    reference(1332.5, 1306.0, "Co-60"),
                ),
            ),
        )
    }

    @Test
    fun `поправка без улучшения не предлагается`() {
        // Точки уже на месте: остаток нулевой и до поправки.
        val references = listOf(
            reference(661.7, 661.7),
            reference(1460.8, 1460.8),
            reference(2614.5, 2614.5),
        )
        assertNull(ScaleCorrectionMath.of(references))
    }

    @Test
    fun `неправдоподобный множитель отбраковывается`() {
        // Линии сопоставлены не с теми: «поправка» удвоила бы шкалу.
        assertNull(
            ScaleCorrectionMath.of(
                listOf(reference(1460.8, 661.7), reference(2614.5, 1200.0)),
            ),
        )
    }

    @Test
    fun `пустой список поправки не даёт`() {
        assertNull(ScaleCorrectionMath.of(emptyList()))
        assertNull(ScaleCorrectionMath.of(listOf(reference(-1.0, 100.0))))
    }

    @Test
    fun `настоящий спектр с одним K-40 поправки не получает`() {
        // Проверка калибровки на реальном спектре (4,2 млн импульсов) уверенно
        // меряет только K-40: остальные опорные линии слабее восьми σ. Поправка
        // по ней одной завышала бы высокие энергии вдвое, поэтому её нет.
        assertNull(ScaleCorrectionMath.of(listOf(reference(1460.8, 1432.3, "K-40"))))
    }

    @Test
    fun `поправка складывается с калибровкой прибора`() {
        val calibration = EnergyCalibration(a0 = 6.88f, a1 = 2.3377f, a2 = 3.87e-4f)
        val correction = ScaleCorrection(
            offsetKeV = -5.0,
            gain = 1.02,
            references = emptyList(),
            residualBeforeKeV = 10.0,
            residualAfterKeV = 1.0,
        )
        val corrected = correction.applyTo(calibration)
        for (channel in listOf(0f, 100f, 557f, 951f)) {
            val direct = correction.apply(calibration.energyAt(channel).toDouble())
            assertTrue(
                abs(direct - corrected.energyAt(channel)) < 0.05,
                "канал $channel: $direct против ${corrected.energyAt(channel)}",
            )
        }
    }

    @Test
    fun `сдвиг на энергии называется числом`() {
        val correction = ScaleCorrectionMath.of(
            listOf(reference(661.7, 650.0), reference(1460.8, 1430.0)),
        )
        assertNotNull(correction)
        assertEquals(1460.8 - 1430.0, correction.shiftAt(1430.0), 0.5)
    }

    @Test
    fun `остатки до и после считаются по тем же линиям`() {
        val references = listOf(
            reference(661.7, 650.0),
            reference(1460.8, 1435.0),
            reference(2614.5, 2570.0),
        )
        val correction = ScaleCorrectionMath.of(references)
        assertNotNull(correction)
        assertTrue(
            correction.residualBeforeKeV > correction.residualAfterKeV,
            "${correction.residualBeforeKeV} против ${correction.residualAfterKeV}",
        )
        assertTrue(correction.residualBeforeKeV > 10.0, "до: ${correction.residualBeforeKeV}")
    }
}
