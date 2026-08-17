package app.alpha.analysis

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Energy windows (spec §7): whole-channel sums, Poisson σ, share, the
 * descriptive R_low/R_high index, and the storage form of the bounds.
 *
 * The calibration used everywhere is E = ch (a0=0, a1=1, a2=0), so a window
 * bound in keV is a channel number and the expected sums can be written out by
 * hand.
 */
class EnergyWindowsTest {

    private val identity = EnergyCalibration(0f, 1f, 0f)

    /** counts[i] = i (channel index), 0..999 — sums are known in closed form. */
    private val ramp = List(1000) { it }

    @Test
    fun `window sums whole channels whose centre falls inside`() {
        // [100,300): channels 100..299 (E(ch)=ch), Σ i = (100+299)·200/2.
        val window = EnergyWindows.window(ramp, 60, identity, EnergyWindowSpec(100f, 300f))
        assertEquals(100, window.firstChannel)
        assertEquals(299, window.lastChannel)
        assertEquals((100L + 299L) * 200L / 2L, window.counts)
    }

    @Test
    fun `adjacent windows never share a channel`() {
        val flat = List(1000) { 1 }
        val low = EnergyWindows.window(flat, 10, identity, EnergyWindowSpec(100f, 300f))
        val high = EnergyWindows.window(flat, 10, identity, EnergyWindowSpec(300f, 700f))
        assertEquals(299, low.lastChannel)
        assertEquals(300, high.firstChannel)
        // No double counting: the two windows together cover exactly 600 channels.
        assertEquals(600L, low.counts + high.counts)
    }

    @Test
    fun `edge channels are taken whole and the realised span is reported`() {
        val flat = List(1000) { 1 }
        // Non-integer bounds: 100.5 → first channel 101, 300.5 → last 300.
        val window = EnergyWindows.window(flat, 10, identity, EnergyWindowSpec(100.5f, 300.5f))
        assertEquals(101, window.firstChannel)
        assertEquals(300, window.lastChannel)
        assertEquals(200L, window.counts)
        // Covered span = outer bin edges of the first and last channel.
        assertEquals(100.5f, window.coveredStartKeV, 1e-3f)
        assertEquals(300.5f, window.coveredEndKeV, 1e-3f)
    }

    @Test
    fun `rate and sigma are Poisson over the live time`() {
        val counts = List(1000) { if (it in 100..299) 5 else 0 }
        val window = EnergyWindows.window(counts, 200, identity, EnergyWindowSpec(100f, 300f))
        assertEquals(1000L, window.counts)
        assertEquals(5.0, window.rateCps, 1e-9)
        // σ_R = √C / t = √1000 / 200
        assertEquals(sqrt(1000.0) / 200.0, window.sigmaCps, 1e-9)
    }

    @Test
    fun `zero live time gives zero rate instead of infinity`() {
        val counts = List(1000) { 1 }
        val window = EnergyWindows.window(counts, 0, identity, EnergyWindowSpec(100f, 300f))
        assertEquals(200L, window.counts)
        assertEquals(0.0, window.rateCps)
        assertEquals(0.0, window.sigmaCps)
    }

    @Test
    fun `share is the fraction of the whole spectrum`() {
        val counts = List(1000) { if (it in 100..199) 3 else 1 }
        val total = counts.sumOf { it.toLong() }
        val window = EnergyWindows.window(counts, 10, identity, EnergyWindowSpec(100f, 200f))
        assertEquals(300L, window.counts)
        assertEquals(300.0 / total, window.fraction, 1e-9)
    }

    @Test
    fun `window outside the spectrum is empty, not an error`() {
        val counts = List(100) { 1 }
        val window = EnergyWindows.window(counts, 10, identity, EnergyWindowSpec(700f, 1500f))
        assertTrue(window.isEmpty)
        assertEquals(0L, window.counts)
        assertEquals(0.0, window.rateCps)
    }

    @Test
    fun `spectral index is the ratio of the end windows with propagated sigma`() {
        // low window (100..299) has 200·4 = 800 counts, high (700..1499) 800·1 = 800
        val counts = List(2000) {
            when (it) {
                in 100..299 -> 4
                in 700..1499 -> 1
                else -> 0
            }
        }
        val analysis = EnergyWindows.analyze(counts, 100, identity)
        val index = assertNotNull(analysis.index)
        val low = analysis.windows.first().counts.toDouble()
        val high = analysis.windows.last().counts.toDouble()
        assertEquals(800.0, low)
        assertEquals(800.0, high)
        assertEquals(low / high, index.value, 1e-9)
        assertEquals(
            (low / high) * sqrt(1.0 / low + 1.0 / high),
            index.sigma,
            1e-9,
        )
        assertEquals(EnergyWindows.DEFAULTS.first(), index.lowWindow)
        assertEquals(EnergyWindows.DEFAULTS.last(), index.highWindow)
    }

