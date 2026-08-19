package app.alpha.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import app.alpha.AppGraph
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Hint
import app.alpha.ui.logic.ActivityFormat
import app.alpha.ui.logic.EfficiencyRecord
import app.alpha.ui.text.EfficiencyCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Состояние калибровки эффективности: точки, диапазон, геометрия.
 *
 * Экран не умеет ДОБАВЛЯТЬ точку: точка берётся из спектра эталона, и место
 * этому действию — «⋮» того самого спектра, а не отдельный список снимков в
 * настройках. Здесь калибровку видно и можно разобрать.
 */
@Composable
fun EfficiencySection(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val s = EfficiencyCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    val raw by graph.settings.efficiencyRaw.collectAsState(initial = null)
    val record = remember(raw) { EfficiencyRecord.decode(raw) }
    val curve = remember(record) { record?.curve() }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.space1),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Text(text = s.screenTitle, style = type.title, color = colors.ink)
        Hint(text = s.intro, style = type.bodySmall, color = colors.ink2)

        if (record == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = s.notCalibrated, style = type.bodySmall, color = colors.ink2)
                    Text(
                        text = s.notCalibratedWhy,
                        style = type.bodySmall,
                        color = colors.muted,
                    )
                    Hint(text = s.addFromSpectrum, style = type.bodySmall, color = colors.muted)
                }
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                Text(
                    text = s.pointsCount(record.points.size),
                    style = type.bodySmall,
                    color = colors.ink,
                )
                if (curve != null) {
                    Text(
                        text = s.rangeLine(
                            fromKeV = whole(curve.minEnergyKeV),
                            toKeV = whole(curve.maxEnergyKeV),
                        ),
                        style = type.footnoteMono,
                        color = colors.ink2,
                    )
                    Text(
                        text = curve.reducedChiSquare
                            ?.let { s.agreementLine(oneDecimal(it)) }
                            ?: s.agreementUnknown,
                        style = type.footnoteMono,
                        color = colors.ink2,
                    )
                }
                // Геометрия — критическая строка: без неё число в беккерелях
                // нельзя истолковать, и она стоит при любом положении
                // переключателя пояснений.
                Text(
                    text = record.geometry.takeIf { it.isNotBlank() }
                        ?.let { s.geometryLine(it) }
                        ?: s.geometryUnnamed,
                    style = type.footnote,
                    color = if (record.geometry.isBlank()) colors.warn else colors.ink2,
                )
                Text(text = s.geometryWarning, style = type.footnote, color = colors.muted)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(bottom = Dimens.space1)) {
                    TableHeader(s.colEnergy, 0.8f)
                    TableHeader(s.colEfficiency, 0.9f)
                    TableHeader(s.colSigma, 0.6f)
                    TableHeader(s.colSource, 1f)
                }
                AppDivider()
                record.points.forEachIndexed { index, point ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.space1),
                    ) {
                        TableCell(whole(point.energyKeV), 0.8f, colors.ink)
                        TableCell(
                            s.efficiencyPercent(twoDecimals(point.efficiency * 100)),
                            0.9f,
                            colors.ink,
                        )
                        TableCell("${ActivityFormat.percent(point.relativeSigma)} %", 0.6f, colors.ink2)
                        TableCell(point.nuclide, 1f, colors.ink2)
                    }
                    if (index < record.points.size - 1) AppDivider()
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Chip(
                text = s.resetCurve,
                color = if (confirmReset) colors.crit else colors.ink2,
                selected = confirmReset,
                onClick = {
                    if (confirmReset) {
                        scope.launch { graph.settings.setEfficiencyRaw(null) }
                        confirmReset = false
                    } else {
                        confirmReset = true
                    }
                },
            )
        }
        if (confirmReset) {
            Text(text = s.resetCurveConfirm, style = type.footnote, color = colors.crit)
        }
    }
}

private fun whole(value: Double): String = String.format(Locale.US, "%.0f", value)

private fun oneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value).replace('.', ',')

private fun twoDecimals(value: Double): String =
    String.format(Locale.US, "%.2f", value).replace('.', ',')
