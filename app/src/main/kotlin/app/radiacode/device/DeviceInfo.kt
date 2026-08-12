package app.radiacode.device

/** Static device identity captured during the init sequence. */
data class DeviceInfo(
    val address: String,
    val serialNumber: String,
    val firmware: FwVersion,
    val spectrumFormatVersion: Int,
) {
    /**
     * Модель прибора — по серийному номеру. Протокол у серии общий, поэтому
     * подключение от модели не зависит; зависит ОБРАБОТКА (разрешение
     * детектора, границы шкалы, наличие спектрометрии).
     */
    val model: DeviceModel get() = DeviceModel.fromSerial(serialNumber)
}

/** Connection lifecycle as observed by consumers (service, later UI). */
sealed interface ConnectionState {
    /** Not started or explicitly stopped. */
    data object Disconnected : ConnectionState

    /** Connection attempt (including the protocol init sequence) in progress. */
    data class Connecting(val attempt: Int) : ConnectionState

    data class Connected(val info: DeviceInfo) : ConnectionState

    /** Link lost; next attempt after [delayMillis]. */
    data class Reconnecting(val attempt: Int, val delayMillis: Long) : ConnectionState
}
