package app.radiacode.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.NavigateGauge
import app.radiacode.ui.components.NavigateGaugeSpec
import app.radiacode.ui.components.NavigateTrace
import app.radiacode.ui.components.NavigateTraceSpec
import app.radiacode.ui.components.StatusRow
import app.radiacode.ui.logic.NavigateArc
import app.radiacode.ui.logic.NavigateEngine
import app.radiacode.ui.logic.NavigateState
import app.radiacode.ui.logic.NavigateTrend
import app.radiacode.ui.logic.NavigateVerdict
import app.radiacode.ui.logic.ReferenceDelta
import app.radiacode.ui.logic.SpotMeasure
import app.radiacode.ui.logic.SpotMeasureMachine
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.text.SearchStrings
import app.radiacode.ui.text.Strings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import app.radiacode.ui.theme.Motion
import java.util.Locale
import kotlin.math.roundToInt

/**
 * «Наведение» — the guidance half of Поиск.
 *
 * It answers «куда вести прибор прямо сейчас», which is a different question
 * from the one «Проверка» answers, not an easier version of it. Everything the
 * verify mode needs and this one does not — confidence intervals of the
 * profile, «1σ Пуассон» as a headline, p-values, dose as the big number, the
 * P10–P90 band, accumulated dose, spectral hypotheses — is deliberately absent:
 * none of it says where to move the probe.
 *
 * The composable owns no state. Readings, windows, marks and the arc frame all
 * live in [NavigateEngine] and the app-scoped spot recorder, so leaving this
 * mode and coming back does not silently restart a measurement.
 */
