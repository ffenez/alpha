package app.alpha.ui.logic

import app.alpha.ui.text.MapRu
import app.alpha.ui.text.MapStrings

/**
 * Dark-theme filter for raster OSM tiles, as a 4×5 color matrix (the layout
 * `android.graphics.ColorMatrix` expects). Pure math so the result is
 * JVM-testable — the map bridge only wraps it in a `ColorMatrixColorFilter`.
 *
 * Why not osmdroid's ready-made `TilesOverlay.INVERT_COLORS`: a plain
 * inversion turns every hue into its complement, so forests go magenta and
 * water goes orange — a map that no longer reads as a map. The classic
 * dark-map recipe fixes exactly that: **invert, then rotate the hue by 180°**.
 * Inversion supplies the light→dark flip (light paper ground becomes dark,
 * dark labels become light); the hue rotation undoes the complement, so green
 * stays green and water stays blue. On top of that we desaturate and darken
 * slightly, because full-strength Mapnik colors are louder than the
 * «научный терминал» palette allows next to the amber track ramp.
 *
 * Light theme uses no filter at all — the tiles are already a light map.
 */
object TileFilter {

    /** Slight desaturation: map colors must not compete with the amber ramp. */
    const val SATURATION = 0.85f

    /** Slight dimming after the inversion, so the ground stays below the UI. */
    const val BRIGHTNESS = 0.92f

    /** Rec. 709 luma weights — the same ones ColorMatrix.setSaturation uses. */
    private const val LUM_R = 0.213f
    private const val LUM_G = 0.715f
    private const val LUM_B = 0.072f

    /** Invert every channel: light ground → dark, dark labels → light. */
    fun invertMatrix(): FloatArray = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * Hue rotation by 180° (luma-preserving, SVG `feColorMatrix hueRotate`
     * with cos = −1, sin = 0). Applied after the inversion it restores the
     * original hues at the inverted lightness.
     */
    fun hueRotate180Matrix(): FloatArray = floatArrayOf(
        LUM_R - (1f - LUM_R), LUM_G + LUM_G, LUM_B + LUM_B, 0f, 0f,
        LUM_R + LUM_R, LUM_G - (1f - LUM_G), LUM_B + LUM_B, 0f, 0f,
        LUM_R + LUM_R, LUM_G + LUM_G, LUM_B - (1f - LUM_B), 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun saturationMatrix(saturation: Float): FloatArray {
        val s = saturation
        return floatArrayOf(
            LUM_R + (1f - LUM_R) * s, LUM_G * (1f - s), LUM_B * (1f - s), 0f, 0f,
            LUM_R * (1f - s), LUM_G + (1f - LUM_G) * s, LUM_B * (1f - s), 0f, 0f,
            LUM_R * (1f - s), LUM_G * (1f - s), LUM_B + (1f - LUM_B) * s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun brightnessMatrix(scale: Float): FloatArray = floatArrayOf(
        scale, 0f, 0f, 0f, 0f,
        0f, scale, 0f, 0f, 0f,
        0f, 0f, scale, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** [after] ∘ [before]: the color goes through [before] first. */
    fun concat(after: FloatArray, before: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (column in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += after[row * 5 + k] * before[k * 5 + column]
                }
                if (column == 4) sum += after[row * 5 + 4]
                out[row * 5 + column] = sum
            }
        }
        return out
    }

    /** The dark-theme tile matrix: invert → hue-rotate 180° → desaturate → dim. */
    fun darkMatrix(): FloatArray {
        var matrix = invertMatrix()
        matrix = concat(hueRotate180Matrix(), matrix)
        matrix = concat(saturationMatrix(SATURATION), matrix)
        matrix = concat(brightnessMatrix(BRIGHTNESS), matrix)
        return matrix
    }

    /** Applies [matrix] to one RGB triple; channels clamped to 0..255. */
    fun apply(matrix: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun channel(row: Int): Int {
            val value = matrix[row * 5] * r +
                matrix[row * 5 + 1] * g +
                matrix[row * 5 + 2] * b +
                matrix[row * 5 + 4]
            return value.toInt().coerceIn(0, 255)
        }
        return Triple(channel(0), channel(1), channel(2))
    }
}

/**
 * Honest tile-loading state for the Карта status line. We are blind in the
 * field (no logcat on the user's phone), so the screen says out loud whether
 * tiles are arriving, and — when nothing ever arrives — names the most likely
 * cause in plain language instead of failing silently on a grey rectangle.
 *
 * Counters come from osmdroid's tile-request handler; «loaded» includes cache
 * hits, which is exactly right for the question «is the map alive?».
 */
object TileStatus {

    /** No tile event at all for this long = something is wrong, say it. */
    const val STALL_MILLIS = 15_000L


    /**
     * One-line cause when not a single tile ever arrived. GrapheneOS gives
     * every app a revocable «Сеть» permission, and a denied one looks exactly
     * like this — a map that never paints.
     */
    fun networkHint(
        loaded: Int,
        failed: Int,
        waitedMillis: Long,
        s: MapStrings = MapRu,
    ): String? =
        if (loaded == 0 && (failed > 0 || waitedMillis >= STALL_MILLIS)) {
            s.tilesNetworkHint
        } else {
            null
        }
}
