package app.alpha.ui.logic

/**
 * Режим полёта: автоматическая пометка сессий, записанных на эшелоне.
 * Полёт = устойчиво (не менее [SUSTAIN_MILLIS] подряд) GPS-высота выше
 * [MIN_ALTITUDE_METERS] — короткий выброс высотомера полётом не считается,
 * а разрыв фиксов рвёт непрерывность. Порог 3000 м лежит выше жилой Европы
 * и почти всех дорог, но глубоко ниже эшелонов (9–12 км).
 *
 * Сравнение «на эшелоне фон ×N»: медиана мощности дозы высотных точек к
 * медиане наземных точек той же записи — обе стороны из одного прибора,
 * никакой внешней нормировки. Pure JVM, tested.
 */
object FlightDetect {

    const val MIN_ALTITUDE_METERS = 3000.0

    /** Minimum continuous high-altitude stretch that counts as a flight. */
    const val SUSTAIN_MILLIS = 120_000L

    /** A gap between consecutive fixes longer than this breaks continuity. */
    const val MAX_FIX_GAP_MILLIS = 60_000L

    /** One track point as the flight math sees it. */
    data class Point(
        val timestampMillis: Long,
        val altitudeMeters: Double?,
        /** Dose rate at the point, µSv/h; null when the stream was silent. */
        val doseMicroSvH: Float?,
    )

    /**
     * True when the points contain a continuous ≥ [SUSTAIN_MILLIS] stretch
     * above [minAltitudeMeters]: every point of the stretch has a known high
     * altitude and consecutive fixes are ≤ [MAX_FIX_GAP_MILLIS] apart.
     */
    fun sustainedFlight(
        points: List<Point>,
        minAltitudeMeters: Double = MIN_ALTITUDE_METERS,
        sustainMillis: Long = SUSTAIN_MILLIS,
    ): Boolean {
        var runStart = -1L
        var lastHighMillis = -1L
        for (point in points.sortedBy { it.timestampMillis }) {
            val high = (point.altitudeMeters ?: -1.0) > minAltitudeMeters
            if (!high) {
                runStart = -1L
                lastHighMillis = -1L
                continue
            }
            if (runStart < 0 || point.timestampMillis - lastHighMillis > MAX_FIX_GAP_MILLIS) {
                runStart = point.timestampMillis
            }
            lastHighMillis = point.timestampMillis
            if (lastHighMillis - runStart >= sustainMillis) return true
        }
        return false
    }

    /** Flight comparison numbers; nulls where the data honestly ends. */
    data class Summary(
        /** Median dose rate of high-altitude points, µSv/h. */
        val flightMedianMicroSvH: Float?,
        /** Median dose rate of ground points of the same record, µSv/h. */
        val groundMedianMicroSvH: Float?,
        /** flightMedian / groundMedian; null without both sides. */
        val factor: Float?,
    )

    /**
     * «На эшелоне фон ×N от вашего наземного медианного»: medians of the
     * dose rates above/below the altitude threshold. Points without altitude
     * or dose are excluded from both sides — no guessing.
     */
    fun summary(
        points: List<Point>,
        minAltitudeMeters: Double = MIN_ALTITUDE_METERS,
    ): Summary {
        val flight = mutableListOf<Float>()
        val ground = mutableListOf<Float>()
        for (point in points) {
            val altitude = point.altitudeMeters ?: continue
            val dose = point.doseMicroSvH ?: continue
            if (altitude > minAltitudeMeters) flight += dose else ground += dose
        }
        val flightMedian = median(flight)
        val groundMedian = median(ground)
        val factor = if (flightMedian != null && groundMedian != null && groundMedian > 0f) {
            flightMedian / groundMedian
        } else {
            null
        }
        return Summary(flightMedian, groundMedian, factor)
    }

    /**
     * Mean altitude per chart column on the same bucket grid as the session
     * dose chart — the two stacked charts share the time axis exactly.
     * Slots without altitude fixes stay null (gaps, not interpolation).
     */
    fun altitudeColumns(
        points: List<Point>,
        alignedFromMillis: Long,
        bucketMillis: Long,
        columnCount: Int,
    ): List<Float?> {
        val sums = DoubleArray(columnCount)
        val counts = IntArray(columnCount)
        for (point in points) {
            val altitude = point.altitudeMeters ?: continue
            val index = ((point.timestampMillis - alignedFromMillis) / bucketMillis).toInt()
            if (index in 0 until columnCount) {
                sums[index] += altitude
                counts[index]++
            }
        }
        return List(columnCount) { i ->
            if (counts[i] > 0) (sums[i] / counts[i]).toFloat() else null
        }
    }

    private fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }
}
