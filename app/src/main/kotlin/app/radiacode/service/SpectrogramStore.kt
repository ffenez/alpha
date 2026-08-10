package app.radiacode.service

import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.Spectrogram
import app.radiacode.analysis.SpectrogramRing
import app.radiacode.analysis.SpectrogramSlice
import app.radiacode.protocol.Spectrum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service-side recorder of the спектрограмма waterfall: every since-reset
 * spectrum the service polls (SpectrumHub 5 s cadence) is diffed against the
 * previous poll into an interval slice and pushed into an in-memory ring
 * (~2 h, [SpectrogramRing.DEFAULT_CAPACITY]). Lives in the app graph so the
 * Спектрограмма screen and the Поиск energy-tone mode can observe it; dies
 * with the process by design — the durable record is the spectrum snapshots.
 */
class SpectrogramStore {

    private val ring = SpectrogramRing()

    private var previous: Spectrum? = null

    private val _slices = MutableStateFlow<List<SpectrogramSlice>>(emptyList())
    /** All retained slices, oldest → newest. */
    val slices: StateFlow<List<SpectrogramSlice>> = _slices.asStateFlow()

    private val _latest = MutableStateFlow<SpectrogramSlice?>(null)
    /** Newest slice (Поиск «тон по энергии» consumes just this). */
    val latest: StateFlow<SpectrogramSlice?> = _latest.asStateFlow()

    /**
     * Feed one polled since-reset spectrum. The first poll after start or a
     * reset only re-arms the diff base; a valid growth of the accumulation
     * produces a slice stamped with the live dose/CPS at that moment.
     */
    fun onSpectrum(spectrum: Spectrum, atMillis: Long, cps: Float?, doseMicroSvH: Float?) {
        val prev = previous
        previous = spectrum
        val interval = Spectrogram.intervalCounts(
            currentCounts = spectrum.counts,
            currentSeconds = spectrum.durationSeconds,
            previousCounts = prev?.counts,
            previousSeconds = prev?.durationSeconds ?: 0L,
        ) ?: return
        val slice = SpectrogramSlice(
            timestampMillis = atMillis,
            intervalSeconds = spectrum.durationSeconds - (prev?.durationSeconds ?: 0L),
            bandCounts = Spectrogram.bandCounts(
                interval,
                EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2),
            ),
            cps = cps,
            doseMicroSvH = doseMicroSvH,
        )
        ring.add(slice)
        _slices.value = ring.snapshot()
        _latest.value = slice
    }

    /** Device-side spectrum reset: drop the diff base, keep recorded history. */
    fun onReset() {
        previous = null
    }
}
