package app.alpha.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Постоянная история спектрограммы (ADR 007).
 *
 * Отдельно от [SpectrumDao] намеренно: снимок спектра и срез спектрограммы —
 * разные виды данных, и общий DAO рано или поздно привёл бы к попытке искать
 * пики по срезам.
 */
@Dao
abstract class SpectrogramDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(slices: List<SpectrogramSliceEntity>)

    /** Страница срезов для резервной копии: ключ — момент начала среза. */
    @Query(
        "SELECT * FROM spectrogram_slices WHERE startMillis > :afterStart " +
            "ORDER BY startMillis LIMIT :limit",
    )
    abstract suspend fun page(afterStart: Long, limit: Int): List<SpectrogramSliceEntity>

    /** Та же страница, но не старее указанного момента — копия за период. */
    @Query(
        "SELECT * FROM spectrogram_slices WHERE startMillis > :afterStart " +
            "AND startMillis >= :from ORDER BY startMillis LIMIT :limit",
    )
    abstract suspend fun pageSince(
        afterStart: Long,
        from: Long,
        limit: Int,
    ): List<SpectrogramSliceEntity>

    @Query("SELECT COUNT(*) FROM spectrogram_slices WHERE startMillis >= :from")
    abstract suspend fun countSince(from: Long): Int

    /** Какие срезы уже есть: начало среза — первичный ключ таблицы. */
    @Query("SELECT startMillis FROM spectrogram_slices WHERE startMillis IN (:starts)")
    abstract suspend fun existingStarts(starts: List<Long>): List<Long>

    @Query("DELETE FROM spectrogram_slices")
    abstract suspend fun clear()

    /**
     * Срезы, ПЕРЕСЕКАЮЩИЕ окно, новейшие первыми и не больше [limit] строк:
     * окно рисуется от «сейчас» назад, поэтому обрезать надо старый край.
     * Срез, начавшийся до окна, но закончившийся внутри, — часть картинки.
     */
    @Query(SpectrogramSql.WINDOW)
    abstract suspend fun window(from: Long, to: Long, limit: Int): List<SpectrogramSliceEntity>

    /** Срезы диапазона по возрастанию — вход прореживания. */
    @Query(SpectrogramSql.RANGE)
    abstract suspend fun range(from: Long, to: Long, limit: Int): List<SpectrogramSliceEntity>

    @Query("DELETE FROM spectrogram_slices WHERE startMillis >= :from AND startMillis < :to")
    abstract suspend fun deleteRange(from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM spectrogram_slices")
    abstract suspend fun count(): Int

    @Query("SELECT MIN(startMillis) FROM spectrogram_slices")
    abstract suspend fun earliestStart(): Long?

    /**
     * Начало первого среза, который ещё КОРОЧЕ целевой длины прореживания.
     * По нему курсор перепрыгивает пустые и уже прорежённые участки: иначе
     * проход шёл бы по каждому часу истории подряд, включая годы, в которых
     * делать нечего.
     */
    @Query(
        """
        SELECT MIN(startMillis) FROM spectrogram_slices
        WHERE startMillis >= :from AND startMillis < :to
              AND durationMillis < :maxDurationMillis
        """,
    )
    abstract suspend fun nextShortSliceStart(
        from: Long,
        to: Long,
        maxDurationMillis: Long,
    ): Long?

    /**
     * Замена диапазона прорежённым набором — ОДНОЙ транзакцией. Без неё падение
     * между удалением и вставкой стирало бы историю, а обратный порядок
     * оставлял бы исходные срезы рядом со слитыми, то есть двойной счёт.
     */
    @Transaction
    open suspend fun replaceRange(from: Long, to: Long, slices: List<SpectrogramSliceEntity>) {
        deleteRange(from, to)
        upsert(slices)
    }
}
