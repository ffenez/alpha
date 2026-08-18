package app.alpha.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
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
import app.alpha.ui.components.NavigateGauge
import app.alpha.ui.components.NavigateGaugeSpec
import app.alpha.ui.components.NavigateTrace
import app.alpha.ui.components.NavigateTraceSpec
import app.alpha.ui.components.NavigateWhySheet
import app.alpha.ui.components.EntityMenuItem
import app.alpha.ui.components.EntityMenuButton
import app.alpha.ui.components.StatusRow
import app.alpha.ui.logic.NavigateArc
import app.alpha.ui.logic.NavigateEngine
import app.alpha.ui.logic.NavigateState
import app.alpha.ui.logic.SearchConfidence
import app.alpha.ui.logic.SearchPulse
import app.alpha.ui.logic.SearchUiState
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
import app.alpha.ui.theme.LocalAppMetrics
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.Motion
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
    /**
     * Единое состояние экрана: из него — «ждём данные», стрелка, отношение и
     * видимость главного действия. Ни одно из этих решений экран не принимает
     * сам, иначе они снова разойдутся ([SearchUiState]).
     */
    ui: SearchUiState,
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
    /** Счёт держится ровно — предложить проверку здесь. */
    offerVerify: Boolean = false,
    onOfferAccept: () -> Unit = {},
    onOfferDismiss: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    var whyOpen by remember { mutableStateOf(false) }

    val trendColor = when (state.trend) {
        NavigateTrend.RISING -> colors.warn
        NavigateTrend.FALLING -> colors.ink2
        NavigateTrend.NO_CHANGE -> colors.ink
        NavigateTrend.COLLECTING -> colors.muted
    }
    val factor = state.scale?.factor ?: NavigateArc.LADDER.first()
    val referenceRatio = ui.ratioOrNull
    val delta = NavigateEngine.referenceDelta(state)
    // Цвет главного числа — цвет ЭКРАНА (эталон): у Поиска он «цвет данных»,
    // и янтарным становится только подтверждённое усиление. Один цвет на
    // число, дыхание и заливку прибора: один смысл — один цвет.
    val numberColor = when {
        !ui.live -> colors.muted
        ui is SearchUiState.ReferenceReady && ui.confidence == SearchConfidence.ABOVE ->
            colors.warn
        else -> colors.dataText
    }
    val animatedNumberColor by animateColorAsState(
        targetValue = numberColor,
        animationSpec = Motion.normal(),
        label = "searchNumber",
    )

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
    // Дыхание — тем же смыслом, что и число; тусклое число (нет потока) не
    // делает свечение серым, оно просто застывает.
    val breathTint = if (numberColor == colors.warn) colors.warn else colors.data

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
                            // Цвет числа — состояние ИЗМЕРЕНИЯ, а не признак
                            // «данные идут»: пока сравнивать не с чем, число
                            // нейтральное, и зелёного рядом с «ждём данные» на
                            // экране больше не бывает.
                            // Число и есть вход в разбор — как на Главной:
                            // отдельная кнопка «Почему?» занимала строку тем,
                            // что уже есть на экране.
                            Text(
                                text = cps?.let { Uncertainty.num1(it) } ?: "—",
                                style = type.valueHero.copy(fontSize = 52.sp, lineHeight = 54.sp),
                                color = animatedNumberColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(LocalAppMetrics.current.radiusChip))
                                    .clickable(
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        },
                                        indication = null,
                                        onClick = { whyOpen = true },
                                    )
                                    .padding(horizontal = Dimens.space2),
                            )
                            // Под числом — одна тихая строка (эталон): после
                            // отсчёта это отношение со знаменателем и
                            // интервалом, до него — сравнение с недавним
                            // уровнем. Смена строки — переход состояния
                            // интерфейса, и ей можно ехать плавно.
                            AnimatedContent(
                                targetState = ui is SearchUiState.ReferenceReady,
                                transitionSpec = {
                                    fadeIn(Motion.normal()) togetherWith fadeOut(Motion.fast())
                                },
                                label = "footLine",
                            ) { withReference ->
                                if (withReference) {
                                    Text(
                                        text = NavigateVerdict.referenceSummary(state, delta, t)
                                            ?: "",
                                        style = type.footnote,
                                        color = colors.ink2,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    StatusRow(
                                        text = NavigateVerdict.trendLine(state, t),
                                        color = trendColor,
                                    )
                                }
                            }
                        }
                        // Один прибор во всех состояниях: та же геометрия, тот
                        // же кадр, разница только в наличии стрелки. Вердикт —
                        // гравировкой под осью: он описывает показание.
                        NavigateIndicator(
                            spec = NavigateGaugeSpec(
                                ratio = referenceRatio,
                                factor = factor,
                                trend = state.trend,
                                referenceLabel = "1×",
                                statusText = if (ui is SearchUiState.ReferenceReady) {
                                    confidenceLine(ui.confidence, t)
                                } else {
                                    t.navScaleNoReference
                                },
                            ),
                        )
                        // Большое действие живёт ровно в одном состоянии —
                        // пока точки отсчёта нет. После сохранения на его
                        // месте НИЧЕГО (эталон): разбор — по числу, действия
                        // над точкой — в её строке под карточкой. Уход кнопки
                        // едет плавно, чтобы карточка не прыгала.
                        AnimatedVisibility(
                            visible = state.reference == null,
                            enter = fadeIn(Motion.normal()) + expandVertically(Motion.springy()),
                            exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.springy()),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                            ) {
                                Hint(
                                    text = t.navSetupBody,
                                    style = type.bodySmall,
                                    color = colors.ink2,
                                )
                                AppButton(
                                    text = t.navMark,
                                    onClick = onMark,
                                    primary = true,
                                    enabled = ui.live,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                // «Ждём данные прибора» — ТОЛЬКО про поток. Пока идут отсчёты,
                // этой строки не бывает, даже если лента ещё пуста: пустая
                // лента живёт секунду и говорит не о приборе, а о себе.
                if (!ui.live) {
                    Text(text = t.waitingStream, style = type.bodySmall, color = colors.muted)
                } else if (state.reference != null && state.trace.isNotEmpty()) {
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
                            // У пунктира — только число, у самой оси: полная
                            // подпись «точка отсчёта 23,6» ложилась поверх
                            // линии и спорила с ней за место. Чей это уровень,
                            // сказано строкой отсчёта под графиком.
                            localLabel = (referenceRate ?: state.local?.ratePerSecond)
                                ?.let { Uncertainty.num1(it.toFloat()) },
                            startLabel = t.navTraceStart,
                            endLabel = strings.nowLabel,
                        ),
                        height = 72.dp,
                    )
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
        // Пока отсчёта нет, ряда нет вовсе: единственное действие стоит в
        // карточке выше, рядом с объяснением, что оно даёт. После сохранения
        // отсчёт становится СТРОКОЙ — величина, момент и «⋮»: три больших
        // кнопки внизу весили больше, чем сам отсчёт, о котором говорили.
        state.reference?.let { reference ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Text(
                    text = t.navReferenceRow(
                        Uncertainty.num1(reference.ratePerSecond.toFloat()),
                        referenceTime,
                    ),
                    style = type.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                EntityMenuButton(
                    menu = listOf(
                        EntityMenuItem(t.navMarkUpdate, enabled = cps != null, onClick = onMark),
                        EntityMenuItem(t.navMarkClear, onClick = onClearMark),
                        EntityMenuItem(
                            t.navMeasureHere(SpotMeasureMachine.TARGET_SECONDS),
                            enabled = spot !is SpotMeasure.Running,
                            onClick = onMeasureHere,
                        ),
                        EntityMenuItem(
                            t.navResetPeak,
                            enabled = state.peak != null,
                            onClick = onResetPeak,
                        ),
                    ),
                )
            }
            // Максимум за сессию — рядом со своей сущностью: он живёт от
            // точки отсчёта и сбрасывается из её же меню, а в карточке прибора
            // был посторонней строкой (эталон её не рисует).
            NavigateVerdict.peakLine(state, nowMillis, t)?.let {
                Text(text = it, style = type.footnote, color = colors.muted)
            }
        }
    }
}

