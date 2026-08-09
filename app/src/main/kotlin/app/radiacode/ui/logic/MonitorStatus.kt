package app.radiacode.ui.logic

/**
 * Main-screen status. Honest wording for what we actually compute today:
 * a comparison against a fixed dose-rate threshold, not a personal baseline.
 *
 * TODO(baseline engine, roadmap #3): replace [of] with a provider that
 * compares against the statistical per-place baseline and yields
 * "обычный фон для этого места" / "выше вашего обычного уровня" wording.
 * The UI consumes only [MonitorStatus], so the engine plugs in here.
 */
enum class MonitorStatus {
    /** Below the configured threshold. */
    NORMAL,

    /** At or above the configured threshold. */
    ABOVE_THRESHOLD,

    /** No current reading to judge. */
    UNKNOWN,
    ;

    companion object {
        fun of(doseRateMicroSvH: Float?, thresholdMicroSvH: Float): MonitorStatus = when {
            doseRateMicroSvH == null -> UNKNOWN
            doseRateMicroSvH >= thresholdMicroSvH -> ABOVE_THRESHOLD
            else -> NORMAL
        }
    }
}

/** Status wording; never claims safety, names the threshold when exceeded. */
fun statusWording(status: MonitorStatus, thresholdMicroSvH: Float): String = when (status) {
    MonitorStatus.NORMAL -> "Фон в норме"
    MonitorStatus.ABOVE_THRESHOLD ->
        "Выше порога ${formatMicroSv(thresholdMicroSvH)} мкЗв/ч"
    MonitorStatus.UNKNOWN -> "Нет данных"
}

/** 0.30 -> "0.30"; readings keep two decimals so digits don't jump. */
fun formatMicroSv(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)
