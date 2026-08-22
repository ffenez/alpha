package app.alpha.data

import app.alpha.analysis.GainDrift

/**
 * Измеренный дрейф шкалы, как он хранится между запусками.
 *
 * Формат тот же, что у принятой модели разрешения: плоские пары `ключ=значение`
 * через `;` в одной строке DataStore. Поломанная строка читается как «дрейф не
 * измерен», а не роняет экран.
 *
 * Серийник хранится вместе с числами и обязателен к совпадению: температурный
 * ход — свойство конкретного кристалла и его фотоприёмника, и показывать
 * чужой ход как ход этого прибора нельзя.
 */
data class GainDriftRecord(
    val drift: GainDrift,
    val deviceSerial: String?,
    val measuredAtMillis: Long,
) {
    fun encode(): String = listOf(
        "at" to drift.atReference.toString(),
        "ats" to drift.atReferenceSigma.toString(),
        "k" to drift.perDegree.toString(),
        "ks" to drift.perDegreeSigma.toString(),
        "ref" to drift.referenceC.toString(),
        "n" to drift.points.toString(),
        "lo" to drift.minC.toString(),
        "hi" to drift.maxC.toString(),
        "e" to drift.lineKeV.toString(),
        "serial" to (deviceSerial ?: ""),
        "t" to measuredAtMillis.toString(),
    ).joinToString(";") { "${it.first}=${it.second}" }

    companion object {

        fun decode(raw: String?): GainDriftRecord? {
            if (raw.isNullOrBlank()) return null
            val fields = raw.split(';')
                .mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
                }
                .toMap()
            val at = fields["at"]?.toDoubleOrNull() ?: return null
            val ats = fields["ats"]?.toDoubleOrNull() ?: return null
            val k = fields["k"]?.toDoubleOrNull() ?: return null
            val ks = fields["ks"]?.toDoubleOrNull() ?: return null
            val ref = fields["ref"]?.toDoubleOrNull() ?: return null
            val line = fields["e"]?.toDoubleOrNull() ?: return null
            if (!at.isFinite() || !k.isFinite() || !ks.isFinite() || ks < 0.0) return null
            if (!ats.isFinite() || ats < 0.0) return null
            return GainDriftRecord(
                drift = GainDrift(
                    atReference = at,
                    atReferenceSigma = ats,
                    perDegree = k,
                    perDegreeSigma = ks,
                    referenceC = ref,
                    points = fields["n"]?.toIntOrNull() ?: 0,
                    minC = fields["lo"]?.toDoubleOrNull() ?: 0.0,
                    maxC = fields["hi"]?.toDoubleOrNull() ?: 0.0,
                    lineKeV = line,
                ),
                deviceSerial = fields["serial"]?.takeIf { it.isNotBlank() },
                measuredAtMillis = fields["t"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}
