package app.alpha.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.SpectrumUnmix
import app.alpha.data.TemplateRepository
import app.alpha.data.db.SpectrumTemplateEntity
import app.alpha.device.ConnectionState
import app.alpha.device.DeviceModel
import app.alpha.ui.components.AppBackButton
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.Card
import app.alpha.ui.components.ChartNotesDialog
import app.alpha.ui.components.Chip
import app.alpha.ui.components.ExplainInfoButton
import app.alpha.ui.components.Hint
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.InstrumentCapability
import app.alpha.ui.logic.Uncertainty
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.UnmixCatalogue
import app.alpha.ui.text.UnmixStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Полноспектральное разложение: из чего состоит накопленный спектр.
 *
 * Экран отвечает на один вопрос — **какие известные формы и в какой доле
 * объясняют то, что набрано**. Поэтому здесь три вещи и ничего больше:
 * библиотека шаблонов с их годностью к этому прибору, состав с долями и
 * пределами, и честный вердикт о согласии модели с данными.
 *
 * Ни беккерелей, ни названий пород: доля формы — это доля формы.
 */
@Composable
fun UnmixScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = UnmixCatalogue.of(strings.language)
    val h = HistoryCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    val connection by graph.serviceStatus.connection.collectAsState()
    val connected = connection as? ConnectionState.Connected
    val model = connected?.info?.model ?: DeviceModel.UNKNOWN
    val serial = connected?.info?.serialNumber
    val spectral = InstrumentCapability.spectral(connected?.info?.model)

    val templates by graph.templateRepository.templates()
        .collectAsState(initial = emptyList())

    var message by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SpectrumUnmix.Result?>(null) }
    var running by remember { mutableStateOf(false) }
    var methodOpen by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                message = importTemplateFile(graph, context, uri, t)
                reload++
            }
        }
    }

    if (methodOpen) {
        ChartNotesDialog(
            title = t.methodTitle,
            notes = listOf(
                t.methodWhole,
                t.methodPoisson,
                t.methodScale,
                t.methodDevice,
                t.methodNoBecquerel,
            ),
        ) { methodOpen = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onBack = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = t.title, color = colors.ink)
            ExplainInfoButton(
                onClick = { methodOpen = true },
                modifier = Modifier.padding(start = Dimens.space1),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppButton(
                        text = t.recordTemplate,
                        enabled = spectral,
                        onClick = {
                            scope.launch {
                                message = recordTemplate(graph, model, serial, t)
                                reload++
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // Импорт — чтение файла, прибор для него не нужен: чужой
                    // шаблон разбирается и без подключения.
                    Chip(
                        text = t.importTemplate,
                        color = colors.ink2,
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                    )
                }
                Hint(text = t.recordHint)
                message?.let { Text(text = it, style = type.footnote, color = colors.ink2) }
            }
        }

        if (templates.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    Text(text = t.emptyTitle, style = type.title, color = colors.ink)
                    Text(text = t.emptyBody, style = type.bodySmall, color = colors.ink2)
                }
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = t.templatesTitle.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                for (entity in templates) {
                    TemplateRow(
                        entity = entity,
                        fitness = graph.templateRepository.fitness(
                            entity = entity,
                            serial = serial,
                            resolution662 = model.peakResolution662,
                        ),
                        t = t,
                        h = h,
                        onDelete = {
                            scope.launch {
                                graph.templateRepository.delete(entity.id)
                                result = null
                            }
                        },
                    )
                }
                AppButton(
                    text = t.run,
                    enabled = !running && spectral,
                    onClick = {
                        running = true
                        scope.launch {
                            val outcome = withContext(Dispatchers.Default) {
                                decompose(graph, model, serial)
                            }
                            result = outcome
                            if (outcome == null) message = t.failed
                            running = false
                        }
                    },
                )
            }
        }

        result?.let { ResultCard(result = it, t = t) }
    }
}

@Composable
private fun TemplateRow(
    entity: SpectrumTemplateEntity,
    fitness: TemplateRepository.Fitness,
    t: UnmixStrings,
    h: app.alpha.ui.text.HistoryStrings,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = t.templateLine(
                    name = entity.name,
                    duration = HistoryFormat.duration(entity.durationSeconds, h),
                    device = entity.deviceName ?: t.deviceUnknown,
                ),
                style = type.bodySmall,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Chip(text = t.deleteTemplate, color = colors.ink2, onClick = onDelete)
        }
        // Годность к ЭТОМУ прибору — критическая строка: чужая форма меняет
        // состав молча, и промолчать о ней нельзя.
        Text(
            text = when (fitness) {
                TemplateRepository.Fitness.OWN -> t.fitnessOwn
                TemplateRepository.Fitness.FOREIGN -> t.fitnessForeign
                TemplateRepository.Fitness.REFUSED -> t.fitnessRefused
            },
            style = type.footnote,
            color = when (fitness) {
                TemplateRepository.Fitness.OWN -> colors.muted
                TemplateRepository.Fitness.FOREIGN -> colors.ink2
                TemplateRepository.Fitness.REFUSED -> colors.warn
            },
        )
    }
}

