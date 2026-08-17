package app.alpha.ui.chart

import app.alpha.ui.logic.ChartSeriesModel
import app.alpha.ui.logic.ChartWindow
import app.alpha.ui.logic.ChartWindows
import app.alpha.ui.logic.QuantileMethod
import app.alpha.ui.logic.QuantilePaths

/**
 * Сколько истории читать про запас вокруг окна — и почему у карточки иначе.
 *
 * Запас существует ради жеста: сдвиг внутри уже прочитанного не требует
 * запроса. Полноэкранному графику он нужен щедрый — там историю листают, и
 * час хода пальцем без единого чтения ощущается как отзывчивость.
 *
 * Карточке Главной он стоит слишком дорого. Пятиминутное окно с часовым
 * запасом — это два часа подсекундных агрегатов, около шести тысяч строк,
 * которые читаются и складываются в колонки КАЖДЫЙ раз, когда приходит новое
 * измерение, и так на каждую из трёх карточек. Ради трёхсот нарисованных
 * измерений. Отсюда мини-графики и подтормаживали.
 */
data class ReadPadding(
    val fraction: Float,
    val minMillis: Long,
) {
    companion object {

        /** Полноэкранный график: окно с каждой стороны, но не меньше часа. */
        val Full = ReadPadding(
            fraction = ChartWindows.LOAD_PADDING_FRACTION,
            minMillis = ChartWindows.MIN_LOAD_PADDING_MILLIS,
        )

        /**
         * Карточка Главной: половина окна и никакого часового пола.
         *
         * **Инженерный параметр**: половина окна — это уверенный рывок пальцем
         * по миниатюре; дальше него читают уже во весь экран, куда карточка и
         * открывается одним нажатием.
         */
        val Compact = ReadPadding(fraction = 0.5f, minMillis = 0L)
    }
}

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
    fun readRange(
        window: ChartWindow,
        edgeMillis: Long,
        padding: ReadPadding = ReadPadding.Full,
    ): ChartWindow = ChartWindows.loadRange(
        window = window,
        nowMillis = edgeMillis,
        paddingFraction = padding.fraction,
        minPaddingMillis = padding.minMillis,
    )

    /** Путь квантилей, которым будет прочитано это окно (ADR 004). */
    fun methodFor(
        window: ChartWindow,
        edgeMillis: Long,
        padding: ReadPadding = ReadPadding.Full,
    ): QuantileMethod = QuantilePaths.methodFor(readRange(window, edgeMillis, padding).spanMillis)

    /**
     * Ширина колонки, которую даст чтение этого окна. Нужна ровно для одного:
     * понять, годится ли уже прочитанный снимок.
     */
    fun expectedBucketMillis(
        window: ChartWindow,
        edgeMillis: Long,
        padding: ReadPadding = ReadPadding.Full,
    ): Long {
        val load = readRange(window, edgeMillis, padding)
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
        padding: ReadPadding = ReadPadding.Full,
    ): Boolean {
        if (loadedRange == null || loadedBucketMillis == null || loadedBucketMillis <= 0L) {
            return false
        }
        if (!ChartWindows.covers(loadedRange, window)) return false
        if (
            QuantilePaths.methodFor(loadedRange.spanMillis) != methodFor(window, edgeMillis, padding)
        ) {
            return false
        }
        val expected = expectedBucketMillis(window, edgeMillis, padding)
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
