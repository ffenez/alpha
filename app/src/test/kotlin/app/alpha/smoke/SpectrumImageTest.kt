package app.alpha.smoke

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import app.alpha.ui.components.SpectrumChartSpec
import app.alpha.ui.components.SpectrumImage
import app.alpha.ui.components.SpectrumPeakMark
import app.alpha.ui.components.SpectrumPlotStyle
import app.alpha.ui.logic.SpectrumScale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.exp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Отрисовка спектра в растр вне экрана ([SpectrumImage]).
 *
 * `@GraphicsMode(NATIVE)` обязателен: в режиме по умолчанию Robolectric
 * подменяет Canvas заглушкой, растр остаётся пустым, и проверка «на картинке
 * что-то есть» проходила бы или падала случайно.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpectrumImageTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Цвета взяты контрастными и различимыми поштучно: сравнение идёт по пикселям. */
    private val style = SpectrumPlotStyle(
        field = Color(0xFF10161B),
        bg = Color(0xFF0B0F12),
        ink2 = Color(0xFFB6C2CC),
        muted = Color(0xFF7B8794),
        data = Color(0xFF2ED3B7),
        dataText = Color(0xFF56E1C8),
        warn = Color(0xFFE0A526),
        axisText = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
    )

    private val channels = 1024

    /**
     * Синтетический спектр: спадающая подложка плюс гауссова линия в канале 512
     * (σ = 6 каналов). Хвост держится около единицы — на логарифмической шкале
     * это низ поля, и по нему видно, что форма данных доехала до растра.
     */
    private fun spec(): SpectrumChartSpec {
        val columns = List(channels) { i ->
            val continuum = 300f * exp(-i / 260f) + 1f
            val peak = 900f * exp(-((i - 512f) * (i - 512f)) / (2f * 6f * 6f))
            continuum + peak
        }
        return SpectrumChartSpec(
            columns = columns,
            scale = SpectrumScale.Log,
            yTop = 1000f,
            peaks = listOf(SpectrumPeakMark(columnIndex = 512, label = "662")),
        )
    }

    private fun pixels(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    /** Самый верхний непустой пиксель в полосе долей ширины [from]…[to]; -1 — полоса пуста. */
    private fun topDrawnRow(px: IntArray, w: Int, h: Int, field: Int, from: Float, to: Float): Int {
        for (y in 0 until h) {
            for (x in (from * w).toInt() until (to * w).toInt()) {
                if (px[y * w + x] != field) return y
            }
        }
        return -1
    }

    @Test
    fun `the spectrum renders into a bitmap of the requested size`() {
        val w = 1200
        val h = 800
        val bitmap = SpectrumImage.render(context, spec(), style, widthPx = w, heightPx = h)

        assertEquals(w, bitmap.width, "ширина растра")
        assertEquals(h, bitmap.height, "высота растра")

        val px = pixels(bitmap)
        val field = style.field.toArgb()
        val drawn = px.count { it != field }
        // Кривая с заливкой на поле 1200 × 800 занимает заметно больше 1 %
        // площади; порог отделяет «нарисовано» от «одна случайная линия».
        assertTrue(drawn > w * h / 100, "на картинке почти ничего не нарисовано: $drawn px")
        // Фон залит непрозрачно: прозрачный PNG на светлом листе не читается.
        assertTrue(px.all { (it ushr 24) == 0xFF }, "в растре есть прозрачные пиксели")
    }

    @Test
    fun `the peak shapes the picture, not just any ink on the field`() {
        val w = 1200
        val h = 800
        val bitmap = SpectrumImage.render(context, spec(), style, widthPx = w, heightPx = h)
        val px = pixels(bitmap)
        val field = style.field.toArgb()

        // Линия стоит в канале 512 из 1024 — это середина поля; хвост спектра
        // (правые проценты ширины) лежит у нижнего края.
        val atPeak = topDrawnRow(px, w, h, field, 0.46f, 0.54f)
        val atTail = topDrawnRow(px, w, h, field, 0.88f, 0.98f)
        assertTrue(atPeak >= 0, "в середине поля ничего не нарисовано")
        assertTrue(atTail >= 0, "у правого края поля ничего не нарисовано")
        assertTrue(
            atPeak < atTail,
            "линия не поднялась над хвостом: строка у пика $atPeak, у хвоста $atTail",
        )
    }

    @Test
    fun `an empty spectrum leaves the field untouched`() {
        val bitmap = SpectrumImage.render(
            context,
            SpectrumChartSpec(columns = emptyList(), yTop = 0f),
            style,
            widthPx = 320,
            heightPx = 240,
        )
        val field = style.field.toArgb()
        assertTrue(
            pixels(bitmap).all { it == field },
            "пустой спектр нарисовал что-то поверх поля",
        )
    }

    /**
     * Плотность растёт вместе с шириной, иначе подписи 10 sp на широкой картинке
     * превращаются в ниточки.
     */
    @Test
    fun `density follows the requested width`() {
        assertEquals(3.75f, SpectrumImage.densityFor(3000), 1e-4f)
        assertEquals(3.0f, SpectrumImage.densityFor(2400), 1e-4f)
        // Края: узкая картинка не уходит ниже 1, широкая — не выше 6.
        assertEquals(1f, SpectrumImage.densityFor(320), 1e-4f)
        assertEquals(6f, SpectrumImage.densityFor(20_000), 1e-4f)
    }
}
