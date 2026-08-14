package app.radiacode.ui.logic

import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings

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
    /** Пустое — имени человек не давал, и подписывать нужно датой. */
    val name: String,
    val startedAt: Long,
    /** Null — запись ещё идёт. */
    val endedAt: Long?,
    /** Запись оборвалась (сбой, выключение), а не была остановлена. */
    val interrupted: Boolean = false,
    val distanceMeters: Double?,
    val measurementCount: Int,
    val avgDoseMicroSvH: Float?,
    val maxDoseMicroSvH: Float?,
) {
    val running: Boolean get() = endedAt == null

    val durationSeconds: Long
        get() = ((endedAt ?: startedAt) - startedAt).coerceAtLeast(0L) / 1000

    /**
     * Доза за маршрут, мкЗв: средняя мощность на длительность.
     *
     * Это ОЦЕНКА, а не интеграл по показаниям: точки следа приходят неровно, и
     * средняя по ним взвешена числом точек, а не временем. На ровной прогулке
     * разница мала, на прогулке с долгим пропуском координат — нет, поэтому
     * величина названа дозой за маршрут и нигде не выдаётся за измеренную.
     * Пока маршрут идёт, её нет вовсе: делить незаконченное не на что.
     */
    val doseMicroSv: Double?
        get() {
            if (running) return null
            val average = avgDoseMicroSvH ?: return null
            val hours = durationSeconds / 3600.0
            if (hours <= 0.0) return null
            return average * hours
        }
}

/** Что показывает журнал: всё вместе или один вид записей. */
enum class HistoryFilter { ALL, SESSIONS, ROUTES, SPECTRA }

object RouteFormat {

    /**
     * Как маршрут подписан в списке.
     *
     * Имя, если человек его дал, иначе «Маршрут · 18:51» — время начала. День
     * в подпись не входит: он уже стоит заголовком группы, и повторять его в
     * каждой строке значило бы занимать место тем, что и так сказано. А
     * требовать название ДО прогулки — требовать решения раньше, чем есть о
     * чём; безымянные же «Маршрут, Маршрут, Маршрут» не различить.
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
 * Считается здесь, а не в отрисовке, по двум причинам: форма не зависит от
 * размера картинки, и её можно проверить числами. Долгота сжимается по
 * широте (cos φ), иначе средняя полоса вытягивала бы маршрут вдвое поперёк —
 * миниатюра обязана быть похожа на то, что человек видел на карте.
 *
 * Пропуски координат здесь НЕ разрываются: миниатюра — не карта, по ней не
 * измеряют, и дробить ноготь на отрезки нечем. Настоящий след с разрывами
 * человек видит, открыв маршрут.
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
        // Маршрут «на месте»: разброса нет, и растягивать нечего — точка в
        // середине честнее, чем шум, растянутый во весь квадрат.
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
