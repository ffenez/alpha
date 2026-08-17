package app.alpha.ui.screens

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.alpha.AppGraph
import app.alpha.analysis.Fingerprint
import app.alpha.analysis.FingerprintComparison
import app.alpha.analysis.FingerprintState
import app.alpha.baseline.BaselineState
import app.alpha.data.FingerprintRepository
import app.alpha.data.db.ProfileFingerprintEntity
import app.alpha.ui.components.Hint
import app.alpha.ui.components.AppButton
import app.alpha.ui.components.AppDivider
import app.alpha.ui.components.Card
import app.alpha.ui.components.Chip
import app.alpha.ui.components.StatusRow
import app.alpha.ui.logic.HistoryFormat
import app.alpha.ui.logic.ProfileTree
import app.alpha.ui.logic.durationWording
import app.alpha.ui.text.FingerprintCatalogue
import app.alpha.ui.text.FingerprintStrings
import app.alpha.ui.text.HistoryCatalogue
import app.alpha.ui.text.HistoryRu
import app.alpha.ui.text.HistoryStrings
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.theme.Dimens
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_MILLIS = 30_000L

/** Всё, что экран показывает; собирается одним проходом по репозиториям. */
private data class FingerprintModel(
    val profileName: String?,
    val comparison: FingerprintComparison,
    val reference: ProfileFingerprintEntity?,
    val maturity: FingerprintRepository.Maturity,
    val baselineActive: Boolean,
)

/**
 * **Радиационный отпечаток места** (ADR 005).
 *
 * Экран отвечает на один вопрос: отличается ли обстановка здесь от того, какой
 * она была, когда для этого места создавался эталон. Отвечает по строке на
 * измерение, с числами под каждой строкой и без сводного балла — «отпечаток
 * совпал на 87 %» здесь невозможно по построению.
 */
@Composable
fun FingerprintScreen(graph: AppGraph, onBack: () -> Unit) {
    val h = HistoryCatalogue.of(LocalStrings.current.language)
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val t = FingerprintCatalogue.of(strings.language)
    val scope = rememberCoroutineScope()

    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val baselineState by graph.serviceStatus.baseline.collectAsState()

    var model by remember { mutableStateOf<FingerprintModel?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // Язык — ключ пересчёта: числа те же, но вердикты и причины собираются
    // текстом, и после переключения языка модель обязана собраться заново.
    LaunchedEffect(activeProfile?.id, baselineState, reload, t) {
        while (true) {
            val profile = activeProfile
            model = if (profile == null) {
                null
            } else {
                val repository = graph.fingerprintRepository
                FingerprintModel(
                    profileName = ProfileTree.displayName(profile, profiles),
                    comparison = Fingerprint.compare(
                        window = repository.window(profile.id),
                        reference = repository.reference(profile.id),
                        s = t,
                    ),
                    reference = repository.entity(profile.id),
                    maturity = repository.maturity(profile.id, baselineState, t),
                    baselineActive = baselineState is BaselineState.Active,
                )
            }
            delay(REFRESH_MILLIS)
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
            AppButton(text = "← ${strings.back}", onClick = onBack)
            Spacer(Modifier.weight(1f))
        }

        val current = model
        if (current == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = t.chooseProfileFirst,
                    style = type.bodySmall,
                    color = colors.muted,
                )
            }
            return@Column
        }

        // --- ответ
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = current.profileName ?: strings.noProfile,
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                StatusRow(
                    text = Fingerprint.headline(current.comparison, t),
                    color = if (current.comparison.anyChanged) colors.warn else colors.ok,
                )
                Fingerprint.hardnessLine(current.comparison, t)?.let {
                    Text(text = it, style = type.footnote, color = colors.muted)
                }
                Hint(text = t.caveat)
            }
        }

        // --- по строке на измерение
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                current.comparison.verdicts.forEachIndexed { index, verdict ->
                    if (index > 0) AppDivider()
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = verdict.dimension.title(t),
                                style = type.label,
                                color = colors.ink,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = stateLabel(verdict.state, t),
                                style = type.value,
                                color = stateColor(verdict.state),
                            )
                        }
                        Text(
                            text = verdict.detail,
                            style = type.footnote,
                            color = colors.muted,
                        )
                        verdict.changePercent?.let {
                            Text(
                                text = t.changeToReference(it),
                                style = type.footnote,
                                color = colors.ink2,
                            )
                        }
                    }
                }
            }
        }

        // --- эталон
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                Text(
                    text = t.referenceSection.uppercase(),
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                val reference = current.reference
                if (reference == null) {
                    Text(
                        text = t.referenceNotCreatedYet,
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    current.maturity.reason?.let {
                        Text(text = it, style = type.footnote, color = colors.muted)
                    }
                } else {
                    Text(
                        text = t.referenceCreated(
                            day = HistoryFormat.dayTime(
                                reference.createdAt,
                                System.currentTimeMillis(),
                                s = h,
                            ),
                            accumulated = durationWording(reference.accumulatedSeconds),
                            spectrum = durationWording(reference.spectrumSeconds),
                        ),
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    Hint(
                        text = t.referenceFrozenExplanation,
                    )
                }
                AppButton(
                    text = if (reference == null) t.createReference else t.updateReference,
                    onClick = {
                        val profileId = activeProfile?.id
                        val baseline = (baselineState as? BaselineState.Active)?.baseline
                        if (profileId != null && baseline != null) {
                            scope.launch {
                                graph.fingerprintRepository.create(
                                    profileId = profileId,
                                    baseline = baseline,
                                    origin = ProfileFingerprintEntity.ORIGIN_USER,
                                )
                                reload += 1
                            }
                        }
                    },
                    enabled = current.baselineActive,
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    text = t.updateReferenceNote,
                )
            }
        }
    }
}

private fun stateLabel(state: FingerprintState, t: FingerprintStrings): String = when (state) {
    FingerprintState.SAME -> t.stateSame
    FingerprintState.CHANGED -> t.stateChanged
    FingerprintState.NOT_ENOUGH_DATA -> t.stateNotEnoughData
    FingerprintState.NOT_EVALUATED -> t.stateNotEvaluated
}

@Composable
private fun stateColor(state: FingerprintState) = when (state) {
    FingerprintState.SAME -> LocalAppColors.current.ok
    FingerprintState.CHANGED -> LocalAppColors.current.warn
    else -> LocalAppColors.current.muted
}
