package app.radiacode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiacode.ui.logic.ProfileShift
import app.radiacode.ui.logic.WhyInput
import app.radiacode.ui.logic.WhyLine
import app.radiacode.ui.logic.WhyReport
import app.radiacode.ui.logic.WhyReportBuilder
import app.radiacode.ui.logic.WhyScale
import app.radiacode.ui.logic.WhySection
import app.radiacode.ui.logic.WhyTone
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography

/**
 * «Почему такой вывод» (why-spec).
 *
 * The order is the audit trail of the conclusion — **вывод → доказательство →
 * статистика → состояние профиля → критерии алгоритма** — and the research
 * numbers (MAD, buckets, thresholds) stay folded behind «Показать расчёты»
 * until asked for. The choice is remembered, so a person who always opens them
 * stops having to.
 *
 * The sheet renders a [WhyReport]; it holds no wording of its own, because
 * every phrase here is pinned by `WhyReportTest`.
 */
@Composable
fun WhySheet(
    input: WhyInput,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    /** §7: the deviation has held long enough to ask about the place itself. */
    offerProfileShift: Boolean = false,
    onUpdateProfile: () -> Unit = {},
    onKeepProfile: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val report = WhyReportBuilder.build(input, LocalStrings.current)

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = "Почему такой вывод", style = type.label, color = colors.ink2)

                // --- the answer, then the evidence for it (§2)
                StatusRow(text = report.status, color = toneColor(report.tone))
                Text(text = report.sentence, style = type.bodySmall, color = colors.ink2)

                if (report.nowValue != null || report.usualValue != null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Сейчас", style = type.labelSmall, color = colors.muted)
                            Text(
                                text = report.nowValue ?: "—",
                                style = type.value,
                                color = colors.ink,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Обычно здесь",
                                style = type.labelSmall,
                                color = colors.muted,
                            )
                            Text(
                                text = report.usualValue ?: "диапазон ещё не собран",
                                style = type.value,
                                color = colors.ink,
                            )
                        }
                    }
                }
                report.scale?.let { BandScale(it, toneColor(report.tone)) }

                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space3),
                ) {
                    report.sections.filter { !it.advanced }.forEach { SectionBlock(it) }

                    if (offerProfileShift) {
                        ProfileShiftBlock(
                            profileName = input.profileName,
                            onUpdate = onUpdateProfile,
                            onKeep = onKeepProfile,
                        )
                    }

                    if (report.hasAdvanced) {
                        Chip(
                            text = if (expanded) "Скрыть расчёты" else "Показать расчёты",
                            color = colors.dataText,
                            onClick = { onExpandedChange(!expanded) },
                        )
                        if (expanded) {
                            report.sections.filter { it.advanced }.forEach { SectionBlock(it) }
                        }
                    }
                }

                AppDivider()
                Text(text = report.legend, style = type.footnote, color = colors.muted)
                AppButton(
                    text = "Понятно",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The P10–P90 band with the current value on it (§2): a position is read
 * faster than one more line of text, and it is the same statement — the dot
 * outside the band is exactly what «выше P90» means.
 */
@Composable
private fun BandScale(scale: WhyScale, tone: Color) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            val trackY = size.height / 2f
            val radius = 5.dp.toPx()
            // The band itself; the track outside it stays visible so «выше
            // P90» has somewhere to be drawn.
            drawLine(
                color = colors.line,
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = 2.dp.toPx(),
            )
            val bandStart = size.width * 0.15f
            val bandWidth = size.width * 0.70f
            drawRect(
                color = colors.ink2.copy(alpha = 0.18f),
                topLeft = Offset(bandStart, trackY - 6.dp.toPx()),
                size = Size(bandWidth, 12.dp.toPx()),
            )
            val x = bandStart + bandWidth * scale.position
            drawCircle(color = colors.surface, radius = radius + 2f, center = Offset(x, trackY))
            drawCircle(color = tone, radius = radius, center = Offset(x, trackY))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "P10 ${scale.lowLabel}", style = type.axis, color = colors.muted)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (scale.outside) "${scale.currentLabel} — вне диапазона" else scale.currentLabel,
                style = type.axis,
                color = colors.ink2,
            )
            Spacer(Modifier.weight(1f))
            Text(text = "P90 ${scale.highLabel}", style = type.axis, color = colors.muted)
        }
    }
}

/**
 * «Уровень изменился надолго» (§7). The app may ask; only the user may answer,
 * and both answers are spelled out before either is pressed — the update is
 * not something the app can undo for them.
 */
@Composable
private fun ProfileShiftBlock(
    profileName: String?,
    onUpdate: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        StatusRow(text = ProfileShift.TITLE, color = colors.warn)
        Text(
            text = ProfileShift.sentence(profileName),
            style = type.bodySmall,
            color = colors.ink2,
        )
        Text(text = ProfileShift.EXPLANATION, style = type.footnote, color = colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            AppButton(
                text = ProfileShift.UPDATE_ACTION,
                onClick = onUpdate,
                primary = true,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = ProfileShift.KEEP_ACTION,
                onClick = onKeep,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionBlock(section: WhySection) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.title.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )
            if (section.tone != WhyTone.UNKNOWN) {
                Spacer(Modifier.weight(1f))
                StatusDot(toneColor(section.tone))
            }
        }
        section.lines.forEach { WhyRow(it) }
        section.note?.let {
            Text(
                text = it,
                style = type.footnote,
                color = colors.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** §14: colour is the state of the comparison, never a safety verdict. */
@Composable
private fun toneColor(tone: WhyTone): Color {
    val colors = LocalAppColors.current
    return when (tone) {
        WhyTone.OK -> colors.ok
        WhyTone.ATTENTION -> colors.warn
        WhyTone.ALARM -> colors.crit
        WhyTone.UNKNOWN -> colors.muted
    }
}

/** One «label — value — tag» row; shared with the Поиск sheet. */
@Composable
internal fun WhyRow(line: WhyLine) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = line.label, style = type.bodySmall, color = colors.ink2)
            Spacer(Modifier.weight(1f))
            EvidenceTag(line.evidence, Modifier.padding(end = 6.dp))
            Text(text = line.value, style = type.value, color = colors.ink)
        }
        line.note?.let {
            Text(text = it, style = type.footnote, color = colors.muted)
        }
    }
}
