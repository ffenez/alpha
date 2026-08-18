package app.alpha.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.BreathingAura
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.NavigateGauge
import app.alpha.ui.components.NavigateScale
import app.alpha.ui.components.NavigateGaugeSpec
import app.alpha.ui.components.NavigateTrace
import app.alpha.ui.components.NavigateTraceSpec
import app.alpha.ui.components.NavigateWhySheet
import app.alpha.ui.components.StatusRow
import app.alpha.ui.logic.NavigateArc
import app.alpha.ui.logic.NavigateEngine
import app.alpha.ui.logic.NavigateState
import app.alpha.ui.logic.SearchIndicator
import app.alpha.ui.logic.SearchPulse
import app.alpha.ui.logic.NavigateTrend
import app.alpha.ui.logic.NavigateVerdict
import app.alpha.ui.logic.ReferenceDelta
import app.alpha.ui.logic.SpotMeasure
import app.alpha.ui.logic.SpotMeasureMachine
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.text.SearchStrings
import app.alpha.ui.text.Strings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.Motion
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
    onClearMark: () -> Unit,
    onResetPeak: () -> Unit,
    onMeasureHere: () -> Unit,
    onCancelMeasure: () -> Unit,
    onDismissMeasure: () -> Unit,
    onGoToVerify: () -> Unit,
    /** Стрелка или прямая шкала — вид выбирается в Настройках. */
    indicator: SearchIndicator = SearchIndicator.NEEDLE,
    /** Счёт держится ровно — предложить проверку здесь. */
    offerVerify: Boolean = false,
    onOfferAccept: () -> Unit = {},
    onOfferDismiss: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var moreOpen by remember { mutableStateOf(false) }
    var whyOpen by remember { mutableStateOf(false) }

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

    if (whyOpen) {
        NavigateWhySheet(
            headline = NavigateVerdict.referenceDirection(delta, t),
            explanation = NavigateVerdict.unresolvedNote(delta, t),
            lines = NavigateVerdict.whyLines(state, delta, cps, t),
            onDismiss = { whyOpen = false },
        )
    }

    // Дыхание Поиска — тот же приём и тот же компонент, что на Главной, но
    // период здесь ПОКАЗАНИЕ: [SearchPulse] считает его по той же
    // логарифмической шкале, что и высоту тона, поэтому глаз и ухо говорят
    // одно и то же. Пока точки отсчёта нет, отношения тоже нет, и дыхание
    // остаётся спокойным — оно означает только «прибор жив».
    val breathPeriod = SearchPulse.periodMillis(referenceRatio)
    val breathTint = when (state.trend) {
        NavigateTrend.RISING -> colors.warn
        else -> colors.data
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
        // Счёт, состояние, шкала и главное действие — ОДНА карточка: это один
        // ответ на один вопрос «куда вести прибор», а разрыв между числом и
        // шкалой заставлял связывать их глазами. Заголовка у карточки нет —
        // название режима стоит на переключателе выше, и повторять его значило
        // бы занимать строку тем, что уже прочитано.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                // Свечение обнимает ЧИСЛО И ШКАЛУ — ту часть карточки, которая
                // отвечает «куда вести прибор». Захватив заодно ленту, оно
                // сместило бы свой центр вниз, и ритм перестал бы читаться как
                // дыхание показания.
                BreathingAura(
                    live = cps != null,
                    tint = breathTint,
                    periodMillis = breathPeriod,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
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
                            // Состояние и величина ОДНОЙ строкой. Знаменатель назван
                            // в самой строке: «недавний уровень» приложение считает
                            // само, и он НЕ точка отсчёта.
                            StatusRow(
                                text = NavigateVerdict.trendLine(state, t),
                                color = trendColor,
                            )
                        }
                        if (state.reference == null) {
                            // Шкала стоит и до отсчёта: пустой прибор — это прибор, а
                            // экран без него выглядел как экран без функции.
                            NavigateIndicator(
                                indicator = indicator,
                                spec = NavigateGaugeSpec(
                                    ratio = null,
                                    peakRatio = null,
                                    factor = NavigateArc.LADDER.first(),
                                    trend = state.trend,
                                    referenceLabel = "1×",
                                    lowLabel = "${factorLabel(1.0 / NavigateArc.LADDER.first())}×",
                                    highLabel = "${factorLabel(NavigateArc.LADDER.first())}×",
                                    referenceCaption = t.navScaleReference,
                                    lowCaption = t.navScaleWeaker,
                                    highCaption = t.navScaleStronger,
                                ),
                            )
                            StatusRow(text = t.navSetupTitle, color = colors.ink)
                            Hint(text = t.navSetupBody, style = type.bodySmall, color = colors.ink2)
                            // Главное действие живёт ВНУТРИ своего блока, а не
                            // отдельной кнопкой внизу экрана: там было не видно, к
                            // чему оно.
                            AppButton(
                                text = t.navMark,
                                onClick = onMark,
                                primary = true,
                                enabled = cps != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Dimens.space1),
                            ) {
                                // Направление здесь считается ОТ ТОЧКИ ОТСЧЁТА, а
                                // состояние над ним — от недавнего уровня:
                                // знаменатели разные, и это не одна фраза дважды.
                                StatusRow(
                                    text = NavigateVerdict.referenceDirection(delta, t),
                                    color = deltaColor,
                                )
                                Text(
                                    text = NavigateVerdict.ratioHeadline(state, t),
                                    style = type.valueHero.copy(fontSize = 34.sp, lineHeight = 36.sp),
                                    color = deltaColor,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = NavigateVerdict.deltaCaption(state, delta, t),
                                    style = type.footnote,
                                    color = colors.ink2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            // Одно утверждение, два рисунка: спецификация общая, и
                            // вид не может изменить показание.
                            NavigateIndicator(
                                indicator = indicator,
                                spec = NavigateGaugeSpec(
                                    ratio = referenceRatio,
                                    peakRatio = peakRatio,
                                    factor = factor,
                                    trend = state.trend,
                                    referenceLabel = "1×",
                                    lowLabel = "${factorLabel(1.0 / factor)}×",
                                    highLabel = "${factorLabel(factor)}×",
                                    referenceCaption = t.navScaleReference,
                                    lowCaption = t.navScaleWeaker,
                                    highCaption = t.navScaleStronger,
                                    intervalLow = state.referenceComparison
                                        ?.ratioLow?.takeIf { it.isFinite() },
                                    intervalHigh = state.referenceComparison
                                        ?.ratioHigh?.takeIf { it.isFinite() },
                                ),
                            )
                            // Одна фраза о том, можно ли на вывод опереться, и
                            // кнопка с числами. Интервал и порог сняты с рабочего
                            // экрана: их разбирают, когда сомневаются, а не пока
                            // несут прибор.
                            NavigateVerdict.unresolvedNote(delta, t)?.let {
                                Text(text = it, style = type.footnote, color = colors.muted)
                            }
                            Chip(
                                text = t.navWhy,
                                color = colors.dataText,
                                onClick = { whyOpen = true },
                            )
                        }
                    }
                }
                if (state.trace.isEmpty()) {
                    Text(text = t.waitingStream, style = type.bodySmall, color = colors.muted)
                } else {
                    // Пунктир ленты — то, С ЧЕМ идёт сравнение прямо сейчас:
                    // поставленная точка отсчёта, а до неё — уровень, который
                    // приложение считает само. Две линии сразу означали бы на
                    // картинке два разных сравнения одновременно.
                    val referenceRate = state.reference?.ratePerSecond
                    NavigateTrace(
                        spec = NavigateTraceSpec(
                            points = state.trace,
                            nowMillis = state.trace.last().timeMillis,
                            spanMillis = NavigateEngine.TRACE_MILLIS,
                            localLevel = (referenceRate ?: state.local?.ratePerSecond)
                                ?.toFloat(),
                            localLabel = referenceRate
                                ?.let { t.navReferenceLevel(Uncertainty.num1(it.toFloat())) }
                                ?: state.local?.ratePerSecond
                                    ?.let { t.navLocalLevel(Uncertainty.num1(it.toFloat())) },
                            startLabel = t.navTraceStart,
                            endLabel = strings.nowLabel,
                        ),
                        height = if (state.reference != null) 96.dp else 72.dp,
                    )
                }
                // Максимум остаётся вторичной подписью и только когда он
                // есть: с направлением изменения он не соревнуется. Назван
                // он сессией, а не окном ленты: держится он с начала
                // прогона, и «68 с назад» под окном в 20 с читалось как
                // противоречие.
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
                    Hint(text = t.navSpotNote)
                }
            }

            // Счёт держится ровно — экран предлагает сменить вопрос, но не
            // решает: остановка не наблюдается, а запуск проверки по спокойному
            // сигналу дал бы измерение с предрешённым результатом.
            null -> if (offerVerify) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        StatusRow(text = t.offerVerifyTitle, color = colors.ink)
                        Hint(
                            text = t.offerVerifyBody,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                            AppButton(
                                text = t.offerVerifyAction,
                                onClick = onOfferAccept,
                                primary = true,
                                modifier = Modifier.weight(1f),
                            )
                            AppButton(
                                text = strings.cancel,
                                onClick = onOfferDismiss,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                Unit
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
        // Пока отсчёта нет, здесь нет и ряда: единственное действие стоит в
        // своём блоке выше, рядом с объяснением, что оно даёт. Как только
        // отсчёт поставлен, ряд становится его СОСТОЯНИЕМ — величина, момент
        // и два действия над ним. Названия говорят, что произойдёт:
        // «Обновить» читалось как «обновить данные», а «Снять» — как «снять
        // измерение», то есть ровно наоборот.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            NavigateVerdict.referenceLine(state.reference, referenceTime, t)?.let { line ->
                Text(
                    text = line,
                    style = type.value,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                AppButton(text = t.navMarkUpdate, onClick = onMark, enabled = cps != null)
                // Пока отсчёт стоит, дуга и отклик считают от него: снять его
                // нужно уметь, не уходя с экрана.
                AppButton(text = t.navMarkClear, onClick = onClearMark)
            } ?: Spacer(Modifier.weight(1f))
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

/** Один вид индикатора на оба места: до отсчёта и после него. */
@Composable
private fun NavigateIndicator(indicator: SearchIndicator, spec: NavigateGaugeSpec) {
    when (indicator) {
        SearchIndicator.NEEDLE -> NavigateGauge(spec = spec, height = 124.dp)
        SearchIndicator.SCALE -> NavigateScale(spec = spec, height = 96.dp)
    }
}
