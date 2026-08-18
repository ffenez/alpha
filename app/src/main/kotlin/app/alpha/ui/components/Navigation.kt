package app.alpha.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors

/**
 * Возврат на предыдущий экран — один компонент на всё приложение.
 *
 * ## Почему стрелка, а не кнопка «← Назад»
 *
 * Большая кнопка означает частое действие и занимает место главного. Выход с
 * экрана делается системным жестом или кнопкой устройства, а экранная стрелка —
 * подстраховка: мал только знак, цель нажатия остаётся [Dimens.touchTarget].
 * Пока каждый экран собирал возврат сам, он был то кнопкой во всю ширину, то
 * стрелкой, и одно и то же действие приходилось искать заново.
 *
 * ## Назад ≠ закрыть
 *
 * [AppBackButton] — навигация: экран уходит, предыдущий возвращается.
 * [AppCloseButton] — выход из наложенного контекста (полный экран, лист,
 * диалог): под ним остаётся то же место, откуда его открыли. Механически
 * объединять их нельзя — это разные обещания, и знак у каждого свой.
 */
@Composable
fun AppBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    GlyphButton(BACK_GLYPH, LocalStrings.current.back, onBack, modifier)
}

/** Закрыть наложенный контекст: полный экран, лист, диалог. */
@Composable
fun AppCloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    GlyphButton(CLOSE_GLYPH, LocalStrings.current.close, onClose, modifier)
}

/**
 * Знак в чипе с пальцевой целью нажатия: сам чип остаётся маленьким, область
 * вокруг него — нет.
 */
@Composable
private fun GlyphButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Dimens.touchTarget)
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        Chip(text = glyph, color = LocalAppColors.current.ink2)
    }
}

/** Стрелка возврата: один знак во всём приложении. */
const val BACK_GLYPH = "←"

/** Крест закрытия: один знак во всём приложении — «✕», не «×» и не «x». */
const val CLOSE_GLYPH = "✕"
