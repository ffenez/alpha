package app.alpha.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Строка журнала — одна на все виды записей.
 *
 * ## Что в ней есть и в каком порядке
 *
 * ```text
 * [превью] Название · состояние            ⋮
 *          время · длительность
 *          главная величина · вторичная
 * ```
 *
 * Первая строка отвечает «что это», вторая — «когда и сколько», третья — «что
 * там получилось». Так сессия, маршрут, снимок спектра и исследование
 * различаются содержанием, а не устройством: глазу не нужно заново
 * разбираться в вёрстке каждого вида.
 *
 * ## Ни стрелки, ни второго способа открыть
 *
 * Раньше у строки была и стрелка «›», и «⋮»: два одинаково выглядящих
 * приглашения, из которых одно открывало запись, а второе — меню. Открывает
 * теперь вся строка, а «⋮» отвечает только за действия.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Третья строка: величины, профиль или состояние записи. */
    detail: String? = null,
    /** Слово о состоянии рядом с названием: «идёт», «прервана». */
    status: String? = null,
    statusColor: Color? = null,
    onLongClick: () -> Unit = {},
    /** Не null — журнал в режиме выбора, и это галочка строки. */
    check: Boolean? = null,
    /** Квадрат превью слева: карта маршрута; у сессии его нет. */
    preview: (@Composable () -> Unit)? = null,
    menu: List<EntityMenuItem> = emptyList(),
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = Dimens.space2),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (check != null) CheckMark(selected = check)
        if (preview != null) {
            Box(modifier = Modifier.size(PREVIEW_SIZE)) { preview() }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = type.label,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (status != null) {
                    Spacer(Modifier.size(Dimens.space2))
                    StatusRow(text = status, color = statusColor ?: colors.ok)
                }
            }
            Text(
                text = subtitle,
                style = type.footnote,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = type.footnote,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (menu.isNotEmpty()) EntityMenuButton(menu = menu)
    }
}

/**
 * Превью маршрута: узнать след по ногтю, а не измерять по нему.
 *
 * **Инженерный параметр**: 64 dp — полторы строки текста рядом. Больше —
 * картинка начинает спорить с названием, меньше — форма перестаёт читаться.
 */
val PREVIEW_SIZE = 64.dp
