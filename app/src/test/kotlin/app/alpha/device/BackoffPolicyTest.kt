package app.alpha.device

import kotlin.test.Test
import kotlin.test.assertEquals

class BackoffPolicyTest {

    @Test
    fun `doubles from 2s and caps at 60s`() {
        val policy = BackoffPolicy()
        val delays = List(7) { policy.nextDelayMillis() }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), delays)
    }

    @Test
    fun `reset restarts the schedule`() {
        val policy = BackoffPolicy()
        repeat(5) { policy.nextDelayMillis() }
        policy.reset()
        assertEquals(2_000L, policy.nextDelayMillis())
        assertEquals(4_000L, policy.nextDelayMillis())
    }
}
