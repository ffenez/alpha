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
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SearchCatalogue
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
    val strings = LocalStrings.current
    val t = SearchCatalogue.of(strings.language)
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.whyTitle, style = type.title, color = colors.ink)
                Text(text = headline, style = type.label, color = colors.ink2)
                Text(text = explanation, style = type.bodySmall, color = colors.muted)
                AppDivider()
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    SearchVerdict.whyLines(input, strings, t).forEach { WhyRow(it) }
                }
                AppDivider()
                // Граница режима переехала сюда из справки «i»: справку сняли
                // с экрана целиком, но это утверждение — не пояснение
                // интерфейса, а ограничение вывода, и жить оно обязано рядом с
                // самим выводом.
                Text(
                    text = t.infoLimit,
                    style = type.footnote,
                    color = colors.muted,
                )
                Hint(
                    text = t.evidenceLegend,
                )
                AppButton(
                    text = t.understood,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
