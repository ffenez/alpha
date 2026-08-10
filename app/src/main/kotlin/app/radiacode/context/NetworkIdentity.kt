package app.radiacode.context

import java.security.MessageDigest
import java.util.Locale

/**
 * Privacy-first Wi-Fi identity (spec §3.2, CLAUDE.md invariants).
 *
 * **Problem.** Since Android 10 the SSID/BSSID of the current network are
 * location-guarded: reading them requires `ACCESS_FINE_LOCATION`. Asking a
 * user for the location permission so the app can notice «I am at home» is
 * exactly the trade the spec rejects (§3.3, §23 — «постоянный GPS ради
 * автоматического профиля» is forbidden, and a location permission just to
 * read a network name is the same bargain in a cheaper coat).
 *
 * **Solution.** A network is identified by the address of its default gateway
 * (and, where the platform exposes it, the DHCP server), which
 * `ConnectivityManager`/`LinkProperties` reports **without any permission**.
 * The raw address is never stored: it is hashed once with SHA-256 under a
 * fixed domain string and truncated to [HASH_HEX_LENGTH] hex characters, so
 * what lands in the database is a stable local token and not an address that
 * could describe someone's network to a third party.
 *
 * **Assumptions and limitations — stated honestly, because the UI states them
 * too:**
 *  - Two networks with the same gateway address (`192.168.1.1` is the default
 *    of nearly every home router) hash to the same token. The app therefore
 *    treats a binding as a hint that must be *confirmed by the user once*
 *    («привязать текущую сеть»), never as proof of location. A wrong match
 *    can only mislabel a profile — it can never fabricate a measurement, and
 *    the spectral fingerprint guard of stage 4 will catch a mismatched
 *    environment (spec §13).
 *  - Changing the router or the subnet changes the token; the user re-binds.
 *  - The token is device-local and salted only by [DOMAIN]; it is never
 *    exported, transmitted or logged.
 *
 * When the user *has* granted fine location for the map, the SSID is read as
 * a human-readable label and stored next to the hash for display only — the
 * matching itself never depends on it.
 */
object NetworkIdentity {

    /** Bump if the token derivation changes; stored bindings would be reset. */
    const val ALGORITHM_VERSION = 1

    /** Domain separation: this hash is only ever meaningful inside this app. */
    private const val DOMAIN = "radiacode-wifi-identity-v1|"

    /** 64 bits of the digest — collision-free enough for a handful of networks. */
    const val HASH_HEX_LENGTH = 16

    /**
     * @param gatewayAddress default-route gateway, e.g. `192.168.1.1`
     * @param dhcpServerAddress DHCP server when the platform exposes it (API 30+)
     * @return the stable local token, or null when there is nothing to identify
     */
    fun of(gatewayAddress: String?, dhcpServerAddress: String? = null): String? {
        val parts = listOfNotNull(
            gatewayAddress?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() },
            dhcpServerAddress?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() },
        ).distinct()
        if (parts.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((DOMAIN + parts.joinToString("|")).toByteArray(Charsets.UTF_8))
        return buildString(HASH_HEX_LENGTH) {
            for (i in 0 until HASH_HEX_LENGTH / 2) {
                append(String.format(Locale.US, "%02x", digest[i]))
            }
        }
    }

    /**
     * Display label for a bound network: the SSID when we were allowed to read
     * one, otherwise an honest short form of the token. Never invents a name.
     */
    fun displayLabel(label: String?, hash: String): String =
        label?.takeIf { it.isNotBlank() } ?: "сеть ${hash.take(6)}"
}
