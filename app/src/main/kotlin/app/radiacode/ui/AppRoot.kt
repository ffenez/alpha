package app.radiacode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.radiacode.AppGraph
import app.radiacode.service.MeasurementService
import app.radiacode.ui.components.AppTab
import app.radiacode.ui.components.PixelNavBar
import app.radiacode.ui.components.crtOverlay
import app.radiacode.ui.screens.HistoryPlaceholder
import app.radiacode.ui.screens.MapPlaceholder
import app.radiacode.ui.screens.MonitorScreen
import app.radiacode.ui.screens.OnboardingScreen
import app.radiacode.ui.screens.SearchScreen
import app.radiacode.ui.screens.SpectrumPlaceholder
import app.radiacode.ui.theme.LocalPixelColors

/** Remembered-device lookup: distinguishes "loading" from "no device yet". */
private sealed interface RememberedDevice {
    data object Loading : RememberedDevice
    data class Loaded(val address: String?) : RememberedDevice
}

/**
 * Single-activity root. A remembered device routes straight to the tabbed
 * app (and resumes the measurement service); otherwise onboarding runs and
 * the first successful connect flips this switch automatically, because the
 * service persists the address into settings.
 */
@Composable
fun AppRoot(graph: AppGraph) {
    val colors = LocalPixelColors.current
    val remembered by produceState<RememberedDevice>(RememberedDevice.Loading, graph) {
        graph.settings.lastDeviceAddress.collect { value = RememberedDevice.Loaded(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .crtOverlay(enabled = colors.isDark),
    ) {
        when (val state = remembered) {
            RememberedDevice.Loading -> Unit // ground-colored frame, resolves in ms
            is RememberedDevice.Loaded ->
                if (state.address == null) {
                    OnboardingScreen(graph)
                } else {
                    MainScaffold(graph)
                }
        }
    }
}

@Composable
private fun MainScaffold(graph: AppGraph) {
    val context = LocalContext.current
    // Keep the measurement service alive (no-op if it already runs).
    LaunchedEffect(Unit) {
        ContextCompat.startForegroundService(context, MeasurementService.resumeIntent(context))
    }

    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            when (tab) {
                AppTab.HOME -> MonitorScreen(graph)
                AppTab.SEARCH -> SearchScreen(graph)
                AppTab.SPECTRUM -> SpectrumPlaceholder()
                AppTab.MAP -> MapPlaceholder()
                AppTab.HISTORY -> HistoryPlaceholder()
            }
        }
        PixelNavBar(selected = tab, onSelect = { tab = it })
    }
}
