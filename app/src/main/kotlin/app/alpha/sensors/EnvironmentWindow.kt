package app.alpha.sensors

import kotlin.math.sqrt

/**
 * Сводка условий за одно окно усреднения.
 *
 * Поля независимы: в телефоне может не быть барометра, а магнитометр может
 * молчать, пока экран выключен. Null означает «датчик ничего не дал», и это
 * не то же самое, что ноль.
 */
data class EnvironmentWindow(
    /** Конец окна, epoch millis. */
    val endMillis: Long,
    val pressureHpa: Float? = null,
    /** Среднее модуля |B| за окно, мкТл. */
    val magneticUt: Float? = null,
    /** Разброс модуля за окно (SD), мкТл; null при единственном отсчёте. */
    val magneticSd: Float? = null,
    val phoneTempC: Float? = null,
    /** Число усреднённых отсчётов магнитометра и барометра суммарно. */
    val samples: Int = 0,
) {
    val isEmpty: Boolean
        get() = pressureHpa == null && magneticUt == null && phoneTempC == null
}

/**
 * Накопитель отсчётов датчиков в сводку за окно.
 *
 * Зачем окно вообще: магнитометр отдаёт десятки отсчётов в секунду, и писать
 * их потоком — гигабайты ради данных, которые всё равно читают усреднёнными.
 * Окно [windowMillis] задаёт шаг ряда; он выбран согласованно с остальными
 * рядами приложения — доза идёт раз в секунду, прибор шлёт свою температуру
 * примерно раз в минуту, и десять секунд ложатся между ними, не создавая
 * ложной подробности.
 *
 * Класс ЧИСТЫЙ: время приходит аргументом, датчики — вызовами [addMagnetic] и
 * прочих. Android-часть живёт в [PhoneSensors] и только кормит его.
 */
class EnvironmentAggregator(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {

    private var startMillis: Long? = null

    private var magneticSum = 0.0
    private var magneticSquares = 0.0
    private var magneticCount = 0

    private var pressureSum = 0.0
    private var pressureCount = 0

    private var phoneTempC: Float? = null

    /** Модуль вектора: он не зависит от того, как повёрнут телефон. */
    fun addMagnetic(x: Float, y: Float, z: Float, atMillis: Long) {
        open(atMillis)
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        magneticSum += magnitude
        magneticSquares += magnitude * magnitude
        magneticCount++
    }

    fun addPressure(hpa: Float, atMillis: Long) {
        open(atMillis)
        pressureSum += hpa
        pressureCount++
    }

    /** Температура батареи приходит событием системы, а не потоком. */
    fun setPhoneTemperature(celsius: Float, atMillis: Long) {
        open(atMillis)
        phoneTempC = celsius
    }

    /**
     * Закрывает окно, если оно кончилось, и возвращает сводку.
     *
     * @return null, пока окно не кончилось или пока в нём нет ни одного
     *   значения — пустых строк в базе быть не должно.
     */
    fun poll(nowMillis: Long): EnvironmentWindow? {
        val start = startMillis ?: return null
        if (nowMillis - start < windowMillis) return null
        return close(nowMillis)
    }

    /** Принудительно закрыть окно: служба останавливается, хвост не теряем. */
    fun flush(nowMillis: Long): EnvironmentWindow? {
        startMillis ?: return null
        return close(nowMillis)
    }

    private fun open(atMillis: Long) {
        if (startMillis == null) startMillis = atMillis
    }

    private fun close(nowMillis: Long): EnvironmentWindow? {
        val magnetic = if (magneticCount > 0) (magneticSum / magneticCount).toFloat() else null
        // SD по несмещённой оценке; при одном отсчёте разброса не существует,
        // и ноль здесь соврал бы про устойчивость поля.
        val sd = if (magneticCount > 1) {
            val mean = magneticSum / magneticCount
            val variance = (magneticSquares / magneticCount - mean * mean)
                .coerceAtLeast(0.0) * magneticCount / (magneticCount - 1)
            sqrt(variance).toFloat()
        } else {
            null
        }
        val pressure = if (pressureCount > 0) (pressureSum / pressureCount).toFloat() else null
        val window = EnvironmentWindow(
            endMillis = nowMillis,
            pressureHpa = pressure,
            magneticUt = magnetic,
            magneticSd = sd,
            phoneTempC = phoneTempC,
            samples = magneticCount + pressureCount,
        )
        reset()
        return if (window.isEmpty) null else window
    }

    private fun reset() {
        startMillis = null
        magneticSum = 0.0
        magneticSquares = 0.0
        magneticCount = 0
        pressureSum = 0.0
        pressureCount = 0
        // Температура батареи держится последним известным значением: система
        // присылает её редко, и обнулять её между окнами значило бы терять
        // единственное, что о ней известно.
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 10_000L
    }
}
