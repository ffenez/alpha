package app.radiacode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.radiacode.AppGraph
import app.radiacode.baseline.AlarmSensitivity
import app.radiacode.baseline.AlarmThresholds
import app.radiacode.baseline.BaselineState
import app.radiacode.baseline.alarmThresholds
import app.radiacode.data.AppSettings
import app.radiacode.data.DoseUnitSetting
import app.radiacode.device.ConnectionState
import app.radiacode.ui.components.PixelBox
import app.radiacode.ui.components.PixelButton
import app.radiacode.ui.components.PixelDivider
import app.radiacode.ui.components.PixelTag
import app.radiacode.ui.components.PixelTextField
import app.radiacode.ui.components.StatusLine
import app.radiacode.ui.logic.DoseFormat
import app.radiacode.ui.logic.Freshness
import app.radiacode.ui.logic.baselineCollectedWording
import app.radiacode.ui.logic.freshnessLabel
import app.radiacode.ui.logic.heldWording
import app.radiacode.ui.logic.learningWording
import app.radiacode.ui.theme.LocalPixelColors
import app.radiacode.ui.theme.LocalPixelTypography
import app.radiacode.ui.theme.PixelDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Настройки (SPEC: opens separately, not a tab). Hybrid style: pixel frames
 * and headers, system font for explanations. Sections: Тревоги, Места,
 * Прибор, Единицы, О приложении.
 */
@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PixelDimens.space4),
        verticalArrangement = Arrangement.spacedBy(PixelDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelButton(text = "НАЗАД", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text("НАСТРОЙКИ", style = type.heading, color = colors.text)
        }

        AlarmsSection(graph)
        PlacesSection(graph)
        DeviceSection(graph)
        UnitsSection(graph)
        AboutSection()
    }
}

// --- Тревоги ---

@Composable
private fun AlarmsSection(graph: AppGraph) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val scope = rememberCoroutineScope()
    val sensitivity by graph.settings.alarmSensitivity
        .collectAsState(initial = AlarmSensitivity.NORMAL)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)
    val customL1 by graph.settings.customAlarmL1MicroSvH
        .collectAsState(initial = AppSettings.DEFAULT_CUSTOM_L1_MICRO_SV_H)
    val customL2 by graph.settings.customAlarmL2MicroSvH
        .collectAsState(initial = AppSettings.DEFAULT_CUSTOM_L2_MICRO_SV_H)

    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text("ТРЕВОГИ", style = type.label, color = colors.text)
            Text(
                text = "Тревога срабатывает не от одиночного скачка: уровень должен " +
                    "превысить порог — по абсолютной величине или относительно " +
                    "обычного фона места — и продержаться указанное время.",
                style = type.bodySmall,
                color = colors.textSecondary,
            )

            SensitivityOption(
                title = "ОБЫЧНАЯ",
                selected = sensitivity == AlarmSensitivity.NORMAL,
                description = presetDescription(
                    alarmThresholds(AlarmSensitivity.NORMAL, 0f, 0f),
                    unit,
                ),
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.NORMAL) }
                },
            )
            SensitivityOption(
                title = "ВЫСОКАЯ",
                selected = sensitivity == AlarmSensitivity.HIGH,
                description = presetDescription(
                    alarmThresholds(AlarmSensitivity.HIGH, 0f, 0f),
                    unit,
                ),
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.HIGH) }
                },
            )
            SensitivityOption(
                title = "СВОЯ",
                selected = sensitivity == AlarmSensitivity.CUSTOM,
                description = "уровни мощности дозы задаются вручную",
                onSelect = {
                    scope.launch { graph.settings.setAlarmSensitivity(AlarmSensitivity.CUSTOM) }
                },
            )

            if (sensitivity == AlarmSensitivity.CUSTOM) {
                CustomLevels(graph, unit, customL1, customL2)
            }
        }
    }
}

@Composable
private fun SensitivityOption(
    title: String,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PixelDimens.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            ),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (selected) LocalPixelColors.current.accent else colors.surface2),
        )
        Column {
            Text(
                text = title,
                style = type.label,
                color = if (selected) colors.accent else colors.text,
            )
            Text(text = description, style = type.bodySmall, color = colors.textMuted)
        }
    }
}

