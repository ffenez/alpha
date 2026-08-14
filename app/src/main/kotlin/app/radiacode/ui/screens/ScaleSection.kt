package app.radiacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.radiacode.AppGraph
import app.radiacode.ui.components.AppButton
import app.radiacode.ui.components.Card
import app.radiacode.ui.text.LocalStrings
import app.radiacode.ui.theme.Dimens
import app.radiacode.ui.theme.LocalAppColors
import app.radiacode.ui.theme.LocalAppTypography
import app.radiacode.ui.theme.UiScale
import kotlinx.coroutines.launch

/**
 * Масштаб интерфейса — два независимых ползунка (Настройки → Вид).
 *
 * Секция вынесена в свой файл намеренно: экран настроек и без того длинный, а
 * здесь единственное место приложения, где элементы управления меняют размер
 * самих себя — по мере движения ползунка секция едет под пальцем, и это
 * ожидаемое поведение, а не дефект отрисовки.
 */
@Composable
fun ScaleSection(graph: AppGraph) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val fontPercent by graph.settings.fontScalePercent
        .collectAsState(initial = UiScale.DEFAULT_PERCENT)
    val elementPercent by graph.settings.elementScalePercent
        .collectAsState(initial = UiScale.DEFAULT_PERCENT)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            Text(
                text = strings.scaleTitle.uppercase(),
                style = type.labelSmall,
                color = colors.ink2,
            )

            ScaleSlider(
                label = strings.scaleFont,
                percent = fontPercent,
                min = UiScale.FONT_MIN_PERCENT,
                max = UiScale.FONT_MAX_PERCENT,
                onCommit = { scope.launch { graph.settings.setFontScalePercent(it) } },
            )
            ScaleSlider(
                label = strings.scaleElements,
                percent = elementPercent,
                min = UiScale.ELEMENT_MIN_PERCENT,
                max = UiScale.ELEMENT_MAX_PERCENT,
                onCommit = { scope.launch { graph.settings.setElementScalePercent(it) } },
            )


            if (fontPercent != UiScale.DEFAULT_PERCENT ||
                elementPercent != UiScale.DEFAULT_PERCENT
            ) {
                AppButton(
                    text = strings.scaleReset,
                    onClick = {
                        scope.launch {
                            graph.settings.setFontScalePercent(UiScale.DEFAULT_PERCENT)
                            graph.settings.setElementScalePercent(UiScale.DEFAULT_PERCENT)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScaleSlider(
    label: String,
    percent: Int,
    min: Int,
    max: Int,
    onCommit: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    val type = LocalAppTypography.current
    val strings = LocalStrings.current

    // Позиция ползунка живёт локально, пока палец на экране: значение из
    // настройки прилетает обратно уже изменившимся масштабом, и ползунок
    // прыгал бы под пальцем.
    var position by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val stepped = UiScale.snap(position)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = type.labelSmall, color = colors.ink2)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(text = strings.scalePercent(stepped), style = type.value, color = colors.data)
        }
    }
    Slider(
        value = position,
        onValueChange = { position = it },
        // Коммит на отпускании, а не на каждом кадре: иначе каждое движение
        // пальца писало бы в DataStore и перерисовывало всё приложение.
        onValueChangeFinished = { onCommit(UiScale.snap(position)) },
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min) / UiScale.STEP_PERCENT - 1,
        modifier = Modifier.fillMaxWidth(),
    )
}
