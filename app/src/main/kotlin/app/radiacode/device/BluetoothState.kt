package app.radiacode.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * Включён ли Bluetooth — один ответ на всё приложение.
 *
 * Отдельно от подключения к прибору: «прибор не отвечает» и «радиомодуль
 * выключен» — разные беды с разными действиями, и вторую видно ещё до того,
 * как приложение попробует соединиться.
 */
object BluetoothState {

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * Нет адаптера — считаем включённым: на устройстве без Bluetooth
     * предупреждение «включите Bluetooth» указывало бы на кнопку, которой
     * там нет.
     */
    fun isEnabled(context: Context): Boolean = adapter(context)?.isEnabled ?: true
}
