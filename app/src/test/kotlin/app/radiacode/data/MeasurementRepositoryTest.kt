package app.radiacode.data

import app.radiacode.data.db.DoseBucketAggregate
import app.radiacode.data.db.DownsampledSample
import app.radiacode.data.db.ExclusionCount
import app.radiacode.data.db.EventDao
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.RangeStats
import app.radiacode.data.db.RareDataDao
import app.radiacode.data.db.RareDataEntity
import app.radiacode.data.db.SampleDao
import app.radiacode.data.db.RangeCensus
import app.radiacode.data.db.SampleEntity
import app.radiacode.data.db.SpectrumDao
import app.radiacode.data.db.SpectrumMetaRow
import app.radiacode.data.db.ValueBucketAggregate
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.protocol.Event
import app.radiacode.protocol.EventId
import app.radiacode.protocol.RareData
import app.radiacode.protocol.RealTimeData
import app.radiacode.protocol.Spectrum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shared by [ExperimentRepositoryTest] — same package, same fakes. */
internal class FakeSampleDao : SampleDao {

    // Резервная копия читает базу постранично; подделке достаточно ответить
    // «больше ничего нет» — её проверяют другие тесты.
    override suspend fun page(afterId: Long, limit: Int): List<SampleEntity> = emptyList()

    override suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<SampleEntity> =
        emptyList()

    override suspend fun countSince(from: Long): Long = 0

    override suspend fun clear() = Unit

    val inserted = mutableListOf<SampleEntity>()

    /** Метки, которые «уже заняты»: фейк повторяет уникальный индекс базы. */
    val occupiedTimestamps = mutableSetOf<Long>()

    override suspend fun insertAll(samples: List<SampleEntity>): List<Long> = samples.map {
        if (!occupiedTimestamps.add(it.timestamp)) {
            // -1 = строка ОТБРОШЕНА уникальным индексом, ровно как в Room.
            -1L
        } else {
            inserted += it
            inserted.size.toLong()
        }
    }
    override fun observeLatest(): Flow<SampleEntity?> = flowOf(inserted.lastOrNull())
    override fun observeRange(from: Long, to: Long): Flow<List<SampleEntity>> = flowOf(emptyList())
    override suspend fun earliestTimestamp(): Long? = inserted.minOfOrNull { it.timestamp }

    override suspend fun rangeCensus(from: Long, to: Long): RangeCensus {
        val window = inserted.filter { it.timestamp in from..to }
        return RangeCensus(
            count = window.size,
            minTimestamp = window.minOfOrNull { it.timestamp },
            maxTimestamp = window.maxOfOrNull { it.timestamp },
        )
    }

    override suspend fun rangeList(from: Long, to: Long): List<SampleEntity> =
        inserted.filter { it.timestamp in from..to }.sortedBy { it.timestamp }
    override suspend fun downsampledRange(from: Long, to: Long, bucketMillis: Long): List<DownsampledSample> = emptyList()
    override suspend fun downsampledRangeForProfile(profileId: Long, from: Long, to: Long, bucketMillis: Long): List<DownsampledSample> = emptyList()
    override suspend fun doseBucketRange(from: Long, to: Long, bucketMillis: Long): List<DoseBucketAggregate> = emptyList()
    override suspend fun countRateBucketRange(from: Long, to: Long, bucketMillis: Long): List<ValueBucketAggregate> = emptyList()
    override suspend fun hardnessBucketRange(from: Long, to: Long, bucketMillis: Long, minCountRate: Float): List<ValueBucketAggregate> = emptyList()
    override suspend fun exclusionCountsForProfile(profileId: Long, from: Long, to: Long): List<ExclusionCount> = emptyList()
    override suspend fun exclusionCountsInRange(from: Long, to: Long): List<ExclusionCount> = emptyList()
    override suspend fun admittedCountInRange(from: Long, to: Long): Int = 0
    override suspend fun admittedCountForProfile(profileId: Long, from: Long, to: Long): Int = 0
    override suspend fun reassignRange(from: Long, to: Long, profileId: Long?) {}
    override suspend fun rewriteLearningVerdict(from: Long, to: Long, reason: String?, learningOffReason: String) {}
    override suspend fun rangeStats(from: Long, to: Long): RangeStats =
        RangeStats(0, null, null, null, null, null)
    override suspend fun detachProfile(profileId: Long) {}
    override suspend fun count(): Long = inserted.size.toLong()
    override suspend fun latestTimestamp(): Long? = inserted.maxOfOrNull { it.timestamp }
    override suspend fun deleteRange(from: Long, to: Long): Int {
        val before = inserted.size
        inserted.removeAll { it.timestamp in from..to }
        return before - inserted.size
    }
    override suspend fun deleteOlderThan(before: Long): Int = 0
}

