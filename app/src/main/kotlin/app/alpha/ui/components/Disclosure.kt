package app.alpha.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Строка раскрытия: название и шеврон, содержимое приезжает по нажатию.
 *
 * Один и тот же приём во всём приложении: подробности, которые нужны редко,
 * живут за одной строкой, а не вываливаются на экран. Компонент общий, потому
 * что раскрытие обязано выглядеть и нажиматься одинаково везде — в справке
 * спектрограммы и в разборе «почему».
 */
@Composable
fun DisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.touchTarget)
                .clickable(onClick = onToggle),
        ) {
            Text(
                text = title,
                style = type.bodySmall,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            DisclosureArrow(expanded = expanded)
        }
        if (expanded) content()
    }
}
