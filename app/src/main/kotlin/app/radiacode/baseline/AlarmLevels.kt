package app.radiacode.baseline

/**
 * Alarm sensitivity (SPEC «Alarms», Simple mode): one choice maps to a full
 * parameter set — the alarm always considers absolute dose rate, the relative
 * excess over the personal baseline AND the duration of the excess, which
 * suppresses false alarms from single statistical jumps.
 */
enum class AlarmSensitivity {
    NORMAL,
    HIGH,
    CUSTOM,
    ;

    companion object {
        fun fromStorage(value: String?): AlarmSensitivity =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
    }
}

/**
 * Resolved alarm parameters. [l1MicroSvH] drives the deviation alert and the
 * dashed line on charts; [l2MicroSvH] is the second, stronger level (stored
 * and shown in custom mode; reserved for a hard alarm tier).
 */
data class AlarmThresholds(
    val l1MicroSvH: Float,
    val l2MicroSvH: Float,
    /** Deviation also triggers at `relativeFactor × baseline P90`. */
    val relativeFactor: Float,
    /** How long the excess must hold before the alert confirms. */
    val persistenceSeconds: Int,
)

/** Preset mapping; custom values are sanitized (L1 > 0, L2 ≥ L1). */
fun alarmThresholds(
    sensitivity: AlarmSensitivity,
    customL1MicroSvH: Float,
    customL2MicroSvH: Float,
): AlarmThresholds = when (sensitivity) {
    AlarmSensitivity.NORMAL -> AlarmThresholds(
        l1MicroSvH = 0.30f,
        l2MicroSvH = 1.00f,
        relativeFactor = 2.0f,
        persistenceSeconds = 120,
    )
    AlarmSensitivity.HIGH -> AlarmThresholds(
        l1MicroSvH = 0.15f,
        l2MicroSvH = 0.50f,
        relativeFactor = 1.5f,
        persistenceSeconds = 60,
    )
    AlarmSensitivity.CUSTOM -> {
        val l1 = if (customL1MicroSvH > 0f) customL1MicroSvH else 0.30f
        val l2 = if (customL2MicroSvH >= l1) customL2MicroSvH else l1
        AlarmThresholds(
            l1MicroSvH = l1,
            l2MicroSvH = l2,
            relativeFactor = 2.0f,
            persistenceSeconds = 120,
        )
    }
}

/**
 * Deviation magnitude condition: absolute level OR relative excess over the
 * baseline's typical high. Duration is judged separately by
 * [PersistenceTracker] — both must hold for a deviation status/alert.
 */
fun deviationMagnitude(
    doseRateMicroSvH: Float,
    baselineHighMicroSvH: Float?,
    thresholds: AlarmThresholds,
): Boolean {
    if (doseRateMicroSvH >= thresholds.l1MicroSvH) return true
    if (baselineHighMicroSvH != null && baselineHighMicroSvH > 0f) {
        return doseRateMicroSvH >= baselineHighMicroSvH * thresholds.relativeFactor
    }
    return false
}

/**
 * "Above usual" magnitude: outside the typical band with a small margin so the
 * status does not flap right at the P90 boundary. Milder than the alarm
 * condition — it changes wording, not alarms.
 */
fun aboveUsualMagnitude(doseRateMicroSvH: Float, baseline: Baseline?): Boolean {
    if (baseline == null || baseline.doseHighMicroSvH <= 0f) return false
    return doseRateMicroSvH > baseline.doseHighMicroSvH * ABOVE_USUAL_MARGIN
}

private const val ABOVE_USUAL_MARGIN = 1.05f

/** Minimum dwell before the UI switches to «Выше обычного», seconds. */
const val ABOVE_USUAL_MIN_DWELL_SECONDS = 60L
