package app.alpha.device

import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/*
 * BLE transport decision (see ADR 001): Kable (com.juul.kable:kable-core,
 * Apache-2.0) is kept as the GATT layer because its Android implementation
 * already provides exactly the semantics this protocol needs:
 *  - write(characteristic, data, WriteType.WithResponse) suspends until the
 *    peer's GATT ack (onCharacteristicWrite), which gives us strict
 *    ack-sequenced 18-byte chunk writes with no extra machinery;
 *  - all GATT operations go through an internal sequential queue, so no two
 *    operations ever overlap;
 *  - observe() enables the CCCD on first collection and reports readiness via
 *    an onSubscription callback (used for the 500 ms settle delay).
 * A raw-GATT fallback wrapper is not needed; if it ever becomes necessary,
 * only this file and RadiaCodeBle.kt would change (DeviceLink stays).
 */

/**
 * Opens Kable-backed links: connect by MAC address, request a large MTU,
 * subscribe to notifications and wait [settleMillis] (field experience:
 * requests sent immediately after CCCD enable are lost).
 */
class KableLinkFactory(
    private val connectTimeoutMillis: Long = 30_000,
    private val subscribeTimeoutMillis: Long = 10_000,
    private val settleMillis: Long = 500,
    private val requestedMtu: Int = 512,
) : DeviceLinkFactory {

    override suspend fun open(address: String): DeviceLink {
        val peripheral = Peripheral(address) {
            onServicesDiscovered { requestMtu(requestedMtu) }
        }
        try {
            withTimeout(connectTimeoutMillis) { peripheral.connect() }

            val notifications = MutableSharedFlow<ByteArray>(
                extraBufferCapacity = 4096,
                onBufferOverflow = BufferOverflow.SUSPEND,
            )
            val subscribed = CompletableDeferred<Unit>()
            val pump = peripheral.scope.launch {
                peripheral.observe(RadiaCodeBle.NOTIFY_CHARACTERISTIC) { subscribed.complete(Unit) }
                    .collect { notifications.emit(it) }
            }
            pump.invokeOnCompletion { cause ->
                subscribed.completeExceptionally(cause ?: IOException("Notification stream ended"))
            }
            withTimeout(subscribeTimeoutMillis) { subscribed.await() }
            delay(settleMillis)

            return KableDeviceLink(peripheral, notifications)
        } catch (t: Throwable) {
            runCatching { peripheral.close() }
            throw t
        }
    }
}

private class KableDeviceLink(
    private val peripheral: Peripheral,
    override val notifications: Flow<ByteArray>,
) : DeviceLink {

    override suspend fun write(chunk: ByteArray) {
        peripheral.write(RadiaCodeBle.WRITE_CHARACTERISTIC, chunk, WriteType.WithResponse)
    }

    override suspend fun awaitDisconnect() {
        peripheral.state.first { it is State.Disconnected }
    }

    override suspend fun close() {
        runCatching { peripheral.disconnect() }
        peripheral.close()
    }
}
