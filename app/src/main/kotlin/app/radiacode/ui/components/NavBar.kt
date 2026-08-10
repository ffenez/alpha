package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens

/** The five data screens (SPEC navigation: Home/Search/Spectrum/Map/History). */
enum class AppTab(val title: String) {
    HOME("ДОМ"),
    SEARCH("ПОИСК"),
    SPECTRUM("СПЕКТР"),
    MAP("КАРТА"),
    HISTORY("ИСТОРИЯ"),
}

private val AppTab.icon: ImageVector
    get() = when (this) {
        AppTab.HOME -> PixelIcons.Home
        AppTab.SEARCH -> PixelIcons.Search
        AppTab.SPECTRUM -> PixelIcons.Spectrum
        AppTab.MAP -> PixelIcons.Map
        AppTab.HISTORY -> PixelIcons.History
    }

/**
 * Bottom pixel nav: hard 3dp top frame, five equal cells. The selected cell
 * carries a 4dp accent block at the top and lights up; state is never color
 * alone — the block is a shape cue and the label brightens and bolds.
 */
@Composable
fun PixelNavBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPixelColors.current
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        Box(Modifier.fillMaxWidth().height(PixelDimens.frame).background(colors.frame))
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) colors.accent else colors.textMuted
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .semantics { this.selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                ) {
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 4.dp)
                            .background(if (isSelected) colors.accent else colors.surface),
                    )
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.padding(top = 4.dp).size(24.dp),
                    )
                    Text(
                        text = tab.title,
                        style = LocalPixelTypography.current.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