private fun presetDescription(thresholds: AlarmThresholds, unit: DoseUnitSetting): String =
    "от ${DoseFormat.rateWithUnit(thresholds.l1MicroSvH, unit)} или " +
        "${formatFactor(thresholds.relativeFactor)}× обычного, " +
        heldWording(thresholds.persistenceSeconds.toLong())

private fun formatFactor(factor: Float): String =
    if (factor == factor.toInt().toFloat()) "${factor.toInt()}" else "$factor"

@Composable
private fun CustomLevels(
    graph: AppGraph,
    unit: DoseUnitSetting,
    storedL1MicroSvH: Float,
    storedL2MicroSvH: Float,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val scope = rememberCoroutineScope()

    // Inputs are in the display unit; stored values stay µSv/h.
    var l1Text by remember(storedL1MicroSvH, unit) {
        mutableStateOf(DoseFormat.rate(storedL1MicroSvH, unit))
    }
    var l2Text by remember(storedL2MicroSvH, unit) {
        mutableStateOf(DoseFormat.rate(storedL2MicroSvH, unit))
    }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
        LevelField("уровень 1, ${DoseFormat.rateUnitLabel(unit)}", l1Text) { l1Text = it }
        LevelField("уровень 2, ${DoseFormat.rateUnitLabel(unit)}", l2Text) { l2Text = it }
        error?.let { Text(text = it, style = type.bodySmall, color = colors.aboveUsual) }
        PixelButton(
            text = "СОХРАНИТЬ УРОВНИ",
            onClick = {
                val l1 = parseLevelToMicroSv(l1Text, unit)
                val l2 = parseLevelToMicroSv(l2Text, unit)
                when {
                    l1 == null || l2 == null -> error = "Введите числа, например 0.30"
                    l1 <= 0f -> error = "Уровень 1 должен быть больше нуля"
                    l2 < l1 -> error = "Уровень 2 не может быть ниже уровня 1"
                    else -> {
                        error = null
                        scope.launch { graph.settings.setCustomAlarmLevels(l1, l2) }
                    }
                }
            },
        )
        Text(
            text = "Уровень 1 — линия тревоги на графиках и порог отклонения; " +
                "уровень 2 — сильное превышение.",
            style = type.bodySmall,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun LevelField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space1)) {
        Text(text = label, style = type.labelSmall, color = colors.textSecondary)
        PixelTextField(value = value, onValueChange = onChange, numeric = true)
    }
}

/** Parses the display-unit input back to stored µSv/h; comma tolerated. */
private fun parseLevelToMicroSv(text: String, unit: DoseUnitSetting): Float? {
    val value = text.trim().replace(',', '.').toFloatOrNull() ?: return null
    return when (unit) {
        DoseUnitSetting.MICRO_SIEVERT -> value
        DoseUnitSetting.MICRO_ROENTGEN -> value / DoseFormat.MICRO_R_PER_MICRO_SV
    }
}

// --- Места ---

