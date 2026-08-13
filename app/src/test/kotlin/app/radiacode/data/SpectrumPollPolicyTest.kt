package app.radiacode.data

import app.radiacode.analysis.HistorySlice
import app.radiacode.analysis.SpectrogramBinning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

/**
 * Политика частоты опроса спектра (ADR 007) и укладка среза в строку.
 */
class SpectrumPollPolicyTest {

    @Test
    fun `an open Спектр or Спектрограмма screen always gets 5 s`() {
        for (policy in SpectrumPollPolicy.entries) {
            assertEquals(
                5_000L,
                SpectrumPollPolicy.intervalMillis(policy, watchers = 1),
                "наблюдатель есть — ступень не имеет значения: $policy",
            )
            assertEquals(
                5_000L,
                SpectrumPollPolicy.intervalMillis(policy, watchers = 3),
            )
        }
    }

    @Test
    fun `with no watcher the chosen step decides, and nothing else does`() {
        assertEquals(
            5_000L,
            SpectrumPollPolicy.intervalMillis(SpectrumPollPolicy.EVERY_5_S, watchers = 0),
        )
        assertEquals(
            30_000L,
            SpectrumPollPolicy.intervalMillis(SpectrumPollPolicy.EVERY_30_S, watchers = 0),
        )
        assertEquals(
            600_000L,
            SpectrumPollPolicy.intervalMillis(SpectrumPollPolicy.EVERY_10_MIN, watchers = 0),
        )
    }

    @Test
    fun `the default is the 30 s step and unknown storage falls back to it`() {
        assertEquals(SpectrumPollPolicy.EVERY_30_S, SpectrumPollPolicy.DEFAULT)
        assertEquals(SpectrumPollPolicy.DEFAULT, SpectrumPollPolicy.of(null))
        assertEquals(SpectrumPollPolicy.DEFAULT, SpectrumPollPolicy.of("balanced"))
        for (policy in SpectrumPollPolicy.entries) {
            assertEquals(policy, SpectrumPollPolicy.of(policy.id), "id — контракт на диске")
        }
    }

    @Test
    fun `a slice survives the trip through a row unchanged`() {
        val slice = HistorySlice(
            startMillis = 1_000L,
            endMillis = 31_000L,
            durationMillis = 30_000L,
            schemeId = SpectrogramBinning.CURRENT_SCHEME,
            bandCounts = IntArray(96) { it },
            cps = 24.5f,
            doseMicroSvH = 0.17f,
            sliceCount = 6,
        )
        val restored = slice.toEntity().toSlice()
        assertEquals(slice.startMillis, restored.startMillis)
        assertEquals(slice.endMillis, restored.endMillis)
        assertEquals(slice.durationMillis, restored.durationMillis)
        assertEquals(slice.schemeId, restored.schemeId)
        assertEquals(slice.sliceCount, restored.sliceCount)
        assertEquals(slice.cps, restored.cps)
        assertEquals(slice.doseMicroSvH, restored.doseMicroSvH)
        assertContentEquals(slice.bandCounts, restored.bandCounts)
    }
}
