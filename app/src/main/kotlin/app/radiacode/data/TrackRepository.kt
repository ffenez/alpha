package app.radiacode.data

import app.radiacode.data.db.TrackAreaSummaryRow
import app.radiacode.data.db.TrackBoundsRow
import app.radiacode.data.db.TrackDao
import app.radiacode.data.db.TrackGridBinRow
import app.radiacode.data.db.TrackPointEntity
import app.radiacode.data.db.TrackSessionEntity
import app.radiacode.device.DoseUnits
import app.radiacode.ui.logic.MapTrackPoint
import app.radiacode.ui.logic.RouteFormat
import app.radiacode.ui.logic.RouteShape
import app.radiacode.ui.logic.RouteSummary
import app.radiacode.ui.logic.TrackMap
import kotlinx.coroutines.flow.Flow

/** Track recording: sessions of GPS points joined with the latest dose rate. */
class TrackRepository(
    private val trackDao: TrackDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun startSession(name: String): Long =
        trackDao.insertSession(TrackSessionEntity(name = name, startedAt = clock()))

    suspend fun endSession(sessionId: Long) {
        trackDao.endSession(sessionId, endedAt = clock())
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
            ),
        )
    }

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
     */
    suspend fun routeSummary(session: TrackSessionEntity): RouteSummary {
        val row = trackDao.routeSummary(session.id)
        val distance = session.distanceMeters ?: computeDistance(session)
        return RouteSummary(
            id = session.id,
            name = session.name,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            distanceMeters = distance,
            measurementCount = row.pointCount,
            avgDoseMicroSvH = row.avgDoseRaw
                ?.let { DoseUnits.rawToMicroSievertPerHour(it.toFloat()) },
            maxDoseMicroSvH = row.maxDoseRaw
                ?.let { DoseUnits.rawToMicroSievertPerHour(it.toFloat()) },
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

    /** Прореженная геометрия маршрута — форма для миниатюры. */
    suspend fun routeShape(sessionId: Long, pointCount: Int): List<Pair<Double, Double>> =
        trackDao.routeShape(sessionId, RouteShape.stride(pointCount))
            .map { it.latitude to it.longitude }

    // --- accumulated map («все записи») ---

    /** Box covering every fix ever recorded; null when nothing was recorded. */
    suspend fun allPointsBounds(): TrackBoundsRow? =
        trackDao.allPointsBounds().takeIf { it.minLatitude != null }

    /**
     * Exact aggregate of one viewport over all recordings — the numbers the
     * summary card shows. Never derived from the drawn subset.
     */
    suspend fun areaSummary(
        useDose: Boolean,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double,
        maxAccuracyMeters: Float,
    ): TrackAreaSummaryRow = trackDao.boundsSummary(
        useDose = useDose,
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude,
        maxAccuracyMeters = maxAccuracyMeters,
    )

    /** Grid × value histogram of a viewport (see `ui/logic/TrackGrid`). */
    @Suppress("LongParameterList")
    suspend fun gridHistogram(
        useDose: Boolean,
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
        useDose = useDose,
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
}
