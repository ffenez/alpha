package app.alpha.analysis.evidence

/**
 * Принятая человеком измеренная модель разрешения — то, что хранится.
 *
 * Хранится вместе с серийником прибора и обстоятельствами измерения. Серийник
 * не украшение: коэффициенты описывают КОНКРЕТНЫЙ кристалл, и применить их к
 * другому прибору значило бы искать пики не той ширины. Поэтому модель
 * действует только на приборе, на котором измерена ([ResolutionSource]).
 *
 * Формат — плоские пары `ключ=значение` через `;`, как у записанного фона
 * Поиска: одна строка в DataStore, читается и пишется чистым JVM-кодом, а
 * поломанная строка декодируется в `null` (то есть «модели нет»), а не роняет
 * экран.
 */
data class AcceptedResolution(
    val a: Double,
    val b: Double,
    val c: Double,
    /** Серийный номер прибора, на котором измерено; null — прибор был неизвестен. */
    val deviceSerial: String?,
    val acceptedAtMillis: Long,
    /** Сколько линий вошло в подгонку — печатается на экране. */
    val points: Int,
    /** Ниже этой энергии модель экстраполируется. */
    val lowestKeV: Double,
    val highestKeV: Double,
    /** Версия математики, которой получены коэффициенты. */
    val algorithmVersion: Int,
) {
    fun model(): MeasuredResolution = MeasuredResolution(a, b, c)

    fun encode(): String = listOf(
        "a" to a.toString(),
        "b" to b.toString(),
        "c" to c.toString(),
        "serial" to (deviceSerial ?: ""),
        "at" to acceptedAtMillis.toString(),
        "n" to points.toString(),
        "lo" to lowestKeV.toString(),
        "hi" to highestKeV.toString(),
        "v" to algorithmVersion.toString(),
    ).joinToString(";") { "${it.first}=${it.second}" }

    companion object {

        fun decode(raw: String?): AcceptedResolution? {
            if (raw.isNullOrBlank()) return null
            val fields = raw.split(';')
                .mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
                }
                .toMap()
            val a = fields["a"]?.toDoubleOrNull() ?: return null
            val b = fields["b"]?.toDoubleOrNull() ?: return null
            val c = fields["c"]?.toDoubleOrNull() ?: return null
            if (!a.isFinite() || !b.isFinite() || !c.isFinite() || a < 0.0) return null
            return AcceptedResolution(
                a = a,
                b = b,
                c = c,
                deviceSerial = fields["serial"]?.takeIf { it.isNotBlank() },
                acceptedAtMillis = fields["at"]?.toLongOrNull() ?: 0L,
                points = fields["n"]?.toIntOrNull() ?: 0,
                lowestKeV = fields["lo"]?.toDoubleOrNull() ?: 0.0,
                highestKeV = fields["hi"]?.toDoubleOrNull() ?: 0.0,
                algorithmVersion = fields["v"]?.toIntOrNull() ?: 0,
            )
        }
    }
}
