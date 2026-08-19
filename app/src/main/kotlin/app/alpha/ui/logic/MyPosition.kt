package app.alpha.ui.logic

import app.alpha.ui.text.MapRu
import app.alpha.ui.text.MapStrings
import app.alpha.ui.text.uiDecimal
import java.util.Locale

/**
 * One location fix, free of Android and map-engine types so the whole
 * «я на карте» behaviour is JVM-testable.
 */
data class PositionFix(
    val latitude: Double,
    val longitude: Double,
    /** Horizontal accuracy the provider reports, meters; 0 = not reported. */
    val accuracyMeters: Float,
    /** Fix time, wall clock ms. */
    val timeMillis: Long,
)

/**
 * What the map can honestly say about the user's own position.
 *
 * Four states, in the order they are decided — the screen must never show a
 * marker it cannot justify:
 *  - [NO_PERMISSION] — location was not granted; the rationale card explains
 *    it and no marker is drawn;
 *  - [PROVIDER_OFF] — permission is there but every location provider is
 *    switched off in the system; the existing «GPS выключен» chip already
 *    names this, so the position chip stays silent;
 *  - [WAITING_FIX] — subscribed, nothing received yet («жду сигнал GPS»);
 *  - [FIXED] — a fix exists; the marker is drawn with its accuracy circle.
 *
 * Staleness is deliberately NOT a fifth state: a fix that stopped refreshing
 * is still the last place we actually measured the user at, so the marker
 * stays (drawn dimmed by the overlay) and the chip says how old it is instead
 * of the position silently disappearing.
 */
enum class PositionState { NO_PERMISSION, PROVIDER_OFF, WAITING_FIX, FIXED }

object MyPosition {

    /** Older than this and the chip reports the age instead of the accuracy. */
    const val STALE_AFTER_MILLIS = 30_000L

    fun state(
        hasPermission: Boolean,
        providersEnabled: Boolean,
        fix: PositionFix?,
    ): PositionState = when {
        !hasPermission -> PositionState.NO_PERMISSION
        !providersEnabled -> PositionState.PROVIDER_OFF
        fix == null -> PositionState.WAITING_FIX
        else -> PositionState.FIXED
    }

    fun isStale(fix: PositionFix, nowMillis: Long): Boolean =
        nowMillis - fix.timeMillis > STALE_AFTER_MILLIS

    /** The marker is drawn whenever a fix exists, even without a live provider. */
    fun markerVisible(state: PositionState, fix: PositionFix?): Boolean =
        fix != null && state != PositionState.NO_PERMISSION

    /**
     * Text of the position chip, or null when another element on the screen
     * already says it (no permission → rationale card, providers off → the
     * «GPS выключен» chip) — the same fact is never stated twice.
     */
    fun chipText(
        state: PositionState,
        fix: PositionFix?,
        nowMillis: Long,
        s: MapStrings = MapRu,
        /**
         * На экране уже стоит карточка «жду первые точки».
         *
         * Тогда чип молчит: «жду сигнал GPS» и «жду первые точки» — одно и то
         * же ожидание, названное дважды, и вместе они читаются как две разные
         * беды.
         */
        trackWaiting: Boolean = false,
    ): String? =
        when (state) {
            PositionState.NO_PERMISSION, PositionState.PROVIDER_OFF -> null
            PositionState.WAITING_FIX -> if (trackWaiting) null else s.waitingGps
            PositionState.FIXED -> {
                val current = fix ?: return null
                if (isStale(current, nowMillis)) {
                    s.fixAgo(HistoryFormat.duration((nowMillis - current.timeMillis) / 1000))
                } else {
                    s.meWithAccuracy(accuracy(current.accuracyMeters, s))
                }
            }
        }

    /** «±12 м»; providers that report no accuracy get «точность неизвестна». */
    fun accuracy(meters: Float, s: MapStrings = MapRu): String = when {
        meters <= 0f || !meters.isFinite() -> s.accuracyUnknown
        meters < 10f ->
            String.format(Locale.US, "±%.1f", meters).uiDecimal() + " " + s.unitMeters
        else -> String.format(Locale.US, "±%.0f", meters) + " " + s.unitMeters
    }
}
