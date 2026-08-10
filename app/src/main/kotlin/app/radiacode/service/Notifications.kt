package app.radiacode.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * Notification channels, creatable from any entry point (service startup or
 * the Settings row that deep-links into the alarm channel's system page —
 * the link needs the channel to exist even if the service never ran).
 */
object Notifications {

    /** Quiet ongoing foreground-service channel. */
    const val MEASUREMENT_CHANNEL_ID = "measurement"

    /**
     * Confirmed persistent deviation (SPEC «Radiation level changed»).
     * High importance with the system default alarm sound and vibration;
     * the user tunes/overrides both via the channel's system settings.
     */
    const val ALARM_CHANNEL_ID = "alarm"

    fun ensureChannels(context: Context) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                MEASUREMENT_CHANNEL_ID,
                "Measurement",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALARM_CHANNEL_ID,
                "Тревога",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Устойчивое превышение уровня, подтверждённое по величине и длительности"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }
}
