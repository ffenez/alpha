package app.alpha.analysis

import app.alpha.data.TemplateRepository
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Кривая разрешения проверяется на настоящем спектре ториевого источника:
 * вопрос ровно в том, описывает ли паспортная форма FWHM = R·√(662·E) реальный
 * прибор на всей шкале, или её надо мерить.
 */
class ResolutionCurveTest {

    private val thorium by lazy { SpectraFixtures.load("th232-source.csv") }

    private fun points() = TemplateRepository.points(
        counts = thorium.first,
        calibration = thorium.second,
        fallback = 0.084f,
    )

    @Test
    fun `паспортная форма — это кривая через ноль`() {
        val curve = ResolutionCurve.ofResolution662(0.084f)
        // FWHM(662) = R·662 по определению «разрешения в процентах».
        assertTrue(abs(curve.fwhmAt(662f) - 0.084f * 662f) < 0.01f, "FWHM ${curve.fwhmAt(662f)}")
        assertTrue(abs(curve.resolution662 - 0.084f) < 1e-4f, "R ${curve.resolution662}")
    }

    @Test
    fun `одна линия кривой не задаёт`() {
        assertNull(
            ResolutionCurve.fit(listOf(ResolutionCurve.Point(1460f, 120f, 10f))),
        )
        // Две близкие линии — тоже: наклон между ними тонет в их же ширине.
        assertNull(
            ResolutionCurve.fit(
                listOf(
                    ResolutionCurve.Point(583f, 60f, 10f),
                    ResolutionCurve.Point(609f, 62f, 10f),
                ),
            ),
        )
    }

    @Test
    fun `кривая измеряется по линиям реального спектра`() {
        val measured = points()
        assertTrue(measured.size >= 2, "линий с измеренной шириной: ${measured.size}")
        val curve = assertNotNull(ResolutionCurve.fit(measured), "кривая не построена")
        assertTrue(
            curve.resolution662 in 0.05f..0.15f,
            "разрешение на 662 кэВ вышло ${curve.resolution662}",
        )
        // Ширина обязана расти с энергией: иначе это не разрешение.
        assertTrue(
            curve.fwhmAt(2615f) > curve.fwhmAt(662f),
            "ширина на 2615 кэВ не больше, чем на 662",
        )
    }

    @Test
    fun `измеренная кривая предсказывает верхнюю линию точнее паспортной формы`() {
        // Проверка «с отложенной точкой»: кривая строится БЕЗ самой высокой
        // линии, а потом предсказывает её ширину. Паспортная форма привязана к
        // 662 кэВ и там же берёт своё число — на 2615 кэВ ей верить не за что.
        val measured = points().sortedBy { it.energyKeV }
        val held = measured.last()
        val curve = assertNotNull(
            ResolutionCurve.fit(measured.dropLast(1)),
            "кривая по остальным линиям не построена",
        )
        val paper = ResolutionCurve.ofResolution662(
            TemplateRepository.measuredResolution(thorium.first, thorium.second, 0.084f),
        )
        val curveError = abs(curve.fwhmAt(held.energyKeV) - held.fwhmKeV)
        val paperError = abs(paper.fwhmAt(held.energyKeV) - held.fwhmKeV)
        assertTrue(
            curveError <= paperError,
            "на ${held.energyKeV} кэВ кривая ошиблась на $curveError кэВ, паспортная форма " +
                "на $paperError кэВ (измерено ${held.fwhmKeV})",
        )
    }
}
