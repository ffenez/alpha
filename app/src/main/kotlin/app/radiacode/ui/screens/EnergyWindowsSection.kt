package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.EnergyWindowSpec
import app.radiacode.analysis.EnergyWindows
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.EvidenceTag
import app.radiacode.ui.logic.Evidence
import app.radiacode.ui.logic.ExperimentFormat
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch

/**
 * Энергетические окна (спец §7) на экране Спектр: компактная таблица
 * «окно | имп | имп/с ±σ | доля» и описательный спектральный индекс
 * R_low/R_high.
 *
 * Границы окон — параметр анализа: они редактируются, хранятся в настройках
 * и уезжают в отчёт эксперимента. UI обязан повторять, что индекс описывает
 * состав спектра и НЕ является мерой опасности (спец §7), а окна — не
 * физические категории излучения.
 */
@Composable
fun EnergyWindowsCard(
    graph: AppGraph,
    counts: List<Int>,
    durationSeconds: Long,
    calibration: EnergyCalibration,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    val raw by graph.settings.energyWindowsRaw.collectAsState(initial = null)
    val specs = remember(raw) { EnergyWindows.parse(raw) }
    var editing by remember { mutableStateOf(false) }

    val analysis = remember(counts, durationSeconds, calibration, specs) {
        EnergyWindows.analyze(counts, durationSeconds, calibration, specs)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Энергетические окна".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                EvidenceTag(Evidence.CALCULATED, Modifier.padding(start = 6.dp))
                Spacer(Modifier.weight(1f))
                Chip(text = "границы…", color = colors.ink2, onClick = { editing = true })
            }

            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                WindowHeader("окно, кэВ", 1.2f)
                WindowHeader("имп", 0.9f)
                WindowHeader("имп/с ±σ", 1.4f)
                WindowHeader("доля", 0.7f)
            }
            AppDivider()
            analysis.windows.forEachIndexed { index, window ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    WindowCell(ExperimentFormat.windowLabel(window.spec), 1.2f, colors.ink)
                    WindowCell(window.counts.toString(), 0.9f, colors.ink)
                    WindowCell(
                        if (window.isEmpty) "—" else ExperimentFormat.windowRate(window),
                        1.4f,
                        colors.ink,
                    )
                    WindowCell(
                        if (window.isEmpty) "—" else ExperimentFormat.windowShare(window),
                        0.7f,
                        colors.ink2,
                    )
                }
                if (index < analysis.windows.size - 1) AppDivider()
            }

            val index = analysis.index
            if (index != null) {
                AppDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                ) {
                    Text(
                        text = "индекс " + ExperimentFormat.indexCaption(index),
                        style = type.valueSmall,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = ExperimentFormat.indexLabel(index),
                        style = type.valueSmall,
                        color = colors.ink,
                    )
                }
            }

            Text(
                text = ExperimentFormat.INDEX_NOTE,
                style = type.footnote,
                color = colors.muted,
            )
            Text(
                text = ExperimentFormat.WINDOWS_EDGE_NOTE,
                style = type.footnote,
                color = colors.muted,
            )
        }
    }

    if (editing) {
        EnergyWindowsDialog(
            specs = specs,
            onDismiss = { editing = false },
            onApply = { next ->
                scope.launch { graph.settings.setEnergyWindowsRaw(EnergyWindows.format(next)) }
                editing = false
            },
            onReset = {
                scope.launch { graph.settings.setEnergyWindowsRaw(null) }
                editing = false
            },
        )
    }
}

/** Редактор границ: три пары полей, честный отказ вместо тихой правки. */
@Composable
private fun EnergyWindowsDialog(
    specs: List<EnergyWindowSpec>,
    onDismiss: () -> Unit,
    onApply: (List<EnergyWindowSpec>) -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var fields by remember(specs) {
        mutableStateOf(
            specs.map { spec ->
                trimNumber(spec.startKeV) to trimNumber(spec.endKeV)
            },
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = "Границы окон", style = type.title, color = colors.ink)
                Text(
                    text = "Окна — параметр анализа, а не физические категории излучения. " +
                        "Значения в кэВ, окна не должны пересекаться.",
                    style = type.bodySmall,
                    color = colors.muted,
                )
                fields.forEachIndexed { index, (start, end) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        AppTextField(
                            value = start,
                            onValueChange = { value ->
                                fields = fields.toMutableList().also { it[index] = value to end }
                            },
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = "—", style = type.body, color = colors.ink2)
                        AppTextField(
                            value = end,
                            onValueChange = { value ->
                                fields = fields.toMutableList().also { it[index] = start to value }
                            },
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                error?.let {
                    Text(text = it, style = type.footnote, color = colors.warn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = "Сохранить",
                        onClick = {
                            val parsed = fields.map { (start, end) ->
                                EnergyWindowSpec(
                                    start.replace(',', '.').trim().toFloatOrNull() ?: Float.NaN,
                                    end.replace(',', '.').trim().toFloatOrNull() ?: Float.NaN,
                                )
                            }
                            val reason = EnergyWindows.validate(parsed)
                            if (reason == null) onApply(parsed) else error = reason
                        },
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(text = "По умолчанию", onClick = onReset, modifier = Modifier.weight(1f))
                }
                AppButton(text = "Отмена", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun trimNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

@Composable
private fun RowScope.WindowHeader(text: String, weight: Float) {
    Text(
        text = text.uppercase(),
        style = LocalAppTypography.current.overline,
        color = LocalAppColors.current.muted,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.WindowCell(
    text: String,
    weight: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = LocalAppTypography.current.valueSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier.weight(weight),
    )
}
