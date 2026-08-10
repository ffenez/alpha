package app.radiacode.ui.map

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.osmdroid.config.Configuration

/**
 * One-time osmdroid configuration; must run before the first
 * [org.osmdroid.views.MapView] is created.
 *
 * Privacy (CLAUDE.md invariant): tile requests carry nothing but the tile
 * coordinates and the User-Agent below — no keys, no accounts, no telemetry
 * in the library. The on-disk tile cache is generous so revisited areas keep
 * working without network.
 *
 * OSM tile usage policy (https://operations.osmfoundation.org/policies/tiles/)
 * requires a **distinctive** User-Agent identifying the application: the
 * library default is the literal string «osmdroid», which is explicitly
 * blocked. We send the package name.
 */
object OsmSetup {

    /** 512 MB of on-device tile cache (osmdroid defaults to 600 MB / 500 MB trim). */
    const val TILE_CACHE_MAX_BYTES: Long = 512L * 1024 * 1024

    /** Trim target once the cache exceeds the maximum. */
    const val TILE_CACHE_TRIM_BYTES: Long = 448L * 1024 * 1024

    private const val PREFS_NAME = "osmdroid"

    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        val configuration = Configuration.getInstance()
        // osmdroid's own SharedPreferences file (not the app's default one):
        // it stores nothing but cache bookkeeping.
        configuration.load(app, app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
        configuration.userAgentValue = app.packageName
        // Cache under the app cache dir: no storage permission on any API
        // level, and Android may reclaim it under pressure like any cache.
        val base = File(app.cacheDir, "osmdroid")
        configuration.osmdroidBasePath = base
        configuration.osmdroidTileCache = File(base, "tiles")
        configuration.tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
        configuration.tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
    }
}
