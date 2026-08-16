package app.radiacode.ui.chart

import app.radiacode.ui.logic.ChartSeriesModel
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.ui.logic.QuantileMethod
import app.radiacode.ui.logic.QuantilePaths

/**
 * Когда графику нужен поход в базу, а когда достаточно того, что уже прочитано.
 *
 * ## Зачем отдельным местом
 *
 * Правило «переиспользовать снимок» жило в двух экранах в двух видах: Главная
 * сравнивала ширину колонки на равенство, полноэкранный график просто читал
 * заново после паузы. Пока окно двигалось по лестнице ступеней, разница ничего
 * не стоила — ширина колонки менялась редко и скачком. С непрерывным щипком
 * ([Viewport]) она меняется КАЖДЫЙ кадр, и строгое равенство означало бы запрос
 * в базу на каждое движение пальцев.
 *
 * ## Правило
 *
 * Снимок годится, когда выполняются три условия:
 *
 * 1. прочитанный диапазон покрывает окно целиком (сдвиг внутри запаса — это
 *    перепроекция, а не чтение);
 * 2. путь квантилей тот же (точные порядковые статистики ↔ слияние почасовых
 *    скетчей): смена пути меняет САМИ ЧИСЛА, и молча её проводить нельзя;
 * 3. ширина колонки отличается не больше чем в [BUCKET_TOLERANCE] раз —
 *    разрешение картинки при этом на глаз то же, а точное значение приходит
 *    следующим чтением, когда движение улеглось.
 */
object ChartDataSource {

    /**
     * Пауза после последнего движения окна, по истечении которой снимок
     * перечитывается. **Инженерный параметр**: 250 мс — рука уже стоит, глаз
     * ещё не начал разглядывать детали; `collectLatest` отменяет отложенное
     * чтение, поэтому щипок на шестьдесят кадров даёт ровно один запрос.
     */
    const val RELOAD_DEBOUNCE_MILLIS = 250L

    /**
     * Во сколько раз ширина колонки имеет право разойтись с расчётной, пока
     * снимок ещё годится.
     *
     * **Инженерный параметр**: полтора. Колонка вдвое шире расчётной уже видна
     * как «график огрубел», а полторы — нет; зато запрос в базу на каждом кадре
     * щипка виден всегда.
     */
    const val BUCKET_TOLERANCE = 1.5

    /** Диапазон, который читается ради этого окна: окно плюс запас по краям. */
    fun readRange(window: ChartWindow, edgeMillis: Long): ChartWindow =
        ChartWindows.loadRange(window, edgeMillis)

    /** Путь квантилей, которым будет прочитано это окно (ADR 004). */
    fun methodFor(window: ChartWindow, edgeMillis: Long): QuantileMethod =
        QuantilePaths.methodFor(readRange(window, edgeMillis).spanMillis)

    /**
     * Ширина колонки, которую даст чтение этого окна. Нужна ровно для одного:
     * понять, годится ли уже прочитанный снимок.
     */
    fun expectedBucketMillis(window: ChartWindow, edgeMillis: Long): Long {
        val load = readRange(window, edgeMillis)
        return QuantilePaths.bucketMillis(load.spanMillis, QuantilePaths.methodFor(load.spanMillis))
    }

    /**
     * Годится ли уже прочитанный снимок для этого окна.
     *
     * @param loadedRange диапазон, реально прочитанный из базы.
     * @param loadedBucketMillis ширина колонки в прочитанном снимке.
     */
    fun reusable(
        loadedRange: ChartWindow?,
        loadedBucketMillis: Long?,
        window: ChartWindow,
        edgeMillis: Long,
    ): Boolean {
        if (loadedRange == null || loadedBucketMillis == null || loadedBucketMillis <= 0L) {
            return false
        }
        if (!ChartWindows.covers(loadedRange, window)) return false
        if (QuantilePaths.methodFor(loadedRange.spanMillis) != methodFor(window, edgeMillis)) {
            return false
        }
        val expected = expectedBucketMillis(window, edgeMillis)
        if (expected <= 0L) return false
        val ratio = loadedBucketMillis.toDouble() / expected
        return ratio in (1.0 / BUCKET_TOLERANCE)..BUCKET_TOLERANCE
    }

    /**
     * Ширина колонки для ПОКАЗА уже прочитанного снимка.
     *
     * Снимок сложен по своей ширине колонки, а видимое окно после щипка может
     * быть уже или шире. Подробный вид пересобирает колонки под окно из тех же
     * подсекундных агрегатов (второго запроса нет) — эта функция называет
     * ширину, к которой их складывать.
     */
    fun displayBucketMillis(window: ChartWindow): Long =
        ChartSeriesModel.bucketMillis(window.spanMillis)
}
