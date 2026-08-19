package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверки SNIP на спектрах с ИЗВЕСТНОЙ подложкой: только так видно, что
 * алгоритм восстанавливает именно континуум, а не сглаженный спектр.
 */
class SnipContinuumTest {

    private val calibration = EnergyCalibration(a0 = 0f, a1 = 2f, a2 = 0f)

    /** Экспоненциальная подложка — обычная форма комптоновского фона. */
    private fun continuum(n: Int): List<Float> =
        List(n) { i -> (5000.0 * exp(-i / 180.0) + 20.0).toFloat() }

    private fun gaussian(n: Int, center: Int, fwhmChannels: Double, area: Double): List<Float> {
        val sigma = fwhmChannels / 2.3548
        val norm = area / (sigma * kotlin.math.sqrt(2 * Math.PI))
        return List(n) { i ->
            val z = (i - center) / sigma
            (norm * exp(-0.5 * z * z)).toFloat()
        }
    }

    private fun spectrum(vararg peaks: Triple<Int, Double, Double>): Pair<List<Int>, List<Float>> {
        val n = 512
        val base = continuum(n)
        var total = base
        for ((center, fwhm, area) in peaks) {
            val peak = gaussian(n, center, fwhm, area)
            total = total.mapIndexed { i, v -> v + peak[i] }
        }
        return total.map { it.roundToInt() } to base
    }

    @Test
    fun `подложка восстанавливается под линией с точностью процентов`() {
        val (counts, base) = spectrum(Triple(200, 20.0, 40_000.0))
        val estimate = SnipContinuum.of(counts, calibration)
        assertEquals(counts.size, estimate.size)
        // Под самой линией — там, где ошибка алгоритма максимальна.
        for (i in 180..220) {
            val relative = abs(estimate[i] - base[i]) / base[i]
            assertTrue(relative < 0.15f, "канал $i: ${estimate[i]} против ${base[i]}")
        }
    }

    @Test
    fun `линия стирается, а не сглаживается`() {
        val (counts, _) = spectrum(Triple(200, 20.0, 40_000.0))
        val estimate = SnipContinuum.of(counts, calibration)
        // Пик над подложкой был; в подложке его не осталось: локальный максимум
        // оценки в области линии не выделяется над её краями.
        val peakCounts = counts[200].toFloat()
        assertTrue(
            estimate[200] < peakCounts * 0.5f,
            "континуум ${estimate[200]} при отсчёте $peakCounts",
        )
        val edge = (estimate[170] + estimate[230]) / 2f
        assertTrue(
            abs(estimate[200] - edge) / edge < 0.2f,
            "в подложке остался горб: ${estimate[200]} против краёв $edge",
        )
    }

    @Test
    fun `на чистой подложке алгоритм почти ничего не меняет`() {
        val base = continuum(512)
        val counts = base.map { it.roundToInt() }
        val estimate = SnipContinuum.of(counts, calibration)
        for (i in 20 until 480) {
            val relative = abs(estimate[i] - base[i]) / base[i]
            assertTrue(relative < 0.1f, "канал $i: ${estimate[i]} против ${base[i]}")
        }
    }

    @Test
    fun `несколько линий разной ширины стираются все`() {
        val (counts, base) = spectrum(
            Triple(120, 14.0, 30_000.0),
            Triple(300, 26.0, 20_000.0),
            Triple(430, 32.0, 12_000.0),
        )
        val estimate = SnipContinuum.of(counts, calibration)
        for (center in listOf(120, 300, 430)) {
            val relative = abs(estimate[center] - base[center]) / base[center]
            assertTrue(relative < 0.25f, "линия $center: ${estimate[center]} против ${base[center]}")
        }
    }

    @Test
    fun `континуум нигде не выше отсчётов`() {
        val (counts, _) = spectrum(Triple(200, 20.0, 40_000.0))
        val estimate = SnipContinuum.of(counts, calibration)
        for (i in counts.indices) {
            assertTrue(estimate[i] <= counts[i].toFloat() + 0.001f, "канал $i")
        }
    }

    @Test
    fun `вычитание оставляет площадь линии`() {
        val area = 40_000.0
        val (counts, _) = spectrum(Triple(200, 20.0, area))
        val estimate = SnipContinuum.of(counts, calibration)
        val net = SnipContinuum.subtract(counts, estimate)
        val recovered = (150..250).sumOf { net[it].toDouble() }
        val relative = abs(recovered - area) / area
        assertTrue(relative < 0.2, "площадь $recovered против $area")
    }

    @Test
    fun `LLS обратимо`() {
        for (y in listOf(0.0, 1.0, 17.0, 1234.0, 250_000.0)) {
            val back = SnipContinuum.inverseLls(SnipContinuum.lls(y))
            assertTrue(abs(back - y) <= 1e-6 * kotlin.math.max(y, 1.0), "y = $y, обратно $back")
        }
    }

    @Test
    fun `короткий спектр отклоняется`() {
        val short = List(16) { 100 }
        assertTrue(SnipContinuum.of(short, calibration).isEmpty())
    }

    @Test
    fun `отрицательные отсчёта не ломают преобразование`() {
        val values = List(64) { if (it == 10) -5f else 100f }
        val estimate = SnipContinuum.of(values, IntArray(64) { 4 })
        assertTrue(estimate.all { it.isFinite() && it >= 0f })
    }
}
