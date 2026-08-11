package app.radiacode.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SampleEntity::class,
        RareDataEntity::class,
        EventEntity::class,
        ProfileEntity::class,
        ProfileNetworkEntity::class,
        MeasurementSessionEntity::class,
        TrackSessionEntity::class,
        TrackPointEntity::class,
        SpectrumSnapshotEntity::class,
        ExperimentEntity::class,
        ExperimentRunEntity::class,
        MinuteStatEntity::class,
        HourSketchEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun rareDataDao(): RareDataDao
    abstract fun eventDao(): EventDao
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun trackDao(): TrackDao
    abstract fun spectrumDao(): SpectrumDao
    abstract fun experimentDao(): ExperimentDao

    /** Derived pre-aggregation of ADR 004 (minute scalars, hourly sketches). */
    abstract fun preAggregateDao(): PreAggregateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_1_TO_2.forEach(db::execSQL)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_2_TO_3.forEach(db::execSQL)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_3_TO_4.forEach(db::execSQL)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_4_TO_5.forEach(db::execSQL)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_5_TO_6.forEach(db::execSQL)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_6_TO_7.forEach(db::execSQL)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_7_TO_8.forEach(db::execSQL)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "radiacode.db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                .build()
    }
}