/**
 * Прибор «Поиска»: один рисунок на все места, где стоит эта шкала — до точки
 * отсчёта и после неё в «Наведении», и на «Проверке», где знаменатель другой.
 *
 * Вида два не бывает: шкала — это прибор, а у прибора одно лицо. Прямая шкала,
 * которая жила здесь вторым вариантом, укладывала ту же лестницу в строку и
 * читалась как индикатор заряда, а не как измерение.
 */
@Composable
fun NavigateIndicator(spec: NavigateGaugeSpec) {
    // Циферблат в 220° высок по построению: при меньшей высоте радиус
    // считается по ней, и прибор снова съёживается в узкий сектор.
    NavigateGauge(spec = spec, height = 210.dp)
}

/**
 * Строка достоверности — одна на все состояния сравнения.
 *
 * Отделена от отношения намеренно: величина разницы и уверенность в ней
 * отвечают на разные вопросы и могут существовать одновременно.
 */
private fun confidenceLine(confidence: SearchConfidence, t: SearchStrings): String =
    when (confidence) {
        SearchConfidence.INSUFFICIENT -> t.navConfidenceInsufficient
        SearchConfidence.NO_DIFFERENCE -> t.navConfidenceNoDifference
        SearchConfidence.ABOVE -> t.navConfidenceAbove
        SearchConfidence.BELOW -> t.navConfidenceBelow
    }

