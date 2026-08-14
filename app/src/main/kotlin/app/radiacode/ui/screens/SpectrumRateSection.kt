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
import app.radiacode.analysis.SpectrogramHistory
import app.radiacode.data.SpectrumPollPolicy
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Segmented
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.SpectrogramCatalogue
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/**
 * Частота записи спектрограммы — Настройки → Прибор.
 *
 * Стояла на самом экране Спектрограммы, где виден её эффект, но занимала там
 * место постоянно ради выбора, который делают один раз: это параметр ОПРОСА
 * ПРИБОРА, а не способ смотреть картинку. Здесь же рядом стоят остальные
 * решения про прибор.
 *
 * Ступень названа своим числом, а не прилагательным. Про батарею не сказано
 * ничего: она не измерена, а счётчики запросов и байт лежат в отладочном
 * отчёте — предупреждение появится после измерения, а не раньше.
 */
@Composable
fun SpectrumRateSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = SpectrogramCatalogue.of(LocalStrings.current.language)
    val scope = rememberCoroutineScope()

    val policy by graph.settings.spectrumPollPolicy
        .collectAsState(initial = SpectrumPollPolicy.DEFAULT)

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
            Text(
                text = t.rateVolume(
                    Uncertainty.num2(SpectrogramHistory.megabytesPerDay(policy.intervalMillis)),
                ),
                style = type.footnote,
                color = colors.muted,
            )
            Text(
                text = t.rateThinning(
                    (SpectrogramHistory.AS_RECORDED_MILLIS / 86_400_000L).toInt(),
                    (SpectrogramHistory.COMPACTED_SLICE_MILLIS / 60_000L).toInt(),
                ),
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}
