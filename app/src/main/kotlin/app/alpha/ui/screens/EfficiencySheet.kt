@file:OptIn(ExperimentalLayoutApi::class)

package app.alpha.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.alpha.analysis.EfficiencyCalibration
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.ActivityMath
import app.alpha.analysis.ReferenceSources
import app.alpha.device.DeviceModel
import app.alpha.ui.components.AppTextField
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Hint
import app.alpha.ui.components.Card
import app.alpha.ui.logic.ActivityFormat
import app.alpha.ui.logic.EfficiencyRecord
import app.alpha.ui.text.EfficiencyCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.uiDecimal
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.util.Calendar
import java.util.Locale

/**
 * Лист «эталонный источник»: превращает спектр на экране в точки кривой.
 *
 * @param counts отсчёты спектра эталона
 * @param seconds время накопления, с
 * @param calibration энергетическая шкала спектра
 * @param measuredAtMillis когда спектр снят — от этого считается распад
 * @param model прибор: его разрешение задаёт допуск поиска линий
 * @param existing уже сохранённая калибровка; новые точки добавляются к ней
 * @param onSave готовая запись — экран её сохраняет
 */
@Composable
fun EfficiencySheet(
    counts: List<Int>?,
    seconds: Long,
    calibration: EnergyCalibration?,
    measuredAtMillis: Long,
    model: DeviceModel,
    existing: EfficiencyRecord?,
    onSave: (EfficiencyRecord) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val s = EfficiencyCatalogue.of(strings.language)

    var nuclide by remember { mutableStateOf(ReferenceSources.ALL.first().nuclide) }
    var activity by remember { mutableStateOf("") }
    var sigmaPercent by remember { mutableStateOf("5") }
    var certifiedAt by remember { mutableStateOf("") }
    var geometry by remember { mutableStateOf(existing?.geometry.orEmpty()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<EfficiencyCalibration.Outcome?>(null) }
    var decayedLine by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
            Text(text = s.sheetTitle, style = type.title, color = colors.ink)
            Hint(text = s.sheetIntro, style = type.bodySmall, color = colors.ink2)

            Text(text = s.nuclideLabel, style = type.footnote, color = colors.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                ReferenceSources.ALL.forEach { source ->
                    Chip(
                        text = source.nuclide,
                        color = if (source.nuclide == nuclide) colors.dataText else colors.ink2,
                        selected = source.nuclide == nuclide,
                        onClick = { nuclide = source.nuclide },
                    )
                }
            }

            Text(text = s.activityLabel, style = type.footnote, color = colors.ink2)
            AppTextField(
                value = activity,
                onValueChange = { activity = it },
                placeholder = s.activityPlaceholder,
                numeric = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = s.activitySigmaLabel, style = type.footnote, color = colors.ink2)
            AppTextField(
                value = sigmaPercent,
                onValueChange = { sigmaPercent = it },
                placeholder = s.activitySigmaPlaceholder,
                numeric = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = s.certifiedAtLabel, style = type.footnote, color = colors.ink2)
            AppTextField(
                value = certifiedAt,
                onValueChange = { certifiedAt = it },
                placeholder = s.certifiedAtPlaceholder,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = s.geometryLabel, style = type.footnote, color = colors.ink2)
            AppTextField(
                value = geometry,
                onValueChange = { geometry = it },
                placeholder = s.geometryPlaceholder,
                modifier = Modifier.fillMaxWidth(),
            )

            problem?.let { Text(text = it, style = type.footnote, color = colors.crit) }
            decayedLine?.let { Text(text = it, style = type.footnote, color = colors.ink2) }
            outcome?.let { result ->
                Text(
                    text = s.linesMeasured(
                        found = result.points.size,
                        total = result.points.size + result.missedKeV.size,
                    ),
                    style = type.footnote,
                    color = colors.ink,
                )
                if (result.missedKeV.isNotEmpty()) {
                    Text(
                        text = s.linesMissed(
                            result.missedKeV.joinToString(" · ") {
                                String.format(Locale.US, "%.1f", it).uiDecimal()
                            },
                        ),
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Chip(
                    text = s.apply,
                    color = colors.dataText,
                    selected = true,
                    onClick = {
                        problem = null
                        outcome = null
                        decayedLine = null
                        val source = ReferenceSources.of(nuclide)
                        val certified = activity.replace(',', '.').toDoubleOrNull()
                        val relative = (sigmaPercent.replace(',', '.').toDoubleOrNull() ?: 0.0) / 100
                        val at = parseDate(certifiedAt)
                        when {
                            counts == null || calibration == null || seconds <= 0L ->
                                problem = s.noSpectrum
                            source == null -> problem = s.noSpectrum
                            certified == null || certified <= 0.0 -> problem = s.badActivity
                            at == null || at > measuredAtMillis -> problem = s.badDate
                            else -> {
                                val now = ActivityMath.decayed(
                                    certifiedBecquerel = certified,
                                    elapsedSeconds = (measuredAtMillis - at) / 1000.0,
                                    halfLifeSeconds = source.halfLifeSeconds,
                                )
                                if (now == null) {
                                    problem = s.badActivity
                                    return@Chip
                                }
                                decayedLine = s.decayedTo(
                                    becquerel = ActivityFormat.value(now, s),
                                    at = certifiedAt,
                                )
                                val result = EfficiencyCalibration.measure(
                                    counts = counts,
                                    seconds = seconds,
                                    calibration = calibration,
                                    source = source,
                                    activityBecquerel = now,
                                    activityRelativeSigma = relative.coerceAtLeast(0.001),
                                    resolution662 = model.peakResolution662,
                                    minEnergyKeV = model.peakFloorKeV,
                                )
                                outcome = result
                                if (result.points.isEmpty()) {
                                    problem = s.nothingMeasured
                                } else {
                                    // Точки того же эталона заменяются, а не
                                    // копятся: пересъёмка того же источника —
                                    // уточнение, а не второе измерение.
                                    val kept = existing?.points
                                        ?.filter { it.nuclide != source.nuclide }
                                        .orEmpty()
                                    onSave(
                                        EfficiencyRecord(
                                            points = (kept + result.points)
                                                .sortedBy { it.energyKeV },
                                            geometry = geometry.trim(),
                                            updatedAtMillis = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            }
                        }
                    },
                )
                Chip(text = s.cancel, color = colors.ink2, onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * Дата вида дд.мм.гггг в миллисекунды эпохи (полночь местного времени).
 *
 * @return null для всего, что не разбирается: молча подставленная «сегодня»
 *   означала бы нулевой распад и завышенную активность эталона
 */
internal fun parseDate(text: String): Long? {
    val parts = text.trim().split('.', '/', '-')
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    if (day !in 1..31 || month !in 1..12 || year !in 1900..2200) return null
    val calendar = Calendar.getInstance()
    calendar.clear()
    calendar.set(year, month - 1, day, 0, 0, 0)
    return calendar.timeInMillis
}
