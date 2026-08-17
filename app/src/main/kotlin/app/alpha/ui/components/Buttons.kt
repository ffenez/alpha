package app.alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Bordered terminal button: surface-2 fill, hairline border, 10dp radius.
 * [primary] fills with data teal — the single main action of a screen.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(LocalAppMetrics.current.radiusButton)

    val background: Color
    val borderColor: Color
    val foreground: Color
    when {
        !enabled -> {
            background = Color.Transparent
            borderColor = colors.line
            foreground = colors.muted
        }
        primary -> {
            background = colors.data
            borderColor = colors.data
            foreground = colors.onData
        }
        else -> {
            background = colors.surface2
            borderColor = colors.line
            foreground = colors.ink
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .clip(shape)
            .background(background)
            .border(LocalAppMetrics.current.border, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.space3, vertical = Dimens.space2),
        contentAlignment = Alignment.Center,
    ) {
        // Подпись кнопки НЕ обрезается посреди слова.
        //
        // Полевой дефект: при увеличенном системном шрифте «Сохранить в
        // историю» превращалось в «Сохранить в» — одна строка с обрезкой по
        // краю выглядит как другое, более короткое действие, а не как
        // не поместившийся текст. Кнопка теперь РАСТЁТ во вторую строку, и
        // только если не хватило и её, обрезка честно помечается многоточием.
        Text(
            text = text,
            style = LocalAppTypography.current.label,
            color = foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Segmented mode switch (the mockup `.periods` control): surface-2 track,
 * the active option sits on a bordered surface pill. Mono labels — modes are
 * data-adjacent. State is never color alone: the active option carries the
 * fill and border shape cue.
 */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
    /**
     * Прокручиваемый ряд вместо равных долей.
     *
     * Пять подписей, поделивших узкий экран поровну, превращаются в огрызки в
     * два этажа. Прокрутка честнее: подпись остаётся подписью, а часть ряда
     * уезжает за край — и это видно.
     */
    scrollable: Boolean = false,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
            .background(colors.surface2)
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val isEnabled = enabled(index)
            val shape = RoundedCornerShape(LocalAppMetrics.current.radiusSegment)
            Box(
                modifier = Modifier
                    .then(if (scrollable) Modifier else Modifier.weight(1f))
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(colors.surface)
                                .border(LocalAppMetrics.current.border, colors.line, shape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = isEnabled) { onSelect(index) }
                    .padding(vertical = 6.dp, horizontal = if (scrollable) 12.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = type.axis,
                    color = when {
                        !isEnabled -> colors.muted
                        selected -> colors.ink
                        else -> colors.ink2
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
