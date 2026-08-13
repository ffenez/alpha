package app.radiacode.analysis.evidence

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Подгонка √(a + bE + cE²): отказы важнее кривой. */
class ResolutionFittingTest {

    private fun point(energyKeV: Double, fwhmKeV: Double, sigma: Double = 0.5) = MeasuredLine(
        line = LibraryLine(
            nuclide = "test",
            chain = null,
            energyKeV = energyKeV,
            energyUncertaintyKeV = null,
            intensityPercent = 10.0,
            intensityUncertaintyPercent = null,
            source = DataSource.ENSDF,
            natural = true,
        ),
        observedKeV = energyKeV,
        observedSigmaKeV = 0.2,
        fwhmKeV = fwhmKeV,
        fwhmSigmaKeV = sigma,
        netArea = 10_000.0,
        netAreaSigma = 100.0,
        significance = 100.0,
        blendBiasKeV = 0.0,
        sourceId = "long",
    )

    @Test
    fun `two points are refused`() {
        val outcome = ResolutionFitting.fit(listOf(point(600.0, 50.0), point(2600.0, 90.0)))
        val refused = assertIs<ResolutionFitOutcome.Refused>(outcome)
        assertEquals(ResolutionFitRefusal.NOT_ENOUGH_LINES, refused.reason)
    }

    @Test
    fun `points crowded in energy are refused`() {
        val outcome = ResolutionFitting.fit(
            listOf(point(1100.0, 56.0), point(1200.0, 58.0), point(1300.0, 60.0)),
        )
        val refused = assertIs<ResolutionFitOutcome.Refused>(outcome)
        assertEquals(ResolutionFitRefusal.NARROW_ENERGY_SPAN, refused.reason)
        assertTrue(refused.spanKeV < ResolutionFitting.MIN_SPAN_KEV)
    }

    @Test
    fun `three points give the two-term form, not an exact interpolation`() {
        val outcome = ResolutionFitting.fit(
            listOf(
                point(1120.3, sqrt(400.0 + 2.5 * 1120.3)),
                point(1764.5, sqrt(400.0 + 2.5 * 1764.5)),
                point(2614.5, sqrt(400.0 + 2.5 * 2614.5)),
            ),
        )
        val fitted = assertIs<ResolutionFitOutcome.Fitted>(outcome)
        assertTrue(!fitted.fit.quadratic, "по трём точкам квадратичный член не берётся")
        assertEquals(0.0, fitted.fit.c)
        assertTrue(kotlin.math.abs(fitted.fit.a - 400.0) < 40.0, "a = ${fitted.fit.a}")
        assertTrue(kotlin.math.abs(fitted.fit.b - 2.5) < 0.2, "b = ${fitted.fit.b}")
    }

    @Test
    fun `a width falling with energy is refused, not fitted`() {
        val outcome = ResolutionFitting.fit(
            listOf(
                point(1120.3, 90.0),
                point(1764.5, 70.0),
                point(2614.5, 50.0),
            ),
        )
        val refused = assertIs<ResolutionFitOutcome.Refused>(outcome)
        assertEquals(ResolutionFitRefusal.NOT_MONOTONE, refused.reason)
    }

    @Test
    fun `the measured range is carried out of the fit`() {
        val outcome = ResolutionFitting.fit(
            listOf(
                point(1120.3, sqrt(400.0 + 2.5 * 1120.3)),
                point(1460.8, sqrt(400.0 + 2.5 * 1460.8)),
                point(1764.5, sqrt(400.0 + 2.5 * 1764.5)),
                point(2614.5, sqrt(400.0 + 2.5 * 2614.5)),
            ),
        )
        val fitted = assertIs<ResolutionFitOutcome.Fitted>(outcome)
        assertEquals(1120.3, fitted.fit.extrapolatedBelowKeV)
        assertEquals(2614.5, fitted.fit.extrapolatedAboveKeV)
        assertEquals(4, fitted.fit.points.size)
    }
}
