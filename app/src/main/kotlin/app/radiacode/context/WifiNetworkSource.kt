package app.radiacode.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What we know about the current Wi-Fi link; null hash = no Wi-Fi at all. */
data class NetworkSnapshot(val hash: String?, val label: String?)

/**
 * Observes the current Wi-Fi network **without the location permission**: the
 * default-route gateway from `LinkProperties` feeds [NetworkIdentity], which
 * turns it into a stable local token (see that class for why this is the
 * privacy-correct signal and what it cannot promise).
 *
 * The SSID is read only as a display label and only if the user already
 * granted `ACCESS_FINE_LOCATION` for the map — the app never asks for it on
 * its own and the profile matching never depends on it.
 */
class WifiNetworkSource(private val context: Context) {

    private val _snapshot = MutableStateFlow(NetworkSnapshot(null, null))
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                _snapshot.value = NetworkSnapshot(hashOf(props), readSsidLabel())
            }

            override fun onLost(network: Network) {
                _snapshot.value = NetworkSnapshot(null, null)
            }

            override fun onUnavailable() {
                _snapshot.value = NetworkSnapshot(null, null)
            }
        }
        callback = cb
        runCatching { connectivity.registerNetworkCallback(request, cb) }
            .onFailure { callback = null }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        runCatching { connectivity.unregisterNetworkCallback(cb) }
    }

    private fun hashOf(props: LinkProperties): String? {
        val gateway = props.routes
            .firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway
            ?.hostAddress
        val dhcp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            props.dhcpServerAddress?.hostAddress
        } else {
            null
        }
        return NetworkIdentity.of(gateway, dhcp)
    }

    /**
     * SSID for display, or null when we are not allowed to read one. Android
     * returns the placeholder `<unknown ssid>` instead of failing when the
     * permission is missing, so that value is filtered out explicitly.
     */
    private fun readSsidLabel(): String? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return runCatching {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifi.connectionInfo?.ssid
                ?.trim('"')
                ?.takeIf { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) }
        }.getOrNull()
    }
}