private class FakeRareDataDao : RareDataDao {

    override suspend fun page(afterId: Long, limit: Int): List<RareDataEntity> = emptyList()

    override suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<RareDataEntity> =
        emptyList()

    override suspend fun countSince(from: Long): Long = 0

    override suspend fun count(): Long = 0

    override suspend fun clear() = Unit

    val inserted = mutableListOf<RareDataEntity>()
    override suspend fun insertAll(entries: List<RareDataEntity>): List<Long> {
        inserted += entries
        return entries.map { 1L }
    }
    override fun observeLatest(): Flow<RareDataEntity?> = flowOf(inserted.lastOrNull())
    override fun observeRange(from: Long, to: Long): Flow<List<RareDataEntity>> = flowOf(emptyList())
}

private class FakeEventDao : EventDao {

    override suspend fun page(afterId: Long, limit: Int): List<EventEntity> = emptyList()

    override suspend fun pageSince(afterId: Long, from: Long, limit: Int): List<EventEntity> =
        emptyList()

    override suspend fun countSince(from: Long): Long = 0

    override suspend fun count(): Long = 0

    override suspend fun existingTimestamps(timestamps: List<Long>, source: String): List<Long> =
        emptyList()

    override suspend fun clear() = Unit

    val inserted = mutableListOf<EventEntity>()
    override suspend fun countInRange(from: Long, to: Long): Int =
        inserted.count { it.timestamp in from..to }
    override suspend fun deleteRange(from: Long, to: Long): Int {
        val before = inserted.size
        inserted.removeAll { it.timestamp in from..to }
        return before - inserted.size
    }
    override suspend fun insert(event: EventEntity): Long { inserted += event; return inserted.size.toLong() }
    override suspend fun insertAll(events: List<EventEntity>) { inserted += events }
    override fun observeRecent(limit: Int): Flow<List<EventEntity>> = flowOf(inserted.takeLast(limit))
    override fun observeRange(from: Long, to: Long): Flow<List<EventEntity>> = flowOf(emptyList())
    override suspend fun inRangeBySource(from: Long, to: Long, sources: List<String>, limit: Int): List<EventEntity> =
        inserted.filter { it.timestamp in from..to && it.source in sources }.take(limit)
    override suspend fun locatedInBounds(
        source: String,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        limit: Int,
    ): List<EventEntity> = inserted
        .filter { event ->
            val latitude = event.latitude
            val longitude = event.longitude
            event.source == source && latitude != null && longitude != null &&
                latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
        }
        .sortedByDescending { it.timestamp }
        .take(limit)
}

internal class FakeSpectrumDao : SpectrumDao {

    override suspend fun page(afterId: Long, limit: Int): List<SpectrumSnapshotEntity> = emptyList()

    override suspend fun pageSince(
        afterId: Long,
        from: Long,
        limit: Int,
    ): List<SpectrumSnapshotEntity> = emptyList()

    override suspend fun countSince(from: Long): Long = 0

    override suspend fun existingTimestamps(timestamps: List<Long>): List<Long> = emptyList()

    override suspend fun clear() = Unit