@Composable
private fun PlacesSection(graph: AppGraph) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val scope = rememberCoroutineScope()
    val places by graph.placeRepository.places().collectAsState(initial = emptyList())
    val activePlace by graph.placeRepository.activePlace().collectAsState(initial = null)
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    // Baseline summary per place, refreshed when the place list changes.
    var baselines by remember { mutableStateOf<Map<Long, BaselineState>>(emptyMap()) }
    LaunchedEffect(places) {
        baselines = places.associate { it.id to graph.baselineRepository.state(it.id) }
    }

    var expandedId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text("МЕСТА", style = type.label, color = colors.text)
            Text(
                text = "У каждого места свой baseline. При удалении места его " +
                    "измерения остаются в журнале.",
                style = type.bodySmall,
                color = colors.textSecondary,
            )

            places.forEach { place ->
                PlaceRow(
                    name = place.name,
                    active = place.id == activePlace?.id,
                    baselineLine = baselineSummary(baselines[place.id], unit),
                    expanded = expandedId == place.id,
                    onToggle = {
                        expandedId = if (expandedId == place.id) null else place.id
                    },
                    onRename = { name ->
                        scope.launch { graph.placeRepository.rename(place.id, name) }
                        expandedId = null
                    },
                    onDelete = {
                        scope.launch { graph.placeRepository.delete(place.id) }
                        expandedId = null
                    },
                )
            }

            if (adding) {
                PixelTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "название места",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    PixelButton(
                        text = "ДОБАВИТЬ",
                        primary = true,
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch { graph.placeRepository.add(newName.trim()) }
                            newName = ""
                            adding = false
                        },
                    )
                    PixelButton(text = "ОТМЕНА", onClick = { adding = false })
                }
            } else {
                PixelButton(text = "+ НОВОЕ МЕСТО", onClick = { adding = true })
            }
        }
    }
}

private fun baselineSummary(state: BaselineState?, unit: DoseUnitSetting): String = when (state) {
    null -> "…"
    is BaselineState.Learning -> learningWording(state)
    is BaselineState.Active ->
        DoseFormat.range(
            state.baseline.doseLowMicroSvH,
            state.baseline.doseHighMicroSvH,
            unit,
        ) + " ${DoseFormat.rateUnitLabel(unit)} · " + baselineCollectedWording(state.baseline)
}

@Composable
private fun PlaceRow(
    name: String,
    active: Boolean,
    baselineLine: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    var renameText by remember(name, expanded) { mutableStateOf(name) }
    var confirmingDelete by remember(expanded) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space1)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = PixelDimens.touchTarget)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    Text(text = name.uppercase(), style = type.label, color = colors.text)
                    if (active) PixelTag(text = "активно", color = colors.accent)
                }
                Text(text = baselineLine, style = type.labelSmall, color = colors.textMuted)
            }
            Text(
                text = if (expanded) "−" else "+",
                style = type.label,
                color = colors.textSecondary,
            )
        }

        if (expanded) {
            if (confirmingDelete) {
                Text(
                    text = "Удалить место «$name»? Его измерения останутся в журнале " +
                        "без привязки к месту.",
                    style = type.bodySmall,
                    color = colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    PixelButton(text = "УДАЛИТЬ", onClick = onDelete)
                    PixelButton(text = "ОТМЕНА", onClick = { confirmingDelete = false })
                }
            } else {
                PixelTextField(value = renameText, onValueChange = { renameText = it })
                Row(horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
                    PixelButton(
                        text = "СОХРАНИТЬ ИМЯ",
                        enabled = renameText.isNotBlank() && renameText.trim() != name,
                        onClick = { onRename(renameText.trim()) },
                    )
                    PixelButton(text = "УДАЛИТЬ МЕСТО", onClick = { confirmingDelete = true })
                }
            }
            PixelDivider()
        }
    }
}

// --- Прибор ---

@Composable
private fun DeviceSection(graph: AppGraph) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val rareData by graph.measurementRepository.latestRareData().collectAsState(initial = null)
    val sample by graph.measurementRepository.latestSample().collectAsState(initial = null)

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val freshness = Freshness.of(sample?.timestamp, nowMillis)

    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text("ПРИБОР", style = type.label, color = colors.text)

            when (val state = connection) {
                is ConnectionState.Connected -> {
                    InfoRow("серийный номер", state.info.serialNumber)
                    InfoRow("прошивка", state.info.firmware.toString())
                    InfoRow("bluetooth", "подключено")
                }
                is ConnectionState.Connecting -> InfoRow("bluetooth", "подключение…")
                is ConnectionState.Reconnecting ->
                    InfoRow("bluetooth", "переподключение, попытка ${state.attempt}")
                ConnectionState.Disconnected ->
                    InfoRow("bluetooth", if (serviceRunning) "нет соединения" else "служба остановлена")
            }

            rareData?.let { rare ->
                InfoRow("батарея прибора", "${rare.batteryPercent.toInt()} %")
                InfoRow("температура", "${rare.temperature.toInt()} °C")
            }

            when (freshness) {
                Freshness.NoData -> StatusLine(text = "данных ещё нет", color = colors.textMuted)
                is Freshness.Fresh -> StatusLine(
                    text = "поток активен · 1 Гц",
                    color = colors.textSecondary,
                )
                is Freshness.Stale -> StatusLine(
                    text = freshnessLabel(freshness),
                    color = colors.aboveUsual,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = type.labelSmall, color = colors.textMuted)
        Spacer(Modifier.weight(1f))
        Text(text = value, style = type.labelSmall, color = colors.text)
    }
}

