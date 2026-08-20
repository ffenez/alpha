package app.alpha.ui.logic

import app.alpha.analysis.Radioelements
import app.alpha.data.JsonMap

/**
 * Принятые коэффициенты стриппинга — вместе с прибором, на котором они сняты.
 *
 * Серийный номер здесь обязателен: доля протечки зависит от кристалла, и
 * коэффициенты одного прибора на другом неверны. Приложение не запрещает
 * подключить второй прибор, оно просто перестаёт применять чужие числа и
 * говорит об этом.
 *
 * Серийный номер живёт только в настройках приложения и наружу не выходит —
 * в выгружаемые файлы он не попадает (см. `RcXml`, `N42`).
 */
data class StrippingRecord(
    val serialNumber: String,
    val thoriumIntoUranium: Float,
    val thoriumIntoPotassium: Float,
    val uraniumIntoPotassium: Float,
    /** Когда сняты, мс эпохи. */
    val measuredAtMillis: Long,
) {

    fun stripping(): Radioelements.Stripping = Radioelements.Stripping(
        thoriumIntoUranium = thoriumIntoUranium,
        thoriumIntoPotassium = thoriumIntoPotassium,
        uraniumIntoPotassium = uraniumIntoPotassium,
    )

    /** Коэффициенты применимы к прибору [serial]; чужие — нет. */
    fun appliesTo(serial: String?): Boolean =
        serial != null && serial.equals(serialNumber, ignoreCase = true)

    fun encode(): String = JsonMap.of(
        "serial" to serialNumber,
        "alpha" to thoriumIntoUranium.toDouble(),
        "beta" to thoriumIntoPotassium.toDouble(),
        "gamma" to uraniumIntoPotassium.toDouble(),
        "measuredAtMillis" to measuredAtMillis,
    )

    companion object {

        fun decode(raw: String?): StrippingRecord? {
            val map = JsonMap.decode(raw)
            if (map.isEmpty()) return null
            val serial = map["serial"]?.takeIf { it.isNotBlank() } ?: return null
            val alpha = map["alpha"]?.toFloatOrNull() ?: return null
            return StrippingRecord(
                serialNumber = serial,
                thoriumIntoUranium = alpha,
                thoriumIntoPotassium = map["beta"]?.toFloatOrNull() ?: 0f,
                uraniumIntoPotassium = map["gamma"]?.toFloatOrNull() ?: 0f,
                measuredAtMillis = map["measuredAtMillis"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}
