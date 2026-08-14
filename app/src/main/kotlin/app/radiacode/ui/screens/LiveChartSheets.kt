package app.radiacode.ui.screens

import android.content.res.Configuration
import app.radiacode.ui.logic.ChartInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.analysis.quantiles.KllSketch
import app.radiacode.analysis.quantiles.QuantileComparison
import app.radiacode.analysis.quantiles.QuantileDiagnostics
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.Baseline
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.DoseUnitSetting
import app.radiacode.data.PreAggregateRepository
import app.radiacode.device.DoseUnits
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.ChartSheet
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.DistributionStrip
import app.radiacode.ui.components.DoseChart
import app.radiacode.ui.components.StatCell
import app.radiacode.ui.components.StatGrid
import app.radiacode.ui.logic.ChartBucket
import app.radiacode.ui.logic.ChartInteraction
import app.radiacode.ui.logic.ChartInteractions
import app.radiacode.ui.logic.ChartWindow
import app.radiacode.ui.logic.ChartViewport
import app.radiacode.ui.logic.ChartWindows
import app.radiacode.analysis.Hardness
import app.radiacode.ui.logic.ChartMetric
import app.radiacode.ui.logic.ChartMetrics
import app.radiacode.ui.logic.ChartRange
import app.radiacode.ui.logic.ChartRanges
import app.radiacode.ui.logic.CursorReadout
import app.radiacode.ui.logic.coverageWording
import app.radiacode.ui.logic.ChartSeriesModel
import app.radiacode.ui.logic.DoseExtremes
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.DoseHistograms
import app.radiacode.ui.logic.DoseReference
import app.radiacode.ui.logic.ChartSnapshot
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.freshnessChipLabel
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.QuantileMetadata
import app.radiacode.ui.logic.QuantileMethod
import app.radiacode.ui.logic.RatioDenominator
import app.radiacode.ui.logic.markerWording
import app.radiacode.ui.logic.referenceWording
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.logic.WindowStats
import app.radiacode.ui.text.ChartAxisCatalogue
import app.radiacode.ui.text.ChartTextCatalogue
import app.radiacode.ui.text.ChartTextStrings
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.HistoryRu
import app.radiacode.ui.text.HistoryStrings
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.text.Strings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Панели поверх полноэкранного графика: «подробнее» и справка «i».
 *
 * Вынесены из `LiveChartScreen`. Правило прежнее и в нём весь смысл этих
 * панелей: вторичное живёт ПОВЕРХ графика, а не под ним — постоянных полос
 * мелкого текста под полем нет ни одной, они читались один раз, а высоту
 * забирали всегда.
 */

/**
 * Панель «подробнее»: распределение, расширенная статистика, покрытие окна и
 * метод квантилей — всё, что нужно по требованию и ничего, что нужно всегда.
 *
 * Панель лежит ПОВЕРХ графика и не двигает его: открыли, посмотрели, закрыли —
 * картинка под ней осталась на месте.
 */
@Composable
internal fun BoxScope.ChartDetailsSheet(
    open: Boolean,
    graph: AppGraph,
    snapshot: ChartSnapshot?,
    frame: ChartFrame?,
    unit: DoseUnitSetting,
    metric: ChartMetric,
    spanMillis: Long,
    onClose: () -> Unit,
) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    ChartSheet(
        open = open,
        title = t.windowSheetTitle(HistoryFormat.duration(spanMillis / 1000, s = h)),
        onClose = onClose,
    ) {
        // §2 ТЗ: неполное окно называется словами, а не остаётся пустым полем.
        coverageWording(frame?.stats, spanMillis)?.let { coverage ->
            Text(
                text = coverage,
                style = type.footnote,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = Dimens.space3),
            )
        }
        val histogram = frame?.histogram
        if (histogram != null) {
            Text(
                text = t.distribution,
                style = type.label,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = Dimens.space3),
            )
            DistributionStrip(
                histogram = histogram,
                labels = frame.histogramLabels,
                countCaption = DoseHistograms.countAxisLabel(
                    ChartAxisCatalogue.of(LocalStrings.current.language),
                ),
            )
        }
        Text(
            text = t.windowStatistics,
            style = type.label,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = Dimens.space3),
        )
        ExpandedStats(stats = frame?.stats, unit = unit, metric = metric)
        QuantileDiagnosticPanel(graph = graph, snapshot = snapshot, unit = unit)
    }
}

/**
 * Справка «как читать график» — по кнопке «i» в шапке.
 *
 * Панель кладётся ПОВЕРХ экрана, а не раздвигает его: график не должен
 * прыгать от того, что человек открыл объяснение и закрыл его. Системная
 * «назад» закрывает справку, а не сам график, — «назад» здесь означает ровно
 * один шаг, как и везде.
 */
@Composable
internal fun BoxScope.ChartInfoSheet(
    open: Boolean,
    metric: ChartMetric,
    frame: ChartFrame?,
    baseline: Baseline?,
    logScale: Boolean,
    onClose: () -> Unit,
    historical: Boolean = false,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val t = ChartTextCatalogue.of(LocalStrings.current.language)
    // Каталог входит в ключ: смена языка обязана пересобрать справку, иначе
    // на экране остались бы разделы, посчитанные прежним языком.
    val sections = remember(metric, frame, baseline, logScale, historical, t) {
        ChartInfo.sections(
            metric = metric,
            hasBaselineBand = baseline != null && ChartMetrics.showsProfileBand(metric),
            hasExtremeMarkers = frame?.spec?.extremeMarkers?.isNotEmpty() == true,
            hasEpisodes = frame?.spec?.episodes?.isNotEmpty() == true,
            method = frame?.stats?.method ?: QuantileMethod.EXACT_RAW,
            logScale = logScale,
            logDropped = frame?.logDropped ?: 0,
            historical = historical,
            s = t,
        )
    }
    // Второй уровень справки: «что я вижу» читают все, «P50, P25–P75, метод
    // квантилей» — по требованию. Один переключатель на всю панель: два
    // раскрытия в четырёх разделах превратили бы справку в меню.
    var details by rememberSaveable { mutableStateOf(false) }
    ChartSheet(open = open, title = t.infoTitle, onClose = onClose) {
        for (section in sections) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = Dimens.space3),
            ) {
                Text(text = section.title, style = type.label, color = colors.ink)
                for (line in section.lines) {
                    Text(text = line, style = type.bodySmall, color = colors.muted)
                }
                if (details) {
                    for (line in section.details) {
                        Text(text = line, style = type.bodySmall, color = colors.ink2)
                    }
                }
            }
        }
        if (sections.any { it.details.isNotEmpty() }) {
            Column(modifier = Modifier.padding(horizontal = Dimens.space3)) {
                Chip(
                    text = if (details) t.hideDetails else t.showDetails,
                    color = colors.dataText,
                    onClick = { details = !details },
                )
            }
        }
    }
}

// --- top bars -------------------------------------------------------------
