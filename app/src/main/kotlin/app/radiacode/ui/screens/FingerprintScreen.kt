package app.radiacode.ui.screens

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
import app.radiacode.AppGraph
import app.radiacode.analysis.Fingerprint
import app.radiacode.analysis.FingerprintComparison
import app.radiacode.analysis.FingerprintState
import app.radiacode.baseline.BaselineState
import app.radiacode.data.FingerprintRepository
import app.radiacode.data.db.ProfileFingerprintEntity
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.AppDivider
import app.radiacode.ui.components.Card
import app.radiacode.ui.components.Chip
import app.radiacode.ui.components.StatusRow
import app.radiacode.ui.logic.HistoryFormat
import app.radiacode.ui.logic.ProfileTree
import app.radiacode.ui.logic.durationWording
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
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
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    val activeProfile by graph.profileRepository.activeProfile().collectAsState(initial = null)
    val profiles by graph.profileRepository.profiles().collectAsState(initial = emptyList())
    val baselineState by graph.serviceStatus.baseline.collectAsState()

    var model by remember { mutableStateOf<FingerprintModel?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeProfile?.id, baselineState, reload) {
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
                    ),
                    reference = repository.entity(profile.id),
                    maturity = repository.maturity(profile.id, baselineState),
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
            AppButton(text = "← Назад", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Chip(text = "Отпечаток места", color = colors.ink)
        }

        val current = model
        if (current == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "сначала выберите профиль на Главной — отпечаток принадлежит месту",
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
                    text = current.profileName ?: "Без профиля",
                    style = type.labelSmall,
                    color = colors.ink2,
                )
                StatusRow(
                    text = Fingerprint.headline(current.comparison),
                    color = if (current.comparison.anyChanged) colors.warn else colors.ok,
                )
                Fingerprint.hardnessLine(current.comparison)?.let {
                    Text(text = it, style = type.footnote, color = colors.muted)
                }
                Text(text = Fingerprint.CAVEAT, style = type.footnote, color = colors.muted)
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
                                text = verdict.dimension.title,
                                style = type.label,
                                color = colors.ink,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = stateLabel(verdict.state),
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
                                text = if (it >= 0) "+$it % к эталону" else "$it % к эталону",
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
                Text(text = "Эталон".uppercase(), style = type.labelSmall, color = colors.ink2)
                val reference = current.reference
                if (reference == null) {
                    Text(
                        text = "ещё не создан — приложение создаст его само, когда у места " +
                            "наберётся достаточно пригодных измерений и спектра",
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    current.maturity.reason?.let {
                        Text(text = it, style = type.footnote, color = colors.muted)
                    }
                } else {
                    Text(
                        text = "создан ${HistoryFormat.dayTime(reference.createdAt, System.currentTimeMillis())} · " +
                            "накопление ${durationWording(reference.accumulatedSeconds)} · " +
                            "спектр ${durationWording(reference.spectrumSeconds)}",
                        style = type.bodySmall,
                        color = colors.ink2,
                    )
                    Text(
                        text = "Текущий профиль обновляется автоматически и отвечает на вопрос " +
                            "«что обычно здесь сейчас». Эталон заморожен и отвечает на вопрос " +
                            "«как здесь было тогда» — поэтому постепенное изменение обстановки " +
                            "видно как расхождение между ними.",
                        style = type.footnote,
                        color = colors.muted,
                    )
                }
                AppButton(
                    text = if (reference == null) "Создать эталон сейчас" else "Обновить эталон",
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
                Text(
                    text = "Обновление нужно после ремонта, переезда или смены прибора: " +
                        "прежний эталон останется в истории места.",
                    style = type.footnote,
                    color = colors.muted,
                )
            }
        }
    }
}

private fun stateLabel(state: FingerprintState): String = when (state) {
    FingerprintState.SAME -> "отличий не найдено"
    FingerprintState.CHANGED -> "отличается"
    FingerprintState.NOT_ENOUGH_DATA -> "мало данных"
    FingerprintState.NOT_EVALUATED -> "не оценивалось"
}

@Composable
private fun stateColor(state: FingerprintState) = when (state) {
    FingerprintState.SAME -> LocalAppColors.current.ok
    FingerprintState.CHANGED -> LocalAppColors.current.warn
    else -> LocalAppColors.current.muted
}
