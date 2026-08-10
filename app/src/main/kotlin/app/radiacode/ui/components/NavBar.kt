package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/** The five data screens (SPEC navigation: Home/Search/Spectrum/Map/History). */
enum class AppTab(val title: String) {
    HOME("Главная"),
    SEARCH("Поиск"),
    SPECTRUM("Спектр"),
    MAP("Карта"),
    HISTORY("История"),
}

private val AppTab.icon: ImageVector
    get() = when (this) {
        AppTab.HOME -> AppIcons.Home
        AppTab.SEARCH -> AppIcons.Search
        AppTab.SPECTRUM -> AppIcons.Spectrum
        AppTab.MAP -> AppIcons.Map
        AppTab.HISTORY -> AppIcons.History
    }

/**
 * Bottom nav: hairline top border, surface fill, thin-line icons. The
 * active item switches to the data-text color; the label is always present,
 * so state is not color alone. [tabs] comes from the interface customization
 * (`ui/logic/NavConfig`): Главная first, then the visible tabs in the
 * user's order.
 */
@Composable
fun NavBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<AppTab> = AppTab.entries,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        AppDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) colors.dataText else colors.muted
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics { this.selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = tab.title,
                        style = LocalAppTypography.current.label.copy(fontSize = 10.sp),
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
