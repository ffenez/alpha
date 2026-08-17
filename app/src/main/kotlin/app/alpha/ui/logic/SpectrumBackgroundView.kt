package app.alpha.ui.logic

/**
 * Что делает картинка спектра с записанным фоном.
 *
 * Три состояния одного вопроса, поэтому и переключатель один: сравнить кривые
 * ([OVERLAY]), вычесть фон ([SUBTRACT]) или смотреть накопление как есть
 * ([NONE]). Два отдельных чипа задавали одно и то же двумя способами и
 * допускали бессмысленную пару «вычитаю и одновременно рисую», то есть одни и
 * те же импульсы дважды.
 */
enum class SpectrumBackgroundView {

    /** Только накопленный спектр. */
    NONE,

    /** Серая кривая записанного фона поверх спектра, приведённая ко времени. */
    OVERLAY,

    /** «− фон»: поканальная разность, отрицательные остатки зажаты нулём. */
    SUBTRACT;

    /** Следующее состояние по кругу: обычный → фон → −фон → обычный. */
    fun next(): SpectrumBackgroundView = when (this) {
        NONE -> OVERLAY
        OVERLAY -> SUBTRACT
        SUBTRACT -> NONE
    }

    val overlay: Boolean get() = this == OVERLAY
    val subtract: Boolean get() = this == SUBTRACT

    companion object {

        /** Состояние из пары флагов вида (порядок разбора важен: вычитание сильнее). */
        fun of(subtract: Boolean, overlay: Boolean): SpectrumBackgroundView = when {
            subtract -> SUBTRACT
            overlay -> OVERLAY
            else -> NONE
        }
    }
}
