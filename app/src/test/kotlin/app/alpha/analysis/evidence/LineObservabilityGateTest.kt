package app.alpha.analysis.evidence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Отсутствие линии доказывает что-либо только там, где линию УВИДЕЛ БЫ тот же
 * прибор, что искал остальные.
 *
 * Две ошибки, из-за которых обычный природный фон объявлялся противоречивым:
 *
 *  1. порогом служил предел Карри, а поиск пиков требует своей значимости
 *     (4σ) — линия между этими двумя порогами считалась пропавшей, хотя
 *     находкой её никто бы не назвал;
 *  2. линия под соседним пиком считалась пропавшей, хотя отдельным максимумом
 *     она не выделяется ни при каком накоплении.
 */
class LineObservabilityGateTest {

    private val resolution = SqrtResolution(0.084)

    /** Плоский континуум: столько-то импульсов на кэВ по всей шкале. */
    private fun continuum(perKeV: Double) = object : ContinuumModel {
        override fun countsPerKeV(energyKeV: Double): Double = perKeV
    }

    private fun line(energyKeV: Double, intensity: Double) = LibraryLine(
        nuclide = "X",
        chain = null,
        energyKeV = energyKeV,
        energyUncertaintyKeV = null,
        intensityPercent = intensity,
        intensityUncertaintyPercent = null,
        source = DataSource.ENSDF,
        natural = true,
    )

    @Test
    fun `the bar is the peak finder's own threshold, not the Currie limit`() {
        val background = 400.0
        assertTrue(
            DetectionLimit.finderCounts(background, 4.0) >
                DetectionLimit.currieCounts(background),
            "порог поиска обязан быть строже предела Карри",
        )
        assertEquals(
            DetectionLimit.finderCounts(background, 4.0),
            DetectionLimit.observableCounts(background, 4.0),
        )
    }

    /**
     * Ожидаемая площадь между пределом Карри и порогом поиска: раньше линия
     * объявлялась обязанной быть видной (и давала противоречие), теперь ответ
     * честный — судить не по чему.
     */
    @Test
    fun `a line the finder would miss is not a missing line`() {
        val perKeV = 30.0
        val fwhm = resolution.fwhmKeV(600.0)
        val background = perKeV * fwhm
        val currie = DetectionLimit.currieCounts(background)
        val finder = DetectionLimit.finderCounts(background, 4.0)
        // Опорная линия подобрана так, что предсказанная площадь второй лежит
        // между пределом Карри и порогом поиска.
        val predicted = (currie + finder) / 2
        val verdict = LineObservabilityRule.evaluate(
            line = line(600.0, 10.0),
            referenceLine = line(1000.0, 10.0),
            referenceArea = predicted,
            continuum = continuum(perKeV),
            resolution = resolution,
            energyRangeKeV = 20.0..3000.0,
        )
        assertEquals(LineObservability.UNDETERMINED, verdict)
    }

    @Test
    fun `a line under a neighbouring peak stays undetermined`() {
        val verdict = LineObservabilityRule.evaluate(
            line = line(583.2, 30.0),
            referenceLine = line(2614.5, 36.0),
            referenceArea = 100_000.0,
            continuum = continuum(30.0),
            resolution = resolution,
            energyRangeKeV = 20.0..3000.0,
            // Найденный пик на 594 кэВ: 583 внутри его ширины.
            foundEnergiesKeV = listOf(594.0),
        )
        assertEquals(LineObservability.UNDETERMINED, verdict)
    }
}
