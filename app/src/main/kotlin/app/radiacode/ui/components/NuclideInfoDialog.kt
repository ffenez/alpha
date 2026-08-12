package app.radiacode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import app.radiacode.analysis.DetectorEfficiency
import app.radiacode.analysis.LineConsistency
import app.radiacode.ui.logic.Uncertainty
import kotlin.math.roundToInt
import app.radiacode.analysis.Nuclide
import app.radiacode.ui.logic.NuclideCard
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * Offline reference card for a candidate nuclide (спец §12): everything is
 * bundled, nothing is fetched. The card describes the **nuclide** — it never
 * claims the nuclide was found, and the honest framing sits above the data,
 * not in a footnote.
 */
/**
 * Проверка гипотезы по нескольким линиям.
 *
 * Совпадение ОДНОЙ энергии — самое слабое свидетельство; блок показывает,
 * какие ещё линии нуклида найдены, и отношение их нетто-площадей. Вердикт о
 * согласии отношения с табличным НЕ выносится: он требует кривой
 * эффективности детектора, которой у нас нет, и это сказано прямо.
 */
@Composable
private fun LineConsistencyBlock(result: LineConsistency.Result) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppDivider()
        Text(
            text = "Проверка по линиям".uppercase(),
            style = type.labelSmall,
            color = colors.ink2,
        )
        Text(
            text = supportWording(result.support, result.foundLines),
            style = type.bodySmall,
            color = when (result.support) {
                LineConsistency.Support.MULTI_LINE -> colors.ink
                else -> colors.ink2
            },
        )
        for (line in result.lines) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${line.energyKeV.roundToInt()} кэВ",
                    style = type.footnoteMono,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "выход ${Uncertainty.num1(line.intensityPercent)} %",
                    style = type.footnote,
                    color = colors.muted,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = when {
                        line.found -> "найдена"
                        line.expectedVisible == false -> "слабая, различить нельзя"
                        else -> "не найдена"
                    },
                    style = type.footnote,
                    color = if (line.found) colors.dataText else colors.muted,
                    modifier = Modifier.weight(1.6f),
                )
            }
        }
        result.ratio?.let { ratio ->
            Text(
                text = "Отношение нетто-площадей ${ratio.fromKeV.roundToInt()}/" +
                    "${ratio.toKeV.roundToInt()} кэВ: " +
                    "${Uncertainty.num2(ratio.observed.toFloat())} ± " +
                    Uncertainty.num2(ratio.sigma.toFloat()),
                style = type.bodySmall,
                color = colors.ink2,
            )
            if (ratio.expectedByYield.isFinite()) {
                Text(
                    text = "По табличным выходам: " +
                        Uncertainty.num2(ratio.expectedByYield.toFloat()) +
                        " — без поправки на эффективность детектора",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
            if (!DetectorEfficiency.AVAILABLE) {
                Text(
                    text = DetectorEfficiency.UNAVAILABLE_NOTE,
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

/** Как называется степень поддержки гипотезы — без слова «обнаружен». */
private fun supportWording(support: LineConsistency.Support, found: Int): String =
    when (support) {
        LineConsistency.Support.MULTI_LINE ->
            "Гипотеза поддерживается несколькими линиями: найдено $found из известных."
        LineConsistency.Support.MISSING_STRONG_LINE ->
            "Найдена одна линия, а другая ожидаемая заметная — нет. Это не опровергает " +
                "нуклид (мешать могут статистика, континуум и наложение соседних пиков), " +
                "но и не подтверждает его."
        LineConsistency.Support.SINGLE_LINE ->
            "Совпала одна энергия — самое слабое свидетельство: у нуклида нет других " +
                "линий в библиотеке или они не найдены."
    }

@Composable
fun NuclideInfoDialog(
    nuclide: Nuclide,
    /** Проверка кандидата по нескольким линиям; null — спектра нет. */
    consistency: LineConsistency.Result? = null,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = NuclideCard.title(nuclide),
                        style = type.title,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Chip(text = "справка", color = colors.ink2)
                }
                Text(
                    text = NuclideCard.FRAMING,
                    style = type.bodySmall,
                    color = colors.ink2,
                )

                consistency?.let { LineConsistencyBlock(it) }

                AppDivider()
                InfoLine("происхождение", NuclideCard.originLine(nuclide))
                InfoLine("период полураспада", nuclide.halfLife)
                InfoLine("распад", nuclide.decay)

                AppDivider()
                Text(text = "Гамма-линии".uppercase(), style = type.labelSmall, color = colors.ink2)
                nuclide.lines.forEach { line ->
                    Text(
                        text = NuclideCard.lineText(line.energyKeV, line.intensityPercent),
                        style = type.valueSmall,
                        color = colors.ink,
                    )
                }

                AppDivider()
                Text(text = "Где встречается".uppercase(), style = type.labelSmall, color = colors.ink2)
                Text(text = nuclide.everyday, style = type.bodySmall, color = colors.ink2)

                AppDivider()
                Text(
                    text = "Что подтвердило бы совпадение".uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Text(text = nuclide.confirmation, style = type.bodySmall, color = colors.ink2)
                Text(text = NuclideCard.LIMITS, style = type.bodySmall, color = colors.muted)

                Text(text = NuclideCard.SOURCE, style = type.footnote, color = colors.muted)
                AppButton(text = "Закрыть", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column {
        Text(text = label, style = type.footnote, color = colors.muted)
        Text(text = value, style = type.bodySmall, color = colors.ink)
    }
    Spacer(Modifier)
}
