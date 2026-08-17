package app.alpha.device

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeLinkFactory(private val fake: FakeRadiaCode) : DeviceLinkFactory {
    val links = mutableListOf<FakeDeviceLink>()
    var failNextOpens = 0

    override suspend fun open(address: String): DeviceLink {
        if (failNextOpens > 0) {
            failNextOpens -= 1
            throw java.io.IOException("connect failed")
        }
        return FakeDeviceLink(fake).also { links += it }
    }
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
class RadiaCodeDeviceTest {

    @Test
    fun `connects, exposes info and streams samples from 1 Hz polls`() = runTest {
        val fake = FakeRadiaCode()
        fake.dataBufPayloads += realTimeDataRecord(seq = 0, tsOffset10ms = 0, countRate = 9f, doseRate = 0.0005f)
        val factory = FakeLinkFactory(fake)
        val device = RadiaCodeDevice("AA:BB", factory, clock = { 1_000_000L + testScheduler.currentTime })

        device.start(backgroundScope)

        val sample = device.realTimeData.first()
        assertEquals(9f, sample.countRate)
        val state = device.connectionState.value
        assertIs<ConnectionState.Connected>(state)
        assertEquals("RC-110-001234", state.info.serialNumber)
        assertEquals("RC-110-001234", device.deviceInfo?.serialNumber)
    }

    @Test
    fun `reconnects after link loss and resets backoff on success`() = runTest {
        val fake = FakeRadiaCode()
        val factory = FakeLinkFactory(fake)
        val device = RadiaCodeDevice("AA:BB", factory)

        device.start(backgroundScope)
        device.connectionState.first { it is ConnectionState.Connected }

        factory.links[0].dropLink()
        val reconnecting = device.connectionState.first { it is ConnectionState.Reconnecting }
        assertEquals(2_000, (reconnecting as ConnectionState.Reconnecting).delayMillis)

        device.connectionState.first { it is ConnectionState.Connected }
        assertEquals(2, factory.links.size)
        assertTrue(factory.links[0].closed)

        // Successful connection reset the backoff: next failure waits 2 s again.
        factory.links[1].dropLink()
        val again = device.connectionState.first { it is ConnectionState.Reconnecting }
        assertEquals(2_000, (again as ConnectionState.Reconnecting).delayMillis)
    }

    @Test
    fun `a busy instrument does not cost the session`() = runTest {
        // Полевой дефект: нажатие кнопок на самом приборе обрывало связь в
        // приложении. Прибор занят своим экраном и не отвечает на один-два
        // опроса — это осечка чтения, а не потеря связи; разрыв сессии стоил
        // бы паузы переподключения и дыры в записи.
        val fake = FakeRadiaCode()
        val factory = FakeLinkFactory(fake)
        val device = RadiaCodeDevice("AA:BB", factory, clock = { 1_000_000L + testScheduler.currentTime })

        device.start(backgroundScope)
        device.connectionState.first { it is ConnectionState.Connected }

        factory.links[0].swallowNextResponses = RadiaCodeDevice.MAX_CONSECUTIVE_READ_FAILURES - 1
        fake.dataBufPayloads += realTimeDataRecord(
            seq = 0, tsOffset10ms = 0, countRate = 9f, doseRate = 0.0005f,
        )

        // Поток возобновляется сам, на том же линке.
        assertEquals(9f, device.realTimeData.first().countRate)
        assertIs<ConnectionState.Connected>(device.connectionState.value)
        assertEquals(1, factory.links.size)
        assertEquals(RadiaCodeDevice.MAX_CONSECUTIVE_READ_FAILURES - 1, device.readFailures)
    }

    @Test
    fun `an instrument that stopped answering gets a fresh session`() = runTest {
        val fake = FakeRadiaCode()
        val factory = FakeLinkFactory(fake)
        val device = RadiaCodeDevice("AA:BB", factory)

        device.start(backgroundScope)
        device.connectionState.first { it is ConnectionState.Connected }

        factory.links[0].swallowNextResponses = RadiaCodeDevice.MAX_CONSECUTIVE_READ_FAILURES
        device.connectionState.first { it is ConnectionState.Reconnecting }
        device.connectionState.first { it is ConnectionState.Connected }
        assertEquals(2, factory.links.size)
    }

    @Test
    fun `backoff grows while connection attempts keep failing`() = runTest {
        val fake = FakeRadiaCode()
        val factory = FakeLinkFactory(fake)
        factory.failNextOpens = 3
        val device = RadiaCodeDevice("AA:BB", factory)

        // Unconfined collector records every state transition synchronously.
        val states = mutableListOf<ConnectionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            device.connectionState.collect { states += it }
        }

        device.start(backgroundScope)
        device.connectionState.first { it is ConnectionState.Connected }

        val delays = states.filterIsInstance<ConnectionState.Reconnecting>().map { it.delayMillis }
        assertEquals(listOf(2_000L, 4_000L, 8_000L), delays)
        val attempts = states.filterIsInstance<ConnectionState.Connecting>().map { it.attempt }
        assertEquals(listOf(1, 2, 3, 4), attempts)
    }

    @Test
    fun `spectrum operations require a connection`() = runTest {
        val fake = FakeRadiaCode()
        val device = RadiaCodeDevice("AA:BB", FakeLinkFactory(fake))
        assertFailsWith<DeviceNotConnectedException> { device.readSpectrum() }
    }

    @Test
    fun `stop returns to Disconnected`() = runTest {
        val fake = FakeRadiaCode()
        val factory = FakeLinkFactory(fake)
        val device = RadiaCodeDevice("AA:BB", factory)

        device.start(backgroundScope)
        device.connectionState.first { it is ConnectionState.Connected }
        device.stop()

        assertEquals(ConnectionState.Disconnected, device.connectionState.value)
        assertFailsWith<DeviceNotConnectedException> { device.resetDose() }
    }
    // --- adjustable poll cadence (Поиск asks for a shorter period) ---

    @Test
    fun `poll interval defaults to one second and can be shortened at runtime`() {
        val device = RadiaCodeDevice("AA:BB", FakeLinkFactory(FakeRadiaCode()))
        assertEquals(1_000L, device.pollIntervalMillis)
        device.pollIntervalMillis = 500L
        assertEquals(500L, device.pollIntervalMillis)
        device.pollIntervalMillis = 1_000L
        assertEquals(1_000L, device.pollIntervalMillis)
    }

    @Test
    fun `an absurd poll period is clamped to the floor, never zero`() {
        val device = RadiaCodeDevice("AA:BB", FakeLinkFactory(FakeRadiaCode()))
        device.pollIntervalMillis = 0L
        assertEquals(RadiaCodeDevice.MIN_POLL_INTERVAL_MILLIS, device.pollIntervalMillis)
        device.pollIntervalMillis = -5L
        assertEquals(RadiaCodeDevice.MIN_POLL_INTERVAL_MILLIS, device.pollIntervalMillis)
    }

    @Test
    fun `an empty DATA_BUF reply is a normal no-op, not a gap`() = runTest {
        // At a faster cadence than the device produces records, some polls
        // legitimately come back empty; that must not be treated as an error.
        val fake = FakeRadiaCode()
        val device = RadiaCodeDevice(
            "AA:BB",
            FakeLinkFactory(fake),
            pollIntervalMillis = 500L,
        )
        device.start(backgroundScope)
        testScheduler.advanceTimeBy(2_500)
        assertEquals(0, device.seqGapTotal)
        assertIs<ConnectionState.Connected>(device.connectionState.value)
    }
}
