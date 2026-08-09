package app.radiacode.data

import app.radiacode.data.db.DownsampledSample
import app.radiacode.data.db.EventDao
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.RangeStats
import app.radiacode.data.db.RareDataDao
import app.radiacode.data.db.RareDataEntity
import app.radiacode.data.db.SampleDao
import app.radiacode.data.db.SampleEntity
import app.radiacode.data.db.SpectrumDao
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

private class FakeSampleDao : SampleDao {
    val inserted = mutableListOf<SampleEntity>()
    override suspend fun insertAll(samples: List<SampleEntity>) { inserted += samples }
    override fun observeLatest(): Flow<SampleEntity?> = flowOf(inserted.lastOrNull())
    override fun observeRange(from: Long, to: Long): Flow<List<SampleEntity>> = flowOf(emptyList())
    override suspend fun downsampledRange(from: Long, to: Long, bucketMillis: Long): List<DownsampledSample> = emptyList()
    override suspend fun downsampledRangeForPlace(placeId: Long, from: Long, to: Long, bucketMillis: Long): List<DownsampledSample> = emptyList()
    override suspend fun rangeStats(from: Long, to: Long): RangeStats =
        RangeStats(0, null, null, null, null, null)
    override suspend fun detachPlace(placeId: Long) {}
    override suspend fun count(): Long = inserted.size.toLong()
    override suspend fun latestTimestamp(): Long? = inserted.maxOfOrNull { it.timestamp }
    override suspend fun deleteOlderThan(before: Long): Int = 0
}

private class FakeRareDataDao : RareDataDao {
    val inserted = mutableListOf<RareDataEntity>()
    override suspend fun insertAll(entries: List<RareDataEntity>) { inserted += entries }
    override fun observeLatest(): Flow<RareDataEntity?> = flowOf(inserted.lastOrNull())
    override fun observeRange(from: Long, to: Long): Flow<List<RareDataEntity>> = flowOf(emptyList())
}

private class FakeEventDao : EventDao {
    val inserted = mutableListOf<EventEntity>()
    override suspend fun insert(event: EventEntity): Long { inserted += event; return inserted.size.toLong() }
    override suspend fun insertAll(events: List<EventEntity>) { inserted += events }
    override fun observeRecent(limit: Int): Flow<List<EventEntity>> = flowOf(inserted.takeLast(limit))
    override fun observeRange(from: Long, to: Long): Flow<List<EventEntity>> = flowOf(emptyList())
    override suspend fun inRangeBySource(from: Long, to: Long, sources: List<String>, limit: Int): List<EventEntity> =
        inserted.filter { it.timestamp in from..to && it.source in sources }.take(limit)
}

private class FakeSpectrumDao : SpectrumDao {
    val inserted = mutableListOf<SpectrumSnapshotEntity>()
    override suspend fun insert(snapshot: SpectrumSnapshotEntity): Long { inserted += snapshot; return inserted.size.toLong() }
    override fun observeLatest(accumulated: Boolean): Flow<SpectrumSnapshotEntity?> =
        flowOf(inserted.lastOrNull { it.accumulated == accumulated })
    override fun observeRange(from: Long, to: Long): Flow<List<SpectrumSnapshotEntity>> = flowOf(emptyList())
    override suspend fun countInRange(from: Long, to: Long): Int =
        inserted.count { it.timestamp in from..to }
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
        assertEquals(spectrum, entity.toSpectrum())
    }
}
