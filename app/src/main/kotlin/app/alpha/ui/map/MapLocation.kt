package app.alpha.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.alpha.ui.logic.PositionFix

/** Update cadence while the map is on screen: enough to walk with, no more. */
private const val UPDATE_INTERVAL_MILLIS = 3_000L
private const val UPDATE_DISTANCE_METERS = 5f

/**
 * The user's own position **while the map is in the foreground**, and only
 * then.
 *
 * Scientific instruction §3.3 — «GPS используется только когда пользователь
 * явно запускает Map Recording или функцию, которой нужны координаты» — is
 * satisfied structurally, not by convention:
 *  - the subscription is created on `ON_RESUME` of the composition that shows
 *    the map and torn down on `ON_PAUSE` and on dispose. Leaving the tab,
 *    locking the screen or switching apps stops it within one lifecycle event;
 *  - it lives in the UI layer, so no service, worker or repository can keep it
 *    alive. Recording a track is the *other* consumer of location and owns its
 *    own subscription inside [app.alpha.service.MeasurementService];
 *  - `LocationManager` directly (CLAUDE.md invariant: no Google Play Services).
 *    GPS is requested first and `NETWORK_PROVIDER` in addition, so an indoor
 *    user still gets a coarse marker with an honest accuracy circle instead of
 *    nothing.
 *
 * @param hasPermission ACCESS_FINE_LOCATION granted — without it nothing is
 *   requested and the screen shows its rationale card instead of a marker.
 */
@Composable
fun rememberMyPosition(hasPermission: Boolean): PositionFix? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var fix by remember { mutableStateOf<PositionFix?>(null) }

    DisposableEffect(hasPermission, lifecycleOwner) {
        // Both the caller's flag and the system's own answer: the flag is what
        // the screen believes, `checkSelfPermission` is what is actually
        // granted right now — a permission revoked while the app was in the
        // background would otherwise reach the location call as an exception.
        if (!hasPermission || !locationGranted(context)) return@DisposableEffect onDispose { }
        val manager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@DisposableEffect onDispose { }
        val listener = LocationListener { location -> fix = location.toFix() }
        var subscribed = false

        fun subscribe() {
            if (subscribed) return
            // Checked again here, right next to the call: the permission can be
            // revoked from the system UI while the app is in the background.
            if (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            subscribed = true
            // A last known fix paints the marker immediately; it carries its own
            // timestamp, so an old one is shown as old rather than as current.
            if (fix == null) {
                fix = lastKnown(context, manager)
            }
            for (provider in PROVIDERS) {
                runCatching {
                    if (!manager.isProviderEnabled(provider)) return@runCatching
                    manager.requestLocationUpdates(
                        provider,
                        UPDATE_INTERVAL_MILLIS,
                        UPDATE_DISTANCE_METERS,
                        listener,
                    )
                }
            }
        }

        fun unsubscribe() {
            if (!subscribed) return
            subscribed = false
            runCatching { manager.removeUpdates(listener) }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> subscribe()
                Lifecycle.Event.ON_PAUSE -> unsubscribe()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) subscribe()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unsubscribe()
        }
    }
    return fix
}

private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

/** True while at least one provider can produce a fix at all. */
fun anyLocationProviderEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return false
    return PROVIDERS.any { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
}

/** ACCESS_FINE_LOCATION as the system sees it at this moment. */
private fun locationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun lastKnown(context: Context, manager: LocationManager): PositionFix? {
    if (
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }
    return PROVIDERS
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.toFix()
}

private fun Location.toFix(): PositionFix = PositionFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else 0f,
    timeMillis = time,
)
