package app.alpha.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.alpha.AppGraph
import app.alpha.device.ConnectionState
import app.alpha.device.DiscoveredRadiaCode
import app.alpha.service.MeasurementService
import app.alpha.ui.components.AppCloseButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Hint
import androidx.compose.runtime.rememberCoroutineScope
import app.alpha.data.DeviceRegistry
import app.alpha.data.db.DeviceEntity
import app.alpha.ui.components.Chip
import app.alpha.ui.components.EntityMenuButton
import app.alpha.ui.components.EntityMenuItem
import app.alpha.ui.components.RenameDialog
import app.alpha.ui.logic.HistoryFormat
import kotlinx.coroutines.launch
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.flow.catch

/**
 * Выбор прибора: сменить тот, с которым работаем.
 *
 * ## Почему по одному, а не все сразу
 *
 * Приложение ведёт ОДИН поток измерений: журнал, базовая линия, сессии,
 * спектрограмма и предагрегация построены на одной секундной ленте, а метка
 * времени в них уникальна. Два прибора, пишущие одновременно, столкнулись бы
 * прямо в ключах таблиц, и разделить их записи было бы нечем. Поэтому здесь
 * ПЕРЕКЛЮЧЕНИЕ: связь со старым прибором закрывается, новый становится текущим.
 *
 * ## Что при этом честно сказать
 *
 * Журнал измерений остаётся общим: строки разных приборов лежат в одном ряду и
 * не помечены прибором. Снимки спектра и шаблоны своего прибора помнят, и
 * анализ, зависящий от кристалла (модель разрешения, температурный ход шкалы,
 * стриппинг), сам отключается на чужом приборе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(graph: AppGraph, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val context = LocalContext.current

    val connection by graph.serviceStatus.connection.collectAsState()
    val currentAddress = (connection as? ConnectionState.Connected)?.info?.address

    val known by graph.deviceRegistry.devices().collectAsState(initial = emptyList())
    val currentSerial = (connection as? ConnectionState.Connected)?.info?.serialNumber
    var renaming by remember { mutableStateOf<DeviceEntity?>(null) }
    val scope = rememberCoroutineScope()

    val found = remember { mutableStateOf<Map<String, DiscoveredRadiaCode>>(emptyMap()) }
    var scanError by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        graph.scanner.scan()
            .catch { scanError = true }
            .collect { device -> found.value = found.value + (device.address to device) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
        contentColor = colors.ink,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.line) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.space4, end = Dimens.space2, bottom = Dimens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.switchDevice,
                style = type.title,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            AppCloseButton(onClose = onDismiss)
        }
        AppDivider()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(Dimens.space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.space3),
        ) {
            // Сначала уже известные приборы: к ним возвращаются, их узнают по
            // имени, и они остаются в списке, когда не в эфире.
            if (known.isNotEmpty()) {
                Text(text = strings.knownDevices, style = type.label, color = colors.ink2)
                known.forEachIndexed { index, device ->
                    if (index > 0) AppDivider()
                    KnownDeviceRow(
                        title = DeviceRegistry.label(device, known, strings.instrumentTitle),
                        subtitle = if (device.serialNumber == currentSerial) {
                            strings.deviceCurrent(strings.instrumentTitle)
                        } else {
                            strings.deviceLastSeen(
                                HistoryFormat.dayTime(
                                    millis = device.lastSeenAt,
                                    nowMillis = System.currentTimeMillis(),
                                    s = HistoryCatalogue.of(strings.language),
                                ),
                            )
                        },
                        current = device.serialNumber == currentSerial,
                        canConnect = device.address != null && device.serialNumber != currentSerial,
                        onConnect = {
                            device.address?.let { address ->
                                picked = address
                                ContextCompat.startForegroundService(
                                    context,
                                    MeasurementService.startIntent(context, address),
                                )
                                onDismiss()
                            }
                        },
                        onRename = { renaming = device },
                    )
                }
                AppDivider()
                Text(text = strings.foundNearby, style = type.label, color = colors.ink2)
            }
            val devices = found.value.values
                .filterNot { device -> known.any { it.address == device.address } }
                .sortedByDescending { it.rssi }
            if (devices.isEmpty() && !scanError) {
                Text(text = strings.scanning, style = type.bodySmall, color = colors.ink2)
            }
            if (scanError) {
                Text(
                    text = strings.onboardingScanFailed,
                    style = type.body,
                    color = colors.warn,
                )
            }
            devices.forEachIndexed { index, device ->
                if (index > 0) AppDivider()
                // Текущий прибор в списке остаётся, но подключать его заново
                // незачем: строка называет его текущим.
                if (device.address == currentAddress) {
                    Text(
                        text = strings.deviceCurrent(device.name ?: "RadiaCode"),
                        style = type.label,
                        color = colors.dataText,
                    )
                } else {
                    DeviceRow(
                        device = device,
                        connecting = picked == device.address,
                        enabled = picked == null,
                        onConnect = {
                            picked = device.address
                            // Служба сама гасит прежнее соединение и запоминает
                            // новый адрес: отдельного «забыть прибор» не нужно.
                            ContextCompat.startForegroundService(
                                context,
                                MeasurementService.startIntent(context, device.address),
                            )
                            onDismiss()
                        },
                    )
                }
            }
            // Ограничение, меняющее прочтение журнала, стоит на самом экране, а
            // не в справке: человек должен знать это ДО переключения.
            Text(
                text = strings.switchDeviceMixNote,
                style = type.footnote,
                color = colors.muted,
            )
            Hint(text = strings.switchDeviceNote)
        }
    }

    renaming?.let { device ->
        RenameDialog(
            title = strings.renameDevice,
            initial = device.displayName.orEmpty(),
            placeholder = device.model ?: strings.instrumentTitle,
            onSave = { name ->
                scope.launch { graph.deviceRegistry.rename(device.id, name) }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

/**
 * Известный прибор в списке: имя, когда виделись, и «⋮» с переименованием.
 *
 * Действие в строке одно — подключиться. Переименование редкое, поэтому оно
 * под «⋮», как и в остальном приложении.
 */
@Composable
private fun KnownDeviceRow(
    title: String,
    subtitle: String,
    current: Boolean,
    canConnect: Boolean,
    onConnect: () -> Unit,
    onRename: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.label,
                color = if (current) colors.dataText else colors.ink,
            )
            Text(text = subtitle, style = type.footnote, color = colors.muted)
        }
        if (canConnect) {
            Chip(text = strings.connect, color = colors.ink2, onClick = onConnect)
        }
        EntityMenuButton(
            menu = listOf(EntityMenuItem(strings.renameDevice, onClick = onRename)),
        )
    }
}
