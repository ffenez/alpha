package app.alpha.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager

/**
 * Датчики телефона рядом с измерением: барометр, магнитометр и температура
 * батареи.
 *
 * Разрешений не требует ни один из трёх — это важное свойство, а не мелочь:
 * приложение обещает, что новых прав не просит.
 *
 * Поток датчиков слушается ТОЛЬКО пока идёт измерение: подписка снимается
 * вместе со службой, иначе магнитометр тянет батарею круглые сутки ради
 * данных, которые некуда писать. Частота подписки — `SENSOR_DELAY_NORMAL`
 * (около 5 Гц): выше не нужно, всё равно усредняем окном.
 *
 * Отсчёты складывает [EnvironmentAggregator]; здесь только Android.
 */
class PhoneSensors(
    context: Context,
    windowMillis: Long = EnvironmentAggregator.DEFAULT_WINDOW_MILLIS,
) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(SensorManager::class.java)

    private val pressure: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val magnetic: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val aggregator = EnvironmentAggregator(windowMillis)
    private val lock = Any()

    /** Что в этом телефоне вообще есть — для честной строки в настройках. */
    val hasPressure: Boolean get() = pressure != null
    val hasMagnetic: Boolean get() = magnetic != null

    /**
     * Датчик температуры ВОЗДУХА (`TYPE_AMBIENT_TEMPERATURE`) в телефонах
     * почти не встречается; проверяем честно, а не считаем, что его нет.
     */
    val hasAmbientTemperature: Boolean =
        manager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Время берём системное, а не event.timestamp: тот считается от
            // загрузки телефона, а весь остальной ряд живёт в epoch millis.
            val now = System.currentTimeMillis()
            synchronized(lock) {
                when (event.sensor.type) {
                    Sensor.TYPE_PRESSURE -> aggregator.addPressure(event.values[0], now)
                    Sensor.TYPE_MAGNETIC_FIELD -> aggregator.addMagnetic(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                        now,
                    )
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var started = false

    fun start() {
        if (started || manager == null) return
        started = true
        pressure?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magnetic?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    /** Снимает подписку и отдаёт незакрытый хвост, чтобы он не пропал. */
    fun stop(nowMillis: Long = System.currentTimeMillis()): EnvironmentWindow? {
        if (!started) return null
        started = false
        manager?.unregisterListener(listener)
        return synchronized(lock) {
            batteryTemperatureC()?.let { aggregator.setPhoneTemperature(it, nowMillis) }
            aggregator.flush(nowMillis)
        }
    }

    /**
     * Вызывается тактом службы; возвращает сводку, когда окно кончилось.
     * Температура батареи спрашивается здесь же — она приходит событием
     * системы, отдельная подписка ради неё не нужна.
     */
    fun poll(nowMillis: Long = System.currentTimeMillis()): EnvironmentWindow? =
        synchronized(lock) {
            batteryTemperatureC()?.let { aggregator.setPhoneTemperature(it, nowMillis) }
            aggregator.poll(nowMillis)
        }

    /**
     * Температура БАТАРЕИ, °C. `ACTION_BATTERY_CHANGED` — липкое намерение:
     * значение отдаётся сразу, подписываться не требуется. Система хранит его
     * в десятых долях градуса.
     */
    private fun batteryTemperatureC(): Float? {
        val intent: Intent? = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, NO_VALUE) ?: NO_VALUE
        return if (tenths == NO_VALUE) null else tenths / 10f
    }

    private companion object {
        const val NO_VALUE = Int.MIN_VALUE
    }
}
