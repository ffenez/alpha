package app.radiacode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import app.radiacode.ui.theme.LocalAppMetrics
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Плитка вспомогательной величины: подпись, значение и при нужде одна тихая
 * строка под ним.
 *
 * Жила приватно на Главной, а нужна везде, где рядом стоят два-три числа
 * одного порядка важности. Две одинаковые с виду плитки, собранные в разных
 * местах по-своему, расходятся при первой же правке отступа — поэтому форма
 * одна на всё приложение.
 */
data class MetricTile(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
    val note: String? = null,
)

@Composable
fun MetricTileBox(tile: MetricTile, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
            .background(colors.surface2)
            .padding(horizontal = Dimens.space2, vertical = Dimens.space2),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = tile.label.uppercase(), style = type.overline, color = colors.muted, maxLines = 1)
        Text(
            text = tile.value,
            style = type.value,
            color = tile.valueColor ?: colors.ink,
            maxLines = 1,
        )
        tile.note?.let {
            Text(text = it, style = type.footnote, color = colors.muted, maxLines = 1)
        }
    }
}
