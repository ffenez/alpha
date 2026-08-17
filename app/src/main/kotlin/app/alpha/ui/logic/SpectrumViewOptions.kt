package app.alpha.ui.logic

import androidx.compose.runtime.Immutable
import app.alpha.analysis.EnergyWindow

/**
 * Что именно человек сейчас рассматривает на спектре: режим картинки и её
 * зум.
 *
 * Полноэкранный режим открывается ТАПОМ ПО ГРАФИКУ, и картинка под пальцем не
 * имеет права смениться от этого: «− фон» остаётся вычтенным, сглаживание —
 * включённым, а окно — тем же участком шкалы. Поэтому состояние вкладки
 * уезжает на полный экран целиком, одним значением.
 */
@Immutable
data class SpectrumViewOptions(
    val minusBackground: Boolean = false,
    /** Серая кривая записанного фона поверх спектра. */
    val overlayBackground: Boolean = false,
    val smoothing: Boolean = false,
    /** Границы зума; startKeV ≥ endKeV означает «вся шкала». */
    val startKeV: Float = 0f,
    val endKeV: Float = 0f,
    /**
     * Энергия, отмеченная из справки о нуклиде; 0 — отметки нет.
     *
     * Отметка уезжает на полный экран по той же причине, что режим и окно: её
     * поставили, чтобы РАССМОТРЕТЬ место линии, и первое, что для этого делают,
     * — открывают график крупнее. Время жизни отметки на новом поле считается
     * заново: человек только что попросил показать её ещё раз.
     */
    val highlightKeV: Float = 0f,
) {
    /** Что делает картинка с записанным фоном — один вопрос вместо двух флагов. */
    fun backgroundView(): SpectrumBackgroundView =
        SpectrumBackgroundView.of(subtract = minusBackground, overlay = overlayBackground)

    /** Окно зума или null, если рассматривают всю шкалу. */
    fun window(): EnergyWindow? =
        if (endKeV > startKeV) EnergyWindow(startKeV, endKeV) else null

    /** Отмеченная энергия или null: энергия линии всегда больше нуля. */
    fun highlight(): Float? = highlightKeV.takeIf { it > 0f }

    companion object {
        fun of(
            minusBackground: Boolean,
            overlayBackground: Boolean = false,
            smoothing: Boolean,
            window: EnergyWindow?,
            highlightKeV: Float? = null,
        ): SpectrumViewOptions = SpectrumViewOptions(
            minusBackground = minusBackground,
            overlayBackground = overlayBackground,
            smoothing = smoothing,
            startKeV = window?.startKeV ?: 0f,
            endKeV = window?.endKeV ?: 0f,
            highlightKeV = highlightKeV ?: 0f,
        )
    }
}
