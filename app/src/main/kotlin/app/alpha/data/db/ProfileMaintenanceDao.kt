package app.alpha.data.db

import androidx.room.Dao
import androidx.room.Query

/**
 * Every row that points at a profile, gathered in one place so profile
 * deletion cannot forget one of them.
 *
 * Deleting a profile **never deletes measurements** (CLAUDE.md: raw data is
 * the asset). Samples, sessions and experiments detach — their profile column
 * becomes NULL, exactly the semantics place deletion had since v2. Leaving a
 * dangling id behind instead would make История attribute a session to a
 * profile that no longer exists.
 *
 * `samples.placeId` / `measurement_sessions.placeId` keep the v2 column name
 * on purpose (see [SampleEntity.profileId]).
 */
@Dao
interface ProfileMaintenanceDao {

    @Query("UPDATE samples SET placeId = NULL WHERE placeId = :profileId")
    suspend fun detachSamples(profileId: Long)

    @Query("UPDATE measurement_sessions SET placeId = NULL WHERE placeId = :profileId")
    suspend fun detachSessions(profileId: Long)

    @Query("UPDATE experiments SET profileId = NULL WHERE profileId = :profileId")
    suspend fun detachExperiments(profileId: Long)

    @Query("SELECT COUNT(*) FROM samples WHERE placeId = :profileId")
    suspend fun sampleCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM measurement_sessions WHERE placeId = :profileId")
    suspend fun sessionCount(profileId: Long): Int
}
