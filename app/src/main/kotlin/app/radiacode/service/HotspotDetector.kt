package app.radiacode.service

/**
 * Rising-edge hotspot detection with hysteresis.
 *
 * Fires once when the dose rate crosses [thresholdMicroSvH] upward, then stays
 * silent until the value drops below `threshold * rearmFraction` — so a sensor
 * hovering around the threshold produces one event, not a stream.
 */
class HotspotDetector(
    @Volatile var thresholdMicroSvH: Float,
    private val rearmFraction: Float = 0.8f,
) {
    init {
        require(rearmFraction in 0f..1f) { "rearmFraction must be within 0..1" }
    }

    private var armed = true

    /** Returns true exactly once per excursion above the threshold. */
    fun onSample(doseRateMicroSvH: Float): Boolean {
        if (armed && doseRateMicroSvH >= thresholdMicroSvH) {
            armed = false
            return true
        }
        if (!armed && doseRateMicroSvH < thresholdMicroSvH * rearmFraction) {
            armed = true
        }
        return false
    }
}
