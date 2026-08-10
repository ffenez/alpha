package app.radiacode.ui.map

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

/**
 * One-time MapLibre initialization. Must run before the first [org.maplibre.android.maps.MapView]
 * is created.
 *
 * Privacy (CLAUDE.md invariant): tile requests carry nothing but tile
 * coordinates — OpenFreeMap needs no API key and no account, and MapLibre
 * (the community fork) contains no telemetry. The ambient cache is set to a
 * generous 512 MB so areas the user has visited keep working fully offline.
 */
object MapSetup {

    /** 512 MB of on-device tile cache (default is 50 MB — too small for field use). */
    const val AMBIENT_CACHE_BYTES: Long = 512L * 1024 * 1024

    /**
     * OpenFreeMap public styles (https://openfreemap.org), no keys: `dark`
     * lands almost exactly on the научный-терминал dark ground (#0C0C0C vs
     * #0F1216), `positron` is the neutral light companion — so no runtime
     * style dimming is needed in either theme.
     */
    const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
    const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"

    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        MapLibre.getInstance(app)
        OfflineManager.getInstance(app).setMaximumAmbientCacheSize(AMBIENT_CACHE_BYTES, null)
    }

    fun styleUrl(dark: Boolean): String = if (dark) STYLE_DARK else STYLE_LIGHT

    /**
     * Local no-network style: our track layers still render on a blank ground
     * when tiles are unavailable on first launch (honest offline state).
     */
    fun fallbackStyleJson(backgroundHex: String): String =
        """{"version":8,"name":"offline","sources":{},""" +
            """"layers":[{"id":"background","type":"background",""" +
            """"paint":{"background-color":"$backgroundHex"}}]}"""
}
