package app.alpha.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.alpha.AppGraph
import app.alpha.analysis.SpectrogramHistory
import app.alpha.data.SpectrumPollPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.Card
import app.alpha.ui.components.ConfirmDialog
import app.alpha.ui.components.Segmented
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrogramCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/**
 * Спектрограмма в фоне — Настройки → Данные: частота записи и уборка записанного.
 *
 * Стояла на самом экране Спектрограммы, где виден её эффект, но занимала там
 * место постоянно ради выбора, который делают один раз: это параметр ОПРОСА
 * ПРИБОРА, а не способ смотреть картинку. Здесь же рядом стоят остальные
 * решения про прибор.
 *
 * Ступень названа своим числом, а не прилагательным. Про батарею не сказано
 * ничего: она не измерена, а счётчики запросов и байт лежат в отладочном
 * отчёте — предупреждение появится после измерения, а не раньше.
 *
 * Здесь же живёт «очистить спектрограмму»: это решение про ХРАНЕНИЕ, и стоять
 * оно должно рядом с частотой записи и объёмом истории, а не кнопкой на экране
 * картинки, где его нажимают случайно.
 */
@Composable
fun SpectrumRateSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = SpectrogramCatalogue.of(LocalStrings.current.language)
    val scope = rememberCoroutineScope()

    val policy by graph.settings.spectrumPollPolicy
        .collectAsState(initial = SpectrumPollPolicy.DEFAULT)
    val strings = LocalStrings.current
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        ConfirmDialog(
            title = t.clearConfirmTitle,
            body = t.clearConfirmBody,
            confirmText = strings.delete,
            onConfirm = {
                confirmClear = false
                scope.launch { graph.spectrogramStore.clearHistory() }
            },
            onDismiss = { confirmClear = false },
        )
    }

    val options = listOf(
        SpectrumPollPolicy.EVERY_5_S to t.rateDetailed,
        SpectrumPollPolicy.EVERY_30_S to t.rateBalanced,
        SpectrumPollPolicy.EVERY_10_MIN to t.rateEconomy,
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(text = t.rateTitle.uppercase(), style = type.labelSmall, color = colors.ink2)
            Segmented(
                options = options.map { it.second },
                selectedIndex = options.indexOfFirst { it.first == policy }.coerceAtLeast(0),
                onSelect = { index ->
                    scope.launch { graph.settings.setSpectrumPollPolicy(options[index].first) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(
                text = t.rateVolume(
                    Uncertainty.num2(SpectrogramHistory.megabytesPerDay(policy.intervalMillis)),
                ),
                style = type.footnote,
                color = colors.muted,
            )
            Hint(
                text = t.rateThinning(
                    (SpectrogramHistory.AS_RECORDED_MILLIS / 86_400_000L).toInt(),
                    (SpectrogramHistory.COMPACTED_SLICE_MILLIS / 60_000L).toInt(),
                ),
                style = type.footnote,
                color = colors.muted,
            )
            AppButton(text = t.clearHistory, onClick = { confirmClear = true })
        }
    }
}
