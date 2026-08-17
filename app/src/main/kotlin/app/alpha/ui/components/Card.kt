package app.alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppColors

/**
 * The terminal card: surface fill, 1dp hairline border, 14dp radius, no
 * shadow (design-language.md — depth comes from the border and surface
 * steps, not elevation).
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Dimens.space3,
    background: Color = LocalAppColors.current.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(LocalAppMetrics.current.radiusCard)
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(LocalAppMetrics.current.border, LocalAppColors.current.line, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Hairline divider in line color. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LocalAppMetrics.current.border)
            .background(LocalAppColors.current.line),
    )
}
