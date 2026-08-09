package app.radiacode.data

import app.radiacode.data.db.PlaceDao
import app.radiacode.data.db.PlaceEntity
import app.radiacode.data.db.SampleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Places and the manually selected active place. The active place id lives in
 * [AppSettings]; this repository resolves it against the actual place list
 * (deleted/never-set ids fall back to the first place).
 */
class PlaceRepository(
    private val placeDao: PlaceDao,
    private val sampleDao: SampleDao,
    private val settings: AppSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun places(): Flow<List<PlaceEntity>> = placeDao.observeAll()

    /** Resolved active place; null only while no places exist at all. */
    fun activePlace(): Flow<PlaceEntity?> =
        combine(placeDao.observeAll(), settings.activePlaceId) { places, activeId ->
            places.firstOrNull { it.id == activeId } ?: places.firstOrNull()
        }

    /** First launch: make sure «Дом» exists and is active. Idempotent. */
    suspend fun ensureDefaultPlace() {
        if (placeDao.count() > 0L) return
        val id = placeDao.insert(PlaceEntity(name = DEFAULT_PLACE_NAME, createdAt = clock()))
        settings.setActivePlaceId(id)
    }

    suspend fun add(name: String): Long =
        placeDao.insert(PlaceEntity(name = name.trim(), createdAt = clock()))

    suspend fun rename(placeId: Long, name: String) = placeDao.rename(placeId, name.trim())

    /** Deletes the place but keeps its measurements (placeId detaches to null). */
    suspend fun delete(placeId: Long) {
        sampleDao.detachPlace(placeId)
        placeDao.delete(placeId)
    }

    suspend fun setActive(placeId: Long) = settings.setActivePlaceId(placeId)

    companion object {
        const val DEFAULT_PLACE_NAME = "Дом"
    }
}
