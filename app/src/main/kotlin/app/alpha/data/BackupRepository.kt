package app.alpha.data

import app.alpha.data.db.AppDatabase
import app.alpha.data.db.BaselineEpochEntity
import app.alpha.data.db.EnvironmentEntity
import app.alpha.data.db.EventEntity
import app.alpha.data.db.ExperimentEntity
import app.alpha.data.db.ExperimentRunEntity
import app.alpha.data.db.MeasurementSessionEntity
import app.alpha.data.db.ProfileEntity
import app.alpha.data.db.ProfileFingerprintEntity
import app.alpha.data.db.ProfileNetworkEntity
import app.alpha.data.db.RareDataEntity
import app.alpha.data.db.SampleEntity
import app.alpha.data.db.SpectrogramSliceEntity
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.data.db.SpectrumTemplateEntity
import app.alpha.data.db.SurveyStationEntity
import app.alpha.data.db.TrackPointEntity
import app.alpha.data.db.TrackSessionEntity
import app.alpha.data.export.backup.BackupBinary
import app.alpha.data.export.backup.BackupCounts
import app.alpha.data.export.backup.BackupEnvironment
import app.alpha.data.export.backup.BackupEpoch
import app.alpha.data.export.backup.BackupEvent
import app.alpha.data.export.backup.BackupExperiment
import app.alpha.data.export.backup.BackupFingerprint
import app.alpha.data.export.backup.BackupKey
import app.alpha.data.export.backup.BackupMeasurement
import app.alpha.data.export.backup.BackupNetwork
import app.alpha.data.export.backup.BackupPage
import app.alpha.data.export.backup.BackupPoint
import app.alpha.data.export.backup.BackupProfile
import app.alpha.data.export.backup.BackupProfiles
import app.alpha.data.export.backup.BackupRare
import app.alpha.data.export.backup.BackupRoute
import app.alpha.data.export.backup.BackupRun
import app.alpha.data.export.backup.BackupSession
import app.alpha.data.export.backup.BackupSink
import app.alpha.data.export.backup.BackupSlice
import app.alpha.data.export.backup.BackupSource
import app.alpha.data.export.backup.BackupSpectrum
import app.alpha.data.export.backup.BackupStation
import app.alpha.data.export.backup.BackupStream
import app.alpha.data.export.backup.BackupTemplate
import app.alpha.data.export.backup.RestoreCount
import app.alpha.data.export.backup.RestoreMode
import app.alpha.data.export.backup.RestoreSelection