@Composable
fun NavigateSection(
    state: NavigateState,
    spot: SpotMeasure,
    /** «Now» on the instrument's clock — the base the windows are built in. */
    nowMillis: Long,
    cps: Float?,
    /** Dose rate, already formatted with its unit and error; small print. */
    doseLine: String?,
    /** Wall-clock time the reference was taken at, «11:44»; null = none. */
    referenceTime: String?,
    strings: Strings,
    t: SearchStrings,
    onMark: () -> Unit,
    onResetPeak: () -> Unit,
    onMeasureHere: () -> Unit,
    onCancelMeasure: () -> Unit,
    onDismissMeasure: () -> Unit,
    onGoToVerify: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var moreOpen by remember { mutableStateOf(false) }

    val trendColor = when (state.trend) {
        NavigateTrend.RISING -> colors.warn
        NavigateTrend.FALLING -> colors.ink2
        NavigateTrend.NO_CHANGE -> colors.ink
        NavigateTrend.COLLECTING -> colors.muted
    }
    val factor = state.scale?.factor ?: NavigateArc.LADDER.first()
    val referenceRatio = state.referenceRatio
    val peakRatio = state.peak?.ratePerSecond?.let { peak ->
        state.reference?.ratePerSecond?.takeIf { it > 0.0 }?.let { peak / it }
    }
    val delta = NavigateEngine.referenceDelta(state)
    // Цвет — вторичный код: знак несут число и подпись, цвет только помогает.
    val deltaColor = when {
        delta !is ReferenceDelta.Resolved -> colors.muted
        delta.percent >= 0 -> colors.warn
        else -> colors.ink2
    }
    // Уровень доверия называется числом только когда интервал вообще есть.
    val bandLevel = state.referenceComparison
        ?.takeIf { it.ratioLow.isFinite() && it.ratioHigh.isFinite() }
        ?.let { (it.confidenceLevel * 100).roundToInt() }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        Card(modifier = Modifier.fillMaxWidth(), contentPadding = Dimens.space4) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.space1),
            ) {
                Text(
                    text = strings.countRate.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Text(
                    text = cps?.let { Uncertainty.num1(it) } ?: "—",
                    style = type.valueHero.copy(fontSize = 52.sp, lineHeight = 54.sp),
                    color = if (cps != null) colors.ink else colors.muted,
                    textAlign = TextAlign.Center,
                )
                Text(text = t.cpsUnit, style = type.footnoteMono, color = colors.ink2)

                StatusRow(
                    text = NavigateVerdict.trendLabel(state.trend, t),
                    color = trendColor,
                    modifier = Modifier.padding(top = Dimens.space2),
                )
                // Одно отношение с интервалом — и знаменатель назван в той же
                // строке. Окна решения уехали под «i»: они описывают алгоритм,
                // а карточка отвечает на вопрос «какой сейчас счёт».
                NavigateVerdict.localRatio(state.trendComparison, t)?.let {
                    Text(text = it, style = type.value, color = trendColor)
                }
                NavigateVerdict.localInterval(state.trendComparison, t)?.let {
                    Text(
                        text = it,
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                // Доза в наведении не участвует: ни одно окно, ни тест, ни дуга
                // её не читают. Она остаётся вторичной строкой — с той же
                // точностью и той же приборной погрешностью, что на Мониторе.
                doseLine?.let {
                    Text(
                        text = t.navDose(it),
                        style = type.footnote,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Dimens.space1),
                    )
                }
            }
        }

        // -------------------------------------------------- the guidance module
        // Direction, one number, the scale, the last seconds and the interval —
        // in ONE card. They were three, and the same ratio appeared in all of
        // them: as a big number, as a sentence and as a needle.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = t.navModuleTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                ) {
                    // Направление здесь считается ОТ ТОЧКИ ОТСЧЁТА, а состояние
                    // на карточке выше — от локального уровня: знаменатели
                    // разные, поэтому это не одна и та же фраза дважды.
                    StatusRow(
                        text = NavigateVerdict.referenceDirection(delta, t),
                        color = deltaColor,
                    )
                    Text(
                        text = NavigateVerdict.deltaHeadline(delta, t),
                        style = type.valueHero.copy(fontSize = 34.sp, lineHeight = 36.sp),
                        color = deltaColor,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = NavigateVerdict.deltaCaption(delta, t),
                        style = type.footnote,
                        color = colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                }
                NavigateGauge(
                    spec = NavigateGaugeSpec(
                        ratio = referenceRatio,
                        peakRatio = peakRatio,
                        factor = factor,
                        trend = state.trend,
                        referenceLabel = t.navScaleReference,
                        lowLabel = "×${factorLabel(1.0 / factor)}",
                        highLabel = "×${factorLabel(factor)}",
                        intervalLow = state.referenceComparison?.ratioLow?.takeIf { it.isFinite() },
                        intervalHigh = state.referenceComparison
                            ?.ratioHigh?.takeIf { it.isFinite() },
                    ),
                    height = 112.dp,
                )
                if (state.trace.isEmpty()) {
                    Text(text = t.waitingStream, style = type.bodySmall, color = colors.muted)
                } else {
                    NavigateTrace(
                        spec = NavigateTraceSpec(
                            points = state.trace,
                            nowMillis = state.trace.last().timeMillis,
                            spanMillis = NavigateEngine.TRACE_MILLIS,
                            localLevel = state.local?.ratePerSecond?.toFloat(),
                            localLabel = state.local?.ratePerSecond
                                ?.let { t.navLocalLevel(Uncertainty.num1(it.toFloat())) },
                            startLabel = t.navTraceStart,
                            endLabel = strings.nowLabel,
                        ),
                        height = 72.dp,
                    )
                }
                // Максимум остаётся вторичной подписью и только когда он есть:
                // с направлением изменения он не соревнуется.
                NavigateVerdict.peakLine(state, nowMillis, t)?.let {
                    Text(text = it, style = type.footnoteMono, color = colors.ink2)
                }
            }
        }

        // ------------------------------------------------- the still spot count
        when (spot) {
            is SpotMeasure.Running -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        Text(
                            text = t.navSpotProgress(spot.collectedSeconds, spot.targetSeconds),
                            style = type.value,
                            color = colors.dataText,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(text = strings.cancel, onClick = onCancelMeasure)
                    }
                    Text(text = t.navSpotNote, style = type.footnote, color = colors.muted)
                }
            }

            is SpotMeasure.Done -> Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    StatusRow(text = t.navSpotTitle, color = colors.ink)
                    Text(
                        text = t.navSpotResult(
                            Uncertainty.num1(spot.result.ratePerSecond.toFloat()),
                            Uncertainty.num1(spot.result.sigma.toFloat()),
                        ),
                        style = type.valueLarge,
                        color = colors.ink,
                    )
                    NavigateVerdict.ratioPhrase(spot.result.comparison, t)?.let {
                        Text(text = it, style = type.value, color = colors.ink2)
                    }
                    Text(
                        text = t.navSpotExposure(spot.result.window.seconds.toInt()),
                        style = type.footnote,
                        color = colors.muted,
                    )
                    // The spot is found; the question changes, so the mode does.
                    AppButton(
                        text = t.navSpotToVerify,
                        onClick = onGoToVerify,
                        primary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = t.hide,
                        onClick = onDismissMeasure,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is SpotMeasure.Aborted -> Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Text(
                        text = SpotMeasureMachine.abortWording(spot, t),
                        style = type.bodySmall,
                        color = colors.warn,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(text = t.hide, onClick = onDismissMeasure)
                }
            }

            SpotMeasure.Idle -> Unit
        }

        // ---------------------------------------------------------- the actions
        // Пока отсчёта нет — одно действие во всю ширину. Как только он
        // поставлен, кнопка превращается в СОСТОЯНИЕ: величина, момент и
        // «Обновить» рядом. «⋯» стоит здесь же — отдельная квадратная кнопка
        // в пустом месте не сообщала, к чему она относится.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            val referenceLine = NavigateVerdict.referenceLine(state.reference, referenceTime, t)
            if (referenceLine == null) {
                AppButton(
                    text = t.navMark,
                    onClick = onMark,
                    primary = true,
                    enabled = cps != null,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = referenceLine,
                    style = type.value,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                AppButton(text = t.navMarkUpdate, onClick = onMark, enabled = cps != null)
            }
            AppButton(text = t.navMore, onClick = { moreOpen = !moreOpen })
        }
        AnimatedVisibility(
            visible = moreOpen,
            enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
            exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                AppButton(
                    text = t.navMeasureHere(SpotMeasureMachine.TARGET_SECONDS),
                    onClick = onMeasureHere,
                    enabled = spot !is SpotMeasure.Running,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    text = t.navResetPeak,
                    onClick = onResetPeak,
                    enabled = state.peak != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** «4» for a whole factor, «0,25» for its reciprocal — no trailing zeros. */
private fun factorLabel(value: Double): String =
    if (value >= 1.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.2f", value).replace('.', ',').trimEnd('0').trimEnd(',')
    }