    override fun observeLatestDeviceSnapshotAt(): Flow<Long?> = flowOf(null)

    val inserted = mutableListOf<SpectrumSnapshotEntity>()
    override suspend fun count(): Long = inserted.size.toLong()
    override suspend fun deleteByIds(ids: List<Long>): Int {
        val before = inserted.size
        inserted.removeAll { it.id in ids }
        return before - inserted.size
    }
    override suspend fun insert(snapshot: SpectrumSnapshotEntity): Long { inserted += snapshot; return inserted.size.toLong() }
    override fun observeLatest(accumulated: Boolean): Flow<SpectrumSnapshotEntity?> =
        flowOf(
            inserted.lastOrNull {
                it.accumulated == accumulated && it.origin != SpectrumSnapshotEntity.ORIGIN_IMPORT
            },
        )
    override fun observeBackgroundReference(): Flow<SpectrumSnapshotEntity?> =
        flowOf(
            inserted.lastOrNull {
                it.isBackgroundReference && it.origin != SpectrumSnapshotEntity.ORIGIN_IMPORT
            },
        )
    override fun observeRange(from: Long, to: Long): Flow<List<SpectrumSnapshotEntity>> = flowOf(emptyList())
    override suspend fun countInRange(from: Long, to: Long): Int =
        inserted.count {
            it.timestamp in from..to && it.origin != SpectrumSnapshotEntity.ORIGIN_IMPORT
        }
    override fun observeSaved(limit: Int): Flow<List<SpectrumSnapshotEntity>> =
        flowOf(
            inserted.filter {
                it.origin != SpectrumSnapshotEntity.ORIGIN_AUTO || it.isBackgroundReference
            }.sortedByDescending { it.timestamp }.take(limit),
        )
    override suspend fun byId(id: Long): SpectrumSnapshotEntity? =
        inserted.getOrNull(id.toInt() - 1)
    override suspend fun deviceSnapshotMeta(from: Long, to: Long): List<SpectrumMetaRow> =
        inserted.mapIndexed { index, entity -> Triple(index + 1L, entity, Unit) }
            .filter { (_, e, _) ->
                e.origin != SpectrumSnapshotEntity.ORIGIN_IMPORT &&
                    !e.accumulated && e.timestamp in from..to
            }
            .map { (id, e, _) -> SpectrumMetaRow(id, e.timestamp, e.durationSeconds) }
}

class MeasurementRepositoryTest {

    private val sampleDao = FakeSampleDao()
    private val rareDataDao = FakeRareDataDao()
    private val eventDao = FakeEventDao()
    private val spectrumDao = FakeSpectrumDao()
    private val repository = MeasurementRepository(
        sampleDao = sampleDao,
        rareDataDao = rareDataDao,
        eventDao = eventDao,
        spectrumDao = spectrumDao,
        clock = { 123_456L },
    )

    @Test
    fun `a different measurement on a taken timestamp is reseated, not lost`() = runTest {
        // Полевой случай: «нет новых данных · 29 с» при зелёном кружке связи.
        // Уникальный индекс `samples.timestamp` отбрасывает строку молча — до
        // этого счётчика отброс был не отличим от записи ни на экране, ни в
        // логах, и три захода на починку шли по рассуждению вместо наблюдения.
        val at = 1_700_000_000_000L

        val first = repository.record(listOf(RealTimeData(at, 0, 10f, 1f, 0.0004f, 2f, 0, 0)))
        assertEquals(1, first.inserted)
        assertEquals(0, first.dropped)

        // Другое измерение с той же меткой — это НЕ повторная доставка, и
        // терять его нельзя: на графике потеря выглядит как обрыв линии при
        // исправно идущем потоке. Метка сдвигается на свободную миллисекунду.
        val second = repository.record(listOf(RealTimeData(at, 0, 25f, 1f, 0.0006f, 2f, 0, 0)))
        assertEquals(1, second.inserted)
        assertEquals(0, second.dropped)
        assertEquals(2, sampleDao.inserted.size)
        assertEquals(at + 1, sampleDao.inserted.last().timestamp)
        // Сдвиг — миллисекунды: ниже любого разрешения анализа (корзины
        // графиков считаются секундами и минутами).
        assertEquals(25f, sampleDao.inserted.last().countRate)
    }