@Composable
private fun ResultCard(result: SpectrumUnmix.Result, t: UnmixStrings) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val total = result.components.sumOf { it.counts }.coerceAtLeast(1.0)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            for (component in result.components) {
                val percent = 100.0 * component.counts / total
                Text(
                    text = if (component.detected) {
                        t.componentShare(
                            name = component.name,
                            percent = Uncertainty.num1(percent.toFloat()),
                            sigma = Uncertainty.num1(
                                (100.0 * component.sigma / component.scale.coerceAtLeast(1e-9) *
                                    percent / 100.0).toFloat(),
                            ),
                        )
                    } else {
                        t.componentBelowLimit(
                            name = component.name,
                            limitPercent = Uncertainty.num1(
                                (100.0 * component.criticalScale /
                                    component.scale.coerceAtLeast(1e-9) * percent / 100.0)
                                    .toFloat(),
                            ),
                        )
                    },
                    style = type.bodySmall,
                    color = if (component.detected) colors.ink else colors.muted,
                )
            }
            // Согласие — вердикт; доля объяснённого стоит рядом как справка, а
            // не как критерий.
            Text(
                text = if (result.consistent) {
                    t.agreementOk(Uncertainty.num1(result.cashDeviation.toFloat()))
                } else {
                    t.agreementBad(Uncertainty.num1(result.cashDeviation.toFloat()))
                },
                style = type.bodySmall,
                color = if (result.consistent) colors.ok else colors.warn,
            )
            Text(
                text = listOf(
                    t.explained(Uncertainty.num1((100.0 * result.explainedFraction).toFloat())),
                    if (result.gain == 1.0 && result.offsetKeV == 0.0) {
                        t.scaleAsMeasured
                    } else {
                        t.scaleFitted(
                            gain = Uncertainty.num2(result.gain.toFloat()),
                            offset = Uncertainty.signed1(result.offsetKeV.toFloat()),
                        )
                    },
                ).joinToString(" · "),
                style = type.footnote,
                color = colors.muted,
            )
        }
    }
}

/** Записать текущее накопление шаблоном этого прибора. */
private suspend fun recordTemplate(
    graph: AppGraph,
    model: DeviceModel,
    serial: String?,
    t: UnmixStrings,
): String {
    val spectrum = graph.spectrumHub.state.value.spectrum ?: return t.needSpectrum
    if (spectrum.counts.isEmpty() || spectrum.durationSeconds <= 0L) return t.needSpectrum
    graph.templateRepository.record(
        // Имя даётся временем: переименование — отдельное действие, а
        // безымянный шаблон в списке не отличить от соседнего.
        name = HistoryFormat.timeOfDay(System.currentTimeMillis()),
        counts = spectrum.counts,
        calibration = EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2),
        seconds = spectrum.durationSeconds,
        resolution662 = model.peakResolution662,
        deviceSerial = serial,
        deviceName = model.takeIf { it != DeviceModel.UNKNOWN }?.displayName,
        atMillis = System.currentTimeMillis(),
    )
    return t.recordTemplate
}

/**
 * Разложить накопленный спектр по пригодным шаблонам.
 *
 * Непригодные (у прибора разрешение лучше) в подгонку не берутся вовсе:
 * приведение для них невозможно, а тихо выбросить форму значит изменить состав.
 */
private suspend fun decompose(
    graph: AppGraph,
    model: DeviceModel,
    serial: String?,
): SpectrumUnmix.Result? {
    val spectrum = graph.spectrumHub.state.value.spectrum ?: return null
    val repository = graph.templateRepository
    val usable = repository.all().filter {
        repository.fitness(it, serial, model.peakResolution662) !=
            TemplateRepository.Fitness.REFUSED
    }
    if (usable.isEmpty()) return null
    return SpectrumUnmix.of(
        counts = spectrum.counts,
        calibration = EnergyCalibration(spectrum.a0, spectrum.a1, spectrum.a2),
        resolution662 = model.peakResolution662,
        templates = usable.map { repository.template(it) },
    )
}