// --- Единицы ---

@Composable
private fun UnitsSection(graph: AppGraph) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val scope = rememberCoroutineScope()
    val unit by graph.settings.doseUnit.collectAsState(initial = DoseUnitSetting.MICRO_SIEVERT)

    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text("ЕДИНИЦЫ", style = type.label, color = colors.text)
            UnitOption(
                title = "мкЗв/ч",
                subtitle = "микрозиверты в час — единица СИ",
                selected = unit == DoseUnitSetting.MICRO_SIEVERT,
                onSelect = {
                    scope.launch { graph.settings.setDoseUnit(DoseUnitSetting.MICRO_SIEVERT) }
                },
            )
            UnitOption(
                title = "мкР/ч",
                subtitle = "микрорентгены в час · 1 мкЗв/ч = 100 мкР/ч",
                selected = unit == DoseUnitSetting.MICRO_ROENTGEN,
                onSelect = {
                    scope.launch { graph.settings.setDoseUnit(DoseUnitSetting.MICRO_ROENTGEN) }
                },
            )
            Text(
                text = "Пересчёт только для отображения: измерения хранятся в исходных " +
                    "единицах прибора без потери точности.",
                style = type.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun UnitOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PixelDimens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PixelDimens.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            ),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (selected) colors.accent else colors.surface2),
        )
        Column {
            Text(
                text = title,
                style = type.label,
                color = if (selected) colors.accent else colors.text,
            )
            Text(text = subtitle, style = type.bodySmall, color = colors.textMuted)
        }
    }
}

// --- О приложении ---

@Composable
private fun AboutSection() {
    val colors = LocalPixelColors.current
    val type = LocalPixelTypography.current
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    var licensesText by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }

    LaunchedEffect(showLicenses) {
        if (showLicenses && licensesText == null) {
            licensesText = runCatching {
                LICENSE_ASSETS.joinToString("\n\n" + "─".repeat(24) + "\n\n") { path ->
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }
            }.getOrElse { "Не удалось прочитать файлы лицензий." }
        }
    }

    PixelBox(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PixelDimens.space2)) {
            Text("О ПРИЛОЖЕНИИ", style = type.label, color = colors.text)
            InfoRow("alpha", "версия $version")
            Text(
                text = "Данные измерений не покидают телефон: без телеметрии, " +
                    "аналитики и сетевых запросов.",
                style = type.bodySmall,
                color = colors.textSecondary,
            )
            PixelDivider()
            Text(
                text = "Протокол RadiaCode — порт библиотеки cdump/radiacode (MIT). " +
                    "BLE — Kable (Apache-2.0). Шрифт Pixelify Sans (OFL) с локальным " +
                    "патчем кириллицы. Полные тексты — в файлах NOTICE внутри приложения.",
                style = type.bodySmall,
                color = colors.textMuted,
            )
            PixelButton(
                text = if (showLicenses) "СКРЫТЬ ЛИЦЕНЗИИ" else "ПОКАЗАТЬ ЛИЦЕНЗИИ",
                onClick = { showLicenses = !showLicenses },
            )
            if (showLicenses) {
                Text(
                    text = licensesText ?: "читаю…",
                    style = type.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

private val LICENSE_ASSETS = listOf(
    "licenses/cdump_radiacode_NOTICE.txt",
    "licenses/pixelify_sans_NOTICE.txt",
    "licenses/pixelify_sans_OFL.txt",
)
