package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Меню в языке «Научного терминала»: плоскость, волосяная рамка, никакой тени.
 *
 * ## Зачем своё
 *
 * Системное `DropdownMenu` приносит с собой чужую поверхность: скруглённую
 * карточку с тенью и материаловскими цветами. Рядом с чипами и карточками
 * терминала она читается как кусок другого приложения — глубина здесь задаётся
 * ступенями плоскостей и рамкой, а не подъёмом над экраном.
 *
 * ## Раскрывается ВВЕРХ
 *
 * Меню графика живёт в нижней панели, у самого края экрана: раскрытое вниз, оно
 * упиралось бы в край и переезжало бы на кнопку, из которой открылось. Якорь —
 * сам элемент, к которому меню относится, поэтому связь «нажал здесь — открылось
 * отсюда» видна без анимации.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Куда расти относительно якоря: у правого края экрана — влево. */
    alignment: Alignment = Alignment.BottomStart,
    /**
     * Сдвиг относительно якоря в пикселях. По умолчанию — вверх на шаг сетки;
     * меню, которому есть куда падать вниз, передаёт высоту своего якоря и
     * раскрывается под ним.
     */
    offset: IntOffset? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val colors = LocalAppColors.current
    val metrics = LocalAppMetrics.current
    val gap = with(LocalDensity.current) { Dimens.space2.roundToPx() }
    Popup(
        alignment = alignment,
        // Вверх от якоря, с зазором в один шаг сетки: меню не должно
        // накрывать кнопку, которой его открыли.
        offset = offset ?: IntOffset(0, -gap),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(metrics.radiusCard)
        Column(
            modifier = modifier
                .widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH)
                .clip(shape)
                .background(colors.surface)
                .border(metrics.border, colors.line, shape)
                .padding(vertical = Dimens.space1),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

/**
 * Строка меню: действие слева, его текущее состояние справа.
 *
 * Состояние стоит отдельным столбцом, а не приклеено к названию через точку:
 * «сглаживание · вкл» читается как одно длинное название, а нужное слово —
 * второе. Включённое состояние подсвечено цветом данных: то же правило, что у
 * чипов панели, — подсвечено значит включено.
 */
@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Текущее состояние пункта: «вкл» / «выкл»; null — у пункта нет состояния. */
    state: String? = null,
    stateOn: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Dimens.space3),
    ) {
        Text(
            text = text,
            style = type.body,
            color = if (enabled) colors.ink else colors.muted,
        )
        if (state != null) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.widthIn(min = Dimens.space3))
            Text(
                text = state,
                style = type.footnoteMono,
                color = if (stateOn) colors.dataText else colors.ink2,
            )
        }
    }
}

/** Заголовок раздела меню — для сетки выбора, а не для списка действий. */
@Composable
fun AppMenuHeader(text: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Text(
        text = text.uppercase(),
        style = type.labelSmall,
        color = colors.ink2,
        modifier = modifier.padding(
            start = Dimens.space3,
            end = Dimens.space3,
            top = Dimens.space2,
            bottom = Dimens.space1,
        ),
    )
}

/** Волосяная черта между группами пунктов. */
@Composable
fun AppMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.space1)
            .height(LocalAppMetrics.current.border)
            .background(LocalAppColors.current.line),
    )
}

private val MENU_MIN_WIDTH = 180.dp
private val MENU_MAX_WIDTH = 280.dp
