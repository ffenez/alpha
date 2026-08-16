package app.radiacode.analysis

import app.radiacode.protocol.Spectrum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun spectrum(seconds: Long, counts: List<Int>) = Spectrum(
    durationSeconds = seconds,
    a0 = 0f,
    a1 = 2.4f,
    a2 = 0.0003f,
    counts = counts,
)

/**
 * Эпоха — то, что отделяет одно непрерывное накопление от другого. Прибор о
 * перезагрузке не сообщает, поэтому решение принимается по самим числам:
 * накопление обязано расти.
 */
class SpectrumEpochTest {

    private val first = spectrum(3_600, listOf(10, 20, 30))

    @Test
    fun `the first poll ever starts an epoch`() {
        val mark = SpectrumEpoch.mark(null, first, "RC-110-42", newEpochId = 100L)
        assertEquals(100L, mark.epochId)
        assertEquals(60L, mark.totalCounts)
    }

    @Test
    fun `growing accumulation stays in the same epoch`() {
        val start = SpectrumEpoch.mark(null, first, "RC-110-42", 100L)
        val later = SpectrumEpoch.mark(
            start,
            spectrum(3_700, listOf(11, 21, 31)),
            "RC-110-42",
            newEpochId = 200L,
        )
        assertEquals(100L, later.epochId)
    }

    /** Сброс виден по числам: длительность или счёты пошли вниз. */
    @Test
    fun `a reset starts a new epoch, whatever caused it`() {
        val start = SpectrumEpoch.mark(null, first, "RC-110-42", 100L)
        val afterReset = SpectrumEpoch.mark(
            start,
            spectrum(5, listOf(1, 1, 1)),
            "RC-110-42",
            newEpochId = 200L,
        )
        assertEquals(200L, afterReset.epochId)

        // И отдельно: счёты убыли при выросшей длительности — тоже новая.
        val shrunk = SpectrumEpoch.mark(
            start,
            spectrum(3_700, listOf(1, 1, 1)),
            "RC-110-42",
            newEpochId = 300L,
        )
        assertEquals(300L, shrunk.epochId)
    }

    @Test
    fun `another device is never the same accumulation`() {
        val start = SpectrumEpoch.mark(null, first, "RC-110-42", 100L)
        val other = SpectrumEpoch.mark(
            start,
            spectrum(7_200, listOf(100, 200, 300)),
            "RC-110-77",
            newEpochId = 200L,
        )
        assertEquals(200L, other.epochId)
    }
}

/**
 * Разность снимков — спектр за промежуток. Проверяется главным образом то,
 * когда движок ОТКАЗЫВАЕТСЯ считать: придуманная разность опаснее её
 * отсутствия, потому что на ней строятся выводы о продукте и об источнике.
 */
class SpectrumDeltaTest {

    private fun snapshot(
        counts: List<Int>,
        seconds: Long,
        serial: String? = "RC-110-42",
        epoch: Long? = 1L,
        a1: Float = 2.4f,
    ) = SpectrumDelta.Snapshot(
        counts = counts,
        durationSeconds = seconds,
        a0 = 0f,
        a1 = a1,
        a2 = 0.0003f,
        deviceSerial = serial,
        epochId = epoch,
    )

    @Test
    fun `the interval spectrum is the difference of two moments`() {
        val delta = SpectrumDelta.of(
            from = snapshot(listOf(10, 20, 30), 600),
            to = snapshot(listOf(15, 26, 45), 1_200),
        )
        val available = assertIs<SpectrumDelta.Delta.Available>(delta)
        assertEquals(listOf(5, 6, 15), available.counts)
        assertEquals(600L, available.durationSeconds)
        assertEquals(26L, available.totalCounts)
        assertTrue(available.countRate > 0.04 && available.countRate < 0.05)
    }

    @Test
    fun `snapshots without provenance are never subtracted`() {
        val delta = SpectrumDelta.of(
            from = snapshot(listOf(1), 10, epoch = null),
            to = snapshot(listOf(2), 20),
        )
        assertEquals(
            SpectrumDelta.Reason.NO_PROVENANCE,
            assertIs<SpectrumDelta.Delta.Unavailable>(delta).reason,
        )
    }

    @Test
    fun `different device, epoch, calibration or channel count all refuse`() {
        fun reason(delta: SpectrumDelta.Delta) =
            assertIs<SpectrumDelta.Delta.Unavailable>(delta).reason

        assertEquals(
            SpectrumDelta.Reason.DIFFERENT_DEVICE,
            reason(
                SpectrumDelta.of(
                    snapshot(listOf(1, 1), 10, serial = "RC-110-42"),
                    snapshot(listOf(2, 2), 20, serial = "RC-110-77"),
                ),
            ),
        )
        assertEquals(
            SpectrumDelta.Reason.DIFFERENT_EPOCH,
            reason(
                SpectrumDelta.of(
                    snapshot(listOf(1, 1), 10, epoch = 1L),
                    snapshot(listOf(2, 2), 20, epoch = 2L),
                ),
            ),
        )
        assertEquals(
            SpectrumDelta.Reason.DIFFERENT_CHANNELS,
            reason(SpectrumDelta.of(snapshot(listOf(1, 1), 10), snapshot(listOf(2, 2, 2), 20))),
        )
        assertEquals(
            SpectrumDelta.Reason.DIFFERENT_CALIBRATION,
            reason(
                SpectrumDelta.of(
                    snapshot(listOf(1, 1), 10, a1 = 2.4f),
                    snapshot(listOf(2, 2), 20, a1 = 3.1f),
                ),
            ),
        )
    }

    @Test
    fun `a snapshot cannot be subtracted from an earlier one`() {
        val delta = SpectrumDelta.of(snapshot(listOf(5), 600), snapshot(listOf(9), 600))
        assertEquals(
            SpectrumDelta.Reason.NOT_LATER,
            assertIs<SpectrumDelta.Delta.Unavailable>(delta).reason,
        )
    }

    /**
     * Канал накопительного спектра убыть не может. Убыл — между снимками
     * произошло то, чего мы не заметили, и «поправить» это нулём значило бы
     * скрыть событие.
     */
    @Test
    fun `a channel that went down refuses the whole difference`() {
        val delta = SpectrumDelta.of(
            from = snapshot(listOf(10, 20), 600),
            to = snapshot(listOf(12, 19), 1_200),
        )
        assertEquals(
            SpectrumDelta.Reason.NOT_MONOTONIC,
            assertIs<SpectrumDelta.Delta.Unavailable>(delta).reason,
        )
    }
}
