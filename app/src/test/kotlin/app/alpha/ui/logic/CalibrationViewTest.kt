package app.alpha.ui.logic

import app.alpha.analysis.CalibrationDataset
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.evidence.BackgroundCalibration
import app.alpha.analysis.evidence.SqrtResolution
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Экран, у которого мало данных, обязан называть, ЧЕГО не хватает, а не
 * показывать пустую картинку.
 */
class CalibrationViewTest {

    private val calibration = EnergyCalibration(0f, 3f, 0f)

    private val emptyReport = BackgroundCalibration.analyse(
        accumulations = emptyList(),
        startResolution = SqrtResolution(0.084),
    )

    @Test
    fun `with no material the screen says so instead of showing nothing`() {
        val rows = CalibrationView.material(
            CalibrationDataset.Selection(null, null, 0, 0, 0L),
        )
        assertTrue(rows.any { it.contains("Снимков спектра пока нет") }, "$rows")
    }

    @Test
    fun `too few radon hours are named, not hidden`() {
        val long = CalibrationDataset.Accumulation(
            counts = List(512) { 10 },
            calibration = calibration,
            seconds = 7_200L,
            intervalCount = 2,
            hoursCovered = 2,
            fromMillis = 0L,
            toMillis = 7_200_000L,
        )
        val rows = CalibrationView.material(
            CalibrationDataset.Selection(long, null, 2, 0, 7_200L),
        )
        assertTrue(rows.any { it.contains("радоновых часов не набралось") }, "$rows")
    }

    @Test
    fun `the resolution refusal names its reason`() {
        val rows = CalibrationView.resolution(emptyReport)
        assertTrue(rows.single().startsWith("Модель не построена"), "$rows")
        assertTrue(rows.single().contains("0 из 3"), "$rows")
    }

    @Test
    fun `missing data lists the lines that were not measured`() {
        val rows = CalibrationView.missing(emptyReport)
        assertTrue(rows.any { it.contains("не найдены") }, "$rows")
        assertTrue(rows.any { it.contains("часов записи") }, "$rows")
        assertTrue(rows.any { it.contains("раз в 10 минут") }, "$rows")
    }

    @Test
    fun `the scale block refuses when there are no residuals`() {
        val rows = CalibrationView.scale(emptyReport)
        assertTrue(rows.single().contains("слишком мало"), "$rows")
    }

    @Test
    fun `the response block refuses without a single-nuclide pair`() {
        val rows = CalibrationView.response(emptyReport)
        assertTrue(rows.single().contains("не считается"), "$rows")
    }
}
