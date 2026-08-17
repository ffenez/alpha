package app.alpha.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindows
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.Card
import app.alpha.ui.logic.EnergyBounds
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrumCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/**
 * Границы спектральных диапазонов — Настройки → Прибор.
 *
 * Стояли кнопкой на самом Спектре, но это ПАРАМЕТР АНАЛИЗА: его задают один
 * раз и потом смотрят результат, а на рабочем экране кнопка занимала место
 * постоянно. Здесь же рядом стоят остальные решения о том, как разбирается
 * спектр прибора.
 *
 * Редактор — тот же самый, что был: границы двигаются прямо по кривой. Кривую
 * он берёт из живого накопления; если прибора нет и накопления тоже, править
 * границы вслепую нельзя — экран честно говорит об этом вместо того, чтобы
 * показать пустое поле.
 */
@Composable
fun SpectralRangesSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = SpectrumCatalogue.of(LocalStrings.current.language)
    val scope = rememberCoroutineScope()

    val raw by graph.settings.energyWindowsRaw.collectAsState(initial = null)
    val specs = remember(raw) { EnergyWindows.parse(raw) }
    val spectrum by graph.spectrumHub.state.collectAsState()
    var editing by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = t.windowsTitle.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            Text(
                text = specs.joinToString(" · ") {
                    "${it.startKeV.toInt()}–${it.endKeV.toInt()}"
                },
                style = type.value,
                color = colors.ink,
            )
            Hint(text = t.rangesSettingsNote)

            val counts = spectrum.spectrum?.counts
            if (counts == null) {
                Text(text = t.boundsNeedSpectrum, style = type.footnote, color = colors.warn)
            } else {
                AppButton(
                    text = t.boundsEditorTitle,
                    onClick = { editing = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val live = spectrum.spectrum
    if (editing && live != null) {
        BoundsEditorDialog(
            counts = live.counts,
            calibration = EnergyCalibration(live.a0, live.a1, live.a2),
            specs = specs,
            t = t,
            onDismiss = { editing = false },
            onApply = { bounds ->
                scope.launch {
                    graph.settings.setEnergyWindowsRaw(EnergyBounds.formatStored(bounds))
                }
                editing = false
            },
        )
    }
}
