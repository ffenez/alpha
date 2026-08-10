package app.radiacode.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.radiacode.AppGraph
import app.radiacode.device.DiscoveredRadiaCode
import app.radiacode.service.BatteryOptimization
import app.radiacode.service.MeasurementService
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.logic.OnboardingPermissions
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import kotlinx.coroutines.flow.catch

private enum class OnboardingStep { INTRO, BATTERY, SCANNING }

/**
 * First-run flow (SPEC/design: the empty state teaches the first action):
 * explain -> runtime permissions -> honest battery-exemption ask -> scan ->
 * tap a device to connect. Connecting starts [MeasurementService], which
 * persists the address; AppRoot switches to the monitor when it appears.
 */
@Composable
fun OnboardingScreen(graph: AppGraph) {
    val context = LocalContext.current
    var step by remember {
        mutableStateOf(
            when {
                !hasAllPermissions(context) -> OnboardingStep.INTRO
                !BatteryOptimization.isExempt(context) -> OnboardingStep.BATTERY
                else -> OnboardingStep.SCANNING
            },
        )
    }
    var permissionsDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Notifications are nice-to-have; BLE permissions are required.
        val bleGranted = results
            .filterKeys { it != OnboardingPermissions.POST_NOTIFICATIONS }
            .values.all { it }
        if (bleGranted) {
            permissionsDenied = false
            step = if (BatteryOptimization.isExempt(context)) {
                OnboardingStep.SCANNING
            } else {
                OnboardingStep.BATTERY
            }
        } else {
            permissionsDenied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        Text(
            text = "Радиакод",
            style = LocalAppTypography.current.title,
            color = LocalAppColors.current.ink,
        )
        when (step) {
            OnboardingStep.INTRO -> IntroStep(
                denied = permissionsDenied,
                onContinue = {
                    permissionLauncher.launch(
                        OnboardingPermissions.required(Build.VERSION.SDK_INT).toTypedArray(),
                    )
                },
            )
            OnboardingStep.BATTERY -> BatteryStep(
                onAllow = {
                    runCatching {
                        context.startActivity(BatteryOptimization.buildRequestIntent(context))
                    }
                    step = OnboardingStep.SCANNING
                },
                onSkip = { step = OnboardingStep.SCANNING },
            )
            OnboardingStep.SCANNING -> ScanStep(graph)
        }
    }
}

private fun hasAllPermissions(context: android.content.Context): Boolean =
    OnboardingPermissions.required(Build.VERSION.SDK_INT)
        .filter { it != OnboardingPermissions.POST_NOTIFICATIONS }
        .all {
            ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

@Composable
private fun IntroStep(denied: Boolean, onContinue: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            Text("Подключение прибора", style = type.title, color = colors.ink)
            Text(
                text = "Приложение подключается к дозиметру RadiaCode по Bluetooth " +
                    "и непрерывно записывает уровень фона. Все измерения остаются " +
                    "на этом телефоне.",
                style = type.body,
                color = colors.ink2,
            )
            Text(
                text = "Понадобятся разрешения: Bluetooth — чтобы найти и подключить " +
                    "прибор, уведомления — чтобы показывать измерение, пока " +
                    "приложение свёрнуто.",
                style = type.bodySmall,
                color = colors.muted,
            )
            if (denied) {
                Text(
                    text = "Без разрешения на Bluetooth прибор найти нельзя. " +
                        "Если запрос больше не показывается — включите разрешение " +
                        "в настройках Android для этого приложения.",
                    style = type.bodySmall,
                    color = colors.warn,
                )
            }
            AppButton(
                text = if (denied) "Повторить" else "Начать",
                onClick = onContinue,
                primary = true,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun BatteryStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            Text("Работа в фоне", style = type.title, color = colors.ink)
            Text(
                text = "Чтобы запись фона не прерывалась ночью и при закрытом " +
                    "экране, исключите приложение из оптимизации батареи. Иначе " +
                    "Android со временем разорвёт связь с прибором.",
                style = type.body,
                color = colors.ink2,
            )
            Text(
                text = "Это увеличит расход батареи — обычно незначительно.",
                style = type.bodySmall,
                color = colors.muted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                modifier = Modifier.align(Alignment.End),
            ) {
                AppButton(text = "Позже", onClick = onSkip)
                AppButton(text = "Разрешить", onClick = onAllow, primary = true)
            }
        }
    }
}

@Composable
private fun ScanStep(graph: AppGraph) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current

    val found = remember { mutableStateOf<Map<String, DiscoveredRadiaCode>>(emptyMap()) }
    var scanError by remember { mutableStateOf(false) }
    var connectingAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        graph.scanner.scan()
            .catch { scanError = true }
            .collect { device ->
                found.value = found.value + (device.address to device)
            }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space3)) {
            Text("Поиск прибора", style = type.title, color = colors.ink)

            val devices = found.value.values.sortedByDescending { it.rssi }
            if (devices.isEmpty() && !scanError) {
                Text(
                    text = "ищем приборы рядом…",
                    style = type.bodySmall,
                    color = colors.ink2,
                )
                Text(
                    text = "Включите прибор и держите его рядом. Официальное " +
                        "приложение RadiaCode должно быть закрыто: прибор " +
                        "соединяется только с одним телефоном.",
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
            if (scanError) {
                Text(
                    text = "Поиск не запустился. Проверьте, что Bluetooth включён, " +
                        "и откройте приложение заново.",
                    style = type.body,
                    color = colors.warn,
                )
            }
            devices.forEachIndexed { index, device ->
                if (index > 0) AppDivider()
                DeviceRow(
                    device = device,
                    connecting = connectingAddress == device.address,
                    enabled = connectingAddress == null,
                    onConnect = {
                        connectingAddress = device.address
                        ContextCompat.startForegroundService(
                            context,
                            MeasurementService.startIntent(context, device.address),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredRadiaCode,
    connecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = device.name ?: "RadiaCode",
                style = type.label,
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(device.address, style = type.footnote, color = colors.muted)
                Chip(text = "${device.rssi} дБм")
            }
        }
        if (connecting) {
            Text(
                text = "подключение…",
                style = type.label,
                color = colors.dataText,
            )
        } else {
            AppButton(text = "Подключить", onClick = onConnect, enabled = enabled)
        }
    }
}
