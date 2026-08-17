package app.alpha.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.alpha.ui.logic.DoseHistogram
import app.alpha.ui.logic.DoseHistograms
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Distribution of the visible window: how much measured time was spent at
 * each dose-rate level.
 *
 * This is the strip that answers what a time series cannot — whether a high
 * reading was a rare visitor or the new level of the place. Bar height is
 * measured seconds, not columns. The usual band of the place is shaded behind
 * the bars, and bins that can hold values at or above the alarm level are
 * painted crit, so «сколько времени провели выше L1» is a glance, not a
 * calculation.
 *
 * Binning maths lives in [app.alpha.ui.logic.DoseHistograms] (JVM-tested);
 * this composable only paints, inside `drawWithCache` so a repaint replays
 * prebuilt geometry.
 */
@Composable
fun DistributionStrip(
    histogram: DoseHistogram,
    labels: List<Pair<Float, String>>,
    modifier: Modifier = Modifier,
    height: Dp = 62.dp,
    caption: String = "распределение за окно",
    // Say exactly what the bars count (graph spec §14, §39): raw 1 Hz samples,
    // i.e. measured seconds at the device's nominal cadence — not columns and
    // not a probability density.
    countCaption: String = DoseHistograms.COUNT_AXIS_LABEL,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val textMeasurer = rememberTextMeasurer()

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val padTop = 13.dp.toPx()
                val padBottom = 12.dp.toPx()
                val plotHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val binWidthPx = size.width / histogram.binCount.coerceAtLeast(1)
                val maxCount = histogram.maxCount.coerceAtLeast(1)
                val normalColor = colors.data.copy(alpha = 0.75f)
                val hotColor = colors.crit.copy(alpha = 0.85f)
                val shadeColor = colors.ink2.copy(alpha = 0.13f)
                val captionText = textMeasurer.measure(caption, axisStyle)
                val countText = textMeasurer.measure(countCaption, axisStyle)
                val labelTexts = labels.map { (fraction, text) ->
                    fraction to textMeasurer.measure(text, axisStyle)
                }
                val gap = 1.dp.toPx()

                onDrawBehind {
                    histogram.baselineBins?.let { range ->
                        drawRect(
                            color = shadeColor,
                            topLeft = Offset(range.first * binWidthPx, padTop),
                            size = Size((range.last - range.first + 1) * binWidthPx, plotHeight),
                        )
                    }
                    for (i in 0 until histogram.binCount) {
                        val count = histogram.counts[i]
                        if (count <= 0) continue
                        val barHeight = plotHeight * count / maxCount
                        val hot = histogram.firstAlarmBin != null && i >= histogram.firstAlarmBin
                        drawRect(
                            color = if (hot) hotColor else normalColor,
                            topLeft = Offset(
                                i * binWidthPx + gap,
                                padTop + plotHeight - barHeight,
                            ),
                            size = Size((binWidthPx - 2 * gap).coerceAtLeast(1f), barHeight),
                        )
                    }
                    drawText(captionText, colors.muted, Offset(4.dp.toPx(), 0f))
                    drawText(
                        countText,
                        colors.muted,
                        Offset(size.width - countText.size.width - 4.dp.toPx(), 0f),
                    )
                    for ((fraction, text) in labelTexts) {
                        val x = (size.width * fraction - text.size.width / 2f)
                            .coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                        drawText(text, colors.muted, Offset(x, size.height - text.size.height))
                    }
                }
            },
    )
}
