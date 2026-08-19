package app.alpha.ui.logic

import app.alpha.analysis.EfficiencyPoint
import app.alpha.analysis.EfficiencyValue
import app.alpha.analysis.Peak
import app.alpha.analysis.ScaleCorrection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EfficiencyRecordTest {

    private val record = EfficiencyRecord(
        points = listOf(
            EfficiencyPoint(59.5, 0.021, 0.06, "Am-241"),
            EfficiencyPoint(661.7, 0.0042, 0.05, "Cs-137"),
        ),
        geometry = "вплотную к торцу",
        updatedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun `запись переживает круг кодирования`() {
        val back = EfficiencyRecord.decode(record.encode())
        assertNotNull(back)
        assertEquals(record.geometry, back.geometry)
        assertEquals(record.updatedAtMillis, back.updatedAtMillis)
        assertEquals(record.points.size, back.points.size)
        for (i in record.points.indices) {
            assertEquals(record.points[i].energyKeV, back.points[i].energyKeV, 1e-9)
            assertEquals(record.points[i].efficiency, back.points[i].efficiency, 1e-12)
            assertEquals(record.points[i].relativeSigma, back.points[i].relativeSigma, 1e-12)
            assertEquals(record.points[i].nuclide, back.points[i].nuclide)
        }
    }

    @Test
    fun `пустое и битое хранилище не дают записи`() {
        assertNull(EfficiencyRecord.decode(null))
        assertNull(EfficiencyRecord.decode(""))
        assertNull(EfficiencyRecord.decode("{\"points\":\"\"}"))
        assertNull(EfficiencyRecord.decode("{\"points\":\"мусор\"}"))
    }

    @Test
    fun `кривая строится из точек, а не хранится`() {
        val curve = record.curve()
        assertNotNull(curve)
        assertEquals(59.5, curve.minEnergyKeV, 1e-9)
        assertEquals(661.7, curve.maxEnergyKeV, 1e-9)
    }

    @Test
    fun `одна точка кривой не даёт`() {
        val single = record.copy(points = record.points.take(1))
        assertNull(single.curve())
    }
}

class ScaleCorrectionRecordTest {

    private val record = ScaleCorrectionRecord(
        offsetKeV = -4.2,
        gain = 1.021,
        residualBeforeKeV = 30.5,
        residualAfterKeV = 1.4,
        referenceCount = 3,
        acceptedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun `запись переживает круг кодирования`() {
        val back = ScaleCorrectionRecord.decode(record.encode())
        assertNotNull(back)
        assertEquals(record, back)
    }

    @Test
    fun `непригодное хранилище не даёт поправки`() {
        assertNull(ScaleCorrectionRecord.decode(null))
        assertNull(ScaleCorrectionRecord.decode("{}"))
        assertNull(ScaleCorrectionRecord.decode("{\"gain\":\"0\",\"offsetKeV\":\"1\"}"))
    }

    @Test
    fun `линии в поправку не переносятся`() {
        // Поправка применяется к ЛЮБОМУ спектру, и линии того, по которому её
        // считали, к нему отношения не имеют.
        assertTrue(record.correction().references.isEmpty())
        assertEquals(record.gain, record.correction().gain, 1e-12)
    }

    @Test
    fun `запись собирается из поправки`() {
        val correction = ScaleCorrection(
            offsetKeV = 1.0,
            gain = 0.99,
            references = listOf(ScaleCorrection.Reference(661.7, 668.0, "Cs-137")),
            residualBeforeKeV = 6.3,
            residualAfterKeV = 0.0,
        )
        val made = ScaleCorrectionRecord.of(correction, atMillis = 42L)
        assertEquals(1, made.referenceCount)
        assertEquals(42L, made.acceptedAtMillis)
        assertEquals(0.99, made.gain, 1e-12)
    }
}

class PeakActivityTest {

    private val curve = app.alpha.analysis.EfficiencyCurve.of(
        listOf(
            EfficiencyPoint(59.5, 0.02, 0.05, "Am-241"),
            EfficiencyPoint(661.7, 0.004, 0.05, "Cs-137"),
            EfficiencyPoint(1332.5, 0.002, 0.05, "Co-60"),
        ),
    )

    private fun peak(energyKeV: Float, net: Float, significance: Float) =
        Peak(channel = 300, energyKeV = energyKeV, netCounts = net, significance = significance)

    @Test
    fun `без кривой активности нет`() {
        assertNull(PeakActivity.of(peak(661.7f, 10_000f, 50f), "Cs-137", null, 1_000L))
    }

    @Test
    fun `без кандидата активности нет`() {
        assertNull(PeakActivity.of(peak(661.7f, 10_000f, 50f), null, curve, 1_000L))
    }

    @Test
    fun `за пределами калибровки активности нет`() {
        // 1460,8 кэВ выше верхней точки кривой (1332,5).
        assertNull(PeakActivity.of(peak(1460.8f, 10_000f, 50f), "K-40", curve, 1_000L))
    }

    @Test
    fun `у линии кандидата активность считается`() {
        val estimate = PeakActivity.of(peak(661.7f, 10_000f, 50f), "Cs-137", curve, 1_000L)
        assertNotNull(estimate)
        assertNotNull(estimate.becquerel)
        // Кривая ИНТЕРПОЛИРУЕТ: через три точки логарифмическая прямая не
        // проходит точно, и ε(661,7) чуть ниже измеренной 0,004. Проверяется
        // само определение A = N / (t · ε · p) на той ε, которую даёт кривая.
        val efficiency = curve!!.efficiencyAt(661.7)!!.efficiency
        val intensity = app.alpha.analysis.GammaLineLibrary.LINES
            .first { it.isotope == "Cs-137" }.intensityPercent / 100.0
        val expected = 10_000.0 / (1_000.0 * efficiency * intensity)
        assertEquals(expected, estimate.becquerel!!, expected * 0.001)
        // Неопределённость не ниже вклада самой кривой: подгонка по трём
        // точкам в середине диапазона знает ε ТОЧНЕЕ отдельной точки (5 %),
        // и сравнивать надо с её собственной σ, а не с σ точки.
        val curveSigma = curve.efficiencyAt(661.7)!!.relativeSigma
        assertTrue(
            estimate.relativeSigma!! >= curveSigma,
            "σ_отн ${estimate.relativeSigma} ниже σ кривой $curveSigma",
        )
    }

    @Test
    fun `у чужой линии кандидата активности нет`() {
        // 1332,5 — линия Co-60, а не Cs-137.
        assertNull(PeakActivity.of(peak(1332.5f, 10_000f, 50f), "Cs-137", curve, 1_000L))
    }

    @Test
    fun `без времени накопления активности нет`() {
        assertNull(PeakActivity.of(peak(661.7f, 10_000f, 50f), "Cs-137", curve, 0L))
    }
}

class ActivityFormatTest {

    @Test
    fun `единица выбирается по величине`() {
        assertTrue(ActivityFormat.value(84.0).endsWith("Бк"))
        assertTrue(ActivityFormat.value(1_200.0).endsWith("кБк"))
        assertTrue(ActivityFormat.value(37_000_000.0).endsWith("МБк"))
    }

    @Test
    fun `три значащие цифры и не больше`() {
        assertEquals("1,20 кБк", ActivityFormat.value(1_200.0))
        assertEquals("12,0 кБк", ActivityFormat.value(12_000.0))
        assertEquals("120 кБк", ActivityFormat.value(120_000.0))
    }

    @Test
    fun `отрицательная активность приводится к нулю`() {
        assertEquals("0,00 Бк", ActivityFormat.value(-5.0))
    }

    @Test
    fun `неопределённость целыми процентами`() {
        assertEquals("12", ActivityFormat.percent(0.1234))
        assertEquals("5", ActivityFormat.percent(0.05))
    }
}

class EfficiencyValueTest {

    @Test
    fun `значение и его относительная неопределённость хранятся вместе`() {
        val value = EfficiencyValue(0.004, 0.05)
        assertEquals(0.004, value.efficiency, 1e-12)
        assertEquals(0.05, value.relativeSigma, 1e-12)
    }
}
