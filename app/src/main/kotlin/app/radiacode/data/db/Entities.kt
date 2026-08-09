package app.radiacode.data.db

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
    indices = [Index(value = ["timestamp"], unique = true)],
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
        const val SOURCE_HOTSPOT = "hotspot"
    }
}

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
}
