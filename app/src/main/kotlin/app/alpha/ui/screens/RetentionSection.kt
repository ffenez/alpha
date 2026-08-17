package app.alpha.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.alpha.AppGraph
import app.alpha.data.RawRetention
import app.alpha.ui.components.Hint
import app.alpha.ui.components.Card
import app.alpha.ui.components.Segmented
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/**
 * Срез сырых измерений — Настройки → Профили и фон.
 *
 * Единственное МЕСТО автоматического удаления данных в приложении, и потому
 * оно существует только как явный выбор владельца с умолчанием «хранить всё»
 * (обоснование — в [RawRetention]).
 */
@Composable
fun RetentionSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val days by graph.settings.rawRetentionDays
        .collectAsState(initial = RawRetention.KEEP_ALL_DAYS)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = strings.retentionTitle.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            RetentionChoice(days) { chosen ->
                scope.launch { graph.settings.setRawRetentionDays(chosen) }
            }
            Hint(text = strings.retentionNote)
        }
    }
}

/**
 * Строки выбора срока хранения без своей карточки — чтобы их можно было
 * поставить в общую группу «Хранение» рядом с размером данных.
 */
@Composable
fun RetentionRows(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val days by graph.settings.rawRetentionDays
        .collectAsState(initial = RawRetention.KEEP_ALL_DAYS)

    Column(
        modifier = Modifier.padding(horizontal = Dimens.space3, vertical = Dimens.space2),
        verticalArrangement = Arrangement.spacedBy(Dimens.space2),
    ) {
        Text(text = strings.retentionTitle, style = type.body, color = colors.ink)
        RetentionChoice(days) { chosen ->
            scope.launch { graph.settings.setRawRetentionDays(chosen) }
        }
        Text(text = strings.retentionNote, style = type.footnote, color = colors.muted)
    }
}

@Composable
private fun RetentionChoice(days: Int, onSelect: (Int) -> Unit) {
    val strings = LocalStrings.current
    Segmented(
        options = RawRetention.OPTIONS.map {
            if (it == RawRetention.KEEP_ALL_DAYS) {
                strings.retentionKeepAll
            } else {
                strings.retentionDays(it)
            }
        },
        selectedIndex = RawRetention.OPTIONS.indexOf(RawRetention.sanitize(days))
            .coerceAtLeast(0),
        onSelect = { index -> onSelect(RawRetention.OPTIONS[index]) },
        modifier = Modifier.fillMaxWidth(),
    )
}
