package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.radiacode.AppGraph
import app.radiacode.analysis.AbAnalysis
import app.radiacode.analysis.EnergyCalibration
import app.radiacode.analysis.FoodScreening
import app.radiacode.analysis.PeakDetection
import app.radiacode.data.db.ExperimentEntity
import app.radiacode.data.db.SpectrumSnapshotEntity
import app.radiacode.data.toSpectrum
import app.radiacode.device.ConnectionState
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.AppTextField
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.Hint
import app.radiacode.ui.components.StatusRow
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.Uncertainty
import app.radiacode.ui.text.FoodCatalogue
import app.radiacode.ui.text.ExperimentCatalogue
import app.radiacode.ui.text.HistoryCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Шаг измерения продукта. Два прогона и вывод — больше здесь ничего нет. */
private enum class FoodStep { SETUP, BACKGROUND, SAMPLE, RESULT }

/**
 * «Проверить продукт» — сравнительный гамма-скрининг.
 *
 * Отдельного движка у этого экрана нет: он ставит обычный опыт (`experiments`,
 * вид [ExperimentEntity.KIND_FOOD]) с двумя прогонами — фон и образец, — и оба
 * прогона пишет тот же рекордер, что и A/B. Значит, спектр каждого прогона это
 * РАЗНОСТЬ снимков за его интервал, а не всё накопление прибора: 126 часов
 * фона не заслоняют полчаса измерения продукта.
 *
 * Чего экран не делает: не называет продукт безопасным, не считает беккерели и
 * не объявляет нуклид по совпадению энергии. Что он умеет — сказано словами в
 * справке, и она открывается прямо отсюда.
 */
