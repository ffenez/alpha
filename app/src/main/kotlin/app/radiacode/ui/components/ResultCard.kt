package app.radiacode.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.radiacode.ui.logic.AnalyticsResult
import app.radiacode.ui.logic.ResultTone
import app.radiacode.ui.text.ChartTextCatalogue
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import app.radiacode.ui.theme.Motion

/**
 * Единая карточка результата аналитических экранов.
 *
 * Четыре части — вывод, объём измерения, смысл и граница — различаются
 * ТИПОГРАФИКОЙ, а не четырьмя заголовками над ними: «РЕЗУЛЬТАТ», «ИЗМЕРЕНИЕ»,
 * «ЧТО ЭТО ЗНАЧИТ», «ОГРАНИЧЕНИЕ» отняли бы у короткой карточки половину
 * высоты, чтобы назвать то, что и так видно по месту и весу строки.
 *
 * «Подробнее» открывает те же данные числами. Выбор запоминается: человек,
 * который всегда открывает подробности, перестаёт это делать руками.
 */
@Composable
fun ResultCard(
    result: AnalyticsResult,
    modifier: Modifier = Modifier,
    /** Что показать между смыслом и границей — график, шкала, свои элементы. */
    content: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val labels = ChartTextCatalogue.of(strings.language)
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            StatusRow(
                text = result.verdict,
                color = when (result.tone) {
                    ResultTone.NOTABLE -> colors.warn
                    ResultTone.PLAIN -> colors.ink
                    ResultTone.UNKNOWN -> colors.muted
                },
            )
            result.measurement?.let {
                Text(text = it, style = type.footnoteMono, color = colors.ink2)
            }
            content?.invoke()
            Hint(text = result.meaning, style = type.bodySmall, color = colors.ink2)
            Text(text = result.limitation, style = type.footnote, color = colors.muted)

            if (result.details.isNotEmpty()) {
                Chip(
                    text = if (expanded) labels.hideDetails else labels.showDetails,
                    color = colors.dataText,
                    selected = expanded,
                    onClick = { expanded = !expanded },
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(Motion.springy()) + fadeIn(Motion.normal()),
                    exit = shrinkVertically(Motion.springy()) + fadeOut(Motion.fast()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                        result.details.forEach { WhyRow(it) }
                    }
                }
            }
        }
    }
}
