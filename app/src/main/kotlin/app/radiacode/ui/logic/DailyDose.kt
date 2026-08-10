package app.radiacode.ui.logic

import app.radiacode.data.db.DownsampledSample
import app.radiacode.device.DoseUnits
import java.time.Instant
import java.time.ZoneId

/**
 * Accumulated dose per local calendar day for the History mini-chart.
 * Buckets (≤ 1 h wide, so none straddles midnight by more than rounding)
 * integrate exactly like [ChartMapping.integrateDoseMicroSv]. Calculated
 * values — the UI labels them «расчёт». Pure JVM, tested.
 */
object DailyDose {

    /**
     * µSv per day for the [days] local days ending at [nowMillis]'s day,
     * oldest first; days without measurements are 0.
     */
    fun perDay(
        buckets: List<DownsampledSample>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        days: Int = 30,
    ): List<Float> {
        // Instant.atZone().toLocalDate() — Java 8 time API, available from minSdk 26
        // (LocalDate.ofInstant needs Android API 34).
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val firstDay = today.minusDays((days - 1).toLong())
        val result = FloatArray(days)
        for (bucket in buckets) {
            val day = Instant.ofEpochMilli(bucket.bucketStart).atZone(zone).toLocalDate()
            val index = (day.toEpochDay() - firstDay.toEpochDay()).toInt()
            if (index !in 0 until days) continue
            result[index] += (
                DoseUnits.rawToMicroSievertPerHour(bucket.avgDoseRate).toDouble() *
                    bucket.sampleCount / 3600.0
                ).toFloat()
        }
        return result.toList()
    }
}
