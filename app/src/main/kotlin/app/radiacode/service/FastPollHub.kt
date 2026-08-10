package app.radiacode.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Poll-cadence bridge between the Поиск screen and [MeasurementService], in
 * the same refcount shape as [SpectrumHub]: the screen calls [attach] while
 * it is on top, the service picks the DATA_BUF poll period from the watcher
 * count. UI never touches the device layer directly.
 *
 * **What faster polling actually buys — and what it does not.** The RadiaCode
 * produces roughly one RealTimeData record per second on its own. Polling the
 * device buffer twice a second does NOT produce two measurements per second;
 * it halves the *pickup* delay — the time a finished record sits in the
 * device buffer before we read it — from ~0.5 s on average to ~0.25 s. When
 * sweeping a surface looking for a source, that lag is the difference between
 * the clicks rising over the spot and rising a step past it. Any wording
 * shown to the user must say this and must not promise «2 измерения в секунду».
 *
 * Costs are bounded by design: the fast rate applies only while the Поиск
 * screen is resumed (background and other tabs stay at 1 Hz), the poll loop
 * is strictly sequential on the single-in-flight `ProtocolClient` (a tick
 * cannot start before the previous read returned, so nothing queues up), and
 * an empty DATA_BUF reply — normal at 2 Hz, since records appear ~1 Hz — is
 * an ordinary no-op, not an error and not a sequence gap.
 */
class FastPollHub {

    private val _watchers = MutableStateFlow(0)
    val watchers: StateFlow<Int> = _watchers.asStateFlow()

    fun attach() {
        _watchers.update { it + 1 }
    }

    fun detach() {
        _watchers.update { (it - 1).coerceAtLeast(0) }
    }

    companion object {
        /** Ordinary cadence: one poll per produced record. */
        const val NORMAL_INTERVAL_MILLIS = 1_000L

        /** Поиск cadence: same records, half the pickup delay. */
        const val FAST_INTERVAL_MILLIS = 500L

        /** Pure: any watcher means fast, none means back to 1 Hz. */
        fun intervalMillis(watchers: Int): Long =
            if (watchers > 0) FAST_INTERVAL_MILLIS else NORMAL_INTERVAL_MILLIS
    }
}
