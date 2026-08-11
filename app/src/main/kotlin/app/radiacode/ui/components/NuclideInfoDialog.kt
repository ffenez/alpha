package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.analysis.Nuclide
import app.radiacode.ui.logic.NuclideCard
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Offline reference card for a candidate nuclide (спец §12): everything is
 * bundled, nothing is fetched. The card describes the **nuclide** — it never
 * claims the nuclide was found, and the honest framing sits above the data,
 * not in a footnote.
 */
@Composable
fun NuclideInfoDialog(nuclide: Nuclide, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = NuclideCard.title(nuclide),
                        style = type.title,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Chip(text = "справка", color = colors.ink2)
                }
                Text(
                    text = NuclideCard.FRAMING,
                    style = type.bodySmall,
                    color = colors.ink2,
                )

                AppDivider()
                InfoLine("происхождение", NuclideCard.originLine(nuclide))
                InfoLine("период полураспада", nuclide.halfLife)
                InfoLine("распад", nuclide.decay)

                AppDivider()
                Text(text = "Гамма-линии".uppercase(), style = type.labelSmall, color = colors.ink2)
                nuclide.lines.forEach { line ->
                    Text(
                        text = NuclideCard.lineText(line.energyKeV, line.intensityPercent),
                        style = type.valueSmall,
                        color = colors.ink,
                    )
                }

                AppDivider()
                Text(text = "Где встречается".uppercase(), style = type.labelSmall, color = colors.ink2)
                Text(text = nuclide.everyday, style = type.bodySmall, color = colors.ink2)

                AppDivider()
                Text(
                    text = "Что подтвердило бы совпадение".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Text(text = nuclide.confirmation, style = type.bodySmall, color = colors.ink2)
                Text(text = NuclideCard.LIMITS, style = type.bodySmall, color = colors.muted)

                Text(text = NuclideCard.SOURCE, style = type.footnote, color = colors.muted)
                AppButton(text = "Закрыть", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column {
        Text(text = label, style = type.footnote, color = colors.muted)
        Text(text = value, style = type.bodySmall, color = colors.ink)
    }
    Spacer(Modifier)
}