    @Test
    fun `index equals the rate ratio because the live time cancels`() {
        val counts = List(2000) {
            when (it) {
                in 100..299 -> 6
                in 700..1499 -> 2
                else -> 0
            }
        }
        val analysis = EnergyWindows.analyze(counts, 137, identity)
        val index = assertNotNull(analysis.index)
        val expected = analysis.windows.first().rateCps / analysis.windows.last().rateCps
        assertEquals(expected, index.value, 1e-9)
    }

    @Test
    fun `index is refused when a window is empty`() {
        val counts = List(2000) { if (it in 100..299) 4 else 0 }
        val analysis = EnergyWindows.analyze(counts, 100, identity)
        assertNull(analysis.index, "a ratio over zero counts would be invented")
    }

    @Test
    fun `defaults are the windows of the specification`() {
        assertEquals(
            listOf(100f to 300f, 300f to 700f, 700f to 1500f),
            EnergyWindows.DEFAULTS.map { it.startKeV to it.endKeV },
        )
    }

    @Test
    fun `validation refuses overlapping, too narrow and out-of-range windows`() {
        assertNull(EnergyWindows.validate(EnergyWindows.DEFAULTS))
        assertNotNull(
            EnergyWindows.validate(
                listOf(EnergyWindowSpec(100f, 400f), EnergyWindowSpec(300f, 700f)),
            ),
            "overlapping windows would count a pulse twice",
        )
        assertNotNull(EnergyWindows.validate(listOf(EnergyWindowSpec(100f, 105f))))
        assertNotNull(EnergyWindows.validate(listOf(EnergyWindowSpec(100f, 5000f))))
        assertNotNull(EnergyWindows.validate(emptyList()))
    }

    @Test
    fun `bounds round-trip through the storage form`() {
        val specs = listOf(EnergyWindowSpec(50f, 250f), EnergyWindowSpec(250f, 900f))
        val raw = EnergyWindows.format(specs)
        assertEquals("50:250,250:900", raw)
        assertEquals(specs, EnergyWindows.parse(raw))
    }

    @Test
    fun `the storage form of the bounds is a contract with the previous version`() {
        // Редактор границ стал цепочкой из четырёх чисел, но НА ДИСКЕ формат
        // прежний: настройка, записанная старой версией, обязана читаться как
        // ровно те же окна — обновление приложения не вправе её переписать.
        assertEquals(
            listOf(100f to 300f, 300f to 700f, 700f to 1500f),
            EnergyWindows.parse("100:300,300:700,700:1500").map { it.startKeV to it.endKeV },
        )
        assertEquals(
            listOf(50f to 250f, 250f to 900f),
            EnergyWindows.parse("50:250,250:900").map { it.startKeV to it.endKeV },
        )
        // Настройка с разрывом (её позволял прежний редактор) тоже читается
        // как была — окна не «склеиваются» молча.
        assertEquals(
            listOf(100f to 300f, 700f to 1500f),
            EnergyWindows.parse("100:300,700:1500").map { it.startKeV to it.endKeV },
        )
    }

    @Test
    fun `malformed or invalid stored bounds fall back to the defaults`() {
        assertEquals(EnergyWindows.DEFAULTS, EnergyWindows.parse(null))
        assertEquals(EnergyWindows.DEFAULTS, EnergyWindows.parse(""))
        assertEquals(EnergyWindows.DEFAULTS, EnergyWindows.parse("100-300"))
        assertEquals(EnergyWindows.DEFAULTS, EnergyWindows.parse("100:300,broken"))
        // Valid syntax, invalid content (overlap) → defaults, never a bad window.
        assertEquals(EnergyWindows.DEFAULTS, EnergyWindows.parse("100:400,300:700"))
    }

    @Test
    fun `a real quadratic calibration maps energies to the right channels`() {
        // Typical RC-110-ish calibration: E = -5.5 + 2.4·ch + 4e-4·ch²
        val calibration = EnergyCalibration(-5.5f, 2.4f, 4.0E-4f)
        val counts = List(1024) { 1 }
        val window = EnergyWindows.window(counts, 100, calibration, EnergyWindowSpec(100f, 300f))
        // Every channel of the window has its centre inside the energy range.
        for (channel in window.firstChannel..window.lastChannel) {
            val energy = calibration.energyAt(channel.toFloat())
            assertTrue(energy >= 100f && energy < 300f, "channel $channel at $energy keV")
        }
        // The channels just outside are indeed outside.
        assertTrue(calibration.energyAt((window.firstChannel - 1).toFloat()) < 100f)
        assertTrue(calibration.energyAt((window.lastChannel + 1).toFloat()) >= 300f)
    }
}
