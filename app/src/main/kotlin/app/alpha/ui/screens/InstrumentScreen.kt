package app.alpha.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.alpha.AppGraph
import app.alpha.ui.components.AppIcons
import app.alpha.ui.components.Chip
import app.alpha.ui.components.ProfilePickerDialog
import app.alpha.ui.components.Segmented
import app.alpha.ui.logic.ChartMetric
import app.alpha.ui.logic.InstrumentIndicator
import app.alpha.ui.logic.InstrumentMode
import app.alpha.ui.logic.ProfileTree
import app.alpha.ui.logic.StreamState
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.MonitorCatalogue
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * Прибор — один экран на три вопроса об одном и том же.
 *
 * ## Почему один экран
 *
 * Наблюдение и Поиск спрашивают «много ли здесь» и различаются ровно
 * ЗНАМЕНАТЕЛЕМ: медиана фона этого места — либо точка отсчёта, поставленная
 * рукой (а без неё тот же фон места). Раз вопрос один, прибор тоже один — у
 * него переключается шкала, а не экран (макет
 * `docs/design/one-instrument.html`).
 *
 * Отсюда и общая шапка: место, связь, поток и настройки не зависят от того,
 * какой сейчас знаменатель, и переезжать при переключении им незачем.
 *
 * ## Что переключается вместе с режимом
 *
 * Величина (мощность дозы для места, скорость счёта для поиска — счёт набирает
 * статистику быстрее и потому годится, чтобы водить прибором), концы шкалы,
 * подпись под осью и содержимое плиток. Дыхание, шапка и нижняя навигация
 * общие.
 */
@Composable
fun InstrumentScreen(
    graph: AppGraph,
    onOpenSettings: () -> Unit = {},
    onOpenChart: () -> Unit = {},
    onOpenMetricChart: (ChartMetric) -> Unit = {},
    onOpenDose: () -> Unit = {},
    onOpenSpectrum: () -> Unit = {},
    onOpenFingerprint: () -> Unit = {},
    onOpenSearchChart: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val strings = LocalStrings.current
    val t = MonitorCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()

    val modeId by graph.settings.instrumentMode.collectAsState(initial = null)
    val mode = InstrumentMode.of(modeId)
    val indicatorId by graph.settings.instrumentIndicator.collectAsState(initial = null)
    val indicator = InstrumentIndicator.of(indicatorId)

    val connection by graph.serviceStatus.connection.collectAsState()
    val serviceRunning by graph.serviceStatus.serviceRunning.collectAsState()
    val live by graph.serviceStatus.lastSample.collectAsState()
    val connectedAt by graph.serviceStatus.connectedAtMillis.collectAsState()
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val contextState by graph.contextHub.state.collectAsState()
    var showProfilePicker by remember { mutableStateOf(false) }

    // Свежесть считается по ЧАСАМ ТЕЛЕФОНА в момент прихода пакета — тем же
    // способом, что внутри режимов: шапка не имеет права говорить о связи
    // иначе, чем говорит показание под ней.
    val stream = StreamState.of(live?.receivedAtMillis, System.currentTimeMillis(), connection)

    if (showProfilePicker) {
        ProfilePickerDialog(
            profiles = profiles,
            activeProfileId = activeProfile?.id,
            manual = contextState.isManual,
            contextWording = contextWording(contextState, t),
            onSelect = { id -> scope.launch { graph.profileRepository.selectManually(id) } },
            onReturnToAuto = { scope.launch { graph.profileRepository.returnToAuto() } },
            onCreate = { name ->
                scope.launch {
                    val id = graph.profileRepository.add(name)
                    graph.profileRepository.selectManually(id)
                }
            },
            onDismiss = { showProfilePicker = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(
                start = Dimens.space3,
                end = Dimens.space3,
                top = Dimens.space3,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.space2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
            ) {
                Chip(
                    text = profileChipText(activeProfile, profiles, contextState, t),
                    color = colors.ink,
                    onClick = { showProfilePicker = true },
                )
                Spacer(Modifier.weight(1f))
                ConnectedFlash(connectedAt)
                ConnectionChip(connection, serviceRunning, stream)
                StreamChip(stream)
                Icon(
                    imageVector = AppIcons.Lambda,
                    contentDescription = strings.settings,
                    tint = colors.ink2,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenSettings,
                        ),
                )
            }
            // Три вопроса прибора одним рядом: переключается знаменатель, а не
            // экран. Выбор запоминается — прибор открывается там, где его
            // оставили.
            Segmented(
                options = InstrumentMode.entries.map { it.title(strings) },
                selectedIndex = InstrumentMode.entries.indexOf(mode),
                onSelect = { index ->
                    scope.launch {
                        graph.settings.setInstrumentMode(InstrumentMode.entries[index].id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (mode) {
            InstrumentMode.OBSERVE -> MonitorScreen(
                graph = graph,
                indicator = indicator,
                onOpenChart = onOpenChart,
                onOpenMetricChart = onOpenMetricChart,
                onOpenDose = onOpenDose,
            )

            InstrumentMode.SEARCH -> SearchScreen(
                graph = graph,
                indicator = indicator,
                onOpenSpectrum = onOpenSpectrum,
                onOpenFingerprint = onOpenFingerprint,
                onOpenChart = onOpenSearchChart,
            )
        }
    }
}
