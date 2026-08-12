package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.Motion
import app.radiacode.ui.theme.LocalAppTypography

/**
 * One status line: dot + bold words + optional muted context. Status is
 * never conveyed by color alone (design-language.md) — the words carry the
 * meaning, the dot and color assist.
 */
@Composable
fun StatusRow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.ink2,
    sub: String? = null,
) {
    val type = LocalAppTypography.current
    // Статус меняется редко и означает многое — резкая смена цвета читается
    // как мигание, а не как переход.
    val tone by animateColorAsState(color, Motion.normal(), label = "statusTone")
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        StatusDot(tone)
        Text(text = text, style = type.label, color = color)
        if (sub != null) {
            Text(
                text = sub,
                style = type.footnote,
                color = LocalAppColors.current.ink2,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
