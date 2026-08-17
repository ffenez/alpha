package app.alpha.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.alpha.AppGraph
import app.alpha.device.DeviceModel
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindow
import app.alpha.analysis.NuclideInfoLibrary
import app.alpha.analysis.DecayFamilies
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.SpectrumDisplay
import app.alpha.analysis.SpectrumEdge
import app.alpha.analysis.SpectrumMerge
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.data.export.N42
import app.alpha.data.export.RcXml
import app.alpha.data.export.SpectrumExport
import app.alpha.data.toEntity
import app.alpha.data.toSpectrum
import app.alpha.device.ConnectionState
import app.alpha.protocol.Spectrum
import app.alpha.service.SpectrumHub
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Card
import app.alpha.ui.components.EvidenceTag
import app.alpha.ui.components.Chip
import app.alpha.ui.components.Segmented
import androidx.compose.material3.Slider
import app.alpha.ui.logic.SpectrumScale
import app.alpha.ui.logic.DeviceActionBlock
import app.alpha.ui.logic.SpectrumSource
import app.alpha.ui.logic.SpectrumSources
import app.alpha.ui.components.SpectrumChart
import app.alpha.ui.components.SpectrumChartSpec
import app.alpha.ui.components.NuclideInfoDialog
import app.alpha.ui.components.SpectrumLineMark
import app.alpha.ui.components.SpectrumPeakMark
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.PeakEvidenceBridge
import app.alpha.ui.logic.PeakMatch
import app.alpha.ui.logic.PeakRow
import app.alpha.ui.logic.involves
import app.alpha.ui.logic.primaryNuclide
import app.alpha.ui.logic.SpectrumHighlight
import app.alpha.ui.logic.Evidence
import app.alpha.ui.logic.SpectrumFormat
import app.alpha.ui.logic.SpectrumFrames
import app.alpha.ui.logic.SpectrumPlot
import app.alpha.ui.logic.SpectrumInfo
import app.alpha.ui.logic.SpectrumInfoLevel
import app.alpha.ui.logic.SpectrumInfoSection
import app.alpha.ui.logic.SpectrumViewOptions
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrumCatalogue
import app.alpha.ui.text.SpectrumStrings
import app.alpha.ui.text.NuclideCatalogue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import app.alpha.ui.theme.Motion
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Таблица пиков: энергия, нетто, значимость и колонка «возможное совпадение».
 *
 * Вынесена из `SpectrumScreen` вместе со своими ячейками. Вердикты в колонке
 * приходят из движка доказательств (ADR 006) и здесь только печатаются:
 * своей логики «найдено / не найдено» у таблицы нет и быть не должно — иначе
 * список кандидатов разошёлся бы со справкой о нуклиде.
 */


@Composable
internal fun PeakTable(
    rows: List<PeakRow>,
    highlightedNuclide: String?,
    onSelect: (String?) -> Unit,
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val type = LocalAppTypography.current

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
            TableHeader(strings.peakTableEnergy, 0.9f)
            TableHeader(strings.peakTableNet, 0.9f)
            TableHeader(strings.peakTableSignificance, 0.9f)
            // Четвёртый заголовок — такой же, как остальные три.
            //
            // Он был собран иначе: своим стилем, без ограничения строк и в
            // своей `Row` с центрированием по вертикали. Пока текст был
            // коротким, разницы не было видно; от слова «ВОЗМОЖНОЕ» он
            // переносился, растил высоту всей строки заголовков, а соседи
            // оставались прижатыми к верху — между заголовками и первой
            // строкой появлялась пустота во весь перенос.
            //
            // Метка «гипотеза» рядом больше не нужна: слово «возможное» в
            // самом заголовке говорит ровно то же и не занимает ширины.
            TableHeader(strings.peakTableCandidate, 1.6f)
        }
        AppDivider()
        rows.forEachIndexed { index, row ->
            val match = row.match
            val target = match.primaryNuclide
            val isHighlighted = highlightedNuclide != null && match.involves(highlightedNuclide)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = target != null) { onSelect(target) }
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TableCell(SpectrumFormat.energyCell(row.peak.energyKeV), 0.9f, colors.ink)
                    TableCell(SpectrumFormat.netCell(row.peak.netCounts), 0.9f, colors.ink)
                    TableCell(
                        SpectrumFormat.significanceCell(row.peak.significance),
                        0.9f,
                        colors.ink,
                    )
                    // Искусственный кандидат — единственное, что выделяется
                    // весом и цветом внимания; артефакты и прочерки приглушены.
                    val artificial = match is PeakMatch.Candidate && !match.natural ||
                        match is PeakMatch.AmbiguousGroup && !match.natural
                    Text(
                        text = (if (isHighlighted) "▸ " else "") +
                            SpectrumFormat.matchCell(match, t),
                        style = if (artificial) {
                            type.valueSmall.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            type.valueSmall
                        },
                        color = when {
                            artificial -> colors.warn
                            match is PeakMatch.Candidate ||
                                match is PeakMatch.AmbiguousGroup -> colors.ink2
                            else -> colors.muted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                // Детали строки: противоречие, группа неразрешимости или
                // механизм артефакта — тихой строкой под самим пиком.
                SpectrumFormat.matchNotes(match, t).forEach { note ->
                    Text(
                        text = note,
                        style = type.footnote,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            if (index < rows.size - 1) AppDivider()
        }
    }
}

@Composable
internal fun RowScope.TableHeader(
    text: String,
    weight: Float,
) {
    Text(
        text = text.uppercase(),
        style = LocalAppTypography.current.overline,
        color = LocalAppColors.current.muted,
        // Заголовок никогда не переносится и не растит строку: на узком экране
        // он усечётся многоточием, а таблица останется таблицей.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

@Composable
internal fun RowScope.TableCell(
    text: String,
    weight: Float,
    color: Color,
) {
    Text(
        text = text,
        style = LocalAppTypography.current.valueSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

private val TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm")
