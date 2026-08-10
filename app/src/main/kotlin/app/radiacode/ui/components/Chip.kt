package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Small bordered chip: surface fill, hairline border, 9dp radius, mono
 * label. [dot] prepends a 7dp status dot (color + adjacent words carry the
 * status together — never color alone). [onClick] makes it tappable.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAppColors.current.ink2,
    dot: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(Dimens.radiusChip)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(Dimens.border, colors.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        if (dot != null) StatusDot(dot)
        Text(
            text = text,
            style = LocalAppTypography.current.axis,
            color = color,
            maxLines = 1,
        )
    }
}

/** 7dp status dot; always accompanied by words nearby. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(7.dp).clip(CircleShape).background(color))
}
