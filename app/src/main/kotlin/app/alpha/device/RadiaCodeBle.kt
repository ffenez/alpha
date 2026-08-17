package app.alpha.device

import com.juul.kable.Characteristic
import com.juul.kable.Filter
import com.juul.kable.Scanner
import com.juul.kable.characteristicOf
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * RadiaCode GATT profile (reverse-engineered, cdump/radiacode):
 * one service, write characteristic ...e6, notify characteristic ...e7.
 */
object RadiaCodeBle {
    val SERVICE_UUID: Uuid = Uuid.parse("e63215e5-7003-49d8-96b0-b024798fb901")
    private val WRITE_UUID: Uuid = Uuid.parse("e63215e6-7003-49d8-96b0-b024798fb901")
    private val NOTIFY_UUID: Uuid = Uuid.parse("e63215e7-7003-49d8-96b0-b024798fb901")

    val WRITE_CHARACTERISTIC: Characteristic = characteristicOf(SERVICE_UUID, WRITE_UUID)
    val NOTIFY_CHARACTERISTIC: Characteristic = characteristicOf(SERVICE_UUID, NOTIFY_UUID)

    const val NAME_PREFIX = "RadiaCode"
}

/** A RadiaCode device seen during scanning. */
data class DiscoveredRadiaCode(
    val address: String,
    val name: String?,
    val rssi: Int,
)

/**
 * BLE scan filtered by the RadiaCode service UUID, with an advertised-name
 * prefix fallback for advertisements that omit the service UUID.
 * Collection requires BLUETOOTH_SCAN (API 31+) / location (API <= 30) permission.
 */
class RadiaCodeScanner {
    fun scan(): Flow<DiscoveredRadiaCode> = Scanner {
        filters {
            match { services = listOf(RadiaCodeBle.SERVICE_UUID) }
            match { name = Filter.Name.Prefix(RadiaCodeBle.NAME_PREFIX) }
        }
    }.advertisements.map { adv ->
        DiscoveredRadiaCode(address = adv.address, name = adv.name, rssi = adv.rssi)
    }
}
