package app.alpha.device

/** Static device identity captured during the init sequence. */
data class DeviceInfo(
    val address: String,
    val serialNumber: String,
    val firmware: FwVersion,
    val spectrumFormatVersion: Int,
    /**
     * Строки конфигурации прибора, отобранные по БЕЛОМУ СПИСКУ ключей.
     *
     * Конфигурация — текст от прибора, и в нём может стоять имя, которое
     * человек задал сам. В отчёт уходят только технические ключи, по которым
     * разбирают совместимость; всё остальное не покидает устройство.
     */
    val configurationLines: List<String> = emptyList(),
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

    /**
     * Связь есть.
     *
     * @param historyAgeMillis возраст новейшей записи прибора: пока он больше
     *   [DeviceConnection.SYNC_WINDOW_MILLIS], прибор отдаёт накопленное в
     *   своей памяти. Это часть СОСТОЯНИЯ связи, а не отдельный флаг рядом с
     *   ней: «связь есть, но данные ещё историчные» — это одно положение дел,
     *   и читатели не должны собирать его из двух источников.
     */
    data class Connected(
        val info: DeviceInfo,
        val historyAgeMillis: Long = 0L,
    ) : ConnectionState {
        /** Слив накопленного догнал живое время. */
        val live: Boolean get() = historyAgeMillis <= DeviceConnection.SYNC_WINDOW_MILLIS
    }

    /** Link lost; next attempt after [delayMillis]. */
    data class Reconnecting(val attempt: Int, val delayMillis: Long) : ConnectionState
}
