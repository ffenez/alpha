@file:OptIn(ExperimentalLayoutApi::class)

package app.alpha.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.alpha.AppGraph
import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.EnergyWindowSpec
import app.alpha.analysis.EnergyWindows
import app.alpha.analysis.SpectrumDisplay
import app.alpha.ui.components.DisclosureArrow
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.AppTextField
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.logic.EnergyBounds
import app.alpha.ui.logic.Evidence
import app.alpha.ui.logic.SpectrumFormat
import app.alpha.ui.logic.SpectrumScale
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.SpectrumCatalogue
import app.alpha.ui.text.SpectrumStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import app.alpha.ui.theme.Motion
import app.alpha.ui.theme.chartField
import kotlinx.coroutines.launch

/**
 * Спектральные диапазоны (спец §7) на экране Спектр.
 *
 * Блок СВЁРНУТ по умолчанию и в свёрнутом виде говорит ровно одно: сколько
 * диапазонов и какое между крайними отношение. Границы — параметр анализа,
 * выбранный нами, а не свойство излучения, и постоянно занимать место под
 * настоящим спектром с настоящими пиками они не должны.
 *
 * Развёрнутый вид — таблица «диапазон · с⁻¹ ± σ · доля» и спектральное
 * отношение с неопределённостью. Абсолютный счёт уехал в «подробности»: при
 * миллионах импульсов число `3 782 400` не добавляет к пониманию ничего, чего
 * не сказали бы скорость и доля.
 *
 * Что обязано оставаться на экране: спектральное отношение — ОПИСАНИЕ состава
 * спектра, не мера опасности и не дозиметрическая величина, и это не
 * «жёсткость» (та приходит от прибора как Ḋ/R).
 */
@Composable
fun SpectralRangesCard(
    graph: AppGraph,
    counts: List<Int>,
    durationSeconds: Long,
    calibration: EnergyCalibration,
    /** Технические данные открываются из «⋮» экрана, а не чипом в карточке. */
    technicalOpen: Boolean = false,
    onCloseTechnical: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = SpectrumCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()

    val raw by graph.settings.energyWindowsRaw.collectAsState(initial = null)
    val expanded by graph.settings.spectralRangesExpanded.collectAsState(initial = false)
    val specs = remember(raw) { EnergyWindows.parse(raw) }
    var explaining by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }

    val analysis = remember(counts, durationSeconds, calibration, specs) {
        EnergyWindows.analyze(counts, durationSeconds, calibration, specs)
    }
    val index = analysis.index

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { graph.settings.setSpectralRangesExpanded(!expanded) }
                    },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = t.windowsTitle.uppercase(),
                            style = type.labelSmall,
                            color = colors.ink2,
                        )
                                            }
                    if (!expanded) {
                        // В свёрнутом виде — только сколько диапазонов.
                        // Отношение стояло рядом числом без знаменателя и
                        // читалось как вывод; оно живёт развёрнутой строкой,
                        // где рядом стоят сами диапазоны.
                        Text(
                            text = t.rangesSummary(analysis.windows.size, null),
                            style = type.footnote,
                            color = colors.muted,
                        )
                    }
                }
                DisclosureArrow(expanded = expanded)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
                exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Компактно: диапазон · скорость · доля. Импульсы, ±σ и
                    // покрытие каналами — в технических данных: они нужны
                    // редко и подробно, а место занимали всегда.
                    RangesTable(analysis, details = false, t = t)
                    RatioRow(index, t, onExplain = { explaining = true })
                    // Кнопка настройки диапазонов уехала в Настройки → Прибор:
                    // границы это ПАРАМЕТР АНАЛИЗА, который задают один раз, а
                    // на рабочем экране он занимал место постоянно.
                }
            }
        }
    }

    if (explaining) {
        RatioExplainDialog(analysis, t, onDismiss = { explaining = false })
    }
    if (technicalOpen) {
        TechnicalDataSheet(analysis, t, onDismiss = onCloseTechnical)
    }
}

