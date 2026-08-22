package app.alpha.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.ByteArrayOutputStream

/**
 * Отрисовка спектра в растр ВНЕ экрана: тем же [drawSpectrumPlot], что рисует
 * поле в композиции, но в холст произвольного размера.
 *
 * Смысл — не снимок экрана. Снимок телефона даёт ~1080 px на 1024 канала, то
 * есть примерно 1 px на канал: тонкая структура (двойные линии, зависание АЦП,
 * пропуски каналов) в нём физически неотличима от шума растеризации. Здесь
 * ширина задаётся числом каналов, а не экраном.
 */
object SpectrumImage {

    /**
     * Ширина картинки по умолчанию, px. Инженерный параметр.
     *
     * Спектр прибора — 1024 канала; чтобы соседние каналы не сливались в один
     * столбик, на канал нужно не меньше 2 px, то есть не меньше
     * 1024 × 2 = 2048 px поля. Поле уже картинки на подписи оси (три знака
     * моноширинного 10 sp при плотности 3,75 — около 90 px слева), поэтому
     * берётся 3000 px: (3000 − 90) / 1024 ≈ 2,8 px на канал — запас на подписи
     * пиков, которые при ровно 2 px на канал начинают наезжать друг на друга.
     */
    const val DEFAULT_WIDTH_PX = 3000

    /**
     * Высота по умолчанию, px: 3:2 к ширине. Пропорция ближе к квадрату
     * растягивает по вертикали три-четыре декады логарифмической шкалы и делает
     * ступеньку в один отсчёт заметной; поле экрана (примерно 6:1) для этого
     * слишком плоское.
     *
     * Память: 3000 × 2000 × 4 Б (ARGB_8888) = 24 МБ на время отрисовки и
     * кодирования. Это разовый пик — [Bitmap.recycle] после [png] возвращает
     * его сразу; PNG на выходе — сотни килобайт (график почти всюду однотонный).
     */
    const val DEFAULT_HEIGHT_PX = 2000

    /**
     * Ширина вёрстки картинки, dp. Инженерный параметр.
     *
     * Толщины линий и кегль подписей заданы в dp/sp, поэтому в пикселях они
     * растут вместе с плотностью. При плотности 1 подпись оси 10 sp = 10 px на
     * картинке шириной 3000 px — нечитаемая ниточка. Плотность считается из
     * ширины: `плотность = ширина / 800 dp`, то есть у картинки любой ширины
     * поле «шириной 800 dp». Экран телефона — 411 dp, значит места под ту же
     * подпись вдвое больше (подписи кэВ и пиков стоят реже и не спорят), а сама
     * подпись остаётся 10 sp физического размера при просмотре картинки целиком.
     *
     * При [DEFAULT_WIDTH_PX] это 3000 / 800 = 3,75: подпись оси ≈ 37 px,
     * кривая — 6 px, сетка — 3,75 px.
     */
    private const val LAYOUT_WIDTH_DP = 800f

    /**
     * Плотность для картинки шириной [widthPx]. Ограничена снизу 1,0 (ниже
     * подписи начинают терять форму) и сверху 6,0 (выше линии становятся
     * жирными мазками, скрывающими те самые единичные каналы).
     */
    fun densityFor(widthPx: Int): Float =
        (widthPx / LAYOUT_WIDTH_DP).coerceIn(1f, 6f)

    /**
     * Рисует поле спектра в растр [widthPx] × [heightPx].
     *
     * [context] нужен только для загрузчика шрифтов ([createFontFamilyResolver]):
     * вне композиции его неоткуда взять. [densityScale] — множитель dp→px, см.
     * [densityFor]. Фон заливается [SpectrumPlotStyle.field] явно: прозрачный
     * PNG на белом листе или в тёмном мессенджере читается как испорченный.
     *
     * Возвращает ARGB_8888-растр; вызывающий отвечает за [Bitmap.recycle].
     */
    fun render(
        context: Context,
        spec: SpectrumChartSpec,
        style: SpectrumPlotStyle,
        widthPx: Int = DEFAULT_WIDTH_PX,
        heightPx: Int = DEFAULT_HEIGHT_PX,
        densityScale: Float = densityFor(widthPx),
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val density = Density(densityScale)
        val image = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(image),
            size = Size(w.toFloat(), h.toFloat()),
        ) {
            drawRect(color = style.field, size = size)
            drawSpectrumPlot(spec, style, measurer)
        }
        return image.asAndroidBitmap()
    }

    /**
     * PNG-байты растра. PNG без потерь, поэтому «качество» 100 для него —
     * формальность; выбран он ради резких линий и подписей, которые JPEG
     * размывает ореолами.
     */
    fun png(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream(1 shl 20)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** [render] + [png] с немедленным освобождением растра (пик — 24 МБ, см. [DEFAULT_HEIGHT_PX]). */
    fun renderPng(
        context: Context,
        spec: SpectrumChartSpec,
        style: SpectrumPlotStyle,
        widthPx: Int = DEFAULT_WIDTH_PX,
        heightPx: Int = DEFAULT_HEIGHT_PX,
        densityScale: Float = densityFor(widthPx),
    ): ByteArray {
        val bitmap = render(context, spec, style, widthPx, heightPx, densityScale)
        return try {
            png(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