@Composable
fun FoodScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = FoodCatalogue.of(strings.language)
    val h = HistoryCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()

    val connection by graph.serviceStatus.connection.collectAsState()
    val connected = connection is ConnectionState.Connected
    val hubState by graph.spectrumHub.state.collectAsState()
    val run by graph.abRun.state.collectAsState()

    var step by rememberSaveable { mutableStateOf(FoodStep.SETUP) }
    var experimentId by rememberSaveable { mutableLongStateOf(0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var mass by rememberSaveable { mutableStateOf("") }
    var container by rememberSaveable { mutableStateOf("") }
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<FoodScreening.Result?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(run) {
        while (run != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Пока прогон идёт, экран только смотрит: владеет прогоном рекордер, и
    // уход с экрана его не прерывает.
    suspend fun startRun(label: String) {
        val now = System.currentTimeMillis()
        val startId = hubState.spectrum?.let {
            graph.measurementRepository.saveSpectrum(
                spectrum = it,
                accumulated = false,
                origin = SpectrumSnapshotEntity.ORIGIN_DERIVED,
                trigger = SpectrumSnapshotEntity.TRIGGER_FOOD,
            ).id
        }
        val runId = graph.experimentRepository.startRun(
            experimentId = experimentId,
            label = label,
            startedAt = now,
            startSpectrumId = startId,
        )
        graph.abRun.start(experimentId, runId, label, plannedSeconds = 0L)
    }

    suspend fun computeResult() {
        val runs = graph.experimentRepository.runs(experimentId)
        val background = runs.firstOrNull { it.label == LABEL_BACKGROUND }
        val sample = runs.firstOrNull { it.label == LABEL_SAMPLE }
        if (background == null || sample == null) return
        val backgroundSpectrum = background.spectrumId
            ?.let { graph.measurementRepository.spectrumById(it) }?.toSpectrum()
        val sampleSpectrum = sample.spectrumId
            ?.let { graph.measurementRepository.spectrumById(it) }?.toSpectrum()
        if (backgroundSpectrum == null || sampleSpectrum == null) return

        val backgroundCounting = AbAnalysis.Counting(
            counts = backgroundSpectrum.counts.sumOf { it.toDouble() },
            seconds = backgroundSpectrum.durationSeconds.toDouble(),
        )
        val sampleCounting = AbAnalysis.Counting(
            counts = sampleSpectrum.counts.sumOf { it.toDouble() },
            seconds = sampleSpectrum.durationSeconds.toDouble(),
        )
        // Линии ищутся в РАЗНОСТИ образца и приведённого по времени фона.
        // Отрицательные каналы разности — статистический шум вычитания, и в
        // поиск пиков они идут нулями: пик из отрицательной площади не бывает.
        val lines = if (backgroundSpectrum.counts.size == sampleSpectrum.counts.size &&
            backgroundCounting.seconds > 0.0
        ) {
            val ratio = sampleCounting.seconds / backgroundCounting.seconds
            val net = sampleSpectrum.counts.mapIndexed { index, value ->
                (value - backgroundSpectrum.counts[index] * ratio).toInt().coerceAtLeast(0)
            }
            PeakDetection.detect(
                counts = net,
                calibration = EnergyCalibration(
                    sampleSpectrum.a0,
                    sampleSpectrum.a1,
                    sampleSpectrum.a2,
                ),
            ).map { FoodScreening.Line(it.energyKeV, it.significance.toDouble()) }
        } else {
            emptyList()
        }
        result = FoodScreening.screen(backgroundCounting, sampleCounting, lines)
    }

    if (guideOpen) {
        Dialog(onDismissRequest = { guideOpen = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(text = t.guideTitle, style = type.label, color = colors.ink)
                    t.guide().forEachIndexed { index, (heading, body) ->
                        if (index > 0) AppDivider()
                        Text(text = heading, style = type.labelSmall, color = colors.ink2)
                        Text(text = body, style = type.bodySmall, color = colors.ink)
                    }
                    AppButton(
                        text = strings.close,
                        onClick = { guideOpen = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(text = strings.back, onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "i", color = colors.ink2, onClick = { guideOpen = true })
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.title, style = type.title, color = colors.ink)
                Hint(text = t.subtitle, style = type.bodySmall, color = colors.ink2)

                when (step) {
                    FoodStep.SETUP -> {
                        AppTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = t.sampleName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = mass,
                            onValueChange = { mass = it },
                            placeholder = t.sampleMass,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppTextField(
                            value = container,
                            onValueChange = { container = it },
                            placeholder = t.container,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppButton(
                            text = t.start,
                            primary = true,
                            enabled = connected,
                            onClick = {
                                scope.launch {
                                    experimentId = graph.experimentRepository.create(
                                        kind = ExperimentEntity.KIND_FOOD,
                                        profileId = null,
                                        geometry = container.trim(),
                                        note = listOfNotNull(
                                            name.trim().ifBlank { null },
                                            mass.trim().ifBlank { null }?.let { "$it г" },
                                        ).joinToString(" · "),
                                    )
                                    step = FoodStep.BACKGROUND
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    FoodStep.BACKGROUND, FoodStep.SAMPLE -> {
                        val isBackground = step == FoodStep.BACKGROUND
                        StatusRow(
                            text = if (isBackground) t.stepBackground else t.stepSample,
                            color = colors.ink,
                        )
                        Text(
                            text = if (isBackground) t.backgroundHint else t.sampleHint,
                            style = type.bodySmall,
                            color = colors.ink2,
                        )
                        val active = run
                        if (active == null) {
                            AppButton(
                                text = t.start,
                                primary = true,
                                enabled = connected,
                                onClick = {
                                    scope.launch {
                                        startRun(
                                            if (isBackground) LABEL_BACKGROUND else LABEL_SAMPLE,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = HistoryFormat.duration(
                                    active.elapsedSeconds(nowMillis),
                                    s = h,
                                ),
                                style = type.valueLarge,
                                color = colors.ink,
                            )
                            AppButton(
                                text = ExperimentCatalogue.of(strings.language).stopRun,
                                onClick = {
                                    graph.abRun.stop()
                                    scope.launch {
                                        if (isBackground) {
                                            step = FoodStep.SAMPLE
                                        } else {
                                            step = FoodStep.RESULT
                                            computeResult()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    FoodStep.RESULT -> FoodResult(
                        result = result,
                        onContinue = {
                            step = FoodStep.SAMPLE
                        },
                        onRecompute = { scope.launch { computeResult() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodResult(
    result: FoodScreening.Result?,
    onContinue: () -> Unit,
    onRecompute: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = FoodCatalogue.of(strings.language)

    LaunchedEffect(result) { if (result == null) onRecompute() }
    val current = result ?: return

    val (title, body) = when (current.verdict) {
        FoodScreening.Verdict.NOT_ENOUGH_DATA -> t.verdictNotEnough to t.verdictNotEnoughBody
        FoodScreening.Verdict.NO_DIFFERENCE -> t.verdictNoDifference to t.verdictNoDifferenceBody
        FoodScreening.Verdict.EXCESS_WITHOUT_LINE -> t.verdictExcess to t.verdictExcessBody
        FoodScreening.Verdict.SPECTRAL_FEATURE -> t.verdictLine to t.verdictLineBody(
            Uncertainty.num1(current.lines.first().energyKev) + " кэВ",
        )
    }

    StatusRow(text = title, color = colors.ink)
    Text(text = body, style = type.bodySmall, color = colors.ink2)

    // Чувствительность — то, что превращает «отличий не найдено» из пустой
    // фразы в утверждение с границей.
    current.sensitivity?.let { sensitivity ->
        sensitivity.detectableFraction?.let { fraction ->
            Text(
                text = t.sensitivityLine(
                    Uncertainty.num1((fraction * 100).toFloat()) + " %",
                    Uncertainty.num2(sensitivity.detectableCps.toFloat()),
                ),
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
    Hint(text = t.screeningDisclaimer, style = type.footnote, color = colors.muted)
    AppButton(text = t.continueMeasuring, onClick = onContinue, modifier = Modifier.fillMaxWidth())
}

/** Метки прогонов: они же — подписи в отчёте и в Истории. */
private const val LABEL_BACKGROUND = "Фон"
private const val LABEL_SAMPLE = "Продукт"
