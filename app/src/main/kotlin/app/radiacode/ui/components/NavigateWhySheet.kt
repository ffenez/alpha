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
import app.radiacode.ui.logic.WhyLine
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SearchCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * «Почему такой вывод» для «Наведения».
 *
 * Здесь живёт вся терминология, снятая с рабочего экрана: оценочный интервал,
 * порог, окна расчёта, разница между запомненной точкой отсчёта и уровнем,
 * который приложение считает само. Порядок — тот же, что у остальных разборов
 * приложения: **вывод → на чём он стоит → чем он ограничен**.
 *
 * Лист не считает ничего сам: строки собирает чистая
 * [app.radiacode.ui.logic.NavigateVerdict.whyLines], и то, что видит человек,
 * и то, что проверяет тест, — одно и то же.
 */
@Composable
fun NavigateWhySheet(
    /** Направление — тот же вывод, что и на экране, слово в слово. */
    headline: String,
    /** Одна фраза о том, можно ли на вывод опереться; null — нечего сказать. */
    explanation: String?,
    lines: List<WhyLine>,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SearchCatalogue.of(strings.language)

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.navWhyTitle, style = type.title, color = colors.ink)
                Text(text = headline, style = type.label, color = colors.ink2)
                explanation?.let { Hint(text = it, style = type.bodySmall) }
                AppDivider()
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    lines.forEach { WhyRow(it) }
                }
                AppDivider()
                // Граница режима стоит рядом с выводом, а не в справке об
                // интерфейсе: это ограничение самого вывода.
                Hint(text = t.navWhyLimit)
                AppButton(
                    text = t.understood,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
