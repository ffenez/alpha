package app.radiacode.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
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
import app.radiacode.ui.screens.HistoryScreen
import app.radiacode.ui.screens.MapPlaceholder
import app.radiacode.ui.screens.MonitorScreen
import app.radiacode.ui.screens.OnboardingScreen
import app.radiacode.ui.screens.SearchScreen
import app.radiacode.ui.screens.SessionDetailScreen
import app.radiacode.ui.screens.SettingsScreen
import app.radiacode.ui.screens.SpectrumScreen
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
    var remembered by remember { mutableStateOf<RememberedDevice>(RememberedDevice.Loading) }
    LaunchedEffect(graph) {
        graph.settings.lastDeviceAddress.collect { remembered = RememberedDevice.Loaded(it) }
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
    // Overlays above the tab content; Settings opens separately (SPEC, gear
    // on Монитор), a session detail comes from История. Back and tab
    // switches dismiss them.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var sessionDetailId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = showSettings || sessionDetailId != null) {
        if (showSettings) showSettings = false else sessionDetailId = null
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            val detailId = sessionDetailId
            when {
                showSettings -> SettingsScreen(graph, onBack = { showSettings = false })
                detailId != null -> SessionDetailScreen(
                    graph = graph,
                    sessionId = detailId,
                    onBack = { sessionDetailId = null },
                )
                else -> when (tab) {
                    AppTab.HOME -> MonitorScreen(
                        graph = graph,
                        onOpenSettings = { showSettings = true },
                    )
                    AppTab.SEARCH -> SearchScreen(graph)
                    AppTab.SPECTRUM -> SpectrumScreen(graph)
                    AppTab.MAP -> MapPlaceholder()
                    AppTab.HISTORY -> HistoryScreen(
                        graph = graph,
                        onOpenSession = { sessionDetailId = it },
                    )
                }
            }
        }
        PixelNavBar(
            selected = tab,
            onSelect = {
                showSettings = false
                sessionDetailId = null
                tab = it
            },
        )
    }
}
