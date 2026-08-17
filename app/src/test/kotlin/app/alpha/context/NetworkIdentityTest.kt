package app.alpha.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkIdentityTest {

    @Test
    fun `same gateway gives the same stable token`() {
        val a = NetworkIdentity.of("192.168.1.1")
        val b = NetworkIdentity.of("192.168.1.1")
        assertEquals(a, b)
        assertEquals(NetworkIdentity.HASH_HEX_LENGTH, a!!.length)
        assertTrue(a.all { it in "0123456789abcdef" }, "token must be plain hex")
    }

    @Test
    fun `token never contains the address itself`() {
        val token = NetworkIdentity.of("192.168.1.1")!!
        assertTrue(!token.contains("192"), "the raw address must not survive hashing")
    }

    @Test
    fun `different gateways give different tokens`() {
        assertNotEquals(NetworkIdentity.of("192.168.1.1"), NetworkIdentity.of("192.168.0.1"))
    }

    @Test
    fun `dhcp server refines the identity when the platform exposes it`() {
        val gatewayOnly = NetworkIdentity.of("192.168.1.1")
        val withDhcp = NetworkIdentity.of("192.168.1.1", "192.168.1.2")
        assertNotEquals(gatewayOnly, withDhcp)
        // A DHCP server equal to the gateway adds nothing and must not change
        // the token, otherwise the same network would look like two.
        assertEquals(gatewayOnly, NetworkIdentity.of("192.168.1.1", "192.168.1.1"))
    }

    @Test
    fun `case and padding do not create a second identity`() {
        assertEquals(NetworkIdentity.of("FE80::1"), NetworkIdentity.of(" fe80::1 "))
    }

    @Test
    fun `no addresses means no identity`() {
        assertNull(NetworkIdentity.of(null, null))
        assertNull(NetworkIdentity.of("  ", null))
    }

    @Test
    fun `display label falls back to a short token instead of inventing a name`() {
        val token = NetworkIdentity.of("192.168.1.1")!!
        assertEquals("Home Wi-Fi", NetworkIdentity.displayLabel("Home Wi-Fi", token))
        assertEquals("сеть ${token.take(6)}", NetworkIdentity.displayLabel(null, token))
        assertEquals("сеть ${token.take(6)}", NetworkIdentity.displayLabel("  ", token))
    }
}
