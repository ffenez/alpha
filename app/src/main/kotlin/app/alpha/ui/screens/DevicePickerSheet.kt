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
            val devices = found.value.values.sortedByDescending { it.rssi }
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
}
