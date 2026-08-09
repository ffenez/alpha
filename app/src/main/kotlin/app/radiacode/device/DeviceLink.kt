package app.radiacode.device

import kotlinx.coroutines.flow.Flow

/**
 * One live BLE connection to a RadiaCode device, reduced to what the protocol
 * layer needs. Implementations are Android-specific ([KableDeviceLink]); tests
 * use in-memory fakes.
 */
interface DeviceLink {

    /** Raw notification payloads from the notify characteristic, in arrival order. */
    val notifications: Flow<ByteArray>

    /**
     * Writes one request chunk (at most [ProtocolClient.CHUNK_SIZE] bytes) to the
     * write characteristic using write-with-response; suspends until the peer acks.
     */
    suspend fun write(chunk: ByteArray)

    /** Suspends until the underlying connection is lost. */
    suspend fun awaitDisconnect()

    /** Releases the connection; safe to call multiple times. */
    suspend fun close()
}

/** Opens [DeviceLink]s to a device by BLE MAC address. */
interface DeviceLinkFactory {
    /**
     * Connects, enables notifications and waits for the link to settle.
     * Throws on failure; the caller owns the returned link and must [DeviceLink.close] it.
     */
    suspend fun open(address: String): DeviceLink
}

/** Thrown when an operation requires a connected device but none is available. */
class DeviceNotConnectedException(message: String = "Device is not connected") : java.io.IOException(message)
