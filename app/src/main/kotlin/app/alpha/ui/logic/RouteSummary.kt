package app.alpha.ui.logic

import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings

/**
 * Маршрут как запись журнала: что о нём известно, не открывая его.
 *
 * Это отдельная сущность, а не строка сессии измерения: сессия отвечает «что
 * прибор намерил за это время», маршрут — «где я прошёл и как менялся уровень
 * по дороге». Пересекаться во времени они могут как угодно, поэтому и живут
 * рядом, а не внутри друг друга.
 *
 * Числа считаются в базе (`TrackDao.routeSummary`), сюда приходят уже
 * посчитанными; здесь — только то, что из них следует, и это проверяется
 * тестами без Android.
 */
data class RouteSummary(
    val id: Long,
    /** Пустое — имя не задано, подписывать нужно датой. */
    val name: String,
    val startedAt: Long,
    /** Null — запись ещё идёт. */
    val endedAt: Long?,
    /** Запись оборвалась (сбой, выключение), а не была остановлена. */
    val interrupted: Boolean = false,
    /** Прибор, которым записан маршрут; null — пометки нет. */
    val deviceSerial: String? = null,
    val distanceMeters: Double?,
    val measurementCount: Int,
    val avgDoseMicroSvH: Float?,
    val maxDoseMicroSvH: Float?,
    /**
     * Доза за маршрут, мкЗв: интеграл по измерениям промежутка. Null у идущей
     * записи (делить незаконченное не на что) и там, где измерений не было.
     */
    val doseMicroSv: Double? = null,
) {
    val running: Boolean get() = endedAt == null

    val durationSeconds: Long
        get() = ((endedAt ?: startedAt) - startedAt).coerceAtLeast(0L) / 1000

}

/** Что показывает журнал: всё вместе или один вид записей. */
/**
 * Вкладки Истории. `EVENTS` показывает журнал целиком — включая точечные
 * записи прежних версий, которых нет в общей ленте: данные измерений не
 * удаляются ради чистой ленты, но и не засоряют её
 * (`history_semantic_events_redesign.md`).
 */
enum class HistoryFilter { ALL, SESSIONS, ROUTES, SPECTRA, FOOD, EVENTS }

object RouteFormat {

    /**
     * Как маршрут подписан в списке: имя, если оно есть, иначе «Маршрут ·
     * 18:51» — время начала. День не входит: он стоит заголовком группы.
     */
    fun title(
        route: RouteSummary,
        nowMillis: Long,
        s: HistoryStrings = HistoryRu,
    ): String = route.name.trim().ifEmpty {
        s.routeAuto(HistoryFormat.timeOfDay(route.startedAt))
    }

    /** Пустое имя означает «имени нет», а не имя из пробелов. */
    fun cleanName(input: String): String = input.trim().take(MAX_NAME_LENGTH)

    const val MAX_NAME_LENGTH = 60
}

/**
 * Миниатюра маршрута: его форма, вписанная в квадрат со стороной 1.
 *
 * Считается здесь, а не в отрисовке: форма не зависит от размера картинки и
 * проверяется числами. Долгота сжимается по широте (cos φ), иначе средняя
 * полоса вытягивает маршрут поперёк.
 *
 * Пропуски координат не разрываются: миниатюра — не карта, по ней не
 * измеряют. Настоящий след с разрывами виден в самом маршруте.
 */
/** Точка миниатюры: где прошли и сколько там было. */
data class RouteShapePoint(
    val latitude: Double,
    val longitude: Double,
    val doseMicroSvH: Float?,
)

/** Точка миниатюры в координатах картинки: 0..1 по обеим осям. */
data class ThumbnailPoint(val x: Float, val y: Float, val value: Float?)

object RouteShape {

    /** Сколько точек берётся на миниатюру: больше не делает её вернее. */
    const val THUMBNAIL_POINTS = 120

    /** Через сколько строк брать точку, чтобы получилось около [THUMBNAIL_POINTS]. */
    fun stride(pointCount: Int, target: Int = THUMBNAIL_POINTS): Int =
        if (pointCount <= target) 1 else (pointCount + target - 1) / target

    /**
     * Точки в координатах картинки: x вправо, y вниз, обе в 0..1.
     * Маршрут вписывается целиком и по центру, пропорции сохраняются.
     */
    fun normalize(points: List<RouteShapePoint>): List<ThumbnailPoint> {
        if (points.isEmpty()) return emptyList()
        val latitudes = points.map { it.latitude }
        val longitudes = points.map { it.longitude }
        val minLat = latitudes.min()
        val maxLat = latitudes.max()
        val minLon = longitudes.min()
        val maxLon = longitudes.max()
        val midLat = (minLat + maxLat) / 2
        val lonScale = Math.cos(Math.toRadians(midLat)).coerceAtLeast(0.01)
        val width = (maxLon - minLon) * lonScale
        val height = maxLat - minLat
        val span = maxOf(width, height)
        // Маршрут «на месте»: разброса нет, точка в середине вместо шума,
        // растянутого во весь квадрат.
        if (span <= 0.0) {
            return points.map { ThumbnailPoint(0.5f, 0.5f, it.doseMicroSvH) }
        }
        val offsetX = (span - width) / 2
        val offsetY = (span - height) / 2
        return points.map { point ->
            val x = ((point.longitude - minLon) * lonScale + offsetX) / span
            // Широта растёт на север, экранный y — вниз.
            val y = 1.0 - ((point.latitude - minLat) + offsetY) / span
            ThumbnailPoint(
                x = x.toFloat().coerceIn(0f, 1f),
                y = y.toFloat().coerceIn(0f, 1f),
                value = point.doseMicroSvH,
            )
        }
    }
}
