package app.radiacode.data

import app.radiacode.data.db.DownsampledSample
import app.radiacode.data.db.EventDao
import app.radiacode.data.db.EventEntity
import app.radiacode.data.db.RareDataDao
import app.radiacode.data.db.RareDataEntity
import app.radiacode.data.db.SampleDao
import app.radiacode.data.db.SampleEntity
import app.radiacode.data.db.SpectrumDao
import app.radiacode.data.db.SpectrumMetaRow
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.protocol.DataBufRecord
import app.radiacode.protocol.Event
import app.radiacode.protocol.RareData
import app.radiacode.protocol.RealTimeData
import app.radiacode.protocol.Spectrum
import kotlinx.coroutines.flow.Flow

/**
 * Persistence facade for measurement data. All flows are Room-backed and
 * update automatically on writes; suitable for direct UI consumption.
 */
class MeasurementRepository(
    private val sampleDao: SampleDao,
    private val rareDataDao: RareDataDao,
    private val eventDao: EventDao,
    private val spectrumDao: SpectrumDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Persists one decoded DATA_BUF batch, routing record types to their
     * tables. [placeId] stamps real-time samples with the place active at
     * write time (per-place baseline input).
     */
    suspend fun record(records: List<DataBufRecord>, placeId: Long? = null) {
        val samples = records.filterIsInstance<RealTimeData>().map { it.toEntity(placeId) }
        if (samples.isNotEmpty()) sampleDao.insertAll(samples)

        val rare = records.filterIsInstance<RareData>().map { it.toEntity() }
        if (rare.isNotEmpty()) rareDataDao.insertAll(rare)

        val events = records.filterIsInstance<Event>().map { it.toEntity() }
        if (events.isNotEmpty()) eventDao.insertAll(events)
    }

    /**
     * Track hotspot: a threshold crossing while recording. `param1` carries
     * the baseline typical high in nSv/h at event time (0 = no baseline),
     * same convention as deviations — the map card can honestly say
     * «обычно здесь X» as of that moment.
     */
    suspend fun recordHotspot(
        timestamp: Long,
        doseRate: Float,
        latitude: Double?,
        longitude: Double?,
        baselineHighMicroSvH: Float? = null,
    ) {
        eventDao.insert(
            EventEntity(
                timestamp = timestamp,
                source = EventEntity.SOURCE_HOTSPOT,
                code = 0,
                name = "HOTSPOT",
                param1 = ((baselineHighMicroSvH ?: 0f) * 1000f).toInt(),
                flags = 0,
                doseRate = doseRate,
                latitude = latitude,
                longitude = longitude,
            ),
        )
    }

    /**
     * Journal entry for a confirmed persistent baseline deviation (SPEC
     * «Radiation level changed»). `param1` carries the baseline typical high
     * in nSv/h at event time (0 = baseline was not active).
     */
    suspend fun recordDeviation(
        timestamp: Long,
        doseRate: Float,
        baselineHighMicroSvH: Float?,
    ) {
        eventDao.insert(
            EventEntity(
                timestamp = timestamp,
                source = EventEntity.SOURCE_DEVIATION,
                code = 0,
                name = "DEVIATION",
                param1 = ((baselineHighMicroSvH ?: 0f) * 1000f).toInt(),
                flags = 0,
                doseRate = doseRate,
            ),
        )
    }

    suspend fun saveSpectrum(
        spectrum: Spectrum,
        accumulated: Boolean,
        isBackgroundReference: Boolean = false,
        origin: String = SpectrumSnapshotEntity.ORIGIN_AUTO,
        label: String? = null,
    ): SpectrumSnapshotEntity {
        val entity = spectrum.toEntity(
            timestamp = clock(),
            accumulated = accumulated,
            isBackgroundReference = isBackgroundReference,
            origin = origin,
            label = label,
        )
        spectrumDao.insert(entity)
        return entity
    }

    /**
     * Persists an imported RC-XML spectrum. [timestamp] is the file's own
     * measurement time when it carries one (raw data preserved), otherwise the
     * import moment. Imported rows never mix into device-data queries.
     */
    suspend fun importSpectrum(
        spectrum: Spectrum,
        label: String?,
        timestamp: Long? = null,
    ): SpectrumSnapshotEntity {
        val entity = spectrum.toEntity(
            timestamp = timestamp ?: clock(),
            accumulated = false,
            origin = SpectrumSnapshotEntity.ORIGIN_IMPORT,
            label = label,
        )
        spectrumDao.insert(entity)
        return entity
    }

    /** Journal entry for an explicit user save on the Спектр screen. */
    suspend fun recordSpectrumSaved(timestamp: Long, accumulationSeconds: Long) {
        eventDao.insert(
            EventEntity(
                timestamp = timestamp,
                source = EventEntity.SOURCE_SPECTRUM,
                code = 0,
                name = "SPECTRUM_SAVED",
                param1 = accumulationSeconds.toInt(),
                flags = 0,
            ),
        )
    }

    fun latestSample(): Flow<SampleEntity?> = sampleDao.observeLatest()

    fun samples(from: Long, to: Long): Flow<List<SampleEntity>> = sampleDao.observeRange(from, to)

    suspend fun downsampledSamples(from: Long, to: Long, bucketMillis: Long): List<DownsampledSample> =
        sampleDao.downsampledRange(from, to, bucketMillis)

    fun latestRareData(): Flow<RareDataEntity?> = rareDataDao.observeLatest()

    fun recentEvents(limit: Int = 100): Flow<List<EventEntity>> = eventDao.observeRecent(limit)

    fun latestSpectrum(accumulated: Boolean): Flow<SpectrumSnapshotEntity?> =
        spectrumDao.observeLatest(accumulated)

    /** Newest background reference recorded on the Спектр screen; null = none yet. */
    fun backgroundReference(): Flow<SpectrumSnapshotEntity?> =
        spectrumDao.observeBackgroundReference()

    /** User-saved and imported snapshots for История (autosaves excluded). */
    fun savedSpectra(limit: Int = 50): Flow<List<SpectrumSnapshotEntity>> =
        spectrumDao.observeSaved(limit)

    suspend fun spectrumById(id: Long): SpectrumSnapshotEntity? = spectrumDao.byId(id)

    /** Device snapshot metadata (no blobs) for the radon hourly thinning. */
    suspend fun deviceSnapshotMeta(from: Long, to: Long): List<SpectrumMetaRow> =
        spectrumDao.deviceSnapshotMeta(from, to)
}
