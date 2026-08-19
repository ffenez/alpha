package app.alpha.data

import app.alpha.baseline.AlarmThresholds
import app.alpha.baseline.LevelEventKind
import app.alpha.baseline.LevelEventTracker
import app.alpha.baseline.LevelEventTransition
import app.alpha.data.db.EventEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Журнал как ЛЕНТА СМЫСЛА: эпизод занимает одну строку и обновляется, а не
 * плодит записи (`history_semantic_events_redesign.md`).
 *
 * Работает на той же связке трекер → репозиторий, что и служба, поэтому ловит
 * разрыв между ними: до правки строка писалась на каждое срабатывание.
 */
class EpisodeJournalTest {

    private val thresholds = AlarmThresholds(
        l1MicroSvH = 0.30f,
        l2MicroSvH = 1.00f,
        relativeFactor = 2.0f,
        persistenceSeconds = 120,
    )

    /** Тот же журнал в памяти, что у службы: открыть, обновлять, закрыть. */
    private class Journal {
        val rows = mutableListOf<EventEntity>()
        private var openId: Int? = null

        fun apply(transition: LevelEventTransition) {
            when (transition) {
                LevelEventTransition.None -> Unit
                is LevelEventTransition.Opened -> {
                    rows += row(transition.event, blankEvent())
                    openId = rows.lastIndex
                }
                is LevelEventTransition.Updated -> {
                    val id = openId ?: return
                    rows[id] = row(transition.event, rows[id])
                }
                is LevelEventTransition.Closed -> {
                    val id = openId ?: return
                    rows[id] = row(transition.event, rows[id])
                    openId = null
                }
            }
        }

        private fun row(event: app.alpha.baseline.LevelEvent, previous: EventEntity) =
            previous.copy(
                timestamp = event.startMillis,
                source = when (event.kind) {
                    LevelEventKind.THRESHOLD -> EventEntity.SOURCE_THRESHOLD
                    LevelEventKind.LEVEL_CHANGE -> EventEntity.SOURCE_LEVEL_CHANGE
                },
                code = 0,
                name = event.kind.name,
                param1 = ((event.baselineHighMicroSvH ?: 0f) * 1000f).toInt(),
                flags = 0,
                doseRate = event.maxMicroSvH,
                endTimestamp = event.endMillis,
                minMicroSvH = event.minMicroSvH,
                maxMicroSvH = event.maxMicroSvH,
                meanMicroSvH = event.meanMicroSvH,
                sampleCount = event.sampleCount,
                thresholdMicroSvH = event.thresholdMicroSvH,
            )
    }

    private fun run(values: List<Float>, baseline: Float? = 0.18f): Journal {
        val tracker = LevelEventTracker(persistenceMillis = 120_000L)
        val journal = Journal()
        values.forEachIndexed { i, value ->
            journal.apply(
                tracker.onSample(i * 1_000L, value, baseline, thresholds),
            )
        }
        return journal
    }

    @Test
    fun `сутки спокойного фона не оставляют в журнале ни строки`() {
        val values = List(86_400) { i -> 0.155f + (i % 4 - 1.5f) * 0.01f }
        assertTrue(run(values).rows.isEmpty(), "строк: ${run(values).rows.size}")
    }

    @Test
    fun `двадцать минут превышения — ОДНА строка`() {
        val journal = run(List(1_200) { 0.42f })
        assertEquals(1, journal.rows.size, "строк: ${journal.rows.size}")
        val row = journal.rows.single()
        assertEquals(EventEntity.SOURCE_THRESHOLD, row.source)
        assertTrue(row.ongoing, "строка закрылась, хотя эпизод идёт")
        assertEquals(0L, row.timestamp)
        assertEquals(1_200, row.sampleCount)
    }

    @Test
    fun `час болтанки у порога тоже одна строка, а не десятки`() {
        // Прежняя схема писала бы точку на каждое подтверждение.
        val journal = run(List(3_600) { i -> if (i % 2 == 0) 0.32f else 0.28f })
        assertEquals(1, journal.rows.size, "строк: ${journal.rows.size}")
    }

    @Test
    fun `возврат закрывает ту же строку, а не заводит новую`() {
        val journal = run(List(600) { 0.42f } + List(600) { 0.16f })
        assertEquals(1, journal.rows.size)
        val row = journal.rows.single()
        assertEquals(600_000L, row.endTimestamp)
        assertTrue(!row.ongoing)
        assertEquals(0.42f, row.minMicroSvH)
        assertEquals(0.42f, row.maxMicroSvH)
    }

    @Test
    fun `изменение уровня и превышение порога — разные записи журнала`() {
        val levelChange = run(List(300) { 0.25f }, baseline = 0.12f).rows.single()
        assertEquals(EventEntity.SOURCE_LEVEL_CHANGE, levelChange.source)
        assertEquals(0.30f, levelChange.thresholdMicroSvH)

        val threshold = run(List(300) { 0.40f }).rows.single()
        assertEquals(EventEntity.SOURCE_THRESHOLD, threshold.source)
    }

    @Test
    fun `дошедший до порога эпизод меняет вид, а не добавляет строку`() {
        val journal = run(List(300) { 0.25f } + List(300) { 0.35f }, baseline = 0.12f)
        assertEquals(1, journal.rows.size, "строк: ${journal.rows.size}")
        assertEquals(EventEntity.SOURCE_THRESHOLD, journal.rows.single().source)
    }

    @Test
    fun `обычный верх места сохраняется в записи`() {
        val row = run(List(300) { 0.42f }).rows.single()
        assertEquals(180, row.param1, "обычный верх в нСв/ч")
    }

    @Test
    fun `запись без интервала эпизодом не считается`() {
        // Прежние точечные записи: у них нет ни конца, ни числа отсчётов, и
        // «идёт до сих пор» о них сказать нельзя.
        val legacy = blankEvent().copy(
            timestamp = 1_000L,
            source = EventEntity.SOURCE_DEVIATION,
            doseRate = 0.4f,
        )
        assertTrue(!legacy.ongoing)
        assertNull(legacy.sampleCount)
        assertNotNull(EventEntity.EPISODE_SOURCES.firstOrNull())
        assertTrue(EventEntity.SOURCE_DEVIATION !in EventEntity.EPISODE_SOURCES)
    }
}

/** Пустая запись журнала: у [EventEntity] нет значений по умолчанию. */
private fun blankEvent() = EventEntity(
    timestamp = 0L,
    source = "",
    code = 0,
    name = "",
    param1 = 0,
    flags = 0,
)
