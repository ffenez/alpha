package app.alpha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Кнопка справки у левого края карточки прибора и сама справка за ней.
 *
 * ## Общий компонент, свой текст
 *
 * Место кнопки, её вид и форма окна — часть дизайн-системы и одинаковы в обоих
 * режимах; содержание — нет. Наблюдение и Поиск отвечают на разные вопросы и
 * показывают разные величины с разными знаменателями, и одна справка на двоих
 * либо говорила бы половину не о том экране, который открыт, либо расплывалась
 * в общие слова. Текст приходит от экрана.
 *
 * ## Почему за кнопкой
 *
 * Это обучающий текст: он нужен один раз и не меняет ни одного показания,
 * поэтому живёт за «i» и исчезает вместе с пояснениями ([ExplainInfoButton]).
 * Рабочий экран от него не зависит.
 */
@Composable
fun ReadingHelpRow(
    title: String,
    notes: List<String>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    // Кнопка у ПРАВОГО края: слева она вставала над числом и читалась как
    // часть показания. Место одно на оба режима — повторяющийся элемент не
    // имеет права стоять в разных углах на соседних вкладках.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        ExplainInfoButton(onClick = { open = true })
    }
    if (open) {
        ChartNotesDialog(title = title, notes = notes, onClose = { open = false })
    }
}
