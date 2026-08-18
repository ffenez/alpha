package app.alpha.ui.logic

import app.alpha.ui.text.Strings

/**
 * Каким рисунком прибор показывает отношение — циферблатом или прямой шкалой.
 *
 * Утверждение у них одно и то же: положение на логарифмической шкале
 * ([ArcScale]). Различается только форма, поэтому выбор — вопрос привычки и
 * места на экране, а не точности: циферблат читается на вытянутой руке и
 * занимает круг, прямая шкала занимает строку и оставляет экран графику.
 */
enum class InstrumentIndicator(val id: String) {
    DIAL("dial"),
    BAR("bar"),
    ;

    fun title(s: Strings): String = when (this) {
        DIAL -> s.indicatorDial
        BAR -> s.indicatorBar
    }

    companion object {
        /** Неизвестное значение — циферблат: он и есть прибор по умолчанию. */
        fun of(id: String?): InstrumentIndicator = entries.firstOrNull { it.id == id } ?: DIAL
    }
}
