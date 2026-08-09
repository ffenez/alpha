package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.radiacode.ui.components.PixelBox
import app.radiacode.ui.components.PixelTag
import app.radiacode.ui.components.StatusLine
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens

/** Pixel placeholder for tabs whose engines are later roadmap stages. */
@Composable
fun PlaceholderScreen(title: String, planned: List<String>) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelDimens.space4),
        verticalArrangement = Arrangement.spacedBy(PixelDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = type.heading, color = colors.text)
            Spacer(Modifier.weight(1f))
            PixelTag(text = "в разработке")
        }
        PixelBox(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                StatusLine(text = "экран ещё строится", cursor = true, color = colors.textSecondary)
                Text(
                    text = "Здесь появится:",
                    style = type.body,
                    color = colors.textSecondary,
                )
                planned.forEach { line ->
                    Text(
                        text = "· $line",
                        style = type.body,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun MapPlaceholder() = PlaceholderScreen(
    title = "КАРТА",
    planned = listOf(
        "трек прогулки с точками измерений",
        "окраска точек по мощности дозы или CPS",
        "участки маршрута с устойчивым повышением",
    ),
)
