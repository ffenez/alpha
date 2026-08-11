package app.radiacode.data.db

/**
 * SQL of the accumulated map, kept as a constant so a JVM test can replay the
 * *same* statement on a real SQLite database (see `TrackGridSqlTest`) and prove
 * that the cell keys SQLite computes are exactly the ones
 * [app.radiacode.ui.logic.TrackGrid] computes in Kotlin. A copy of the query
 * inside a test would only prove that the copy works.
 *
 * `CAST(… AS INTEGER)` truncates towards zero rather than flooring, which is
 * why coordinates are shifted into positive space (+90 / +180) before the
 * division: there truncation and floor agree. SQLite below API 31 has no
 * `floor()`, so this is not stylistic.
 */
object TrackGridSql {

    const val GRID_HISTOGRAM = """
        SELECT CAST((latitude + 90.0) / :latStepDeg AS INTEGER) AS latKey,
               CAST((longitude + 180.0) / :lonStepDeg AS INTEGER) AS lonKey,
               CAST(((CASE WHEN :useDose THEN doseRate ELSE countRate END) - :valueMin)
                    / :valueStep AS INTEGER) AS valueKey,
               COUNT(*) AS pointCount,
               MIN(CASE WHEN :useDose THEN doseRate ELSE countRate END) AS minValue,
               MAX(CASE WHEN :useDose THEN doseRate ELSE countRate END) AS maxValue,
               MIN(timestamp) AS minTime,
               MAX(timestamp) AS maxTime
        FROM track_points
        WHERE latitude BETWEEN :minLatitude AND :maxLatitude
          AND longitude BETWEEN :minLongitude AND :maxLongitude
          AND accuracyMeters <= :maxAccuracyMeters
          AND (CASE WHEN :useDose THEN doseRate ELSE countRate END) IS NOT NULL
        GROUP BY latKey, lonKey, valueKey
        LIMIT :limit
    """

    const val AREA_SUMMARY = """
        SELECT COUNT(*) AS pointCount,
               COUNT(CASE WHEN :useDose THEN doseRate ELSE countRate END) AS valueCount,
               MIN(CASE WHEN :useDose THEN doseRate ELSE countRate END) AS minValue,
               MAX(CASE WHEN :useDose THEN doseRate ELSE countRate END) AS maxValue,
               MIN(timestamp) AS firstTime,
               MAX(timestamp) AS lastTime
        FROM track_points
        WHERE latitude BETWEEN :minLatitude AND :maxLatitude
          AND longitude BETWEEN :minLongitude AND :maxLongitude
          AND accuracyMeters <= :maxAccuracyMeters
    """
}
