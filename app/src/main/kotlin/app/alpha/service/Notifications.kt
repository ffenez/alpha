package app.alpha.service

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import app.alpha.ui.text.NotificationRu
import app.alpha.ui.text.NotificationStrings

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

    fun ensureChannels(context: Context, s: NotificationStrings = NotificationRu) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                MEASUREMENT_CHANNEL_ID,
                s.measurementChannel,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALARM_CHANNEL_ID,
                s.alarmChannel,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = s.alarmChannelDescription
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

    /**
     * Состояние канала «Тревога» глазами системы.
     *
     * Включить канал программно НЕЛЬЗЯ: Android разрешает приложению создать
     * канал, но не воскресить выключенный человеком — отсюда системная фраза
     * «at your request, Android is blocking this category». Единственное, что
     * может приложение, — честно показать состояние и открыть его настройки.
     */
    enum class AlarmChannelState {
        /** Канал звучит: уведомления разрешены и важность не нулевая. */
        ENABLED,

        /** Выключены уведомления приложения целиком. */
        APP_BLOCKED,

        /** Выключен именно этот канал. */
        CHANNEL_BLOCKED,
    }

    fun alarmChannelState(context: Context): AlarmChannelState {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return AlarmChannelState.APP_BLOCKED
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(ALARM_CHANNEL_ID)
            ?: return AlarmChannelState.ENABLED
        return if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            AlarmChannelState.CHANNEL_BLOCKED
        } else {
            AlarmChannelState.ENABLED
        }
    }
}
