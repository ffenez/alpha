package app.radiacode.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/** One statgrid cell: mono value over a muted uppercase key. */
data class StatCell(val value: String, val key: String)

/**
 * The statistics strip under a chart (design-language.md: every chart
 * carries its summary numbers). Equal columns above a hairline.
 */
@Composable
fun StatGrid(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(modifier = modifier.fillMaxWidth()) {
        AppDivider()
        Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
            cells.forEach { cell ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = cell.value,
                        style = type.value,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = cell.key.uppercase(),
                        style = type.overline,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
