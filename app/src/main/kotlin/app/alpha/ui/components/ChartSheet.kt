package app.alpha.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.Motion

/** Панель никогда не занимает больше этой доли экрана — поле остаётся видно. */
private const val SHEET_MAX_FRACTION = 0.72f

/**
 * Общая оболочка панелей поверх графика: затемнение, заголовок, крестик,
 * «назад» и потолок высоты.
 *
 * Панель не двигает поле и не может съесть весь экран: под ней всегда видна
 * часть картинки, к которой она относится. Касание мимо панели закрывает её —
 * тем же движением, каким человек возвращается к графику.
 *
 * Одна на все полноэкранные поля (график дозы/счёта/жёсткости и спектр):
 * вторичное везде живёт панелью поверх, а не полосой мелкого текста под полем.
 */
@Composable
fun BoxScope.ChartSheet(
    open: Boolean,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * SHEET_MAX_FRACTION).dp
    BackHandler(enabled = open) { onClose() }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(Motion.fast()),
        exit = fadeOut(Motion.fast()),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.bg.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(Motion.normal()) + expandVertically(Motion.springy()),
        exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.springy()),
        modifier = Modifier.align(Alignment.BottomStart),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .background(colors.surface)
                // Панель лежит ПОВЕРХ поля, поэтому забирает касания себе:
                // иначе прокрутка её содержимого двигала бы окно под ней.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(vertical = Dimens.space2)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.space3),
            ) {
                Text(text = title.uppercase(), style = type.labelSmall, color = colors.ink2)
                Spacer(Modifier.weight(1f))
                AppCloseButton(onClose = onClose)
            }
            content()
        }
    }
}
