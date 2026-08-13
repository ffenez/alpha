package app.radiacode.service

import app.radiacode.data.SpectrumPollPolicy
import app.radiacode.protocol.Spectrum
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process bridge between the Спектр UI and [MeasurementService] (same
 * pattern as [ServiceStatus]: the service is unbound, so live spectrum state
 * and device commands travel through the app graph).
 *
 * Acquisition is demand-driven: the screen calls [attach]/[detach] and the
 * service polls the since-reset spectrum every [POLL_INTERVAL_MILLIS] only
 * while at least one watcher is attached. The watcher count (instead of a
 * boolean) is the seam for a future background «скан объекта» mode that keeps
 * acquisition running while the screen is away.
 *
 * The spectrum poll shares the single-in-flight [app.radiacode.device.ProtocolClient]
 * with the 1 Hz DATA_BUF poll: requests simply interleave, and because
 * DATA_BUF drains the device-side buffer, a spectrum read delays real-time
 * records by at most one read without losing any.
 */
class SpectrumHub {

    /** Latest acquisition picture for the UI; all fields survive tab switches. */
    data class State(
        /** Since-reset spectrum, updated every poll; null until the first read. */
        val spectrum: Spectrum? = null,
        val updatedAtMillis: Long = 0L,
        /** Set when the device reports a SpecFormatVersion this app cannot decode. */
        val unsupportedFormatVersion: Int? = null,
        /** Wall time of the last explicit user save, for on-screen feedback. */
        val lastSavedAtMillis: Long? = null,
    )

    /** Device/persistence actions the UI may request; executed by the service. */
    enum class Command {
        /** resetSpectrum() on the device, then an immediate refresh poll. */
        RESET,
        /** Persist the current spectrum as a snapshot + journal event. */
        SAVE_SNAPSHOT,
        /** Persist the current spectrum as the background reference. */
        RECORD_BACKGROUND,
    }

    private val _watchers = MutableStateFlow(0)
    val watchers: StateFlow<Int> = _watchers.asStateFlow()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun attach() {
        _watchers.update { it + 1 }
    }

    fun detach() {
        _watchers.update { (it - 1).coerceAtLeast(0) }
    }

    fun request(command: Command) {
        _commands.tryEmit(command)
    }

    internal fun onSpectrum(spectrum: Spectrum, atMillis: Long) {
        _state.update {
            it.copy(spectrum = spectrum, updatedAtMillis = atMillis, unsupportedFormatVersion = null)
        }
    }

    internal fun onUnsupportedFormat(version: Int) {
        _state.update { it.copy(unsupportedFormatVersion = version) }
    }

    internal fun onSaved(atMillis: Long) {
        _state.update { it.copy(lastSavedAtMillis = atMillis) }
    }

    /** Clears the stale spectrum right after a device-side reset. */
    internal fun onReset() {
        _state.update { State(unsupportedFormatVersion = it.unsupportedFormatVersion) }
    }

    companion object {
        /** Formats [app.radiacode.protocol.SpectrumDecoder] understands (v0 raw, v1 RLE). */
        val SUPPORTED_FORMAT_VERSIONS = 0..1

        /**
         * 5 s while a screen is watching: fast enough that накопление feels
         * live, sparse enough that the ~1–3 KB spectrum read (worst-case budget
         * 30 s, typically well under a second) leaves the 1 Hz DATA_BUF poll
         * essentially undisturbed.
         *
         * Один источник истины с политикой частоты (ADR 007): «5 с» это
         * ступень [SpectrumPollPolicy.EVERY_5_S], а не второе такое же число.
         */
        val POLL_INTERVAL_MILLIS = SpectrumPollPolicy.EVERY_5_S.intervalMillis

        /** Auto-persist throttle: one snapshot per minute while watching. */
        const val AUTOSAVE_INTERVAL_MILLIS = 60_000L
    }
}
