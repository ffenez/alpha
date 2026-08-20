package app.alpha.data

import app.alpha.data.db.EnvironmentDao
import app.alpha.data.db.EnvironmentEntity
import app.alpha.sensors.EnvironmentWindow
import kotlinx.coroutines.flow.Flow

/**
 * Ряд условий вокруг измерения: давление, магнитное поле, температура
 * телефона. Пишется сводками окон (см. [EnvironmentWindow]), читается
 * отрезками времени — так же, как остальные ряды приложения.
 */
class EnvironmentRepository(private val dao: EnvironmentDao) {

    suspend fun save(window: EnvironmentWindow) {
        dao.insertAll(listOf(window.toEntity()))
    }

    fun latest(): Flow<EnvironmentEntity?> = dao.observeLatest()

    suspend fun range(fromMillis: Long, toMillis: Long): List<EnvironmentEntity> =
        dao.range(fromMillis, toMillis)

    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<EnvironmentEntity>> =
        dao.observeRange(fromMillis, toMillis)

    /** Уборка хранения: ряд живёт по тому же сроку, что и измерения. */
    suspend fun deleteBefore(millis: Long): Int = dao.deleteBefore(millis)
}

fun EnvironmentWindow.toEntity() = EnvironmentEntity(
    timestamp = endMillis,
    pressureHpa = pressureHpa,
    magneticUt = magneticUt,
    magneticSd = magneticSd,
    phoneTempC = phoneTempC,
    samples = samples,
)
