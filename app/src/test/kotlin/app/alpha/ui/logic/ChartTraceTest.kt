package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Вывод о месте потери делается не рассуждением, а по трём срезам одного окна.
 *
 * Конвейер объявлен архитектурой: Room → снимок → кадр → канва. Когда график
 * «замирает», по картинке нельзя сказать, где потеря, и чинить последний этап
 * по догадке — самый быстрый способ починить не то.
 */
class ChartTraceTest {

    private val now = 1_700_000_000_000L

    private fun pass(
        roomCount: Int,
        snapshotBuckets: Int,
        frameBuckets: Int,
        roomMax: Long? = now,
        frameMax: Long? = now,
    ) = ChartTrace.Pass(
        atMillis = now,
        metric = "dose",
        nowMillis = now,
        windowStart = now - 60_000,
        windowEnd = now,
        roomCount = roomCount,
        roomMin = now - 60_000,
        roomMax = roomMax,
        snapshotBuckets = snapshotBuckets,
        snapshotMin = now - 60_000,
        snapshotMax = roomMax,
        frameBuckets = frameBuckets,
        frameMin = now - 60_000,
        frameMax = frameMax,
    )

    @Test
    fun `an empty window in the database is a question to recording, not to the chart`() {
        assertEquals(
            ChartTrace.Verdict.NO_DATA_IN_ROOM,
            ChartTrace.verdict(pass(roomCount = 0, snapshotBuckets = 0, frameBuckets = 0)),
        )
    }

    @Test
    fun `a full database and an empty snapshot points at the query and the window`() {
        assertEquals(
            ChartTrace.Verdict.LOST_IN_SNAPSHOT,
            ChartTrace.verdict(pass(roomCount = 60, snapshotBuckets = 0, frameBuckets = 0)),
        )
    }

    @Test
    fun `a full snapshot and an empty frame points at folding and selection`() {
        assertEquals(
            ChartTrace.Verdict.LOST_IN_FRAME,
            ChartTrace.verdict(pass(roomCount = 60, snapshotBuckets = 60, frameBuckets = 0)),
        )
    }

    @Test
    fun `a full frame moves the question below the frame`() {
        assertEquals(
            ChartTrace.Verdict.FRAME_COMPLETE,
            ChartTrace.verdict(pass(roomCount = 60, snapshotBuckets = 60, frameBuckets = 60)),
        )
    }

    @Test
    fun `a complete frame can still lag, and the lag is a number`() {
        // «Все этапы полны, а график обрывается до сейчас» — это не отсутствие
        // данных, а отставание последней колонки, и оно измеряется.
        val lagging = pass(
            roomCount = 60,
            snapshotBuckets = 60,
            frameBuckets = 59,
            roomMax = now,
            frameMax = now - 30_000,
        )

        assertEquals(ChartTrace.Verdict.FRAME_COMPLETE, ChartTrace.verdict(lagging))
        assertEquals(30_000L, ChartTrace.frameLagMillis(lagging))
    }

    @Test
    fun `without data there is no lag to report`() {
        assertNull(
            ChartTrace.frameLagMillis(
                pass(roomCount = 0, snapshotBuckets = 0, frameBuckets = 0, roomMax = null),
            ),
        )
    }

    @Test
    fun `the ring keeps the freshest passes`() {
        val trace = ChartTrace()
        repeat(ChartTrace.CAPACITY + 10) {
            trace.add(pass(roomCount = it, snapshotBuckets = 1, frameBuckets = 1))
        }

        assertEquals(ChartTrace.CAPACITY, trace.snapshot().size)
        assertEquals(ChartTrace.CAPACITY + 9, trace.snapshot().last().roomCount)
    }
}
