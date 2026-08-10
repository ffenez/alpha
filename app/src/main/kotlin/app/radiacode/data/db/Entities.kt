package app.radiacode.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One real-time measurement (~1 Hz). Values are stored raw as decoded from
 * DATA_BUF (doseRate in device units, countRate in cps, errors in percent) —
 * unit conversion happens at display time only.
 *
 * `timestamp` is unique so that overlapping DATA_BUF reads after a reconnect
 * deduplicate on insert (OnConflictStrategy.IGNORE).
 */
@Entity(
    tableName = "samples",
    indices = [
        Index(value = ["timestamp"], unique = true),
        Index(value = ["placeId", "timestamp"]),
    ],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis (device base time + record offset). */
    val timestamp: Long,
    val doseRate: Float,
    val doseRateErr: Float,
    val countRate: Float,
    val countRateErr: Float,
    val flags: Int,
    val realTimeFlags: Int,
    /**
     * Measurement profile active when the sample was recorded; null for
     * samples measured before profiles existed or with no profile selected.
     * Deleting a profile detaches its samples (sets null) instead of deleting
     * measurements.
     *
     * The **column** is still called `placeId` (v2 name). Renaming it would
     * require a full table rebuild: SQLite gained `ALTER TABLE … RENAME
     * COLUMN` only in 3.25 (Android API 30) and this app runs from API 26, so
     * the portable path is copying every row — millions of them after a month
     * of 1 Hz recording. The mapping stays in the entity instead; see
     * MigrationSql.FROM_5_TO_6.
     */
    @ColumnInfo(name = "placeId")
    val profileId: Long? = null,
    /**
     * Baseline admission verdict at write time: null = admitted into baseline
     * statistics, otherwise [app.radiacode.baseline.BaselineExclusion.storageKey]
     * of the first unmet condition (spec §4.2). Raw values are stored either
     * way — exclusion only keeps an anomaly from becoming «the new normal».
     */
    val baselineExcluded: String? = null,
)

/** Battery / temperature / accumulated dose status (every few minutes). */
@Entity(
    tableName = "rare_data",
    indices = [Index(value = ["timestamp"], unique = true)],
)
data class RareDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Accumulated dose, raw device units. */
    val dose: Float,
    /** Degrees Celsius. */
    val temperature: Float,
    /** Battery charge, percent 0..100. */
    val batteryPercent: Float,
    /** Measurement duration reported by the device, seconds. */
    val durationSeconds: Long,
    val flags: Int,
)

/** Device-originated events and app-detected hotspots, one journal. */
@Entity(
    tableName = "events",
    indices = [Index("timestamp")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** [SOURCE_DEVICE] or [SOURCE_HOTSPOT]. */
    val source: String,
    /** Device event code ([app.radiacode.protocol.EventId]) or 0 for hotspots. */
    val code: Int,
    val name: String,
    val param1: Int,
    val flags: Int,
    /** Dose rate at the moment of the event, raw device units (hotspots). */
    val doseRate: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    companion object {
        const val SOURCE_DEVICE = "device"

        /**
         * Track hotspot (threshold crossing while recording). Carries lat/lon;
         * [param1] stores the baseline typical high in nSv/h at event time
         * (0 = no baseline), same convention as [SOURCE_DEVIATION].
         */
        const val SOURCE_HOTSPOT = "hotspot"

        /**
         * Persistent baseline deviation confirmed by the alarm engine.
         * [doseRate] holds the raw dose rate; [param1] stores the baseline
         * typical high at that moment in nSv/h (µSv/h × 1000, 0 = no baseline)
         * so History can honestly say «обычно здесь X» as of the event time.
         */
        const val SOURCE_DEVIATION = "deviation"

        /** User saved a spectrum snapshot ([param1] = accumulation seconds). */
        const val SOURCE_SPECTRUM = "spectrum"
    }
}

/**
 * Measurement profile / context (spec §3): a user-named environment with its
 * own history and statistical baseline. Replaces the v2–v5 «place»: `Дом` is
 * not a special GPS mode, just a profile that may carry automatic activation
 * rules.
 *
 * Nesting is one level deep («Дом / Спальня»): [parentId] points at a root
 * profile and a profile that already has children can never become a child
 * itself (see `ui/logic/ProfileTree`). Deeper trees buy nothing here and make
 * the picker unreadable on a phone.
 */
@Entity(
    tableName = "profiles",
    indices = [Index("parentId")],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Single glyph shown before the name; empty = none. */
    val icon: String = "",
    /** Parent profile for nesting; null = root profile. */
    val parentId: Long? = null,
    /** Archived profiles stay in history but leave the picker. */
    val archived: Boolean = false,
    /** Wi-Fi bindings of this profile may activate it automatically. */
    val autoActivate: Boolean = true,
    /** Condition 1 of the baseline admission pipeline (spec §4.2). */
    val baselineLearning: Boolean = true,
    /** [ROLE_USER], [ROLE_TRANSIT] or [ROLE_NO_PLACE]. */
    val role: String = ROLE_USER,
    val createdAt: Long,
) {
    companion object {
        /** Ordinary user profile. */
        const val ROLE_USER = "user"

        /** Activated when no known network is around after the grace period. */
        const val ROLE_TRANSIT = "transit"

        /** Activated when the context cannot be determined at all. */
        const val ROLE_NO_PLACE = "no_place"
    }
}

/**
 * A Wi-Fi network bound to a profile, identified **without** the location
 * permission (spec §3.2, CLAUDE.md privacy invariant).
 *
 * [networkHash] is a local one-way hash of the network's gateway/DHCP-server
 * address taken from `LinkProperties` (see
 * [app.radiacode.context.NetworkIdentity]) — not an SSID and not a BSSID.
 * [label] holds a human-readable SSID only when the user has granted fine
 * location and only for display; the binding never depends on it.
 */
@Entity(
    tableName = "profile_networks",
    indices = [
        Index(value = ["networkHash"], unique = true),
        Index("profileId"),
    ],
)
data class ProfileNetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val networkHash: String,
    val label: String? = null,
    val createdAt: Long,
)