/**
 * База ⇄ резервная копия.
 *
 * ## Что здесь есть и чего здесь нет
 *
 * Здесь живёт превращение «строка таблицы ↔ запись копии» и ничего больше:
 * сам формат — в `data/export/backup`, и он не знает ни одной сущности Room.
 * Благодаря этому копия переживает перестройку таблиц, а формат проверяется
 * обычными JVM-тестами без базы и прибора.
 *
 * ## Как связываются записи между собой
 *
 * В копии нет идентификаторов строк: у двух телефонов они свои. Связи
 * восстанавливаются по ЕСТЕСТВЕННЫМ ключам — профиль по имени, маршрут по
 * началу записи и названию, спектр по моменту съёмки. Поэтому при
 * восстановлении сначала создаются профили и маршруты, а потом всё, что на
 * них ссылается.
 *
 * ## Производное не хранится
 *
 * Минутные скаляры и почасовые скетчи (ADR 004) в копию не попадают: они
 * ПЕРЕСЧИТЫВАЮТСЯ из измерений. Класть их в копию значило бы удвоить её ради
 * того, что приложение построит само, — и рискнуть тем, что в копии окажутся
 * скетчи одной версии алгоритма, а в приложении другой.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val settings: AppSettings,
) : BackupSource, BackupSink {

    private val sampleDao = database.sampleDao()
    private val rareDao = database.rareDataDao()
    private val environmentDao = database.environmentDao()
    private val surveyDao = database.surveyDao()
    private val eventDao = database.eventDao()
    private val profileDao = database.profileDao()
    private val sessionDao = database.sessionDao()
    private val trackDao = database.trackDao()
    private val spectrumDao = database.spectrumDao()
    private val templateDao = database.templateDao()
    private val spectrogramDao = database.spectrogramDao()
    private val experimentDao = database.experimentDao()

    // --- источник ---------------------------------------------------------

    /**
     * Момент, с которого берутся записи; null — вся история.
     *
     * Поле, а не параметр каждого потока: `BackupSource` описывает источник, а
     * не запрос, и добавлять «с какого момента» в девять сигнатур значило бы
     * рассказать формату о том, что его не касается. Одновременных копий не
     * бывает — `BackupManager` не начинает вторую, пока идёт первая.
     */
    private var fromMillis: Long? = null

    /** Источник за период. Возвращает себя же: смысл вызова — задать границу. */
    fun scopedTo(fromMillis: Long?): BackupSource = apply { this.fromMillis = fromMillis }

    override suspend fun counts(): BackupCounts {
        val from = fromMillis ?: return BackupCounts(
            measurements = sampleDao.count(),
            events = eventDao.count(),
            rare = rareDao.count(),
            environment = environmentDao.count(),
            stations = surveyDao.count(),
            sessions = sessionDao.count().toLong(),
            routes = trackDao.sessionCount(),
            points = trackDao.totalPointCount(),
            spectra = spectrumDao.count(),
            templates = templateDao.count(),
            slices = spectrogramDao.count().toLong(),
            experiments = experimentDao.count().toLong(),
        )
        return BackupCounts(
            measurements = sampleDao.countSince(from),
            events = eventDao.countSince(from),
            rare = rareDao.countSince(from),
            environment = environmentDao.countSince(from),
            stations = surveyDao.count(),
            sessions = sessionDao.countSince(from),
            routes = trackDao.sessionCountSince(from),
            points = trackDao.pointCountSince(from),
            spectra = spectrumDao.countSince(from),
            // Шаблон — не история, а библиотека прибора: он попадает в копию
            // целиком независимо от выбранного периода.
            templates = templateDao.count(),
            slices = spectrogramDao.countSince(from).toLong(),
            experiments = experimentDao.countSince(from),
        )
    }

    override suspend fun profiles(): BackupProfiles {
        val profiles = profileDao.all()
        val byId = profiles.associateBy { it.id }
        return BackupProfiles(
            profiles = profiles.map { profile ->
                BackupProfile(
                    name = profile.name,
                    icon = profile.icon,
                    parentName = profile.parentId?.let { byId[it]?.name },
                    archived = profile.archived,
                    autoActivate = profile.autoActivate,
                    baselineLearning = profile.baselineLearning,
                    role = profile.role,
                    baselineEpochMillis = profile.baselineEpochMillis,
                    createdAt = profile.createdAt,
                )
            },
            networks = profileDao.allNetworks().mapNotNull { network ->
                byId[network.profileId]?.let {
                    BackupNetwork(it.name, network.networkHash, network.label, network.createdAt)
                }
            },
            epochs = profileDao.allEpochs().mapNotNull { epoch ->
                byId[epoch.profileId]?.let {
                    BackupEpoch(
                        profileName = it.name,
                        startedAtMillis = epoch.startedAtMillis,
                        endedAtMillis = epoch.endedAtMillis,
                        stats = epoch.stats,
                        reason = epoch.reason,
                        createdAt = epoch.createdAt,
                    )
                }
            },
            fingerprints = profileDao.allFingerprints().mapNotNull { print ->
                byId[print.profileId]?.let {
                    BackupFingerprint(
                        profileName = it.name,
                        createdAt = print.createdAt,
                        accumulatedSeconds = print.accumulatedSeconds,
                        sampleCount = print.sampleCount,
                        doseLow = print.doseLowMicroSvH,
                        doseMedian = print.doseMedianMicroSvH,
                        doseHigh = print.doseHighMicroSvH,
                        doseP25 = print.doseP25MicroSvH,
                        doseP75 = print.doseP75MicroSvH,
                        doseMad = print.doseMadMicroSvH,
                        cpsLow = print.cpsLow,
                        cpsMedian = print.cpsMedian,
                        cpsHigh = print.cpsHigh,
                        spectrumSeconds = print.spectrumSeconds,
                        a0 = print.a0,
                        a1 = print.a1,
                        a2 = print.a2,
                        channelCount = print.channelCount,
                        spectrumBase64 = BackupBinary.encode(print.spectrum),
                        origin = print.origin,
                        algorithmVersion = print.algorithmVersion,
                    )
                }
            },
        )
    }

    override suspend fun settings(): List<Pair<String, String>> = settings.exportSettings()

    override fun sessions() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { sessionDao.pageSince(cursor, it, limit) }
            ?: sessionDao.page(cursor, limit)
        val names = profileNames()
        BackupPage(
            items = rows.map {
                BackupSession(
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                    profileName = it.profileId?.let(names::get),
                    deviceSerial = it.deviceSerial,
                )
            },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun measurements() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { sampleDao.pageSince(cursor, it, limit) }
            ?: sampleDao.page(cursor, limit)
        val names = profileNames()
        BackupPage(
            items = rows.map { row ->
                BackupMeasurement(
                    timestamp = row.timestamp,
                    doseRate = row.doseRate,
                    doseRateErr = row.doseRateErr,
                    countRate = row.countRate,
                    countRateErr = row.countRateErr,
                    flags = row.flags,
                    realTimeFlags = row.realTimeFlags,
                    profileName = row.profileId?.let(names::get),
                    baselineExcluded = row.baselineExcluded,
                    deviceSerial = row.deviceSerial,
                )
            },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun events() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { eventDao.pageSince(cursor, it, limit) }
            ?: eventDao.page(cursor, limit)
        BackupPage(
            items = rows.map { row ->
                BackupEvent(
                    timestamp = row.timestamp,
                    source = row.source,
                    code = row.code,
                    name = row.name,
                    param1 = row.param1,
                    flags = row.flags,
                    doseRate = row.doseRate,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    deviceSerial = row.deviceSerial,
                )
            },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun stations() = BackupStream { cursor, limit ->
        // Станций на съёмку — десятки, а не миллионы: страница берётся целиком
        // и отдаётся одним куском, курсор нужен лишь чтобы не отдать её дважды.
        val rows = if (cursor > 0L) emptyList() else surveyDao.all()
        BackupPage(
            items = rows.mapNotNull { station ->
                val snapshot = spectrumDao.byId(station.spectrumId) ?: return@mapNotNull null
                BackupStation(
                    timestamp = station.timestamp,
                    spectrumTimestamp = snapshot.timestamp,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    accuracyMeters = station.accuracyMeters,
                    heightCm = station.heightCm,
                    pressureHpa = station.pressureHpa,
                    note = station.note,
                )
            },
            nextCursor = if (rows.isEmpty()) null else 1L,
        )
    }

    override fun environment() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { environmentDao.pageSince(cursor, it, limit) }
            ?: environmentDao.page(cursor, limit)
        BackupPage(
            items = rows.map {
                BackupEnvironment(
                    timestamp = it.timestamp,
                    pressureHpa = it.pressureHpa,
                    magneticUt = it.magneticUt,
                    magneticSd = it.magneticSd,
                    phoneTempC = it.phoneTempC,
                    samples = it.samples,
                )
            },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun rare() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { rareDao.pageSince(cursor, it, limit) }
            ?: rareDao.page(cursor, limit)
        BackupPage(
            items = rows.map {
                BackupRare(
                    timestamp = it.timestamp,
                    dose = it.dose,
                    temperature = it.temperature,
                    batteryPercent = it.batteryPercent,
                    durationSeconds = it.durationSeconds,
                    flags = it.flags,
                    deviceSerial = it.deviceSerial,
                )
            },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun routes() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { trackDao.sessionPageSince(cursor, it, limit) }
            ?: trackDao.sessionPage(cursor, limit)
        BackupPage(
            items = rows.map {
                BackupRoute(
                    name = it.name,
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                    distanceMeters = it.distanceMeters,
                    interrupted = it.interrupted,
                    deviceSerial = it.deviceSerial,
                )
            },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun points() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { trackDao.pointPageSince(cursor, it, limit) }
            ?: trackDao.pointPage(cursor, limit)
        val routes = routeKeys()
        BackupPage(
            items = rows.mapNotNull { row ->
                routes[row.sessionId]?.let { key ->
                    BackupPoint(
                        routeKey = key,
                        timestamp = row.timestamp,
                        latitude = row.latitude,
                        longitude = row.longitude,
                        accuracyMeters = row.accuracyMeters,
                        doseRate = row.doseRate,
                        countRate = row.countRate,
                        altitudeMeters = row.altitudeMeters,
                    )
                }
            },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun spectra() = BackupStream { cursor, limit ->
        // Спектры тяжёлые: страница мельче, чем у измерений.
        val page = minOf(limit, SPECTRA_PAGE)
        val rows = fromMillis?.let { spectrumDao.pageSince(cursor, it, page) }
            ?: spectrumDao.page(cursor, page)
        BackupPage(
            items = rows.map { it.toBackup() },
            nextCursor = rows.lastOrNull()?.id,
        )
    }

    override fun templates() = BackupStream { cursor, limit ->
        // Шаблонов единицы — страница берётся целиком, курсор нужен лишь чтобы
        // не отдать её дважды.
        val rows = if (cursor > 0L) emptyList() else templateDao.all()
        BackupPage(
            items = rows.map { row ->
                BackupTemplate(
                    name = row.name,
                    createdAt = row.createdAt,
                    deviceSerial = row.deviceSerial,
                    deviceName = row.deviceName,
                    a0 = row.a0,
                    a1 = row.a1,
                    a2 = row.a2,
                    durationSeconds = row.durationSeconds,
                    resolution662 = row.resolution662,
                    channelCount = row.channelCount,
                    countsBase64 = BackupBinary.encode(row.counts),
                    source = row.source,
                    note = row.note,
                )
            },
            nextCursor = if (rows.isEmpty()) null else 1L,
        )
    }

    override fun slices() = BackupStream { cursor, limit ->
        val page = minOf(limit, SPECTRA_PAGE)
        val rows = fromMillis?.let { spectrogramDao.pageSince(cursor, it, page) }
            ?: spectrogramDao.page(cursor, page)
        BackupPage(
            items = rows.map {
                BackupSlice(
                    startMillis = it.startMillis,
                    endMillis = it.endMillis,
                    durationMillis = it.durationMillis,
                    schemeId = it.schemeId,
                    bandCount = it.bandCount,
                    countsBase64 = BackupBinary.encode(it.counts),
                    cps = it.cps,
                    doseMicroSvH = it.doseMicroSvH,
                    sliceCount = it.sliceCount,
                )
            },
            nextCursor = rows.lastOrNull()?.startMillis,
        )
    }

    override fun experiments() = BackupStream { cursor, limit ->
        val rows = fromMillis?.let { experimentDao.pageSince(cursor, it, limit) }
            ?: experimentDao.page(cursor, limit)
        val names = profileNames()
        val items = rows.map { experiment ->
            val runs = experimentDao.runs(experiment.id).map { run ->
                BackupRun(
                    label = run.label,
                    startedAt = run.startedAt,
                    endedAt = run.endedAt,
                    spectrumKey = run.spectrumId?.let { spectrumDao.byId(it)?.toBackup()?.key },
                    doseStats = run.doseStats,
                    distanceCm = run.distanceCm,
                    shieldingNote = run.shieldingNote,
                )
            }
            BackupExperiment(
                kind = experiment.kind,
                profileName = experiment.profileId?.let(names::get),
                createdAt = experiment.createdAt,
                note = experiment.note,
                geometry = experiment.geometry,
                distanceCm = experiment.distanceCm,
                placement = experiment.placement,
                orientation = experiment.orientation,
                plannedSeconds = experiment.plannedSeconds,
                algorithmVersion = experiment.algorithmVersion,
                params = experiment.params,
                runs = runs,
            )
        }
        BackupPage(items = items, nextCursor = rows.lastOrNull()?.id)
    }

    // --- приёмник ---------------------------------------------------------

    private var restoreMode: RestoreMode = RestoreMode.MERGE
    private var profileIdsByName: MutableMap<String, Long> = mutableMapOf()
    private var routeIdsByKey: MutableMap<String, Long> = mutableMapOf()
    private var spectrumIdsByKey: MutableMap<String, Long> = mutableMapOf()

    override suspend fun begin(mode: RestoreMode, selection: RestoreSelection) {
        restoreMode = mode
        profileIdsByName = mutableMapOf()
        routeIdsByKey = mutableMapOf()
        spectrumIdsByKey = mutableMapOf()
        if (mode == RestoreMode.REPLACE) {
            // Копия к этому моменту УЖЕ проверена целиком (манифест, версия,
            // контрольные суммы) — иначе сюда не попадают. Удаление идёт
            // только после проверки: спецификация §45.
            if (selection.measurements) {
                sampleDao.clear()
                eventDao.clear()
                rareDao.clear()
                environmentDao.clear()
                surveyDao.clear()
                sessionDao.clear()
            }
            if (selection.routes) trackDao.clearSessions()
            if (selection.spectra) {
                spectrumDao.clear()
                templateDao.clear()
                spectrogramDao.clear()
            }
            if (selection.experiments) experimentDao.clear()
            if (selection.profiles) {
                profileDao.clearEpochs()
                profileDao.clearFingerprints()
                profileDao.clearProfiles()
            }
        }
        // Имена уже существующих профилей нужны в обоих режимах: к ним
        // привязываются измерения и маршруты.
        for (profile in profileDao.all()) profileIdsByName[profile.name] = profile.id
    }

    override suspend fun settings(entries: List<Pair<String, String>>) {
        settings.importSettings(entries)
    }

    override suspend fun profiles(bundle: BackupProfiles): RestoreCount {
        var added = 0L
        var skipped = 0L
        // Сначала сами профили: сети, эпохи и отпечатки ссылаются на них.
        for (profile in bundle.profiles) {
            val existing = profileIdsByName[profile.name]
            if (existing != null) {
                skipped++
                continue
            }
            val id = profileDao.insert(
                ProfileEntity(
                    name = profile.name,
                    icon = profile.icon,
                    parentId = null,
                    archived = profile.archived,
                    autoActivate = profile.autoActivate,
                    baselineLearning = profile.baselineLearning,
                    role = profile.role,
                    baselineEpochMillis = profile.baselineEpochMillis,
                    createdAt = profile.createdAt,
                ),
            )
            profileIdsByName[profile.name] = id
            added++
        }
        // Вложенность — вторым проходом: родитель мог быть создан только что.
        for (profile in bundle.profiles) {
            val parent = profile.parentName?.let { profileIdsByName[it] } ?: continue
            val id = profileIdsByName[profile.name] ?: continue
            profileDao.byId(id)?.let { profileDao.update(it.copy(parentId = parent)) }
        }
        for (network in bundle.networks) {
            val profileId = profileIdsByName[network.profileName] ?: continue
            if (profileDao.networkByHash(network.networkHash) != null) continue
            profileDao.insertNetwork(
                ProfileNetworkEntity(
                    profileId = profileId,
                    networkHash = network.networkHash,
                    label = network.label,
                    createdAt = network.createdAt,
                ),
            )
        }
        for (epoch in bundle.epochs) {
            val profileId = profileIdsByName[epoch.profileName] ?: continue
            val known = profileDao.epochs(profileId)
                .any { it.startedAtMillis == epoch.startedAtMillis }
            if (known) continue
            profileDao.insertEpoch(
                BaselineEpochEntity(
                    profileId = profileId,
                    startedAtMillis = epoch.startedAtMillis,
                    endedAtMillis = epoch.endedAtMillis,
                    stats = epoch.stats,
                    reason = epoch.reason,
                    createdAt = epoch.createdAt,
                ),
            )
        }
        for (print in bundle.fingerprints) {
            val profileId = profileIdsByName[print.profileName] ?: continue
            val newest = profileDao.newestFingerprint(profileId)
            if (newest != null && newest.createdAt == print.createdAt) continue
            profileDao.insertFingerprint(
                ProfileFingerprintEntity(
                    profileId = profileId,
                    createdAt = print.createdAt,
                    accumulatedSeconds = print.accumulatedSeconds,
                    sampleCount = print.sampleCount,
                    doseLowMicroSvH = print.doseLow,
                    doseMedianMicroSvH = print.doseMedian,
                    doseHighMicroSvH = print.doseHigh,
                    doseP25MicroSvH = print.doseP25,
                    doseP75MicroSvH = print.doseP75,
                    doseMadMicroSvH = print.doseMad,
                    cpsLow = print.cpsLow,
                    cpsMedian = print.cpsMedian,
                    cpsHigh = print.cpsHigh,
                    spectrumSeconds = print.spectrumSeconds,
                    a0 = print.a0,
                    a1 = print.a1,
                    a2 = print.a2,
                    channelCount = print.channelCount,
                    spectrum = BackupBinary.decode(print.spectrumBase64),
                    origin = print.origin,
                    algorithmVersion = print.algorithmVersion,
                ),
            )
        }
        return RestoreCount(added, skipped)
    }

    override suspend fun sessions(batch: List<BackupSession>): RestoreCount {
        val existing = sessionDao.existingStarts(batch.map { it.startedAt }).toSet()
        var added = 0L
        for (session in batch) {
            if (session.startedAt in existing) continue
            sessionDao.insert(
                MeasurementSessionEntity(
                    profileId = session.profileName?.let { profileIdsByName[it] },
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    deviceSerial = session.deviceSerial,
                ),
            )
            added++
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun measurements(batch: List<BackupMeasurement>): RestoreCount {
        // Уникальный индекс по времени сам отбрасывает уже записанное, а
        // возвращённые rowid говорят, что именно было отброшено.
        val rows = batch.map { item ->
            SampleEntity(
                timestamp = item.timestamp,
                doseRate = item.doseRate,
                doseRateErr = item.doseRateErr,
                countRate = item.countRate,
                countRateErr = item.countRateErr,
                flags = item.flags,
                realTimeFlags = item.realTimeFlags,
                profileId = item.profileName?.let { profileIdsByName[it] },
                baselineExcluded = item.baselineExcluded,
                deviceSerial = item.deviceSerial,
            )
        }
        val ids = sampleDao.insertAll(rows)
        val added = ids.count { it != -1L }.toLong()
        return RestoreCount(added, rows.size - added)
    }

    override suspend fun events(batch: List<BackupEvent>): RestoreCount {
        var added = 0L
        for ((source, group) in batch.groupBy { it.source }) {
            val existing = eventDao
                .existingTimestamps(group.map { it.timestamp }, source)
                .toSet()
            val fresh = group.filter { it.timestamp !in existing }
            if (fresh.isEmpty()) continue
            eventDao.insertAll(
                fresh.map {
                    EventEntity(
                        timestamp = it.timestamp,
                        source = it.source,
                        code = it.code,
                        name = it.name,
                        param1 = it.param1,
                        flags = it.flags,
                        doseRate = it.doseRate,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        deviceSerial = it.deviceSerial,
                    )
                },
            )
            added += fresh.size
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun stations(batch: List<BackupStation>): RestoreCount {
        var added = 0L
        for (station in batch) {
            // Снимок ищется по метке времени: идентификаторы после
            // восстановления другие, метка та же. Нет снимка — нет станции.
            val spectrumId = spectrumDao.idByTimestamp(station.spectrumTimestamp) ?: continue
            val id = surveyDao.insert(
                SurveyStationEntity(
                    spectrumId = spectrumId,
                    timestamp = station.timestamp,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    accuracyMeters = station.accuracyMeters,
                    heightCm = station.heightCm,
                    pressureHpa = station.pressureHpa,
                    note = station.note,
                ),
            )
            if (id != -1L) added++
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun environment(batch: List<BackupEnvironment>): RestoreCount {
        val ids = environmentDao.insertAll(
            batch.map {
                EnvironmentEntity(
                    timestamp = it.timestamp,
                    pressureHpa = it.pressureHpa,
                    magneticUt = it.magneticUt,
                    magneticSd = it.magneticSd,
                    phoneTempC = it.phoneTempC,
                    samples = it.samples,
                )
            },
        )
        val added = ids.count { it != -1L }.toLong()
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun rare(batch: List<BackupRare>): RestoreCount {
        val ids = rareDao.insertAll(
            batch.map {
                RareDataEntity(
                    timestamp = it.timestamp,
                    dose = it.dose,
                    temperature = it.temperature,
                    batteryPercent = it.batteryPercent,
                    durationSeconds = it.durationSeconds,
                    flags = it.flags,
                    deviceSerial = it.deviceSerial,
                )
            },
        )
        val added = ids.count { it != -1L }.toLong()
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun routes(batch: List<BackupRoute>): RestoreCount {
        var added = 0L
        for (route in batch) {
            val existing = trackDao.sessionByKey(route.startedAt, route.name)
            if (existing != null) {
                routeIdsByKey[route.key] = existing
                continue
            }
            val id = trackDao.insertSession(
                TrackSessionEntity(
                    name = route.name,
                    startedAt = route.startedAt,
                    endedAt = route.endedAt,
                    distanceMeters = route.distanceMeters,
                    interrupted = route.interrupted,
                    deviceSerial = route.deviceSerial,
                ),
            )
            routeIdsByKey[route.key] = id
            added++
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun points(batch: List<BackupPoint>): RestoreCount {
        var added = 0L
        for ((routeKey, group) in batch.groupBy { it.routeKey }) {
            val sessionId = routeIdsByKey[routeKey] ?: continue
            val existing = trackDao
                .existingPointTimes(sessionId, group.map { it.timestamp })
                .toSet()
            val fresh = group.filter { it.timestamp !in existing }
            if (fresh.isEmpty()) continue
            trackDao.insertPoints(
                fresh.map {
                    TrackPointEntity(
                        sessionId = sessionId,
                        timestamp = it.timestamp,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracyMeters = it.accuracyMeters,
                        doseRate = it.doseRate,
                        countRate = it.countRate,
                        altitudeMeters = it.altitudeMeters,
                    )
                },
            )
            added += fresh.size
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun spectra(batch: List<BackupSpectrum>): RestoreCount {
        val existing = spectrumDao.existingTimestamps(batch.map { it.timestamp }).toSet()
        var added = 0L
        for (spectrum in batch) {
            if (spectrum.timestamp in existing) continue
            val id = spectrumDao.insert(
                SpectrumSnapshotEntity(
                    timestamp = spectrum.timestamp,
                    accumulated = spectrum.accumulated,
                    isBackgroundReference = spectrum.isBackgroundReference,
                    origin = spectrum.origin,
                    label = spectrum.label,
                    analysisMeta = spectrum.analysisMeta,
                    durationSeconds = spectrum.durationSeconds,
                    a0 = spectrum.a0,
                    a1 = spectrum.a1,
                    a2 = spectrum.a2,
                    channelCount = spectrum.channelCount,
                    counts = BackupBinary.decode(spectrum.countsBase64),
                    deviceSerial = spectrum.deviceSerial,
                    firmware = spectrum.firmware,
                    epochId = spectrum.epochId,
                    trigger = spectrum.trigger,
                ),
            )
            spectrumIdsByKey[spectrum.key] = id
            added++
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun templates(batch: List<BackupTemplate>): RestoreCount {
        // У таблицы нет уникального индекса, а вставка идёт с REPLACE по
        // идентификатору: без проверки повторный импорт удвоил бы библиотеку.
        // Ключ тот же, что в копии, — имя и момент записи.
        val existing = templateDao.all().map { BackupKey.of(it.name, it.createdAt) }.toMutableSet()
        var added = 0L
        for (template in batch) {
            if (!existing.add(template.key)) continue
            templateDao.insert(
                SpectrumTemplateEntity(
                    name = template.name,
                    createdAt = template.createdAt,
                    deviceSerial = template.deviceSerial,
                    deviceName = template.deviceName,
                    a0 = template.a0,
                    a1 = template.a1,
                    a2 = template.a2,
                    durationSeconds = template.durationSeconds,
                    resolution662 = template.resolution662,
                    channelCount = template.channelCount,
                    counts = BackupBinary.decode(template.countsBase64),
                    source = template.source,
                    note = template.note,
                ),
            )
            added++
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun slices(batch: List<BackupSlice>): RestoreCount {
        val existing = spectrogramDao.existingStarts(batch.map { it.startMillis }).toSet()
        val fresh = batch.filter { it.startMillis !in existing }
        if (fresh.isNotEmpty()) {
            spectrogramDao.upsert(
                fresh.map {
                    SpectrogramSliceEntity(
                        startMillis = it.startMillis,
                        endMillis = it.endMillis,
                        durationMillis = it.durationMillis,
                        schemeId = it.schemeId,
                        bandCount = it.bandCount,
                        counts = BackupBinary.decode(it.countsBase64),
                        cps = it.cps,
                        doseMicroSvH = it.doseMicroSvH,
                        sliceCount = it.sliceCount,
                    )
                },
            )
        }
        return RestoreCount(fresh.size.toLong(), (batch.size - fresh.size).toLong())
    }

    override suspend fun experiments(batch: List<BackupExperiment>): RestoreCount {
        var added = 0L
        for (experiment in batch) {
            if (experimentDao.byKey(experiment.createdAt, experiment.kind) != null) continue
            val id = experimentDao.insert(
                ExperimentEntity(
                    kind = experiment.kind,
                    profileId = experiment.profileName?.let { profileIdsByName[it] },
                    createdAt = experiment.createdAt,
                    note = experiment.note,
                    geometry = experiment.geometry,
                    distanceCm = experiment.distanceCm,
                    placement = experiment.placement,
                    orientation = experiment.orientation,
                    plannedSeconds = experiment.plannedSeconds,
                    algorithmVersion = experiment.algorithmVersion,
                    params = experiment.params,
                    photoUri = null,
                ),
            )
            for (run in experiment.runs) {
                experimentDao.insertRun(
                    ExperimentRunEntity(
                        experimentId = id,
                        label = run.label,
                        startedAt = run.startedAt,
                        endedAt = run.endedAt,
                        spectrumId = run.spectrumKey?.let { spectrumIdsByKey[it] },
                        doseStats = run.doseStats,
                        distanceCm = run.distanceCm,
                        shieldingNote = run.shieldingNote,
                    ),
                )
            }
            added++
        }
        return RestoreCount(added, batch.size - added)
    }

    override suspend fun finish() {
        // Производные ряды (ADR 004) в копии не лежат — они пересобираются из
        // восстановленных измерений тем же путём, что и всегда.
    }

    // --- вспомогательное --------------------------------------------------

    private suspend fun profileNames(): Map<Long, String> =
        profileDao.all().associate { it.id to it.name }

    private suspend fun routeKeys(): Map<Long, String> =
        trackDao.sessionsOnce().associate { session ->
            session.id to BackupRoute(
                name = session.name,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                distanceMeters = session.distanceMeters,
                interrupted = session.interrupted,
            ).key
        }

    private fun SpectrumSnapshotEntity.toBackup() = BackupSpectrum(
        timestamp = timestamp,
        accumulated = accumulated,
        isBackgroundReference = isBackgroundReference,
        origin = origin,
        label = label,
        analysisMeta = analysisMeta,
        durationSeconds = durationSeconds,
        a0 = a0,
        a1 = a1,
        a2 = a2,
        channelCount = channelCount,
        countsBase64 = BackupBinary.encode(counts),
        deviceSerial = deviceSerial,
        firmware = firmware,
        epochId = epochId,
        trigger = trigger,
    )

    private companion object {

        /**
         * Спектров и срезов за одно чтение.
         * **Инженерный параметр**: полсотни — у каждой строки внутри тысячи
         * каналов, и страница в две тысячи была бы десятками мегабайт.
         */
        const val SPECTRA_PAGE = 50
    }
}
