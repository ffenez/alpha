package app.radiacode.ui.logic

import app.radiacode.analysis.EnergyWindow
import java.util.Locale
import kotlin.math.roundToInt

/** Pure formatting for the Спектр screen. JVM-tested. */
object SpectrumFormat {

    /** Accumulation clock: «04:32», hours as «1:07:09». */
    fun accumulationClock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = s % 3600 / 60
        val secs = s % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    /** Visible-range indicator: «0–3072 кэВ». */
    fun windowLabel(window: EnergyWindow): String =
        "${window.startKeV.roundToInt()}–${window.endKeV.roundToInt()} кэВ"
}
