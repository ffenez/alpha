package app.alpha.ui.logic

import app.alpha.analysis.ScaleCorrection
import app.alpha.data.JsonMap

/**
 * Принятая поправка энергетической шкалы.
 *
 * Хранятся сами коэффициенты, а не линии: поправка применяется к каждому
 * спектру, который открывают, включая старые и чужие, — пересчитывать её по
 * линиям того спектра означало бы разную шкалу у разных спектров, то есть
 * ровно ту молчаливую подгонку, от которой [ScaleCorrection] отказывается.
 *
 * Остатки «до» и «после» хранятся не ради красоты: по ним экран показывает,
 * что именно было принято, а человек может решить снять поправку.
 */
data class ScaleCorrectionRecord(
    val offsetKeV: Double,
    val gain: Double,
    val residualBeforeKeV: Double,
    val residualAfterKeV: Double,
    /** По скольким линиям посчитана. */
    val referenceCount: Int,
    /** Когда принята, мс эпохи. */
    val acceptedAtMillis: Long,
) {

    /** Поправка для расчёта; линии в неё не переносятся — они уже сыграли. */
    fun correction(): ScaleCorrection = ScaleCorrection(
        offsetKeV = offsetKeV,
        gain = gain,
        references = emptyList(),
        residualBeforeKeV = residualBeforeKeV,
        residualAfterKeV = residualAfterKeV,
    )

    fun encode(): String = JsonMap.of(
        "offsetKeV" to offsetKeV,
        "gain" to gain,
        "residualBeforeKeV" to residualBeforeKeV,
        "residualAfterKeV" to residualAfterKeV,
        "referenceCount" to referenceCount,
        "acceptedAtMillis" to acceptedAtMillis,
    )

    companion object {

        fun of(correction: ScaleCorrection, atMillis: Long) = ScaleCorrectionRecord(
            offsetKeV = correction.offsetKeV,
            gain = correction.gain,
            residualBeforeKeV = correction.residualBeforeKeV,
            residualAfterKeV = correction.residualAfterKeV,
            referenceCount = correction.references.size,
            acceptedAtMillis = atMillis,
        )

        fun decode(raw: String?): ScaleCorrectionRecord? {
            val map = JsonMap.decode(raw)
            if (map.isEmpty()) return null
            val gain = map["gain"]?.toDoubleOrNull() ?: return null
            val offset = map["offsetKeV"]?.toDoubleOrNull() ?: return null
            if (!gain.isFinite() || !offset.isFinite() || gain <= 0.0) return null
            return ScaleCorrectionRecord(
                offsetKeV = offset,
                gain = gain,
                residualBeforeKeV = map["residualBeforeKeV"]?.toDoubleOrNull() ?: 0.0,
                residualAfterKeV = map["residualAfterKeV"]?.toDoubleOrNull() ?: 0.0,
                referenceCount = map["referenceCount"]?.toIntOrNull() ?: 0,
                acceptedAtMillis = map["acceptedAtMillis"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}