    @Test
    fun `the same reading delivered twice is not written twice`() = runTest {
        // Буфер прибора отдаёт записи повторно — для этого уникальный индекс и
        // существует. Пересадка обязана отличать повтор от столкновения.
        val at = 1_700_000_000_000L
        val record = RealTimeData(at, 0, 10f, 1f, 0.0004f, 2f, 0, 0)

        repository.record(listOf(record))
        val again = repository.record(listOf(record))

        assertEquals(0, again.inserted)
        assertEquals(1, again.dropped)
        assertEquals(1, sampleDao.inserted.size)
    }

    @Test
    fun `routes DATA_BUF record types to their tables`() = runTest {
        val batch = listOf(
            RealTimeData(1_000, 0, 10f, 1f, 0.0004f, 2f, 0, 0),
            RealTimeData(2_000, 100, 11f, 1f, 0.0005f, 2f, 0, 0),
            RareData(3_000, 200, 3600, 0.2f, 20f, 90f, 0),
            Event(4_000, 300, EventId.CHARGE_START, 7, 0, 0),
        )

        repository.record(batch)

        assertEquals(listOf(1_000L, 2_000L), sampleDao.inserted.map { it.timestamp })
        assertEquals(listOf(3_000L), rareDataDao.inserted.map { it.timestamp })
        assertEquals(listOf(4_000L), eventDao.inserted.map { it.timestamp })
    }

    @Test
    fun `empty batch inserts nothing`() = runTest {
        repository.record(emptyList())
        assertTrue(sampleDao.inserted.isEmpty())
        assertTrue(rareDataDao.inserted.isEmpty())
        assertTrue(eventDao.inserted.isEmpty())
    }

    @Test
    fun `hotspot event carries dose rate and location`() = runTest {
        repository.recordHotspot(timestamp = 9_000, doseRate = 0.001f, latitude = 55.75, longitude = 37.62)

        val event = eventDao.inserted.single()
        assertEquals(EventEntity.SOURCE_HOTSPOT, event.source)
        assertEquals(9_000, event.timestamp)
        assertEquals(0.001f, event.doseRate)
        assertEquals(55.75, event.latitude)
        assertEquals(37.62, event.longitude)
    }

    @Test
    fun `saves spectrum snapshot with repository clock`() = runTest {
        val spectrum = Spectrum(600, -6f, 2.4f, 0.0004f, List(1024) { it % 3 })

        repository.saveSpectrum(spectrum, accumulated = false)

        val entity = spectrumDao.inserted.single()
        assertEquals(123_456L, entity.timestamp)
        assertEquals(false, entity.accumulated)
        assertEquals(SpectrumSnapshotEntity.ORIGIN_AUTO, entity.origin)
        assertEquals(spectrum, entity.toSpectrum())
    }

    @Test
    fun `imported spectrum keeps file time and label, falls back to clock`() = runTest {
        val spectrum = Spectrum(3600, -6f, 2.4f, 0.0004f, List(1024) { it % 3 })

        repository.importSpectrum(spectrum, label = "Th-232", timestamp = 42_000L)
        repository.importSpectrum(spectrum, label = null, timestamp = null)

        val withTime = spectrumDao.inserted[0]
        assertEquals(SpectrumSnapshotEntity.ORIGIN_IMPORT, withTime.origin)
        assertEquals("Th-232", withTime.label)
        assertEquals(42_000L, withTime.timestamp)
        assertEquals(spectrum, withTime.toSpectrum())

        assertEquals(123_456L, spectrumDao.inserted[1].timestamp)
    }
}
