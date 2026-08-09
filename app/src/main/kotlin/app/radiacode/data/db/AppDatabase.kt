package app.radiacode.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SampleEntity::class,
        RareDataEntity::class,
        EventEntity::class,
        TrackSessionEntity::class,
        TrackPointEntity::class,
        SpectrumSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun rareDataDao(): RareDataDao
    abstract fun eventDao(): EventDao
    abstract fun trackDao(): TrackDao
    abstract fun spectrumDao(): SpectrumDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "radiacode.db")
                .build()
    }
}
