package app.radiacode.ui.logic

import app.radiacode.analysis.Nuclide
import app.radiacode.analysis.NuclideOrigin
import java.util.Locale

/**
 * Wording of the nuclide reference card opened from the Спектр peak table.
 *
 * The framing is fixed by the scientific instruction and pinned by tests:
 *
 *  - §12 — the allowed vocabulary is «возможное совпадение», «совместимые
 *    линии»; writing that a nuclide was *detected* from one nearby peak is
 *    forbidden, and so is a numeric «% уверенности»;
 *  - §17 — whatever the app shows, it must also show what the conclusion
 *    rests on, so every card repeats what would be needed to confirm;
 *  - §23 — no statistical anomaly is ever presented as danger, therefore this
 *    card carries no safety advice, no dose limits and no «опасно».
 *
 * The card is about the **nuclide**, not about the measurement: it is a
 * reference page that happens to be reachable from a candidate row.
 */
object NuclideCard {

    /** Standing disclaimer, shown on every card above the data. */
    const val FRAMING: String =
        "Это справка о нуклиде, а не сообщение о том, что он найден. " +
            "Приложение нашло пик, энергия которого совместима с одной из " +
            "линий библиотеки: возможное совпадение ≠ обнаружение."

    /** What the app can and cannot conclude from one spectrum on this device. */
    const val LIMITS: String =
        "Один сцинтилляционный спектр не определяет нуклид. Совпадение по " +
            "энергии не учитывает наложение линий, точность калибровки и " +
            "статистику пика, а близкие по энергии линии разных нуклидов " +
            "прибор с таким разрешением не разделяет."

    fun title(nuclide: Nuclide): String = "${nuclide.symbol} · ${nuclide.name}"

    /** «природный · ряд Th-232» / «искусственный». */
    fun originLine(nuclide: Nuclide): String {
        val origin = when (nuclide.origin) {
            NuclideOrigin.NATURAL -> "природный"
            NuclideOrigin.ARTIFICIAL -> "искусственный"
        }
        return nuclide.chain?.let { "$origin · ряд $it" } ?: origin
    }

    /** «609,3 кэВ · 45,5 % на распад». */
    fun lineText(energyKeV: Float, intensityPercent: Float): String =
        "${decimal(energyKeV, 1)} кэВ · ${decimal(intensityPercent, 1)} % на распад"

    /** Attribution of the numbers; the card must say where they come from. */
    const val SOURCE: String =
        "Данные о распаде: IAEA Live Chart of Nuclides и NNDC NuDat 3 (ENSDF)."

    private fun decimal(value: Float, digits: Int): String =
        String.format(Locale.US, "%.${digits}f", value).replace('.', ',')
}
