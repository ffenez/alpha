package app.alpha.ui.logic

import app.alpha.analysis.Radioelements
import app.alpha.data.db.SurveyStationEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurveyModelTest {

    private fun measure(
        element: Radioelements.Element,
        cps: Float,
        relativeSigma: Float = 0.05f,
        detected: Boolean = true,
        seconds: Long = 1_800L,
    ): Radioelements.Measure {
        val net = cps * seconds
        return Radioelements.Measure(
            element = element,
            energyKeV = 1000f,
            fromKeV = 900f,
            toKeV = 1100f,
            netCounts = net,
            sigmaCounts = net * relativeSigma,
            criticalCounts = if (detected) net / 2f else net * 2f,
            seconds = seconds,
        )
    }

    private fun station(
        id: Long,
        k: Float,
        u: Float,
        th: Float,
        relativeSigma: Float = 0.05f,
    ) = SurveyModel.Station(
        entity = SurveyStationEntity(
            id = id,
            spectrumId = id,
            timestamp = id * 1_000L,
            latitude = 55.0,
            longitude = 37.0,
            accuracyMeters = 8f,
        ),
        measures = listOf(
            measure(Radioelements.Element.K, k, relativeSigma),
            measure(Radioelements.Element.U, u, relativeSigma),
            measure(Radioelements.Element.TH, th, relativeSigma),
        ),
        deviceName = "RadiaCode-110",
    )

    /** Ровная съёмка: пять одинаковых станций. */
    private fun flatSurvey() = (1L..5L).map { station(it, k = 0.35f, u = 0.06f, th = 0.03f) }

    @Test
    fun `на трёх станциях сравнивать ещё не с чем`() {
        val two = listOf(station(1, 0.35f, 0.06f, 0.03f), station(2, 0.40f, 0.06f, 0.03f))
        assertNull(
            SurveyModel.deviation(two.first(), two, SurveyModel.Quantity.K),
            "сравнение с единственным соседом выдано за съёмку",
        )
    }

    @Test
    fun `станция сравнивается с соседями, а не сама с собой`() {
        // Станция, входящая в набор, сдвигала бы медиану в свою сторону — на
        // десятке точек это заметно.
        val survey = flatSurvey() + station(6, k = 1.05f, u = 0.06f, th = 0.03f)
        val outlier = survey.last()
        val deviation = assertNotNull(
            SurveyModel.deviation(outlier, survey, SurveyModel.Quantity.K),
        )
        assertEquals(0.35f, deviation.median, 1e-3f)
        assertEquals(3f, deviation.ratioToMedian, 1e-2f)
        assertTrue(deviation.notable, "тройное превышение не выделено")
        assertTrue(deviation.above)
        assertEquals(5, deviation.neighbours)
    }

    @Test
    fun `ровная станция от съёмки не отличается`() {
        val survey = flatSurvey()
        val deviation = assertNotNull(
            SurveyModel.deviation(survey.first(), survey, SurveyModel.Quantity.K),
        )
        assertTrue(!deviation.notable, "${deviation.sigmas}σ на ровной съёмке")
    }

    @Test
    fun `разброс самой съёмки учитывается наравне с ошибкой станции`() {
        // Если станции и без того разбросаны, отличие одной из них ничего не
        // выделяет: та же величина на шумной съёмке не должна становиться
        // находкой.
        val calm = listOf(
            station(1, 0.30f, 0.06f, 0.03f), station(2, 0.31f, 0.06f, 0.03f),
            station(3, 0.30f, 0.06f, 0.03f), station(4, 0.31f, 0.06f, 0.03f),
        )
        val noisy = listOf(
            station(1, 0.10f, 0.06f, 0.03f), station(2, 0.55f, 0.06f, 0.03f),
            station(3, 0.20f, 0.06f, 0.03f), station(4, 0.60f, 0.06f, 0.03f),
        )
        val probe = station(9, k = 0.45f, u = 0.06f, th = 0.03f)
        val onCalm = assertNotNull(
            SurveyModel.deviation(probe, calm + probe, SurveyModel.Quantity.K),
        )
        val onNoisy = assertNotNull(
            SurveyModel.deviation(probe, noisy + probe, SurveyModel.Quantity.K),
        )
        assertTrue(
            kotlin.math.abs(onCalm.sigmas) > kotlin.math.abs(onNoisy.sigmas),
            "шумная съёмка выделила ту же станцию не слабее ровной",
        )
    }

    @Test
    fun `ненабранная линия не даёт ни величины, ни отношения`() {
        val station = SurveyModel.Station(
            entity = SurveyStationEntity(
                id = 1,
                spectrumId = 1,
                timestamp = 1_000L,
                latitude = 55.0,
                longitude = 37.0,
                accuracyMeters = 8f,
            ),
            measures = listOf(
                measure(Radioelements.Element.K, 0.35f),
                measure(Radioelements.Element.TH, 0.03f, detected = false),
            ),
            deviceName = null,
        )
        assertNull(SurveyModel.value(station, SurveyModel.Quantity.TH))
        assertNull(station.thoriumToPotassium, "отношение к ненабранной линии выдано")
        assertTrue(!station.complete)
    }

    @Test
    fun `нормировка сжимает выброс, а не всю картину`() {
        val values = listOf(0.30f, 0.31f, 0.32f, 0.33f, 5.0f)
        // Крайние 5 % отрезаны: одна выброшенная станция иначе сжала бы
        // остальные в один цвет.
        val ordinary = assertNotNull(SurveyModel.normalize(values, 0.32f))
        assertTrue(ordinary > 0.05f, "обычная станция схлопнулась в ноль: $ordinary")
        assertEquals(1f, assertNotNull(SurveyModel.normalize(values, 5.0f)), 1e-3f)
    }
}
