package app.radiacode.device

/** Exponential reconnect backoff: 2 s, 4 s, 8 s, ... capped at 60 s. */
class BackoffPolicy(
    private val initialDelayMillis: Long = 2_000,
    private val maxDelayMillis: Long = 60_000,
    private val multiplier: Double = 2.0,
) {
    init {
        require(initialDelayMillis > 0 && maxDelayMillis >= initialDelayMillis && multiplier >= 1.0)
    }

    private var nextDelay = initialDelayMillis

    /** Returns the delay to wait before the next attempt and advances the schedule. */
    fun nextDelayMillis(): Long {
        val delay = nextDelay
        nextDelay = (nextDelay * multiplier).toLong().coerceAtMost(maxDelayMillis)
        return delay
    }

    /** Call after a successful connection so the next failure starts over. */
    fun reset() {
        nextDelay = initialDelayMillis
    }
}
