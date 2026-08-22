package app.alpha.data

import app.alpha.data.db.DoseBucketAggregate
import app.alpha.data.db.ValueBucketAggregate
import app.alpha.data.db.DownsampledSample
import app.alpha.data.db.EventDao
import app.alpha.baseline.LevelEvent
import app.alpha.baseline.LevelEventKind
import app.alpha.data.db.EventEntity
import app.alpha.data.db.RareDataDao
import app.alpha.data.db.RareDataEntity
import app.alpha.data.db.SampleDao
import app.alpha.data.db.RangeCensus
import app.alpha.data.db.SampleEntity
import app.alpha.data.db.SpectrumDao
import app.alpha.data.db.SpectrumMetaRow
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.protocol.DataBufRecord
import app.alpha.protocol.Event
import app.alpha.protocol.RareData
import app.alpha.protocol.RealTimeData
import app.alpha.protocol.Spectrum
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
     * tables. [profileId] stamps real-time samples with the profile active at
     * write time (per-profile baseline input); [admission] decides per sample
     * whether it may feed the baseline statistics — raw values are stored
     * either way (spec §4.2, §22).
     */
    /**
     * @param inserted сколько строк реально добавилось в `samples`.
     * @param dropped сколько отброшено уникальным индексом по `timestamp`.
     */
    data class RecordOutcome(val inserted: Int, val dropped: Int)

    /**
     * @param deviceSerial чей это поток. Пишется в каждую строку: приборы
     *   можно менять, а журнал один, и без признака записи двух кристаллов
     *   уже не разделить.
     */
    suspend fun record(
        records: List<DataBufRecord>,
        profileId: Long? = null,
        deviceSerial: String? = null,
        admission: (RealTimeData) -> String? = { null },
    ): RecordOutcome {
        val samples = records.filterIsInstance<RealTimeData>()
            .map { it.toEntity(profileId, admission(it), deviceSerial) }
        var inserted = 0
        var dropped = 0
        if (samples.isNotEmpty()) {
            val ids = sampleDao.insertAll(samples)
            inserted = ids.count { it != -1L }
            val rejected = samples.filterIndexed { index, _ -> ids[index] == -1L }
            // Отброшенная строка — это ЛИБО повторная доставка той же записи
            // (буфер прибора отдал её снова, и уникальный индекс работает как
            // задумано), ЛИБО другое измерение, метка которого совпала с уже
            // занятой: так бывает, когда база времени прибора переехала назад.
            // Второе — потеря данных, и её не должно быть: на графике она
            // выглядит как обрыв линии при исправно идущем потоке.
            val retried = if (rejected.isEmpty()) emptyList() else reseat(rejected)
            if (retried.isNotEmpty()) {
                val retryIds = sampleDao.insertAll(retried)
                inserted += retryIds.count { it != -1L }
            }
            dropped = rejected.size - retried.size
        }

        val rare = records.filterIsInstance<RareData>().map { it.toEntity(deviceSerial) }
        if (rare.isNotEmpty()) rareDataDao.insertAll(rare)

        val events = records.filterIsInstance<Event>().map { it.toEntity() }
        if (events.isNotEmpty()) eventDao.insertAll(events)
        return RecordOutcome(inserted = inserted, dropped = dropped)
    }

    /**
     * Пересаживает измерения, чьи метки заняты, на ближайшие свободные.
     *
     * Повторная доставка распознаётся по РАВЕНСТВУ значений: если в занятой
     * метке лежит то же показание, это та же запись, и второй раз она не
     * нужна. Если показание другое — это отдельное измерение, и терять его
     * нельзя; метка сдвигается на первую свободную миллисекунду. Сдвиг на
     * единицы миллисекунд ниже любого разрешения анализа (корзины графиков —
     * секунды и минуты) и не меняет ни одного вывода, а потеря измерения
     * меняет картинку на экране.
     */
    private suspend fun reseat(rejected: List<SampleEntity>): List<SampleEntity> {
        val from = rejected.minOf { it.timestamp }
        val to = rejected.maxOf { it.timestamp } + RESEAT_WINDOW_MILLIS
        val taken = sampleDao.rangeList(from, to).associateBy { it.timestamp }
        val occupied = taken.keys.toMutableSet()
        val out = mutableListOf<SampleEntity>()
        for (sample in rejected) {
            val stored = taken[sample.timestamp]
            val sameReading = stored != null &&
                stored.doseRate == sample.doseRate &&
                stored.countRate == sample.countRate
            if (sameReading) continue
            var stamp = sample.timestamp
            var step = 0
            while (stamp in occupied && step < RESEAT_WINDOW_MILLIS) {
                stamp += 1
                step += 1
            }
            if (stamp in occupied) continue
            occupied += stamp
            out += sample.copy(timestamp = stamp)
        }
        return out
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
     * Открыть эпизод: подтверждённое изменение уровня или превышение порога.
     *
     * Строка пишется ОДИН раз на эпизод и дальше обновляется
     * ([updateEpisode]). Прежний `recordDeviation` писал точку на каждое
     * срабатывание, и журнал превращался в лог детектора.
     *
     * @return id строки — по нему эпизод обновляется и закрывается
     */
    suspend fun openEpisode(event: LevelEvent): Long = eventDao.insert(
        EventEntity(
            timestamp = event.startMillis,
            source = sourceOf(event.kind),
            code = 0,
            name = event.kind.name,
            param1 = ((event.baselineHighMicroSvH ?: 0f) * 1000f).toInt(),
            flags = 0,
            doseRate = event.maxMicroSvH,
            endTimestamp = null,
            minMicroSvH = event.minMicroSvH,
            maxMicroSvH = event.maxMicroSvH,
            meanMicroSvH = event.meanMicroSvH,
            sampleCount = event.sampleCount,
            thresholdMicroSvH = event.thresholdMicroSvH,
        ),
    )

    /**
     * Обновить эпизод — идущий ([LevelEvent.active]) или закрываемый.
     *
     * `doseRate` держит максимум эпизода: карта и старые экраны читают именно
     * его, и максимум там честнее последнего отсчёта.
     */
    suspend fun updateEpisode(id: Long, event: LevelEvent) {
        eventDao.updateEpisode(
            id = id,
            source = sourceOf(event.kind),
            endTimestamp = event.endMillis,
            doseRate = event.maxMicroSvH,
            minMicroSvH = event.minMicroSvH,
            maxMicroSvH = event.maxMicroSvH,
            meanMicroSvH = event.meanMicroSvH,
            sampleCount = event.sampleCount,
        )
    }

    /**
     * Закрыть эпизоды, оставшиеся без конца: служба остановилась, не
     * дождавшись возврата. Концом берётся последний известный отсчёт эпизода —
     * выдумывать «идёт до сих пор» нельзя.
     */
    suspend fun closeOngoingEpisodes(atMillis: Long) {
        for (row in eventDao.ongoingEpisodes()) {
            eventDao.updateEpisode(
                id = row.id,
                source = row.source,
                endTimestamp = maxOf(row.timestamp, atMillis),
                doseRate = row.doseRate ?: 0f,
                minMicroSvH = row.minMicroSvH ?: 0f,
                maxMicroSvH = row.maxMicroSvH ?: 0f,
                meanMicroSvH = row.meanMicroSvH ?: 0f,
                sampleCount = row.sampleCount ?: 0,
            )
        }
    }

    private fun sourceOf(kind: LevelEventKind): String = when (kind) {
        LevelEventKind.THRESHOLD -> EventEntity.SOURCE_THRESHOLD
        LevelEventKind.LEVEL_CHANGE -> EventEntity.SOURCE_LEVEL_CHANGE
    }

    /**
     * Journal entry for a confirmed persistent baseline deviation (SPEC
     * «Radiation level changed»). `param1` carries the baseline typical high
     * in nSv/h at event time (0 = baseline was not active).
     */
    @Deprecated("Событие журнала — эпизод: openEpisode/updateEpisode")
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

    /**
     * Persists a snapshot. [analysisMeta] is the reproducibility stamp of a
     * *derived* spectrum (spec §22) — the method and parameters that produced
     * these counts; raw device snapshots leave it null. The row id is filled
     * in from the insert so callers can reference the snapshot afterwards.
     */
    suspend fun saveSpectrum(
        spectrum: Spectrum,
        accumulated: Boolean,
        isBackgroundReference: Boolean = false,
        origin: String = SpectrumSnapshotEntity.ORIGIN_AUTO,
        label: String? = null,
        analysisMeta: String? = null,
        trigger: String? = null,
        deviceSerial: String? = null,
        firmware: String? = null,
        epochId: Long? = null,
        /** Профиль на момент съёмки: ссылка и имя, каким оно было тогда. */
        profileId: Long? = null,
        profileName: String? = null,
    ): SpectrumSnapshotEntity {
        val entity = spectrum.toEntity(
            timestamp = clock(),
            accumulated = accumulated,
            isBackgroundReference = isBackgroundReference,
            origin = origin,
            label = label,
            analysisMeta = analysisMeta,
            trigger = trigger,
            deviceSerial = deviceSerial,
            firmware = firmware,
            epochId = epochId,
            profileId = profileId,
            profileName = profileName,
        )
        val id = spectrumDao.insert(entity)
        return entity.copy(id = id)
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
        // Идентификатор присваивает БАЗА (autoGenerate), и до вставки в
        // объекте стоит ноль. Возвращать его значило бы отдать ссылку в
        // никуда: открытый по такому id снимок оказывался пустым.
        val id = spectrumDao.insert(entity)
        return entity.copy(id = id)
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

    /** Начало истории измерений; null — измерений нет вовсе. */
    suspend fun earliestSampleMillis(): Long? = sampleDao.earliestTimestamp()

    /** Сигнал «появился новый приборный снимок» — для рядов по спектрам. */
    fun deviceSnapshotsChanged(): Flow<Long?> = spectrumDao.observeLatestDeviceSnapshotAt()

    /** Что лежит в окне до всякой обработки — вход трассы конвейера графика. */
    suspend fun rangeCensus(from: Long, to: Long): RangeCensus =
        sampleDao.rangeCensus(from, to)

    /** Разовое чтение диапазона — для экспорта, которому поток не нужен. */
    suspend fun samplesList(from: Long, to: Long): List<SampleEntity> =
        sampleDao.rangeList(from, to)

    suspend fun downsampledSamples(from: Long, to: Long, bucketMillis: Long): List<DownsampledSample> =
        sampleDao.downsampledRange(from, to, bucketMillis)

    /** Bucketed moments (min/max/Σx/Σx²/n) for the fullscreen dose chart. */
    suspend fun doseBuckets(from: Long, to: Long, bucketMillis: Long): List<DoseBucketAggregate> =
        sampleDao.doseBucketRange(from, to, bucketMillis)

    /** Те же корзины для скорости счёта (полноэкранный график, вкладка «счёт»). */
    suspend fun countRateBuckets(
        from: Long,
        to: Long,
        bucketMillis: Long,
    ): List<ValueBucketAggregate> = sampleDao.countRateBucketRange(from, to, bucketMillis)

    /**
     * И для жёсткости: отношение берётся по каждому отсчёту, отсчёты со счётом
     * ниже порога отбрасываются в самом запросе — делить на них нечего.
     */
    suspend fun hardnessBuckets(
        from: Long,
        to: Long,
        bucketMillis: Long,
        minCountRate: Float,
    ): List<ValueBucketAggregate> =
        sampleDao.hardnessBucketRange(from, to, bucketMillis, minCountRate)

    /** App-detected deviations and hotspots inside a range (chart episodes). */
    suspend fun deviationEvents(from: Long, to: Long, limit: Int = 200): List<EventEntity> =
        eventDao.inRangeBySource(
            from = from,
            to = to,
            // Прежние точечные `deviation` остаются: график рисует их как
            // отметки прошлого, и стирать их с картинки значило бы прятать
            // измерения.
            sources = EventEntity.EPISODE_SOURCES +
                listOf(EventEntity.SOURCE_DEVIATION, EventEntity.SOURCE_HOTSPOT),
            limit = limit,
        )

    /** Located hotspots inside a map viewport, from any recording, newest first. */
    suspend fun hotspotsInBounds(
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        limit: Int = 200,
    ): List<EventEntity> = eventDao.locatedInBounds(
        source = EventEntity.SOURCE_HOTSPOT,
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude,
        limit = limit,
    )

    fun latestRareData(): Flow<RareDataEntity?> = rareDataDao.observeLatest()

    /** Редкие данные прибора за отрезок: температура прибора для графиков. */
    suspend fun rareData(fromMillis: Long, toMillis: Long): List<RareDataEntity> =
        rareDataDao.range(fromMillis, toMillis)

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

    /**
     * Переименовать снимок.
     *
     * Пустое имя стирает своё название, а не записывает пустую строку: снимок
     * тогда снова подписывается временем съёмки, как и все безымянные.
     */
    suspend fun renameSpectrum(id: Long, label: String) =
        spectrumDao.rename(id, label.trim().ifEmpty { null })

    /**
     * Поправить профиль снимка вручную.
     *
     * Имя запоминается рядом со ссылкой — то, каким оно было в момент
     * исправления: профиль потом могут переименовать, а запись о снимке
     * должна остаться восстановимой.
     */
    suspend fun setSpectrumProfile(id: Long, profileId: Long?, profileName: String?) =
        spectrumDao.setProfile(id, profileId, profileName?.trim()?.ifEmpty { null })

    /** Device snapshot metadata (no blobs) for the radon hourly thinning. */
    suspend fun deviceSnapshotMeta(from: Long, to: Long): List<SpectrumMetaRow> =
        spectrumDao.deviceSnapshotMeta(from, to)

    private companion object {
        /**
         * На сколько миллисекунд вперёд ищется свободная метка.
         * **Инженерный параметр**: прибор пишет раз в секунду, поэтому даже
         * при сплошном столкновении свободная миллисекунда находится сразу; а
         * ограничение не даёт циклу разрастись, если база уехала так, что
         * занят целый диапазон.
         */
        const val RESEAT_WINDOW_MILLIS = 250
    }
}
