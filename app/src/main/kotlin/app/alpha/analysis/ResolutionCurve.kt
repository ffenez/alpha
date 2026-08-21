package app.alpha.analysis

import kotlin.math.sqrt

/**
 * Разрешение прибора как ФУНКЦИЯ энергии, измеренная по его же спектрам.
 *
 * ## Почему одного числа мало
 *
 * Ширина линии сцинтиллятора складывается из двух независимых вкладов:
 * статистики фотонов (∝ √E) и постоянного вклада шума тракта и разброса
 * усиления. Отсюда FWHM²(E) = a + b·E — прямая по E, а не пропорциональность
 * корню. Правило «FWHM = R·√(662·E)», которым приложение пользуется по
 * умолчанию, — это частный случай с a = 0, и оно тем хуже, чем дальше энергия
 * от 662 кэВ: на 2615 кэВ ошибка в ширине разъезжается на десятки процентов, а
 * на ней держится и поиск пиков, и приведение чужого шаблона.
 *
 * ## Почему это персонально
 *
 * Паспортное число вендор публикует для 103, 103G и 110, для 101 и 102 не
 * публикует вовсе, и оно в любом случае одно на модель. Два прибора одной
 * модели отличаются кристаллом, склейкой и фотоприёмником, а один и тот же
 * прибор — температурой. Кривая измеряется по линиям в СОБСТВЕННЫХ спектрах
 * прибора, и с каждым записанным шаблоном становится точнее.
 *
 * ## Границы метода
 *
 * Две точки дают прямую, но не проверяют её: пока линий меньше [MIN_POINTS] или
 * они не разнесены по энергии, кривая не строится и остаётся паспортное число.
 * Результат проверяется на правдоподобие ([MIN_RESOLUTION_662],
 * [MAX_RESOLUTION_662], b > 0) — подогнанная под шум прямая хуже честного
 * паспорта.
 */
data class ResolutionCurve(
    /** Постоянный вклад в FWHM², кэВ². */
    val a: Float,
    /** Коэффициент при энергии в FWHM², кэВ. */
    val b: Float,
) {
    /** Ширина линии на половине высоты, кэВ. */
    fun fwhmAt(energyKeV: Float): Float {
        val square = a + b * energyKeV
        return if (square > 0f) sqrt(square) else 0f
    }

    /** Разрешение на 662 кэВ, доля — то, чем пользуются шаблоны и поиск пиков. */
    val resolution662: Float get() = fwhmAt(REFERENCE_KEV) / REFERENCE_KEV

    /** Точка измерения: линия с измеренной шириной. */
    data class Point(
        val energyKeV: Float,
        val fwhmKeV: Float,
        /** Вес точки: значимость линии, по которой измерена ширина. */
        val weight: Float,
    )

    companion object {

        /** Энергия Cs-137, к которой приведено «разрешение в процентах». */
        const val REFERENCE_KEV = 662f

        /**
         * Кривая, эквивалентная одному паспортному числу: FWHM = R·√(662·E),
         * то есть a = 0, b = R²·662.
         */
        fun ofResolution662(resolution662: Float) =
            ResolutionCurve(a = 0f, b = resolution662 * resolution662 * REFERENCE_KEV)

        /**
         * Взвешенная прямая FWHM² = a + b·E по измеренным линиям.
         *
         * @return null, если точек мало, они стоят слишком близко по энергии
         *   или результат неправдоподобен; вызывающий берёт паспортное число.
         */
        fun fit(points: List<Point>): ResolutionCurve? {
            val usable = points.filter { it.fwhmKeV > 0f && it.energyKeV > 0f && it.weight > 0f }
            if (usable.size < MIN_POINTS) return null
            val minEnergy = usable.minOf { it.energyKeV }
            val maxEnergy = usable.maxOf { it.energyKeV }
            // Без разноса по энергии прямая не определена: две близкие точки
            // дают любой наклон в пределах своей же погрешности.
            if (maxEnergy - minEnergy < MIN_ENERGY_SPAN_KEV) return null

            var sw = 0.0
            var sx = 0.0
            var sxx = 0.0
            var sy = 0.0
            var sxy = 0.0
            for (point in usable) {
                val w = point.weight.toDouble()
                val x = point.energyKeV.toDouble()
                val y = point.fwhmKeV.toDouble() * point.fwhmKeV.toDouble()
                sw += w
                sx += w * x
                sxx += w * x * x
                sy += w * y
                sxy += w * x * y
            }
            val determinant = sw * sxx - sx * sx
            if (determinant == 0.0) return null
            val a = (sy * sxx - sx * sxy) / determinant
            val b = (sw * sxy - sx * sy) / determinant
            if (b <= 0.0) return null
            // Отрицательный постоянный вклад физического смысла не имеет; он
            // означает, что двух-трёх линий не хватило. Тогда прямая берётся
            // через ноль — это ровно паспортная форма, но с ИЗМЕРЕННЫМ наклоном.
            val curve = if (a < 0.0) {
                ResolutionCurve(a = 0f, b = (sxy / sxx).toFloat())
            } else {
                ResolutionCurve(a = a.toFloat(), b = b.toFloat())
            }
            if (curve.b <= 0f) return null
            val resolution = curve.resolution662
            return if (resolution in MIN_RESOLUTION_662..MAX_RESOLUTION_662) curve else null
        }

        /** Меньше двух точек прямой не задают. */
        const val MIN_POINTS = 2

        /**
         * **Инженерный параметр**: минимальный разнос точек по энергии, кэВ.
         * Половина шкалы прибора серии (20…3000 кэВ) — линии 583 и 2615 кэВ
         * ториевого ряда проходят, дублет 583/609 нет.
         */
        const val MIN_ENERGY_SPAN_KEV = 400f

        /** Границы правдоподобия: вне их измерение — это не разрешение. */
        const val MIN_RESOLUTION_662 = 0.03f
        const val MAX_RESOLUTION_662 = 0.20f
    }
}
