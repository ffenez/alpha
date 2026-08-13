package app.radiacode.service

import kotlin.test.Test
import kotlin.test.assertEquals

class FastPollHubTest {

    @Test
    fun `no watchers means the ordinary one hertz poll`() {
        val hub = FastPollHub()
        assertEquals(0, hub.watchers.value)
        assertEquals(
            FastPollHub.NORMAL_INTERVAL_MILLIS,
            FastPollHub.intervalMillis(hub.watchers.value),
        )
    }

    @Test
    fun `attaching speeds the poll up`() {
        val hub = FastPollHub()
        hub.attach()
        assertEquals(
            FastPollHub.FAST_INTERVAL_MILLIS,
            FastPollHub.intervalMillis(hub.watchers.value),
        )
    }

    @Test
    fun `detaching returns to one hertz`() {
        val hub = FastPollHub()
        hub.attach()
        hub.detach()
        assertEquals(0, hub.watchers.value)
        assertEquals(
            FastPollHub.NORMAL_INTERVAL_MILLIS,
            FastPollHub.intervalMillis(hub.watchers.value),
        )
    }

    @Test
    fun `nested attaches need as many detaches`() {
        val hub = FastPollHub()
        hub.attach()
        hub.attach()
        hub.detach()
        assertEquals(1, hub.watchers.value)
        assertEquals(
            FastPollHub.FAST_INTERVAL_MILLIS,
            FastPollHub.intervalMillis(hub.watchers.value),
        )
        hub.detach()
        assertEquals(
            FastPollHub.NORMAL_INTERVAL_MILLIS,
            FastPollHub.intervalMillis(hub.watchers.value),
        )
    }

    @Test
    fun `an unbalanced detach cannot drive the count negative`() {
        val hub = FastPollHub()
        hub.detach()
        hub.detach()
        assertEquals(0, hub.watchers.value)
        hub.attach()
        assertEquals(
            FastPollHub.FAST_INTERVAL_MILLIS,
            FastPollHub.intervalMillis(hub.watchers.value),
        )
    }

    @Test
    fun `the fast period matches the device floor and no faster`() {
        // Быстрее пола опроса смысла нет: запись всё равно появляется раз в
        // секунду, а лишние запросы — только эфир и батарея.
        assertEquals(250L, FastPollHub.FAST_INTERVAL_MILLIS)
        assertEquals(
            app.radiacode.device.RadiaCodeDevice.MIN_POLL_INTERVAL_MILLIS,
            FastPollHub.FAST_INTERVAL_MILLIS,
        )
        assertEquals(1_000L, FastPollHub.NORMAL_INTERVAL_MILLIS)
    }
}