/**
 * Технические данные диапазонов.
 *
 * Импульсы, стандартная неопределённость и фактические границы каналов — то,
 * чем проверяют результат, а не то, чем его читают. На рабочем экране они
 * занимали место всегда и читались один раз; здесь они собраны вместе и
 * открываются по требованию.
 */
@Composable
private fun TechnicalDataSheet(
    analysis: EnergyWindows.Analysis,
    t: SpectrumStrings,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = t.technicalTitle, style = type.title, color = colors.ink)
                analysis.windows.forEach { window ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = SpectrumFormat.rangeLabel(window.spec),
                            style = type.label,
                            color = colors.ink,
                        )
                        if (window.isEmpty) {
                            Text(text = "—", style = type.footnote, color = colors.muted)
                        } else {
                            Text(
                                text = SpectrumFormat.rangeRate(window),
                                style = type.valueSmall,
                                color = colors.ink2,
                            )
                            Text(
                                text = t.rangeCounts(
                                    SpectrumFormat.groupThousands(window.counts),
                                ) + " · " + t.rangeCovered(SpectrumFormat.rangeCovered(window)),
                                style = type.footnote,
                                color = colors.muted,
                            )
                        }
                    }
                }
                analysis.index?.let { index ->
                    AppDivider()
                    Text(text = t.ratioTitle, style = type.label, color = colors.ink)
                    Text(
                        text = SpectrumFormat.ratioValue(index),
                        style = type.valueSmall,
                        color = colors.ink2,
                    )
                }
                Text(text = t.windowsEdgeNote, style = type.footnote, color = colors.muted)
                AppButton(
                    text = strings.close,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Таблица диапазонов. Шапка НЕ переводится в верхний регистр: «с⁻¹ ± σ»
 * превращалось в «С⁻¹ ± Σ», и обозначение стандартной неопределённости
 * читалось как знак суммы. Обозначения величин пишутся так, как их пишет
 * физика, а не так, как удобно оформлению.
 */
@Composable
private fun RangesTable(
    analysis: EnergyWindows.Analysis,
    details: Boolean,
    t: SpectrumStrings,
) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        RangeHeader(t.columnWindow, 1.2f)
        RangeHeader(t.columnRate, 1.4f)
        RangeHeader(t.columnShare, 0.7f)
    }
    AppDivider()
    analysis.windows.forEachIndexed { i, window ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        ) {
            RangeCell(SpectrumFormat.rangeLabel(window.spec), 1.2f, colors.ink)
            RangeCell(
                if (window.isEmpty) "—" else SpectrumFormat.rangeRate(window),
                1.4f,
                colors.ink,
            )
            RangeCell(
                if (window.isEmpty) "—" else SpectrumFormat.rangeShare(window),
                0.7f,
                colors.ink2,
            )
        }
        if (details && !window.isEmpty) {
            Text(
                text = t.rangeCounts(SpectrumFormat.groupThousands(window.counts)) +
                    " · " + t.rangeCovered(SpectrumFormat.rangeCovered(window)),
                style = LocalAppTypography.current.footnote,
                color = colors.muted,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        if (i < analysis.windows.size - 1) AppDivider()
    }
    if (details) {
        Text(
            text = t.windowsEdgeNote,
            style = LocalAppTypography.current.footnote,
            color = colors.muted,
        )
    }
}

/** Спектральное отношение: значение с неопределённостью и дверь в «i». */
@Composable
private fun RatioRow(
    index: EnergyWindows.SpectralIndex?,
    t: SpectrumStrings,
    onExplain: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    AppDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
    ) {
        Text(
            text = t.ratioTitle,
            style = type.bodySmall,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = index?.let { SpectrumFormat.ratioValue(it) } ?: "—",
            style = type.valueSmall,
            color = colors.ink,
        )
        Chip(text = "i", color = colors.ink2, onClick = onExplain)
    }
}

