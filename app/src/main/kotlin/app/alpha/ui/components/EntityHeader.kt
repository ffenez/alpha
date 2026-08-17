package app.alpha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/** Пункт меню «⋮»: редкое действие над самой записью. */
data class EntityMenuItem(
    val title: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Шапка экрана записи: `← Название · дата · длительность · ⋮`.
 *
 * ## Одна шапка на все записи
 *
 * Сессия, маршрут, спектр и опыт — разные по содержанию, но одинаковые по
 * устройству: у каждого есть имя, время и набор редких действий. Пока каждый
 * экран собирал верх сам, «назад» был то большой кнопкой, то стрелкой, а
 * действия — то кнопкой рядом с заголовком, то диалогом; человеку приходилось
 * заново искать одно и то же на каждом экране.
 *
 * ## Почему «назад» — не большая кнопка
 *
 * Большая кнопка означает частое действие. Выход с экрана делается системным
 * жестом или кнопкой «назад» устройства; экранная стрелка — подстраховка, и
 * место главного действия она занимать не должна. Размер цели при этом остаётся
 * пальцевым — уменьшилась подпись, а не область нажатия.
 */
@Composable
fun EntityHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** null — экран открыт вкладкой, и уходить с него некуда. */
    onBack: (() -> Unit)? = null,
    /** Вторая строка: время, длительность, состояние — то, что уточняет имя. */
    subtitle: String? = null,
    menu: List<EntityMenuItem> = emptyList(),
    /** Что стоит между заголовком и «⋮» — например, метка «идёт запись». */
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var open by remember { mutableStateOf(false) }
    var menuHeight by remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { Dimens.space1.roundToPx() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        if (onBack != null) Chip(text = "←", color = colors.ink2, onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
        if (menu.isNotEmpty()) {
            Box(modifier = Modifier.onSizeChanged { menuHeight = it.height }) {
                Chip(text = MENU_GLYPH, color = colors.ink2, onClick = { open = true })
                AppMenu(
                    expanded = open,
                    onDismiss = { open = false },
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, menuHeight + gap),
                ) {
                    for (item in menu) {
                        AppMenuItem(
                            text = item.title,
                            enabled = item.enabled,
                            onClick = { open = false; item.onClick() },
                        )
                    }
                }
            }
        }
    }
}

/** То же меню «⋮» без шапки — для строки списка. */
@Composable
fun EntityMenuButton(menu: List<EntityMenuItem>, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { Dimens.space1.roundToPx() }
    Box(modifier = modifier.onSizeChanged { height = it.height }) {
        Chip(text = MENU_GLYPH, color = colors.ink2, onClick = { open = true })
        AppMenu(
            expanded = open,
            onDismiss = { open = false },
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, height + gap),
        ) {
            for (item in menu) {
                AppMenuItem(
                    text = item.title,
                    enabled = item.enabled,
                    onClick = { open = false; item.onClick() },
                )
            }
        }
    }
}

/** Три точки: одно и то же обозначение редких действий во всём приложении. */
const val MENU_GLYPH = "⋮"
