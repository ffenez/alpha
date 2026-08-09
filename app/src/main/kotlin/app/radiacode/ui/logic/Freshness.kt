package app.radiacode.ui.logic

/**
 * Data honesty (SPEC): when the stream stops, the app must say so instead of
 * presenting the last value as current. Pure state machine, JVM-tested.
 */
sealed interface Freshness {
    /** Nothing was ever measured. */
    data object NoData : Freshness

    /** Stream alive: last sample is at most [STALE_AFTER_SECONDS] old. */
    data class Fresh(val ageSeconds: Long) : Freshness

    /** Stream stopped: last sample is older than [STALE_AFTER_SECONDS]. */
    data class Stale(val ageSeconds: Long) : Freshness

    companion object {
        const val STALE_AFTER_SECONDS = 10L

        /**
         * [lastSampleAtMillis] is the newest sample timestamp (device time base;
         * may run slightly ahead of the phone clock, so negative ages clamp to 0).
         */
        fun of(lastSampleAtMillis: Long?, nowMillis: Long): Freshness {
            if (lastSampleAtMillis == null) return NoData
            val age = ((nowMillis - lastSampleAtMillis) / 1000L).coerceAtLeast(0L)
            return if (age > STALE_AFTER_SECONDS) Stale(age) else Fresh(age)
        }
    }
}

/** UI wording for the staleness indicator; amber only in the stale state. */
fun freshnessLabel(freshness: Freshness): String = when (freshness) {
    Freshness.NoData -> "данных ещё нет"
    is Freshness.Fresh ->
        if (freshness.ageSeconds <= 2) "обновлено только что"
        else "обновлено ${freshness.ageSeconds} с назад"
    is Freshness.Stale -> "поток прерван ${freshness.ageSeconds} с назад"
}
