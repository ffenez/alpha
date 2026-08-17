package app.alpha.ui.logic

import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.SearchRu
import app.alpha.ui.text.SearchStrings
import app.alpha.ui.text.Strings
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How Поиск sounds and buzzes (search redesign §7).
 *
 * The mode is **one** choice, not three independent switches: the user is
 * walking with the instrument and choosing how to hear the signal, and the four
 * states of the redesign — «нет», «клики», «тон», «вибро» — are alternatives to
 * each other, not options to combine.
 */
enum class SearchFeedbackMode(val id: String, val label: String) {
    // `label` остаётся русским: его печатает отладочный отчёт, который не
    // зависит от языка интерфейса. Подпись на экране — [title].
    /** Nothing: the screen is the only channel. */
    OFF("off", "нет"),

    /** The Geiger-style tick per registered event ([ClickEngine]). */
    CLICKS("clicks", "клики"),

    /** A continuous tone whose pitch follows the ratio to the background. */
    TONE("tone", "тон"),

    /** The same signal without sound: pulse cadence follows the ratio. */
    VIBRO("vibro", "вибро");

    /** Подпись сегмента на языке интерфейса. */
    fun title(s: Strings = RuStrings): String = when (this) {
        OFF -> s.modeOff
        CLICKS -> s.modeClicks
        TONE -> s.modeTone
        VIBRO -> s.modeVibro
    }

