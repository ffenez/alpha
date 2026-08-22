package app.alpha.data.db

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
        BaselineEpochEntity::class,
        ProfileFingerprintEntity::class,
        SpectrogramSliceEntity::class,
        EnvironmentEntity::class,
        SurveyStationEntity::class,
        SpectrumTemplateEntity::class,
    ],
    version = 23,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun rareDataDao(): RareDataDao
    abstract fun eventDao(): EventDao
    abstract fun profileDao(): ProfileDao
    abstract fun profileMaintenanceDao(): ProfileMaintenanceDao
    abstract fun sessionDao(): SessionDao
    abstract fun trackDao(): TrackDao
    abstract fun spectrumDao(): SpectrumDao
    abstract fun experimentDao(): ExperimentDao

    /** Условия вокруг измерения: давление, поле, температура телефона. */
    abstract fun environmentDao(): EnvironmentDao

    /** Станции радиоэлементной съёмки. */
    abstract fun surveyDao(): SurveyDao

    /** Шаблоны для полноспектрального разложения. */
    abstract fun templateDao(): SpectrumTemplateDao

    /** Derived pre-aggregation of ADR 004 (minute scalars, hourly sketches). */
    abstract fun preAggregateDao(): PreAggregateDao

    /** Постоянная история спектрограммы (ADR 007). */
    abstract fun spectrogramDao(): SpectrogramDao

    companion object {

        /**
         * Версия схемы одним числом. Резервная копия записывает её в манифест
         * — не для миграций (у копии своя версия формата), а чтобы при разборе
         * жалобы было видно, из какой базы копия снята.
         */
        const val VERSION = 23

        /** Имя файла базы: его же спрашивает экран «сколько занято». */
        const val NAME = "alpha.db"

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_8_TO_9.forEach(db::execSQL)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_9_TO_10.forEach(db::execSQL)
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_10_TO_11.forEach(db::execSQL)
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_11_TO_12.forEach(db::execSQL)
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_12_TO_13.forEach(db::execSQL)
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_13_TO_14.forEach(db::execSQL)
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_14_TO_15.forEach(db::execSQL)
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_15_TO_16.forEach(db::execSQL)
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_16_TO_17.forEach(db::execSQL)
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_17_TO_18.forEach(db::execSQL)
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_18_TO_19.forEach(db::execSQL)
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_19_TO_20.forEach(db::execSQL)
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_20_TO_21.forEach(db::execSQL)
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_21_TO_22.forEach(db::execSQL)
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.FROM_22_TO_23.forEach(db::execSQL)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                )
                .build()
    }
}
