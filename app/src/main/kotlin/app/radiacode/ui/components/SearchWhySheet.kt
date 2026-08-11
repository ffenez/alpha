package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.ui.logic.SearchVerdict
import app.radiacode.ui.logic.SearchWhyInput
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * The research layer of Поиск (search redesign §4): the same verdict, with
 * every number it stands on — both windows, both counts, the criterion, the
 * significance, the dispersion of the stream and the state of the connection.
 *
 * Deliberately the same shape as the Монитор sheet: one place in the app where
 * «Почему?» means «вот всё, на чём стоит вывод», never a summary score.
 */
@Composable
fun SearchWhySheet(
    input: SearchWhyInput,
    headline: String,
    explanation: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = "Почему такой вывод", style = type.title, color = colors.ink)
                Text(text = headline, style = type.label, color = colors.ink2)
                Text(text = explanation, style = type.bodySmall, color = colors.muted)
                AppDivider()
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    SearchVerdict.whyLines(input).forEach { WhyRow(it) }
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
