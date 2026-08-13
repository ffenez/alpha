package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.radiacode.AppGraph
import app.radiacode.data.RawRetention
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
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
                onSelect = { index ->
                    scope.launch {
                        graph.settings.setRawRetentionDays(RawRetention.OPTIONS[index])
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = strings.retentionNote, style = type.footnote, color = colors.muted)
        }
    }
}