/**
 * «i» у отношения: формула, что оно описывает, чем НЕ является и чем оно не
 * приходится жёсткости прибора. Отказ «не мера опасности» стоит здесь же —
 * объяснение величины и её ограничение живут вместе.
 */
@Composable
private fun RatioExplainDialog(
    analysis: EnergyWindows.Analysis,
    t: SpectrumStrings,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val index = analysis.index
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(text = t.ratioTitle, style = type.title, color = colors.ink)
                if (index != null) {
                    Text(
                        text = t.ratioFormula(
                            SpectrumFormat.rangeLabel(index.lowWindow),
                            SpectrumFormat.rangeLabel(index.highWindow),
                        ),
                        style = type.footnoteMono,
                        color = colors.ink2,
                    )
                }
                Hint(text = t.ratioWhat, style = type.bodySmall, color = colors.ink2)
                Hint(text = t.ratioNotHardness, style = type.bodySmall, color = colors.ink2)
                Hint(text = t.indexNote)
                AppButton(
                    text = strings.close,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RowScope.RangeHeader(text: String, weight: Float) {
    Text(
        text = text,
        style = LocalAppTypography.current.overline,
        color = LocalAppColors.current.muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.RangeCell(text: String, weight: Float, color: Color) {
    Text(
        text = text,
        style = LocalAppTypography.current.valueSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight),
    )
}

/**
 * Настройка границ НА САМОМ СПЕКТРЕ: вертикальные ручки поверх реальной
 * кривой. Человек видит, какую часть спектра берёт диапазон, — из шести полей
 * с числами этого не видно совсем. Точный ввод числом остаётся: тап по числу
 * под полем открывает поле ввода.
 *
 * Правится ЦЕПОЧКА границ, поэтому пересечение и разрыв невозможны по
 * построению, а не запрещены проверкой после ввода.
 */
@Composable
internal fun BoundsEditorDialog(
    counts: List<Int>,
    calibration: EnergyCalibration,
    specs: List<EnergyWindowSpec>,
    t: SpectrumStrings,
    onDismiss: () -> Unit,
    onApply: (List<Float>) -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current

    val full = remember(calibration, counts.size) {
        SpectrumDisplay.fullWindow(calibration, counts.size)
    }
    val columns = remember(counts, full, calibration) {
        val range = SpectrumDisplay.channelRange(full, calibration, counts.size)
        SpectrumDisplay.aggregateMax(counts.map { it.toFloat() }, range, EDITOR_COLUMNS)
    }
    val yTop = remember(columns) { SpectrumDisplay.logTop(columns.maxOrNull() ?: 0f) }

    var bounds by remember(specs) { mutableStateOf(EnergyBounds.boundsOf(specs)) }
    var active by remember { mutableStateOf<Int?>(null) }
    var typing by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val preset = EnergyBounds.presetOf(bounds, full.startKeV, full.endKeV)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(Dimens.space3)) {
            // Экран может быть низким (ландшафт, крупный системный шрифт):
            // содержимое прокручивается, вместо того чтобы уехать за край.
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = t.boundsEditorTitle, style = type.title, color = colors.ink)

                BoundsField(
                    columns = columns,
                    yTop = yTop,
                    startKeV = full.startKeV,
                    endKeV = full.endKeV,
                    bounds = bounds,
                    active = active,
                    onGrab = { active = it },
                    onMove = { index, keV ->
                        bounds = EnergyBounds.move(
                            bounds = bounds,
                            index = index,
                            keV = keV,
                            minKeV = full.startKeV,
                            maxKeV = full.endKeV,
                        )
                        error = null
                    },
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    bounds.forEachIndexed { index, value ->
                        Chip(
                            text = value.toInt().toString(),
                            color = colors.ink,
                            selected = active == index || typing == index,
                            onClick = {
                                typing = index
                                active = index
                                draft = value.toInt().toString()
                            },
                        )
                    }
                    Text(text = t.unitKeV, style = type.axis, color = colors.muted)
                }

                typing?.let { index ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = boundName(index, bounds.size, t),
                            style = type.footnote,
                            color = colors.ink2,
                        )
                        AppTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            numeric = true,
                            placeholder = t.boundsExact,
                            modifier = Modifier.weight(1f),
                        )
                        AppButton(
                            text = t.done,
                            onClick = {
                                val value = draft.replace(',', '.').trim().toFloatOrNull()
                                if (value == null) {
                                    error = t.windowBoundsNotNumbers
                                } else {
                                    bounds = EnergyBounds.move(
                                        bounds = bounds,
                                        index = index,
                                        keV = value,
                                        minKeV = full.startKeV,
                                        maxKeV = full.endKeV,
                                    )
                                    error = null
                                    typing = null
                                    active = null
                                }
                            },
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    Chip(
                        text = t.presetDefault,
                        color = colors.ink2,
                        selected = preset == EnergyBounds.Preset.DEFAULT,
                        onClick = { bounds = EnergyBounds.defaults() },
                    )
                    Chip(
                        text = t.presetFullScale,
                        color = colors.ink2,
                        selected = preset == EnergyBounds.Preset.FULL_SCALE,
                        onClick = {
                            bounds = EnergyBounds.fullScale(
                                full.startKeV,
                                full.endKeV,
                                bounds.size - 1,
                            )
                        },
                    )
                    // «Свои» — состояние, а не действие: он загорается сам,
                    // когда числа перестали совпадать с пресетом.
                    Chip(
                        text = t.presetCustom,
                        color = colors.ink2,
                        selected = preset == EnergyBounds.Preset.CUSTOM,
                    )
                }
                if (preset == EnergyBounds.Preset.FULL_SCALE) {
                    Hint(
                        text = t.presetFullScaleNote,
                    )
                }
                error?.let { Text(text = it, style = type.footnote, color = colors.warn) }

                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    AppButton(
                        text = t.resetBounds,
                        onClick = {
                            bounds = EnergyBounds.defaults()
                            typing = null
                            error = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        text = t.done,
                        primary = true,
                        onClick = {
                            val next = EnergyBounds.toSpecs(bounds)
                            val reason = EnergyWindows.validate(next, t)
                            if (reason == null) onApply(bounds) else error = reason
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                AppButton(
                    text = strings.cancel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun boundName(index: Int, count: Int, t: SpectrumStrings): String = when (index) {
    0 -> t.boundsLower
    count - 1 -> t.boundsUpper
    else -> t.boundsInner(index)
}

/** Столбцов кривой в редакторе: меньше, чем на самом графике, — тут форма. */
private const val EDITOR_COLUMNS = 160

/** Промах мимо ручки: палец должен попасть в эту зону вокруг границы. */
private val GRAB_RADIUS = 24.dp

/**
 * Поле редактора: кривая спектра, залитые полосы диапазонов и вертикальные
 * ручки границ.
 *
 * Жест читает АКТУАЛЬНЫЕ границы и обработчики через [rememberUpdatedState]:
 * `pointerInput(Unit)` запускается один раз за жизнь узла, и без этого он
 * захватил бы первую версию лямбды — ту, что видит исходные границы. На этом
 * экране такая ловушка уже была (сдвиг спектра «не работал»), второй раз её
 * повторять нельзя.
 */
@Composable
private fun BoundsField(
    columns: List<Float>,
    yTop: Float,
    startKeV: Float,
    endKeV: Float,
    bounds: List<Float>,
    active: Int?,
    onGrab: (Int?) -> Unit,
    onMove: (Int, Float) -> Unit,
) {
    val colors = LocalAppColors.current
    val axisStyle = LocalAppTypography.current.axis
    val measurer = rememberTextMeasurer()
    val boundsState = rememberUpdatedState(bounds)
    val viewState = rememberUpdatedState(startKeV to endKeV)
    val grabState = rememberUpdatedState(onGrab)
    val moveState = rememberUpdatedState(onMove)
    val scale = SpectrumScale.Log

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .chartField()
            .pointerInput(Unit) {
                var grabbed: Int? = null
                val pad = FIELD_PAD.toPx()
                fun keVAt(x: Float): Float {
                    val plot = (size.width - 2 * pad).coerceAtLeast(1f)
                    val (from, to) = viewState.value
                    return EnergyBounds.keVAt((x - pad) / plot, from, to)
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        val plot = (size.width - 2 * pad).coerceAtLeast(1f)
                        val (from, to) = viewState.value
                        val tolerance = GRAB_RADIUS.toPx() / plot * (to - from)
                        grabbed = EnergyBounds.grab(
                            boundsState.value,
                            keVAt(offset.x),
                            tolerance,
                        )
                        grabState.value(grabbed)
                    },
                    onDragEnd = { grabbed = null; grabState.value(null) },
                    onDragCancel = { grabbed = null; grabState.value(null) },
                    onDrag = { change, _ ->
                        val index = grabbed ?: return@detectDragGestures
                        change.consume()
                        moveState.value(index, keVAt(change.position.x))
                    },
                )
            },
    ) {
        val pad = FIELD_PAD.toPx()
        val plotW = size.width - 2 * pad
        val plotH = size.height - 2 * pad
        if (plotW <= 0f || plotH <= 0f || columns.isEmpty() || yTop <= 0f) return@Canvas
        val bottom = pad + plotH

        fun x(keV: Float): Float = pad + EnergyBounds.fractionOf(keV, startKeV, endKeV) * plotW

        // 1. Полосы диапазонов: соседние различаются плотностью заливки —
        // одинаковые полосы читались бы как один диапазон.
        for (i in 0 until bounds.size - 1) {
            val from = x(bounds[i])
            val to = x(bounds[i + 1])
            drawRect(
                color = colors.data.copy(alpha = if (i % 2 == 0) 0.14f else 0.07f),
                topLeft = Offset(from, pad),
                size = Size((to - from).coerceAtLeast(0f), plotH),
            )
        }

        // 2. Кривая спектра — та же логарифмическая форма, что на графике.
        val line = Path()
        columns.forEachIndexed { i, value ->
            val px = pad + if (columns.size <= 1) 0f else i * plotW / (columns.size - 1)
            val py = pad + (1f - scale.fraction(value, yTop)) * plotH
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }
        drawPath(
            path = line,
            color = colors.data,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 3. Ручки границ; подписана только та, которую сейчас держат, —
        // четыре подписи рядом слипались бы на узких диапазонах. Держимая
        // ручка выделена цветом ДАННЫХ и толщиной: янтарь в этом приложении
        // означает «выше обычного», и тратить его на состояние жеста нельзя.
        bounds.forEachIndexed { i, value ->
            val px = x(value)
            val held = i == active
            drawLine(
                color = if (held) colors.dataText else colors.ink2,
                start = Offset(px, pad),
                end = Offset(px, bottom),
                strokeWidth = if (held) 2.dp.toPx() else 1.4.dp.toPx(),
            )
            drawCircle(
                color = if (held) colors.dataText else colors.ink2,
                radius = if (held) 8.dp.toPx() else 6.dp.toPx(),
                center = Offset(px, bottom),
            )
            if (held) {
                val label = measurer.measure(value.toInt().toString(), axisStyle)
                drawText(
                    textLayoutResult = label,
                    color = colors.ink,
                    topLeft = Offset(
                        (px - label.size.width / 2f)
                            .coerceIn(0f, size.width - label.size.width),
                        pad,
                    ),
                )
            }
        }
    }
}

private val FIELD_PAD = 8.dp
