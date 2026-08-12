package app.radiacode.ui.logic

import app.radiacode.data.db.DownsampledSample
import app.radiacode.device.DoseUnits
import java.time.Instant
import java.time.ZoneId

/**
 * Доза за календарный день и то, СКОЛЬКО этого дня реально измерено.
 *
 * Доза копится по измеренным секундам ([DownsampledSample.sampleCount] при 1
 * Гц это и есть секунды), поэтому день, в который прибор был включён два
 * часа, даёт столбик примерно в двенадцать раз ниже полного — и на картинке
 * он неотличим от дня с низким уровнем. Это ровно та двусмысленность, из-за
 * которой на графике дозы штрихуются пропуски, поэтому покрытие дня едет
 * вместе с его дозой, а неполные дни рисуются иначе.
 *
 * Pure JVM, tested.
 */
object DailyDose {

    /** Один день: накопленная доза и измеренное время. */
    data class Day(val microSv: Float, val measuredSeconds: Long) {
        /** Доля суток, покрытая измерениями, 0..1. */
        val coverage: Float get() = (measuredSeconds / 86_400f).coerceIn(0f, 1f)

        /**
         * День считается полным при покрытии ≥ [FULL_COVERAGE]. **Инженерный
         * параметр**: BLE-разрывы в несколько минут не делают сутки неполными,
         * а вот пропуск в пару часов уже меняет смысл столбика.
         */
        val full: Boolean get() = coverage >= FULL_COVERAGE
    }

    const val FULL_COVERAGE = 0.9f

    /**
     * µSv per day for the [days] local days ending at [nowMillis]'s day,
     * oldest first; days without measurements are 0.
     */
    fun perDay(
        buckets: List<DownsampledSample>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        days: Int = 30,
    ): List<Day> {
        // Instant.atZone().toLocalDate() — Java 8 time API, available from minSdk 26
        // (LocalDate.ofInstant needs Android API 34).
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val firstDay = today.minusDays((days - 1).toLong())
        val result = FloatArray(days)
        val measured = LongArray(days)
        for (bucket in buckets) {
            val day = Instant.ofEpochMilli(bucket.bucketStart).atZone(zone).toLocalDate()
            val index = (day.toEpochDay() - firstDay.toEpochDay()).toInt()
            if (index !in 0 until days) continue
            result[index] += (
                DoseUnits.rawToMicroSievertPerHour(bucket.avgDoseRate).toDouble() *
                    bucket.sampleCount / 3600.0
                ).toFloat()
            // Прибор пишет раз в секунду, поэтому число отсчётов — это и есть
            // измеренные секунды дня.
            measured[index] += bucket.sampleCount.toLong()
        }
        return (0 until days).map { Day(result[it], measured[it]) }
    }
}
