package app.alpha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.alpha.analysis.FingerprintComparison
import app.alpha.analysis.FingerprintState
import app.alpha.ui.text.FingerprintStrings
import app.alpha.ui.theme.LocalAppColors
import app.alpha.ui.theme.LocalAppTypography

/**
 * Три составляющие отпечатка места — доза, счёт, форма спектра — по строке на
 * каждую.
 *
 * Общий компонент, потому что этих строк два места: сам экран отпечатка и
 * сводка в «Проверке». Готовность у составляющих РАЗНАЯ, и строка называет
 * состояние каждой отдельно: одно «мало данных» на всех скрывало уже сделанную
 * часть сравнения.
 *
 * @param detailed показывать ли числа под состоянием — на своём экране да, в
 *   сводке достаточно состояния и того, чего не хватает
 */
@Composable
fun FingerprintDimensionRows(
    comparison: FingerprintComparison,
    t: FingerprintStrings,
    detailed: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    Column(modifier = modifier.fillMaxWidth()) {
        comparison.verdicts.forEachIndexed { index, verdict ->
            if (index > 0 && detailed) AppDivider()
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(vertical = if (detailed) 9.dp else 3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = verdict.dimension.title(t),
                        style = if (detailed) type.label else type.bodySmall,
                        color = colors.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = fingerprintStateLabel(verdict.state, t),
                        style = if (detailed) type.value else type.bodySmall,
                        color = fingerprintStateColor(verdict.state),
                    )
                }
                // Чего именно не хватает — часть состояния, а не подробность:
                // «мало данных» без «сколько из скольких» не отвечает на
                // единственный вопрос, который в этот момент есть.
                val showDetail = detailed || verdict.state == FingerprintState.NOT_ENOUGH_DATA
                if (showDetail && verdict.detail.isNotBlank()) {
                    Text(text = verdict.detail, style = type.footnote, color = colors.muted)
                }
                if (detailed) {
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
}

/** Название состояния измерения — одно на все места, где оно показывается. */
fun fingerprintStateLabel(state: FingerprintState, t: FingerprintStrings): String = when (state) {
    FingerprintState.SAME -> t.stateSame
    FingerprintState.CHANGED -> t.stateChanged
    FingerprintState.NOT_ENOUGH_DATA -> t.stateNotEnoughData
    FingerprintState.NOT_EVALUATED -> t.stateNotEvaluated
}

/** Цвет состояния: отличие — янтарное, непроверенное — тусклое. */
@Composable
fun fingerprintStateColor(state: FingerprintState): Color = when (state) {
    FingerprintState.SAME -> LocalAppColors.current.ok
    FingerprintState.CHANGED -> LocalAppColors.current.warn
    else -> LocalAppColors.current.muted
}