    companion object {
        fun of(id: String?): SearchFeedbackMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The search tone: pitch as a function of **how much above the recorded
 * background the count rate is**, not of the raw rate (redesign §7).
 *
 * ## Scientific release gate (spec §24)
 *
 * 1. **Formula.** The pitch is logarithmic in the rate ratio R:
 *
 *    ```text
 *    f(R) = f₀ · (f₁/f₀)^( ln(R/R₀) / ln(R₁/R₀) ),  R clamped to [R₀, R₁]
 *    ```
 *
 *    so equal *factors* of R are equal musical intervals — the ear hears
 *    doubling as doubling. Below [MIN_RATIO] there is no tone at all: that
 *    range is the background, and a tone there would sing at noise.
 * 2. **Assumptions.** R comes from the decision window of [SearchEngine]
 *    (3 s against the recorded reference), so it is already a statistically
 *    stable quantity — the redesign explicitly forbids a pitch that jumps on
 *    single events. The glide ([MAX_OCTAVES_PER_SECOND]) is what keeps a step
 *    in R from becoming a click in the audio.
 * 3. **Units.** R is dimensionless, frequencies are hertz, the glide is
 *    octaves per second.
 * 4. **Reference.** None needed — this is an interface mapping, not physics.
 *    It is documented here precisely so it is never mistaken for one.
 * 5. **Validation data.** `SearchToneTest`: monotone in R, an octave per
 *    doubling inside the range, silent below [MIN_RATIO], phase-continuous
 *    across chunks, and a step in R reaches its new pitch at the glide rate.
 * 6. **Limitations.** **Every constant here is an engineering parameter.** The
 *    pitch is a *presentation* of the ratio and carries no significance: a
 *    high tone means «счёт выше записанного фона во столько-то раз», never
 *    «опасно» and never «источник найден» — the verdict stays with the ladder.
 * 7. **Tests.** `app/src/test/.../ui/logic/SearchToneTest.kt`.
 * 8. **Algorithm version.** None: no measurement is derived from it.
 * 9. **User-facing meaning.** «Выше тон — счёт дальше от записанного фона».
 */
object SearchTone {

    /** Below this ratio the tone is silent: that is the background. */
    const val MIN_RATIO = 1.15

    /** At and above this ratio the pitch is at its top — the tone saturates. */
    const val MAX_RATIO = 8.0

    const val MIN_HZ = 320f
    const val MAX_HZ = 2_560f

    /** Peak amplitude of the sine, 0…1. Quieter than a click on purpose. */
    const val AMPLITUDE = 0.28f

    /** How fast the pitch may travel — no zipper noise, no jumps. */
    const val MAX_OCTAVES_PER_SECOND = 2.0f

    /**
     * Target pitch for a rate ratio, or null while the ratio is inside the
     * background (silence is the honest answer there).
     */
    fun frequencyHz(ratio: Double?): Float? {
        if (ratio == null || !ratio.isFinite() || ratio < MIN_RATIO) return null
        val clamped = ratio.coerceAtMost(MAX_RATIO)
        val position = ln(clamped / MIN_RATIO) / ln(MAX_RATIO / MIN_RATIO)
        val octaves = ln(MAX_HZ.toDouble() / MIN_HZ) / ln(2.0)
        return (MIN_HZ * 2.0.pow(position * octaves)).toFloat()
    }

}

/**
 * Phase-continuous sine generator for the search tone.
 *
 * Two properties matter and both are tested rather than assumed: the phase
 * carries across chunk boundaries (a reset phase is an audible click every
 * ~46 ms), and the frequency only ever *glides* towards its target at
 * [SearchTone.MAX_OCTAVES_PER_SECOND], so a jump in the ratio cannot become a
 * jump in the audio.
 */
class ToneEngine(private val sampleRate: Int) {

    private var phase = 0.0
    private var currentHz = 0f

    /** The pitch actually sounding right now, Hz; 0 = silent. */
    val frequencyHz: Float get() = currentHz

    /**
     * Renders one chunk towards [targetHz] (null = fade to silence).
     *
     * The amplitude follows the same glide as the pitch, so starting and
     * stopping the tone is a short fade rather than a pop.
     */
    fun fillChunk(out: ShortArray, targetHz: Float?, amplitude: Float = SearchTone.AMPLITUDE) {
        val target = targetHz ?: 0f
        val secondsPerFrame = 1.0 / sampleRate
        val maxRatioPerFrame = 2.0.pow(SearchTone.MAX_OCTAVES_PER_SECOND * secondsPerFrame)
        for (i in out.indices) {
            currentHz = glide(currentHz, target, maxRatioPerFrame)
            if (currentHz <= 0f) {
                out[i] = 0
                phase = 0.0
                continue
            }
            phase += 2.0 * PI * currentHz * secondsPerFrame
            if (phase > 2.0 * PI) phase -= 2.0 * PI
            val level = amplitude * fadeIn(currentHz, target)
            out[i] = (sin(phase) * level * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Move [from] towards [to] by at most one frame's worth of glide. */
    private fun glide(from: Float, to: Float, maxRatioPerFrame: Double): Float {
        if (to <= 0f) {
            // Fading out: slide down to the floor and then stop.
            if (from <= SearchTone.MIN_HZ / 2f) return 0f
            return (from / maxRatioPerFrame).toFloat()
        }
        if (from <= 0f) return SearchTone.MIN_HZ
        val up = (from * maxRatioPerFrame).toFloat()
        val down = (from / maxRatioPerFrame).toFloat()
        return when {
            to > from -> minOf(up, to)
            to < from -> maxOf(down, to)
            else -> from
        }
    }

    /** Silence-to-tone and tone-to-silence are shaped, never switched. */
    private fun fadeIn(current: Float, target: Float): Float =
        if (target <= 0f) (current / SearchTone.MIN_HZ).coerceIn(0f, 1f) else 1f
}

/**
 * The silent equivalent of the tone (redesign §7): pulse **cadence** follows
 * the same ratio, so the instrument can be searched with the phone in a pocket
 * or in a noisy place.
 *
 * It **repeats** rather than firing once per newly reached σ step, which is
 * what the pre-redesign policy did: that version went silent while standing
 * still over a source, and «холодно/горячо» is the whole point of a silent
 * search.
 *
 * All constants are engineering parameters.
 */
object SearchVibro {

    /** Below this ratio there is nothing to feel: it is the background. */
    const val MIN_RATIO = SearchTone.MIN_RATIO

    /** Ratio at which the cadence is at its fastest. */
    const val MAX_RATIO = SearchTone.MAX_RATIO

    /** Slowest and fastest pulse spacing, ms. */
    const val SLOW_INTERVAL_MILLIS = 1_200L
    const val FAST_INTERVAL_MILLIS = 120L

    /**
     * Milliseconds between pulses for a rate ratio, or null while inside the
     * background (no pulses at all).
     */
    fun intervalMillis(ratio: Double?): Long? {
        if (ratio == null || !ratio.isFinite() || ratio < MIN_RATIO) return null
        val clamped = ratio.coerceAtMost(MAX_RATIO)
        val position = ln(clamped / MIN_RATIO) / ln(MAX_RATIO / MIN_RATIO)
        val span = SLOW_INTERVAL_MILLIS - FAST_INTERVAL_MILLIS
        return (SLOW_INTERVAL_MILLIS - position * span).toLong()
            .coerceIn(FAST_INTERVAL_MILLIS, SLOW_INTERVAL_MILLIS)
    }

}
