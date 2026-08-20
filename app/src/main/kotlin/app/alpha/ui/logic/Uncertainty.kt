package app.alpha.ui.logic

import app.alpha.ui.text.uiDecimal
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Honest uncertainty wording (design: every value carries its error).
 *
 * Dose rate: the device itself reports an error estimate with every
 * real-time record (dr_err, decoded to percent in :protocol); we display it
 * verbatim as «±N%» without claiming a confidence level the device does not
 * document.
 *
 * CPS: counts in a window τ are Poisson, so the 1σ uncertainty of the rate
 * is σ = √(N)/τ = √(cps/τ); at the 1 Hz display window τ = 1 s this is
 * simply √cps. That claim is ours, so it is labeled 1σ.
 */
object Uncertainty {

    /** Poisson 1σ of a rate averaged over [tauSeconds]. */
    fun cpsSigma(cps: Float, tauSeconds: Float = 1f): Float {
        if (cps < 0f || tauSeconds <= 0f) return 0f
        return sqrt(cps / tauSeconds)
    }

    /**
     * «24,3 ±2,1» — счёт и его пуассоновская 1σ при τ = 1 с, БЕЗ единицы.
     *
     * Единица приходит из каталога языка (`с⁻¹` / `s⁻¹`): вшитая сюда, она
     * уезжала в английский экран русской буквой.
     */
    fun cpsWithSigma(cps: Float): String = cpsWithSigmaBare(cps)

    /**
     * То же без единицы — для плитки, у которой единица стоит в подписи.
     *
     * «СЧЁТ, с⁻¹» над «23,7 ±4,9 с⁻¹» называло единицу дважды в одном
     * элементе шириной в треть экрана.
     */
    fun cpsWithSigmaBare(cps: Float): String = "${num1(cps)} ±${num1(cpsSigma(cps))}"

    /**
     * Только счёт, без ±: плитка Главной.
     *
     * Пуассоновская σ никуда не делась — она в «Почему такой вывод», где стоит
     * рядом с окном, по которому посчитана. На плитке шириной в треть экрана
     * «24,9 ±5,0» читается как одно длинное число, а не как измерение с
     * неопределённостью.
     */
    fun cpsPlain(cps: Float): String = num1(cps)

    /** «±8%» from the device error percent; null when absent or zero. */
    fun errPercentLabel(errPercent: Float?): String? {
        if (errPercent == null || errPercent <= 0f) return null
        return "±${errPercent.roundToInt()}%"
    }

    /** One-decimal number with a comma: 24.31 → «24,3». */
    /** Два знака — для отношений: «1,04», «0,82». */
    fun num2(value: Float): String =
        String.format(java.util.Locale.US, "%.2f", value).uiDecimal()

    fun num1(value: Float): String =
        String.format(Locale.US, "%.1f", value).uiDecimal()

    /**
     * Изменение со знаком: «+1,2», «−6,0». Минус — типографский (U+2212), как
     * во всех остальных числах приложения; ноль остаётся без знака, потому что
     * «+0,0» читается как рост, которого не было.
     */
    fun signed1(value: Float): String {
        val text = num1(kotlin.math.abs(value))
        return when {
            text.trimStart('0', ',', '.').isEmpty() -> text
            value < 0f -> "−$text"
            else -> "+$text"
        }
    }
}
