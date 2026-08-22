package app.alpha.smoke

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.alpha.analysis.CalibrationDataset
import app.alpha.analysis.evidence.CalibrationReport
import app.alpha.analysis.evidence.ResolutionFitOutcome
import app.alpha.analysis.evidence.ResolutionFitResult
import app.alpha.ui.screens.CalibrationContent
import app.alpha.data.CalibrationModel
import app.alpha.ui.text.CalibrationCatalogue
import app.alpha.ui.text.HistoryCatalogue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Регрессия полевого краша №3: подгонка модели разрешения может вернуть
 * неопределённые коэффициенты, и NaN-координата доезжала до `drawPath` УЖЕ НА
 * ТЕЛЕФОНЕ — в чистых JVM-тестах Canvas не выполняется вовсе. Здесь карточка
 * калибровки рисуется под Robolectric с моделью из NaN: кривая подгонки
 * обязана молча выпасть, канва — отрисовать остальное, экран — не упасть.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CalibrationNanRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun nanModel() = CalibrationModel(
        selection = CalibrationDataset.Selection(
            long = null,
            radonRich = null,
            hoursAvailable = 0,
            radonHours = 0,
            radonSeconds = 0L,
        ),
        report = CalibrationReport(
            accumulations = emptyList(),
            candidates = emptyList(),
            measurements = emptyList(),
            notFound = emptyList(),
            // Тот самый вход: подгонка «удалась», но коэффициенты — не числа.
            fit = ResolutionFitOutcome.Fitted(
                ResolutionFitResult(
                    a = Double.NaN,
                    b = Double.NaN,
                    c = Double.NaN,
                    points = listOf(609.3, 1460.8),
                    quadratic = false,
                    extrapolatedBelowKeV = 300.0,
                    extrapolatedAboveKeV = 1500.0,
                ),
            ),
            scale = null,
            response = emptyList(),
        ),
        // Приближение прибора конечно — значит, ось строится и канва РИСУЕТ:
        // краш ловится только если рисование действительно выполняется.
        startResolution662 = 0.084f,
        resolutionPublished = true,
        spectrometer = true,
        deviceSerial = null,
    )

    // setContent зовётся один раз на тест, поэтому по тесту на вариант.

    private fun renderWith(variant: UiVariant) {
        val model = nanModel()
        val s = CalibrationCatalogue.of(variant.language)
        compose.showScreen(variant) {
            CalibrationContent(
                model = model,
                accepted = null,
                s = s,
                h = HistoryCatalogue.of(variant.language),
                onAccept = {},
                onRevert = {},
            )
        }
        // Карточка отрисована (а не упала), кривая подгонки честно выпала.
        // ignoreCase: заголовок раздела печатается в верхнем регистре.
        compose.onNodeWithText(s.resolutionTitle, ignoreCase = true).assertExists()
    }

    @Test
    fun `calibration card renders with a NaN resolution model (terminal)`() =
        renderWith(UiVariant.ALL[0])

    @Test
    fun `calibration card renders with a NaN resolution model (8bit)`() =
        renderWith(UiVariant.ALL[1])
}
