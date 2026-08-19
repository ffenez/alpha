package app.alpha.ui.feedback

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
     *
     * Note for diagnosis: `getCurrentInterruptionFilter` returns
     * `INTERRUPTION_FILTER_UNKNOWN` when the app has no notification-policy
     * access, and we treat unknown as «allowed» — an unreadable policy must
     * not silence the feature.
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

    /** True when this device has a vibration motor at all. */
    fun hasVibrator(context: Context): Boolean = vibrator(context)?.hasVibrator() == true

    /**
     * One short σ-step pulse (see VibrationPolicy); no-op under DND or with
     * no motor. Returns whether the pulse was actually emitted, so the caller
     * can explain silence instead of guessing.
     */
    fun pulse(context: Context): Boolean {
        if (!dndAllowsFeedback(context)) return false
        return pulseNow(context)
    }

    private fun pulseNow(context: Context): Boolean {
        val vibrator = vibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false
        vibrator.vibrate(
            VibrationEffect.createOneShot(PULSE_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE),
        )
        return true
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

    /**
     * Тревожная серия толчков: превышение порога, заданного В ЭТОМ приложении.
     *
     * Отдельно от [pulse] по двум причинам. Во-первых, это событие, а не
     * ведение: серия из трёх толчков ощущается иначе, чем поисковый пульс, и
     * их нельзя спутать. Во-вторых, она НЕ спрашивает тихий режим: порог
     * поставил сам человек, и молча проглотить его срабатывание значило бы
     * подменить его решение системной настройкой. Отключается тревога там же,
     * где включается, — в настройках приложения.
     */
    fun alarmPattern(context: Context): Boolean {
        val vibrator = vibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false
        vibrator.vibrate(
            VibrationEffect.createWaveform(ALARM_PATTERN_MILLIS, ALARM_AMPLITUDES, -1),
        )
        return true
    }

    /**
     * Пауза-толчок-пауза-толчок-пауза-толчок, мс.
     *
     * **Инженерный параметр**: три толчка по 220 мс с промежутком 160 мс —
     * узнаваемая серия, которая заметна в кармане и не сливается с поисковым
     * пульсом (одиночные 40 мс).
     */
    private val ALARM_PATTERN_MILLIS =
        longArrayOf(0L, 220L, 160L, 220L, 160L, 220L)

    private val ALARM_AMPLITUDES =
        intArrayOf(0, 255, 0, 255, 0, 255)
}