/**
 * A measurement session: one continuous connected period of the measurement
 * service (opened on device connect, closed on disconnect/stop). Sessions
 * carry no measurements themselves — summaries aggregate `samples` by the
 * [startedAt, endedAt] range, so the raw data stays single-sourced.
 */
@Entity(
    tableName = "measurement_sessions",
    indices = [Index("startedAt")],
)
data class MeasurementSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Profile active at session start; null = no profile selected. The user
     * can correct it afterwards from История (spec §20). Column name kept from
     * v2 for the same reason as [SampleEntity.profileId].
     */
    @ColumnInfo(name = "placeId")
    val profileId: Long?,
    val startedAt: Long,
    /** Null while the session is still running. */
    val endedAt: Long? = null,
)

/** A recorded track (GPS walk with the dosimeter). */
@Entity(tableName = "track_sessions")
data class TrackSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "timestamp"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    /** Latest dose rate at this point, raw device units; null if not yet received. */
    val doseRate: Float?,
    val countRate: Float?,
    /**
     * GPS (ellipsoid) altitude, meters; null when the fix carries none or the
     * point predates v5. Feeds flight detection (sustained >3000 м) and the
     * dose-vs-altitude view of flight sessions.
     */
    val altitudeMeters: Double? = null,
)

/** A saved 1024-channel spectrum with its energy calibration. */
@Entity(
    tableName = "spectra",
    indices = [Index("timestamp")],
)
data class SpectrumSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** true = accumulated (lifetime) spectrum, false = current since last reset. */
    val accumulated: Boolean,
    /**
     * true = the user recorded this snapshot as the background reference for
     * overlay/subtraction on the Спектр screen. The newest flagged row wins;
     * older references stay as ordinary history snapshots.
     */
    val isBackgroundReference: Boolean = false,
    /**
     * How the row appeared: [ORIGIN_AUTO] (periodic autosave), [ORIGIN_USER]
     * (explicit «Сохранить»/«Записать фон»/comparator result) or
     * [ORIGIN_IMPORT] (RC-XML file). History lists user+import rows; imported
     * rows are excluded from device-data queries (latest spectrum, background
     * reference, session badges) so foreign files never mix into device data.
     * Rows saved before v4 are all 'auto' — they were indistinguishable.
     */
    @ColumnInfo(defaultValue = ORIGIN_AUTO)
    val origin: String = ORIGIN_AUTO,
    /** Display name: RC-XML sample name for imports, user label otherwise. */
    val label: String? = null,
    val durationSeconds: Long,
    val a0: Float,
    val a1: Float,
    val a2: Float,
    val channelCount: Int,
    /** Channel counts encoded as i32 LE array, see [app.radiacode.data.SpectrumBlob]. */
    val counts: ByteArray,
) {
    // ByteArray needs manual equality; identity by id is enough for entities.
    override fun equals(other: Any?): Boolean = other is SpectrumSnapshotEntity && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val ORIGIN_AUTO = "auto"
        const val ORIGIN_USER = "user"
        const val ORIGIN_IMPORT = "import"
    }
}
