package app.radiacode.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.radiacode.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Продолжение измерения после перезагрузки телефона.
 *
 * ## Почему это выключено по умолчанию
 *
 * Приложение, которое само поднимает службу и открывает Bluetooth-обмен после
 * каждой перезагрузки, делает это без спроса и не в тот момент, когда человек
 * об этом думает. Поэтому автозапуск — ЯВНАЯ настройка: кому нужен непрерывный
 * мониторинг, включает сам и знает, что включил.
 *
 * ## Условия
 *
 * Запуск происходит, только если (1) настройка включена И (2) есть прибор,
 * который человек уже выбирал: поднимать службу, которой не с чем работать,
 * значит показать уведомление ни о чём. Прав на сканирование здесь не
 * запрашивается — их выдают на экране, а не в приёмнике.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val graph = AppGraph.get(context)
        // Приёмник живёт считанные миллисекунды, и корутине здесь негде
        // пережить возврат из onReceive: чтение двух ключей DataStore — то
        // немногое, ради чего блокировка оправдана.
        val start = runBlocking {
            graph.settings.startOnBoot.first() && graph.settings.lastDeviceAddress.first() != null
        }
        if (!start) return
        ContextCompat.startForegroundService(context, MeasurementService.resumeIntent(context))
    }
}
