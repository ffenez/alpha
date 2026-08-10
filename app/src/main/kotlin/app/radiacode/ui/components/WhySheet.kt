package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.ui.logic.WhyInput
import app.radiacode.ui.logic.WhyExplain
import app.radiacode.ui.logic.WhyLine
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * «Почему?» (spec §17): everything that produced the verdict on the Монитор,
 * line by line, each with its certainty tag. No score, no summary — if a line
 * cannot be shown honestly it says so («не оценивается») instead of guessing.
 */
@Composable
fun WhySheet(input: WhyInput, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = "Почему такой вывод",
                    style = type.title,
                    color = colors.ink,
                )
                Text(
                    text = WhyExplain.verdict(input.status),
                    style = type.label,
                    color = colors.ink2,
                )
                AppDivider()
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    WhyExplain.lines(input).forEach { WhyRow(it) }
                }
                AppDivider()
                Text(
                    text = "изм. — измерено прибором · расчёт — арифметика из измерений · " +
                        "стат. — вывод статистической модели",
                    style = type.footnote,
                    color = colors.muted,
                )
                AppButton(
                    text = "Понятно",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WhyRow(line: WhyLine) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = line.label, style = type.bodySmall, color = colors.ink2)
            Spacer(Modifier.weight(1f))
            EvidenceTag(line.evidence, Modifier.padding(end = 6.dp))
            Text(text = line.value, style = type.value, color = colors.ink)
        }
        line.note?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
    }
}
