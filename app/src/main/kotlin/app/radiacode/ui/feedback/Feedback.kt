package app.radiacode.ui.feedback

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Shared politeness/haptics helpers for the Search feedback mode.
 * System alarm notifications are unaffected — they go through the «Тревога»
 * notification channel, where DND policy is the system's business.
 */
object Feedback {

    /**
     * Search clicks/pulses are convenience feedback, not an alarm, so any
     * active Do-Not-Disturb filter (priority/alarms-only/total) silences them.
     */
    fun dndAllowsFeedback(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            -> true
            else -> false
        }
    }

    /** One short σ-step pulse (see VibrationPolicy); no-op under DND. */
    fun pulse(context: Context) {
        if (!dndAllowsFeedback(context)) return
        vibrator(context)?.vibrate(
            VibrationEffect.createOneShot(PULSE_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private const val PULSE_MILLIS = 40L
}
