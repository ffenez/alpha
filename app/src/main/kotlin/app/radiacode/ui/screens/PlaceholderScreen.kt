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
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/** Placeholder for tabs whose engines are later roadmap stages. */
@Composable
fun PlaceholderScreen(title: String, planned: List<String>) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(text = title, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Chip(text = "в разработке")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = "Экран ещё строится. Здесь появится:",
                    style = type.body,
                    color = colors.ink2,
                )
                planned.forEach { line ->
                    Text(
                        text = "· $line",
                        style = type.body,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
fun MapPlaceholder() = PlaceholderScreen(
    title = "Карта",
    planned = listOf(
        "трек прогулки с точками измерений",
        "окраска точек по мощности дозы или CPS",
        "участки маршрута с устойчивым повышением",
    ),
)
