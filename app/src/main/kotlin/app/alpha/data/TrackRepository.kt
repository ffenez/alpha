package app.alpha.data

import app.alpha.data.db.SampleDao
import app.alpha.data.db.TrackAreaSummaryRow
import app.alpha.data.db.TrackBoundsRow
import app.alpha.data.db.TrackDao
import app.alpha.data.db.TrackGridBinRow
import app.alpha.data.db.TrackPointEntity
import app.alpha.data.db.TrackSessionEntity
import app.alpha.device.DoseUnits
import app.alpha.ui.logic.MapTrackPoint
import app.alpha.ui.logic.RouteFormat
import app.alpha.ui.logic.RouteShape
import app.alpha.ui.logic.RouteShapePoint
import app.alpha.ui.logic.RouteSummary
import app.alpha.ui.logic.ChartMapping
import app.alpha.ui.logic.TrackMap
import kotlinx.coroutines.flow.Flow

/** Track recording: sessions of GPS points joined with the latest dose rate. */
class TrackRepository(
    private val trackDao: TrackDao,
    /**
     * Измерения того же времени: доза маршрута считается по ним, а не по
     * средней мощности за календарное время (см. [routeSummary]).
     */
    private val sampleDao: SampleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** @param deviceSerial чей прибор пишет маршрут; null — неизвестен. */
    suspend fun startSession(name: String, deviceSerial: String? = null): Long =
        trackDao.insertSession(
            TrackSessionEntity(name = name, startedAt = clock(), deviceSerial = deviceSerial),
        )

    suspend fun endSession(sessionId: Long) {
        trackDao.endSession(sessionId, endedAt = clock())
    }

    /**
     * Остановка записи: маршрут закрывается, а маршрут без единой точки
     * исчезает.
     *
     * Пустая строка в журнале не безобидна: она выглядит как прогулка, у
     * которой почему-то ничего не намерено, и человек ищет причину там, где
     * события просто не было.
     */
    suspend fun finishSession(sessionId: Long) {
        if (trackDao.pointCount(sessionId) == 0) {
            trackDao.deleteSession(sessionId)
            return
        }
        trackDao.endSession(sessionId, endedAt = clock())
    }

    /** Черновик, который так и не получил точек, удаляется без следа. */
    suspend fun discardIfEmpty(sessionId: Long) {
        if (trackDao.pointCount(sessionId) == 0) trackDao.deleteSession(sessionId)
    }

    /**
     * Что делать с записями, которые никто не останавливал.
     *
     * Приложение убили, телефон выключился, служба упала — маршрут остался
     * открытым, и в журнале он вечно «идёт запись». Пустые такие записи
     * удаляются, у остальных концом становится последняя записанная точка, а
     * сам маршрут называется прерванным: время после последней точки не
     * измерено, и выдавать его за прогулку нельзя.
     *
     * @return сколько записей пришлось закрыть.
     */
    suspend fun recoverUnfinished(exceptId: Long? = null): Int {
        var recovered = 0
        for (session in trackDao.unfinishedSessions()) {
            // Возобновлённая запись прерванной не считается: она идёт.
            if (session.id == exceptId) continue
            val lastPoint = trackDao.lastPointTime(session.id)
            if (lastPoint == null) {
                trackDao.deleteSession(session.id)
                continue
            }
            trackDao.markInterrupted(session.id, endedAt = lastPoint)
            recovered++
        }
        return recovered
    }

    suspend fun delete(sessionId: Long) {
        trackDao.deleteSession(sessionId)
    }

    suspend fun addPoint(
        sessionId: Long,
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        doseRate: Float?,
        countRate: Float?,
        altitudeMeters: Double? = null,
        magneticUt: Float? = null,
    ) {
        trackDao.insertPoint(
            TrackPointEntity(
                sessionId = sessionId,
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                doseRate = doseRate,
                countRate = countRate,
                altitudeMeters = altitudeMeters,
                magneticUt = magneticUt,
            ),
        )
    }

    /**
     * Где был прибор в этот момент; null — маршрут тогда не писался.
     *
     * Допуск нужен: точки приходят по фиксам координат, а не по расписанию, и
     * соседний фикс в минуте от среза описывает то же место. Дальше — уже не
     * «здесь», и лучше не отвечать вовсе.
     */
    suspend fun pointNear(
        atMillis: Long,
        toleranceMillis: Long = POSITION_TOLERANCE_MILLIS,
    ): TrackPointEntity? = trackDao.pointNear(
        atMillis = atMillis,
        from = atMillis - toleranceMillis,
        to = atMillis + toleranceMillis,
    )

    fun sessions(): Flow<List<TrackSessionEntity>> = trackDao.observeSessions()

    fun points(sessionId: Long): Flow<List<TrackPointEntity>> = trackDao.observePoints(sessionId)

    suspend fun session(sessionId: Long): TrackSessionEntity? = trackDao.session(sessionId)

    /** Newest track session; null = nothing was ever recorded. */
    suspend fun latestSession(): TrackSessionEntity? = trackDao.latestSession()

    /** Track sessions overlapping a measurement-session time range. */
    suspend fun sessionsOverlapping(from: Long, to: Long): List<TrackSessionEntity> =
        trackDao.sessionsOverlapping(from, to)

    // --- маршруты как записи журнала ---

    suspend fun rename(sessionId: Long, name: String) {
        trackDao.renameSession(sessionId, RouteFormat.cleanName(name))
    }

    /**
     * Сводка маршрута для списка Истории.
     *
     * Расстояние берётся из строки маршрута, а если его там нет — считается
     * один раз по полному следу и записывается. Так список остаётся дешёвым
     * (четыре числа одним запросом), а маршруты, записанные прежней версией,
     * не остаются без расстояния навсегда.
     *
     * Доза — ИНТЕГРАЛ ПО ИЗМЕРЕНИЯМ того же промежутка, той же формулой, что у
     * сессии ([ChartMapping.integrateDoseMicroSv]): каждое показание отвечает
     * за свою секунду, а время без показаний не даёт дозы вовсе. Прежняя
     * оценка «средняя мощность × длительность» приписывала прогулке дозу за
     * те минуты, когда прибор молчал, и уезжала в отчёт как измеренная.
     */
    suspend fun routeSummary(session: TrackSessionEntity): RouteSummary {
        val row = trackDao.routeSummary(session.id)
        val distance = session.distanceMeters ?: computeDistance(session)
        val doseMicroSv = session.endedAt?.let { endedAt ->
            ChartMapping.integrateDoseMicroSv(
                sampleDao.downsampledRange(session.startedAt, endedAt, DOSE_BUCKET_MILLIS),
            )
        }
        return RouteSummary(
            id = session.id,
            name = session.name,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            interrupted = session.interrupted,
            deviceSerial = session.deviceSerial,
            distanceMeters = distance,
            measurementCount = row.pointCount,
            avgDoseMicroSvH = row.avgDoseRaw
                ?.let { DoseUnits.rawToMicroSievertPerHour(it.toFloat()) },
            maxDoseMicroSvH = row.maxDoseRaw
                ?.let { DoseUnits.rawToMicroSievertPerHour(it.toFloat()) },
            doseMicroSv = doseMicroSv,
        )
    }

    /**
     * Расстояние законченного маршрута — считается по полному следу и
     * сохраняется. У идущей записи не считается вовсе: она ещё меняется, а
     * число, посчитанное «пока что», через минуту было бы неправдой.
     */
    private suspend fun computeDistance(session: TrackSessionEntity): Double? {
        if (session.endedAt == null) return null
        val points = trackDao.pointsOnce(session.id).map {
            MapTrackPoint(
                timestamp = it.timestamp,
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = it.accuracyMeters,
                doseMicroSvH = null,
                cps = null,
            )
        }
        if (points.isEmpty()) return null
        val meters = TrackMap.distanceMeters(points)
        trackDao.setDistance(session.id, meters)
        return meters
    }

    /** Прореженная геометрия маршрута с измерением — форма для миниатюры. */
    suspend fun routeShape(sessionId: Long, pointCount: Int): List<RouteShapePoint> =
        trackDao.routeShape(sessionId, RouteShape.stride(pointCount)).map {
            RouteShapePoint(
                latitude = it.latitude,
                longitude = it.longitude,
                doseMicroSvH = it.doseRate?.let(DoseUnits::rawToMicroSievertPerHour),
            )
        }

    // --- accumulated map («все записи») ---

    /** Box covering every fix ever recorded; null when nothing was recorded. */
    suspend fun allPointsBounds(): TrackBoundsRow? =
        trackDao.allPointsBounds().takeIf { it.minLatitude != null }

    /**
     * Exact aggregate of one viewport over all recordings — the numbers the
     * summary card shows. Never derived from the drawn subset.
     */
    suspend fun areaSummary(
        metric: Int,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        maxAccuracyMeters: Float,
    ): TrackAreaSummaryRow = trackDao.boundsSummary(
        metric = metric,
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude,
        maxAccuracyMeters = maxAccuracyMeters,
    )

    /** Grid × value histogram of a viewport (see `ui/logic/TrackGrid`). */
    @Suppress("LongParameterList")
    suspend fun gridHistogram(
        metric: Int,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        maxAccuracyMeters: Float,
        latStepDeg: Double,
        lonStepDeg: Double,
        valueMin: Float,
        valueStep: Float,
        limit: Int,
    ): List<TrackGridBinRow> = trackDao.gridHistogram(
        metric = metric,
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude,
        maxAccuracyMeters = maxAccuracyMeters,
        latStepDeg = latStepDeg,
        lonStepDeg = lonStepDeg,
        valueMin = valueMin,
        valueStep = valueStep,
        limit = limit,
    )
    companion object {

        /**
         * Допуск сшивки по времени: минута. Фиксы приходят раз в несколько
         * секунд, и минута покрывает пропуск сигнала, не превращая соседнюю
         * улицу в «то же место».
         */
        const val POSITION_TOLERANCE_MILLIS = 60_000L


        /**
         * Корзина интегрирования дозы, мс. Те же десять минут, что у сессии
         * ([SessionRepository]): доза обеих записей обязана считаться одним
         * способом, иначе один и тот же промежуток давал бы два числа.
         */
        private const val DOSE_BUCKET_MILLIS = 10L * 60_000L
    }

}
